package dev.nineofgaming.itemlab.editor.panel;

import com.google.gson.JsonElement;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.component.CodecComponentState;
import dev.nineofgaming.itemlab.editor.component.IdentifierCollectionEditor;
import dev.nineofgaming.itemlab.editor.ui.control.AutocompleteInput;
import dev.nineofgaming.itemlab.editor.ui.control.ColorSelector;
import dev.nineofgaming.itemlab.editor.ui.control.FieldHints;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.editor.ui.list.EditorListMutations;
import dev.nineofgaming.itemlab.editor.ui.list.OrderedListDragDrop;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import imgui.ImGui;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Purpose-built banner/shield pattern editor with a native Minecraft preview.
 */
public final class BannerEditorPanel extends ComponentEditorPanel {
    private static final AtomicLong NEXT_ROW_ID = new AtomicLong();

    private final AutocompleteInput autocomplete = new AutocompleteInput();
    private final ImBoolean patternsPresent = new ImBoolean(false);
    private final ImBoolean baseColorPresent = new ImBoolean(false);
    private final CodecComponentState providedPatterns = new CodecComponentState(
        DataComponents.PROVIDES_BANNER_PATTERNS,
        "\"minecraft:base\""
    );
    private final List<PatternRow> patterns = new ArrayList<>();
    private List<IdSuggestion> patternIds = List.of();
    private List<IdSuggestion> providedPatternIds = List.of();
    private DyeColor baseColor = DyeColor.WHITE;
    private long focusPatternRowId;
    private boolean focusAddPatternButton;
    private final IdentifierCollectionEditor.FocusState providedPatternsFocus = new IdentifierCollectionEditor.FocusState();
    private OrderedListDragDrop.Scope patternDragScope = OrderedListDragDrop.newScope("banner_patterns");

    public BannerEditorPanel(ItemEditor host) {
        super(host, "banner");
    }

    @Override
    public void requestPendingFocus() {
        focusPatternRowId = 0L;
        focusAddPatternButton = false;
        providedPatternsFocus.clear();
        if (patternsPresent.get()) {
            for (PatternRow row : patterns) {
                if (invalidPatternIdentifier(row.patternId.get(), patternIds, false)) {
                    focusPatternRowId = row.rowId;
                    requestPendingField("patterns", "row/" + row.rowId + "/id");
                    return;
                }
            }
        }
        if (providedPatterns.present()) {
            JsonElement values = providedPatterns.value();
            int size = values.isJsonArray() ? values.getAsJsonArray().size() : 1;
            for (int index = 0; index < size; index++) {
                JsonElement entry = values.isJsonArray() ? values.getAsJsonArray().get(index) : values;
                String source = pendingBufferText("banner/provided_patterns/@identifier/" + index,
                    entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString() ? entry.getAsString() : "");
                if (invalidPatternIdentifier(source, providedPatternIds, !values.isJsonArray())) {
                    requestProvidedPatternFocus(index);
                    return;
                }
            }
            requestProvidedPatternFocus(0);
            return;
        }
        requestPendingField(patternsPresent.get() ? "patterns" : "base_color", "$enabled");
    }

    private void requestProvidedPatternFocus(int index) {
        requestPendingField("provides_banner_patterns", "identifiers");
        providedPatternsFocus.requestRow(index);
    }

    private static boolean invalidPatternIdentifier(String source, List<IdSuggestion> suggestions, boolean allowTag) {
        String value = source.trim();
        boolean tag = allowTag && value.startsWith("#");
        Identifier id = Identifier.tryParse(tag ? value.substring(1) : value);
        if (id == null) return true;
        Minecraft client = Minecraft.getInstance();
        if (!tag && client.level != null) {
            return !client.level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).containsKey(id);
        }
        String canonical = (tag ? "#" : "") + id;
        return !suggestions.isEmpty() && suggestions.stream().noneMatch(suggestion -> suggestion.id().equals(canonical));
    }

    public static boolean supports(DataComponentType<?> type) {
        return type == DataComponents.BANNER_PATTERNS
            || type == DataComponents.BASE_COLOR
            || type == DataComponents.PROVIDES_BANNER_PATTERNS;
    }

    public boolean isAvailable(ItemStack stack) {
        return stack.has(DataComponents.BANNER_PATTERNS)
            || stack.has(DataComponents.BASE_COLOR)
            || stack.has(DataComponents.PROVIDES_BANNER_PATTERNS);
    }

    public void setCatalog(EditorCatalog catalog) {
        patternIds = catalog.registryIds("banner_pattern");
        providedPatternIds = catalog.registryIdsAndTags("banner_pattern");
    }

    public void sync(Minecraft client, ItemStack stack) {
        syncBaseColor(stack);
        syncPatterns(stack);
        providedPatterns.sync(client, stack);
        dirty = false;
        autocomplete.reset();
        resetEditorUi();
        providedPatternsFocus.clear();
    }

    public void syncComponent(Minecraft client, DataComponentType<?> type, ItemStack stack) {
        if (type == DataComponents.BANNER_PATTERNS) syncPatterns(stack);
        if (type == DataComponents.BASE_COLOR) syncBaseColor(stack);
        if (type == DataComponents.PROVIDES_BANNER_PATTERNS) providedPatterns.sync(client, stack);
        dirty = hasPendingChanges();
        autocomplete.reset();
        resetEditorUi();
        providedPatternsFocus.clear();
    }

    private void syncBaseColor(ItemStack stack) {
        DyeColor installed = stack.get(DataComponents.BASE_COLOR);
        baseColorPresent.set(installed != null);
        if (installed != null) {
            baseColor = installed;
        } else if (stack.getItem() instanceof BannerItem bannerItem) {
            baseColor = bannerItem.getColor();
        } else {
            baseColor = DyeColor.WHITE;
        }
    }

    private void syncPatterns(ItemStack stack) {
        patterns.clear();
        focusPatternRowId = 0L;
        focusAddPatternButton = false;
        patternDragScope = OrderedListDragDrop.newScope("banner_patterns");
        BannerPatternLayers installed = stack.get(DataComponents.BANNER_PATTERNS);
        patternsPresent.set(installed != null);
        if (installed != null) {
            for (BannerPatternLayers.Layer layer : installed.layers()) {
                String id = layer.pattern().unwrapKey()
                    .map(key -> key.identifier().toString())
                    .orElse("minecraft:base");
                patterns.add(new PatternRow(id, layer.color()));
            }
        }
    }

    public void draw(Minecraft client) {
        drawEditor(client, null);
    }

    public void drawComponent(Minecraft client, DataComponentType<?> type) {
        drawEditor(client, type);
    }

    private void drawEditor(Minecraft client, DataComponentType<?> componentType) {
        if (drawPanelToolbar(
            () -> save(client, true),
            () -> {
                if (componentType == null) sync(client, host.draft());
                else syncComponent(client, componentType, host.draft());
            }
        )) return;

        EditorUi.beginScrollableChild("banner_editor", 0.0f, Math.max(1.0f, ImGui.getContentRegionAvailY()), false);
        try {
            ItemStack preview = previewStack(client);
            host.setItemIconPreview(preview);
            EditorUi.drawPreviewFormSplit(
                "banner_workspace",
                310.0f,
                () -> drawPreview(preview),
                () -> drawFields(client, componentType)
            );
        } finally {
            ImGui.endChild();
        }
    }

    private void drawFields(Minecraft client, DataComponentType<?> componentType) {
        if (componentType == null || componentType == DataComponents.BASE_COLOR) drawBaseColor();
        if (componentType == null || componentType == DataComponents.BANNER_PATTERNS) drawPatterns(client);
        if (componentType == null || componentType == DataComponents.PROVIDES_BANNER_PATTERNS) {
            drawProvidedPatterns();
        }
    }

    private void drawPreview(ItemStack preview) {
        ImGui.text(tr("banner.preview"));
        host.drawBannerPreview(preview);
    }

    private void drawBaseColor() {
        if (!host.beginComponentCard(
            "banner_base_color",
            tr("banner.base_color"),
            DataComponents.BASE_COLOR,
            hasPendingSectionFocus("base_color")
        )) return;
        applyPendingFieldFocus("base_color", "$enabled");
        if (ImGui.checkbox(tr("banner.base_color.override"), baseColorPresent)) dirty = true;
        ImGui.beginDisabled(!baseColorPresent.get());
        if (drawColorPalette("base", baseColor)) dirty = true;
        ImGui.endDisabled();
        EditorUi.endCard();
        ImGui.spacing();
    }

    private void drawPatterns(Minecraft client) {
        if (!host.beginComponentCard(
            "banner_patterns",
            tr("banner.patterns"),
            DataComponents.BANNER_PATTERNS,
            hasPendingSectionFocus("patterns")
        )) return;
        applyPendingFieldFocus("patterns", "$enabled");
        if (ImGui.checkbox(tr("banner.patterns.enabled"), patternsPresent)) dirty = true;
        ImGui.beginDisabled(!patternsPresent.get());
        drawAddPatternAction("top", false);

        int actionIndex = -1;
        EditorUi.ListAction action = EditorUi.ListAction.NONE;
        OrderedListDragDrop.ReorderRequest reorderRequest = null;
        for (int index = 0; index < patterns.size(); index++) {
            PatternRow row = patterns.get(index);
            ImGui.pushID("banner_pattern_" + row.rowId);
            ImGui.spacing();
            EditorUi.ListAction rowAction = drawPattern(client, index, row);
            if (rowAction != EditorUi.ListAction.NONE) {
                actionIndex = index;
                action = rowAction;
            }
            OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                patternDragScope,
                row.rowId,
                index
            );
            if (reorderRequest == null) reorderRequest = rowRequest;
            ImGui.popID();
        }
        if (reorderRequest != null) applyPatternReorder(reorderRequest);
        else applyPatternAction(actionIndex, action);
        if (patterns.isEmpty()) {
            EditorUi.drawEmptyState(tr("banner.patterns.empty"));
        }
        drawAddPatternAction("bottom", true);
        ImGui.endDisabled();
        EditorUi.endCard();
        OrderedListDragDrop.autoScrollCurrentWindow(patternDragScope);
    }

    private void drawAddPatternAction(String placement, boolean acceptFocus) {
        if (acceptFocus && focusAddPatternButton) {
            ImGui.setKeyboardFocusHere();
            focusAddPatternButton = false;
        }
        if (EditorIcons.button(
            "add_banner_pattern_" + placement,
            EditorIcons.Icon.ADD,
            tr("banner.patterns.add")
        )) {
            PatternRow added = createPatternRow();
            patterns.add(added);
            focusPatternRowId = added.rowId;
            focusAddPatternButton = false;
            autocomplete.reset();
            dirty = true;
        }
    }

    private PatternRow createPatternRow() {
        return new PatternRow(
            patternIds.isEmpty() ? "minecraft:base" : patternIds.getFirst().id(),
            DyeColor.WHITE
        );
    }

    private void drawProvidedPatterns() {
        if (!beginSection("provides_banner_patterns", providedPatterns)) return;
        EditorUi.textDisabledWrapped(tr("banner.provided_patterns.help"));
        if (consumePendingFieldFocus("provides_banner_patterns", "identifiers")) ImGui.setScrollHereY(0.35f);
        dirty |= IdentifierCollectionEditor.draw(
            host,
            "provided_banner_pattern",
            "banner/provided_patterns",
            providedPatterns.value(),
            providedPatterns::setValue,
            providedPatternIds,
            tr("field_purpose.banner_pattern_identifier"),
            "minecraft:base",
            tr("banner.provided_patterns.entry"),
            textBuffers,
            providedPatternsFocus
        );
        endSection();
        ImGui.spacing();
    }

    private EditorUi.ListAction drawPattern(Minecraft client, int index, PatternRow row) {
        boolean requestFocus = focusPatternRowId == row.rowId;
        EditorUi.ListCardResult card = EditorUi.beginCollapsibleListCard(
            "banner_pattern_row",
            EditorUi.identifiedTitle(
                tr("banner.patterns.entry", index + 1),
                row.patternId.get().trim() + " | " + row.color.getName()
            ),
            ImGuiTableFlags.SizingStretchProp,
            index,
            patterns.size(),
            true,
            true,
            true,
            requestFocus,
            patternDragScope,
            row.rowId
        );
        if (card.focusApplied()) focusPatternRowId = 0L;
        if (card.expanded()) {
            ImGui.text(tr("banner.patterns.pattern"));
            Identifier id = Identifier.tryParse(row.patternId.get().trim());
            Registry<BannerPattern> registry = Objects.requireNonNull(client.level).registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
            String problem = id == null ? tr("validation.identifier_format")
                : !registry.containsKey(id) ? tr("validation.identifier_unknown") : "";
            applyPendingFieldFocus("patterns", "row/" + row.rowId + "/id");
            if (autocomplete.input(
                "banner_pattern_id",
                new FieldHints(
                    tr("field_purpose.banner_pattern_identifier"),
                    "minecraft:stripe_bottom"
                ),
                row.patternId,
                patternIds,
                problem
            )) dirty = true;

            ImGui.text(tr("banner.patterns.color"));
            if (drawColorPalette("layer_" + index, row.color)) dirty = true;

            EditorUi.endCard();
        }
        return card.action();
    }

    private void applyPatternAction(int index, EditorUi.ListAction action) {
        EditorListMutations.Mutation<PatternRow> mutation = EditorListMutations.apply(
            patterns,
            index,
            action,
            PatternRow::copy,
            this::createPatternRow
        );
        finishPatternMutation(mutation);
    }

    private void applyPatternReorder(OrderedListDragDrop.ReorderRequest request) {
        if (!patternDragScope.equals(request.scope())) return;
        finishPatternMutation(EditorListMutations.reorder(patterns, request, row -> row.rowId));
    }

    private void finishPatternMutation(EditorListMutations.Mutation<PatternRow> mutation) {
        if (!mutation.changed()) return;
        if (mutation.focusRow() == null) {
            focusPatternRowId = 0L;
            focusAddPatternButton = true;
        } else {
            focusPatternRowId = mutation.focusRow().rowId;
            focusAddPatternButton = false;
        }
        autocomplete.reset();
        dirty = true;
    }

    private boolean drawColorPalette(String id, DyeColor selected) {
        DyeColor changed = ColorSelector.drawDye(id, selected);
        if (changed == selected) return false;
        if (id.equals("base")) baseColor = changed;
        else patterns.get(Integer.parseInt(id.substring("layer_".length()))).color = changed;
        return true;
    }

    public boolean save(Minecraft client, boolean announce) {
        return savePendingStack(client, announce, edited -> sync(client, edited));
    }

    private ItemStack previewStack(Minecraft client) {
        try {
            return buildEditedStack(client);
        } catch (RuntimeException ignored) {
            return host.draft();
        }
    }

    private ItemStack buildEditedStack(Minecraft client) {
        ItemStack edited = host.draft().copy();
        if (baseColorPresent.get()) edited.set(DataComponents.BASE_COLOR, baseColor);
        else edited.remove(DataComponents.BASE_COLOR);

        if (patternsPresent.get()) {
            Registry<BannerPattern> registry = Objects.requireNonNull(client.level).registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
            List<BannerPatternLayers.Layer> layers = new ArrayList<>();
            for (PatternRow row : patterns) {
                Identifier id = Identifier.tryParse(row.patternId.get().trim());
                if (id == null) throw new IllegalArgumentException(tr("validation.identifier_format"));
                Holder.Reference<BannerPattern> pattern = registry.get(id)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown banner pattern: " + id));
                layers.add(new BannerPatternLayers.Layer(pattern, row.color));
            }
            edited.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers(List.copyOf(layers)));
        } else {
            edited.remove(DataComponents.BANNER_PATTERNS);
        }
        providedPatterns.apply(client, edited);
        return edited;
    }

    @Override
    public ItemStack pendingStack(Minecraft client) {
        return buildEditedStack(client);
    }

    @Override
    public void restorePending(Minecraft client, ItemStack state) {
        sync(client, state);
        dirty = true;
    }

    @Override
    protected boolean hasPendingChanges() {
        return !ItemStack.matches(host.draft(), buildEditedStack(Minecraft.getInstance()));
    }

    private static final class PatternRow {
        private final long rowId;
        private final ImString patternId;
        private DyeColor color;

        private PatternRow(String patternId, DyeColor color) {
            this.rowId = NEXT_ROW_ID.incrementAndGet();
            this.patternId = new ImString(patternId, 512);
            this.color = color;
        }

        private PatternRow copy() {
            return new PatternRow(patternId.get(), color);
        }
    }
}

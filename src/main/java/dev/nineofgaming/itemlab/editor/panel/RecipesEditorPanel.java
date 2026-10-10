package dev.nineofgaming.itemlab.editor.panel;

import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.component.CodecComponentState;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import dev.nineofgaming.itemlab.editor.component.RecipesDraft;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.editor.ui.list.EditorListMutations;
import dev.nineofgaming.itemlab.editor.ui.list.OrderedListDragDrop;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import dev.nineofgaming.itemlab.editor.ui.control.FieldHints;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static dev.nineofgaming.itemlab.editor.ui.control.EditorInput.inputTextWithHint;
import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Focused Components-tab editor for the ordered {@code minecraft:recipes} list.
 */
public final class RecipesEditorPanel extends ComponentEditorPanel {
    private static final AtomicLong NEXT_ROW_ID = new AtomicLong();
    private static final String FALLBACK_RECIPE = "minecraft:acacia_boat";
    private static final int MAX_VISIBLE_CATALOG_RESULTS = 50;

    private final CodecComponentState recipes = new CodecComponentState(DataComponents.RECIPES, "[]");
    private final List<RecipeRow> rows = new ArrayList<>();
    private final ImString search = new ImString(256);
    private List<IdSuggestion> suggestions = List.of();
    private Set<String> knownIds = Set.of();
    private List<String> pendingBulkIds = List.of();
    private long focusRowId;
    private boolean focusAddButton;
    private boolean requestBulkConfirmation;
    private OrderedListDragDrop.Scope rowDragScope = OrderedListDragDrop.newScope("recipes");

    public RecipesEditorPanel(ItemEditor host) {
        super(host, "recipes");
    }

    @Override
    public void requestPendingFocus() {
        requestPendingSection();
        RecipeRow blocking = rows.stream()
            .filter(row -> !identifierProblem(row.value().get().trim()).isBlank())
            .findFirst().orElse(rows.isEmpty() ? null : rows.getFirst());
        focusRowId = blocking == null ? 0L : blocking.id();
        focusAddButton = blocking == null;
    }

    public static boolean supports(DataComponentType<?> type) {
        return type == DataComponents.RECIPES;
    }

    public void setCatalog(EditorCatalog catalog) {
        suggestions = catalog.registryIds("recipe");
        Set<String> ids = new HashSet<>();
        for (IdSuggestion suggestion : suggestions) ids.add(suggestion.id());
        knownIds = Set.copyOf(ids);
    }

    public void sync(Minecraft client, ItemStack stack) {
        recipes.sync(client, stack);
        loadRows();
        resetTransientUi();
        dirty = false;
    }

    public void syncComponent(Minecraft client, DataComponentType<?> type, ItemStack stack) {
        if (!supports(type)) return;
        sync(client, stack);
    }

    public void draw(Minecraft client) {
        if (drawPanelToolbar(
            () -> save(client, true),
            () -> sync(client, host.draft())
        )) return;

        if (beginSection("recipes", recipes)) {
            drawSearchAndActions();
            ImGui.spacing();
            drawRows();
            endSection();
            OrderedListDragDrop.autoScrollCurrentWindow(rowDragScope);
        }
        if (requestBulkConfirmation) {
            ImGui.openPopup("itemlab_recipes_bulk_confirmation");
            requestBulkConfirmation = false;
        }
        drawBulkConfirmation();
    }

    private void drawSearchAndActions() {
        ImGui.setNextItemWidth(-1.0f);
        inputTextWithHint("##recipes_search", tr("recipes.search_hint"), search);

        List<String> filtered = filteredCatalogIds();
        drawCatalogSearchResults(filtered);
        List<String> missing = missingIds(filtered);
        drawAddRecipeAction("top", false, filtered, missing);
        EditorUi.sameLineIfFits(tr("recipes.add_all_filtered", missing.size()));
        ImGui.beginDisabled(missing.isEmpty());
        if (ImGui.button(tr("recipes.add_all_filtered", missing.size()))) {
            pendingBulkIds = List.copyOf(missing);
            requestBulkConfirmation = true;
        }
        ImGui.endDisabled();

        int duplicates = duplicateCount();
        if (duplicates > 0) {
            EditorUi.sameLineIfFits(tr("recipes.remove_duplicates", duplicates));
            if (ImGui.button(tr("recipes.remove_duplicates", duplicates))) {
                removeDuplicates();
            }
        }

        ImGui.textDisabled(tr(
            "recipes.catalog_summary",
            rows.size(),
            suggestions.size(),
            filtered.size()
        ));
    }

    private void drawCatalogSearchResults(List<String> filtered) {
        if (normalizedSearch().isEmpty()) return;
        ImGui.separatorText(tr("recipes.search_results", filtered.size()));
        if (filtered.isEmpty()) {
            EditorUi.textDisabledWrapped(tr("recipes.search_results.empty"));
            return;
        }

        Set<String> existing = new HashSet<>();
        for (RecipeRow row : rows) existing.add(row.value().get().trim());
        float resultHeight = Math.clamp(Math.min(filtered.size(), 6) * ImGui.getFrameHeightWithSpacing() + 8.0f, 54.0f,
            180.0f);
        EditorUi.beginScrollableChild("recipe_catalog_results", 0.0f, resultHeight, true);
        try {
            int limit = Math.min(filtered.size(), MAX_VISIBLE_CATALOG_RESULTS);
            for (int index = 0; index < limit; index++) {
                String id = filtered.get(index);
                boolean alreadyAdded = existing.contains(id);
                ImGui.beginDisabled(alreadyAdded);
                if (ImGui.selectable(
                    (alreadyAdded ? "= " : "+ ") + id + "###recipe_catalog_" + id
                )) {
                    RecipeRow added = newRow(id);
                    rows.add(added);
                    focusRowId = added.id();
                    existing.add(id);
                    dirty = true;
                    host.resetAutocomplete();
                }
                ImGui.endDisabled();
            }
            if (filtered.size() > limit) {
                ImGui.textDisabled(tr("recipes.search_results.more", filtered.size() - limit));
            }
        } finally {
            ImGui.endChild();
        }
    }

    private void drawRows() {
        ImGui.separatorText(tr("recipes.entries", rows.size()));
        int actionIndex = -1;
        EditorUi.ListAction pendingAction = EditorUi.ListAction.NONE;
        OrderedListDragDrop.ReorderRequest reorderRequest = null;
        Map<String, Integer> frequencies = frequencies();

        for (int index = 0; index < rows.size(); index++) {
            RecipeRow row = rows.get(index);
            String value = row.value().get().trim();

            ImGui.pushID("recipe_row_" + row.id());
            boolean requestFocus = focusRowId == row.id();
            EditorUi.ListCardResult card = EditorUi.beginCollapsibleListCard(
                "recipe_entry",
                EditorUi.identifiedTitle(tr("recipes.entry", index + 1), value),
                ImGuiTableFlags.SizingStretchProp,
                index,
                rows.size(),
                true,
                true,
                true,
                requestFocus,
                rowDragScope,
                row.id()
            );
            if (card.focusApplied()) focusRowId = 0L;
            if (card.action() != EditorUi.ListAction.NONE) {
                actionIndex = index;
                pendingAction = card.action();
            }
            if (card.expanded()) {
                if (requestFocus) ImGui.setKeyboardFocusHere();
                String problem = identifierProblem(value);
                if (host.inputIdentifierWithSuggestions(
                    "recipe_id",
                    new FieldHints(tr("field_purpose.recipe_identifier"), FALLBACK_RECIPE),
                    row.value(),
                    suggestions,
                    problem
                )) dirty = true;

                String current = row.value().get().trim();
                String currentProblem = identifierProblem(current);
                if (currentProblem.isBlank() && !knownIds.isEmpty() && !knownIds.contains(current)) {
                    ItemEditorTheme.drawFieldWarning(tr("recipes.unavailable", current));
                }
                if (frequencies.getOrDefault(current, 0) > 1) {
                    ItemEditorTheme.drawFieldWarning(tr("recipes.duplicate", current));
                }
                EditorUi.endCard();
            }
            OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                rowDragScope,
                row.id(),
                index
            );
            if (reorderRequest == null) reorderRequest = rowRequest;
            ImGui.popID();
            ImGui.spacing();
        }

        if (reorderRequest != null) applyRowReorder(reorderRequest);
        else if (actionIndex >= 0) applyRowAction(actionIndex, pendingAction);
        if (rows.isEmpty()) {
            EditorUi.drawEmptyState(tr("recipes.empty"));
        }
        List<String> filtered = filteredCatalogIds();
        drawAddRecipeAction("bottom", true, filtered, missingIds(filtered));
    }

    private void drawAddRecipeAction(
        String placement,
        boolean acceptFocus,
        List<String> filtered,
        List<String> missing
    ) {
        if (acceptFocus && focusAddButton) {
            ImGui.setKeyboardFocusHere();
            focusAddButton = false;
        }
        if (EditorIcons.smallButton(
            "add_recipe_" + placement,
            EditorIcons.Icon.ADD,
            tr("recipes.add")
        )) {
            RecipeRow added = newRow(recipeValueForAdd(filtered, missing));
            rows.add(added);
            focusRowId = added.id();
            focusAddButton = false;
            dirty = true;
            host.resetAutocomplete();
            resetEditorUi();
        }
    }

    private void drawBulkConfirmation() {
        if (!ImGui.isPopupOpen("itemlab_recipes_bulk_confirmation")) return;
        ImGui.setNextWindowSizeConstraints(420.0f, 1.0f, 420.0f, Float.MAX_VALUE);
        ImGui.setNextWindowPos(
            ImGui.getMainViewport().getWorkCenterX(),
            ImGui.getMainViewport().getWorkCenterY(),
            ImGuiCond.Appearing,
            0.5f,
            0.5f
        );
        if (!ImGui.beginPopupModal(
            tr("recipes.add_all.title") + "###itemlab_recipes_bulk_confirmation",
            ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoSavedSettings
        )) return;
        try {
            ImGui.textWrapped(tr("recipes.add_all.description", pendingBulkIds.size()));
            EditorUi.textDisabledWrapped(tr("recipes.add_all.duplicates"));
            ImGui.spacing();
            if (ImGui.button(tr("recipes.add_all.confirm", pendingBulkIds.size()), 150.0f, 0.0f)) {
                RecipeRow firstAdded = null;
                for (String id : pendingBulkIds) {
                    RecipeRow added = newRow(id);
                    rows.add(added);
                    if (firstAdded == null) firstAdded = added;
                }
                if (firstAdded != null) focusRowId = firstAdded.id();
                dirty = true;
                pendingBulkIds = List.of();
                search.set("");
                host.resetAutocomplete();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(tr("action.cancel"), 100.0f, 0.0f)) {
                pendingBulkIds = List.of();
                ImGui.closeCurrentPopup();
            }
        } finally {
            ImGui.endPopup();
        }
    }

    private void applyRowAction(int index, EditorUi.ListAction action) {
        EditorListMutations.Mutation<RecipeRow> mutation = EditorListMutations.apply(
            rows,
            index,
            action,
            source -> newRow(source.value().get()),
            this::newRecipeRowForAdd
        );
        finishRowMutation(mutation);
    }

    private void applyRowReorder(OrderedListDragDrop.ReorderRequest request) {
        if (!rowDragScope.equals(request.scope())) return;
        finishRowMutation(EditorListMutations.reorder(rows, request, RecipeRow::id));
    }

    private void finishRowMutation(EditorListMutations.Mutation<RecipeRow> mutation) {
        if (!mutation.changed()) return;
        if (mutation.focusRow() == null) {
            focusRowId = 0L;
            focusAddButton = true;
        } else {
            focusRowId = mutation.focusRow().id();
            focusAddButton = false;
        }
        dirty = true;
        host.resetAutocomplete();
        resetEditorUi();
    }

    private RecipeRow newRecipeRowForAdd() {
        List<String> filtered = filteredCatalogIds();
        return newRow(recipeValueForAdd(filtered, missingIds(filtered)));
    }

    private static String recipeValueForAdd(List<String> filtered, List<String> missing) {
        return missing.isEmpty()
            ? filtered.isEmpty() ? FALLBACK_RECIPE : filtered.getFirst()
            : missing.getFirst();
    }

    private void removeDuplicates() {
        Set<String> seen = new LinkedHashSet<>();
        rows.removeIf(row -> !seen.add(row.value().get().trim()));
        dirty = true;
        host.resetAutocomplete();
    }

    private List<String> filteredCatalogIds() {
        String query = normalizedSearch();
        return suggestions.stream()
            .filter(suggestion -> query.isEmpty() || suggestion.searchable().contains(query))
            .map(IdSuggestion::id)
            .toList();
    }

    private List<String> missingIds(List<String> candidates) {
        Set<String> existing = new HashSet<>();
        for (RecipeRow row : rows) existing.add(row.value().get().trim());
        return candidates.stream().filter(existing::add).toList();
    }

    private String normalizedSearch() {
        return search.get().trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, Integer> frequencies() {
        Map<String, Integer> frequencies = new HashMap<>();
        for (RecipeRow row : rows) {
            frequencies.merge(row.value().get().trim(), 1, Integer::sum);
        }
        return frequencies;
    }

    private int duplicateCount() {
        return frequencies().values().stream().mapToInt(count -> Math.max(0, count - 1)).sum();
    }

    public boolean save(Minecraft client, boolean announce) {
        return savePendingStack(client, announce, edited -> sync(client, edited));
    }

    @Override
    public ItemStack pendingStack(Minecraft client) {
        RecipesDraft pending = pendingRecipes();
        ItemStack edited = host.draft().copy();
        if (pending.componentPresent()) {
            ItemStackCodecs.decodeAndSetComponent(client, edited, DataComponents.RECIPES, pending.toJson());
        } else {
            edited.remove(DataComponents.RECIPES);
        }
        ItemStackCodecs.validateStack(edited);
        return edited;
    }

    @Override
    public void restorePending(Minecraft client, ItemStack state) {
        sync(client, state);
        dirty = true;
    }

    @Override
    protected boolean hasPendingChanges() {
        RecipesDraft pending = pendingRecipes();
        Object installed = host.draft().get(DataComponents.RECIPES);
        if (pending.componentPresent() != (installed != null)) return true;
        return pending.componentPresent() && !pending.toJson().equals(
            ItemStackCodecs.encodeComponent(Minecraft.getInstance(), DataComponents.RECIPES, installed)
        );
    }

    private RecipesDraft pendingRecipes() {
        return RecipesDraft.fromStrings(
            recipes.present(),
            recipes.present() ? rows.stream().map(row -> row.value().get()).toList() : List.of()
        );
    }

    private void loadRows() {
        rows.clear();
        RecipesDraft.fromJson(recipes.present(), recipes.value()).recipeIds()
            .forEach(identifier -> rows.add(newRow(identifier.toString())));
    }

    private void resetTransientUi() {
        search.set("");
        pendingBulkIds = List.of();
        focusRowId = 0L;
        focusAddButton = false;
        requestBulkConfirmation = false;
        rowDragScope = OrderedListDragDrop.newScope("recipes");
        resetEditorUi();
    }

    private static String identifierProblem(String source) {
        if (source.isBlank()) return tr("validation.identifier_required");
        return Identifier.tryParse(source) == null ? tr("validation.identifier_format") : "";
    }

    private static RecipeRow newRow(String value) {
        String normalized = value == null ? "" : value.trim();
        return new RecipeRow(
            NEXT_ROW_ID.incrementAndGet(),
            new ImString(normalized, Math.max(512, normalized.length() * 2 + 64))
        );
    }

    private record RecipeRow(long id, ImString value) {
    }
}

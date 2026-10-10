package dev.nineofgaming.itemlab.editor.panel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.component.CodecComponentState;
import dev.nineofgaming.itemlab.editor.component.IdentifierCollectionEditor;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import dev.nineofgaming.itemlab.editor.container.BundleContentsDraft;
import dev.nineofgaming.itemlab.editor.container.ContainerContentsDraft;
import dev.nineofgaming.itemlab.editor.container.ContainerPresentationProfile;
import dev.nineofgaming.itemlab.editor.container.SourceInventorySnapshot;
import dev.nineofgaming.itemlab.editor.ui.control.CodeEditorInput;
import dev.nineofgaming.itemlab.editor.ui.syntax.CodeLanguage;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import dev.nineofgaming.itemlab.editor.ui.control.FieldHints;
import dev.nineofgaming.itemlab.editor.ui.list.EditorListMutations;
import dev.nineofgaming.itemlab.editor.ui.list.OrderedListDragDrop;
import dev.nineofgaming.itemlab.editor.ui.preview.NativeItemPreviews;
import imgui.ImGui;
import imgui.flag.ImGuiDragDropFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.apache.commons.lang3.math.Fraction;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static dev.nineofgaming.itemlab.editor.ui.control.EditorInput.inputTextWithHint;
import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Visual editor for container contents, lock conditions, and loot-table metadata.
 */
public final class ContainerEditorPanel extends ComponentEditorPanel {
    private static final AtomicLong NEXT_BUNDLE_ROW_ID = new AtomicLong();
    private static final int MAX_ITEM_COUNT = 99;
    private static final String SOURCE_STACK_PAYLOAD = "ITEMLAB_CONTAINER_SOURCE";
    private static final String CONTENTS_SLOT_PAYLOAD = "ITEMLAB_CONTAINER_CONTENTS_SLOT";
    private static final String BUNDLE_ITEM_PAYLOAD = "ITEMLAB_BUNDLE_CONTENTS_ITEM";

    private final CodecComponentState lock = new CodecComponentState(
        DataComponents.LOCK,
        "{\"items\":[\"minecraft:tripwire_hook\"]}"
    );
    private final CodecComponentState loot = new CodecComponentState(
        DataComponents.CONTAINER_LOOT,
        "{\"loot_table\":\"minecraft:empty\"}"
    );
    private final ImString exactComponentsSource = new ImString("{}", 262_144);
    private final ImString partialPredicatesSource = new ImString("{}", 262_144);
    private final ImString lootTable = new ImString("minecraft:empty", 512);
    private final ImString lootSeed = new ImString("0", 64);
    private final CodeEditorInput exactComponentsEditor = new CodeEditorInput();
    private final CodeEditorInput partialPredicatesEditor = new CodeEditorInput();
    private ContainerContentsDraft contents = new ContainerContentsDraft();
    private BundleContentsDraft bundleContents = new BundleContentsDraft();
    private final List<Long> bundleRowIds = new ArrayList<>();
    private OrderedListDragDrop.Scope bundleDragScope = OrderedListDragDrop.newScope("bundle_contents");
    private final ImString containedItemId = new ImString(256);
    private final ImInt containedItemCount = new ImInt(1);
    private final ImInt goToContentsSlot = new ImInt(1);
    private final ImString bundleItemId = new ImString(256);
    private final ImInt bundleItemCount = new ImInt(1);
    private int selectedContentsSlot;
    private int selectedBundleItem;
    private int contentsSlotFocusRequest = -1;
    private int bundleSlotFocusRequest = -1;
    private int indexedMinimumCapacity;
    private boolean containedItemIdPending;
    private boolean bundleItemIdPending;
    private boolean indexedView;
    private ItemStack selectedSourceStack = ItemStack.EMPTY;
    private String selectedSourceId = "";
    private String selectedSourceLabel = "";
    private SourceInventorySnapshot.SourceSlot activeSourceDrag;
    private ContentsSlotDrag activeContentsDrag;
    private OrderedListDragDrop.Payload activeBundleDrag;
    private PendingSlotCopy pendingSlotCopy;
    private boolean focusBundleAddButton;
    private List<IdSuggestion> itemSelectors = List.of();
    private List<IdSuggestion> itemIds = List.of();
    private Set<String> knownItemSelectors = Set.of();
    private List<IdSuggestion> lootTables = List.of(IdSuggestion.of("minecraft:empty"));
    private final Map<Item, Boolean> containerBlockItems = new IdentityHashMap<>();
    private final IdentifierCollectionEditor.FocusState lockItemsFocus = new IdentifierCollectionEditor.FocusState();
    private int recoveryContentsSlot = -1;
    private long recoveryBundleRowId;

    public ContainerEditorPanel(ItemEditor host) {
        super(host, "container");
    }

    @Override
    public void requestPendingFocus() {
        clearContainerRecoveryRequests();
        if (contents.present() && !containedItemProblem().isBlank()) {
            requestPendingField("contents", "item_id");
            return;
        }
        if (bundleContents.present()) {
            if (bundleItemIdPending && !bundleItemProblem().isBlank()) {
                requestPendingField("bundle_contents", "item_id");
                return;
            }
            int disallowed = hasSelectedBundleItem() && !bundleItemProblem().isBlank()
                ? selectedBundleItem : bundleContents.firstDisallowedItem();
            if (disallowed >= 0) {
                requestBundleRecovery(disallowed);
                return;
            }
        }
        Minecraft client = Minecraft.getInstance();
        ItemStack probe = host.draft().copy();
        try {
            contents.applyTo(probe);
        } catch (RuntimeException ignored) {
            requestPendingField("contents", "$enabled");
            return;
        }
        try {
            bundleContents.applyTo(probe);
        } catch (RuntimeException ignored) {
            requestPendingField("bundle_contents", "$enabled");
            return;
        }
        if (lock.present()) {
            String field = invalidLockField(client);
            if (field != null) {
                requestPendingField("lock", field);
                if (field.equals("components")) exactComponentsEditor.requestFocus();
                if (field.equals("predicates")) partialPredicatesEditor.requestFocus();
                return;
            }
        }
        if (loot.present()) {
            if (!identifierProblem(lootTable.get(), true).isBlank()) {
                requestPendingField("loot_table", "loot_table");
                return;
            }
            if (!seedProblem(lootSeed.get()).isBlank()) {
                requestPendingField("loot_table", "seed");
                return;
            }
            if (!loot.value().isJsonObject()) {
                requestPendingField("loot_table", "$enabled");
                return;
            }
            if (client.level != null) {
                try {
                    ItemStackCodecs.decodeAndSetComponent(client, probe.copy(), DataComponents.CONTAINER_LOOT, pendingLootValue());
                } catch (RuntimeException ignored) {
                    requestPendingField("loot_table", "$enabled");
                    return;
                }
            }
        }
        // Nested stack invariants are checked after both metadata builders, just as in preflight.
        if (contents.present()) {
            for (int index = 0; index <= contents.highestOccupiedSlot(); index++) {
                if (invalidStoredItem(contents.item(index))) {
                    recoveryContentsSlot = index;
                    requestPendingField("contents", "slot/" + index);
                    return;
                }
            }
        }
        if (bundleContents.present()) {
            for (int index = 0; index < bundleContents.size(); index++) {
                if (invalidStoredItem(bundleContents.item(index))) {
                    requestBundleRecovery(index);
                    return;
                }
            }
        }
        requestPendingField(contents.present() ? "contents" : bundleContents.present() ? "bundle_contents"
            : lock.present() ? "lock" : "loot_table", "$enabled");
    }

    private String invalidLockField(Minecraft client) {
        if (!lock.value().isJsonObject()) return "$enabled";
        JsonObject value = lock.value().getAsJsonObject();
        if (value.has("items")) {
            JsonElement items = value.get("items");
            int size = items.isJsonArray() ? items.getAsJsonArray().size() : 1;
            for (int index = 0; index < Math.max(1, size); index++) {
                JsonElement entry = items.isJsonArray() ? (index < size ? items.getAsJsonArray().get(index) : null) : items;
                String id = pendingBufferText("container/lock/items/@identifier/" + index, string(entry, "")).trim();
                if (id.isBlank() || (items.isJsonArray() && id.startsWith("#"))
                    || !itemSelectionProblem(new com.google.gson.JsonPrimitive(id)).isBlank()) {
                    lockItemsFocus.requestRow(index);
                    return "items";
                }
            }
        }
        if (value.has("count")) {
            JsonElement count = value.get("count");
            if (count.isJsonObject()) {
                JsonObject bounds = count.getAsJsonObject();
                for (String key : List.of("min", "max"))
                    if (invalidCodecField(bounds, key, Codec.INT)) return "count/" + key;
                if (!countRangeProblem(bounds).isBlank()) return "count/min";
            } else if (invalidCodecValue(Codec.INT, count)) return "count";
        }
        if (value.has("components") && !snbtCompoundProblem(exactComponentsSource.get()).isBlank()) return "components";
        if (value.has("predicates") && !snbtCompoundProblem(partialPredicatesSource.get()).isBlank())
            return "predicates";
        if (client != null && client.level != null) {
            try {
                CompoundTag encoded = pendingLockValue();
                Tag components = encoded.remove("components");
                Tag predicates = encoded.remove("predicates");
                if (invalidLockValue(client, encoded)) {
                    if (value.has("items")) {
                        CompoundTag withoutItems = encoded.copy();
                        withoutItems.remove("items");
                        if (!invalidLockValue(client, withoutItems)) {
                            lockItemsFocus.requestRow(0);
                            return "items";
                        }
                    }
                    return "$enabled";
                }
                if (components != null) {
                    encoded.put("components", components);
                    if (invalidLockValue(client, encoded)) return "components";
                }
                if (predicates != null) {
                    encoded.put("predicates", predicates);
                    if (invalidLockValue(client, encoded)) return "predicates";
                }
            } catch (RuntimeException ignored) {
                return "$enabled";
            }
        }
        return null;
    }

    private boolean invalidLockValue(Minecraft client, CompoundTag value) {
        try {
            ItemStackCodecs.decodeAndSetComponentNbt(client, host.draft().copy(), DataComponents.LOCK, value);
            return false;
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private static boolean invalidStoredItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        try {
            ItemStackCodecs.validateStack(stack);
            return false;
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private void requestBundleRecovery(int index) {
        while (bundleRowIds.size() < bundleContents.size()) bundleRowIds.add(NEXT_BUNDLE_ROW_ID.incrementAndGet());
        recoveryBundleRowId = bundleRowIds.get(index);
        requestPendingField("bundle_contents", "row/" + recoveryBundleRowId);
    }

    private void clearContainerRecoveryRequests() {
        recoveryContentsSlot = -1;
        recoveryBundleRowId = 0L;
        contentsSlotFocusRequest = -1;
        bundleSlotFocusRequest = -1;
        focusBundleAddButton = false;
        lockItemsFocus.clear();
        exactComponentsEditor.cancelFocusRequest();
        partialPredicatesEditor.cancelFocusRequest();
    }

    public static boolean supports(DataComponentType<?> type) {
        return type == DataComponents.CONTAINER
            || type == DataComponents.BUNDLE_CONTENTS
            || type == DataComponents.LOCK
            || type == DataComponents.CONTAINER_LOOT;
    }

    public boolean isAvailable(ItemStack stack) {
        return stack.has(DataComponents.LOCK)
            || stack.has(DataComponents.CONTAINER_LOOT)
            || stack.has(DataComponents.CONTAINER)
            || stack.has(DataComponents.BUNDLE_CONTENTS)
            || ContainerPresentationProfile.hasKnownOwner(stack)
            || containerBlockItems.computeIfAbsent(stack.getItem(), ContainerEditorPanel::supportsContainerMetadata);
    }

    private static boolean supportsContainerMetadata(Item item) {
        if (!(item instanceof BlockItem blockItem)
            || !(blockItem.getBlock() instanceof EntityBlock entityBlock)) {
            return false;
        }
        try {
            BlockEntity blockEntity = entityBlock.newBlockEntity(
                BlockPos.ZERO,
                blockItem.getBlock().defaultBlockState()
            );
            return blockEntity instanceof Container || blockEntity instanceof BaseContainerBlockEntity;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public void setCatalog(EditorCatalog catalog) {
        itemSelectors = catalog.registryIdsAndTags("item");
        itemIds = catalog.itemIds();
        knownItemSelectors = itemSelectors.stream().map(IdSuggestion::id).collect(java.util.stream.Collectors.toSet());

        Map<String, IdSuggestion> availableLootTables = new LinkedHashMap<>();
        availableLootTables.put("minecraft:empty", IdSuggestion.of("minecraft:empty"));
        for (IdSuggestion suggestion : catalog.registryIds("loot_table")) {
            availableLootTables.putIfAbsent(suggestion.id(), suggestion);
        }
        lootTables = List.copyOf(availableLootTables.values());
    }

    public void sync(Minecraft client, ItemStack stack) {
        syncContents(client, stack);
        syncBundleContents(stack);
        syncLock(client, stack);
        syncLoot(client, stack);
        resetEditorUi();
        clearContainerRecoveryRequests();
        dirty = false;
    }

    public void syncComponent(Minecraft client, DataComponentType<?> type, ItemStack stack) {
        if (type == DataComponents.CONTAINER) syncContents(client, stack);
        else if (type == DataComponents.BUNDLE_CONTENTS) syncBundleContents(stack);
        else if (type == DataComponents.LOCK) syncLock(client, stack);
        else if (type == DataComponents.CONTAINER_LOOT) syncLoot(client, stack);
        clearPendingFocus();
        if (type == DataComponents.LOCK) textBuffers.keySet().removeIf(key -> key.startsWith("container/lock/"));
        host.resetAutocomplete();
        clearContainerRecoveryRequests();
        try {
            dirty = hasPendingChanges();
        } catch (RuntimeException ignored) {
            dirty = true;
        }
    }

    public void beginItemSession() {
        clearPendingFocus();
        clearContainerRecoveryRequests();
        selectedContentsSlot = 0;
        selectedBundleItem = 0;
        contentsSlotFocusRequest = -1;
        bundleSlotFocusRequest = -1;
        focusBundleAddButton = false;
        goToContentsSlot.set(1);
    }

    private void syncLock(Minecraft client, ItemStack stack) {
        lock.sync(client, stack);
        lock.object();
        Object installed = stack.get(DataComponents.LOCK);
        Tag encodedNbt = installed == null
            ? null
            : ItemStackCodecs.encodeComponentNbt(client, DataComponents.LOCK, installed);
        CompoundTag encoded = encodedNbt instanceof CompoundTag compound ? compound : null;
        exactComponentsSource.set(snbtSource(encoded, "components"));
        partialPredicatesSource.set(snbtSource(encoded, "predicates"));
        exactComponentsEditor.resetCursor();
        partialPredicatesEditor.resetCursor();
    }

    private void syncContents(Minecraft client, ItemStack stack) {
        contents = ContainerContentsDraft.fromStack(stack, ItemStackCodecs.jsonOps(client));
        selectedContentsSlot = Math.clamp(selectedContentsSlot, 0, ContainerContentsDraft.MAX_SLOTS - 1);
        ContainerPresentationProfile profile = ContainerPresentationProfile.resolve(
            stack,
            contents.highestOccupiedSlot()
        );
        indexedMinimumCapacity = Math.max(
            profile.nativeCapacity(),
            contents.highestOccupiedSlot() + 1
        );
        goToContentsSlot.set(selectedContentsSlot + 1);
        clearSourceSelection();
        activeSourceDrag = null;
        activeContentsDrag = null;
        activeBundleDrag = null;
        contentsSlotFocusRequest = -1;
        pendingSlotCopy = null;
        syncContainedItemFields();
    }

    private void syncBundleContents(ItemStack stack) {
        bundleContents = BundleContentsDraft.fromStack(stack);
        bundleRowIds.clear();
        for (int index = 0; index < bundleContents.size(); index++) {
            bundleRowIds.add(NEXT_BUNDLE_ROW_ID.incrementAndGet());
        }
        bundleDragScope = OrderedListDragDrop.newScope("bundle_contents");
        selectedBundleItem = clampBundleItemIndex(selectedBundleItem);
        activeBundleDrag = null;
        bundleSlotFocusRequest = -1;
        focusBundleAddButton = false;
        syncBundleItemFields();
    }

    private void syncLoot(Minecraft client, ItemStack stack) {
        loot.sync(client, stack);
        JsonObject value = loot.object();
        lootTable.set(string(value.get("loot_table"), "minecraft:empty"));
        lootSeed.set(longString(value.get("seed"), "0"));
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
            () -> sync(client, host.draft())
        )) return;

        EditorUi.beginScrollableChild(
            "container_editor",
            0.0f,
            Math.max(1.0f, ImGui.getContentRegionAvailY()),
            false
        );
        try {
            ItemStack preview = previewStack(client);
            host.setItemIconPreview(preview);
            host.drawTooltipPreview(client, preview, "container");
            ImGui.spacing();
            drawMetadata(componentType);
        } finally {
            ImGui.endChild();
        }
    }

    private void drawMetadata(DataComponentType<?> componentType) {
        drawWarnings(componentType);
        if (componentType == null || componentType == DataComponents.CONTAINER) drawContents();
        if (componentType == null || componentType == DataComponents.BUNDLE_CONTENTS) drawBundleContents();
        if (componentType == null || componentType == DataComponents.LOCK) drawLock();
        if (componentType == null || componentType == DataComponents.CONTAINER_LOOT) {
            ImGui.spacing();
            drawLootTable();
        }
    }

    private void drawWarnings(DataComponentType<?> componentType) {
        boolean showContents = componentType == null || componentType == DataComponents.CONTAINER;
        boolean showBundle = componentType == null || componentType == DataComponents.BUNDLE_CONTENTS;
        boolean showLock = componentType == null || componentType == DataComponents.LOCK;
        boolean showLoot = componentType == null || componentType == DataComponents.CONTAINER_LOOT;
        List<String> warnings = new ArrayList<>();

        ItemStack owner = host.draft();
        String ownerId = itemId(owner);
        ContainerPresentationProfile profile = ContainerPresentationProfile.resolve(
            owner,
            contents.highestOccupiedSlot()
        );

        if (contents.present() && showContents && profile.generic()) {
            warnings.add(tr("container.contents.generic_warning", ownerId));
        }
        if (contents.present() && showContents && !profile.generic()) {
            int overflowCount = 0;
            int firstOverflow = -1;
            for (int index = profile.nativeCapacity(); index <= contents.highestOccupiedSlot(); index++) {
                if (contents.item(index).isEmpty()) continue;
                if (firstOverflow < 0) firstOverflow = index;
                overflowCount++;
            }
            if (overflowCount > 0) {
                warnings.add(overflowCount == 1
                    ? tr(
                    "container.contents.capacity_warning.one",
                    profile.nativeCapacity(),
                    firstOverflow + 1
                )
                    : tr(
                    "container.contents.capacity_warning.many",
                    profile.nativeCapacity(),
                    overflowCount,
                    firstOverflow + 1
                ));
            }
        }
        if (contents.present() && bundleContents.present() && (showContents || showBundle)) {
            warnings.add(tr("container.contents.bundle_warning"));
        }
        if (contents.present() && loot.present() && (showContents || showLoot)) {
            warnings.add(tr("container.contents.loot_warning"));
        }
        if (bundleContents.present() && showBundle && !isBundleOwner(ownerId)) {
            warnings.add(tr("bundle.contents.owner_warning", ownerId));
        }

        boolean hasShownMetadata = showLock && lock.present() || showLoot && loot.present();
        if (hasShownMetadata && !isRecognizedContainerOwner(owner)) {
            warnings.add(tr("container.metadata.owner_warning", ownerId));
        }

        if (warnings.isEmpty()) return;
        for (String warning : warnings) ItemEditorTheme.drawFieldWarning(warning);
        ImGui.spacing();
    }

    private boolean isRecognizedContainerOwner(ItemStack owner) {
        return ContainerPresentationProfile.hasKnownOwner(owner)
            || containerBlockItems.computeIfAbsent(owner.getItem(), ContainerEditorPanel::supportsContainerMetadata);
    }

    private static boolean isBundleOwner(String itemId) {
        return itemId.equals("minecraft:bundle") || itemId.endsWith("_bundle");
    }

    private static String itemId(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.toString();
    }

    private void drawContents() {
        boolean reveal = hasPendingSectionFocus("contents");
        if (!EditorUi.beginCard("container_contents", 1, ImGuiTableFlags.SizingStretchProp, reveal)) return;
        ImGui.tableNextColumn();
        ImBoolean enabled = new ImBoolean(contents.present());
        applyPendingFieldFocus("contents", "$enabled");
        if (ImGui.checkbox("##enabled_contents", enabled)) {
            contents.setPresent(enabled.get());
            dirty = true;
        }
        ImGui.sameLine();
        boolean expanded = EditorUi.collapsibleHeader(
            "container_section_contents",
            tr("panel.container.section.contents.title"),
            host.componentSectionStartsExpanded(DataComponents.CONTAINER),
            reveal
        );
        host.drawDiamondFireComponentHint(DataComponents.CONTAINER);
        if (!expanded) {
            EditorUi.endCard();
            return;
        }
        ImGui.beginDisabled(!contents.present());
        try {
            ImGui.spacing();
            ContainerPresentationProfile nativeProfile = ContainerPresentationProfile.resolve(
                host.draft(),
                contents.highestOccupiedSlot()
            );
            ImGui.text(tr("container.contents.layout", nativeProfile.name(), nativeProfile.nativeCapacity()));
            if (viewButton(tr("container.contents.native_layout"), !indexedView)) indexedView = false;
            EditorUi.sameLineIfFits(tr("container.contents.indexed_slots"));
            if (viewButton(tr("container.contents.indexed_slots"), indexedView)) indexedView = true;
            EditorUi.sameLineIfFits(72.0f);
            ImGui.setNextItemWidth(72.0f);
            if (ImGui.inputInt("##container_contents_go_to_slot", goToContentsSlot, 0)) {
                goToContentsSlot.set(Math.clamp(
                    goToContentsSlot.get(),
                    1,
                    ContainerContentsDraft.MAX_SLOTS
                ));
            }
            EditorUi.sameLineIfFits(tr("container.contents.go_to_slot"));
            if (ImGui.button(tr("container.contents.go_to_slot"))) {
                int target = Math.clamp(
                    goToContentsSlot.get() - 1,
                    0,
                    ContainerContentsDraft.MAX_SLOTS - 1
                );
                indexedMinimumCapacity = Math.max(indexedMinimumCapacity, target + 1);
                indexedView = true;
                selectContentsSlot(target);
                contentsSlotFocusRequest = target;
            }
            ImGui.spacing();

            ContainerPresentationProfile displayedProfile = indexedView
                ? ContainerPresentationProfile.indexed(indexedCapacity(nativeProfile))
                : nativeProfile;
            drawContentsProfile(displayedProfile, "contents_main", false);

            List<Integer> overflow = indexedView
                ? List.of()
                : nativeProfile.overflowIndices(contents.highestOccupiedSlot());
            if (!overflow.isEmpty()) {
                ImGui.spacing();
                ImGui.textWrapped(tr("container.contents.overflow", overflow.getFirst() + 1));
                drawLinearContentsSlots(overflow, "contents_overflow", true);
            }
            EditorUi.textDisabledWrapped(tr("container.contents.interaction_help"));
            EditorUi.textDisabledWrapped(tr("container.contents.keyboard_help"));

            ImGui.spacing();
            drawSelectedContentsInspector(nativeProfile);
            ImGui.spacing();
            drawSourceInventory(SourceTarget.CONTAINER);
            drawReplaceSlotConfirmation();
        } finally {
            ImGui.endDisabled();
            EditorUi.endCard();
        }
    }

    private void drawBundleContents() {
        boolean reveal = hasPendingSectionFocus("bundle_contents");
        if (!EditorUi.beginCard("bundle_contents", 1, ImGuiTableFlags.SizingStretchProp, reveal)) return;
        ImGui.tableNextColumn();
        ImBoolean enabled = new ImBoolean(bundleContents.present());
        applyPendingFieldFocus("bundle_contents", "$enabled");
        if (ImGui.checkbox("##enabled_bundle_contents", enabled)) {
            bundleContents.setPresent(enabled.get());
            dirty = true;
        }
        ImGui.sameLine();
        boolean expanded = EditorUi.collapsibleHeader(
            "container_section_bundle_contents",
            tr("panel.container.section.bundle_contents.title"),
            host.componentSectionStartsExpanded(DataComponents.BUNDLE_CONTENTS),
            reveal
        );
        host.drawDiamondFireComponentHint(DataComponents.BUNDLE_CONTENTS);
        if (!expanded) {
            EditorUi.endCard();
            return;
        }
        ImGui.beginDisabled(!bundleContents.present());
        try {
            ImGui.spacing();
            drawBundleWeight();
            ImGui.spacing();
            drawBundleItems();
            ImGui.spacing();
            drawSelectedBundleInspector();
            ImGui.spacing();
            drawSourceInventory(SourceTarget.BUNDLE);
        } finally {
            ImGui.endDisabled();
            EditorUi.endCard();
        }
    }

    private void drawBundleWeight() {
        try {
            Fraction weight = bundleContents.totalWeight();
            float fraction = weight.floatValue();
            String label = tr(
                "bundle.contents.weight",
                Math.round(fraction * 100.0f),
                weight.getNumerator(),
                weight.getDenominator()
            );
            ImGui.progressBar(Math.clamp(fraction, 0.0f, 1.0f), -1.0f, 0.0f, label);
        } catch (RuntimeException exception) {
            ItemEditorTheme.drawFieldProblem(tr(
                "bundle.contents.weight_error",
                EditorUi.readableMessage(exception)
            ));
        }

        int disallowed = bundleContents.firstDisallowedItem();
        if (disallowed >= 0) {
            ItemEditorTheme.drawFieldProblem(tr("bundle.contents.disallowed", disallowed + 1));
        }
    }

    private void drawBundleItems() {
        float slotSize = host.itemSlotPreviewSize(Minecraft.getInstance());
        int columns = Math.max(1, (int) (ImGui.getContentRegionAvailX() / slotSize));
        int destinationSlots = bundleContents.size() + 1;
        int rows = (destinationSlots + columns - 1) / columns;
        float originX = ImGui.getCursorPosX();
        float originY = ImGui.getCursorPosY();
        ImGui.dummy(Math.max(slotSize, columns * slotSize), Math.max(slotSize, rows * slotSize));
        float endY = ImGui.getCursorPosY();

        BundleSlotCommand requestedCommand = null;
        OrderedListDragDrop.ReorderRequest reorderRequest = null;
        for (int index = 0; index < bundleContents.size(); index++) {
            ImGui.pushID("bundle_entry");
            ImGui.pushID(index);
            try {
                ImGui.setCursorPos(
                    originX + (index % columns) * slotSize,
                    originY + ((float) index / columns) * slotSize
                );
                ItemStack stack = bundleContents.item(index);
                long rowId = bundleRowIds.get(index);
                applyPendingFieldFocus("bundle_contents", "row/" + rowId);
                boolean requestingFocus = bundleSlotFocusRequest == index;
                if (requestingFocus) ImGui.setKeyboardFocusHere();
                NativeItemPreviews.ItemSlotInteraction interaction = host.drawItemSlotPreview(
                    Minecraft.getInstance(),
                    "bundle_item",
                    stack,
                    selectedBundleItem == index,
                    false
                );
                if (interaction.clicked()) {
                    bundleSlotFocusRequest = -1;
                    selectBundleItem(index);
                } else if (interaction.focused() && allowBundleFocusSelection(rowId) && (bundleSlotFocusRequest < 0 || requestingFocus)) {
                    bundleSlotFocusRequest = -1;
                    selectBundleItem(index);
                }
                if (interaction.doubleClicked() || interaction.keyboardActivated()) {
                    requestedCommand = BundleSlotCommand.edit(index);
                }
                if (interaction.deletePressed()) requestedCommand = BundleSlotCommand.remove(index);
                if (ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceNoPreviewTooltip)) {
                    try {
                        activeBundleDrag = new OrderedListDragDrop.Payload(bundleDragScope, rowId);
                        ImGui.setDragDropPayload(BUNDLE_ITEM_PAYLOAD, activeBundleDrag);
                        host.markLastDrawnItemSlotAsDragSource();
                        host.drawDraggedItemPreview(Minecraft.getInstance(), stack);
                    } finally {
                        ImGui.endDragDropSource();
                    }
                }
                OrderedListDragDrop.Payload activeBundle = ImGui.getDragDropPayload(BUNDLE_ITEM_PAYLOAD);
                boolean matchingBundleDrag = OrderedListDragDrop.accepts(bundleDragScope, activeBundle);
                boolean sourceStackDrag = ImGui.getDragDropPayload(SOURCE_STACK_PAYLOAD) != null;
                if (matchingBundleDrag || sourceStackDrag) {
                    host.markLastDrawnItemSlotAsInsertionGuide();
                }
                if (matchingBundleDrag && activeBundle.sourceRowId() == rowId) {
                    host.markLastDrawnItemSlotAsDragSource();
                }
                if (ImGui.beginDragDropTarget()) {
                    try {
                        int destinationBoundary = ImGui.getMousePosX()
                            < (ImGui.getItemRectMinX() + ImGui.getItemRectMaxX()) * 0.5f
                            ? index
                            : index + 1;
                        if (matchingBundleDrag || sourceStackDrag) {
                            host.markLastDrawnItemSlotAsInsertionTarget(destinationBoundary > index);
                        }
                        OrderedListDragDrop.Payload bundleItem = ImGui.acceptDragDropPayload(
                            BUNDLE_ITEM_PAYLOAD,
                            ImGuiDragDropFlags.AcceptNoDrawDefaultRect,
                            OrderedListDragDrop.Payload.class
                        );
                        SourceInventorySnapshot.SourceSlot source = ImGui.acceptDragDropPayload(
                            SOURCE_STACK_PAYLOAD,
                            ImGuiDragDropFlags.AcceptNoDrawDefaultRect,
                            SourceInventorySnapshot.SourceSlot.class
                        );
                        if (OrderedListDragDrop.accepts(bundleDragScope, bundleItem)) {
                            reorderRequest = new OrderedListDragDrop.ReorderRequest(
                                bundleDragScope,
                                bundleItem.sourceRowId(),
                                destinationBoundary
                            );
                        } else if (source != null) {
                            requestedCommand = BundleSlotCommand.insert(destinationBoundary, source.stack());
                        }
                    } finally {
                        ImGui.endDragDropTarget();
                    }
                }
                BundleSlotAction contextAction = drawBundleSlotContext(index);
                if (contextAction != BundleSlotAction.NONE) {
                    requestedCommand = new BundleSlotCommand(contextAction, index, index, ItemStack.EMPTY);
                }
            } finally {
                ImGui.popID();
                ImGui.popID();
            }
        }
        int appendIndex = bundleContents.size();
        ImGui.setCursorPos(
            originX + (appendIndex % columns) * slotSize,
            originY + ((float) appendIndex / columns) * slotSize
        );
        BundleDropResult appendResult = drawBundleAppendTarget(appendIndex);
        if (appendResult.command() != null) requestedCommand = appendResult.command();
        if (appendResult.reorderRequest() != null) reorderRequest = appendResult.reorderRequest();
        ImGui.setCursorPos(originX, endY);
        if (reorderRequest != null) applyBundleReorder(reorderRequest);
        else applyBundleSlotCommand(requestedCommand);
        EditorUi.textDisabledWrapped(tr("bundle.contents.order_help"));
        EditorUi.textDisabledWrapped(tr("bundle.contents.interaction_help"));
        EditorUi.textDisabledWrapped(tr("bundle.contents.keyboard_help"));
    }

    private BundleDropResult drawBundleAppendTarget(int destination) {
        ImGui.pushID("bundle_append_target");
        try {
            NativeItemPreviews.ItemSlotInteraction interaction = host.drawItemSlotPreview(
                Minecraft.getInstance(),
                "source_drop_target",
                ItemStack.EMPTY,
                false,
                false
            );
            if ((interaction.clicked() || interaction.focused()) && hasSelectedBundleItem()) {
                clearBundleItemSelection();
            }
            BundleSlotCommand requestedCommand = null;
            OrderedListDragDrop.ReorderRequest reorderRequest = null;
            OrderedListDragDrop.Payload activeBundle = ImGui.getDragDropPayload(BUNDLE_ITEM_PAYLOAD);
            boolean matchingBundleDrag = OrderedListDragDrop.accepts(bundleDragScope, activeBundle);
            boolean sourceStackDrag = ImGui.getDragDropPayload(SOURCE_STACK_PAYLOAD) != null;
            if (matchingBundleDrag || sourceStackDrag) {
                host.markLastDrawnItemSlotAsInsertionGuide();
            }
            if (ImGui.beginDragDropTarget()) {
                try {
                    if (matchingBundleDrag || sourceStackDrag) {
                        host.markLastDrawnItemSlotAsInsertionTarget(false);
                    }
                    OrderedListDragDrop.Payload bundleItem = ImGui.acceptDragDropPayload(
                        BUNDLE_ITEM_PAYLOAD,
                        ImGuiDragDropFlags.AcceptNoDrawDefaultRect,
                        OrderedListDragDrop.Payload.class
                    );
                    SourceInventorySnapshot.SourceSlot source = ImGui.acceptDragDropPayload(
                        SOURCE_STACK_PAYLOAD,
                        ImGuiDragDropFlags.AcceptNoDrawDefaultRect,
                        SourceInventorySnapshot.SourceSlot.class
                    );
                    if (OrderedListDragDrop.accepts(bundleDragScope, bundleItem)) {
                        reorderRequest = new OrderedListDragDrop.ReorderRequest(
                            bundleDragScope,
                            bundleItem.sourceRowId(),
                            destination
                        );
                    } else if (source != null) {
                        requestedCommand = BundleSlotCommand.insert(destination, source.stack());
                    }
                } finally {
                    ImGui.endDragDropTarget();
                }
            }
            BundleSlotCommand contextCommand = drawBundleAppendContext(destination);
            if (contextCommand != null) requestedCommand = contextCommand;
            return new BundleDropResult(requestedCommand, reorderRequest);
        } finally {
            ImGui.popID();
        }
    }

    private BundleSlotCommand drawBundleAppendContext(int destination) {
        if (!ImGui.beginPopupContextItem("##bundle_append_context")) return null;
        try {
            ImGui.textDisabled(tr("bundle.contents.new_entry"));
            ImGui.separator();
            if (ImGui.menuItem(tr("bundle.contents.add"))) {
                return BundleSlotCommand.insert(destination, new ItemStack(Items.STONE));
            }

            ImGui.beginDisabled();
            try {
                ImGui.menuItem(tr("bundle.contents.edit_item"));
                ImGui.menuItem(tr("action.duplicate"));
                ImGui.menuItem(tr("bundle.contents.move_earlier"));
                ImGui.menuItem(tr("bundle.contents.move_later"));
                ImGui.separator();
                ImGui.menuItem(tr("bundle.contents.remove"));
            } finally {
                ImGui.endDisabled();
            }
            return null;
        } finally {
            ImGui.endPopup();
        }
    }

    private BundleSlotAction drawBundleSlotContext(int index) {
        if (!ImGui.beginPopupContextItem("##bundle_slot_context")) return BundleSlotAction.NONE;
        try {
            selectBundleItem(index);
            ImGui.textDisabled(tr("bundle.contents.selected_entry", index + 1));
            ImGui.separator();
            if (ImGui.menuItem(tr("bundle.contents.edit_item"))) return BundleSlotAction.EDIT;
            if (ImGui.menuItem(tr("action.duplicate"))) return BundleSlotAction.DUPLICATE;

            ImGui.beginDisabled(index <= 0);
            boolean moveEarlier = ImGui.menuItem(tr("bundle.contents.move_earlier"));
            ImGui.endDisabled();
            if (moveEarlier) return BundleSlotAction.MOVE_EARLIER;

            ImGui.beginDisabled(index + 1 >= bundleContents.size());
            boolean moveLater = ImGui.menuItem(tr("bundle.contents.move_later"));
            ImGui.endDisabled();
            if (moveLater) return BundleSlotAction.MOVE_LATER;

            ImGui.separator();
            if (ImGui.menuItem(tr("bundle.contents.remove"))) return BundleSlotAction.REMOVE;
            return BundleSlotAction.NONE;
        } finally {
            ImGui.endPopup();
        }
    }

    private void applyBundleSlotCommand(BundleSlotCommand command) {
        if (command == null) return;
        int source = command.source();
        switch (command.action()) {
            case EDIT -> host.requestBundleItemEdit(source, bundleContents.item(source));
            case DUPLICATE -> insertBundleItem(source + 1, bundleContents.item(source));
            case REMOVE -> removeBundleItem(source);
            case MOVE_EARLIER -> applyBundleReorder(bundleReorderRequest(source, source - 1));
            case MOVE_LATER -> applyBundleReorder(bundleReorderRequest(source, source + 2));
            case INSERT -> insertBundleItem(command.destination(), command.stack());
            case REPLACE -> replaceBundleItem(source, command.stack());
            case NONE -> {
            }
        }
    }

    private OrderedListDragDrop.ReorderRequest bundleReorderRequest(
        int source,
        int destinationBoundary
    ) {
        if (source < 0 || source >= bundleRowIds.size()) return null;
        return new OrderedListDragDrop.ReorderRequest(
            bundleDragScope,
            bundleRowIds.get(source),
            destinationBoundary
        );
    }

    private void applyBundleReorder(OrderedListDragDrop.ReorderRequest request) {
        if (request == null || !bundleDragScope.equals(request.scope())) return;
        int source = bundleRowIds.indexOf(request.sourceRowId());
        EditorListMutations.Mutation<Long> mutation = EditorListMutations.reorder(
            bundleRowIds,
            request,
            Long::longValue
        );
        if (!mutation.changed()) return;

        int destination = bundleRowIds.indexOf(mutation.focusRow());
        bundleContents.moveItem(source, destination);
        selectBundleItem(destination);
        bundleSlotFocusRequest = destination;
        dirty = true;
    }

    private void removeBundleItem(int index) {
        if (index < 0 || index >= bundleContents.size()) return;
        bundleContents.removeItem(index);
        bundleRowIds.remove(index);
        selectedBundleItem = clampBundleItemIndex(index);
        syncBundleItemFields();
        bundleSlotFocusRequest = bundleContents.isEmpty() ? -1 : selectedBundleItem;
        focusBundleAddButton = bundleContents.isEmpty();
        dirty = true;
    }

    private void replaceBundleItem(int index, ItemStack stack) {
        if (index < 0 || index >= bundleContents.size() || stack == null || stack.isEmpty()) return;
        bundleContents.setItem(index, stack);
        selectBundleItem(index);
        bundleSlotFocusRequest = index;
        dirty = true;
    }

    private void drawSelectedBundleInspector() {
        boolean hasItem = hasSelectedBundleItem();
        ImGui.text(hasItem
            ? tr("bundle.contents.selected_entry", selectedBundleItem + 1)
            : tr("bundle.contents.new_entry"));

        if (beginFields("bundle_contents_inspector")) {
            fieldLabel(tr("bundle.contents.item"), tr("bundle.contents.item.help"));
            String problem = bundleItemProblem();
            if (!problem.isBlank()) ItemEditorTheme.pushInvalidField();
            applyPendingFieldFocus("bundle_contents", "item_id");
            if (host.inputIdentifierWithSuggestions(
                "bundle_contents_item",
                new FieldHints(tr("field_purpose.item_identifier"), "minecraft:stone"),
                bundleItemId,
                itemIds,
                problem
            )) {
                bundleItemIdChanged();
            }
            if (!problem.isBlank()) ItemEditorTheme.popInvalidField();
            ItemEditorTheme.drawFieldProblem(problem);

            fieldLabel(tr("bundle.contents.count"), tr("bundle.contents.count.help"));
            ImGui.beginDisabled(!hasItem);
            ImGui.setNextItemWidth(-1.0f);
            if (ImGui.inputInt("##bundle_contents_count", bundleItemCount, 1, 8)) {
                ItemStack selected = bundleContents.item(selectedBundleItem);
                bundleItemCount.set(Math.clamp(bundleItemCount.get(), 1, MAX_ITEM_COUNT));
                bundleContents.setItem(selectedBundleItem, selected.copyWithCount(bundleItemCount.get()));
                dirty = true;
            }
            ImGui.endDisabled();
            ImGui.endTable();
        }

        if (focusBundleAddButton) {
            ImGui.setKeyboardFocusHere();
            focusBundleAddButton = false;
        }
        if (ImGui.button(tr("bundle.contents.add"))) {
            applyBundleSlotCommand(BundleSlotCommand.insert(
                bundleContents.size(),
                new ItemStack(Items.STONE)
            ));
        }
        EditorUi.sameLineIfFits(tr("action.duplicate"));
        ImGui.beginDisabled(!hasItem);
        if (ImGui.button(tr("action.duplicate") + "##bundle_contents")) {
            applyBundleSlotCommand(new BundleSlotCommand(
                BundleSlotAction.DUPLICATE,
                selectedBundleItem,
                selectedBundleItem,
                ItemStack.EMPTY
            ));
        }
        EditorUi.sameLineIfFits(tr("bundle.contents.remove"));
        if (ImGui.button(tr("bundle.contents.remove"))) {
            applyBundleSlotCommand(BundleSlotCommand.remove(selectedBundleItem));
        }
        ImGui.endDisabled();

        ImGui.beginDisabled(!hasItem || selectedBundleItem == 0);
        if (ImGui.button(tr("bundle.contents.move_earlier"))) {
            applyBundleSlotCommand(new BundleSlotCommand(
                BundleSlotAction.MOVE_EARLIER,
                selectedBundleItem,
                selectedBundleItem - 1,
                ItemStack.EMPTY
            ));
        }
        ImGui.endDisabled();
        EditorUi.sameLineIfFits(tr("bundle.contents.move_later"));
        ImGui.beginDisabled(!hasItem || selectedBundleItem >= bundleContents.size() - 1);
        if (ImGui.button(tr("bundle.contents.move_later"))) {
            applyBundleSlotCommand(new BundleSlotCommand(
                BundleSlotAction.MOVE_LATER,
                selectedBundleItem,
                selectedBundleItem + 1,
                ItemStack.EMPTY
            ));
        }
        ImGui.endDisabled();
        EditorUi.sameLineIfFits(tr("bundle.contents.edit_item"));
        ImGui.beginDisabled(!hasItem);
        if (ImGui.button(tr("bundle.contents.edit_item"))) {
            applyBundleSlotCommand(BundleSlotCommand.edit(selectedBundleItem));
        }
        ImGui.endDisabled();
    }

    private void drawContentsProfile(
        ContainerPresentationProfile profile,
        String id,
        boolean overflow
    ) {
        float slotSize = host.itemSlotPreviewSize(Minecraft.getInstance());
        float originX = ImGui.getCursorPosX();
        float originY = ImGui.getCursorPosY();
        float canvasWidth = Math.max(slotSize, profile.width() * slotSize);
        float canvasHeight = Math.max(slotSize, profile.height() * slotSize);
        ImGui.dummy(canvasWidth, canvasHeight);
        float endY = ImGui.getCursorPosY();
        List<ContainerPresentationProfile.PresentedSlot> slots = profile.slots();
        for (int presentedIndex = 0; presentedIndex < slots.size(); presentedIndex++) {
            ContainerPresentationProfile.PresentedSlot slot = slots.get(presentedIndex);
            ImGui.pushID(id);
            ImGui.pushID(presentedIndex);
            try {
                ImGui.setCursorPos(originX + slot.x() * slotSize, originY + slot.y() * slotSize);
                drawContentsSlot(slot.index(), overflow, slot.role());
            } finally {
                ImGui.popID();
                ImGui.popID();
            }
        }
        ImGui.setCursorPos(originX, endY);
    }

    private void drawLinearContentsSlots(List<Integer> indices, String id, boolean overflow) {
        List<ContainerPresentationProfile.PresentedSlot> slots = new ArrayList<>();
        for (int local = 0; local < indices.size(); local++) {
            slots.add(new ContainerPresentationProfile.PresentedSlot(
                indices.get(local), local % 9, local / 9, ""
            ));
        }
        drawContentsProfile(new ContainerPresentationProfile(
            "Overflow",
            Math.clamp(indices.size(), 1, 9),
            Math.max(1, (indices.size() + 8) / 9),
            slots,
            indices.size(),
            true
        ), id, overflow);
    }

    private void drawContentsSlot(int index, boolean overflow, String role) {
        ItemStack stack = contents.item(index);
        ContentsSlotCommand requestedCommand = null;
        applyPendingFieldFocus("contents", "slot/" + index);
        boolean requestingFocus = contentsSlotFocusRequest == index;
        if (requestingFocus) ImGui.setKeyboardFocusHere();
        NativeItemPreviews.ItemSlotInteraction interaction = host.drawItemSlotPreview(
            Minecraft.getInstance(),
            "contents_slot",
            stack,
            selectedContentsSlot == index,
            overflow
        );
        if (interaction.clicked()) {
            contentsSlotFocusRequest = -1;
            selectContentsSlot(index);
        } else if (interaction.focused() && allowContentsFocusSelection(index) && (contentsSlotFocusRequest < 0 || requestingFocus)) {
            contentsSlotFocusRequest = -1;
            selectContentsSlot(index);
        }
        if ((interaction.doubleClicked() || interaction.keyboardActivated()) && !stack.isEmpty()) {
            requestedCommand = ContentsSlotCommand.edit(index);
        }
        if (interaction.deletePressed() && !stack.isEmpty()) {
            requestedCommand = ContentsSlotCommand.clear(index);
        }
        if (!stack.isEmpty() && ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceNoPreviewTooltip)) {
            try {
                activeContentsDrag = new ContentsSlotDrag(index);
                ImGui.setDragDropPayload(CONTENTS_SLOT_PAYLOAD, activeContentsDrag);
                host.drawDraggedItemPreview(Minecraft.getInstance(), stack);
            } finally {
                ImGui.endDragDropSource();
            }
        }
        if (ImGui.beginDragDropTarget()) {
            try {
                if (ImGui.getDragDropPayload(CONTENTS_SLOT_PAYLOAD) != null
                    || ImGui.getDragDropPayload(SOURCE_STACK_PAYLOAD) != null) {
                    host.markLastDrawnItemSlotAsDropTarget();
                }
                ContentsSlotDrag contentsSlot = ImGui.acceptDragDropPayload(
                    CONTENTS_SLOT_PAYLOAD,
                    ImGuiDragDropFlags.AcceptNoDrawDefaultRect,
                    ContentsSlotDrag.class
                );
                SourceInventorySnapshot.SourceSlot source = ImGui.acceptDragDropPayload(
                    SOURCE_STACK_PAYLOAD,
                    ImGuiDragDropFlags.AcceptNoDrawDefaultRect,
                    SourceInventorySnapshot.SourceSlot.class
                );
                if (contentsSlot != null) {
                    requestedCommand = ContentsSlotCommand.move(contentsSlot.index(), index);
                } else if (source != null) {
                    requestedCommand = ContentsSlotCommand.copy(source.stack(), index);
                }
            } finally {
                ImGui.endDragDropTarget();
            }
        }
        ContentsSlotCommand contextCommand = drawContentsSlotContext(index, role);
        if (contextCommand != null) requestedCommand = contextCommand;
        if (interaction.hovered() && stack.isEmpty()) {
            ImGui.setTooltip(role.isBlank()
                ? tr("container.contents.empty_slot", index + 1)
                : tr("container.contents.empty_named_slot", index + 1, role));
        }
        applyContentsSlotCommand(requestedCommand);
    }

    private ContentsSlotCommand drawContentsSlotContext(int index, String role) {
        if (!ImGui.beginPopupContextItem("##contents_slot_context")) return null;
        ContentsSlotCommand requestedCommand = null;
        try {
            selectContentsSlot(index);
            ImGui.textDisabled(role.isBlank()
                ? tr("container.contents.selected_slot", index + 1)
                : tr("container.contents.selected_named_slot", index + 1, role));
            ImGui.separator();

            boolean hasItem = !contents.item(index).isEmpty();
            ImGui.beginDisabled(hasItem);
            if (ImGui.menuItem(tr("container.contents.add_item"))) {
                requestedCommand = ContentsSlotCommand.add(index);
            }
            ImGui.endDisabled();

            ImGui.beginDisabled(!hasItem);
            if (ImGui.menuItem(tr("container.contents.edit_item"))) {
                requestedCommand = ContentsSlotCommand.edit(index);
            }

            int duplicateDestination = findDuplicateContentsDestination();
            ImGui.beginDisabled(duplicateDestination < 0);
            if (ImGui.menuItem(tr("action.duplicate"))) {
                requestedCommand = ContentsSlotCommand.duplicate(index, duplicateDestination);
            }
            ImGui.endDisabled();

            ImGui.beginDisabled(index <= 0);
            boolean moveLeft = ImGui.menuItem(tr("container.contents.move_left"));
            ImGui.endDisabled();
            if (moveLeft) requestedCommand = ContentsSlotCommand.move(index, index - 1);

            ImGui.beginDisabled(index >= ContainerContentsDraft.MAX_SLOTS - 1);
            boolean moveRight = ImGui.menuItem(tr("container.contents.move_right"));
            ImGui.endDisabled();
            if (moveRight) requestedCommand = ContentsSlotCommand.move(index, index + 1);

            ImGui.separator();
            if (ImGui.menuItem(tr("container.contents.clear"))) {
                requestedCommand = ContentsSlotCommand.clear(index);
            }
            ImGui.endDisabled();
        } finally {
            ImGui.endPopup();
        }
        return requestedCommand;
    }

    private void applyContentsSlotCommand(ContentsSlotCommand command) {
        if (command == null) return;
        switch (command.action()) {
            case EDIT -> {
                ItemStack stack = contents.item(command.source());
                if (!stack.isEmpty()) host.requestContainedItemEdit(command.source(), stack);
            }
            case ADD -> addContentsItem(command.destination());
            case DUPLICATE -> duplicateContentsSlot(command.source(), command.destination());
            case CLEAR -> clearContentsSlot(command.source());
            case MOVE -> moveContentsSlot(command.source(), command.destination());
            case COPY -> queueSlotCopy(command.stack(), command.destination());
            case REPLACE -> applySlotCopy(command.stack(), command.destination());
        }
    }

    private void moveContentsSlot(int from, int to) {
        if (from < 0 || from >= ContainerContentsDraft.MAX_SLOTS
            || to < 0 || to >= ContainerContentsDraft.MAX_SLOTS
            || from == to
            || contents.item(from).isEmpty()) return;
        contents.swap(from, to);
        indexedMinimumCapacity = Math.max(indexedMinimumCapacity, to + 1);
        selectContentsSlot(to);
        contentsSlotFocusRequest = to;
        dirty = true;
    }

    private void addContentsItem(int index) {
        if (!contents.item(index).isEmpty()) return;
        contents.setItem(index, new ItemStack(Items.STONE));
        indexedMinimumCapacity = Math.max(indexedMinimumCapacity, index + 1);
        selectContentsSlot(index);
        contentsSlotFocusRequest = index;
        dirty = true;
    }

    private void clearContentsSlot(int index) {
        if (index < 0 || index >= ContainerContentsDraft.MAX_SLOTS || contents.item(index).isEmpty()) return;
        contents.setItem(index, ItemStack.EMPTY);
        selectContentsSlot(index);
        contentsSlotFocusRequest = index;
        dirty = true;
    }

    private void drawSelectedContentsInspector(ContainerPresentationProfile profile) {
        String role = profile.slots().stream()
            .filter(slot -> slot.index() == selectedContentsSlot)
            .map(ContainerPresentationProfile.PresentedSlot::role)
            .filter(value -> !value.isBlank())
            .findFirst()
            .orElse("");
        ImGui.text(role.isBlank()
            ? tr("container.contents.selected_slot", selectedContentsSlot + 1)
            : tr("container.contents.selected_named_slot", selectedContentsSlot + 1, role));

        if (beginFields("container_contents_inspector")) {
            ItemStack selected = contents.item(selectedContentsSlot);
            fieldLabel(tr("container.contents.item"), tr("container.contents.item.help"));
            String problem = containedItemProblem();
            if (!problem.isBlank()) ItemEditorTheme.pushInvalidField();
            applyPendingFieldFocus("contents", "item_id");
            if (host.inputIdentifierWithSuggestions(
                "container_contents_item",
                new FieldHints(tr("field_purpose.item_identifier"), "minecraft:stone"),
                containedItemId,
                itemIds,
                problem
            )) {
                containedItemIdChanged();
            }
            if (!problem.isBlank()) ItemEditorTheme.popInvalidField();
            ItemEditorTheme.drawFieldProblem(problem);

            fieldLabel(tr("container.contents.count"), tr("container.contents.count.help"));
            ImGui.beginDisabled(selected.isEmpty());
            ImGui.setNextItemWidth(-1.0f);
            if (ImGui.inputInt("##container_contents_count", containedItemCount, 1, 8)) {
                containedItemCount.set(Math.clamp(containedItemCount.get(), 1, MAX_ITEM_COUNT));
                contents.setItem(selectedContentsSlot, selected.copyWithCount(containedItemCount.get()));
                dirty = true;
            }
            ImGui.endDisabled();
            ImGui.endTable();
        }

        boolean hasItem = !contents.item(selectedContentsSlot).isEmpty();
        ImGui.beginDisabled(!hasItem || selectedContentsSlot == 0);
        if (ImGui.button(tr("container.contents.move_left"))) {
            applyContentsSlotCommand(ContentsSlotCommand.move(
                selectedContentsSlot,
                selectedContentsSlot - 1
            ));
        }
        ImGui.endDisabled();
        EditorUi.sameLineIfFits(tr("container.contents.move_right"));
        ImGui.beginDisabled(!hasItem || selectedContentsSlot >= ContainerContentsDraft.MAX_SLOTS - 1);
        if (ImGui.button(tr("container.contents.move_right"))) {
            applyContentsSlotCommand(ContentsSlotCommand.move(
                selectedContentsSlot,
                selectedContentsSlot + 1
            ));
        }
        ImGui.endDisabled();
        EditorUi.sameLineIfFits(tr("action.duplicate"));
        int duplicateDestination = findDuplicateContentsDestination();
        ImGui.beginDisabled(!hasItem || duplicateDestination < 0);
        if (ImGui.button(tr("action.duplicate") + "##container_contents")) {
            applyContentsSlotCommand(ContentsSlotCommand.duplicate(
                selectedContentsSlot,
                duplicateDestination
            ));
        }
        EditorUi.sameLineIfFits(tr("container.contents.clear"));
        if (ImGui.button(tr("container.contents.clear"))) {
            applyContentsSlotCommand(ContentsSlotCommand.clear(selectedContentsSlot));
        }
        EditorUi.sameLineIfFits(tr("container.contents.edit_item"));
        if (ImGui.button(tr("container.contents.edit_item"))) {
            applyContentsSlotCommand(ContentsSlotCommand.edit(selectedContentsSlot));
        }
        ImGui.endDisabled();
    }

    private int findDuplicateContentsDestination() {
        return findFirstAvailableContentsSlot();
    }

    private int findFirstAvailableContentsSlot() {
        return contents.firstEmptySlot(Math.max(
            indexedMinimumCapacity,
            contents.highestOccupiedSlot() + 1
        ));
    }

    private void duplicateContentsSlot(int source, int destination) {
        ItemStack stack = contents.item(source);
        if (stack.isEmpty() || destination < 0 || destination >= ContainerContentsDraft.MAX_SLOTS) return;
        contents.setItem(destination, stack);
        indexedMinimumCapacity = Math.max(indexedMinimumCapacity, destination + 1);
        selectContentsSlot(destination);
        contentsSlotFocusRequest = destination;
        dirty = true;
    }

    private void drawSourceInventory(SourceTarget target) {
        SourceInventorySnapshot source = host.sourceInventory();
        if (!EditorUi.beginCollapsibleCard(
            target == SourceTarget.CONTAINER ? "container_copy_source" : "bundle_copy_source",
            tr("container.source.title"),
            ImGuiTableFlags.SizingStretchProp
        )) return;
        try {
            EditorUi.textDisabledWrapped(tr(target == SourceTarget.CONTAINER
                ? "container.source.help"
                : "bundle.source.help"));
            for (int groupIndex = 0; groupIndex < source.groups().size(); groupIndex++) {
                SourceInventorySnapshot.SourceGroup group = source.groups().get(groupIndex);
                ImGui.spacing();
                ImGui.text(group.label());
                drawSourceGroup(group, groupIndex, target);
            }
            if (!selectedSourceStack.isEmpty()) {
                ImGui.spacing();
                ImGui.textWrapped(tr(
                    "container.source.selected",
                    selectedSourceLabel,
                    selectedSourceStack.getHoverName().getString(),
                    selectedSourceStack.getCount()
                ));
            }
            ImGui.beginDisabled(selectedSourceStack.isEmpty());
            String copyStackLabel = target == SourceTarget.CONTAINER
                ? tr("container.source.copy_stack", selectedContentsSlot + 1)
                : tr("bundle.source.add_stack");
            if (ImGui.button(copyStackLabel)) {
                copySourceStack(target, selectedSourceStack, false);
            }
            String copyOneLabel = target == SourceTarget.CONTAINER
                ? tr("container.source.copy_one", selectedContentsSlot + 1)
                : tr("bundle.source.add_one");
            EditorUi.sameLineIfFits(copyOneLabel);
            if (ImGui.button(copyOneLabel)) {
                copySourceStack(target, selectedSourceStack, true);
            }
            ImGui.endDisabled();
            if (target == SourceTarget.CONTAINER) {
                int firstAvailable = findFirstAvailableContentsSlot();
                String destinationLabel = firstAvailable < 0 ? "-" : Integer.toString(firstAvailable + 1);
                ImGui.beginDisabled(selectedSourceStack.isEmpty() || firstAvailable < 0);
                if (ImGui.button(tr("container.source.copy_stack_first_empty", destinationLabel))) {
                    copySourceStackToFirstAvailable(selectedSourceStack, false);
                }
                String copyOneFirstEmptyLabel = tr(
                    "container.source.copy_one_first_empty",
                    destinationLabel
                );
                EditorUi.sameLineIfFits(copyOneFirstEmptyLabel);
                if (ImGui.button(copyOneFirstEmptyLabel)) {
                    copySourceStackToFirstAvailable(selectedSourceStack, true);
                }
                ImGui.endDisabled();
            }
            EditorUi.textDisabledWrapped(tr("container.source.interaction_help"));
            EditorUi.textDisabledWrapped(tr("container.source.keyboard_help"));
        } finally {
            EditorUi.endCard();
        }
    }

    private void drawSourceGroup(
        SourceInventorySnapshot.SourceGroup group,
        int groupIndex,
        SourceTarget target
    ) {
        List<SourceInventorySnapshot.SourceSlot> slots = group.slots();
        if (slots.stream().anyMatch(slot -> !slot.displayLabel().isBlank())) {
            drawLabeledSourceGroup(group, groupIndex, target);
            return;
        }
        float slotSize = host.itemSlotPreviewSize(Minecraft.getInstance());
        float scale = slotSize / 18.0f;
        float originX = ImGui.getCursorPosX();
        float originY = ImGui.getCursorPosY();
        ImGui.dummy(Math.max(slotSize, group.width() * scale), Math.max(slotSize, group.height() * scale));
        float endY = ImGui.getCursorPosY();
        for (int sourceIndex = 0; sourceIndex < slots.size(); sourceIndex++) {
            SourceInventorySnapshot.SourceSlot slot = slots.get(sourceIndex);
            ImGui.setCursorPos(originX + slot.x() * scale, originY + slot.y() * scale);
            drawSourceSlot(group, groupIndex, sourceIndex, target);
        }
        ImGui.setCursorPos(originX, endY);
    }

    private void drawLabeledSourceGroup(
        SourceInventorySnapshot.SourceGroup group,
        int groupIndex,
        SourceTarget target
    ) {
        if (!ImGui.beginTable(
            "source_labeled_group_" + target + "_" + groupIndex,
            2,
            ImGuiTableFlags.SizingFixedFit | ImGuiTableFlags.NoPadOuterX
        )) return;
        try {
            List<SourceInventorySnapshot.SourceSlot> slots = group.slots();
            float slotSize = host.itemSlotPreviewSize(Minecraft.getInstance());
            for (int sourceIndex = 0; sourceIndex < slots.size(); sourceIndex++) {
                SourceInventorySnapshot.SourceSlot slot = slots.get(sourceIndex);
                ImGui.tableNextColumn();
                float labelY = ImGui.getCursorPosY()
                    + Math.max(0.0f, (slotSize - ImGui.getTextLineHeight()) * 0.5f);
                drawSourceSlot(group, groupIndex, sourceIndex, target);
                ImGui.sameLine(0.0f, 6.0f);
                ImGui.setCursorPosY(labelY);
                ImGui.textDisabled(slot.displayLabel());
            }
        } finally {
            ImGui.endTable();
        }
    }

    private void drawSourceSlot(
        SourceInventorySnapshot.SourceGroup group,
        int groupIndex,
        int sourceIndex,
        SourceTarget target
    ) {
        SourceInventorySnapshot.SourceSlot slot = group.slots().get(sourceIndex);
        ImGui.pushID("source_group_" + groupIndex);
        ImGui.pushID(sourceIndex);
        try {
            String id = groupIndex + "/" + slot.id();
            ItemStack stack = slot.stack();
            NativeItemPreviews.ItemSlotInteraction interaction = host.drawItemSlotPreview(
                Minecraft.getInstance(),
                "source_slot",
                stack,
                selectedSourceId.equals(id),
                false
            );
            if (interaction.clicked() || interaction.focused()) {
                if (stack.isEmpty()) {
                    clearSourceSelection();
                } else {
                    selectSourceSlot(group, slot, id);
                }
            }
            if (!stack.isEmpty() && (interaction.doubleClicked() || interaction.keyboardActivated())) {
                selectSourceSlot(group, slot, id);
                copySourceStack(target, stack, false);
            }
            if (!stack.isEmpty() && ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceNoPreviewTooltip)) {
                try {
                    activeSourceDrag = slot;
                    ImGui.setDragDropPayload(SOURCE_STACK_PAYLOAD, activeSourceDrag);
                    host.drawDraggedItemPreview(Minecraft.getInstance(), stack);
                } finally {
                    ImGui.endDragDropSource();
                }
            }
            drawSourceSlotContext(target, group, slot, id, stack);
            if (interaction.hovered() && stack.isEmpty() && !slot.displayLabel().isBlank()) {
                ImGui.setTooltip(slot.displayLabel());
            }
        } finally {
            ImGui.popID();
            ImGui.popID();
        }
    }

    private void drawSourceSlotContext(
        SourceTarget target,
        SourceInventorySnapshot.SourceGroup group,
        SourceInventorySnapshot.SourceSlot slot,
        String id,
        ItemStack stack
    ) {
        if (stack.isEmpty() || !ImGui.beginPopupContextItem("##source_slot_context")) return;
        try {
            selectSourceSlot(group, slot, id);
            ImGui.textDisabled(tr(
                "container.source.selected",
                selectedSourceLabel,
                stack.getHoverName().getString(),
                stack.getCount()
            ));
            ImGui.separator();
            String fullStackLabel = target == SourceTarget.CONTAINER
                ? tr("container.source.copy_stack", selectedContentsSlot + 1)
                : tr("bundle.source.add_stack");
            if (ImGui.menuItem(fullStackLabel)) copySourceStack(target, stack, false);
            String singleItemLabel = target == SourceTarget.CONTAINER
                ? tr("container.source.copy_one", selectedContentsSlot + 1)
                : tr("bundle.source.add_one");
            if (ImGui.menuItem(singleItemLabel)) copySourceStack(target, stack, true);
            if (target == SourceTarget.CONTAINER) {
                int firstAvailable = findFirstAvailableContentsSlot();
                String destinationLabel = firstAvailable < 0 ? "-" : Integer.toString(firstAvailable + 1);
                ImGui.separator();
                ImGui.beginDisabled(firstAvailable < 0);
                if (ImGui.menuItem(tr("container.source.copy_stack_first_empty", destinationLabel))) {
                    copySourceStackToFirstAvailable(stack, false);
                }
                if (ImGui.menuItem(tr("container.source.copy_one_first_empty", destinationLabel))) {
                    copySourceStackToFirstAvailable(stack, true);
                }
                ImGui.endDisabled();
            }
        } finally {
            ImGui.endPopup();
        }
    }

    private void selectSourceSlot(
        SourceInventorySnapshot.SourceGroup group,
        SourceInventorySnapshot.SourceSlot slot,
        String id
    ) {
        selectedSourceStack = slot.stack();
        selectedSourceId = id;
        selectedSourceLabel = slot.displayLabel().isBlank()
            ? group.label() + " #" + (slot.sourceIndex() + 1)
            : group.label() + " - " + slot.displayLabel();
    }

    private void clearSourceSelection() {
        selectedSourceStack = ItemStack.EMPTY;
        selectedSourceId = "";
        selectedSourceLabel = "";
    }

    private void copySourceStack(SourceTarget target, ItemStack stack, boolean singleItem) {
        if (stack == null || stack.isEmpty()) return;
        ItemStack copied = singleItem ? stack.copyWithCount(1) : stack.copy();
        if (target == SourceTarget.CONTAINER) {
            applyContentsSlotCommand(ContentsSlotCommand.copy(copied, selectedContentsSlot));
        } else {
            addBundleItem(copied);
        }
    }

    private void copySourceStackToFirstAvailable(ItemStack stack, boolean singleItem) {
        int destination = findFirstAvailableContentsSlot();
        if (destination < 0 || stack == null || stack.isEmpty()) return;
        applyContentsSlotCommand(ContentsSlotCommand.copy(
            singleItem ? stack.copyWithCount(1) : stack.copy(),
            destination
        ));
    }

    private int indexedCapacity(ContainerPresentationProfile nativeProfile) {
        return Math.max(
            Math.max(nativeProfile.nativeCapacity(), contents.highestOccupiedSlot() + 1),
            Math.max(indexedMinimumCapacity, selectedContentsSlot + 1)
        );
    }

    private void queueSlotCopy(ItemStack source, int destination) {
        if (source == null || source.isEmpty()) return;
        int boundedDestination = Math.clamp(
            destination,
            0,
            ContainerContentsDraft.MAX_SLOTS - 1
        );
        ItemStack current = contents.item(boundedDestination);
        if (current.isEmpty()) {
            applySlotCopy(source, boundedDestination);
            return;
        }
        pendingSlotCopy = new PendingSlotCopy(boundedDestination, current, source);
    }

    private void drawReplaceSlotConfirmation() {
        if (pendingSlotCopy == null) return;
        ImGui.spacing();
        ImGui.separatorText(tr("container.contents.replace.title"));
        ItemStack current = pendingSlotCopy.current();
        ItemStack replacement = pendingSlotCopy.replacement();
        ImGui.textWrapped(tr(
            "container.contents.replace.description",
            pendingSlotCopy.destination() + 1,
            current.getHoverName().getString(),
            current.getCount(),
            replacement.getHoverName().getString(),
            replacement.getCount()
        ));
        ImGui.spacing();
        if (ImGui.button(tr("container.contents.replace.confirm"), 120.0f, 0.0f)) {
            applySlotCopy(replacement, pendingSlotCopy.destination());
            pendingSlotCopy = null;
        }
        EditorUi.sameLineIfFits(100.0f);
        if (ImGui.button(tr("action.cancel"), 100.0f, 0.0f)) pendingSlotCopy = null;
    }

    private void applySlotCopy(ItemStack source, int destination) {
        contents.setItem(destination, source);
        indexedMinimumCapacity = Math.max(indexedMinimumCapacity, destination + 1);
        selectContentsSlot(destination);
        contentsSlotFocusRequest = destination;
        dirty = true;
    }

    private void addBundleItem(ItemStack stack) {
        insertBundleItem(bundleContents.size(), stack);
    }

    private void insertBundleItem(int index, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        int destination = Math.clamp(index, 0, bundleContents.size());
        bundleContents.insertItem(destination, stack);
        bundleRowIds.add(destination, NEXT_BUNDLE_ROW_ID.incrementAndGet());
        selectBundleItem(destination);
        bundleSlotFocusRequest = destination;
        focusBundleAddButton = false;
        dirty = true;
    }

    private void selectContentsSlot(int index) {
        recoveryContentsSlot = -1;
        selectedContentsSlot = Math.clamp(index, 0, ContainerContentsDraft.MAX_SLOTS - 1);
        goToContentsSlot.set(selectedContentsSlot + 1);
        syncContainedItemFields();
    }

    private boolean hasSelectedBundleItem() {
        return selectedBundleItem >= 0 && selectedBundleItem < bundleContents.size();
    }

    private int clampBundleItemIndex(int index) {
        if (bundleContents.isEmpty()) return 0;
        return Math.clamp(index, 0, bundleContents.size() - 1);
    }

    private void selectBundleItem(int index) {
        recoveryBundleRowId = 0L;
        selectedBundleItem = clampBundleItemIndex(index);
        syncBundleItemFields();
    }

    private boolean allowContentsFocusSelection(int index) {
        return index != recoveryContentsSlot;
    }

    private boolean allowBundleFocusSelection(long rowId) {
        return rowId != recoveryBundleRowId;
    }

    private void clearBundleItemSelection() {
        selectedBundleItem = -1;
        bundleSlotFocusRequest = -1;
        syncBundleItemFields();
    }

    public SessionUiState captureSessionUiState() {
        return new SessionUiState(
            selectedContentsSlot,
            indexedMinimumCapacity,
            indexedView,
            selectedBundleItem,
            selectedSourceStack,
            selectedSourceId,
            selectedSourceLabel
        );
    }

    public void restoreSessionUiState(SessionUiState state) {
        selectedContentsSlot = Math.clamp(
            state.selectedSlot(),
            0,
            ContainerContentsDraft.MAX_SLOTS - 1
        );
        indexedMinimumCapacity = Math.clamp(
            state.indexedMinimumCapacity(),
            0,
            ContainerContentsDraft.MAX_SLOTS
        );
        indexedView = state.indexedView();
        selectedBundleItem = clampBundleItemIndex(state.selectedBundleItem());
        selectedSourceStack = state.selectedSourceStack();
        selectedSourceId = state.selectedSourceId();
        selectedSourceLabel = state.selectedSourceLabel();
        activeSourceDrag = null;
        activeContentsDrag = null;
        activeBundleDrag = null;
        contentsSlotFocusRequest = -1;
        bundleSlotFocusRequest = -1;
        focusBundleAddButton = false;
        pendingSlotCopy = null;
        goToContentsSlot.set(selectedContentsSlot + 1);
        syncContainedItemFields();
        syncBundleItemFields();
    }

    public void replaceContainedItemFromChild(int index, ItemStack stack) {
        applyContentsSlotCommand(ContentsSlotCommand.replace(stack, index));
    }

    public void replaceBundleItemFromChild(int index, ItemStack stack) {
        applyBundleSlotCommand(BundleSlotCommand.replace(index, stack));
    }

    public void requestContainedItemFocus(int index) {
        selectContentsSlot(index);
        contentsSlotFocusRequest = selectedContentsSlot;
    }

    public void requestBundleItemFocus(int index) {
        if (bundleContents.isEmpty()) {
            focusBundleAddButton = true;
            return;
        }
        selectBundleItem(index);
        bundleSlotFocusRequest = selectedBundleItem;
    }

    public record SessionUiState(
        int selectedSlot,
        int indexedMinimumCapacity,
        boolean indexedView,
        int selectedBundleItem,
        ItemStack selectedSourceStack,
        String selectedSourceId,
        String selectedSourceLabel
    ) {
        public SessionUiState {
            selectedSourceStack = selectedSourceStack == null ? ItemStack.EMPTY : selectedSourceStack.copy();
            if (selectedSourceStack.isEmpty()) {
                selectedSourceId = "";
                selectedSourceLabel = "";
            } else {
                selectedSourceId = selectedSourceId == null ? "" : selectedSourceId;
                selectedSourceLabel = selectedSourceLabel == null ? "" : selectedSourceLabel;
            }
        }

        @Override
        public ItemStack selectedSourceStack() {
            return selectedSourceStack.copy();
        }
    }

    private record PendingSlotCopy(int destination, ItemStack current, ItemStack replacement) {
        private PendingSlotCopy {
            current = current.copy();
            replacement = replacement.copy();
        }

        @Override
        public ItemStack current() {
            return current.copy();
        }

        @Override
        public ItemStack replacement() {
            return replacement.copy();
        }
    }

    private record ContentsSlotDrag(int index) {
    }

    private record BundleDropResult(
        BundleSlotCommand command,
        OrderedListDragDrop.ReorderRequest reorderRequest
    ) {
    }

    private enum ContentsSlotAction {
        EDIT,
        ADD,
        DUPLICATE,
        CLEAR,
        MOVE,
        COPY,
        REPLACE
    }

    private record ContentsSlotCommand(
        ContentsSlotAction action,
        int source,
        int destination,
        ItemStack stack
    ) {
        private ContentsSlotCommand {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
        }

        private static ContentsSlotCommand edit(int index) {
            return new ContentsSlotCommand(ContentsSlotAction.EDIT, index, index, ItemStack.EMPTY);
        }

        private static ContentsSlotCommand add(int index) {
            return new ContentsSlotCommand(ContentsSlotAction.ADD, index, index, ItemStack.EMPTY);
        }

        private static ContentsSlotCommand duplicate(int source, int destination) {
            return new ContentsSlotCommand(
                ContentsSlotAction.DUPLICATE,
                source,
                destination,
                ItemStack.EMPTY
            );
        }

        private static ContentsSlotCommand clear(int index) {
            return new ContentsSlotCommand(ContentsSlotAction.CLEAR, index, index, ItemStack.EMPTY);
        }

        private static ContentsSlotCommand move(int source, int destination) {
            return new ContentsSlotCommand(
                ContentsSlotAction.MOVE,
                source,
                destination,
                ItemStack.EMPTY
            );
        }

        private static ContentsSlotCommand copy(ItemStack stack, int destination) {
            return new ContentsSlotCommand(ContentsSlotAction.COPY, destination, destination, stack);
        }

        private static ContentsSlotCommand replace(ItemStack stack, int destination) {
            return new ContentsSlotCommand(ContentsSlotAction.REPLACE, destination, destination, stack);
        }

        @Override
        public ItemStack stack() {
            return stack.copy();
        }
    }

    private enum BundleSlotAction {
        NONE,
        EDIT,
        DUPLICATE,
        REMOVE,
        MOVE_EARLIER,
        MOVE_LATER,
        INSERT,
        REPLACE
    }

    private record BundleSlotCommand(
        BundleSlotAction action,
        int source,
        int destination,
        ItemStack stack
    ) {
        private BundleSlotCommand {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
        }

        private static BundleSlotCommand edit(int source) {
            return new BundleSlotCommand(BundleSlotAction.EDIT, source, source, ItemStack.EMPTY);
        }

        private static BundleSlotCommand remove(int source) {
            return new BundleSlotCommand(BundleSlotAction.REMOVE, source, source, ItemStack.EMPTY);
        }

        private static BundleSlotCommand insert(int destination, ItemStack stack) {
            return new BundleSlotCommand(BundleSlotAction.INSERT, destination, destination, stack);
        }

        private static BundleSlotCommand replace(int source, ItemStack stack) {
            return new BundleSlotCommand(BundleSlotAction.REPLACE, source, source, stack);
        }

        @Override
        public ItemStack stack() {
            return stack.copy();
        }
    }

    private enum SourceTarget {
        CONTAINER,
        BUNDLE
    }

    private void syncContainedItemFields() {
        ItemStack selected = contents.item(selectedContentsSlot);
        Identifier id = selected.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(selected.getItem());
        containedItemId.set(id == null ? "" : id.toString());
        containedItemCount.set(selected.isEmpty() ? 1 : Math.clamp(selected.getCount(), 1, MAX_ITEM_COUNT));
        containedItemIdPending = false;
    }

    private void applyContainedItemIdIfValid() {
        String value = containedItemId.get().trim();
        Identifier id = Identifier.tryParse(value);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == Items.AIR) return;

        ItemStack current = contents.item(selectedContentsSlot);
        int count = Math.clamp(containedItemCount.get(), 1, MAX_ITEM_COUNT);
        ItemStack replacement = current.isEmpty()
            ? new ItemStack(item, count)
            : current.transmuteCopy(item, count);
        contents.setItem(selectedContentsSlot, replacement);
        containedItemIdPending = false;
        dirty = true;
    }

    private void containedItemIdChanged() {
        containedItemIdPending = true;
        dirty = true;
        applyContainedItemIdIfValid();
    }

    private String containedItemProblem() {
        if (!containedItemIdPending) return "";
        String value = containedItemId.get().trim();
        if (value.isEmpty()) return tr("container.contents.item_required");
        Identifier id = Identifier.tryParse(value);
        if (id == null) return tr("validation.identifier_format");
        if (!BuiltInRegistries.ITEM.containsKey(id)) return tr("validation.identifier_unknown");
        return BuiltInRegistries.ITEM.getValue(id) == Items.AIR ? tr("error.empty_item") : "";
    }

    private void syncBundleItemFields() {
        if (!hasSelectedBundleItem()) {
            bundleItemId.set("");
            bundleItemCount.set(1);
            bundleItemIdPending = false;
            return;
        }
        ItemStack selected = bundleContents.item(selectedBundleItem);
        Identifier id = BuiltInRegistries.ITEM.getKey(selected.getItem());
        bundleItemId.set(id.toString());
        bundleItemCount.set(Math.clamp(selected.getCount(), 1, MAX_ITEM_COUNT));
        bundleItemIdPending = false;
    }

    private void applyBundleItemIdIfValid() {
        String value = bundleItemId.get().trim();
        Identifier id = Identifier.tryParse(value);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == Items.AIR) return;

        if (hasSelectedBundleItem()) {
            ItemStack current = bundleContents.item(selectedBundleItem);
            ItemStack replacement = current.transmuteCopy(item, 1);
            int count = Math.clamp(bundleItemCount.get(), 1, MAX_ITEM_COUNT);
            bundleContents.setItem(selectedBundleItem, replacement.copyWithCount(count));
            syncBundleItemFields();
        } else {
            int count = Math.clamp(bundleItemCount.get(), 1, MAX_ITEM_COUNT);
            addBundleItem(new ItemStack(item, count));
        }
        bundleItemIdPending = false;
        dirty = true;
    }

    private void bundleItemIdChanged() {
        bundleItemIdPending = true;
        dirty = true;
        applyBundleItemIdIfValid();
    }

    private String bundleItemProblem() {
        if (bundleItemIdPending) {
            String value = bundleItemId.get().trim();
            if (value.isEmpty()) return tr("bundle.contents.item_required");
            Identifier id = Identifier.tryParse(value);
            if (id == null) return tr("validation.identifier_format");
            if (!BuiltInRegistries.ITEM.containsKey(id)) return tr("validation.identifier_unknown");
            if (BuiltInRegistries.ITEM.getValue(id) == Items.AIR) return tr("error.empty_item");
        }
        if (hasSelectedBundleItem()
            && !net.minecraft.world.item.component.BundleContents.canItemBeInBundle(
            bundleContents.item(selectedBundleItem)
        )) {
            return tr("bundle.contents.item_disallowed");
        }
        return "";
    }

    private static boolean viewButton(String label, boolean selected) {
        if (selected) ItemEditorTheme.pushActiveFilter();
        boolean clicked = ImGui.button(label);
        if (selected) ItemEditorTheme.popActiveFilter();
        return clicked && !selected;
    }

    private void drawLock() {
        if (!beginSection("lock", lock)) return;
        JsonObject value = lock.object();
        if (beginFields("container_lock_fields")) {
            drawItemRestriction(value);
            drawCountRestriction(value);
            ImGui.endTable();
        }
        ImGui.spacing();
        drawJsonCondition(
            "exact_components",
            tr("container.lock.components"),
            tr("container.lock.components.help"),
            value,
            "components",
            exactComponentsSource,
            exactComponentsEditor
        );
        drawJsonCondition(
            "partial_predicates",
            tr("container.lock.predicates"),
            tr("container.lock.predicates.help"),
            value,
            "predicates",
            partialPredicatesSource,
            partialPredicatesEditor
        );
        endSection();
    }

    private void drawItemRestriction(JsonObject value) {
        boolean present = value.has("items");
        fieldLabel(tr("container.lock.items.enabled"), tr("container.lock.items.enabled.help"));
        ImBoolean enabled = new ImBoolean(present);
        if (ImGui.checkbox("##container_lock_items_enabled", enabled)) {
            if (enabled.get()) value.add("items", directItemSet("minecraft:tripwire_hook"));
            else value.remove("items");
            textBuffers.keySet().removeIf(key -> key.startsWith("container/lock/items/"));
            present = enabled.get();
            dirty = true;
        }
        if (!present) return;

        fieldLabel(tr("container.lock.items"), tr("container.lock.items.help"));
        if (consumePendingFieldFocus("lock", "items")) ImGui.setScrollHereY(0.35f);
        if (IdentifierCollectionEditor.draw(
            host,
            "container_lock_items",
            "container/lock/items",
            value.get("items"),
            items -> value.add("items", normalizeItemSet(items)),
            itemSelectors,
            tr("field_purpose.item_identifier"),
            "minecraft:tripwire_hook",
            tr("container.lock.item"),
            textBuffers,
            lockItemsFocus
        )) dirty = true;
        ItemEditorTheme.drawFieldProblem(itemSelectionProblem(value.get("items")));
    }

    private void drawCountRestriction(JsonObject value) {
        boolean present = value.has("count");
        fieldLabel(tr("container.lock.count.enabled"), tr("container.lock.count.enabled.help"));
        ImBoolean enabled = new ImBoolean(present);
        if (ImGui.checkbox("##container_lock_count_enabled", enabled)) {
            if (enabled.get()) value.addProperty("count", 1);
            else value.remove("count");
            present = enabled.get();
            dirty = true;
        }
        if (!present) return;

        JsonElement count = value.get("count");
        boolean range = count != null && count.isJsonObject();
        fieldLabel(tr("container.lock.count.mode"), tr("container.lock.count.mode.help"));
        if (modeButton(tr("container.lock.count.exact") + "##count_exact", !range)) {
            value.addProperty("count", rangeEndpoint(count, "min", 1));
            range = false;
            dirty = true;
        }
        EditorUi.sameLineIfFits(tr("container.lock.count.range"));
        if (modeButton(tr("container.lock.count.range") + "##count_range", range)) {
            JsonObject bounds = new JsonObject();
            bounds.addProperty("min", integer(count, 1));
            value.add("count", bounds);
            range = true;
            dirty = true;
        }

        if (!range) {
            fieldLabel(tr("container.lock.count.value"), tr("container.lock.count.value.help"));
            ImInt exact = new ImInt(integer(value.get("count"), 1));
            ImGui.setNextItemWidth(-1.0f);
            applyPendingFieldFocus("lock", "count");
            if (ImGui.inputInt("##container_lock_count", exact, 1, 10)) {
                value.addProperty("count", exact.get());
                dirty = true;
            }
            return;
        }

        JsonObject bounds = value.getAsJsonObject("count");
        fieldLabel(tr("container.lock.count.minimum"), tr("container.lock.count.minimum.help"));
        drawOptionalBound(bounds, "min", "minimum");
        fieldLabel(tr("container.lock.count.maximum"), tr("container.lock.count.maximum.help"));
        drawOptionalBound(bounds, "max", "maximum");
        ItemEditorTheme.drawFieldProblem(countRangeProblem(bounds));
    }

    private void drawOptionalBound(JsonObject bounds, String key, String id) {
        boolean present = bounds.has(key);
        ImBoolean enabled = new ImBoolean(present);
        if (ImGui.checkbox("##container_lock_" + id + "_enabled", enabled)) {
            if (enabled.get()) bounds.addProperty(key, 1);
            else bounds.remove(key);
            present = enabled.get();
            dirty = true;
        }
        ImGui.sameLine();
        ImInt amount = new ImInt(integer(bounds.get(key), 1));
        ImGui.beginDisabled(!present);
        ImGui.setNextItemWidth(-1.0f);
        applyPendingFieldFocus("lock", "count/" + key);
        if (ImGui.inputInt("##container_lock_" + id, amount, 1, 10)) {
            bounds.addProperty(key, amount.get());
            dirty = true;
        }
        ImGui.endDisabled();
    }

    private void drawJsonCondition(
        String id,
        String label,
        String help,
        JsonObject value,
        String field,
        ImString source,
        CodeEditorInput editor
    ) {
        boolean present = value.has(field);
        ImBoolean enabled = new ImBoolean(present);
        if (ImGui.checkbox(label + "##enabled_" + id, enabled)) {
            if (enabled.get()) {
                value.add(field, new JsonObject());
                source.set("{}");
            } else {
                value.remove(field);
            }
            present = enabled.get();
            editor.resetCursor();
            dirty = true;
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(help);
        if (!present) {
            ImGui.spacing();
            return;
        }

        if (consumePendingFieldFocus("lock", field)) {
            ImGui.setScrollHereY(0.35f);
            editor.requestFocus();
        }
        String problem = snbtCompoundProblem(source.get());
        int errorLine = problem.isBlank() ? 0 : snbtCompoundErrorLine(source.get());
        if (!problem.isBlank()) ItemEditorTheme.pushInvalidField();
        if (editor.draw(
            "container_lock_" + id,
            source,
            -1.0f,
            132.0f,
            ImGuiInputTextFlags.AllowTabInput,
            errorLine,
            CodeLanguage.SNBT
        )) dirty = true;
        if (!problem.isBlank()) ItemEditorTheme.popInvalidField();
        problem = snbtCompoundProblem(source.get());
        ItemEditorTheme.drawFieldProblem(problem);
        ImGui.beginDisabled(!problem.isBlank());
        if (ImGui.button(tr("container.lock.snbt.format") + "##" + id)) {
            if (formatPendingSnbt(source, "container.lock.snbt.invalid")) editor.resetCursor();
        }
        ImGui.endDisabled();
        ImGui.spacing();
    }

    private void drawLootTable() {
        if (!beginSection("loot_table", loot)) return;
        if (beginFields("container_loot_table_fields")) {
            fieldLabel(tr("container.loot_table"), tr("container.loot_table.help"));
            applyPendingFieldFocus("loot_table", "loot_table");
            if (host.inputIdentifierWithSuggestions(
                "container_loot_table",
                new FieldHints(
                    tr("field_purpose.loot_table_identifier"),
                    "minecraft:empty"
                ),
                lootTable,
                lootTables,
                identifierProblem(lootTable.get(), true)
            )) dirty = true;

            fieldLabel(tr("container.loot_seed"), tr("container.loot_seed.help"));
            String seedProblem = seedProblem(lootSeed.get());
            if (!seedProblem.isBlank()) ItemEditorTheme.pushInvalidField();
            ImGui.setNextItemWidth(-1.0f);
            applyPendingFieldFocus("loot_table", "seed");
            if (inputTextWithHint(
                "##container_loot_seed",
                new FieldHints(tr("field_purpose.seed_value"), "0"),
                lootSeed
            )) {
                dirty = true;
            }
            if (!seedProblem.isBlank()) ItemEditorTheme.popInvalidField();
            ItemEditorTheme.drawFieldProblem(seedProblem);
            ImGui.endTable();
        }
        endSection();
    }

    public boolean save(Minecraft client, boolean announce) {
        return savePendingStack(client, announce, edited -> sync(client, edited));
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

    private ItemStack previewStack(Minecraft client) {
        try {
            return buildEditedStack(client);
        } catch (RuntimeException ignored) {
            return host.draft();
        }
    }

    private ItemStack buildEditedStack(Minecraft client) {
        if (contents.present()) {
            String contentsProblem = containedItemProblem();
            if (!contentsProblem.isBlank()) throw new IllegalArgumentException(contentsProblem);
        }
        if (bundleContents.present()) {
            String bundleItemProblem = bundleItemProblem();
            if (!bundleItemProblem.isBlank()) throw new IllegalArgumentException(bundleItemProblem);
            int disallowedBundleItem = bundleContents.firstDisallowedItem();
            if (disallowedBundleItem >= 0) {
                throw new IllegalArgumentException(tr("bundle.contents.disallowed", disallowedBundleItem + 1));
            }
        }
        ItemStack edited = host.draft().copy();
        contents.applyTo(edited);
        bundleContents.applyTo(edited);
        if (lock.present())
            ItemStackCodecs.decodeAndSetComponentNbt(client, edited, DataComponents.LOCK, pendingLockValue());
        else edited.remove(DataComponents.LOCK);
        if (loot.present())
            ItemStackCodecs.decodeAndSetComponent(client, edited, DataComponents.CONTAINER_LOOT, pendingLootValue());
        else edited.remove(DataComponents.CONTAINER_LOOT);
        ItemStackCodecs.validateStack(edited);
        return edited;
    }

    private CompoundTag pendingLockValue() {
        if (!lock.value().isJsonObject()) throw new IllegalArgumentException("Lock must be an object");
        JsonObject result = lock.value().getAsJsonObject().deepCopy();
        if (result.has("items")) {
            String problem = itemSelectionProblem(result.get("items"));
            if (!problem.isBlank()) throw new IllegalArgumentException(problem);
        }
        if (result.has("count") && result.get("count").isJsonObject()) {
            String problem = countRangeProblem(result.getAsJsonObject("count"));
            if (!problem.isBlank()) throw new IllegalArgumentException(problem);
        }
        boolean hasComponents = result.has("components");
        boolean hasPredicates = result.has("predicates");
        result.remove("components");
        result.remove("predicates");
        CompoundTag encoded = ItemStackCodecs.jsonObjectToNbt(result);
        if (hasComponents) {
            encoded.put("components", ItemStackCodecs.parseCompoundSnbt(exactComponentsSource.get()));
        }
        if (hasPredicates) {
            encoded.put("predicates", ItemStackCodecs.parseCompoundSnbt(partialPredicatesSource.get()));
        }
        return encoded;
    }

    private JsonObject pendingLootValue() {
        String table = lootTable.get().trim();
        String tableProblem = identifierProblem(table, true);
        if (!tableProblem.isBlank()) throw new IllegalArgumentException(tableProblem);
        String seedProblem = seedProblem(lootSeed.get());
        if (!seedProblem.isBlank()) throw new IllegalArgumentException(seedProblem);

        if (!loot.value().isJsonObject()) throw new IllegalArgumentException("Container loot must be an object");
        JsonObject result = loot.value().getAsJsonObject().deepCopy();
        result.addProperty("loot_table", table);
        result.addProperty("seed", parseSeed(lootSeed.get()));
        return result;
    }

    private String itemSelectionProblem(JsonElement items) {
        List<String> values = identifierValues(items);
        if (values.isEmpty()) return tr("container.lock.items.required");
        if (values.stream().anyMatch(value -> value.startsWith("#")) && values.size() > 1) {
            return tr("collection.tag_is_collection");
        }
        for (String value : values) {
            boolean tag = value.startsWith("#");
            String rawId = tag ? value.substring(1) : value;
            Identifier id = Identifier.tryParse(rawId);
            if (id == null) return tr("validation.identifier_or_tag_format");
            if (tag) {
                if (!knownItemSelectors.isEmpty() && !knownItemSelectors.contains(value)) {
                    return tr("validation.identifier_unknown");
                }
            } else if (!BuiltInRegistries.ITEM.containsKey(id)) {
                return tr("validation.identifier_unknown");
            }
        }
        return "";
    }

    private static String countRangeProblem(JsonObject bounds) {
        if (bounds.has("min") && bounds.has("max")
            && integer(bounds.get("min"), 0) > integer(bounds.get("max"), 0)) {
            return tr("container.lock.count.invalid_range");
        }
        return "";
    }

    private static int rangeEndpoint(JsonElement count, String key, int fallback) {
        return count != null && count.isJsonObject()
            ? integer(count.getAsJsonObject().get(key), fallback)
            : integer(count, fallback);
    }

    private static boolean modeButton(String label, boolean selected) {
        if (selected) ItemEditorTheme.pushActiveFilter();
        boolean pressed = ImGui.button(label);
        if (selected) ItemEditorTheme.popActiveFilter();
        return pressed && !selected;
    }

    private static String snbtCompoundProblem(String source) {
        try {
            ItemStackCodecs.parseCompoundSnbt(source);
            return "";
        } catch (RuntimeException exception) {
            return tr("container.lock.snbt.invalid", EditorUi.readableMessage(exception));
        }
    }

    private static int snbtCompoundErrorLine(String source) {
        try {
            ItemStackCodecs.parseCompoundSnbt(source);
            return 0;
        } catch (RuntimeException exception) {
            return CodeEditorInput.errorLine(exception, source);
        }
    }

    private static String identifierProblem(String source, boolean required) {
        String value = source == null ? "" : source.trim();
        if (value.isEmpty()) return required ? tr("validation.identifier_required") : "";
        return Identifier.tryParse(value) == null ? tr("validation.identifier_format") : "";
    }

    private static String seedProblem(String source) {
        try {
            parseSeed(source);
            return "";
        } catch (NumberFormatException exception) {
            return tr("container.loot_seed.invalid");
        }
    }

    private static long parseSeed(String source) {
        String value = source == null ? "" : source.trim();
        return value.isEmpty() ? 0L : Long.parseLong(value);
    }

    private static String snbtSource(CompoundTag owner, String field) {
        if (owner == null) return "{}";
        return owner.getCompound(field).map(ItemStackCodecs::prettySnbt).orElse("{}");
    }

    private static String string(JsonElement value, String fallback) {
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String longString(JsonElement value, String fallback) {
        try {
            return value != null && value.isJsonPrimitive() ? Long.toString(value.getAsLong()) : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int integer(JsonElement value, int fallback) {
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static List<String> identifierValues(JsonElement encoded) {
        List<String> values = new ArrayList<>();
        if (encoded == null || encoded.isJsonNull()) return values;
        if (encoded.isJsonPrimitive()) {
            values.add(encoded.getAsString().trim());
        } else if (encoded.isJsonArray()) {
            JsonArray array = encoded.getAsJsonArray();
            for (JsonElement entry : array) {
                if (entry.isJsonPrimitive()) values.add(entry.getAsString().trim());
            }
        }
        values.removeIf(String::isBlank);
        return values;
    }

    private static JsonElement normalizeItemSet(JsonElement value) {
        if (value != null && value.isJsonPrimitive() && !value.getAsString().startsWith("#")) {
            return directItemSet(value.getAsString());
        }
        return value == null ? new JsonArray() : value.deepCopy();
    }

    private static JsonArray directItemSet(String id) {
        JsonArray items = new JsonArray();
        items.add(id);
        return items;
    }
}

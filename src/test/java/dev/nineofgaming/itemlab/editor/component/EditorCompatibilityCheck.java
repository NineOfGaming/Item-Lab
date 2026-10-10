package dev.nineofgaming.itemlab.editor.component;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.nineofgaming.itemlab.ItemLab;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.catalog.ComponentOption;
import dev.nineofgaming.itemlab.editor.catalog.ComponentState;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.catalog.RecipeResourceCatalog;
import dev.nineofgaming.itemlab.editor.container.BundleContentsDraft;
import dev.nineofgaming.itemlab.editor.container.ContainerContentsDraft;
import dev.nineofgaming.itemlab.editor.container.ContainerPresentationProfile;
import dev.nineofgaming.itemlab.editor.container.SourceInventorySnapshot;
import dev.nineofgaming.itemlab.editor.container.SulfurCubeContentDraft;
import dev.nineofgaming.itemlab.editor.panel.AdventureEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.BannerEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.BeesEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.CombatEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.CompassEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.ContainerEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.DataEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.MusicEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.PotionEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.RecipesEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.SulfurCubeContentEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.UseEditorPanel;
import dev.nineofgaming.itemlab.editor.state.EditorItemTarget;
import dev.nineofgaming.itemlab.editor.ui.control.CodeEditorInput;
import dev.nineofgaming.itemlab.editor.ui.list.EditorListMutations;
import dev.nineofgaming.itemlab.editor.ui.control.EditorShortcutPolicy;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.list.JsonListMutations;
import dev.nineofgaming.itemlab.editor.ui.list.OrderedListDragDrop;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Headless build check for version-specific component and dedicated-editor assumptions.
 */
public final class EditorCompatibilityCheck {
    private static final Logger LOGGER = ItemLab.createLogger("Compatibility Check");
    private static HolderLookup.Provider vanillaRegistries;

    private EditorCompatibilityCheck() {
    }

    public static void main(String[] arguments) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        vanillaRegistries = VanillaRegistries.createLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(vanillaRegistries)
            .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);

        List<ComponentOption> components = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.DATA_COMPONENT_TYPE.keySet()) {
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
            if (type != null) components.add(new ComponentOption(id.toString(), id.toString(), type));
        }
        EditorVersionProfile.refresh(components);

        String expectedVersion = System.getProperty("itemlab.expectedMinecraftVersion", "");
        if (!expectedVersion.equals(EditorVersionProfile.targetVersion())) {
            throw new AssertionError(
                "Version profile reports " + EditorVersionProfile.targetVersion()
                    + " but this build targets " + expectedVersion
            );
        }

        boolean expectsSulfurCube = expectedVersion.compareTo("26.2") >= 0;
        if (EditorVersionProfile.hasComponent("minecraft:sulfur_cube_content") != expectsSulfurCube) {
            throw new AssertionError(
                "minecraft:sulfur_cube_content availability does not match Minecraft " + expectedVersion
            );
        }

        List<String> problems = new ArrayList<>();
        verifyPrimitiveStarters(problems);
        verifyPotionEditor(problems);
        verifyPotionUseExtras(problems);
        verifyPiercingWeaponEditor(problems);
        verifyKineticWeaponEditor(problems);
        verifyChargedProjectilesEditor(problems);
        verifyMusicEditor(problems);
        verifyProvidedBannerPatternsEditor(problems);
        verifyCompassEditor(problems);
        verifyContainerMetadataEditor(problems);
        verifyContainerContentsModel(problems);
        verifyBundleContentsModel(problems);
        verifyOverstackedItemCounts(problems);
        verifySnbtStackNumericRoundTrip(problems);
        verifyOpaqueFragmentNumericRoundTrips(problems);
        verifyExpandedDefaultDeletion(problems);
        verifySulfurCubeContentEditor(problems);
        verifyFireworkEditor(problems);
        verifyPlayerHeadEditor(problems);
        verifyAdventureEditor(problems);
        verifyRecipesEditor(problems);
        verifyBeesEditor(problems);
        verifyComponentRecommendations(problems);
        verifyDataEditor(problems, components);
        verifyDedicatedComponentFilter(problems);
        verifyComponentCoverage(problems, components);
        verifyCodeEditorErrorLines(problems);
        verifyEditorShortcutPolicy(problems);
        verifyComponentSectionInitialState(problems);
        verifyEditorListMutations(problems);
        verifyIdentifierCollectionFocus(problems);
        verifyGeneratedComponentFormInfrastructure(problems);
        verifyCreativeInventorySlots(problems);
        for (ComponentOption option : components) {
            Set<String> declared = ComponentSchemas.declaredRootFields(option.id());
            if (!declared.isEmpty()) {
                List<String> missing = EditorVersionProfile.handledFieldsNotSupportedBy(option.type(), declared);
                if (!missing.isEmpty()) {
                    problems.add(option.id() + " schema fields absent from codec: " + String.join(", ", missing));
                }
            }

            DedicatedEditorSchema.Coverage coverage = DedicatedEditorSchema.coverage(option.type());
            if (!coverage.missingFields().isEmpty()) {
                problems.add(option.id() + " dedicated fields absent from codec: "
                    + String.join(", ", coverage.missingFields()));
            }
        }
        if (!problems.isEmpty()) {
            throw new AssertionError(
                "Editor compatibility check failed for Minecraft " + expectedVersion + ":\n - "
                    + String.join("\n - ", problems)
            );
        }

        LOGGER.info(
            "Verified Minecraft {} profile against {} registered item components",
            expectedVersion,
            components.size()
        );
    }

    private static void verifyPrimitiveStarters(List<String> problems) {
        verifyPrimitive(problems, "minecraft:fox/variant", DataComponents.FOX_VARIANT, "red");
        verifyPrimitive(problems, "minecraft:salmon/size", DataComponents.SALMON_SIZE, "medium");
        verifyPrimitive(problems, "minecraft:parrot/variant", DataComponents.PARROT_VARIANT, "red_blue");
        verifyPrimitive(problems, "minecraft:tropical_fish/pattern", DataComponents.TROPICAL_FISH_PATTERN, "kob");
        verifyPrimitive(problems, "minecraft:mooshroom/variant", DataComponents.MOOSHROOM_VARIANT, "red");
        verifyPrimitive(problems, "minecraft:rabbit/variant", DataComponents.RABBIT_VARIANT, "brown");
        verifyPrimitive(problems, "minecraft:horse/variant", DataComponents.HORSE_VARIANT, "white");
        verifyPrimitive(problems, "minecraft:llama/variant", DataComponents.LLAMA_VARIANT, "creamy");
        verifyPrimitive(problems, "minecraft:axolotl/variant", DataComponents.AXOLOTL_VARIANT, "lucy");
    }

    private static void verifyPotionEditor(List<String> problems) {
        DedicatedEditorSchema.Coverage coverage = DedicatedEditorSchema.coverage(DataComponents.POTION_CONTENTS);
        if (!coverage.isExact()) {
            problems.add("minecraft:potion_contents dedicated coverage is not exact");
        }
        String sample = """
            {
              "potion": "minecraft:swiftness",
              "custom_color": 3368703,
              "custom_effects": [{
                "id": "minecraft:strength",
                "duration": 600,
                "amplifier": 2,
                "ambient": true,
                "show_particles": false,
                "show_icon": true,
                "hidden_effect": {
                  "duration": 200,
                  "amplifier": 0,
                  "ambient": false,
                  "show_particles": true,
                  "show_icon": true
                }
              }],
              "custom_name": "custom"
            }
            """;
        try {
            DataComponents.POTION_CONTENTS.codec()
                .parse(JsonOps.INSTANCE, JsonParser.parseString(sample))
                .getOrThrow();
        } catch (RuntimeException exception) {
            problems.add("minecraft:potion_contents full editor sample is invalid: " + exception.getMessage());
        }
    }

    private static void verifyPotionUseExtras(List<String> problems) {
        verifyPanelSupport(
            problems,
            "minecraft:potion_duration_scale",
            PotionEditorPanel.supports(DataComponents.POTION_DURATION_SCALE)
        );
        verifyPanelSupport(
            problems,
            "minecraft:suspicious_stew_effects",
            UseEditorPanel.supports(DataComponents.SUSPICIOUS_STEW_EFFECTS)
        );
        verifyPanelSupport(
            problems,
            "minecraft:ominous_bottle_amplifier",
            UseEditorPanel.supports(DataComponents.OMINOUS_BOTTLE_AMPLIFIER)
        );
        verifyPanelSupport(
            problems,
            "minecraft:death_protection",
            UseEditorPanel.supports(DataComponents.DEATH_PROTECTION)
        );

        verifyDedicatedSample(
            problems,
            "minecraft:potion_duration_scale",
            DataComponents.POTION_DURATION_SCALE,
            "1.5"
        );
        verifyDedicatedSample(
            problems,
            "minecraft:suspicious_stew_effects",
            DataComponents.SUSPICIOUS_STEW_EFFECTS,
            "[{\"id\":\"minecraft:speed\",\"duration\":160}]"
        );
        verifyDedicatedSample(
            problems,
            "minecraft:ominous_bottle_amplifier",
            DataComponents.OMINOUS_BOTTLE_AMPLIFIER,
            "2"
        );
        verifyDedicatedSample(
            problems,
            "minecraft:death_protection",
            DataComponents.DEATH_PROTECTION,
            "{\"death_effects\":[{\"type\":\"minecraft:clear_all_effects\"}]}"
        );
    }

    private static void verifyPanelSupport(List<String> problems, String id, boolean supported) {
        if (!supported) problems.add(id + " is not routed to its purpose-built panel");
    }

    private static void verifyCompassEditor(List<String> problems) {
        verifyPanelSupport(
            problems,
            "minecraft:lodestone_tracker",
            CompassEditorPanel.supports(DataComponents.LODESTONE_TRACKER)
        );
        if (!ItemEditor.shouldExcludeAsDedicatedComponent(DataComponents.LODESTONE_TRACKER)) {
            problems.add("minecraft:lodestone_tracker is not recognized as having a dedicated editor");
        }
        verifyDedicatedSample(
            problems,
            "minecraft:lodestone_tracker",
            DataComponents.LODESTONE_TRACKER,
            "{\"target\":{\"dimension\":\"minecraft:overworld\",\"pos\":[12,64,-8]},\"tracked\":false}"
        );
        verifyDedicatedSample(
            problems,
            "minecraft:lodestone_tracker (unbound)",
            DataComponents.LODESTONE_TRACKER,
            "{\"tracked\":true}"
        );
    }

    private static void verifyPiercingWeaponEditor(List<String> problems) {
        verifyPanelSupport(
            problems,
            "minecraft:piercing_weapon",
            CombatEditorPanel.supports(DataComponents.PIERCING_WEAPON)
        );
        if (!ItemEditor.shouldExcludeAsDedicatedComponent(DataComponents.PIERCING_WEAPON)) {
            problems.add("minecraft:piercing_weapon is not recognized as having a dedicated editor");
        }
        verifyDedicatedSample(
            problems,
            "minecraft:piercing_weapon",
            DataComponents.PIERCING_WEAPON,
            "{\"deals_knockback\":false,\"dismounts\":true,\"sound\":\"minecraft:item.trident.throw\",\"hit_sound\":\"minecraft:item.trident.hit\"}"
        );
    }

    private static void verifyKineticWeaponEditor(List<String> problems) {
        verifyPanelSupport(
            problems,
            "minecraft:kinetic_weapon",
            CombatEditorPanel.supports(DataComponents.KINETIC_WEAPON)
        );
        if (!ItemEditor.shouldExcludeAsDedicatedComponent(DataComponents.KINETIC_WEAPON)) {
            problems.add("minecraft:kinetic_weapon is not recognized as having a dedicated editor");
        }
        verifyDedicatedSample(
            problems,
            "minecraft:kinetic_weapon",
            DataComponents.KINETIC_WEAPON,
            "{\"contact_cooldown_ticks\":10,\"delay_ticks\":15,"
                + "\"dismount_conditions\":{\"max_duration_ticks\":100,\"min_speed\":14.0,\"min_relative_speed\":0.0},"
                + "\"knockback_conditions\":{\"max_duration_ticks\":200,\"min_speed\":5.1,\"min_relative_speed\":0.0},"
                + "\"damage_conditions\":{\"max_duration_ticks\":300,\"min_speed\":0.0,\"min_relative_speed\":4.6},"
                + "\"forward_movement\":0.38,\"damage_multiplier\":0.7,"
                + "\"sound\":\"minecraft:item.trident.throw\",\"hit_sound\":\"minecraft:item.trident.hit\"}"
        );
    }

    private static void verifyChargedProjectilesEditor(List<String> problems) {
        verifyPanelSupport(
            problems,
            "minecraft:charged_projectiles",
            CombatEditorPanel.supports(DataComponents.CHARGED_PROJECTILES)
        );
        if (!ItemEditor.shouldExcludeAsDedicatedComponent(DataComponents.CHARGED_PROJECTILES)) {
            problems.add("minecraft:charged_projectiles is not recognized as having a dedicated editor");
        }
        verifyDedicatedSample(
            problems,
            "minecraft:charged_projectiles",
            DataComponents.CHARGED_PROJECTILES,
            "[{\"id\":\"minecraft:tipped_arrow\",\"count\":2,"
                + "\"components\":{\"minecraft:custom_name\":\"Loaded projectile\"}}]"
        );
    }

    private static void verifyMusicEditor(List<String> problems) {
        verifyPanelSupport(
            problems,
            "minecraft:instrument",
            MusicEditorPanel.supports(DataComponents.INSTRUMENT)
        );
        verifyPanelSupport(
            problems,
            "minecraft:jukebox_playable",
            MusicEditorPanel.supports(DataComponents.JUKEBOX_PLAYABLE)
        );
        verifyPanelSupport(
            problems,
            "minecraft:note_block_sound",
            MusicEditorPanel.supports(DataComponents.NOTE_BLOCK_SOUND)
        );
        if (!ItemEditor.shouldExcludeAsDedicatedComponent(DataComponents.INSTRUMENT)
            || !ItemEditor.shouldExcludeAsDedicatedComponent(DataComponents.JUKEBOX_PLAYABLE)
            || !ItemEditor.shouldExcludeAsDedicatedComponent(DataComponents.NOTE_BLOCK_SOUND)) {
            problems.add("music components are not all recognized as having dedicated editors");
        }

        verifyDedicatedSample(
            problems,
            "minecraft:instrument (registered)",
            DataComponents.INSTRUMENT,
            "\"minecraft:ponder_goat_horn\""
        );
        verifyDedicatedSample(
            problems,
            "minecraft:instrument (custom)",
            DataComponents.INSTRUMENT,
            "{\"sound_event\":{\"sound_id\":\"minecraft:item.goat_horn.sound.0\",\"range\":32.0},"
                + "\"use_duration\":7.0,\"range\":256.0,"
                + "\"description\":{\"text\":\"Custom instrument\"}}"
        );
        verifyDedicatedSample(
            problems,
            "minecraft:jukebox_playable",
            DataComponents.JUKEBOX_PLAYABLE,
            "\"minecraft:13\""
        );
        verifyDedicatedSample(
            problems,
            "minecraft:note_block_sound",
            DataComponents.NOTE_BLOCK_SOUND,
            "\"minecraft:block.note_block.harp\""
        );
    }

    private static void verifyProvidedBannerPatternsEditor(List<String> problems) {
        verifyPanelSupport(
            problems,
            "minecraft:provides_banner_patterns",
            BannerEditorPanel.supports(DataComponents.PROVIDES_BANNER_PATTERNS)
        );
        if (!ItemEditor.shouldExcludeAsDedicatedComponent(DataComponents.PROVIDES_BANNER_PATTERNS)) {
            problems.add("minecraft:provides_banner_patterns is not recognized as having a dedicated editor");
        }
        verifyDedicatedSample(
            problems,
            "minecraft:provides_banner_patterns",
            DataComponents.PROVIDES_BANNER_PATTERNS,
            "[\"minecraft:base\",\"minecraft:border\"]"
        );
    }

    private static void verifyContainerMetadataEditor(List<String> problems) {
        verifyPanelSupport(
            problems,
            "minecraft:lock",
            ContainerEditorPanel.supports(DataComponents.LOCK)
        );
        verifyPanelSupport(
            problems,
            "minecraft:container_loot",
            ContainerEditorPanel.supports(DataComponents.CONTAINER_LOOT)
        );
        if (!ContainerEditorPanel.supports(DataComponents.CONTAINER)
            || !ItemEditor.shouldExcludeAsDedicatedComponent(DataComponents.CONTAINER)) {
            problems.add("minecraft:container contents are not recognized as having a dedicated editor");
        }
        if (!ContainerEditorPanel.supports(DataComponents.BUNDLE_CONTENTS)
            || !ItemEditor.shouldExcludeAsDedicatedComponent(DataComponents.BUNDLE_CONTENTS)) {
            problems.add("minecraft:bundle_contents is not recognized as having a dedicated editor");
        }
        if (!ItemEditor.shouldExcludeAsDedicatedComponent(DataComponents.LOCK)
            || !ItemEditor.shouldExcludeAsDedicatedComponent(DataComponents.CONTAINER_LOOT)) {
            problems.add("container metadata is not recognized as having a dedicated editor");
        }

        verifyDedicatedSample(
            problems,
            "minecraft:lock",
            DataComponents.LOCK,
            "{\"items\":[\"minecraft:tripwire_hook\"],\"count\":{\"min\":1,\"max\":16},\"components\":{},\"predicates\":{}}"
        );
        verifyDedicatedSample(
            problems,
            "minecraft:container_loot",
            DataComponents.CONTAINER_LOOT,
            "{\"loot_table\":\"minecraft:chests/simple_dungeon\",\"seed\":9223372036854775807}"
        );

        verifyContainerSessionSourceSelection(problems);
    }

    private static void verifyContainerSessionSourceSelection(List<String> problems) {
        ContainerEditorPanel.SessionUiState cleared = new ContainerEditorPanel.SessionUiState(
            0,
            0,
            false,
            0,
            ItemStack.EMPTY,
            "stale/source",
            "Stale source"
        );
        if (!cleared.selectedSourceStack().isEmpty()
            || !cleared.selectedSourceId().isEmpty()
            || !cleared.selectedSourceLabel().isEmpty()) {
            problems.add("container session state retained stale source metadata for an empty selection");
        }

        ItemStack selected = testStack("minecraft:diamond", 4);
        selected.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Selected source"));
        ItemStack selectedSnapshot = selected.copy();
        ContainerEditorPanel.SessionUiState populated = new ContainerEditorPanel.SessionUiState(
            0,
            0,
            false,
            0,
            selected,
            "0/player_9",
            "Main inventory #10"
        );
        selected.setCount(1);
        ItemStack returned = populated.selectedSourceStack();
        returned.remove(DataComponents.CUSTOM_NAME);
        if (!ItemStack.matches(selectedSnapshot, populated.selectedSourceStack())
            || !populated.selectedSourceId().equals("0/player_9")
            || !populated.selectedSourceLabel().equals("Main inventory #10")) {
            problems.add("container session state did not preserve a defensive captured-source selection");
        }
    }

    private static void verifyContainerContentsModel(List<String> problems) {
        ItemStack owner = testStack("minecraft:stone", 1);
        if (containerDraft(owner).present()) {
            problems.add("minecraft:container draft confused an absent component with present-empty contents");
        }
        ContainerContentsDraft draft = new ContainerContentsDraft();
        draft.setPresent(true);
        draft.applyTo(owner);
        ContainerContentsDraft presentEmpty = containerDraft(owner);
        if (!presentEmpty.present() || presentEmpty.highestOccupiedSlot() != -1) {
            problems.add("minecraft:container draft did not preserve a present-empty component");
        }
        draft.setItem(0, testStack("minecraft:stone", 17));
        draft.setItem(8, testStack("minecraft:diamond", 3));
        draft.setItem(255, testStack("minecraft:netherite_ingot", 1));
        draft.applyTo(owner);

        ContainerContentsDraft restored = containerDraft(owner);
        if (!restored.present()
            || restored.highestOccupiedSlot() != 255
            || restored.item(0).getCount() != 17
            || restored.item(8).getItem() != Items.DIAMOND
            || restored.item(255).getItem() != Items.NETHERITE_INGOT
            || !restored.matchesStack(owner)) {
            problems.add("minecraft:container sparse contents did not round-trip through the draft model");
        }

        restored.swap(0, 8);
        if (restored.item(0).getItem() != Items.DIAMOND
            || restored.item(8).getItem() != Items.STONE
            || restored.item(8).getCount() != 17) {
            problems.add("minecraft:container draft did not swap occupied slots losslessly");
        }
        restored.swap(0, 8);
        restored.swap(8, 7);
        if (!restored.item(8).isEmpty()
            || restored.item(7).getItem() != Items.DIAMOND) {
            problems.add("minecraft:container draft did not move a stack into an empty slot losslessly");
        }
        restored.swap(8, 7);

        ItemStack nestedContainer = testStack("minecraft:chest", 1);
        ContainerContentsDraft nestedContents = new ContainerContentsDraft();
        nestedContents.setPresent(true);
        nestedContents.setItem(2, testStack("minecraft:emerald", 5));
        nestedContents.applyTo(nestedContainer);
        draft.setItem(4, nestedContainer);
        draft.applyTo(owner);

        ContainerContentsDraft restoredParent = containerDraft(owner);
        ItemStack restoredChild = restoredParent.item(4);
        ContainerContentsDraft restoredNested = containerDraft(restoredChild);
        if (!restoredNested.present()
            || restoredNested.item(2).getItem() != Items.EMERALD
            || restoredNested.item(2).getCount() != 5) {
            problems.add("minecraft:container draft did not preserve recursively nested contents");
        }
        restoredChild.setCount(9);
        if (restoredParent.item(4).getCount() != 1) {
            problems.add("minecraft:container draft exposed a mutable contained-stack reference");
        }

        ContainerPresentationProfile hopper = ContainerPresentationProfile.resolve(
            testStack("minecraft:hopper", 1),
            -1
        );
        if (hopper.nativeCapacity() != 5 || hopper.width() != 5 || hopper.height() != 1) {
            problems.add("hopper container presentation profile is not a five-slot row");
        }
        ContainerPresentationProfile furnace = ContainerPresentationProfile.resolve(
            testStack("minecraft:furnace", 1),
            -1
        );
        if (furnace.nativeCapacity() != 3
            || furnace.slots().stream().noneMatch(slot -> slot.index() == 2 && slot.x() == 2 && slot.y() == 1)) {
            problems.add("furnace container presentation profile lost its specialized result slot");
        }
        ContainerPresentationProfile shelf = ContainerPresentationProfile.resolve(
            testStack("minecraft:oak_shelf", 1),
            -1
        );
        if (shelf.nativeCapacity() != 3 || shelf.width() != 3 || shelf.height() != 1) {
            problems.add("wood shelf presentation profile is not a three-slot row");
        }
        ContainerPresentationProfile chestOverflow = ContainerPresentationProfile.resolve(
            testStack("minecraft:chest", 1),
            255
        );
        if (!chestOverflow.overflowIndices(255).contains(255)) {
            problems.add("known container presentation profile hid occupied slot 255 instead of exposing overflow");
        }
        ContainerPresentationProfile maximumIndexed = ContainerPresentationProfile.indexed(
            ContainerContentsDraft.MAX_SLOTS
        );
        if (maximumIndexed.width() != 9
            || maximumIndexed.nativeCapacity() != ContainerContentsDraft.MAX_SLOTS) {
            problems.add("maximum indexed container profile exceeded the nine-column responsive grid");
        }
    }

    private static void verifyBundleContentsModel(List<String> problems) {
        ItemStack owner = testStack("minecraft:stone", 1);
        if (BundleContentsDraft.fromStack(owner).present()) {
            problems.add("minecraft:bundle_contents draft confused an absent component with present-empty contents");
        }

        BundleContentsDraft draft = new BundleContentsDraft();
        draft.setPresent(true);
        draft.applyTo(owner);
        BundleContentsDraft presentEmpty = BundleContentsDraft.fromStack(owner);
        if (!presentEmpty.present() || !presentEmpty.isEmpty()) {
            problems.add("minecraft:bundle_contents draft did not preserve a present-empty component");
        }

        ItemStack source = testStack("minecraft:diamond", 3);
        source.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Captured source"));
        ItemStack sourceSnapshot = source.copy();
        SourceInventorySnapshot.SourceSlot sourceSlot = new SourceInventorySnapshot.SourceSlot(
            "test_source", source, 0, 0, 0, "Test source"
        );
        ItemStack deliveredSource = sourceSlot.stack();
        presentEmpty.insertItem(0, deliveredSource);
        if (!ItemStack.matches(sourceSnapshot, sourceSlot.stack())) {
            problems.add("minecraft:bundle_contents insertion mutated its captured source stack");
        }
        deliveredSource.setCount(1);
        deliveredSource.remove(DataComponents.CUSTOM_NAME);
        ItemStack inserted = presentEmpty.item(0);
        if (inserted.getItem() != Items.DIAMOND
            || inserted.getCount() != 3
            || inserted.get(DataComponents.CUSTOM_NAME) == null
            || !ItemStack.matches(sourceSnapshot, inserted)) {
            problems.add("minecraft:bundle_contents present-empty insertion did not preserve the copied source stack");
        }
        presentEmpty.applyTo(owner);
        BundleContentsDraft insertedEmpty = BundleContentsDraft.fromStack(owner);
        if (!insertedEmpty.present()
            || insertedEmpty.size() != 1
            || !ItemStack.matches(sourceSnapshot, insertedEmpty.item(0))) {
            problems.add("minecraft:bundle_contents present-empty insertion did not round-trip at index zero");
        }
        insertedEmpty.insertItem(insertedEmpty.size(), testStack("minecraft:emerald", 2));
        if (insertedEmpty.size() != 2
            || insertedEmpty.item(0).getItem() != Items.DIAMOND
            || insertedEmpty.item(1).getItem() != Items.EMERALD
            || insertedEmpty.item(1).getCount() != 2) {
            problems.add("minecraft:bundle_contents trailing insertion did not append after the existing entries");
        }

        draft.addItem(testStack("minecraft:stone", 7));
        draft.addItem(testStack("minecraft:diamond", 2));
        draft.applyTo(owner);
        BundleContentsDraft restored = BundleContentsDraft.fromStack(owner);
        if (!restored.present()
            || restored.size() != 2
            || restored.item(0).getItem() != Items.STONE
            || restored.item(0).getCount() != 7
            || restored.item(1).getItem() != Items.DIAMOND
            || restored.item(1).getCount() != 2
            || !restored.matchesStack(owner)) {
            problems.add("minecraft:bundle_contents ordered entries did not round-trip through the draft model");
        }

        restored.moveItem(1, 0);
        restored.applyTo(owner);
        BundleContentsDraft reordered = BundleContentsDraft.fromStack(owner);
        if (reordered.item(0).getItem() != Items.DIAMOND
            || reordered.item(1).getItem() != Items.STONE) {
            problems.add("minecraft:bundle_contents draft did not preserve explicit entry ordering");
        }
        ItemStack copiedEntry = reordered.item(0);
        copiedEntry.setCount(9);
        if (reordered.item(0).getCount() != 2) {
            problems.add("minecraft:bundle_contents draft exposed a mutable contained-stack reference");
        }

        ItemStack nestedBundle = testStack("minecraft:bundle", 1);
        BundleContentsDraft nested = BundleContentsDraft.fromStack(nestedBundle);
        nested.setPresent(true);
        nested.addItem(testStack("minecraft:emerald", 1));
        nested.applyTo(nestedBundle);
        BundleContentsDraft parent = new BundleContentsDraft();
        parent.setPresent(true);
        parent.addItem(nestedBundle);
        parent.applyTo(owner);
        BundleContentsDraft restoredParent = BundleContentsDraft.fromStack(owner);
        BundleContentsDraft restoredNested = BundleContentsDraft.fromStack(restoredParent.item(0));
        if (!restoredNested.present()
            || restoredNested.size() != 1
            || restoredNested.item(0).getItem() != Items.EMERALD) {
            problems.add("minecraft:bundle_contents draft did not preserve recursively nested bundle contents");
        }

        BundleContentsDraft overweight = new BundleContentsDraft();
        overweight.setPresent(true);
        overweight.addItem(testStack("minecraft:diamond_sword", 1));
        overweight.addItem(testStack("minecraft:stone", 1));
        if (overweight.totalWeight().floatValue() <= 1.0f) {
            problems.add("minecraft:bundle_contents draft did not detect excess bundle weight");
        }
        try {
            overweight.applyTo(owner);
            ItemStack.validateStrict(owner).getOrThrow();
            BundleContentsDraft restoredOverweight = BundleContentsDraft.fromStack(owner);
            if (restoredOverweight.size() != 2
                || restoredOverweight.totalWeight().floatValue() <= 1.0f) {
                problems.add("minecraft:bundle_contents did not preserve a codec-valid over-capacity list");
            }
        } catch (RuntimeException exception) {
            problems.add("minecraft:bundle_contents rejected a codec-valid over-capacity list: "
                + exception.getMessage());
        }

        BundleContentsDraft disallowed = new BundleContentsDraft();
        disallowed.setPresent(true);
        disallowed.addItem(testStack("minecraft:shulker_box", 1));
        if (disallowed.firstDisallowedItem() != 0) {
            problems.add("minecraft:bundle_contents draft did not identify a disallowed entry");
        }
        try {
            disallowed.applyTo(owner);
            problems.add("minecraft:bundle_contents draft allowed a disallowed entry to be saved");
        } catch (IllegalArgumentException ignored) {
            // Expected.
        }
    }

    private static void verifyOverstackedItemCounts(List<String> problems) {
        ItemStack overstacked = testStack("minecraft:diamond_sword", 1);
        overstacked.setCount(ItemStackCodecs.MAX_SERIALIZED_COUNT);

        try {
            ItemStack.validateStrict(overstacked).getOrThrow();
            problems.add("vanilla strict validation unexpectedly accepted the overstacked test item");
        } catch (RuntimeException ignored) {
            // This is the item-type maximum that the editor intentionally does not enforce.
        }

        try {
            ItemStackCodecs.validateStack(overstacked);
        } catch (RuntimeException exception) {
            problems.add("editor validation rejected a codec-valid overstacked item: "
                + exception.getMessage());
        }

        try {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, vanillaRegistries);
            JsonElement encoded = ItemStack.CODEC.encodeStart(ops, overstacked).getOrThrow();
            ItemStack restored = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
            if (!ItemStack.matches(overstacked, restored)) {
                problems.add("overstacked item count did not round-trip through Minecraft's persistent codec");
            }
        } catch (RuntimeException exception) {
            problems.add("Minecraft's persistent codec rejected count 99: " + exception.getMessage());
        }

        ItemStack containerOwner = testStack("minecraft:chest", 1);
        ContainerContentsDraft container = new ContainerContentsDraft();
        container.setPresent(true);
        container.setItem(0, overstacked);
        container.applyTo(containerOwner);
        try {
            ItemStackCodecs.validateStack(containerOwner);
            if (containerDraft(containerOwner).item(0).getCount()
                != ItemStackCodecs.MAX_SERIALIZED_COUNT) {
                problems.add("container contents changed an overstacked nested item count");
            }
        } catch (RuntimeException exception) {
            problems.add("editor validation rejected an overstacked container entry: "
                + exception.getMessage());
        }

        ItemStack bundleOwner = testStack("minecraft:bundle", 1);
        BundleContentsDraft bundle = new BundleContentsDraft();
        bundle.setPresent(true);
        bundle.addItem(overstacked);
        try {
            bundle.applyTo(bundleOwner);
            ItemStackCodecs.validateStack(bundleOwner);
            if (BundleContentsDraft.fromStack(bundleOwner).item(0).getCount()
                != ItemStackCodecs.MAX_SERIALIZED_COUNT) {
                problems.add("bundle contents changed an overstacked nested item count");
            }
        } catch (RuntimeException exception) {
            problems.add("editor validation rejected an overstacked bundle entry: "
                + exception.getMessage());
        }

        try {
            ItemStack chargedOwner = parseStack("""
                {
                  "id": "minecraft:crossbow",
                  "components": {
                    "minecraft:charged_projectiles": [
                      {"id": "minecraft:diamond_sword", "count": 99}
                    ]
                  }
                }
                """);
            ItemStackCodecs.validateStack(chargedOwner);
            if (chargedOwner.get(DataComponents.CHARGED_PROJECTILES).items().getFirst().count()
                != ItemStackCodecs.MAX_SERIALIZED_COUNT) {
                problems.add("charged projectiles changed an overstacked nested item count");
            }
        } catch (RuntimeException exception) {
            problems.add("editor validation rejected an overstacked charged projectile: "
                + exception.getMessage());
        }

        ItemStack tooLarge = testStack("minecraft:stone", 1);
        tooLarge.setCount(ItemStackCodecs.MAX_SERIALIZED_COUNT + 1);
        try {
            ItemStackCodecs.validateStack(tooLarge);
            problems.add("editor validation accepted an item count above the serialized range");
        } catch (IllegalArgumentException ignored) {
            // Expected.
        }
    }

    private static void verifySnbtStackNumericRoundTrip(List<String> problems) {
        CompoundTag numericTypes = numericTypesFixture();

        ItemStack original = testStack("minecraft:stone", 1);
        original.set(DataComponents.CUSTOM_DATA, CustomData.of(numericTypes));
        try {
            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, vanillaRegistries);
            Tag encoded = ItemStack.CODEC.encodeStart(ops, original).getOrThrow();
            String snbt = new SnbtPrinterTagVisitor("  ", 0, new ArrayList<>()).visit(encoded);
            CompoundTag reparsed = TagParser.parseCompoundFully(snbt);
            ItemStack restored = ItemStack.CODEC.parse(ops, reparsed).getOrThrow();
            CompoundTag restoredData = restored.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

            assertNbtType(problems, restoredData, "byte_value", ByteTag.class);
            assertNbtType(problems, restoredData, "short_value", ShortTag.class);
            assertNbtType(problems, restoredData, "int_value", IntTag.class);
            assertNbtType(problems, restoredData, "long_value", LongTag.class);
            assertNbtType(problems, restoredData, "float_value", FloatTag.class);
            assertNbtType(problems, restoredData, "double_value", DoubleTag.class);
        } catch (Exception exception) {
            problems.add("whole-stack SNBT round trip failed: " + exception.getMessage());
        }
    }

    private static void verifyOpaqueFragmentNumericRoundTrips(List<String> problems) {
        try {
            CompoundTag projectile = ItemStackCodecs.jsonObjectToNbt(
                JsonParser.parseString("{\"id\":\"minecraft:tipped_arrow\"}").getAsJsonObject()
            );
            projectile.put("components", exactCustomDataFixture());
            ListTag projectiles = new ListTag();
            projectiles.add(projectile);
            assertOpaqueNumericTypes(
                problems,
                "charged-projectile components",
                roundTripComponentNbt(DataComponents.CHARGED_PROJECTILES, projectiles)
            );

            CompoundTag adventure = ItemStackCodecs.jsonObjectToNbt(
                JsonParser.parseString("{}").getAsJsonObject()
            );
            adventure.put("components", exactCustomDataFixture());
            adventure.put("predicates", partialCustomDataFixture());
            Tag adventureRoundTrip = roundTripComponentNbt(DataComponents.CAN_BREAK, adventure);
            assertOpaqueNumericTypes(
                problems,
                "adventure exact components",
                compoundField(adventureRoundTrip, "components")
            );
            assertOpaqueNumericTypes(
                problems,
                "adventure component predicates",
                compoundField(adventureRoundTrip, "predicates")
            );

            CompoundTag lock = ItemStackCodecs.jsonObjectToNbt(
                JsonParser.parseString("{}").getAsJsonObject()
            );
            lock.put("components", exactCustomDataFixture());
            lock.put("predicates", partialCustomDataFixture());
            Tag lockRoundTrip = roundTripComponentNbt(DataComponents.LOCK, lock);
            assertOpaqueNumericTypes(
                problems,
                "container-lock exact components",
                compoundField(lockRoundTrip, "components")
            );
            assertOpaqueNumericTypes(
                problems,
                "container-lock component predicates",
                compoundField(lockRoundTrip, "predicates")
            );
        } catch (RuntimeException exception) {
            problems.add("opaque SNBT fragment round trip failed: " + exception.getMessage());
        }
    }

    private static CompoundTag numericTypesFixture() {
        CompoundTag numericTypes = new CompoundTag();
        numericTypes.putByte("byte_value", (byte) 1);
        numericTypes.putShort("short_value", (short) 2);
        numericTypes.putInt("int_value", 3);
        numericTypes.putLong("long_value", 4L);
        numericTypes.putFloat("float_value", 5.25f);
        numericTypes.putDouble("double_value", 6.5d);
        return numericTypes;
    }

    private static CompoundTag exactCustomDataFixture() {
        CompoundTag components = new CompoundTag();
        components.put("minecraft:custom_data", numericTypesFixture());
        return components;
    }

    private static CompoundTag partialCustomDataFixture() {
        CompoundTag predicates = new CompoundTag();
        predicates.put("minecraft:custom_data", numericTypesFixture());
        return predicates;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Tag roundTripComponentNbt(DataComponentType<?> rawType, Tag encoded) {
        DataComponentType type = rawType;
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, vanillaRegistries);
        Object decoded = type.codec().parse(ops, encoded).getOrThrow();
        return (Tag) type.codec().encodeStart(ops, decoded).getOrThrow();
    }

    private static void assertOpaqueNumericTypes(List<String> problems, String label, Tag encoded) {
        CompoundTag numericTypes = findNumericTypesFixture(encoded);
        if (numericTypes == null) {
            problems.add(label + " lost its custom-data numeric fixture: " + encoded);
            return;
        }
        assertOpaqueNbtType(problems, label, numericTypes, "byte_value", ByteTag.class);
        assertOpaqueNbtType(problems, label, numericTypes, "short_value", ShortTag.class);
        assertOpaqueNbtType(problems, label, numericTypes, "int_value", IntTag.class);
        assertOpaqueNbtType(problems, label, numericTypes, "long_value", LongTag.class);
        assertOpaqueNbtType(problems, label, numericTypes, "float_value", FloatTag.class);
        assertOpaqueNbtType(problems, label, numericTypes, "double_value", DoubleTag.class);
    }

    private static Tag compoundField(Tag owner, String field) {
        return owner instanceof CompoundTag compound ? compound.get(field) : null;
    }

    private static CompoundTag findNumericTypesFixture(Tag value) {
        if (value instanceof CompoundTag compound) {
            if (compound.get("byte_value") != null
                && compound.get("short_value") != null
                && compound.get("int_value") != null
                && compound.get("long_value") != null
                && compound.get("float_value") != null
                && compound.get("double_value") != null) {
                return compound;
            }
            for (String key : compound.keySet()) {
                CompoundTag found = findNumericTypesFixture(compound.get(key));
                if (found != null) return found;
            }
        } else if (value instanceof ListTag list) {
            for (Tag entry : list) {
                CompoundTag found = findNumericTypesFixture(entry);
                if (found != null) return found;
            }
        } else if (value instanceof StringTag string) {
            try {
                return findNumericTypesFixture(TagParser.parseCompoundFully(string.value()));
            } catch (Exception ignored) {
                // Ordinary strings are not embedded SNBT documents.
            }
        }
        return null;
    }

    private static void assertOpaqueNbtType(
        List<String> problems,
        String label,
        CompoundTag tag,
        String key,
        Class<? extends Tag> expectedType
    ) {
        Tag value = tag.get(key);
        if (!expectedType.isInstance(value)) {
            problems.add(label + " changed " + key + " to "
                + (value == null ? "missing" : value.getClass().getSimpleName())
                + "; expected " + expectedType.getSimpleName());
        }
    }

    private static void assertNbtType(
        List<String> problems,
        CompoundTag tag,
        String key,
        Class<? extends Tag> expectedType
    ) {
        Tag value = tag.get(key);
        if (!expectedType.isInstance(value)) {
            problems.add(key + " became "
                + (value == null ? "missing" : value.getClass().getSimpleName())
                + " after the whole-stack SNBT round trip; expected " + expectedType.getSimpleName());
        }
    }

    private static void verifyExpandedDefaultDeletion(List<String> problems) {
        ItemStack inherited = testStack("minecraft:stone", 1);
        Set<DataComponentType<?>> expanded = ItemStackCodecs.inheritedPersistentComponents(inherited);
        if (!expanded.contains(DataComponents.MAX_STACK_SIZE)) {
            problems.add("expanded raw view did not identify inherited max_stack_size");
            return;
        }

        ItemStack removed = inherited.copy();
        ItemStackCodecs.applyExpandedDefaultDeletions(removed, expanded, Set.of());
        if (removed.get(DataComponents.MAX_STACK_SIZE) != null
            || !removed.hasNonDefault(DataComponents.MAX_STACK_SIZE)) {
            problems.add("deleting an expanded default did not create an explicit component removal");
        }

        ItemStack retained = inherited.copy();
        retained.set(DataComponents.MAX_STACK_SIZE, inherited.get(DataComponents.MAX_STACK_SIZE));
        ItemStackCodecs.applyExpandedDefaultDeletions(
            retained,
            expanded,
            Set.of("minecraft:max_stack_size")
        );
        if (retained.get(DataComponents.MAX_STACK_SIZE) == null) {
            problems.add("retaining an expanded default was mistaken for deleting it");
        }

        ItemStack modified = inherited.copy();
        modified.set(DataComponents.MAX_STACK_SIZE, 17);
        ItemStackCodecs.applyExpandedDefaultDeletions(
            modified,
            expanded,
            Set.of("minecraft:max_stack_size")
        );
        if (!Integer.valueOf(17).equals(modified.get(DataComponents.MAX_STACK_SIZE))) {
            problems.add("editing an expanded default was mistaken for deleting it");
        }

        try {
            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, vanillaRegistries);
            Tag encoded = ItemStack.CODEC.encodeStart(ops, removed).getOrThrow();
            ItemStack restored = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
            if (restored.get(DataComponents.MAX_STACK_SIZE) != null
                || !restored.hasNonDefault(DataComponents.MAX_STACK_SIZE)) {
                problems.add("expanded-default removal did not survive the persistent item codec");
            }
        } catch (RuntimeException exception) {
            problems.add("expanded-default removal codec round trip failed: " + exception.getMessage());
        }
    }

    private static void verifySulfurCubeContentEditor(List<String> problems) {
        Identifier id = Identifier.tryParse("minecraft:sulfur_cube_content");
        DataComponentType<?> type = id == null ? null : BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
        if ((type != null) != SulfurCubeContentDraft.supported()) {
            problems.add("minecraft:sulfur_cube_content source gate does not match registry availability");
            return;
        }
        if (type == null) return;
        if (!SulfurCubeContentEditorPanel.supports(type)) {
            problems.add("minecraft:sulfur_cube_content is not routed to its focused editor");
        }
        if (ItemEditor.shouldExcludeAsDedicatedComponent(type)) {
            problems.add("minecraft:sulfur_cube_content is hidden by the dedicated-editor filter");
        }

        //? if >=26.2 {
        ItemStack owner = testStack("minecraft:sulfur_cube_bucket", 1);
        ItemStack absorbed = testStack("minecraft:chest", 6);
        absorbed.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Absorbed"));
        absorbed.remove(DataComponents.ITEM_MODEL);
        SulfurCubeContentDraft draft = new SulfurCubeContentDraft();
        draft.setPresent(true);
        draft.setStack(absorbed);
        absorbed.setCount(1);
        if (draft.stack().getCount() != 6) {
            problems.add("minecraft:sulfur_cube_content draft did not defensively copy its input stack");
        }
        draft.applyTo(owner);

        SulfurCubeContentDraft restored = SulfurCubeContentDraft.fromStack(owner);
        if (!restored.present()
            || restored.stack().getCount() != 6
            || restored.stack().has(DataComponents.ITEM_MODEL)
            || !"Absorbed".equals(restored.stack().getHoverName().getString())
            || !restored.matchesStack(owner)) {
            problems.add("minecraft:sulfur_cube_content did not preserve the nested stack template");
        }
        ItemStack exposed = restored.stack();
        exposed.setCount(1);
        if (restored.stack().getCount() != 6) {
            problems.add("minecraft:sulfur_cube_content draft exposed a mutable nested-stack reference");
        }
        restored.setPresent(false);
        restored.applyTo(owner);
        if (owner.has(DataComponents.SULFUR_CUBE_CONTENT)) {
            problems.add("minecraft:sulfur_cube_content removal did not remove the component");
        }
        //?}
    }

    private static ItemStack testStack(String id, int count) {
        return parseStack("{\"id\":\"" + id + "\",\"count\":" + count + "}");
    }

    private static ContainerContentsDraft containerDraft(ItemStack owner) {
        return ContainerContentsDraft.fromStack(
            owner,
            RegistryOps.create(JsonOps.INSTANCE, vanillaRegistries)
        );
    }

    private static ItemStack parseStack(String source) {
        return ItemStack.CODEC.parse(
            RegistryOps.create(JsonOps.INSTANCE, vanillaRegistries),
            JsonParser.parseString(source)
        ).getOrThrow();
    }

    private static void verifyFireworkEditor(List<String> problems) {
        verifyDedicatedSample(
            problems,
            "minecraft:firework_explosion",
            DataComponents.FIREWORK_EXPLOSION,
            """
                {
                  "shape": "star",
                  "colors": [16711680, 255],
                  "fade_colors": [16777215],
                  "has_trail": true,
                  "has_twinkle": true
                }
                """
        );
        verifyDedicatedSample(
            problems,
            "minecraft:fireworks",
            DataComponents.FIREWORKS,
            """
                {
                  "flight_duration": 3,
                  "explosions": [{
                    "shape": "creeper",
                    "colors": [65280],
                    "fade_colors": [16777215],
                    "has_trail": true,
                    "has_twinkle": false
                  }]
                }
                """
        );
    }

    private static void verifyPlayerHeadEditor(List<String> problems) {
        verifyDedicatedSample(
            problems,
            "minecraft:profile",
            DataComponents.PROFILE,
            """
                {
                  "name": "Player",
                  "id": [0, 0, 0, 0],
                  "properties": [{
                    "name": "textures",
                    "value": "eyJ0ZXh0dXJlcyI6e319",
                    "signature": "unsigned-editor-sample"
                  }],
                  "texture": "minecraft:entity/player/wide/steve",
                  "cape": "minecraft:entity/player/wide/steve",
                  "elytra": "minecraft:entity/player/wide/steve",
                  "model": "wide"
                }
                """
        );
        try {
            var ops = RegistryOps.create(JsonOps.INSTANCE, vanillaRegistries);
            JsonElement source = JsonParser.parseString("""
                {
                  "name": "Player",
                  "properties": [
                    {"name": "textures", "value": "first"},
                    {"name": "textures", "value": "second"}
                  ]
                }
                """);
            var decoded = DataComponents.PROFILE.codec().parse(ops, source).getOrThrow();
            JsonElement reencoded = DataComponents.PROFILE.codec()
                .encodeStart(ops, decoded)
                .getOrThrow();
            JsonArray properties = reencoded.getAsJsonObject().getAsJsonArray("properties");
            if (properties == null
                || properties.size() != 2
                || !"first".equals(properties.get(0).getAsJsonObject().get("value").getAsString())
                || !"second".equals(properties.get(1).getAsJsonObject().get("value").getAsString())) {
                problems.add("minecraft:profile codec did not preserve duplicate property ordering");
            }
        } catch (RuntimeException exception) {
            problems.add("minecraft:profile property-order check failed: " + exception.getMessage());
        }
    }

    private static void verifyAdventureEditor(List<String> problems) {
        String sample = """
            {
              "state": {
                "age": {"min": "1", "max": "3"},
                "powered": "true"
              },
              "nbt": "{Lock:Key}",
              "components": {},
              "predicates": {}
            }
            """;
        verifyAdventureComponent(problems, "minecraft:can_break", DataComponents.CAN_BREAK, sample);
        verifyAdventureComponent(problems, "minecraft:can_place_on", DataComponents.CAN_PLACE_ON, sample);
    }

    private static void verifyRecipesEditor(List<String> problems) {
        if (!RecipesEditorPanel.supports(DataComponents.RECIPES)) {
            problems.add("minecraft:recipes is not routed to its focused editor");
        }
        if (ItemEditor.shouldExcludeAsDedicatedComponent(DataComponents.RECIPES)) {
            problems.add("minecraft:recipes is hidden by the dedicated-editor filter");
        }
        if (RecipeResourceCatalog.load().stream()
            .noneMatch(suggestion -> suggestion.id().equals("minecraft:acacia_boat"))) {
            problems.add("bundled recipe catalog did not discover minecraft:acacia_boat");
        }

        String encoded = "[\"minecraft:acacia_boat\",\"minecraft:crafting_table\",\"minecraft:acacia_boat\"]";
        try {
            DataComponents.RECIPES.codec().parse(
                RegistryOps.create(JsonOps.INSTANCE, vanillaRegistries),
                JsonParser.parseString(encoded)
            ).getOrThrow();
            DataComponents.RECIPES.codec().parse(
                RegistryOps.create(JsonOps.INSTANCE, vanillaRegistries),
                JsonParser.parseString("[]")
            ).getOrThrow();
            DataComponents.RECIPES.codec().parse(
                RegistryOps.create(JsonOps.INSTANCE, vanillaRegistries),
                JsonParser.parseString("[\"example:missing_recipe\"]")
            ).getOrThrow();
        } catch (RuntimeException exception) {
            problems.add("minecraft:recipes editor sample is invalid: " + exception.getMessage());
        }

        RecipesDraft ordered = RecipesDraft.fromJson(true, JsonParser.parseString(encoded));
        if (!ordered.componentPresent()
            || ordered.recipeIds().size() != 3
            || ordered.duplicateCount() != 1
            || !ordered.toJson().equals(JsonParser.parseString(encoded))) {
            problems.add("minecraft:recipes draft did not preserve presence, order, or duplicates");
        }
        if (RecipesDraft.fromJson(false, JsonParser.parseString("[]")).componentPresent()) {
            problems.add("minecraft:recipes draft confused absent with present-empty state");
        }
        if (ordered.withoutDuplicates().recipeIds().size() != 2) {
            problems.add("minecraft:recipes explicit de-duplication did not preserve first entries");
        }
        Identifier missing = Identifier.tryParse("example:missing_recipe");
        if (missing == null || ordered.withMissing(List.of(missing)).recipeIds().size() != 4) {
            problems.add("minecraft:recipes bulk addition did not append a missing identifier");
        }
        try {
            RecipesDraft.fromStrings(true, List.of("not a valid identifier"));
            problems.add("minecraft:recipes draft accepted invalid identifier syntax");
        } catch (IllegalArgumentException ignored) {
            // Expected.
        }
    }

    private static void verifyBeesEditor(List<String> problems) {
        if (!BeesEditorPanel.supports(DataComponents.BEES)) {
            problems.add("minecraft:bees is not routed to its focused editor");
        }
        if (ItemEditor.shouldExcludeAsDedicatedComponent(DataComponents.BEES)) {
            problems.add("minecraft:bees is hidden by the dedicated-editor filter");
        }

        ItemStack owner = testStack("minecraft:stone", 1);
        BeesDraft absent = BeesDraft.fromStack(owner);
        if (absent.present() || !absent.isEmpty()) {
            problems.add("minecraft:bees draft confused an absent component with present-empty");
        }

        BeesDraft empty = new BeesDraft();
        empty.setPresent(true);
        empty.applyTo(owner);
        BeesDraft restoredEmpty = BeesDraft.fromStack(owner);
        if (!restoredEmpty.present() || !restoredEmpty.isEmpty()) {
            problems.add("minecraft:bees did not preserve a present-empty component");
        }

        try {
            var custom = TagParser.parseCompoundFully(
                "{Health:12.5f,Nested:{Flag:1b},Tags:[\"alpha\",\"beta\"]}"
            );
            BeesDraft ordered = new BeesDraft();
            ordered.setPresent(true);
            ordered.add(new BeesDraft.Occupant("minecraft:bee", custom, -20, 600));
            ordered.add(new BeesDraft.Occupant("minecraft:cow", TagParser.parseCompoundFully("{Age:-10}"), 0, Integer.MAX_VALUE));
            ordered.add(BeesDraft.normalBee());
            ordered.add(new BeesDraft.Occupant("minecraft:bee", TagParser.parseCompoundFully("{NoAI:1b}"), 40, 80));
            ordered.applyTo(owner);
            ItemStack.validateStrict(owner).getOrThrow();

            BeesDraft restored = BeesDraft.fromStack(owner);
            if (!restored.present() || restored.size() != 4) {
                problems.add("minecraft:bees did not preserve an over-capacity occupant list");
            } else {
                BeesDraft.Occupant first = restored.occupant(0);
                BeesDraft.Occupant second = restored.occupant(1);
                if (!first.entityType().equals("minecraft:bee")
                    || first.ticksInHive() != -20
                    || first.minTicksInHive() != 600
                    || !first.entityData().equals(custom)
                    || !second.entityType().equals("minecraft:cow")
                    || second.minTicksInHive() != Integer.MAX_VALUE) {
                    problems.add("minecraft:bees did not preserve entity data, timing, or occupant order");
                }
                var exposed = first.entityData();
                exposed.putInt("Mutated", 1);
                if (restored.occupant(0).entityData().contains("Mutated")) {
                    problems.add("minecraft:bees draft exposed a mutable entity-data reference");
                }

                restored.insert(1, restored.occupant(0));
                restored.move(1, 2);
                restored.remove(2);
                if (restored.size() != 4 || !restored.occupant(0).entityType().equals("minecraft:bee")) {
                    problems.add("minecraft:bees list insertion, movement, or removal failed");
                }
            }

            BeesDraft removed = BeesDraft.fromStack(owner);
            removed.setPresent(false);
            removed.applyTo(owner);
            if (owner.has(DataComponents.BEES)) {
                problems.add("minecraft:bees removal did not remove the component");
            }
        } catch (Exception exception) {
            problems.add("minecraft:bees editor sample is invalid: " + exception.getMessage());
        }
    }

    private static void verifyComponentRecommendations(List<String> problems) {
        if (ComponentRecommendations.classify(
            testStack("minecraft:stone", 1),
            "minecraft:lore"
        ) != ComponentRecommendations.Kind.FREQUENTLY_USED) {
            problems.add("common component recommendation did not include minecraft:lore");
        }
        if (ComponentRecommendations.classify(
            testStack("minecraft:bee_nest", 1),
            "minecraft:bees"
        ) != ComponentRecommendations.Kind.RELATED) {
            problems.add("bee nest recommendation did not include minecraft:bees");
        }
        if (ComponentRecommendations.classify(
            testStack("minecraft:chest", 1),
            "minecraft:block_entity_data"
        ) != ComponentRecommendations.Kind.RELATED) {
            problems.add("block entity recommendation did not include minecraft:block_entity_data");
        }
        if (ComponentRecommendations.classify(
            testStack("minecraft:diamond_helmet", 1),
            "minecraft:trim"
        ) != ComponentRecommendations.Kind.RELATED) {
            problems.add("armor recommendation did not include minecraft:trim");
        }
        if (ComponentRecommendations.classify(
            testStack("minecraft:stone", 1),
            "minecraft:sulfur_cube_content"
        ) != ComponentRecommendations.Kind.NONE) {
            problems.add("unrelated stone item recommended minecraft:sulfur_cube_content");
        }
        //? if >=26.2 {
        if (ComponentRecommendations.classify(
            testStack("minecraft:sulfur_cube_bucket", 1),
            "minecraft:sulfur_cube_content"
        ) != ComponentRecommendations.Kind.RELATED) {
            problems.add("sulfur cube bucket recommendation did not include its content component");
        }
        //?}
    }

    private static void verifyAdventureComponent(
        List<String> problems,
        String id,
        DataComponentType<?> type,
        String sample
    ) {
        if (!AdventureEditorPanel.supports(type)) {
            problems.add(id + " is not routed to the adventure editor");
        }
        DedicatedEditorSchema.Coverage coverage = DedicatedEditorSchema.coverage(type);
        if (!coverage.isExact()) {
            problems.add(id + " adventure-editor coverage is not exact");
        }
        try {
            type.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(sample)).getOrThrow();
            type.codec().parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("[" + sample + "," + sample + "]")
            ).getOrThrow();
        } catch (RuntimeException exception) {
            problems.add(id + " adventure-editor sample is invalid: " + exception.getMessage());
        }
    }

    private static void verifyDataEditor(List<String> problems, List<ComponentOption> components) {
        verifyDataEditorSample(
            problems,
            "minecraft:custom_data",
            DataComponents.CUSTOM_DATA,
            "{\"itemlab_test\":true}"
        );
        verifyDataEditorSample(
            problems,
            "minecraft:entity_data",
            DataComponents.ENTITY_DATA,
            "{\"id\":\"minecraft:pig\",\"Health\":10.0}"
        );
        verifyDataEditorSample(
            problems,
            "minecraft:bucket_entity_data",
            DataComponents.BUCKET_ENTITY_DATA,
            "{\"Health\":10.0}"
        );
        verifyDataEditorSample(
            problems,
            "minecraft:block_entity_data",
            DataComponents.BLOCK_ENTITY_DATA,
            "{\"id\":\"minecraft:chest\",\"CustomName\":\"Chest\"}"
        );
        verifyDataEditorSample(
            problems,
            "minecraft:block_state",
            DataComponents.BLOCK_STATE,
            "{\"facing\":\"north\",\"waterlogged\":\"false\"}"
        );
        verifyDataEditorSample(
            problems,
            "minecraft:debug_stick_state",
            DataComponents.DEBUG_STICK_STATE,
            "{\"minecraft:oak_log\":\"axis\"}"
        );
        for (ComponentOption option : components) {
            if (option.type().codec() == null && DataEditorPanel.supports(option.type())) {
                problems.add(option.id() + " network-only component is routed to the data editor");
            }
        }
    }

    private static void verifyDedicatedComponentFilter(List<String> problems) {
        if (ItemEditor.shouldExcludeAsDedicatedComponent(DataComponents.CAN_BREAK)) {
            problems.add("minecraft:can_break is hidden by the dedicated-editor filter");
        }
        if (ItemEditor.shouldExcludeAsDedicatedComponent(DataComponents.CAN_PLACE_ON)) {
            problems.add("minecraft:can_place_on is hidden by the dedicated-editor filter");
        }

        if (!CombatEditorPanel.supports(DataComponents.TOOL)
            || !ItemEditor.shouldExcludeAsDedicatedComponent(DataComponents.TOOL)) {
            problems.add("minecraft:tool is not recognized in the Combat section");
        }
    }

    private static void verifyComponentCoverage(
        List<String> problems,
        List<ComponentOption> components
    ) {
        Map<ComponentCoverage.Category, Integer> counts = new EnumMap<>(ComponentCoverage.Category.class);
        Map<String, ComponentOption> byId = new java.util.HashMap<>();
        for (ComponentOption option : components) {
            byId.put(option.id(), option);
            boolean excludedAsTopLevel = ItemEditor.shouldExcludeAsDedicatedComponent(option.type());
            ComponentCoverage.Category category = ComponentCoverage.classify(option, excludedAsTopLevel);
            counts.merge(category, 1, Integer::sum);
            boolean persistent = option.type().codec() != null;
            if (category == ComponentCoverage.Category.UNCLASSIFIED) {
                problems.add(option.id() + " is not classified in the component coverage manifest");
            } else if ((category == ComponentCoverage.Category.NETWORK_ONLY) == persistent) {
                problems.add(option.id() + " coverage persistence classification disagrees with its codec");
            }
            if (category == ComponentCoverage.Category.INLINE_DEDICATED && excludedAsTopLevel) {
                problems.add(option.id() + " inline editor is hidden by Exclude dedicated");
            }
            if ((category == ComponentCoverage.Category.GENERATED_STRUCTURED
                || category == ComponentCoverage.Category.RAW_PERSISTENT_FALLBACK)
                && excludedAsTopLevel) {
                problems.add(option.id() + " fallback editor is hidden by Exclude dedicated");
            }
            if (category == ComponentCoverage.Category.TOP_LEVEL_DEDICATED && !excludedAsTopLevel) {
                problems.add(option.id() + " top-level editor is not hidden by Exclude dedicated");
            }
        }

        for (VariantComponents.Definition definition : VariantComponents.definitions()) {
            ComponentOption option = byId.get(definition.componentId());
            if (option == null) {
                problems.add(definition.componentId() + " variant metadata has no registered component");
                continue;
            }
            List<String> values = definition.registryBacked()
                ? List.of(definition.fallbackValue())
                : definition.fixedChoices().stream().map(IdSuggestion::id).toList();
            for (String value : values) {
                verifyVariantCodec(
                    problems,
                    definition.componentId(),
                    option.type(),
                    value,
                    !definition.registryBacked()
                );
            }
        }

        List<IdSuggestion> modded = VariantComponents.suggestions(
            "minecraft:wolf/variant",
            Map.of("wolf_variant", List.of(IdSuggestion.of("examplemod:night_wolf")))
        );
        if (modded.size() != 1
            || !"examplemod:night_wolf".equals(modded.getFirst().id())
            || !modded.getFirst().searchable().contains("night wolf")) {
            problems.add("registry-backed variant suggestions do not retain modded values and friendly search text");
        }

        for (String colorComponent : List.of(
            "minecraft:cat/collar",
            "minecraft:sheep/color",
            "minecraft:shulker/color",
            "minecraft:tropical_fish/base_color",
            "minecraft:tropical_fish/pattern_color",
            "minecraft:wolf/collar"
        )) {
            if (ComponentFormPanel.literalSuggestionsForComponent(colorComponent).size() != 16) {
                problems.add(colorComponent + " is missing its generated color choices");
            }
        }

        for (String inlineId : InlineComponentEditorRegistry.registeredIds()) {
            if (!byId.containsKey(inlineId)
                && !(inlineId.equals("minecraft:sulfur_cube_content")
                && !EditorVersionProfile.hasComponent(inlineId))) {
                problems.add(inlineId + " inline editor registration has no component in this target");
            }
        }

        LOGGER.info(
            "Component coverage for Minecraft {}: {}",
            EditorVersionProfile.targetVersion(),
            counts
        );
        for (ComponentCoverage.Category category : ComponentCoverage.Category.values()) {
            List<String> ids = components.stream()
                .filter(option -> ComponentCoverage.classify(
                    option,
                    ItemEditor.shouldExcludeAsDedicatedComponent(option.type())
                ) == category)
                .map(ComponentOption::id)
                .sorted()
                .toList();
            if (!ids.isEmpty()) LOGGER.debug("{}: {}", category, ids);
        }
    }

    private static <T> void verifyVariantCodec(
        List<String> problems,
        String componentId,
        DataComponentType<T> type,
        String value,
        boolean verifyRoundTrip
    ) {
        try {
            var ops = RegistryOps.create(JsonOps.INSTANCE, vanillaRegistries);
            JsonPrimitive encoded = new JsonPrimitive(value);
            T decoded = type.codec().parse(ops, encoded).getOrThrow();
            if (!verifyRoundTrip) return;
            JsonElement reencoded = type.codec().encodeStart(ops, decoded).getOrThrow();
            T decodedAgain = type.codec().parse(ops, reencoded).getOrThrow();
            if (!decoded.equals(decodedAgain)) {
                problems.add(componentId + " variant " + value + " changed during codec round trip");
            }
        } catch (RuntimeException exception) {
            problems.add(componentId + " variant " + value + " is invalid: " + exception.getMessage());
        }
    }

    private static void verifyDataEditorSample(
        List<String> problems,
        String id,
        DataComponentType<?> type,
        String sample
    ) {
        if (!DataEditorPanel.supports(type)) {
            problems.add(id + " is not routed to the data editor");
            return;
        }
        try {
            type.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(sample)).getOrThrow();
        } catch (RuntimeException exception) {
            problems.add(id + " data editor sample is invalid: " + exception.getMessage());
        }
    }

    private static void verifyCodeEditorErrorLines(List<String> problems) {
        String json = "{\n  \"value\": ]\n}";
        try {
            JsonParser.parseString(json);
            problems.add("invalid JSON code-editor sample was accepted");
        } catch (RuntimeException exception) {
            int line = CodeEditorInput.errorLine(exception, json);
            if (line != 2) problems.add("JSON code-editor error line is " + line + " instead of 2");
        }

        String snbt = "{\n  value: ]\n}";
        try {
            TagParser.parseCompoundFully(snbt);
            problems.add("invalid SNBT code-editor sample was accepted");
        } catch (Exception exception) {
            int line = CodeEditorInput.errorLine(exception, snbt);
            if (line != 2) problems.add("SNBT code-editor error line is " + line + " instead of 2");
        }
    }

    private static void verifyEditorShortcutPolicy(List<String> problems) {
        if (EditorShortcutPolicy.suppresses(false, false)) {
            problems.add("editor shortcuts were suppressed without an active input");
        }
        if (!EditorShortcutPolicy.suppresses(true, false)) {
            problems.add("editor shortcuts remained active while a control was active");
        }
        if (!EditorShortcutPolicy.suppresses(false, true)) {
            problems.add("editor shortcuts remained active while text input was requested");
        }
        if (!EditorShortcutPolicy.suppresses(true, true)) {
            problems.add("editor shortcuts remained active while a text control was active");
        }
    }

    private static void verifyComponentSectionInitialState(List<String> problems) {
        if (!ComponentSectionInitialState.resolve(false, false, ComponentState.ABSENT)) {
            problems.add("absent component collapsed while the layout preference was disabled");
        }
        if (ComponentSectionInitialState.resolve(true, false, ComponentState.ABSENT)) {
            problems.add("absent component did not collapse while the layout preference was enabled");
        }
        for (ComponentState state : List.of(
            ComponentState.DEFAULT,
            ComponentState.OVERRIDDEN,
            ComponentState.REMOVED
        )) {
            if (!ComponentSectionInitialState.resolve(true, false, state)) {
                problems.add(state + " component did not remain expanded");
            }
        }
        if (!ComponentSectionInitialState.resolve(
            true,
            false,
            ComponentState.ABSENT,
            ComponentState.DEFAULT
        )) {
            problems.add("multi-component section did not expand when one component was present");
        }
        if (!ComponentSectionInitialState.resolve(true, true, ComponentState.ABSENT)) {
            problems.add("focused Components-tab editor collapsed an absent component");
        }
    }

    private static void verifyGeneratedComponentFormInfrastructure(List<String> problems) {
        verifyGeneratedRoundTrip(problems, "primitive rarity", DataComponents.RARITY, "\"rare\"");
        verifyGeneratedRoundTrip(
            problems,
            "object and scalar lists",
            DataComponents.CUSTOM_MODEL_DATA,
            "{\"floats\":[1.0],\"flags\":[true],\"strings\":[\"examplemod:widget\"],\"colors\":[-1]}"
        );
        verifyGeneratedRoundTrip(
            problems,
            "nested firework explosion",
            DataComponents.FIREWORKS,
            "{\"flight_duration\":2,\"explosions\":[{\"shape\":\"star\",\"colors\":[16711680],"
                + "\"fade_colors\":[255],\"has_trail\":true,\"has_twinkle\":false}]}"
        );
        verifyGeneratedRoundTrip(
            problems,
            "optional food property",
            DataComponents.FOOD,
            "{\"nutrition\":4,\"saturation\":0.3}"
        );
        verifyGeneratedRoundTrip(
            problems,
            "custom modded identifier",
            DataComponents.CUSTOM_DATA,
            "{\"modded_id\":\"examplemod:widget\"}"
        );

        ComponentFormPanel emptyScalar = new ComponentFormPanel(null);
        emptyScalar.load(new JsonPrimitive(""));
        if (emptyScalar.isDirty()) {
            problems.add("loading an empty generated scalar marked the form dirty before user input");
        }

        JsonArray rows = JsonParser.parseString(
            "[{\"value\":1},{\"value\":2},{\"value\":3}]"
        ).getAsJsonArray();
        List<Long> rowIds = new ArrayList<>(List.of(11L, 12L, 13L));
        JsonListMutations.Mutation moved = JsonListMutations.apply(
            rows,
            rowIds,
            1,
            EditorUi.ListAction.MOVE_UP,
            () -> 99L
        );
        if (!moved.changed()
            || moved.focusRowId() != 12L
            || !rowIds.equals(List.of(12L, 11L, 13L))
            || rows.get(0).getAsJsonObject().get("value").getAsInt() != 2) {
            problems.add("generated list move did not preserve row identity");
        }
        JsonListMutations.Mutation duplicated = JsonListMutations.apply(
            rows,
            rowIds,
            0,
            EditorUi.ListAction.DUPLICATE,
            () -> 99L
        );
        rows.get(1).getAsJsonObject().addProperty("value", 20);
        if (!duplicated.changed()
            || duplicated.focusRowId() != 99L
            || !rowIds.equals(List.of(12L, 99L, 11L, 13L))
            || rows.get(0).getAsJsonObject().get("value").getAsInt() != 2) {
            problems.add("generated list duplicate did not deep-copy or assign a stable identity");
        }
        JsonListMutations.Mutation removed = JsonListMutations.apply(
            rows,
            rowIds,
            1,
            EditorUi.ListAction.REMOVE,
            () -> 100L
        );
        if (!removed.changed()
            || removed.focusRowId() != 11L
            || !rowIds.equals(List.of(12L, 11L, 13L))
            || rows.size() != 3) {
            problems.add("generated list removal did not preserve adjacent focus identity");
        }

        JsonArray insertionRows = JsonParser.parseString(
            "[{\"value\":1},{\"value\":3}]"
        ).getAsJsonArray();
        List<Long> insertionRowIds = new ArrayList<>(List.of(21L, 23L));
        JsonListMutations.Mutation inserted = JsonListMutations.apply(
            insertionRows,
            insertionRowIds,
            0,
            EditorUi.ListAction.INSERT_AFTER,
            () -> 22L,
            () -> JsonParser.parseString("{\"value\":2}")
        );
        if (!inserted.changed()
            || inserted.focusRowId() != 22L
            || !insertionRowIds.equals(List.of(21L, 22L, 23L))
            || insertionRows.get(1).getAsJsonObject().get("value").getAsInt() != 2) {
            problems.add("shared JSON list insertion did not add and focus a fresh adjacent row");
        }

        OrderedListDragDrop.Scope jsonScope = new OrderedListDragDrop.Scope(71L, "json_rows");
        JsonArray reorderedRows = JsonParser.parseString("[1,2,3,4]").getAsJsonArray();
        List<Long> reorderedIds = new ArrayList<>(List.of(1L, 2L, 3L, 4L));
        JsonListMutations.Mutation movedToEnd = JsonListMutations.reorder(
            reorderedRows,
            reorderedIds,
            new OrderedListDragDrop.ReorderRequest(jsonScope, 2L, 4)
        );
        JsonListMutations.Mutation movedToStart = JsonListMutations.reorder(
            reorderedRows,
            reorderedIds,
            new OrderedListDragDrop.ReorderRequest(jsonScope, 4L, 0)
        );
        String jsonBeforeNoOps = reorderedRows.toString();
        JsonListMutations.Mutation beforeSource = JsonListMutations.reorder(
            reorderedRows,
            reorderedIds,
            new OrderedListDragDrop.ReorderRequest(jsonScope, 3L, 2)
        );
        JsonListMutations.Mutation afterSource = JsonListMutations.reorder(
            reorderedRows,
            reorderedIds,
            new OrderedListDragDrop.ReorderRequest(jsonScope, 3L, 3)
        );
        JsonListMutations.Mutation staleSource = JsonListMutations.reorder(
            reorderedRows,
            reorderedIds,
            new OrderedListDragDrop.ReorderRequest(jsonScope, 99L, 0)
        );
        if (!movedToEnd.changed()
            || movedToEnd.focusRowId() != 2L
            || !movedToStart.changed()
            || movedToStart.focusRowId() != 4L
            || !reorderedIds.equals(List.of(4L, 1L, 3L, 2L))
            || !"[4,1,3,2]".equals(reorderedRows.toString())
            || beforeSource.changed()
            || afterSource.changed()
            || staleSource.changed()
            || !jsonBeforeNoOps.equals(reorderedRows.toString())) {
            problems.add("shared JSON arbitrary reorder lost boundary, identity, or no-op semantics");
        }

        if (!ComponentFormPanel.numberProblem("12.5").isBlank()
            || ComponentFormPanel.numberProblem("1e").isBlank()) {
            problems.add("generated numeric fields do not distinguish valid and intermediate invalid input");
        }
    }

    private static void verifyEditorListMutations(List<String> problems) {
        List<String> rows = new ArrayList<>(List.of("first", "second", "third"));
        EditorListMutations.Mutation<String> moved = EditorListMutations.apply(
            rows,
            1,
            EditorUi.ListAction.MOVE_UP,
            value -> value + " copy"
        );
        if (!moved.changed()
            || !"second".equals(moved.focusRow())
            || !rows.equals(List.of("second", "first", "third"))) {
            problems.add("shared list move did not preserve row identity and focus");
        }

        EditorListMutations.Mutation<String> duplicated = EditorListMutations.apply(
            rows,
            0,
            EditorUi.ListAction.DUPLICATE,
            value -> value + " copy"
        );
        if (!duplicated.changed()
            || !"second copy".equals(duplicated.focusRow())
            || !rows.equals(List.of("second", "second copy", "first", "third"))) {
            problems.add("shared list duplicate did not focus the new row");
        }

        List<String> insertionRows = new ArrayList<>(List.of("first", "third"));
        EditorListMutations.Mutation<String> inserted = EditorListMutations.apply(
            insertionRows,
            0,
            EditorUi.ListAction.INSERT_AFTER,
            value -> value + " copy",
            () -> "second"
        );
        if (!inserted.changed()
            || !"second".equals(inserted.focusRow())
            || !insertionRows.equals(List.of("first", "second", "third"))) {
            problems.add("shared list insertion did not add and focus a fresh adjacent row");
        }

        EditorListMutations.Mutation<String> removed = EditorListMutations.apply(
            rows,
            1,
            EditorUi.ListAction.REMOVE,
            null
        );
        if (!removed.changed()
            || !"first".equals(removed.focusRow())
            || !rows.equals(List.of("second", "first", "third"))) {
            problems.add("shared list removal did not focus the adjacent row");
        }

        EditorListMutations.Mutation<String> rejected = EditorListMutations.apply(
            rows,
            0,
            EditorUi.ListAction.MOVE_UP,
            null
        );
        if (rejected.changed() || !rows.equals(List.of("second", "first", "third"))) {
            problems.add("shared list mutation accepted an invalid boundary move");
        }

        List<String> singleton = new ArrayList<>(List.of("only"));
        EditorListMutations.Mutation<String> emptied = EditorListMutations.apply(
            singleton,
            0,
            EditorUi.ListAction.REMOVE,
            null
        );
        if (!emptied.changed() || emptied.focusRow() != null || !singleton.isEmpty()) {
            problems.add("shared list removal did not report the empty-list focus fallback");
        }

        OrderedListDragDrop.Scope scope = new OrderedListDragDrop.Scope(81L, "rows");
        List<String> reordered = new ArrayList<>(List.of("first", "second", "third", "fourth"));
        Map<String, Long> stableIds = Map.of(
            "first", 1L,
            "second", 2L,
            "third", 3L,
            "fourth", 4L
        );
        EditorListMutations.Mutation<String> movedToEnd = EditorListMutations.reorder(
            reordered,
            new OrderedListDragDrop.ReorderRequest(scope, 2L, 4),
            stableIds::get
        );
        EditorListMutations.Mutation<String> movedToStart = EditorListMutations.reorder(
            reordered,
            new OrderedListDragDrop.ReorderRequest(scope, 4L, 0),
            stableIds::get
        );
        List<String> beforeNoOps = List.copyOf(reordered);
        EditorListMutations.Mutation<String> beforeSource = EditorListMutations.reorder(
            reordered,
            new OrderedListDragDrop.ReorderRequest(scope, 3L, 2),
            stableIds::get
        );
        EditorListMutations.Mutation<String> afterSource = EditorListMutations.reorder(
            reordered,
            new OrderedListDragDrop.ReorderRequest(scope, 3L, 3),
            stableIds::get
        );
        EditorListMutations.Mutation<String> staleSource = EditorListMutations.reorder(
            reordered,
            new OrderedListDragDrop.ReorderRequest(scope, 99L, 0),
            stableIds::get
        );
        if (!movedToEnd.changed()
            || !"second".equals(movedToEnd.focusRow())
            || !movedToStart.changed()
            || !"fourth".equals(movedToStart.focusRow())
            || !reordered.equals(List.of("fourth", "first", "third", "second"))
            || beforeSource.changed()
            || afterSource.changed()
            || staleSource.changed()
            || !beforeNoOps.equals(reordered)) {
            problems.add("shared object-list arbitrary reorder lost boundary, identity, or no-op semantics");
        }

        OrderedListDragDrop.Payload payload = new OrderedListDragDrop.Payload(scope, 2L);
        OrderedListDragDrop.Scope sibling = new OrderedListDragDrop.Scope(81L, "sibling");
        OrderedListDragDrop.Scope staleSession = new OrderedListDragDrop.Scope(82L, "rows");
        OrderedListDragDrop.Scope nested = scope.nested(1L, "children");
        OrderedListDragDrop.Scope otherNested = scope.nested(2L, "children");
        if (!OrderedListDragDrop.accepts(scope, payload)
            || OrderedListDragDrop.accepts(sibling, payload)
            || OrderedListDragDrop.accepts(staleSession, payload)
            || OrderedListDragDrop.accepts(nested, payload)
            || nested.equals(otherNested)) {
            problems.add("ordered-list drag payload scopes do not isolate sibling, nested, or stale sessions");
        }
    }

    private static void verifyIdentifierCollectionFocus(List<String> problems) {
        IdentifierCollectionEditor.FocusState focus = new IdentifierCollectionEditor.FocusState();
        focus.requestRow(2);
        if (focus.consumeRow(1) || !focus.consumeRow(2) || focus.consumeRow(2)) {
            problems.add("identifier collection focus was not consumed by its requested row");
        }

        focus.requestRow(-1);
        if (!focus.consumeRow(0)) {
            problems.add("identifier collection focus did not clamp to the first row");
        }

        focus.requestRow(1);
        focus.clear();
        if (focus.consumeRow(1)) {
            problems.add("identifier collection focus survived an editor reset");
        }
    }

    private static <T> void verifyGeneratedRoundTrip(
        List<String> problems,
        String fixture,
        DataComponentType<T> type,
        String sample
    ) {
        try {
            var ops = RegistryOps.create(JsonOps.INSTANCE, vanillaRegistries);
            JsonElement encoded = JsonParser.parseString(sample);
            T decoded = type.codec().parse(ops, encoded).getOrThrow();
            JsonElement reencoded = type.codec().encodeStart(ops, decoded).getOrThrow();
            T decodedAgain = type.codec().parse(ops, reencoded).getOrThrow();
            if (!decoded.equals(decodedAgain)) {
                problems.add("generated " + fixture + " fixture changed after a codec round trip");
            }
        } catch (RuntimeException exception) {
            problems.add("generated " + fixture + " fixture is invalid: " + exception.getMessage());
        }
    }

    private static void verifyDedicatedSample(
        List<String> problems,
        String id,
        DataComponentType<?> type,
        String sample
    ) {
        DedicatedEditorSchema.Coverage coverage = DedicatedEditorSchema.coverage(type);
        if (!coverage.isExact()) problems.add(id + " dedicated coverage is not exact");
        try {
            type.codec().parse(
                RegistryOps.create(
                    JsonOps.INSTANCE,
                    vanillaRegistries
                ),
                JsonParser.parseString(sample)
            ).getOrThrow();
        } catch (RuntimeException exception) {
            problems.add(id + " full editor sample is invalid: " + exception.getMessage());
        }
    }

    private static void verifyPrimitive(
        List<String> problems,
        String id,
        DataComponentType<?> type,
        String encoded
    ) {
        try {
            type.codec().parse(JsonOps.INSTANCE, new JsonPrimitive(encoded)).getOrThrow();
        } catch (RuntimeException exception) {
            problems.add(id + " starter '" + encoded + "' is invalid: " + exception.getMessage());
        }
    }

    private static void verifyCreativeInventorySlots(List<String> problems) {
        int[][] expectations = {
            {0, 36}, {8, 44}, {9, 9}, {35, 35},
            {36, 8}, {39, 5}, {40, 45}, {41, -1}
        };
        for (int[] expectation : expectations) {
            int actual = EditorItemTarget.creativeMenuSlot(expectation[0]);
            if (actual != expectation[1]) {
                problems.add(
                    "inventory slot " + expectation[0] + " maps to creative slot " + actual
                        + " instead of " + expectation[1]
                );
            }
        }
    }

}

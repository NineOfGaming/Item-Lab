package dev.nineofgaming.itemlab.editor;

import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Lifecycle;
import dev.nineofgaming.itemlab.config.ItemLabConfig;
import dev.nineofgaming.itemlab.editor.component.CodecComponentState;
import dev.nineofgaming.itemlab.editor.component.IdentifierCollectionEditor;
import dev.nineofgaming.itemlab.editor.container.ContainerContentsDraft;
import dev.nineofgaming.itemlab.editor.container.BundleContentsDraft;
import dev.nineofgaming.itemlab.editor.component.ComponentFormPanel;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import dev.nineofgaming.itemlab.editor.component.BeesDraft;
import dev.nineofgaming.itemlab.editor.component.RecipesDraft;
import dev.nineofgaming.itemlab.editor.container.SulfurCubeContentDraft;
import dev.nineofgaming.itemlab.editor.catalog.ComponentOption;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.diamondfire.DiamondFireTagEditorPanel;
import dev.nineofgaming.itemlab.editor.diamondfire.DiamondFireContext;
import dev.nineofgaming.itemlab.editor.diamondfire.DiamondFireFeatures;
import dev.nineofgaming.itemlab.editor.panel.BeesEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.DataEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.LoreEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.RecipesEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.RawStackEditorPanel;
import dev.nineofgaming.itemlab.editor.state.EditorItemTarget;
import dev.nineofgaming.itemlab.editor.state.ItemEditHistory;
import dev.nineofgaming.itemlab.editor.ui.text.RichTextLine;
import dev.nineofgaming.itemlab.editor.ui.text.TextInputFormat;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImDouble;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.DebugStickState;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.Blocks;
import net.kyori.adventure.platform.modcommon.MinecraftAudiences;
import net.kyori.adventure.platform.modcommon.MinecraftClientAudiences;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;

import static dev.nineofgaming.itemlab.editor.EditorCheckSupport.invoke;
import static dev.nineofgaming.itemlab.editor.EditorCheckSupport.read;
import static dev.nineofgaming.itemlab.editor.EditorCheckSupport.require;
import static dev.nineofgaming.itemlab.editor.EditorCheckSupport.write;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Real codecs/panels/commit paths; mocked client IO cannot reach a world or server.
 */
public final class PendingEditorCommitCheck {
    private static RegistryAccess fixtureRegistries;
    private static final String INVALID_MINI_MESSAGE = "<hover:show_entity:'itemlab:unknown_entity':'00000000-0000-0000-0000-000000000001'>Invalid</hover>";

    private PendingEditorCommitCheck() {
    }

    public static void main(String[] arguments) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registries = VanillaRegistries.createLookup();
        // Banner patterns are a dynamic registry; keep actual vanilla values in the isolated mock world.
        MappedRegistry<BannerPattern> bannerPatterns = new MappedRegistry<>(Registries.BANNER_PATTERN, Lifecycle.stable());
        registries.lookupOrThrow(Registries.BANNER_PATTERN).listElements()
            .forEach(holder -> Registry.register(bannerPatterns, holder.key(), holder.value()));
        bannerPatterns.bindTags(Map.of(TagKey.create(Registries.BANNER_PATTERN, Identifier.parse("itemlab:patterns")),
            List.of(bannerPatterns.get(Identifier.parse("minecraft:stripe_bottom")).orElseThrow(), bannerPatterns.get(Identifier.parse("minecraft:creeper")).orElseThrow())));
        bannerPatterns.freeze();
        List<Registry<?>> clientRegistries = new ArrayList<>(BuiltInRegistries.REGISTRY.stream().toList());
        clientRegistries.add(bannerPatterns);
        MappedRegistry<DamageType> damageTypes = new MappedRegistry<>(Registries.DAMAGE_TYPE, Lifecycle.stable());
        registries.lookupOrThrow(Registries.DAMAGE_TYPE).listElements()
            .forEach(holder -> Registry.register(damageTypes, holder.key(), holder.value()));
        damageTypes.bindTags(Map.of(TagKey.create(Registries.DAMAGE_TYPE, Identifier.parse("itemlab:combat_damage")),
            List.of(damageTypes.get(Identifier.parse("minecraft:in_fire")).orElseThrow(), damageTypes.get(Identifier.parse("minecraft:player_attack")).orElseThrow())));
        damageTypes.freeze();
        clientRegistries.add(damageTypes);
        var catVariants = new MappedRegistry<>(Registries.CAT_VARIANT, Lifecycle.stable());
        registries.lookupOrThrow(Registries.CAT_VARIANT).listElements()
            .forEach(holder -> Registry.register(catVariants, holder.key(), holder.value()));
        catVariants.freeze();
        clientRegistries.add(catVariants);
        MappedRegistry<Enchantment> enchantments = new MappedRegistry<>(Registries.ENCHANTMENT, Lifecycle.stable());
        registries.lookupOrThrow(Registries.ENCHANTMENT).listElements()
            .forEach(holder -> Registry.register(enchantments, holder.key(), holder.value()));
        enchantments.freeze();
        clientRegistries.add(enchantments);
        var instruments = new MappedRegistry<>(Registries.INSTRUMENT, Lifecycle.stable());
        registries.lookupOrThrow(Registries.INSTRUMENT).listElements()
            .forEach(holder -> Registry.register(instruments, holder.key(), holder.value()));
        instruments.freeze();
        clientRegistries.add(instruments);
        var jukeboxSongs = new MappedRegistry<>(Registries.JUKEBOX_SONG, Lifecycle.stable());
        registries.lookupOrThrow(Registries.JUKEBOX_SONG).listElements()
            .forEach(holder -> Registry.register(jukeboxSongs, holder.key(), holder.value()));
        jukeboxSongs.freeze();
        clientRegistries.add(jukeboxSongs);
        var trimMaterials = new MappedRegistry<>(Registries.TRIM_MATERIAL, Lifecycle.stable());
        registries.lookupOrThrow(Registries.TRIM_MATERIAL).listElements()
            .forEach(holder -> Registry.register(trimMaterials, holder.key(), holder.value()));
        trimMaterials.freeze();
        clientRegistries.add(trimMaterials);
        var trimPatterns = new MappedRegistry<>(Registries.TRIM_PATTERN, Lifecycle.stable());
        registries.lookupOrThrow(Registries.TRIM_PATTERN).listElements()
            .forEach(holder -> Registry.register(trimPatterns, holder.key(), holder.value()));
        trimPatterns.freeze();
        clientRegistries.add(trimPatterns);
        // A real named holder set in this isolated registry, without changing the built-in registry's tags.
        MappedRegistry<EntityType<?>> entityTypes = new MappedRegistry<>(Registries.ENTITY_TYPE, Lifecycle.stable());
        BuiltInRegistries.ENTITY_TYPE.entrySet().forEach(entry -> Registry.register(entityTypes, entry.getKey(), entry.getValue()));
        entityTypes.bindTags(Map.of(TagKey.create(Registries.ENTITY_TYPE, Identifier.parse("itemlab:wearers")),
            List.of(entityTypes.get(Identifier.parse("minecraft:player")).orElseThrow(), entityTypes.get(Identifier.parse("minecraft:cow")).orElseThrow())));
        entityTypes.freeze();
        clientRegistries.removeIf(registry -> registry.key().equals(Registries.ENTITY_TYPE));
        clientRegistries.add(entityTypes);
        // Exercise real tag-backed Adventure predicates without rebinding built-in tags.
        var adventureBlocks = new MappedRegistry<>(Registries.BLOCK, Lifecycle.stable());
        BuiltInRegistries.BLOCK.entrySet().forEach(entry -> Registry.register(adventureBlocks, entry.getKey(), entry.getValue()));
        adventureBlocks.bindTags(Map.of(TagKey.create(Registries.BLOCK, Identifier.parse("itemlab:adventure_blocks")),
            List.of(adventureBlocks.get(Identifier.parse("minecraft:oak_stairs")).orElseThrow(), adventureBlocks.get(Identifier.parse("minecraft:stone")).orElseThrow())));
        adventureBlocks.freeze();
        clientRegistries.removeIf(registry -> registry.key().equals(Registries.BLOCK));
        clientRegistries.add(adventureBlocks);
        var useEffectsRegistry = new MappedRegistry<>(Registries.MOB_EFFECT, Lifecycle.stable());
        BuiltInRegistries.MOB_EFFECT.entrySet().forEach(entry -> Registry.register(useEffectsRegistry, entry.getKey(), entry.getValue()));
        useEffectsRegistry.bindTags(Map.of(TagKey.create(Registries.MOB_EFFECT, Identifier.parse("itemlab:use_effects")),
            List.of(useEffectsRegistry.get(Identifier.parse("minecraft:speed")).orElseThrow(), useEffectsRegistry.get(Identifier.parse("minecraft:strength")).orElseThrow())));
        useEffectsRegistry.freeze();
        clientRegistries.removeIf(registry -> registry.key().equals(Registries.MOB_EFFECT));
        clientRegistries.add(useEffectsRegistry);
        var combatItems = new MappedRegistry<>(Registries.ITEM, Lifecycle.stable());
        BuiltInRegistries.ITEM.entrySet().forEach(entry -> Registry.register(combatItems, entry.getKey(), entry.getValue()));
        combatItems.bindTags(Map.of(TagKey.create(Registries.ITEM, Identifier.parse("itemlab:combat_repairs")),
            List.of(combatItems.get(Identifier.parse("minecraft:stone")).orElseThrow(), combatItems.get(Identifier.parse("minecraft:iron_ingot")).orElseThrow())));
        combatItems.freeze();
        clientRegistries.removeIf(registry -> registry.key().equals(Registries.ITEM));
        clientRegistries.add(combatItems);
        fixtureRegistries = new RegistryAccess.ImmutableRegistryAccess(clientRegistries);
        // Default item holders must belong to the same registries used by the fixture codecs.
        var defaultRegistryKeys = Set.of(Registries.TRIM_MATERIAL, Registries.TRIM_PATTERN);
        var initializerLookup = HolderLookup.Provider.create(java.util.stream.Stream.concat(
            fixtureRegistries.listRegistries().filter(lookup -> defaultRegistryKeys.contains(lookup.key())),
            registries.listRegistries().filter(lookup -> !defaultRegistryKeys.contains(lookup.key()))));
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(initializerLookup)
            .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
        Path configDirectory = Files.createTempDirectory("itemlab-commit-check-");
        try {
            write(FabricLoader.getInstance(), "configDir", configDirectory);
            // Change only this test JVM's preferences, without saving a config file.
            write(read(ItemLabConfig.get(), "text"), "inputFormat", TextInputFormat.COLOR_CODES);
            // Native-only fixtures start here; formatting fixtures temporarily install the real serializer.
            write(read(ItemLabConfig.get(), "text"), "removeDefaultItalics", false);
            write(read(ItemLabConfig.get(), "text"), "useWhiteDefaultLoreColor", false);
            verifyDiamondFireContext();
            if (DiamondFireFeatures.available()) {
                verifyDiamondFireLosslessKeys();
                verifyDiamondFireRepresentationPairs();
            }
            verifyUnavailableViewportRetainsPendingEdits();
            verifyDiscardInvalidPendingEdits();
            verifySaveAndRepeatedCommit();
            verifyGiveCopy();
            verifyContainerCommits();
            verifyContainerActionFailures();
            verifyContainerOtherFailures();
            verifyContainerConflicts();
            verifyContainerComponentResets();
            verifyContainerInspectorRetargets();
            verifyCombatCommits();
            verifyCombatActionFailures();
            verifyCombatProjectileActions();
            verifyCombatOtherFailures();
            verifyCombatConflicts();
            verifyCombatComponentResets();
            verifyUseTypedRemainder();
            verifyUseCommits();
            verifyUseActionFailures();
            verifyUseOtherFailures();
            verifyUseConflicts();
            verifyAdventureCommits();
            verifyAdventureActionFailures();
            verifyAdventureOtherFailures();
            verifyAdventureConflicts();
            try (TextBridge ignored = new TextBridge()) {
                verifyBookCommits();
                verifyBookFailures();
                verifyBookOtherFailures();
                verifyBookConflicts();
                verifyBookSigning();
                verifyBookStackSizeHistory();
            }
            verifyPresentationCommits();
            verifyPresentationFailures();
            verifyPresentationOtherFailures();
            verifyPresentationConflicts();
            verifyEquipmentMapPotCommits();
            verifyEquipmentMapPotFailures();
            verifyEquipmentMapPotOtherFailures();
            verifyEquipmentMapPotConflicts();
            verifyMusicCompassProfileCommits();
            verifyMusicCompassProfileFailures();
            try (TextBridge ignored = new TextBridge()) {
                verifyMusicCompassProfileOtherFailures();
            }
            verifyMusicCompassProfileConflicts();
            verifyDataTargetCommits();
            verifyDataTargetFailures();
            verifyDataTargetConflicts();
            verifyDataTargetEarlierFailures();
            verifyEnchantmentsAndAttributes();
            verifyEnchantmentAndAttributeFailures();
            verifyInvalidInputWritesNothing();
            verifyFullInventoryRetainsComposedDraft();
            verifyStaleTargetRetainsComposedDraft();
            verifyRichTextSerialization();
            verifyFormattedCommits();
            verifyMiniMessageFailures();
            verifyComponentRepresentationCommits();
            verifyComponentRepresentationFailures();
            verifyExplicitCountDecoding();
            verifyRawCommitsAndFailures();
            verifyRepresentationConflicts();
            verifyDiamondFireCommitsAndFailures();
            verifyInlinePreviewIsolation();
            verifyInlineCommits();
            verifyInlineFailures();
            verifyDisabledInlineComponents();
            verifyGroupedFailureFocus();
            verifyAppearanceAndBookFailureFocus();
            verifyWrittenBookRecoveryTargets();
            verifyAppearanceCodecRecovery();
            verifyBannerPotionFireworkFailures();
            verifyAdventureFailures();
            verifyUseFailures();
            verifyCombatFailures();
            verifyProjectileFailuresAndIsolation();
            verifyContainerFailuresAndIsolation();
            verifyContainerComponentSyncRetention();
            verifySulfurCubeContentValidation();
            verifySulfurCubeContentFailures();
            verifySulfurCubeIdentifierInput();
            verifyNestedItemCommits();
            verifyNestedItemDiscard();
            verifyNestedItemCancel();
            verifyMultiLevelNestedCommit();
        } finally {
            Files.delete(configDirectory);
        }
    }

    private static void verifyUnavailableViewportRetainsPendingEdits() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ItemEditor editor = fixture.editedEditor();
            ItemStack draft = editor.draft();
            write(editor, "open", true);
            ImGui.createContext();
            try {
                ImGui.getIO().setIniFilename(null);
                require(ImGui.getIO().getFonts().build(), "could not build the headless ImGui font atlas");
                for (float[] size : List.of(new float[]{0.0f, 0.0f}, new float[]{0.0f, 1080.0f},
                    new float[]{1920.0f, 0.0f})) {
                    ImGui.getIO().setDisplaySize(size[0], size[1]);
                    ImGui.newFrame();
                    try {
                        write(editor, "capturingTextInput", true);
                        editor.render();
                        require(editor.isOpen(), "an unavailable viewport closed the editor session");
                        require(editor.draft() == draft, "an unavailable viewport replaced the draft");
                        require((boolean) read(editor, "basicDirty") && ((ImInt) read(editor, "count")).get() == 99,
                            "an unavailable viewport discarded pending edits");
                        require(!editor.isCapturingTextInput(), "an unavailable viewport retained text input capture");
                        require(fixture.writes.isEmpty(), "an unavailable viewport wrote an item to the client");
                    } finally {
                        ImGui.endFrame();
                    }
                }
            } finally {
                ImGui.destroyContext();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifyDiscardInvalidPendingEdits() throws Exception {
        try (TextBridge ignored = new TextBridge()) {
            for (String problem : List.of("data", "name", "book", "music", "equipment", "appearance", "flags",
                "generated", "fixed_variant", "registry_variant", "raw_only", "raw_conflict", "diamondfire",
                "bad_item_id", "unknown_item_id", "air_item_id", "valid_item_id", "missing_snapshot")) {
                for (String action : List.of("revertDraft", "resetDraftToDefaults")) {
                    try (Fixture fixture = new Fixture()) {
                        ItemStack opening = diamondFireOpening();
                        opening.setCount(7);
                        ItemEditor editor = fixture.cleanEditor(opening);
                        if (!problem.equals("missing_snapshot")) {
                            invoke(editor, "captureOpeningState", new Class<?>[]{Minecraft.class}, fixture.client);
                        }
                        ItemStack applied = opening.copy();
                        applied.set(DataComponents.CUSTOM_NAME, Component.literal("Already applied name"));
                        write(editor, "historyEnabled", false);
                        invoke(editor, "setDraft", new Class<?>[]{Minecraft.class, ItemStack.class, boolean.class}, fixture.client, applied, true);
                        write(editor, "historyEnabled", true);
                        fixture.addPendingEdits(editor);
                        switch (problem) {
                            case "data", "missing_snapshot" -> {
                                Object entry = ((List<?>) read(read(editor, "dataEditor"), "entries")).getFirst();
                                ((ImString) read(entry, "value")).set("invalid integer");
                            }
                            case "name" ->
                                write(read(editor, "loreEditor"), "displayName", text(INVALID_MINI_MESSAGE, TextInputFormat.MINI_MESSAGE));
                            case "book" -> {
                                addFormattedBook(editor);
                                write(read(editor, "bookEditor"), "title", text(INVALID_MINI_MESSAGE, TextInputFormat.MINI_MESSAGE));
                            }
                            case "music" -> {
                                Object music = read(editor, "musicEditor");
                                ((CodecComponentState) read(music, "instrument")).setPresent(true);
                                ((ImBoolean) read(music, "customInstrument")).set(true);
                                ((ImString) read(music, "instrumentDescriptionSource")).set("{");
                                write(music, "dirty", true);
                            }
                            case "equipment", "appearance" -> {
                                Object panel = read(editor, problem + "Editor");
                                ((CodecComponentState) read(panel, problem.equals("equipment") ? "equippable" : "itemModel"))
                                    .setValue(JsonParser.parseString(problem.equals("equipment") ? "{\"slot\":\"not a slot\"}" : "\"not an id\""));
                            }
                            case "flags" ->
                                ((ImBoolean) read(read(editor, "flagEditor"), "overrideMaxDamage")).set(true);
                            case "generated" -> {
                                addComponentRepresentation(fixture, editor, problem);
                                Object form = read(editor, "componentForm");
                                ((Map<String, ImString>) read(form, "fieldBuffers")).put("$/value", new ImString("not a number", 128));
                                ((Map<String, String>) read(form, "fieldProblems")).put("$/value", "Enter a number.");
                                write(form, "dirty", true);
                            }
                            case "fixed_variant", "registry_variant" -> {
                                addComponentRepresentation(fixture, editor, problem);
                                ((ImString) read(read(editor, "variantComponentEditor"), "value")).set("not a variant");
                            }
                            case "raw_only", "raw_conflict" -> {
                                if (problem.equals("raw_only")) {
                                    invoke(editor, "setDraft", new Class<?>[]{Minecraft.class, ItemStack.class, boolean.class}, fixture.client, applied, true);
                                }
                                ((RawStackEditorPanel) read(editor, "rawStackEditor")).restorePendingSource("{");
                            }
                            case "diamondfire" -> {
                                ((DataEditorPanel) read(editor, "dataEditor")).sync(editor.draft());
                                addDiamondFirePending(fixture, editor);
                                Object row = diamondFireRow(read(editor, "diamondFireTagEditor"), "edit");
                                ((ImString) read(row, "value")).set("invalid number");
                                write(row, "valueChanged", true);
                            }
                            case "bad_item_id", "unknown_item_id", "air_item_id", "valid_item_id" ->
                                ((ImString) read(editor, "itemId")).set(switch (problem) {
                                    case "bad_item_id" -> "not an item id";
                                    case "unknown_item_id" -> "itemlab:unknown_item";
                                    case "air_item_id" -> "minecraft:air";
                                    default -> "minecraft:diamond";
                                });
                            default -> throw new AssertionError(problem);
                        }
                        ItemStack expected = action.equals("revertDraft") ? opening.copy()
                            : new ItemStack(problem.equals("valid_item_id") ? Items.DIAMOND : Items.STONE, ((ImInt) read(editor, "count")).get());
                        Object pendingState = null;
                        if (!problem.equals("valid_item_id")) {
                            invoke(editor, "applyDraftToSlot", new Class<?>[]{Minecraft.class}, fixture.client);
                            require(!(boolean) read(editor, "statusSuccess") && ItemStack.matches(applied, editor.draft()) && fixture.writes.isEmpty(),
                                "Discard fixture did not first reject Save: " + problem);
                        }
                        if (problem.equals("data") || problem.equals("raw_conflict")) {
                            captureHistory(editor, fixture.client);
                            pendingState = invoke(editor, "captureEditorSnapshot", new Class<?>[]{Minecraft.class}, fixture.client);
                        }
                        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                        // The held-item load fallback clears status rather than posting a success message.
                        boolean restoredViaLoad = problem.equals("missing_snapshot") && action.equals("revertDraft");
                        require(restoredViaLoad ? ((String) read(editor, "status")).isEmpty() : (boolean) read(editor, "statusSuccess"),
                            "Discard action blocked by invalid input: " + problem + "/" + action);
                        require(ItemStack.matches(expected, editor.draft()), "Discard action produced the wrong replacement: " + problem + "/" + action);
                        require(!editor.hasPendingNonRawEditorChanges()
                                && !((RawStackEditorPanel) read(editor, "rawStackEditor")).isDirty()
                                && read(editor, "pendingComponentRecovery") == null,
                            "Discard action left pending input/recovery: " + problem + "/" + action);
                        require((boolean) read(editor, "draftDirty") == !action.equals("revertDraft")
                                && ((Deque<?>) read(read(editor, "history"), "undo")).size() == 1,
                            "Discard action lost dirty/history semantics: " + problem + "/" + action);
                        if (pendingState != null) {
                            captureHistory(editor, fixture.client);
                            require(((Deque<?>) read(editor, "editorUndo")).size() == 1, "Discard did not record one editor-history replacement");
                            invoke(editor, "undo", new Class<?>[]{Minecraft.class}, fixture.client);
                            Object undone = invoke(editor, "captureEditorSnapshot", new Class<?>[]{Minecraft.class}, fixture.client);
                            require((boolean) invoke(pendingState, "sameState", new Class<?>[]{pendingState.getClass()}, undone),
                                "Discard Undo lost the previous draft or invalid buffers");
                            invoke(editor, "redo", new Class<?>[]{Minecraft.class}, fixture.client);
                            require(ItemStack.matches(expected, editor.draft()) && !editor.hasPendingNonRawEditorChanges()
                                    && !((RawStackEditorPanel) read(editor, "rawStackEditor")).isDirty(),
                                "Discard Redo lost the clean replacement");
                        }
                        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                        require(ItemStack.matches(expected, editor.draft()) && ((Deque<?>) read(read(editor, "history"), "undo")).size() == 1,
                            "Repeated discard added another item-history replacement");
                        require(fixture.writes.isEmpty() && fixture.slots.size() == 1 && ItemStack.matches(opening, fixture.slots.get(0)),
                            "Discard action/history wrote to inventory: " + problem + "/" + action);
                    }
                }
            }
        }
    }

    private static void verifySaveAndRepeatedCommit() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ItemEditor editor = fixture.editedEditor();
            ItemStack opening = editor.draft().copy();
            ItemEditHistory history = (ItemEditHistory) read(editor, "history");
            var result = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy,
                ItemStackCodecs::validateStack, participants(editor, fixture.client));
            require(result.succeeded(), "combined preflight failed: " + result.failure());
            require(result.included().equals(List.of("basic_name_lore", "flags", "equipment", "appearance", "map", "data")),
                "grouped panels were omitted/reordered: " + result.included());
            require(ItemStack.matches(opening, editor.draft()) && !history.canUndo(), "preflight changed draft/history");
            assertCombined(result.candidate());
            require(editor.hasPendingNonRawEditorChanges(), "preflight cleaned pending panels");
            captureHistory(editor, fixture.client);

            invoke(editor, "applyDraftToSlot", new Class<?>[]{Minecraft.class}, fixture.client);
            captureHistory(editor, fixture.client);
            require((boolean) read(editor, "statusSuccess"), "Save Item failed: " + read(editor, "status"));
            assertCombined(editor.draft());
            assertCombined(fixture.slots.get(0));
            require(!editor.hasPendingNonRawEditorChanges() && !(boolean) read(editor, "draftDirty"), "Save left dirty baselines");
            require(((Deque<?>) read(history, "undo")).size() == 1, "Save did not record one combined transaction");
            require(((Deque<?>) read(editor, "editorUndo")).size() == 1, "Save did not record one combined editor undo step");
            require(fixture.writes.size() == 1 && fixture.writes.getFirst().slot() == 36, "Save wrote the wrong slot/count");
            assertCombined(fixture.writes.getFirst().stack());
            ItemStack saved = editor.draft().copy();

            invoke(editor, "applyDraftToSlot", new Class<?>[]{Minecraft.class}, fixture.client);
            captureHistory(editor, fixture.client);
            require(ItemStack.matches(saved, editor.draft()) && ((Deque<?>) read(history, "undo")).size() == 1
                    && ((Deque<?>) read(editor, "editorUndo")).size() == 1,
                "repeat Save changed the draft or added a transaction");
            invoke(editor, "undo", new Class<?>[]{Minecraft.class}, fixture.client);
            require(ItemStack.matches(opening, editor.draft()) && editor.hasPendingNonRawEditorChanges()
                && ((Deque<?>) read(editor, "editorUndo")).isEmpty()
                && ((Deque<?>) read(editor, "editorRedo")).size() == 1, "combined editor undo lost its before-state/buffers");
            var afterUndo = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy,
                ItemStackCodecs::validateStack, participants(editor, fixture.client));
            require(afterUndo.succeeded() && ItemStack.matches(saved, afterUndo.candidate()), "Undo did not retain the combined pending inputs");
            invoke(editor, "redo", new Class<?>[]{Minecraft.class}, fixture.client);
            require(ItemStack.matches(saved, editor.draft()) && !editor.hasPendingNonRawEditorChanges()
                && ((Deque<?>) read(editor, "editorRedo")).isEmpty(), "combined editor redo lost components/clean baselines");
            assertCombined(fixture.slots.get(0));
            // Inventory writes receive copies, not the editor's mutable draft reference.
            editor.draft().setCount(1);
            require(fixture.slots.get(0).getCount() == 99 && fixture.writes.getFirst().stack().getCount() == 99,
                "target/network output aliases the editor draft");
        }
    }

    private static void verifyGiveCopy() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ItemEditor editor = fixture.editedEditor();
            ItemStack targetBefore = fixture.slots.get(0).copy();
            captureHistory(editor, fixture.client);
            invoke(editor, "giveDraftCopy", new Class<?>[]{Minecraft.class}, fixture.client);
            captureHistory(editor, fixture.client);
            require((boolean) read(editor, "statusSuccess"), "Give Copy failed: " + read(editor, "status"));
            assertCombined(fixture.slots.get(1));
            require(ItemStack.matches(targetBefore, fixture.slots.get(0)), "Give Copy overwrote the original target");
            require(fixture.writes.size() == 1 && fixture.writes.getFirst().slot() == 37, "Give Copy wrote the wrong destination");
            require(!editor.hasPendingNonRawEditorChanges(), "Give Copy left committed panels pending");
            require(((Deque<?>) read(read(editor, "history"), "undo")).size() == 1, "Give Copy did not combine history");
            require(((Deque<?>) read(editor, "editorUndo")).size() == 1, "Give Copy did not combine editor history");
        }
    }

    private static final List<String> CONTAINER_COMPONENTS = List.of("container", "bundle_contents", "lock", "container_loot");

    private static ListTag containerEntries(boolean pending) throws Exception {
        var entries = new ListTag();
        entries.add(TagParser.parseCompoundFully(pending ? "{slot:0,item:{id:\"minecraft:arrow\",count:2}}" : "{slot:2,item:{id:\"minecraft:stone\"}}"));
        if (pending) {
            entries.add(TagParser.parseCompoundFully("{slot:200,item:{id:\"minecraft:diamond\",count:99,components:{\"minecraft:max_stack_size\":16,\"minecraft:custom_data\":" + ADVENTURE_DATA + ",\"minecraft:instrument\":\"minecraft:ponder_goat_horn\"}}}"));
            entries.add(TagParser.parseCompoundFully("{slot:255,item:{id:\"minecraft:arrow\",count:3,components:{\"minecraft:custom_data\":" + ADVENTURE_DATA + ",\"minecraft:damage_type\":\"minecraft:player_attack\"}}}"));
        }
        return entries;
    }

    private static Map<String, Tag> containerValues(boolean pending) throws Exception {
        var values = new java.util.LinkedHashMap<String, Tag>();
        values.put("container", containerEntries(pending));
        var bundle = new ListTag();
        bundle.add(TagParser.parseCompoundFully(pending ? "{id:\"minecraft:arrow\",count:2}" : "{id:\"minecraft:stone\"}"));
        if (pending) {
            bundle.add(((CompoundTag) containerEntries(true).get(1)).getCompound("item").orElseThrow().copy());
            bundle.add(((CompoundTag) containerEntries(true).get(2)).getCompound("item").orElseThrow().copy());
        }
        values.put("bundle_contents", bundle);
        values.put("lock", TagParser.parseCompoundFully(pending
            ? "{items:[\"minecraft:stone\",\"minecraft:iron_ingot\",\"minecraft:stone\"],count:{min:-2,max:-1},components:{\"minecraft:custom_data\":" + ADVENTURE_DATA
            + ",\"minecraft:instrument\":\"minecraft:ponder_goat_horn\"},predicates:{\"minecraft:custom_data\":" + ADVENTURE_DATA + "}}"
            : "{items:\"minecraft:tripwire_hook\",count:1}"));
        values.put("container_loot", TagParser.parseCompoundFully(pending ? "{loot_table:\"itemlab:future_loot\",seed:-9223372036854775808L}" : "{loot_table:\"minecraft:empty\",seed:7L}"));
        return values;
    }

    private static ItemStack containerExpected(Fixture fixture, Map<String, Tag> values) {
        ItemStack expected = new ItemStack(Items.STONE);
        for (var entry : values.entrySet())
            ItemStackCodecs.decodeAndSetComponentNbt(fixture.client, expected, dataType(entry.getKey()), entry.getValue());
        return expected;
    }

    @SuppressWarnings("unchecked")
    private static void addContainerPending(Fixture fixture, ItemEditor editor, Map<String, Tag> values) throws Exception {
        Object panel = read(editor, "containerEditor");
        ItemStack expected = containerExpected(fixture, values);
        write(panel, "knownItemSelectors", java.util.stream.Stream.concat(BuiltInRegistries.ITEM.keySet().stream().map(Identifier::toString),
            java.util.stream.Stream.of("#itemlab:combat_repairs")).collect(java.util.stream.Collectors.toSet()));
        invoke(panel, "syncContents", new Class<?>[]{Minecraft.class, ItemStack.class}, fixture.client, expected);
        invoke(panel, "syncBundleContents", new Class<?>[]{ItemStack.class}, expected);
        write(panel, "selectedContentsSlot", 200);
        write(panel, "selectedBundleItem", ((BundleContentsDraft) read(panel, "bundleContents")).size() > 1 ? 1 : 0);
        invoke(panel, "syncContainedItemFields", new Class<?>[0]);
        invoke(panel, "syncBundleItemFields", new Class<?>[0]);
        for (String target : List.of("lock", "container_loot")) {
            CodecComponentState state = (CodecComponentState) read(panel, target.equals("lock") ? "lock" : "loot");
            state.setPresent(true);
            state.setValue(ItemStackCodecs.encodeComponent(fixture.client, dataType(target), expected.get(dataType(target))));
            // Stale cached representations must not override the separate live source inputs.
            state.setNbtValue(fixture.client, containerValues(false).get(target));
        }
        CompoundTag lock = (CompoundTag) values.get("lock");
        ((ImString) read(panel, "exactComponentsSource")).set(" \n" + ItemStackCodecs.prettySnbt(lock.getCompound("components").orElseGet(CompoundTag::new)) + "\n ");
        ((ImString) read(panel, "partialPredicatesSource")).set(" \n" + ItemStackCodecs.prettySnbt(lock.getCompound("predicates").orElseGet(CompoundTag::new)) + "\n ");
        CompoundTag loot = (CompoundTag) values.get("container_loot");
        ((ImString) read(panel, "lootTable")).set(" " + loot.getString("loot_table").orElseThrow() + " ");
        ((ImString) read(panel, "lootSeed")).set(" " + loot.getLong("seed").orElse(0L) + " ");
        Object code = read(panel, "exactComponentsEditor");
        write(code, "cursorByteOffset", 3);
        write(read(panel, "partialPredicatesEditor"), "cursorByteOffset", 3);
        JsonObject model = ((CodecComponentState) read(panel, "lock")).value().getAsJsonObject();
        JsonElement items = model.get("items");
        if (items != null) {
            List<JsonElement> entries = items.isJsonArray() ? items.getAsJsonArray().asList() : List.of(items);
            Map<String, ImString> buffers = (Map<String, ImString>) read(panel, "textBuffers");
            for (int index = 0; index < entries.size(); index++)
                buffers.put("container/lock/items/@identifier/" + index, new ImString(" " + entries.get(index).getAsString() + " ", 512));
        }
        write(panel, "dirty", true);
    }

    private static List<Object> storedStackState(Fixture fixture, ItemStack stack) {
        var values = new ArrayList<Object>(List.of(stack.getItem(), stack.getCount()));
        for (var component : stack.getComponents()) {
            values.add(component.type());
            try {
                values.add(ItemStackCodecs.encodeComponentNbt(fixture.client, component.type(), component.value()).copy());
            } catch (RuntimeException ignored) {
                values.add(component.value());
            } // Invalid primitive fields must remain inspectable.
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> containerBuffers(Fixture fixture, ItemEditor editor, boolean references) throws Exception {
        Object panel = read(editor, "containerEditor");
        var result = new ArrayList<Object>();
        for (String target : List.of("contents", "bundleContents")) {
            Object model = read(panel, target);
            List<ItemStack> stacks = (List<ItemStack>) read(model, target.equals("contents") ? "slots" : "items");
            if (references) {
                result.add(model);
                result.add(stacks);
            } else result.add(read(model, "present"));
            for (ItemStack stack : stacks) {
                if (references) {
                    result.add(stack);
                    result.add(stack.getComponents());
                    for (var component : stack.getComponents()) result.add(component.value());
                } else result.add(storedStackState(fixture, stack));
            }
        }
        for (String target : List.of("lock", "loot")) {
            CodecComponentState state = (CodecComponentState) read(panel, target);
            if (references) {
                result.add(state);
                jsonReferences(state.value(), result);
                result.add(read(state, "nbtValue") == null ? "" : read(state, "nbtValue"));
            } else
                result.add(List.of(state.present(), state.value().deepCopy(), read(state, "nbtValue") == null ? "" : ((Tag) read(state, "nbtValue")).copy()));
        }
        for (String name : List.of("exactComponentsSource", "partialPredicatesSource", "lootTable", "lootSeed", "containedItemId", "containedItemCount", "bundleItemId", "bundleItemCount", "goToContentsSlot")) {
            Object input = read(panel, name);
            result.add(references ? input : input instanceof ImString source ? source.get() : ((ImInt) input).get());
        }
        for (String name : List.of("exactComponentsEditor", "partialPredicatesEditor")) {
            Object code = read(panel, name);
            result.add(references ? code : read(code, "cursorByteOffset"));
        }
        if (references) result.add(read(panel, "lockItemsFocus"));
        else
            for (String name : List.of("selectedContentsSlot", "selectedBundleItem", "containedItemIdPending", "bundleItemIdPending", "indexedMinimumCapacity", "indexedView"))
                result.add(read(panel, name));
        List<Long> rows = (List<Long>) read(panel, "bundleRowIds");
        result.add(references ? rows : List.copyOf(rows));
        Map<String, ImString> buffers = (Map<String, ImString>) read(panel, "textBuffers");
        if (references) result.add(buffers);
        for (String key : buffers.keySet().stream().sorted().toList()) {
            if (!references) result.add(key);
            result.add(references ? buffers.get(key) : buffers.get(key).get());
        }
        return result;
    }

    private static void assertContainerOutput(Fixture fixture, ItemStack saved, Map<String, Tag> values, Set<String> removed) {
        assertCombined(saved);
        ItemStack expected = containerExpected(fixture, values);
        for (String target : CONTAINER_COMPONENTS) {
            if (removed.contains(target)) require(!saved.has(dataType(target)), "Container failed to remove " + target);
            else
                require(ItemStackCodecs.encodeComponentNbt(fixture.client, dataType(target), expected.get(dataType(target))).equals(
                    ItemStackCodecs.encodeComponentNbt(fixture.client, dataType(target), saved.get(dataType(target)))), "Container lost exact native output: " + target);
        }
    }

    private record ContainerValueCase(String target, Tag value) {
    }

    private static List<ContainerValueCase> containerValueCases() throws Exception {
        var cases = new ArrayList<ContainerValueCase>();
        for (String source : List.of("{}", "{items:\"minecraft:stone\"}", "{items:[\"minecraft:stone\"]}", "{items:[\"minecraft:stone\",\"minecraft:iron_ingot\",\"minecraft:stone\"]}",
            "{items:\"#itemlab:combat_repairs\"}", "{count:0}", "{count:-1}", "{count:2147483647}", "{count:{}}", "{count:{min:-2}}", "{count:{max:4}}", "{count:{min:2,max:2}}",
            "{components:{}}", "{predicates:{}}", "{components:{\"minecraft:custom_data\":" + ADVENTURE_DATA + "}}", "{predicates:{\"minecraft:custom_data\":" + ADVENTURE_DATA + "}}"))
            cases.add(new ContainerValueCase("lock", TagParser.parseCompoundFully(source)));
        cases.add(new ContainerValueCase("lock", containerValues(true).get("lock")));
        for (String table : List.of("minecraft:empty", "itemlab:unregistered_future_table"))
            for (long seed : List.of(0L, -1L, 9007199254740993L, Long.MIN_VALUE, Long.MAX_VALUE))
                cases.add(new ContainerValueCase("container_loot", TagParser.parseCompoundFully("{loot_table:\"" + table + "\",seed:" + seed + "L}")));
        for (String target : List.of("container", "bundle_contents")) {
            cases.add(new ContainerValueCase(target, new ListTag()));
            cases.add(new ContainerValueCase(target, containerValues(true).get(target)));
            for (int count : List.of(1, 16, 64, 99)) {
                String item = "{id:\"minecraft:diamond\",count:" + count + ",components:{\"minecraft:max_stack_size\":16,\"minecraft:custom_data\":" + ADVENTURE_DATA + "}}";
                ListTag entries = new ListTag();
                entries.add(TagParser.parseCompoundFully(target.equals("container") ? "{slot:255,item:" + item + "}" : item));
                cases.add(new ContainerValueCase(target, entries));
            }
        }
        ListTag full = new ListTag();
        for (int index = 0; index < 256; index++)
            full.add(TagParser.parseCompoundFully("{slot:" + index + ",item:\"minecraft:arrow\"}"));
        cases.add(new ContainerValueCase("container", full));
        ListTag scalarBundle = new ListTag();
        scalarBundle.add(net.minecraft.nbt.StringTag.valueOf("minecraft:arrow"));
        cases.add(new ContainerValueCase("bundle_contents", scalarBundle));
        // The nested Bundle/Container/Projectile codecs must retain opaque native data too.
        for (String nested : List.of("bundle_contents", "container", "charged_projectiles")) {
            CompoundTag components = new CompoundTag();
            components.put("minecraft:" + nested, containerValues(true).get(nested.equals("container") ? "container" : "bundle_contents").copy());
            CompoundTag item = TagParser.parseCompoundFully("{id:\"minecraft:bundle\",count:1}");
            item.put("components", components);
            ListTag entries = new ListTag();
            entries.add(item);
            cases.add(new ContainerValueCase("bundle_contents", entries));
        }
        return cases;
    }

    private static void assertContainerPreflight(Fixture fixture, ItemEditor editor) throws Exception {
        var before = containerBuffers(fixture, editor, false);
        var references = containerBuffers(fixture, editor, true);
        var prepared = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
        require(prepared.succeeded() && before.equals(containerBuffers(fixture, editor, false)), "Container preflight changed source/models: " + prepared.failure());
        var retained = containerBuffers(fixture, editor, true);
        require(references.size() == retained.size(), "Container preflight replaced collections");
        for (int index = 0; index < references.size(); index++)
            require(references.get(index) == retained.get(index), "Container preflight replaced source/model/buffer/stored-item/native-cache identity");
    }

    private static void verifyContainerCommits() throws Exception {
        for (ContainerValueCase representation : containerValueCases())
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(containerExpected(fixture, containerValues(false)));
                    var values = containerValues(true);
                    values.put(representation.target(), representation.value().copy());
                    addContainerPending(fixture, editor, values);
                    assertContainerPreflight(fixture, editor);
                    assertContainerOutput(fixture, verifyActionHistory(fixture, editor, action, "Container " + representation.target() + "/" + representation.value()), values, Set.of());
                }
        for (String target : List.of("container", "bundle_contents", "lock", "container_loot", "contents_pair", "all"))
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(containerExpected(fixture, containerValues(false)));
                    var values = containerValues(true);
                    addContainerPending(fixture, editor, values);
                    Object panel = read(editor, "containerEditor");
                    Set<String> removed = target.equals("all") ? Set.copyOf(CONTAINER_COMPONENTS) : target.equals("contents_pair") ? Set.of("container", "bundle_contents") : Set.of(target);
                    if (removed.contains("container")) {
                        ((ContainerContentsDraft) read(panel, "contents")).setPresent(false);
                        ((ImString) read(panel, "containedItemId")).set("invalid inactive id");
                        write(panel, "containedItemIdPending", true);
                        ItemStack bad = new ItemStack(Items.ARROW, 100);
                        ((ContainerContentsDraft) read(panel, "contents")).setItem(200, bad);
                    }
                    if (removed.contains("bundle_contents")) {
                        ((BundleContentsDraft) read(panel, "bundleContents")).setPresent(false);
                        ((ImString) read(panel, "bundleItemId")).set("invalid inactive id");
                        write(panel, "bundleItemIdPending", true);
                        ((BundleContentsDraft) read(panel, "bundleContents")).setItem(1, new ItemStack(Items.SHULKER_BOX));
                    }
                    if (removed.contains("lock")) {
                        CodecComponentState state = (CodecComponentState) read(panel, "lock");
                        state.setPresent(false);
                        state.setValue(JsonParser.parseString("\"inactive invalid lock\""));
                        ((ImString) read(panel, "exactComponentsSource")).set("{inactive invalid");
                        ((ImString) read(panel, "partialPredicatesSource")).set("{inactive invalid");
                    }
                    if (removed.contains("container_loot")) {
                        CodecComponentState state = (CodecComponentState) read(panel, "loot");
                        state.setPresent(false);
                        state.setValue(JsonParser.parseString("\"inactive invalid loot\""));
                        ((ImString) read(panel, "lootTable")).set("invalid inactive table");
                        ((ImString) read(panel, "lootSeed")).set("invalid inactive seed");
                    }
                    assertContainerPreflight(fixture, editor);
                    assertContainerOutput(fixture, verifyActionHistory(fixture, editor, action, "Container removal " + target), values, removed);
                }
        for (String target : CONTAINER_COMPONENTS)
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(containerExpected(fixture, containerValues(false)));
                    var values = containerValues(false);
                    values.put(target, containerValues(true).get(target));
                    addContainerPending(fixture, editor, values);
                    assertContainerPreflight(fixture, editor);
                    assertContainerOutput(fixture, verifyActionHistory(fixture, editor, action, "Container single component " + target), values, Set.of());
                }
        for (String disabled : List.of("components", "predicates", "both"))
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(containerExpected(fixture, containerValues(false)));
                    var values = containerValues(true);
                    addContainerPending(fixture, editor, values);
                    Object panel = read(editor, "containerEditor");
                    for (String field : disabled.equals("both") ? List.of("components", "predicates") : List.of(disabled)) {
                        ((CodecComponentState) read(panel, "lock")).value().getAsJsonObject().remove(field);
                        ((CompoundTag) values.get("lock")).remove(field);
                        ((ImString) read(panel, field.equals("components") ? "exactComponentsSource" : "partialPredicatesSource")).set("{inactive invalid");
                    }
                    assertContainerPreflight(fixture, editor);
                    assertContainerOutput(fixture, verifyActionHistory(fixture, editor, action, "Container disabled Lock fields " + disabled), values, Set.of());
                }
    }

    private record ContainerProblem(String target, String path, String invalid, String section, String control) {
    }

    private static List<ContainerProblem> containerProblems() {
        var cases = new ArrayList<ContainerProblem>();
        for (String target : List.of("containedItemId", "bundleItemId"))
            for (String invalid : List.of(" ", "not an item id", "itemlab:unknown_item", "minecraft:air"))
                cases.add(new ContainerProblem(target, "", invalid, target.equals("containedItemId") ? "contents" : "bundle_contents", "item_id"));
        for (String target : List.of("contents", "bundleContents"))
            for (String field : List.of("count", "max_stack_size"))
                cases.add(new ContainerProblem(target, field, "", target.equals("contents") ? "contents" : "bundle_contents", target.equals("contents") ? "slot/255" : "row/@2"));
        cases.add(new ContainerProblem("bundleContents", "disallowed", "", "bundle_contents", "row/@2"));
        for (String invalid : List.of("\" \"", "\"not an item id\"", "\"itemlab:unknown_item\"", "\"#itemlab:combat_repairs\""))
            cases.add(new ContainerProblem("lock", "items/1", invalid, "lock", "items"));
        cases.add(new ContainerProblem("lock", "items", "[]", "lock", "items"));
        cases.add(new ContainerProblem("lock", "items", "\"#itemlab:unknown_items\"", "lock", "items"));
        cases.add(new ContainerProblem("lock", "count", "\"invalid integer\"", "lock", "count"));
        cases.add(new ContainerProblem("lock", "count/min", "\"invalid minimum\"", "lock", "count/min"));
        cases.add(new ContainerProblem("lock", "count/max", "\"invalid maximum\"", "lock", "count/max"));
        cases.add(new ContainerProblem("lock", "count", "{\"min\":4,\"max\":2}", "lock", "count/min"));
        for (String target : List.of("exactComponentsSource", "partialPredicatesSource"))
            for (String invalid : List.of("{", target.equals("exactComponentsSource") ? "{\"itemlab:unknown_component\":{}}" : "{\"itemlab:unknown_predicate\":{}}",
                "{\"minecraft:custom_data\":1}"))
                cases.add(new ContainerProblem(target, "", invalid, "lock", target.equals("exactComponentsSource") ? "components" : "predicates"));
        cases.add(new ContainerProblem("lock", "$", "[]", "lock", "$enabled"));
        for (String invalid : List.of(" ", "not a loot table"))
            cases.add(new ContainerProblem("lootTable", "", invalid, "loot_table", "loot_table"));
        for (String invalid : List.of("invalid seed", "9223372036854775808", "-9223372036854775809"))
            cases.add(new ContainerProblem("lootSeed", "", invalid, "loot_table", "seed"));
        cases.add(new ContainerProblem("loot", "$", "[]", "loot_table", "$enabled"));
        return cases;
    }

    @SuppressWarnings("unchecked")
    private static Runnable invalidateContainer(ItemEditor editor, ContainerProblem problem) throws Exception {
        Object panel = read(editor, "containerEditor");
        if (problem.target().equals("contents") || problem.target().equals("bundleContents")) {
            boolean container = problem.target().equals("contents");
            int index = container ? 255 : 2;
            ContainerContentsDraft contents = (ContainerContentsDraft) read(panel, "contents");
            BundleContentsDraft bundle = (BundleContentsDraft) read(panel, "bundleContents");
            ItemStack valid = container ? contents.item(index) : bundle.item(index);
            ItemStack invalid = valid.copy();
            if (problem.path().equals("count")) invalid.setCount(100);
            else if (problem.path().equals("max_stack_size")) invalid.set(DataComponents.MAX_STACK_SIZE, 0);
            else invalid = new ItemStack(Items.SHULKER_BOX);
            if (container) contents.setItem(index, invalid);
            else bundle.setItem(index, invalid);
            return () -> {
                if (container) contents.setItem(index, valid);
                else bundle.setItem(index, valid);
            };
        }
        if (!problem.target().equals("lock") && !problem.target().equals("loot")) {
            ImString input = (ImString) read(panel, problem.target());
            String valid = input.get();
            input.set(problem.invalid());
            String changed = problem.target().equals("containedItemId") ? "containedItemIdChanged" : problem.target().equals("bundleItemId") ? "bundleItemIdChanged" : null;
            if (changed != null) invoke(panel, changed, new Class<?>[0]);
            return () -> {
                input.set(valid);
                if (changed != null) try {
                    invoke(panel, changed, new Class<?>[0]);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            };
        }
        CodecComponentState state = (CodecComponentState) read(panel, problem.target());
        JsonElement invalid = JsonParser.parseString(problem.invalid());
        Runnable repair;
        if (problem.path().equals("$")) {
            JsonElement valid = state.value();
            state.setValue(invalid);
            repair = () -> state.setValue(valid);
        } else {
            String[] path = problem.path().split("/");
            JsonElement parent = state.value();
            for (int index = 0; index < path.length - 1; index++)
                parent = parent.isJsonArray() ? parent.getAsJsonArray().get(Integer.parseInt(path[index])) : parent.getAsJsonObject().get(path[index]);
            JsonElement owner = parent;
            String key = path[path.length - 1];
            JsonElement valid = parent.isJsonArray() ? parent.getAsJsonArray().get(Integer.parseInt(key)) : parent.getAsJsonObject().get(key);
            if (parent.isJsonArray()) parent.getAsJsonArray().set(Integer.parseInt(key), invalid);
            else parent.getAsJsonObject().add(key, invalid);
            repair = () -> {
                if (owner.isJsonArray()) owner.getAsJsonArray().set(Integer.parseInt(key), valid);
                else if (valid == null) owner.getAsJsonObject().remove(key);
                else owner.getAsJsonObject().add(key, valid);
            };
        }
        String key = "container/lock/items/@identifier/" + (problem.path().startsWith("items/") ? problem.path().substring(6) : "0");
        ImString input = problem.control().equals("items") ? ((Map<String, ImString>) read(panel, "textBuffers")).get(key) : null;
        String valid = input == null ? "" : input.get();
        if (input != null) input.set(invalid.isJsonPrimitive() ? invalid.getAsString() : invalid.toString());
        return () -> {
            repair.run();
            if (input != null) input.set(valid);
        };
    }

    private static void assertContainerRejected(Fixture fixture, ItemEditor editor, String action, String tab) throws Exception {
        ItemStack opening = editor.draft().copy();
        Object history = read(editor, "lastEditorState");
        var buffers = containerBuffers(fixture, editor, false);
        var references = containerBuffers(fixture, editor, true);
        var lore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
        var data = dataBuffers((DataEditorPanel) read(editor, "dataEditor"));
        var prepared = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
        require(!prepared.succeeded() && buffers.equals(containerBuffers(fixture, editor, false)), "Container rejection preflight changed input or accepted invalid ownership/source");
        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
        require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals(tab)
                && buffers.equals(containerBuffers(fixture, editor, false)) && data.equals(dataBuffers((DataEditorPanel) read(editor, "dataEditor")))
                && lore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState()) && history == read(editor, "lastEditorState")
                && ((Deque<?>) read(editor, "editorUndo")).isEmpty() && !((ItemEditHistory) read(editor, "history")).canUndo()
                && ItemStack.matches(opening, editor.draft()) && ItemStack.matches(opening, fixture.slots.get(0)) && fixture.slots.size() == 1 && fixture.writes.isEmpty(),
            "Container rejection lost input/history, used the wrong route or wrote output: " + read(editor, "status"));
        var retained = containerBuffers(fixture, editor, true);
        require(references.size() == retained.size(), "Container recovery replaced collections");
        for (int index = 0; index < references.size(); index++)
            require(references.get(index) == retained.get(index), "Container recovery replaced source/model/buffer/stored-item/native-cache identity");
    }

    @SuppressWarnings("unchecked")
    private static void verifyContainerActionFailures() throws Exception {
        for (ContainerProblem problem : containerProblems())
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(containerExpected(fixture, containerValues(false)));
                    var values = containerValues(true);
                    addContainerPending(fixture, editor, values);
                    captureHistory(editor, fixture.client);
                    Object panel = read(editor, "containerEditor");
                    Runnable repair = invalidateContainer(editor, problem);
                    try {
                        assertContainerRejected(fixture, editor, action, "CONTAINER");
                    } catch (AssertionError error) {
                        throw new AssertionError("Container " + problem + "/" + action, error);
                    }
                    long row = ((List<Long>) read(panel, "bundleRowIds")).get(2);
                    String control = problem.control().equals("row/@2") ? "row/" + row : problem.control();
                    require(problem.section().equals(read(panel, "pendingFocusSection")) && control.equals(read(panel, "pendingFocusField")) && !(boolean) read(panel, "focusPendingToolbar"),
                        "Container precise recovery mismatch: " + problem + "/" + read(panel, "pendingFocusSection") + "/" + read(panel, "pendingFocusField"));
                    if (control.equals("items"))
                        require((int) read(read(panel, "lockItemsFocus"), "requestedRow") == (problem.path().startsWith("items/") ? 1 : 0), "Container Lock recovery focused the wrong identifier row");
                    if (control.equals("components") || control.equals("predicates"))
                        require((boolean) read(read(panel, control.equals("components") ? "exactComponentsEditor" : "partialPredicatesEditor"), "focusRequested"), "Container recovery did not request the actual code input");
                    if (control.startsWith("row/"))
                        require((long) read(panel, "recoveryBundleRowId") == row && !(boolean) invoke(panel, "allowBundleFocusSelection", new Class<?>[]{long.class}, row), "Container Bundle recovery would select/reload another inspector");
                    if (control.startsWith("slot/"))
                        require((int) read(panel, "recoveryContentsSlot") == 255 && !(boolean) invoke(panel, "allowContentsFocusSelection", new Class<?>[]{int.class}, 255), "Container stored-slot recovery would select/reload another inspector");
                    repair.run();
                    assertContainerOutput(fixture, verifyActionHistory(fixture, editor, action, "Container repaired " + problem), values, Set.of());
                }
    }

    private static void verifyContainerOtherFailures() throws Exception {
        for (boolean earlier : List.of(true, false))
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(containerExpected(fixture, containerValues(false)));
                    var values = containerValues(true);
                    addContainerPending(fixture, editor, values);
                    captureHistory(editor, fixture.client);
                    ImString source = earlier ? (ImString) read(editor, "itemId") : (ImString) read(((List<?>) read(read(editor, "dataEditor"), "entries")).getFirst(), "value");
                    String valid = source.get();
                    source.set(earlier ? "not an item id" : "invalid late integer");
                    assertContainerRejected(fixture, editor, action, earlier ? "NAME" : "DATA");
                    source.set(valid);
                    assertContainerOutput(fixture, verifyActionHistory(fixture, editor, action, "Container earlier/later " + earlier), values, Set.of());
                }
    }

    private static void verifyContainerConflicts() throws Exception {
        var generatedValues = containerValues(false);
        generatedValues.put("container", TagParser.parseCompoundFully("{value:[{slot:4,item:{id:\"minecraft:snowball\",count:4}}]}").get("value"));
        generatedValues.put("bundle_contents", TagParser.parseCompoundFully("{value:[{id:\"minecraft:snowball\",count:4}]}").get("value"));
        generatedValues.put("lock", TagParser.parseCompoundFully("{items:\"minecraft:diamond\",count:4}"));
        generatedValues.put("container_loot", TagParser.parseCompoundFully("{loot_table:\"itemlab:generated_loot\",seed:4L}"));
        for (String target : CONTAINER_COMPONENTS)
            for (boolean keepContainer : List.of(true, false))
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                    try (Fixture fixture = new Fixture()) {
                        ItemStack opening = containerExpected(fixture, containerValues(false));
                        ItemEditor editor = fixture.editedEditor(opening);
                        var option = new ComponentOption("minecraft:" + target, target, dataType(target));
                        write(editor, "componentOptions", List.of(option));
                        require(editor.selectComponent(fixture.client, option), "Container component selection failed");
                        write(editor, "useGenericSelectedComponentEditor", true);
                        var values = containerValues(true);
                        addContainerPending(fixture, editor, values);
                        ItemStack chosen = containerExpected(fixture, generatedValues);
                        JsonElement generated = ItemStackCodecs.encodeComponent(fixture.client, dataType(target), chosen.get(dataType(target)));
                        ComponentFormPanel form = (ComponentFormPanel) read(editor, "componentForm");
                        JsonElement baseline = form.pendingValue().deepCopy();
                        form.restorePending(baseline, generated);
                        captureHistory(editor, fixture.client);
                        assertContainerRejected(fixture, editor, action, "CONTAINER");
                        require(editor.selectedComponent() == option && (boolean) read(editor, "useGenericSelectedComponentEditor") && generated.equals(form.pendingValue()) && baseline.equals(read(form, "loadedValue")), "Container conflict replaced generated selection/value/baseline");
                        if (keepContainer) form.discardChanges();
                        else {
                            invoke(read(editor, "containerEditor"), "sync", new Class<?>[]{Minecraft.class, ItemStack.class}, fixture.client, opening);
                            values = containerValues(false);
                            values.put(target, generatedValues.get(target));
                        }
                        assertContainerOutput(fixture, verifyActionHistory(fixture, editor, action, "Container generated resolution " + target + "/" + keepContainer), values, Set.of());
                    }
    }

    @SuppressWarnings("unchecked")
    private static void verifyContainerComponentResets() throws Exception {
        for (String target : CONTAINER_COMPONENTS)
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemStack opening = containerExpected(fixture, containerValues(false));
                    ItemEditor editor = fixture.editedEditor(opening);
                    var values = containerValues(true);
                    addContainerPending(fixture, editor, values);
                    captureHistory(editor, fixture.client);
                    Object panel = read(editor, "containerEditor");
                    String invalidName = target.equals("lock") ? "lootSeed" : "exactComponentsSource";
                    ImString invalid = (ImString) read(panel, invalidName);
                    String valid = invalid.get();
                    invalid.set(target.equals("lock") ? "invalid seed" : "{");
                    Map<String, Object> models = new HashMap<>();
                    for (String name : List.of("contents", "bundleContents", "lock", "loot"))
                        models.put(name, read(panel, name));
                    Map<String, JsonElement> metadata = new HashMap<>();
                    Map<String, Object> caches = new HashMap<>();
                    for (String name : List.of("lock", "loot")) {
                        metadata.put(name, ((CodecComponentState) read(panel, name)).value());
                        caches.put(name, read(read(panel, name), "nbtValue"));
                    }
                    List<Long> rows = List.copyOf((List<Long>) read(panel, "bundleRowIds"));
                    Map<String, ImString> inputs = new HashMap<>((Map<String, ImString>) read(panel, "textBuffers"));
                    Map<String, String> sources = new HashMap<>();
                    inputs.forEach((key, input) -> sources.put(key, input.get()));
                    Map<String, String> raw = new HashMap<>();
                    for (String name : List.of("exactComponentsSource", "partialPredicatesSource", "lootTable", "lootSeed", "containedItemId", "bundleItemId"))
                        raw.put(name, ((ImString) read(panel, name)).get());
                    invoke(panel, "requestPendingFocus", new Class<?>[0]);
                    invoke(panel, "syncComponent", new Class<?>[]{Minecraft.class, DataComponentType.class, ItemStack.class}, fixture.client, dataType(target), opening);
                    String owned = switch (target) {
                        case "container" -> "contents";
                        case "bundle_contents" -> "bundleContents";
                        case "container_loot" -> "loot";
                        default -> "lock";
                    };
                    for (String name : models.keySet())
                        if (!name.equals(owned))
                            require(models.get(name) == read(panel, name), "Container component reset replaced unrelated model identity");
                    for (String name : metadata.keySet())
                        if (!name.equals(owned))
                            require(metadata.get(name) == ((CodecComponentState) read(panel, name)).value() && caches.get(name) == read(read(panel, name), "nbtValue"), "Container component reset replaced unrelated JSON/cache identity");
                    for (String name : raw.keySet())
                        if (!(target.equals("lock") && (name.startsWith("exact") || name.startsWith("partial")))
                            && !(target.equals("container_loot") && name.startsWith("loot")) && !(target.equals("container") && name.equals("containedItemId"))
                            && !(target.equals("bundle_contents") && name.equals("bundleItemId")))
                            require(raw.get(name).equals(((ImString) read(panel, name)).get()), "Container component reset replaced unrelated raw input");
                    Map<String, ImString> retained = (Map<String, ImString>) read(panel, "textBuffers");
                    for (String key : inputs.keySet())
                        if (!target.equals("lock") || !key.startsWith("container/lock/"))
                            require(retained.get(key) == inputs.get(key) && sources.get(key).equals(retained.get(key).get()), "Container component reset replaced unrelated input identity/source");
                    require((target.equals("bundle_contents") || rows.equals(read(panel, "bundleRowIds")))
                            && (target.equals("lock") || (int) read(read(panel, "exactComponentsEditor"), "cursorByteOffset") == 3 && (int) read(read(panel, "partialPredicatesEditor"), "cursorByteOffset") == 3)
                            && (boolean) read(panel, "dirty") && read(panel, "pendingFocusSection") == null && read(panel, "pendingFocusField") == null
                            && (int) read(panel, "recoveryContentsSlot") == -1 && (long) read(panel, "recoveryBundleRowId") == 0L
                            && (int) read(read(panel, "lockItemsFocus"), "requestedRow") == -1 && !(boolean) read(read(panel, "exactComponentsEditor"), "focusRequested"),
                        "Container component reset lost unrelated row/cursor/dirty state or retained recovery");
                    var before = containerBuffers(fixture, editor, false);
                    var rejected = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                    require(!rejected.succeeded() && before.equals(containerBuffers(fixture, editor, false)) && fixture.writes.isEmpty(), "Container reset discarded/accepted unrelated invalid input");
                    invalid.set(valid);
                    values.put(target, containerValues(false).get(target));
                    assertContainerOutput(fixture, verifyActionHistory(fixture, editor, action, "Container component reset " + target), values, Set.of());
                }
    }

    private static void verifyContainerInspectorRetargets() throws Exception {
        for (String target : List.of("container", "bundle_contents"))
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(containerExpected(fixture, containerValues(false)));
                    var values = containerValues(true);
                    addContainerPending(fixture, editor, values);
                    Object panel = read(editor, "containerEditor");
                    ((ImString) read(panel, target.equals("container") ? "containedItemId" : "bundleItemId")).set(" minecraft:iron_ingot ");
                    invoke(panel, target.equals("container") ? "containedItemIdChanged" : "bundleItemIdChanged", new Class<?>[0]);
                    CompoundTag entry = (CompoundTag) ((ListTag) values.get(target)).get(1);
                    CompoundTag item = target.equals("container") ? entry.getCompound("item").orElseThrow() : entry;
                    item.putString("id", "minecraft:iron_ingot");
                    assertContainerPreflight(fixture, editor);
                    assertContainerOutput(fixture, verifyActionHistory(fixture, editor, action, "Container inspector retarget " + target), values, Set.of());
                }
    }

    private static final List<String> COMBAT_COMPONENTS = List.of("minimum_attack_charge", "damage_type", "swing_animation", "weapon", "piercing_weapon", "kinetic_weapon",
        "charged_projectiles", "tool", "attack_range", "damage_resistant", "repairable", "blocks_attacks", "enchantable");

    private static CodecComponentState combatState(ItemEditor editor, String target) throws Exception {
        return (CodecComponentState) read(read(editor, "combatEditor"), switch (target) {
            case "minimum_attack_charge" -> "minimumCharge";
            case "damage_type" -> "damageType";
            case "swing_animation" -> "swing";
            case "piercing_weapon" -> "piercingWeapon";
            case "kinetic_weapon" -> "kineticWeapon";
            case "charged_projectiles" -> "chargedProjectiles";
            case "attack_range" -> "range";
            case "damage_resistant" -> "resistance";
            case "blocks_attacks" -> "blocksAttacks";
            default -> target;
        });
    }

    private static String combatSection(String target) {
        return switch (target) {
            case "minimum_attack_charge" -> "minimum_charge";
            case "swing_animation" -> "swing";
            default -> target;
        };
    }

    private static Map<String, JsonElement> combatValues(boolean pending) {
        var values = new java.util.LinkedHashMap<String, JsonElement>();
        values.put("minimum_attack_charge", JsonParser.parseString(pending ? "0.75" : "0.1"));
        values.put("damage_type", JsonParser.parseString(pending ? "\"minecraft:player_attack\"" : "\"minecraft:in_fire\""));
        values.put("swing_animation", JsonParser.parseString(pending ? "{\"type\":\"stab\",\"duration\":12}" : "{\"type\":\"whack\",\"duration\":3}"));
        values.put("weapon", JsonParser.parseString(pending ? "{\"item_damage_per_attack\":2,\"disable_blocking_for_seconds\":1.5}" : "{\"item_damage_per_attack\":1}"));
        values.put("piercing_weapon", JsonParser.parseString(pending
            ? "{\"deals_knockback\":false,\"dismounts\":true,\"sound\":\"minecraft:entity.generic.eat\",\"hit_sound\":{\"sound_id\":\"itemlab:piercing_hit\",\"range\":-1}}"
            : "{\"deals_knockback\":true,\"dismounts\":false}"));
        values.put("kinetic_weapon", JsonParser.parseString(pending
            ? "{\"contact_cooldown_ticks\":0,\"delay_ticks\":2,\"forward_movement\":-2,\"damage_multiplier\":-3,\"sound\":\"minecraft:entity.generic.eat\","
            + "\"hit_sound\":{\"sound_id\":\"itemlab:kinetic_hit\",\"range\":0},\"dismount_conditions\":{\"max_duration_ticks\":3,\"min_speed\":-1},"
            + "\"knockback_conditions\":{\"max_duration_ticks\":4,\"min_relative_speed\":-2},\"damage_conditions\":{\"max_duration_ticks\":5,\"min_speed\":0,\"min_relative_speed\":-3}}"
            : "{\"contact_cooldown_ticks\":1,\"delay_ticks\":1,\"forward_movement\":0.1,\"damage_multiplier\":0.2}"));
        values.put("charged_projectiles", JsonParser.parseString("[]")); // Native projectiles are supplied separately.
        values.put("tool", JsonParser.parseString(pending
            ? "{\"rules\":[{\"blocks\":\"minecraft:stone\"},{\"blocks\":[\"minecraft:stone\",\"minecraft:dirt\",\"minecraft:stone\"],\"speed\":2,\"correct_for_drops\":true},"
            + "{\"blocks\":\"#itemlab:adventure_blocks\",\"speed\":3,\"correct_for_drops\":false}],\"default_mining_speed\":-1,\"damage_per_block\":0,\"can_destroy_blocks_in_creative\":false}"
            : "{\"rules\":[{\"blocks\":\"minecraft:dirt\",\"speed\":1}],\"default_mining_speed\":1,\"damage_per_block\":1}"));
        values.put("attack_range", JsonParser.parseString(pending
            ? "{\"min_reach\":64,\"max_reach\":0,\"min_creative_reach\":64,\"max_creative_reach\":0,\"hitbox_margin\":1,\"mob_factor\":2}"
            : "{\"min_reach\":0,\"max_reach\":1,\"min_creative_reach\":0,\"max_creative_reach\":1,\"hitbox_margin\":0,\"mob_factor\":1}"));
        values.put("damage_resistant", JsonParser.parseString(pending ? "{\"types\":[\"minecraft:in_fire\",\"minecraft:player_attack\",\"minecraft:in_fire\"]}" : "{\"types\":\"minecraft:in_fire\"}"));
        values.put("repairable", JsonParser.parseString(pending ? "{\"items\":[\"minecraft:stone\",\"minecraft:iron_ingot\",\"minecraft:stone\"]}" : "{\"items\":\"minecraft:stone\"}"));
        values.put("blocks_attacks", JsonParser.parseString(pending
            ? "{\"block_delay_seconds\":0,\"disable_cooldown_scale\":0,\"block_sound\":\"minecraft:entity.generic.eat\",\"disabled_sound\":{\"sound_id\":\"itemlab:disabled\",\"range\":-1},"
            + "\"bypassed_by\":[\"minecraft:in_fire\",\"minecraft:player_attack\",\"minecraft:in_fire\"],\"item_damage\":{\"threshold\":0,\"base\":-1,\"factor\":-2},"
            + "\"damage_reductions\":[{\"base\":0,\"factor\":1},{\"horizontal_blocking_angle\":90,\"type\":[\"minecraft:in_fire\",\"minecraft:player_attack\",\"minecraft:in_fire\"],\"base\":-1,\"factor\":-2},"
            + "{\"horizontal_blocking_angle\":180,\"type\":\"#itemlab:combat_damage\",\"base\":2,\"factor\":0}]}"
            : "{\"block_delay_seconds\":1,\"disable_cooldown_scale\":1,\"damage_reductions\":[{\"base\":0,\"factor\":1}]}"));
        values.put("enchantable", JsonParser.parseString(pending ? "{\"value\":42}" : "{\"value\":1}"));
        return values;
    }

    private static ListTag combatProjectiles(String representation) throws Exception {
        var result = new ListTag();
        if (representation.equals("empty")) return result;
        if (representation.equals("scalar")) {
            result.add(net.minecraft.nbt.StringTag.valueOf("minecraft:arrow"));
            return result;
        }
        if (representation.equals("opening")) {
            result.add(TagParser.parseCompoundFully("{id:\"minecraft:arrow\",count:2}"));
            return result;
        }
        if (representation.startsWith("count_")) {
            result.add(TagParser.parseCompoundFully("{id:\"minecraft:arrow\",count:" + representation.substring(6) + ",components:{\"minecraft:max_stack_size\":16}}"));
            return result;
        }
        result.add(TagParser.parseCompoundFully("{id:\"minecraft:arrow\",count:99,components:{\"minecraft:max_stack_size\":16,\"minecraft:custom_data\":" + ADVENTURE_DATA + "}}"));
        result.add(TagParser.parseCompoundFully("{id:\"minecraft:diamond\",count:3,components:{\"minecraft:custom_data\":" + ADVENTURE_DATA + ",\"minecraft:instrument\":\"minecraft:ponder_goat_horn\"}}"));
        result.add(TagParser.parseCompoundFully("{id:\"minecraft:arrow\",count:2,components:{\"!minecraft:custom_name\":{},\"minecraft:item_name\":\"Projectile {name} ☃\"}}"));
        return result;
    }

    private static ItemStack combatOpening(Fixture fixture) throws Exception {
        ItemStack opening = new ItemStack(Items.STONE);
        for (var entry : combatValues(false).entrySet())
            if (!entry.getKey().equals("charged_projectiles"))
                ItemStackCodecs.decodeAndSetComponent(fixture.client, opening, dataType(entry.getKey()), entry.getValue());
        ItemStackCodecs.decodeAndSetComponentNbt(fixture.client, opening, DataComponents.CHARGED_PROJECTILES, combatProjectiles("opening"));
        return opening;
    }

    @SuppressWarnings("unchecked")
    private static void seedCombatCollection(Object panel, JsonElement values, String path) throws Exception {
        Map<String, ImString> buffers = (Map<String, ImString>) read(panel, "textBuffers");
        if (values == null) return;
        List<JsonElement> entries = values.isJsonArray() ? values.getAsJsonArray().asList() : List.of(values);
        for (int index = 0; index < entries.size(); index++)
            buffers.put(path + "/@identifier/" + index, new ImString(" " + entries.get(index).getAsString() + " ", 512));
        ((Map<String, IdentifierCollectionEditor.FocusState>) read(panel, "collectionFocus")).put(path, new IdentifierCollectionEditor.FocusState());
    }

    @SuppressWarnings("unchecked")
    private static void seedCombatInputs(ItemEditor editor) throws Exception {
        Object panel = read(editor, "combatEditor");
        Map<String, ImString> buffers = (Map<String, ImString>) read(panel, "textBuffers");
        JsonElement damage = combatState(editor, "damage_type").value();
        if (damage.isJsonPrimitive()) buffers.put("damage_type", new ImString(" " + damage.getAsString() + " ", 512));
        for (String target : List.of("piercing_weapon", "kinetic_weapon", "blocks_attacks")) {
            JsonObject value = combatState(editor, target).value().getAsJsonObject();
            for (String key : target.equals("blocks_attacks") ? List.of("block_sound", "disabled_sound") : List.of("sound", "hit_sound"))
                if (value.has(key) && value.get(key).isJsonPrimitive())
                    buffers.put((target.equals("blocks_attacks") ? "blocking" : target) + "/" + key, new ImString(" " + value.get(key).getAsString() + " ", 512));
        }
        for (String target : List.of("tool", "blocks_attacks")) {
            JsonObject value = combatState(editor, target).value().getAsJsonObject();
            String array = target.equals("tool") ? "rules" : "damage_reductions";
            if (!value.has(array)) continue;
            var entries = value.getAsJsonArray(array);
            List<Long> rows = (List<Long>) read(panel, target.equals("tool") ? "toolRuleRowIds" : "damageReductionRowIds");
            invoke(panel, "ensureRowIds", new Class<?>[]{List.class, int.class, java.util.concurrent.atomic.AtomicLong.class}, rows, entries.size(),
                read(panel, target.equals("tool") ? "NEXT_TOOL_RULE_ID" : "NEXT_DAMAGE_REDUCTION_ID"));
            for (int index = 0; index < entries.size(); index++)
                seedCombatCollection(panel, entries.get(index).getAsJsonObject().get(target.equals("tool") ? "blocks" : "type"),
                    (target.equals("tool") ? "tool/rule/" : "blocking/reduction/") + rows.get(index) + (target.equals("tool") ? "/blocks" : "/type"));
        }
        seedCombatCollection(panel, combatState(editor, "damage_resistant").value().getAsJsonObject().get("types"), "resistance/types");
        seedCombatCollection(panel, combatState(editor, "repairable").value().getAsJsonObject().get("items"), "repairable/items");
        seedCombatCollection(panel, combatState(editor, "blocks_attacks").value().getAsJsonObject().get("bypassed_by"), "blocking/bypassed_by");
    }

    private static void addCombatPending(Fixture fixture, ItemEditor editor, Map<String, JsonElement> values, ListTag projectiles) throws Exception {
        Object panel = read(editor, "combatEditor");
        write(panel, "damageTypeIds", java.util.stream.Stream.concat(fixtureRegistries.lookupOrThrow(Registries.DAMAGE_TYPE).keySet().stream().map(Identifier::toString),
            java.util.stream.Stream.of("#itemlab:combat_damage")).map(IdSuggestion::of).toList());
        write(panel, "blockIds", java.util.stream.Stream.concat(BuiltInRegistries.BLOCK.keySet().stream().map(Identifier::toString),
            java.util.stream.Stream.of("#itemlab:adventure_blocks")).map(IdSuggestion::of).toList());
        write(panel, "itemIds", java.util.stream.Stream.concat(BuiltInRegistries.ITEM.keySet().stream().map(Identifier::toString),
            java.util.stream.Stream.of("#itemlab:combat_repairs")).map(IdSuggestion::of).toList());
        for (String target : COMBAT_COMPONENTS) {
            CodecComponentState state = combatState(editor, target);
            state.setPresent(true);
            state.setValue(values.get(target));
        }
        ItemStack loaded = new ItemStack(Items.STONE);
        ItemStackCodecs.decodeAndSetComponentNbt(fixture.client, loaded, DataComponents.CHARGED_PROJECTILES, projectiles);
        combatState(editor, "charged_projectiles").setValue(ItemStackCodecs.encodeComponent(fixture.client, DataComponents.CHARGED_PROJECTILES, loaded.get(DataComponents.CHARGED_PROJECTILES)));
        invoke(read(editor, "combatEditor"), "loadProjectileRows", new Class<?>[]{Minecraft.class, ItemStack.class}, fixture.client, loaded);
        seedCombatInputs(editor);
        write(read(editor, "combatEditor"), "dirty", true);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> combatBuffers(ItemEditor editor, boolean references) throws Exception {
        Object panel = read(editor, "combatEditor");
        var result = new ArrayList<Object>();
        for (String target : COMBAT_COMPONENTS) {
            CodecComponentState state = combatState(editor, target);
            if (references) {
                result.add(state);
                jsonReferences(state.value(), result);
                result.add(read(state, "nbtValue") == null ? "" : read(state, "nbtValue"));
            } else
                result.add(List.of(state.present(), state.value().deepCopy(), read(state, "nbtValue") == null ? "" : ((Tag) read(state, "nbtValue")).copy()));
        }
        for (String name : List.of("projectileRows", "toolRuleRowIds", "damageReductionRowIds")) {
            List<?> rows = (List<?>) read(panel, name);
            if (references) result.add(rows);
            if (!name.equals("projectileRows")) {
                result.add(references ? rows : List.copyOf(rows));
                continue;
            }
            for (Object row : rows) {
                if (references) result.add(row);
                else result.add(read(row, "id"));
                for (String field : List.of("itemId", "count", "componentsPresent", "componentsSource")) {
                    Object input = read(row, field);
                    result.add(references ? input : input instanceof ImString text ? text.get() : input instanceof ImInt number ? number.get() : ((ImBoolean) input).get());
                }
                JsonObject extra = (JsonObject) read(row, "extraFields");
                if (references) jsonReferences(extra, result);
                else result.add(extra.deepCopy());
                Object code = read(row, "componentsEditor");
                if (references) result.add(code);
                else result.add(read(code, "cursorByteOffset"));
            }
        }
        Map<String, ImString> buffers = (Map<String, ImString>) read(panel, "textBuffers");
        if (references) result.add(buffers);
        for (String key : buffers.keySet().stream().sorted().toList()) {
            if (!references) result.add(key);
            result.add(references ? buffers.get(key) : buffers.get(key).get());
        }
        Map<String, IdentifierCollectionEditor.FocusState> focus = (Map<String, IdentifierCollectionEditor.FocusState>) read(panel, "collectionFocus");
        if (references) {
            result.add(focus);
            for (String key : focus.keySet().stream().sorted().toList()) result.add(focus.get(key));
        } else
            result.add(focus.keySet().stream().sorted().toList()); // Focus requests themselves are transient and view-only.
        return result;
    }

    private static void assertCombatOutput(Fixture fixture, ItemStack saved, Map<String, JsonElement> values, ListTag projectiles, Set<String> removed) {
        assertCombined(saved);
        ItemStack expected = new ItemStack(Items.STONE);
        for (String target : COMBAT_COMPONENTS) {
            if (removed.contains(target)) require(!saved.has(dataType(target)), "Combat failed to remove " + target);
            else {
                if (target.equals("charged_projectiles"))
                    ItemStackCodecs.decodeAndSetComponentNbt(fixture.client, expected, DataComponents.CHARGED_PROJECTILES, projectiles);
                else
                    ItemStackCodecs.decodeAndSetComponent(fixture.client, expected, dataType(target), values.get(target));
                require(ItemStackCodecs.encodeComponentNbt(fixture.client, dataType(target), expected.get(dataType(target))).equals(
                    ItemStackCodecs.encodeComponentNbt(fixture.client, dataType(target), saved.get(dataType(target)))), "Combat lost exact native output: " + target);
            }
        }
    }

    private record CombatValueCase(String target, String source) {
    }

    private static List<CombatValueCase> combatValueCases() {
        var cases = new ArrayList<CombatValueCase>();
        for (String value : List.of("0", "0.5", "1")) cases.add(new CombatValueCase("minimum_attack_charge", value));
        cases.add(new CombatValueCase("damage_type", "\"minecraft:in_fire\""));
        for (String id : List.of("minecraft:player_attack", "minecraft:magic", "minecraft:fall"))
            cases.add(new CombatValueCase("damage_type", "\"" + id + "\""));
        for (String animation : List.of("none", "whack", "stab"))
            cases.add(new CombatValueCase("swing_animation", "{\"type\":\"" + animation + "\",\"duration\":1}"));
        cases.add(new CombatValueCase("swing_animation", "{}"));
        for (String value : List.of("{}", "{\"item_damage_per_attack\":0,\"disable_blocking_for_seconds\":0}", "{\"item_damage_per_attack\":2147483647,\"disable_blocking_for_seconds\":2.5}"))
            cases.add(new CombatValueCase("weapon", value));
        for (String target : List.of("piercing_weapon", "kinetic_weapon")) {
            cases.add(new CombatValueCase(target, "{}"));
            cases.add(new CombatValueCase(target, "{\"sound\":\"minecraft:entity.generic.eat\",\"hit_sound\":\"minecraft:entity.generic.drink\"}"));
            cases.add(new CombatValueCase(target, "{\"sound\":{\"sound_id\":\"itemlab:direct\",\"range\":-1},\"hit_sound\":{\"sound_id\":\"itemlab:hit\",\"range\":0}}"));
        }
        for (String key : List.of("dismount_conditions", "knockback_conditions", "damage_conditions"))
            cases.add(new CombatValueCase("kinetic_weapon",
                "{\"" + key + "\":{\"max_duration_ticks\":0,\"min_speed\":-1,\"min_relative_speed\":-2},\"forward_movement\":-2,\"damage_multiplier\":0}"));
        for (String value : List.of("{\"rules\":[]}", "{\"rules\":[{\"blocks\":[]}]}", "{\"rules\":[{\"blocks\":\"minecraft:stone\"}]}",
            "{\"rules\":[{\"blocks\":\"#itemlab:adventure_blocks\",\"speed\":0.01,\"correct_for_drops\":false}]}",
            "{\"rules\":[{\"blocks\":[\"minecraft:stone\",\"minecraft:dirt\",\"minecraft:stone\"],\"speed\":2,\"correct_for_drops\":true}],\"default_mining_speed\":-2,\"damage_per_block\":0,\"can_destroy_blocks_in_creative\":false}"))
            cases.add(new CombatValueCase("tool", value));
        for (String value : List.of("{}", "{\"min_reach\":0,\"max_reach\":64,\"min_creative_reach\":0,\"max_creative_reach\":64,\"hitbox_margin\":0,\"mob_factor\":0}",
            "{\"min_reach\":64,\"max_reach\":0,\"min_creative_reach\":64,\"max_creative_reach\":0,\"hitbox_margin\":1,\"mob_factor\":2}"))
            cases.add(new CombatValueCase("attack_range", value));
        for (String value : List.of("\"minecraft:in_fire\"", "[\"minecraft:in_fire\"]", "[\"minecraft:in_fire\",\"minecraft:player_attack\",\"minecraft:in_fire\"]", "[]", "\"#itemlab:combat_damage\"")) {
            cases.add(new CombatValueCase("damage_resistant", "{\"types\":" + value + "}"));
            cases.add(new CombatValueCase("blocks_attacks", "{\"bypassed_by\":" + value + "}"));
            cases.add(new CombatValueCase("blocks_attacks", "{\"damage_reductions\":[{\"type\":" + value + ",\"base\":-1,\"factor\":-2}]}"));
        }
        for (String value : List.of("\"minecraft:iron_ingot\"", "[\"minecraft:iron_ingot\"]", "[\"minecraft:stone\",\"minecraft:iron_ingot\",\"minecraft:stone\"]", "[]", "\"#itemlab:combat_repairs\""))
            cases.add(new CombatValueCase("repairable", "{\"items\":" + value + "}"));
        for (String value : List.of("{}", "{\"damage_reductions\":[]}", "{\"block_delay_seconds\":0,\"disable_cooldown_scale\":0,\"item_damage\":{\"threshold\":0,\"base\":-1,\"factor\":-2}}",
            "{\"block_sound\":{\"sound_id\":\"itemlab:block\",\"range\":-1},\"disabled_sound\":{\"sound_id\":\"itemlab:disable\",\"range\":0}}"))
            cases.add(new CombatValueCase("blocks_attacks", value));
        for (int value : List.of(1, 42, Integer.MAX_VALUE))
            cases.add(new CombatValueCase("enchantable", "{\"value\":" + value + "}"));
        return cases;
    }

    private static void verifyCombatCommits() throws Exception {
        for (CombatValueCase representation : combatValueCases())
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(combatOpening(fixture));
                    var values = combatValues(true);
                    var projectiles = combatProjectiles("typed");
                    values.put(representation.target(), JsonParser.parseString(representation.source()));
                    addCombatPending(fixture, editor, values, projectiles);
                    var before = combatBuffers(editor, false);
                    var references = combatBuffers(editor, true);
                    var prepared = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                    require(prepared.succeeded() && before.equals(combatBuffers(editor, false)), "Combat preflight changed source/models: " + representation + "/" + prepared.failure());
                    var retained = combatBuffers(editor, true);
                    require(references.size() == retained.size(), "Combat preflight replaced collections");
                    for (int index = 0; index < references.size(); index++)
                        require(references.get(index) == retained.get(index), "Combat preflight replaced input/model/row/cache identity");
                    assertCombatOutput(fixture, verifyActionHistory(fixture, editor, action, "Combat " + representation), values, projectiles, Set.of());
                }
        for (String representation : List.of("empty", "scalar", "typed", "count_1", "count_16", "count_64", "count_99"))
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(combatOpening(fixture));
                    var values = combatValues(true);
                    var projectiles = combatProjectiles(representation);
                    addCombatPending(fixture, editor, values, projectiles);
                    assertCombatOutput(fixture, verifyActionHistory(fixture, editor, action, "Combat projectiles " + representation), values, projectiles, Set.of());
                }
        for (String target : COMBAT_COMPONENTS)
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(combatOpening(fixture));
                    var values = combatValues(true);
                    var projectiles = combatProjectiles("typed");
                    addCombatPending(fixture, editor, values, projectiles);
                    combatState(editor, target).setPresent(false);
                    combatState(editor, target).setValue(JsonParser.parseString("\"invalid inactive input\""));
                    if (target.equals("charged_projectiles")) {
                        Object row = ((List<?>) read(read(editor, "combatEditor"), "projectileRows")).get(1);
                        ((ImString) read(row, "itemId")).set("not an item id");
                        ((ImInt) read(row, "count")).set(0);
                        ((ImString) read(row, "componentsSource")).set("{inactive invalid");
                    }
                    var before = combatBuffers(editor, false);
                    var prepared = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                    require(prepared.succeeded() && before.equals(combatBuffers(editor, false)), "Combat removal validated or replaced inactive input: " + target);
                    assertCombatOutput(fixture, verifyActionHistory(fixture, editor, action, "Combat removal " + target), values, projectiles, Set.of(target));
                }
        for (String target : COMBAT_COMPONENTS)
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemStack opening = combatOpening(fixture);
                    ItemEditor editor = fixture.editedEditor(opening);
                    var values = combatValues(true);
                    var projectiles = combatProjectiles(target.equals("charged_projectiles") ? "typed" : "opening");
                    for (String other : COMBAT_COMPONENTS)
                        if (!other.equals(target))
                            values.put(other, ItemStackCodecs.encodeComponent(fixture.client, dataType(other), opening.get(dataType(other))));
                    addCombatPending(fixture, editor, values, projectiles);
                    assertCombatOutput(fixture, verifyActionHistory(fixture, editor, action, "Combat single component " + target), values, projectiles, Set.of());
                }
    }

    private record CombatProblem(String target, String path, String invalid, String control) {
    }

    private static List<CombatProblem> combatProblems() {
        var cases = new ArrayList<CombatProblem>(List.of(
            new CombatProblem("minimum_attack_charge", "$", "-0.01", "value"),
            new CombatProblem("minimum_attack_charge", "$", "1.01", "value"),
            new CombatProblem("damage_type", "$", "\" \"", "value"),
            new CombatProblem("damage_type", "$", "\"not a damage id\"", "value"),
            new CombatProblem("damage_type", "$", "\"itemlab:unknown_damage\"", "value"),
            new CombatProblem("damage_type", "$", "{\"message_id\":\"itemlab.damage\",\"scaling\":\"never\",\"exhaustion\":-2,\"effects\":\"thorns\",\"death_message_type\":\"intentional_game_design\"}", "$enabled"),
            new CombatProblem("swing_animation", "type", "\"unknown animation\"", "type"),
            new CombatProblem("swing_animation", "duration", "0", "duration"),
            new CombatProblem("weapon", "item_damage_per_attack", "-1", "item_damage_per_attack"),
            new CombatProblem("weapon", "disable_blocking_for_seconds", "-1", "disable_blocking_for_seconds"),
            new CombatProblem("tool", "default_mining_speed", "\"invalid speed\"", "default_mining_speed"),
            new CombatProblem("tool", "damage_per_block", "-1", "damage_per_block"),
            new CombatProblem("tool", "can_destroy_blocks_in_creative", "\"invalid flag\"", "can_destroy_blocks_in_creative"),
            new CombatProblem("tool", "rules/1/blocks/1", "\"not a block id\"", "tool/rule/@1/blocks"),
            new CombatProblem("tool", "rules/1/blocks/1", "\"itemlab:unknown_block\"", "tool/rule/@1/blocks"),
            new CombatProblem("tool", "rules/1/blocks/1", "\"#itemlab:adventure_blocks\"", "tool/rule/@1/blocks"),
            new CombatProblem("tool", "rules/2/blocks", "\"#itemlab:unknown_blocks\"", "tool/rule/@2/blocks"),
            new CombatProblem("tool", "rules/1/speed", "0", "tool/rule/@1/speed"),
            new CombatProblem("tool", "rules/1/correct_for_drops", "\"invalid flag\"", "tool/rule/@1/correct_for_drops"),
            new CombatProblem("tool", "rules", "{}", "$enabled"),
            new CombatProblem("damage_resistant", "types/1", "\"not a damage id\"", "resistance/types"),
            new CombatProblem("damage_resistant", "types/1", "\"itemlab:unknown_damage\"", "resistance/types"),
            new CombatProblem("damage_resistant", "types/1", "\"#itemlab:combat_damage\"", "resistance/types"),
            new CombatProblem("damage_resistant", "types", "\"#itemlab:unknown_damage\"", "resistance/types"),
            new CombatProblem("repairable", "items/1", "\"not an item id\"", "repairable/items"),
            new CombatProblem("repairable", "items/1", "\"itemlab:unknown_item\"", "repairable/items"),
            new CombatProblem("repairable", "items/1", "\"#itemlab:combat_repairs\"", "repairable/items"),
            new CombatProblem("repairable", "items", "\"#itemlab:unknown_items\"", "repairable/items"),
            new CombatProblem("blocks_attacks", "block_delay_seconds", "-1", "block_delay_seconds"),
            new CombatProblem("blocks_attacks", "disable_cooldown_scale", "-1", "disable_cooldown_scale"),
            new CombatProblem("blocks_attacks", "block_sound", "\"itemlab:unknown_sound\"", "block_sound"),
            new CombatProblem("blocks_attacks", "disabled_sound/sound_id", "\"not a sound id\"", "disabled_sound"),
            new CombatProblem("blocks_attacks", "bypassed_by/1", "\"itemlab:unknown_damage\"", "blocking/bypassed_by"),
            new CombatProblem("blocks_attacks", "bypassed_by", "\"#itemlab:unknown_damage\"", "blocking/bypassed_by"),
            new CombatProblem("blocks_attacks", "item_damage/threshold", "-1", "blocking/item_damage/threshold"),
            new CombatProblem("blocks_attacks", "item_damage/base", "\"invalid float\"", "blocking/item_damage/base"),
            new CombatProblem("blocks_attacks", "item_damage/factor", "\"invalid float\"", "blocking/item_damage/factor"),
            new CombatProblem("blocks_attacks", "damage_reductions/1/horizontal_blocking_angle", "0", "blocking/reduction/@1/horizontal_blocking_angle"),
            new CombatProblem("blocks_attacks", "damage_reductions/1/type/1", "\"itemlab:unknown_damage\"", "blocking/reduction/@1/type"),
            new CombatProblem("blocks_attacks", "damage_reductions/2/type", "\"#itemlab:unknown_damage\"", "blocking/reduction/@2/type"),
            new CombatProblem("blocks_attacks", "damage_reductions/1/base", "\"invalid float\"", "blocking/reduction/@1/base"),
            new CombatProblem("blocks_attacks", "damage_reductions/1/factor", "\"invalid float\"", "blocking/reduction/@1/factor"),
            new CombatProblem("blocks_attacks", "damage_reductions", "{}", "$enabled"),
            new CombatProblem("enchantable", "value", "0", "value"),
            new CombatProblem("enchantable", "$", "10", "value")));
        for (String key : List.of("deals_knockback", "dismounts"))
            cases.add(new CombatProblem("piercing_weapon", key, "\"invalid flag\"", key));
        for (String target : List.of("piercing_weapon", "kinetic_weapon")) {
            cases.add(new CombatProblem(target, "sound", "\"itemlab:unknown_sound\"", "sound"));
            cases.add(new CombatProblem(target, "hit_sound/sound_id", "\"not a sound id\"", "hit_sound"));
        }
        for (String key : List.of("contact_cooldown_ticks", "delay_ticks"))
            cases.add(new CombatProblem("kinetic_weapon", key, "-1", key));
        for (String key : List.of("forward_movement", "damage_multiplier"))
            cases.add(new CombatProblem("kinetic_weapon", key, "\"invalid float\"", key));
        for (String key : List.of("dismount_conditions", "knockback_conditions", "damage_conditions")) {
            cases.add(new CombatProblem("kinetic_weapon", key, "\"invalid condition\"", key + "/$enabled"));
            cases.add(new CombatProblem("kinetic_weapon", key + "/max_duration_ticks", "-1", key + "/max_duration_ticks"));
        }
        cases.add(new CombatProblem("kinetic_weapon", "damage_conditions/min_speed", "\"invalid float\"", "damage_conditions/min_speed"));
        cases.add(new CombatProblem("kinetic_weapon", "damage_conditions/min_relative_speed", "\"invalid float\"", "damage_conditions/min_relative_speed"));
        for (String key : List.of("min_reach", "max_reach", "min_creative_reach", "max_creative_reach", "hitbox_margin", "mob_factor"))
            cases.add(new CombatProblem("attack_range", key, key.equals("hitbox_margin") ? "1.01" : key.equals("mob_factor") ? "2.01" : key.startsWith("min") ? "65" : "-1", key));
        return cases;
    }

    @SuppressWarnings("unchecked")
    private static String combatControl(ItemEditor editor, String template) throws Exception {
        if (!template.contains("@")) return template;
        Object panel = read(editor, "combatEditor");
        String[] parts = template.split("/");
        for (int index = 0; index < parts.length; index++)
            if (parts[index].startsWith("@")) {
                int row = Integer.parseInt(parts[index].substring(1));
                parts[index] = parts[0].equals("row") ? read(((List<?>) read(panel, "projectileRows")).get(row), "id").toString()
                    : ((List<Long>) read(panel, parts[0].equals("tool") ? "toolRuleRowIds" : "damageReductionRowIds")).get(row).toString();
            }
        return String.join("/", parts);
    }

    private static boolean combatCollectionControl(String control) {
        return control.equals("resistance/types") || control.equals("repairable/items") || control.equals("blocking/bypassed_by")
            || control.startsWith("tool/rule/") && control.endsWith("/blocks") || control.startsWith("blocking/reduction/") && control.endsWith("/type");
    }

    private static int combatCollectionIndex(String path) {
        String last = path.substring(path.lastIndexOf('/') + 1);
        return last.chars().allMatch(Character::isDigit) ? Integer.parseInt(last) : 0;
    }

    @SuppressWarnings("unchecked")
    private static Runnable invalidateCombat(ItemEditor editor, CombatProblem problem) throws Exception {
        CodecComponentState state = combatState(editor, problem.target());
        JsonElement invalid = JsonParser.parseString(problem.invalid());
        Runnable repair;
        if (problem.path().equals("$")) {
            JsonElement before = state.value();
            state.setValue(invalid);
            repair = () -> state.setValue(before);
        } else {
            JsonElement parent = state.value();
            String[] path = problem.path().split("/");
            for (int index = 0; index < path.length - 1; index++)
                parent = parent.isJsonArray() ? parent.getAsJsonArray().get(Integer.parseInt(path[index])) : parent.getAsJsonObject().get(path[index]);
            JsonElement owner = parent;
            String key = path[path.length - 1];
            JsonElement before = parent.isJsonArray() ? parent.getAsJsonArray().get(Integer.parseInt(key)) : parent.getAsJsonObject().get(key);
            if (parent.isJsonArray()) parent.getAsJsonArray().set(Integer.parseInt(key), invalid);
            else parent.getAsJsonObject().add(key, invalid);
            repair = () -> {
                if (owner.isJsonArray()) owner.getAsJsonArray().set(Integer.parseInt(key), before);
                else if (before == null) owner.getAsJsonObject().remove(key);
                else owner.getAsJsonObject().add(key, before);
            };
        }
        String control = combatControl(editor, problem.control());
        String key = combatCollectionControl(control) ? control + "/@identifier/" + combatCollectionIndex(problem.path())
            : problem.target().equals("damage_type") ? "damage_type" : problem.target().equals("blocks_attacks") ? "blocking/" + control : problem.target() + "/" + control;
        ImString input = ((Map<String, ImString>) read(read(editor, "combatEditor"), "textBuffers")).get(key);
        String before = input == null ? "" : input.get();
        if (input != null) input.set(invalid.isJsonPrimitive() ? invalid.getAsString() : invalid.toString());
        return () -> {
            repair.run();
            if (input != null) input.set(before);
        };
    }

    private static void assertCombatRejected(Fixture fixture, ItemEditor editor, String action, String tab) throws Exception {
        ItemStack opening = editor.draft().copy();
        Object history = read(editor, "lastEditorState");
        var buffers = combatBuffers(editor, false);
        var references = combatBuffers(editor, true);
        var lore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
        var data = dataBuffers((DataEditorPanel) read(editor, "dataEditor"));
        var prepared = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
        require(!prepared.succeeded() && buffers.equals(combatBuffers(editor, false)), "Combat rejection preflight changed input or accepted invalid ownership/source");
        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
        require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals(tab)
                && buffers.equals(combatBuffers(editor, false)) && data.equals(dataBuffers((DataEditorPanel) read(editor, "dataEditor")))
                && lore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState()) && history == read(editor, "lastEditorState")
                && ((Deque<?>) read(editor, "editorUndo")).isEmpty() && !((ItemEditHistory) read(editor, "history")).canUndo()
                && ItemStack.matches(opening, editor.draft()) && ItemStack.matches(opening, fixture.slots.get(0)) && fixture.slots.size() == 1 && fixture.writes.isEmpty(),
            "Combat rejection lost input/history, used the wrong route or wrote output: " + read(editor, "status"));
        var retained = combatBuffers(editor, true);
        require(references.size() == retained.size(), "Combat recovery replaced collections");
        for (int index = 0; index < references.size(); index++)
            require(references.get(index) == retained.get(index), "Combat recovery replaced source/model/buffer/row/native-cache identity");
    }

    private static void verifyCombatActionFailures() throws Exception {
        for (CombatProblem problem : combatProblems())
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(combatOpening(fixture));
                    var values = combatValues(true);
                    var projectiles = combatProjectiles("typed");
                    addCombatPending(fixture, editor, values, projectiles);
                    captureHistory(editor, fixture.client);
                    Runnable repair = invalidateCombat(editor, problem);
                    String control = combatControl(editor, problem.control());
                    Object panel = read(editor, "combatEditor");
                    assertCombatRejected(fixture, editor, action, "COMBAT");
                    require(combatSection(problem.target()).equals(read(panel, "pendingFocusSection")) && control.equals(read(panel, "pendingFocusField")) && !(boolean) read(panel, "focusPendingToolbar"),
                        "Combat precise recovery mismatch: " + problem + "/" + read(panel, "pendingFocusSection") + "/" + read(panel, "pendingFocusField"));
                    if (control.startsWith("tool/rule/") || control.startsWith("blocking/reduction/"))
                        require((long) read(panel, control.startsWith("tool") ? "focusToolRuleRowId" : "focusDamageReductionRowId") == Long.parseLong(control.split("/")[2]), "Combat recovery revealed the wrong parent row");
                    if (combatCollectionControl(control))
                        require((int) read(((Map<?, ?>) read(panel, "collectionFocus")).get(control), "requestedRow") == combatCollectionIndex(problem.path()), "Combat recovery focused the wrong nested collection entry");
                    repair.run();
                    assertCombatOutput(fixture, verifyActionHistory(fixture, editor, action, "Combat repaired " + problem), values, projectiles, Set.of());
                }
    }

    @SuppressWarnings("unchecked")
    private static void verifyCombatProjectileActions() throws Exception {
        for (String problem : List.of("blank_id", "malformed_id", "unknown_id", "air_id", "count_zero", "count_over", "syntax", "codec", "inactive_components"))
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(combatOpening(fixture));
                    var values = combatValues(true);
                    var projectiles = combatProjectiles("typed");
                    addCombatPending(fixture, editor, values, projectiles);
                    Object panel = read(editor, "combatEditor");
                    Object row = ((List<?>) read(panel, "projectileRows")).get(1);
                    Object code = read(row, "componentsEditor");
                    ImString id = (ImString) read(row, "itemId");
                    ImString source = (ImString) read(row, "componentsSource");
                    ImInt count = (ImInt) read(row, "count");
                    String validId = id.get();
                    String validSource = source.get();
                    int validCount = count.get();
                    write(code, "cursorByteOffset", 3);
                    // Even an explicit component cache must not be rewritten by row comparison/preflight.
                    combatState(editor, "charged_projectiles").setNbtValue(fixture.client, projectiles);
                    captureHistory(editor, fixture.client);
                    if (problem.endsWith("_id")) id.set(switch (problem) {
                        case "blank_id" -> " ";
                        case "malformed_id" -> "not an item id";
                        case "air_id" -> "minecraft:air";
                        default -> "itemlab:unknown_item";
                    });
                    else if (problem.startsWith("count")) count.set(problem.equals("count_zero") ? 0 : 100);
                    else
                        source.set(problem.equals("codec") ? "{\"itemlab:unknown_component\":{}}" : "{invalid pending");
                    if (problem.equals("inactive_components")) {
                        ((ImBoolean) read(row, "componentsPresent")).set(false);
                        ((CompoundTag) projectiles.get(1)).remove("components");
                    } else {
                        var before = combatBuffers(editor, false);
                        invoke(panel, "hasPendingChanges", new Class<?>[0]);
                        require(before.equals(combatBuffers(editor, false)), "Combat projectile dirty query rewrote cached/input state");
                        assertCombatRejected(fixture, editor, action, "COMBAT");
                        String field = "row/" + read(row, "id") + "/" + (problem.endsWith("_id") ? "id" : problem.startsWith("count") ? "count" : "components");
                        require("charged_projectiles".equals(read(panel, "pendingFocusSection")) && field.equals(read(panel, "pendingFocusField"))
                            && (long) read(panel, "focusProjectileRowId") == (long) read(row, "id") && (int) read(code, "cursorByteOffset") == 3
                            && (!field.endsWith("/components") || (boolean) read(code, "focusRequested")), "Combat projectile recovery lost exact row/source/cursor");
                        id.set(validId);
                        count.set(validCount);
                        source.set(validSource);
                    }
                    assertCombatOutput(fixture, verifyActionHistory(fixture, editor, action, "Combat projectile repaired " + problem), values, projectiles, Set.of());
                }
        for (int size : List.of(1024, 1025))
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(combatOpening(fixture));
                    var values = combatValues(true);
                    var projectiles = new ListTag();
                    for (int index = 0; index < Math.min(size, 1024); index++)
                        projectiles.add(TagParser.parseCompoundFully("{id:\"minecraft:arrow\"}"));
                    addCombatPending(fixture, editor, values, projectiles);
                    Object panel = read(editor, "combatEditor");
                    List<Object> rows = (List<Object>) read(panel, "projectileRows");
                    if (size == 1025) {
                        captureHistory(editor, fixture.client);
                        Object extra = invoke(rows.getFirst(), "copy", new Class<?>[0]);
                        rows.add(extra);
                        if (dev.nineofgaming.itemlab.editor.component.EditorVersionProfile.targetVersion().compareTo("26.2") >= 0) {
                            assertCombatRejected(fixture, editor, action, "COMBAT");
                            require(("row/" + read(extra, "id") + "/id").equals(read(panel, "pendingFocusField")) && (long) read(panel, "focusProjectileRowId") == (long) read(extra, "id"), "Combat projectile-limit recovery revealed the wrong row");
                            rows.removeLast();
                        } else projectiles.add(TagParser.parseCompoundFully("{id:\"minecraft:arrow\"}"));
                    }
                    assertCombatOutput(fixture, verifyActionHistory(fixture, editor, action, "Combat projectile limit " + size), values, projectiles, Set.of());
                }
    }

    private static void verifyCombatOtherFailures() throws Exception {
        for (boolean earlier : List.of(true, false))
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(combatOpening(fixture));
                    var values = combatValues(true);
                    var projectiles = combatProjectiles("typed");
                    addCombatPending(fixture, editor, values, projectiles);
                    captureHistory(editor, fixture.client);
                    ImString source = earlier ? (ImString) read(editor, "itemId") : (ImString) read(((List<?>) read(read(editor, "dataEditor"), "entries")).getFirst(), "value");
                    String valid = source.get();
                    source.set(earlier ? "not an item id" : "invalid late integer");
                    assertCombatRejected(fixture, editor, action, earlier ? "NAME" : "DATA");
                    source.set(valid);
                    assertCombatOutput(fixture, verifyActionHistory(fixture, editor, action, "Combat earlier/later " + earlier), values, projectiles, Set.of());
                }
    }

    private static void verifyCombatConflicts() throws Exception {
        var generatedValues = combatValues(false);
        generatedValues.put("minimum_attack_charge", JsonParser.parseString("0.4"));
        generatedValues.put("damage_type", JsonParser.parseString("\"minecraft:magic\""));
        generatedValues.put("swing_animation", JsonParser.parseString("{\"type\":\"none\",\"duration\":4}"));
        generatedValues.put("weapon", JsonParser.parseString("{\"item_damage_per_attack\":4}"));
        generatedValues.put("piercing_weapon", JsonParser.parseString("{\"deals_knockback\":false,\"sound\":\"minecraft:entity.generic.drink\"}"));
        generatedValues.put("kinetic_weapon", JsonParser.parseString("{\"delay_ticks\":4}"));
        generatedValues.put("charged_projectiles", JsonParser.parseString("[{\"id\":\"minecraft:snowball\",\"count\":4}]"));
        generatedValues.put("tool", JsonParser.parseString("{\"rules\":[{\"blocks\":\"minecraft:glass\",\"speed\":4}]}"));
        generatedValues.put("attack_range", JsonParser.parseString("{\"max_reach\":4}"));
        generatedValues.put("damage_resistant", JsonParser.parseString("{\"types\":\"minecraft:magic\"}"));
        generatedValues.put("repairable", JsonParser.parseString("{\"items\":\"minecraft:diamond\"}"));
        generatedValues.put("blocks_attacks", JsonParser.parseString("{\"block_delay_seconds\":4}"));
        generatedValues.put("enchantable", JsonParser.parseString("{\"value\":4}"));
        for (String target : COMBAT_COMPONENTS)
            for (boolean keepCombat : List.of(true, false))
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                    try (Fixture fixture = new Fixture()) {
                        ItemStack opening = combatOpening(fixture);
                        ItemEditor editor = fixture.editedEditor(opening);
                        var option = new ComponentOption("minecraft:" + target, target, dataType(target));
                        write(editor, "componentOptions", List.of(option));
                        require(editor.selectComponent(fixture.client, option), "Combat component selection failed");
                        write(editor, "useGenericSelectedComponentEditor", true);
                        var values = combatValues(true);
                        var projectiles = combatProjectiles("typed");
                        addCombatPending(fixture, editor, values, projectiles);
                        JsonElement generated = generatedValues.get(target);
                        ComponentFormPanel form = (ComponentFormPanel) read(editor, "componentForm");
                        JsonElement baseline = form.pendingValue().deepCopy();
                        form.restorePending(baseline, generated);
                        captureHistory(editor, fixture.client);
                        assertCombatRejected(fixture, editor, action, "COMBAT");
                        require(editor.selectedComponent() == option && (boolean) read(editor, "useGenericSelectedComponentEditor") && generated.equals(form.pendingValue()) && baseline.equals(read(form, "loadedValue")), "Combat conflict replaced generated selection/value/baseline");
                        if (keepCombat) form.discardChanges();
                        else {
                            invoke(read(editor, "combatEditor"), "sync", new Class<?>[]{Minecraft.class, ItemStack.class}, fixture.client, opening);
                            for (String component : COMBAT_COMPONENTS)
                                values.put(component, ItemStackCodecs.encodeComponent(fixture.client, dataType(component), opening.get(dataType(component))));
                            values.put(target, generated);
                            projectiles = combatProjectiles("opening");
                            if (target.equals("charged_projectiles")) {
                                ItemStack chosen = opening.copy();
                                ItemStackCodecs.decodeAndSetComponent(fixture.client, chosen, DataComponents.CHARGED_PROJECTILES, generated);
                                projectiles = (ListTag) ItemStackCodecs.encodeComponentNbt(fixture.client, DataComponents.CHARGED_PROJECTILES, chosen.get(DataComponents.CHARGED_PROJECTILES));
                            }
                        }
                        assertCombatOutput(fixture, verifyActionHistory(fixture, editor, action, "Combat generated resolution " + target + "/" + keepCombat), values, projectiles, Set.of());
                    }
    }

    private static String combatBufferPrefix(String target) {
        return switch (target) {
            case "damage_type" -> "damage_type";
            case "piercing_weapon", "kinetic_weapon" -> target + "/";
            case "tool" -> "tool/";
            case "damage_resistant" -> "resistance/";
            case "repairable" -> "repairable/";
            case "blocks_attacks" -> "blocking/";
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static void verifyCombatComponentResets() throws Exception {
        for (String target : COMBAT_COMPONENTS)
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemStack opening = combatOpening(fixture);
                    ItemEditor editor = fixture.editedEditor(opening);
                    var values = combatValues(true);
                    var projectiles = combatProjectiles("typed");
                    addCombatPending(fixture, editor, values, projectiles);
                    captureHistory(editor, fixture.client);
                    Object panel = read(editor, "combatEditor");
                    CombatProblem problem = target.equals("tool") ? new CombatProblem("weapon", "item_damage_per_attack", "-1", "item_damage_per_attack")
                        : new CombatProblem("tool", "rules/1/blocks/1", "\"not a block id\"", "tool/rule/@1/blocks");
                    Runnable repair = invalidateCombat(editor, problem);
                    Map<String, JsonElement> models = new HashMap<>();
                    for (String component : COMBAT_COMPONENTS)
                        models.put(component, combatState(editor, component).value());
                    Map<String, ImString> inputs = new HashMap<>((Map<String, ImString>) read(panel, "textBuffers"));
                    Map<String, String> sources = new HashMap<>();
                    inputs.forEach((key, input) -> sources.put(key, input.get()));
                    Map<String, IdentifierCollectionEditor.FocusState> collections = new HashMap<>((Map<String, IdentifierCollectionEditor.FocusState>) read(panel, "collectionFocus"));
                    var rows = new ArrayList<>((List<Object>) read(panel, "projectileRows"));
                    var tools = List.copyOf((List<Long>) read(panel, "toolRuleRowIds"));
                    var reductions = List.copyOf((List<Long>) read(panel, "damageReductionRowIds"));
                    Object row = rows.get(1);
                    Object code = read(row, "componentsEditor");
                    write(code, "cursorByteOffset", 3);
                    invoke(panel, "requestPendingFocus", new Class<?>[0]);
                    invoke(panel, "syncComponent", new Class<?>[]{Minecraft.class, DataComponentType.class, ItemStack.class}, fixture.client, dataType(target), opening);
                    for (String component : COMBAT_COMPONENTS)
                        if (!component.equals(target))
                            require(models.get(component) == combatState(editor, component).value(), "Combat component reset replaced unrelated model identity: " + target + "/" + component);
                    String prefix = combatBufferPrefix(target);
                    Map<String, ImString> retained = (Map<String, ImString>) read(panel, "textBuffers");
                    for (String key : inputs.keySet())
                        if (prefix == null || !key.startsWith(prefix))
                            require(retained.get(key) == inputs.get(key) && sources.get(key).equals(retained.get(key).get()), "Combat component reset replaced unrelated input");
                    Map<String, IdentifierCollectionEditor.FocusState> retainedCollections = (Map<String, IdentifierCollectionEditor.FocusState>) read(panel, "collectionFocus");
                    for (String key : collections.keySet())
                        if (prefix == null || !key.startsWith(prefix))
                            require(retainedCollections.get(key) == collections.get(key), "Combat component reset replaced another collection's identity");
                    require((target.equals("tool") || tools.equals(read(panel, "toolRuleRowIds"))) && (target.equals("blocks_attacks") || reductions.equals(read(panel, "damageReductionRowIds"))),
                        "Combat component reset replaced unrelated stable row IDs");
                    if (!target.equals("charged_projectiles")) {
                        List<?> retainedRows = (List<?>) read(panel, "projectileRows");
                        require(rows.size() == retainedRows.size(), "Combat component reset replaced projectile collection");
                        for (int index = 0; index < rows.size(); index++)
                            require(rows.get(index) == retainedRows.get(index), "Combat component reset replaced unrelated projectile identity");
                        require((int) read(code, "cursorByteOffset") == 3, "Combat component reset moved another code-input cursor");
                    } else projectiles = combatProjectiles("opening");
                    require((boolean) read(panel, "dirty") && read(panel, "pendingFocusSection") == null && read(panel, "pendingFocusField") == null
                            && (long) read(panel, "focusToolRuleRowId") == 0L && (long) read(panel, "focusProjectileRowId") == 0L && (long) read(panel, "focusDamageReductionRowId") == 0L,
                        "Combat component reset threw on invalid unrelated input, cleaned it or retained recovery focus");
                    var before = combatBuffers(editor, false);
                    var rejected = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                    require(!rejected.succeeded() && before.equals(combatBuffers(editor, false)) && fixture.writes.isEmpty(), "Combat reset discarded/accepted an unrelated invalid source");
                    repair.run();
                    values.put(target, ItemStackCodecs.encodeComponent(fixture.client, dataType(target), opening.get(dataType(target))));
                    assertCombatOutput(fixture, verifyActionHistory(fixture, editor, action, "Combat component reset " + target), values, projectiles, Set.of());
                }
    }

    private static final List<String> USE_COMPONENTS = List.of("food", "consumable", "use_effects", "use_remainder", "use_cooldown",
        "suspicious_stew_effects", "ominous_bottle_amplifier", "death_protection");

    private static CodecComponentState useState(ItemEditor editor, String target) throws Exception {
        return (CodecComponentState) read(read(editor, "useEditor"), switch (target) {
            case "use_effects" -> "useEffects";
            case "use_remainder" -> "remainder";
            case "use_cooldown" -> "cooldown";
            case "suspicious_stew_effects" -> "stewEffects";
            case "ominous_bottle_amplifier" -> "ominousAmplifier";
            case "death_protection" -> "deathProtection";
            default -> target;
        });
    }

    private static String useSection(String target) {
        return switch (target) {
            case "suspicious_stew_effects" -> "stew_effects";
            case "ominous_bottle_amplifier" -> "ominous_amplifier";
            default -> target;
        };
    }

    private static String useAllEffects() {
        return "[{\"type\":\"minecraft:clear_all_effects\"},{\"type\":\"minecraft:apply_effects\",\"probability\":0.5,\"effects\":["
            + "{\"id\":\"minecraft:speed\",\"duration\":200,\"amplifier\":255,\"ambient\":true,\"show_particles\":false,\"show_icon\":false},"
            + "{\"id\":\"minecraft:strength\",\"duration\":-20,\"amplifier\":256},{\"id\":\"minecraft:speed\",\"duration\":0,\"amplifier\":-1}]},"
            + "{\"type\":\"minecraft:remove_effects\",\"effects\":[\"minecraft:speed\",\"minecraft:strength\",\"minecraft:speed\"]},"
            + "{\"type\":\"minecraft:teleport_randomly\",\"diameter\":24},"
            + "{\"type\":\"minecraft:play_sound\",\"sound\":{\"sound_id\":\"itemlab:effect_sound\",\"range\":-1}},"
            + "{\"type\":\"minecraft:clear_all_effects\"}]";
    }

    private static Map<String, JsonElement> useValues(boolean pending) {
        Map<String, JsonElement> values = new java.util.LinkedHashMap<>();
        values.put("food", JsonParser.parseString(pending ? "{\"nutrition\":7,\"saturation\":-2,\"can_always_eat\":true}" : "{\"nutrition\":1,\"saturation\":0.1}"));
        values.put("consumable", JsonParser.parseString(pending
            ? "{\"consume_seconds\":2.5,\"animation\":\"drink\",\"sound\":{\"sound_id\":\"itemlab:consume\",\"range\":0},\"has_consume_particles\":false,\"on_consume_effects\":" + useAllEffects() + "}"
            : "{\"consume_seconds\":1,\"animation\":\"eat\",\"sound\":\"minecraft:entity.generic.eat\"}"));
        values.put("use_effects", JsonParser.parseString(pending ? "{\"can_sprint\":true,\"interact_vibrations\":true,\"speed_multiplier\":0.8}" : "{\"speed_multiplier\":0.2}"));
        values.put("use_remainder", JsonParser.parseString(pending ? "{\"id\":\"minecraft:diamond\",\"count\":3,\"components\":{\"minecraft:item_name\":{\"text\":\"Remainder {name} ☃\",\"bold\":true}}}" : "{\"id\":\"minecraft:glass_bottle\",\"count\":1}"));
        values.put("use_cooldown", JsonParser.parseString(pending ? "{\"seconds\":2.5,\"cooldown_group\":\"itemlab:shared\"}" : "{\"seconds\":1,\"cooldown_group\":\"itemlab:opening\"}"));
        values.put("suspicious_stew_effects", JsonParser.parseString(pending
            ? "[{\"id\":\"minecraft:speed\",\"duration\":200},{\"id\":\"minecraft:strength\",\"duration\":-20},{\"id\":\"minecraft:speed\",\"duration\":\"native default\"}]"
            : "[{\"id\":\"minecraft:regeneration\",\"duration\":20}]"));
        values.put("ominous_bottle_amplifier", JsonParser.parseString(pending ? "3" : "0"));
        values.put("death_protection", JsonParser.parseString(pending ? "{\"death_effects\":" + useAllEffects() + "}" : "{\"death_effects\":[{\"type\":\"minecraft:clear_all_effects\"}]}"));
        return values;
    }

    private static ItemStack useOpening(Fixture fixture) {
        ItemStack opening = new ItemStack(Items.STONE);
        for (var entry : useValues(false).entrySet())
            ItemStackCodecs.decodeAndSetComponent(fixture.client, opening, dataType(entry.getKey()), entry.getValue());
        return opening;
    }

    @SuppressWarnings("unchecked")
    private static void seedUseInputs(ItemEditor editor) throws Exception {
        Object panel = read(editor, "useEditor");
        Map<String, ImString> buffers = (Map<String, ImString>) read(panel, "textBuffers");
        JsonObject remainder = useState(editor, "use_remainder").value().getAsJsonObject();
        buffers.put("remainder/id", new ImString(" " + remainder.get("id").getAsString() + " ", 512));
        JsonObject cooldown = useState(editor, "use_cooldown").value().getAsJsonObject();
        if (cooldown.has("cooldown_group"))
            buffers.put("cooldown/group", new ImString(" " + cooldown.get("cooldown_group").getAsString() + " ", 512));
        for (String target : List.of("consumable", "death_protection")) {
            JsonObject owner = useState(editor, target).value().getAsJsonObject();
            String field = target.equals("consumable") ? "on_consume_effects" : "death_effects";
            String prefix = target.equals("consumable") ? "consume" : "death_protection";
            if (target.equals("consumable") && owner.has("sound") && owner.get("sound").isJsonPrimitive())
                buffers.put("consumable/sound", new ImString(" " + owner.get("sound").getAsString() + " ", 512));
            if (!owner.has(field)) continue;
            var effects = owner.getAsJsonArray(field);
            List<Long> rows = (List<Long>) invoke(panel, "rowIdsFor", new Class<?>[]{String.class, int.class}, prefix, effects.size());
            for (int index = 0; index < effects.size(); index++) {
                JsonObject effect = effects.get(index).getAsJsonObject();
                String path = prefix + "/" + rows.get(index);
                if (effect.has("effects") && effect.get("effects").isJsonArray()) {
                    var entries = effect.getAsJsonArray("effects");
                    boolean statuses = effect.get("type").getAsString().equals("minecraft:apply_effects");
                    String nested = path + (statuses ? "/status" : "/remove_effect");
                    List<Long> children = (List<Long>) invoke(panel, "rowIdsFor", new Class<?>[]{String.class, int.class}, nested, entries.size());
                    for (int child = 0; child < entries.size(); child++)
                        buffers.put(nested + "/" + children.get(child), new ImString(" "
                            + (statuses ? entries.get(child).getAsJsonObject().get("id").getAsString() : entries.get(child).getAsString()) + " ", 512));
                }
                if (effect.has("sound") && effect.get("sound").isJsonPrimitive())
                    buffers.put(path + "/sound", new ImString(" " + effect.get("sound").getAsString() + " ", 512));
            }
        }
        var stew = useState(editor, "suspicious_stew_effects").value().getAsJsonArray();
        List<Long> stewRows = (List<Long>) invoke(panel, "rowIdsFor", new Class<?>[]{String.class, int.class}, "stew", stew.size());
        for (int index = 0; index < stew.size(); index++)
            buffers.put("stew_effect/" + stewRows.get(index) + "/id", new ImString(" " + stew.get(index).getAsJsonObject().get("id").getAsString() + " ", 512));
    }

    private static void addUsePending(ItemEditor editor, Map<String, JsonElement> values) throws Exception {
        for (String target : USE_COMPONENTS) {
            CodecComponentState state = useState(editor, target);
            state.setPresent(true);
            state.setValue(values.get(target));
        }
        seedUseInputs(editor);
        write(read(editor, "useEditor"), "dirty", true);
    }

    private static void jsonReferences(JsonElement value, List<Object> references) {
        references.add(value);
        if (value.isJsonArray()) for (JsonElement entry : value.getAsJsonArray()) jsonReferences(entry, references);
        else if (value.isJsonObject()) for (String key : value.getAsJsonObject().keySet().stream().sorted().toList())
            jsonReferences(value.getAsJsonObject().get(key), references);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> useBuffers(ItemEditor editor, boolean references) throws Exception {
        Object panel = read(editor, "useEditor");
        var result = new ArrayList<Object>();
        for (String target : USE_COMPONENTS) {
            CodecComponentState state = useState(editor, target);
            if (references) {
                result.add(state);
                jsonReferences(state.value(), result);
            } else
                result.add(List.of(state.present(), state.value().deepCopy(), read(state, "nbtValue") == null ? "" : ((Tag) read(state, "nbtValue")).copy()));
        }
        Map<String, ImString> buffers = (Map<String, ImString>) read(panel, "textBuffers");
        if (references) result.add(buffers);
        for (String key : buffers.keySet().stream().sorted().toList()) {
            if (!references) result.add(key);
            result.add(references ? buffers.get(key) : buffers.get(key).get());
        }
        Map<String, List<Long>> rows = (Map<String, List<Long>>) read(panel, "rowIdsByList");
        if (references) result.add(rows);
        for (String key : rows.keySet().stream().sorted().toList()) {
            if (!references) result.add(key);
            result.add(references ? rows.get(key) : List.copyOf(rows.get(key)));
        }
        JsonElement componentsJson = (JsonElement) read(panel, "remainderComponentsJson");
        Tag componentsNbt = (Tag) read(panel, "remainderComponentsNbt");
        result.add(componentsJson == null ? "" : references ? componentsJson : componentsJson.deepCopy());
        result.add(componentsNbt == null ? "" : references ? componentsNbt : componentsNbt.copy());
        return result;
    }

    private static void assertUseOutput(Fixture fixture, ItemStack saved, Map<String, JsonElement> values, Map<String, Tag> nativeValues, Set<String> removed) {
        assertCombined(saved);
        ItemStack expected = new ItemStack(Items.STONE);
        for (String target : USE_COMPONENTS) {
            if (removed.contains(target)) require(!saved.has(dataType(target)), "Use failed to remove " + target);
            else {
                if (nativeValues.containsKey(target))
                    ItemStackCodecs.decodeAndSetComponentNbt(fixture.client, expected, dataType(target), nativeValues.get(target));
                else
                    ItemStackCodecs.decodeAndSetComponent(fixture.client, expected, dataType(target), values.get(target));
                require(ItemStackCodecs.encodeComponentNbt(fixture.client, dataType(target), expected.get(dataType(target))).equals(
                    ItemStackCodecs.encodeComponentNbt(fixture.client, dataType(target), saved.get(dataType(target)))), "Use lost exact native output: " + target);
            }
        }
    }

    private static void verifyUseTypedRemainder() throws Exception {
        for (String edit : List.of("unchanged", "count", "item", "components_changed", "components_invalid", "native_override", "component_sync"))
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemStack opening = useOpening(fixture);
                    CompoundTag remainder = TagParser.parseCompoundFully("{id:\"minecraft:diamond\",count:3,components:{\"minecraft:custom_data\":" + ADVENTURE_DATA + ",\"minecraft:instrument\":\"minecraft:ponder_goat_horn\"}}");
                    ItemStackCodecs.decodeAndSetComponentNbt(fixture.client, opening, DataComponents.USE_REMAINDER, remainder);
                    ItemEditor editor = fixture.editedEditor(opening);
                    var values = useValues(false);
                    values.put("food", useValues(true).get("food"));
                    useState(editor, "food").setValue(values.get("food"));
                    if (edit.equals("count")) {
                        useState(editor, "use_remainder").value().getAsJsonObject().addProperty("count", 99);
                        remainder.putInt("count", 99);
                    }
                    if (edit.equals("item")) {
                        useState(editor, "use_remainder").value().getAsJsonObject().addProperty("id", "minecraft:glass_bottle");
                        remainder.putString("id", "minecraft:glass_bottle");
                    }
                    if (edit.equals("components_changed")) {
                        useState(editor, "use_remainder").value().getAsJsonObject().add("components", JsonParser.parseString("{\"minecraft:item_name\":{\"text\":\"Changed {name} ☃\"}}"));
                        remainder.put("components", TagParser.parseCompoundFully("{\"minecraft:item_name\":\"Changed {name} ☃\"}"));
                    }
                    if (edit.equals("native_override")) {
                        remainder.putInt("count", 42);
                        useState(editor, "use_remainder").setNbtValue(fixture.client, remainder);
                    }
                    if (edit.equals("component_sync")) {
                        Object panel = read(editor, "useEditor");
                        Object nativeCache = read(panel, "remainderComponentsNbt");
                        Object jsonCache = read(panel, "remainderComponentsJson");
                        invoke(panel, "syncComponent", new Class<?>[]{Minecraft.class, DataComponentType.class, ItemStack.class}, fixture.client, DataComponents.USE_EFFECTS, opening);
                        require(nativeCache == read(panel, "remainderComponentsNbt") && jsonCache == read(panel, "remainderComponentsJson"), "Use component sync replaced another component's opaque cache");
                    }
                    write(read(editor, "useEditor"), "dirty", true);
                    seedUseInputs(editor);
                    if (edit.equals("components_invalid")) {
                        captureHistory(editor, fixture.client);
                        Runnable repair = invalidateUse(editor, new UseProblem("use_remainder", "components/minecraft:custom_data", "0", "$enabled"));
                        assertUseRejected(fixture, editor, action, "USE");
                        require("use_remainder".equals(read(read(editor, "useEditor"), "pendingFocusSection")) && "$enabled".equals(read(read(editor, "useEditor"), "pendingFocusField")),
                            "Use opaque cache masked invalid changed components or requested the wrong recovery");
                        repair.run();
                    }
                    var before = useBuffers(editor, false);
                    var references = useBuffers(editor, true);
                    var prepared = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                    require(prepared.succeeded() && before.equals(useBuffers(editor, false)), "Typed Use Remainder preflight changed input");
                    var retained = useBuffers(editor, true);
                    require(references.size() == retained.size(), "Typed Use Remainder preflight replaced collections");
                    for (int index = 0; index < references.size(); index++)
                        require(references.get(index) == retained.get(index), "Typed Use Remainder preflight replaced source/buffer/opaque cache identity");
                    assertUseOutput(fixture, prepared.candidate(), values, Map.of("use_remainder", remainder), Set.of());
                    assertUseOutput(fixture, verifyActionHistory(fixture, editor, action, "Use typed remainder " + edit), values, Map.of("use_remainder", remainder), Set.of());
                }
    }

    private record UseValueCase(String target, String source) {
    }

    private static List<UseValueCase> useValueCases() {
        var cases = new ArrayList<UseValueCase>();
        for (String value : List.of("{\"nutrition\":0,\"saturation\":0}", "{\"nutrition\":2147483647,\"saturation\":-2}", "{\"nutrition\":7,\"saturation\":1.25,\"can_always_eat\":true}"))
            cases.add(new UseValueCase("food", value));
        for (String animation : List.of("none", "eat", "drink", "block", "bow", "trident", "crossbow", "spyglass", "toot_horn", "brush", "bundle", "spear"))
            cases.add(new UseValueCase("consumable", "{\"consume_seconds\":0,\"animation\":\"" + animation + "\",\"sound\":\"minecraft:entity.generic.eat\",\"has_consume_particles\":false}"));
        cases.add(new UseValueCase("consumable", "{}"));
        cases.add(new UseValueCase("consumable", "{\"on_consume_effects\":[],\"sound\":{\"sound_id\":\"itemlab:direct\",\"range\":-1}}"));
        for (String effects : List.of(useAllEffects(), "[{\"type\":\"minecraft:apply_effects\",\"probability\":0,\"effects\":[]}]", "[{\"type\":\"minecraft:remove_effects\",\"effects\":[]}]",
            "[{\"type\":\"minecraft:remove_effects\",\"effects\":\"minecraft:strength\"}]", "[{\"type\":\"minecraft:remove_effects\",\"effects\":\"#itemlab:use_effects\"}]", "[]")) {
            cases.add(new UseValueCase("consumable", "{\"on_consume_effects\":" + effects + "}"));
            cases.add(new UseValueCase("death_protection", "{\"death_effects\":" + effects + "}"));
        }
        cases.add(new UseValueCase("death_protection", "{}"));
        for (String value : List.of("{}", "{\"can_sprint\":true,\"interact_vibrations\":true,\"speed_multiplier\":0}", "{\"can_sprint\":false,\"interact_vibrations\":false,\"speed_multiplier\":1}"))
            cases.add(new UseValueCase("use_effects", value));
        for (String value : List.of("{\"id\":\"minecraft:glass_bottle\"}", "{\"id\":\"minecraft:diamond\",\"count\":99}", "{\"id\":\"minecraft:diamond\",\"count\":3,\"components\":{\"minecraft:item_name\":{\"text\":\"Remainder ☃\",\"italic\":true}}}"))
            cases.add(new UseValueCase("use_remainder", value));
        for (String value : List.of("{\"seconds\":0.01}", "{\"seconds\":20,\"cooldown_group\":\"itemlab:custom_group\"}", "{\"seconds\":2.5,\"cooldown_group\":\"minecraft:food\"}"))
            cases.add(new UseValueCase("use_cooldown", value));
        for (String value : List.of("[]", "[{\"id\":\"minecraft:strength\",\"duration\":-20}]", "[{\"id\":\"minecraft:speed\",\"duration\":0},{\"id\":\"minecraft:speed\",\"duration\":\"native default\"}]"))
            cases.add(new UseValueCase("suspicious_stew_effects", value));
        for (int amplifier = 0; amplifier <= 4; amplifier++)
            cases.add(new UseValueCase("ominous_bottle_amplifier", Integer.toString(amplifier)));
        return cases;
    }

    private static void verifyUseCommits() throws Exception {
        for (UseValueCase representation : useValueCases())
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(useOpening(fixture));
                    var values = useValues(true);
                    values.put(representation.target(), JsonParser.parseString(representation.source()));
                    addUsePending(editor, values);
                    var before = useBuffers(editor, false);
                    var prepared = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                    require(prepared.succeeded() && before.equals(useBuffers(editor, false)), "Use preflight changed source/models: " + representation + "/" + prepared.failure());
                    assertUseOutput(fixture, verifyActionHistory(fixture, editor, action, "Use " + representation), values, Map.of(), Set.of());
                }
        for (String target : USE_COMPONENTS)
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(useOpening(fixture));
                    var values = useValues(true);
                    addUsePending(editor, values);
                    useState(editor, target).setPresent(false);
                    useState(editor, target).setValue(JsonParser.parseString("\"invalid inactive input\""));
                    var before = useBuffers(editor, false);
                    var prepared = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                    require(prepared.succeeded() && before.equals(useBuffers(editor, false)), "Use removal validated or replaced inactive input: " + target);
                    assertUseOutput(fixture, verifyActionHistory(fixture, editor, action, "Use removal " + target), values, Map.of(), Set.of(target));
                }
        for (String target : USE_COMPONENTS)
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemStack opening = useOpening(fixture);
                    ItemEditor editor = fixture.editedEditor(opening);
                    var values = useValues(true);
                    for (String other : USE_COMPONENTS)
                        if (!other.equals(target))
                            values.put(other, ItemStackCodecs.encodeComponent(fixture.client, dataType(other), opening.get(dataType(other))));
                    addUsePending(editor, values);
                    assertUseOutput(fixture, verifyActionHistory(fixture, editor, action, "Use single component " + target), values, Map.of(), Set.of());
                }
    }

    private record UseProblem(String target, String path, String invalid, String control) {
    }

    private static List<UseProblem> useProblems() {
        var cases = new ArrayList<UseProblem>(List.of(
            new UseProblem("food", "nutrition", "-1", "nutrition"),
            new UseProblem("food", "saturation", "\"invalid float\"", "saturation"),
            new UseProblem("food", "can_always_eat", "\"invalid flag\"", "can_always_eat"),
            new UseProblem("consumable", "consume_seconds", "-1", "consume_seconds"),
            new UseProblem("consumable", "animation", "\"unknown animation\"", "animation"),
            new UseProblem("consumable", "sound", "\"itemlab:unknown_sound\"", "sound"),
            new UseProblem("consumable", "sound/sound_id", "\"not a sound id\"", "sound"),
            new UseProblem("consumable", "has_consume_particles", "\"invalid flag\"", "has_consume_particles"),
            new UseProblem("use_effects", "can_sprint", "\"invalid flag\"", "can_sprint"),
            new UseProblem("use_effects", "interact_vibrations", "\"invalid flag\"", "interact_vibrations"),
            new UseProblem("use_effects", "speed_multiplier", "1.01", "speed_multiplier"),
            new UseProblem("use_remainder", "id", "\" \"", "id"),
            new UseProblem("use_remainder", "id", "\"not an item id\"", "id"),
            new UseProblem("use_remainder", "id", "\"itemlab:unknown_item\"", "id"),
            new UseProblem("use_remainder", "id", "\"minecraft:air\"", "$enabled"),
            new UseProblem("use_remainder", "count", "0", "count"),
            new UseProblem("use_remainder", "count", "100", "count"),
            new UseProblem("use_remainder", "components/minecraft:item_name", "{\"text\":{}}", "$enabled"),
            new UseProblem("use_cooldown", "seconds", "0", "seconds"),
            new UseProblem("use_cooldown", "seconds", "\"invalid float\"", "seconds"),
            new UseProblem("use_cooldown", "cooldown_group", "\"not a group id\"", "cooldown_group"),
            new UseProblem("suspicious_stew_effects", "1/id", "\" \"", "stew_effect/@1/id"),
            new UseProblem("suspicious_stew_effects", "1/id", "\"not an effect id\"", "stew_effect/@1/id"),
            new UseProblem("suspicious_stew_effects", "1/id", "\"itemlab:unknown_effect\"", "stew_effect/@1/id"),
            new UseProblem("ominous_bottle_amplifier", "$", "-1", "value"),
            new UseProblem("ominous_bottle_amplifier", "$", "5", "value")));
        for (String target : List.of("consumable", "death_protection")) {
            String root = target.equals("consumable") ? "on_consume_effects/" : "death_effects/";
            String prefix = target.equals("consumable") ? "consume/" : "death_protection/";
            cases.addAll(List.of(
                new UseProblem(target, root + "5/type", "\"itemlab:unknown_effect_type\"", prefix + "@5/type"),
                new UseProblem(target, root + "1/probability", "1.01", prefix + "@1/probability"),
                new UseProblem(target, root + "1/effects", "\"not a list\"", prefix + "@1/type"),
                new UseProblem(target, root + "1/effects/1/id", "\"not an effect id\"", prefix + "@1/status/@1/id"),
                new UseProblem(target, root + "1/effects/1/id", "\"itemlab:unknown_effect\"", prefix + "@1/status/@1/id"),
                new UseProblem(target, root + "1/effects/1/duration", "\"invalid integer\"", prefix + "@1/status/@1/duration"),
                new UseProblem(target, root + "1/effects/1/amplifier", "\"invalid byte\"", prefix + "@1/status/@1/amplifier"),
                new UseProblem(target, root + "1/effects/1/ambient", "\"invalid flag\"", prefix + "@1/type"),
                new UseProblem(target, root + "2/effects/1", "\"itemlab:unknown_effect\"", prefix + "@2/remove_effect/@1/id"),
                new UseProblem(target, root + "2/effects", "\"#itemlab:unknown_effects\"", prefix + "@2/type"),
                new UseProblem(target, root + "3/diameter", "0", prefix + "@3/diameter"),
                new UseProblem(target, root + "4/sound/sound_id", "\"not a sound id\"", prefix + "@4/sound"),
                new UseProblem(target, root + "4/sound", "\"itemlab:unknown_sound\"", prefix + "@4/sound"),
                new UseProblem(target, root.substring(0, root.length() - 1), "\"not a list\"", "$enabled")));
        }
        return cases;
    }

    /**
     * Resolve real stable row IDs instead of assuming fixture IDs or array indices.
     */
    @SuppressWarnings("unchecked")
    private static String useControl(ItemEditor editor, String template) throws Exception {
        Map<String, List<Long>> rows = (Map<String, List<Long>>) read(read(editor, "useEditor"), "rowIdsByList");
        StringBuilder path = new StringBuilder();
        for (String part : template.split("/")) {
            if (part.startsWith("@")) {
                String list = path.toString().equals("stew_effect") ? "stew" : path.toString();
                part = rows.get(list).get(Integer.parseInt(part.substring(1))).toString();
            }
            if (!path.isEmpty()) path.append('/');
            path.append(part);
        }
        return path.toString();
    }

    @SuppressWarnings("unchecked")
    private static Runnable invalidateUse(ItemEditor editor, UseProblem problem) throws Exception {
        CodecComponentState state = useState(editor, problem.target());
        JsonElement invalid = JsonParser.parseString(problem.invalid());
        Runnable repair;
        if (problem.path().equals("$")) {
            JsonElement before = state.value();
            state.setValue(invalid);
            repair = () -> state.setValue(before);
        } else {
            JsonElement parent = state.value();
            String[] path = problem.path().split("/");
            for (int index = 0; index < path.length - 1; index++)
                parent = parent.isJsonArray() ? parent.getAsJsonArray().get(Integer.parseInt(path[index])) : parent.getAsJsonObject().get(path[index]);
            JsonElement owner = parent;
            String key = path[path.length - 1];
            JsonElement before = parent.isJsonArray() ? parent.getAsJsonArray().get(Integer.parseInt(key)) : parent.getAsJsonObject().get(key);
            if (parent.isJsonArray()) parent.getAsJsonArray().set(Integer.parseInt(key), invalid);
            else parent.getAsJsonObject().add(key, invalid);
            repair = () -> {
                if (owner.isJsonArray()) owner.getAsJsonArray().set(Integer.parseInt(key), before);
                else if (before == null) owner.getAsJsonObject().remove(key);
                else owner.getAsJsonObject().add(key, before);
            };
        }
        String field = useControl(editor, problem.control());
        String key = switch (problem.target()) {
            case "use_remainder" -> problem.path().equals("id") ? "remainder/id" : "";
            case "use_cooldown" -> problem.path().equals("cooldown_group") ? "cooldown/group" : "";
            default ->
                field.endsWith("/id") && (field.contains("/status/") || field.contains("/remove_effect/")) ? field.substring(0, field.length() - "/id".length()) : field;
        };
        Map<String, ImString> buffers = (Map<String, ImString>) read(read(editor, "useEditor"), "textBuffers");
        ImString input = buffers.get(key);
        String before = input == null ? "" : input.get();
        if (input != null) input.set(invalid.isJsonPrimitive() ? invalid.getAsString() : invalid.toString());
        return () -> {
            repair.run();
            if (input != null) input.set(before);
        };
    }

    private static void assertUseRejected(Fixture fixture, ItemEditor editor, String action, String tab) throws Exception {
        ItemStack opening = editor.draft().copy();
        Object history = read(editor, "lastEditorState");
        var buffers = useBuffers(editor, false);
        var references = useBuffers(editor, true);
        var lore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
        var data = dataBuffers((DataEditorPanel) read(editor, "dataEditor"));
        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
        require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals(tab)
                && buffers.equals(useBuffers(editor, false)) && data.equals(dataBuffers((DataEditorPanel) read(editor, "dataEditor")))
                && lore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState()) && history == read(editor, "lastEditorState")
                && ((Deque<?>) read(editor, "editorUndo")).isEmpty() && !((ItemEditHistory) read(editor, "history")).canUndo()
                && ItemStack.matches(opening, editor.draft()) && ItemStack.matches(opening, fixture.slots.get(0)) && fixture.slots.size() == 1 && fixture.writes.isEmpty(),
            "Use rejection lost input/history, used the wrong route or wrote output: " + read(editor, "status"));
        var retained = useBuffers(editor, true);
        require(references.size() == retained.size(), "Use recovery replaced collections");
        for (int index = 0; index < references.size(); index++)
            require(references.get(index) == retained.get(index), "Use recovery replaced source/model/buffer/row/cache identity");
    }

    private static void verifyUseActionFailures() throws Exception {
        for (UseProblem problem : useProblems())
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(useOpening(fixture));
                    var values = useValues(true);
                    addUsePending(editor, values);
                    captureHistory(editor, fixture.client);
                    Runnable repair = invalidateUse(editor, problem);
                    String field = useControl(editor, problem.control());
                    Object panel = read(editor, "useEditor");
                    assertUseRejected(fixture, editor, action, "USE");
                    require(useSection(problem.target()).equals(read(panel, "pendingFocusSection")) && field.equals(read(panel, "pendingFocusField"))
                        && !(boolean) read(panel, "focusPendingToolbar"), "Use precise recovery mismatch: " + problem + "/" + read(panel, "pendingFocusField"));
                    if (field.startsWith("consume/") || field.startsWith("death_protection/") || field.startsWith("stew_effect/")) {
                        String[] parts = field.split("/");
                        String list = parts[0].equals("stew_effect") ? "stew" : parts[0];
                        require(list.equals(read(panel, "focusListId")) && (long) read(panel, "focusListRowId") == Long.parseLong(parts[1]), "Use recovery revealed the wrong parent card/row");
                    } else
                        require(read(panel, "focusListId") == null && (long) read(panel, "focusListRowId") == 0L, "Use section-only recovery retained stale row focus");
                    repair.run();
                    assertUseOutput(fixture, verifyActionHistory(fixture, editor, action, "Use repaired " + problem), values, Map.of(), Set.of());
                }
    }

    private static void verifyUseOtherFailures() throws Exception {
        for (boolean earlier : List.of(true, false))
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(useOpening(fixture));
                    var values = useValues(true);
                    addUsePending(editor, values);
                    captureHistory(editor, fixture.client);
                    ImString source = earlier ? (ImString) read(editor, "itemId") : (ImString) read(((List<?>) read(read(editor, "dataEditor"), "entries")).getFirst(), "value");
                    String valid = source.get();
                    source.set(earlier ? "not an item id" : "invalid late integer");
                    assertUseRejected(fixture, editor, action, earlier ? "NAME" : "DATA");
                    source.set(valid);
                    assertUseOutput(fixture, verifyActionHistory(fixture, editor, action, "Use earlier/later " + earlier), values, Map.of(), Set.of());
                }
    }

    private static void verifyUseConflicts() throws Exception {
        var generatedValues = useValues(false);
        generatedValues.put("food", JsonParser.parseString("{\"nutrition\":4,\"saturation\":0.4}"));
        generatedValues.put("consumable", JsonParser.parseString("{\"consume_seconds\":4,\"animation\":\"block\"}"));
        generatedValues.put("use_effects", JsonParser.parseString("{\"speed_multiplier\":0.4}"));
        generatedValues.put("use_remainder", JsonParser.parseString("{\"id\":\"minecraft:glass\",\"count\":4}"));
        generatedValues.put("use_cooldown", JsonParser.parseString("{\"seconds\":4,\"cooldown_group\":\"itemlab:generated\"}"));
        generatedValues.put("suspicious_stew_effects", JsonParser.parseString("[{\"id\":\"minecraft:strength\",\"duration\":4}]"));
        generatedValues.put("ominous_bottle_amplifier", JsonParser.parseString("4"));
        generatedValues.put("death_protection", JsonParser.parseString("{\"death_effects\":[{\"type\":\"minecraft:teleport_randomly\",\"diameter\":4}]}"));
        for (String target : USE_COMPONENTS)
            for (boolean keepUse : List.of(true, false))
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                    try (Fixture fixture = new Fixture()) {
                        ItemStack opening = useOpening(fixture);
                        ItemEditor editor = fixture.editedEditor(opening);
                        var option = new ComponentOption("minecraft:" + target, target, dataType(target));
                        write(editor, "componentOptions", List.of(option));
                        require(editor.selectComponent(fixture.client, option), "Use component selection failed");
                        write(editor, "useGenericSelectedComponentEditor", true);
                        var values = useValues(true);
                        addUsePending(editor, values);
                        JsonElement generated = generatedValues.get(target);
                        ComponentFormPanel form = (ComponentFormPanel) read(editor, "componentForm");
                        JsonElement baseline = form.pendingValue().deepCopy();
                        form.restorePending(baseline, generated);
                        captureHistory(editor, fixture.client);
                        assertUseRejected(fixture, editor, action, "USE");
                        require(editor.selectedComponent() == option && (boolean) read(editor, "useGenericSelectedComponentEditor")
                            && generated.equals(form.pendingValue()) && baseline.equals(read(form, "loadedValue")), "Use conflict replaced generated selection/value/baseline");
                        if (keepUse) form.discardChanges();
                        else {
                            invoke(read(editor, "useEditor"), "sync", new Class<?>[]{Minecraft.class, ItemStack.class}, fixture.client, opening);
                            for (String component : USE_COMPONENTS)
                                values.put(component, ItemStackCodecs.encodeComponent(fixture.client, dataType(component), opening.get(dataType(component))));
                            values.put(target, generated);
                        }
                        assertUseOutput(fixture, verifyActionHistory(fixture, editor, action, "Use generated resolution " + target + "/" + keepUse), values, Map.of(), Set.of());
                    }
    }

    private static final List<String> ADVENTURE_COMPONENTS = List.of("can_break", "can_place_on");
    private static final String ADVENTURE_DATA = "{b:1b,s:2s,i:3,l:9007199254740993L,f:3.5f,d:4.5d,bytes:[B;1b,2b],ints:[I;3,4],longs:[L;5L,6L],nested:{text:\"Unicode {value} ☃\"}}";

    private static Object adventureSection(ItemEditor editor, String target) throws Exception {
        return read(read(editor, "adventureEditor"), target.equals("can_break") ? "canBreak" : "canPlaceOn");
    }

    private static CompoundTag adventurePredicate(String representation) throws Exception {
        CompoundTag predicate = TagParser.parseCompoundFully("{blocks:[\"minecraft:oak_stairs\",\"minecraft:stone\",\"minecraft:oak_stairs\"],state:{half:bottom,waterlogged:\"false\"}}");
        switch (representation) {
            case "scalar" -> predicate = TagParser.parseCompoundFully("{blocks:\"minecraft:oak_stairs\"}");
            case "singleton_list" -> predicate = TagParser.parseCompoundFully("{blocks:[\"minecraft:oak_stairs\"]}");
            case "collection" -> predicate.remove("state");
            case "tag" -> predicate = TagParser.parseCompoundFully("{blocks:\"#itemlab:adventure_blocks\"}");
            case "any" -> predicate = new CompoundTag();
            case "state_exact" -> {
            }
            case "state_range" ->
                predicate = TagParser.parseCompoundFully("{blocks:\"minecraft:wheat\",state:{age:{min:\"2\",max:\"6\"}}}");
            case "state_min" ->
                predicate = TagParser.parseCompoundFully("{blocks:\"minecraft:wheat\",state:{age:{min:\"0\"}}}");
            case "state_max" ->
                predicate = TagParser.parseCompoundFully("{blocks:\"minecraft:wheat\",state:{age:{max:\"7\"}}}");
            case "opaque_state" ->
                predicate = TagParser.parseCompoundFully("{state:{custom_property:\"Unicode {state} ☃\",other:{min:\"a\",max:\"z\"}}}");
            case "empty_state" -> predicate = TagParser.parseCompoundFully("{state:{}}");
            case "empty_nbt", "typed_nbt" -> {
                predicate.remove("state");
                predicate.putString("nbt", representation.equals("empty_nbt") ? "{}" : ADVENTURE_DATA);
            }
            case "empty_components", "typed_components", "empty_predicates", "typed_predicates" -> {
                predicate.remove("state");
                CompoundTag matcher = new CompoundTag();
                if (representation.startsWith("typed"))
                    matcher.put("minecraft:custom_data", TagParser.parseCompoundFully(ADVENTURE_DATA));
                predicate.put(representation.endsWith("components") ? "components" : "predicates", matcher);
            }
            case "combined", "ordered", "disabled_fields" -> {
                predicate.putString("nbt", ADVENTURE_DATA);
                CompoundTag exact = new CompoundTag();
                exact.put("minecraft:custom_data", TagParser.parseCompoundFully(ADVENTURE_DATA));
                exact.putString("minecraft:instrument", "minecraft:ponder_goat_horn");
                predicate.put("components", exact);
                CompoundTag partial = new CompoundTag();
                partial.put("minecraft:custom_data", TagParser.parseCompoundFully(ADVENTURE_DATA));
                predicate.put("predicates", partial);
            }
            default -> throw new AssertionError(representation);
        }
        return predicate;
    }

    private static Tag adventureValue(String representation) throws Exception {
        CompoundTag predicate = adventurePredicate(representation);
        if (!representation.equals("ordered")) return predicate;
        ListTag ordered = new ListTag();
        ordered.add(adventurePredicate("scalar"));
        ordered.add(predicate.copy());
        ordered.add(predicate.copy());
        return ordered;
    }

    private static Map<String, Tag> adventureValues(String target, String representation) throws Exception {
        Map<String, Tag> values = new java.util.LinkedHashMap<>();
        for (String component : ADVENTURE_COMPONENTS)
            values.put(component, adventureValue(component.equals(target) ? representation : "combined"));
        return values;
    }

    private static ItemStack adventureOpening(Fixture fixture) throws Exception {
        ItemStack opening = new ItemStack(Items.STONE);
        ItemStackCodecs.decodeAndSetComponentNbt(fixture.client, opening, DataComponents.CAN_BREAK, TagParser.parseCompoundFully("{blocks:\"minecraft:dirt\",nbt:\"{Opening:Break}\"}"));
        ItemStackCodecs.decodeAndSetComponentNbt(fixture.client, opening, DataComponents.CAN_PLACE_ON, TagParser.parseCompoundFully("{blocks:\"minecraft:cobblestone\",nbt:\"{Opening:Place}\"}"));
        return opening;
    }

    @SuppressWarnings("unchecked")
    private static void addAdventurePending(Fixture fixture, ItemEditor editor, Map<String, Tag> values) throws Exception {
        Object panel = read(editor, "adventureEditor");
        ItemStack source = editor.draft().copy();
        for (var entry : values.entrySet())
            ItemStackCodecs.decodeAndSetComponentNbt(fixture.client, source, dataType(entry.getKey()), entry.getValue());
        invoke(panel, "sync", new Class<?>[]{Minecraft.class, ItemStack.class}, fixture.client, source);
        write(panel, "knownBlocksAndTags", Set.of("#itemlab:adventure_blocks"));
        Map<String, ImString> buffers = (Map<String, ImString>) read(panel, "textBuffers");
        for (String target : ADVENTURE_COMPONENTS)
            for (Object row : (List<?>) read(adventureSection(editor, target), "predicates")) {
                JsonElement blocks = (JsonElement) read(row, "blocks");
                List<JsonElement> identifiers = blocks.isJsonArray() ? blocks.getAsJsonArray().asList() : List.of(blocks);
                for (int index = 0; index < identifiers.size(); index++)
                    buffers.put("adventure/" + target + "/" + read(row, "id") + "/blocks/@identifier/" + index,
                        new ImString(" " + identifiers.get(index).getAsString() + " ", 512));
                for (Object state : (List<?>) read(row, "states"))
                    for (String field : List.of("name", "exact", "minimum", "maximum")) {
                        ImString input = (ImString) read(state, field);
                        if (!input.get().isEmpty()) input.set(" " + input.get() + " ");
                    }
                for (String prefix : List.of("nbt", "components", "componentPredicates")) {
                    ImString input = (ImString) read(row, prefix + "Source");
                    input.set("\n " + input.get() + " \n");
                    write(read(row, prefix + "Editor"), "cursorByteOffset", 4);
                    write(read(row, prefix + "Editor"), "cursorColumn", 5);
                }
            }
        write(panel, "dirty", true);
    }

    private static List<Object> adventureBuffers(ItemEditor editor, boolean references) throws Exception {
        Object panel = read(editor, "adventureEditor");
        var result = new ArrayList<Object>();
        if (!references) result.add(read(panel, "knownBlocksAndTags"));
        for (String target : ADVENTURE_COMPONENTS) {
            Object section = adventureSection(editor, target);
            List<?> rows = (List<?>) read(section, "predicates");
            if (references) {
                result.add(section);
                result.add(rows);
                result.add(read(section, "present"));
            } else result.add(((ImBoolean) read(section, "present")).get());
            for (Object row : rows) {
                if (references) {
                    result.add(row);
                    result.add(read(row, "blocks"));
                    result.add(read(row, "extraFields"));
                    result.add(read(row, "states"));
                } else {
                    result.add(read(row, "id"));
                    result.add(((JsonElement) read(row, "blocks")).deepCopy());
                    result.add(((JsonObject) read(row, "extraFields")).deepCopy());
                }
                for (String field : List.of("blocksPresent", "statePresent", "nbtPresent", "componentsPresent", "componentPredicatesPresent"))
                    result.add(references ? read(row, field) : ((ImBoolean) read(row, field)).get());
                for (Object state : (List<?>) read(row, "states")) {
                    if (references) result.add(state);
                    else {
                        result.add(read(state, "id"));
                        result.add(((ImInt) read(state, "mode")).get());
                    }
                    for (String field : List.of("name", "exact", "minimum", "maximum"))
                        result.add(references ? read(state, field) : ((ImString) read(state, field)).get());
                }
                for (String prefix : List.of("nbt", "components", "componentPredicates")) {
                    Object code = read(row, prefix + "Editor");
                    if (references) {
                        result.add(read(row, prefix + "Source"));
                        result.add(code);
                    } else {
                        result.add(((ImString) read(row, prefix + "Source")).get());
                        result.add(read(code, "cursorByteOffset"));
                        result.add(read(code, "cursorColumn"));
                    }
                }
            }
        }
        @SuppressWarnings("unchecked") Map<String, ImString> buffers = (Map<String, ImString>) read(panel, "textBuffers");
        for (String key : buffers.keySet().stream().sorted().toList()) {
            if (!references) result.add(key);
            result.add(references ? buffers.get(key) : buffers.get(key).get());
        }
        return result;
    }

    private static void assertAdventureOutput(Fixture fixture, ItemStack saved, Map<String, Tag> values, Set<String> removed) {
        assertCombined(saved);
        for (String target : ADVENTURE_COMPONENTS) {
            if (removed.contains(target)) require(!saved.has(dataType(target)), "Adventure failed to remove " + target);
            else
                require(ItemStackCodecs.canonicalizeComponentNbt(fixture.client, dataType(target), values.get(target)).equals(
                    ItemStackCodecs.encodeComponentNbt(fixture.client, dataType(target), saved.get(dataType(target)))), "Adventure changed typed/order/constraint output: " + target);
        }
    }

    private static void verifyAdventureCommits() throws Exception {
        for (String target : ADVENTURE_COMPONENTS)
            for (String representation : List.of("scalar", "singleton_list", "collection", "tag", "any", "state_exact",
                "state_range", "state_min", "state_max", "opaque_state", "empty_state", "empty_nbt", "typed_nbt", "empty_components", "typed_components",
                "empty_predicates", "typed_predicates", "combined", "ordered", "disabled_fields"))
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                    try (Fixture fixture = new Fixture()) {
                        ItemEditor editor = fixture.editedEditor(adventureOpening(fixture));
                        var values = adventureValues(target, representation);
                        addAdventurePending(fixture, editor, values);
                        if (representation.equals("disabled_fields")) {
                            Object row = ((List<?>) read(adventureSection(editor, target), "predicates")).getFirst();
                            for (String prefix : List.of("state", "nbt", "components", "componentPredicates"))
                                ((ImBoolean) read(row, prefix + "Present")).set(false);
                            ((ImString) read(((List<?>) read(row, "states")).getFirst(), "name")).set("invalid inactive property");
                            for (String prefix : List.of("nbt", "components", "componentPredicates"))
                                ((ImString) read(row, prefix + "Source")).set("{invalid inactive " + prefix);
                            CompoundTag expected = ((CompoundTag) values.get(target)).copy();
                            for (String field : List.of("state", "nbt", "components", "predicates"))
                                expected.remove(field);
                            values.put(target, expected);
                        }
                        var buffers = adventureBuffers(editor, false);
                        var prepared = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                        require(prepared.succeeded() && buffers.equals(adventureBuffers(editor, false)), "Adventure preflight changed input: " + representation + "/" + prepared.failure());
                        assertAdventureOutput(fixture, verifyActionHistory(fixture, editor, action, "Adventure " + target + "/" + representation), values, Set.of());
                    }
        for (String target : List.of("can_break", "can_place_on", "both"))
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(adventureOpening(fixture));
                    var values = adventureValues("can_break", "ordered");
                    addAdventurePending(fixture, editor, values);
                    Set<String> removed = target.equals("both") ? Set.copyOf(ADVENTURE_COMPONENTS) : Set.of(target);
                    for (String component : removed) {
                        Object section = adventureSection(editor, component);
                        ((ImBoolean) read(section, "present")).set(false);
                        for (Object row : (List<?>) read(section, "predicates")) {
                            write(row, "blocks", JsonParser.parseString("[\"not a block\"]"));
                            for (String prefix : List.of("nbt", "components", "componentPredicates"))
                                ((ImString) read(row, prefix + "Source")).set("{inactive invalid");
                        }
                    }
                    var buffers = adventureBuffers(editor, false);
                    var prepared = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                    require(prepared.succeeded() && buffers.equals(adventureBuffers(editor, false)), "Adventure removal validated or replaced inactive input");
                    assertAdventureOutput(fixture, verifyActionHistory(fixture, editor, action, "Adventure removal " + target), values, removed);
                }
        for (String target : ADVENTURE_COMPONENTS)
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemStack opening = adventureOpening(fixture);
                    ItemEditor editor = fixture.editedEditor(opening);
                    var values = adventureValues(target, "ordered");
                    for (String other : ADVENTURE_COMPONENTS)
                        if (!other.equals(target))
                            values.put(other, ItemStackCodecs.encodeComponentNbt(fixture.client, dataType(other), opening.get(dataType(other))));
                    addAdventurePending(fixture, editor, values);
                    assertAdventureOutput(fixture, verifyActionHistory(fixture, editor, action, "Adventure single component " + target), values, Set.of());
                }
    }

    private static void assertAdventureRejected(Fixture fixture, ItemEditor editor, String action, String tab) throws Exception {
        ItemStack opening = editor.draft().copy();
        Object history = read(editor, "lastEditorState");
        var buffers = adventureBuffers(editor, false);
        var references = adventureBuffers(editor, true);
        var lore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
        var data = dataBuffers((DataEditorPanel) read(editor, "dataEditor"));
        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
        require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals(tab)
                && buffers.equals(adventureBuffers(editor, false)) && data.equals(dataBuffers((DataEditorPanel) read(editor, "dataEditor")))
                && lore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState()) && history == read(editor, "lastEditorState")
                && ((Deque<?>) read(editor, "editorUndo")).isEmpty() && !((ItemEditHistory) read(editor, "history")).canUndo()
                && ItemStack.matches(opening, editor.draft()) && ItemStack.matches(opening, fixture.slots.get(0)) && fixture.slots.size() == 1 && fixture.writes.isEmpty(),
            "Adventure rejection lost source/history, used the wrong route or wrote output: " + read(editor, "status"));
        var retained = adventureBuffers(editor, true);
        require(references.size() == retained.size(), "Adventure recovery replaced collections");
        for (int index = 0; index < references.size(); index++)
            require(references.get(index) == retained.get(index), "Adventure recovery replaced predicate/state/source/model identity");
    }

    @SuppressWarnings("unchecked")
    private static void verifyAdventureActionFailures() throws Exception {
        for (String target : ADVENTURE_COMPONENTS)
            for (String problem : List.of("block_blank", "block_malformed", "block_unknown", "tag_unknown", "mixed_tag", "blocks_empty",
                "state_blank", "state_duplicate", "state_unknown", "exact_blank", "exact_invalid", "minimum", "maximum", "empty_range",
                "nbt_syntax", "components_syntax", "predicates_syntax", "components_native", "predicates_native", "empty_section", "codec_section"))
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                    try (Fixture fixture = new Fixture()) {
                        ItemEditor editor = fixture.editedEditor(adventureOpening(fixture));
                        var values = adventureValues(target, "ordered");
                        if (List.of("minimum", "maximum", "empty_range").contains(problem)) {
                            CompoundTag last = (CompoundTag) ((ListTag) values.get(target)).get(2);
                            last.getCompound("state").orElseThrow().put("half", TagParser.parseCompoundFully("{min:bottom,max:top}"));
                        }
                        addAdventurePending(fixture, editor, values);
                        captureHistory(editor, fixture.client);
                        Object panel = read(editor, "adventureEditor");
                        Object section = adventureSection(editor, target);
                        List<Object> rows = (List<Object>) read(section, "predicates");
                        Object row = rows.get(2);
                        List<?> states = (List<?>) read(row, "states");
                        Object state = states.getFirst();
                        String field = "predicate/" + read(row, "id") + "/";
                        String stateField = null;
                        String codePrefix = null;
                        Runnable repair;
                        if (problem.startsWith("block_") || List.of("tag_unknown", "mixed_tag", "blocks_empty").contains(problem)) {
                            JsonElement valid = ((JsonElement) read(row, "blocks")).deepCopy();
                            JsonElement invalid = switch (problem) {
                                case "tag_unknown" -> JsonParser.parseString("\"#itemlab:unknown_blocks\"");
                                case "mixed_tag" ->
                                    JsonParser.parseString("[\"#itemlab:adventure_blocks\",\"minecraft:stone\"]");
                                case "blocks_empty" -> JsonParser.parseString("[]");
                                default -> {
                                    JsonElement bad = valid.deepCopy();
                                    bad.getAsJsonArray().set(1, new com.google.gson.JsonPrimitive(problem.equals("block_blank") ? "" : problem.equals("block_malformed") ? "not a block id" : "itemlab:unknown_block"));
                                    yield bad;
                                }
                            };
                            write(row, "blocks", invalid);
                            Map<String, ImString> buffers = (Map<String, ImString>) read(panel, "textBuffers");
                            int index = problem.startsWith("block_") ? 1 : 0;
                            String key = "adventure/" + target + "/" + read(row, "id") + "/blocks/@identifier/" + index;
                            ImString input = buffers.get(key);
                            String before = input.get();
                            input.set(invalid.isJsonArray() ? invalid.getAsJsonArray().isEmpty() ? "" : invalid.getAsJsonArray().get(index).getAsString() : invalid.getAsString());
                            repair = () -> {
                                try {
                                    write(row, "blocks", valid);
                                    input.set(before);
                                } catch (Exception exception) {
                                    throw new AssertionError(exception);
                                }
                            };
                            field += "blocks";
                        } else if (problem.startsWith("state_") || problem.startsWith("exact_") || List.of("minimum", "maximum", "empty_range").contains(problem)) {
                            Object editedState = problem.equals("state_duplicate") ? states.get(1) : state;
                            stateField = problem.startsWith("state_") ? "name" : problem.startsWith("exact_") ? "exact" : problem.equals("empty_range") ? "minimum" : problem;
                            ImString input = (ImString) read(editedState, stateField);
                            String before = input.get();
                            String maximum = ((ImString) read(state, "maximum")).get();
                            input.set(switch (problem) {
                                case "state_blank", "exact_blank" -> " ";
                                case "state_duplicate" -> "half";
                                case "state_unknown" -> "not_a_property";
                                case "empty_range" -> "";
                                default -> "invalid value";
                            });
                            if (problem.equals("empty_range")) ((ImString) read(state, "maximum")).set("");
                            repair = () -> {
                                input.set(before);
                                try {
                                    ((ImString) read(state, "maximum")).set(maximum);
                                } catch (Exception exception) {
                                    throw new AssertionError(exception);
                                }
                            };
                            // Duplicate-key validation detects the first member of the duplicate pair.
                            field += "state/" + read(state, "id") + "/" + stateField;
                        } else if (problem.equals("empty_section")) {
                            var valid = new ArrayList<>(rows);
                            rows.clear();
                            repair = () -> rows.addAll(valid);
                            field = "add_predicate";
                        } else if (problem.equals("codec_section")) {
                            JsonObject extra = (JsonObject) read(row, "extraFields");
                            extra.addProperty("blocks", "not an id");
                            ((ImBoolean) read(row, "blocksPresent")).set(false);
                            repair = () -> {
                                extra.remove("blocks");
                                try {
                                    ((ImBoolean) read(row, "blocksPresent")).set(true);
                                } catch (Exception exception) {
                                    throw new AssertionError(exception);
                                }
                            };
                            field = "$enabled";
                        } else {
                            String prefix = problem.startsWith("predicates") ? "componentPredicates" : problem.substring(0, problem.indexOf('_'));
                            codePrefix = prefix;
                            ImString input = (ImString) read(row, prefix + "Source");
                            String before = input.get();
                            input.set(problem.endsWith("syntax") ? "{broken pending " + prefix : "{\"minecraft:custom_data\":0}");
                            repair = () -> input.set(before);
                            field += prefix.equals("componentPredicates") ? "predicates" : prefix;
                        }
                        assertAdventureRejected(fixture, editor, action, "ADVENTURE");
                        require(target.equals(read(panel, "pendingFocusSection")) && field.equals(read(panel, "pendingFocusField")) && !(boolean) read(panel, "focusPendingToolbar"),
                            "Adventure used wrong precise recovery: " + target + "/" + problem + "/" + read(panel, "pendingFocusField"));
                        if (!field.equals("$enabled") && !field.equals("add_predicate"))
                            require((long) read(section, "focusPredicateRowId") == (long) read(row, "id"), "Adventure focused the wrong predicate row");
                        if (stateField != null)
                            require((long) read(row, "focusStateRowId") == (long) read(state, "id"), "Adventure focused the wrong state row");
                        if (problem.startsWith("block_") || List.of("tag_unknown", "mixed_tag", "blocks_empty").contains(problem))
                            require((int) read(read(row, "blocksFocus"), "requestedRow") == (problem.startsWith("block_") ? 1 : 0), "Adventure focused the wrong block input");
                        if (codePrefix != null)
                            require((boolean) read(read(row, codePrefix + "Editor"), "focusRequested"), "Adventure failed to focus the native-invalid SNBT source");
                        repair.run();
                        assertAdventureOutput(fixture, verifyActionHistory(fixture, editor, action, "Adventure repaired " + target + "/" + problem), values, Set.of());
                    }
    }

    private static void verifyAdventureOtherFailures() throws Exception {
        for (boolean earlier : List.of(true, false))
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(adventureOpening(fixture));
                    var values = adventureValues("can_place_on", "ordered");
                    addAdventurePending(fixture, editor, values);
                    captureHistory(editor, fixture.client);
                    ImString source = earlier ? (ImString) read(editor, "itemId") : (ImString) read(((List<?>) read(read(editor, "dataEditor"), "entries")).getFirst(), "value");
                    String valid = source.get();
                    source.set(earlier ? "not an item id" : "invalid late integer");
                    assertAdventureRejected(fixture, editor, action, earlier ? "NAME" : "DATA");
                    source.set(valid);
                    assertAdventureOutput(fixture, verifyActionHistory(fixture, editor, action, "Adventure earlier/later " + earlier), values, Set.of());
                }
    }

    private static void verifyAdventureConflicts() throws Exception {
        for (String target : ADVENTURE_COMPONENTS)
            for (boolean keepAdventure : List.of(true, false))
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                    try (Fixture fixture = new Fixture()) {
                        ItemStack opening = adventureOpening(fixture);
                        ItemEditor editor = fixture.editedEditor(opening);
                        var option = new ComponentOption("minecraft:" + target, target, dataType(target));
                        write(editor, "componentOptions", List.of(option));
                        require(editor.selectComponent(fixture.client, option), "Adventure component selection failed");
                        write(editor, "useGenericSelectedComponentEditor", true);
                        var values = adventureValues(target, "ordered");
                        addAdventurePending(fixture, editor, values);
                        JsonElement generated = JsonParser.parseString("{\"blocks\":\"minecraft:glass\",\"nbt\":\"{Generated:42}\"}");
                        ComponentFormPanel form = (ComponentFormPanel) read(editor, "componentForm");
                        JsonElement baseline = form.pendingValue().deepCopy();
                        form.restorePending(baseline, generated);
                        captureHistory(editor, fixture.client);
                        assertAdventureRejected(fixture, editor, action, "ADVENTURE");
                        require(editor.selectedComponent() == option && (boolean) read(editor, "useGenericSelectedComponentEditor")
                            && generated.equals(form.pendingValue()) && baseline.equals(read(form, "loadedValue")), "Adventure conflict replaced generated selection/value/baseline");
                        if (keepAdventure) form.discardChanges();
                        else {
                            invoke(read(editor, "adventureEditor"), "sync", new Class<?>[]{Minecraft.class, ItemStack.class}, fixture.client, opening);
                            for (String component : ADVENTURE_COMPONENTS)
                                values.put(component, ItemStackCodecs.encodeComponentNbt(fixture.client, dataType(component), opening.get(dataType(component))));
                            ItemStack chosen = opening.copy();
                            ItemStackCodecs.decodeAndSetComponent(fixture.client, chosen, dataType(target), generated);
                            values.put(target, ItemStackCodecs.encodeComponentNbt(fixture.client, dataType(target), chosen.get(dataType(target))));
                        }
                        assertAdventureOutput(fixture, verifyActionHistory(fixture, editor, action, "Adventure generated resolution " + target + "/" + keepAdventure), values, Set.of());
                    }
    }

    private record BookValues(WritableBookContent writable, WrittenBookContent written) {
        void apply(ItemStack stack) {
            if (writable == null) stack.remove(DataComponents.WRITABLE_BOOK_CONTENT);
            else stack.set(DataComponents.WRITABLE_BOOK_CONTENT, writable);
            if (written == null) stack.remove(DataComponents.WRITTEN_BOOK_CONTENT);
            else stack.set(DataComponents.WRITTEN_BOOK_CONTENT, written);
        }
    }

    private static ItemStack bookOpening(Item item) {
        ItemStack opening = new ItemStack(item);
        // The combined Flags edit to 16 must differ from both vanilla book prototypes.
        if (item == Items.WRITABLE_BOOK || item == Items.WRITTEN_BOOK) opening.set(DataComponents.MAX_STACK_SIZE, 8);
        new BookValues(new WritableBookContent(List.of(new Filterable<>("Opening writable", Optional.of("Opening filtered writable")))),
            new WrittenBookContent(new Filterable<>("Opening title", Optional.of("Opening filtered title")), "Opening author", 1,
                List.of(new Filterable<>(Component.literal("Opening written"), Optional.of(Component.literal("Opening filtered written")))), false)).apply(opening);
        return opening;
    }

    private static BookValues bookValues(ItemStack stack) {
        return new BookValues(stack.get(DataComponents.WRITABLE_BOOK_CONTENT), stack.get(DataComponents.WRITTEN_BOOK_CONTENT));
    }

    /**
     * Expected components are built independently of Book's pending/save builder.
     */
    private static BookValues addBookPending(ItemEditor editor, TextInputFormat format, String representation, boolean signed) throws Exception {
        Object book = read(editor, "bookEditor");
        String titleSource = representation.equals("max_title") ? "T".repeat(WrittenBookContent.TITLE_MAX_LENGTH)
            : representation.equals("empty_metadata") ? "" : formattedSource(format, "<gold><bold>Title</bold></gold>", "&6&lTitle");
        String authorSource = representation.equals("empty_metadata") ? "" : formattedSource(format, "<aqua>Author {name}</aqua>", "&bAuthor {name}");
        String pageSource = representation.equals("max_writable_page") ? "P".repeat(WritableBookContent.PAGE_EDIT_LENGTH)
            : formattedSource(format, "<red><bold>Page {one} ☃</bold></red>", "&c&lPage {one} ☃");
        List<String> sources = representation.equals("empty_pages") ? List.of()
            : representation.equals("many_pages") ? java.util.Collections.nCopies(100, pageSource) : List.of(pageSource, "", pageSource);
        boolean filtered = !representation.equals("unfiltered");
        var writable = new ArrayList<Filterable<String>>();
        var written = new ArrayList<Filterable<Component>>();
        for (String source : sources) {
            writable.add(new Filterable<>(text(source, format).toLegacyString(), filtered ? Optional.of("Filtered writable ☃") : Optional.empty()));
            written.add(new Filterable<>(text(source, format).toComponent(false), filtered ? Optional.of(Component.literal("Filtered written ☃").withStyle(net.minecraft.ChatFormatting.GREEN)) : Optional.empty()));
        }
        int generation = switch (representation) {
            case "generation_low" -> -1;
            case "generation_high" -> 4;
            default -> 2;
        };
        BookValues values = new BookValues(representation.equals("written_only") ? null : new WritableBookContent(writable),
            representation.equals("writable_only") ? null : new WrittenBookContent(new Filterable<>(text(titleSource, format).toLegacyString(),
                filtered ? Optional.of("Filtered title") : Optional.empty()), text(authorSource, format).toLegacyString(),
                Math.clamp(generation, 0, WrittenBookContent.MAX_GENERATION), written, representation.equals("resolved")));
        ItemStack source = editor.draft().copy();
        values.apply(source);
        write(book, "textFormat", format);
        invoke(book, "sync", new Class<?>[]{ItemStack.class}, source);
        // Real source buffers, not a mocked parser or pendingStack result.
        write(book, "title", text(titleSource, format));
        write(book, "author", text(authorSource, format));
        for (String field : List.of("writablePages", "writtenPages")) {
            List<?> pages = (List<?>) read(book, field);
            for (int index = 0; index < pages.size(); index++)
                write(pages.get(index), "text", text(sources.get(index), format));
        }
        ((ImInt) read(book, "generation")).set(generation);
        write(book, "showWritten", signed);
        write(book, "dirty", true);
        write(book, "writableDirty", true);
        write(book, "writtenDirty", true);
        return values;
    }

    private static List<Object> bookBuffers(ItemEditor editor, boolean references) throws Exception {
        Object book = read(editor, "bookEditor");
        var values = new ArrayList<Object>();
        var lines = new ArrayList<RichTextLine>();
        lines.add((RichTextLine) read(book, "title"));
        lines.add((RichTextLine) read(book, "author"));
        for (String field : List.of("writablePages", "writtenPages")) {
            List<?> pages = (List<?>) read(book, field);
            if (references) values.add(pages);
            for (Object page : pages) {
                if (references) values.add(page);
                else values.add(read(page, "filtered"));
                lines.add((RichTextLine) read(page, "text"));
            }
        }
        for (RichTextLine line : lines) {
            if (references) {
                values.add(line);
                values.add(read(line, "source"));
                values.add(read(line, "editorSource"));
            } else {
                values.add(((ImString) read(line, "source")).get());
                values.add(((ImString) read(line, "editorSource")).get());
                values.add(read(line, "concealedEditorPrefix"));
                values.add(line.changed);
                values.add(read(line, "format"));
            }
        }
        if (!references) {
            for (String field : List.of("writablePresent", "writtenPresent", "showWritten", "filteredTitle"))
                values.add(read(book, field));
            values.add(((ImInt) read(book, "generation")).get());
            values.add(((ImBoolean) read(book, "resolved")).get());
        }
        return values;
    }

    private static void assertBookOutput(ItemStack saved, BookValues expected, Item item) {
        assertCombinedPanels(saved, item);
        require(saved.get(DataComponents.CUSTOM_DATA).copyTag().getInt("commit_check").orElseThrow() == 42
            && expected.equals(bookValues(saved)), "Book output lost exact content, filtered metadata, removal or unrelated pending edits");
    }

    private static void verifyBookCommits() throws Exception {
        for (String representation : List.of("both", "writable_only", "written_only", "empty_pages", "empty_metadata", "unfiltered",
            "max_title", "max_writable_page", "many_pages", "generation_low", "generation_high", "resolved"))
            for (TextInputFormat format : TextInputFormat.values())
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                    try (Fixture fixture = new Fixture()) {
                        ItemEditor editor = fixture.editedEditor(bookOpening(Items.STONE));
                        BookValues values = addBookPending(editor, format, representation, representation.equals("written_only"));
                        var buffers = bookBuffers(editor, false);
                        var prepared = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                        require(prepared.succeeded() && buffers.equals(bookBuffers(editor, false)), "Book preflight mutated input: " + representation + "/" + prepared.failure());
                        assertBookOutput(verifyActionHistory(fixture, editor, action, "Book " + representation + "/" + format), values, Items.STONE);
                    }
                }
        for (String removed : List.of("writable", "written", "both"))
            for (boolean signed : List.of(false, true))
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                    try (Fixture fixture = new Fixture()) {
                        ItemEditor editor = fixture.editedEditor(bookOpening(Items.STONE));
                        BookValues values = addBookPending(editor, TextInputFormat.MINI_MESSAGE, "both", signed);
                        Object book = read(editor, "bookEditor");
                        if (!removed.equals("written")) {
                            write(book, "writablePresent", false);
                            write(((List<?>) read(book, "writablePages")).get(2), "text", text(INVALID_MINI_MESSAGE, TextInputFormat.MINI_MESSAGE));
                            values = new BookValues(null, values.written());
                        }
                        if (!removed.equals("writable")) {
                            write(book, "writtenPresent", false);
                            write(book, "title", text(INVALID_MINI_MESSAGE, TextInputFormat.MINI_MESSAGE));
                            write(book, "author", text(INVALID_MINI_MESSAGE, TextInputFormat.MINI_MESSAGE));
                            write(((List<?>) read(book, "writtenPages")).get(2), "text", text(INVALID_MINI_MESSAGE, TextInputFormat.MINI_MESSAGE));
                            values = new BookValues(values.writable(), null);
                        }
                        var buffers = bookBuffers(editor, false);
                        var prepared = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                        require(prepared.succeeded() && buffers.equals(bookBuffers(editor, false)), "Book removal validated or replaced inactive invalid input");
                        assertBookOutput(verifyActionHistory(fixture, editor, action, "Book removal " + removed + "/" + signed), values, Items.STONE);
                    }
        for (Item item : List.of(Items.WRITABLE_BOOK, Items.WRITTEN_BOOK))
            for (boolean signed : List.of(false, true))
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                    try (Fixture fixture = new Fixture()) {
                        ItemEditor editor = fixture.editedEditor(bookOpening(item));
                        BookValues values = addBookPending(editor, TextInputFormat.MINI_MESSAGE, "both", signed);
                        // Keep the header in step with signing intent, as the real signing control does.
                        editor.setPendingVanillaBookType(signed);
                        assertBookOutput(verifyActionHistory(fixture, editor, action, "Book dual content " + item + "/" + signed), values,
                            signed ? Items.WRITTEN_BOOK : Items.WRITABLE_BOOK);
                    }
    }

    private static void assertBookRejected(Fixture fixture, ItemEditor editor, String action, String tab) throws Exception {
        ItemStack opening = editor.draft().copy();
        var buffers = bookBuffers(editor, false);
        var references = bookBuffers(editor, true);
        var data = dataBuffers((DataEditorPanel) read(editor, "dataEditor"));
        var lore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
        Object history = read(editor, "lastEditorState");
        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
        require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals(tab)
            && buffers.equals(bookBuffers(editor, false)) && data.equals(dataBuffers((DataEditorPanel) read(editor, "dataEditor")))
            && lore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState()) && history == read(editor, "lastEditorState")
            && ((Deque<?>) read(editor, "editorUndo")).isEmpty() && !((ItemEditHistory) read(editor, "history")).canUndo()
            && ItemStack.matches(opening, editor.draft()) && ItemStack.matches(opening, fixture.slots.get(0))
            && fixture.writes.isEmpty() && fixture.slots.size() == 1, "Book rejection lost input/history, used wrong tab or wrote output: " + read(editor, "status"));
        var retained = bookBuffers(editor, true);
        require(references.size() == retained.size(), "Book recovery replaced collections");
        for (int index = 0; index < references.size(); index++)
            require(references.get(index) == retained.get(index), "Book recovery replaced a page/text/source buffer");
    }

    private static void verifyBookFailures() throws Exception {
        for (String problem : List.of("title_length", "writable_length", "title_format", "author_format", "writable_format", "written_format"))
            for (boolean signed : List.of(false, true))
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                    try (Fixture fixture = new Fixture()) {
                        ItemEditor editor = fixture.editedEditor(bookOpening(Items.STONE));
                        BookValues values = addBookPending(editor, TextInputFormat.MINI_MESSAGE, "both", signed);
                        Object book = read(editor, "bookEditor");
                        captureHistory(editor, fixture.client);
                        boolean writable = problem.startsWith("writable");
                        boolean page = writable || problem.startsWith("written");
                        RichTextLine line = (RichTextLine) (page ? read(((List<?>) read(book, writable ? "writablePages" : "writtenPages")).get(2), "text")
                            : read(book, problem.startsWith("author") ? "author" : "title"));
                        String valid = line.editorSource(false, false).get();
                        line.editorSource(false, false).set(problem.equals("title_length") ? "T".repeat(WrittenBookContent.TITLE_MAX_LENGTH + 1)
                            : problem.equals("writable_length") ? "P".repeat(WritableBookContent.PAGE_EDIT_LENGTH + 1) : INVALID_MINI_MESSAGE);
                        line.commitEditorSource();
                        assertBookRejected(fixture, editor, action, "BOOK");
                        String section = page ? (writable ? "writable_book_pages" : "written_book_pages") : "written_book_metadata";
                        String field = page ? "page/2" : problem.startsWith("author") ? "author" : "title";
                        require(section.equals(read(book, "pendingFocusSection")) && field.equals(read(book, "pendingFocusField"))
                                && Boolean.valueOf(!writable).equals(read(book, "recoveryWritten")) && !(boolean) read(book, "focusPendingToolbar"),
                            "Book recovery used wrong field/page/view: " + problem);
                        line.editorSource(false, false).set(valid);
                        line.commitEditorSource();
                        assertBookOutput(verifyActionHistory(fixture, editor, action, "Book repaired " + problem + "/" + signed), values, Items.STONE);
                    }
    }

    private static void verifyBookOtherFailures() throws Exception {
        for (boolean earlier : List.of(true, false))
            for (boolean signed : List.of(false, true))
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                    try (Fixture fixture = new Fixture()) {
                        ItemEditor editor = fixture.editedEditor(bookOpening(Items.STONE));
                        BookValues values = addBookPending(editor, TextInputFormat.MINI_MESSAGE, "both", signed);
                        captureHistory(editor, fixture.client);
                        ImString source = earlier ? (ImString) read(editor, "itemId") : (ImString) read(((List<?>) read(read(editor, "dataEditor"), "entries")).getFirst(), "value");
                        String valid = source.get();
                        source.set(earlier ? "not an item id" : "invalid late integer");
                        assertBookRejected(fixture, editor, action, earlier ? "NAME" : "DATA");
                        source.set(valid);
                        assertBookOutput(verifyActionHistory(fixture, editor, action, "Book earlier/later " + earlier + "/" + signed), values, Items.STONE);
                    }
    }

    private static void verifyBookConflicts() throws Exception {
        for (String target : List.of("writable_book_content", "written_book_content"))
            for (boolean signed : List.of(false, true))
                for (boolean keepBook : List.of(true, false))
                    for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                        try (Fixture fixture = new Fixture()) {
                            ItemEditor editor = fixture.editedEditor(bookOpening(Items.STONE));
                            var option = new ComponentOption("minecraft:" + target, target, dataType(target));
                            write(editor, "componentOptions", List.of(option));
                            require(editor.selectComponent(fixture.client, option), "Book component selection failed");
                            write(editor, "useGenericSelectedComponentEditor", true);
                            BookValues values = addBookPending(editor, TextInputFormat.MINI_MESSAGE, "both", signed);
                            ItemStack generatedStack = bookOpening(Items.STONE);
                            new BookValues(new WritableBookContent(List.of(Filterable.passThrough("Generated writable"))),
                                new WrittenBookContent(Filterable.passThrough("Generated title"), "Generated author", 3,
                                    List.of(Filterable.passThrough(Component.literal("Generated written"))), true)).apply(generatedStack);
                            JsonElement generated = ItemStackCodecs.encodeComponent(fixture.client, dataType(target), generatedStack.get(dataType(target)));
                            ComponentFormPanel form = (ComponentFormPanel) read(editor, "componentForm");
                            JsonElement baseline = form.pendingValue().deepCopy();
                            form.restorePending(baseline, generated);
                            captureHistory(editor, fixture.client);
                            assertBookRejected(fixture, editor, action, "BOOK");
                            require(editor.selectedComponent() == option && (boolean) read(editor, "useGenericSelectedComponentEditor")
                                && generated.equals(form.pendingValue()) && baseline.equals(read(form, "loadedValue")), "Book conflict replaced generated selection/value/baseline");
                            if (keepBook) form.discardChanges();
                            else {
                                invoke(read(editor, "bookEditor"), "sync", new Class<?>[]{ItemStack.class}, editor.draft());
                                values = target.equals("writable_book_content") ? new BookValues(generatedStack.get(DataComponents.WRITABLE_BOOK_CONTENT), editor.draft().get(DataComponents.WRITTEN_BOOK_CONTENT))
                                    : new BookValues(editor.draft().get(DataComponents.WRITABLE_BOOK_CONTENT), generatedStack.get(DataComponents.WRITTEN_BOOK_CONTENT));
                            }
                            assertBookOutput(verifyActionHistory(fixture, editor, action, "Book generated resolution " + target + "/" + keepBook + "/" + signed), values, Items.STONE);
                        }
    }

    private static void verifyBookSigning() throws Exception {
        for (boolean signed : List.of(false, true))
            for (TextInputFormat format : TextInputFormat.values())
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                    try (Fixture fixture = new Fixture()) {
                        ItemStack opening = bookOpening(signed ? Items.WRITABLE_BOOK : Items.WRITTEN_BOOK);
                        if (signed) opening.remove(DataComponents.WRITTEN_BOOK_CONTENT);
                        else opening.remove(DataComponents.WRITABLE_BOOK_CONTENT);
                        ItemEditor editor = fixture.editedEditor(opening);
                        BookValues values = addBookPending(editor, format, signed ? "writable_only" : "written_only", !signed);
                        Object book = read(editor, "bookEditor");
                        var writable = new ArrayList<Filterable<String>>();
                        var written = new ArrayList<Filterable<Component>>();
                        if (signed) {
                            List<?> pages = (List<?>) read(book, "writablePages");
                            for (int index = 0; index < pages.size(); index++) {
                                var page = values.writable().pages().get(index);
                                // Signing retains the editable rich component, not a lossy legacy reparse.
                                written.add(new Filterable<>(((RichTextLine) read(pages.get(index), "text")).toComponent(false),
                                    page.filtered().map(value -> RichTextLine.fromLegacyString(value, format).toComponent(false))));
                            }
                            values = new BookValues(null, new WrittenBookContent(new Filterable<>(((RichTextLine) read(book, "title")).toLegacyString(), Optional.empty()),
                                ((RichTextLine) read(book, "author")).toLegacyString(), 0, written, true));
                        } else {
                            for (var page : values.written().pages())
                                writable.add(new Filterable<>(RichTextLine.from(page.raw(), format).toLegacyString(),
                                    page.filtered().map(value -> RichTextLine.from(value, format).toLegacyString())));
                            values = new BookValues(new WritableBookContent(writable), null);
                        }
                        invoke(book, "setSigned", new Class<?>[]{Minecraft.class, boolean.class}, fixture.client, signed);
                        require((boolean) read(book, "showWritten") == signed && ItemStack.matches(opening, editor.draft()) && fixture.writes.isEmpty(), "Book signing blocked valid input or prematurely wrote output");
                        assertBookOutput(verifyActionHistory(fixture, editor, action, "Book signing " + signed + "/" + format), values, signed ? Items.WRITTEN_BOOK : Items.WRITABLE_BOOK);
                    }
    }

    private static void verifyBookStackSizeHistory() throws Exception {
        for (int openingSize : List.of(8, 16))
            for (boolean override : List.of(false, true))
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy"))
                    try (Fixture fixture = new Fixture()) {
                        ItemStack opening = bookOpening(Items.WRITTEN_BOOK);
                        opening.set(DataComponents.MAX_STACK_SIZE, openingSize);
                        ItemEditor editor = fixture.editedEditor(opening);
                        BookValues values = addBookPending(editor, TextInputFormat.MINI_MESSAGE, "both", false);
                        editor.setPendingVanillaBookType(false);
                        Object flags = read(editor, "flagEditor");
                        ((ImBoolean) read(flags, "overrideMaxStackSize")).set(override);
                        // Keep Flags meaningfully pending even if its size equals the old prototype.
                        ((ImInt) read(flags, "repairCost")).set(7);
                        ItemStack saved = verifyActionHistory(fixture, editor, action, "Book stack override " + openingSize + "/" + override);
                        require(saved.is(Items.WRITABLE_BOOK) && saved.getCount() == 99 && saved.getMaxStackSize() == (override ? 16 : 1)
                                && saved.get(DataComponents.REPAIR_COST) == 7 && values.equals(bookValues(saved))
                                && saved.get(DataComponents.CUSTOM_DATA).copyTag().getInt("commit_check").orElseThrow() == 42,
                            "Book retarget/history lost explicit stack-size override/reset, content or unrelated edits");
                    }
    }

    private static final List<String> PRESENTATION_COMPONENTS = List.of("item_name", "item_model", "tooltip_style", "custom_model_data", "dyed_color", "dye",
        "banner_patterns", "base_color", "provides_banner_patterns", "potion_contents", "potion_duration_scale", "fireworks", "firework_explosion");

    private static String presentationOwner(String target) {
        return switch (target) {
            case "banner_patterns", "base_color", "provides_banner_patterns" -> "banner";
            case "potion_contents", "potion_duration_scale" -> "potion";
            case "fireworks", "firework_explosion" -> "firework";
            default -> "appearance";
        };
    }

    private static CodecComponentState presentationState(ItemEditor editor, String target) throws Exception {
        String field = switch (target) {
            case "item_model" -> "itemModel";
            case "tooltip_style" -> "tooltipStyle";
            case "custom_model_data" -> "customModelData";
            case "dyed_color" -> "dyedColor";
            case "provides_banner_patterns" -> "providedPatterns";
            case "potion_contents" -> "contents";
            case "potion_duration_scale" -> "durationScale";
            case "firework_explosion" -> "starExplosion";
            default -> target;
        };
        return (CodecComponentState) read(read(editor, presentationOwner(target) + "Editor"), field);
    }

    private static JsonObject presentationName(boolean pending) {
        JsonObject name = JsonParser.parseString("{\"translate\":\"itemlab.opening\",\"fallback\":\"Opening %s\",\"with\":[{\"text\":\"argument\",\"italic\":true}],\"bold\":true,\"extra\":[{\"text\":\" suffix\",\"color\":\"aqua\"}]}").getAsJsonObject();
        if (pending) {
            name.addProperty("translate", "itemlab.pending");
            name.addProperty("fallback", "Pending %s");
        }
        return name;
    }

    private static Map<String, JsonElement> presentationValues(boolean pending) {
        Map<String, JsonElement> values = new java.util.LinkedHashMap<>();
        values.put("item_name", presentationName(pending));
        values.put("item_model", JsonParser.parseString(pending ? "\"minecraft:diamond\"" : "\"minecraft:stone\""));
        values.put("tooltip_style", JsonParser.parseString(pending ? "\"itemlab:custom_tooltip\"" : "\"minecraft:default\""));
        values.put("custom_model_data", JsonParser.parseString(pending
            ? "{\"floats\":[-2.5,0,3.125],\"flags\":[true,false,true],\"strings\":[\"Custom {model}\",\"\",\"Unicode \u2603\",\"Custom {model}\"],\"colors\":[-1,0,16777215,2147483647]}"
            : "{\"floats\":[9],\"flags\":[false],\"strings\":[\"Opening\"],\"colors\":[123]}"));
        values.put("dyed_color", JsonParser.parseString(pending ? "1267611" : "987654"));
        values.put("dye", JsonParser.parseString(pending ? "\"red\"" : "\"black\""));
        values.put("banner_patterns", JsonParser.parseString(pending
            ? "[{\"pattern\":\"minecraft:stripe_bottom\",\"color\":\"red\"},{\"pattern\":\"minecraft:creeper\",\"color\":\"lime\"},{\"pattern\":\"minecraft:stripe_bottom\",\"color\":\"blue\"}]"
            : "[{\"pattern\":\"minecraft:base\",\"color\":\"white\"}]"));
        values.put("base_color", JsonParser.parseString(pending ? "\"red\"" : "\"black\""));
        values.put("provides_banner_patterns", JsonParser.parseString(pending ? "[\"minecraft:base\",\"minecraft:stripe_bottom\",\"minecraft:base\"]" : "\"minecraft:creeper\""));
        values.put("potion_contents", JsonParser.parseString(pending
            ? "{\"potion\":\"minecraft:water\",\"custom_color\":1267611,\"custom_name\":\"Custom {potion}\",\"custom_effects\":[{\"id\":\"minecraft:speed\",\"duration\":200,\"amplifier\":2,\"ambient\":true,\"show_particles\":false,\"show_icon\":false},{\"id\":\"minecraft:strength\",\"duration\":-1},{\"id\":\"minecraft:speed\",\"duration\":0,\"amplifier\":255}]}"
            : "{\"potion\":\"minecraft:healing\",\"custom_color\":123,\"custom_name\":\"Opening\",\"custom_effects\":[{\"id\":\"minecraft:regeneration\",\"duration\":20}]}"));
        values.put("potion_duration_scale", JsonParser.parseString(pending ? "2.5" : "1"));
        JsonObject rocket = new JsonObject();
        rocket.addProperty("flight_duration", pending ? 3 : 1);
        var explosions = new com.google.gson.JsonArray();
        for (String shape : pending ? List.of("small_ball", "large_ball", "star", "creeper", "burst") : List.of("small_ball")) {
            explosions.add(JsonParser.parseString("{\"shape\":\"" + shape + "\",\"colors\":[0,16777215,16711680,16711680],\"fade_colors\":[255,65280],\"has_trail\":" + pending + ",\"has_twinkle\":" + pending + "}"));
        }
        rocket.add("explosions", explosions);
        values.put("fireworks", rocket);
        values.put("firework_explosion", JsonParser.parseString(pending
            ? "{\"shape\":\"creeper\",\"colors\":[-1,2147483647,0],\"fade_colors\":[16777215,0],\"has_trail\":true,\"has_twinkle\":true}"
            : "{\"shape\":\"small_ball\",\"colors\":[123]}"));
        return values;
    }

    private static ItemStack presentationOpening(Fixture fixture) {
        ItemStack opening = new ItemStack(Items.STONE);
        for (var entry : presentationValues(false).entrySet())
            ItemStackCodecs.decodeAndSetComponent(fixture.client, opening, dataType(entry.getKey()), entry.getValue());
        return opening;
    }

    @SuppressWarnings("unchecked")
    private static void addPresentationPending(ItemEditor editor, Map<String, JsonElement> values) throws Exception {
        Object appearance = read(editor, "appearanceEditor");
        JsonObject name = values.get("item_name").getAsJsonObject();
        ((ImString) read(appearance, "itemNameTranslationKey")).set(" " + name.get("translate").getAsString() + " ");
        ((ImString) read(appearance, "itemNameFallback")).set(name.has("fallback") ? name.get("fallback").getAsString() : "");
        write(appearance, "itemNamePresent", true);
        write(appearance, "itemNameChanged", true);
        for (String target : PRESENTATION_COMPONENTS) {
            if (List.of("item_name", "banner_patterns", "base_color").contains(target)) continue;
            CodecComponentState state = presentationState(editor, target);
            state.setPresent(true);
            state.setValue(values.get(target));
        }
        for (String field : List.of("floats", "flags", "strings", "colors"))
            invoke(appearance, "customModelRowIds", new Class<?>[]{String.class, int.class}, field,
                values.get("custom_model_data").getAsJsonObject().getAsJsonArray(field).size());
        Object banner = read(editor, "bannerEditor");
        ((ImBoolean) read(banner, "patternsPresent")).set(true);
        ((ImBoolean) read(banner, "baseColorPresent")).set(true);
        write(banner, "baseColor", DyeColor.valueOf(values.get("base_color").getAsString().toUpperCase(java.util.Locale.ROOT)));
        var suggestions = new ArrayList<>(fixtureRegistries.lookupOrThrow(Registries.BANNER_PATTERN).keySet().stream().map(id -> IdSuggestion.of(id.toString())).toList());
        suggestions.add(IdSuggestion.of("#itemlab:patterns"));
        write(banner, "providedPatternIds", List.copyOf(suggestions));
        List<Object> layers = (List<Object>) read(banner, "patterns");
        layers.clear();
        for (JsonElement element : values.get("banner_patterns").getAsJsonArray()) {
            Object row = invoke(banner, "createPatternRow", new Class<?>[0]);
            ((ImString) read(row, "patternId")).set(" " + element.getAsJsonObject().get("pattern").getAsString().replace("minecraft:", "") + " ");
            write(row, "color", DyeColor.valueOf(element.getAsJsonObject().get("color").getAsString().toUpperCase(java.util.Locale.ROOT)));
            layers.add(row);
        }
        JsonElement potion = values.get("potion_contents");
        int effects = potion.isJsonObject() && potion.getAsJsonObject().has("custom_effects") ? potion.getAsJsonObject().getAsJsonArray("custom_effects").size() : 0;
        invoke(read(editor, "potionEditor"), "ensureEffectRowIds", new Class<?>[]{int.class}, effects);
        Object firework = read(editor, "fireworkEditor");
        invoke(firework, "ensureExplosionRowIds", new Class<?>[]{int.class}, values.get("fireworks").getAsJsonObject().getAsJsonArray("explosions").size());
        Map<String, List<Long>> colors = (Map<String, List<Long>>) read(firework, "colorRowIds");
        List<Long> ids = (List<Long>) read(firework, "explosionRowIds");
        for (int index = -1; index < ids.size(); index++) {
            String path = index < 0 ? "star_explosion" : "rocket_explosion_" + ids.get(index);
            JsonObject explosion = index < 0 ? values.get("firework_explosion").getAsJsonObject() : values.get("fireworks").getAsJsonObject().getAsJsonArray("explosions").get(index).getAsJsonObject();
            for (String[] field : new String[][]{{"colors", "primary"}, {"fade_colors", "fade"}}) {
                List<Long> rows = new ArrayList<>();
                int size = explosion.has(field[0]) ? explosion.getAsJsonArray(field[0]).size() : 0;
                for (int row = 0; row < size; row++)
                    rows.add(1_000_000L + 100L * (index + 1) + 10L * (field[1].equals("primary") ? 0 : 1) + row);
                colors.put(path + "/" + field[1], rows);
            }
        }
        for (String owner : List.of("appearance", "banner", "potion", "firework"))
            write(read(editor, owner + "Editor"), "dirty", true);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> presentationBuffers(ItemEditor editor, boolean references) throws Exception {
        List<Object> result = new ArrayList<>();
        for (String target : PRESENTATION_COMPONENTS) {
            if (List.of("item_name", "banner_patterns", "base_color").contains(target)) continue;
            CodecComponentState state = presentationState(editor, target);
            result.add(references ? state.value() : List.of(state.present(), state.value().deepCopy(), read(state, "nbtValue") == null ? "" : read(state, "nbtValue")));
        }
        for (String owner : List.of("appearance", "banner", "potion", "firework")) {
            Object panel = read(editor, owner + "Editor");
            var buffers = new java.util.TreeMap<>((Map<String, ImString>) read(panel, "textBuffers"));
            if (references) result.addAll(buffers.values());
            else {
                Map<String, String> text = new HashMap<>();
                buffers.forEach((key, value) -> text.put(key, value.get()));
                result.add(List.of(read(panel, "dirty"), text));
            }
        }
        Object appearance = read(editor, "appearanceEditor");
        for (String field : List.of("itemNameTranslationKey", "itemNameFallback")) {
            ImString source = (ImString) read(appearance, field);
            result.add(references ? source : source.get());
        }
        if (!references) {
            result.add(List.of(read(appearance, "itemNamePresent"), read(appearance, "itemNameChanged"), read(appearance, "originalItemName"), read(appearance, "originalItemNameWasTranslatable"), List.of((Object[]) read(appearance, "itemNameArguments"))));
            for (var entry : new java.util.TreeMap<>((Map<String, List<Long>>) read(appearance, "customModelRowIds")).entrySet())
                result.add(List.of(entry.getKey(), List.copyOf(entry.getValue())));
        }
        Object banner = read(editor, "bannerEditor");
        if (!references)
            result.add(List.of(((ImBoolean) read(banner, "patternsPresent")).get(), ((ImBoolean) read(banner, "baseColorPresent")).get(), read(banner, "baseColor")));
        for (Object row : (List<?>) read(banner, "patterns"))
            result.add(references ? row : List.of(read(row, "rowId"), ((ImString) read(row, "patternId")).get(), read(row, "color")));
        if (!references) {
            result.add(List.copyOf((List<?>) read(read(editor, "potionEditor"), "effectRowIds")));
            result.add(List.copyOf((List<?>) read(read(editor, "fireworkEditor"), "explosionRowIds")));
            for (var entry : new java.util.TreeMap<>((Map<String, List<Long>>) read(read(editor, "fireworkEditor"), "colorRowIds")).entrySet())
                result.add(List.of(entry.getKey(), List.copyOf(entry.getValue())));
        }
        return result;
    }

    private static void assertPresentationOutput(Fixture fixture, ItemStack saved, Map<String, JsonElement> values, Set<String> removed) {
        assertCombinedTextAndFlags(saved, Items.STONE);
        require(saved.get(DataComponents.EQUIPPABLE).slot() == EquipmentSlot.HEAD && saved.get(DataComponents.MAP_COLOR).rgb() == 0x123456
            && saved.get(DataComponents.CUSTOM_DATA).copyTag().getInt("commit_check").orElse(-1) == 42, "Presentation lost other pending panels");
        ItemStack expected = new ItemStack(Items.STONE);
        for (String target : PRESENTATION_COMPONENTS) {
            if (removed.contains(target)) expected.remove(dataType(target));
            else ItemStackCodecs.decodeAndSetComponent(fixture.client, expected, dataType(target), values.get(target));
            require(Objects.equals(saved.get(dataType(target)), expected.get(dataType(target))), "Wrong presentation output: " + target);
        }
    }

    private static void verifyPresentationCommits() throws Exception {
        List<String> representations = new ArrayList<>(List.of("full", "name_no_fallback", "custom_model_empty", "model_custom", "tooltip_custom", "dyed_black", "dyed_white",
            "provided_scalar", "provided_empty", "provided_tag", "banner_empty", "potion_scalar", "potion_empty", "potion_base_only", "potion_no_base", "scale_zero", "scale_max",
            "flight_zero", "flight_max", "rocket_empty", "star_empty", "star_small_ball", "star_large_ball", "star_star", "star_creeper", "star_burst"));
        for (DyeColor color : DyeColor.values()) representations.add("dye_" + color.getName());
        for (String representation : representations)
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    var values = presentationValues(true);
                    switch (representation) {
                        case "full" -> {
                        }
                        case "name_no_fallback" -> values.get("item_name").getAsJsonObject().remove("fallback");
                        case "custom_model_empty" ->
                            values.put("custom_model_data", JsonParser.parseString("{\"floats\":[],\"flags\":[],\"strings\":[],\"colors\":[]}"));
                        case "model_custom" ->
                            values.put("item_model", JsonParser.parseString("\"itemlab:custom_model\""));
                        case "tooltip_custom" ->
                            values.put("tooltip_style", JsonParser.parseString("\"itemlab:another_tooltip\""));
                        case "dyed_black", "dyed_white" ->
                            values.put("dyed_color", JsonParser.parseString(representation.equals("dyed_black") ? "0" : "16777215"));
                        case "provided_scalar", "provided_empty", "provided_tag" ->
                            values.put("provides_banner_patterns", JsonParser.parseString(switch (representation) {
                                case "provided_scalar" -> "\"minecraft:stripe_bottom\"";
                                case "provided_empty" -> "[]";
                                default -> "\"#itemlab:patterns\"";
                            }));
                        case "banner_empty" -> values.put("banner_patterns", JsonParser.parseString("[]"));
                        case "potion_scalar", "potion_empty", "potion_base_only" ->
                            values.put("potion_contents", JsonParser.parseString(switch (representation) {
                                case "potion_scalar" -> "\"minecraft:strong_swiftness\"";
                                case "potion_empty" -> "{}";
                                default -> "{\"potion\":\"minecraft:long_swiftness\"}";
                            }));
                        case "potion_no_base" -> values.get("potion_contents").getAsJsonObject().remove("potion");
                        case "scale_zero", "scale_max" ->
                            values.put("potion_duration_scale", JsonParser.parseString(representation.equals("scale_zero") ? "0" : Float.toString(Float.MAX_VALUE)));
                        case "flight_zero", "flight_max" ->
                            values.get("fireworks").getAsJsonObject().addProperty("flight_duration", representation.equals("flight_zero") ? 0 : 255);
                        case "rocket_empty" ->
                            values.get("fireworks").getAsJsonObject().add("explosions", JsonParser.parseString("[]"));
                        case "star_empty" -> {
                            values.get("firework_explosion").getAsJsonObject().add("colors", JsonParser.parseString("[]"));
                            values.get("firework_explosion").getAsJsonObject().add("fade_colors", JsonParser.parseString("[]"));
                        }
                        default -> {
                            if (representation.startsWith("star_"))
                                values.get("firework_explosion").getAsJsonObject().addProperty("shape", representation.substring(5));
                            else {
                                values.put("dye", JsonParser.parseString("\"" + representation.substring(4) + "\""));
                                values.put("base_color", values.get("dye"));
                            }
                        }
                    }
                    ItemEditor editor = fixture.editedEditor(presentationOpening(fixture));
                    addPresentationPending(editor, values);
                    var before = presentationBuffers(editor, false);
                    var prepared = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                    require(prepared.succeeded() && before.equals(presentationBuffers(editor, false)), "Presentation preflight changed raw input: " + representation);
                    assertPresentationOutput(fixture, verifyActionHistory(fixture, editor, action, "Presentation " + representation), values, Set.of());
                }
            }
        for (String target : PRESENTATION_COMPONENTS)
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(presentationOpening(fixture));
                    var values = presentationValues(true);
                    addPresentationPending(editor, values);
                    Object panel = read(editor, presentationOwner(target) + "Editor");
                    if (target.equals("item_name")) {
                        write(panel, "itemNamePresent", false);
                        ((ImString) read(panel, "itemNameTranslationKey")).set("");
                    } else if (target.equals("banner_patterns")) {
                        ((ImBoolean) read(panel, "patternsPresent")).set(false);
                        ((ImString) read(((List<?>) read(panel, "patterns")).getLast(), "patternId")).set("inactive invalid id");
                    } else if (target.equals("base_color")) ((ImBoolean) read(panel, "baseColorPresent")).set(false);
                    else {
                        presentationState(editor, target).setPresent(false);
                        presentationState(editor, target).setValue(JsonParser.parseString("\"inactive invalid value\""));
                    }
                    assertPresentationOutput(fixture, verifyActionHistory(fixture, editor, action, "Presentation removal " + target), values, Set.of(target));
                }
            }
    }

    private record PresentationProblem(String target, String path, String invalid, String section, String field,
                                       String buffer) {
    }

    private static List<PresentationProblem> presentationProblems() {
        List<PresentationProblem> problems = new ArrayList<>(List.of(
            new PresentationProblem("item_name", "translation_key", " ", "item_name", "translation_key", ""),
            new PresentationProblem("item_model", "$", "\"not a model id\"", "item_model", "identifier", "item_model"),
            new PresentationProblem("tooltip_style", "$", "\"not a tooltip id\"", "tooltip_style", "identifier", "tooltip_style"),
            new PresentationProblem("dyed_color", "$", "{}", "dyed_color", "$enabled", ""),
            new PresentationProblem("dye", "$", "\"invalid_dye\"", "dye", "$enabled", ""),
            new PresentationProblem("banner_patterns", "id", "not a pattern id", "patterns", "@layer/id", ""),
            new PresentationProblem("banner_patterns", "id", "itemlab:unknown_pattern", "patterns", "@layer/id", ""),
            new PresentationProblem("provides_banner_patterns", "1", "\"not a pattern id\"", "provides_banner_patterns", "identifiers", "banner/provided_patterns/@identifier/1"),
            new PresentationProblem("provides_banner_patterns", "1", "\"itemlab:unknown_pattern\"", "provides_banner_patterns", "identifiers", "banner/provided_patterns/@identifier/1"),
            new PresentationProblem("provides_banner_patterns", "$", "\"#itemlab:unknown_tag\"", "provides_banner_patterns", "identifiers", "banner/provided_patterns/@identifier/0"),
            new PresentationProblem("potion_contents", "potion", "\"itemlab:unknown_potion\"", "contents", "base_potion", "base_potion"),
            new PresentationProblem("potion_contents", "custom_color", "{}", "contents", "custom_color", ""),
            new PresentationProblem("potion_contents", "custom_name", "{}", "contents", "custom_name", ""),
            new PresentationProblem("potion_contents", "custom_effects/1/id", "\"itemlab:unknown_effect\"", "contents", "@effect/id", "@effect/id"),
            new PresentationProblem("potion_contents", "custom_effects/1/hidden_effect", "{\"duration\":20,\"amplifier\":1}", "contents", "@effect/hidden_effect", ""),
            new PresentationProblem("potion_contents", "$", "[]", "contents", "$enabled", ""),
            new PresentationProblem("potion_duration_scale", "$", "-1", "duration_scale", "value", ""),
            new PresentationProblem("potion_duration_scale", "$", "{}", "duration_scale", "value", ""),
            new PresentationProblem("fireworks", "flight_duration", "{}", "rocket", "flight_duration", ""),
            new PresentationProblem("fireworks", "explosions/3/shape", "\"invalid_shape\"", "rocket", "@rocket/shape", ""),
            new PresentationProblem("fireworks", "$", "\"wrong representation\"", "rocket", "$enabled", ""),
            new PresentationProblem("firework_explosion", "shape", "\"invalid_shape\"", "star", "star_explosion/shape", "")));
        for (String field : List.of("floats", "flags", "strings", "colors"))
            problems.add(new PresentationProblem("custom_model_data", field + "/1", "{}", "custom_model_data", "$enabled", ""));
        for (String field : List.of("amplifier", "duration", "ambient", "show_particles", "show_icon"))
            problems.add(new PresentationProblem("potion_contents", "custom_effects/1/" + field, "{}", "contents", "@effect/" + (field.equals("amplifier") ? "strength" : field), ""));
        for (String target : List.of("fireworks", "firework_explosion")) {
            String path = target.equals("fireworks") ? "explosions/3/" : "";
            String fieldPath = target.equals("fireworks") ? "@rocket" : "star_explosion";
            String section = target.equals("fireworks") ? "rocket" : "star";
            for (String field : List.of("has_trail", "has_twinkle"))
                problems.add(new PresentationProblem(target, path + field, "{}", section, fieldPath + "/" + field, ""));
            for (String[] field : new String[][]{{"colors", "primary"}, {"fade_colors", "fade"}})
                problems.add(new PresentationProblem(target, path + field[0] + "/1", "{}", section, fieldPath + "/" + field[1] + "/row/@color", ""));
        }
        return problems;
    }

    @SuppressWarnings("unchecked")
    private static String presentationField(ItemEditor editor, String field) throws Exception {
        if (field.contains("@layer"))
            field = field.replace("@layer", "row/" + read(((List<?>) read(read(editor, "bannerEditor"), "patterns")).get(1), "rowId"));
        if (field.contains("@effect"))
            field = field.replace("@effect", "effect_" + ((List<?>) read(read(editor, "potionEditor"), "effectRowIds")).get(1));
        if (field.contains("@rocket"))
            field = field.replace("@rocket", "rocket_explosion_" + ((List<?>) read(read(editor, "fireworkEditor"), "explosionRowIds")).get(3));
        if (field.contains("@color"))
            field = field.replace("@color", ((Map<String, List<Long>>) read(read(editor, "fireworkEditor"), "colorRowIds")).get(field.substring(0, field.indexOf("/row/"))).get(1).toString());
        return field;
    }

    @SuppressWarnings("unchecked")
    private static Runnable invalidatePresentation(ItemEditor editor, PresentationProblem problem) throws Exception {
        Object panel = read(editor, presentationOwner(problem.target()) + "Editor");
        if (problem.target().equals("item_name") || problem.target().equals("banner_patterns")) {
            ImString input = problem.target().equals("item_name") ? (ImString) read(panel, "itemNameTranslationKey")
                : (ImString) read(((List<?>) read(panel, "patterns")).get(1), "patternId");
            String before = input.get();
            input.set(problem.invalid());
            return () -> input.set(before);
        }
        CodecComponentState state = presentationState(editor, problem.target());
        JsonElement invalid = JsonParser.parseString(problem.invalid());
        Runnable repair;
        if (problem.path().equals("$")) {
            JsonElement before = state.value();
            state.setValue(invalid);
            repair = () -> state.setValue(before);
        } else {
            JsonElement parent = state.value();
            String[] path = problem.path().split("/");
            for (int index = 0; index < path.length - 1; index++)
                parent = parent.isJsonArray() ? parent.getAsJsonArray().get(Integer.parseInt(path[index])) : parent.getAsJsonObject().get(path[index]);
            JsonElement owner = parent;
            String key = path[path.length - 1];
            JsonElement before = parent.isJsonArray() ? parent.getAsJsonArray().get(Integer.parseInt(key)) : parent.getAsJsonObject().get(key);
            if (parent.isJsonArray()) parent.getAsJsonArray().set(Integer.parseInt(key), invalid);
            else parent.getAsJsonObject().add(key, invalid);
            repair = () -> {
                if (owner.isJsonArray()) owner.getAsJsonArray().set(Integer.parseInt(key), before);
                else if (before == null) owner.getAsJsonObject().remove(key);
                else owner.getAsJsonObject().add(key, before);
            };
        }
        Map<String, ImString> buffers = (Map<String, ImString>) read(panel, "textBuffers");
        String key = presentationField(editor, problem.buffer());
        ImString beforeBuffer = buffers.get(key);
        ImString source = key.isEmpty() ? null : buffers.computeIfAbsent(key, ignored -> new ImString(512));
        String beforeText = source == null ? "" : source.get();
        if (source != null) source.set(invalid.isJsonPrimitive() ? invalid.getAsString() : invalid.toString());
        return () -> {
            repair.run();
            if (source != null) {
                if (beforeBuffer == null) buffers.remove(key);
                else source.set(beforeText);
            }
        };
    }

    private static void verifyPresentationFailures() throws Exception {
        for (var problem : presentationProblems())
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(presentationOpening(fixture));
                    var values = presentationValues(true);
                    addPresentationPending(editor, values);
                    captureHistory(editor, fixture.client);
                    var valid = presentationBuffers(editor, false);
                    Runnable repair = invalidatePresentation(editor, problem);
                    var invalid = presentationBuffers(editor, false);
                    var references = presentationBuffers(editor, true);
                    Object panel = read(editor, presentationOwner(problem.target()) + "Editor");
                    String field = presentationField(editor, problem.field());
                    String tab = presentationOwner(problem.target()).equals("appearance") ? "PRESENTATION" : presentationOwner(problem.target()).toUpperCase(java.util.Locale.ROOT);
                    var lore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                    var data = dataBuffers((DataEditorPanel) read(editor, "dataEditor"));
                    Object history = read(editor, "lastEditorState");
                    ItemStack opening = editor.draft().copy();
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals(tab)
                        && problem.section().equals(read(panel, "pendingFocusSection")) && field.equals(read(panel, "pendingFocusField"))
                        && !(boolean) read(panel, "focusPendingToolbar"), "Presentation failure accepted input or used the wrong recovery: " + problem + "/" + read(editor, "status"));
                    require(invalid.equals(presentationBuffers(editor, false)) && lore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                        && data.equals(dataBuffers((DataEditorPanel) read(editor, "dataEditor"))) && history == read(editor, "lastEditorState")
                        && ((Deque<?>) read(editor, "editorUndo")).isEmpty() && !((ItemEditHistory) read(editor, "history")).canUndo()
                        && ItemStack.matches(opening, editor.draft()) && ItemStack.matches(opening, fixture.slots.get(0))
                        && fixture.writes.isEmpty() && fixture.slots.size() == 1, "Presentation rejection lost input/history or wrote output: " + problem);
                    var retained = presentationBuffers(editor, true);
                    require(references.size() == retained.size(), "Presentation recovery replaced input collections");
                    for (int index = 0; index < references.size(); index++)
                        require(references.get(index) == retained.get(index), "Presentation recovery replaced a buffer/model/row: " + problem);
                    if (problem.target().equals("provides_banner_patterns"))
                        require((int) read(read(panel, "providedPatternsFocus"), "requestedRow") == (problem.path().equals("$") ? 0 : 1), "Banner focused the wrong provided-pattern row");
                    repair.run();
                    require(valid.equals(presentationBuffers(editor, false)), "Presentation repair did not restore raw pending input: " + problem);
                    assertPresentationOutput(fixture, verifyActionHistory(fixture, editor, action, "Presentation repaired " + problem.target() + "/" + problem.path()), values, Set.of());
                }
            }
    }

    private static void syncPresentation(ItemEditor editor, Fixture fixture, String owner) throws Exception {
        invoke(read(editor, owner + "Editor"), "sync", new Class<?>[]{Minecraft.class, ItemStack.class}, fixture.client, editor.draft());
    }

    private static void verifyPresentationOtherFailures() throws Exception {
        for (String owner : List.of("appearance", "banner", "potion", "firework"))
            for (boolean earlier : List.of(true, false))
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                    try (Fixture fixture = new Fixture()) {
                        ItemEditor editor = fixture.editedEditor(presentationOpening(fixture));
                        var values = presentationValues(true);
                        addPresentationPending(editor, values);
                        for (String other : List.of("appearance", "banner", "potion", "firework"))
                            if (!other.equals(owner)) syncPresentation(editor, fixture, other);
                        captureHistory(editor, fixture.client);
                        Object history = read(editor, "lastEditorState");
                        ImString source = (ImString) read(((List<?>) read(read(editor, "dataEditor"), "entries")).getFirst(), "value");
                        if (earlier) ((ImString) read(editor, "itemId")).set("not an item id");
                        else source.set("invalid late integer");
                        var buffers = presentationBuffers(editor, false);
                        ItemStack opening = editor.draft().copy();
                        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                        require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals(earlier ? "NAME" : "DATA")
                            && buffers.equals(presentationBuffers(editor, false)) && history == read(editor, "lastEditorState")
                            && ItemStack.matches(opening, editor.draft()) && ItemStack.matches(opening, fixture.slots.get(0))
                            && fixture.writes.isEmpty() && fixture.slots.size() == 1 && ((Deque<?>) read(editor, "editorUndo")).isEmpty(), "Presentation earlier/later failure lost " + owner);
                        if (earlier) ((ImString) read(editor, "itemId")).set("minecraft:stone");
                        else source.set("42");
                        for (String target : PRESENTATION_COMPONENTS)
                            if (!presentationOwner(target).equals(owner))
                                values.put(target, ItemStackCodecs.encodeComponent(fixture.client, dataType(target), opening.get(dataType(target))));
                        assertPresentationOutput(fixture, verifyActionHistory(fixture, editor, action, "Presentation earlier/later " + owner + "/" + earlier), values, Set.of());
                    }
                }
    }

    private static JsonElement generatedPresentationValue(String target) {
        return JsonParser.parseString(switch (target) {
            case "item_name" -> "{\"text\":\"Generated {name}\",\"color\":\"yellow\"}";
            case "item_model" -> "\"minecraft:apple\"";
            case "tooltip_style" -> "\"itemlab:generated_tooltip\"";
            case "custom_model_data" -> "{\"floats\":[42],\"strings\":[\"Generated\"]}";
            case "dyed_color" -> "42";
            case "dye", "base_color" -> "\"blue\"";
            case "banner_patterns" -> "[{\"pattern\":\"minecraft:stripe_top\",\"color\":\"blue\"}]";
            case "provides_banner_patterns" -> "\"minecraft:stripe_top\"";
            case "potion_contents" -> "{\"potion\":\"minecraft:night_vision\"}";
            case "potion_duration_scale" -> "4";
            case "fireworks" -> "{\"flight_duration\":2,\"explosions\":[]}";
            case "firework_explosion" -> "{\"shape\":\"burst\",\"colors\":[42]}";
            default -> throw new AssertionError(target);
        });
    }

    private static void verifyPresentationConflicts() throws Exception {
        for (String target : PRESENTATION_COMPONENTS)
            for (boolean keepGrouped : List.of(true, false))
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                    try (Fixture fixture = new Fixture()) {
                        ItemEditor editor = fixture.editedEditor(presentationOpening(fixture));
                        var values = presentationValues(true);
                        var option = new ComponentOption("minecraft:" + target, target, dataType(target));
                        write(editor, "componentOptions", List.of(option));
                        require(editor.selectComponent(fixture.client, option), "Presentation component selection failed: " + target);
                        write(editor, "useGenericSelectedComponentEditor", true);
                        addPresentationPending(editor, values);
                        ComponentFormPanel form = (ComponentFormPanel) read(editor, "componentForm");
                        JsonElement baseline = form.pendingValue().deepCopy();
                        JsonElement generated = generatedPresentationValue(target);
                        form.restorePending(baseline, generated);
                        captureHistory(editor, fixture.client);
                        Object before = invoke(editor, "captureEditorSnapshot", new Class<?>[]{Minecraft.class}, fixture.client);
                        var buffers = presentationBuffers(editor, false);
                        Object history = read(editor, "lastEditorState");
                        ItemStack opening = editor.draft().copy();
                        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                        Object after = invoke(editor, "captureEditorSnapshot", new Class<?>[]{Minecraft.class}, fixture.client);
                        String tab = presentationOwner(target).equals("appearance") ? "PRESENTATION" : presentationOwner(target).toUpperCase(java.util.Locale.ROOT);
                        require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals(tab)
                            && (boolean) invoke(before, "sameState", new Class<?>[]{before.getClass()}, after) && buffers.equals(presentationBuffers(editor, false))
                            && form.pendingValue().equals(generated) && baseline.equals(read(form, "loadedValue")) && editor.selectedComponent() == option
                            && (boolean) read(editor, "useGenericSelectedComponentEditor") && history == read(editor, "lastEditorState")
                            && ItemStack.matches(opening, editor.draft()) && ItemStack.matches(opening, fixture.slots.get(0))
                            && fixture.writes.isEmpty() && fixture.slots.size() == 1 && ((Deque<?>) read(editor, "editorUndo")).isEmpty(), "Presentation conflict lost input or wrote output: " + target);
                        if (keepGrouped) form.discardChanges();
                        else {
                            syncPresentation(editor, fixture, presentationOwner(target));
                            for (String other : PRESENTATION_COMPONENTS)
                                if (presentationOwner(other).equals(presentationOwner(target)))
                                    values.put(other, ItemStackCodecs.encodeComponent(fixture.client, dataType(other), opening.get(dataType(other))));
                            values.put(target, generated);
                        }
                        assertPresentationOutput(fixture, verifyActionHistory(fixture, editor, action, "Presentation generated resolution " + target + "/" + keepGrouped), values, Set.of());
                    }
                }
    }

    private static final List<String> EQUIPMENT_MAP_POT = List.of("equippable", "trim", "provides_trim_material", "break_sound",
        "map_id", "map_color", "map_decorations", "pot_decorations");

    private static String equipmentMapPotOwner(String target) {
        return target.startsWith("map_") ? "map" : target.equals("pot_decorations") ? "decoratedPot" : "equipment";
    }

    private static CodecComponentState equipmentState(ItemEditor editor, String target) throws Exception {
        return (CodecComponentState) read(read(editor, "equipmentEditor"), switch (target) {
            case "provides_trim_material" -> "providedTrimMaterial";
            case "break_sound" -> "breakSound";
            default -> target;
        });
    }

    private static Map<String, JsonElement> equipmentMapPotValues() {
        Map<String, JsonElement> values = new HashMap<>();
        values.put("equippable", JsonParser.parseString("{\"slot\":\"head\",\"equip_sound\":\"minecraft:item.armor.equip_diamond\","
            + "\"asset_id\":\"itemlab:custom_equipment\",\"camera_overlay\":\"itemlab:custom_overlay\",\"allowed_entities\":[\"minecraft:player\",\"minecraft:cow\",\"minecraft:player\"],"
            + "\"dispensable\":false,\"swappable\":false,\"damage_on_hurt\":false,\"equip_on_interact\":true,\"can_be_sheared\":true,\"shearing_sound\":\"minecraft:item.shears.snip\"}"));
        values.put("trim", JsonParser.parseString("{\"material\":\"minecraft:gold\",\"pattern\":\"minecraft:spire\"}"));
        values.put("provides_trim_material", JsonParser.parseString("\"minecraft:diamond\""));
        values.put("break_sound", JsonParser.parseString("\"minecraft:block.glass.break\""));
        values.put("map_id", JsonParser.parseString("42"));
        values.put("map_color", JsonParser.parseString("1267611"));
        values.put("map_decorations", JsonParser.parseString("{\"home\":{\"type\":\"minecraft:red_marker\",\"x\":75.125,\"z\":-90.25,\"rotation\":450.5},"
            + "\"later\":{\"type\":\"minecraft:blue_marker\",\"x\":0.0,\"z\":0.0,\"rotation\":-45.25}}"));
        values.put("pot_decorations", JsonParser.parseString("[\"minecraft:angler_pottery_sherd\",\"minecraft:archer_pottery_sherd\",\"minecraft:arms_up_pottery_sherd\",\"minecraft:snort_pottery_sherd\"]"));
        return values;
    }

    private static ItemStack equipmentMapPotOpening(Fixture fixture) {
        ItemStack opening = new ItemStack(Items.STONE);
        opening.set(DataComponents.CUSTOM_NAME, Component.literal("Opening name"));
        for (var entry : Map.of("equippable", "{\"slot\":\"offhand\"}", "trim", "{\"material\":\"minecraft:iron\",\"pattern\":\"minecraft:coast\"}",
            "provides_trim_material", "\"minecraft:iron\"", "break_sound", "\"minecraft:entity.item.break\"", "map_id", "7", "map_color", "8943462",
            "map_decorations", "{\"opening\":{\"type\":\"minecraft:player\",\"x\":1,\"z\":2,\"rotation\":3}}",
            "pot_decorations", "[\"minecraft:burn_pottery_sherd\",\"minecraft:burn_pottery_sherd\",\"minecraft:burn_pottery_sherd\",\"minecraft:burn_pottery_sherd\"]").entrySet()) {
            ItemStackCodecs.decodeAndSetComponent(fixture.client, opening, dataType(entry.getKey()), JsonParser.parseString(entry.getValue()));
        }
        return opening;
    }

    @SuppressWarnings("unchecked")
    private static void addEquipmentMapPotPending(ItemEditor editor, Map<String, JsonElement> values) throws Exception {
        Object equipment = read(editor, "equipmentEditor");
        write(equipment, "sounds", BuiltInRegistries.SOUND_EVENT.keySet().stream().map(id -> IdSuggestion.of(id.toString())).toList());
        var entityIds = new ArrayList<>(fixtureRegistries.lookupOrThrow(Registries.ENTITY_TYPE).keySet().stream().map(id -> IdSuggestion.of(id.toString())).toList());
        entityIds.add(IdSuggestion.of("#itemlab:wearers"));
        write(equipment, "entities", List.copyOf(entityIds));
        write(equipment, "trimMaterials", fixtureRegistries.lookupOrThrow(Registries.TRIM_MATERIAL).keySet().stream().map(id -> IdSuggestion.of(id.toString())).toList());
        write(equipment, "trimPatterns", fixtureRegistries.lookupOrThrow(Registries.TRIM_PATTERN).keySet().stream().map(id -> IdSuggestion.of(id.toString())).toList());
        for (String target : EQUIPMENT_MAP_POT.subList(0, 4)) {
            CodecComponentState state = equipmentState(editor, target);
            state.setPresent(true);
            state.setValue(values.get(target));
        }
        write(equipment, "dirty", true);
        Object map = read(editor, "mapEditor");
        write(map, "mapIdPresent", true);
        ((ImInt) read(map, "mapId")).set(values.get("map_id").getAsInt());
        ((ImBoolean) read(map, "colorPresent")).set(true);
        write(map, "mapColor", values.get("map_color").getAsInt());
        ((ImBoolean) read(map, "decorationsPresent")).set(true);
        write(map, "decorationTypes", BuiltInRegistries.MAP_DECORATION_TYPE.keySet().stream().map(id -> IdSuggestion.of(id.toString())).toList());
        List<Object> rows = (List<Object>) read(map, "decorations");
        rows.clear();
        for (var entry : values.get("map_decorations").getAsJsonObject().entrySet()) {
            JsonObject value = entry.getValue().getAsJsonObject();
            Object row = invoke(map, "createDecorationRow", new Class<?>[0]);
            ((ImString) read(row, "name")).set(" " + entry.getKey() + " ");
            ((ImString) read(row, "type")).set(" " + value.get("type").getAsString().replace("minecraft:", "") + " ");
            ((ImDouble) read(row, "x")).set(value.get("x").getAsDouble());
            ((ImDouble) read(row, "z")).set(value.get("z").getAsDouble());
            ((ImDouble) read(row, "rotation")).set(value.get("rotation").getAsDouble());
            rows.add(row);
        }
        write(map, "dirty", true);
        Object pot = read(editor, "decoratedPotEditor");
        ((ImBoolean) read(pot, "decorationsPresent")).set(true);
        int index = 0;
        for (ImString side : ((Map<?, ImString>) read(pot, "sideValues")).values())
            side.set(" " + values.get("pot_decorations").getAsJsonArray().get(index++).getAsString().replace("minecraft:", "") + " ");
        write(pot, "dirty", true);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> equipmentMapPotBuffers(ItemEditor editor) throws Exception {
        List<Object> buffers = new ArrayList<>();
        for (String target : EQUIPMENT_MAP_POT.subList(0, 4)) {
            CodecComponentState state = equipmentState(editor, target);
            buffers.add(List.of(state.present(), state.value().deepCopy(), read(state, "nbtValue") == null ? "" : read(state, "nbtValue")));
        }
        for (String owner : List.of("equipment", "map", "decoratedPot")) {
            Object panel = read(editor, owner + "Editor");
            Map<String, String> text = new HashMap<>();
            ((Map<String, ImString>) read(panel, "textBuffers")).forEach((key, value) -> text.put(key, value.get()));
            buffers.add(List.of(read(panel, "dirty"), Map.copyOf(text)));
        }
        Object map = read(editor, "mapEditor");
        buffers.add(List.of(read(map, "mapIdPresent"), ((ImInt) read(map, "mapId")).get(), ((ImBoolean) read(map, "colorPresent")).get(),
            read(map, "mapColor"), ((ImBoolean) read(map, "decorationsPresent")).get(), read(map, "originalDecorationNames")));
        for (Object row : (List<?>) read(map, "decorations"))
            buffers.add(List.of(read(row, "rowId"), ((ImString) read(row, "name")).get(),
                ((ImString) read(row, "type")).get(), ((ImDouble) read(row, "x")).get(), ((ImDouble) read(row, "z")).get(), ((ImDouble) read(row, "rotation")).get()));
        Object pot = read(editor, "decoratedPotEditor");
        buffers.add(List.of(((ImBoolean) read(pot, "decorationsPresent")).get(), ((Map<?, ImString>) read(pot, "sideValues")).values().stream().map(ImString::get).toList()));
        return buffers;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> equipmentMapPotReferences(ItemEditor editor) throws Exception {
        List<Object> references = new ArrayList<>();
        for (String target : EQUIPMENT_MAP_POT.subList(0, 4)) references.add(equipmentState(editor, target).value());
        for (String owner : List.of("equipment", "map", "decoratedPot"))
            references.addAll(new java.util.TreeMap<>((Map<String, ImString>) read(read(editor, owner + "Editor"), "textBuffers")).values());
        references.addAll((List<?>) read(read(editor, "mapEditor"), "decorations"));
        references.addAll(((Map<?, ImString>) read(read(editor, "decoratedPotEditor"), "sideValues")).values());
        return references;
    }

    private static void assertEquipmentMapPotOutput(Fixture fixture, ItemStack saved, Map<String, JsonElement> values, Set<String> removed) {
        assertCombinedCore(saved, Items.STONE);
        require(saved.get(DataComponents.CUSTOM_DATA).copyTag().getInt("commit_check").orElse(-1) == 42, "Equipment/Map/Pot lost pending Data");
        ItemStack expected = new ItemStack(Items.STONE);
        for (String target : EQUIPMENT_MAP_POT) {
            if (!removed.contains(target))
                ItemStackCodecs.decodeAndSetComponent(fixture.client, expected, dataType(target), values.get(target));
            else expected.remove(dataType(target));
            require(Objects.equals(saved.get(dataType(target)), expected.get(dataType(target))), "Wrong Equipment/Map/Pot output: " + target);
        }
    }

    private static JsonElement inlineTrimValue(Fixture fixture, boolean material) {
        JsonObject value = (material
            ? TrimMaterial.DIRECT_CODEC.encodeStart(ItemStackCodecs.jsonOps(fixture.client), fixtureRegistries.lookupOrThrow(Registries.TRIM_MATERIAL).getValue(Identifier.parse("minecraft:gold")))
            : TrimPattern.DIRECT_CODEC.encodeStart(ItemStackCodecs.jsonOps(fixture.client), fixtureRegistries.lookupOrThrow(Registries.TRIM_PATTERN).getValue(Identifier.parse("minecraft:spire"))))
            .getOrThrow().getAsJsonObject();
        value.add("description", JsonParser.parseString("{\"text\":\"Custom trim {description}\",\"bold\":true,\"color\":\"gold\"}"));
        return value;
    }

    private static void verifyEquipmentMapPotCommits() throws Exception {
        for (String representation : List.of("mainhand", "offhand", "feet", "legs", "chest", "head", "body", "saddle",
            "scalar_entities", "empty_entities", "tag_entities", "no_restriction", "inline_equip_sound", "inline_break_sound", "inline_shearing_sound",
            "inline_material", "inline_pattern", "inline_trims", "inline_provided", "map_zero_id", "map_max_id", "map_negative_id", "map_black", "map_white", "map_empty",
            "pot_bricks", "pot_any_items")) {
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    var values = equipmentMapPotValues();
                    JsonObject equip = values.get("equippable").getAsJsonObject();
                    switch (representation) {
                        case "mainhand", "offhand", "feet", "legs", "chest", "head", "body", "saddle" ->
                            equip.addProperty("slot", representation);
                        case "scalar_entities" -> equip.addProperty("allowed_entities", "minecraft:cow");
                        case "empty_entities" -> equip.add("allowed_entities", JsonParser.parseString("[]"));
                        case "tag_entities" -> equip.addProperty("allowed_entities", "#itemlab:wearers");
                        case "no_restriction" -> equip.remove("allowed_entities");
                        case "inline_equip_sound" ->
                            equip.add("equip_sound", JsonParser.parseString("{\"sound_id\":\"itemlab:custom.equip\",\"range\":-1}"));
                        case "inline_break_sound" ->
                            values.put("break_sound", JsonParser.parseString("{\"sound_id\":\"itemlab:custom.break\",\"range\":0}"));
                        case "inline_shearing_sound" ->
                            equip.add("shearing_sound", JsonParser.parseString("{\"sound_id\":\"itemlab:custom.shear\"}"));
                        case "inline_material" ->
                            values.get("trim").getAsJsonObject().add("material", inlineTrimValue(fixture, true));
                        case "inline_pattern" ->
                            values.get("trim").getAsJsonObject().add("pattern", inlineTrimValue(fixture, false));
                        case "inline_trims" -> {
                            values.get("trim").getAsJsonObject().add("material", inlineTrimValue(fixture, true));
                            values.get("trim").getAsJsonObject().add("pattern", inlineTrimValue(fixture, false));
                        }
                        case "inline_provided" -> values.put("provides_trim_material", inlineTrimValue(fixture, true));
                        case "map_zero_id", "map_negative_id" -> values.put("map_id", JsonParser.parseString("0"));
                        case "map_max_id" -> values.put("map_id", JsonParser.parseString("2147483647"));
                        case "map_black" -> values.put("map_color", JsonParser.parseString("0"));
                        case "map_white" -> values.put("map_color", JsonParser.parseString("16777215"));
                        case "map_empty" -> values.put("map_decorations", JsonParser.parseString("{}"));
                        case "pot_bricks" ->
                            values.put("pot_decorations", JsonParser.parseString("[\"minecraft:brick\",\"minecraft:brick\",\"minecraft:brick\",\"minecraft:brick\"]"));
                        case "pot_any_items" ->
                            values.put("pot_decorations", JsonParser.parseString("[\"minecraft:diamond\",\"minecraft:air\",\"minecraft:stone\",\"minecraft:apple\"]"));
                        default -> throw new AssertionError(representation);
                    }
                    ItemEditor editor = fixture.editedEditor(equipmentMapPotOpening(fixture));
                    addEquipmentMapPotPending(editor, values);
                    if (representation.equals("map_negative_id"))
                        ((ImInt) read(read(editor, "mapEditor"), "mapId")).set(-7);
                    var before = equipmentMapPotBuffers(editor);
                    var prepared = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                    require(prepared.succeeded() && before.equals(equipmentMapPotBuffers(editor)), "Equipment/Map/Pot preflight changed models: " + representation + "/" + prepared.failure());
                    assertEquipmentMapPotOutput(fixture, verifyActionHistory(fixture, editor, action, "Equipment/Map/Pot " + representation), values, Set.of());
                }
            }
        }
        for (String target : EQUIPMENT_MAP_POT) {
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(equipmentMapPotOpening(fixture));
                    var values = equipmentMapPotValues();
                    addEquipmentMapPotPending(editor, values);
                    if (equipmentMapPotOwner(target).equals("equipment")) {
                        equipmentState(editor, target).setPresent(false);
                        equipmentState(editor, target).setValue(JsonParser.parseString("\"inactive invalid input\""));
                    } else if (target.equals("pot_decorations")) {
                        Object pot = read(editor, "decoratedPotEditor");
                        ((ImBoolean) read(pot, "decorationsPresent")).set(false);
                        for (Object side : ((Map<?, ?>) read(pot, "sideValues")).values())
                            ((ImString) side).set("inactive invalid item");
                    } else {
                        Object map = read(editor, "mapEditor");
                        if (target.equals("map_id")) write(map, "mapIdPresent", false);
                        else
                            ((ImBoolean) read(map, target.equals("map_color") ? "colorPresent" : "decorationsPresent")).set(false);
                        if (target.equals("map_decorations"))
                            ((ImString) read(((List<?>) read(map, "decorations")).getLast(), "type")).set("inactive invalid type");
                    }
                    assertEquipmentMapPotOutput(fixture, verifyActionHistory(fixture, editor, action, "Equipment/Map/Pot removal " + target), values, Set.of(target));
                }
            }
        }
    }

    private record EquipmentMapPotProblem(String target, String path, String invalid, String section, String field,
                                          String buffer) {
    }

    private static List<EquipmentMapPotProblem> equipmentMapPotProblems() {
        List<EquipmentMapPotProblem> problems = new ArrayList<>(List.of(
            new EquipmentMapPotProblem("equippable", "slot", "\"invalid_slot\"", "equippable", "slot", ""),
            new EquipmentMapPotProblem("equippable", "equip_sound", "\"not a sound id\"", "equippable", "equippable/equip_sound", "equippable/equip_sound"),
            new EquipmentMapPotProblem("equippable", "equip_sound", "\"itemlab:unknown_sound\"", "equippable", "equippable/equip_sound", "equippable/equip_sound"),
            new EquipmentMapPotProblem("equippable", "asset_id", "\"not an asset id\"", "equippable", "equippable/asset", "equippable/asset"),
            new EquipmentMapPotProblem("equippable", "camera_overlay", "\"not an overlay id\"", "equippable", "equippable/overlay", "equippable/overlay"),
            new EquipmentMapPotProblem("equippable", "allowed_entities/1", "\"not an entity id\"", "equippable", "allowed_entities", "equippable/entities/@identifier/1"),
            new EquipmentMapPotProblem("equippable", "allowed_entities/1", "\"itemlab:unknown_entity\"", "equippable", "allowed_entities", "equippable/entities/@identifier/1"),
            new EquipmentMapPotProblem("equippable", "allowed_entities/1", "\"#itemlab:wearers\"", "equippable", "allowed_entities", "equippable/entities/@identifier/1"),
            new EquipmentMapPotProblem("equippable", "allowed_entities", "\"#itemlab:unknown_tag\"", "equippable", "allowed_entities", "equippable/entities/@identifier/0"),
            new EquipmentMapPotProblem("equippable", "shearing_sound", "\"not a shear id\"", "equippable", "equippable/shearing_sound", "equippable/shearing_sound"),
            new EquipmentMapPotProblem("equippable", "dispensable", "{}", "equippable", "$enabled", ""),
            new EquipmentMapPotProblem("equippable", "equip_sound/sound_id", "\"not a sound id\"", "equippable", "$enabled", ""),
            new EquipmentMapPotProblem("equippable", "shearing_sound/sound_id", "\"not a sound id\"", "equippable", "$enabled", ""),
            new EquipmentMapPotProblem("trim", "material", "\"not a material id\"", "trim", "trim/material", "trim/material"),
            new EquipmentMapPotProblem("trim", "material", "\"itemlab:unknown_material\"", "trim", "trim/material", "trim/material"),
            new EquipmentMapPotProblem("trim", "pattern", "\"not a pattern id\"", "trim", "trim/pattern", "trim/pattern"),
            new EquipmentMapPotProblem("trim", "pattern", "\"itemlab:unknown_pattern\"", "trim", "trim/pattern", "trim/pattern"),
            new EquipmentMapPotProblem("trim", "material/description", "{\"text\":{}}", "trim", "$enabled", ""),
            new EquipmentMapPotProblem("trim", "pattern/description", "{\"text\":{}}", "trim", "$enabled", ""),
            new EquipmentMapPotProblem("provides_trim_material", "$", "\"not a material id\"", "provided_trim", "provided_trim/material", "provided_trim/material"),
            new EquipmentMapPotProblem("provides_trim_material", "$", "\"itemlab:unknown_material\"", "provided_trim", "provided_trim/material", "provided_trim/material"),
            new EquipmentMapPotProblem("provides_trim_material", "description", "{\"text\":{}}", "provided_trim", "$enabled", ""),
            new EquipmentMapPotProblem("break_sound", "$", "\"not a sound id\"", "break_sound", "break_sound", "break_sound"),
            new EquipmentMapPotProblem("break_sound", "$", "\"itemlab:unknown_sound\"", "break_sound", "break_sound", "break_sound"),
            new EquipmentMapPotProblem("break_sound", "sound_id", "\"not a sound id\"", "break_sound", "$enabled", ""),
            new EquipmentMapPotProblem("map_decorations", "name", "", "map_decorations", "name", ""),
            new EquipmentMapPotProblem("map_decorations", "name", " home ", "map_decorations", "name", ""),
            new EquipmentMapPotProblem("map_decorations", "type", "not a type id", "map_decorations", "type", ""),
            new EquipmentMapPotProblem("map_decorations", "type", "itemlab:unknown_type", "map_decorations", "type", "")));
        for (String field : List.of("x", "z", "rotation"))
            for (String value : List.of("NaN", "Infinity"))
                problems.add(new EquipmentMapPotProblem("map_decorations", field, value, "map_decorations", field, ""));
        problems.add(new EquipmentMapPotProblem("map_decorations", "rotation", "1e100", "map_decorations", "rotation", ""));
        for (String side : List.of("back", "left", "right", "front"))
            for (String value : List.of("", "not an item id", "itemlab:unknown_item"))
                problems.add(new EquipmentMapPotProblem("pot_decorations", side, value, "pot_decorations", "side/" + side, ""));
        return problems;
    }

    @SuppressWarnings("unchecked")
    private static Runnable invalidateEquipmentMapPot(ItemEditor editor, EquipmentMapPotProblem problem) throws Exception {
        Object panel = read(editor, equipmentMapPotOwner(problem.target()) + "Editor");
        if (problem.target().equals("pot_decorations")) {
            for (var side : ((Map<?, ImString>) read(panel, "sideValues")).entrySet())
                if (side.getKey().toString().equalsIgnoreCase(problem.path())) {
                    String before = side.getValue().get();
                    side.getValue().set(problem.invalid());
                    return () -> side.getValue().set(before);
                }
            throw new AssertionError(problem);
        }
        if (problem.target().equals("map_decorations")) {
            Object row = ((List<?>) read(panel, "decorations")).getLast();
            Object field = read(row, problem.path());
            if (field instanceof ImString text) {
                String before = text.get();
                text.set(problem.invalid());
                return () -> text.set(before);
            }
            ImDouble number = (ImDouble) field;
            double before = number.get();
            number.set(Double.parseDouble(problem.invalid()));
            return () -> number.set(before);
        }
        CodecComponentState state = equipmentState(editor, problem.target());
        JsonElement invalid = JsonParser.parseString(problem.invalid());
        Runnable repair;
        if (problem.path().equals("$")) {
            JsonElement before = state.value().deepCopy();
            state.setValue(invalid);
            repair = () -> state.setValue(before);
        } else {
            JsonElement parent = state.value();
            String[] path = problem.path().split("/");
            for (int index = 0; index < path.length - 1; index++)
                parent = parent.isJsonArray() ? parent.getAsJsonArray().get(Integer.parseInt(path[index])) : parent.getAsJsonObject().get(path[index]);
            JsonElement owner = parent;
            String key = path[path.length - 1];
            JsonElement before = parent.isJsonArray() ? parent.getAsJsonArray().get(Integer.parseInt(key)) : parent.getAsJsonObject().get(key);
            if (parent.isJsonArray()) parent.getAsJsonArray().set(Integer.parseInt(key), invalid);
            else parent.getAsJsonObject().add(key, invalid);
            repair = () -> {
                if (owner.isJsonArray()) owner.getAsJsonArray().set(Integer.parseInt(key), before);
                else owner.getAsJsonObject().add(key, before);
            };
        }
        Map<String, ImString> buffers = (Map<String, ImString>) read(panel, "textBuffers");
        ImString beforeBuffer = buffers.get(problem.buffer());
        ImString source = problem.buffer().isEmpty() ? null : buffers.computeIfAbsent(problem.buffer(), ignored -> new ImString(512));
        String beforeText = source == null ? "" : source.get();
        if (source != null) source.set(invalid.isJsonPrimitive() ? invalid.getAsString() : invalid.toString());
        return () -> {
            repair.run();
            if (source != null) {
                if (beforeBuffer == null) buffers.remove(problem.buffer());
                else source.set(beforeText);
            }
        };
    }

    private static void verifyEquipmentMapPotFailures() throws Exception {
        for (var problem : equipmentMapPotProblems()) {
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(equipmentMapPotOpening(fixture));
                    var values = equipmentMapPotValues();
                    if (problem.path().equals("equip_sound/sound_id") || problem.path().equals("shearing_sound/sound_id")) {
                        values.get("equippable").getAsJsonObject().add(problem.path().split("/")[0],
                            JsonParser.parseString("{\"sound_id\":\"itemlab:custom.sound\",\"range\":-1}"));
                    } else if (problem.target().equals("trim") && problem.path().endsWith("/description")) {
                        values.get("trim").getAsJsonObject().add(problem.path().split("/")[0], inlineTrimValue(fixture, problem.path().startsWith("material/")));
                    } else if (problem.target().equals("provides_trim_material") && problem.path().equals("description")) {
                        values.put(problem.target(), inlineTrimValue(fixture, true));
                    } else if (problem.target().equals("break_sound") && problem.path().equals("sound_id")) {
                        values.put(problem.target(), JsonParser.parseString("{\"sound_id\":\"itemlab:custom.break\",\"range\":0}"));
                    }
                    addEquipmentMapPotPending(editor, values);
                    captureHistory(editor, fixture.client);
                    var valid = equipmentMapPotBuffers(editor);
                    Runnable repair = invalidateEquipmentMapPot(editor, problem);
                    var invalid = equipmentMapPotBuffers(editor);
                    var references = equipmentMapPotReferences(editor);
                    Object panel = read(editor, equipmentMapPotOwner(problem.target()) + "Editor");
                    String field = problem.field();
                    if (problem.target().equals("map_decorations"))
                        field = "row/" + read(((List<?>) read(panel, "decorations")).getLast(), "rowId") + "/" + field;
                    String tab = equipmentMapPotOwner(problem.target()).equals("decoratedPot") ? "DECORATED_POT" : equipmentMapPotOwner(problem.target()).toUpperCase(java.util.Locale.ROOT);
                    var lore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                    var data = dataBuffers((DataEditorPanel) read(editor, "dataEditor"));
                    Object history = read(editor, "lastEditorState");
                    ItemStack opening = editor.draft().copy();
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals(tab)
                        && problem.section().equals(read(panel, "pendingFocusSection")) && field.equals(read(panel, "pendingFocusField"))
                        && !(boolean) read(panel, "focusPendingToolbar"), "Equipment/Map/Pot failure accepted input or used the wrong recovery: " + problem + "/" + read(editor, "status"));
                    require(invalid.equals(equipmentMapPotBuffers(editor)) && lore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                        && data.equals(dataBuffers((DataEditorPanel) read(editor, "dataEditor"))) && history == read(editor, "lastEditorState")
                        && ((Deque<?>) read(editor, "editorUndo")).isEmpty() && !((ItemEditHistory) read(editor, "history")).canUndo()
                        && ItemStack.matches(opening, editor.draft()) && ItemStack.matches(opening, fixture.slots.get(0))
                        && fixture.writes.isEmpty() && fixture.slots.size() == 1, "Equipment/Map/Pot rejection lost models/baselines/history or wrote output: " + problem);
                    var retained = equipmentMapPotReferences(editor);
                    require(references.size() == retained.size(), "Equipment/Map/Pot recovery replaced input collections");
                    for (int index = 0; index < references.size(); index++)
                        require(references.get(index) == retained.get(index), "Equipment/Map/Pot recovery replaced a row/buffer/model: " + problem);
                    if (problem.field().equals("allowed_entities"))
                        require((int) read(read(panel, "allowedEntitiesFocus"), "requestedRow") == (problem.path().endsWith("/1") ? 1 : 0), "Equipment recovery focused the wrong allowed-entity row");
                    if (problem.target().equals("map_decorations"))
                        require((long) read(panel, "focusDecorationRowId") == (long) read(((List<?>) read(panel, "decorations")).getLast(), "rowId"), "Map did not reveal its failing later row");
                    repair.run();
                    require(valid.equals(equipmentMapPotBuffers(editor)), "Equipment/Map/Pot in-place repair lost other fields: " + problem);
                    assertEquipmentMapPotOutput(fixture, verifyActionHistory(fixture, editor, action, "Equipment/Map/Pot repair " + problem), values, Set.of());
                }
            }
        }
    }

    private static void syncEquipmentMapPot(ItemEditor editor, Fixture fixture, String owner) throws Exception {
        if (owner.equals("equipment"))
            invoke(read(editor, "equipmentEditor"), "sync", new Class<?>[]{Minecraft.class, ItemStack.class}, fixture.client, editor.draft());
        else invoke(read(editor, owner + "Editor"), "sync", new Class<?>[]{ItemStack.class}, editor.draft());
    }

    private static void verifyEquipmentMapPotOtherFailures() throws Exception {
        for (String owner : List.of("equipment", "map", "decoratedPot"))
            for (boolean earlier : List.of(true, false))
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                    try (Fixture fixture = new Fixture()) {
                        ItemEditor editor = fixture.editedEditor(equipmentMapPotOpening(fixture));
                        var values = equipmentMapPotValues();
                        addEquipmentMapPotPending(editor, values);
                        for (String other : List.of("equipment", "map", "decoratedPot"))
                            if (!other.equals(owner)) syncEquipmentMapPot(editor, fixture, other);
                        captureHistory(editor, fixture.client);
                        Object history = read(editor, "lastEditorState");
                        ImString source = (ImString) read(((List<?>) read(read(editor, "dataEditor"), "entries")).getFirst(), "value");
                        // Basic/header validation precedes every grouped owner; Data follows all three.
                        if (earlier) ((ImString) read(editor, "itemId")).set("not an item id");
                        else source.set("invalid late integer");
                        var buffers = equipmentMapPotBuffers(editor);
                        ItemStack opening = editor.draft().copy();
                        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                        require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals(earlier ? "NAME" : "DATA")
                            && buffers.equals(equipmentMapPotBuffers(editor)) && history == read(editor, "lastEditorState")
                            && ItemStack.matches(opening, editor.draft()) && ItemStack.matches(opening, fixture.slots.get(0))
                            && fixture.writes.isEmpty() && fixture.slots.size() == 1 && ((Deque<?>) read(editor, "editorUndo")).isEmpty(), "Earlier/later failure lost " + owner);
                        if (earlier) ((ImString) read(editor, "itemId")).set("minecraft:stone");
                        else source.set("42");
                        for (String target : EQUIPMENT_MAP_POT)
                            if (!equipmentMapPotOwner(target).equals(owner))
                                values.put(target, ItemStackCodecs.encodeComponent(fixture.client, dataType(target), opening.get(dataType(target))));
                        assertEquipmentMapPotOutput(fixture, verifyActionHistory(fixture, editor, action, "Equipment/Map/Pot earlier/later " + owner + "/" + earlier), values, Set.of());
                    }
                }
    }

    private static JsonElement generatedEquipmentMapPotValue(String target) {
        return JsonParser.parseString(switch (target) {
            case "equippable" -> "{\"slot\":\"body\"}";
            case "trim" -> "{\"material\":\"minecraft:diamond\",\"pattern\":\"minecraft:coast\"}";
            case "provides_trim_material" -> "\"minecraft:gold\"";
            case "break_sound" -> "\"minecraft:block.stone.break\"";
            case "map_id" -> "99";
            case "map_color" -> "16711935";
            case "map_decorations" ->
                "{\"generated\":{\"type\":\"minecraft:player\",\"x\":10,\"z\":20,\"rotation\":30}}";
            case "pot_decorations" ->
                "[\"minecraft:diamond\",\"minecraft:brick\",\"minecraft:brick\",\"minecraft:brick\"]";
            default -> throw new AssertionError(target);
        });
    }

    private static void verifyEquipmentMapPotConflicts() throws Exception {
        for (String target : EQUIPMENT_MAP_POT)
            for (boolean keepGrouped : List.of(true, false))
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                    try (Fixture fixture = new Fixture()) {
                        ItemEditor editor = fixture.editedEditor(equipmentMapPotOpening(fixture));
                        var values = equipmentMapPotValues();
                        var option = new ComponentOption("minecraft:" + target, target, dataType(target));
                        write(editor, "componentOptions", List.of(option));
                        require(editor.selectComponent(fixture.client, option), "Equipment/Map/Pot component selection failed: " + target);
                        write(editor, "useGenericSelectedComponentEditor", true);
                        addEquipmentMapPotPending(editor, values);
                        ComponentFormPanel form = (ComponentFormPanel) read(editor, "componentForm");
                        JsonElement baseline = form.pendingValue().deepCopy();
                        JsonElement generated = generatedEquipmentMapPotValue(target);
                        form.restorePending(baseline, generated);
                        captureHistory(editor, fixture.client);
                        Object before = invoke(editor, "captureEditorSnapshot", new Class<?>[]{Minecraft.class}, fixture.client);
                        var buffers = equipmentMapPotBuffers(editor);
                        Object history = read(editor, "lastEditorState");
                        ItemStack opening = editor.draft().copy();
                        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                        Object after = invoke(editor, "captureEditorSnapshot", new Class<?>[]{Minecraft.class}, fixture.client);
                        String tab = equipmentMapPotOwner(target).equals("decoratedPot") ? "DECORATED_POT" : equipmentMapPotOwner(target).toUpperCase(java.util.Locale.ROOT);
                        require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals(tab)
                            && (boolean) invoke(before, "sameState", new Class<?>[]{before.getClass()}, after) && buffers.equals(equipmentMapPotBuffers(editor))
                            && form.pendingValue().equals(generated) && baseline.equals(read(form, "loadedValue")) && editor.selectedComponent() == option
                            && (boolean) read(editor, "useGenericSelectedComponentEditor") && history == read(editor, "lastEditorState")
                            && ItemStack.matches(opening, editor.draft()) && ItemStack.matches(opening, fixture.slots.get(0))
                            && fixture.writes.isEmpty() && fixture.slots.size() == 1 && ((Deque<?>) read(editor, "editorUndo")).isEmpty(), "Equipment/Map/Pot conflict lost input or wrote output: " + target);
                        if (keepGrouped) form.discardChanges();
                        else {
                            syncEquipmentMapPot(editor, fixture, equipmentMapPotOwner(target));
                            for (String other : EQUIPMENT_MAP_POT)
                                if (equipmentMapPotOwner(other).equals(equipmentMapPotOwner(target)))
                                    values.put(other, ItemStackCodecs.encodeComponent(fixture.client, dataType(other), opening.get(dataType(other))));
                            values.put(target, generated);
                        }
                        assertEquipmentMapPotOutput(fixture, verifyActionHistory(fixture, editor, action, "Equipment/Map/Pot generated resolution " + target + "/" + keepGrouped), values, Set.of());
                    }
                }
    }

    private static final List<String> MUSIC_COMPASS_PROFILE = List.of("instrument", "jukebox_playable", "note_block_sound", "lodestone_tracker", "profile");

    private static String groupedOwner(String target) {
        return switch (target) {
            case "lodestone_tracker" -> "compass";
            case "profile" -> "playerHead";
            default -> "music";
        };
    }

    private static CodecComponentState groupedState(ItemEditor editor, String target) throws Exception {
        return (CodecComponentState) read(read(editor, groupedOwner(target) + "Editor"), switch (target) {
            case "jukebox_playable" -> "jukeboxPlayable";
            case "note_block_sound" -> "noteBlockSound";
            case "lodestone_tracker" -> "tracker";
            default -> target;
        });
    }

    private static Map<String, JsonElement> groupedValues() {
        Map<String, JsonElement> values = new HashMap<>();
        values.put("instrument", JsonParser.parseString("{\"sound_event\":{\"sound_id\":\"itemlab:custom.sound\",\"range\":32.5},"
            + "\"use_duration\":3.25,\"range\":96.5,\"description\":{\"text\":\"Custom {instrument}\",\"bold\":true,\"color\":\"gold\"}}"));
        values.put("jukebox_playable", JsonParser.parseString("\"minecraft:cat\""));
        // Unlike registered instruments/songs, these two identifier controls accept datapack/custom IDs.
        values.put("note_block_sound", JsonParser.parseString("\"itemlab:custom.note\""));
        values.put("lodestone_tracker", JsonParser.parseString("{\"target\":{\"dimension\":\"itemlab:custom_dimension\",\"pos\":[-12345,2047,29999999]},\"tracked\":false}"));
        values.put("profile", JsonParser.parseString("{\"name\":\"Check_Player\",\"id\":[123456789,-987654321,42,-1],"
            + "\"texture\":\"itemlab:skin\",\"cape\":\"itemlab:cape\",\"elytra\":\"itemlab:elytra\",\"model\":\"wide\","
            + "\"properties\":[{\"name\":\"textures\",\"value\":\"opaque {payload}\\nsecond line\",\"signature\":\"opaque signature\"},"
            + "{\"name\":\"textures\",\"value\":\"second payload\"},{\"name\":\"custom\",\"value\":\"\",\"signature\":\"\"}]}"));
        return values;
    }

    private static ItemStack groupedOpening(Fixture fixture) {
        ItemStack opening = new ItemStack(Items.STONE);
        opening.set(DataComponents.CUSTOM_NAME, Component.literal("Opening name"));
        for (var entry : Map.of("instrument", "\"minecraft:ponder_goat_horn\"", "jukebox_playable", "\"minecraft:13\"",
            "note_block_sound", "\"minecraft:block.note_block.harp\"", "lodestone_tracker", "{\"target\":{\"dimension\":\"minecraft:overworld\",\"pos\":[9,10,11]},\"tracked\":true}",
            "profile", "\"OpeningPlayer\"").entrySet()) {
            ItemStackCodecs.decodeAndSetComponent(fixture.client, opening, dataType(entry.getKey()), JsonParser.parseString(entry.getValue()));
        }
        return opening;
    }

    @SuppressWarnings("unchecked")
    private static void addGroupedPending(ItemEditor editor, Map<String, JsonElement> values) throws Exception {
        Object music = read(editor, "musicEditor");
        write(music, "knownInstrumentIds", fixtureRegistries.lookupOrThrow(Registries.INSTRUMENT).keySet().stream().map(Object::toString).collect(java.util.stream.Collectors.toSet()));
        write(music, "knownJukeboxSongIds", fixtureRegistries.lookupOrThrow(Registries.JUKEBOX_SONG).keySet().stream().map(Object::toString).collect(java.util.stream.Collectors.toSet()));
        write(music, "knownSoundIds", BuiltInRegistries.SOUND_EVENT.keySet().stream().map(Object::toString).collect(java.util.stream.Collectors.toSet()));
        JsonElement instrument = values.get("instrument");
        groupedState(editor, "instrument").setPresent(true);
        ((ImBoolean) read(music, "customInstrument")).set(instrument.isJsonObject());
        if (instrument.isJsonObject()) {
            write(music, "customInstrumentValue", instrument.deepCopy());
            ((ImString) read(music, "instrumentDescriptionSource")).set(instrument.getAsJsonObject().get("description").toString());
        } else ((ImString) read(music, "registeredInstrumentId")).set(" " + instrument.getAsString() + " ");
        for (String target : MUSIC_COMPASS_PROFILE) {
            if (!target.equals("instrument")) {
                CodecComponentState state = groupedState(editor, target);
                state.setPresent(true);
                state.setValue(values.get(target));
            }
            write(read(editor, groupedOwner(target) + "Editor"), "dirty", true);
        }
        invoke(read(editor, "playerHeadEditor"), "resetPropertyRowIds", new Class<?>[0]);
        ((Map<String, ImString>) read(music, "textBuffers")).put("music/jukebox_song", new ImString(values.get("jukebox_playable").getAsString(), 512));
        ((Map<String, ImString>) read(music, "textBuffers")).put("music/note_block_sound", new ImString(values.get("note_block_sound").getAsString(), 512));
    }

    /**
     * Stored codec baselines, raw UI models/buffers and stable property IDs, not just the decoded candidate.
     */
    @SuppressWarnings("unchecked")
    private static List<Object> groupedBuffers(ItemEditor editor) throws Exception {
        List<Object> snapshot = new ArrayList<>();
        for (String target : MUSIC_COMPASS_PROFILE) {
            CodecComponentState state = groupedState(editor, target);
            snapshot.add(List.of(state.present(), state.value().deepCopy(), read(state, "nbtValue") == null ? "" : read(state, "nbtValue")));
        }
        Object music = read(editor, "musicEditor");
        snapshot.add(List.of(((ImBoolean) read(music, "customInstrument")).get(), ((ImString) read(music, "registeredInstrumentId")).get(),
            ((JsonObject) read(music, "customInstrumentValue")).deepCopy(), ((JsonObject) read(music, "preservedCustomSoundEvent")).deepCopy(),
            ((ImString) read(music, "instrumentDescriptionSource")).get()));
        for (String owner : List.of("music", "compass", "playerHead")) {
            Object panel = read(editor, owner + "Editor");
            Map<String, String> texts = new HashMap<>();
            ((Map<String, ImString>) read(panel, "textBuffers")).forEach((key, value) -> texts.put(key, value.get()));
            snapshot.add(List.of(read(panel, "dirty"), Map.copyOf(texts)));
        }
        snapshot.add(List.copyOf((List<?>) read(read(editor, "playerHeadEditor"), "propertyRowIds")));
        return snapshot;
    }

    private static List<Object> groupedModelReferences(ItemEditor editor) throws Exception {
        List<Object> references = new ArrayList<>();
        for (String target : MUSIC_COMPASS_PROFILE) references.add(groupedState(editor, target).value());
        Object music = read(editor, "musicEditor");
        references.add(read(music, "customInstrumentValue"));
        references.add(read(music, "preservedCustomSoundEvent"));
        references.add(read(read(editor, "playerHeadEditor"), "propertyRowIds"));
        for (JsonElement property : groupedState(editor, "profile").value().getAsJsonObject().getAsJsonArray("properties"))
            references.add(property);
        return references;
    }

    private static void assertGroupedOutput(Fixture fixture, ItemStack saved, Map<String, JsonElement> values, Set<String> removed) {
        assertCombined(saved);
        ItemStack expected = new ItemStack(Items.STONE);
        for (String target : MUSIC_COMPASS_PROFILE) {
            if (!removed.contains(target))
                ItemStackCodecs.decodeAndSetComponent(fixture.client, expected, dataType(target), values.get(target));
            require(Objects.equals(saved.get(dataType(target)), expected.get(dataType(target))), "Wrong native grouped output: " + target);
        }
    }

    private static void verifyMusicCompassProfileCommits() throws Exception {
        for (String representation : List.of("registered", "custom_registered_sound", "custom_variable_sound", "custom_zero_sound", "custom_negative_sound", "custom_fixed_sound",
            "no_target_tracked", "no_target_untracked", "vanilla_dimension", "custom_dimension", "name_profile", "uuid_profile", "empty_profile", "slim_profile")) {
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    Map<String, JsonElement> values = groupedValues();
                    JsonObject instrument = values.get("instrument").getAsJsonObject();
                    switch (representation) {
                        case "registered" ->
                            values.put("instrument", JsonParser.parseString("\"minecraft:sing_goat_horn\""));
                        case "custom_registered_sound" ->
                            instrument.addProperty("sound_event", "minecraft:item.goat_horn.sound.1");
                        case "custom_variable_sound" -> instrument.getAsJsonObject("sound_event").remove("range");
                        case "custom_zero_sound" -> instrument.getAsJsonObject("sound_event").addProperty("range", 0);
                        case "custom_negative_sound" ->
                            instrument.getAsJsonObject("sound_event").addProperty("range", -1);
                        case "no_target_tracked" ->
                            values.put("lodestone_tracker", JsonParser.parseString("{\"tracked\":true}"));
                        case "no_target_untracked" ->
                            values.put("lodestone_tracker", JsonParser.parseString("{\"tracked\":false}"));
                        case "vanilla_dimension" ->
                            values.put("lodestone_tracker", JsonParser.parseString("{\"target\":{\"dimension\":\"minecraft:the_nether\",\"pos\":[1,-64,-2]},\"tracked\":true}"));
                        case "name_profile" ->
                            values.put("profile", JsonParser.parseString("{\"name\":\"Check_Player\"}"));
                        case "uuid_profile" ->
                            values.put("profile", JsonParser.parseString("{\"id\":[123456789,-987654321,42,-1]}"));
                        case "empty_profile" -> values.put("profile", JsonParser.parseString("{}"));
                        case "slim_profile" -> values.get("profile").getAsJsonObject().addProperty("model", "slim");
                        default -> {
                        }
                    }
                    ItemEditor editor = fixture.editedEditor(groupedOpening(fixture));
                    addGroupedPending(editor, values);
                    var before = groupedBuffers(editor);
                    var preview = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                    require(preview.succeeded() && before.equals(groupedBuffers(editor)), "Grouped preview mutated its inputs: " + representation + "/" + preview.failure());
                    assertGroupedOutput(fixture, verifyActionHistory(fixture, editor, action, "Music/Compass/Profile " + representation), values, Set.of());
                }
            }
        }
        // Removal ignores inactive invalid sources, including an unfinished custom description.
        for (String target : MUSIC_COMPASS_PROFILE) {
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(groupedOpening(fixture));
                    var values = groupedValues();
                    addGroupedPending(editor, values);
                    CodecComponentState state = groupedState(editor, target);
                    state.setPresent(false);
                    state.setValue(JsonParser.parseString("\"inactive invalid source\""));
                    if (target.equals("instrument"))
                        ((ImString) read(read(editor, "musicEditor"), "instrumentDescriptionSource")).set("{");
                    assertGroupedOutput(fixture, verifyActionHistory(fixture, editor, action, "Grouped removal " + target), values, Set.of(target));
                }
            }
        }
    }

    private record GroupedProblem(String target, String path, String invalid, String section, String field,
                                  String buffer) {
    }

    private static List<GroupedProblem> groupedProblems() {
        return List.of(
            new GroupedProblem("instrument", "$registered", "", "instrument", "instrument_id", ""),
            new GroupedProblem("instrument", "$registered", "not an id", "instrument", "instrument_id", ""),
            new GroupedProblem("instrument", "$registered", "itemlab:unknown_instrument", "instrument", "instrument_id", ""),
            new GroupedProblem("instrument", "$description", "{\n  \"text\":", "instrument", "description", ""),
            new GroupedProblem("instrument", "$description", "{\"text\":{}}", "instrument", "description", ""),
            new GroupedProblem("instrument", "sound_event/sound_id", "\"not a sound id\"", "instrument", "sound", "music/custom_sound_id"),
            new GroupedProblem("instrument", "sound_event", "\"itemlab:missing_sound\"", "instrument", "sound", "music/registered_sound"),
            new GroupedProblem("instrument", "use_duration", "0", "instrument", "use_duration", ""),
            new GroupedProblem("instrument", "use_duration", "-1", "instrument", "use_duration", ""),
            new GroupedProblem("instrument", "range", "0", "instrument", "range", ""),
            new GroupedProblem("instrument", "range", "-1", "instrument", "range", ""),
            new GroupedProblem("instrument", "use_duration", "NaN", "instrument", "use_duration", ""),
            new GroupedProblem("instrument", "range", "Infinity", "instrument", "range", ""),
            new GroupedProblem("jukebox_playable", "$", "\"\"", "jukebox_playable", "song", "music/jukebox_song"),
            new GroupedProblem("jukebox_playable", "$", "\"not a song id\"", "jukebox_playable", "song", "music/jukebox_song"),
            new GroupedProblem("jukebox_playable", "$", "\"itemlab:unknown_song\"", "jukebox_playable", "song", "music/jukebox_song"),
            new GroupedProblem("note_block_sound", "$", "\"not a sound id\"", "note_block_sound", "sound", "music/note_block_sound"),
            new GroupedProblem("lodestone_tracker", "target/dimension", "\"not a dimension id\"", "tracker", "dimension", "lodestone/dimension"),
            new GroupedProblem("lodestone_tracker", "target/pos", "[1,2]", "tracker", "$enabled", ""),
            new GroupedProblem("profile", "name", "\"A_name_that_is_far_too_long\"", "profile", "name", "profile_name"),
            new GroupedProblem("profile", "id", "\"invalid uuid source\"", "profile", "uuid", "profile_uuid"),
            new GroupedProblem("profile", "texture", "\"not a texture id\"", "profile", "texture", "skin_texture"),
            new GroupedProblem("profile", "cape", "\"not a cape id\"", "profile", "cape", "cape_texture"),
            new GroupedProblem("profile", "elytra", "\"not an elytra id\"", "profile", "elytra", "elytra_texture"),
            new GroupedProblem("profile", "model", "\"not_a_model\"", "profile", "$enabled", ""),
            new GroupedProblem("profile", "properties/1/name", "null", "profile", "property/1/name", ""),
            new GroupedProblem("profile", "properties/1/value", "null", "profile", "$enabled", ""),
            new GroupedProblem("profile", "properties/0/signature", "{}", "profile", "$enabled", ""));
    }

    /**
     * Changes the existing row/object; returned repair restores that same model, not a panel reload.
     */
    @SuppressWarnings("unchecked")
    private static Runnable invalidateGrouped(ItemEditor editor, GroupedProblem problem) throws Exception {
        Object panel = read(editor, groupedOwner(problem.target()) + "Editor");
        if (problem.path().startsWith("$registered") || problem.path().equals("$description")) {
            ImString source = (ImString) read(panel, problem.path().equals("$registered") ? "registeredInstrumentId" : "instrumentDescriptionSource");
            String before = source.get();
            ImBoolean customControl = (ImBoolean) read(panel, "customInstrument");
            boolean custom = customControl.get();
            if (problem.path().equals("$registered")) customControl.set(false);
            source.set(problem.invalid());
            return () -> {
                source.set(before);
                customControl.set(custom);
            };
        }
        CodecComponentState state = groupedState(editor, problem.target());
        JsonElement invalid = switch (problem.invalid()) {
            case "NaN" -> new com.google.gson.JsonPrimitive(Double.NaN);
            case "Infinity" -> new com.google.gson.JsonPrimitive(Double.POSITIVE_INFINITY);
            default -> JsonParser.parseString(problem.invalid());
        };
        Runnable repair;
        if (problem.path().equals("$")) {
            JsonElement before = state.value().deepCopy();
            state.setValue(invalid);
            repair = () -> state.setValue(before);
        } else {
            JsonElement value = problem.target().equals("instrument") ? (JsonObject) read(panel, "customInstrumentValue") : state.value();
            String[] path = problem.path().split("/");
            for (int index = 0; index < path.length - 1; index++)
                value = value.isJsonArray()
                    ? value.getAsJsonArray().get(Integer.parseInt(path[index])) : value.getAsJsonObject().get(path[index]);
            JsonObject parent = value.getAsJsonObject();
            String key = path[path.length - 1];
            JsonElement before = parent.get(key);
            parent.add(key, invalid);
            repair = () -> parent.add(key, before);
        }
        Map<String, ImString> buffers = (Map<String, ImString>) read(panel, "textBuffers");
        ImString buffer = problem.buffer().isEmpty() ? null : buffers.computeIfAbsent(problem.buffer(), ignored -> new ImString(512));
        String beforeText = buffer == null ? "" : buffer.get();
        if (buffer != null) buffer.set(invalid.isJsonPrimitive() ? invalid.getAsString() : invalid.toString());
        return () -> {
            repair.run();
            if (buffer != null) {
                if (beforeText.isEmpty()) buffers.remove(problem.buffer());
                else buffer.set(beforeText);
            }
        };
    }

    private static void verifyMusicCompassProfileFailures() throws Exception {
        for (GroupedProblem problem : groupedProblems()) {
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(groupedOpening(fixture));
                    var values = groupedValues();
                    if (problem.target().equals("jukebox_playable") && problem.invalid().contains("unknown_song")) {
                        // Native SoundEvent permits signed ranges; that valid earlier field must not steal recovery.
                        values.get("instrument").getAsJsonObject().getAsJsonObject("sound_event").addProperty("range", -1);
                    }
                    addGroupedPending(editor, values);
                    captureHistory(editor, fixture.client);
                    Object panel = read(editor, groupedOwner(problem.target()) + "Editor");
                    var valid = groupedBuffers(editor);
                    Runnable repair = invalidateGrouped(editor, problem);
                    Object descriptionEditor = read(read(editor, "musicEditor"), "instrumentDescriptionEditor");
                    write(descriptionEditor, "cursorByteOffset", 8);
                    write(descriptionEditor, "cursorLine", 2);
                    write(descriptionEditor, "cursorColumn", 4);
                    var invalid = groupedBuffers(editor);
                    var references = groupedModelReferences(editor);
                    Object history = read(editor, "lastEditorState");
                    ItemStack before = editor.draft().copy();
                    var lore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                    var data = dataBuffers((DataEditorPanel) read(editor, "dataEditor"));
                    String tab = groupedOwner(problem.target()).equals("playerHead") ? "PLAYER_HEAD" : groupedOwner(problem.target()).toUpperCase(java.util.Locale.ROOT);
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals(tab)
                        && problem.section().equals(read(panel, "pendingFocusSection")) && problem.field().equals(read(panel, "pendingFocusField"))
                        && !(boolean) read(panel, "focusPendingToolbar"), "Grouped failure accepted input or used the wrong field: " + problem + "/" + read(editor, "status"));
                    require(invalid.equals(groupedBuffers(editor)) && lore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                            && data.equals(dataBuffers((DataEditorPanel) read(editor, "dataEditor")))
                            && history == read(editor, "lastEditorState") && ((Deque<?>) read(editor, "editorUndo")).isEmpty()
                            && !((ItemEditHistory) read(editor, "history")).canUndo()
                            && ItemStack.matches(before, editor.draft()) && ItemStack.matches(before, fixture.slots.get(0))
                            && fixture.writes.isEmpty() && fixture.slots.size() == 1,
                        "Grouped rejection changed inputs/baselines/history or wrote output: " + problem);
                    var retainedReferences = groupedModelReferences(editor);
                    for (int index = 0; index < references.size(); index++)
                        require(references.get(index) == retainedReferences.get(index), "Grouped recovery replaced a model/property row: " + problem);
                    require((int) read(descriptionEditor, "cursorByteOffset") == 8 && (int) read(descriptionEditor, "cursorLine") == 2
                        && (int) read(descriptionEditor, "cursorColumn") == 4
                        && (!problem.path().equals("$description") || (boolean) read(descriptionEditor, "focusRequested")), "Grouped recovery reset the code cursor/focus");
                    repair.run();
                    require(valid.equals(groupedBuffers(editor)), "Grouped in-place repair lost the valid model: " + problem);
                    assertGroupedOutput(fixture, verifyActionHistory(fixture, editor, action, "Grouped repair " + problem), values, Set.of());
                }
            }
        }
    }

    private static void verifyMusicCompassProfileOtherFailures() throws Exception {
        for (String owner : List.of("music", "compass", "playerHead")) {
            for (String earlierOrLater : List.of("earlier", "later")) {
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                    try (Fixture fixture = new Fixture()) {
                        ItemEditor editor = fixture.editedEditor(groupedOpening(fixture));
                        var values = groupedValues();
                        addGroupedPending(editor, values);
                        // Keep only the named grouped participant pending to locate it relative to the failing owner.
                        for (String other : List.of("music", "compass", "playerHead"))
                            if (!other.equals(owner)) {
                                invoke(read(editor, other + "Editor"), "sync", new Class<?>[]{Minecraft.class, ItemStack.class}, fixture.client, editor.draft());
                            }
                        captureHistory(editor, fixture.client);
                        Object history = read(editor, "lastEditorState");
                        ImString source;
                        RichTextLine name = null;
                        if (earlierOrLater.equals("earlier")) {
                            name = text(INVALID_MINI_MESSAGE, TextInputFormat.MINI_MESSAGE);
                            write(read(editor, "loreEditor"), "displayName", name);
                            source = name.editorSource(false, false);
                        } else
                            source = (ImString) read(((List<?>) read(read(editor, "dataEditor"), "entries")).getFirst(), "value");
                        if (name == null) source.set("invalid late integer");
                        var before = groupedBuffers(editor);
                        ItemStack opening = editor.draft().copy();
                        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                        require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals(name == null ? "DATA" : "NAME")
                                && before.equals(groupedBuffers(editor)) && history == read(editor, "lastEditorState")
                                && ItemStack.matches(opening, editor.draft()) && ItemStack.matches(opening, fixture.slots.get(0))
                                && fixture.writes.isEmpty() && fixture.slots.size() == 1 && ((Deque<?>) read(editor, "editorUndo")).isEmpty(),
                            "Earlier/later failure lost pending " + owner + "/" + earlierOrLater + ": " + read(editor, "requestedEditorTab") + "/" + read(editor, "status"));
                        if (name == null) source.set("42");
                        else write(read(editor, "loreEditor"), "displayName", text("&6&lCombined name"));
                        ItemStack saved = verifyActionHistory(fixture, editor, action, owner + "/" + earlierOrLater + " repair");
                        assertCombined(saved);
                        ItemStack expected = opening.copy();
                        for (String target : MUSIC_COMPASS_PROFILE)
                            if (groupedOwner(target).equals(owner)) {
                                ItemStackCodecs.decodeAndSetComponent(fixture.client, expected, dataType(target), values.get(target));
                            }
                        for (String target : MUSIC_COMPASS_PROFILE)
                            require(Objects.equals(saved.get(dataType(target)), expected.get(dataType(target))), "Retained grouped owner lost output: " + owner);
                    }
                }
            }
        }
    }

    private static JsonElement generatedGroupedValue(String target) {
        return JsonParser.parseString(switch (target) {
            case "instrument" -> "\"minecraft:dream_goat_horn\"";
            case "jukebox_playable" -> "\"minecraft:blocks\"";
            case "note_block_sound" -> "\"itemlab:generated.note\"";
            case "lodestone_tracker" ->
                "{\"target\":{\"dimension\":\"minecraft:the_end\",\"pos\":[3,4,5]},\"tracked\":true}";
            case "profile" -> "{\"name\":\"GeneratedPlayer\"}";
            default -> throw new AssertionError(target);
        });
    }

    private static void verifyMusicCompassProfileConflicts() throws Exception {
        for (String target : MUSIC_COMPASS_PROFILE) {
            for (boolean keepGrouped : List.of(true, false)) {
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                    try (Fixture fixture = new Fixture()) {
                        ItemEditor editor = fixture.editedEditor(groupedOpening(fixture));
                        var values = groupedValues();
                        var option = new ComponentOption("minecraft:" + target, target, dataType(target));
                        write(editor, "componentOptions", List.of(option));
                        require(editor.selectComponent(fixture.client, option), "Grouped component selection failed: " + target);
                        write(editor, "useGenericSelectedComponentEditor", true);
                        addGroupedPending(editor, values);
                        ComponentFormPanel form = (ComponentFormPanel) read(editor, "componentForm");
                        JsonElement baseline = form.pendingValue().deepCopy();
                        JsonElement generated = generatedGroupedValue(target);
                        form.restorePending(baseline, generated);
                        captureHistory(editor, fixture.client);
                        Object before = invoke(editor, "captureEditorSnapshot", new Class<?>[]{Minecraft.class}, fixture.client);
                        var buffers = groupedBuffers(editor);
                        Object history = read(editor, "lastEditorState");
                        ItemStack opening = editor.draft().copy();
                        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                        Object after = invoke(editor, "captureEditorSnapshot", new Class<?>[]{Minecraft.class}, fixture.client);
                        String tab = groupedOwner(target).equals("playerHead") ? "PLAYER_HEAD" : groupedOwner(target).toUpperCase(java.util.Locale.ROOT);
                        require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals(tab)
                                && (boolean) invoke(before, "sameState", new Class<?>[]{before.getClass()}, after)
                                && buffers.equals(groupedBuffers(editor)) && form.pendingValue().equals(generated)
                                && baseline.equals(read(form, "loadedValue")) && editor.selectedComponent() == option
                                && (boolean) read(editor, "useGenericSelectedComponentEditor") && history == read(editor, "lastEditorState")
                                && ItemStack.matches(opening, editor.draft()) && ItemStack.matches(opening, fixture.slots.get(0))
                                && fixture.writes.isEmpty() && fixture.slots.size() == 1 && ((Deque<?>) read(editor, "editorUndo")).isEmpty(),
                            "Grouped/generated conflict lost state or wrote output: " + target);
                        if (keepGrouped) form.discardChanges();
                        else {
                            invoke(read(editor, groupedOwner(target) + "Editor"), "sync", new Class<?>[]{Minecraft.class, ItemStack.class}, fixture.client, opening);
                            // Discarding Music discards all three owned components, not just the generated field's target.
                            for (String other : MUSIC_COMPASS_PROFILE)
                                if (groupedOwner(other).equals(groupedOwner(target))) {
                                    values.put(other, ItemStackCodecs.encodeComponent(fixture.client, dataType(other), opening.get(dataType(other))));
                                }
                            values.put(target, generated);
                        }
                        assertGroupedOutput(fixture, verifyActionHistory(fixture, editor, action, "Grouped/generated " + target + "/" + keepGrouped), values, Set.of());
                    }
                }
            }
        }
    }

    private static final List<String> DATA_TARGETS = List.of("custom_data", "entity_data", "bucket_entity_data",
        "block_entity_data", "block_state", "debug_stick_state");

    private static DataComponentType<?> dataType(String target) {
        return BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse("minecraft:" + target));
    }

    private static boolean typedDataTarget(String target) {
        return target.equals("entity_data") || target.equals("block_entity_data");
    }

    private static List<String> dataModes(String target) {
        return target.equals("block_state") || target.equals("debug_stick_state") ? List.of("properties") : List.of("tree", "snbt");
    }

    private static CompoundTag dataPayload() throws Exception {
        return TagParser.parseCompoundFully("{commit_check:42,b:1b,s:2s,i:3,l:9007199254740993L,f:3.5f,d:4.5d,"
            + "ba:[B;1b,2b],ia:[I;3,4],la:[L;5L,9007199254740993L],nested:{answer:42,list:[{n:1b},{n:2b}]},text:\"literal {test}\"}");
    }

    private static ItemStack dataTargetOpening() throws Exception {
        ItemStack opening = new ItemStack(Items.OAK_STAIRS);
        opening.set(DataComponents.CUSTOM_NAME, Component.literal("Opening name"));
        opening.set(DataComponents.CUSTOM_DATA, CustomData.of(TagParser.parseCompoundFully("{commit_check:42,untouched:9L}")));
        opening.set(DataComponents.ENTITY_DATA, TypedEntityData.of(BuiltInRegistries.ENTITY_TYPE.get(Identifier.parse("minecraft:pig")).orElseThrow().value(), TagParser.parseCompoundFully("{baseline:1b}")));
        opening.set(DataComponents.BUCKET_ENTITY_DATA, CustomData.of(TagParser.parseCompoundFully("{baseline:2L}")));
        opening.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(BuiltInRegistries.BLOCK_ENTITY_TYPE.get(Identifier.parse("minecraft:chest")).orElseThrow().value(), TagParser.parseCompoundFully("{baseline:3.5f}")));
        opening.set(DataComponents.BLOCK_STATE, new BlockItemStateProperties(Map.of("facing", "north", "half", "bottom")));
        opening.set(DataComponents.DEBUG_STICK_STATE, new DebugStickState(Map.of(
            BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.OAK_LOG), Blocks.OAK_LOG.getStateDefinition().getProperty("axis"))));
        return opening;
    }

    private static void setExpectedData(ItemStack stack, String target, boolean empty, boolean disabled) throws Exception {
        CompoundTag tag = empty ? new CompoundTag() : dataPayload();
        switch (target) {
            case "custom_data", "bucket_entity_data" -> {
                DataComponentType<CustomData> type = target.equals("custom_data") ? DataComponents.CUSTOM_DATA : DataComponents.BUCKET_ENTITY_DATA;
                if (empty) stack.remove(type);
                else stack.set(type, CustomData.of(tag));
            }
            case "entity_data" -> {
                if (disabled) stack.remove(DataComponents.ENTITY_DATA);
                else
                    stack.set(DataComponents.ENTITY_DATA, TypedEntityData.of(BuiltInRegistries.ENTITY_TYPE.get(Identifier.parse("minecraft:cow")).orElseThrow().value(), tag));
            }
            case "block_entity_data" -> {
                if (disabled) stack.remove(DataComponents.BLOCK_ENTITY_DATA);
                else
                    stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(BuiltInRegistries.BLOCK_ENTITY_TYPE.get(Identifier.parse("minecraft:barrel")).orElseThrow().value(), tag));
            }
            case "block_state" -> {
                if (empty) stack.remove(DataComponents.BLOCK_STATE);
                else
                    stack.set(DataComponents.BLOCK_STATE, new BlockItemStateProperties(Map.of("facing", "west", "half", "top", "opaque_property", "keep")));
            }
            case "debug_stick_state" -> {
                if (empty) stack.remove(DataComponents.DEBUG_STICK_STATE);
                else stack.set(DataComponents.DEBUG_STICK_STATE, new DebugStickState(Map.of(
                    BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.OAK_LOG), Blocks.OAK_LOG.getStateDefinition().getProperty("axis"),
                    BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.OAK_STAIRS), Blocks.OAK_STAIRS.getStateDefinition().getProperty("facing"))));
            }
            default -> throw new AssertionError(target);
        }
    }

    private static Object dataProperty(String key, String value) throws Exception {
        var constructor = Class.forName("dev.nineofgaming.itemlab.editor.panel.DataEditorPanel$PropertyRow")
            .getDeclaredConstructor(String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(key, value);
    }

    @SuppressWarnings("unchecked")
    private static DataEditorPanel addDataTargetPending(ItemEditor editor, String target, String mode, boolean empty) throws Exception {
        DataEditorPanel data = (DataEditorPanel) read(editor, "dataEditor");
        // Resolve the fixture's initial custom-data representation explicitly before retargeting.
        data.sync(editor.draft());
        data.selectComponent(dataType(target), editor.draft());
        require(data.pendingComponentType() == dataType(target), "Data selection was blocked or chose the wrong target");
        write(data, "snbtMode", mode.equals("snbt"));
        if (target.equals("entity_data")) {
            write(data, "entityDataPresent", true);
            ((ImString) read(data, "entityType")).set("minecraft:cow");
        } else if (target.equals("block_entity_data")) {
            write(data, "blockEntityDataPresent", true);
            ((ImString) read(data, "blockEntityType")).set("minecraft:barrel");
        }
        CompoundTag payload = empty ? new CompoundTag() : dataPayload();
        invoke(data, "loadTree", new Class<?>[]{CompoundTag.class}, payload);
        ((ImString) read(data, "snbtSource")).set(payload.toString());
        List<Object> properties = (List<Object>) read(data, "propertyRows");
        properties.clear();
        if (!empty && target.equals("block_state")) {
            properties.add(dataProperty(" facing ", " west "));
            properties.add(dataProperty("half", "top"));
            properties.add(dataProperty("opaque_property", "keep"));
        } else if (!empty && target.equals("debug_stick_state")) {
            properties.add(dataProperty("oak_log", "axis"));
            properties.add(dataProperty("minecraft:oak_stairs", "facing"));
        }
        write(data, "dirty", true);
        invoke(data, "clearFocusRequests", new Class<?>[0]);
        return data;
    }

    /**
     * Exact raw model state, not only HistoryState's intentionally semantic equality.
     */
    private static List<Object> dataBuffers(DataEditorPanel data) throws Exception {
        Object state = data.historyState();
        return List.of(read(state, "target"), read(state, "snbtMode"), read(state, "snbtSource"), read(state, "entries"),
            read(state, "entityDataPresent"), read(state, "entityType"), read(state, "blockEntityDataPresent"),
            read(state, "blockEntityType"), read(state, "properties"));
    }

    private static void assertDataTargetOutput(ItemStack opening, ItemStack saved, String target, boolean empty, boolean disabled) throws Exception {
        assertCombinedPanels(saved, Items.OAK_STAIRS);
        ItemStack expected = opening.copy();
        setExpectedData(expected, target, empty, disabled);
        for (String component : DATA_TARGETS) {
            DataComponentType<?> type = dataType(component);
            require(Objects.equals(saved.get(type), expected.get(type)),
                "Data output lost typed payload/metadata, removal/empty presence or an inactive target: " + target + "/" + component);
        }
    }

    private static void verifyDataTargetCommits() throws Exception {
        for (String target : DATA_TARGETS) {
            for (String mode : dataModes(target)) {
                for (String value : typedDataTarget(target) ? List.of("payload", "empty", "disabled_invalid") : List.of("payload", "empty")) {
                    for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                        try (Fixture fixture = new Fixture()) {
                            ItemStack opening = dataTargetOpening();
                            ItemEditor editor = fixture.editedEditor(opening);
                            boolean empty = !value.equals("payload");
                            boolean disabled = value.equals("disabled_invalid");
                            DataEditorPanel data = addDataTargetPending(editor, target, mode, empty);
                            if (disabled) {
                                ((ImString) read(data, target.equals("entity_data") ? "entityType" : "blockEntityType")).set("not a type id");
                                write(data, target.equals("entity_data") ? "entityDataPresent" : "blockEntityDataPresent", false);
                                invoke(data, "loadTree", new Class<?>[]{CompoundTag.class}, TagParser.parseCompoundFully("{id:\"reserved\",invalid:42}"));
                                Object node = dataNode(data, "invalid");
                                ((ImString) read(node, "value")).set("not an integer");
                                ((ImString) read(data, "snbtSource")).set("{");
                            }
                            var before = dataBuffers(data);
                            var prepared = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                            require(prepared.succeeded() && before.equals(dataBuffers(data)), "Data preflight modified raw input: " + target + "/" + mode);
                            assertDataTargetOutput(opening, verifyActionHistory(fixture, editor, action, "Data " + target + "/" + mode + "/" + value), target, empty, disabled);
                            require(data.pendingComponentType() == dataType(target) && !(boolean) read(data, "focusMetadataType")
                                    && !(boolean) read(read(data, "codeEditor"), "focusRequested") && (long) read(data, "focusNodeId") == 0L
                                    && (long) read(data, "focusPropertyRowId") == 0L,
                                "Data commit retargeted the panel or left stale recovery requests");
                        }
                    }
                }
            }
        }
    }

    private static Object dataNode(Object parent, String key) throws Exception {
        List<?> nodes = (List<?>) read(parent, parent instanceof DataEditorPanel ? "entries" : "children");
        for (Object node : nodes) {
            if (((ImString) read(node, "key")).get().equals(key)) return node;
        }
        throw new AssertionError("Missing Data fixture node: " + key);
    }

    private static List<String> dataProblems(String target, String mode) {
        List<String> problems = new ArrayList<>();
        if (mode.equals("properties")) {
            problems.addAll(target.equals("block_state") ? List.of("blank_key", "duplicate", "blank_value", "bad_value")
                : List.of("blank_key", "block_id", "unknown_block", "duplicate", "blank_value", "bad_value"));
        } else {
            problems.addAll(mode.equals("snbt") ? List.of("malformed", "non_compound")
                : List.of("number", "blank_key", "duplicate", "nested_number", "mixed_list"));
            if (typedDataTarget(target))
                problems.addAll(List.of("reserved_id", "metadata_blank", "metadata_id", "metadata_unknown"));
        }
        return problems;
    }

    @SuppressWarnings("unchecked")
    private static void verifyDataTargetFailures() throws Exception {
        for (String target : DATA_TARGETS) {
            for (String mode : dataModes(target)) {
                for (String problem : dataProblems(target, mode)) {
                    for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                        try (Fixture fixture = new Fixture()) {
                            ItemStack opening = dataTargetOpening();
                            ItemEditor editor = fixture.editedEditor(opening);
                            DataEditorPanel data = addDataTargetPending(editor, target, mode, false);
                            captureHistory(editor, fixture.client);
                            var validBuffers = dataBuffers(data);
                            Object focused = null;
                            Object changed = null;
                            String originalKey = "";
                            String originalValue = "";
                            Object originalType = null;
                            List<?> originalChildren = List.of();
                            String focusedField = "";
                            Object nested = null;
                            if (problem.startsWith("metadata_")) {
                                ((ImString) read(data, target.equals("entity_data") ? "entityType" : "blockEntityType"))
                                    .set(problem.equals("metadata_blank") ? " " : problem.equals("metadata_id") ? "not a type id" : "itemlab:unknown_type");
                            } else if (mode.equals("snbt")) {
                                ((ImString) read(data, "snbtSource")).set(problem.equals("malformed") ? "{\n nested: {"
                                    : problem.equals("non_compound") ? "[1,2]" : "{id:\"minecraft:cow\"}");
                            } else if (mode.equals("properties")) {
                                List<?> rows = (List<?>) read(data, "propertyRows");
                                focused = rows.get(1);
                                changed = focused;
                                originalKey = ((ImString) read(changed, "key")).get();
                                originalValue = ((ImString) read(changed, "value")).get();
                                focusedField = problem.equals("blank_value") || problem.equals("bad_value") ? "VALUE" : "KEY";
                                if (problem.equals("duplicate")) {
                                    ((ImString) read(focused, "key")).set(target.equals("block_state") ? "facing" : "minecraft:oak_log");
                                    focused = rows.getFirst();
                                } else if (focusedField.equals("KEY")) {
                                    ((ImString) read(focused, "key")).set(problem.equals("blank_key") ? " " : problem.equals("block_id") ? "not a block id" : "itemlab:unknown_block");
                                } else
                                    ((ImString) read(focused, "value")).set(problem.equals("blank_value") ? " " : "not a value");
                            } else {
                                focused = dataNode(data, "i");
                                changed = focused;
                                originalKey = ((ImString) read(changed, "key")).get();
                                originalValue = ((ImString) read(changed, "value")).get();
                                focusedField = problem.equals("blank_key") || problem.equals("duplicate") || problem.equals("reserved_id") ? "KEY" : "VALUE";
                                switch (problem) {
                                    case "number" -> ((ImString) read(focused, "value")).set("not an integer");
                                    case "blank_key", "reserved_id" ->
                                        ((ImString) read(focused, "key")).set(problem.equals("blank_key") ? " " : "id");
                                    case "duplicate" -> {
                                        ((ImString) read(focused, "key")).set("b");
                                        focused = dataNode(data, "b");
                                    }
                                    case "nested_number" -> {
                                        nested = dataNode(data, "nested");
                                        focused = dataNode(nested, "answer");
                                        changed = focused;
                                        originalKey = ((ImString) read(changed, "key")).get();
                                        originalValue = ((ImString) read(changed, "value")).get();
                                        ((ImString) read(focused, "value")).set("not an integer");
                                    }
                                    case "mixed_list" -> {
                                        nested = dataNode(data, "nested");
                                        Object list = dataNode(nested, "list");
                                        focused = ((List<?>) read(list, "children")).get(1);
                                        changed = focused;
                                        originalKey = ((ImString) read(changed, "key")).get();
                                        originalValue = ((ImString) read(changed, "value")).get();
                                        originalType = read(changed, "type");
                                        originalChildren = List.copyOf((List<?>) read(changed, "children"));
                                        Object intType = java.util.Arrays.stream(read(focused, "type").getClass().getEnumConstants())
                                            .filter(value -> value.toString().equals("INT")).findFirst().orElseThrow();
                                        write(focused, "type", intType);
                                        ((List<?>) read(focused, "children")).clear();
                                        ((ImString) read(focused, "value")).set("2");
                                        focusedField = "ACTION";
                                    }
                                    default -> throw new AssertionError(problem);
                                }
                            }
                            Object code = read(data, "codeEditor");
                            write(code, "cursorByteOffset", 8);
                            write(code, "cursorLine", 2);
                            write(code, "cursorColumn", 4);
                            var before = dataBuffers(data);
                            var nodes = List.copyOf((List<?>) read(data, "entries"));
                            var rows = List.copyOf((List<?>) read(data, "propertyRows"));
                            var lore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                            Object history = read(editor, "lastEditorState");
                            invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                            require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals("DATA")
                                    && fixture.writes.isEmpty() && fixture.slots.size() == 1 && ItemStack.matches(opening, fixture.slots.get(0))
                                    && ItemStack.matches(opening, editor.draft()) && history == read(editor, "lastEditorState")
                                    && ((Deque<?>) read(editor, "editorUndo")).isEmpty() && !((ItemEditHistory) read(editor, "history")).canUndo(),
                                "Data failure partially saved or used the wrong route: " + target + "/" + mode + "/" + problem);
                            require(before.equals(dataBuffers(data)) && nodes.equals(read(data, "entries")) && rows.equals(read(data, "propertyRows"))
                                    && lore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                                    && (int) read(code, "cursorByteOffset") == 8 && (int) read(code, "cursorLine") == 2 && (int) read(code, "cursorColumn") == 4,
                                "Data failure lost raw buffers, row identity, cursor or other pending input");
                            if (problem.startsWith("metadata_"))
                                require((boolean) read(data, "focusMetadataType"), "Data failed to focus invalid metadata type");
                            else if (mode.equals("snbt"))
                                require((boolean) read(code, "focusRequested"), "Data failed to request its SNBT input");
                            else if (mode.equals("properties"))
                                require(read(data, "focusPropertyRowId").equals(read(focused, "id"))
                                    && (boolean) read(data, "focusPropertyValue") == focusedField.equals("VALUE"), "Data failed to target its exact property field");
                            else {
                                require(read(data, "focusNodeId").equals(read(focused, "id")) && read(data, "focusNodeField").toString().equals(focusedField),
                                    "Data failed to target its exact tree node field");
                                if (nested != null)
                                    require(((java.util.Set<?>) read(data, "focusAncestorIds")).contains(read(nested, "id")), "Data failed to reveal the invalid node's ancestor");
                            }
                            // Repair the same model in place, preserving all other pending panels.
                            if (problem.startsWith("metadata_"))
                                ((ImString) read(data, target.equals("entity_data") ? "entityType" : "blockEntityType"))
                                    .set(target.equals("entity_data") ? "minecraft:cow" : "minecraft:barrel");
                            else if (mode.equals("snbt"))
                                ((ImString) read(data, "snbtSource")).set(dataPayload().toString());
                            else {
                                ((ImString) read(changed, "key")).set(originalKey);
                                ((ImString) read(changed, "value")).set(originalValue);
                                if (originalType != null) {
                                    write(changed, "type", originalType);
                                    List<Object> children = (List<Object>) read(changed, "children");
                                    children.clear();
                                    children.addAll(originalChildren);
                                }
                            }
                            require(validBuffers.equals(dataBuffers(data)), "In-place Data repair lost other pending fields");
                            assertDataTargetOutput(opening, verifyActionHistory(fixture, editor, action, "Data repair " + target + "/" + mode + "/" + problem), target, false, false);
                        }
                    }
                }
            }
        }
    }

    private static JsonElement generatedDataValue(String target) {
        return JsonParser.parseString(switch (target) {
            case "custom_data", "bucket_entity_data" -> "{\"commit_check\":42,\"generated_check\":24}";
            case "entity_data" -> "{\"id\":\"minecraft:chicken\",\"generated_check\":24}";
            case "block_entity_data" -> "{\"id\":\"minecraft:chest\",\"generated_check\":24}";
            case "block_state" -> "{\"facing\":\"south\",\"waterlogged\":\"false\"}";
            case "debug_stick_state" -> "{\"minecraft:oak_stairs\":\"half\"}";
            default -> throw new AssertionError(target);
        });
    }

    private static void verifyDataTargetConflicts() throws Exception {
        for (String target : DATA_TARGETS) {
            for (boolean keepData : List.of(true, false)) {
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                    try (Fixture fixture = new Fixture()) {
                        ItemStack opening = dataTargetOpening();
                        ItemEditor editor = fixture.editedEditor(opening);
                        DataEditorPanel data = addDataTargetPending(editor, target, dataModes(target).getFirst(), false);
                        var option = new ComponentOption("minecraft:" + target, target, dataType(target));
                        write(editor, "componentOptions", List.of(option));
                        require(editor.selectComponent(fixture.client, option), "Data's current component could not be selected");
                        write(editor, "useGenericSelectedComponentEditor", true);
                        ComponentFormPanel form = (ComponentFormPanel) read(editor, "componentForm");
                        JsonElement baseline = form.pendingValue().deepCopy();
                        JsonElement generated = generatedDataValue(target);
                        form.restorePending(baseline, generated);
                        captureHistory(editor, fixture.client);
                        Object before = invoke(editor, "captureEditorSnapshot", new Class<?>[]{Minecraft.class}, fixture.client);
                        var buffers = dataBuffers(data);
                        Object history = read(editor, "lastEditorState");
                        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                        Object retained = invoke(editor, "captureEditorSnapshot", new Class<?>[]{Minecraft.class}, fixture.client);
                        require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals("DATA")
                                && (boolean) invoke(before, "sameState", new Class<?>[]{before.getClass()}, retained)
                                && buffers.equals(dataBuffers(data)) && form.pendingValue().equals(generated)
                                && Objects.equals(baseline, read(form, "loadedValue")) && editor.selectedComponent() == option
                                && (boolean) read(editor, "useGenericSelectedComponentEditor") && history == read(editor, "lastEditorState")
                                && ItemStack.matches(opening, editor.draft()) && ItemStack.matches(opening, fixture.slots.get(0))
                                && fixture.writes.isEmpty() && fixture.slots.size() == 1
                                && ((Deque<?>) read(editor, "editorUndo")).isEmpty() && !((ItemEditHistory) read(editor, "history")).canUndo(),
                            "Data/generated conflict lost input/selection/baseline/history or wrote a target: " + target);
                        if (keepData) form.discardChanges();
                        else data.sync(editor.draft());
                        ItemStack saved = verifyActionHistory(fixture, editor, action, "Data/generated resolution " + target + "/" + keepData);
                        if (keepData) assertDataTargetOutput(opening, saved, target, false, false);
                        else {
                            assertCombinedPanels(saved, Items.OAK_STAIRS);
                            ItemStack expected = opening.copy();
                            ItemStackCodecs.decodeAndSetComponent(fixture.client, expected, dataType(target), generated);
                            for (String component : DATA_TARGETS) {
                                require(Objects.equals(saved.get(dataType(component)), expected.get(dataType(component))),
                                    "Data discard lost generated output or overwrote an inactive target: " + target + "/" + component);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void verifyDataTargetEarlierFailures() throws Exception {
        for (String target : DATA_TARGETS) {
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    ItemStack opening = dataTargetOpening();
                    ItemEditor editor = fixture.editedEditor(opening);
                    DataEditorPanel data = addDataTargetPending(editor, target, dataModes(target).getFirst(), false);
                    captureHistory(editor, fixture.client);
                    var buffers = dataBuffers(data);
                    CodecComponentState model = (CodecComponentState) read(read(editor, "appearanceEditor"), "itemModel");
                    model.setValue(JsonParser.parseString("\"not an item model id\""));
                    Object history = read(editor, "lastEditorState");
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals("PRESENTATION")
                            && buffers.equals(dataBuffers(data)) && history == read(editor, "lastEditorState")
                            && ItemStack.matches(opening, editor.draft()) && ItemStack.matches(opening, fixture.slots.get(0))
                            && fixture.writes.isEmpty() && fixture.slots.size() == 1 && ((Deque<?>) read(editor, "editorUndo")).isEmpty()
                            && !((ItemEditHistory) read(editor, "history")).canUndo(),
                        "Earlier Appearance failure lost pending Data or wrote a result: " + target);
                    model.setValue(JsonParser.parseString("\"minecraft:diamond\""));
                    assertDataTargetOutput(opening, verifyActionHistory(fixture, editor, action, "Earlier failure/Data repair " + target), target, false, false);
                }
            }
        }
    }

    private static final EquipmentSlotGroup[] ATTRIBUTE_SLOTS = {
        EquipmentSlotGroup.ANY, EquipmentSlotGroup.MAINHAND, EquipmentSlotGroup.OFFHAND,
        EquipmentSlotGroup.HAND, EquipmentSlotGroup.FEET, EquipmentSlotGroup.LEGS,
        EquipmentSlotGroup.CHEST, EquipmentSlotGroup.HEAD, EquipmentSlotGroup.ARMOR,
        EquipmentSlotGroup.BODY, EquipmentSlotGroup.SADDLE
    };
    private static final AttributeModifier.Operation[] ATTRIBUTE_OPERATIONS = {
        AttributeModifier.Operation.ADD_VALUE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
    };

    private static DataComponentType<ItemEnchantments> enchantmentType(int target) {
        return target == 0 ? DataComponents.ENCHANTMENTS : DataComponents.STORED_ENCHANTMENTS;
    }

    private static ItemEnchantments nativeEnchantments(Map<String, Integer> levels) {
        ItemEnchantments.Mutable value = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        Registry<Enchantment> registry = fixtureRegistries.lookupOrThrow(Registries.ENCHANTMENT);
        levels.forEach((id, level) -> value.set(registry.get(Identifier.parse(id)).orElseThrow(), level));
        return value.toImmutable();
    }

    private static ItemStack enchantmentAttributeOpening() {
        ItemStack opening = new ItemStack(Items.STONE);
        opening.set(DataComponents.CUSTOM_NAME, Component.literal("Opening name"));
        opening.set(DataComponents.ENCHANTMENTS, nativeEnchantments(Map.of("minecraft:mending", 3)));
        opening.set(DataComponents.STORED_ENCHANTMENTS, nativeEnchantments(Map.of("minecraft:efficiency", 7)));
        opening.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().add(
            BuiltInRegistries.ATTRIBUTE.get(Identifier.parse("minecraft:armor")).orElseThrow(),
            new AttributeModifier(Identifier.parse("itemlab:old_modifier"), 4.0, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.HEAD).build());
        return opening;
    }

    private static Object enchantmentRow(String id, int level) throws Exception {
        var constructor = Class.forName("dev.nineofgaming.itemlab.editor.panel.EnchantmentRow")
            .getDeclaredConstructor(String.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(id, level);
    }

    private static String attributeDisplaySource(int index, TextInputFormat format) {
        return switch (format) {
            case MINI_MESSAGE -> "<gold><bold>Bonus " + index + "</bold></gold>";
            case COLOR_CODES -> "&6&lBonus " + index;
            case SECTION_CODES -> "\u00a76\u00a7lBonus " + index;
        };
    }

    private static double attributeAmount(int index) {
        return index == 0 ? -2.5 : index == 1 ? 0.0 : (index + 1) / 8.0;
    }

    @SuppressWarnings("unchecked")
    private static void addEnchantmentAttributePending(ItemEditor editor, int target, TextInputFormat format) throws Exception {
        Object enchantments = read(editor, "enchantmentEditor");
        invoke(enchantments, "reloadComponent", new Class<?>[]{DataComponentType.class, ItemStack.class}, enchantmentType(target), editor.draft());
        List<Object> enchantmentRows = (List<Object>) read(enchantments, "rows");
        enchantmentRows.clear();
        enchantmentRows.add(enchantmentRow("minecraft:sharpness", 12));
        enchantmentRows.add(enchantmentRow("minecraft:unbreaking", 0));
        enchantmentRows.add(enchantmentRow("minecraft:efficiency", 10));
        enchantmentRows.add(enchantmentRow("minecraft:sharpness", 300));
        write(enchantments, "dirty", true);
        Object attributes = read(editor, "attributeEditor");
        write(attributes, "textFormat", format);
        List<Object> attributeRows = (List<Object>) read(attributes, "rows");
        attributeRows.clear();
        var constructor = Class.forName("dev.nineofgaming.itemlab.editor.panel.AttributeRow")
            .getDeclaredConstructor(String.class, String.class, double.class, int.class, int.class, int.class, Component.class, TextInputFormat.class);
        constructor.setAccessible(true);
        for (int index = 0; index < ATTRIBUTE_SLOTS.length; index++) {
            Object row = constructor.newInstance(index % 2 == 0 ? "minecraft:attack_damage" : "minecraft:movement_speed",
                "itemlab:pending_" + index, attributeAmount(index), index % 3, index, index % 3, Component.empty(), format);
            if (index % 3 == 2) {
                RichTextLine display = (RichTextLine) read(row, "displayText");
                display.editorSource(false, false).set(attributeDisplaySource(index, format));
                display.commitEditorSource();
            }
            attributeRows.add(row);
        }
        write(attributes, "dirty", true);
    }

    private static void assertEnchantmentAttributeOutput(ItemStack opening, ItemStack saved, int target, boolean removed) {
        assertCombined(saved);
        require(saved.get(enchantmentType(1 - target)).equals(opening.get(enchantmentType(1 - target))),
            "Enchantment commit overwrote the inactive normal/stored component");
        if (removed) {
            require(!saved.has(enchantmentType(target)) && !saved.has(DataComponents.ATTRIBUTE_MODIFIERS),
                "Empty pending rows did not remove the active enchantments/attributes");
            return;
        }
        require(saved.get(enchantmentType(target)).equals(nativeEnchantments(Map.of(
                "minecraft:sharpness", 255, "minecraft:unbreaking", 1, "minecraft:efficiency", 10))),
            "Enchantment commit lost native holders, level clamping or duplicate last-row semantics");
        List<ItemAttributeModifiers.Entry> entries = saved.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers();
        require(entries.size() == ATTRIBUTE_SLOTS.length, "Attribute commit lost/reordered modifier rows");
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            require(entry.attribute().unwrapKey().orElseThrow().identifier().toString()
                    .equals(index % 2 == 0 ? "minecraft:attack_damage" : "minecraft:movement_speed")
                    && entry.modifier().id().toString().equals("itemlab:pending_" + index)
                    && entry.modifier().amount() == attributeAmount(index)
                    && entry.modifier().operation() == ATTRIBUTE_OPERATIONS[index % 3]
                    && entry.slot() == ATTRIBUTE_SLOTS[index],
                "Attribute output lost order, duplicate attributes, signed/zero amounts, operation or slot at " + index);
            require(entry.display().type().toString().equals(index % 3 == 0 ? "DEFAULT" : index % 3 == 1 ? "HIDDEN" : "OVERRIDE"),
                "Attribute output lost its display choice at " + index);
            if (index % 3 == 2) {
                Component display = ((ItemAttributeModifiers.Display.OverrideText) entry.display()).component();
                require(display.getString().equals("Bonus " + index)
                        && display.toFlatList().stream().filter(part -> !part.getString().isEmpty()).allMatch(part ->
                        part.getStyle().isBold() && part.getStyle().getColor() != null && part.getStyle().getColor().getValue() == 0xFFAA00),
                    "Attribute output lost custom tooltip text/style at " + index);
            }
        }
    }

    private static void verifyEnchantmentsAndAttributes() throws Exception {
        try (TextBridge ignored = new TextBridge()) {
            for (int target = 0; target < 2; target++) {
                for (TextInputFormat format : TextInputFormat.values()) {
                    for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                        try (Fixture fixture = new Fixture()) {
                            ItemStack opening = enchantmentAttributeOpening();
                            ItemEditor editor = fixture.editedEditor(opening);
                            addEnchantmentAttributePending(editor, target, format);
                            var preflight = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy,
                                ItemStackCodecs::validateStack, participants(editor, fixture.client));
                            require(preflight.succeeded() && preflight.included().equals(List.of("basic_name_lore", "enchantments", "attributes",
                                    "flags", "equipment", "appearance", "map", "data")),
                                "Enchantment/Attribute participants were omitted or reordered: " + preflight.failure());
                            ItemStack saved = verifyActionHistory(fixture, editor, action, "Enchantments/Attributes " + target + "/" + format);
                            assertEnchantmentAttributeOutput(opening, saved, target, false);
                            require((int) invoke(read(editor, "enchantmentEditor"), "historyTarget", new Class<?>[0]) == target,
                                "Enchantment history changed the normal/stored target");
                        }
                    }
                }
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                    try (Fixture fixture = new Fixture()) {
                        ItemStack opening = enchantmentAttributeOpening();
                        ItemEditor editor = fixture.editedEditor(opening);
                        Object enchantments = read(editor, "enchantmentEditor");
                        invoke(enchantments, "reloadComponent", new Class<?>[]{DataComponentType.class, ItemStack.class}, enchantmentType(target), editor.draft());
                        ((List<?>) read(enchantments, "rows")).clear();
                        ((List<?>) read(read(editor, "attributeEditor"), "rows")).clear();
                        write(enchantments, "dirty", true);
                        write(read(editor, "attributeEditor"), "dirty", true);
                        assertEnchantmentAttributeOutput(opening, verifyActionHistory(fixture, editor, action, "Enchantments/Attributes removal " + target), target, true);
                    }
                }
            }
        }
    }

    private static List<List<Object>> pendingRowBuffers(Object panel, boolean attributes) throws Exception {
        List<List<Object>> state = new ArrayList<>();
        for (Object row : (List<?>) read(panel, "rows")) {
            if (!attributes) {
                state.add(List.of(read(row, "rowId"), ((ImString) read(row, "id")).get(), ((ImInt) read(row, "level")).get()));
            } else {
                RichTextLine display = (RichTextLine) read(row, "displayText");
                state.add(List.of(read(row, "rowId"), ((ImString) read(row, "attributeId")).get(), ((ImString) read(row, "modifierId")).get(),
                    ((ImDouble) read(row, "amount")).get(), ((ImInt) read(row, "operation")).get(), ((ImInt) read(row, "slot")).get(),
                    ((ImInt) read(row, "display")).get(), display.format(), display.convertedSource(display.format()), read(display, "editorSource"),
                    ((ImString) read(display, "editorSource")).get(), read(display, "concealedEditorPrefix"), display.changed));
            }
        }
        return state;
    }

    private static void verifyEnchantmentAndAttributeFailures() throws Exception {
        try (TextBridge ignored = new TextBridge()) {
            for (int target = 0; target < 2; target++) {
                for (String problem : List.of("enchantment_blank", "enchantment_id", "enchantment_unknown", "attribute_id", "attribute_unknown",
                    "modifier_id", "NaN", "Infinity", "-Infinity", "display", "late_data")) {
                    for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                        try (Fixture fixture = new Fixture()) {
                            ItemStack opening = enchantmentAttributeOpening();
                            ItemEditor editor = fixture.editedEditor(opening);
                            addEnchantmentAttributePending(editor, target, TextInputFormat.MINI_MESSAGE);
                            captureHistory(editor, fixture.client);
                            Object enchantments = read(editor, "enchantmentEditor");
                            Object attributes = read(editor, "attributeEditor");
                            Object enchantment = ((List<?>) read(enchantments, "rows")).get(2);
                            Object attribute = ((List<?>) read(attributes, "rows")).get(8);
                            Object dataEntry = ((List<?>) read(read(editor, "dataEditor"), "entries")).getFirst();
                            RichTextLine display = (RichTextLine) read(attribute, "displayText");
                            switch (problem) {
                                case "enchantment_blank", "enchantment_id", "enchantment_unknown" ->
                                    ((ImString) read(enchantment, "id"))
                                        .set(problem.equals("enchantment_blank") ? " " : problem.equals("enchantment_id") ? "not an id" : "itemlab:unknown_enchantment");
                                case "attribute_id", "attribute_unknown" -> ((ImString) read(attribute, "attributeId"))
                                    .set(problem.equals("attribute_id") ? "not an id" : "itemlab:unknown_attribute");
                                case "modifier_id" ->
                                    ((ImString) read(attribute, "modifierId")).set("not a modifier id");
                                case "NaN", "Infinity", "-Infinity" ->
                                    ((ImDouble) read(attribute, "amount")).set(Double.parseDouble(problem));
                                case "display" -> {
                                    display.editorSource(false, false).set(INVALID_MINI_MESSAGE);
                                    display.commitEditorSource();
                                }
                                case "late_data" -> ((ImString) read(dataEntry, "value")).set("invalid late integer");
                                default -> throw new AssertionError(problem);
                            }
                            var enchantmentBuffers = pendingRowBuffers(enchantments, false);
                            var attributeBuffers = pendingRowBuffers(attributes, true);
                            var enchantmentRows = List.copyOf((List<?>) read(enchantments, "rows"));
                            var attributeRows = List.copyOf((List<?>) read(attributes, "rows"));
                            var lore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                            var data = ((DataEditorPanel) read(editor, "dataEditor")).historyState();
                            Object historyBefore = read(editor, "lastEditorState");
                            invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                            boolean enchantmentProblem = problem.startsWith("enchantment_");
                            require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString()
                                    .equals(enchantmentProblem ? "ENCHANTMENTS" : problem.equals("late_data") ? "DATA" : "ATTRIBUTES")
                                    && ItemStack.matches(opening, editor.draft()) && ItemStack.matches(opening, fixture.slots.get(0))
                                    && fixture.slots.size() == 1 && fixture.writes.isEmpty()
                                    && ((Deque<?>) read(editor, "editorUndo")).isEmpty() && !((ItemEditHistory) read(editor, "history")).canUndo()
                                    && historyBefore == read(editor, "lastEditorState"),
                                "Enchantment/Attribute failure partially saved or used the wrong recovery route: " + problem);
                            require(enchantmentBuffers.equals(pendingRowBuffers(enchantments, false)) && attributeBuffers.equals(pendingRowBuffers(attributes, true))
                                    && enchantmentRows.equals(read(enchantments, "rows")) && attributeRows.equals(read(attributes, "rows"))
                                    && lore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                                    && data.sameState(((DataEditorPanel) read(editor, "dataEditor")).historyState())
                                    && (int) invoke(enchantments, "historyTarget", new Class<?>[0]) == target,
                                "Enchantment/Attribute recovery replaced rows, buffers, target or other pending panels: " + problem);
                            if (enchantmentProblem) {
                                require(read(enchantments, "focusIdentifierRowId").equals(read(enchantment, "rowId"))
                                        && read(enchantments, "focusRowId").equals(read(enchantment, "rowId")),
                                    "Enchantment recovery failed to target the later invalid row");
                                ((ImString) read(enchantment, "id")).set("minecraft:efficiency");
                            } else if (problem.equals("late_data")) {
                                ((ImString) read(dataEntry, "value")).set("42");
                            } else {
                                String field = problem.startsWith("attribute_") ? "ATTRIBUTE" : problem.equals("modifier_id") ? "MODIFIER"
                                    : problem.equals("display") ? "DISPLAY" : "AMOUNT";
                                require(read(attributes, "focusInputRowId").equals(read(attribute, "rowId"))
                                        && read(attributes, "focusRowId").equals(read(attribute, "rowId"))
                                        && read(attributes, "focusInputField").toString().equals(field),
                                    "Attribute recovery failed to target the exact later-row field: " + problem);
                                ((ImString) read(attribute, "attributeId")).set("minecraft:attack_damage");
                                ((ImString) read(attribute, "modifierId")).set("itemlab:pending_8");
                                ((ImDouble) read(attribute, "amount")).set(attributeAmount(8));
                                if (problem.equals("display")) {
                                    display.editorSource(false, false).set(attributeDisplaySource(8, TextInputFormat.MINI_MESSAGE));
                                    display.commitEditorSource();
                                }
                            }
                            assertEnchantmentAttributeOutput(opening,
                                verifyActionHistory(fixture, editor, action, "Enchantment/Attribute repair " + target + "/" + problem), target, false);
                            require((long) read(enchantments, "focusIdentifierRowId") == 0L && (long) read(attributes, "focusInputRowId") == 0L,
                                "Successful Enchantment/Attribute repair left a stale recovery request");
                        }
                    }
                }
            }
        }
    }

    private static void verifyInvalidInputWritesNothing() throws Exception {
        for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
            try (Fixture fixture = new Fixture()) {
                ItemEditor editor = fixture.editedEditor();
                Object data = read(editor, "dataEditor");
                Object entry = ((List<?>) read(data, "entries")).getFirst();
                ((ImString) read(entry, "value")).set("invalid integer");
                ItemStack before = editor.draft().copy();
                ItemStack targetBefore = fixture.slots.get(0).copy();
                var buffers = ((DataEditorPanel) data).historyState();
                var loreBefore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals("DATA"),
                    action + " did not report/route the invalid input");
                require(ItemStack.matches(before, editor.draft()) && ItemStack.matches(targetBefore, fixture.slots.get(0))
                    && !fixture.slots.containsKey(1) && fixture.writes.isEmpty(), action + " partially wrote an invalid item");
                require(buffers.sameState(((DataEditorPanel) data).historyState())
                    && editor.hasPendingNonRawEditorChanges(), action + " discarded pending input");
                require(loreBefore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                    && ((ImInt) read(editor, "count")).get() == 99, action + " discarded name/lore/count input");
                for (String panel : List.of("equipmentEditor", "appearanceEditor", "mapEditor")) {
                    require((boolean) read(read(editor, panel), "dirty"), action + " advanced " + panel + "'s baseline");
                }
                require(!((ItemEditHistory) read(editor, "history")).canUndo(), action + " added history after rejection");
            }
        }
    }

    private static void verifyFullInventoryRetainsComposedDraft() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ItemEditor editor = fixture.editedEditor();
            when(fixture.inventory.getFreeSlot()).thenReturn(-1);
            ItemStack targetBefore = fixture.slots.get(0).copy();
            invoke(editor, "giveDraftCopy", new Class<?>[]{Minecraft.class}, fixture.client);
            require(!(boolean) read(editor, "statusSuccess"), "full-inventory Give Copy reported success");
            assertCombined(editor.draft());
            require(ItemStack.matches(targetBefore, fixture.slots.get(0)) && fixture.writes.isEmpty(), "full inventory was overwritten");
            require(!editor.hasPendingNonRawEditorChanges()
                && ((Deque<?>) read(read(editor, "history"), "undo")).size() == 1, "composed-draft failure policy was lost");
        }
    }

    private static void verifyStaleTargetRetainsComposedDraft() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ItemEditor editor = fixture.editedEditor();
            ItemStack targetBefore = fixture.slots.get(0).copy();
            var constructor = EditorItemTarget.class.getDeclaredConstructor(ItemStack.class, int.class,
                EditorItemTarget.Kind.class, AbstractContainerMenu.class, int.class, int.class);
            constructor.setAccessible(true);
            // The original menu is not the player's current menu: rejection happens before any IO.
            write(editor, "target", constructor.newInstance(targetBefore, 0, EditorItemTarget.Kind.CONTAINER,
                mock(AbstractContainerMenu.class), 0, 0));
            invoke(editor, "applyDraftToSlot", new Class<?>[]{Minecraft.class}, fixture.client);
            require(!(boolean) read(editor, "statusSuccess"), "Save to a stale container reported success");
            assertCombined(editor.draft());
            require(ItemStack.matches(targetBefore, fixture.slots.get(0)) && fixture.writes.isEmpty(), "stale target was overwritten");
            require(!editor.hasPendingNonRawEditorChanges()
                && ((Deque<?>) read(read(editor, "history"), "undo")).size() == 1, "stale-target retained-draft policy was lost");
        }
    }

    private static void verifyRichTextSerialization() throws Exception {
        try (TextBridge ignored = new TextBridge()) {
            RichTextLine name = text("<gold><bold>Mini name</bold></gold>", TextInputFormat.MINI_MESSAGE);
            Component parsed = name.toComponent(true);
            require(parsed.getString().equals("Mini name") && parsed.toFlatList().stream().anyMatch(part -> part.getStyle().isBold())
                && Boolean.FALSE.equals(read(parsed.getStyle(), "italic")), "Real MiniMessage/native serialization lost formatting or default italics");
            RichTextLine invalid = text(INVALID_MINI_MESSAGE, TextInputFormat.MINI_MESSAGE);
            require(!invalid.validationError().isBlank(), "Native-invalid MiniMessage entity hover was not rejected");
            RichTextLine unfinished = text("<unfinished", TextInputFormat.MINI_MESSAGE);
            require(unfinished.validationError().isBlank() && unfinished.toComponent(false).getString().equals("<unfinished"),
                "Lenient MiniMessage input was made stricter than the production parser");

            // Concealing generated defaults is a view change, not permission to discard them while typing.
            String prefix = "<!italic><white>";
            RichTextLine concealed = text(prefix + "Before", TextInputFormat.MINI_MESSAGE);
            ImString visible = concealed.editorSource(true, true);
            require(visible.get().equals("Before"), "Generated defaults were not concealed");
            visible.set(INVALID_MINI_MESSAGE);
            concealed.commitEditorSource();
            RichTextLine copy = concealed.copy();
            require(!copy.validationError().isBlank() && copy.sameEditorState(concealed)
                    && copy.editorSource(true, true).get().equals(INVALID_MINI_MESSAGE)
                    && copy.convertedSource(TextInputFormat.MINI_MESSAGE).equals(prefix + INVALID_MINI_MESSAGE),
                "Copy/validation lost an invalid visible buffer or its concealed defaults");
            copy.editorSource(true, true).set("After");
            copy.commitEditorSource();
            Component repaired = copy.toComponent(false);
            require(repaired.getString().equals("After") && Boolean.FALSE.equals(read(repaired.getStyle(), "italic"))
                    && repaired.getStyle().getColor().getValue() == 0xFFFFFF,
                "Editing a concealed-prefix line discarded its saved defaults");
        }
    }

    private static void configureTextPreferences(TextInputFormat format, boolean nonItalic, boolean whiteLore) throws Exception {
        // Reflection avoids preferences.save(): no user configuration or fixture config file is written.
        Object preferences = read(ItemLabConfig.get(), "text");
        write(preferences, "inputFormat", format);
        write(preferences, "removeDefaultItalics", nonItalic);
        write(preferences, "useWhiteDefaultLoreColor", whiteLore);
    }

    private static String formattedSource(TextInputFormat format, String miniMessage, String legacy) {
        return format == TextInputFormat.MINI_MESSAGE ? miniMessage : legacy.replace('&', format.codeMarker());
    }

    @SuppressWarnings("unchecked")
    private static void addFormattedText(ItemEditor editor, TextInputFormat format) throws Exception {
        Object lore = read(editor, "loreEditor");
        write(lore, "displayName", text(formattedSource(format, "<gold><bold>Combined name</bold></gold>", "&6&lCombined name"), format));
        List<RichTextLine> rows = (List<RichTextLine>) read(lore, "rows");
        rows.clear();
        rows.add(text(formattedSource(format, "<aqua><underlined>Combined lore</underlined></aqua>", "&b&nCombined lore"), format));
        rows.add(text("Plain lore", format));
        rows.add(text(formattedSource(format, "<red><italic>Explicit lore</italic></red>", "&c&oExplicit lore"), format));
        if (format == TextInputFormat.MINI_MESSAGE) {
            rows.add(text("<click:run_command:'/say formatting'><hover:show_text:'<yellow>Hover'>Interactive</hover></click> — {safe}", format));
        }
    }

    private static void assertFormattedText(ItemStack stack, TextInputFormat format, boolean nonItalic, boolean whiteLore) throws Exception {
        assertCombined(stack);
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        var lore = stack.get(DataComponents.LORE);
        require(name.toFlatList().stream().filter(part -> !part.getString().isEmpty())
                .allMatch(part -> part.getStyle().getColor().getValue() == 0xFFAA00),
            "A lore color preference overwrote the explicit name color");
        Component plain = lore.lines().get(1);
        require((nonItalic ? Boolean.FALSE : null) == read(name.getStyle(), "italic")
                && (nonItalic ? Boolean.FALSE : null) == read(plain.getStyle(), "italic"),
            "Name/plain-lore default italic state does not match the preference");
        require(whiteLore ? plain.getStyle().getColor() != null && plain.getStyle().getColor().getValue() == 0xFFFFFF
                : plain.getStyle().getColor() == null,
            "Plain lore default color does not match the preference");
        require(lore.lines().get(2).toFlatList().stream().filter(part -> !part.getString().isEmpty())
                .allMatch(part -> part.getStyle().isItalic() && part.getStyle().getColor().getValue() == 0xFF5555),
            "Base defaults overwrote explicit lore italics/color");
        Component styled = lore.styledLines().get(1);
        require(styled.getStyle().isItalic() == !nonItalic
                && styled.getStyle().getColor().getValue() == (whiteLore ? 0xFFFFFF : 0xAA00AA),
            "Native displayed lore ignored the saved default overrides");
        if (format == TextInputFormat.MINI_MESSAGE) {
            require(lore.lines().get(3).getString().equals("Interactive — {safe}")
                    && lore.lines().get(3).toFlatList().stream().anyMatch(part -> part.getStyle().getClickEvent() != null
                    && part.getStyle().getHoverEvent() != null),
                "MiniMessage interaction events or literal Unicode/braces were lost");
        }
    }

    private static void verifyFormattedCommits() throws Exception {
        try (TextBridge ignored = new TextBridge()) {
            for (TextInputFormat format : TextInputFormat.values()) {
                for (boolean nonItalic : List.of(false, true)) {
                    for (boolean whiteLore : List.of(false, true)) {
                        configureTextPreferences(format, nonItalic, whiteLore);
                        for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                            try (Fixture fixture = new Fixture()) {
                                ItemEditor editor = fixture.editedEditor();
                                addFormattedText(editor, format);
                                ItemStack opening = editor.draft().copy();
                                var pending = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                                var prepared = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy,
                                    ItemStackCodecs::validateStack, participants(editor, fixture.client));
                                require(prepared.succeeded(), "Formatting preflight failed: " + prepared.failure());
                                assertFormattedText(prepared.candidate(), format, nonItalic, whiteLore);
                                require(pending.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                                    && ItemStack.matches(opening, editor.draft()), "Formatting preflight mutated pending state");
                                captureHistory(editor, fixture.client);
                                invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                                captureHistory(editor, fixture.client);
                                require((boolean) read(editor, "statusSuccess"), "Formatted " + action + " failed: " + read(editor, "status"));
                                ItemStack saved = editor.draft().copy();
                                ItemStack output = fixture.slots.get(action.equals("applyDraftToSlot") ? 0 : 1);
                                require(ItemStack.matches(prepared.candidate(), saved) && ItemStack.matches(saved, output)
                                        && ItemStack.matches(saved, fixture.writes.getFirst().stack())
                                        && fixture.writes.size() == 1 && !editor.hasPendingNonRawEditorChanges(),
                                    "Formatted commit differed from preflight/output or left dirty baselines");
                                if (action.equals("giveDraftCopy"))
                                    require(ItemStack.matches(opening, fixture.slots.get(0)), "Formatted copy overwrote its target");
                                ItemStack persistent = ItemStackCodecs.decodeStackSnbt(fixture.client, ItemStackCodecs.encodeStackSnbt(fixture.client, saved));
                                require(ItemStack.matches(saved, persistent), "Persistent encoding lost rich text styles/events");
                                assertFormattedText(persistent, format, nonItalic, whiteLore);
                                invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                                captureHistory(editor, fixture.client);
                                require(ItemStack.matches(saved, editor.draft()) && ((Deque<?>) read(editor, "editorUndo")).size() == 1
                                        && ((Deque<?>) read(read(editor, "history"), "undo")).size() == 1,
                                    "Repeated formatted " + action + " changed the result/history");
                                invoke(editor, "undo", new Class<?>[]{Minecraft.class}, fixture.client);
                                var restored = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy,
                                    ItemStackCodecs::validateStack, participants(editor, fixture.client));
                                require(ItemStack.matches(opening, editor.draft()) && pending.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                                        && restored.succeeded() && ItemStack.matches(saved, restored.candidate()),
                                    "Formatted Undo lost the pending source/default options");
                                invoke(editor, "redo", new Class<?>[]{Minecraft.class}, fixture.client);
                                require(ItemStack.matches(saved, editor.draft()) && !editor.hasPendingNonRawEditorChanges(),
                                    "Formatted Redo lost styles/defaults or clean baselines");
                            }
                        }
                    }
                }
            }
        } finally {
            configureTextPreferences(TextInputFormat.COLOR_CODES, false, false);
        }
    }

    private static void addFormattedBook(ItemEditor editor) throws Exception {
        Object book = read(editor, "bookEditor");
        ItemStack source = editor.draft().copy();
        source.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
            new Filterable<>("Title", Optional.of("Filtered title")), "Author", 2,
            List.of(new Filterable<>(Component.literal("First"), Optional.of(Component.literal("Filtered first"))),
                new Filterable<>(Component.literal("Second"), Optional.of(Component.literal("Filtered second")))), true));
        invoke(book, "sync", new Class<?>[]{ItemStack.class}, source);
        write(book, "title", text("<gold>Title</gold>", TextInputFormat.MINI_MESSAGE));
        write(book, "author", text("<aqua><bold>Author</bold></aqua>", TextInputFormat.MINI_MESSAGE));
        Object page = ((List<?>) read(book, "writtenPages")).get(1);
        write(page, "text", text("<green><italic>Written page</italic></green>", TextInputFormat.MINI_MESSAGE));
        write(book, "dirty", true);
        write(book, "writtenDirty", true);
    }

    private static void assertFormattedBook(ItemStack saved) {
        WrittenBookContent book = saved.get(DataComponents.WRITTEN_BOOK_CONTENT);
        require(book.title().raw().equals("§6Title") && book.title().filtered().orElseThrow().equals("Filtered title")
                && book.author().equals("§b§lAuthor") && book.generation() == 2 && book.resolved()
                && book.pages().get(0).raw().getString().equals("First")
                && book.pages().get(1).raw().getString().equals("Written page")
                && book.pages().get(1).raw().toFlatList().stream().anyMatch(part -> part.getStyle().isItalic())
                && book.pages().get(1).filtered().orElseThrow().getString().equals("Filtered second"),
            "Real MiniMessage Book conversion lost title/author formatting, pages or filtered metadata");
    }

    @SuppressWarnings("unchecked")
    private static void verifyMiniMessageFailures() throws Exception {
        try (TextBridge ignored = new TextBridge()) {
            configureTextPreferences(TextInputFormat.MINI_MESSAGE, true, true);
            for (String problem : List.of("name", "lore", "title", "author", "page")) {
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                    try (Fixture fixture = new Fixture()) {
                        ItemEditor editor = fixture.editedEditor();
                        addFormattedText(editor, TextInputFormat.MINI_MESSAGE);
                        LoreEditorPanel lore = (LoreEditorPanel) read(editor, "loreEditor");
                        boolean bookProblem = !problem.equals("name") && !problem.equals("lore");
                        if (bookProblem) addFormattedBook(editor);
                        RichTextLine invalid = text("<!italic><white>" + INVALID_MINI_MESSAGE, TextInputFormat.MINI_MESSAGE);
                        Object book = read(editor, "bookEditor");
                        Object page = bookProblem ? ((List<?>) read(book, "writtenPages")).get(1) : null;
                        switch (problem) {
                            case "name" -> write(lore, "displayName", invalid);
                            case "lore" -> ((List<RichTextLine>) read(lore, "rows")).set(1, invalid);
                            case "title", "author" -> write(book, problem, invalid);
                            case "page" -> write(page, "text", invalid);
                            default -> throw new AssertionError(problem);
                        }
                        ImString visible = invalid.editorSource(true, true);
                        require(!invalid.validationError().isBlank() && visible.get().equals(INVALID_MINI_MESSAGE), "Expected a real native MiniMessage rejection");
                        ItemStack before = editor.draft().copy();
                        ItemStack targetBefore = fixture.slots.get(0).copy();
                        var pending = lore.historyState();
                        var data = ((DataEditorPanel) read(editor, "dataEditor")).historyState();
                        // The live history initializer tolerates unrepresentable grouped-panel input.
                        invoke(editor, "ensureEditorHistoryInitialized", new Class<?>[]{Minecraft.class}, fixture.client);
                        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                        invoke(editor, "ensureEditorHistoryInitialized", new Class<?>[]{Minecraft.class}, fixture.client);
                        require(!(boolean) read(editor, "statusSuccess")
                                && read(editor, "requestedEditorTab").toString().equals(bookProblem ? "BOOK" : "NAME")
                                && ItemStack.matches(before, editor.draft()) && ItemStack.matches(targetBefore, fixture.slots.get(0))
                                && !fixture.slots.containsKey(1) && fixture.writes.isEmpty(),
                            "MiniMessage " + problem + " rejection partially committed or used the wrong route");
                        require(pending.sameState(lore.historyState()) && data.sameState(((DataEditorPanel) read(editor, "dataEditor")).historyState())
                                && visible.get().equals(INVALID_MINI_MESSAGE)
                                && invalid.convertedSource(TextInputFormat.MINI_MESSAGE).equals("<!italic><white>" + INVALID_MINI_MESSAGE)
                                && ((Deque<?>) read(editor, "editorUndo")).isEmpty()
                                && !((ItemEditHistory) read(editor, "history")).canUndo(),
                            "MiniMessage rejection discarded a buffer/default prefix, unrelated input or history");
                        if (bookProblem) {
                            require(read(book, "pendingFocusSection").equals(problem.equals("page") ? "written_book_pages" : "written_book_metadata")
                                    && read(book, "pendingFocusField").equals(problem.equals("page") ? "page/1" : problem),
                                "Real MiniMessage Book error did not target its exact field/page");
                        } else {
                            require(problem.equals("name") ? (boolean) read(lore, "focusDisplayName")
                                    : (long) read(lore, "focusLoreRowId") == (long) invoke(lore, "loreRowId", new Class<?>[]{RichTextLine.class}, invalid),
                                "Real MiniMessage name/lore error did not target its exact input");
                        }
                        String repairedSource = switch (problem) {
                            case "name" -> "<gold><bold>Combined name</bold></gold>";
                            case "lore" -> "Plain lore";
                            case "title" -> "<gold>Title</gold>";
                            case "author" -> "<aqua><bold>Author</bold></aqua>";
                            case "page" -> "<green><italic>Written page</italic></green>";
                            default -> throw new AssertionError(problem);
                        };
                        visible.set(repairedSource);
                        invalid.commitEditorSource();
                        captureHistory(editor, fixture.client);
                        var repaired = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy,
                            ItemStackCodecs::validateStack, participants(editor, fixture.client));
                        require(repaired.succeeded(), "MiniMessage repair preflight failed: " + repaired.failure());
                        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                        captureHistory(editor, fixture.client);
                        ItemStack output = fixture.slots.get(action.equals("applyDraftToSlot") ? 0 : 1);
                        require((boolean) read(editor, "statusSuccess") && ItemStack.matches(repaired.candidate(), output)
                                && !editor.hasPendingNonRawEditorChanges() && fixture.writes.size() == 1,
                            "MiniMessage repair did not save every pending edit exactly once");
                        assertFormattedText(output, TextInputFormat.MINI_MESSAGE, true, true);
                        if (bookProblem) assertFormattedBook(output);
                    }
                }
            }
        } finally {
            configureTextPreferences(TextInputFormat.COLOR_CODES, false, false);
        }
    }

    private static ComponentOption representationOption(String kind) {
        String id = switch (kind) {
            case "generated" -> "minecraft:enchantable";
            case "fixed_variant" -> "minecraft:axolotl/variant";
            case "registry_variant" -> "minecraft:cat/variant";
            default -> throw new AssertionError(kind);
        };
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(id));
        require(type != null, "Missing representation fixture component: " + id);
        return new ComponentOption(id, id, type);
    }

    private static JsonElement representationValue(String kind, boolean pending) {
        return JsonParser.parseString(switch (kind) {
            case "generated" -> pending ? "{\"value\":42}" : "{\"value\":1}";
            case "fixed_variant" -> pending ? "\"blue\"" : "\"lucy\"";
            case "registry_variant" -> pending ? "\"minecraft:siamese\"" : "\"minecraft:tabby\"";
            default -> throw new AssertionError(kind);
        });
    }

    private static ItemStack representationOpening(Fixture fixture, String kind) {
        ItemStack opening = new ItemStack(Items.STONE);
        opening.set(DataComponents.CUSTOM_NAME, Component.literal("Opening name"));
        ItemStackCodecs.decodeAndSetComponent(fixture.client, opening, representationOption(kind).type(), representationValue(kind, false));
        return opening;
    }

    private static void addComponentRepresentation(Fixture fixture, ItemEditor editor, String kind) throws Exception {
        ComponentOption option = representationOption(kind);
        // The isolated catalog contains the real option needed by production history restoration.
        write(editor, "componentOptions", List.of(option));
        require(editor.selectComponent(fixture.client, option), "Component selection was unexpectedly blocked");
        if (kind.equals("generated")) {
            write(editor, "useGenericSelectedComponentEditor", true);
            ComponentFormPanel form = (ComponentFormPanel) read(editor, "componentForm");
            form.restorePending(form.pendingValue(), representationValue(kind, true));
        } else {
            ((ImString) read(read(editor, "variantComponentEditor"), "value")).set(representationValue(kind, true).getAsString());
        }
    }

    private static void assertComponentRepresentation(Fixture fixture, ItemStack saved, String kind) {
        assertCombined(saved);
        DataComponentType<?> type = representationOption(kind).type();
        require(ItemStackCodecs.encodeComponent(fixture.client, type, saved.get(type)).equals(representationValue(kind, true)),
            kind + " lost its selected component value");
    }

    /**
     * Exact production action output plus one combined history step, repetition and pending-state restoration.
     */
    private static ItemStack verifyActionHistory(Fixture fixture, ItemEditor editor, String action, String label) throws Exception {
        ItemStack opening = editor.draft().copy();
        DataEditorPanel data = (DataEditorPanel) read(editor, "dataEditor");
        var beforeData = dataBuffers(data);
        Object before = invoke(editor, "captureEditorSnapshot", new Class<?>[]{Minecraft.class}, fixture.client);
        var prepared = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy,
            ItemStackCodecs::validateStack, participants(editor, fixture.client));
        require(prepared.succeeded(), label + " preflight failed: " + prepared.failure());
        Object afterPreflight = invoke(editor, "captureEditorSnapshot", new Class<?>[]{Minecraft.class}, fixture.client);
        require(ItemStack.matches(opening, editor.draft()) && (boolean) invoke(before, "sameState", new Class<?>[]{before.getClass()}, afterPreflight),
            label + " preflight advanced pending state/baselines");
        captureHistory(editor, fixture.client);
        int editorUndoBefore = ((Deque<?>) read(editor, "editorUndo")).size();
        int itemUndoBefore = ((Deque<?>) read(read(editor, "history"), "undo")).size();
        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
        captureHistory(editor, fixture.client);
        ItemStack saved = editor.draft().copy();
        int destination = action.equals("applyDraftToSlot") ? 0 : 1;
        require((boolean) read(editor, "statusSuccess") && ItemStack.matches(prepared.candidate(), saved)
                && ItemStack.matches(saved, fixture.slots.get(destination)) && fixture.writes.size() == 1
                && fixture.writes.getFirst().slot() == (destination == 0 ? 36 : 37)
                && ItemStack.matches(saved, fixture.writes.getFirst().stack())
                && !editor.hasPendingNonRawEditorChanges() && !((RawStackEditorPanel) read(editor, "rawStackEditor")).isDirty()
                && read(editor, "pendingComponentRecovery") == null,
            label + " action lost composed output, wrote the wrong target or left pending state");
        if (destination == 1)
            require(ItemStack.matches(opening, fixture.slots.get(0)), label + " Give Copy overwrote its original target");
        for (String owner : List.of("music", "compass", "playerHead", "equipment", "map", "decoratedPot", "appearance", "banner", "potion", "firework", "book", "adventure", "use", "combat", "container")) {
            Object panel = read(editor, owner + "Editor");
            require(read(panel, "pendingFocusSection") == null && read(panel, "pendingFocusField") == null
                && !(boolean) read(panel, "focusPendingToolbar"), label + " retained grouped recovery after commit: " + owner);
        }
        require((int) read(read(read(editor, "equipmentEditor"), "allowedEntitiesFocus"), "requestedRow") == -1
                && (long) read(read(editor, "mapEditor"), "focusDecorationRowId") == 0L
                && read(read(editor, "decoratedPotEditor"), "requestedSideFocus") == null,
            label + " retained Equipment/Map/Pot row focus after commit");
        require((long) read(read(editor, "appearanceEditor"), "focusCustomModelRowId") == 0L
                && (long) read(read(editor, "bannerEditor"), "focusPatternRowId") == 0L
                && (int) read(read(read(editor, "bannerEditor"), "providedPatternsFocus"), "requestedRow") == -1
                && (long) read(read(editor, "potionEditor"), "focusEffectRowId") == 0L
                && (long) read(read(editor, "fireworkEditor"), "focusExplosionRowId") == 0L
                && (long) read(read(editor, "fireworkEditor"), "focusColorRowId") == 0L,
            label + " retained presentation-panel row focus after commit");
        require(!(boolean) read(read(read(editor, "musicEditor"), "instrumentDescriptionEditor"), "focusRequested"), label + " retained Music code-input focus after commit");
        require(read(read(editor, "bookEditor"), "recoveryWritten") == null && !(boolean) read(read(editor, "bookEditor"), "requestedContentFocus"),
            label + " retained Book recovery/content focus after commit");
        require(read(read(editor, "useEditor"), "focusListId") == null && (long) read(read(editor, "useEditor"), "focusListRowId") == 0L
                && read(read(editor, "useEditor"), "focusAddListId") == null && ((Map<?, ?>) read(read(editor, "useEditor"), "rowIdsByList")).isEmpty(),
            label + " retained Use row identity/recovery after commit");
        Object combat = read(editor, "combatEditor");
        for (String row : List.of("Projectile", "ToolRule", "DamageReduction"))
            require((long) read(combat, "focus" + row + "RowId") == 0L
                && !(boolean) read(combat, "focusAdd" + row + "Button"), label + " retained Combat row/add focus after commit");
        require(((List<?>) read(combat, "toolRuleRowIds")).isEmpty() && ((List<?>) read(combat, "damageReductionRowIds")).isEmpty()
            && ((Map<?, ?>) read(combat, "collectionFocus")).isEmpty(), label + " retained Combat collection/row identity after commit");
        for (Object row : (List<?>) read(combat, "projectileRows"))
            require(!(boolean) read(read(row, "componentsEditor"), "focusRequested"), label + " retained Combat projectile code-input focus");
        Object container = read(editor, "containerEditor");
        require((int) read(container, "recoveryContentsSlot") == -1 && (long) read(container, "recoveryBundleRowId") == 0L
                && (int) read(container, "contentsSlotFocusRequest") == -1 && (int) read(container, "bundleSlotFocusRequest") == -1
                && !(boolean) read(container, "focusBundleAddButton") && (int) read(read(container, "lockItemsFocus"), "requestedRow") == -1
                && !(boolean) read(container, "containedItemIdPending") && !(boolean) read(container, "bundleItemIdPending")
                && !(boolean) read(read(container, "exactComponentsEditor"), "focusRequested")
                && !(boolean) read(read(container, "partialPredicatesEditor"), "focusRequested"),
            label + " retained Container inspector/slot/collection/code-input recovery after commit");
        for (String target : ADVENTURE_COMPONENTS) {
            Object section = adventureSection(editor, target);
            require((long) read(section, "focusPredicateRowId") == 0L && !(boolean) read(section, "focusAddPredicateButton")
                && !(boolean) read(section, "focusPresenceToggle"), label + " retained Adventure section/predicate focus");
            for (Object row : (List<?>) read(section, "predicates")) {
                require((long) read(row, "focusStateRowId") == 0L && !(boolean) read(row, "focusAddStateButton")
                    && (int) read(read(row, "blocksFocus"), "requestedRow") == -1, label + " retained Adventure state/block-row focus");
                for (String prefix : List.of("nbt", "components", "componentPredicates"))
                    require(!(boolean) read(read(row, prefix + "Editor"), "focusRequested"),
                        label + " retained Adventure SNBT focus");
            }
        }
        ItemStack persisted = ItemStackCodecs.decodeStackSnbt(fixture.client, ItemStackCodecs.encodeStackSnbt(fixture.client, saved));
        require(ItemStack.matches(saved, persisted),
            label + " persistent encoding changed native components/typed data");
        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
        captureHistory(editor, fixture.client);
        require(ItemStack.matches(saved, editor.draft()) && ((Deque<?>) read(editor, "editorUndo")).size() == editorUndoBefore + 1
                && ((Deque<?>) read(read(editor, "history"), "undo")).size() == itemUndoBefore + 1,
            label + " did not retain one combined history step on same-action repetition");
        int writesBeforeUndo = fixture.writes.size();
        invoke(editor, "undo", new Class<?>[]{Minecraft.class}, fixture.client);
        Object restored = invoke(editor, "captureEditorSnapshot", new Class<?>[]{Minecraft.class}, fixture.client);
        var restoredCandidate = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy,
            ItemStackCodecs::validateStack, participants(editor, fixture.client));
        require(ItemStack.matches(opening, editor.draft()) && (boolean) invoke(before, "sameState", new Class<?>[]{before.getClass()}, restored)
                && beforeData.equals(dataBuffers(data))
                && restoredCandidate.succeeded() && ItemStack.matches(saved, restoredCandidate.candidate()),
            label + " Undo lost its original draft, selected representation or pending buffers");
        invoke(editor, "redo", new Class<?>[]{Minecraft.class}, fixture.client);
        require(ItemStack.matches(saved, editor.draft()) && !editor.hasPendingNonRawEditorChanges()
                && !((RawStackEditorPanel) read(editor, "rawStackEditor")).isDirty()
                && fixture.writes.size() == writesBeforeUndo && ItemStack.matches(saved, fixture.slots.get(destination)),
            label + " Redo lost clean baselines or history rewrote the external output");
        return saved;
    }

    private static void verifyComponentRepresentationCommits() throws Exception {
        for (String kind : List.of("generated", "fixed_variant", "registry_variant")) {
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(representationOpening(fixture, kind));
                    addComponentRepresentation(fixture, editor, kind);
                    assertComponentRepresentation(fixture, verifyActionHistory(fixture, editor, action, kind), kind);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifyComponentRepresentationFailures() throws Exception {
        for (String problem : List.of("generated_buffer", "generated_codec", "fixed_variant", "registry_variant",
            "generated_late_data", "fixed_variant_late_data", "registry_variant_late_data")) {
            String kind = problem.startsWith("generated") ? "generated" : problem.startsWith("fixed") ? "fixed_variant" : "registry_variant";
            boolean late = problem.endsWith("late_data");
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(representationOpening(fixture, kind));
                    addComponentRepresentation(fixture, editor, kind);
                    ComponentFormPanel form = (ComponentFormPanel) read(editor, "componentForm");
                    Object variant = read(editor, "variantComponentEditor");
                    ImString invalid = null;
                    if (late) {
                        Object entry = ((List<?>) read(read(editor, "dataEditor"), "entries")).getFirst();
                        invalid = (ImString) read(entry, "value");
                        invalid.set("invalid late integer");
                    } else if (problem.equals("generated_buffer")) {
                        invalid = new ImString("not a number", 128);
                        ((Map<String, ImString>) read(form, "fieldBuffers")).put("$/value", invalid);
                        ((Map<String, String>) read(form, "fieldProblems")).put("$/value", "Enter a number.");
                        write(form, "dirty", true);
                    } else if (problem.equals("generated_codec")) {
                        write(form, "value", JsonParser.parseString("{\"value\":0}"));
                    } else {
                        invalid = (ImString) read(variant, "value");
                        invalid.set(kind.equals("fixed_variant") ? "not a variant" : "itemlab:unknown_cat");
                    }
                    String source = invalid == null ? "" : invalid.get();
                    JsonElement value = form.pendingValue();
                    JsonElement baseline = ((JsonElement) read(form, "loadedValue")).deepCopy();
                    ComponentOption selected = editor.selectedComponent();
                    ItemStack before = editor.draft().copy();
                    var lore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                    var data = ((DataEditorPanel) read(editor, "dataEditor")).historyState();
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    require(!(boolean) read(editor, "statusSuccess") && ItemStack.matches(before, editor.draft())
                            && ItemStack.matches(before, fixture.slots.get(0)) && !fixture.slots.containsKey(1) && fixture.writes.isEmpty()
                            && ((Deque<?>) read(editor, "editorUndo")).isEmpty() && !((ItemEditHistory) read(editor, "history")).canUndo(),
                        problem + " rejection partially committed or created history");
                    require(editor.selectedComponent() == selected && value.equals(form.pendingValue()) && baseline.equals(read(form, "loadedValue"))
                            && (invalid == null || source.equals(invalid.get())) && lore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                            && data.sameState(((DataEditorPanel) read(editor, "dataEditor")).historyState()),
                        problem + " recovery replaced selection, buffers, baselines or unrelated panels");
                    require(read(editor, "requestedEditorTab").toString().equals(late ? "DATA" : "COMPONENTS"), problem + " used the wrong recovery route");
                    if (!late) {
                        require(read(editor, "pendingComponentRecovery").equals(kind.equals("generated") ? "component_form" : "variant_component"),
                            problem + " opened the wrong component recovery owner");
                        require(kind.equals("generated") ? read(form, "focusFieldPath").equals(problem.equals("generated_buffer") ? "$/value" : "$")
                            : (boolean) read(variant, "focusValueInput"), problem + " did not request its precise input");
                    }
                    if (late) invalid.set("42");
                    else if (kind.equals("generated")) {
                        if (invalid != null) invalid.set("42");
                        ((Map<String, String>) read(form, "fieldProblems")).clear();
                        write(form, "value", representationValue(kind, true));
                    } else invalid.set(representationValue(kind, true).getAsString());
                    assertComponentRepresentation(fixture, verifyActionHistory(fixture, editor, action, problem + " repair"), kind);
                }
            }
        }
    }

    private static ItemStack rawReplacement() throws Exception {
        ItemStack replacement = new ItemStack(Items.DIAMOND, 99);
        replacement.set(DataComponents.MAX_STACK_SIZE, 16);
        replacement.set(DataComponents.CUSTOM_NAME, text("&6&lRaw name").toComponent(false));
        replacement.set(DataComponents.LORE, new ItemLore(List.of(text("&b&nRaw lore").toComponent(false))));
        replacement.set(DataComponents.CUSTOM_DATA, CustomData.of(TagParser.parseCompoundFully("{b:1b,l:9007199254740993L,f:3.5f,d:4.5d,bytes:[B;1b,2b],longs:[L;3L,4L],nested:{raw_check:42}}")));
        return replacement;
    }

    private static void verifyRawCommitsAndFailures() throws Exception {
        for (String problem : List.of("valid", "{", "{id:\"minecraft:diamond\",count:100}", "{id:\"minecraft:diamond\",count:0}",
            "{id:\"minecraft:diamond\",count:-1}", "{id:\"minecraft:diamond\",count:\"abc\"}",
            "{id:\"minecraft:diamond\",count:1.5f}", "{id:\"minecraft:diamond\",count:4294967297L}",
            "{id:\"minecraft:air\",count:1}", "{id:\"itemlab:unknown_item\",count:1}")) {
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    ItemStack opening = new ItemStack(Items.STONE);
                    opening.set(DataComponents.CUSTOM_NAME, Component.literal("Opening name"));
                    opening.set(DataComponents.MAP_COLOR, new net.minecraft.world.item.component.MapItemColor(0x987654));
                    ItemEditor editor = fixture.cleanEditor(opening);
                    RawStackEditorPanel raw = (RawStackEditorPanel) read(editor, "rawStackEditor");
                    ItemStack replacement = rawReplacement();
                    String valid = " \n" + ItemStackCodecs.encodeStackSnbt(fixture.client, replacement) + "\n ";
                    raw.restorePendingSource(problem.equals("valid") ? valid : problem);
                    Object code = read(raw, "codeEditor");
                    write(code, "cursorByteOffset", 17);
                    write(code, "cursorLine", 3);
                    if (!problem.equals("valid")) {
                        var lore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                        require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals("RAW")
                                && (boolean) read(code, "focusRequested") && (int) read(code, "cursorByteOffset") == 17 && (int) read(code, "cursorLine") == 3
                                && raw.pendingSource().equals(problem) && raw.isDirty() && ItemStack.matches(opening, editor.draft())
                                && ItemStack.matches(opening, fixture.slots.get(0)) && !fixture.slots.containsKey(1) && fixture.writes.isEmpty()
                                && lore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                                && ((Deque<?>) read(editor, "editorUndo")).isEmpty() && !((ItemEditHistory) read(editor, "history")).canUndo(),
                            "Raw rejection lost input/cursor or partially committed: " + problem);
                        raw.restorePendingSource(valid);
                    }
                    var prepared = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                    require(prepared.succeeded() && prepared.included().equals(List.of("raw")) && raw.pendingSource().equals(valid),
                        "Raw-only preflight included stale structured panels or changed its source");
                    ItemStack saved = verifyActionHistory(fixture, editor, action, "Raw " + problem);
                    require(ItemStack.matches(replacement, saved) && saved.get(DataComponents.MAP_COLOR) == null,
                        "Raw replacement merged old components instead of replacing the whole stack");
                }
            }
        }
    }

    private static void verifyExplicitCountDecoding() throws Exception {
        try (Fixture fixture = new Fixture()) {
            for (String count : List.of("", "1b", "16s", "64L", "99", "1.0f", "99.0d")) {
                String source = "{id:\"minecraft:diamond\"" + (count.isEmpty() ? "" : ",count:" + count) + "}";
                int expected = count.isEmpty() ? 1 : (int) Double.parseDouble(count.replaceAll("[bsLfd]", ""));
                ItemStack snbt = ItemStackCodecs.decodeStackSnbt(fixture.client, source);
                ItemStack nbt = ItemStackCodecs.decodeStackNbt(fixture.client, TagParser.parseCompoundFully(source));
                require(snbt.is(Items.DIAMOND) && snbt.getCount() == expected && ItemStack.matches(snbt, nbt),
                    "Explicit count validation rejected native numeric types or changed the omitted-count default");
                ItemStackCodecs.validateStack(snbt);
            }
            for (String count : List.of("0", "-1", "100", "\"abc\"", "{}", "1.5f", "4294967297L")) {
                boolean rejected = false;
                try {
                    ItemStackCodecs.decodeStackNbt(fixture.client, TagParser.parseCompoundFully("{id:\"minecraft:diamond\",count:" + count + "}"));
                } catch (RuntimeException expected) {
                    rejected = true;
                }
                require(rejected, "The NBT decoder silently replaced an invalid explicit count: " + count);
            }
        }
    }

    private static void verifyDiamondFireLosslessKeys() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ItemStack opening = diamondFireOpening();
            CompoundTag original = opening.get(DataComponents.CUSTOM_DATA).copyTag();
            CompoundTag publicValues = original.getCompound("PublicBukkitValues").orElseThrow();
            publicValues.putString("hypercube: spaced ", "literal");
            publicValues.putString("hypercube:", "unknown empty suffix");
            publicValues.putString("hypercube:hypercube:nested_prefix", "literal prefix");
            publicValues.putString("hypercube:" + "long".repeat(200), "long key");
            publicValues.putFloat("hypercube:nonfinite", Float.NaN);
            publicValues.put("hypercube:unknown", TagParser.parseCompoundFully("{bytes:[B;1b,-2b],ints:[I;1,2],longs:[L;9007199254740993L],list:[{n:1s}]}"));
            opening.set(DataComponents.CUSTOM_DATA, CustomData.of(original));
            ItemEditor editor = fixture.editedEditor(opening);
            DiamondFireTagEditorPanel tags = (DiamondFireTagEditorPanel) read(editor, "diamondFireTagEditor");
            Object edited = diamondFireRow(tags, "edit");
            Object nonfinite = diamondFireRow(tags, "nonfinite");
            require(((String) invoke(tags, "numberProblem", new Class<?>[]{nonfinite.getClass()}, nonfinite)).isBlank(),
                "An untouched non-finite NBT number must not be presented as an invalid edit");
            ((ImString) read(edited, "value")).set("3.5");
            write(edited, "valueChanged", true);
            CompoundTag expected = original.copy();
            expected.getCompound("PublicBukkitValues").orElseThrow().putDouble("hypercube:edit", 3.5);
            ItemStack pending = tags.pendingStack(fixture.client);
            require(pending.get(DataComponents.CUSTOM_DATA).copyTag().equals(expected),
                "An unrelated DiamondFire edit changed an untouched key or typed value");
            var history = tags.historyState();
            tags.restoreHistoryState(history);
            require(history.sameState(tags.historyState()) && tags.pendingStack(fixture.client).get(DataComponents.CUSTOM_DATA).copyTag().equals(expected),
                "DiamondFire history normalized unknown keys or changed typed values");
            Object renamed = diamondFireRow(tags, "edit");
            ((ImString) read(renamed, "key")).set(" hypercube:renamed ");
            var renamedHistory = tags.historyState();
            tags.restoreHistoryState(renamedHistory);
            CompoundTag renamedValues = tags.pendingStack(fixture.client).get(DataComponents.CUSTOM_DATA).copyTag().getCompound("PublicBukkitValues").orElseThrow();
            require(renamedValues.contains("hypercube:renamed") && !renamedValues.contains("hypercube:edit"),
                "Explicit DiamondFire key edits must still normalize the entered prefix");
        }
        for (String malformed : List.of("{}", "\"opaque\"", "[1L,2L]", "42b")) {
            try (Fixture fixture = new Fixture()) {
                ItemStack opening = diamondFireOpening();
                ItemEditor editor = fixture.cleanEditor(opening);
                DiamondFireTagEditorPanel tags = (DiamondFireTagEditorPanel) read(editor, "diamondFireTagEditor");
                Object row = diamondFireRow(tags, "edit");
                CompoundTag custom = TagParser.parseCompoundFully("{other:1b,PublicBukkitValues:" + malformed + "}");
                editor.draft().set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
                tags.sync(editor.draft());
                require(tags.pendingStack(fixture.client).get(DataComponents.CUSTOM_DATA).copyTag().equals(custom),
                    "Opaque PublicBukkitValues changed without any tag edits");
                ((List<Object>) read(tags, "rows")).add(row);
                if (malformed.equals("{}")) {
                    require(tags.pendingStack(fixture.client).get(DataComponents.CUSTOM_DATA).copyTag()
                            .getCompound("PublicBukkitValues").orElseThrow().contains("hypercube:edit"),
                        "Empty PublicBukkitValues must remain editable");
                    continue;
                }
                boolean rejected = false;
                try {
                    tags.pendingStack(fixture.client);
                } catch (IllegalArgumentException expected) {
                    rejected = true;
                }
                require(rejected && editor.draft().get(DataComponents.CUSTOM_DATA).copyTag().equals(custom),
                    "Adding tags silently replaced opaque PublicBukkitValues");
            }
        }
    }

    private static void verifyDiamondFireContext() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ItemEditor editor = fixture.cleanEditor(diamondFireOpening());
            Object preferences = read(editor, "preferences");
            Object integration = read(preferences, "diamondFire");
            Object originalOverride = read(integration, "forceCompatibility");
            try {
                if (!DiamondFireFeatures.available()) {
                    write(integration, "forceCompatibility", true);
                    DiamondFireContext.setConnectionTarget(ServerAddress.parseString("mcdiamondfire.com"));
                    when(fixture.client.getConnection()).thenReturn(mock(ClientPacketListener.class));
                    require(!DiamondFireContext.current().active(), "Older client gained DF server context");
                    addDiamondFirePending(fixture, editor);
                    var connected = new DiamondFireContext.State(true, "mcdiamondfire.com");
                    require(!(boolean) invoke(editor, "diamondFireSupportActive", new Class<?>[]{DiamondFireContext.State.class}, connected),
                        "Connection or saved override bypassed the minimum DF client version");
                    require(!(boolean) invoke(editor, "diamondFireTabAvailable", new Class<?>[]{DiamondFireContext.State.class}, connected),
                        "Pending tags or saved override exposed DF UI on an older client");
                    return;
                }
                write(integration, "forceCompatibility", false);
                DiamondFireContext.clearConnectionTarget();
                require(!DiamondFireContext.current().active(), "Single-player gained DF context");
                for (String host : List.of("mcdiamondfire.com", "beta.mcdiamondfire.com", "play.mcdiamondfire.com",
                    "diamondfire.games", "node.diamondfire.games", "BETA.MCDIAMONDFIRE.COM.:25565")) {
                    DiamondFireContext.setConnectionTarget(ServerAddress.parseString(host));
                    require(!DiamondFireContext.current().active(), "Pending/failed connection gained DF context: " + host);
                    when(fixture.client.getConnection()).thenReturn(mock(ClientPacketListener.class));
                    var connected = DiamondFireContext.current();
                    require(connected.active() && !connected.host().contains(":"), "DF target was not recognized: " + host);
                    require((boolean) invoke(editor, "diamondFireTabAvailable", new Class<?>[]{DiamondFireContext.State.class}, connected),
                        "DF context did not expose its tab");
                    DiamondFireContext.clearConnectionTarget();
                    require(!DiamondFireContext.current().active(), "Disconnect kept stale DF context");
                    when(fixture.client.getConnection()).thenReturn(null);
                }
                when(fixture.client.getConnection()).thenReturn(mock(ClientPacketListener.class));
                for (String host : List.of("mcdiamondfire.com.example.org", "notmcdiamondfire.com", "diamondfire.games.example.org",
                    "example.org", "127.0.0.1", "[::1]:25565")) {
                    DiamondFireContext.setConnectionTarget(ServerAddress.parseString("beta.mcdiamondfire.com"));
                    require(DiamondFireContext.current().active(), "DF reconnect failed");
                    DiamondFireContext.setConnectionTarget(ServerAddress.parseString(host));
                    var ordinary = DiamondFireContext.current();
                    require(!ordinary.active() && !(boolean) invoke(editor, "diamondFireTabAvailable", new Class<?>[]{DiamondFireContext.State.class}, ordinary),
                        "Transfer to an ordinary/spoofed host kept DF context: " + host);
                }
                var inactive = DiamondFireContext.current();
                addDiamondFirePending(fixture, editor);
                require((boolean) invoke(editor, "diamondFireTabAvailable", new Class<?>[]{DiamondFireContext.State.class}, inactive),
                    "Disconnect stranded pending DF edits");
                require(!(boolean) invoke(editor, "diamondFireSupportActive", new Class<?>[]{DiamondFireContext.State.class}, inactive),
                    "Pending tag edits emphasized DF compatibility on an ordinary server");
                write(integration, "forceCompatibility", true);
                require((boolean) invoke(editor, "diamondFireSupportActive", new Class<?>[]{DiamondFireContext.State.class}, inactive),
                    "Explicit compatibility override was ignored");
            } finally {
                write(integration, "forceCompatibility", originalOverride);
                DiamondFireContext.clearConnectionTarget();
            }
        }
    }

    private static void verifyDiamondFireRepresentationPairs() throws Exception {
        List<String> owners = List.of("diamondfire", "data", "components", "raw");
        for (int first = 0; first < owners.size(); first++) {
            for (int second = first + 1; second < owners.size(); second++) {
                for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                    try (Fixture fixture = new Fixture()) {
                        ItemEditor editor = fixture.cleanEditor(diamondFireOpening());
                        var option = new ComponentOption("minecraft:custom_data", "minecraft:custom_data", DataComponents.CUSTOM_DATA);
                        write(editor, "componentOptions", List.of(option));
                        require(editor.selectComponent(fixture.client, option), "Custom-data selection was blocked before edits");
                        for (String owner : List.of(owners.get(first), owners.get(second))) {
                            switch (owner) {
                                case "diamondfire" -> addDiamondFirePending(fixture, editor);
                                case "data" -> {
                                    Object data = read(editor, "dataEditor");
                                    CompoundTag custom = editor.draft().get(DataComponents.CUSTOM_DATA).copyTag();
                                    custom.putString("from_data", "pending");
                                    invoke(data, "loadTree", new Class<?>[]{CompoundTag.class}, custom);
                                    write(data, "dirty", true);
                                }
                                case "components" -> {
                                    write(editor, "useGenericSelectedComponentEditor", true);
                                    JsonElement baseline = ItemStackCodecs.encodeComponent(fixture.client, DataComponents.CUSTOM_DATA,
                                        editor.draft().get(DataComponents.CUSTOM_DATA));
                                    JsonObject pending = baseline.deepCopy().getAsJsonObject();
                                    pending.addProperty("from_components", "pending");
                                    ((ComponentFormPanel) read(editor, "componentForm")).restorePending(baseline, pending);
                                }
                                case "raw" -> {
                                    ItemStack pending = editor.draft().copy();
                                    CompoundTag custom = pending.get(DataComponents.CUSTOM_DATA).copyTag();
                                    custom.putString("from_raw", "pending");
                                    pending.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
                                    ((RawStackEditorPanel) read(editor, "rawStackEditor")).restorePendingSource(
                                        ItemStackCodecs.encodeStackSnbt(fixture.client, pending));
                                }
                                default -> throw new AssertionError(owner);
                            }
                        }
                        ItemStack before = editor.draft().copy();
                        Object snapshot = invoke(editor, "captureEditorSnapshot", new Class<?>[]{Minecraft.class}, fixture.client);
                        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                        Object retained = invoke(editor, "captureEditorSnapshot", new Class<?>[]{Minecraft.class}, fixture.client);
                        require(!(boolean) read(editor, "statusSuccess")
                                && (boolean) invoke(snapshot, "sameState", new Class<?>[]{snapshot.getClass()}, retained)
                                && ItemStack.matches(before, editor.draft()) && ItemStack.matches(before, fixture.slots.get(0))
                                && fixture.writes.isEmpty() && !fixture.slots.containsKey(1)
                                && !((ItemEditHistory) read(editor, "history")).canUndo(),
                            "Custom-data owners silently overwrote each other: " + owners.get(first) + "/" + owners.get(second) + " on " + action);
                    }
                }
            }
        }
    }

    private static ItemStack diamondFireOpening() throws Exception {
        ItemStack opening = new ItemStack(Items.STONE);
        opening.set(DataComponents.CUSTOM_NAME, Component.literal("Opening name"));
        opening.set(DataComponents.CUSTOM_DATA, CustomData.of(TagParser.parseCompoundFully("{commit_check:42,b:1b,l:2L,f:3.5f,d:4.5d,PublicBukkitValues:{\"other:retained\":{b:2b,l:3L},\"hypercube:keep_long\":9007199254740993L,\"hypercube:keep_nbt\":{a:[B;1b,2b]},\"hypercube:edit\":2b,\"hypercube:remove\":\"gone\"}}")));
        return opening;
    }

    private static CompoundTag addDiamondFirePending(Fixture fixture, ItemEditor editor) throws Exception {
        ItemStack pending = editor.draft().copy();
        CompoundTag custom = pending.get(DataComponents.CUSTOM_DATA).copyTag();
        CompoundTag publicValues = custom.getCompound("PublicBukkitValues").orElseThrow();
        publicValues.remove("hypercube:remove");
        publicValues.putDouble("hypercube:edit", 3.5);
        publicValues.putString("hypercube:new", "  literal {tag}  ");
        pending.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        ((DiamondFireTagEditorPanel) read(editor, "diamondFireTagEditor")).restorePending(fixture.client, pending);
        return custom.copy();
    }

    private static Object diamondFireRow(Object panel, String key) throws Exception {
        for (Object row : (List<?>) read(panel, "rows")) {
            if (((ImString) read(row, "key")).get().equals(key)) return row;
        }
        throw new AssertionError("Missing DiamondFire row: " + key);
    }

    private static void verifyDiamondFireCommitsAndFailures() throws Exception {
        for (String problem : List.of("valid", "blank", "duplicate", "bad number", "NaN", "Infinity")) {
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(diamondFireOpening());
                    // Deliberately resolve Data's competing representation before editing tags.
                    ((DataEditorPanel) read(editor, "dataEditor")).sync(editor.draft());
                    CompoundTag expected = addDiamondFirePending(fixture, editor);
                    DiamondFireTagEditorPanel tags = (DiamondFireTagEditorPanel) read(editor, "diamondFireTagEditor");
                    Object row = diamondFireRow(tags, "edit");
                    boolean keyProblem = problem.equals("blank") || problem.equals("duplicate");
                    if (!problem.equals("valid")) {
                        ImString source = (ImString) read(row, keyProblem ? "key" : "value");
                        source.set(problem.equals("blank") ? " " : problem.equals("duplicate") ? "hypercube:keep_long" : problem);
                        if (!keyProblem) write(row, "valueChanged", true);
                        var pending = tags.historyState();
                        var lore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                        ItemStack before = editor.draft().copy();
                        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                        require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals("DIAMONDFIRE")
                                && read(tags, keyProblem ? "focusRowId" : "focusValueRowId").equals(read(row, "rowId"))
                                && pending.sameState(tags.historyState()) && lore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                                && ItemStack.matches(before, editor.draft()) && ItemStack.matches(before, fixture.slots.get(0))
                                && !fixture.slots.containsKey(1) && fixture.writes.isEmpty()
                                && ((Deque<?>) read(editor, "editorUndo")).isEmpty() && !((ItemEditHistory) read(editor, "history")).canUndo(),
                            "DiamondFire rejection lost input or partially committed: " + problem);
                        source.set(keyProblem ? "edit" : "3.5");
                    }
                    ItemStack saved = verifyActionHistory(fixture, editor, action, "DiamondFire " + problem);
                    assertCombined(saved);
                    require(saved.get(DataComponents.CUSTOM_DATA).copyTag().equals(expected),
                        "DiamondFire lost typed untouched tags, unrelated custom data or exact edited values");
                }
            }
        }
    }

    private static void verifyRepresentationConflicts() throws Exception {
        for (String conflict : List.of("raw", "generated_flags", "generated_variant", "data_diamondfire_keep_data", "data_diamondfire_keep_tags")) {
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor(conflict.startsWith("data_diamondfire") ? diamondFireOpening()
                        : conflict.equals("generated_variant") ? representationOpening(fixture, "fixed_variant") : new ItemStack(Items.STONE));
                    CompoundTag expectedTags = null;
                    if (conflict.equals("raw")) {
                        ((RawStackEditorPanel) read(editor, "rawStackEditor")).restorePendingSource(ItemStackCodecs.encodeStackSnbt(fixture.client, rawReplacement()));
                    } else if (conflict.equals("generated_flags")) {
                        var option = new ComponentOption("minecraft:max_stack_size", "minecraft:max_stack_size", DataComponents.MAX_STACK_SIZE);
                        write(editor, "componentOptions", List.of(option));
                        require(editor.selectComponent(fixture.client, option), "Flag component selection was blocked");
                        write(editor, "useGenericSelectedComponentEditor", true);
                        ((ComponentFormPanel) read(editor, "componentForm")).restorePending(JsonParser.parseString("64"), JsonParser.parseString("32"));
                    } else if (conflict.equals("generated_variant")) {
                        addComponentRepresentation(fixture, editor, "fixed_variant");
                        ((ComponentFormPanel) read(editor, "componentForm")).restorePending(representationValue("fixed_variant", false), JsonParser.parseString("\"wild\""));
                    } else expectedTags = addDiamondFirePending(fixture, editor);
                    ItemStack before = editor.draft().copy();
                    Object snapshot = invoke(editor, "captureEditorSnapshot", new Class<?>[]{Minecraft.class}, fixture.client);
                    ComponentOption selected = editor.selectedComponent();
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    Object retained = invoke(editor, "captureEditorSnapshot", new Class<?>[]{Minecraft.class}, fixture.client);
                    String route = conflict.equals("raw") ? "RAW" : conflict.equals("generated_flags") ? "FLAGS"
                        : conflict.equals("generated_variant") ? "COMPONENTS" : "DIAMONDFIRE";
                    require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals(route)
                            && editor.selectedComponent() == selected && (boolean) invoke(snapshot, "sameState", new Class<?>[]{snapshot.getClass()}, retained)
                            && ItemStack.matches(before, editor.draft()) && ItemStack.matches(before, fixture.slots.get(0))
                            && !fixture.slots.containsKey(1) && fixture.writes.isEmpty()
                            && ((Deque<?>) read(editor, "editorUndo")).isEmpty() && !((ItemEditHistory) read(editor, "history")).canUndo(),
                        conflict + " conflict changed a representation, target, baseline or history");
                    // Explicit user-like discard of one owner is distinct from recovery, which changed neither.
                    switch (conflict) {
                        case "raw" ->
                            ((RawStackEditorPanel) read(editor, "rawStackEditor")).sync(fixture.client, editor.draft());
                        case "generated_flags", "generated_variant" ->
                            ((ComponentFormPanel) read(editor, "componentForm")).discardChanges();
                        case "data_diamondfire_keep_data" ->
                            ((DiamondFireTagEditorPanel) read(editor, "diamondFireTagEditor")).sync(editor.draft());
                        case "data_diamondfire_keep_tags" ->
                            ((DataEditorPanel) read(editor, "dataEditor")).sync(editor.draft());
                        default -> throw new AssertionError(conflict);
                    }
                    ItemStack saved = verifyActionHistory(fixture, editor, action, conflict + " resolution");
                    assertCombined(saved);
                    if (conflict.equals("generated_variant"))
                        assertComponentRepresentation(fixture, saved, "fixed_variant");
                    if (conflict.equals("data_diamondfire_keep_tags"))
                        require(saved.get(DataComponents.CUSTOM_DATA).copyTag().equals(expectedTags), "Resolving Data conflict lost pending tags");
                    if (conflict.equals("data_diamondfire_keep_data"))
                        require(saved.get(DataComponents.CUSTOM_DATA).copyTag().equals(TagParser.parseCompoundFully("{commit_check:42}")), "Resolving tag conflict lost pending Data");
                }
            }
        }
    }

    private static void verifyInlinePreviewIsolation() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ItemEditor editor = fixture.editedEditor();
            addInlinePending(editor);
            var recipes = (RecipesEditorPanel) read(editor, "recipesEditor");
            var bees = (BeesEditorPanel) read(editor, "beesEditor");
            CodecComponentState state = (CodecComponentState) read(recipes, "recipes");
            state.setNbtValue(fixture.client, new net.minecraft.nbt.ListTag());
            JsonElement jsonBefore = state.value().deepCopy();
            Object nbtBefore = read(state, "nbtValue");
            List<?> recipeRows = List.copyOf((List<?>) read(recipes, "rows"));
            List<?> beeRows = List.copyOf((List<?>) read(bees, "rows"));
            ItemStack before = editor.draft().copy();
            require(recipes.isDirty() && bees.isDirty() && state.value().equals(jsonBefore) && read(state, "nbtValue") == nbtBefore,
                "Inline dirty comparison mutated a pending Recipes JSON/NBT model");
            ItemStack recipePreview = recipes.pendingStack(fixture.client);
            ItemStack beePreview = bees.pendingStack(fixture.client);
            require(state.value().equals(jsonBefore) && read(state, "nbtValue") == nbtBefore
                    && recipeRows.equals(read(recipes, "rows")) && beeRows.equals(read(bees, "rows"))
                    && ItemStack.matches(before, editor.draft()) && fixture.writes.isEmpty(),
                "Inline previews changed source models, row identity, draft or inventory");
            assertRecipes(fixture.client, recipePreview);
            assertBees(beePreview);
        }
    }

    private static void verifyInlineCommits() throws Exception {
        for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
            try (Fixture fixture = new Fixture()) {
                ItemEditor editor = fixture.editedEditor();
                addInlinePending(editor);
                ItemStack targetBefore = fixture.slots.get(0).copy();
                captureHistory(editor, fixture.client);
                int undoBefore = ((Deque<?>) read(editor, "editorUndo")).size();
                var prepared = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                require(prepared.succeeded() && prepared.included().containsAll(List.of("recipes", "bees")), "Inline owners were omitted from global composition");
                invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                captureHistory(editor, fixture.client);
                ItemStack saved = fixture.slots.get(action.equals("giveDraftCopy") ? 1 : 0);
                require((boolean) read(editor, "statusSuccess") && fixture.writes.size() == 1
                        && ItemStack.matches(prepared.candidate(), saved) && !editor.hasPendingNonRawEditorChanges()
                        && ((Deque<?>) read(editor, "editorUndo")).size() == undoBefore + 1,
                    "Inline global " + action + " did not produce one combined clean commit");
                assertCombined(saved);
                assertRecipes(fixture.client, saved);
                assertBees(saved);
                if (action.equals("giveDraftCopy"))
                    require(ItemStack.matches(targetBefore, fixture.slots.get(0)), "Inline Give Copy changed its source target");
                ItemStack persistent = ItemStackCodecs.decodeStackSnbt(fixture.client, ItemStackCodecs.encodeStackSnbt(fixture.client, saved));
                assertRecipes(fixture.client, persistent);
                assertBees(persistent);
                invoke(editor, "undo", new Class<?>[]{Minecraft.class}, fixture.client);
                var undone = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                require(undone.succeeded() && ItemStack.matches(prepared.candidate(), undone.candidate())
                    && ((RecipesEditorPanel) read(editor, "recipesEditor")).isDirty() && ((BeesEditorPanel) read(editor, "beesEditor")).isDirty()
                    && fixture.writes.size() == 1, "Inline combined Undo lost pending components or wrote inventory");
                invoke(editor, "redo", new Class<?>[]{Minecraft.class}, fixture.client);
                require(!editor.hasPendingNonRawEditorChanges() && ItemStack.matches(saved, editor.draft()) && fixture.writes.size() == 1,
                    "Inline Redo did not restore clean committed components");
            }
        }
    }

    private static void verifyInlineFailures() throws Exception {
        for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
            for (String invalid : List.of("recipe_blank", "recipe_id", "bee_id", "bee_unknown", "bee_snbt", "bee_reserved", "late_data")) {
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor();
                    addInlinePending(editor);
                    captureHistory(editor, fixture.client);
                    var recipes = (RecipesEditorPanel) read(editor, "recipesEditor");
                    var bees = (BeesEditorPanel) read(editor, "beesEditor");
                    List<?> recipeRows = List.copyOf((List<?>) read(recipes, "rows")), beeRows = List.copyOf((List<?>) read(bees, "rows"));
                    Object recipeRow = recipeRows.get(1), beeRow = beeRows.get(1);
                    Object beeCode = read(beeRow, "editor");
                    write(beeCode, "cursorByteOffset", 4);
                    write(beeCode, "cursorColumn", 3);
                    ImString rejectedSource;
                    if (invalid.startsWith("recipe")) {
                        rejectedSource = (ImString) read(recipeRow, "value");
                        rejectedSource.set(invalid.equals("recipe_blank") ? "  " : "not a recipe id  ");
                    } else if (invalid.startsWith("bee")) {
                        rejectedSource = (ImString) read(beeRow, invalid.equals("bee_id") || invalid.equals("bee_unknown") ? "entityType" : "snbt");
                        rejectedSource.set(switch (invalid) {
                            case "bee_id" -> "not an entity id  ";
                            case "bee_unknown" -> "itemlab:unknown_entity";
                            case "bee_snbt" -> "{unfinished:";
                            default -> "{id:\"minecraft:bee\",b:1b,l:2L}";
                        });
                    } else {
                        Object data = read(editor, "dataEditor");
                        write(data, "snbtMode", true);
                        rejectedSource = (ImString) read(data, "snbtSource");
                        rejectedSource.set("{unfinished:");
                        write(data, "dirty", true);
                    }
                    String sourceBefore = rejectedSource.get();
                    CodecComponentState state = (CodecComponentState) read(recipes, "recipes");
                    JsonElement modelBefore = state.value().deepCopy();
                    Object cachedNbt = read(state, "nbtValue");
                    ItemStack draftBefore = editor.draft().copy(), targetBefore = fixture.slots.get(0).copy();
                    Object history = read(editor, "history");
                    List<?> undoBefore = List.copyOf((Deque<?>) read(editor, "editorUndo"));
                    var loreBefore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                    var dataBefore = ((DataEditorPanel) read(editor, "dataEditor")).historyState();
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    require(!(boolean) read(editor, "statusSuccess") && ItemStack.matches(draftBefore, editor.draft())
                            && ItemStack.matches(targetBefore, fixture.slots.get(0)) && !fixture.slots.containsKey(1) && fixture.writes.isEmpty()
                            && read(editor, "history") == history && !((ItemEditHistory) history).canUndo()
                            && undoBefore.equals(List.copyOf((Deque<?>) read(editor, "editorUndo"))) && rejectedSource.get().equals(sourceBefore)
                            && recipeRows.equals(read(recipes, "rows")) && beeRows.equals(read(bees, "rows"))
                            && (int) read(beeCode, "cursorByteOffset") == 4 && (int) read(beeCode, "cursorColumn") == 3
                            && state.value().equals(modelBefore) && read(state, "nbtValue") == cachedNbt && recipes.isDirty() && bees.isDirty()
                            && loreBefore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                            && dataBefore.sameState(((DataEditorPanel) read(editor, "dataEditor")).historyState()),
                        "Inline " + invalid + " failure changed draft/targets/models/input/history: " + read(editor, "status"));
                    if (invalid.startsWith("recipe")) {
                        require(read(editor, "pendingComponentRecovery").equals("recipes") && (long) read(recipes, "focusRowId") == (long) read(recipeRow, "id"), "Recipe recovery missed the invalid row");
                        rejectedSource.set(" itemlab:unregistered_recipe ");
                    } else if (invalid.startsWith("bee")) {
                        require(read(editor, "pendingComponentRecovery").equals("bees") && (long) read(bees, "focusRowId") == (long) read(beeRow, "id")
                            && (long) read(bees, "focusEntityDataRowId") == (invalid.equals("bee_snbt") || invalid.equals("bee_reserved") ? (long) read(beeRow, "id") : 0L), "Bee recovery missed the invalid row/source");
                        rejectedSource.set(invalid.equals("bee_id") || invalid.equals("bee_unknown") ? "minecraft:pig" : inlineBeeTag(1).toString());
                    } else {
                        require(read(editor, "requestedEditorTab").toString().equals("DATA"), "Late Data failure was misrouted");
                        rejectedSource.set("{commit_check:42}");
                    }
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    ItemStack saved = fixture.slots.get(action.equals("giveDraftCopy") ? 1 : 0);
                    require((boolean) read(editor, "statusSuccess") && fixture.writes.size() == 1 && !editor.hasPendingNonRawEditorChanges()
                            && read(editor, "pendingComponentRecovery") == null && (long) read(recipes, "focusRowId") == 0L
                            && (long) read(bees, "focusRowId") == 0L && (long) read(bees, "focusEntityDataRowId") == 0L,
                        "Inline repair did not save together or clear baselines/recovery: " + read(editor, "status"));
                    assertCombined(saved);
                    assertRecipes(fixture.client, saved);
                    assertBees(saved);
                }
            }
        }
    }

    private static void verifyDisabledInlineComponents() throws Exception {
        for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
            try (Fixture fixture = new Fixture()) {
                ItemEditor seed = fixture.editedEditor();
                addInlinePending(seed);
                ItemStack installed = ((RecipesEditorPanel) read(seed, "recipesEditor")).pendingStack(fixture.client);
                ItemStack bees = ((BeesEditorPanel) read(seed, "beesEditor")).pendingStack(fixture.client);
                installed.set(DataComponents.BEES, bees.get(DataComponents.BEES));
                ItemEditor editor = fixture.editedEditor(installed);
                Object recipes = read(editor, "recipesEditor"), beePanel = read(editor, "beesEditor");
                ((CodecComponentState) read(recipes, "recipes")).setPresent(false);
                ((ImString) read(((List<?>) read(recipes, "rows")).getFirst(), "value")).set("not a recipe id");
                write(recipes, "dirty", true);
                write(beePanel, "present", false);
                ((ImString) read(((List<?>) read(beePanel, "rows")).getFirst(), "snbt")).set("{unfinished:");
                write(beePanel, "dirty", true);
                invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                ItemStack saved = fixture.slots.get(action.equals("giveDraftCopy") ? 1 : 0);
                require((boolean) read(editor, "statusSuccess") && fixture.writes.size() == 1 && !saved.has(DataComponents.RECIPES)
                        && !saved.has(DataComponents.BEES) && !editor.hasPendingNonRawEditorChanges(),
                    "Inactive inline buffers blocked deliberate component removal: " + read(editor, "status"));
                assertCombined(saved);
            }
            try (Fixture fixture = new Fixture()) {
                ItemEditor editor = fixture.editedEditor();
                addInlinePending(editor);
                Object recipes = read(editor, "recipesEditor"), bees = read(editor, "beesEditor");
                ((List<?>) read(recipes, "rows")).clear();
                ((List<?>) read(bees, "rows")).clear();
                invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                ItemStack saved = fixture.slots.get(action.equals("giveDraftCopy") ? 1 : 0);
                require((boolean) read(editor, "statusSuccess") && fixture.writes.size() == 1
                    && saved.has(DataComponents.RECIPES) && saved.get(DataComponents.RECIPES).isEmpty()
                    && saved.has(DataComponents.BEES) && saved.get(DataComponents.BEES).bees().isEmpty()
                    && ((CodecComponentState) read(recipes, "recipes")).present() && (boolean) read(bees, "present")
                    && !editor.hasPendingNonRawEditorChanges(), "Enabled empty inline lists were removed instead of committed");
                assertCombined(saved);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void addInlinePending(ItemEditor editor) throws Exception {
        Object recipes = read(editor, "recipesEditor");
        ((CodecComponentState) read(recipes, "recipes")).setPresent(true);
        List<Object> recipeRows = (List<Object>) read(recipes, "rows");
        for (String id : List.of("minecraft:acacia_boat", " itemlab:unregistered_recipe ", "minecraft:acacia_boat")) {
            recipeRows.add(invoke(recipes, "newRow", new Class<?>[]{String.class}, id));
        }
        write(recipes, "dirty", true);
        Object bees = read(editor, "beesEditor");
        write(bees, "present", true);
        List<Object> beeRows = (List<Object>) read(bees, "rows");
        for (int index = 0; index < 4; index++) {
            beeRows.add(invoke(bees, "newRow", new Class<?>[]{BeesDraft.Occupant.class},
                new BeesDraft.Occupant(index == 1 ? "minecraft:pig" : "minecraft:bee", inlineBeeTag(index), index == 1 ? -25 : 123 + index, index == 1 ? -50 : 1200 + index)));
        }
        write(bees, "dirty", true);
    }

    private static CompoundTag inlineBeeTag(int index) throws Exception {
        return TagParser.parseCompoundFully("{row:" + index + ",b:1b,l:2L,f:3.5f,d:4.5d,nested:{typed:9L}}");
    }

    private static void assertRecipes(Minecraft client, ItemStack stack) {
        require(ItemStackCodecs.encodeComponent(client, DataComponents.RECIPES, stack.get(DataComponents.RECIPES))
                .equals(RecipesDraft.fromStrings(true, List.of("minecraft:acacia_boat", "itemlab:unregistered_recipe", "minecraft:acacia_boat")).toJson()),
            "Inline Recipes output lost order, duplicates or a native-valid unregistered key");
    }

    private static void assertBees(ItemStack stack) throws Exception {
        BeesDraft bees = BeesDraft.fromStack(stack);
        require(bees.present() && bees.size() == 4, "Inline Bees output lost occupants or applied the UI capacity warning as a limit");
        for (int index = 0; index < 4; index++) {
            var occupant = bees.occupant(index);
            require(occupant.entityType().equals(index == 1 ? "minecraft:pig" : "minecraft:bee") && occupant.entityData().equals(inlineBeeTag(index))
                    && occupant.ticksInHive() == (index == 1 ? -25 : 123 + index) && occupant.minTicksInHive() == (index == 1 ? -50 : 1200 + index),
                "Inline Bees output changed row order, entity type, signed times or typed SNBT");
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifyGroupedFailureFocus() throws Exception {
        for (String owner : List.of("equipment", "map", "decoratedPot")) {
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor();
                    Object panel = read(editor, owner + "Editor");
                    String section;
                    String field;
                    if (owner.equals("equipment")) {
                        ((CodecComponentState) read(panel, "equippable")).setValue(
                            JsonParser.parseString("{\"slot\":\"head\",\"asset_id\":\"not an id\"}"));
                        section = "equippable";
                        field = "equippable/asset";
                    } else if (owner.equals("map")) {
                        ((ImBoolean) read(panel, "decorationsPresent")).set(true);
                        Object row = invoke(panel, "createDecorationRow", new Class<?>[0]);
                        ((List<Object>) read(panel, "decorations")).add(row);
                        section = "map_decorations";
                        field = "row/" + read(row, "rowId") + "/name";
                    } else {
                        ((ImBoolean) read(panel, "decorationsPresent")).set(true);
                        Map<?, ImString> sides = (Map<?, ImString>) read(panel, "sideValues");
                        sides.entrySet().stream().filter(entry -> entry.getKey().toString().equals("LEFT"))
                            .findFirst().orElseThrow().getValue().set("not an item id");
                        section = "pot_decorations";
                        field = "side/left";
                    }
                    write(panel, "dirty", true);
                    ItemStack before = editor.draft().copy();
                    ItemStack targetBefore = fixture.slots.get(0).copy();
                    var loreBefore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    String tab = owner.equals("decoratedPot") ? "DECORATED_POT" : owner.toUpperCase(java.util.Locale.ROOT);
                    require(!(boolean) read(editor, "statusSuccess") && section.equals(read(panel, "pendingFocusSection"))
                        && field.equals(read(panel, "pendingFocusField"))
                        && read(editor, "requestedEditorTab").toString().equals(tab), owner + " failed to route/focus its precise field on " + action);
                    require(!(boolean) read(panel, "focusPendingToolbar"), owner + " still requested its action toolbar");
                    require(ItemStack.matches(before, editor.draft()) && ItemStack.matches(targetBefore, fixture.slots.get(0))
                        && fixture.writes.isEmpty() && !fixture.slots.containsKey(1), owner + " failure partially wrote a result");
                    require((boolean) read(panel, "dirty")
                        && loreBefore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                        && !((ItemEditHistory) read(editor, "history")).canUndo(), owner + " recovery changed a baseline/history");
                    if (owner.equals("equipment")) {
                        require(((CodecComponentState) read(panel, "equippable")).value().getAsJsonObject()
                            .get("asset_id").getAsString().equals("not an id"), "Equipment recovery lost invalid input");
                    } else if (owner.equals("map")) {
                        Object row = ((List<?>) read(panel, "decorations")).getFirst();
                        require(((ImString) read(row, "name")).get().isEmpty()
                            && (long) read(panel, "focusDecorationRowId") == (long) read(row, "rowId"), "Map recovery lost its row");
                    } else {
                        require(((Map<?, ImString>) read(panel, "sideValues")).values().stream()
                            .anyMatch(value -> value.get().equals("not an item id")), "Pot recovery lost invalid input");
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifyAppearanceAndBookFailureFocus() throws Exception {
        for (String problem : List.of("translation_key", "item_model", "tooltip_style", "title", "page")) {
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor();
                    boolean bookProblem = problem.equals("title") || problem.equals("page");
                    Object panel = read(editor, bookProblem ? "bookEditor" : "appearanceEditor");
                    ImString source;
                    RichTextLine line = null;
                    CodecComponentState identifier = null;
                    String section;
                    String field;
                    if (problem.equals("translation_key")) {
                        write(panel, "itemNamePresent", true);
                        write(panel, "itemNameChanged", true);
                        source = (ImString) read(panel, "itemNameTranslationKey");
                        source.set("   ");
                        section = "item_name";
                        field = problem;
                    } else if (!bookProblem) {
                        identifier = (CodecComponentState) read(panel, problem.equals("item_model") ? "itemModel" : "tooltipStyle");
                        identifier.setPresent(true);
                        identifier.setValue(JsonParser.parseString("\"not an id\""));
                        source = new ImString("not an id", 128);
                        ((Map<String, ImString>) read(panel, "textBuffers")).put(problem, source);
                        section = problem;
                        field = "identifier";
                    } else if (problem.equals("title")) {
                        write(panel, "writtenPresent", true);
                        write(panel, "writtenDirty", true);
                        write(panel, "showWritten", false);
                        line = text("t".repeat(WrittenBookContent.TITLE_MAX_LENGTH + 1));
                        write(panel, "title", line);
                        source = line.editorSource(false, false);
                        section = "written_book_metadata";
                        field = "title";
                    } else {
                        write(panel, "writablePresent", true);
                        invoke(panel, "addPage", new Class<?>[0]);
                        invoke(panel, "addPage", new Class<?>[0]);
                        Object page = ((List<?>) read(panel, "writablePages")).get(1);
                        line = (RichTextLine) read(page, "text");
                        source = line.editorSource(false, false);
                        source.set("p".repeat(WritableBookContent.PAGE_EDIT_LENGTH + 1));
                        line.commitEditorSource();
                        write(panel, "showWritten", true);
                        section = "writable_book_pages";
                        field = "page/1";
                    }
                    write(panel, "dirty", true);
                    String pending = source.get();
                    ItemStack before = editor.draft().copy();
                    ItemStack targetBefore = fixture.slots.get(0).copy();
                    var loreBefore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    require(!(boolean) read(editor, "statusSuccess") && section.equals(read(panel, "pendingFocusSection"))
                            && field.equals(read(panel, "pendingFocusField")) && !(boolean) read(panel, "focusPendingToolbar")
                            && read(editor, "requestedEditorTab").toString().equals(bookProblem ? "BOOK" : "PRESENTATION"),
                        problem + " did not route to its precise field on " + action);
                    require(ItemStack.matches(before, editor.draft()) && ItemStack.matches(targetBefore, fixture.slots.get(0))
                            && fixture.writes.isEmpty() && !fixture.slots.containsKey(1)
                            && !((ItemEditHistory) read(editor, "history")).canUndo()
                            && loreBefore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                            && pending.equals(source.get()) && (boolean) read(panel, "dirty"),
                        problem + " failure changed draft/target/history or lost pending input");
                    if (bookProblem) {
                        require((boolean) read(panel, "showWritten") == problem.equals("page")
                            && (boolean) invoke(panel, "viewingWritten", new Class<?>[0]) == problem.equals("title")
                            && line.editorSource(false, false) == source, "Book recovery changed signing intent or replaced the buffer");
                        source.set("Repaired");
                        line.commitEditorSource();
                    } else if (identifier != null) {
                        require(((Map<String, ImString>) read(panel, "textBuffers")).get(problem) == source
                            && identifier.value().getAsString().equals(pending), "Appearance recovery replaced a buffer/model");
                        source.set("minecraft:diamond");
                        identifier.setValue(JsonParser.parseString("\"minecraft:diamond\""));
                    } else {
                        source.set("item.minecraft.stone");
                    }
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    require((boolean) read(editor, "statusSuccess") && fixture.writes.size() == 1,
                        problem + " failed to save all retained edits after repair");
                    ItemStack saved = fixture.slots.get(action.equals("giveDraftCopy") ? 1 : 0);
                    assertCombined(saved);
                    if (problem.equals("title")) {
                        require(saved.get(DataComponents.WRITTEN_BOOK_CONTENT).title().raw().equals("Repaired"),
                            "Repaired written title was not included in the output");
                    } else if (problem.equals("page")) {
                        var pages = saved.get(DataComponents.WRITABLE_BOOK_CONTENT).pages();
                        require(pages.size() == 2 && pages.get(1).raw().equals("Repaired"),
                            "Repaired later writable page was not included in the output");
                    } else if (problem.equals("translation_key")) {
                        require(saved.get(DataComponents.ITEM_NAME).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents name
                            && name.getKey().equals("item.minecraft.stone"), "Repaired translation key was not included in the output");
                    } else if (problem.equals("tooltip_style")) {
                        require(saved.get(DataComponents.TOOLTIP_STYLE).toString().equals("minecraft:diamond"),
                            "Repaired tooltip style was not included in the output");
                    }
                    require(!(boolean) read(panel, "dirty") && read(panel, "pendingFocusSection") == null
                            && (!bookProblem || read(panel, "recoveryWritten") == null),
                        problem + " retained a stale recovery request after committing");
                }
            }
        }
    }

    private static void verifyAppearanceCodecRecovery() throws Exception {
        for (String[] failure : List.of(
            new String[]{"customModelData", "custom_model_data", "\"wrong representation\""},
            new String[]{"dyedColor", "dyed_color", "\"not a color\""},
            new String[]{"dye", "dye", "\"not a dye\""})) {
            try (Fixture fixture = new Fixture()) {
                ItemEditor editor = fixture.editedEditor();
                Object appearance = read(editor, "appearanceEditor");
                CodecComponentState state = (CodecComponentState) read(appearance, failure[0]);
                state.setPresent(true);
                state.setValue(JsonParser.parseString(failure[2]));
                JsonElement before = state.value().deepCopy();
                invoke(editor, "applyDraftToSlot", new Class<?>[]{Minecraft.class}, fixture.client);
                require(!(boolean) read(editor, "statusSuccess") && fixture.writes.isEmpty()
                        && failure[1].equals(read(appearance, "pendingFocusSection"))
                        && "$enabled".equals(read(appearance, "pendingFocusField")) && before.equals(state.value()),
                    "Appearance codec-only failure did not reveal its section without normalizing it");
            }
        }
    }

    private static void verifyWrittenBookRecoveryTargets() throws Exception {
        try (TextBridge ignored = new TextBridge(); Fixture fixture = new Fixture()) {
            Object book = read(fixture.editedEditor(), "bookEditor");
            ItemStack source = new ItemStack(Items.WRITTEN_BOOK);
            source.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Title"), "Author", 0,
                List.of(Filterable.passThrough(Component.literal("First")), Filterable.passThrough(Component.literal("Second"))), false));
            invoke(book, "sync", new Class<?>[]{ItemStack.class}, source);
            RichTextLine invalid = text(INVALID_MINI_MESSAGE, TextInputFormat.MINI_MESSAGE);
            require(!invalid.validationError().isBlank(), "Expected a real native-invalid MiniMessage Book input");
            write(book, "author", invalid);
            invoke(book, "requestPendingFocus", new Class<?>[0]);
            require("written_book_metadata".equals(read(book, "pendingFocusSection")) && "author".equals(read(book, "pendingFocusField"))
                && read(book, "author") == invalid, "Book author error did not target the author field");
            write(book, "author", text("Author"));
            Object page = ((List<?>) read(book, "writtenPages")).get(1);
            write(page, "text", invalid);
            invoke(book, "requestPendingFocus", new Class<?>[0]);
            require("written_book_pages".equals(read(book, "pendingFocusSection")) && "page/1".equals(read(book, "pendingFocusField"))
                    && (int) read(book, "writtenPage") == 1 && read(page, "text") == invalid,
                "Book rich-page error did not target its exact page");
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifyBannerPotionFireworkFailures() throws Exception {
        for (String problem : List.of("banner_layer", "banner_provided", "potion_base", "potion_effect", "potion_hidden_strength",
            "potion_scale", "firework_flight", "firework_shape", "firework_color")) {
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor();
                    String owner = problem.substring(0, problem.indexOf('_'));
                    Object panel = read(editor, owner + "Editor");
                    CodecComponentState state = null;
                    ImString source = null;
                    String section;
                    String field;
                    switch (problem) {
                        case "banner_layer" -> {
                            ((ImBoolean) read(panel, "patternsPresent")).set(true);
                            List<Object> rows = (List<Object>) read(panel, "patterns");
                            rows.add(invoke(panel, "createPatternRow", new Class<?>[0]));
                            Object row = invoke(panel, "createPatternRow", new Class<?>[0]);
                            rows.add(row);
                            source = (ImString) read(row, "patternId");
                            source.set("not a pattern id");
                            write(row, "color", DyeColor.RED);
                            section = "patterns";
                            field = "row/" + read(row, "rowId") + "/id";
                        }
                        case "banner_provided" -> {
                            state = (CodecComponentState) read(panel, "providedPatterns");
                            state.setPresent(true);
                            state.setValue(JsonParser.parseString("[\"minecraft:base\",\"not a pattern id\"]"));
                            source = new ImString("not a pattern id", 128);
                            ((Map<String, ImString>) read(panel, "textBuffers")).put("banner/provided_patterns/@identifier/1", source);
                            section = "provides_banner_patterns";
                            field = "identifiers";
                        }
                        case "potion_base", "potion_effect", "potion_hidden_strength" -> {
                            state = (CodecComponentState) read(panel, "contents");
                            state.setPresent(true);
                            state.setValue(JsonParser.parseString(switch (problem) {
                                case "potion_base" -> "{\"potion\":\"not a potion id\"}";
                                case "potion_effect" ->
                                    "{\"potion\":\"minecraft:water\",\"custom_effects\":[{\"id\":\"minecraft:speed\"},{\"id\":\"not an effect id\",\"duration\":200}]}";
                                default ->
                                    "{\"custom_effects\":[{\"id\":\"minecraft:speed\",\"hidden_effect\":{\"amplifier\":\"bad strength\"}}]}";
                            }));
                            section = "contents";
                            if (problem.equals("potion_base")) field = "base_potion";
                            else {
                                invoke(panel, "ensureEffectRowIds", new Class<?>[]{int.class}, problem.equals("potion_effect") ? 2 : 1);
                                List<Long> ids = (List<Long>) read(panel, "effectRowIds");
                                field = "effect_" + ids.getLast() + (problem.equals("potion_effect") ? "/id" : "_hidden/strength");
                            }
                        }
                        case "potion_scale" -> {
                            state = (CodecComponentState) read(panel, "durationScale");
                            state.setPresent(true);
                            state.setValue(JsonParser.parseString("-1"));
                            section = "duration_scale";
                            field = "value";
                        }
                        case "firework_flight", "firework_shape" -> {
                            state = (CodecComponentState) read(panel, "fireworks");
                            state.setPresent(true);
                            state.setValue(JsonParser.parseString(problem.equals("firework_flight")
                                ? "{\"flight_duration\":\"bad duration\",\"explosions\":[]}"
                                : "{\"flight_duration\":1,\"explosions\":[{\"shape\":\"small_ball\"},{\"shape\":\"unknown shape\",\"colors\":[16711680]}]}"));
                            section = "rocket";
                            if (problem.equals("firework_flight")) field = "flight_duration";
                            else {
                                invoke(panel, "ensureExplosionRowIds", new Class<?>[]{int.class}, 2);
                                field = "rocket_explosion_" + ((List<Long>) read(panel, "explosionRowIds")).getLast() + "/shape";
                            }
                        }
                        default -> {
                            state = (CodecComponentState) read(panel, "starExplosion");
                            state.setPresent(true);
                            state.setValue(JsonParser.parseString("{\"shape\":\"star\",\"colors\":[16777215,{}]}"));
                            ((Map<String, List<Long>>) read(panel, "colorRowIds")).put("star_explosion/primary", new ArrayList<>(List.of(101L, 102L)));
                            section = "star";
                            field = "star_explosion/primary/row/102";
                        }
                    }
                    write(panel, "dirty", true);
                    JsonElement pendingValue = state == null ? null : state.value().deepCopy();
                    String pendingText = source == null ? null : source.get();
                    ItemStack before = editor.draft().copy();
                    ItemStack targetBefore = fixture.slots.get(0).copy();
                    var loreBefore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    require(!(boolean) read(editor, "statusSuccess") && section.equals(read(panel, "pendingFocusSection"))
                            && field.equals(read(panel, "pendingFocusField")) && !(boolean) read(panel, "focusPendingToolbar")
                            && read(editor, "requestedEditorTab").toString().equals(owner.toUpperCase(java.util.Locale.ROOT)),
                        problem + " did not target its precise field on " + action);
                    require(ItemStack.matches(before, editor.draft()) && ItemStack.matches(targetBefore, fixture.slots.get(0))
                            && fixture.writes.isEmpty() && !fixture.slots.containsKey(1) && !((ItemEditHistory) read(editor, "history")).canUndo()
                            && loreBefore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                            && (state == null || pendingValue.equals(state.value())) && (source == null || pendingText.equals(source.get())),
                        problem + " recovery wrote a partial result or changed pending input/history");
                    if (problem.equals("banner_provided")) {
                        require((int) read(read(panel, "providedPatternsFocus"), "requestedRow") == 1,
                            "Banner recovery selected the wrong provided-pattern input");
                    }
                    switch (problem) {
                        case "banner_layer" -> source.set("minecraft:stripe_bottom");
                        case "banner_provided" -> {
                            source.set("minecraft:stripe_bottom");
                            state.value().getAsJsonArray().set(1, JsonParser.parseString("\"minecraft:stripe_bottom\""));
                        }
                        case "potion_base" -> state.value().getAsJsonObject().addProperty("potion", "minecraft:water");
                        case "potion_effect" ->
                            state.value().getAsJsonObject().getAsJsonArray("custom_effects").get(1).getAsJsonObject()
                                .addProperty("id", "minecraft:speed");
                        case "potion_hidden_strength" ->
                            state.value().getAsJsonObject().getAsJsonArray("custom_effects").get(0).getAsJsonObject()
                                .getAsJsonObject("hidden_effect").addProperty("amplifier", 1);
                        case "potion_scale" -> state.setValue(JsonParser.parseString("2.5"));
                        case "firework_flight" -> state.value().getAsJsonObject().addProperty("flight_duration", 3);
                        case "firework_shape" ->
                            state.value().getAsJsonObject().getAsJsonArray("explosions").get(1).getAsJsonObject()
                                .addProperty("shape", "star");
                        default ->
                            state.value().getAsJsonObject().getAsJsonArray("colors").set(1, JsonParser.parseString("16711680"));
                    }
                    if (problem.equals("potion_hidden_strength")) {
                        // Fixing the parse error is insufficient: Vanilla's codec would still drop the fallback.
                        JsonElement repairedValue = state.value().deepCopy();
                        invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                        long rowId = ((List<Long>) read(panel, "effectRowIds")).getFirst();
                        require(!(boolean) read(editor, "statusSuccess") && "contents".equals(read(panel, "pendingFocusSection"))
                                && ("effect_" + rowId + "/hidden_effect").equals(read(panel, "pendingFocusField"))
                                && ItemStack.matches(before, editor.draft()) && ItemStack.matches(targetBefore, fixture.slots.get(0))
                                && fixture.writes.isEmpty() && !fixture.slots.containsKey(1)
                                && !((ItemEditHistory) read(editor, "history")).canUndo() && repairedValue.equals(state.value())
                                && loreBefore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState()),
                            "Potion fallback serialization loss was silently committed or discarded pending input");
                        state.value().getAsJsonObject().getAsJsonArray("custom_effects").get(0).getAsJsonObject().remove("hidden_effect");
                    }
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    require((boolean) read(editor, "statusSuccess") && fixture.writes.size() == 1,
                        problem + " did not commit the retained edits after repair");
                    ItemStack saved = fixture.slots.get(action.equals("giveDraftCopy") ? 1 : 0);
                    assertCombined(saved);
                    switch (problem) {
                        case "banner_layer" -> {
                            var layers = saved.get(DataComponents.BANNER_PATTERNS).layers();
                            require(layers.size() == 2 && layers.get(1).color() == DyeColor.RED
                                    && layers.get(1).pattern().unwrapKey().orElseThrow().identifier().toString().equals("minecraft:stripe_bottom"),
                                "Repaired Banner layer/color was lost");
                        }
                        case "banner_provided" ->
                            require(saved.get(DataComponents.PROVIDES_BANNER_PATTERNS).size() == 2,
                                "Repaired provided-pattern collection was lost");
                        case "potion_base" ->
                            require(saved.get(DataComponents.POTION_CONTENTS).potion().isPresent(), "Repaired base potion was lost");
                        case "potion_effect" ->
                            require(saved.get(DataComponents.POTION_CONTENTS).customEffects().size() == 2
                                && saved.get(DataComponents.POTION_CONTENTS).customEffects().get(1).getDuration() == 200, "Repaired effect/duration was lost");
                        case "potion_hidden_strength" ->
                            require(saved.get(DataComponents.POTION_CONTENTS).customEffects().size() == 1,
                                "Removing the unsupported fallback discarded the primary effect");
                        case "potion_scale" ->
                            require(saved.get(DataComponents.POTION_DURATION_SCALE) == 2.5f, "Repaired potion scale was lost");
                        case "firework_flight" ->
                            require(saved.get(DataComponents.FIREWORKS).flightDuration() == 3, "Repaired flight duration was lost");
                        case "firework_shape" ->
                            require(saved.get(DataComponents.FIREWORKS).explosions().get(1).shape() == FireworkExplosion.Shape.STAR,
                                "Repaired explosion shape was lost");
                        default ->
                            require(saved.get(DataComponents.FIREWORK_EXPLOSION).colors().getInt(1) == 16711680, "Repaired star color was lost");
                    }
                    require(!(boolean) read(panel, "dirty") && read(panel, "pendingFocusSection") == null,
                        problem + " retained a dirty baseline or stale recovery request after commit");
                    if (problem.equals("banner_provided"))
                        require((int) read(read(panel, "providedPatternsFocus"), "requestedRow") == -1,
                            "Banner synchronization retained collection focus");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifyAdventureFailures() throws Exception {
        for (String problem : List.of("blocks", "property", "exact", "minimum", "maximum", "empty_range",
            "nbt", "components", "predicates", "native_components", "native_predicates", "empty_section", "codec_section")) {
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor();
                    Object panel = read(editor, "adventureEditor");
                    Object canBreak = read(panel, "canBreak");
                    Object canPlace = read(panel, "canPlaceOn");
                    ((ImBoolean) read(canBreak, "present")).set(true);
                    addAdventureRow(canBreak, "minecraft:stone");
                    ((ImBoolean) read(canPlace, "present")).set(true);
                    addAdventureRow(canPlace, "minecraft:stone");
                    Object row = addAdventureRow(canPlace, "minecraft:oak_stairs");
                    String field = "predicate/" + read(row, "id") + "/";
                    ImString source = null;
                    Object stateRow = null;
                    String editorPrefix = null;
                    switch (problem) {
                        case "blocks" -> {
                            write(row, "blocks", JsonParser.parseString("[\"minecraft:stone\",\"not a block id\"]"));
                            source = new ImString("not a block id", 128);
                            ((Map<String, ImString>) read(panel, "textBuffers"))
                                .put("adventure/can_place_on/" + read(row, "id") + "/blocks/@identifier/1", source);
                            field += "blocks";
                        }
                        case "property", "exact", "minimum", "maximum", "empty_range" -> {
                            ((ImBoolean) read(row, "statePresent")).set(true);
                            var constructor = Class.forName("dev.nineofgaming.itemlab.editor.panel.AdventureEditorPanel$StateRow")
                                .getDeclaredConstructor(String.class, String.class);
                            constructor.setAccessible(true);
                            stateRow = constructor.newInstance(problem.equals("property") ? "not_a_property" : "half",
                                problem.equals("exact") ? "invalid half" : "bottom");
                            ((List<Object>) read(row, "states")).add(stateRow);
                            String control = problem.equals("property") ? "name" : problem.equals("empty_range") ? "minimum" : problem;
                            field += "state/" + read(stateRow, "id") + "/" + control;
                            if (!problem.equals("property") && !problem.equals("exact")) {
                                ((ImInt) read(stateRow, "mode")).set(1);
                                ((ImString) read(stateRow, "minimum")).set(problem.equals("minimum") ? "invalid minimum" : problem.equals("empty_range") ? "" : "bottom");
                                ((ImString) read(stateRow, "maximum")).set(problem.equals("maximum") ? "invalid maximum" : "");
                            }
                            source = (ImString) read(stateRow, control);
                        }
                        case "empty_section" -> {
                            ((List<Object>) read(canPlace, "predicates")).clear();
                            field = "add_predicate";
                        }
                        case "codec_section" -> {
                            ((JsonObject) read(row, "extraFields")).addProperty("state", "wrong representation");
                            field = "$enabled";
                        }
                        default -> {
                            String control = problem.startsWith("native_") ? problem.substring("native_".length()) : problem;
                            editorPrefix = control.equals("predicates") ? "componentPredicates" : control;
                            ((ImBoolean) read(row, editorPrefix + "Present")).set(true);
                            source = (ImString) read(row, editorPrefix + "Source");
                            source.set(problem.startsWith("native_") ? "{\"itemlab:unknown_component\":{}}" : "{broken pending " + control);
                            field += control;
                            Object codeEditor = read(row, editorPrefix + "Editor");
                            write(codeEditor, "cursorByteOffset", 4);
                            write(codeEditor, "cursorColumn", 5);
                        }
                    }
                    write(panel, "dirty", true);
                    ItemStack before = editor.draft().copy();
                    ItemStack targetBefore = fixture.slots.get(0).copy();
                    JsonElement blocksBefore = ((JsonElement) read(row, "blocks")).deepCopy();
                    JsonObject extraBefore = ((JsonObject) read(row, "extraFields")).deepCopy();
                    String textBefore = source == null ? null : source.get();
                    var loreBefore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    require(!(boolean) read(editor, "statusSuccess") && "can_place_on".equals(read(panel, "pendingFocusSection"))
                            && field.equals(read(panel, "pendingFocusField")) && !(boolean) read(panel, "focusPendingToolbar")
                            && read(editor, "requestedEditorTab").toString().equals("ADVENTURE"),
                        "Adventure " + problem + " did not target its precise field on " + action + ": " + read(editor, "status"));
                    require(ItemStack.matches(before, editor.draft()) && ItemStack.matches(targetBefore, fixture.slots.get(0))
                        && fixture.writes.isEmpty() && !fixture.slots.containsKey(1) && !((ItemEditHistory) read(editor, "history")).canUndo()
                        && loreBefore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                        && blocksBefore.equals(read(row, "blocks")) && extraBefore.equals(read(row, "extraFields"))
                        && (source == null || textBefore.equals(source.get()))
                        && (boolean) read(panel, "dirty"), "Adventure " + problem + " changed pending input or partially committed");
                    if (editorPrefix != null) {
                        Object codeEditor = read(row, editorPrefix + "Editor");
                        require((boolean) read(codeEditor, "focusRequested") && (int) read(codeEditor, "cursorByteOffset") == 4
                            && (int) read(codeEditor, "cursorColumn") == 5, "Adventure SNBT recovery reset its cursor or missed its nested input");
                    }
                    switch (problem) {
                        case "blocks" -> {
                            source.set("minecraft:oak_stairs");
                            write(row, "blocks", JsonParser.parseString("[\"minecraft:stone\",\"minecraft:oak_stairs\"]"));
                        }
                        case "property" -> source.set("half");
                        case "exact" -> source.set("bottom");
                        case "minimum", "empty_range" -> source.set("bottom");
                        case "maximum" -> source.set("top");
                        case "empty_section" -> row = addAdventureRow(canPlace, "minecraft:oak_stairs");
                        case "codec_section" -> ((JsonObject) read(row, "extraFields")).remove("state");
                        case "nbt" -> source.set("{repair_check:42}");
                        case "components", "native_components" ->
                            source.set("{\"minecraft:custom_data\":{repair_check:42}}");
                        default -> source.set("{\"minecraft:custom_data\":{repair_check:42}}");
                    }
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    require((boolean) read(editor, "statusSuccess") && fixture.writes.size() == 1,
                        "Adventure " + problem + " failed its repaired combined commit: " + read(editor, "status"));
                    ItemStack saved = fixture.slots.get(action.equals("giveDraftCopy") ? 1 : 0);
                    assertCombined(saved);
                    JsonElement encoded = ItemStackCodecs.encodeComponent(fixture.client, DataComponents.CAN_PLACE_ON, saved.get(DataComponents.CAN_PLACE_ON));
                    JsonObject predicate = encoded.isJsonArray() ? encoded.getAsJsonArray().get(encoded.getAsJsonArray().size() - 1).getAsJsonObject() : encoded.getAsJsonObject();
                    require(saved.has(DataComponents.CAN_BREAK) && saved.has(DataComponents.CAN_PLACE_ON)
                        && predicate.get("blocks").toString().contains("minecraft:oak_stairs"), "Adventure lost another section or repaired block selection");
                    if (stateRow != null) {
                        JsonElement condition = predicate.getAsJsonObject("state").get("half");
                        if (problem.equals("property") || problem.equals("exact"))
                            require(condition.getAsString().equals("bottom"), "Adventure lost the repaired exact state value");
                        else require(condition.getAsJsonObject().get("min").getAsString().equals("bottom")
                            && (problem.equals("maximum") ? condition.getAsJsonObject().get("max").getAsString().equals("top")
                            : !condition.getAsJsonObject().has("max")), "Adventure lost the repaired range values");
                    }
                    if (editorPrefix != null) {
                        String control = editorPrefix.equals("componentPredicates") ? "predicates" : editorPrefix;
                        String expectedSource = control.equals("nbt")
                            ? "{blocks:\"minecraft:oak_stairs\",nbt:\"{repair_check:42}\"}"
                            : "{blocks:\"minecraft:oak_stairs\"," + control + ":{\"minecraft:custom_data\":{repair_check:42}}}";
                        CompoundTag expected = (CompoundTag) ItemStackCodecs.canonicalizeComponentNbt(fixture.client,
                            DataComponents.CAN_PLACE_ON, TagParser.parseCompoundFully(expectedSource));
                        var nativeOutput = ItemStackCodecs.encodeComponentNbt(fixture.client, DataComponents.CAN_PLACE_ON, saved.get(DataComponents.CAN_PLACE_ON));
                        var list = (net.minecraft.nbt.ListTag) nativeOutput;
                        CompoundTag actual = (CompoundTag) list.get(list.size() - 1);
                        require(expected.get(control).equals(actual.get(control)), "Adventure lost the exact repaired " + control + " value");
                    }
                    require(!(boolean) read(panel, "dirty") && read(panel, "pendingFocusSection") == null
                        && (long) read(canPlace, "focusPredicateRowId") == 0L, "Adventure synchronization retained stale recovery/baseline state");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Object addAdventureRow(Object section, String block) throws Exception {
        var constructor = Class.forName("dev.nineofgaming.itemlab.editor.panel.AdventureEditorPanel$PredicateRow")
            .getDeclaredConstructor(JsonObject.class, CompoundTag.class);
        constructor.setAccessible(true);
        JsonObject value = new JsonObject();
        value.addProperty("blocks", block);
        Object row = constructor.newInstance(value, null);
        ((List<Object>) read(section, "predicates")).add(row);
        return row;
    }

    private record ComponentRecoveryCase(String model, String section, JsonElement bad, JsonElement repaired,
                                         String control) {
    }

    private static ComponentRecoveryCase useCase(String model, String section, String repaired, String badPath, String badValue, String control) {
        JsonElement good = JsonParser.parseString(repaired);
        JsonElement bad = good.deepCopy();
        JsonElement replacement = JsonParser.parseString(badValue);
        if (badPath.isEmpty()) bad = replacement;
        else {
            String[] parts = badPath.split("/");
            JsonElement owner = bad;
            for (int index = 0; index < parts.length - 1; index++) {
                owner = owner.isJsonArray() ? owner.getAsJsonArray().get(Integer.parseInt(parts[index])) : owner.getAsJsonObject().get(parts[index]);
            }
            String last = parts[parts.length - 1];
            if (owner.isJsonArray()) owner.getAsJsonArray().set(Integer.parseInt(last), replacement);
            else owner.getAsJsonObject().add(last, replacement);
        }
        return new ComponentRecoveryCase(model, section, bad, good, control);
    }

    @SuppressWarnings("unchecked")
    private static void verifyUseFailures() throws Exception {
        String food = "{\"nutrition\":2,\"saturation\":-2,\"can_always_eat\":true}";
        String consumable = "{\"consume_seconds\":2,\"animation\":\"drink\",\"sound\":\"minecraft:entity.generic.eat\",\"has_consume_particles\":true}";
        String apply = "{\"type\":\"minecraft:apply_effects\",\"probability\":0.5,\"effects\":[{\"id\":\"minecraft:speed\"},{\"id\":\"minecraft:strength\",\"duration\":200,\"amplifier\":256}]}";
        String remove = "{\"type\":\"minecraft:remove_effects\",\"effects\":[\"minecraft:speed\",\"minecraft:strength\"]}";
        String teleport = "{\"type\":\"minecraft:teleport_randomly\",\"diameter\":16}";
        String sound = "{\"type\":\"minecraft:play_sound\",\"sound\":\"minecraft:entity.generic.eat\"}";
        String clear = "{\"type\":\"minecraft:clear_all_effects\"}";
        String consumeApply = "{\"on_consume_effects\":[" + clear + "," + apply + "]}";
        String consumeRemove = "{\"on_consume_effects\":[" + clear + "," + remove + "]}";
        String deathApply = "{\"death_effects\":[" + clear + "," + apply + "]}";
        String deathRemove = "{\"death_effects\":[" + clear + "," + remove + "]}";
        List<ComponentRecoveryCase> cases = List.of(
            useCase("food", "food", food, "nutrition", "-1", "nutrition"),
            useCase("food", "food", food, "saturation", "\"bad saturation\"", "saturation"),
            useCase("food", "food", food, "can_always_eat", "\"bad flag\"", "can_always_eat"),
            useCase("consumable", "consumable", consumable, "consume_seconds", "-1", "consume_seconds"),
            useCase("consumable", "consumable", consumable, "animation", "\"unknown animation\"", "animation"),
            useCase("consumable", "consumable", consumable, "sound", "\"not a sound id\"", "sound"),
            useCase("consumable", "consumable", consumable, "sound", "\"example:unknown_sound\"", "sound"),
            useCase("consumable", "consumable", consumable, "has_consume_particles", "\"bad flag\"", "has_consume_particles"),
            useCase("consumable", "consumable", consumeApply, "on_consume_effects/1/type", "\"example:unknown_effect_type\"", "consume/102/type"),
            useCase("consumable", "consumable", consumeApply, "on_consume_effects/1/probability", "1.1", "consume/102/probability"),
            useCase("consumable", "consumable", consumeApply, "on_consume_effects/1/effects/1/id", "\"not an effect id\"", "consume/102/status/402/id"),
            useCase("consumable", "consumable", consumeApply, "on_consume_effects/1/effects/1/duration", "\"bad duration\"", "consume/102/status/402/duration"),
            useCase("consumable", "consumable", consumeApply, "on_consume_effects/1/effects/1/amplifier", "\"bad strength\"", "consume/102/status/402/amplifier"),
            useCase("consumable", "consumable", consumeApply, "on_consume_effects/1/effects/1/ambient", "\"bad hidden flag\"", "consume/102/type"),
            useCase("consumable", "consumable", consumeRemove, "on_consume_effects/1/effects/1", "\"bad removed effect\"", "consume/102/remove_effect/502/id"),
            useCase("consumable", "consumable", "{\"on_consume_effects\":[" + clear + ",{\"type\":\"minecraft:remove_effects\",\"effects\":\"minecraft:strength\"}]}", "on_consume_effects/1/effects", "\"bad scalar effect\"", "consume/102/type"),
            useCase("consumable", "consumable", "{\"on_consume_effects\":[" + clear + "," + teleport + "]}", "on_consume_effects/1/diameter", "0", "consume/102/diameter"),
            useCase("consumable", "consumable", "{\"on_consume_effects\":[" + clear + "," + sound + "]}", "on_consume_effects/1/sound", "\"bad effect sound\"", "consume/102/sound"),
            useCase("consumable", "consumable", consumable, "", "\"wrong representation\"", "$enabled"),
            useCase("consumable", "consumable", "{\"sound\":{\"sound_id\":\"example:custom_sound\",\"range\":16}}", "sound/sound_id", "\"bad inline sound id\"", "sound"),
            useCase("useEffects", "use_effects", "{\"can_sprint\":true,\"interact_vibrations\":true,\"speed_multiplier\":0.5}", "speed_multiplier", "1.1", "speed_multiplier"),
            useCase("useEffects", "use_effects", "{\"can_sprint\":true}", "can_sprint", "\"bad flag\"", "can_sprint"),
            useCase("useEffects", "use_effects", "{\"interact_vibrations\":true}", "interact_vibrations", "\"bad flag\"", "interact_vibrations"),
            useCase("remainder", "use_remainder", "{\"id\":\"minecraft:glass_bottle\",\"count\":3}", "id", "\"not an item id\"", "id"),
            useCase("remainder", "use_remainder", "{\"id\":\"minecraft:glass_bottle\",\"count\":3}", "count", "0", "count"),
            useCase("remainder", "use_remainder", "{\"id\":\"minecraft:glass_bottle\",\"count\":3}", "components", "{\"example:unknown_component\":{}}", "$enabled"),
            useCase("cooldown", "use_cooldown", "{\"seconds\":2,\"cooldown_group\":\"minecraft:food\"}", "seconds", "0", "seconds"),
            useCase("cooldown", "use_cooldown", "{\"seconds\":2,\"cooldown_group\":\"minecraft:food\"}", "cooldown_group", "\"not a group id\"", "cooldown_group"),
            useCase("stewEffects", "stew_effects", "[{\"id\":\"minecraft:speed\"},{\"id\":\"minecraft:strength\",\"duration\":\"uses native default\"}]", "1/id", "\"bad stew effect\"", "stew_effect/302/id"),
            useCase("ominousAmplifier", "ominous_amplifier", "3", "", "5", "value"),
            useCase("deathProtection", "death_protection", deathApply, "death_effects/1/effects/1/id", "\"bad death effect\"", "death_protection/202/status/602/id"),
            useCase("deathProtection", "death_protection", deathApply, "death_effects/1/probability", "-1", "death_protection/202/probability"),
            useCase("deathProtection", "death_protection", deathRemove, "death_effects/1/effects/1", "\"bad removed effect\"", "death_protection/202/remove_effect/702/id"),
            useCase("deathProtection", "death_protection", "{\"death_effects\":[" + clear + "," + sound + "]}", "death_effects/1/sound", "\"bad death sound\"", "death_protection/202/sound")
        );
        for (ComponentRecoveryCase check : cases) {
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor();
                    Object panel = read(editor, "useEditor");
                    CodecComponentState state = (CodecComponentState) read(panel, check.model());
                    state.setPresent(true);
                    state.setValue(check.bad());
                    CodecComponentState companion = (CodecComponentState) read(panel, check.model().equals("food") ? "cooldown" : "food");
                    companion.setPresent(true);
                    JsonElement companionValue = JsonParser.parseString(check.model().equals("food") ? "{\"seconds\":2.5}"
                        : "{\"nutrition\":3,\"saturation\":0.25}");
                    companion.setValue(companionValue);
                    write(panel, "dirty", true);
                    Map<String, List<Long>> rows = (Map<String, List<Long>>) read(panel, "rowIdsByList");
                    rows.put("consume", new ArrayList<>(List.of(101L, 102L)));
                    rows.put("death_protection", new ArrayList<>(List.of(201L, 202L)));
                    rows.put("stew", new ArrayList<>(List.of(301L, 302L)));
                    rows.put("consume/102/status", new ArrayList<>(List.of(401L, 402L)));
                    rows.put("consume/102/remove_effect", new ArrayList<>(List.of(501L, 502L)));
                    rows.put("death_protection/202/status", new ArrayList<>(List.of(601L, 602L)));
                    rows.put("death_protection/202/remove_effect", new ArrayList<>(List.of(701L, 702L)));
                    Map<String, List<Long>> identities = new HashMap<>(rows);
                    ItemStack before = editor.draft().copy();
                    ItemStack targetBefore = fixture.slots.get(0).copy();
                    var loreBefore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    require(!(boolean) read(editor, "statusSuccess") && check.section().equals(read(panel, "pendingFocusSection"))
                            && check.control().equals(read(panel, "pendingFocusField")) && !(boolean) read(panel, "focusPendingToolbar")
                            && read(editor, "requestedEditorTab").toString().equals("USE"),
                        "Use " + check.control() + " did not target its precise field on " + action + ": " + read(editor, "status"));
                    require(ItemStack.matches(before, editor.draft()) && ItemStack.matches(targetBefore, fixture.slots.get(0))
                            && fixture.writes.isEmpty() && !fixture.slots.containsKey(1) && !((ItemEditHistory) read(editor, "history")).canUndo()
                            && loreBefore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                            && check.bad().equals(state.value()) && (boolean) read(panel, "dirty")
                            && companion.present() && companionValue.equals(companion.value())
                            && identities.entrySet().stream().allMatch(entry -> rows.get(entry.getKey()) == entry.getValue()),
                        "Use " + check.control() + " changed pending input/row identity or partially committed");
                    if (check.control().startsWith("consume/102/")) require("consume".equals(read(panel, "focusListId"))
                        && (long) read(panel, "focusListRowId") == 102L, "Use recovery did not reveal the parent consume-effect card");
                    if (check.control().startsWith("death_protection/202/"))
                        require("death_protection".equals(read(panel, "focusListId"))
                            && (long) read(panel, "focusListRowId") == 202L, "Use recovery did not reveal the parent death-effect card");
                    state.setValue(check.repaired());
                    ItemStack expected = new ItemStack(Items.STONE);
                    ItemStackCodecs.decodeAndSetComponent(fixture.client, expected, state.type(), check.repaired());
                    ItemStackCodecs.decodeAndSetComponent(fixture.client, expected, companion.type(), companionValue);
                    JsonElement expectedValue = ItemStackCodecs.encodeComponent(fixture.client, state.type(), expected.get(state.type()));
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    require((boolean) read(editor, "statusSuccess") && fixture.writes.size() == 1,
                        "Use " + check.control() + " failed its repaired combined commit: " + read(editor, "status"));
                    ItemStack saved = fixture.slots.get(action.equals("giveDraftCopy") ? 1 : 0);
                    assertCombined(saved);
                    require(expectedValue.equals(ItemStackCodecs.encodeComponent(fixture.client, state.type(), saved.get(state.type()))),
                        "Use lost the exact repaired " + check.model() + " component");
                    require(ItemStackCodecs.encodeComponent(fixture.client, companion.type(), expected.get(companion.type()))
                            .equals(ItemStackCodecs.encodeComponent(fixture.client, companion.type(), saved.get(companion.type()))),
                        "Use lost another pending section during the repaired commit");
                    require(!(boolean) read(panel, "dirty") && read(panel, "pendingFocusSection") == null
                            && read(panel, "focusListId") == null && read(panel, "focusAddListId") == null && rows.isEmpty(),
                        "Use synchronization retained stale recovery/baseline state");
                }
            }
        }
        try (Fixture fixture = new Fixture()) {
            ItemEditor editor = fixture.editedEditor();
            Object panel = read(editor, "useEditor");
            CodecComponentState state = (CodecComponentState) read(panel, "consumable");
            state.setPresent(true);
            state.setValue(JsonParser.parseString("{\"on_consume_effects\":[{\"type\":\"minecraft:apply_effects\",\"effects\":[{\"id\":\"bad effect id\"}]}]}"));
            write(panel, "dirty", true);
            invoke(panel, "requestPendingFocus", new Class<?>[0]);
            Map<String, List<Long>> rows = (Map<String, List<Long>>) read(panel, "rowIdsByList");
            List<Long> parentRows = rows.get("consume");
            JsonElement before = state.value().deepCopy();
            invoke(panel, "syncComponent", new Class<?>[]{Minecraft.class, net.minecraft.core.component.DataComponentType.class, ItemStack.class},
                fixture.client, DataComponents.FOOD, editor.draft());
            require(state.present() && before.equals(state.value()) && rows.get("consume") == parentRows
                && read(panel, "pendingFocusSection") == null && read(panel, "focusListId") == null && read(panel, "focusAddListId") == null
                && (boolean) read(panel, "dirty"), "Use component synchronization kept stale focus or discarded unrelated pending effects");
        }
    }

    private static ComponentRecoveryCase componentCase(String model, String section, String repaired, String badPath, String badValue, String control) {
        return useCase(model, section, repaired, badPath, badValue, control);
    }

    @SuppressWarnings("unchecked")
    private static void verifyCombatFailures() throws Exception {
        String weapon = "{\"item_damage_per_attack\":2,\"disable_blocking_for_seconds\":1}";
        String piercing = "{\"deals_knockback\":true,\"dismounts\":false,\"sound\":\"minecraft:entity.generic.eat\",\"hit_sound\":\"minecraft:entity.generic.eat\"}";
        String kinetic = "{\"contact_cooldown_ticks\":0,\"delay_ticks\":0,\"forward_movement\":-2,\"damage_multiplier\":-3,\"sound\":\"minecraft:entity.generic.eat\",\"damage_conditions\":{\"max_duration_ticks\":0,\"min_speed\":-1,\"min_relative_speed\":-2}}";
        String tool = "{\"rules\":[{\"blocks\":\"minecraft:stone\"},{\"blocks\":[\"minecraft:stone\",\"minecraft:dirt\"],\"speed\":2,\"correct_for_drops\":true}],\"default_mining_speed\":-1,\"damage_per_block\":0,\"can_destroy_blocks_in_creative\":true}";
        String range = "{\"min_reach\":64,\"max_reach\":0,\"min_creative_reach\":64,\"max_creative_reach\":0,\"hitbox_margin\":1,\"mob_factor\":2}";
        String blocking = "{\"block_delay_seconds\":0,\"disable_cooldown_scale\":0,\"block_sound\":\"minecraft:entity.generic.eat\",\"disabled_sound\":\"minecraft:entity.generic.eat\",\"bypassed_by\":[\"minecraft:in_fire\",\"minecraft:player_attack\"],\"item_damage\":{\"threshold\":0,\"base\":-1,\"factor\":-2},\"damage_reductions\":[{\"base\":0,\"factor\":1}, {\"horizontal_blocking_angle\":90,\"type\":[\"minecraft:in_fire\",\"minecraft:player_attack\"],\"base\":-1,\"factor\":-2}]}";
        List<ComponentRecoveryCase> cases = List.of(
            componentCase("minimumCharge", "minimum_charge", "0.5", "", "2", "value"),
            componentCase("damageType", "damage_type", "\"minecraft:player_attack\"", "", "\"bad damage id\"", "value"),
            componentCase("damageType", "damage_type", "\"minecraft:player_attack\"", "", "\"example:unknown_damage\"", "value"),
            componentCase("swing", "swing", "{\"type\":\"stab\",\"duration\":6}", "type", "\"unknown animation\"", "type"),
            componentCase("swing", "swing", "{\"duration\":6}", "duration", "0", "duration"),
            componentCase("weapon", "weapon", weapon, "item_damage_per_attack", "-1", "item_damage_per_attack"),
            componentCase("weapon", "weapon", weapon, "disable_blocking_for_seconds", "-1", "disable_blocking_for_seconds"),
            componentCase("piercingWeapon", "piercing_weapon", piercing, "deals_knockback", "\"bad flag\"", "deals_knockback"),
            componentCase("piercingWeapon", "piercing_weapon", piercing, "dismounts", "\"bad flag\"", "dismounts"),
            componentCase("piercingWeapon", "piercing_weapon", piercing, "sound", "\"bad sound id\"", "sound"),
            componentCase("piercingWeapon", "piercing_weapon", piercing, "hit_sound", "\"example:unknown_sound\"", "hit_sound"),
            componentCase("piercingWeapon", "piercing_weapon", "{\"sound\":{\"sound_id\":\"example:custom_sound\",\"range\":16}}", "sound/sound_id", "\"bad inline sound id\"", "sound"),
            componentCase("kineticWeapon", "kinetic_weapon", kinetic, "contact_cooldown_ticks", "-1", "contact_cooldown_ticks"),
            componentCase("kineticWeapon", "kinetic_weapon", kinetic, "delay_ticks", "-1", "delay_ticks"),
            componentCase("kineticWeapon", "kinetic_weapon", kinetic, "forward_movement", "\"bad movement\"", "forward_movement"),
            componentCase("kineticWeapon", "kinetic_weapon", kinetic, "damage_multiplier", "\"bad multiplier\"", "damage_multiplier"),
            componentCase("kineticWeapon", "kinetic_weapon", kinetic, "sound", "\"bad sound id\"", "sound"),
            componentCase("kineticWeapon", "kinetic_weapon", kinetic, "damage_conditions", "\"bad condition\"", "damage_conditions/$enabled"),
            componentCase("kineticWeapon", "kinetic_weapon", kinetic, "damage_conditions/max_duration_ticks", "-1", "damage_conditions/max_duration_ticks"),
            componentCase("kineticWeapon", "kinetic_weapon", kinetic, "damage_conditions/min_speed", "\"bad speed\"", "damage_conditions/min_speed"),
            componentCase("kineticWeapon", "kinetic_weapon", kinetic, "damage_conditions/min_relative_speed", "\"bad speed\"", "damage_conditions/min_relative_speed"),
            componentCase("tool", "tool", tool, "default_mining_speed", "\"bad speed\"", "default_mining_speed"),
            componentCase("tool", "tool", tool, "damage_per_block", "-1", "damage_per_block"),
            componentCase("tool", "tool", tool, "can_destroy_blocks_in_creative", "\"bad flag\"", "can_destroy_blocks_in_creative"),
            componentCase("tool", "tool", tool, "rules/1/blocks/1", "\"bad block id\"", "tool/rule/102/blocks"),
            componentCase("tool", "tool", tool, "rules/1/blocks/1", "\"example:unknown_block\"", "tool/rule/102/blocks"),
            componentCase("tool", "tool", tool, "rules/1/speed", "0", "tool/rule/102/speed"),
            componentCase("tool", "tool", tool, "rules/1/correct_for_drops", "\"bad flag\"", "tool/rule/102/correct_for_drops"),
            componentCase("tool", "tool", tool, "rules", "{}", "$enabled"),
            componentCase("range", "attack_range", range, "min_reach", "65", "min_reach"),
            componentCase("range", "attack_range", range, "max_reach", "-1", "max_reach"),
            componentCase("range", "attack_range", range, "min_creative_reach", "65", "min_creative_reach"),
            componentCase("range", "attack_range", range, "max_creative_reach", "-1", "max_creative_reach"),
            componentCase("range", "attack_range", range, "hitbox_margin", "1.1", "hitbox_margin"),
            componentCase("range", "attack_range", range, "mob_factor", "2.1", "mob_factor"),
            componentCase("resistance", "damage_resistant", "{\"types\":[\"minecraft:in_fire\",\"minecraft:player_attack\"]}", "types/1", "\"bad damage id\"", "resistance/types"),
            componentCase("repairable", "repairable", "{\"items\":[\"minecraft:stone\",\"minecraft:iron_ingot\"]}", "items/1", "\"bad item id\"", "repairable/items"),
            componentCase("repairable", "repairable", "{\"items\":[]}", "items", "\"bad item id\"", "repairable/items"),
            componentCase("resistance", "damage_resistant", "{\"types\":\"minecraft:in_fire\"}", "types", "\"bad damage id\"", "resistance/types"),
            componentCase("resistance", "damage_resistant", "{\"types\":[]}", "types", "\"bad damage id\"", "resistance/types"),
            componentCase("blocksAttacks", "blocks_attacks", blocking, "block_delay_seconds", "-1", "block_delay_seconds"),
            componentCase("blocksAttacks", "blocks_attacks", blocking, "disable_cooldown_scale", "-1", "disable_cooldown_scale"),
            componentCase("blocksAttacks", "blocks_attacks", blocking, "block_sound", "\"bad sound id\"", "block_sound"),
            componentCase("blocksAttacks", "blocks_attacks", blocking, "disabled_sound", "\"bad sound id\"", "disabled_sound"),
            componentCase("blocksAttacks", "blocks_attacks", blocking, "bypassed_by/1", "\"bad damage id\"", "blocking/bypassed_by"),
            componentCase("blocksAttacks", "blocks_attacks", blocking, "item_damage/threshold", "-1", "blocking/item_damage/threshold"),
            componentCase("blocksAttacks", "blocks_attacks", blocking, "item_damage/base", "\"bad base\"", "blocking/item_damage/base"),
            componentCase("blocksAttacks", "blocks_attacks", blocking, "item_damage/factor", "\"bad factor\"", "blocking/item_damage/factor"),
            componentCase("blocksAttacks", "blocks_attacks", blocking, "damage_reductions/1/horizontal_blocking_angle", "0", "blocking/reduction/202/horizontal_blocking_angle"),
            componentCase("blocksAttacks", "blocks_attacks", blocking, "damage_reductions/1/type/1", "\"bad damage id\"", "blocking/reduction/202/type"),
            componentCase("blocksAttacks", "blocks_attacks", blocking, "damage_reductions/1/base", "\"bad base\"", "blocking/reduction/202/base"),
            componentCase("blocksAttacks", "blocks_attacks", blocking, "damage_reductions/1/factor", "\"bad factor\"", "blocking/reduction/202/factor"),
            componentCase("enchantable", "enchantable", "{\"value\":10}", "value", "0", "value"),
            componentCase("enchantable", "enchantable", "{\"value\":10}", "", "10", "value")
        );
        for (ComponentRecoveryCase check : cases) {
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor();
                    Object panel = read(editor, "combatEditor");
                    CodecComponentState state = (CodecComponentState) read(panel, check.model());
                    state.setPresent(true);
                    state.setValue(check.bad());
                    CodecComponentState companion = (CodecComponentState) read(panel, check.model().equals("weapon") ? "range" : "weapon");
                    companion.setPresent(true);
                    JsonElement companionValue = JsonParser.parseString(check.model().equals("weapon") ? "{\"max_reach\":4}" : weapon);
                    companion.setValue(companionValue);
                    write(panel, "dirty", true);
                    List<Long> toolRows = (List<Long>) read(panel, "toolRuleRowIds");
                    List<Long> reductions = (List<Long>) read(panel, "damageReductionRowIds");
                    toolRows.addAll(List.of(101L, 102L));
                    reductions.addAll(List.of(201L, 202L));
                    ItemStack before = editor.draft().copy();
                    ItemStack targetBefore = fixture.slots.get(0).copy();
                    var loreBefore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    require(!(boolean) read(editor, "statusSuccess") && check.section().equals(read(panel, "pendingFocusSection"))
                            && check.control().equals(read(panel, "pendingFocusField")) && !(boolean) read(panel, "focusPendingToolbar")
                            && read(editor, "requestedEditorTab").toString().equals("COMBAT"),
                        "Combat " + check.control() + " did not target its field on " + action + ": " + read(editor, "status")
                            + "; got " + read(panel, "pendingFocusSection") + "/" + read(panel, "pendingFocusField"));
                    require(ItemStack.matches(before, editor.draft()) && ItemStack.matches(targetBefore, fixture.slots.get(0))
                            && fixture.writes.isEmpty() && !fixture.slots.containsKey(1) && !((ItemEditHistory) read(editor, "history")).canUndo()
                            && loreBefore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                            && check.bad().equals(state.value()) && (boolean) read(panel, "dirty")
                            && companionValue.equals(companion.value()) && toolRows.equals(List.of(101L, 102L)) && reductions.equals(List.of(201L, 202L)),
                        "Combat " + check.control() + " changed pending input/identity or partially committed");
                    if (check.control().startsWith("tool/rule/102/"))
                        require((long) read(panel, "focusToolRuleRowId") == 102L, "Combat did not reveal the tool rule");
                    if (check.control().startsWith("blocking/reduction/202/"))
                        require((long) read(panel, "focusDamageReductionRowId") == 202L, "Combat did not reveal the damage reduction");
                    state.setValue(check.repaired());
                    ItemStack expected = new ItemStack(Items.STONE);
                    ItemStackCodecs.decodeAndSetComponent(fixture.client, expected, state.type(), check.repaired());
                    ItemStackCodecs.decodeAndSetComponent(fixture.client, expected, companion.type(), companionValue);
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    require((boolean) read(editor, "statusSuccess") && fixture.writes.size() == 1,
                        "Combat " + check.control() + " failed repaired commit: " + read(editor, "status"));
                    ItemStack saved = fixture.slots.get(action.equals("giveDraftCopy") ? 1 : 0);
                    assertCombined(saved);
                    for (CodecComponentState component : List.of(state, companion))
                        require(
                            ItemStackCodecs.encodeComponent(fixture.client, component.type(), expected.get(component.type()))
                                .equals(ItemStackCodecs.encodeComponent(fixture.client, component.type(), saved.get(component.type()))),
                            "Combat lost repaired/companion component " + component.type());
                    require(!(boolean) read(panel, "dirty") && read(panel, "pendingFocusSection") == null && toolRows.isEmpty() && reductions.isEmpty(),
                        "Combat synchronization retained recovery state/dirty baseline");
                }
            }
        }
        try (Fixture fixture = new Fixture()) {
            ItemEditor editor = fixture.editedEditor();
            Object panel = read(editor, "combatEditor");
            CodecComponentState state = (CodecComponentState) read(panel, "tool");
            state.setPresent(true);
            state.setValue(JsonParser.parseString("{\"rules\":[{\"blocks\":\"bad block id\"}]}"));
            write(panel, "dirty", true);
            invoke(panel, "requestPendingFocus", new Class<?>[0]);
            List<Long> identities = List.copyOf((List<Long>) read(panel, "toolRuleRowIds"));
            Map<String, ImString> buffers = (Map<String, ImString>) read(panel, "textBuffers");
            String path = "tool/rule/" + identities.getFirst() + "/blocks/@identifier/0";
            ImString input = new ImString("bad block id  ", 512);
            buffers.put(path, input);
            JsonElement before = state.value().deepCopy();
            invoke(panel, "syncComponent", new Class<?>[]{Minecraft.class, net.minecraft.core.component.DataComponentType.class, ItemStack.class}, fixture.client, DataComponents.WEAPON, editor.draft());
            require(state.present() && before.equals(state.value()) && identities.equals(read(panel, "toolRuleRowIds"))
                    && buffers.get(path) == input && input.get().equals("bad block id  ")
                    && read(panel, "pendingFocusSection") == null && (long) read(panel, "focusToolRuleRowId") == 0L && (boolean) read(panel, "dirty"),
                "Combat component sync discarded another pending rule or retained transient focus");
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifyProjectileFailuresAndIsolation() throws Exception {
        Class<?> rowType = Class.forName("dev.nineofgaming.itemlab.editor.panel.CombatEditorPanel$ProjectileRow");
        var constructor = rowType.getDeclaredConstructor(JsonObject.class, CompoundTag.class);
        constructor.setAccessible(true);
        for (String failure : List.of("id", "count", "syntax", "codec", "later_component")) {
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor();
                    Object panel = read(editor, "combatEditor");
                    CodecComponentState state = (CodecComponentState) read(panel, "chargedProjectiles");
                    state.setPresent(true);
                    List<Object> rows = (List<Object>) read(panel, "projectileRows");
                    Object first = constructor.newInstance(JsonParser.parseString("{\"id\":\"minecraft:arrow\"}").getAsJsonObject(), null);
                    Object second = constructor.newInstance(JsonParser.parseString("{\"id\":\"minecraft:arrow\",\"count\":2,\"components\":{}}").getAsJsonObject(), null);
                    rows.addAll(List.of(first, second));
                    ImString source = (ImString) read(second, "componentsSource");
                    String typedSource = "{\"minecraft:custom_data\":{b:1b,l:2L,f:3.5f,d:4.5d}}";
                    source.set(typedSource);
                    var expectedRows = (net.minecraft.nbt.ListTag) invoke(panel, "buildProjectileList", new Class<?>[]{Minecraft.class}, fixture.client);
                    state.setNbtValue(fixture.client, expectedRows);
                    JsonElement modelBefore = state.value().deepCopy();
                    Object cachedNbt = read(state, "nbtValue");
                    ItemStack comparison = editor.draft().copy();
                    ItemStackCodecs.decodeAndSetComponentNbt(fixture.client, comparison, DataComponents.CHARGED_PROJECTILES, expectedRows);
                    require(!(boolean) invoke(panel, "projectilesDifferFromDraft", new Class<?>[]{Minecraft.class, ItemStack.class}, fixture.client, comparison),
                        "Combat projectile dirty comparison disagreed with native typed NBT");
                    switch (failure) {
                        case "id" -> ((ImString) read(second, "itemId")).set("bad item id");
                        case "count" -> ((ImInt) read(second, "count")).set(0);
                        case "syntax" -> source.set("{");
                        case "codec" -> source.set("{\"example:unknown_component\":{}}");
                        default -> {
                            CodecComponentState later = (CodecComponentState) read(panel, "enchantable");
                            later.setPresent(true);
                            later.setValue(JsonParser.parseString("0"));
                        }
                    }
                    write(panel, "dirty", true);
                    Object code = read(second, "componentsEditor");
                    write(code, "cursorByteOffset", 1);
                    String badSource = source.get();
                    ItemStack before = editor.draft().copy();
                    ItemStack targetBefore = fixture.slots.get(0).copy();
                    invoke(panel, "hasPendingChanges", new Class<?>[0]);
                    var result = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                    require(!result.succeeded(), "Invalid Combat projectile/later component passed preflight");
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    String section = failure.equals("later_component") ? "enchantable" : "charged_projectiles";
                    String control = failure.equals("later_component") ? "value" : "row/" + read(second, "id") + "/" + (failure.equals("syntax") || failure.equals("codec") ? "components" : failure);
                    require(!(boolean) read(editor, "statusSuccess") && section.equals(read(panel, "pendingFocusSection")) && control.equals(read(panel, "pendingFocusField")),
                        "Combat projectile " + failure + " recovery mismatch: " + read(editor, "status"));
                    require(ItemStack.matches(before, editor.draft()) && ItemStack.matches(targetBefore, fixture.slots.get(0))
                            && fixture.writes.isEmpty() && !fixture.slots.containsKey(1) && !((ItemEditHistory) read(editor, "history")).canUndo()
                            && modelBefore.equals(state.value()) && cachedNbt == read(state, "nbtValue") && source.get().equals(badSource)
                            && rows.get(0) == first && rows.get(1) == second && (int) read(code, "cursorByteOffset") == 1,
                        "Combat projectile preflight/recovery mutated cached model, input, row identity, draft, target or history");
                    ((ImString) read(second, "itemId")).set("minecraft:arrow");
                    ((ImInt) read(second, "count")).set(2);
                    source.set(typedSource);
                    if (failure.equals("later_component"))
                        ((CodecComponentState) read(panel, "enchantable")).setValue(JsonParser.parseString("{\"value\":10}"));
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    require((boolean) read(editor, "statusSuccess") && fixture.writes.size() == 1, "Combat projectile repair failed: " + read(editor, "status"));
                    ItemStack saved = fixture.slots.get(action.equals("giveDraftCopy") ? 1 : 0);
                    assertCombined(saved);
                    require(ItemStackCodecs.canonicalizeComponentNbt(fixture.client, DataComponents.CHARGED_PROJECTILES, expectedRows)
                            .equals(ItemStackCodecs.encodeComponentNbt(fixture.client, DataComponents.CHARGED_PROJECTILES, saved.get(DataComponents.CHARGED_PROJECTILES))),
                        "Combat projectile commit lost typed NBT/count/list order");
                    var savedRows = (net.minecraft.nbt.ListTag) ItemStackCodecs.encodeComponentNbt(fixture.client, DataComponents.CHARGED_PROJECTILES, saved.get(DataComponents.CHARGED_PROJECTILES));
                    CompoundTag customData = ((CompoundTag) savedRows.get(1)).getCompound("components").orElseThrow()
                        .getCompound("minecraft:custom_data").orElseThrow();
                    require(customData.get("b") instanceof net.minecraft.nbt.ByteTag && customData.get("l") instanceof net.minecraft.nbt.LongTag
                            && customData.get("f") instanceof net.minecraft.nbt.FloatTag && customData.get("d") instanceof net.minecraft.nbt.DoubleTag,
                        "Combat converted projectile custom-data NBT numeric types");
                    require(!(boolean) read(panel, "dirty") && read(panel, "pendingFocusSection") == null && !(boolean) read(code, "focusRequested"),
                        "Combat projectile synchronization retained dirty/focus state");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifyContainerFailuresAndIsolation() throws Exception {
        String lockJson = "{\"items\":[\"minecraft:tripwire_hook\",\"minecraft:stone\"],\"count\":{\"min\":-2,\"max\":-1},\"components\":{},\"predicates\":{}}";
        String typedSource = "{\"minecraft:custom_data\":{b:1b,l:2L,f:3.5f,d:4.5d}}";
        List<String> failures = List.of("contents_id", "bundle_id", "disallowed_bundle", "contents_count", "bundle_count",
            "lock_items", "lock_unknown_item", "lock_empty_items", "lock_tag_list", "lock_count", "lock_min", "lock_max", "lock_range",
            "components_syntax", "predicates_syntax", "components_codec", "predicates_codec", "lock_shape", "loot_table", "loot_seed", "loot_seed_overflow", "loot_shape");
        for (String failure : failures) {
            for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor();
                    Object panel = read(editor, "containerEditor");
                    var contents = (dev.nineofgaming.itemlab.editor.container.ContainerContentsDraft) read(panel, "contents");
                    var bundle = (dev.nineofgaming.itemlab.editor.container.BundleContentsDraft) read(panel, "bundleContents");
                    contents.setPresent(true);
                    contents.setItem(0, new ItemStack(Items.STONE, 99));
                    CompoundTag nestedTag = TagParser.parseCompoundFully("{b:1b,l:2L,f:3.5f,d:4.5d}");
                    ItemStack stored = new ItemStack(Items.ARROW, 3);
                    stored.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(nestedTag));
                    var damageTypeKey = net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE, net.minecraft.resources.Identifier.parse("minecraft:player_attack"));
                    stored.set(DataComponents.DAMAGE_TYPE, fixtureRegistries.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(damageTypeKey));
                    contents.setItem(200, stored);
                    bundle.setPresent(true);
                    bundle.addItem(new ItemStack(Items.STONE, 2));
                    bundle.addItem(stored);
                    List<Long> rowIds = (List<Long>) read(panel, "bundleRowIds");
                    rowIds.addAll(List.of(101L, 102L));
                    write(panel, "selectedContentsSlot", failure.equals("contents_id") ? 200 : 0);
                    write(panel, "selectedBundleItem", 0);
                    invoke(panel, "syncContainedItemFields", new Class<?>[0]);
                    invoke(panel, "syncBundleItemFields", new Class<?>[0]);
                    CodecComponentState lock = (CodecComponentState) read(panel, "lock");
                    CodecComponentState loot = (CodecComponentState) read(panel, "loot");
                    lock.setPresent(true);
                    lock.setValue(JsonParser.parseString(lockJson));
                    lock.setNbtValue(fixture.client, TagParser.parseCompoundFully("{items:[\"minecraft:stone\"]}"));
                    loot.setPresent(true);
                    loot.setValue(JsonParser.parseString("{\"loot_table\":\"minecraft:empty\"}"));
                    loot.setNbtValue(fixture.client, TagParser.parseCompoundFully("{loot_table:\"minecraft:empty\",seed:99L}"));
                    ImString exact = (ImString) read(panel, "exactComponentsSource");
                    ImString partial = (ImString) read(panel, "partialPredicatesSource");
                    ImString table = (ImString) read(panel, "lootTable");
                    ImString seed = (ImString) read(panel, "lootSeed");
                    exact.set(typedSource);
                    partial.set("{}");
                    table.set("example:future_loot_table  ");
                    seed.set("  -9223372036854775808  ");
                    String section = "lock";
                    String control;
                    switch (failure) {
                        case "contents_id" -> {
                            ((ImString) read(panel, "containedItemId")).set("bad item id  ");
                            invoke(panel, "containedItemIdChanged", new Class<?>[0]);
                            section = "contents";
                            control = "item_id";
                        }
                        case "bundle_id" -> {
                            ((ImString) read(panel, "bundleItemId")).set("bad bundle id  ");
                            invoke(panel, "bundleItemIdChanged", new Class<?>[0]);
                            section = "bundle_contents";
                            control = "item_id";
                        }
                        case "disallowed_bundle" -> {
                            bundle.setItem(1, new ItemStack(Items.SHULKER_BOX));
                            section = "bundle_contents";
                            control = "row/102";
                        }
                        case "contents_count" -> {
                            contents.setItem(200, stored.copyWithCount(100));
                            section = "contents";
                            control = "slot/200";
                        }
                        case "bundle_count" -> {
                            bundle.setItem(1, stored.copyWithCount(100));
                            section = "bundle_contents";
                            control = "row/102";
                        }
                        case "lock_items", "lock_unknown_item", "lock_empty_items", "lock_tag_list" -> {
                            JsonObject value = lock.value().getAsJsonObject().deepCopy();
                            value.add("items", JsonParser.parseString(switch (failure) {
                                case "lock_unknown_item" -> "[\"minecraft:stone\",\"example:unknown_item\"]";
                                case "lock_empty_items" -> "[]";
                                case "lock_tag_list" -> "[\"minecraft:stone\",\"#minecraft:planks\"]";
                                default -> "[\"minecraft:stone\",\"bad lock id\"]";
                            }));
                            lock.setValue(value);
                            control = "items";
                        }
                        case "lock_count", "lock_min", "lock_max", "lock_range" -> {
                            JsonObject value = lock.value().getAsJsonObject().deepCopy();
                            value.add("count", JsonParser.parseString(switch (failure) {
                                case "lock_min" -> "{\"min\":\"bad minimum\",\"max\":4}";
                                case "lock_max" -> "{\"min\":2,\"max\":\"bad maximum\"}";
                                case "lock_range" -> "{\"min\":4,\"max\":2}";
                                default -> "\"bad count\"";
                            }));
                            lock.setValue(value);
                            control = failure.equals("lock_count") ? "count" : failure.equals("lock_max") ? "count/max" : "count/min";
                        }
                        case "components_syntax", "components_codec" -> {
                            exact.set(failure.equals("components_syntax") ? "{" : "{\"itemlab:unknown_component\":{}}");
                            control = "components";
                        }
                        case "predicates_syntax", "predicates_codec" -> {
                            partial.set(failure.equals("predicates_syntax") ? "{" : "{\"itemlab:unknown_predicate\":{}}");
                            control = "predicates";
                        }
                        case "lock_shape" -> {
                            lock.setValue(JsonParser.parseString("[]"));
                            control = "$enabled";
                        }
                        case "loot_table" -> {
                            table.set("bad loot table  ");
                            section = "loot_table";
                            control = "loot_table";
                        }
                        case "loot_seed", "loot_seed_overflow" -> {
                            seed.set(failure.equals("loot_seed") ? "bad seed  " : "9223372036854775808");
                            section = "loot_table";
                            control = "seed";
                        }
                        default -> {
                            loot.setValue(JsonParser.parseString("[]"));
                            section = "loot_table";
                            control = "$enabled";
                        }
                    }
                    // The input-change helpers must dirty a clean panel even when the ID cannot be applied.
                    if (failure.equals("contents_id") || failure.equals("bundle_id"))
                        require((boolean) read(panel, "dirty"),
                            "Invalid Container ID input left the panel clean");
                    else write(panel, "dirty", true);
                    JsonElement lockBefore = lock.value().deepCopy();
                    JsonElement lootBefore = loot.value().deepCopy();
                    Object lockNbt = read(lock, "nbtValue");
                    Object lootNbt = read(loot, "nbtValue");
                    String exactBefore = exact.get(), partialBefore = partial.get(), tableBefore = table.get(), seedBefore = seed.get();
                    String itemBefore = ((ImString) read(panel, "containedItemId")).get(), bundleBefore = ((ImString) read(panel, "bundleItemId")).get();
                    Object exactEditor = read(panel, "exactComponentsEditor"), partialEditor = read(panel, "partialPredicatesEditor");
                    write(exactEditor, "cursorByteOffset", 1);
                    write(partialEditor, "cursorByteOffset", 1);
                    ItemStack before = editor.draft().copy(), targetBefore = fixture.slots.get(0).copy();
                    var loreBefore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                    require(((dev.nineofgaming.itemlab.editor.panel.ContainerEditorPanel) panel).isDirty(), "Invalid Container " + failure + " was not dirty");
                    var result = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                    require(!result.succeeded(), "Invalid Container " + failure + " passed preflight");
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    require(!(boolean) read(editor, "statusSuccess") && section.equals(read(panel, "pendingFocusSection"))
                            && control.equals(read(panel, "pendingFocusField")) && !(boolean) read(panel, "focusPendingToolbar")
                            && read(editor, "requestedEditorTab").toString().equals("CONTAINER"),
                        "Container " + failure + " did not target " + section + "/" + control + ": " + read(editor, "status")
                            + "; got " + read(panel, "pendingFocusSection") + "/" + read(panel, "pendingFocusField"));
                    require(ItemStack.matches(before, editor.draft()) && ItemStack.matches(targetBefore, fixture.slots.get(0))
                            && fixture.writes.isEmpty() && !fixture.slots.containsKey(1) && !((ItemEditHistory) read(editor, "history")).canUndo()
                            && loreBefore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                            && lockBefore.equals(lock.value()) && lootBefore.equals(loot.value())
                            && lockNbt == read(lock, "nbtValue") && lootNbt == read(loot, "nbtValue")
                            && exactBefore.equals(exact.get()) && partialBefore.equals(partial.get()) && tableBefore.equals(table.get()) && seedBefore.equals(seed.get())
                            && itemBefore.equals(((ImString) read(panel, "containedItemId")).get()) && bundleBefore.equals(((ImString) read(panel, "bundleItemId")).get())
                            && (int) read(panel, "selectedContentsSlot") == (failure.equals("contents_id") ? 200 : 0) && (int) read(panel, "selectedBundleItem") == 0
                            && rowIds.equals(List.of(101L, 102L)) && contents == read(panel, "contents") && bundle == read(panel, "bundleContents")
                            && (int) read(exactEditor, "cursorByteOffset") == 1 && (int) read(partialEditor, "cursorByteOffset") == 1,
                        "Container " + failure + " mutated pending input/model/identity, draft, target or history");
                    if (control.equals("row/102"))
                        require(!(boolean) invoke(panel, "allowBundleFocusSelection", new Class<?>[]{long.class}, 102L),
                            "Bundle recovery would discard inspector input on native focus");
                    if (control.equals("slot/200"))
                        require(!(boolean) invoke(panel, "allowContentsFocusSelection", new Class<?>[]{int.class}, 200),
                            "Container recovery would discard inspector input on native focus");
                    lock.setValue(JsonParser.parseString(lockJson));
                    loot.setValue(JsonParser.parseString("{\"loot_table\":\"minecraft:empty\"}"));
                    exact.set(typedSource);
                    partial.set("{}");
                    table.set("example:future_loot_table  ");
                    seed.set("  -9223372036854775808  ");
                    contents.setItem(200, stored);
                    bundle.setItem(1, stored);
                    if (failure.equals("contents_id")) {
                        ((ImString) read(panel, "containedItemId")).set("minecraft:diamond");
                        invoke(panel, "containedItemIdChanged", new Class<?>[0]);
                    }
                    if (failure.equals("bundle_id")) {
                        ((ImString) read(panel, "bundleItemId")).set("minecraft:diamond");
                        invoke(panel, "bundleItemIdChanged", new Class<?>[0]);
                    }
                    CompoundTag expectedLock = (CompoundTag) invoke(panel, "pendingLockValue", new Class<?>[0]);
                    Object cachedAfterRepair = read(lock, "nbtValue");
                    invoke(panel, "pendingStack", new Class<?>[]{Minecraft.class}, fixture.client);
                    require(cachedAfterRepair == read(lock, "nbtValue") && lock.value().equals(JsonParser.parseString(lockJson))
                            && loot.value().equals(JsonParser.parseString("{\"loot_table\":\"minecraft:empty\"}")) && seed.get().equals("  -9223372036854775808  "),
                        "Valid Container preview normalized/stored pending Lock/Loot buffers");
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    require((boolean) read(editor, "statusSuccess") && fixture.writes.size() == 1, "Container " + failure + " repair failed: " + read(editor, "status"));
                    ItemStack saved = fixture.slots.get(action.equals("giveDraftCopy") ? 1 : 0);
                    assertCombined(saved);
                    var savedContents = dev.nineofgaming.itemlab.editor.container.ContainerContentsDraft.fromStack(saved, ItemStackCodecs.jsonOps(fixture.client));
                    var savedBundle = dev.nineofgaming.itemlab.editor.container.BundleContentsDraft.fromStack(saved);
                    require(savedContents.highestOccupiedSlot() == 200 && savedContents.item(0).getCount() == 99 && savedContents.item(199).isEmpty()
                            && savedContents.item(200).is(failure.equals("contents_id") ? Items.DIAMOND : Items.ARROW) && savedContents.item(200).getCount() == 3
                            && savedContents.item(200).get(DataComponents.CUSTOM_DATA).copyTag().equals(nestedTag)
                            && savedContents.item(200).get(DataComponents.DAMAGE_TYPE).is(damageTypeKey)
                            && savedBundle.size() == 2 && savedBundle.item(0).is(failure.equals("bundle_id") ? Items.DIAMOND : Items.STONE)
                            && savedBundle.item(0).getCount() == 2 && savedBundle.item(1).get(DataComponents.CUSTOM_DATA).copyTag().equals(nestedTag),
                        "Container " + failure + " repair lost sparse/overstacked contents, Bundle order/count or typed custom data: slot200="
                            + savedContents.item(200) + ", data=" + savedContents.item(200).get(DataComponents.CUSTOM_DATA)
                            + ", bundle0=" + savedBundle.item(0) + ", bundle1=" + savedBundle.item(1));
                    require(ItemStackCodecs.canonicalizeComponentNbt(fixture.client, DataComponents.LOCK, expectedLock)
                        .equals(ItemStackCodecs.encodeComponentNbt(fixture.client, DataComponents.LOCK, saved.get(DataComponents.LOCK))), "Container repair lost exact Lock data");
                    CompoundTag savedLock = (CompoundTag) ItemStackCodecs.encodeComponentNbt(fixture.client, DataComponents.LOCK, saved.get(DataComponents.LOCK));
                    CompoundTag lockData = savedLock.getCompound("components").orElseThrow().getCompound("minecraft:custom_data").orElseThrow();
                    require(lockData.get("b") instanceof net.minecraft.nbt.ByteTag && lockData.get("l") instanceof net.minecraft.nbt.LongTag
                            && lockData.get("f") instanceof net.minecraft.nbt.FloatTag && lockData.get("d") instanceof net.minecraft.nbt.DoubleTag,
                        "Container Lock preview converted numeric NBT tag types");
                    JsonObject savedLoot = ItemStackCodecs.encodeComponent(fixture.client, DataComponents.CONTAINER_LOOT, saved.get(DataComponents.CONTAINER_LOOT)).getAsJsonObject();
                    require(savedLoot.get("loot_table").getAsString().equals("example:future_loot_table") && savedLoot.get("seed").getAsLong() == Long.MIN_VALUE,
                        "Container lost a native resource key or signed 64-bit seed");
                    require(!(boolean) read(panel, "dirty") && read(panel, "pendingFocusSection") == null && (int) read(panel, "recoveryContentsSlot") == -1
                            && (long) read(panel, "recoveryBundleRowId") == 0L && !(boolean) read(exactEditor, "focusRequested") && !(boolean) read(partialEditor, "focusRequested"),
                        "Container synchronization retained stale dirty/focus state");
                    require(ItemStack.matches(saved, ((dev.nineofgaming.itemlab.editor.panel.ContainerEditorPanel) panel).pendingStack(fixture.client)),
                        "Container's synchronized models changed typed data on a subsequent preview");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifyContainerComponentSyncRetention() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ItemEditor editor = fixture.editedEditor();
            var panel = (dev.nineofgaming.itemlab.editor.panel.ContainerEditorPanel) read(editor, "containerEditor");
            var contents = (dev.nineofgaming.itemlab.editor.container.ContainerContentsDraft) read(panel, "contents");
            contents.setPresent(true);
            contents.setItem(200, new ItemStack(Items.STONE, 99));
            var bundle = (dev.nineofgaming.itemlab.editor.container.BundleContentsDraft) read(panel, "bundleContents");
            bundle.setPresent(true);
            bundle.addItem(new ItemStack(Items.ARROW, 3));
            List<Long> identities = (List<Long>) read(panel, "bundleRowIds");
            identities.add(101L);
            CodecComponentState lock = (CodecComponentState) read(panel, "lock");
            lock.setPresent(true);
            lock.setValue(JsonParser.parseString("{\"items\":[\"minecraft:stone\"],\"components\":{}}"));
            ImString source = (ImString) read(panel, "exactComponentsSource");
            source.set("{");
            Object code = read(panel, "exactComponentsEditor");
            write(code, "cursorByteOffset", 1);
            Map<String, ImString> buffers = (Map<String, ImString>) read(panel, "textBuffers");
            ImString input = new ImString("minecraft:stone  ", 512);
            buffers.put("container/lock/items/@identifier/0", input);
            JsonElement before = lock.value().deepCopy();
            panel.requestPendingFocus();
            panel.syncComponent(fixture.client, DataComponents.CONTAINER_LOOT, editor.draft());
            require(contents == read(panel, "contents") && bundle == read(panel, "bundleContents") && identities.equals(List.of(101L))
                    && before.equals(lock.value()) && source.get().equals("{") && (int) read(code, "cursorByteOffset") == 1
                    && buffers.get("container/lock/items/@identifier/0") == input && input.get().equals("minecraft:stone  ")
                    && read(panel, "pendingFocusSection") == null && !(boolean) read(code, "focusRequested") && panel.isDirty(),
                "Container component sync discarded unrelated models/buffers/rows/cursor, retained focus or threw on invalid pending Lock");
            ((ImString) read(panel, "bundleItemId")).set("bad bundle id  ");
            invoke(panel, "bundleItemIdChanged", new Class<?>[0]);
            panel.syncComponent(fixture.client, DataComponents.LOCK, editor.draft());
            require(!lock.present() && buffers.isEmpty() && source.get().equals("{}") && (int) read(code, "cursorByteOffset") == 0
                    && bundle == read(panel, "bundleContents") && identities.equals(List.of(101L))
                    && ((ImString) read(panel, "bundleItemId")).get().equals("bad bundle id  ") && (boolean) read(panel, "bundleItemIdPending") && panel.isDirty(),
                "Lock-only sync failed to reset its own inputs or discarded another pending inspector");
        }
    }

    private static void verifySulfurCubeContentValidation() throws Exception {
        if (!SulfurCubeContentDraft.supported()) return;
        try (Fixture fixture = new Fixture()) {
            for (int count : List.of(1, 16, 64, 99)) {
                ItemStack child = nestedOpeningItem();
                child.setCount(count);
                child.set(DataComponents.MAX_STACK_SIZE, 16);
                ItemStack owner = new ItemStack(Items.STONE);
                var content = new SulfurCubeContentDraft();
                content.setPresent(true);
                content.setStack(child);
                content.applyTo(owner);
                ItemStackCodecs.validateStack(owner);
                require(ItemStack.matches(child, SulfurCubeContentDraft.fromStack(owner).stack())
                    && content.matchesStack(owner), "Sulfur Cube loading lost a valid stored count/component patch");
                ItemStack roundTrip = ItemStackCodecs.decodeStackSnbt(fixture.client, ItemStackCodecs.encodeStackSnbt(fixture.client, owner));
                require(ItemStack.matches(child, SulfurCubeContentDraft.fromStack(roundTrip).stack()),
                    "Sulfur Cube serialization changed typed child data or overstacked count");
            }
            for (String invalid : List.of("count", "component")) {
                ItemStack child = nestedOpeningItem();
                if (invalid.equals("count")) child.setCount(100);
                else child.set(DataComponents.MAX_STACK_SIZE, 0);
                ItemStack owner = new ItemStack(Items.STONE);
                var content = new SulfurCubeContentDraft();
                content.setPresent(true);
                content.setStack(child);
                content.applyTo(owner);
                ItemStack before = owner.copy();
                boolean rejected = false;
                try {
                    ItemStackCodecs.validateStack(owner);
                } catch (RuntimeException expected) {
                    rejected = true;
                }
                require(rejected && ItemStack.matches(before, owner) && ItemStack.matches(child, content.stack()),
                    "Sulfur Cube nested " + invalid + " validation was skipped or mutated source data");
            }
        }
    }

    private static void verifySulfurCubeContentFailures() throws Exception {
        if (!SulfurCubeContentDraft.supported()) return;
        for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
            for (String invalid : List.of("count", "component")) {
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor();
                    ItemStack child = nestedOpeningItem();
                    if (invalid.equals("count")) child.setCount(100);
                    else child.set(DataComponents.MAX_STACK_SIZE, 0);
                    var panel = (dev.nineofgaming.itemlab.editor.panel.SulfurCubeContentEditorPanel) read(editor, "sulfurCubeContentEditor");
                    panel.replaceItemFromChild(child);
                    Object model = read(panel, "content");
                    ItemStack draftBefore = editor.draft().copy(), targetBefore = fixture.slots.get(0).copy();
                    Object history = read(editor, "history");
                    List<?> undoBefore = List.copyOf((Deque<?>) read(editor, "editorUndo"));
                    var loreBefore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                    var dataBefore = ((DataEditorPanel) read(editor, "dataEditor")).historyState();
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    require(!(boolean) read(editor, "statusSuccess") && read(editor, "requestedEditorTab").toString().equals("COMPONENTS")
                            && read(editor, "pendingComponentRecovery").equals("sulfur_cube_content") && (boolean) read(panel, "focusItemSlot")
                            && ItemStack.matches(draftBefore, editor.draft()) && ItemStack.matches(targetBefore, fixture.slots.get(0))
                            && !fixture.slots.containsKey(1) && fixture.writes.isEmpty() && read(editor, "history") == history
                            && !((ItemEditHistory) history).canUndo() && undoBefore.equals(List.copyOf((Deque<?>) read(editor, "editorUndo")))
                            && model == read(panel, "content") && ItemStack.matches(child, ((SulfurCubeContentDraft) model).stack())
                            && panel.isDirty() && loreBefore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                            && dataBefore.sameState(((DataEditorPanel) read(editor, "dataEditor")).historyState()),
                        "Sulfur Cube " + invalid + " rejection changed pending input/draft/history/target or failed recovery: " + read(editor, "status"));
                    child.setCount(99);
                    child.set(DataComponents.MAX_STACK_SIZE, 16);
                    panel.replaceItemFromChild(child);
                    var prepared = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                    require(prepared.succeeded(), "Sulfur Cube repair did not prepare a valid combined item");
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    ItemStack saved = fixture.slots.get(action.equals("giveDraftCopy") ? 1 : 0);
                    require((boolean) read(editor, "statusSuccess") && fixture.writes.size() == 1
                            && ItemStack.matches(prepared.candidate(), saved) && !editor.hasPendingNonRawEditorChanges()
                            && read(editor, "pendingComponentRecovery") == null
                            && ItemStack.matches(child, SulfurCubeContentDraft.fromStack(saved).stack()),
                        "Sulfur Cube repaired " + action + " lost combined edits or failed baseline/recovery cleanup");
                    assertCombined(saved);
                    if (action.equals("giveDraftCopy"))
                        require(ItemStack.matches(targetBefore, fixture.slots.get(0)), "Sulfur Cube Give Copy changed its original target");
                }
            }
        }
    }

    private static void verifySulfurCubeIdentifierInput() throws Exception {
        if (!SulfurCubeContentDraft.supported()) return;
        for (String action : List.of("applyDraftToSlot", "giveDraftCopy")) {
            for (String invalid : List.of("not an item id  ", "  ", "minecraft:air", "itemlab:unknown_item")) {
                try (Fixture fixture = new Fixture()) {
                    ItemStack child = nestedOpeningItem();
                    ItemStack opening = new ItemStack(Items.STONE);
                    var content = new SulfurCubeContentDraft();
                    content.setPresent(true);
                    content.setStack(child);
                    content.applyTo(opening);
                    ItemEditor editor = fixture.editedEditor(opening);
                    var panel = (dev.nineofgaming.itemlab.editor.panel.SulfurCubeContentEditorPanel) read(editor, "sulfurCubeContentEditor");
                    require(!panel.isDirty(), "Sulfur Cube identifier fixture did not start clean");
                    ImString input = (ImString) read(panel, "itemId");
                    input.set(invalid);
                    invoke(panel, "itemIdChanged", new Class<?>[0]);
                    require(panel.isDirty() && (boolean) read(panel, "itemIdPending") && ItemStack.matches(child, ((SulfurCubeContentDraft) read(panel, "content")).stack()),
                        "Invalid Sulfur Cube item-ID typing was omitted from dirty tracking or replaced the stored item");
                    ItemStack before = editor.draft().copy(), targetBefore = fixture.slots.get(0).copy();
                    var loreBefore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                    Object history = read(editor, "history");
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    require(!(boolean) read(editor, "statusSuccess") && read(editor, "pendingComponentRecovery").equals("sulfur_cube_content")
                            && read(panel, "pendingFocusSection").equals("content") && read(panel, "pendingFocusField").equals("item_id")
                            && !(boolean) read(panel, "focusItemSlot") && input.get().equals(invalid) && panel.isDirty()
                            && ItemStack.matches(before, editor.draft()) && ItemStack.matches(targetBefore, fixture.slots.get(0))
                            && fixture.writes.isEmpty() && !fixture.slots.containsKey(1) && read(editor, "history") == history
                            && !((ItemEditHistory) history).canUndo() && loreBefore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState()),
                        "Sulfur Cube invalid-ID Save changed input/state/targets/history or missed identifier recovery");
                    input.set("minecraft:dirt");
                    invoke(panel, "itemIdChanged", new Class<?>[0]);
                    require(panel.isDirty() && !(boolean) read(panel, "itemIdPending"), "Repaired Sulfur Cube identifier did not update its pending model");
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    ItemStack saved = fixture.slots.get(action.equals("giveDraftCopy") ? 1 : 0);
                    ItemStack expectedChild = child.transmuteCopy(Items.DIRT, child.getCount());
                    require((boolean) read(editor, "statusSuccess") && fixture.writes.size() == 1 && !editor.hasPendingNonRawEditorChanges()
                            && ItemStack.matches(expectedChild, SulfurCubeContentDraft.fromStack(saved).stack())
                            && read(panel, "pendingFocusSection") == null && read(editor, "pendingComponentRecovery") == null,
                        "Sulfur Cube ID repair lost child components or other pending edits");
                    assertCombined(saved);
                }
            }
        }
    }

    private static void verifyNestedItemCommits() throws Exception {
        for (String destination : nestedDestinations()) {
            for (String childAction : List.of("applyNestedItemEdit", "applyDraftToSlot")) {
                for (String parentAction : List.of("applyDraftToSlot", "giveDraftCopy")) {
                    try (Fixture fixture = new Fixture()) {
                        ItemEditor editor = fixture.editedEditor();
                        ItemStack originalChild = nestedOpeningItem();
                        addNestedDestination(editor, destination, originalChild);
                        ItemStack parentDraft = editor.draft().copy(), targetBefore = fixture.slots.get(0).copy();
                        Object parentHistory = read(editor, "history");
                        captureHistory(editor, fixture.client);
                        int parentUndoCount = ((Deque<?>) read(editor, "editorUndo")).size();
                        openNested(editor, fixture.client, destination, originalChild);
                        Deque<?> frames = (Deque<?>) read(editor, "nestedEditorFrames");
                        Object frame = frames.getLast();
                        Object parentState = read(frame, "parentState");
                        require(ItemStack.matches(originalChild, editor.draft()) && read(editor, "history") != parentHistory,
                            "Nested open did not isolate the child draft/history");
                        captureHistory(editor, fixture.client);
                        editNestedChild(editor);
                        captureHistory(editor, fixture.client);
                        Object appearance = read(editor, "appearanceEditor");
                        CodecComponentState model = (CodecComponentState) read(appearance, "itemModel");
                        model.setPresent(true);
                        model.setValue(JsonParser.parseString("\"bad child model id\""));
                        write(appearance, "dirty", true);
                        ItemStack childBefore = editor.draft().copy();
                        var loreBefore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                        var dataBefore = ((DataEditorPanel) read(editor, "dataEditor")).historyState();
                        List<?> childUndo = List.copyOf((Deque<?>) read(editor, "editorUndo"));
                        Object childHistory = read(editor, "history");
                        invoke(editor, childAction, new Class<?>[]{Minecraft.class}, fixture.client);
                        require(!(boolean) read(editor, "statusSuccess") && frames.size() == 1 && frames.getLast() == frame
                                && read(frame, "parentState") == parentState && read(editor, "history") == childHistory
                                && ItemStack.matches(childBefore, editor.draft()) && childUndo.equals(List.copyOf((Deque<?>) read(editor, "editorUndo")))
                                && !((ItemEditHistory) childHistory).canUndo() && model.value().equals(JsonParser.parseString("\"bad child model id\""))
                                && loreBefore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                                && dataBefore.sameState(((DataEditorPanel) read(editor, "dataEditor")).historyState())
                                && read(editor, "requestedEditorTab").toString().equals("PRESENTATION")
                                && read(appearance, "pendingFocusSection").equals("item_model") && read(appearance, "pendingFocusField").equals("identifier"),
                            "Rejected nested " + destination + " commit changed child/parent/history/input or failed recovery: " + read(editor, "status"));
                        require(fixture.writes.isEmpty() && !fixture.slots.containsKey(1) && ItemStack.matches(targetBefore, fixture.slots.get(0)),
                            "Rejected nested apply wrote an external target");
                        model.setValue(JsonParser.parseString("\"minecraft:diamond\""));
                        var prepared = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                        require(prepared.succeeded() && prepared.included().equals(List.of("basic_name_lore", "flags", "appearance", "data")),
                            "Nested child pending participants were omitted: " + prepared.failure());
                        ItemStack expectedChild = prepared.candidate().copy();
                        invoke(editor, childAction, new Class<?>[]{Minecraft.class}, fixture.client);
                        require((boolean) read(editor, "statusSuccess") && frames.isEmpty() && read(editor, "history") == parentHistory
                                && ItemStack.matches(parentDraft, editor.draft()) && ((Deque<?>) read(editor, "editorUndo")).size() == parentUndoCount + 1,
                            "Successful nested apply did not restore parent baseline/history with one replacement step: " + read(editor, "status"));
                        assertNestedChild(nestedPendingItem(editor, fixture.client, destination));
                        require(ItemStack.matches(expectedChild, nestedPendingItem(editor, fixture.client, destination)) && editor.hasPendingNonRawEditorChanges()
                                && fixture.writes.isEmpty() && !fixture.slots.containsKey(1) && ItemStack.matches(targetBefore, fixture.slots.get(0)),
                            "Nested apply lost composed child/parent pending input or wrote inventory before parent Save");
                        invoke(editor, "undo", new Class<?>[]{Minecraft.class}, fixture.client);
                        require(ItemStack.matches(originalChild, nestedPendingItem(editor, fixture.client, destination))
                            && editor.hasPendingNonRawEditorChanges() && fixture.writes.isEmpty(), "Nested replacement Undo lost the original child/other pending parent edits");
                        invoke(editor, "redo", new Class<?>[]{Minecraft.class}, fixture.client);
                        require(ItemStack.matches(expectedChild, nestedPendingItem(editor, fixture.client, destination)) && fixture.writes.isEmpty(),
                            "Nested replacement Redo lost the composed child or wrote inventory");
                        int beforeSaveUndo = ((Deque<?>) read(editor, "editorUndo")).size();
                        invoke(editor, parentAction, new Class<?>[]{Minecraft.class}, fixture.client);
                        captureHistory(editor, fixture.client);
                        require((boolean) read(editor, "statusSuccess") && fixture.writes.size() == 1
                                && ((Deque<?>) read(editor, "editorUndo")).size() == beforeSaveUndo + 1 && !editor.hasPendingNonRawEditorChanges(),
                            "Parent " + parentAction + " failed the combined nested commit/history: " + read(editor, "status"));
                        ItemStack saved = fixture.slots.get(parentAction.equals("giveDraftCopy") ? 1 : 0);
                        assertCombined(saved);
                        require(ItemStack.matches(expectedChild, nestedStoredItem(saved, fixture.client, destination)),
                            "Parent Save/Give Copy lost exact nested output");
                        if (parentAction.equals("giveDraftCopy"))
                            require(ItemStack.matches(targetBefore, fixture.slots.get(0)), "Parent Give Copy overwrote its original target");
                        // Nested payloads and output copies must not alias editable panel state.
                        ItemStack editable = nestedPendingItem(editor, fixture.client, destination);
                        editable.setCount(1);
                        require(nestedStoredItem(saved, fixture.client, destination).getCount() == 99, "Saved nested item aliases later editable copies");
                    }
                }
            }
        }
    }

    private static void verifyNestedItemDiscard() throws Exception {
        for (String destination : nestedDestinations()) {
            for (String action : List.of("revertDraft", "resetDraftToDefaults")) {
                try (Fixture fixture = new Fixture()) {
                    ItemEditor editor = fixture.editedEditor();
                    ItemStack originalChild = nestedOpeningItem();
                    addNestedDestination(editor, destination, originalChild);
                    ItemStack targetBefore = fixture.slots.get(0).copy();
                    var parentLore = ((LoreEditorPanel) read(editor, "loreEditor")).historyState();
                    var parentData = ((DataEditorPanel) read(editor, "dataEditor")).historyState();
                    Object parentHistory = read(editor, "history");
                    openNested(editor, fixture.client, destination, originalChild);
                    Deque<?> frames = (Deque<?>) read(editor, "nestedEditorFrames");
                    Object frame = frames.getLast();
                    Object parentState = read(frame, "parentState");
                    editNestedChild(editor);
                    Object entry = ((List<?>) read(read(editor, "dataEditor"), "entries")).getFirst();
                    ((ImString) read(entry, "value")).set("invalid number");
                    invoke(editor, "applyNestedItemEdit", new Class<?>[]{Minecraft.class}, fixture.client);
                    require(!(boolean) read(editor, "statusSuccess"), "Nested discard fixture did not reject the invalid child");
                    invoke(editor, action, new Class<?>[]{Minecraft.class}, fixture.client);
                    ItemStack expected = action.equals("revertDraft") ? originalChild : new ItemStack(Items.STONE, 99);
                    require((boolean) read(editor, "statusSuccess") && ItemStack.matches(expected, editor.draft())
                            && !editor.hasPendingNonRawEditorChanges() && frames.size() == 1 && frames.getLast() == frame
                            && read(frame, "parentState") == parentState && fixture.writes.isEmpty()
                            && ItemStack.matches(targetBefore, fixture.slots.get(0)),
                        "Nested discard changed its parent/target or retained invalid child input: " + destination + "/" + action);
                    invoke(editor, "applyNestedItemEdit", new Class<?>[]{Minecraft.class}, fixture.client);
                    require((boolean) read(editor, "statusSuccess") && frames.isEmpty() && read(editor, "history") == parentHistory
                            && ItemStack.matches(expected, nestedPendingItem(editor, fixture.client, destination))
                            && parentLore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                            && parentData.sameState(((DataEditorPanel) read(editor, "dataEditor")).historyState())
                            && fixture.writes.isEmpty() && ItemStack.matches(targetBefore, fixture.slots.get(0)),
                        "Applying discarded child lost parent pending edits or wrote inventory: " + destination + "/" + action);
                }
            }
        }
    }

    private static void verifyNestedItemCancel() throws Exception {
        for (String destination : nestedDestinations()) {
            try (Fixture fixture = new Fixture()) {
                ItemEditor editor = fixture.editedEditor();
                ItemStack originalChild = nestedOpeningItem();
                addNestedDestination(editor, destination, originalChild);
                var parentBefore = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                require(parentBefore.succeeded(), "Nested cancel parent preparation failed");
                ItemStack targetBefore = fixture.slots.get(0).copy();
                Object history = read(editor, "history");
                captureHistory(editor, fixture.client);
                List<?> undoBefore = List.copyOf((Deque<?>) read(editor, "editorUndo"));
                openNested(editor, fixture.client, destination, originalChild);
                editNestedChild(editor);
                invoke(editor, "cancelNestedItemEdit", new Class<?>[]{Minecraft.class}, fixture.client);
                var parentAfter = PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor, fixture.client));
                require((boolean) read(editor, "statusSuccess") && ((Deque<?>) read(editor, "nestedEditorFrames")).isEmpty()
                        && read(editor, "history") == history && undoBefore.equals(List.copyOf((Deque<?>) read(editor, "editorUndo")))
                        && parentAfter.succeeded() && ItemStack.matches(parentBefore.candidate(), parentAfter.candidate())
                        && ItemStack.matches(originalChild, nestedPendingItem(editor, fixture.client, destination))
                        && fixture.writes.isEmpty() && ItemStack.matches(targetBefore, fixture.slots.get(0)),
                    "Nested Cancel discarded parent pending edits, retained child edits, or changed history/target: " + read(editor, "status"));
                invoke(editor, "applyDraftToSlot", new Class<?>[]{Minecraft.class}, fixture.client);
                require((boolean) read(editor, "statusSuccess") && fixture.writes.size() == 1 && ItemStack.matches(parentBefore.candidate(), fixture.slots.get(0)),
                    "Parent Save after nested Cancel did not include its original pending edits");
            }
        }
    }

    private static void verifyMultiLevelNestedCommit() throws Exception {
        for (String middleDestination : SulfurCubeContentDraft.supported() ? List.of("bundle", "sulfur_cube") : List.of("bundle")) {
            verifyMultiLevelNestedCommit(middleDestination);
        }
    }

    private static void verifyMultiLevelNestedCommit(String middleDestination) throws Exception {
        try (Fixture fixture = new Fixture()) {
            ItemEditor editor = fixture.editedEditor();
            ItemStack leaf = nestedOpeningItem();
            ItemStack middle = new ItemStack(Items.STONE, 5);
            if (middleDestination.equals("bundle")) {
                var middleBundle = new dev.nineofgaming.itemlab.editor.container.BundleContentsDraft();
                middleBundle.setPresent(true);
                middleBundle.addItem(new ItemStack(Items.ARROW, 2));
                middleBundle.addItem(leaf);
                middleBundle.applyTo(middle);
            } else {
                var middleContent = new SulfurCubeContentDraft();
                middleContent.setPresent(true);
                middleContent.setStack(leaf);
                middleContent.applyTo(middle);
            }
            addNestedDestination(editor, "container", middle);
            ItemStack targetBefore = fixture.slots.get(0).copy();
            openNested(editor, fixture.client, "container", middle);
            Object lore = read(editor, "loreEditor");
            write(lore, "displayName", text("&aMiddle pending name"));
            Object data = read(editor, "dataEditor");
            CompoundTag middleTag = TagParser.parseCompoundFully("{middle_check:7,long_value:2L}");
            invoke(data, "loadTree", new Class<?>[]{CompoundTag.class}, middleTag);
            write(data, "dirty", true);
            var middleLoreBefore = ((LoreEditorPanel) lore).historyState();
            var middleDataBefore = ((DataEditorPanel) data).historyState();
            Object middleHistory = read(editor, "history");
            openNested(editor, fixture.client, middleDestination, leaf);
            Deque<?> frames = (Deque<?>) read(editor, "nestedEditorFrames");
            require(frames.size() == 2, "Multi-level nested opening lost a parent frame");
            List<?> frameBefore = List.copyOf(frames);
            editNestedChild(editor);
            CodecComponentState model = (CodecComponentState) read(read(editor, "appearanceEditor"), "itemModel");
            model.setPresent(true);
            model.setValue(JsonParser.parseString("\"bad deep model\""));
            write(read(editor, "appearanceEditor"), "dirty", true);
            ItemStack before = editor.draft().copy();
            invoke(editor, "applyDraftToSlot", new Class<?>[]{Minecraft.class}, fixture.client);
            require(!(boolean) read(editor, "statusSuccess") && frameBefore.equals(List.copyOf(frames)) && ItemStack.matches(before, editor.draft())
                && fixture.writes.isEmpty() && ItemStack.matches(targetBefore, fixture.slots.get(0)), "Deep nested failure changed an ancestor or external target");
            model.setValue(JsonParser.parseString("\"minecraft:diamond\""));
            invoke(editor, "applyDraftToSlot", new Class<?>[]{Minecraft.class}, fixture.client);
            require((boolean) read(editor, "statusSuccess") && frames.size() == 1 && fixture.writes.isEmpty(), "Deep child did not apply only to its immediate parent");
            assertNestedChild(nestedPendingItem(editor, fixture.client, middleDestination));
            require(read(editor, "history") == middleHistory
                    && middleLoreBefore.sameState(((LoreEditorPanel) read(editor, "loreEditor")).historyState())
                    && middleDataBefore.sameState(((DataEditorPanel) read(editor, "dataEditor")).historyState()),
                "Deep child Apply discarded its parent's pending text/data or history");
            invoke(editor, "applyDraftToSlot", new Class<?>[]{Minecraft.class}, fixture.client);
            require((boolean) read(editor, "statusSuccess") && frames.isEmpty() && fixture.writes.isEmpty()
                && ItemStack.matches(targetBefore, fixture.slots.get(0)), "Middle Apply wrote inventory or skipped restoring the root");
            ItemStack appliedMiddle = nestedPendingItem(editor, fixture.client, "container");
            require(appliedMiddle.getCount() == 5 && appliedMiddle.get(DataComponents.CUSTOM_NAME).getString().equals("Middle pending name")
                && appliedMiddle.get(DataComponents.CUSTOM_DATA).copyTag().equals(middleTag), "Middle pending edits were omitted while applying its child");
            assertNestedChild(nestedStoredItem(appliedMiddle, fixture.client, middleDestination));
            invoke(editor, "applyDraftToSlot", new Class<?>[]{Minecraft.class}, fixture.client);
            require((boolean) read(editor, "statusSuccess") && fixture.writes.size() == 1, "Root multi-level Save failed: " + read(editor, "status"));
            ItemStack saved = fixture.slots.get(0);
            assertCombined(saved);
            assertNestedChild(nestedStoredItem(nestedStoredItem(saved, fixture.client, "container"), fixture.client, middleDestination));
        }
    }

    private static List<String> nestedDestinations() {
        return SulfurCubeContentDraft.supported() ? List.of("container", "bundle", "sulfur_cube") : List.of("container", "bundle");
    }

    private static ItemStack nestedOpeningItem() throws Exception {
        ItemStack child = new ItemStack(Items.STONE, 3);
        child.set(DataComponents.CUSTOM_NAME, Component.literal("Opening child"));
        child.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(TagParser.parseCompoundFully("{b:1b,l:2L,f:3.5f,d:4.5d}")));
        return child;
    }

    @SuppressWarnings("unchecked")
    private static void addNestedDestination(ItemEditor editor, String destination, ItemStack child) throws Exception {
        if (destination.equals("sulfur_cube")) {
            ((dev.nineofgaming.itemlab.editor.panel.SulfurCubeContentEditorPanel) read(editor, "sulfurCubeContentEditor")).replaceItemFromChild(child);
            return;
        }
        Object panel = read(editor, "containerEditor");
        if (destination.equals("container")) {
            var contents = (dev.nineofgaming.itemlab.editor.container.ContainerContentsDraft) read(panel, "contents");
            contents.setPresent(true);
            contents.setItem(0, new ItemStack(Items.ARROW, 2));
            contents.setItem(200, child);
        } else {
            var bundle = (dev.nineofgaming.itemlab.editor.container.BundleContentsDraft) read(panel, "bundleContents");
            bundle.setPresent(true);
            bundle.addItem(new ItemStack(Items.ARROW, 2));
            bundle.addItem(child);
            ((List<Long>) read(panel, "bundleRowIds")).addAll(List.of(101L, 102L));
        }
        write(panel, "dirty", true);
    }

    private static void openNested(ItemEditor editor, Minecraft client, String destination, ItemStack child) throws Exception {
        int before = ((Deque<?>) read(editor, "nestedEditorFrames")).size();
        if (destination.equals("container")) editor.requestContainedItemEdit(200, child);
        else if (destination.equals("bundle")) editor.requestBundleItemEdit(1, child);
        else editor.requestSulfurCubeItemEdit(child);
        invoke(editor, "openRequestedNestedItem", new Class<?>[]{Minecraft.class}, client);
        require((boolean) read(editor, "statusSuccess") && ((Deque<?>) read(editor, "nestedEditorFrames")).size() == before + 1,
            "Could not open isolated nested " + destination + " fixture: " + read(editor, "status"));
    }

    @SuppressWarnings("unchecked")
    private static void editNestedChild(ItemEditor editor) throws Exception {
        ((ImInt) read(editor, "count")).set(99);
        write(editor, "basicDirty", true);
        Object lore = read(editor, "loreEditor");
        write(lore, "displayName", text("&b&lNested combined name"));
        ((List<RichTextLine>) read(lore, "rows")).add(text("&d&nNested combined lore"));
        Object flags = read(editor, "flagEditor");
        ((ImBoolean) read(flags, "overrideMaxStackSize")).set(true);
        ((ImInt) read(flags, "maxStackSize")).set(16);
        write(flags, "nonTooltipDirty", true);
        CompoundTag tag = TagParser.parseCompoundFully("{b:1b,l:2L,f:3.5f,d:4.5d,child_check:42}");
        Object data = read(editor, "dataEditor");
        invoke(data, "loadTree", new Class<?>[]{CompoundTag.class}, tag);
        write(data, "dirty", true);
    }

    private static ItemStack nestedPendingItem(ItemEditor editor, Minecraft client, String destination) throws Exception {
        if (destination.equals("sulfur_cube"))
            return nestedStoredItem(((dev.nineofgaming.itemlab.editor.panel.SulfurCubeContentEditorPanel) read(editor, "sulfurCubeContentEditor")).pendingStack(client), client, destination);
        return nestedStoredItem(((dev.nineofgaming.itemlab.editor.panel.ContainerEditorPanel) read(editor, "containerEditor")).pendingStack(client), client, destination);
    }

    private static ItemStack nestedStoredItem(ItemStack owner, Minecraft client, String destination) {
        if (destination.equals("sulfur_cube")) return SulfurCubeContentDraft.fromStack(owner).stack();
        if (destination.equals("container"))
            return dev.nineofgaming.itemlab.editor.container.ContainerContentsDraft.fromStack(owner, ItemStackCodecs.jsonOps(client)).item(200);
        return dev.nineofgaming.itemlab.editor.container.BundleContentsDraft.fromStack(owner).item(1);
    }

    private static void assertNestedChild(ItemStack child) throws Exception {
        require(child.is(Items.STONE) && child.getCount() == 99 && child.getMaxStackSize() == 16
                && child.get(DataComponents.CUSTOM_NAME).getString().equals("Nested combined name")
                && child.get(DataComponents.CUSTOM_NAME).toFlatList().stream().anyMatch(part -> part.getStyle().isBold())
                && child.get(DataComponents.LORE).lines().getFirst().getString().equals("Nested combined lore")
                && child.get(DataComponents.LORE).lines().getFirst().toFlatList().stream().anyMatch(part -> part.getStyle().isUnderlined())
                && child.get(DataComponents.ITEM_MODEL).toString().equals("minecraft:diamond")
                && child.get(DataComponents.CUSTOM_DATA).copyTag().equals(TagParser.parseCompoundFully("{b:1b,l:2L,f:3.5f,d:4.5d,child_check:42}")),
            "Nested output lost pending fields, overstacked count or typed NBT");
    }

    private static void assertCombined(ItemStack stack) {
        assertCombinedPanels(stack, Items.STONE);
        require(stack.get(DataComponents.CUSTOM_DATA).copyTag().getInt("commit_check").orElse(-1) == 42, "pending custom data is missing");
    }

    private static void assertCombinedPanels(ItemStack stack, Item expectedItem) {
        assertCombinedCore(stack, expectedItem);
        require(stack.get(DataComponents.EQUIPPABLE).slot() == EquipmentSlot.HEAD
                && stack.get(DataComponents.MAP_COLOR).rgb() == 0x123456,
            "grouped Equipment/Map values are missing");
    }

    private static void assertCombinedCore(ItemStack stack, Item expectedItem) {
        assertCombinedTextAndFlags(stack, expectedItem);
        require(stack.get(DataComponents.ITEM_MODEL).toString().equals("minecraft:diamond"), "pending Appearance is missing");
    }

    private static void assertCombinedTextAndFlags(ItemStack stack, Item expectedItem) {
        require(stack != null && stack.is(expectedItem) && stack.getCount() == 99 && stack.getMaxStackSize() == 16,
            "combined item/count/Flags values are missing");
        require(stack.get(DataComponents.CUSTOM_NAME).getString().equals("Combined name")
                && stack.get(DataComponents.CUSTOM_NAME).toFlatList().stream().anyMatch(part -> part.getStyle().isBold()),
            "formatted name/style was lost");
        require(stack.get(DataComponents.LORE).lines().getFirst().getString().equals("Combined lore")
                && stack.get(DataComponents.LORE).lines().getFirst().toFlatList().stream().anyMatch(part -> part.getStyle().isUnderlined()),
            "formatted lore/style was lost");
    }

    private static RichTextLine text(String source) {
        return text(source, TextInputFormat.COLOR_CODES);
    }

    private static RichTextLine text(String source, TextInputFormat format) {
        RichTextLine line = RichTextLine.createEmpty(format);
        line.editorSource(false, false).set(source);
        line.commitEditorSource();
        return line;
    }

    private static void captureHistory(ItemEditor editor, Minecraft client) throws Exception {
        Object snapshot = invoke(editor, "captureEditorSnapshot", new Class<?>[]{Minecraft.class}, client);
        // Zero means no active native control; the same recorder is used by the live frame.
        invoke(editor, "recordEditorHistorySnapshot", new Class<?>[]{snapshot.getClass(), int.class}, snapshot, 0);
    }

    private record Write(int slot, ItemStack stack) {
    }

    /**
     * Only audience lookup is mocked; parsing and bidirectional native conversion remain real.
     */
    private static final class TextBridge implements AutoCloseable {
        private final MockedStatic<MinecraftClientAudiences> lookup;

        private TextBridge() {
            MinecraftClientAudiences audiences = mock(MinecraftClientAudiences.class, CALLS_REAL_METHODS);
            when(audiences.nonWrappingSerializer()).thenReturn(MinecraftAudiences.nonWrappingSerializer(() -> fixtureRegistries));
            lookup = mockStatic(MinecraftClientAudiences.class);
            lookup.when(MinecraftClientAudiences::of).thenReturn(audiences);
        }

        @Override
        public void close() {
            lookup.close();
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final Minecraft client = mock(Minecraft.class);
        private final Inventory inventory = mock(Inventory.class);
        private final Map<Integer, ItemStack> slots = new HashMap<>();
        private final List<Write> writes = new ArrayList<>();
        private final MockedStatic<Minecraft> currentClient;

        private Fixture() {
            client.level = mock(ClientLevel.class);
            when(client.level.registryAccess()).thenReturn(fixtureRegistries);
            when(client.level.dimension()).thenReturn(Level.OVERWORLD);
            client.player = mock(LocalPlayer.class);
            when(client.player.hasInfiniteMaterials()).thenReturn(true);
            when(client.player.getInventory()).thenReturn(inventory);
            client.gameMode = mock(MultiPlayerGameMode.class);
            when(inventory.getFreeSlot()).thenReturn(1);
            when(inventory.getItem(anyInt())).thenAnswer(call -> slots.getOrDefault(call.getArgument(0), ItemStack.EMPTY));
            doAnswer(call -> {
                slots.put(call.getArgument(0), call.getArgument(1));
                return null;
            })
                .when(inventory).setItem(anyInt(), any(ItemStack.class));
            doAnswer(call -> {
                writes.add(new Write(call.getArgument(1), call.getArgument(0)));
                return null;
            })
                .when(client.gameMode).handleCreativeModeItemAdd(any(ItemStack.class), anyInt());
            currentClient = mockStatic(Minecraft.class, CALLS_REAL_METHODS);
            currentClient.when(Minecraft::getInstance).thenReturn(client);
        }

        private ItemEditor editedEditor() throws Exception {
            ItemStack opening = new ItemStack(Items.STONE);
            opening.set(DataComponents.CUSTOM_NAME, Component.literal("Opening name"));
            return editedEditor(opening);
        }

        @SuppressWarnings("unchecked")
        private ItemEditor editedEditor(ItemStack opening) throws Exception {
            ItemEditor editor = cleanEditor(opening);
            addPendingEdits(editor);
            return editor;
        }

        @SuppressWarnings("unchecked")
        private void addPendingEdits(ItemEditor editor) throws Exception {
            ((ImInt) read(editor, "count")).set(99);
            write(editor, "basicDirty", true);
            Object lore = read(editor, "loreEditor");
            write(lore, "displayName", text("&6&lCombined name"));
            ((List<RichTextLine>) read(lore, "rows")).add(text("&b&nCombined lore"));
            Object flags = read(editor, "flagEditor");
            ((ImBoolean) read(flags, "overrideMaxStackSize")).set(true);
            ((ImInt) read(flags, "maxStackSize")).set(16);
            write(flags, "nonTooltipDirty", true);
            Object equipment = read(editor, "equipmentEditor");
            CodecComponentState equippable = (CodecComponentState) read(equipment, "equippable");
            equippable.setPresent(true);
            equippable.setValue(JsonParser.parseString("{\"slot\":\"head\"}"));
            write(equipment, "dirty", true);
            Object appearance = read(editor, "appearanceEditor");
            CodecComponentState model = (CodecComponentState) read(appearance, "itemModel");
            model.setPresent(true);
            model.setValue(JsonParser.parseString("\"minecraft:diamond\""));
            write(appearance, "dirty", true);
            Object map = read(editor, "mapEditor");
            ((ImBoolean) read(map, "colorPresent")).set(true);
            write(map, "mapColor", 0x123456);
            write(map, "dirty", true);
            CompoundTag tag = new CompoundTag();
            tag.putInt("commit_check", 42);
            Object data = read(editor, "dataEditor");
            invoke(data, "loadTree", new Class<?>[]{CompoundTag.class}, tag);
            write(data, "dirty", true);
        }

        private ItemEditor cleanEditor(ItemStack opening) throws Exception {
            ItemEditor editor = new ItemEditor();
            slots.put(0, opening.copy());
            invoke(editor, "setDraft", new Class<?>[]{Minecraft.class, ItemStack.class, boolean.class}, client, opening, false);
            write(editor, "historyEnabled", true);
            var constructor = EditorItemTarget.class.getDeclaredConstructor(ItemStack.class, int.class, EditorItemTarget.Kind.class);
            constructor.setAccessible(true);
            write(editor, "target", constructor.newInstance(opening, 0, EditorItemTarget.Kind.HELD_ITEM));
            return editor;
        }

        @Override
        public void close() {
            currentClient.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<PendingEditTransaction.Participant<ItemStack>> participants(ItemEditor editor, Minecraft client) throws Exception {
        return (List<PendingEditTransaction.Participant<ItemStack>>) invoke(editor, "pendingParticipants", new Class<?>[]{Minecraft.class}, client);
    }





}

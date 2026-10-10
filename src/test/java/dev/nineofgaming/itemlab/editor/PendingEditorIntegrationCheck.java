package dev.nineofgaming.itemlab.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Lifecycle;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import dev.nineofgaming.itemlab.editor.component.CodecComponentState;
import dev.nineofgaming.itemlab.editor.diamondfire.DiamondFireFeatures;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.panel.DataEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.FlagEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.LoreEditorPanel;
import dev.nineofgaming.itemlab.editor.state.ItemEditHistory;
import dev.nineofgaming.itemlab.editor.ui.text.RichTextLine;
import dev.nineofgaming.itemlab.editor.ui.text.TextInputFormat;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import imgui.type.ImDouble;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.nineofgaming.itemlab.editor.EditorCheckSupport.invoke;
import static dev.nineofgaming.itemlab.editor.EditorCheckSupport.read;
import static dev.nineofgaming.itemlab.editor.EditorCheckSupport.require;
import static dev.nineofgaming.itemlab.editor.EditorCheckSupport.write;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Real editor/participant fixtures with mocked client registry access; no window or target-slot write is needed.
 */
public final class PendingEditorIntegrationCheck {
    private PendingEditorIntegrationCheck() {
    }

    public static void main(String[] arguments) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registries = VanillaRegistries.createLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries)
            .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
        Minecraft client = mock(Minecraft.class);
        client.level = mock(ClientLevel.class);
        when(client.level.registryAccess()).thenReturn(clientRegistries(registries));
        // Isolate preferences from real user settings in this standalone test JVM.
        Path configDirectory = Files.createTempDirectory("itemlab-pending-check-");
        try (MockedStatic<Minecraft> currentClient = mockStatic(Minecraft.class, CALLS_REAL_METHODS)) {
            // Recovery callbacks use the initialized client singleton even when participant IO is headless.
            currentClient.when(Minecraft::getInstance).thenReturn(client);
            Field configDir = FabricLoader.getInstance().getClass().getDeclaredField("configDir");
            configDir.setAccessible(true);
            configDir.set(FabricLoader.getInstance(), configDirectory);
            verifyRegistryCoverageAndDirtyQueries();
            verifyRealPanelsComposeWithoutAdvancingBaselines();
            verifyFirstMiddleAndLastFailuresRetainRealBuffers();
            verifyAttributeAndEnchantmentFieldFocus();
            verifyDataMetadataFocus();
            verifyMusicPreviewAndRecovery();
            verifyMusicDescriptionValidationAfterTyping();
            verifySnbtFormattingAfterTyping();
            verifyCompassAndProfileRecovery();
            verifyEquipmentRecovery();
            verifyMapAndPotRecovery();
            verifyAppearanceRecovery();
            verifyBookRecovery();
            verifyBannerRecovery();
            verifyPotionRecovery();
            verifyFireworkRecovery();
            verifyAdventureRecovery();
            verifyUseRecovery();
            verifyCombatRecovery();
            verifyContainerRecovery();
            if (DiamondFireFeatures.available()) verifyDataAndDiamondFireConflict();
            verifyRawConflictRetainsSource();
        } finally {
            Files.delete(configDirectory);
        }
    }

    private static RegistryAccess clientRegistries(HolderLookup.Provider registries) {
        List<Registry<?>> clientRegistries = new ArrayList<>(BuiltInRegistries.REGISTRY.stream().toList());
        registries.listRegistries().forEach(lookup -> {
            if (clientRegistries.stream().noneMatch(registry -> registry.key().equals(lookup.key()))) {
                clientRegistries.add(copyRegistry(lookup));
            }
        });
        return new RegistryAccess.ImmutableRegistryAccess(clientRegistries);
    }

    private static <T> Registry<T> copyRegistry(HolderLookup.RegistryLookup<T> lookup) {
        MappedRegistry<T> registry = new MappedRegistry<>(ResourceKey.createRegistryKey(lookup.key().identifier()), Lifecycle.stable());
        lookup.listElements().forEach(holder -> Registry.register(registry, holder.key(), holder.value()));
        return registry.freeze();
    }

    private static void verifyRegistryCoverageAndDirtyQueries() throws Exception {
        ItemEditor editor = editor();
        participants(editor);
        PendingEditorRegistry registry = (PendingEditorRegistry) read(editor, "pendingEditorRegistry");
        registry.verifyCoverage(editor);
        require(!registry.hasPending(id -> true), "clean editor was reported dirty");
        ((ImInt) read(editor, "count")).set(99);
        write(editor, "basicDirty", true);
        require(editor.hasPendingNonRawEditorChanges(), "basic changes were omitted from the registered aggregate");

        FuturePanelHost host = new FuturePanelHost();
        PendingEditorRegistry missing = new PendingEditorRegistry();
        missing.register("existing", host.existing, host.existing::isDirty);
        expectFailure(() -> missing.verifyCoverage(host), "forgotten", "an unregistered future panel passed coverage");
        missing.register("future", host.forgotten, () -> {
            throw new IllegalArgumentException("invalid input");
        });
        missing.verifyCoverage(host);
        require(missing.hasPending(id -> true), "a failed dirty query became clean");
        require(!missing.hasPending(id -> !id.equals("future")), "dirty-query filtering did not exclude the owner");
        expectFailure(() -> missing.register("existing", new FuturePanel(), () -> false),
            "Duplicate", "duplicate participant IDs were accepted");
        expectFailure(() -> missing.register("duplicate_owner", host.existing, () -> false),
            "more than once", "the same panel was registered twice");
    }

    private static void verifyRealPanelsComposeWithoutAdvancingBaselines() throws Exception {
        ItemEditor editor = editedPanels();
        ItemStack original = editor.draft().copy();
        DataEditorPanel data = (DataEditorPanel) read(editor, "dataEditor");
        var buffers = data.historyState();
        var result = prepare(editor);
        require(result.succeeded(), "real panel composition failed: " + result.failure());
        require(result.included().equals(List.of("basic_name_lore", "flags", "data")),
            "real dirty panels were omitted or composed in a different order: " + result.included());
        ItemStack candidate = result.candidate();
        require(candidate.get(DataComponents.CUSTOM_NAME) == null
                && candidate.getMaxStackSize() == 16
                && candidate.get(DataComponents.CUSTOM_DATA).copyTag().getInt("fixture").orElse(-1) == 7,
            "composition lost a real panel's component");
        require(ItemStack.matches(original, editor.draft()), "preflight mutated the real draft");
        require(buffers.sameState(data.historyState()) && data.isDirty(), "preflight changed Data's buffers/baseline");
        require(((LoreEditorPanel) read(editor, "loreEditor")).isDirty()
            && ((FlagEditorPanel) read(editor, "flagEditor")).isDirty(), "preflight cleaned another panel");
        var again = prepare(editor);
        require(again.succeeded() && ItemStack.matches(candidate, again.candidate()), "repeat preflight was not idempotent");
        require(!((ItemEditHistory) read(editor, "history")).canUndo(), "preflight created an undo entry");
    }

    private static void verifyFirstMiddleAndLastFailuresRetainRealBuffers() throws Exception {
        for (int position = 0; position < 3; position++) {
            ItemEditor editor = editedPanels();
            DataEditorPanel data = (DataEditorPanel) read(editor, "dataEditor");
            FlagEditorPanel flags = (FlagEditorPanel) read(editor, "flagEditor");
            Object badNode = null;
            if (position == 0) {
                ((ImString) read(editor, "itemId")).set("invalid item identifier");
                write(editor, "basicDirty", true);
            } else if (position == 1) {
                ((ImBoolean) read(flags, "overrideMaxDamage")).set(true);
                ((ImInt) read(flags, "maxDamage")).set(10);
            } else {
                CompoundTag nested = new CompoundTag();
                nested.putInt("number", 3);
                CompoundTag root = new CompoundTag();
                root.put("nested", nested);
                invoke(data, "loadTree", new Class<?>[]{CompoundTag.class}, root);
                Object parent = ((List<?>) read(data, "entries")).getFirst();
                badNode = ((List<?>) read(parent, "children")).getFirst();
                ((ImString) read(badNode, "value")).set("broken numeric text");
            }
            var beforeData = data.historyState();
            ItemStack before = editor.draft().copy();
            String beforeId = ((ImString) read(editor, "itemId")).get();
            String beforeName = ((RichTextLine) read(read(editor, "loreEditor"), "displayName"))
                .editorSource(false, false).get();
            require(!commit(editor), "invalid real panel unexpectedly committed");
            require(ItemStack.matches(before, editor.draft()), "failed global save partially changed the draft");
            require(beforeId.equals(((ImString) read(editor, "itemId")).get())
                && beforeName.equals(((RichTextLine) read(read(editor, "loreEditor"), "displayName"))
                .editorSource(false, false).get()), "failed recovery changed identity/name input");
            require(beforeData.sameState(data.historyState()), "failed recovery changed Data input");
            require(flags.isDirty() && data.isDirty() && ((LoreEditorPanel) read(editor, "loreEditor")).isDirty(),
                "failed global save advanced a real panel baseline");
            require(!((ItemEditHistory) read(editor, "history")).canUndo()
                && ((java.util.Deque<?>) read(editor, "editorUndo")).isEmpty(), "failed save changed history");
            require(read(editor, "requestedEditorTab").toString().equals(new String[]{"NAME", "FLAGS", "DATA"}[position]),
                "failure opened the wrong real editor");
            if (position == 0) require((boolean) read(editor, "focusItemId"), "item input was not targeted");
            if (position == 1)
                require((boolean) read(flags, "focusMaxStackSize"), "invalid stack size was not targeted");
            if (position == 2) {
                require(read(data, "focusNodeId").equals(read(badNode, "id"))
                        && read(data, "focusNodeField").toString().equals("VALUE")
                        && !((java.util.Set<?>) read(data, "focusAncestorIds")).isEmpty(),
                    "nested invalid value and its collapsed ancestors were not targeted");
            }
        }
    }

    private static void verifyDataAndDiamondFireConflict() throws Exception {
        ItemEditor editor = editedPanels();
        Object tags = read(editor, "diamondFireTagEditor");
        CompoundTag publicValues = new CompoundTag();
        publicValues.putDouble("hypercube:fixture", 1);
        CompoundTag custom = new CompoundTag();
        custom.put("PublicBukkitValues", publicValues);
        ItemStack pending = editor.draft().copy();
        pending.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        invoke(tags, "restorePending", new Class<?>[]{net.minecraft.client.Minecraft.class, ItemStack.class}, null, pending);
        var data = (DataEditorPanel) read(editor, "dataEditor");
        var beforeData = data.historyState();
        ItemStack before = editor.draft().copy();
        require(!commit(editor), "Data and DiamondFire ownership overlap was silently merged");
        require(ItemStack.matches(before, editor.draft()) && beforeData.sameState(data.historyState()),
            "a real custom-data conflict changed input or the draft");
        require(read(editor, "requestedEditorTab").toString().equals("DIAMONDFIRE"), "conflict did not route to tags");
        require((long) read(tags, "focusRowId") != 0, "tag conflict did not request a row input");
    }

    @SuppressWarnings("unchecked")
    private static void verifyAttributeAndEnchantmentFieldFocus() throws Exception {
        ItemEditor editor = editor();
        Object attributes = read(editor, "attributeEditor");
        write(attributes, "textFormat", TextInputFormat.COLOR_CODES);
        Class<?> attributeRow = Class.forName("dev.nineofgaming.itemlab.editor.panel.AttributeRow");
        var constructor = attributeRow.getDeclaredConstructor(String.class, String.class, double.class,
            int.class, int.class, int.class, Component.class, TextInputFormat.class);
        constructor.setAccessible(true);
        String attributeId = BuiltInRegistries.ATTRIBUTE.keySet().iterator().next().toString();
        List<Object> rows = (List<Object>) read(attributes, "rows");
        rows.add(constructor.newInstance(attributeId, "itemlab:first", 1.0, 0, 0, 0,
            Component.empty(), TextInputFormat.COLOR_CODES));
        Object invalid = constructor.newInstance(attributeId, "invalid modifier id", 2.0, 0, 0, 0,
            Component.empty(), TextInputFormat.COLOR_CODES);
        rows.add(invalid);
        write(attributes, "dirty", true);
        ItemStack before = editor.draft().copy();
        require(!commit(editor), "invalid modifier ID was committed");
        require(read(attributes, "focusInputRowId").equals(read(invalid, "rowId"))
                && read(attributes, "focusInputField").toString().equals("MODIFIER"),
            "Attributes targeted the first row instead of the invalid modifier");
        require(((ImString) read(invalid, "modifierId")).get().equals("invalid modifier id")
            && ItemStack.matches(before, editor.draft()), "attribute recovery changed input or draft");
        ((ImString) read(invalid, "modifierId")).set("itemlab:second");
        ((ImDouble) read(invalid, "amount")).set(Double.NaN);
        require(!commit(editor) && read(attributes, "focusInputField").toString().equals("AMOUNT"),
            "non-finite attribute amount did not target the amount control");
        ((ImDouble) read(invalid, "amount")).set(2.0);
        var valid = prepare(editor);
        require(valid.succeeded() && valid.candidate().get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().size() == 2,
            "valid real attribute rows did not compose");

        ItemEditor enchantmentHost = editor();
        Object enchantments = read(enchantmentHost, "enchantmentEditor");
        Class<?> enchantmentRow = Class.forName("dev.nineofgaming.itemlab.editor.panel.EnchantmentRow");
        var enchantmentConstructor = enchantmentRow.getDeclaredConstructor(String.class, int.class);
        enchantmentConstructor.setAccessible(true);
        List<Object> enchantmentRows = (List<Object>) read(enchantments, "rows");
        enchantmentRows.add(enchantmentConstructor.newInstance("minecraft:sharpness", 1));
        Object badEnchantment = enchantmentConstructor.newInstance("invalid enchantment id", 2);
        enchantmentRows.add(badEnchantment);
        invoke(enchantments, "requestPendingFocus", new Class<?>[0]);
        require(read(enchantments, "focusIdentifierRowId").equals(read(badEnchantment, "rowId"))
                && ((ImString) read(badEnchantment, "id")).get().equals("invalid enchantment id"),
            "Enchantment recovery missed or changed the invalid row");
    }

    private static void verifyDataMetadataFocus() throws Exception {
        ItemEditor editor = editedPanels();
        Object data = read(editor, "dataEditor");
        Object entityTarget = java.util.Arrays.stream(read(data, "target").getClass().getEnumConstants())
            .filter(value -> value.toString().equals("ENTITY_DATA")).findFirst().orElseThrow();
        write(data, "target", entityTarget);
        write(data, "entityDataPresent", true);
        ((ImString) read(data, "entityType")).set("invalid entity type");
        ItemStack before = editor.draft().copy();
        require(!commit(editor) && (boolean) read(data, "focusMetadataType"), "invalid entity metadata did not focus its type");
        require(((ImString) read(data, "entityType")).get().equals("invalid entity type")
                && read(data, "target") == entityTarget && ItemStack.matches(before, editor.draft()),
            "metadata recovery retargeted the editor or changed input/draft");
    }

    private static void verifyMusicPreviewAndRecovery() throws Exception {
        ItemEditor editor = editedPanels();
        Object music = read(editor, "musicEditor");
        CodecComponentState instrument = (CodecComponentState) read(music, "instrument");
        instrument.setPresent(true);
        ((ImBoolean) read(music, "customInstrument")).set(true);
        write(music, "dirty", true);
        JsonElement baseline = instrument.value().deepCopy();
        require((boolean) invoke(music, "isDirty", new Class<?>[0]), "pending Music model was omitted");
        require(baseline.equals(instrument.value()), "Music dirty query overwrote the loaded instrument model");

        ImString description = (ImString) read(music, "instrumentDescriptionSource");
        description.set("{broken pending description");
        Object descriptionEditor = read(music, "instrumentDescriptionEditor");
        write(descriptionEditor, "cursorByteOffset", 8);
        write(descriptionEditor, "cursorLine", 1);
        write(descriptionEditor, "cursorColumn", 9);
        JsonObject customBefore = ((JsonObject) read(music, "customInstrumentValue")).deepCopy();
        ItemStack before = editor.draft().copy();
        var beforeData = ((DataEditorPanel) read(editor, "dataEditor")).historyState();
        require(!commit(editor), "invalid Music description was committed");
        require(read(editor, "requestedEditorTab").toString().equals("MUSIC"), "Music failure opened the wrong tab");
        assertPendingField(music, "instrument", "description");
        require((boolean) read(read(music, "instrumentDescriptionEditor"), "focusRequested"),
            "invalid Music JSON did not request the actual nested code input");
        require((int) read(descriptionEditor, "cursorByteOffset") == 8 && (int) read(descriptionEditor, "cursorColumn") == 9,
            "Music recovery reset the description cursor");
        require(description.get().equals("{broken pending description")
                && customBefore.equals(read(music, "customInstrumentValue")) && baseline.equals(instrument.value()),
            "Music failure changed source or stored models");
        require(ItemStack.matches(before, editor.draft())
                && beforeData.sameState(((DataEditorPanel) read(editor, "dataEditor")).historyState())
                && ((FlagEditorPanel) read(editor, "flagEditor")).isDirty()
                && !((ItemEditHistory) read(editor, "history")).canUndo(),
            "Music failure partially applied earlier panels or changed history");

        // A later codec attempt must also leave the stored instrument model untouched.
        description.set("\"Valid description\"");
        require(!commit(editor), "client-backed Music codecs unexpectedly ran without a client");
        require(baseline.equals(instrument.value()) && customBefore.equals(read(music, "customInstrumentValue")),
            "Music preview overwrote its model before a codec failure");

        JsonObject custom = (JsonObject) read(music, "customInstrumentValue");
        custom.addProperty("use_duration", Double.NaN);
        invoke(music, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(music, "instrument", "use_duration");
        require(Double.isNaN(custom.get("use_duration").getAsDouble()), "numeric recovery changed Music input");
        custom.addProperty("use_duration", 7.0);
        ((ImBoolean) read(music, "customInstrument")).set(false);
        ((ImString) read(music, "registeredInstrumentId")).set("minecraft:missing_instrument");
        write(music, "knownInstrumentIds", java.util.Set.of("minecraft:ponder_goat_horn"));
        invoke(music, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(music, "instrument", "instrument_id");

        instrument.setPresent(false);
        CodecComponentState jukebox = (CodecComponentState) read(music, "jukeboxPlayable");
        jukebox.setPresent(true);
        jukebox.setValue(JsonParser.parseString("\"minecraft:13\""));
        CodecComponentState note = (CodecComponentState) read(music, "noteBlockSound");
        note.setPresent(true);
        note.setValue(JsonParser.parseString("\"invalid sound identifier\""));
        JsonElement beforeNote = note.value().deepCopy();
        invoke(music, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(music, "note_block_sound", "sound");
        require(!(boolean) invoke(music, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "instrument", "sound")
                && !(boolean) invoke(music, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "jukebox_playable", "song"),
            "an earlier Music section consumed a later section's recovery focus");
        assertPendingField(music, "note_block_sound", "sound");
        require(beforeNote.equals(note.value()) && !(boolean) read(read(music, "instrumentDescriptionEditor"), "focusRequested"),
            "Music recovery changed a later value or retained stale code-editor focus");
        require((boolean) invoke(music, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "note_block_sound", "sound")
                && !(boolean) invoke(music, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "note_block_sound", "sound"),
            "field recovery was not consumed exactly once");
        invoke(music, "requestPendingFocus", new Class<?>[0]);
        invoke(music, "resetEditorUi", new Class<?>[0]);
        require(read(music, "pendingFocusSection") == null && read(music, "pendingFocusField") == null,
            "draft synchronization did not clear transient field recovery");
    }

    private static void verifyMusicDescriptionValidationAfterTyping() throws Exception {
        ItemEditor editor = editedPanels();
        Object music = read(editor, "musicEditor");
        CodecComponentState instrument = (CodecComponentState) read(music, "instrument");
        instrument.setPresent(true);
        ((ImBoolean) read(music, "customInstrument")).set(true);
        write(music, "dirty", true);
        ImString description = (ImString) read(music, "instrumentDescriptionSource");
        JsonElement baseline = instrument.value().deepCopy();
        JsonObject customBefore = ((JsonObject) read(music, "customInstrumentValue")).deepCopy();
        ItemStack draftBefore = editor.draft().copy();
        for (String invalid : List.of("{", "[", "{\"text\":", "\"unterminated")) {
            description.set("\"Custom instrument\"");
            String beforeInput = (String) invoke(music, "descriptionProblem", new Class<?>[0]);
            require(beforeInput.isBlank(), "valid pre-input description was rejected");
            // InputText replaces the source after the draw path has computed beforeInput.
            description.set(invalid);
            String afterInput = (String) invoke(music, "descriptionProblem", new Class<?>[0]);
            require(!afterInput.isBlank(), "description actions used stale pre-input validation for " + invalid);
            String previewProblem = (String) invoke(music, "instrumentProblem", new Class<?>[]{net.minecraft.client.Minecraft.class}, new Object[]{null});
            require(!previewProblem.isBlank(), "render validation ignored invalid current JSON");
            require(!commit(editor), "invalid typed description was committed");
            assertPendingField(music, "instrument", "description");
            require(description.get().equals(invalid) && baseline.equals(instrument.value())
                    && customBefore.equals(read(music, "customInstrumentValue"))
                    && ItemStack.matches(draftBefore, editor.draft())
                    && !((ItemEditHistory) read(editor, "history")).canUndo(),
                "typing/recovery changed input, models, draft, or history");
        }
        description.set("\"Repaired description\"");
        require(((String) invoke(music, "descriptionProblem", new Class<?>[0])).isBlank(),
            "description remained invalid after repair");
        JsonObject rebuilt = (JsonObject) invoke(music, "buildInstrumentValue", new Class<?>[0]);
        require(rebuilt.get("description").getAsString().equals("Repaired description")
                && baseline.equals(instrument.value()) && customBefore.equals(read(music, "customInstrumentValue")),
            "repair failed to build a valid isolated value");
    }

    private static void verifySnbtFormattingAfterTyping() throws Exception {
        verifySnbtFormatAction("adventureEditor", "snbtProblem", "adventure.nbt.invalid");
        verifySnbtFormatAction("adventureEditor", "componentSnbtProblem", "adventure.snbt.invalid");
        verifySnbtFormatAction("containerEditor", "snbtCompoundProblem", "container.lock.snbt.invalid");
        verifySnbtFormatAction("combatEditor", "snbtCompoundProblem", "combat.projectile.components.invalid");
    }

    private static void verifySnbtFormatAction(String panelField, String validator, String problemKey) throws Exception {
        ItemEditor editor = editor();
        Object panel = read(editor, panelField);
        ImString source = new ImString("{}", 65_536);
        ItemStack draftBefore = editor.draft().copy();
        ItemEditHistory history = (ItemEditHistory) read(editor, "history");
        for (String invalid : List.of("{", "{value:", "{value:\"unterminated", "[]")) {
            source.set("{}");
            String beforeInput = (String) invoke(panel, validator, new Class<?>[]{String.class}, source.get());
            require(beforeInput.isBlank(), panelField + " rejected valid pre-input SNBT");
            // Simulate a buffer changing after the initial draw validation, before Format runs.
            source.set(invalid);
            String afterInput = (String) invoke(panel, validator, new Class<?>[]{String.class}, source.get());
            require(!afterInput.isBlank(), panelField + " accepted invalid post-input SNBT: " + invalid);
            for (boolean wasDirty : List.of(false, true)) {
                write(panel, "dirty", wasDirty);
                editor.setStatus(true, "previous success");
                boolean formatted = (boolean) invoke(panel, "formatPendingSnbt",
                    new Class<?>[]{ImString.class, String.class}, source, problemKey);
                require(!formatted && source.get().equals(invalid), panelField + " lost rejected SNBT input");
                require(read(panel, "dirty").equals(wasDirty), panelField + " changed dirty state after failed Format");
                require(!(boolean) read(editor, "statusSuccess") && !((String) read(editor, "status")).isBlank(),
                    panelField + " failed Format without reporting an error");
                require(ItemStack.matches(draftBefore, editor.draft()) && !history.canUndo(),
                    panelField + " changed draft/history after failed Format");
            }
        }
        source.set("{b:2,a:1}");
        CompoundTag beforeFormatting = ItemStackCodecs.parseCompoundSnbt(source.get());
        String expected = ItemStackCodecs.formatSnbt(source.get());
        write(panel, "dirty", false);
        require((boolean) invoke(panel, "formatPendingSnbt", new Class<?>[]{ImString.class, String.class}, source, problemKey),
            panelField + " failed Format after repairing SNBT");
        require(source.get().equals(expected) && beforeFormatting.equals(ItemStackCodecs.parseCompoundSnbt(source.get()))
            && (boolean) read(panel, "dirty"), panelField + " lost values/dirty state during valid Format");
        require(ItemStack.matches(draftBefore, editor.draft()) && !history.canUndo(),
            panelField + " committed a draft/history change during valid Format");
    }

    private static void verifyCompassAndProfileRecovery() throws Exception {
        ItemEditor editor = editor();
        Object compass = read(editor, "compassEditor");
        CodecComponentState tracker = (CodecComponentState) read(compass, "tracker");
        tracker.setPresent(true);
        tracker.setValue(JsonParser.parseString("{\"target\":{\"dimension\":\"invalid dimension id\",\"pos\":[1,64,2]},\"tracked\":true}"));
        JsonElement trackerBefore = tracker.value().deepCopy();
        invoke(compass, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(compass, "tracker", "dimension");
        require(trackerBefore.equals(tracker.value()) && tracker.present(), "Compass recovery changed its target or baseline");

        Object head = read(editor, "playerHeadEditor");
        CodecComponentState profile = (CodecComponentState) read(head, "profile");
        profile.setPresent(true);
        JsonObject value = JsonParser.parseString("{\"name\":\"Player\",\"id\":\"invalid uuid source\",\"properties\":[]}").getAsJsonObject();
        profile.setValue(value);
        JsonElement profileBefore = profile.value().deepCopy();
        invoke(head, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(head, "profile", "uuid");
        require(profileBefore.equals(profile.value()), "Player Head recovery normalized or replaced invalid UUID input");

        value.remove("id");
        value.addProperty("name", "A_name_that_is_far_too_long_for_a_player");
        profile.setValue(value);
        invoke(head, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(head, "profile", "name");
        value.addProperty("name", "Player");
        value.addProperty("texture", "invalid texture id");
        profile.setValue(value);
        invoke(head, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(head, "profile", "texture");

        value.remove("texture");
        JsonArray properties = value.getAsJsonArray("properties");
        properties.add(JsonParser.parseString("{\"name\":\"textures\",\"value\":\"first payload\"}"));
        properties.add(JsonParser.parseString("{\"name\":\"\",\"value\":\"retained second payload\"}"));
        profile.setValue(value);
        profileBefore = profile.value().deepCopy();
        Object rowIdsBefore = List.copyOf((List<?>) read(head, "propertyRowIds"));
        invoke(head, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(head, "profile", "property/1/name");
        require(profileBefore.equals(profile.value()) && rowIdsBefore.equals(read(head, "propertyRowIds")),
            "property recovery reloaded list rows or changed payloads");
    }

    private static void verifyEquipmentRecovery() throws Exception {
        for (String[] fixture : List.of(
            new String[]{"equippable", "{\"slot\":\"invalid_slot\"}", "equippable", "slot"},
            new String[]{"equippable", "{\"slot\":\"head\",\"equip_sound\":\"not an id\"}", "equippable", "equippable/equip_sound"},
            new String[]{"equippable", "{\"slot\":\"head\",\"asset_id\":\"not an id\"}", "equippable", "equippable/asset"},
            new String[]{"equippable", "{\"slot\":\"head\",\"camera_overlay\":\"not an id\"}", "equippable", "equippable/overlay"},
            new String[]{"trim", "{\"material\":\"not an id\",\"pattern\":\"minecraft:sentry\"}", "trim", "trim/material"},
            new String[]{"trim", "{\"material\":\"minecraft:iron\",\"pattern\":\"not an id\"}", "trim", "trim/pattern"},
            new String[]{"providedTrimMaterial", "\"not an id\"", "provided_trim", "provided_trim/material"},
            new String[]{"breakSound", "\"not an id\"", "break_sound", "break_sound"}
        )) {
            ItemEditor editor = editor();
            Object equipment = read(editor, "equipmentEditor");
            CodecComponentState state = (CodecComponentState) read(equipment, fixture[0]);
            state.setPresent(true);
            state.setValue(JsonParser.parseString(fixture[1]));
            JsonElement before = state.value().deepCopy();
            invoke(equipment, "requestPendingFocus", new Class<?>[0]);
            assertPendingField(equipment, fixture[2], fixture[3]);
            require(before.equals(state.value()) && state.present(), "Equipment recovery changed its model/presence");
            require(!(boolean) invoke(equipment, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "other_section", fixture[3]),
                "an earlier section consumed Equipment recovery");
        }
        Object equipment = read(editor(), "equipmentEditor");
        CodecComponentState state = (CodecComponentState) read(equipment, "equippable");
        state.setPresent(true);
        state.setValue(JsonParser.parseString("{\"slot\":\"head\",\"allowed_entities\":[\"minecraft:player\",\"bad entity id\"]}"));
        JsonElement before = state.value().deepCopy();
        invoke(equipment, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(equipment, "equippable", "allowed_entities");
        Object rowFocus = read(equipment, "allowedEntitiesFocus");
        require(!(boolean) invoke(rowFocus, "consumeRow", new Class<?>[]{int.class}, 0)
            && (boolean) invoke(rowFocus, "consumeRow", new Class<?>[]{int.class}, 1)
            && !(boolean) invoke(rowFocus, "consumeRow", new Class<?>[]{int.class}, 1), "restriction focus was not row-specific/one-shot");
        require(before.equals(state.value()), "restriction recovery rewrote the list");
        state.setValue(JsonParser.parseString("{\"slot\":\"head\",\"allowed_entities\":[\"minecraft:player\",\"minecraft:missing_entity\"]}"));
        write(equipment, "entities", List.of(IdSuggestion.of("minecraft:player")));
        invoke(equipment, "requestPendingFocus", new Class<?>[0]);
        require((int) read(rowFocus, "requestedRow") == 1, "unknown registered entity did not select its row");
        state.setValue(JsonParser.parseString("{\"slot\":\"head\",\"asset_id\":\"minecraft:valid\"}"));
        @SuppressWarnings("unchecked") Map<String, ImString> buffers = (Map<String, ImString>) read(equipment, "textBuffers");
        ImString input = new ImString("retained invalid asset input", 512);
        buffers.put("equippable/asset", input);
        before = state.value().deepCopy();
        invoke(equipment, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(equipment, "equippable", "equippable/asset");
        require(buffers.get("equippable/asset") == input && input.get().equals("retained invalid asset input")
            && before.equals(state.value()), "Equipment recovery cleared or normalized a pending buffer");
        invoke(equipment, "resetEditorUi", new Class<?>[0]);
        require(read(equipment, "pendingFocusSection") == null, "Equipment synchronization retained a stale field request");
    }

    @SuppressWarnings("unchecked")
    private static void verifyMapAndPotRecovery() throws Exception {
        ItemEditor editor = editor();
        Object map = read(editor, "mapEditor");
        ((ImBoolean) read(map, "decorationsPresent")).set(true);
        List<Object> rows = (List<Object>) read(map, "decorations");
        Object first = invoke(map, "createDecorationRow", new Class<?>[0]);
        Object second = invoke(map, "createDecorationRow", new Class<?>[0]);
        ((ImString) read(first, "name")).set(" home ");
        ((ImString) read(second, "name")).set("home");
        rows.add(first);
        rows.add(second);
        long secondId = (long) read(second, "rowId");
        invoke(map, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(map, "map_decorations", "row/" + secondId + "/name");
        require((long) read(map, "focusDecorationRowId") == secondId && rows.get(1) == second
            && ((ImString) read(first, "name")).get().equals(" home "), "Map recovery reordered rows or normalized names");
        ((ImString) read(second, "name")).set("second");
        ((ImString) read(second, "type")).set("bad decoration id");
        invoke(map, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(map, "map_decorations", "row/" + secondId + "/type");
        require((boolean) invoke(map, "hasPendingSectionFocus", new Class<?>[]{String.class}, "map_decorations")
                && !(boolean) invoke(map, "hasPendingSectionFocus", new Class<?>[]{String.class}, "map_id"),
            "Map recovery did not reveal only its containing section");
        ((ImString) read(second, "type")).set("minecraft:missing_decoration");
        write(map, "decorationTypes", List.of(IdSuggestion.of("minecraft:player")));
        invoke(map, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(map, "map_decorations", "row/" + secondId + "/type");
        ((ImString) read(second, "type")).set("minecraft:player");
        ((ImDouble) read(second, "x")).set(Double.NaN);
        invoke(map, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(map, "map_decorations", "row/" + secondId + "/x");
        invoke(map, "sync", new Class<?>[]{ItemStack.class}, editor.draft());
        require(read(map, "pendingFocusSection") == null && (long) read(map, "focusDecorationRowId") == 0L,
            "Map synchronization retained a recovery request");

        Object pot = read(editor, "decoratedPotEditor");
        ((ImBoolean) read(pot, "decorationsPresent")).set(true);
        Map<?, ImString> sides = (Map<?, ImString>) read(pot, "sideValues");
        for (var entry : sides.entrySet()) {
            String original = entry.getValue().get();
            entry.getValue().set("not an item id");
            invoke(pot, "requestPendingFocus", new Class<?>[0]);
            assertPendingField(pot, "pot_decorations", "side/" + entry.getKey().toString().toLowerCase(java.util.Locale.ROOT));
            require(entry.getValue().get().equals("not an item id") && sides.get(entry.getKey()) == entry.getValue(),
                "pot recovery replaced a pending side buffer");
            entry.getValue().set(original);
        }
        ((ImBoolean) read(pot, "decorationsPresent")).set(false);
        invoke(pot, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(pot, "pot_decorations", "$enabled");
        invoke(pot, "sync", new Class<?>[]{ItemStack.class}, editor.draft());
        require(read(pot, "pendingFocusSection") == null, "pot synchronization retained a recovery request");
    }

    @SuppressWarnings("unchecked")
    private static void verifyAppearanceRecovery() throws Exception {
        ItemEditor editor = editor();
        Object appearance = read(editor, "appearanceEditor");
        write(appearance, "itemNamePresent", true);
        write(appearance, "itemNameChanged", true);
        ImString key = (ImString) read(appearance, "itemNameTranslationKey");
        key.set("   ");
        invoke(appearance, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(appearance, "item_name", "translation_key");
        require(key == read(appearance, "itemNameTranslationKey") && key.get().equals("   ")
            && (boolean) read(appearance, "itemNameChanged"), "Appearance recovery replaced its name/baseline");
        key.set("item.minecraft.stone");
        Map<String, ImString> buffers = (Map<String, ImString>) read(appearance, "textBuffers");
        for (String section : List.of("item_model", "tooltip_style")) {
            CodecComponentState state = (CodecComponentState) read(appearance, section.equals("item_model") ? "itemModel" : "tooltipStyle");
            state.setPresent(true);
            ImString input = new ImString("not an id", 128);
            buffers.put(section, input);
            JsonElement before = state.value().deepCopy();
            invoke(appearance, "requestPendingFocus", new Class<?>[0]);
            assertPendingField(appearance, section, "identifier");
            require(buffers.get(section) == input && before.equals(state.value()), "Appearance recovery normalized a buffer/model");
            require(!(boolean) invoke(appearance, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "item_name", "$enabled")
                    && (boolean) invoke(appearance, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, section, "identifier")
                    && !(boolean) invoke(appearance, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, section, "identifier"),
                "Appearance field focus was consumed by another control or more than once");
            buffers.remove(section);
            state.setPresent(false);
        }
        CodecComponentState custom = (CodecComponentState) read(appearance, "customModelData");
        custom.setPresent(true);
        custom.setValue(JsonParser.parseString("\"wrong representation\""));
        JsonElement before = custom.value().deepCopy();
        invoke(appearance, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(appearance, "custom_model_data", "$enabled");
        require(before.equals(custom.value()), "Appearance recovery normalized a codec-only failure");
        invoke(appearance, "clearTransientState", new Class<?>[0]);
        require(read(appearance, "pendingFocusSection") == null, "Appearance sync retained recovery focus");
    }

    @SuppressWarnings("unchecked")
    private static void verifyBookRecovery() throws Exception {
        ItemEditor editor = editor();
        Object book = read(editor, "bookEditor");
        write(book, "textFormat", TextInputFormat.COLOR_CODES);
        ItemStack original = new ItemStack(Items.WRITABLE_BOOK);
        original.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(List.of(
            Filterable.passThrough("First"), new Filterable<>("Second", Optional.of("Filtered second")))));
        original.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
            new Filterable<>("Title", Optional.of("Filtered title")), "Author", 1,
            List.of(Filterable.passThrough(Component.literal("Signed page"))), true));
        write(editor, "draft", original);
        invoke(book, "sync", new Class<?>[]{ItemStack.class}, original);
        require(!(boolean) read(book, "showWritten"), "Dual-content writable book loaded with signed intent");
        require(invoke(book, "activeContentType", new Class<?>[0]) == DataComponents.WRITABLE_BOOK_CONTENT,
            "Book badge must describe the writable view, not merely written-component presence");
        // Deliberately test recovery away from a pending signing choice, not the
        // old implicit signing caused merely by written-component presence.
        write(book, "showWritten", true);
        require(invoke(book, "activeContentType", new Class<?>[0]) == DataComponents.WRITTEN_BOOK_CONTENT,
            "Book badge must follow a switch to the signed view");
        ItemStack baseline = original.copy();
        List<Object> writable = (List<Object>) read(book, "writablePages");
        List<Object> written = (List<Object>) read(book, "writtenPages");
        Object second = writable.get(1);
        RichTextLine page = (RichTextLine) read(second, "text");
        ImString source = page.editorSource(false, false);
        source.set("x".repeat(WritableBookContent.PAGE_EDIT_LENGTH + 1));
        page.commitEditorSource();
        write(book, "writableDirty", true);
        write(book, "dirty", true);
        invoke(book, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(book, "writable_book_pages", "page/1");
        require((boolean) read(book, "showWritten") && !(boolean) invoke(book, "viewingWritten", new Class<?>[0])
                && (int) read(book, "writablePage") == 1 && (int) read(book, "writtenPage") == 0,
            "Book page recovery changed signing intent or selected the wrong page");
        require(invoke(book, "activeContentType", new Class<?>[0]) == DataComponents.WRITABLE_BOOK_CONTENT,
            "Book badge must describe the recovered page view without changing signing intent");
        require(writable.get(1) == second && read(second, "text") == page && page.editorSource(false, false) == source
                && read(second, "filtered").equals(Optional.of("Filtered second"))
                && (boolean) read(book, "writableDirty") && written.size() == 1 && ItemStack.matches(baseline, editor.draft()),
            "Book recovery changed page buffers, filtered values, dirty state, or draft");
        require(!(boolean) invoke(book, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "written_book_metadata", "title"),
            "Book metadata stole page recovery focus");
        source.set("Second");
        page.commitEditorSource();
        ItemStack repaired = (ItemStack) invoke(book, "pendingStack", new Class<?>[]{net.minecraft.client.Minecraft.class}, new Object[]{null});
        require(repaired.is(Items.WRITTEN_BOOK), "Book recovery unsigned the repaired candidate");

        write(book, "showWritten", false);
        RichTextLine title = (RichTextLine) read(book, "title");
        ImString titleSource = title.editorSource(false, false);
        titleSource.set("t".repeat(WrittenBookContent.TITLE_MAX_LENGTH + 1));
        title.commitEditorSource();
        write(book, "writtenDirty", true);
        write(book, "dirty", true);
        invoke(book, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(book, "written_book_metadata", "title");
        require(!(boolean) read(book, "showWritten") && (boolean) invoke(book, "viewingWritten", new Class<?>[0])
            && read(book, "title") == title && title.editorSource(false, false) == titleSource
            && read(book, "filteredTitle").equals(Optional.of("Filtered title")), "Book title recovery signed or reset the book");
        titleSource.set("Title");
        title.commitEditorSource();
        repaired = (ItemStack) invoke(book, "pendingStack", new Class<?>[]{net.minecraft.client.Minecraft.class}, new Object[]{null});
        require(repaired.is(Items.WRITABLE_BOOK), "Book metadata recovery signed the repaired candidate");
        invoke(book, "returnToSigningView", new Class<?>[0]);
        require(read(book, "recoveryWritten") == null && read(book, "pendingFocusSection") == null
                && !(boolean) read(book, "showWritten") && read(book, "title") == title,
            "Returning from recovery retained focus or changed pending input/signing intent");
        invoke(book, "requestPendingFocus", new Class<?>[0]);
        invoke(book, "addPage", new Class<?>[0]);
        require(written.size() == 2 && writable.size() == 2 && !(boolean) read(book, "showWritten"),
            "Book recovery actions edited the hidden representation or changed signing intent");
        invoke(book, "sync", new Class<?>[]{ItemStack.class}, original);
        require(read(book, "pendingFocusSection") == null && read(book, "recoveryWritten") == null,
            "Book synchronization retained recovery state");
    }

    @SuppressWarnings("unchecked")
    private static void verifyBannerRecovery() throws Exception {
        Object banner = read(editor(), "bannerEditor");
        write(banner, "patternIds", List.of(IdSuggestion.of("minecraft:base"), IdSuggestion.of("minecraft:stripe_bottom")));
        ((ImBoolean) read(banner, "patternsPresent")).set(true);
        List<Object> rows = (List<Object>) read(banner, "patterns");
        Object first = invoke(banner, "createPatternRow", new Class<?>[0]);
        Object second = invoke(banner, "createPatternRow", new Class<?>[0]);
        rows.add(first);
        rows.add(second);
        ImString source = (ImString) read(second, "patternId");
        for (String bad : List.of("not a pattern id", "minecraft:missing_pattern")) {
            source.set(bad);
            invoke(banner, "requestPendingFocus", new Class<?>[0]);
            assertPendingField(banner, "patterns", "row/" + read(second, "rowId") + "/id");
            require(rows.get(0) == first && rows.get(1) == second && read(second, "patternId") == source
                    && source.get().equals(bad) && read(banner, "focusPatternRowId").equals(read(second, "rowId")),
                "Banner recovery replaced or reordered a pending pattern row");
        }
        ((ImBoolean) read(banner, "patternsPresent")).set(false);
        CodecComponentState provided = (CodecComponentState) read(banner, "providedPatterns");
        provided.setPresent(true);
        provided.setValue(JsonParser.parseString("[\"minecraft:base\",\"not a pattern id\"]"));
        JsonElement before = provided.value().deepCopy();
        Map<String, ImString> buffers = (Map<String, ImString>) read(banner, "textBuffers");
        ImString pending = new ImString("retained bad pattern input", 128);
        buffers.put("banner/provided_patterns/@identifier/1", pending);
        invoke(banner, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(banner, "provides_banner_patterns", "identifiers");
        Object focus = read(banner, "providedPatternsFocus");
        require((int) read(focus, "requestedRow") == 1 && provided.value().equals(before)
                && buffers.get("banner/provided_patterns/@identifier/1") == pending,
            "Banner collection recovery lost its model/buffer or selected the wrong row");
        require(!(boolean) invoke(focus, "consumeRow", new Class<?>[]{int.class}, 0)
            && (boolean) invoke(focus, "consumeRow", new Class<?>[]{int.class}, 1)
            && !(boolean) invoke(focus, "consumeRow", new Class<?>[]{int.class}, 1), "Banner collection focus was not one-shot");
        require(!(boolean) invoke(banner, "invalidPatternIdentifier", new Class<?>[]{String.class, List.class, boolean.class},
                "#minecraft:pattern_item/flower", List.of(IdSuggestion.of("#minecraft:pattern_item/flower")), true),
            "Banner recovery treated a scalar holder-set tag as a direct pattern ID");
        invoke(banner, "resetEditorUi", new Class<?>[0]);
        require(read(banner, "pendingFocusSection") == null, "Banner reset retained its section request");
    }

    @SuppressWarnings("unchecked")
    private static void verifyPotionRecovery() throws Exception {
        Object potion = read(editor(), "potionEditor");
        CodecComponentState contents = (CodecComponentState) read(potion, "contents");
        contents.setPresent(true);
        contents.setValue(JsonParser.parseString("{\"potion\":\"not a potion id\"}"));
        invoke(potion, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(potion, "contents", "base_potion");
        contents.setValue(JsonParser.parseString("{\"potion\":\"minecraft:water\",\"custom_effects\":[{\"id\":\"minecraft:speed\"},{\"id\":\"not an effect id\"}]}"));
        invoke(potion, "ensureEffectRowIds", new Class<?>[]{int.class}, 2);
        List<Long> rowIds = (List<Long>) read(potion, "effectRowIds");
        long secondId = rowIds.get(1);
        JsonElement before = contents.value().deepCopy();
        invoke(potion, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(potion, "contents", "effect_" + secondId + "/id");
        require(rowIds.get(1) == secondId && (long) read(potion, "focusEffectRowId") == secondId && before.equals(contents.value()),
            "Potion recovery changed its effect model/identity");
        JsonObject second = contents.value().getAsJsonObject().getAsJsonArray("custom_effects").get(1).getAsJsonObject();
        second.addProperty("id", "minecraft:speed");
        second.add("hidden_effect", JsonParser.parseString("{\"amplifier\":256}"));
        require(invoke(potion, "invalidEffectField", new Class<?>[]{JsonObject.class, String.class, int.class}, second, "effect_" + secondId, 0) == null,
            "Potion recovery rejected a numeric byte conversion accepted by the native codec");
        second.getAsJsonObject("hidden_effect").addProperty("amplifier", "bad strength");
        invoke(potion, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(potion, "contents", "effect_" + secondId + "_hidden/strength");
        require(second.getAsJsonObject("hidden_effect").get("amplifier").getAsString().equals("bad strength"),
            "Potion recovery normalized an invalid hidden effect");
        second.remove("hidden_effect");
        second.addProperty("duration", -20);
        require(invoke(potion, "invalidEffectField", new Class<?>[]{JsonObject.class, String.class, int.class}, second, "effect_" + secondId, 0) == null,
            "Potion recovery imposed a duration constraint absent from the native codec");
        Map<String, ImString> buffers = (Map<String, ImString>) read(potion, "textBuffers");
        ImString input = new ImString("bad cached effect ID", 128);
        buffers.put("effect_" + secondId + "/id", input);
        before = contents.value().deepCopy();
        invoke(potion, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(potion, "contents", "effect_" + secondId + "/id");
        require(before.equals(contents.value()) && buffers.get("effect_" + secondId + "/id") == input,
            "Potion recovery overwrote a cached identifier/model");
        buffers.clear();
        CodecComponentState scale = (CodecComponentState) read(potion, "durationScale");
        scale.setPresent(true);
        scale.setValue(JsonParser.parseString("-1"));
        invoke(potion, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(potion, "duration_scale", "value");
        require(scale.value().getAsDouble() == -1, "Potion recovery clamped the duration scale");
        require(!(boolean) invoke(potion, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "contents", "$enabled")
                && (boolean) invoke(potion, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "duration_scale", "value")
                && !(boolean) invoke(potion, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "duration_scale", "value"),
            "Potion field request was consumed by another section or more than once");
    }

    @SuppressWarnings("unchecked")
    private static void verifyFireworkRecovery() throws Exception {
        Object firework = read(editor(), "fireworkEditor");
        CodecComponentState rocket = (CodecComponentState) read(firework, "fireworks");
        rocket.setPresent(true);
        rocket.setValue(JsonParser.parseString("{\"flight_duration\":\"bad duration\",\"explosions\":[]}"));
        invoke(firework, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(firework, "rocket", "flight_duration");
        require(rocket.value().getAsJsonObject().get("flight_duration").getAsString().equals("bad duration"), "Firework recovery normalized flight duration");
        rocket.setValue(JsonParser.parseString("{\"flight_duration\":255,\"explosions\":[{\"shape\":\"small_ball\"},{\"shape\":\"unknown shape\"}]}"));
        JsonElement before = rocket.value().deepCopy();
        invoke(firework, "requestPendingFocus", new Class<?>[0]);
        List<Long> rows = (List<Long>) read(firework, "explosionRowIds");
        long secondId = rows.get(1);
        assertPendingField(firework, "rocket", "rocket_explosion_" + secondId + "/shape");
        require((long) read(firework, "focusExplosionRowId") == secondId && rocket.value().equals(before),
            "Firework recovery changed its model or selected the wrong explosion");
        JsonObject second = rocket.value().getAsJsonObject().getAsJsonArray("explosions").get(1).getAsJsonObject();
        second.addProperty("shape", "star");
        second.add("fade_colors", JsonParser.parseString("[16777215,{}]"));
        invoke(firework, "requestPendingFocus", new Class<?>[0]);
        String listId = "rocket_explosion_" + secondId + "/fade";
        List<Long> colors = ((Map<String, List<Long>>) read(firework, "colorRowIds")).get(listId);
        long colorId = colors.get(1);
        assertPendingField(firework, "rocket", listId + "/row/" + colorId);
        invoke(firework, "requestPendingFocus", new Class<?>[0]);
        require(((Map<String, List<Long>>) read(firework, "colorRowIds")).get(listId) == colors && colors.get(1) == colorId
                && rows.get(1) == secondId && second.getAsJsonArray("fade_colors").get(1).isJsonObject(),
            "Firework color recovery replaced row identities or normalized an invalid color");
        second.remove("fade_colors");
        second.add("colors", JsonParser.parseString("[-1]"));
        CodecComponentState star = (CodecComponentState) read(firework, "starExplosion");
        star.setPresent(true);
        star.setValue(JsonParser.parseString("{\"shape\":\"unknown shape\"}"));
        invoke(firework, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(firework, "star", "star_explosion/shape");
        require(!(boolean) invoke(firework, "hasPendingSectionFocus", new Class<?>[]{String.class}, "rocket"),
            "Valid unsigned-byte flight duration incorrectly stole the star request");
        star.setValue(JsonParser.parseString("\"wrong representation\""));
        invoke(firework, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(firework, "star", "$enabled");
        require(star.value().isJsonPrimitive(), "Firework fallback normalized a malformed component");
    }

    @SuppressWarnings("unchecked")
    private static void verifyAdventureRecovery() throws Exception {
        ItemEditor editor = editor();
        Object adventure = read(editor, "adventureEditor");
        Object canBreak = read(adventure, "canBreak");
        Object canPlace = read(adventure, "canPlaceOn");
        ((ImBoolean) read(canBreak, "present")).set(true);
        addAdventureRow(canBreak, "minecraft:stone");
        ((ImBoolean) read(canPlace, "present")).set(true);
        Object first = addAdventureRow(canPlace, "minecraft:stone");
        Object row = addAdventureRow(canPlace, "minecraft:oak_stairs");
        String path = "predicate/" + read(row, "id") + "/";
        JsonElement badBlocks = JsonParser.parseString("[\"minecraft:stone\",\"not a block id\"]");
        write(row, "blocks", badBlocks);
        Map<String, ImString> buffers = (Map<String, ImString>) read(adventure, "textBuffers");
        String bufferKey = "adventure/can_place_on/" + read(row, "id") + "/blocks/@identifier/1";
        ImString pending = new ImString("retained invalid block", 128);
        buffers.put(bufferKey, pending);
        ItemStack before = editor.draft().copy();
        for (int repeat = 0; repeat < 2; repeat++) {
            invoke(adventure, "requestPendingFocus", new Class<?>[0]);
            assertPendingField(adventure, "can_place_on", path + "blocks");
            require((int) read(read(row, "blocksFocus"), "requestedRow") == 1
                && read(canPlace, "focusPredicateRowId").equals(read(row, "id"))
                && read(row, "blocks") == badBlocks && buffers.get(bufferKey) == pending
                && ((List<Object>) read(canPlace, "predicates")).get(0) == first
                && ((List<Object>) read(canPlace, "predicates")).get(1) == row
                && ItemStack.matches(before, editor.draft()), "Adventure recovery changed input/model or predicate identity");
        }
        Object blockFocus = read(row, "blocksFocus");
        require(!(boolean) invoke(blockFocus, "consumeRow", new Class<?>[]{int.class}, 0)
            && (boolean) invoke(blockFocus, "consumeRow", new Class<?>[]{int.class}, 1)
            && !(boolean) invoke(blockFocus, "consumeRow", new Class<?>[]{int.class}, 1), "Adventure collection focus was not one-shot");
        buffers.clear();
        write(row, "blocks", new com.google.gson.JsonPrimitive("minecraft:oak_stairs"));
        ((ImBoolean) read(row, "statePresent")).set(true);
        List<Object> states = (List<Object>) read(row, "states");
        var constructor = Class.forName("dev.nineofgaming.itemlab.editor.panel.AdventureEditorPanel$StateRow")
            .getDeclaredConstructor(String.class, String.class);
        constructor.setAccessible(true);
        Object facing = constructor.newInstance("facing", "north");
        Object half = constructor.newInstance("half", "invalid half");
        states.add(facing);
        states.add(half);
        String statePath = path + "state/" + read(half, "id") + "/";
        invoke(adventure, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(adventure, "can_place_on", statePath + "exact");
        require(read(row, "focusStateRowId").equals(read(half, "id")) && states.get(1) == half,
            "Adventure state recovery did not reveal the exact stable row");
        ImString name = (ImString) read(half, "name");
        for (String bad : List.of("", "not_a_property")) {
            name.set(bad);
            invoke(adventure, "requestPendingFocus", new Class<?>[0]);
            assertPendingField(adventure, "can_place_on", statePath + "name");
            require(read(half, "name") == name && name.get().equals(bad), "Adventure recovery rewrote a state property");
        }
        name.set(" facing ");
        invoke(adventure, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(adventure, "can_place_on", path + "state/" + read(facing, "id") + "/name");
        name.set("half");
        ((ImInt) read(half, "mode")).set(1);
        invoke(adventure, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(adventure, "can_place_on", statePath + "minimum");
        ((ImString) read(half, "minimum")).set("bottom");
        ((ImString) read(half, "maximum")).set("bad maximum");
        invoke(adventure, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(adventure, "can_place_on", statePath + "maximum");
        ((ImBoolean) read(row, "statePresent")).set(false);
        for (String field : List.of("nbt", "components", "predicates")) {
            String prefix = field.equals("predicates") ? "componentPredicates" : field;
            ((ImBoolean) read(row, prefix + "Present")).set(true);
            ImString source = (ImString) read(row, prefix + "Source");
            source.set("{broken pending " + field);
            Object codeEditor = read(row, prefix + "Editor");
            write(codeEditor, "cursorByteOffset", 5);
            write(codeEditor, "cursorColumn", 6);
            invoke(adventure, "requestPendingFocus", new Class<?>[0]);
            assertPendingField(adventure, "can_place_on", path + field);
            require((boolean) read(codeEditor, "focusRequested") && (int) read(codeEditor, "cursorByteOffset") == 5
                && (int) read(codeEditor, "cursorColumn") == 6 && read(row, prefix + "Source") == source
                && source.get().equals("{broken pending " + field), "Adventure SNBT recovery reset its source/cursor");
            require(!(boolean) invoke(adventure, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "can_break", path + field)
                    && (boolean) invoke(adventure, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "can_place_on", path + field)
                    && !(boolean) invoke(adventure, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "can_place_on", path + field),
                "Adventure recovery request was consumed by an earlier section or twice");
            source.set("{}");
        }
        ((List<Object>) read(canPlace, "predicates")).clear();
        invoke(adventure, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(adventure, "can_place_on", "add_predicate");
        require(((ImBoolean) read(canPlace, "present")).get(), "Adventure recovery disabled an empty pending section");
        invoke(adventure, "sync", new Class<?>[]{net.minecraft.client.Minecraft.class, ItemStack.class}, null, editor.draft());
        require(read(adventure, "pendingFocusSection") == null && read(adventure, "pendingFocusField") == null
            && (long) read(canPlace, "focusPredicateRowId") == 0L, "Adventure synchronization retained recovery requests");
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

    @SuppressWarnings("unchecked")
    private static void verifyUseRecovery() throws Exception {
        ItemEditor editor = editor();
        Object use = read(editor, "useEditor");
        CodecComponentState food = (CodecComponentState) read(use, "food");
        food.setPresent(true);
        food.setValue(JsonParser.parseString("{\"nutrition\":1,\"saturation\":-2}"));
        CodecComponentState cooldown = (CodecComponentState) read(use, "cooldown");
        cooldown.setPresent(true);
        cooldown.setValue(JsonParser.parseString("{\"seconds\":1,\"cooldown_group\":\"not a group id\"}"));
        invoke(use, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(use, "use_cooldown", "cooldown_group");
        require(food.value().getAsJsonObject().get("saturation").getAsFloat() == -2,
            "Use recovery clamped saturation accepted by the native float codec");
        cooldown.setValue(JsonParser.parseString("{\"seconds\":0}"));
        invoke(use, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(use, "use_cooldown", "seconds");
        require(cooldown.value().getAsJsonObject().get("seconds").getAsInt() == 0, "Use recovery rewrote the invalid cooldown");
        cooldown.setPresent(false);
        CodecComponentState consumable = (CodecComponentState) read(use, "consumable");
        consumable.setPresent(true);
        consumable.setValue(JsonParser.parseString("{\"on_consume_effects\":[{\"type\":\"minecraft:clear_all_effects\"},{\"type\":\"minecraft:apply_effects\",\"effects\":[{\"id\":\"minecraft:speed\"},{\"id\":\"bad effect id\"}]}]}"));
        List<Long> parents = (List<Long>) invoke(use, "rowIdsFor", new Class<?>[]{String.class, int.class}, "consume", 2);
        long parentId = parents.get(1);
        String parentPath = "consume/" + parentId;
        List<Long> children = (List<Long>) invoke(use, "rowIdsFor", new Class<?>[]{String.class, int.class}, parentPath + "/status", 2);
        long childId = children.get(1);
        String childPath = parentPath + "/status/" + childId;
        Map<String, ImString> buffers = (Map<String, ImString>) read(use, "textBuffers");
        ImString pending = new ImString("retained bad effect input", 128);
        buffers.put(childPath, pending);
        JsonElement before = consumable.value().deepCopy();
        for (int repeat = 0; repeat < 2; repeat++) {
            invoke(use, "requestPendingFocus", new Class<?>[0]);
            assertPendingField(use, "consumable", childPath + "/id");
            require(consumable.value().equals(before) && buffers.get(childPath) == pending
                && ((Map<String, List<Long>>) read(use, "rowIdsByList")).get("consume") == parents
                && ((Map<String, List<Long>>) read(use, "rowIdsByList")).get(parentPath + "/status") == children
                && children.get(1) == childId && "consume".equals(read(use, "focusListId"))
                && (long) read(use, "focusListRowId") == parentId, "Use nested recovery changed its model, buffers, or row identity");
        }
        buffers.clear();
        JsonObject status = consumable.value().getAsJsonObject().getAsJsonArray("on_consume_effects")
            .get(1).getAsJsonObject().getAsJsonArray("effects").get(1).getAsJsonObject();
        status.addProperty("id", "minecraft:speed");
        status.addProperty("amplifier", 256);
        status.addProperty("duration", -20);
        require(invoke(use, "invalidUseField", new Class<?>[]{String.class, JsonElement.class}, "consumable", consumable.value()) == null,
            "Use recovery added byte/duration constraints absent from the native codecs");
        status.addProperty("amplifier", "bad strength");
        invoke(use, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(use, "consumable", childPath + "/amplifier");
        status.addProperty("amplifier", 1);
        status.addProperty("duration", "bad duration");
        invoke(use, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(use, "consumable", childPath + "/duration");
        require(!(boolean) invoke(use, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "food", childPath + "/duration")
                && (boolean) invoke(use, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "consumable", childPath + "/duration")
                && !(boolean) invoke(use, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "consumable", childPath + "/duration"),
            "Use field request was consumed by another section or twice");
        consumable.setPresent(false);
        CodecComponentState stew = (CodecComponentState) read(use, "stewEffects");
        stew.setPresent(true);
        stew.setValue(JsonParser.parseString("[{\"id\":\"minecraft:speed\",\"duration\":\"uses native default\"}]"));
        require(invoke(use, "invalidUseField", new Class<?>[]{String.class, JsonElement.class}, "stew_effects", stew.value()) == null,
            "Use recovery treated a lenient stew duration as a blocking error");
        JsonElement directSound = JsonParser.parseString("{\"sound_id\":\"example:custom_sound\",\"range\":16}");
        require(!(boolean) invoke(use, "invalidSound", new Class<?>[]{JsonElement.class, String.class, boolean.class}, directSound, "custom_sound", true),
            "Use recovery rejected a native inline sound representation");
        JsonElement scalarEffects = JsonParser.parseString("[{\"type\":\"minecraft:remove_effects\",\"effects\":\"minecraft:speed\"}]");
        require(invoke(use, "invalidConsumeEffects", new Class<?>[]{JsonElement.class, String.class}, scalarEffects, "death_protection") == null
                && scalarEffects.getAsJsonArray().get(0).getAsJsonObject().get("effects").isJsonPrimitive(),
            "Use recovery normalized a scalar holder set into a list");
        // Stone has a native UseEffects default; full sync needs registry ops and is covered by the mock-client checks.
        invoke(use, "resetListRows", new Class<?>[0]);
        invoke(use, "resetEditorUi", new Class<?>[0]);
        require(read(use, "pendingFocusSection") == null && read(use, "focusListId") == null && read(use, "focusAddListId") == null,
            "Use UI reset retained transient recovery state");
    }

    @SuppressWarnings("unchecked")
    private static void verifyCombatRecovery() throws Exception {
        Object combat = read(editor(), "combatEditor");
        CodecComponentState tool = (CodecComponentState) read(combat, "tool");
        tool.setPresent(true);
        tool.setValue(JsonParser.parseString("{\"rules\":[{\"blocks\":\"minecraft:stone\"},{\"blocks\":[\"minecraft:stone\",\"bad block id\"]}]}"));
        List<Long> identities = (List<Long>) read(combat, "toolRuleRowIds");
        identities.addAll(List.of(101L, 102L));
        JsonElement before = tool.value().deepCopy();
        Map<String, ImString> buffers = (Map<String, ImString>) read(combat, "textBuffers");
        String path = "tool/rule/102/blocks";
        ImString input = new ImString("bad block id  ", 512);
        buffers.put(path + "/@identifier/1", input);
        invoke(combat, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(combat, "tool", path);
        Map<String, Object> collectionFocus = (Map<String, Object>) read(combat, "collectionFocus");
        require((int) read(collectionFocus.get(path), "requestedRow") == 1 && (long) read(combat, "focusToolRuleRowId") == 102L,
            "Combat did not reveal the exact tool rule/collection row");
        require(before.equals(tool.value()) && buffers.get(path + "/@identifier/1") == input && input.get().equals("bad block id  ")
            && identities.equals(List.of(101L, 102L)), "Combat recovery rewrote pending JSON/buffers/row IDs");
        require(!(boolean) invoke(combat, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "weapon", path)
                && (boolean) invoke(combat, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "tool", path)
                && !(boolean) invoke(combat, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "tool", path),
            "Combat field focus was consumed by another section or more than once");
        tool.setPresent(false);
        CodecComponentState projectiles = (CodecComponentState) read(combat, "chargedProjectiles");
        projectiles.setPresent(true);
        Class<?> rowType = Class.forName("dev.nineofgaming.itemlab.editor.panel.CombatEditorPanel$ProjectileRow");
        var constructor = rowType.getDeclaredConstructor(com.google.gson.JsonObject.class, CompoundTag.class);
        constructor.setAccessible(true);
        Object row = constructor.newInstance(JsonParser.parseString("{\"id\":\"minecraft:arrow\",\"components\":{}}").getAsJsonObject(), null);
        ((List<Object>) read(combat, "projectileRows")).add(row);
        ImString source = (ImString) read(row, "componentsSource");
        source.set("{");
        Object code = read(row, "componentsEditor");
        write(code, "cursorByteOffset", 1);
        JsonElement modelBefore = projectiles.value().deepCopy();
        invoke(combat, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(combat, "charged_projectiles", "row/" + read(row, "id") + "/components");
        require((boolean) read(code, "focusRequested") && (int) read(code, "cursorByteOffset") == 1 && source.get().equals("{")
                && modelBefore.equals(projectiles.value()) && read(projectiles, "nbtValue") == null,
            "Combat source recovery changed the projectile model/source/cursor");
        source.set("{}");
        ((ImString) read(row, "itemId")).set("bad item id");
        invoke(combat, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(combat, "charged_projectiles", "row/" + read(row, "id") + "/id");
        require(!(boolean) read(code, "focusRequested"), "Combat retained an obsolete code-editor request");
        projectiles.setPresent(false);
        buffers.put("piercing_weapon/sound", new ImString("", 512));
        require(invoke(combat, "invalidCombatField", new Class<?>[]{String.class, JsonElement.class}, "piercing_weapon",
                JsonParser.parseString("{\"sound\":{\"sound_id\":\"example:custom_sound\",\"range\":16}}")) == null,
            "Combat recovery rejected a valid inline sound after its identifier control cached a blank buffer");
        for (String[] valid : List.of(
            new String[]{"attack_range", "{\"min_reach\":64,\"max_reach\":0,\"hitbox_margin\":1,\"mob_factor\":2}"},
            new String[]{"kinetic_weapon", "{\"forward_movement\":-2,\"damage_multiplier\":-3,\"damage_conditions\":{\"max_duration_ticks\":0,\"min_speed\":-1,\"min_relative_speed\":-2}}"},
            new String[]{"tool", "{\"rules\":[],\"default_mining_speed\":-1}"},
            new String[]{"repairable", "{\"items\":[]}"},
            new String[]{"blocks_attacks", "{\"item_damage\":{\"threshold\":0,\"base\":-1,\"factor\":-2},\"damage_reductions\":[{\"type\":[],\"base\":-1,\"factor\":-2}]}"})) {
            require(invoke(combat, "invalidCombatField", new Class<?>[]{String.class, JsonElement.class}, valid[0], JsonParser.parseString(valid[1])) == null,
                "Combat recovery imposed a non-native constraint on " + valid[0]);
        }
        invoke(combat, "resetCombatListUi", new Class<?>[0]);
        invoke(combat, "resetEditorUi", new Class<?>[0]);
        require(read(combat, "pendingFocusSection") == null && (long) read(combat, "focusProjectileRowId") == 0L
            && collectionFocus.isEmpty() && !(boolean) read(code, "focusRequested"), "Combat UI reset retained recovery state");
    }

    @SuppressWarnings("unchecked")
    private static void verifyContainerRecovery() throws Exception {
        Object panel = read(editor(), "containerEditor");
        var contents = (dev.nineofgaming.itemlab.editor.container.ContainerContentsDraft) read(panel, "contents");
        contents.setPresent(true);
        contents.setItem(128, new ItemStack(Items.STONE, 3));
        write(panel, "selectedContentsSlot", 128);
        invoke(panel, "syncContainedItemFields", new Class<?>[0]);
        ImString itemInput = (ImString) read(panel, "containedItemId");
        itemInput.set("bad item id  ");
        invoke(panel, "containedItemIdChanged", new Class<?>[0]);
        require((boolean) read(panel, "dirty") && (boolean) read(panel, "containedItemIdPending")
            && contents.item(128).is(Items.STONE), "Invalid Container item input did not become pending without replacing its stack");
        invoke(panel, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(panel, "contents", "item_id");
        require(itemInput.get().equals("bad item id  ") && (int) read(panel, "selectedContentsSlot") == 128
            && (boolean) read(panel, "containedItemIdPending"), "Container ID recovery discarded its buffer/selection");
        contents.setPresent(false);
        var bundle = (dev.nineofgaming.itemlab.editor.container.BundleContentsDraft) read(panel, "bundleContents");
        bundle.setPresent(true);
        bundle.addItem(new ItemStack(Items.STONE));
        bundle.addItem(new ItemStack(Items.SHULKER_BOX));
        List<Long> rowIds = (List<Long>) read(panel, "bundleRowIds");
        rowIds.addAll(List.of(101L, 102L));
        ImString bundleInput = (ImString) read(panel, "bundleItemId");
        bundleInput.set("bad bundle id  ");
        invoke(panel, "bundleItemIdChanged", new Class<?>[0]);
        invoke(panel, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(panel, "bundle_contents", "item_id");
        require((boolean) read(panel, "dirty") && bundleInput.get().equals("bad bundle id  "), "Invalid Bundle input was not retained/pending");
        bundleInput.set("minecraft:stone  ");
        write(panel, "bundleItemIdPending", false);
        invoke(panel, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(panel, "bundle_contents", "row/102");
        require((int) read(panel, "selectedBundleItem") == 0 && bundleInput.get().equals("minecraft:stone  ")
                && rowIds.equals(List.of(101L, 102L)) && (long) read(panel, "recoveryBundleRowId") == 102L
                && !(boolean) invoke(panel, "allowBundleFocusSelection", new Class<?>[]{long.class}, 102L),
            "Bundle recovery changed inspector/row identity or allowed automatic focus to replace its input");
        bundle.setPresent(false);
        contents.setPresent(true);
        contents.setItem(128, new ItemStack(Items.STONE, 100));
        write(panel, "containedItemIdPending", false);
        write(panel, "selectedContentsSlot", 0);
        itemInput.set("unchanged inspector");
        invoke(panel, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(panel, "contents", "slot/128");
        require((int) read(panel, "selectedContentsSlot") == 0 && itemInput.get().equals("unchanged inspector")
                && !(boolean) invoke(panel, "allowContentsFocusSelection", new Class<?>[]{int.class}, 128),
            "Stored-slot recovery selected another slot or overwrote its inspector");
        contents.setPresent(false);
        CodecComponentState lock = (CodecComponentState) read(panel, "lock");
        lock.setPresent(true);
        lock.setValue(JsonParser.parseString("{\"items\":[\"minecraft:stone\",\"bad lock id\"]}"));
        Map<String, ImString> buffers = (Map<String, ImString>) read(panel, "textBuffers");
        ImString lockInput = new ImString("bad lock id  ", 512);
        buffers.put("container/lock/items/@identifier/1", lockInput);
        JsonElement before = lock.value().deepCopy();
        invoke(panel, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(panel, "lock", "items");
        require((int) read(read(panel, "lockItemsFocus"), "requestedRow") == 1 && before.equals(lock.value())
                && buffers.get("container/lock/items/@identifier/1") == lockInput && lockInput.get().equals("bad lock id  "),
            "Lock recovery lost collection identity/raw input/model");
        require(!(boolean) invoke(panel, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "contents", "items")
                && (boolean) invoke(panel, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "lock", "items")
                && !(boolean) invoke(panel, "consumePendingFieldFocus", new Class<?>[]{String.class, String.class}, "lock", "items"),
            "Container recovery was consumed in another section or more than once");
        lock.setValue(JsonParser.parseString("{\"components\":{}}"));
        ImString source = (ImString) read(panel, "exactComponentsSource");
        source.set("{");
        Object code = read(panel, "exactComponentsEditor");
        write(code, "cursorByteOffset", 1);
        before = lock.value().deepCopy();
        invoke(panel, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(panel, "lock", "components");
        require(source.get().equals("{") && before.equals(lock.value()) && read(lock, "nbtValue") == null
                && (boolean) read(code, "focusRequested") && (int) read(code, "cursorByteOffset") == 1,
            "Lock SNBT recovery changed source/cursor/cached model");
        lock.setValue(JsonParser.parseString("{\"count\":{\"min\":-2,\"max\":-1}}"));
        invoke(panel, "requestPendingFocus", new Class<?>[0]);
        require(read(panel, "pendingFocusField").equals("$enabled") && !(boolean) read(code, "focusRequested"),
            "Lock recovery added non-native restrictions to signed counts or retained stale code focus");
        lock.setValue(JsonParser.parseString("{\"count\":{\"min\":2,\"max\":1}}"));
        invoke(panel, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(panel, "lock", "count/min");
        lock.setPresent(false);
        CodecComponentState loot = (CodecComponentState) read(panel, "loot");
        loot.setPresent(true);
        ((ImString) read(panel, "lootSeed")).set("bad seed  ");
        invoke(panel, "requestPendingFocus", new Class<?>[0]);
        assertPendingField(panel, "loot_table", "seed");
        require(((ImString) read(panel, "lootSeed")).get().equals("bad seed  "), "Loot recovery changed raw seed input");
        invoke(panel, "clearContainerRecoveryRequests", new Class<?>[0]);
        invoke(panel, "clearPendingFocus", new Class<?>[0]);
        require(read(panel, "pendingFocusSection") == null && (int) read(panel, "recoveryContentsSlot") == -1
                && (long) read(panel, "recoveryBundleRowId") == 0L && (int) read(read(panel, "lockItemsFocus"), "requestedRow") == -1,
            "Container reset retained recovery state");
    }

    private static void assertPendingField(Object panel, String section, String control) throws Exception {
        require(section.equals(read(panel, "pendingFocusSection")) && control.equals(read(panel, "pendingFocusField")),
            "recovery did not target " + section + "/" + control + "; got " + read(panel, "pendingFocusSection") + "/" + read(panel, "pendingFocusField"));
    }

    private static void verifyRawConflictRetainsSource() throws Exception {
        ItemEditor editor = editedPanels();
        Object raw = read(editor, "rawStackEditor");
        ((ImString) read(raw, "source")).set("unparsed pending source");
        write(raw, "dirty", true);
        ItemStack before = editor.draft().copy();
        require(!commit(editor), "Raw and structured changes were silently merged");
        require(ItemStack.matches(before, editor.draft())
            && ((ImString) read(raw, "source")).get().equals("unparsed pending source"), "Raw conflict lost input");
        require(read(editor, "requestedEditorTab").toString().equals("RAW")
            && (boolean) read(read(raw, "codeEditor"), "focusRequested"), "Raw conflict did not focus its source");
    }

    private static ItemEditor editor() throws Exception {
        ItemEditor editor = new ItemEditor();
        ItemStack source = new ItemStack(Items.STONE);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("Opening name"));
        write(editor, "draft", source);
        ((ImString) read(editor, "itemId")).set("minecraft:stone");
        // The legacy formatter is fully client-independent; MiniMessage's platform bridge is not.
        write(read(editor, "loreEditor"), "textFormat", TextInputFormat.COLOR_CODES);
        ((LoreEditorPanel) read(editor, "loreEditor")).sync(source);
        ((FlagEditorPanel) read(editor, "flagEditor")).sync(source);
        ((DataEditorPanel) read(editor, "dataEditor")).sync(source);
        return editor;
    }

    private static ItemEditor editedPanels() throws Exception {
        ItemEditor editor = editor();
        Object lore = read(editor, "loreEditor");
        // Removing a name exercises the real lore participant without the running client's text bridge.
        write(lore, "displayName", RichTextLine.createEmpty(TextInputFormat.COLOR_CODES));
        Object flags = read(editor, "flagEditor");
        ((ImBoolean) read(flags, "overrideMaxStackSize")).set(true);
        ((ImInt) read(flags, "maxStackSize")).set(16);
        write(flags, "nonTooltipDirty", true);
        Object data = read(editor, "dataEditor");
        CompoundTag tag = new CompoundTag();
        tag.putInt("fixture", 7);
        invoke(data, "loadTree", new Class<?>[]{CompoundTag.class}, tag);
        write(data, "dirty", true);
        return editor;
    }

    @SuppressWarnings("unchecked")
    private static List<PendingEditTransaction.Participant<ItemStack>> participants(ItemEditor editor) throws Exception {
        return (List<PendingEditTransaction.Participant<ItemStack>>) invoke(
            editor, "pendingParticipants", new Class<?>[]{net.minecraft.client.Minecraft.class}, (Object) null);
    }

    private static PendingEditTransaction.Result<ItemStack> prepare(ItemEditor editor) throws Exception {
        return PendingEditTransaction.prepare(editor.draft(), ItemStack::copy, ItemStackCodecs::validateStack, participants(editor));
    }

    private static boolean commit(ItemEditor editor) throws Exception {
        return (boolean) invoke(editor, "commitPendingForms", new Class<?>[]{net.minecraft.client.Minecraft.class}, (Object) null);
    }


    private static void expectFailure(Runnable action, String detail, String problem) {
        try {
            action.run();
            throw new AssertionError(problem);
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains(detail), "unexpected registry diagnostic: " + expected.getMessage());
        }
    }

    private static final class FuturePanelHost {
        private final FuturePanel existing = new FuturePanel();
        private final FuturePanel forgotten = new FuturePanel();
    }

    private static final class FuturePanel {
        public boolean isDirty() {
            return false;
        }
    }


}

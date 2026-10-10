package dev.nineofgaming.itemlab.editor.panel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.component.CodecComponentState;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import dev.nineofgaming.itemlab.editor.ui.control.CodeEditorInput;
import dev.nineofgaming.itemlab.editor.ui.syntax.CodeLanguage;
import dev.nineofgaming.itemlab.editor.ui.control.FieldHints;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import dev.nineofgaming.itemlab.editor.ui.sound.SoundPreviewKind;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImBoolean;
import imgui.type.ImDouble;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.number;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.primitiveString;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.string;
import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Purpose-built editor for item components that select or define playable sounds.
 */
public final class MusicEditorPanel extends ComponentEditorPanel {
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SOURCE_CAPACITY = 65_536;
    private static final String DEFAULT_INSTRUMENT = "minecraft:ponder_goat_horn";
    private static final String DEFAULT_JUKEBOX_SONG = "minecraft:13";
    private static final String DEFAULT_SOUND = "minecraft:block.note_block.harp";

    private final CodecComponentState instrument = new CodecComponentState(
        DataComponents.INSTRUMENT,
        '"' + DEFAULT_INSTRUMENT + '"'
    );
    private final CodecComponentState jukeboxPlayable = new CodecComponentState(
        DataComponents.JUKEBOX_PLAYABLE,
        '"' + DEFAULT_JUKEBOX_SONG + '"'
    );
    private final CodecComponentState noteBlockSound = new CodecComponentState(
        DataComponents.NOTE_BLOCK_SOUND,
        '"' + DEFAULT_SOUND + '"'
    );

    private final ImBoolean customInstrument = new ImBoolean(false);
    private final ImString registeredInstrumentId = new ImString(DEFAULT_INSTRUMENT, 512);
    private JsonObject customInstrumentValue = defaultCustomInstrument();
    private JsonObject preservedCustomSoundEvent = defaultCustomSoundEvent(DEFAULT_SOUND);
    private final ImString instrumentDescriptionSource = new ImString("\"Custom instrument\"", SOURCE_CAPACITY);
    private final CodeEditorInput instrumentDescriptionEditor = new CodeEditorInput();

    private List<IdSuggestion> instrumentIds = List.of();
    private List<IdSuggestion> jukeboxSongIds = List.of();
    private List<IdSuggestion> soundIds = List.of();
    private Set<String> knownInstrumentIds = Set.of();
    private Set<String> knownJukeboxSongIds = Set.of();
    private Set<String> knownSoundIds = Set.of();
    private DataComponentType<?> requestedInputFocus;

    public MusicEditorPanel(ItemEditor host) {
        super(host, "music");
    }

    @Override
    public void requestPendingFocus() {
        requestedInputFocus = null;
        instrumentDescriptionEditor.cancelFocusRequest();
        if (instrument.present()) {
            if (customInstrument.get()) {
                if (!descriptionProblem().isBlank() || hasInvalidNativeDescription(Minecraft.getInstance())) {
                    requestPendingField("instrument", "description");
                    instrumentDescriptionEditor.requestFocus();
                    return;
                }
                JsonElement sound = customInstrumentValue.get("sound_event");
                boolean direct = sound != null && sound.isJsonObject();
                String soundId = direct
                    ? string(sound.getAsJsonObject(), "sound_id", DEFAULT_SOUND)
                    : soundId(sound);
                String soundProblem = direct ? identifierProblem(soundId) : registeredIdentifierProblem(soundId, knownSoundIds);
                if (!soundProblem.isBlank()) {
                    requestPendingField("instrument", "sound");
                    return;
                }
                // SoundEvent's optional range is a lenient native float, not a
                // positive instrument field; it must not steal a later error's focus.
                for (String key : new String[]{"use_duration", "range"}) {
                    if (invalidNumber(customInstrumentValue, key, key.equals("range") ? 256.0 : 7.0, true)) {
                        requestPendingField("instrument", key);
                        return;
                    }
                }
            } else if (!registeredIdentifierProblem(registeredInstrumentId.get(), knownInstrumentIds).isBlank()) {
                requestPendingField("instrument", "instrument_id");
                return;
            }
        }
        if (jukeboxPlayable.present()
            && !registeredIdentifierProblem(pendingText("music/jukebox_song", primitiveString(jukeboxPlayable, DEFAULT_JUKEBOX_SONG)), knownJukeboxSongIds).isBlank()) {
            requestPendingField("jukebox_playable", "song");
            return;
        }
        if (noteBlockSound.present()
            && !identifierProblem(pendingText("music/note_block_sound", primitiveString(noteBlockSound, DEFAULT_SOUND))).isBlank()) {
            requestPendingField("note_block_sound", "sound");
            return;
        }
        if (instrument.present()) requestPendingField("instrument", customInstrument.get() ? "sound" : "instrument_id");
        else if (jukeboxPlayable.present()) requestPendingField("jukebox_playable", "song");
        else if (noteBlockSound.present()) requestPendingField("note_block_sound", "sound");
        else requestPendingField("instrument", "$enabled");
    }

    private String pendingText(String key, String fallback) {
        ImString source = textBuffers.get(key);
        return source == null ? fallback : source.get();
    }

    private static boolean invalidNumber(JsonObject value, String key, double fallback, boolean positive) {
        double amount = number(value, key, fallback);
        return !Double.isFinite(amount) || (positive ? amount <= 0.0 : amount < 0.0);
    }

    public static boolean supports(DataComponentType<?> type) {
        return type == DataComponents.INSTRUMENT
            || type == DataComponents.JUKEBOX_PLAYABLE
            || type == DataComponents.NOTE_BLOCK_SOUND;
    }

    public boolean isAvailable(ItemStack stack) {
        return stack.has(DataComponents.INSTRUMENT)
            || stack.has(DataComponents.JUKEBOX_PLAYABLE)
            || stack.has(DataComponents.NOTE_BLOCK_SOUND);
    }

    public void setCatalog(EditorCatalog catalog) {
        instrumentIds = catalog.registryIds("instrument");
        jukeboxSongIds = catalog.registryIds("jukebox_song");
        soundIds = catalog.soundIds();
        knownInstrumentIds = ids(instrumentIds);
        knownJukeboxSongIds = ids(jukeboxSongIds);
        knownSoundIds = ids(soundIds);
    }

    public void sync(Minecraft client, ItemStack draft) {
        instrument.sync(client, draft);
        jukeboxPlayable.sync(client, draft);
        noteBlockSound.sync(client, draft);
        loadInstrumentModel();
        resetTransientUi();
        dirty = false;
    }

    public void syncComponent(Minecraft client, DataComponentType<?> type, ItemStack draft) {
        if (type == DataComponents.INSTRUMENT) {
            instrument.sync(client, draft);
            loadInstrumentModel();
        } else if (type == DataComponents.JUKEBOX_PLAYABLE) {
            jukeboxPlayable.sync(client, draft);
        } else if (type == DataComponents.NOTE_BLOCK_SOUND) {
            noteBlockSound.sync(client, draft);
        }
        resetTransientUi();
        requestedInputFocus = type;
        dirty = differsFromDraft(client, draft);
    }

    public void draw(Minecraft client) {
        if (drawToolbar(client, null)) return;
        EditorUi.beginScrollableChild(
            "music_editor_sections",
            0.0f,
            Math.max(1.0f, ImGui.getContentRegionAvailY()),
            false
        );
        try {
            drawInstrument(client);
            ImGui.spacing();
            drawJukeboxPlayable(client);
            ImGui.spacing();
            drawNoteBlockSound(client);
        } finally {
            ImGui.endChild();
        }
    }

    public void drawComponent(Minecraft client, DataComponentType<?> type) {
        if (drawToolbar(client, type)) return;
        if (type == DataComponents.INSTRUMENT) drawInstrument(client);
        else if (type == DataComponents.JUKEBOX_PLAYABLE) drawJukeboxPlayable(client);
        else if (type == DataComponents.NOTE_BLOCK_SOUND) drawNoteBlockSound(client);
    }

    private boolean drawToolbar(Minecraft client, DataComponentType<?> componentType) {
        return drawPanelToolbar(
            () -> save(client, true),
            () -> {
                if (componentType == null) sync(client, host.draft());
                else syncComponent(client, componentType, host.draft());
            }
        );
    }

    private void drawInstrument(Minecraft client) {
        if (!beginSection("instrument", instrument)) return;

        ImGui.text(tr("music.definition"));
        if (modeButton(tr("music.definition.registered") + "##instrument_registered", !customInstrument.get())) {
            customInstrument.set(false);
            requestedInputFocus = DataComponents.INSTRUMENT;
            dirty = true;
        }
        String customInstrumentLabel = tr("music.definition.custom");
        EditorUi.sameLineIfFits(customInstrumentLabel);
        if (modeButton(customInstrumentLabel + "##instrument_custom", customInstrument.get())) {
            customInstrument.set(true);
            requestedInputFocus = DataComponents.INSTRUMENT;
            dirty = true;
        }
        ImGui.spacing();

        if (customInstrument.get()) {
            drawCustomInstrument(client);
        } else if (beginFields("registered_instrument_fields")) {
            fieldLabel(tr("music.instrument"), tr("music.instrument.help"));
            if (host.inputSoundWithPreview(
                client,
                "instrument_id",
                new FieldHints(tr("field_purpose.instrument_identifier"), DEFAULT_INSTRUMENT),
                registeredInstrumentId,
                instrumentIds,
                SoundPreviewKind.INSTRUMENT,
                registeredIdentifierProblem(registeredInstrumentId.get(), knownInstrumentIds),
                consumeMusicInputFocus("instrument", "instrument_id", DataComponents.INSTRUMENT, instrument.present())
            )) dirty = true;
            ImGui.endTable();
        }
        endSection();
    }

    private void drawCustomInstrument(Minecraft client) {
        JsonElement soundValue = customInstrumentValue.get("sound_event");
        boolean customSound = soundValue != null && soundValue.isJsonObject();
        ImGui.separatorText(tr("music.sound_event"));
        if (modeButton(tr("music.sound_event.registered") + "##sound_registered", !customSound)) {
            switchToRegisteredSound(soundValue);
            textBuffers.clear();
            requestedInputFocus = DataComponents.INSTRUMENT;
            dirty = true;
            customSound = false;
        }
        String customSoundLabel = tr("music.sound_event.custom");
        EditorUi.sameLineIfFits(customSoundLabel);
        if (modeButton(customSoundLabel + "##sound_custom", customSound)) {
            switchToCustomSound(soundValue);
            textBuffers.clear();
            requestedInputFocus = DataComponents.INSTRUMENT;
            dirty = true;
        }
        soundValue = customInstrumentValue.get("sound_event");
        customSound = soundValue != null && soundValue.isJsonObject();

        if (beginFields("custom_instrument_fields")) {
            if (customSound) {
                JsonObject directSound = customInstrumentValue.getAsJsonObject("sound_event");
                fieldLabel(tr("music.sound_id"), tr("music.sound_id.help"));
                String current = string(directSound, "sound_id", DEFAULT_SOUND);
                ImString soundId = buffer("music/custom_sound_id", current);
                if (host.inputSoundWithPreview(
                    client,
                    "custom_sound_id",
                    new FieldHints(tr("field_purpose.sound_identifier"), DEFAULT_SOUND),
                    soundId,
                    soundIds,
                    SoundPreviewKind.SOUND_EVENT,
                    identifierProblem(soundId.get()),
                    consumeMusicInputFocus("instrument", "sound", DataComponents.INSTRUMENT, instrument.present())
                )) {
                    directSound.addProperty("sound_id", soundId.get().trim());
                    dirty = true;
                }

                fieldLabel(tr("music.fixed_range"), tr("music.fixed_range.help"));
                ImBoolean fixedRange = new ImBoolean(directSound.has("range"));
                if (ImGui.checkbox("##custom_sound_range_enabled", fixedRange)) {
                    if (fixedRange.get()) directSound.addProperty("range", 16.0);
                    else directSound.remove("range");
                    dirty = true;
                }
                if (fixedRange.get()) {
                    EditorUi.sameLineIfFits(120.0f);
                    ImDouble range = new ImDouble(number(directSound, "range", 16.0));
                    ImGui.setNextItemWidth(-1.0f);
                    applyPendingFieldFocus("instrument", "sound_range");
                    if (ImGui.inputDouble(
                        "##custom_sound_range",
                        range,
                        1.0,
                        8.0,
                        "%.2f"
                    )) {
                        directSound.addProperty("range", Math.max(0.0, range.get()));
                        dirty = true;
                    }
                }
            } else {
                fieldLabel(tr("music.sound_event"), tr("music.sound_event.help"));
                String current = soundValue != null && soundValue.isJsonPrimitive()
                    ? soundValue.getAsString()
                    : DEFAULT_SOUND;
                ImString soundId = buffer("music/registered_sound", current);
                if (host.inputSoundWithPreview(
                    client,
                    "registered_sound_id",
                    new FieldHints(tr("field_purpose.sound_identifier"), DEFAULT_SOUND),
                    soundId,
                    soundIds,
                    SoundPreviewKind.SOUND_EVENT,
                    registeredIdentifierProblem(soundId.get(), knownSoundIds),
                    consumeMusicInputFocus("instrument", "sound", DataComponents.INSTRUMENT, instrument.present())
                )) {
                    customInstrumentValue.addProperty("sound_event", soundId.get().trim());
                    dirty = true;
                }
            }

            positiveDoubleField(
                customInstrumentValue,
                "use_duration",
                tr("music.use_duration"),
                tr("music.use_duration.help"),
                7.0
            );
            positiveDoubleField(
                customInstrumentValue,
                "range",
                tr("music.range"),
                tr("music.range.help"),
                256.0
            );
            ImGui.endTable();
        }

        ImGui.spacing();
        ImGui.separatorText(tr("music.description"));
        EditorUi.textDisabledWrapped(tr("music.description.help"));
        String descriptionProblem = descriptionProblem();
        if (!descriptionProblem.isBlank()) ItemEditorTheme.pushInvalidField();
        if (consumePendingFieldFocus("instrument", "description")) {
            ImGui.setScrollHereY(0.35f);
            instrumentDescriptionEditor.requestFocus();
        }
        if (instrumentDescriptionEditor.draw(
            "instrument_description",
            instrumentDescriptionSource,
            -1.0f,
            132.0f,
            ImGuiInputTextFlags.AllowTabInput,
            descriptionProblem.isBlank() ? 0 : jsonErrorLine(instrumentDescriptionSource.get()),
            CodeLanguage.JSON
        )) dirty = true;
        if (!descriptionProblem.isBlank()) ItemEditorTheme.popInvalidField();
        // InputText changes the source during this frame. The pre-input validation is only
        // for the input's style/gutter; actions and codec preview must use the new text.
        String currentDescriptionProblem = descriptionProblem();
        ItemEditorTheme.drawFieldProblem(currentDescriptionProblem);
        ImGui.beginDisabled(!currentDescriptionProblem.isBlank());
        if (ImGui.button(tr("music.description.format"))) {
            instrumentDescriptionSource.set(PRETTY_GSON.toJson(parseJson(instrumentDescriptionSource.get())));
            dirty = true;
        }
        ImGui.endDisabled();

        if (currentDescriptionProblem.isBlank()) {
            ItemEditorTheme.drawFieldProblem(instrumentProblem(client));
        }
    }

    private void drawJukeboxPlayable(Minecraft client) {
        if (!beginSection("jukebox_playable", jukeboxPlayable)) return;
        if (beginFields("jukebox_playable_fields")) {
            fieldLabel(tr("music.jukebox_song"), tr("music.jukebox_song.help"));
            String current = primitiveString(jukeboxPlayable, DEFAULT_JUKEBOX_SONG);
            ImString song = buffer("music/jukebox_song", current);
            if (host.inputSoundWithPreview(
                client,
                "jukebox_song",
                new FieldHints(
                    tr("field_purpose.jukebox_song_identifier"),
                    DEFAULT_JUKEBOX_SONG
                ),
                song,
                jukeboxSongIds,
                SoundPreviewKind.JUKEBOX_SONG,
                registeredIdentifierProblem(song.get(), knownJukeboxSongIds),
                consumeMusicInputFocus("jukebox_playable", "song", DataComponents.JUKEBOX_PLAYABLE, jukeboxPlayable.present())
            )) {
                jukeboxPlayable.setValue(new JsonPrimitive(song.get().trim()));
                dirty = true;
            }
            ImGui.endTable();
        }
        endSection();
    }

    private void drawNoteBlockSound(Minecraft client) {
        if (!beginSection("note_block_sound", noteBlockSound)) return;
        if (beginFields("note_block_sound_fields")) {
            fieldLabel(tr("music.note_block_sound"), tr("music.note_block_sound.help"));
            String current = primitiveString(noteBlockSound, DEFAULT_SOUND);
            ImString sound = buffer("music/note_block_sound", current);
            if (host.inputSoundWithPreview(
                client,
                "note_block_sound",
                new FieldHints(tr("field_purpose.sound_identifier"), DEFAULT_SOUND),
                sound,
                soundIds,
                SoundPreviewKind.SOUND_EVENT,
                identifierProblem(sound.get()),
                consumeMusicInputFocus("note_block_sound", "sound", DataComponents.NOTE_BLOCK_SOUND, noteBlockSound.present())
            )) {
                noteBlockSound.setValue(new JsonPrimitive(sound.get().trim()));
                dirty = true;
            }
            ImGui.endTable();
        }
        endSection();
    }

    private void positiveDoubleField(
        JsonObject owner,
        String key,
        String label,
        String description,
        double fallback
    ) {
        fieldLabel(label, description);
        ImDouble value = new ImDouble(number(owner, key, fallback));
        ImGui.setNextItemWidth(-1.0f);
        applyPendingFieldFocus("instrument", key);
        if (ImGui.inputDouble("##" + key, value, 0.1, 1.0, "%.2f")) {
            owner.addProperty(key, Math.max(0.01, value.get()));
            dirty = true;
        }
    }

    public boolean save(Minecraft client, boolean announce) {
        return savePendingStack(client, announce, edited -> sync(client, edited));
    }

    @Override
    public ItemStack pendingStack(Minecraft client) {
        ItemStack edited = host.draft().copy();
        if (instrument.present()) {
            ItemStackCodecs.decodeAndSetComponent(client, edited, instrument.type(), buildInstrumentValue());
        } else {
            instrument.apply(client, edited);
        }
        jukeboxPlayable.apply(client, edited);
        noteBlockSound.apply(client, edited);
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
        Minecraft client = Minecraft.getInstance();
        return differsFromDraft(client, host.draft());
    }

    private boolean differsFromDraft(Minecraft client, ItemStack draft) {
        Object installed = draft.get(DataComponents.INSTRUMENT);
        boolean instrumentDiffers = instrument.present() != (installed != null)
            || instrument.present() && !Objects.equals(buildInstrumentValue(), ItemStackCodecs.encodeComponent(client, instrument.type(), installed));
        return instrumentDiffers
            || jukeboxPlayable.differsFrom(client, draft)
            || noteBlockSound.differsFrom(client, draft);
    }

    private void loadInstrumentModel() {
        JsonElement encoded = instrument.value();
        customInstrument.set(encoded.isJsonObject());
        if (encoded.isJsonPrimitive()) {
            registeredInstrumentId.set(encoded.getAsString());
            customInstrumentValue = defaultCustomInstrument();
        } else if (encoded.isJsonObject()) {
            customInstrumentValue = encoded.getAsJsonObject().deepCopy();
        } else {
            registeredInstrumentId.set(DEFAULT_INSTRUMENT);
            customInstrumentValue = defaultCustomInstrument();
        }
        JsonElement description = customInstrumentValue.get("description");
        instrumentDescriptionSource.set(PRETTY_GSON.toJson(
            description == null ? new JsonPrimitive("Custom instrument") : description
        ));
        JsonElement soundEvent = customInstrumentValue.get("sound_event");
        preservedCustomSoundEvent = soundEvent != null && soundEvent.isJsonObject()
            ? soundEvent.getAsJsonObject().deepCopy()
            : defaultCustomSoundEvent(soundId(soundEvent));
    }

    private void switchToRegisteredSound(JsonElement soundValue) {
        if (soundValue != null && soundValue.isJsonObject()) {
            preservedCustomSoundEvent = soundValue.getAsJsonObject().deepCopy();
        }
        customInstrumentValue.addProperty("sound_event", soundId(soundValue));
    }

    private void switchToCustomSound(JsonElement soundValue) {
        JsonObject directSound = preservedCustomSoundEvent.deepCopy();
        directSound.addProperty("sound_id", soundId(soundValue));
        customInstrumentValue.add("sound_event", directSound);
    }

    private static String soundId(JsonElement soundValue) {
        if (soundValue == null) return DEFAULT_SOUND;
        if (soundValue.isJsonPrimitive()) return soundValue.getAsString();
        if (soundValue.isJsonObject()) {
            return string(soundValue.getAsJsonObject(), "sound_id", DEFAULT_SOUND);
        }
        return DEFAULT_SOUND;
    }

    private JsonElement buildInstrumentValue() {
        if (!customInstrument.get()) {
            return new JsonPrimitive(registeredInstrumentId.get().trim());
        }
        JsonObject result = customInstrumentValue.deepCopy();
        result.add("description", parseJson(instrumentDescriptionSource.get()));
        return result;
    }

    private void resetTransientUi() {
        resetEditorUi();
        instrumentDescriptionEditor.resetCursor();
        requestedInputFocus = null;
    }

    private boolean consumeRequestedInputFocus(DataComponentType<?> type, boolean enabled) {
        if (!enabled || requestedInputFocus != type) return false;
        requestedInputFocus = null;
        return true;
    }

    private boolean consumeMusicInputFocus(String section, String field, DataComponentType<?> type, boolean enabled) {
        boolean recovery = enabled && consumePendingFieldFocus(section, field);
        if (recovery) ImGui.setScrollHereY(0.35f);
        boolean requested = consumeRequestedInputFocus(type, enabled);
        return recovery || requested;
    }

    private static JsonObject defaultCustomInstrument() {
        JsonObject result = new JsonObject();
        result.addProperty("sound_event", "minecraft:item.goat_horn.sound.0");
        result.addProperty("use_duration", 7.0);
        result.addProperty("range", 256.0);
        result.addProperty("description", "Custom instrument");
        return result;
    }

    private static JsonObject defaultCustomSoundEvent(String soundId) {
        JsonObject result = new JsonObject();
        result.addProperty("sound_id", soundId);
        return result;
    }

    private String instrumentProblem(Minecraft client) {
        try {
            // Building may itself reject in-progress JSON, so it belongs inside the
            // same error boundary as codec validation, not in a caller's arguments.
            JsonElement value = buildInstrumentValue();
            ItemStack probe = host.draft().copy();
            ItemStackCodecs.decodeAndSetComponent(client, probe, instrument.type(), value);
            ItemStackCodecs.validateStack(probe);
            return "";
        } catch (RuntimeException exception) {
            return EditorUi.readableMessage(exception);
        }
    }

    private static String identifierProblem(String source) {
        String value = source.trim();
        if (value.isEmpty()) return tr("validation.identifier_required");
        return Identifier.tryParse(value) == null ? tr("validation.identifier_format") : "";
    }

    private static String registeredIdentifierProblem(String source, Set<String> knownIds) {
        String formatProblem = identifierProblem(source);
        if (!formatProblem.isEmpty()) return formatProblem;
        return !knownIds.isEmpty() && !knownIds.contains(source.trim())
            ? tr("validation.identifier_unknown")
            : "";
    }

    private static Set<String> ids(List<IdSuggestion> suggestions) {
        Set<String> result = new HashSet<>();
        suggestions.forEach(suggestion -> result.add(suggestion.id()));
        return Set.copyOf(result);
    }

    private static JsonElement parseJson(String source) {
        return JsonParser.parseString(source);
    }

    private String descriptionProblem() {
        return jsonProblem(instrumentDescriptionSource.get());
    }

    private boolean hasInvalidNativeDescription(Minecraft client) {
        if (client == null || client.level == null) return false;
        try {
            // Valid JSON can still be invalid Minecraft text. Classify it without
            // building the instrument or changing any pending model/cursor state.
            return ComponentSerialization.CODEC.parse(
                ItemStackCodecs.jsonOps(client), parseJson(instrumentDescriptionSource.get())
            ).error().isPresent();
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private static String jsonProblem(String source) {
        try {
            parseJson(source);
            return "";
        } catch (RuntimeException exception) {
            return tr("music.description.invalid", EditorUi.readableMessage(exception));
        }
    }

    private static int jsonErrorLine(String source) {
        try {
            parseJson(source);
            return 0;
        } catch (RuntimeException exception) {
            return CodeEditorInput.errorLine(exception, source);
        }
    }

    private static boolean modeButton(String label, boolean selected) {
        if (selected) ItemEditorTheme.pushActiveFilter();
        boolean pressed = ImGui.button(label);
        if (selected) ItemEditorTheme.popActiveFilter();
        return pressed && !selected;
    }
}

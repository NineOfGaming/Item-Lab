package dev.nineofgaming.itemlab.editor.ui.sound;

import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import imgui.ImGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.JukeboxSong;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Owns the editor's one active local sound preview and its play/stop controls.
 */
public final class SoundPreviewController {
    private SoundInstance active;

    public void drawControls(Minecraft client, String id, SoundPreviewKind kind, String requestedId) {
        SoundEvent sound = resolve(client, kind, requestedId);
        ImGui.beginDisabled(sound == null);
        if (EditorIcons.smallButton(id + "_play", EditorIcons.Icon.PLAY, tr("sound_preview.play"))) {
            play(client, sound);
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        ImGui.beginDisabled(!isActive(client));
        if (EditorIcons.smallButton(id + "_stop", EditorIcons.Icon.STOP, tr("sound_preview.stop"))) {
            stop(client);
        }
        ImGui.endDisabled();
    }

    public void stop(Minecraft client) {
        if (active != null) client.getSoundManager().stop(active);
        active = null;
    }

    private void play(Minecraft client, SoundEvent sound) {
        stop(client);
        active = SimpleSoundInstance.forUI(sound, 1.0f);
        client.getSoundManager().play(active);
    }

    private boolean isActive(Minecraft client) {
        if (active == null) return false;
        if (client.getSoundManager().isActive(active)) return true;
        active = null;
        return false;
    }

    private static SoundEvent resolve(Minecraft client, SoundPreviewKind kind, String requestedId) {
        if (client.level == null) return null;
        Identifier id = Identifier.tryParse(requestedId == null ? "" : requestedId.trim());
        if (id == null) return null;
        return switch (kind) {
            case SOUND_EVENT -> BuiltInRegistries.SOUND_EVENT.containsKey(id)
                ? BuiltInRegistries.SOUND_EVENT.getValue(id)
                : null;
            case INSTRUMENT -> {
                Registry<Instrument> registry = client.level.registryAccess().lookupOrThrow(Registries.INSTRUMENT);
                Instrument instrument = registry.getValue(id);
                yield instrument == null ? null : instrument.soundEvent().value();
            }
            case JUKEBOX_SONG -> {
                Registry<JukeboxSong> registry = client.level.registryAccess().lookupOrThrow(Registries.JUKEBOX_SONG);
                JukeboxSong song = registry.getValue(id);
                yield song == null ? null : song.soundEvent().value();
            }
        };
    }
}

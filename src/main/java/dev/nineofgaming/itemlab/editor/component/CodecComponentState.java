package dev.nineofgaming.itemlab.editor.component;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Codec-backed state used by purpose-built editors without losing unknown fields.
 */
public final class CodecComponentState {
    private final DataComponentType<?> type;
    private final JsonElement requestedStarter;
    private JsonElement starter;
    private JsonElement value;
    private Tag nbtValue;
    private boolean present;

    public CodecComponentState(DataComponentType<?> type, String starterJson) {
        this.type = type;
        this.requestedStarter = JsonParser.parseString(starterJson);
        this.starter = requestedStarter.deepCopy();
        this.value = starter.deepCopy();
    }

    public DataComponentType<?> type() {
        return type;
    }

    public boolean present() {
        return present;
    }

    public void setPresent(boolean present) {
        this.present = present;
    }

    public JsonElement value() {
        return value;
    }

    public void setValue(JsonElement value) {
        this.value = value.deepCopy();
        this.nbtValue = null;
    }

    /**
     * Selects a lossless NBT representation for the next comparison/application.
     */
    public void setNbtValue(Minecraft client, Tag value) {
        this.nbtValue = ItemStackCodecs.canonicalizeComponentNbt(client, type, value);
    }

    /**
     * Whether an explicit native representation overrides the editable JSON view.
     */
    public boolean hasNbtValue() {
        return nbtValue != null;
    }

    public JsonObject object() {
        if (!value.isJsonObject()) {
            value = starter.isJsonObject() ? starter.deepCopy() : new JsonObject();
        }
        return value.getAsJsonObject();
    }

    public JsonArray array() {
        if (!value.isJsonArray()) {
            value = starter.isJsonArray() ? starter.deepCopy() : new JsonArray();
        }
        return value.getAsJsonArray();
    }

    public boolean supportsField(String field) {
        return EditorVersionProfile.supportsRootField(type, field);
    }

    public void sync(Minecraft client, ItemStack stack) {
        Object installed = stack.get(type);
        present = installed != null;
        starter = EditorVersionProfile.compatibleStarter(client, stack, type, requestedStarter);
        value = installed == null
            ? starter.deepCopy()
            : ItemStackCodecs.encodeComponent(client, type, installed).deepCopy();
        nbtValue = null;
    }

    public boolean differsFrom(Minecraft client, ItemStack stack) {
        Object installed = stack.get(type);
        if (present != (installed != null)) return true;
        if (!present) return false;
        return nbtValue != null
            ? !Objects.equals(nbtValue, ItemStackCodecs.encodeComponentNbt(client, type, installed))
            : !Objects.equals(value, ItemStackCodecs.encodeComponent(client, type, installed));
    }

    public void apply(Minecraft client, ItemStack stack) {
        if (present) {
            if (nbtValue != null) {
                ItemStackCodecs.decodeAndSetComponentNbt(client, stack, type, nbtValue);
            } else {
                ItemStackCodecs.decodeAndSetComponent(client, stack, type, value);
            }
        } else {
            stack.remove(type);
        }
    }
}

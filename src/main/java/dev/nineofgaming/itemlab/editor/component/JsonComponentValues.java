package dev.nineofgaming.itemlab.editor.component;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Defensive JSON accessors used by codec-backed component editors.
 */
public final class JsonComponentValues {
    private JsonComponentValues() {
    }

    public static JsonArray array(JsonObject owner, String key) {
        JsonElement value = owner.get(key);
        if (value != null && value.isJsonArray()) {
            return value.getAsJsonArray();
        }
        JsonArray created = new JsonArray();
        owner.add(key, created);
        return created;
    }

    public static JsonObject object(JsonObject owner, String key) {
        JsonElement value = owner.get(key);
        if (value != null && value.isJsonObject()) {
            return value.getAsJsonObject();
        }
        JsonObject created = new JsonObject();
        owner.add(key, created);
        return created;
    }

    public static String string(JsonObject owner, String key, String fallback) {
        return string(owner.get(key), fallback);
    }

    public static int integer(JsonObject owner, String key, int fallback) {
        return integer(owner.get(key), fallback);
    }

    public static double number(JsonObject owner, String key, double fallback) {
        return number(owner.get(key), fallback);
    }

    public static boolean bool(JsonObject owner, String key, boolean fallback) {
        return bool(owner.get(key), fallback);
    }

    public static String string(JsonElement value, String fallback) {
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static int integer(JsonElement value, int fallback) {
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static double number(JsonElement value, double fallback) {
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsDouble() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static boolean bool(JsonElement value, boolean fallback) {
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static String primitiveString(CodecComponentState state, String fallback) {
        return string(state.value(), fallback);
    }

    public static int primitiveInt(CodecComponentState state, int fallback) {
        return integer(state.value(), fallback);
    }

    public static double primitiveDouble(CodecComponentState state, double fallback) {
        return number(state.value(), fallback);
    }

    public static int indexOf(String[] values, String requested) {
        for (int index = 0; index < values.length; index++) {
            if (values[index].equals(requested)) {
                return index;
            }
        }
        return 0;
    }
}

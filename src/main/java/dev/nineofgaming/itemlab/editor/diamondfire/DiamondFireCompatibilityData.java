package dev.nineofgaming.itemlab.editor.diamondfire;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checked-in offline facts and a projection of separately reviewed component-field evidence.
 */
final class DiamondFireCompatibilityData {
    private static final String RESOURCE_ROOT = "/assets/itemlab/diamondfire/";
    static final Map<String, ServerComponent> SERVER_COMPONENTS = parseServerRegistry(read("server-registry.json"));
    private static final JsonObject ACTION_SNAPSHOT = read("actiondump.json");
    static final Map<String, String> ACTIONS = parseActions(ACTION_SNAPSHOT);
    static final Map<String, ComponentPolicy> POLICIES = parsePolicies(read("component-policy.json"));
    static final Map<String, List<String>> CLIENT_FORMATS = parseFormats(read("component-formats.json"));

    private DiamondFireCompatibilityData() {
    }

    static JsonObject read(String name) {
        try (InputStream stream = DiamondFireCompatibilityData.class.getResourceAsStream(RESOURCE_ROOT + name)) {
            if (stream == null) throw new IllegalStateException("Missing DiamondFire snapshot: " + name);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Invalid DiamondFire snapshot: " + name, exception);
        }
    }

    static Map<String, ServerComponent> parseServerRegistry(JsonObject data) {
        requireSchema(data, 2);
        if (!DiamondFireServerCompatibility.SERVER_VERSION.equals(data.get("minecraft_version").getAsString())
            || data.get("source").getAsString().isBlank()) {
            throw new IllegalArgumentException("DiamondFire server registry has the wrong baseline or no source");
        }
        Map<String, ServerComponent> components = new LinkedHashMap<>();
        for (JsonElement entry : data.getAsJsonArray("components")) {
            JsonObject component = entry.getAsJsonObject();
            String id = component.get("id").getAsString();
            List<String> fields = strings(component.getAsJsonArray("discovered_codec_fields"));
            if (!id.startsWith("minecraft:") || components.containsKey(id)
                || new HashSet<>(fields).size() != fields.size()
                || !component.get("persistent").getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException("Invalid or duplicate server component: " + id);
            }
            components.put(id, new ServerComponent(component.get("persistent").getAsBoolean(), fields));
        }
        if (components.isEmpty()) throw new IllegalArgumentException("Empty DiamondFire server component registry");
        return Map.copyOf(components);
    }

    static Map<String, String> parseActions(JsonObject data) {
        requireSchema(data, 2);
        JsonObject provenance = data.getAsJsonObject("provenance");
        if (!"https://github.com/NineOfGaming/df-info".equals(provenance.get("repository").getAsString())
            || !provenance.get("commit").getAsString().matches("[0-9a-f]{40}")
            || !provenance.get("dump_sha256").getAsString().matches("[0-9a-f]{64}")
            || !"actiondump/actiondump_minimessage.json".equals(provenance.get("dump").getAsString())
            || provenance.get("branch").getAsString().isBlank()
            || provenance.get("patch").getAsString().isBlank()
            || !"versions.patch".equals(provenance.get("patch_source").getAsString())) {
            throw new IllegalArgumentException("Missing or invalid df-info provenance");
        }
        Map<String, String> actions = new LinkedHashMap<>();
        for (JsonElement entry : data.getAsJsonArray("actions")) {
            JsonObject action = entry.getAsJsonObject();
            String id = action.get("id").getAsString();
            String codeblock = action.get("codeblock").getAsString();
            String name = action.get("display_name").getAsString();
            if (id.isBlank() || codeblock.isBlank() || name.isBlank() || action.has("legacy_replacement")
                || actions.putIfAbsent(actionKey(codeblock, id), name) != null) {
                throw new IllegalArgumentException("Invalid or duplicate DiamondFire action: " + codeblock + "/" + id);
            }
        }
        if (actions.isEmpty()) throw new IllegalArgumentException("Empty DiamondFire action snapshot");
        return Map.copyOf(actions);
    }

    static Map<String, ComponentPolicy> parsePolicies(JsonObject data) {
        requireSchema(data, 2);
        if (!ACTION_SNAPSHOT.getAsJsonObject("provenance").get("commit").getAsString()
            .equals(data.get("evidence_commit").getAsString())
            || !"src/test/resources/diamondfire/component-evidence.json".equals(data.get("policy_source").getAsString())) {
            throw new IllegalArgumentException("Policy is not linked to the pinned field evidence");
        }
        Map<String, ComponentPolicy> policies = new LinkedHashMap<>();
        for (JsonElement element : data.getAsJsonArray("components")) {
            JsonObject component = element.getAsJsonObject();
            String id = component.get("id").getAsString();
            String badge = component.get("badge").getAsString();
            String limitation = component.has("limitation") ? component.get("limitation").getAsString() : "";
            List<String> actions = strings(component.getAsJsonArray("actions"));
            if (!SERVER_COMPONENTS.containsKey(id) || policies.containsKey(id)
                || !Set.of("DF+", "DFP", "DF?").contains(badge)
                || component.has("detail") || component.has("rationale")
                || limitation.length() > 160 || limitation.contains("\n")
                || ("DF+".equals(badge) ? !limitation.isEmpty() : limitation.isBlank())
                || actions.isEmpty() || new HashSet<>(actions).size() != actions.size()
                || !ACTIONS.keySet().containsAll(actions)) {
                throw new IllegalArgumentException("Invalid reviewed DiamondFire policy: " + id);
            }
            policies.put(id, new ComponentPolicy(badge, limitation, actions));
        }
        if (policies.isEmpty()) throw new IllegalArgumentException("Empty DiamondFire coverage policy");
        return Map.copyOf(policies);
    }

    static String actionKey(String codeblock, String id) {
        // Do not strip IDs: two visibility actions intentionally have different whitespace.
        return codeblock + "/" + id;
    }

    static Map<String, List<String>> parseFormats(JsonObject data) {
        requireSchema(data, 1);
        if (!DiamondFireServerCompatibility.SERVER_VERSION.equals(data.get("server_version").getAsString())
            || !"src/test/resources/diamondfire/codec-format-policy.json".equals(data.get("policy_source").getAsString())) {
            throw new IllegalArgumentException("Wrong component-format baseline or evidence");
        }
        Map<String, List<String>> formats = new LinkedHashMap<>();
        for (var entry : data.getAsJsonObject("client_versions").entrySet()) {
            if (!DiamondFireFeatures.available(entry.getKey())) {
                throw new IllegalArgumentException("Older clients must not have DiamondFire format profiles");
            }
            List<String> ids = strings(entry.getValue().getAsJsonArray());
            if (!entry.getKey().matches("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?") || new HashSet<>(ids).size() != ids.size()
                || !SERVER_COMPONENTS.keySet().containsAll(ids)) {
                throw new IllegalArgumentException("Invalid component-format entries: " + entry.getKey());
            }
            formats.put(entry.getKey(), ids);
        }
        if (formats.isEmpty()) throw new IllegalArgumentException("Empty component-format profile");
        return Map.copyOf(formats);
    }

    private static void requireSchema(JsonObject data, int version) {
        if (!Integer.toString(version).equals(data.get("schema_version").getAsString())) {
            throw new IllegalArgumentException("Unknown DiamondFire snapshot schema");
        }
    }

    private static List<String> strings(JsonArray values) {
        return values.asList().stream().map(JsonElement::getAsString).toList();
    }

    record ServerComponent(boolean persistent, List<String> discoveredCodecFields) {
    }

    record ComponentPolicy(String badge, String limitation, List<String> actions) {
    }
}

package dev.nineofgaming.itemlab.editor.component;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Captures the maintained server codec corpus and checks it against supported client versions.
 */
public final class DiamondFireCodecCheck {
    private DiamondFireCodecCheck() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 3 || !Set.of("capture", "report", "verify").contains(arguments[0])) {
            throw new IllegalArgumentException("Expected capture, report, or verify, followed by baseline and case/report paths");
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registries = VanillaRegistries.createLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries)
            .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        String version = SharedConstants.getCurrentVersion().name();
        Path baselinePath = Path.of(arguments[1]);
        JsonObject baseline;
        if ("capture".equals(arguments[0])) {
            if (!"26.2".equals(version)) throw new AssertionError("Server baseline must be captured on 26.2");
            JsonObject extraCases = JsonParser.parseString(Files.readString(Path.of(arguments[2]))).getAsJsonObject();
            baseline = new JsonObject();
            baseline.addProperty("schema_version", 1);
            baseline.addProperty("minecraft_version", version);
            baseline.addProperty("source", "Minecraft vanilla registry-aware persistent codecs; defaults and reviewed nested/scalar probes");
            JsonArray components = new JsonArray();
            for (Identifier id : BuiltInRegistries.DATA_COMPONENT_TYPE.keySet().stream().sorted().toList()) {
                DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
                JsonObject component = new JsonObject();
                component.addProperty("id", id.toString());
                component.addProperty("persistent", type.codec() != null);
                JsonArray cases = new JsonArray();
                if (type.codec() != null) {
                    List<JsonElement> inputs = new ArrayList<>();
                    // Detect scalar ranges/alternatives and empty/default handling, even without discoverable keys.
                    for (String input : List.of("{}", "[]", "0", "1", "-1", "true", "false", "\"\"", "\"minecraft:stone\"")) {
                        inputs.add(JsonParser.parseString(input));
                    }
                    for (var item : BuiltInRegistries.ITEM) {
                        if (item == Items.AIR) continue;
                        Object value = item.getDefaultInstance().get(type);
                        if (value == null) continue;
                        var encoded = codec(type).encodeStart(ops, value).result();
                        if (encoded.isPresent() && !inputs.contains(encoded.get())) inputs.add(encoded.get());
                    }
                    if (extraCases.has(id.toString())) {
                        for (JsonElement input : extraCases.getAsJsonArray(id.toString())) {
                            if (!inputs.contains(input)) inputs.add(input);
                        }
                    }
                    inputs.sort(Comparator.comparing(JsonElement::toString));
                    for (JsonElement input : inputs) {
                        JsonObject sample = new JsonObject();
                        sample.add("input", input);
                        sample.add("result", outcome(type, ops, input));
                        cases.add(sample);
                    }
                }
                component.add("cases", cases);
                components.add(component);
            }
            baseline.add("components", components);
            write(baselinePath, baseline);
            System.out.println("Captured complete 26.2 codec corpus in " + baselinePath);
            return;
        }
        baseline = JsonParser.parseString(Files.readString(baselinePath)).getAsJsonObject();
        if (baseline.get("schema_version").getAsInt() != 1 || !"26.2".equals(baseline.get("minecraft_version").getAsString())) {
            throw new AssertionError("Invalid independent codec baseline");
        }
        Set<String> serverIds = new TreeSet<>();
        Set<String> differenceIds = new TreeSet<>();
        JsonArray differences = new JsonArray();
        int sampleCount = 0;
        for (JsonElement entry : baseline.getAsJsonArray("components")) {
            JsonObject component = entry.getAsJsonObject();
            String id = component.get("id").getAsString();
            if (!serverIds.add(id)) throw new AssertionError("Duplicate codec baseline ID: " + id);
            var key = Identifier.parse(id);
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(key)
                ? BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(key) : null;
            boolean persistent = type != null && type.codec() != null;
            if (type != null && persistent != component.get("persistent").getAsBoolean()) differenceIds.add(id);
            for (JsonElement sampleElement : component.getAsJsonArray("cases")) {
                sampleCount++;
                JsonObject sample = sampleElement.getAsJsonObject();
                JsonObject actual = outcome(type, ops, sample.get("input"));
                if (!actual.equals(sample.get("result"))) {
                    differenceIds.add(id);
                    JsonObject difference = new JsonObject();
                    difference.addProperty("component", id);
                    difference.add("input", sample.get("input"));
                    difference.add("server", sample.get("result"));
                    difference.add("client", actual);
                    differences.add(difference);
                }
            }
        }
        Set<String> clientIds = new TreeSet<>();
        BuiltInRegistries.DATA_COMPONENT_TYPE.keySet().forEach(id -> clientIds.add(id.toString()));
        Set<String> clientOnly = new TreeSet<>(clientIds);
        clientOnly.removeAll(serverIds);
        Set<String> serverOnly = new TreeSet<>(serverIds);
        serverOnly.removeAll(clientIds);
        JsonObject report = new JsonObject();
        report.addProperty("schema_version", 1);
        report.addProperty("server_version", "26.2");
        report.addProperty("client_version", version);
        report.addProperty("samples_compared", sampleCount);
        report.add("client_only", strings(clientOnly));
        report.add("server_only", strings(serverOnly));
        report.add("codec_differences", strings(differenceIds.stream().filter(clientIds::contains).toList()));
        report.add("differences", differences);
        if ("report".equals(arguments[0])) {
            write(Path.of(arguments[2]), report);
        } else if ("verify".equals(arguments[0])) {
            JsonObject expected = JsonParser.parseString(Files.readString(Path.of(arguments[2]))).getAsJsonObject();
            if (!report.equals(expected))
                throw new AssertionError("Codec comparison changed for " + version + "; regenerate and review its report");
        } else throw new IllegalArgumentException("Expected capture, report, or verify");
        if ("26.2".equals(version) && (!differenceIds.isEmpty() || !clientOnly.isEmpty() || !serverOnly.isEmpty())) {
            throw new AssertionError("Independent server baseline drift: " + differenceIds);
        }
        System.out.println("Compared " + sampleCount + " codec inputs: client " + version + " vs DF 26.2; client-only="
            + clientOnly + "; server-only=" + serverOnly + "; codec differences=" + report.get("codec_differences"));
    }

    private static JsonObject outcome(DataComponentType<?> type, com.mojang.serialization.DynamicOps<JsonElement> ops, JsonElement input) {
        JsonObject result = new JsonObject();
        if (type == null || type.codec() == null) {
            result.addProperty("status", type == null ? "absent" : "network-only");
            return result;
        }
        var decoded = codec(type).parse(ops, input).result();
        if (decoded.isEmpty()) {
            result.addProperty("status", "rejected");
            return result;
        }
        var encoded = codec(type).encodeStart(ops, decoded.get()).result();
        if (encoded.isEmpty()) throw new AssertionError("Accepted component cannot be encoded: " + input);
        result.addProperty("status", "accepted");
        result.add("encoded", encoded.get());
        // Normalize floating-point JSON primitives exactly as the checked-in JSON is serialized.
        return JsonParser.parseString(result.toString()).getAsJsonObject();
    }

    @SuppressWarnings("unchecked")
    private static Codec<Object> codec(DataComponentType<?> type) {
        return (Codec<Object>) type.codec();
    }

    private static JsonArray strings(Iterable<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static void write(Path path, JsonObject value) throws Exception {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(value) + "\n", StandardCharsets.UTF_8);
    }
}

package dev.nineofgaming.itemlab.editor.component;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nineofgaming.itemlab.editor.diamondfire.DiamondFireFeatures;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Developer-only capture of the independent DiamondFire server's vanilla registry.
 */
public final class DiamondFireRegistrySnapshot {
    private DiamondFireRegistrySnapshot() {
    }

    public static void main(String[] arguments) throws IOException {
        SharedConstants.tryDetectVersion();
        String version = SharedConstants.getCurrentVersion().name();
        if (!DiamondFireFeatures.serverVersion().equals(version)) {
            throw new IllegalStateException("Capture the DiamondFire registry using Minecraft "
                + DiamondFireFeatures.serverVersion() + ", not " + version);
        }
        Bootstrap.bootStrap();
        var registries = VanillaRegistries.createLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries)
            .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("schema_version", 2);
        snapshot.addProperty("minecraft_version", version);
        snapshot.addProperty("source", "Minecraft vanilla runtime: BuiltInRegistries.DATA_COMPONENT_TYPE");
        JsonArray components = new JsonArray();
        BuiltInRegistries.DATA_COMPONENT_TYPE.keySet().stream().sorted().forEach(id -> {
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
            JsonObject component = new JsonObject();
            component.addProperty("id", id.toString());
            component.addProperty("persistent", type.codec() != null);
            // Keys may belong to nested records or alternatives, not necessarily the root.
            // Empty means an undiscovered/scalar shape, not proof that there are no fields.
            JsonArray fields = new JsonArray();
            CodecFieldDiscovery.fieldsFor(type).stream().sorted().forEach(fields::add);
            component.add("discovered_codec_fields", fields);
            components.add(component);
        });
        snapshot.add("components", components);
        String encoded = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(snapshot) + "\n";
        Path destination = Path.of(arguments[0]);
        boolean check = arguments.length > 1 && "--check".equals(arguments[1]);
        if (check) {
            if (!Files.readString(destination, StandardCharsets.UTF_8).replace("\r\n", "\n").equals(encoded)) {
                throw new AssertionError("Generated server registry differs from " + destination);
            }
        } else {
            Files.createDirectories(destination.toAbsolutePath().getParent());
            Files.writeString(destination, encoded, StandardCharsets.UTF_8);
        }
        System.out.println((check ? "Verified " : "Captured ") + components.size() + " Minecraft " + version + " components in " + destination);
    }
}

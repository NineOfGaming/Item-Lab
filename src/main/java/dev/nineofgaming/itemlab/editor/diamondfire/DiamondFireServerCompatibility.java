package dev.nineofgaming.itemlab.editor.diamondfire;

import net.minecraft.SharedConstants;

import java.util.List;
import java.util.Set;

/**
 * The vanilla data-component registry used by DiamondFire's current server.
 * Keep this profile versioned separately from the client so newer clients do
 * not accidentally report their own components as server-supported.
 */
final class DiamondFireServerCompatibility {
    static final String SERVER_VERSION = DiamondFireFeatures.serverVersion();

    private DiamondFireServerCompatibility() {
    }

    static Compatibility forComponent(String id, List<String> clientCodecFields) {
        return forComponent(id, clientCodecFields, SharedConstants.getCurrentVersion().name());
    }

    static Compatibility forComponent(String id, List<String> clientCodecFields, String clientVersion) {
        if (!DiamondFireFeatures.available(clientVersion)) {
            return new Compatibility(false, false, "DiamondFire features are unavailable on this Minecraft version.");
        }
        var server = DiamondFireCompatibilityData.SERVER_COMPONENTS.get(id);
        if (server == null) {
            return new Compatibility(false, false,
                "Not in DiamondFire's Minecraft " + SERVER_VERSION
                    + " registry; likely rejected. Kept locally.");
        }
        if (DiamondFireCompatibilityData.CLIENT_FORMATS.getOrDefault(clientVersion, List.of()).contains(id)) {
            return new Compatibility(true, true,
                "Some component values use a different format from DiamondFire (Minecraft " + SERVER_VERSION + ").");
        }
        // Discovery may reach nested MapCodecs or scalar alternatives: this is only a drift warning.
        if (!clientCodecFields.isEmpty() && !server.discoveredCodecFields().isEmpty()
            && !Set.copyOf(clientCodecFields).equals(Set.copyOf(server.discoveredCodecFields()))) {
            return new Compatibility(true, true,
                "Different component fields from DiamondFire (Minecraft " + SERVER_VERSION + ").");
        }
        return new Compatibility(true, false, "");
    }

    record Compatibility(boolean supported, boolean versionSensitive, String detail) {
    }
}

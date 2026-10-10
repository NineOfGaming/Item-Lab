package dev.nineofgaming.itemlab.editor.diamondfire;

import dev.nineofgaming.itemlab.editor.component.EditorVersionProfile;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentType;

import java.util.List;

/**
 * Offline registry facts combined with the reviewed component-field evidence policy.
 */
public final class DiamondFireComponentSupport {
    private static final Support DISABLED = new Support(Level.UNSUPPORTED, "",
        "DiamondFire features are unavailable on this Minecraft version.", List.of());

    private DiamondFireComponentSupport() {
    }

    public static Support forComponent(String id, DataComponentType<?> type) {
        if (!DiamondFireFeatures.available()) return DISABLED;
        return forComponent(id, type.codec() != null, EditorVersionProfile.rootFields(type));
    }

    static Support forComponent(String id, boolean persistent, List<String> clientCodecFields) {
        return forComponent(id, persistent, clientCodecFields, SharedConstants.getCurrentVersion().name());
    }

    static Support forComponent(String id, boolean persistent, List<String> clientCodecFields, String clientVersion) {
        if (!DiamondFireFeatures.available(clientVersion)) return DISABLED;
        if (!id.startsWith("minecraft:")) {
            return new Support(Level.UNSUPPORTED, "DF-",
                "This is a mod-registered component. DiamondFire's server does not know this client-side registry entry.",
                List.of());
        }
        var compatibility = DiamondFireServerCompatibility.forComponent(id, clientCodecFields, clientVersion);
        if (!compatibility.supported()) {
            return new Support(Level.UNKNOWN, "DF?", compatibility.detail(), List.of());
        }
        var server = DiamondFireCompatibilityData.SERVER_COMPONENTS.get(id);
        if (!persistent || !server.persistent()) {
            return new Support(Level.UNKNOWN, "DF?",
                "This component is network-only, so persistent DiamondFire support cannot be determined safely.",
                List.of());
        }
        var policy = DiamondFireCompatibilityData.POLICIES.get(id);
        if (policy != null) {
            Level level = switch (policy.badge()) {
                case "DF+" -> Level.CODEBLOCKS;
                case "DFP" -> Level.PARTIAL;
                case "DF?" -> Level.UNKNOWN;
                default -> throw new IllegalStateException("Unvalidated DiamondFire policy badge: " + policy.badge());
            };
            String description = switch (level) {
                case CODEBLOCKS -> "Supported by DiamondFire with dedicated codeblocks.";
                case PARTIAL ->
                    "Supported as vanilla item data on DiamondFire, but dedicated codeblocks cover only part of this component.";
                default -> "Dedicated codeblock support is unverified.";
            };
            if (!policy.limitation().isEmpty()) description += "\n\n" + policy.limitation();
            if (compatibility.versionSensitive()) description += "\n\n" + compatibility.detail();
            List<String> actions = policy.actions().stream().map(DiamondFireCompatibilityData.ACTIONS::get).distinct().toList();
            return new Support(level, policy.badge(), description, actions);
        }
        String description = "Supported as vanilla item data on DiamondFire, but the current actiondump has no dedicated codeblock for it.";
        if (compatibility.versionSensitive()) {
            return new Support(Level.VERSION_SENSITIVE, "DF~",
                description + "\n\n" + compatibility.detail(), List.of());
        }
        return new Support(Level.PASSIVE, "DF", description, List.of());
    }

    public static String badgeLegend() {
        return """
            DF+ = dedicated codeblock coverage
            DFP = partial codeblock coverage
            DF = vanilla item data
            DF~ = version-sensitive format
            DF? = uncertain/network-only
            DF- = unsupported client registry entry""";
    }

    public enum Level {
        CODEBLOCKS,
        PARTIAL,
        PASSIVE,
        VERSION_SENSITIVE,
        UNKNOWN,
        UNSUPPORTED
    }

    public record Support(Level level, String badge, String description, List<String> codeblocks) {
        public String tooltip() {
            StringBuilder result = new StringBuilder(description);
            if (!codeblocks.isEmpty()) {
                result.append("\n\nCodeblocks:");
                for (String codeblock : codeblocks) result.append("\n- ").append(codeblock);
            }
            return result.toString();
        }
    }
}

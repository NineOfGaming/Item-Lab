package dev.nineofgaming.itemlab.editor.diamondfire;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.util.Locale;

/**
 * Tracks whether the client's actual multiplayer connection target belongs to DiamondFire.
 */
public final class DiamondFireContext {
    private static final String PRIMARY_DOMAIN = "mcdiamondfire.com";
    private static final String LEGACY_DOMAIN = "mcdiamondfire.net";
    private static final String GAMES_DOMAIN = "diamondfire.games";

    private static volatile String connectionHost;

    private DiamondFireContext() {
    }

    public static State current() {
        if (!DiamondFireFeatures.available()) return State.INACTIVE;
        String host = connectionHost;
        if (host == null
            || Minecraft.getInstance().getConnection() == null
            || !isDiamondFireHost(host)) {
            return State.INACTIVE;
        }
        return new State(true, host);
    }

    /**
     * Called whenever Minecraft begins connecting, including server transfers.
     */
    public static void setConnectionTarget(ServerAddress address) {
        connectionHost = normalizeHost(address.getHost());
    }

    public static void clearConnectionTarget() {
        connectionHost = null;
    }

    static boolean isDiamondFireHost(String host) {
        String normalized = normalizeHost(host);
        return normalized != null
            && (isDomain(normalized, PRIMARY_DOMAIN) || isDomain(normalized, GAMES_DOMAIN) || isDomain(normalized, LEGACY_DOMAIN));
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return null;
        }

        String normalized = host.strip().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static boolean isDomain(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    public record State(boolean active, String host) {
        private static final State INACTIVE = new State(false, "");
    }
}

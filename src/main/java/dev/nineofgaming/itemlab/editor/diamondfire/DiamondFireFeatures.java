package dev.nineofgaming.itemlab.editor.diamondfire;

import net.minecraft.SharedConstants;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The current server baseline is the minimum client version for all DF features.
 */
public final class DiamondFireFeatures {
    private static final String SERVER_VERSION = readServerVersion();

    private DiamondFireFeatures() {
    }

    public static String serverVersion() {
        return SERVER_VERSION;
    }

    public static boolean available() {
        return available(SharedConstants.getCurrentVersion().name());
    }

    static boolean available(String clientVersion) {
        return atLeast(clientVersion, SERVER_VERSION);
    }

    static boolean atLeast(String clientVersion, String serverVersion) {
        if (clientVersion == null || serverVersion == null
            || !clientVersion.matches("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?")
            || !serverVersion.matches("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?")) return false;
        String[] client = clientVersion.split("\\.");
        String[] server = serverVersion.split("\\.");
        for (int index = 0; index < Math.max(client.length, server.length); index++) {
            var clientPart = new java.math.BigInteger(index < client.length ? client[index] : "0");
            var serverPart = new java.math.BigInteger(index < server.length ? server[index] : "0");
            int difference = clientPart.compareTo(serverPart);
            if (difference != 0) return difference > 0;
        }
        return true;
    }

    private static String readServerVersion() {
        try (InputStream stream = DiamondFireFeatures.class.getResourceAsStream(
            "/assets/itemlab/diamondfire/server.properties")) {
            if (stream == null) throw new IllegalStateException("Missing DiamondFire server baseline");
            Properties properties = new Properties();
            properties.load(stream);
            String version = properties.getProperty("minecraft_version");
            if (!atLeast(version, version)) throw new IllegalStateException("Invalid DiamondFire server baseline");
            return version;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read DiamondFire server baseline", exception);
        }
    }
}

package dev.nineofgaming.itemlab.editor.catalog;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Recipe identifiers discovered from data bundled with Minecraft and installed mods.
 */
public final class RecipeResourceCatalog {
    private static volatile List<IdSuggestion> cached;

    private RecipeResourceCatalog() {
    }

    public static List<IdSuggestion> load() {
        List<IdSuggestion> current = cached;
        if (current != null) return current;
        synchronized (RecipeResourceCatalog.class) {
            if (cached == null) cached = discover();
            return cached;
        }
    }

    private static List<IdSuggestion> discover() {
        Map<String, IdSuggestion> recipes = new HashMap<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            for (Path root : mod.getRootPaths()) {
                scanRoot(recipes, root);
            }
        }
        scanRuntimeClasspath(recipes);
        return recipes.values().stream()
            .sorted(Comparator.comparing(IdSuggestion::id))
            .toList();
    }

    private static void scanRuntimeClasspath(Map<String, IdSuggestion> recipes) {
        String classpath = System.getProperty("java.class.path", "");
        for (String entry : classpath.split(Pattern.quote(File.pathSeparator))) {
            if (entry.isBlank()) continue;
            try {
                Path path = Path.of(entry);
                if (Files.isDirectory(path)) {
                    scanRoot(recipes, path);
                } else if (Files.isRegularFile(path) && entry.endsWith(".jar")) {
                    scanJar(recipes, path);
                }
            } catch (Exception ignored) {
                // Some launchers include virtual or no-longer-present classpath entries.
            }
        }
    }

    private static void scanRoot(Map<String, IdSuggestion> recipes, Path root) {
        Path dataRoot = root.resolve("data");
        if (!Files.isDirectory(dataRoot)) return;
        try (Stream<Path> namespaces = Files.list(dataRoot)) {
            namespaces.filter(Files::isDirectory).forEach(namespaceRoot -> {
                scanDirectory(recipes, namespaceRoot, "recipe");
                scanDirectory(recipes, namespaceRoot, "recipes");
            });
        } catch (Exception ignored) {
            // A broken or temporarily unavailable resource root should not block the catalog.
        }
    }

    private static void scanJar(Map<String, IdSuggestion> recipes, Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            jar.stream()
                .filter(entry -> !entry.isDirectory())
                .map(JarEntry::getName)
                .filter(name -> name.endsWith(".json"))
                .forEach(name -> addJarRecipe(recipes, name));
        } catch (Exception ignored) {
            // Non-resource libraries and unreadable archives can be skipped safely.
        }
    }

    private static void addJarRecipe(Map<String, IdSuggestion> recipes, String name) {
        String[] parts = name.split("/", 4);
        if (parts.length != 4 || !parts[0].equals("data")) return;
        if (!parts[2].equals("recipe") && !parts[2].equals("recipes")) return;
        String localPath = parts[3].substring(0, parts[3].length() - ".json".length());
        addRecipe(recipes, parts[1], localPath);
    }

    private static void scanDirectory(
        Map<String, IdSuggestion> recipes,
        Path namespaceRoot,
        String directoryName
    ) {
        Path recipeRoot = namespaceRoot.resolve(directoryName);
        if (!Files.isDirectory(recipeRoot)) return;
        String namespace = namespaceRoot.getFileName().toString();
        try (Stream<Path> files = Files.walk(recipeRoot)) {
            files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .forEach(path -> {
                    String localPath = recipeRoot.relativize(path).toString().replace('\\', '/');
                    localPath = localPath.substring(0, localPath.length() - ".json".length());
                    addRecipe(recipes, namespace, localPath);
                });
        } catch (Exception ignored) {
            // Continue with other roots when one mod's resources cannot be enumerated.
        }
    }

    private static void addRecipe(Map<String, IdSuggestion> recipes, String namespace, String localPath) {
        Identifier id = Identifier.tryParse(namespace + ":" + localPath);
        if (id != null) recipes.putIfAbsent(id.toString(), IdSuggestion.of(id.toString()));
    }
}

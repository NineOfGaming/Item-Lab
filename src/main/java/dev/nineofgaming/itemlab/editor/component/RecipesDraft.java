package dev.nineofgaming.itemlab.editor.component;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable, order-preserving model for the {@code minecraft:recipes} component.
 */
public record RecipesDraft(boolean componentPresent, List<Identifier> recipeIds) {
    public RecipesDraft {
        recipeIds = List.copyOf(recipeIds);
    }

    public static RecipesDraft fromJson(boolean componentPresent, JsonElement encoded) {
        if (encoded == null || !encoded.isJsonArray()) {
            throw new IllegalArgumentException("Recipes must be encoded as a list");
        }
        List<Identifier> identifiers = new ArrayList<>();
        for (JsonElement entry : encoded.getAsJsonArray()) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Every recipe entry must be an identifier");
            }
            identifiers.add(parse(entry.getAsString()));
        }
        return new RecipesDraft(componentPresent, identifiers);
    }

    public static RecipesDraft fromStrings(boolean componentPresent, List<String> recipeIds) {
        return new RecipesDraft(
            componentPresent,
            recipeIds.stream().map(RecipesDraft::parse).toList()
        );
    }

    public JsonArray toJson() {
        JsonArray encoded = new JsonArray();
        recipeIds.forEach(identifier -> encoded.add(identifier.toString()));
        return encoded;
    }

    public int duplicateCount() {
        Set<Identifier> seen = new HashSet<>();
        int duplicates = 0;
        for (Identifier identifier : recipeIds) {
            if (!seen.add(identifier)) duplicates++;
        }
        return duplicates;
    }

    public RecipesDraft withoutDuplicates() {
        return new RecipesDraft(componentPresent, List.copyOf(new LinkedHashSet<>(recipeIds)));
    }

    public RecipesDraft withMissing(List<Identifier> candidates) {
        List<Identifier> combined = new ArrayList<>(recipeIds);
        Set<Identifier> present = new HashSet<>(recipeIds);
        for (Identifier candidate : candidates) {
            if (present.add(candidate)) combined.add(candidate);
        }
        return new RecipesDraft(componentPresent, combined);
    }

    private static Identifier parse(String source) {
        String value = source == null ? "" : source.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Recipe identifier cannot be empty");
        Identifier identifier = Identifier.tryParse(value);
        if (identifier == null) throw new IllegalArgumentException("Invalid recipe identifier: " + value);
        return identifier;
    }
}

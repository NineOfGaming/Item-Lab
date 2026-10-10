package dev.nineofgaming.itemlab.editor.catalog;

import java.util.Locale;

public record IdSuggestion(String id, String displayName, String searchable) {
    public static IdSuggestion of(String id) {
        return new IdSuggestion(id, id, id.toLowerCase(Locale.ROOT));
    }
}

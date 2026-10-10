package dev.nineofgaming.itemlab.editor.catalog;

public enum ComponentState {
    OVERRIDDEN("[*]", "explicitly overridden"),
    DEFAULT("[D]", "inherited from the base item"),
    REMOVED("[-]", "explicitly removed from the base item"),
    ABSENT("[ ]", "not present on this item");

    private final String marker;
    private final String description;

    ComponentState(String marker, String description) {
        this.marker = marker;
        this.description = description;
    }

    public String marker() {
        return marker;
    }

    public String description() {
        return description;
    }
}

package dev.nineofgaming.itemlab.editor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Dirty aggregates and fail-fast coverage for the owners registered with global save.
 */
public final class PendingEditorRegistry {
    private final Map<String, Supplier<Boolean>> dirtyQueries = new LinkedHashMap<>();
    private final Set<Object> owners = Collections.newSetFromMap(new IdentityHashMap<>());

    public void register(String id, Object owner, Supplier<Boolean> dirty) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(dirty, "dirty");
        if (dirtyQueries.containsKey(id)) throw new IllegalStateException("Duplicate pending participant: " + id);
        if (!owners.add(owner)) throw new IllegalStateException("Editor registered more than once: " + id);
        dirtyQueries.put(id, dirty);
    }

    public boolean hasPending(Predicate<String> include) {
        for (var entry : dirtyQueries.entrySet()) {
            if (!include.test(entry.getKey())) continue;
            try {
                if (entry.getValue().get()) return true;
            } catch (RuntimeException exception) {
                // A broken/invalid dirty query must never make an editor disappear from Save.
                return true;
            }
        }
        return false;
    }

    /**
     * Automatically inventories state owners exposing the public dirty-state contract.
     */
    public void verifyCoverage(Object host) {
        for (Class<?> type = host.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !hasDirtyContract(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    if (!owners.contains(field.get(host))) {
                        throw new IllegalStateException("Editor missing from global save: " + field.getName());
                    }
                } catch (IllegalAccessException exception) {
                    throw new IllegalStateException("Cannot audit pending editor: " + field.getName(), exception);
                }
            }
        }
    }

    private static boolean hasDirtyContract(Class<?> type) {
        try {
            return type.getMethod("isDirty").getReturnType() == boolean.class;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }
}

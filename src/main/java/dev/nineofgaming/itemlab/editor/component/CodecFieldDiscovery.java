package dev.nineofgaming.itemlab.editor.component;

import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.component.DataComponentType;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Discovers record field names from installed component codecs without assuming vanilla component IDs.
 */
final class CodecFieldDiscovery {
    private static final int MAX_DEPTH = 24;
    private static final int MAX_VISITED_OBJECTS = 4_096;
    private static final int MAX_FIELDS = 64;
    private static final Map<DataComponentType<?>, List<String>> CACHE = new IdentityHashMap<>();

    private CodecFieldDiscovery() {
    }

    static synchronized List<String> fieldsFor(DataComponentType<?> type) {
        return CACHE.computeIfAbsent(type, CodecFieldDiscovery::discover);
    }

    private static List<String> discover(DataComponentType<?> type) {
        Object codec;
        try {
            codec = type.codec();
        } catch (RuntimeException exception) {
            return List.of();
        }
        if (codec == null) {
            return List.of();
        }

        ArrayDeque<Node> queue = new ArrayDeque<>();
        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        queue.add(new Node(codec, 0));
        while (!queue.isEmpty() && visited.size() < MAX_VISITED_OBJECTS) {
            Node node = queue.removeFirst();
            Object value = node.value();
            if (value == null || node.depth() > MAX_DEPTH || visited.put(value, Boolean.TRUE) != null) {
                continue;
            }
            if (value instanceof MapCodec<?> mapCodec) {
                try {
                    List<String> names = mapCodec.keys(JsonOps.INSTANCE)
                        .filter(element -> element != null && element.isJsonPrimitive())
                        .map(element -> element.getAsString().trim())
                        .filter(name -> !name.isEmpty())
                        .distinct()
                        .limit(MAX_FIELDS)
                        .toList();
                    if (!names.isEmpty()) {
                        return names;
                    }
                } catch (RuntimeException ignored) {
                    // Continue through wrapper codecs supplied by Minecraft or another mod.
                }
            }
            enqueueChildren(queue, value, node.depth() + 1);
        }
        return List.of();
    }

    private static void enqueueChildren(ArrayDeque<Node> queue, Object value, int depth) {
        if (value instanceof Optional<?> optional) {
            optional.ifPresent(child -> enqueue(queue, child, depth));
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(child -> enqueue(queue, child, depth));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(child -> enqueue(queue, child, depth));
            return;
        }
        if (value.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(value); index++) {
                enqueue(queue, Array.get(value, index), depth);
            }
            return;
        }
        if (!isCodecImplementation(value.getClass())) {
            return;
        }

        for (Class<?> owner = value.getClass(); owner != null && owner != Object.class; owner = owner.getSuperclass()) {
            for (Field field : owner.getDeclaredFields()) {
                // Captured codecs in Mojang's anonymous/lambda wrappers are synthetic.
                // Static fields are global graphs and must not be traversed.
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    enqueue(queue, field.get(value), depth);
                } catch (RuntimeException | ReflectiveOperationException ignored) {
                    // Some JVM or mod implementation fields are intentionally inaccessible.
                }
            }
        }
    }

    private static void enqueue(ArrayDeque<Node> queue, Object value, int depth) {
        if (value != null) {
            queue.addLast(new Node(value, depth));
        }
    }

    private static boolean isCodecImplementation(Class<?> type) {
        if (type.isPrimitive() || type.isEnum()) {
            return false;
        }
        String name = type.getName();
        return name.startsWith("com.mojang.serialization.")
            || name.startsWith("com.mojang.datafixers.")
            || name.startsWith("net.minecraft.");
    }

    private record Node(Object value, int depth) {
    }
}

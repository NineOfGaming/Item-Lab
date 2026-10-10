package dev.nineofgaming.itemlab.editor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Shared access to private editor state in headless fixtures. */
public final class EditorCheckSupport {
    private EditorCheckSupport() {
    }

    public static Object read(Object target, String name) throws ReflectiveOperationException {
        return field(target, name).get(target);
    }

    public static void write(Object target, String name, Object value) throws ReflectiveOperationException {
        field(target, name).set(target, value);
    }

    public static Object invoke(Object target, String name, Class<?>[] parameters, Object... arguments)
        throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                return method.invoke(target, arguments);
            } catch (NoSuchMethodException ignored) {
                // Grouped panels inherit shared recovery helpers.
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }

    public static void require(boolean condition, String problem) {
        if (!condition) throw new AssertionError(problem);
    }

    private static Field field(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Grouped panels keep their dirty flags in the superclass.
            }
        }
        throw new NoSuchFieldException(target.getClass().getName() + "." + name);
    }
}

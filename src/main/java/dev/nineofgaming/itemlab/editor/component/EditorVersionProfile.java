package dev.nineofgaming.itemlab.editor.component;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nineofgaming.itemlab.ItemLab;
import dev.nineofgaming.itemlab.editor.catalog.ComponentOption;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Capabilities of the Minecraft version this jar was built for.
 *
 * <p>The target name is selected by Stonecutter, while component availability and
 * serialized root fields come from the active game's registry and codecs. That keeps
 * the editor adaptive when a release, datapack registry, or mod changes item data.</p>
 */
public final class EditorVersionProfile {
    private static final Logger LOGGER = ItemLab.createLogger("Version Profile");
    private static final Map<DataComponentType<?>, ComponentCapability> BY_TYPE = new IdentityHashMap<>();
    private static final Map<String, ComponentCapability> BY_ID = new HashMap<>();
    private static Map<String, ComponentCapability> lastReportedProfile = Map.of();
    private static boolean profileReported;

    private EditorVersionProfile() {
    }

    public static String targetVersion() {
        //? if >=26.2 {
        return "26.2";
        //?} else {
        /*return "26.1";
         *///?}
    }

    public static synchronized void refresh(List<ComponentOption> components) {
        BY_TYPE.clear();
        BY_ID.clear();
        for (ComponentOption option : components) {
            List<String> fields = List.copyOf(CodecFieldDiscovery.fieldsFor(option.type()));
            ComponentCapability capability = new ComponentCapability(option.id(), fields);
            BY_TYPE.put(option.type(), capability);
            BY_ID.put(option.id(), capability);
        }

        Map<String, ComponentCapability> currentProfile = Map.copyOf(BY_ID);
        if (profileReported && currentProfile.equals(lastReportedProfile)) return;
        for (ComponentCapability capability : currentProfile.values()) reportSchemaDrift(capability);
        LOGGER.info(
            "Loaded Minecraft {} editor profile: {} item components, {} object codec shapes discovered",
            targetVersion(),
            BY_ID.size(),
            BY_TYPE.values().stream().filter(ComponentCapability::hasKnownRootShape).count()
        );
        lastReportedProfile = currentProfile;
        profileReported = true;
    }

    public static synchronized boolean hasComponent(String id) {
        return BY_ID.containsKey(id);
    }

    /**
     * Returns true when the field is present, or when the codec shape could not be
     * discovered safely. Unknown shapes deliberately remain editable.
     */
    public static synchronized boolean supportsRootField(DataComponentType<?> type, String field) {
        ComponentCapability capability = capability(type);
        return capability == null || !capability.hasKnownRootShape() || capability.rootFields().contains(field);
    }

    public static synchronized boolean hasKnownRootShape(DataComponentType<?> type) {
        ComponentCapability capability = capability(type);
        return capability != null && capability.hasKnownRootShape();
    }

    public static synchronized List<String> rootFields(DataComponentType<?> type) {
        ComponentCapability capability = capability(type);
        return capability == null ? List.of() : capability.rootFields();
    }

    /**
     * Removes fields which are known not to exist in this version's root codec.
     */
    public static JsonElement adaptStarter(DataComponentType<?> type, JsonElement requested) {
        if (!requested.isJsonObject() || !hasKnownRootShape(type)) {
            return requested.deepCopy();
        }
        Set<String> supported = Set.copyOf(rootFields(type));
        JsonObject adapted = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : requested.getAsJsonObject().entrySet()) {
            if (supported.contains(entry.getKey())) {
                adapted.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        return adapted;
    }

    /**
     * Finds a codec-compatible starter without assuming that an older starter still fits.
     */
    static JsonElement compatibleStarter(
        Minecraft client,
        ItemStack draft,
        DataComponentType<?> type,
        JsonElement requested
    ) {
        JsonElement adapted = adaptStarter(type, requested);
        if (ComponentValueFactory.accepts(client, draft, type, adapted)) {
            return adapted;
        }

        Object prototype = draft.getPrototype().get(type);
        if (prototype != null) {
            try {
                return ItemStackCodecs.encodeComponent(client, type, prototype);
            } catch (RuntimeException ignored) {
                // Continue with other values installed for the active version.
            }
        }

        Object common = ComponentValueFactory.commonStarter(type);
        if (common != null) {
            try {
                JsonElement encoded = ItemStackCodecs.encodeComponent(client, type, common);
                if (ComponentValueFactory.accepts(client, draft, type, encoded)) {
                    return encoded;
                }
            } catch (RuntimeException ignored) {
                // Continue with installed item examples.
            }
        }

        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            Object installed = item.getDefaultInstance().get(type);
            if (installed == null) continue;
            try {
                JsonElement encoded = ItemStackCodecs.encodeComponent(client, type, installed);
                if (ComponentValueFactory.accepts(client, draft, type, encoded)) {
                    return encoded;
                }
            } catch (RuntimeException ignored) {
                // A modded example may require registry data unavailable here.
            }
        }
        return adapted;
    }

    public static synchronized List<String> fieldsNotHandledBy(
        DataComponentType<?> type,
        Set<String> handledFields
    ) {
        ComponentCapability capability = capability(type);
        if (capability == null || !capability.hasKnownRootShape()) return List.of();
        List<String> extra = new ArrayList<>();
        for (String field : capability.rootFields()) {
            if (!handledFields.contains(field)) extra.add(field);
        }
        return List.copyOf(extra);
    }

    public static synchronized List<String> handledFieldsNotSupportedBy(
        DataComponentType<?> type,
        Set<String> handledFields
    ) {
        ComponentCapability capability = capability(type);
        if (capability == null || !capability.hasKnownRootShape()) return List.of();
        List<String> missing = new ArrayList<>();
        for (String field : handledFields) {
            if (!capability.rootFields().contains(field)) missing.add(field);
        }
        Collections.sort(missing);
        return List.copyOf(missing);
    }

    private static ComponentCapability capability(DataComponentType<?> type) {
        ComponentCapability capability = BY_TYPE.get(type);
        if (capability != null) return capability;
        Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        return id == null ? null : BY_ID.get(id.toString());
    }

    private static void reportSchemaDrift(ComponentCapability capability) {
        Set<String> declared = ComponentSchemas.declaredRootFields(capability.id());
        if (declared.isEmpty() || !capability.hasKnownRootShape()) return;

        Set<String> removed = new HashSet<>(declared);
        capability.rootFields().forEach(removed::remove);
        Set<String> added = new HashSet<>(capability.rootFields());
        added.removeAll(declared);
        if (!removed.isEmpty() || !added.isEmpty()) {
            LOGGER.debug(
                "Component schema differs in Minecraft {} for {} (not present: {}; newly discovered: {})",
                targetVersion(),
                capability.id(),
                sorted(removed),
                sorted(added)
            );
        }
    }

    private static List<String> sorted(Set<String> values) {
        List<String> result = new ArrayList<>(values);
        Collections.sort(result);
        return result;
    }

    private record ComponentCapability(
        String id,
        List<String> rootFields
    ) {
        boolean hasKnownRootShape() {
            return !rootFields.isEmpty();
        }
    }
}

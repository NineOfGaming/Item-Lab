package dev.nineofgaming.itemlab.editor.diamondfire;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nineofgaming.itemlab.editor.catalog.ComponentOption;
import dev.nineofgaming.itemlab.editor.component.EditorVersionProfile;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Offline validation of generated facts, reviewed field evidence, and compatibility policy.
 */
public final class DiamondFireCompatibilityCheck {
    private static final Set<String> BADGES = Set.of("DF+", "DFP", "DF", "DF~", "DF?", "DF-");
    private static final Set<String> OPERATIONS = Set.of("read", "write", "clear", "approximate", "uncertain");

    private DiamondFireCompatibilityCheck() {
    }

    public static void main(String[] arguments) throws Exception {
        SharedConstants.tryDetectVersion();
        verifyAvailability();
        if (!DiamondFireFeatures.available()) {
            require(!DiamondFireContext.current().active(), "Older client gained DF context");
            var support = DiamondFireComponentSupport.forComponent("minecraft:custom_data", true, List.of());
            require(support.level() == DiamondFireComponentSupport.Level.UNSUPPORTED
                && support.badge().isEmpty() && support.codeblocks().isEmpty(), "Older client gained DF support hints");
            require(!DiamondFireServerCompatibility.forComponent("minecraft:custom_data", List.of()).supported(),
                "Older client still evaluates DF component compatibility");
            require(DiamondFireCompatibilityCheck.class.getResource("/assets/itemlab/diamondfire/actiondump.json") == null,
                "Older client still bundles DF compatibility snapshots");
            System.out.println("Verified DiamondFire features disabled on Minecraft " + SharedConstants.getCurrentVersion().name());
            return;
        }
        Bootstrap.bootStrap();
        var registries = VanillaRegistries.createLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries)
            .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
        List<ComponentOption> components = new ArrayList<>();
        BuiltInRegistries.DATA_COMPONENT_TYPE.keySet().forEach(id -> components.add(
            new ComponentOption(id.toString(), id.toString(), BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id))
        ));
        EditorVersionProfile.refresh(components);
        verifyRuntimeRegistry(components);
        verifyBadges();
        verifyCodecEvidence();
        verifyConnectionHookTargets();
        JsonObject current = readFixture("actiondump-current.json");
        JsonObject previous = readFixture("actiondump-previous.json");
        Map<String, JsonObject> currentActions = actions(current);
        require(actions(previous).containsKey("SET VARIABLE/SetItemGlowing"), "Prior fixture lost the glow action");
        require(previous.getAsJsonObject("provenance").get("patch").isJsonNull(), "Do not invent a patch for the historical export");
        verifyRuntimeActions(current, currentActions);
        JsonObject evidence = readFixture("component-evidence.json");
        verifyEvidence(evidence, currentActions);
        verifyRejectedMetadata(evidence, currentActions);
        System.out.println("Verified DiamondFire Minecraft " + DiamondFireServerCompatibility.SERVER_VERSION
            + " snapshot: " + DiamondFireCompatibilityData.SERVER_COMPONENTS.size() + " components, "
            + currentActions.size() + " actions, all six badges and reviewed field evidence (client "
            + EditorVersionProfile.targetVersion() + ")");
    }

    private static void verifyAvailability() {
        for (String older : List.of("1.21.11", "26.1", "26.1.9")) {
            require(!DiamondFireFeatures.atLeast(older, "26.2"), "Older version enabled DF: " + older);
        }
        for (String supported : List.of("26.2", "26.2.0", "26.2.1", "26.3", "26.10", "37.4")) {
            require(DiamondFireFeatures.atLeast(supported, "26.2"), "Supported version disabled DF: " + supported);
        }
        require(!DiamondFireFeatures.atLeast("26.2", "37.4")
                && !DiamondFireFeatures.atLeast("37.3", "37.4")
                && DiamondFireFeatures.atLeast("37.4", "37.4")
                && DiamondFireFeatures.atLeast("37.10", "37.4"),
            "Updating the server baseline must retire all older clients numerically");
        require(!DiamondFireFeatures.atLeast(null, "26.2")
            && !DiamondFireFeatures.atLeast("unknown", "26.2"), "Unknown versions must not bypass the gate");
    }

    private static void verifyConnectionHookTargets() throws Exception {
        ClassLoader loader = DiamondFireCompatibilityCheck.class.getClassLoader();
        Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft", false, loader);
        Class<?> screen = Class.forName("net.minecraft.client.gui.screens.Screen", false, loader);
        Class<?> connect = Class.forName("net.minecraft.client.gui.screens.ConnectScreen", false, loader);
        Class<?> address = Class.forName("net.minecraft.client.multiplayer.resolver.ServerAddress", false, loader);
        Class<?> data = Class.forName("net.minecraft.client.multiplayer.ServerData", false, loader);
        Class<?> transfer = Class.forName("net.minecraft.client.multiplayer.TransferState", false, loader);
        require(!java.lang.reflect.Modifier.isStatic(connect.getDeclaredMethod("connect", minecraft, address, data, transfer).getModifiers()),
            "Accepted connection hook no longer targets the actual instance connect method");
        minecraft.getDeclaredMethod("disconnect", screen, boolean.class, boolean.class);
    }

    private static void verifyRuntimeRegistry(List<ComponentOption> components) {
        Set<String> runtimeIds = new HashSet<>();
        for (ComponentOption component : components) {
            runtimeIds.add(component.id());
            var server = DiamondFireCompatibilityData.SERVER_COMPONENTS.get(component.id());
            require(server != null, "Supported client component missing from server snapshot: " + component.id());
            if (!DiamondFireServerCompatibility.forComponent(component.id(), EditorVersionProfile.rootFields(component.type())).versionSensitive()) {
                require(!DiamondFireComponentSupport.forComponent(component.id(), component.type()).tooltip()
                        .contains(DiamondFireServerCompatibility.SERVER_VERSION),
                    "Client version alone must not add a component warning: " + component.id());
            }
            if ("26.2".equals(EditorVersionProfile.targetVersion())) {
                require(server.persistent() == (component.type().codec() != null), "Persistent codec drift: " + component.id());
                require(Set.copyOf(server.discoveredCodecFields()).equals(Set.copyOf(EditorVersionProfile.rootFields(component.type()))),
                    "Discovered server codec-key drift: " + component.id());
                require(!DiamondFireServerCompatibility.forComponent(component.id(), EditorVersionProfile.rootFields(component.type())).versionSensitive(),
                    "Matching 26.2 codec incorrectly marked version sensitive: " + component.id());
            }
        }
        if ("26.2".equals(EditorVersionProfile.targetVersion())) {
            require(runtimeIds.equals(DiamondFireCompatibilityData.SERVER_COMPONENTS.keySet()), "Server snapshot is not the complete vanilla 26.2 registry");
        }
        for (String id : List.of("minecraft:additional_trade_cost", "minecraft:dye", "minecraft:sulfur_cube_content",
            "minecraft:cat/sound_variant", "minecraft:chicken/sound_variant", "minecraft:cow/sound_variant", "minecraft:pig/sound_variant")) {
            require(DiamondFireServerCompatibility.forComponent(id, List.of()).supported(), "Stale server exclusion: " + id);
            var expected = id.equals("minecraft:additional_trade_cost")
                ? DiamondFireComponentSupport.Level.UNKNOWN : DiamondFireComponentSupport.Level.PASSIVE;
            require(DiamondFireComponentSupport.forComponent(id, true, List.of()).level() == expected,
                "Server-only or recently added component lost registry/codec support: " + id);
        }
    }

    private static void verifyCodecEvidence() throws Exception {
        JsonObject corpus = readFixture("codec-baseline.json");
        require(corpus.get("schema_version").getAsInt() == 1 && corpus.get("minecraft_version").getAsString().equals("26.2"),
            "Codec corpus lost the independent server baseline");
        Set<String> ids = new HashSet<>();
        int cases = 0;
        for (var entry : corpus.getAsJsonArray("components")) {
            JsonObject component = entry.getAsJsonObject();
            String id = component.get("id").getAsString();
            require(ids.add(id) && DiamondFireCompatibilityData.SERVER_COMPONENTS.containsKey(id), "Invalid codec corpus ID: " + id);
            boolean persistent = component.get("persistent").getAsBoolean();
            require(persistent == DiamondFireCompatibilityData.SERVER_COMPONENTS.get(id).persistent(), "Codec corpus persistence drift: " + id);
            require(persistent ? component.getAsJsonArray("cases").size() >= 9 : component.getAsJsonArray("cases").isEmpty(),
                "Scalar/default probes are missing: " + id);
            cases += component.getAsJsonArray("cases").size();
        }
        require(ids.equals(DiamondFireCompatibilityData.SERVER_COMPONENTS.keySet()), "Codec corpus omits server components");
        JsonObject formatPolicy = readFixture("codec-format-policy.json");
        Set<String> versions = new HashSet<>();
        for (var entry : formatPolicy.getAsJsonArray("comparisons")) {
            JsonObject comparison = entry.getAsJsonObject();
            String version = comparison.get("client_version").getAsString();
            require(versions.add(version), "Duplicate reviewed codec client version");
            JsonObject report = readFixture("codec-comparison-" + version + ".json");
            require(report.get("server_version").getAsString().equals("26.2") && report.get("client_version").getAsString().equals(version)
                && report.get("samples_compared").getAsInt() == cases, "Codec report baseline/count drift");
            Set<String> reviewed = new HashSet<>();
            Set<String> formats = new HashSet<>();
            comparison.getAsJsonArray("formats").forEach(id -> {
                require(formats.add(id.getAsString()), "Duplicate format difference");
                reviewed.add(id.getAsString());
            });
            comparison.getAsJsonArray("registry_value_only").forEach(id -> require(reviewed.add(id.getAsString()), "Overlapping registry/format policy"));
            Set<String> observed = new HashSet<>();
            report.getAsJsonArray("codec_differences").forEach(id -> observed.add(id.getAsString()));
            require(reviewed.equals(observed) && formats.equals(Set.copyOf(DiamondFireCompatibilityData.CLIENT_FORMATS.get(version))),
                "Unreviewed codec differences or runtime projection drift: " + version);
            for (String id : formats) {
                require(DiamondFireServerCompatibility.forComponent(id, List.of(), version).versionSensitive(),
                    "Nested/scalar difference needs a warning even when discovered keys match: " + id);
                require(DiamondFireComponentSupport.forComponent(id, true, List.of(), version).tooltip().contains("26.2"),
                    "Editor hints hide a reviewed nested/scalar difference: " + id);
            }
            if ("26.3".equals(version)) {
                require(report.getAsJsonArray("client_only").size() == 13, "Actual 26.3 registry evidence is missing");
                for (var id : report.getAsJsonArray("client_only")) {
                    var support = DiamondFireComponentSupport.forComponent(id.getAsString(), true, List.of());
                    require(support.badge().equals("DF?") && support.description().contains("Kept locally"),
                        "Actual future component lost cautious preservation: " + id);
                }
            }
        }
        require(versions.equals(DiamondFireCompatibilityData.CLIENT_FORMATS.keySet()), "Runtime format profiles have no reviewed codec evidence");
        JsonObject invalid = DiamondFireCompatibilityData.read("component-formats.json").deepCopy();
        invalid.addProperty("server_version", "26.3");
        expectRejected(() -> DiamondFireCompatibilityData.parseFormats(invalid), "wrong format baseline");
    }

    private static void verifyBadges() {
        assertBadge("minecraft:enchantment_glint_override", true, List.of(), DiamondFireComponentSupport.Level.CODEBLOCKS, "DF+");
        assertBadge("minecraft:custom_data", true, List.of(), DiamondFireComponentSupport.Level.PARTIAL, "DFP");
        assertBadge("minecraft:dye", true, List.of(), DiamondFireComponentSupport.Level.PASSIVE, "DF");
        assertBadge("minecraft:damage_resistant", true, List.of("future_field"), DiamondFireComponentSupport.Level.VERSION_SENSITIVE, "DF~");
        assertBadge("minecraft:creative_slot_lock", false, List.of(), DiamondFireComponentSupport.Level.UNKNOWN, "DF?");
        assertBadge("example:client_component", true, List.of(), DiamondFireComponentSupport.Level.UNSUPPORTED, "DF-");

        var versioned = DiamondFireComponentSupport.forComponent("minecraft:food", true, List.of("future_field"));
        require(versioned.badge().equals("DF+")
            && versioned.tooltip().contains(DiamondFireServerCompatibility.SERVER_VERSION),
            "Dedicated policy hid the independent server-format warning");
        var future = DiamondFireComponentSupport.forComponent("minecraft:future_client_only_fixture", true, List.of("future_field"));
        require(future.level() == DiamondFireComponentSupport.Level.UNKNOWN && future.badge().equals("DF?")
            && future.description().contains("Kept locally"), "Unknown future data needs a preserved, cautious state");
        for (var entry : DiamondFireCompatibilityData.SERVER_COMPONENTS.entrySet()) {
            var support = DiamondFireComponentSupport.forComponent(entry.getKey(), entry.getValue().persistent(), entry.getValue().discoveredCodecFields());
            require(BADGES.contains(support.badge()), "Unknown support badge: " + support.badge());
            for (String line : support.description().split("\n")) {
                require(line.length() <= 160, "Badge hint contains a research paragraph: " + entry.getKey());
            }
            var policy = DiamondFireCompatibilityData.POLICIES.get(entry.getKey());
            if (support.level() == DiamondFireComponentSupport.Level.PARTIAL) {
                require(support.description().startsWith(
                        "Supported as vanilla item data on DiamondFire, but dedicated codeblocks cover only part of this component."),
                    "Partial hints lost their existing support explanation: " + entry.getKey());
                require(policy != null && !policy.limitation().isBlank()
                        && support.description().endsWith("\n\n" + policy.limitation()),
                    "Partial hints lost their component-specific limitation: " + entry.getKey());
            }
            require(!support.tooltip().contains(DiamondFireServerCompatibility.SERVER_VERSION)
                && !support.tooltip().toLowerCase(java.util.Locale.ROOT).contains("legacy"),
                "Matching components must not show versions or legacy actions: " + entry.getKey());
        }
    }

    private static void assertBadge(String id, boolean persistent, List<String> fields, DiamondFireComponentSupport.Level level, String badge) {
        var support = DiamondFireComponentSupport.forComponent(id, persistent, fields);
        require(support.level() == level && support.badge().equals(badge), "Wrong DiamondFire badge for " + id + ": " + support);
    }

    private static void verifyRuntimeActions(JsonObject fixture, Map<String, JsonObject> fullActions) {
        JsonObject runtime = DiamondFireCompatibilityData.read("actiondump.json");
        require(runtime.get("provenance").equals(fixture.get("provenance")), "Runtime and research snapshot provenance differ");
        Set<String> currentKeys = new HashSet<>();
        for (var entry : fullActions.entrySet()) {
            if (entry.getValue().has("legacyReplacement")) {
                require(!DiamondFireCompatibilityData.ACTIONS.containsKey(entry.getKey()),
                    "Legacy action leaked into runtime data: " + entry.getKey());
                continue;
            }
            currentKeys.add(entry.getKey());
            String display = entry.getValue().getAsJsonObject("icon").get("name").getAsString()
                .replaceAll("<[^<>]+>|(?:&|§)[0-9A-FK-ORa-fk-or]", "");
            require(display.equals(DiamondFireCompatibilityData.ACTIONS.get(entry.getKey())), "Runtime display-name drift: " + entry.getKey());
        }
        require(fullActions.containsKey("SET VARIABLE/ SetItemFlags ") && fullActions.containsKey("SET VARIABLE/SetItemFlags"),
            "Significant action-ID whitespace was lost");
        require(currentKeys.equals(DiamondFireCompatibilityData.ACTIONS.keySet()),
            "Runtime must contain exactly the current action subset");
        require(currentKeys.contains("SET VARIABLE/ SetItemName "), "Significant whitespace is not a legacy flag");
    }

    private static Map<String, JsonObject> actions(JsonObject fixture) {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        for (JsonElement element : fixture.getAsJsonArray("actions")) {
            JsonObject action = element.getAsJsonObject();
            String key = DiamondFireCompatibilityData.actionKey(action.get("codeblockName").getAsString(), action.get("name").getAsString());
            require(result.putIfAbsent(key, action) == null, "Duplicate fixture action: " + key);
        }
        return result;
    }

    private static void verifyEvidence(JsonObject evidence, Map<String, JsonObject> actions) {
        require(evidence.get("schema_version").getAsInt() == 1, "Unknown evidence schema");
        for (JsonElement element : evidence.getAsJsonArray("synonyms")) {
            JsonObject synonym = element.getAsJsonObject();
            require(DiamondFireCompatibilityData.SERVER_COMPONENTS.containsKey(synonym.get("component").getAsString()), "Synonym references an absent component");
            require(actions.containsKey(sourceKey(synonym)), "Synonym references an absent action");
            require(!synonym.get("vanilla").getAsString().isBlank() && !synonym.getAsJsonArray("diamondfire").isEmpty(), "Incomplete terminology relationship");
        }
        Map<String, Set<String>> reviewedFields = new LinkedHashMap<>();
        for (JsonElement element : evidence.getAsJsonArray("components")) {
            JsonObject component = element.getAsJsonObject();
            String id = component.get("id").getAsString();
            var server = DiamondFireCompatibilityData.SERVER_COMPONENTS.get(id);
            var policy = DiamondFireCompatibilityData.POLICIES.get(id);
            require(server != null && !reviewedFields.containsKey(id) && policy != null, "Absent or duplicate evidence/policy component: " + id);
            String badge = component.get("badge").getAsString();
            require(Set.of("DF+", "DFP", "DF?").contains(badge), "Unknown evidence badge: " + badge);
            require(badge.equals(policy.badge()), "Evidence/policy classification mismatch: " + id);
            require((component.has("limitation") ? component.get("limitation").getAsString() : "").equals(policy.limitation()),
                "Evidence/policy limitation mismatch: " + id);
            if (component.has("action_order")) {
                require(component.getAsJsonArray("action_order").asList().stream().map(JsonElement::getAsString).toList()
                    .equals(policy.actions()), "Curated component action order changed: " + id);
            }
            require(badge.equals(DiamondFireComponentSupport.forComponent(id, server.persistent(), server.discoveredCodecFields()).badge()), "Evidence/UI badge mismatch: " + id);
            Set<String> fields = new HashSet<>();
            Set<String> references = new HashSet<>();
            for (JsonElement fieldElement : component.getAsJsonArray("fields")) {
                JsonObject field = fieldElement.getAsJsonObject();
                String path = field.get("path").getAsString();
                require(!path.isBlank() && fields.add(path), "Blank or duplicate evidence field: " + id);
                if (field.getAsJsonArray("sources").isEmpty()) {
                    require(!badge.equals("DF+") && field.has("uncertainty")
                        && !field.get("uncertainty").getAsString().isBlank(), "Unexplained field gap: " + id + "/" + path);
                }
                boolean exposed = false;
                for (JsonElement sourceElement : field.getAsJsonArray("sources")) {
                    JsonObject source = sourceElement.getAsJsonObject();
                    String operation = source.get("operation").getAsString();
                    require(OPERATIONS.contains(operation), "Unknown field operation: " + operation);
                    require(!Set.of("uncertain", "approximate").contains(operation)
                        || (source.has("restriction") && !source.get("restriction").getAsString().isBlank()), "Uncertainty needs an explicit limitation");
                    verifySource(source, actions);
                    if (!actions.get(sourceKey(source)).has("legacyReplacement")) references.add(sourceKey(source));
                    if (Set.of("read", "write", "clear").contains(operation)
                        && !actions.get(sourceKey(source)).has("legacyReplacement")) exposed = true;
                }
                require(!badge.equals("DF+") || exposed, "DF+ field has only legacy/uncertain/approximate evidence: " + id + "/" + path);
            }
            require(!fields.isEmpty() && references.equals(Set.copyOf(policy.actions())), "Evidence/policy action references differ: " + id);
            reviewedFields.put(id, fields);
            // Only these reviewed object codecs have discovery keys known to be root keys.
            // A list's entry keys or an inline scalar alternative must not imply missing root coverage.
            if (badge.equals("DF+") && Set.of("minecraft:custom_model_data", "minecraft:tooltip_display",
                "minecraft:food", "minecraft:weapon").contains(id)) {
                require(fields.equals(Set.copyOf(server.discoveredCodecFields())), "Complete object coverage omitted a captured field: " + id);
            }
            if (!component.has("group_expansions")) continue;
            for (JsonElement groupElement : component.getAsJsonArray("group_expansions")) {
                JsonObject group = groupElement.getAsJsonObject();
                require(fields.contains(group.get("field").getAsString()), "Grouped action references an absent field");
                for (JsonElement memberElement : group.getAsJsonArray("members")) {
                    JsonObject member = memberElement.getAsJsonObject().deepCopy();
                    member.add("action", group.get("action"));
                    member.add("codeblock", group.get("codeblock"));
                    verifySource(member, actions);
                    require(!member.getAsJsonArray("components").isEmpty() || member.has("uncertainty"), "Unexpanded group must stay uncertain");
                    for (JsonElement target : member.getAsJsonArray("components")) {
                        require(DiamondFireCompatibilityData.SERVER_COMPONENTS.containsKey(target.getAsString()), "Grouped action references an absent component");
                    }
                }
            }
        }
        require(reviewedFields.keySet().equals(DiamondFireCompatibilityData.POLICIES.keySet()), "Runtime policy lacks field evidence");
        for (JsonElement groupElement : evidence.getAsJsonArray("shared_actions")) {
            JsonObject group = groupElement.getAsJsonObject();
            require(!group.getAsJsonArray("members").isEmpty(), "Shared action needs concrete field targets");
            for (JsonElement memberElement : group.getAsJsonArray("members")) {
                JsonObject member = memberElement.getAsJsonObject().deepCopy();
                member.add("action", group.get("action"));
                member.add("codeblock", group.get("codeblock"));
                verifySource(member, actions);
                String id = member.get("component").getAsString();
                require(reviewedFields.containsKey(id) && reviewedFields.get(id).contains(member.get("field").getAsString()),
                    "Shared action references an absent reviewed field: " + id);
                require(DiamondFireCompatibilityData.POLICIES.get(id).actions().contains(sourceKey(member)),
                    "Shared action is absent from the target policy: " + id);
            }
        }
    }

    private static void verifySource(JsonObject source, Map<String, JsonObject> actions) {
        JsonElement value = actions.get(sourceKey(source));
        require(value != null, "Evidence references an absent action: " + sourceKey(source));
        require(source.get("path").getAsString().startsWith("/"), "Evidence must use an absolute JSON pointer");
        require(source.get("expected").isJsonPrimitive(), "Evidence must identify a concrete scalar fact");
        for (String token : source.get("path").getAsString().substring(1).split("/")) {
            String key = token.replace("~1", "/").replace("~0", "~");
            value = value.isJsonArray() ? value.getAsJsonArray().get(Integer.parseInt(key)) : value.getAsJsonObject().get(key);
            require(value != null, "Missing evidence path: " + source);
        }
        require(value.equals(source.get("expected")), "Evidence no longer matches the full action record: " + source);
    }

    private static String sourceKey(JsonObject source) {
        return DiamondFireCompatibilityData.actionKey(source.get("codeblock").getAsString(), source.get("action").getAsString());
    }

    private static void verifyRejectedMetadata(JsonObject evidence, Map<String, JsonObject> actions) {
        JsonObject server = DiamondFireCompatibilityData.read("server-registry.json");
        server.getAsJsonArray("components").add(server.getAsJsonArray("components").get(0).deepCopy());
        expectRejected(() -> DiamondFireCompatibilityData.parseServerRegistry(server), "duplicate component ID");
        JsonObject wrongBaseline = DiamondFireCompatibilityData.read("server-registry.json");
        wrongBaseline.addProperty("minecraft_version", "26.3");
        expectRejected(() -> DiamondFireCompatibilityData.parseServerRegistry(wrongBaseline), "client version substituted for server baseline");
        JsonObject duplicate = DiamondFireCompatibilityData.read("actiondump.json");
        duplicate.getAsJsonArray("actions").add(duplicate.getAsJsonArray("actions").get(0).deepCopy());
        expectRejected(() -> DiamondFireCompatibilityData.parseActions(duplicate), "duplicate action ID");
        JsonObject noProvenance = DiamondFireCompatibilityData.read("actiondump.json");
        noProvenance.getAsJsonObject("provenance").addProperty("commit", "");
        expectRejected(() -> DiamondFireCompatibilityData.parseActions(noProvenance), "missing df-info provenance");
        JsonObject noPatch = DiamondFireCompatibilityData.read("actiondump.json");
        noPatch.getAsJsonObject("provenance").addProperty("patch", "");
        expectRejected(() -> DiamondFireCompatibilityData.parseActions(noPatch), "missing current patch");
        JsonObject badBadge = evidence.deepCopy();
        badBadge.getAsJsonArray("components").get(0).getAsJsonObject().addProperty("badge", "DF-new");
        expectRejected(() -> verifyEvidence(badBadge, actions), "unknown badge category");
        JsonObject missingAction = evidence.deepCopy();
        missingAction.getAsJsonArray("components").get(0).getAsJsonObject().getAsJsonArray("fields").get(0)
            .getAsJsonObject().getAsJsonArray("sources").get(0).getAsJsonObject().addProperty("action", "MissingAction");
        expectRejected(() -> verifyEvidence(missingAction, actions), "absent generated action reference");
        JsonObject duplicatePolicy = DiamondFireCompatibilityData.read("component-policy.json");
        duplicatePolicy.getAsJsonArray("components").add(duplicatePolicy.getAsJsonArray("components").get(0).deepCopy());
        expectRejected(() -> DiamondFireCompatibilityData.parsePolicies(duplicatePolicy), "duplicate policy ID");
        JsonObject unknownBadge = DiamondFireCompatibilityData.read("component-policy.json");
        unknownBadge.getAsJsonArray("components").get(0).getAsJsonObject().addProperty("badge", "DF-new");
        expectRejected(() -> DiamondFireCompatibilityData.parsePolicies(unknownBadge), "unknown runtime badge");
        JsonObject noPolicySource = DiamondFireCompatibilityData.read("component-policy.json");
        noPolicySource.addProperty("evidence_commit", "0".repeat(40));
        expectRejected(() -> DiamondFireCompatibilityData.parsePolicies(noPolicySource), "policy provenance drift");
        JsonObject absentReference = DiamondFireCompatibilityData.read("component-policy.json");
        absentReference.getAsJsonArray("components").get(0).getAsJsonObject().getAsJsonArray("actions").add("SET VARIABLE/MissingAction");
        expectRejected(() -> DiamondFireCompatibilityData.parsePolicies(absentReference), "missing runtime action reference");
        JsonObject absentReplacement = DiamondFireCompatibilityData.read("actiondump.json");
        absentReplacement.getAsJsonArray("actions").get(0).getAsJsonObject().addProperty("legacy_replacement", "MissingAction");
        expectRejected(() -> DiamondFireCompatibilityData.parseActions(absentReplacement), "legacy runtime metadata");
        JsonObject longRuntimeDetail = DiamondFireCompatibilityData.read("component-policy.json");
        longRuntimeDetail.getAsJsonArray("components").get(0).getAsJsonObject().addProperty("detail", "Research rationale");
        expectRejected(() -> DiamondFireCompatibilityData.parsePolicies(longRuntimeDetail), "research text in runtime hints");
        JsonObject missingLimitation = DiamondFireCompatibilityData.read("component-policy.json");
        missingLimitation.getAsJsonArray("components").get(0).getAsJsonObject().remove("limitation");
        expectRejected(() -> DiamondFireCompatibilityData.parsePolicies(missingLimitation), "partial badge without a limitation");
        JsonObject longLimitation = DiamondFireCompatibilityData.read("component-policy.json");
        longLimitation.getAsJsonArray("components").get(0).getAsJsonObject().addProperty("limitation", "x".repeat(161));
        expectRejected(() -> DiamondFireCompatibilityData.parsePolicies(longLimitation), "research paragraph as a limitation");
        JsonObject changedFact = evidence.deepCopy();
        changedFact.getAsJsonArray("components").get(0).getAsJsonObject().getAsJsonArray("fields").get(0)
            .getAsJsonObject().getAsJsonArray("sources").get(0).getAsJsonObject().addProperty("expected", "NotEnable");
        expectRejected(() -> verifyEvidence(changedFact, actions), "field fact drift");
        JsonObject unreviewedGap = evidence.deepCopy();
        unreviewedGap.getAsJsonArray("components").get(0).getAsJsonObject().getAsJsonArray("fields").get(0)
            .getAsJsonObject().getAsJsonArray("sources").asList().clear();
        expectRejected(() -> verifyEvidence(unreviewedGap, actions), "DF+ field without evidence");
        JsonObject wrongSharedTarget = evidence.deepCopy();
        wrongSharedTarget.getAsJsonArray("shared_actions").get(0).getAsJsonObject().getAsJsonArray("members").get(0)
            .getAsJsonObject().addProperty("component", "minecraft:custom_name");
        expectRejected(() -> verifyEvidence(wrongSharedTarget, actions), "incorrect shared-action expansion");
    }

    private static void expectRejected(Runnable operation, String problem) {
        try {
            operation.run();
        } catch (IllegalArgumentException | AssertionError expected) {
            return;
        }
        throw new AssertionError("Accepted " + problem);
    }

    private static JsonObject readFixture(String name) throws Exception {
        try (InputStream stream = DiamondFireCompatibilityCheck.class.getResourceAsStream("/diamondfire/" + name)) {
            require(stream != null, "Missing local fixture: " + name);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void require(boolean condition, String problem) {
        if (!condition) throw new AssertionError(problem);
    }
}

package dev.nineofgaming.itemlab.editor;

import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import dev.nineofgaming.itemlab.editor.state.ItemEditHistory;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

/**
 * Minecraft-backed global-save transaction fixtures shared by every supported version.
 */
public final class PendingItemStackTransactionCheck {
    private PendingItemStackTransactionCheck() {
    }

    public static void main(String[] arguments) {
        bootstrap();
        verifyDistantComponentsComposeOnce();
        verifyItemTypeChangeComposesWithComponents();
        verifyFirstMiddleAndLastFailuresAreAtomic();
        verifyComponentAndWholeStackConflictsRunNoPreviews();
        verifyCorruptComponentFailsBeforeCommit();
        verifyRetainedDraftPolicyForStaleTarget();
    }

    private static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registries = VanillaRegistries.createLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries)
            .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
    }

    private static void verifyDistantComponentsComposeOnce() {
        ItemStack source = new ItemStack(Items.STONE);
        AtomicBoolean countDirty = new AtomicBoolean(true);
        AtomicBoolean nameDirty = new AtomicBoolean(true);
        AtomicBoolean loreDirty = new AtomicBoolean(true);
        AtomicBoolean dataDirty = new AtomicBoolean(true);
        CompoundTag customTag = new CompoundTag();
        customTag.putString("fixture", "combined");
        CustomData expectedData = CustomData.of(customTag);

        List<PendingEditTransaction.Participant<ItemStack>> participants = List.of(
            participant("count", "$count", countDirty, stack -> {
                stack.setCount(99);
                return stack;
            }),
            participant("name", "minecraft:custom_name", nameDirty, stack -> {
                stack.set(DataComponents.CUSTOM_NAME, Component.literal("Combined name"));
                return stack;
            }),
            participant("lore", "minecraft:lore", loreDirty, stack -> {
                stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Combined lore"))));
                return stack;
            }),
            participant("data", "minecraft:custom_data", dataDirty, stack -> {
                stack.set(DataComponents.CUSTOM_DATA, expectedData);
                return stack;
            })
        );

        PendingEditTransaction.Result<ItemStack> prepared = prepare(source, participants);
        if (!prepared.succeeded()) throw new AssertionError("valid components failed: " + prepared.failure());
        ItemStack candidate = prepared.candidate();
        if (candidate.getCount() != 99
            || !"Combined name".equals(candidate.getHoverName().getString())
            || candidate.get(DataComponents.LORE) == null
            || !"Combined lore".equals(candidate.get(DataComponents.LORE).lines().getFirst().getString())
            || !expectedData.equals(candidate.get(DataComponents.CUSTOM_DATA))) {
            throw new AssertionError("the combined candidate lost a pending component: " + candidate);
        }
        assertUnchangedStone(source, "successful preflight mutated the source");

        ItemEditHistory history = new ItemEditHistory();
        ItemStack committed = commitOnce(
            source,
            prepared,
            history,
            countDirty,
            nameDirty,
            loreDirty,
            dataDirty
        );
        if (!history.canUndo()) throw new AssertionError("combined commit created no history step");
        if (countDirty.get() || nameDirty.get() || loreDirty.get() || dataDirty.get()) {
            throw new AssertionError("a committed participant remained dirty");
        }

        PendingEditTransaction.Result<ItemStack> repeated = prepare(committed, participants);
        if (!repeated.succeeded() || !repeated.included().isEmpty()) {
            throw new AssertionError("repeated save was not idempotent: " + repeated);
        }
        commitOnce(committed, repeated, history, countDirty, nameDirty, loreDirty, dataDirty);
        ItemStack restored = history.undo(null, committed, List.of());
        if (restored == null || !ItemStack.matches(restored, source) || history.canUndo()) {
            throw new AssertionError("combined/idempotent saves did not produce exactly one history step");
        }
    }

    private static void verifyItemTypeChangeComposesWithComponents() {
        ItemStack source = new ItemStack(Items.STONE);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("Preserved name"));
        ItemStack original = source.copy();
        AtomicBoolean itemDirty = new AtomicBoolean(true);
        AtomicBoolean loreDirty = new AtomicBoolean(true);
        List<PendingEditTransaction.Participant<ItemStack>> participants = List.of(
            participant("item", "$item", itemDirty, stack -> {
                ItemStack changed = new ItemStack(Items.DIAMOND_SWORD, stack.getCount());
                changed.applyComponents(stack.getComponentsPatch());
                return changed;
            }),
            participant("lore", "minecraft:lore", loreDirty, stack -> {
                stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("After type change"))));
                return stack;
            })
        );
        PendingEditTransaction.Result<ItemStack> prepared = prepare(source, participants);
        if (!prepared.succeeded()
            || prepared.candidate().getItem() != Items.DIAMOND_SWORD
            || !"Preserved name".equals(prepared.candidate().getHoverName().getString())
            || prepared.candidate().get(DataComponents.LORE) == null) {
            throw new AssertionError("item-type composition failed: " + prepared);
        }
        if (!ItemStack.matches(source, original)) {
            throw new AssertionError("item-type preflight mutated its source");
        }
    }

    private static void verifyFirstMiddleAndLastFailuresAreAtomic() {
        for (int failureIndex = 0; failureIndex < 3; failureIndex++) {
            int selectedFailure = failureIndex;
            ItemStack source = new ItemStack(Items.STONE);
            ItemStack target = source.copy();
            ItemEditHistory history = new ItemEditHistory();
            AtomicBoolean[] dirty = {
                new AtomicBoolean(true),
                new AtomicBoolean(true),
                new AtomicBoolean(true)
            };
            List<PendingEditTransaction.Participant<ItemStack>> participants = new ArrayList<>();
            for (int index = 0; index < 3; index++) {
                int participantIndex = index;
                participants.add(participant(
                    "participant-" + index,
                    "owner-" + index,
                    dirty[index],
                    stack -> {
                        if (participantIndex == selectedFailure) stack.setCount(100);
                        else stack.set(DataComponents.CUSTOM_NAME, Component.literal("valid-" + participantIndex));
                        return stack;
                    }
                ));
            }
            PendingEditTransaction.Result<ItemStack> prepared = prepare(source, participants);
            if (prepared.succeeded()
                || prepared.failure().kind() != PendingEditTransaction.FailureKind.INVALID
                || !prepared.failure().participantId().equals("participant-" + failureIndex)) {
                throw new AssertionError("wrong failure position result: " + prepared);
            }
            assertUnchangedStone(source, "failed preflight mutated the draft");
            assertUnchangedStone(target, "failed preflight mutated the target");
            if (history.canUndo()) throw new AssertionError("failed preflight wrote history");
            for (AtomicBoolean state : dirty) {
                if (!state.get()) throw new AssertionError("failed preflight advanced a baseline");
            }
        }
    }

    private static void verifyComponentAndWholeStackConflictsRunNoPreviews() {
        ItemStack source = new ItemStack(Items.STONE);
        AtomicInteger previews = new AtomicInteger();
        AtomicBoolean firstDirty = new AtomicBoolean(true);
        AtomicBoolean secondDirty = new AtomicBoolean(true);
        PendingEditTransaction.Result<ItemStack> componentConflict = prepare(source, List.of(
            participant("data", "minecraft:custom_data", firstDirty, stack -> {
                previews.incrementAndGet();
                return stack;
            }),
            participant("diamondfire", "minecraft:custom_data", secondDirty, stack -> {
                previews.incrementAndGet();
                return stack;
            })
        ));
        assertConflictBeforePreview(componentConflict, previews, "component overlap");

        PendingEditTransaction.Result<ItemStack> rawConflict = prepare(source, List.of(
            participant("name", "minecraft:custom_name", firstDirty, stack -> {
                previews.incrementAndGet();
                return stack;
            }),
            participant("raw", PendingEditTransaction.WHOLE_VALUE, secondDirty, stack -> {
                previews.incrementAndGet();
                return new ItemStack(Items.DIAMOND);
            })
        ));
        assertConflictBeforePreview(rawConflict, previews, "whole-stack overlap");
        assertUnchangedStone(source, "conflict preflight mutated the source");
    }

    private static void verifyCorruptComponentFailsBeforeCommit() {
        ItemStack source = new ItemStack(Items.STONE);
        AtomicBoolean dirty = new AtomicBoolean(true);
        PendingEditTransaction.Result<ItemStack> prepared = prepare(source, List.of(participant(
            "codec",
            "minecraft:max_stack_size",
            dirty,
            stack -> {
                ItemStackCodecs.setComponentValue(stack, DataComponents.MAX_STACK_SIZE, "not-an-integer");
                return stack;
            }
        )));
        if (prepared.succeeded() || prepared.failure().kind() != PendingEditTransaction.FailureKind.INVALID) {
            throw new AssertionError("corrupt component reached commit: " + prepared);
        }
        assertUnchangedStone(source, "codec failure mutated the source");
        if (!dirty.get()) throw new AssertionError("codec failure advanced its baseline");
    }

    private static void verifyRetainedDraftPolicyForStaleTarget() {
        ItemStack source = new ItemStack(Items.STONE);
        ItemStack externalTarget = source.copy();
        AtomicBoolean dirty = new AtomicBoolean(true);
        List<PendingEditTransaction.Participant<ItemStack>> participants = List.of(participant(
            "name",
            "minecraft:custom_name",
            dirty,
            stack -> {
                stack.set(DataComponents.CUSTOM_NAME, Component.literal("Retained editor draft"));
                return stack;
            }
        ));
        PendingEditTransaction.Result<ItemStack> prepared = prepare(source, participants);
        ItemEditHistory history = new ItemEditHistory();
        ItemStack editorDraft = commitOnce(source, prepared, history, dirty);
        try {
            throw new IllegalStateException("stale target");
        } catch (RuntimeException expected) {
            // The external action fails after the editor transaction has committed.
        }
        if (!"Retained editor draft".equals(editorDraft.getHoverName().getString())
            || !ItemStack.matches(externalTarget, source)
            || !history.canUndo()
            || dirty.get()) {
            throw new AssertionError("stale-target retained-draft policy changed");
        }
    }

    private static PendingEditTransaction.Result<ItemStack> prepare(
        ItemStack source,
        List<PendingEditTransaction.Participant<ItemStack>> participants
    ) {
        return PendingEditTransaction.prepare(
            source,
            ItemStack::copy,
            ItemStackCodecs::validateStack,
            participants
        );
    }

    private static PendingEditTransaction.Participant<ItemStack> participant(
        String id,
        String owner,
        AtomicBoolean dirty,
        UnaryOperator<ItemStack> preview
    ) {
        return new PendingEditTransaction.Participant<>(
            id,
            id,
            id,
            null,
            dirty::get,
            Set.of(owner),
            preview
        );
    }

    private static ItemStack commitOnce(
        ItemStack current,
        PendingEditTransaction.Result<ItemStack> prepared,
        ItemEditHistory history,
        AtomicBoolean... includedDirtyStates
    ) {
        if (!prepared.succeeded() || prepared.included().isEmpty()) return current.copy();
        history.begin(null, current, List.of());
        history.record(null, current, prepared.candidate(), List.of());
        history.end(null, prepared.candidate());
        // Successful ItemEditor#setDraft synchronization advances all included baselines.
        for (AtomicBoolean dirty : includedDirtyStates) dirty.set(false);
        return prepared.candidate().copy();
    }

    private static void assertConflictBeforePreview(
        PendingEditTransaction.Result<ItemStack> result,
        AtomicInteger previews,
        String label
    ) {
        if (result.succeeded()
            || result.failure().kind() != PendingEditTransaction.FailureKind.CONFLICT
            || previews.get() != 0) {
            throw new AssertionError(label + " was not rejected before preview: " + result);
        }
    }

    private static void assertUnchangedStone(ItemStack stack, String message) {
        if (!ItemStack.matches(stack, new ItemStack(Items.STONE))) {
            throw new AssertionError(message + ": " + stack);
        }
    }
}

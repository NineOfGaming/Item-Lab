package dev.nineofgaming.itemlab.editor.container;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable stacks available to copy into a contained-item editor session.
 */
public record SourceInventorySnapshot(List<SourceGroup> groups) {
    public static final SourceInventorySnapshot EMPTY = new SourceInventorySnapshot(List.of());

    public SourceInventorySnapshot {
        groups = List.copyOf(groups);
    }

    public static SourceInventorySnapshot capture(Minecraft client, Screen screen) {
        if (client.player == null) return EMPTY;

        Inventory inventory = client.player.getInventory();
        List<SourceGroup> groups = new ArrayList<>();
        if (screen instanceof AbstractContainerScreen<?> containerScreen
            && containerScreen.getMenu() != client.player.inventoryMenu) {
            boolean creativeCatalog = screen instanceof CreativeModeInventoryScreen;
            List<SourceSlot> external = new ArrayList<>();
            for (Slot slot : containerScreen.getMenu().slots) {
                if (!slot.isActive() || slot.container == inventory) continue;
                if (creativeCatalog && (slot.x < 0 || slot.y < 0 || slot.getItem().isEmpty())) continue;
                external.add(new SourceSlot(
                    "menu_" + slot.index,
                    slot.getItem().copy(),
                    slot.x,
                    slot.y,
                    slot.getContainerSlot(),
                    ""
                ));
            }
            if (!external.isEmpty()) {
                String label = creativeCatalog ? "Creative catalog" : screen.getTitle().getString();
                groups.add(SourceGroup.normalized(label, external));
            }
        }

        groups.add(playerGroup("Main inventory", inventory, 9, 36, 9));
        groups.add(playerGroup("Hotbar", inventory, 0, 9, 9));
        if (inventory.getContainerSize() > 36) {
            groups.add(equipmentGroup(inventory));
        }
        return new SourceInventorySnapshot(groups);
    }

    private static SourceGroup equipmentGroup(Inventory inventory) {
        List<SourceSlot> slots = new ArrayList<>();
        addEquipmentSlot(slots, inventory, EquipmentSlot.HEAD.getIndex(Inventory.INVENTORY_SIZE), "Head");
        addEquipmentSlot(slots, inventory, Inventory.SLOT_OFFHAND, "Offhand");
        addEquipmentSlot(slots, inventory, EquipmentSlot.CHEST.getIndex(Inventory.INVENTORY_SIZE), "Chest");
        addEquipmentSlot(slots, inventory, Inventory.SLOT_BODY_ARMOR, "Body armor");
        addEquipmentSlot(slots, inventory, EquipmentSlot.LEGS.getIndex(Inventory.INVENTORY_SIZE), "Legs");
        addEquipmentSlot(slots, inventory, Inventory.SLOT_SADDLE, "Saddle");
        addEquipmentSlot(slots, inventory, EquipmentSlot.FEET.getIndex(Inventory.INVENTORY_SIZE), "Feet");
        return SourceGroup.normalized("Equipment", slots);
    }

    private static void addEquipmentSlot(
        List<SourceSlot> slots,
        Inventory inventory,
        int inventorySlot,
        String displayLabel
    ) {
        if (inventorySlot >= inventory.getContainerSize()) return;
        int displayIndex = slots.size();
        slots.add(new SourceSlot(
            "player_" + inventorySlot,
            inventory.getItem(inventorySlot).copy(),
            displayIndex % 2 * 18,
            displayIndex / 2 * 18,
            inventorySlot,
            displayLabel
        ));
    }

    private static SourceGroup playerGroup(String label, Inventory inventory, int start, int end, int columns) {
        List<SourceSlot> slots = new ArrayList<>();
        int boundedEnd = Math.min(end, inventory.getContainerSize());
        for (int inventorySlot = start; inventorySlot < boundedEnd; inventorySlot++) {
            int local = inventorySlot - start;
            slots.add(new SourceSlot(
                "player_" + inventorySlot,
                inventory.getItem(inventorySlot).copy(),
                local % columns * 18,
                local / columns * 18,
                inventorySlot,
                ""
            ));
        }
        return SourceGroup.normalized(label, slots);
    }

    public record SourceGroup(String label, List<SourceSlot> slots, int width, int height) {
        public SourceGroup {
            slots = List.copyOf(slots);
        }

        private static SourceGroup normalized(String label, List<SourceSlot> source) {
            if (source.isEmpty()) return new SourceGroup(label, List.of(), 18, 18);
            int minX = source.stream().mapToInt(SourceSlot::x).min().orElse(0);
            int minY = source.stream().mapToInt(SourceSlot::y).min().orElse(0);
            List<SourceSlot> normalized = source.stream()
                .map(slot -> new SourceSlot(
                    slot.id(), slot.stack(), slot.x() - minX, slot.y() - minY,
                    slot.sourceIndex(), slot.displayLabel()
                ))
                .toList();
            int width = normalized.stream().mapToInt(slot -> slot.x() + 18).max().orElse(18);
            int height = normalized.stream().mapToInt(slot -> slot.y() + 18).max().orElse(18);
            return new SourceGroup(label, normalized, width, height);
        }
    }

    public record SourceSlot(
        String id,
        ItemStack stack,
        int x,
        int y,
        int sourceIndex,
        String displayLabel
    ) {
        public SourceSlot {
            stack = stack.copy();
            displayLabel = displayLabel == null ? "" : displayLabel;
        }

        @Override
        public ItemStack stack() {
            return stack.copy();
        }
    }
}

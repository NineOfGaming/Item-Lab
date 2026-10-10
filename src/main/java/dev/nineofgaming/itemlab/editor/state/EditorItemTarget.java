package dev.nineofgaming.itemlab.editor.state;

import dev.nineofgaming.itemlab.mixin.client.AbstractContainerScreenAccessor;
import dev.nineofgaming.itemlab.mixin.client.CreativeSlotWrapperAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Objects;

/**
 * The stack used to open the editor and the slot receiving Save Item.
 */
public final class EditorItemTarget {
    public enum Kind {
        HELD_ITEM,
        PLAYER_INVENTORY,
        CONTAINER,
        HOVERED_COPY,
        NEW_ITEM
    }

    private final ItemStack openingStack;
    private final int inventorySlot;
    private final Kind kind;
    private final AbstractContainerMenu containerMenu;
    private final int menuSlot;
    private final int containerSlot;

    private EditorItemTarget(ItemStack openingStack, int inventorySlot, Kind kind) {
        this(openingStack, inventorySlot, kind, null, -1, -1);
    }

    private EditorItemTarget(
        ItemStack openingStack,
        int inventorySlot,
        Kind kind,
        AbstractContainerMenu containerMenu,
        int menuSlot,
        int containerSlot
    ) {
        this.openingStack = openingStack.copy();
        this.inventorySlot = inventorySlot;
        this.kind = kind;
        this.containerMenu = containerMenu;
        this.menuSlot = menuSlot;
        this.containerSlot = containerSlot;
    }

    public static EditorItemTarget contextual(Minecraft client, Screen screen) {
        Inventory inventory = Objects.requireNonNull(client.player).getInventory();
        int selectedSlot = inventory.getSelectedSlot();
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            Slot hovered = ((AbstractContainerScreenAccessor) containerScreen).itemlab$getHoveredSlot();
            if (hovered != null && hovered.hasItem()) {
                boolean creativeScreen = screen instanceof CreativeModeInventoryScreen;
                if (hovered instanceof CreativeSlotWrapperAccessor wrapper) {
                    hovered = wrapper.itemlab$getTargetSlot();
                }
                int hoveredInventorySlot = hovered.getContainerSlot();
                if (hovered.container == inventory && creativeMenuSlot(hoveredInventorySlot) >= 0) {
                    return new EditorItemTarget(
                        hovered.getItem(),
                        hoveredInventorySlot,
                        hoveredInventorySlot == selectedSlot ? Kind.HELD_ITEM : Kind.PLAYER_INVENTORY
                    );
                }
                if (creativeScreen) {
                    return new EditorItemTarget(hovered.getItem(), selectedSlot, Kind.HOVERED_COPY);
                }
                AbstractContainerMenu menu = containerScreen.getMenu();
                if (menu == client.player.containerMenu
                    && hovered.index >= 0
                    && hovered.index < menu.slots.size()
                    && menu.getSlot(hovered.index) == hovered
                    && hovered.mayPickup(client.player)
                    && hovered.mayPlace(hovered.getItem())) {
                    return new EditorItemTarget(
                        hovered.getItem(),
                        selectedSlot,
                        Kind.CONTAINER,
                        menu,
                        hovered.index,
                        hoveredInventorySlot
                    );
                }
                return new EditorItemTarget(hovered.getItem(), selectedSlot, Kind.HOVERED_COPY);
            }
        }

        ItemStack held = inventory.getSelectedItem();
        if (held.isEmpty()) {
            return new EditorItemTarget(Items.STONE.getDefaultInstance(), selectedSlot, Kind.NEW_ITEM);
        }
        return new EditorItemTarget(held, selectedSlot, Kind.HELD_ITEM);
    }

    public static EditorItemTarget newItem(Minecraft client) {
        return new EditorItemTarget(
            Items.STONE.getDefaultInstance(),
            Objects.requireNonNull(client.player).getInventory().getSelectedSlot(),
            Kind.NEW_ITEM
        );
    }

    public ItemStack openingStack() {
        return openingStack.copy();
    }

    public int inventorySlot() {
        return inventorySlot;
    }

    public Kind kind() {
        return kind;
    }

    public int containerSlot() {
        return containerSlot;
    }

    public void write(Minecraft client, ItemStack stack) {
        if (kind == Kind.CONTAINER) {
            writeContainerSlot(client, stack);
            return;
        }

        int menuSlot = creativeMenuSlot(inventorySlot);
        if (menuSlot < 0) {
            throw new IllegalStateException("Unsupported player inventory slot " + inventorySlot);
        }
        ItemStack localCopy = stack.copy();
        Objects.requireNonNull(client.player).getInventory().setItem(inventorySlot, localCopy);
        Objects.requireNonNull(client.gameMode).handleCreativeModeItemAdd(localCopy.copy(), menuSlot);
    }

    private void writeContainerSlot(Minecraft client, ItemStack stack) {
        if (client.player == null || client.gameMode == null) {
            throw new IllegalStateException("The player is no longer available");
        }
        if (client.player.containerMenu != containerMenu) {
            throw new IllegalStateException("The original container is no longer open");
        }
        if (menuSlot < 0 || menuSlot >= containerMenu.slots.size()) {
            throw new IllegalStateException("The original container slot is no longer available");
        }

        Slot slot = containerMenu.getSlot(menuSlot);
        if (!slot.mayPickup(client.player)) {
            throw new IllegalStateException("The original container slot cannot be changed");
        }
        if (!stack.isEmpty()) {
            if (!slot.mayPlace(stack)) {
                throw new IllegalStateException("This item is not accepted by the original container slot");
            }
        }

        Inventory inventory = client.player.getInventory();
        int hotbarSlot = inventorySlot;
        int creativeSlot = creativeMenuSlot(hotbarSlot);
        if (hotbarSlot < 0 || hotbarSlot >= Inventory.getSelectionSize() || creativeSlot < 0) {
            throw new IllegalStateException("No hotbar slot is available for the container update");
        }

        ItemStack previousHotbarStack = inventory.getItem(hotbarSlot).copy();
        ItemStack editedStack = stack.copy();
        boolean staged = false;
        try {
            inventory.setItem(hotbarSlot, editedStack);
            staged = true;
            client.gameMode.handleCreativeModeItemAdd(editedStack.copy(), creativeSlot);
            client.gameMode.handleContainerInput(
                containerMenu.containerId,
                menuSlot,
                hotbarSlot,
                ContainerInput.SWAP,
                client.player
            );
            if (!ItemStack.matches(slot.getItem(), editedStack)) {
                throw new IllegalStateException("The original container slot rejected the edited item");
            }
        } finally {
            if (staged) {
                inventory.setItem(hotbarSlot, previousHotbarStack);
                client.gameMode.handleCreativeModeItemAdd(previousHotbarStack.copy(), creativeSlot);
            }
        }
    }

    public static int creativeMenuSlot(int inventorySlot) {
        if (inventorySlot >= 36 && inventorySlot <= 39) {
            return 8 - (inventorySlot - 36);
        }
        if (inventorySlot == Inventory.SLOT_OFFHAND) {
            return 45;
        }
        if (inventorySlot >= 0 && inventorySlot < Inventory.getSelectionSize()) {
            return 36 + inventorySlot;
        }
        if (inventorySlot >= Inventory.getSelectionSize() && inventorySlot < 36) {
            return inventorySlot;
        }
        return -1;
    }
}

package dev.nineofgaming.itemlab.editor.container;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Editor-only slot geometry selected from the item that owns minecraft:container.
 */
public record ContainerPresentationProfile(
    String name,
    int width,
    int height,
    List<PresentedSlot> slots,
    int nativeCapacity,
    boolean generic
) {
    private static final Set<String> STORAGE_9_BY_3 = Set.of(
        "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "minecraft:ender_chest"
    );
    private static final Set<String> GRID_3_BY_3 = Set.of(
        "minecraft:dispenser", "minecraft:dropper", "minecraft:crafter"
    );
    private static final Set<String> FURNACES = Set.of(
        "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker"
    );

    public ContainerPresentationProfile {
        slots = List.copyOf(slots);
    }

    public static ContainerPresentationProfile resolve(ItemStack owner, int highestOccupiedSlot) {
        String id = itemId(owner);
        if (isStorageOwner(id)) {
            return rectangular("Storage", 9, 3);
        }
        if (GRID_3_BY_3.contains(id)) return rectangular("3 x 3", 3, 3);
        switch (id) {
            case "minecraft:hopper", "minecraft:hopper_minecart" -> {
                return rectangular("Hopper", 5, 1);
            }
            case "minecraft:chiseled_bookshelf" -> {
                return rectangular("Bookshelf", 3, 2);
            }
            case "minecraft:decorated_pot" -> {
                return rectangular("Decorated pot", 1, 1);
            }
        }
        if (id.endsWith("_shelf")) return rectangular("Shelf", 3, 1);
        if (FURNACES.contains(id)) return furnace();
        if (id.equals("minecraft:brewing_stand")) return brewingStand();

        int detectedCapacity = detectedCapacity(owner);
        int required = Math.max(highestOccupiedSlot + 1, detectedCapacity);
        int capacity = Math.clamp(required, 9, ContainerContentsDraft.MAX_SLOTS);
        int columns = 9;
        int rows = Math.max(1, (capacity + columns - 1) / columns);
        return rectangular("Indexed slots", columns, rows, capacity, true);
    }

    public static boolean hasKnownOwner(ItemStack owner) {
        String id = itemId(owner);
        return isStorageOwner(id)
            || GRID_3_BY_3.contains(id)
            || id.equals("minecraft:hopper")
            || id.equals("minecraft:hopper_minecart")
            || id.equals("minecraft:chiseled_bookshelf")
            || id.equals("minecraft:decorated_pot")
            || id.endsWith("_shelf")
            || FURNACES.contains(id)
            || id.equals("minecraft:brewing_stand");
    }

    public static ContainerPresentationProfile indexed(int requiredCapacity) {
        int capacity = Math.clamp(Math.max(9, requiredCapacity), 9, ContainerContentsDraft.MAX_SLOTS);
        return rectangular("Indexed slots", 9, (capacity + 8) / 9, capacity, true);
    }

    public List<Integer> overflowIndices(int highestOccupiedSlot) {
        if (highestOccupiedSlot < nativeCapacity) return List.of();
        List<Integer> indices = new ArrayList<>();
        for (int index = nativeCapacity; index <= highestOccupiedSlot; index++) indices.add(index);
        return List.copyOf(indices);
    }

    private static ContainerPresentationProfile rectangular(String name, int columns, int rows) {
        return rectangular(name, columns, rows, columns * rows, false);
    }

    private static ContainerPresentationProfile rectangular(
        String name,
        int columns,
        int rows,
        int capacity,
        boolean generic
    ) {
        List<PresentedSlot> slots = new ArrayList<>(capacity);
        for (int index = 0; index < capacity; index++) {
            slots.add(new PresentedSlot(index, index % columns, index / columns, ""));
        }
        return new ContainerPresentationProfile(name, columns, rows, slots, capacity, generic);
    }

    private static ContainerPresentationProfile furnace() {
        return new ContainerPresentationProfile(
            "Furnace",
            3,
            3,
            List.of(
                new PresentedSlot(0, 0, 0, "Input"),
                new PresentedSlot(1, 0, 2, "Fuel"),
                new PresentedSlot(2, 2, 1, "Result")
            ),
            3,
            false
        );
    }

    private static ContainerPresentationProfile brewingStand() {
        return new ContainerPresentationProfile(
            "Brewing stand",
            5,
            3,
            List.of(
                new PresentedSlot(0, 1, 2, "Bottle 1"),
                new PresentedSlot(1, 2, 2, "Bottle 2"),
                new PresentedSlot(2, 3, 2, "Bottle 3"),
                new PresentedSlot(3, 2, 0, "Ingredient"),
                new PresentedSlot(4, 0, 0, "Fuel")
            ),
            5,
            false
        );
    }

    private static boolean isStorageOwner(String id) {
        return STORAGE_9_BY_3.contains(id)
            || id.endsWith("_shulker_box")
            || id.endsWith("copper_chest")
            || id.equals("minecraft:chest_minecart")
            || id.endsWith("_chest_boat")
            || id.endsWith("_chest_raft");
    }

    private static int detectedCapacity(ItemStack owner) {
        if (!(owner.getItem() instanceof BlockItem blockItem)
            || !(blockItem.getBlock() instanceof EntityBlock entityBlock)) {
            return 0;
        }
        try {
            BlockEntity blockEntity = entityBlock.newBlockEntity(BlockPos.ZERO, blockItem.getBlock().defaultBlockState());
            return blockEntity instanceof Container container
                ? Math.clamp(container.getContainerSize(), 0, ContainerContentsDraft.MAX_SLOTS)
                : 0;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static String itemId(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.toString();
    }

    public record PresentedSlot(int index, int x, int y, String role) {
    }
}

package dev.nineofgaming.itemlab.editor.component;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Bees;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered, lossless editable state for the {@code minecraft:bees} component.
 */
public final class BeesDraft {
    private boolean present;
    private final List<Occupant> occupants = new ArrayList<>();

    public static BeesDraft fromStack(ItemStack owner) {
        BeesDraft draft = new BeesDraft();
        Bees bees = owner.get(DataComponents.BEES);
        draft.present = bees != null;
        if (bees != null) {
            for (BeehiveBlockEntity.Occupant occupant : bees.bees()) {
                Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(occupant.entityData().type());
                draft.occupants.add(new Occupant(
                    typeId.toString(),
                    occupant.entityData().copyTagWithoutId(),
                    occupant.ticksInHive(),
                    occupant.minTicksInHive()
                ));
            }
        }
        return draft;
    }

    public static Occupant normalBee() {
        BeehiveBlockEntity.Occupant occupant = BeehiveBlockEntity.Occupant.create(0);
        Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(occupant.entityData().type());
        return new Occupant(
            typeId.toString(),
            occupant.entityData().copyTagWithoutId(),
            occupant.ticksInHive(),
            occupant.minTicksInHive()
        );
    }

    public boolean present() {
        return present;
    }

    public void setPresent(boolean present) {
        this.present = present;
    }

    public int size() {
        return occupants.size();
    }

    public boolean isEmpty() {
        return occupants.isEmpty();
    }

    public Occupant occupant(int index) {
        return occupants.get(index).copy();
    }

    public void add(Occupant occupant) {
        insert(occupants.size(), occupant);
    }

    public void insert(int index, Occupant occupant) {
        if (index < 0 || index > occupants.size()) {
            throw new IllegalArgumentException("Bee occupant index is outside the list");
        }
        occupants.add(index, occupant.copy());
    }

    public void set(int index, Occupant occupant) {
        occupants.set(index, occupant.copy());
    }

    public void remove(int index) {
        occupants.remove(index);
    }

    public void move(int from, int to) {
        if (from < 0 || from >= occupants.size() || to < 0 || to >= occupants.size()) {
            throw new IllegalArgumentException("Bee occupant index is outside the list");
        }
        if (from != to) occupants.add(to, occupants.remove(from));
    }

    public void applyTo(ItemStack owner) {
        if (!present) {
            owner.remove(DataComponents.BEES);
            return;
        }
        List<BeehiveBlockEntity.Occupant> encoded = new ArrayList<>(occupants.size());
        for (Occupant occupant : occupants) {
            EntityType<?> type = resolveEntityType(occupant.entityType());
            TypedEntityData<EntityType<?>> data = TypedEntityData.of(type, occupant.entityData());
            encoded.add(new BeehiveBlockEntity.Occupant(
                data,
                occupant.ticksInHive(),
                occupant.minTicksInHive()
            ));
        }
        owner.set(DataComponents.BEES, encoded.isEmpty() ? Bees.EMPTY : new Bees(List.copyOf(encoded)));
    }

    public boolean matchesStack(ItemStack owner) {
        BeesDraft other = fromStack(owner);
        if (present != other.present || occupants.size() != other.occupants.size()) return false;
        for (int index = 0; index < occupants.size(); index++) {
            if (!occupants.get(index).equals(other.occupants.get(index))) return false;
        }
        return true;
    }

    private static EntityType<?> resolveEntityType(String source) {
        String value = source == null ? "" : source.trim();
        Identifier id = Identifier.tryParse(value);
        EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getValue(id);
        if (type == null) {
            throw new IllegalArgumentException("Invalid entity type identifier");
        }
        return type;
    }

    public record Occupant(
        String entityType,
        CompoundTag entityData,
        int ticksInHive,
        int minTicksInHive
    ) {
        public Occupant {
            entityType = entityType == null ? "" : entityType;
            entityData = entityData == null ? new CompoundTag() : entityData.copy();
        }

        @Override
        public CompoundTag entityData() {
            return entityData.copy();
        }

        public Occupant copy() {
            return new Occupant(entityType, entityData, ticksInHive, minTicksInHive);
        }
    }
}

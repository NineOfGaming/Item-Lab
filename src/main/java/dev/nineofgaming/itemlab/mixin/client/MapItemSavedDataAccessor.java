package dev.nineofgaming.itemlab.mixin.client;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

/**
 * Native map-state access needed to build a non-mutating live item preview.
 */
@Mixin(MapItemSavedData.class)
public interface MapItemSavedDataAccessor {
    @Accessor("decorations")
    Map<String, MapDecoration> itemlab$getDecorationsById();

    @Invoker("addDecoration")
    void itemlab$addDecoration(
        Holder<MapDecorationType> type,
        LevelAccessor level,
        String id,
        double x,
        double z,
        double rotation,
        Component name
    );
}

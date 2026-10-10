package dev.nineofgaming.itemlab.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nineofgaming.itemlab.ItemLab;
import dev.nineofgaming.itemlab.config.ItemLabConfig;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.mixin.client.RecipeBookComponentAccessor;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Registered controls, including the screen-aware inventory shortcut.
 */
public final class ItemLabKeyMappings {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath(ItemLab.MOD_ID, "item_lab")
    );

    private final KeyMapping editItem = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.itemlab.open_editor",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_I,
        CATEGORY
    ));
    private final KeyMapping newItem = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.itemlab.new_item",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN,
        CATEGORY
    ));

    public ItemLabKeyMappings(ItemEditor editor) {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
            ScreenKeyboardEvents.allowKeyPress(screen).register((activeScreen, event) -> {
                if (!(activeScreen instanceof AbstractContainerScreen<?>)) {
                    return true;
                }
                if (capturesTextInput(activeScreen)) {
                    return true;
                }
                if (editItem.matches(event)) {
                    editor.toggle(client);
                    return false;
                }
                if (newItem.matches(event)) {
                    editor.openNewItem(client);
                    return false;
                }
                return true;
            })
        );
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (!ItemLabConfig.get().showInventoryEditHint()
                || editItem.isUnbound()
                || !flag.isAdvanced()) {
                return;
            }
            Minecraft client = Minecraft.getInstance();
            //? if >=26.2 {
            boolean inventoryOpen = client.gui.screen() instanceof AbstractContainerScreen<?>;
            //?} else {
            /*boolean inventoryOpen = client.screen instanceof AbstractContainerScreen<?>;
             *///?}
            if (inventoryOpen && client.player != null && client.player.hasInfiniteMaterials()) {
                lines.add(Component.translatable(
                    "itemlab.tooltip.edit",
                    editItem.getTranslatedKeyMessage()
                ).withStyle(ChatFormatting.DARK_GRAY));
            }
        });
    }

    public void processClicks(ItemEditor editor, Minecraft client) {
        boolean suppress = capturesTextInput(currentScreen(client))
            || editor.isCapturingTextInput();
        while (editItem.consumeClick()) {
            if (!suppress) editor.toggle(client);
        }
        while (newItem.consumeClick()) {
            if (!suppress) editor.openNewItem(client);
        }
    }

    private static Screen currentScreen(Minecraft client) {
        //? if >=26.2 {
        return client.gui.screen();
        //?} else {
        /*return client.screen;
         *///?}
    }

    private static boolean capturesTextInput(Screen screen) {
        return screen != null && capturesTextInput((GuiEventListener) screen);
    }

    private static boolean capturesTextInput(GuiEventListener listener) {
        if (listener instanceof EditBox editBox) {
            return editBox.canConsumeInput();
        }
        if (listener instanceof MultiLineEditBox multiLineEditBox) {
            return multiLineEditBox.isFocused() && multiLineEditBox.active && multiLineEditBox.visible;
        }
        if (listener instanceof RecipeBookComponentAccessor recipeBook) {
            EditBox searchBox = recipeBook.itemlab$getSearchBox();
            if (searchBox != null && searchBox.canConsumeInput()) return true;
        }
        if (listener instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                if (capturesTextInput(child)) return true;
            }
        }
        return false;
    }
}

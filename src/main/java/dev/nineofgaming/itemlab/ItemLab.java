package dev.nineofgaming.itemlab;

import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.ui.text.EditorContentFont;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.input.ItemLabKeyMappings;
import foundry.imgui.api.ImGuiMC;
import foundry.imgui.fabric.api.event.RegisterImGuiFontsEventFabric;
import foundry.imgui.fabric.api.event.RenderImGuiEventsFabric;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ItemLab implements ClientModInitializer {
    public static final String MOD_NAME = "Item Lab";
    public static final String MOD_ID = "itemlab";
    @SuppressWarnings("unused")
    public static final String MOD_VERSION = FabricLoader.getInstance().getModContainer(MOD_ID).map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("unknown");

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    private final ItemEditor itemEditor = new ItemEditor();
    private static ItemEditor activeEditor;
    private ItemLabKeyMappings keyMappings;

    public static Logger createLogger(String source) {
        return LoggerFactory.getLogger(MOD_NAME + "/" + source);
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Item Lab");

        activeEditor = itemEditor;
        keyMappings = new ItemLabKeyMappings(itemEditor);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            keyMappings.processClicks(itemEditor, client);
            itemEditor.tick(client);
            EditorContentFont.tickMinecraftFallback();
        });
        RegisterImGuiFontsEventFabric.EVENT.register(EditorContentFont::registerFont);
        RegisterImGuiFontsEventFabric.EVENT.register(EditorIcons::registerFont);
        ImGuiMC.rebuildFonts();
        RenderImGuiEventsFabric.POST.register(itemEditor::render);
    }

    public static void renderNativePreviewsForImGui() {
        if (activeEditor != null) {
            activeEditor.renderNativePreviewsForImGui();
        }
    }

    public static boolean isEditorOpen() {
        return activeEditor != null && activeEditor.isOpen();
    }
}

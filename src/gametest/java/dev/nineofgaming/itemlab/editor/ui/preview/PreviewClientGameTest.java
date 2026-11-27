package dev.nineofgaming.itemlab.editor.ui.preview;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.nineofgaming.itemlab.ItemLab;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import foundry.imgui.impl.ImGuiMCImpl;
import foundry.imgui.impl.ImGuiWindowHandlerSDL3;
import imgui.ImGui;
import imgui.type.ImString;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLKeycode;
import org.lwjgl.sdl.SDLScancode;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryStack;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;

/** Exercises the real SDL/ImGui/GPU preview path in an isolated local world. */
public final class PreviewClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                client.options.guiScale().set(2);
                client.getWindow().setGuiScale(2);
                client.player.getInventory().setItem(0, new ItemStack(Items.STONE));
            });
            context.waitTicks(4);
            Path closed = captureFrame(context, "itemlab-editor-closed");
            context.runOnClient(client -> {
                editor().toggle(client);
                if (!editor().isOpen()) throw new AssertionError("Item Lab did not open in the test world");
            });
            context.waitTicks(10);
            context.runOnClient(client -> {
                ImGuiMCImpl.handler.start();
                try {
                    ImGui.setWindowPos("Item Lab###itemlab_editor", 50, 50);
                    ImGui.setWindowSize("Item Lab###itemlab_editor", 1200, 900);
                } finally {
                    ImGuiMCImpl.handler.stop();
                }
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                RenderTarget target = (RenderTarget) read(read(read(editor(), "nativePreviews"), "compositor"), "target");
                if (target.getColorTexture() == null || target.getColorTextureView() == null || target.hasDepth()) {
                    throw new AssertionError("Preview compositor requires a color texture/view and no depth attachment");
                }
                if (!editor().isOpen()) throw new AssertionError("Item Lab closed during rendering");
            });
            Path opened = captureFrame(context, "itemlab-editor-open");
            assertInterfaceVisible(closed, opened);

            context.runOnClient(client -> write(editor(), "focusItemId", true));
            context.waitTicks(3);
            context.runOnClient(client -> {
                ImGuiMCImpl.handler.start();
                try {
                    boolean wantsTextInput = ImGui.getIO().getWantTextInput();
                    boolean textInputActive = SDLKeyboard.SDL_TextInputActive(client.getWindow().handle());
                    if (!wantsTextInput) throw new AssertionError("Item ID field did not gain keyboard focus");
                    if (!textInputActive) throw new AssertionError("Focused ImGui field has no native SDL text input");
                } finally {
                    ImGuiMCImpl.handler.stop();
                }
            });

            pressKey(context, SDLKeycode.SDLK_A, SDLScancode.SDL_SCANCODE_A, SDLKeycode.SDL_KMOD_CTRL);
            typeText(context, "minecraft:diamond");
            assertItemId(context, "minecraft:diamond");
            typeText(context, "\u00e4\u6f22");
            assertItemId(context, "minecraft:diamond\u00e4\u6f22");
            pressKey(context, SDLKeycode.SDLK_BACKSPACE, SDLScancode.SDL_SCANCODE_BACKSPACE, 0);
            assertItemId(context, "minecraft:diamond\u00e4");

            // A vanilla field may claim input before ImGui releases its old
            // focus. Closing the overlay must not disable the new owner's input.
            context.setScreen(() -> new ChatScreen("", false));
            context.waitTicks(3);
            typeText(context, "chat\u00e4\u6f22");
            context.runOnClient(client -> {
                EditBox input = (EditBox) read(client.gui.screen(), "input");
                if (!input.getValue().equals("chat\u00e4\u6f22")) {
                    throw new AssertionError("Vanilla chat lost text input after closing the editor");
                }
            });
            context.setScreen(() -> null);
            context.waitTicks(3);
            context.runOnClient(client -> {
                if (SDLKeyboard.SDL_TextInputActive(client.getWindow().handle())) {
                    throw new AssertionError("Native text input remained enabled after both editors closed");
                }
                editor().toggle(client);
            });
            context.waitTicks(3);

            for (ItemModelPreviewMode mode : ItemModelPreviewMode.values()) {
                context.runOnClient(client -> {
                    selectTab("PRESENTATION");
                    write(read(editor(), "appearanceEditor"), "previewMode", mode);
                });
                context.waitTicks(5);
                context.runOnClient(client -> {
                    Object preview = read(read(editor(), "nativePreviews"), "itemModel");
                    if (!Boolean.TRUE.equals(read(preview, "requestedThisFrame")) || read(preview, "mode") != mode) {
                        throw new AssertionError("Model preview was not rendered: " + mode);
                    }
                });
                captureFrame(context, "itemlab-model-" + mode.name().toLowerCase(java.util.Locale.ROOT));
            }
            context.runOnClient(client -> selectTab("EQUIPMENT"));
            context.waitTicks(5);
            context.runOnClient(client -> {
                if (!Boolean.TRUE.equals(read(read(read(editor(), "nativePreviews"), "equipment"), "requestedThisFrame"))) {
                    throw new AssertionError("Equipment preview was not rendered");
                }
            });
            captureFrame(context, "itemlab-equipment");
            context.runOnClient(client -> editor().toggle(client));
            context.waitTicks(2);
            context.runOnClient(client -> editor().toggle(client));
            context.waitTicks(10);
            context.runOnClient(client -> editor().toggle(client));
        }
    }

    private static void assertItemId(ClientGameTestContext context, String expected) {
        context.runOnClient(client -> {
            String actual = ((ImString) read(editor(), "itemId")).get();
            if (!expected.equals(actual)) throw new AssertionError("Text input mismatch: expected " + expected + ", got " + actual);
        });
    }

    private static void pressKey(ClientGameTestContext context, int key, int scancode, int modifiers) {
        for (boolean down : new boolean[]{true, false}) {
            context.runOnClient(client -> {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    SDL_Event event = SDL_Event.calloc(stack);
                    event.key().type(down ? SDLEvents.SDL_EVENT_KEY_DOWN : SDLEvents.SDL_EVENT_KEY_UP)
                        .windowID(SDLVideo.SDL_GetWindowID(client.getWindow().handle()))
                        .key(key).scancode(scancode).mod((short) (down ? modifiers : 0)).down(down);
                    dispatchSdlInput(event);
                }
            });
            context.waitTicks(2);
        }
    }

    private static void typeText(ClientGameTestContext context, String text) {
        context.runOnClient(client -> {
            if (!SDLKeyboard.SDL_TextInputActive(client.getWindow().handle())) {
                throw new AssertionError("SDL text input is disabled for the focused field");
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                SDL_Event event = SDL_Event.calloc(stack);
                event.text().type(SDLEvents.SDL_EVENT_TEXT_INPUT)
                    .windowID(SDLVideo.SDL_GetWindowID(client.getWindow().handle())).text(stack.UTF8(text));
                dispatchSdlInput(event);
                if (client.gui.screen() != null) client.keyboardHandler.textInput(client.getWindow().handle(), text);
            }
        });
        context.waitTicks(3);
    }

    private static void dispatchSdlInput(SDL_Event event) {
        // Fabric client game tests cancel real SDL event handlers to isolate
        // input. Feed the same SDL payload into ImGui's native backend instead.
        ImGuiWindowHandlerSDL3 backend = (ImGuiWindowHandlerSDL3) ImGuiMCImpl.handler.getWindowHandler();
        if (!backend.processEvent(event)) throw new AssertionError("SDL backend rejected the test input event");
    }

    // Fabric's takeScreenshot rerenders GameRenderer alone, excluding ImGui's
    // Minecraft.renderFrame hook. Read the completed real frame instead.
    private static Path captureFrame(ClientGameTestContext context, String name) {
        CompletableFuture<Path> result = new CompletableFuture<>();
        context.runOnClient(client -> {
            RenderSystem.getDevice().createCommandEncoder().submit();
            Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> {
                try (image) {
                    Path path = FabricLoader.getInstance().getGameDir().resolve("screenshots/" + name + ".png");
                    Files.createDirectories(path.getParent());
                    image.writeToFile(path);
                    result.complete(path);
                } catch (Throwable exception) {
                    result.completeExceptionally(exception);
                }
            });
        });
        context.waitFor(client -> result.isDone());
        return result.join();
    }

    private static void assertInterfaceVisible(Path closedPath, Path openPath) {
        try {
            BufferedImage closed = ImageIO.read(closedPath.toFile());
            BufferedImage opened = ImageIO.read(openPath.toFile());
            int changed = 0;
            int area = opened.getWidth() * opened.getHeight() * 3 / 4;
            for (int y = 0; y < opened.getHeight() * 3 / 4; y++) {
                for (int x = 0; x < opened.getWidth(); x++) {
                    int a = closed.getRGB(x, y);
                    int b = opened.getRGB(x, y);
                    int difference = Math.abs((a >> 16 & 255) - (b >> 16 & 255))
                        + Math.abs((a >> 8 & 255) - (b >> 8 & 255)) + Math.abs((a & 255) - (b & 255));
                    if (difference > 90) changed++;
                }
            }
            if (changed < area / 4) throw new AssertionError("Editor is open but its interface was not composited into the frame");
        } catch (IOException exception) {
            throw new AssertionError("Cannot inspect rendered editor", exception);
        }
    }

    private static void selectTab(String name) {
        for (Class<?> type : ItemEditor.class.getDeclaredClasses()) {
            if (!type.getSimpleName().equals("EditorTab")) continue;
            for (Object tab : type.getEnumConstants()) {
                if (((Enum<?>) tab).name().equals(name)) {
                    write(editor(), "requestedEditorTab", tab);
                    return;
                }
            }
        }
        throw new AssertionError("Missing editor tab: " + name);
    }

    private static void write(Object instance, String name, Object value) {
        try {
            Field field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(instance, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot configure preview state", exception);
        }
    }

    private static ItemEditor editor() {
        return (ItemEditor) read(ItemLab.class, "activeEditor");
    }

    private static Object read(Object instance, String name) {
        try {
            Class<?> type = instance instanceof Class<?> clazz ? clazz : instance.getClass();
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance instanceof Class<?> ? null : instance);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot inspect preview state", exception);
        }
    }
}

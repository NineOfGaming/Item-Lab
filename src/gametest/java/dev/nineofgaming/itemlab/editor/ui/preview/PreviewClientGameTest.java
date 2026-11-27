package dev.nineofgaming.itemlab.editor.ui.preview;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.nineofgaming.itemlab.ItemLab;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import foundry.imgui.impl.ImGuiMCImpl;
import imgui.ImGui;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Screenshot;
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

package dev.nineofgaming.itemlab.editor.ui;

import dev.nineofgaming.itemlab.editor.ui.text.EditorContentFont;

import foundry.imgui.api.ImGuiMC;
import imgui.ImFont;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseCursor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.List;

/**
 * Renders Minecraft-formatted text with the editor's Unicode-capable font family.
 */
final class EditorRichTextRenderer {
    private static final StringSplitter SPLITTER = new StringSplitter((codePoint, style) -> {
        ImFont font = styleFont(style);
        ImGui.pushFont(font, 0.0f);
        try {
            return ImGui.calcTextSizeX(Character.toString(codePoint));
        } finally {
            ImGui.popFont();
        }
    });

    private EditorRichTextRenderer() {
    }

    static void render(FormattedText text, float wrapWidth) {
        StyledSink sink = new StyledSink();
        sink.setup();
        try {
            for (FormattedCharSequence line : Language.getInstance().getVisualOrder(
                SPLITTER.splitLines(text, (int) wrapWidth, Style.EMPTY)
            )) {
                line.accept(sink);
                sink.finish();
                ImGui.newLine();
            }
        } finally {
            sink.reset();
        }
    }

    private static ImFont styleFont(Style style) {
        if (FontDescription.DEFAULT.equals(style.getFont())) {
            ImFont font = EditorContentFont.styleFont(style.isBold(), style.isItalic());
            if (font != null) return font;
        }
        return ImGuiMC.getStyleFont(style);
    }

    private static void openUri(URI uri) {
        net.minecraft.util.Util.getPlatform().openUri(uri);
    }

    private static final class StyledSink implements FormattedCharSink {
        private final StringBuilder buffer = new StringBuilder();
        private ImFont font;
        private int defaultTextColor;
        private int textColor;
        private HoverEvent hoverEvent;
        private ClickEvent clickEvent;

        void setup() {
            font = ImGui.getFont();
            defaultTextColor = ImGuiMC.getColor(ImGuiCol.Text);
            textColor = defaultTextColor;
        }

        void reset() {
            font = null;
            defaultTextColor = 0;
            textColor = 0;
            buffer.setLength(0);
            hoverEvent = null;
            clickEvent = null;
        }

        @Override
        public boolean accept(int positionInCurrentSequence, @NotNull Style style, int codePoint) {
            EditorContentFont.observeCodepoint(codePoint);
            ImFont nextFont = styleFont(style);
            int nextColor = style.getColor() != null ? style.getColor().getValue() : defaultTextColor;
            if (nextFont != font
                || nextColor != textColor
                || style.getHoverEvent() != hoverEvent
                || style.getClickEvent() != clickEvent) {
                finish();
                font = nextFont;
                textColor = nextColor;
                hoverEvent = style.getHoverEvent();
                clickEvent = style.getClickEvent();
            }
            buffer.appendCodePoint(codePoint);
            return true;
        }

        void finish() {
            if (buffer.isEmpty()) return;

            ImGui.pushFont(font, 0.0f);
            try {
                ImGui.textColored(toAbgr(textColor), buffer.toString());
                if (ImGui.isItemClicked() && clickEvent != null) {
                    handleClick();
                }
                if (ImGui.isItemHovered() && hoverEvent != null) {
                    if (clickEvent != null) ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
                    handleHover();
                }
                ImGui.sameLine();
            } finally {
                ImGui.popFont();
                buffer.setLength(0);
            }
        }

        private static int toAbgr(int rgb) {
            return 0xFF000000
                | (rgb & 0xFF0000) >> 16
                | (rgb & 0x00FF00)
                | (rgb & 0x0000FF) << 16;
        }

        private void handleClick() {
            Minecraft minecraft = Minecraft.getInstance();
            switch (clickEvent) {
                case ClickEvent.OpenUrl(final URI uri) -> {
                    if (!minecraft.options.chatLinks().get()) return;
                    if (minecraft.options.chatLinksPrompt().get()) {
                        //? if >=26.2 {
                        final Screen oldScreen = minecraft.gui.screen();
                        minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmLinkScreen(confirm -> {
                            if (confirm) openUri(uri);
                            minecraft.gui.setScreen(oldScreen);
                        }, uri.toString(), false));
                        //?} else {
                        /*final Screen oldScreen = minecraft.screen;
                        minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmLinkScreen(confirm -> {
                            if (confirm) openUri(uri);
                            minecraft.setScreen(oldScreen);
                        }, uri.toString(), false));
                        *///?}
                    } else {
                        openUri(uri);
                    }
                }
                case ClickEvent.OpenFile(final String file) -> openUri(java.nio.file.Path.of(file).toUri());
                case ClickEvent.RunCommand(final String command) -> {
                    LocalPlayer player = minecraft.player;
                    if (player != null) {
                        //? if >=26.2 {
                        player.connection.sendUnattendedCommand(
                            net.minecraft.commands.Commands.trimOptionalPrefix(command),
                            minecraft.gui.screen()
                        );
                        //?} else {
                        /*player.connection.sendUnattendedCommand(
                                net.minecraft.commands.Commands.trimOptionalPrefix(command),
                                minecraft.screen
                        );
                        *///?}
                    }
                }
                case ClickEvent.CopyToClipboard(final String text) -> minecraft.keyboardHandler.setClipboard(text);
                case ClickEvent.SuggestCommand(final String ignored) -> {
                    // The editor does not own a chat input in which to place the suggestion.
                }
                default -> {
                }
            }
        }

        private void handleHover() {
            Minecraft minecraft = Minecraft.getInstance();
            switch (hoverEvent) {
                case HoverEvent.ShowItem(final net.minecraft.world.item.ItemStackTemplate item) -> {
                    List<Component> tooltip = Screen.getTooltipFromItem(minecraft, item.create());
                    drawTooltip(tooltip);
                }
                case HoverEvent.ShowEntity(final HoverEvent.EntityTooltipInfo info) -> {
                    if (minecraft.options.advancedItemTooltips) {
                        drawTooltip(info.getTooltipLines());
                    }
                }
                case HoverEvent.ShowText(final Component text) -> {
                    ImGui.beginTooltip();
                    render(text, ImGui.getFontSize() * 35.0f);
                    ImGui.endTooltip();
                }
                default -> {
                }
            }
        }

        private static void drawTooltip(List<Component> lines) {
            ImGui.beginTooltip();
            for (Component line : lines) {
                render(line, ImGui.getFontSize() * 35.0f);
            }
            ImGui.endTooltip();
        }
    }
}

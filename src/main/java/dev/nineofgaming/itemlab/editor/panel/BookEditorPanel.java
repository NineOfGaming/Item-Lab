package dev.nineofgaming.itemlab.editor.panel;

import dev.nineofgaming.itemlab.config.ItemLabConfig;
import dev.nineofgaming.itemlab.editor.EditorNavigationGuard;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import dev.nineofgaming.itemlab.editor.ui.text.RichTextLine;
import dev.nineofgaming.itemlab.editor.ui.text.TextInputFormat;
import dev.nineofgaming.itemlab.editor.ui.text.TextFormatUi;
import imgui.ImGui;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;
import static dev.nineofgaming.itemlab.editor.ui.control.EditorInput.inputText;
import static dev.nineofgaming.itemlab.editor.ui.control.EditorInput.inputTextMultiline;

/**
 * Purpose-built writable/written book editor with Minecraft's native page preview.
 */
public final class BookEditorPanel extends ComponentEditorPanel {
    private static final float TEXT_FORMAT_POPUP_WIDTH = 360.0f;

    private final List<WritablePage> writablePages = new ArrayList<>();
    private final List<WrittenPage> writtenPages = new ArrayList<>();
    private final ImInt generation = new ImInt(0);
    private final ImBoolean resolved = new ImBoolean(false);

    private Optional<String> filteredTitle = Optional.empty();
    private boolean writablePresent;
    private boolean writtenPresent;
    private boolean writableDirty;
    private boolean writtenDirty;
    private boolean showWritten;
    // Recovery may show either component, but must never change the signing intent.
    private Boolean recoveryWritten;
    private int writablePage;
    private int writtenPage;
    private boolean requestedContentFocus;
    private TextInputFormat textFormat = ItemLabConfig.get().textInputFormat();
    private RichTextLine title = RichTextLine.createEmpty(textFormat);
    private RichTextLine author = RichTextLine.createEmpty(textFormat);

    public BookEditorPanel(ItemEditor host) {
        super(host, "book");
    }

    @Override
    public void requestPendingFocus() {
        requestedContentFocus = false;
        if (writablePresent) {
            for (int index = 0; index < writablePages.size(); index++) {
                if (!legacyPageProblem(writablePages.get(index).text).isBlank()) {
                    recoveryWritten = false;
                    writablePage = index;
                    requestPendingField("writable_book_pages", "page/" + index);
                    return;
                }
            }
        }
        if (writtenPresent) {
            recoveryWritten = true;
            if (!legacyFieldProblem(title, WrittenBookContent.TITLE_MAX_LENGTH).isBlank()) {
                requestPendingField("written_book_metadata", "title");
                return;
            }
            if (!formattedFieldProblem(author).isBlank()) {
                requestPendingField("written_book_metadata", "author");
                return;
            }
            for (int index = 0; index < writtenPages.size(); index++) {
                if (!writtenPages.get(index).text.validationError().isBlank()) {
                    writtenPage = index;
                    requestPendingField("written_book_pages", "page/" + index);
                    return;
                }
            }
            requestPendingField("written_book_metadata", "title");
            return;
        }
        recoveryWritten = !writablePresent && showWritten;
        String section = viewingWritten() ? "written_book_pages" : "writable_book_pages";
        requestPendingField(section, pageCount() == 0 ? "add" : "page/" + currentPage());
    }

    private boolean viewingWritten() {
        return recoveryWritten == null ? showWritten : recoveryWritten;
    }

    private DataComponentType<?> activeContentType() {
        return viewingWritten() ? DataComponents.WRITTEN_BOOK_CONTENT : DataComponents.WRITABLE_BOOK_CONTENT;
    }

    private void returnToSigningView() {
        recoveryWritten = null;
        clearPendingFocus();
    }

    public static boolean supports(DataComponentType<?> type) {
        return type == DataComponents.WRITABLE_BOOK_CONTENT
            || type == DataComponents.WRITTEN_BOOK_CONTENT;
    }

    public boolean isAvailable(ItemStack stack) {
        return stack.has(DataComponents.WRITABLE_BOOK_CONTENT)
            || stack.has(DataComponents.WRITTEN_BOOK_CONTENT);
    }

    public boolean isComponentDirty(DataComponentType<?> type) {
        if (writableDirty || writtenDirty) isDirty();
        return type == DataComponents.WRITABLE_BOOK_CONTENT ? writableDirty
            : type == DataComponents.WRITTEN_BOOK_CONTENT && writtenDirty;
    }

    public void sync(ItemStack stack) {
        resetEditorUi();
        recoveryWritten = null;
        syncWritable(stack);
        syncWritten(stack);
        // Both components can coexist. For vanilla books the item, not component
        // presence, carries signing intent (including native pending-history stacks).
        showWritten = stack.getItem() == Items.WRITTEN_BOOK
            || stack.getItem() != Items.WRITABLE_BOOK && writtenPresent;
        requestedContentFocus = false;
        updateDirty();
    }

    public void syncComponent(DataComponentType<?> type, ItemStack stack) {
        resetEditorUi();
        recoveryWritten = null;
        if (type == DataComponents.WRITABLE_BOOK_CONTENT) {
            syncWritable(stack);
            showWritten = false;
        } else if (type == DataComponents.WRITTEN_BOOK_CONTENT) {
            syncWritten(stack);
            showWritten = true;
        }
        requestedContentFocus = true;
        updateDirty();
    }

    private void syncWritable(ItemStack stack) {
        writablePages.clear();
        WritableBookContent content = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        writablePresent = content != null;
        if (content != null) {
            for (Filterable<String> page : content.pages()) {
                writablePages.add(new WritablePage(
                    RichTextLine.fromLegacyString(page.raw(), textFormat),
                    page.filtered()
                ));
            }
        }
        writablePage = clampPage(writablePage, writablePages.size());
        writableDirty = false;
    }

    private void syncWritten(ItemStack stack) {
        writtenPages.clear();
        WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        writtenPresent = content != null;
        if (content != null) {
            title = RichTextLine.fromLegacyString(content.title().raw(), textFormat);
            filteredTitle = content.title().filtered();
            author = RichTextLine.fromLegacyString(content.author(), textFormat);
            generation.set(content.generation());
            resolved.set(content.resolved());
            for (Filterable<Component> page : content.pages()) {
                writtenPages.add(new WrittenPage(
                    RichTextLine.from(page.raw(), textFormat),
                    page.filtered()
                ));
            }
        } else {
            title = RichTextLine.createEmpty(textFormat);
            filteredTitle = Optional.empty();
            author = RichTextLine.createEmpty(textFormat);
            generation.set(0);
            resolved.set(false);
        }
        writtenPage = clampPage(writtenPage, writtenPages.size());
        writtenDirty = false;
    }

    public void draw(Minecraft client) {
        drawEditor(client, null);
    }

    public void drawComponent(Minecraft client, DataComponentType<?> type) {
        recoveryWritten = null;
        showWritten = type == DataComponents.WRITTEN_BOOK_CONTENT;
        drawEditor(client, type);
    }

    private void drawEditor(Minecraft client, DataComponentType<?> componentType) {
        ImGui.spacing();
        boolean componentOnly = componentType != null;
        boolean currentDirty = componentOnly ? isComponentDirty(componentType) : isDirty();
        boolean openTextFormat;
        EditorUi.drawSaveDiscardActions(
            currentDirty,
            () -> {
                if (componentOnly) saveComponent(client, componentType, true);
                else save(client, true);
            },
            () -> {
                if (componentOnly) syncComponent(componentType, host.draft());
                else sync(host.draft());
                host.setStatus(true, tr("message.discarded"));
            }
        );

        if (componentOnly) {
            String stateLabel = tr("book.component.enabled");
            float stateWidth = ImGui.getFrameHeight()
                + ImGui.getStyle().getItemSpacingX()
                + ImGui.calcTextSizeX(stateLabel);
            EditorUi.sameLineIfFits(stateWidth);
            boolean present = componentType == DataComponents.WRITTEN_BOOK_CONTENT
                ? writtenPresent
                : writablePresent;
            ImBoolean enabled = new ImBoolean(present);
            if (ImGui.checkbox(tr("book.component.enabled"), enabled)) {
                if (componentType == DataComponents.WRITTEN_BOOK_CONTENT) {
                    writtenPresent = enabled.get();
                    writtenDirty = true;
                } else {
                    writablePresent = enabled.get();
                    writableDirty = true;
                }
                if (enabled.get()) requestedContentFocus = true;
                updateDirty();
            }
        }

        EditorUi.sameLineIfFits(TextFormatUi.buttonWidth(textFormat));
        openTextFormat = TextFormatUi.button("book_text_format", textFormat, null);
        host.drawDiamondFireComponentHint(activeContentType());
        if (openTextFormat) {
            ImGui.openPopup("book_text_format_popup");
        }

        EditorUi.beginScrollableChild("book_editor", 0.0f, Math.max(1.0f, ImGui.getContentRegionAvailY()), false);
        try {
            ItemStack preview = previewStack(componentType);
            host.setItemIconPreview(preview);
            host.drawTooltipPreview(client, preview, "book_metadata");
            ImGui.spacing();
            EditorUi.drawPreviewFormSplit(
                "book_workspace",
                300.0f,
                () -> drawPagePreview(preview),
                () -> drawDocumentEditor(client, !componentOnly)
            );
        } finally {
            ImGui.endChild();
        }
        drawTextFormatPopup();
    }

    private void drawPagePreview(ItemStack preview) {
        ImGui.text(tr("book.preview"));
        host.drawBookPreview(preview, currentPage());
    }

    private void drawDocumentEditor(Minecraft client, boolean fullTab) {
        if (fullTab) drawBookTypeSelector(client);
        if (viewingWritten()) drawWrittenMetadata();
        if (pageCount() == 0) {
            drawAddPageButton("empty", true);
            ImGui.spacing();
            EditorUi.textDisabledWrapped(tr("book.no_pages"));
            return;
        }

        drawPageNavigation();
        drawPageActions();
        if (pageCount() == 0) {
            ImGui.spacing();
            EditorUi.textDisabledWrapped(tr("book.no_pages"));
            return;
        }
        ImGui.spacing();
        ImGui.text(tr("book.page", currentPage() + 1));
        if (viewingWritten()) {
            WrittenPage page = writtenPages.get(writtenPage);
            ImString source = page.text.editorSource(false, false);
            String problem = page.text.validationError();
            if (!problem.isBlank()) ItemEditorTheme.pushInvalidField();
            applyRequestedContentFocus();
            applyPendingFieldFocus("written_book_pages", "page/" + writtenPage);
            if (inputTextMultiline(
                "##written_book_page",
                source,
                -1.0f,
                230.0f,
                0
            )) {
                page.text.commitEditorSource();
                page.filtered = Optional.empty();
                writtenDirty = true;
                updateDirty();
            }
            if (!problem.isBlank()) ItemEditorTheme.popInvalidField();
            ItemEditorTheme.drawFieldProblem(page.text.validationError());
        } else {
            WritablePage page = writablePages.get(writablePage);
            String problem = legacyPageProblem(page.text);
            if (!problem.isBlank()) ItemEditorTheme.pushInvalidField();
            applyRequestedContentFocus();
            applyPendingFieldFocus("writable_book_pages", "page/" + writablePage);
            if (inputTextMultiline(
                "##writable_book_page",
                page.text.editorSource(false, false),
                -1.0f,
                230.0f,
                0
            )) {
                page.text.commitEditorSource();
                page.filtered = Optional.empty();
                writableDirty = true;
                updateDirty();
            }
            if (!problem.isBlank()) ItemEditorTheme.popInvalidField();
            ItemEditorTheme.drawFieldProblem(legacyPageProblem(page.text));
        }
        ImGui.spacing();
        drawAddPageButton("bottom", false);
    }

    private void drawBookTypeSelector(Minecraft client) {
        ImGui.text(tr("book.type"));
        boolean writable = !showWritten;
        if (writable) ItemEditorTheme.pushActiveFilter();
        if (ImGui.button(tr("book.type.writable") + "##book_type_writable")) {
            if (!writable) setSigned(client, false);
            else returnToSigningView();
        }
        if (writable) ItemEditorTheme.popActiveFilter();

        EditorUi.sameLineIfFits(tr("book.type.signed"));
        boolean signed = showWritten;
        if (signed) ItemEditorTheme.pushActiveFilter();
        if (ImGui.button(tr("book.type.signed") + "##book_type_signed")) {
            if (!signed) setSigned(client, true);
            else returnToSigningView();
        }
        if (signed) ItemEditorTheme.popActiveFilter();

        EditorUi.textDisabledWrapped(tr("book.type.help"));
        if (recoveryWritten != null && recoveryWritten != showWritten) {
            EditorUi.textDisabledWrapped(tr("book.recovery_view", tr(recoveryWritten ? "book.type.signed" : "book.type.writable")));
        }
        ImGui.spacing();
    }

    private void drawPageNavigation() {
        int pages = pageCount();
        int page = currentPage();
        ImGui.beginDisabled(page <= 0);
        if (EditorIcons.smallButton(
            "previous_page",
            EditorIcons.Icon.PREVIOUS,
            tr("book.previous_page")
        )) {
            setCurrentPage(page - 1);
            requestedContentFocus = true;
        }
        ImGui.endDisabled();
        String counter = tr("book.page_counter", page + 1, pages);
        EditorUi.sameLineIfFits(ImGui.calcTextSizeX(counter));
        ImGui.alignTextToFramePadding();
        ImGui.text(counter);
        EditorUi.sameLineIfFits(EditorIcons.smallButtonSize());
        ImGui.beginDisabled(page + 1 >= pages);
        if (EditorIcons.smallButton("next_page", EditorIcons.Icon.NEXT, tr("book.next_page"))) {
            setCurrentPage(page + 1);
            requestedContentFocus = true;
        }
        ImGui.endDisabled();

    }

    private void drawTextFormatPopup() {
        ImGui.setNextWindowSizeConstraints(
            TEXT_FORMAT_POPUP_WIDTH,
            0.0f,
            TEXT_FORMAT_POPUP_WIDTH,
            Float.MAX_VALUE
        );
        if (!ImGui.beginPopup("book_text_format_popup")) return;
        TextFormatUi.drawSelector("book_text_input_format", textFormat, host::switchTextInputFormat);
        if (!showWritten) EditorUi.textDisabledWrapped(tr("book.formatting.legacy_limit"));
        ImGui.endPopup();
    }

    private void setSigned(Minecraft client, boolean signed) {
        if (showWritten == signed) return;
        boolean invalid = activePagesHaveFormattingErrors();
        EditorNavigationGuard.Blocker blocker = invalid
            ? new EditorNavigationGuard.Blocker(
            tr("tab.book"),
            "book",
            EditorNavigationGuard.PendingState.INVALID,
            tr("book.mode_change_invalid"),
            new EditorNavigationGuard.FocusRequest("book_content")
        )
            : isDirty()
            ? new EditorNavigationGuard.Blocker(
            tr("tab.book"),
            "book",
            EditorNavigationGuard.PendingState.VALID,
            tr("navigation.pending", tr("tab.book")),
            null
        )
            : null;
        EditorNavigationGuard.Decision decision = EditorNavigationGuard.decide(
            EditorNavigationGuard.Source.USER,
            EditorNavigationGuard.Transition.COMPATIBLE_RETARGET,
            false,
            blocker
        );
        if (!decision.allowed()) {
            host.setStatus(false, decision.blocker().explanation());
            if (decision.blocker().focusRequest() != null) requestedContentFocus = true;
            return;
        }
        if (signed) {
            writtenPages.clear();
            for (WritablePage page : writablePages) {
                writtenPages.add(new WrittenPage(
                    RichTextLine.from(page.text.toPreviewComponent(false), textFormat),
                    page.filtered.map(value -> RichTextLine.fromLegacyString(value, textFormat)
                        .toPreviewComponent(false))
                ));
            }
            writtenPage = clampPage(writablePage, writtenPages.size());
            if (author.isBlank() && client.player != null) {
                author = RichTextLine.fromLegacyString(client.player.getName().getString(), textFormat);
            }
            generation.set(0);
            resolved.set(true);
            writablePresent = false;
            writtenPresent = true;
        } else {
            writablePages.clear();
            for (WrittenPage page : writtenPages) {
                writablePages.add(new WritablePage(
                    RichTextLine.from(page.text.toPreviewComponent(false), textFormat),
                    page.filtered.map(value -> RichTextLine.from(value, textFormat).toLegacyPreviewString())
                ));
            }
            writablePage = clampPage(writtenPage, writablePages.size());
            writtenPresent = false;
            writablePresent = true;
        }
        showWritten = signed;
        returnToSigningView();
        host.setPendingVanillaBookType(signed);
        requestedContentFocus = true;
        writableDirty = true;
        writtenDirty = true;
        updateDirty();
    }

    private boolean activePagesHaveFormattingErrors() {
        return showWritten
            ? writtenPages.stream().anyMatch(page -> !page.text.validationError().isBlank())
            : writablePages.stream().anyMatch(page -> !page.text.validationError().isBlank());
    }

    private void drawWrittenMetadata() {
        if (!EditorUi.beginCollapsibleCard(
            "written_book_metadata",
            tr("book.metadata"),
            ImGuiTableFlags.SizingStretchProp,
            true,
            hasPendingSectionFocus("written_book_metadata")
        )) return;

        ImGui.text(tr("book.title"));
        String titleProblem = legacyFieldProblem(title, WrittenBookContent.TITLE_MAX_LENGTH);
        if (!titleProblem.isBlank()) ItemEditorTheme.pushInvalidField();
        ImGui.setNextItemWidth(-1.0f);
        applyRequestedContentFocus();
        applyPendingFieldFocus("written_book_metadata", "title");
        if (inputText(
            "##book_title",
            title.editorSource(false, false)
        )) {
            title.commitEditorSource();
            filteredTitle = Optional.empty();
            writtenDirty = true;
        }
        if (!titleProblem.isBlank()) ItemEditorTheme.popInvalidField();
        ItemEditorTheme.drawFieldProblem(legacyFieldProblem(title, WrittenBookContent.TITLE_MAX_LENGTH));
        ImGui.text(tr("book.author"));
        String authorProblem = formattedFieldProblem(author);
        if (!authorProblem.isBlank()) ItemEditorTheme.pushInvalidField();
        ImGui.setNextItemWidth(-1.0f);
        applyPendingFieldFocus("written_book_metadata", "author");
        if (inputText(
            "##book_author",
            author.editorSource(false, false)
        )) {
            author.commitEditorSource();
            writtenDirty = true;
        }
        if (!authorProblem.isBlank()) ItemEditorTheme.popInvalidField();
        ItemEditorTheme.drawFieldProblem(formattedFieldProblem(author));

        ImGui.text(tr("book.generation"));
        for (int value = 0; value <= WrittenBookContent.MAX_GENERATION; value++) {
            if (value > 0) EditorUi.sameLineIfFits(generationLabel(value));
            boolean active = generation.get() == value;
            if (active) ItemEditorTheme.pushActiveFilter();
            if (ImGui.button(generationLabel(value) + "##generation_" + value)) {
                generation.set(value);
                writtenDirty = true;
            }
            if (active) ItemEditorTheme.popActiveFilter();
        }
        EditorUi.textDisabledWrapped(tr("book.generation.selected", generationLabel(generation.get())));
        if (ImGui.checkbox(tr("book.resolved"), resolved)) writtenDirty = true;
        updateDirty();
        EditorUi.endCard();
        ImGui.spacing();
    }

    private void drawPageActions() {
        int pages = pageCount();
        drawAddPageButton("top", false);
        EditorUi.sameLineIfFits(EditorIcons.smallButtonSize());
        ImGui.beginDisabled(pages >= WritableBookContent.MAX_PAGES);
        if (EditorIcons.smallButton(
            "insert_page_after",
            EditorIcons.Icon.ADD,
            tr("book.insert_page")
        )) applyPageAction(EditorUi.ListAction.INSERT_AFTER);
        ImGui.endDisabled();

        EditorUi.sameLineIfFits(EditorIcons.smallButtonSize());
        ImGui.beginDisabled(pages >= WritableBookContent.MAX_PAGES);
        if (EditorIcons.smallButton(
            "duplicate_page",
            EditorIcons.Icon.DUPLICATE,
            tr("book.duplicate_page")
        )) applyPageAction(EditorUi.ListAction.DUPLICATE);
        ImGui.endDisabled();

        EditorUi.sameLineIfFits(EditorIcons.smallButtonSize());
        ImGui.beginDisabled(currentPage() <= 0);
        if (EditorIcons.smallButton(
            "move_page_earlier",
            EditorIcons.Icon.MOVE_UP,
            tr("book.move_page_earlier")
        )) applyPageAction(EditorUi.ListAction.MOVE_UP);
        ImGui.endDisabled();

        EditorUi.sameLineIfFits(EditorIcons.smallButtonSize());
        ImGui.beginDisabled(currentPage() + 1 >= pages);
        if (EditorIcons.smallButton(
            "move_page_later",
            EditorIcons.Icon.MOVE_DOWN,
            tr("book.move_page_later")
        )) applyPageAction(EditorUi.ListAction.MOVE_DOWN);
        ImGui.endDisabled();

        EditorUi.sameLineIfFits(EditorIcons.smallButtonSize());
        if (EditorIcons.smallButton("remove_page", EditorIcons.Icon.REMOVE, tr("book.remove_page"))) {
            applyPageAction(EditorUi.ListAction.REMOVE);
        }
    }

    private void drawAddPageButton(String placement, boolean acceptFocus) {
        ImGui.beginDisabled(pageCount() >= WritableBookContent.MAX_PAGES);
        if (acceptFocus) applyRequestedContentFocus();
        if (acceptFocus) applyPendingFieldFocus(viewingWritten() ? "written_book_pages" : "writable_book_pages", "add");
        if (EditorIcons.smallButton(
            "add_page_" + placement,
            EditorIcons.Icon.ADD,
            tr("book.add_page")
        )) addPage();
        ImGui.endDisabled();
    }

    private void addPage() {
        if (viewingWritten()) {
            writtenPages.add(new WrittenPage(
                RichTextLine.createEmpty(textFormat),
                Optional.empty()
            ));
            writtenPage = writtenPages.size() - 1;
            writtenDirty = true;
        } else {
            writablePages.add(new WritablePage(
                RichTextLine.createEmpty(textFormat),
                Optional.empty()
            ));
            writablePage = writablePages.size() - 1;
            writableDirty = true;
        }
        requestedContentFocus = true;
        updateDirty();
    }

    private void applyPageAction(EditorUi.ListAction action) {
        int page = currentPage();
        switch (action) {
            case MOVE_UP -> {
                if (viewingWritten()) {
                    WrittenPage moved = writtenPages.remove(page);
                    writtenPages.add(page - 1, moved);
                    writtenPage--;
                    writtenDirty = true;
                } else {
                    WritablePage moved = writablePages.remove(page);
                    writablePages.add(page - 1, moved);
                    writablePage--;
                    writableDirty = true;
                }
            }
            case MOVE_DOWN -> {
                if (viewingWritten()) {
                    WrittenPage moved = writtenPages.remove(page);
                    writtenPages.add(page + 1, moved);
                    writtenPage++;
                    writtenDirty = true;
                } else {
                    WritablePage moved = writablePages.remove(page);
                    writablePages.add(page + 1, moved);
                    writablePage++;
                    writableDirty = true;
                }
            }
            case DUPLICATE -> {
                if (viewingWritten()) {
                    WrittenPage source = writtenPages.get(page);
                    writtenPages.add(page + 1, new WrittenPage(
                        source.text.copy(),
                        source.filtered
                    ));
                    writtenPage++;
                    writtenDirty = true;
                } else {
                    WritablePage source = writablePages.get(page);
                    writablePages.add(page + 1, new WritablePage(
                        source.text.copy(),
                        source.filtered
                    ));
                    writablePage++;
                    writableDirty = true;
                }
            }
            case INSERT_AFTER -> {
                if (viewingWritten()) {
                    writtenPages.add(page + 1, new WrittenPage(
                        RichTextLine.createEmpty(textFormat),
                        Optional.empty()
                    ));
                    writtenPage++;
                    writtenDirty = true;
                } else {
                    writablePages.add(page + 1, new WritablePage(
                        RichTextLine.createEmpty(textFormat),
                        Optional.empty()
                    ));
                    writablePage++;
                    writableDirty = true;
                }
            }
            case REMOVE -> {
                if (viewingWritten()) {
                    writtenPages.remove(page);
                    writtenPage = clampPage(page, writtenPages.size());
                    writtenDirty = true;
                } else {
                    writablePages.remove(page);
                    writablePage = clampPage(page, writablePages.size());
                    writableDirty = true;
                }
            }
            default -> {
                return;
            }
        }
        requestedContentFocus = true;
        updateDirty();
    }

    public boolean save(Minecraft client, boolean announce) {
        return saveInternal(client, null, announce);
    }

    private boolean saveComponent(Minecraft client, DataComponentType<?> type, boolean announce) {
        return saveInternal(client, type, announce);
    }

    private boolean saveInternal(Minecraft client, DataComponentType<?> onlyType, boolean announce) {
        try {
            ItemStack edited = host.draft().copy();
            if (onlyType == null) edited = transmuteVanillaBook(edited);
            if (onlyType == null || onlyType == DataComponents.WRITABLE_BOOK_CONTENT) applyWritable(edited, false);
            if (onlyType == null || onlyType == DataComponents.WRITTEN_BOOK_CONTENT) applyWritten(edited, false);
            ItemStackCodecs.validateStack(edited);
            host.acceptSectionDraft(client, edited);
            if (onlyType == null) sync(edited);
            else syncComponent(onlyType, edited);
            if (announce) host.setStatus(true, tr("message.saved"));
            return true;
        } catch (RuntimeException exception) {
            host.setStatus(false, tr("error.save", EditorUi.readableMessage(exception)));
            return false;
        }
    }

    private ItemStack previewStack(DataComponentType<?> componentType) {
        try {
            ItemStack edited = host.draft().copy();
            if (componentType == null) {
                edited = transmuteVanillaBook(edited);
                applyWritable(edited, true);
                applyWritten(edited, true);
            } else if (componentType == DataComponents.WRITABLE_BOOK_CONTENT) {
                edited.remove(DataComponents.WRITTEN_BOOK_CONTENT);
                applyWritable(edited, true);
            } else if (componentType == DataComponents.WRITTEN_BOOK_CONTENT) {
                edited.remove(DataComponents.WRITABLE_BOOK_CONTENT);
                applyWritten(edited, true);
            }
            return edited;
        } catch (RuntimeException ignored) {
            return host.draft();
        }
    }

    @Override
    public ItemStack pendingStack(Minecraft client) {
        ItemStack edited = transmuteVanillaBook(host.draft().copy());
        applyWritable(edited, false);
        applyWritten(edited, false);
        ItemStackCodecs.validateStack(edited);
        return edited;
    }

    @Override
    public void restorePending(Minecraft client, ItemStack state) {
        sync(state);
        dirty = true;
        writableDirty = true;
        writtenDirty = true;
    }

    private ItemStack transmuteVanillaBook(ItemStack stack) {
        if (showWritten && stack.getItem() == Items.WRITABLE_BOOK) {
            return stack.transmuteCopy(Items.WRITTEN_BOOK);
        }
        if (!showWritten && stack.getItem() == Items.WRITTEN_BOOK) {
            return stack.transmuteCopy(Items.WRITABLE_BOOK);
        }
        return stack;
    }

    private void applyWritable(ItemStack stack, boolean preview) {
        if (!writablePresent) {
            stack.remove(DataComponents.WRITABLE_BOOK_CONTENT);
            return;
        }
        for (WritablePage page : writablePages) {
            String value = preview ? page.text.toLegacyPreviewString() : page.text.toLegacyString();
            if (value.length() > WritableBookContent.PAGE_EDIT_LENGTH) {
                throw new IllegalArgumentException(
                    tr("book.page_too_long", WritableBookContent.PAGE_EDIT_LENGTH)
                );
            }
        }
        List<Filterable<String>> pages = writablePages.stream()
            .map(page -> new Filterable<>(
                preview ? page.text.toLegacyPreviewString() : page.text.toLegacyString(),
                page.filtered
            ))
            .toList();
        stack.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(pages));
    }

    private void applyWritten(ItemStack stack, boolean preview) {
        if (!writtenPresent) {
            stack.remove(DataComponents.WRITTEN_BOOK_CONTENT);
            return;
        }
        String savedTitle = preview ? title.toLegacyPreviewString() : title.toLegacyString();
        if (savedTitle.length() > WrittenBookContent.TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                tr("book.title_too_long", WrittenBookContent.TITLE_MAX_LENGTH)
            );
        }
        String savedAuthor = preview ? author.toLegacyPreviewString() : author.toLegacyString();
        List<Filterable<Component>> pages = writtenPages.stream()
            .map(page -> new Filterable<>(
                preview ? page.text.toPreviewComponent(false) : page.text.toComponent(false),
                page.filtered
            ))
            .toList();
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
            new Filterable<>(savedTitle, filteredTitle),
            savedAuthor,
            Math.clamp(generation.get(), 0, WrittenBookContent.MAX_GENERATION),
            pages,
            resolved.get()
        ));
    }

    private int pageCount() {
        return viewingWritten() ? writtenPages.size() : writablePages.size();
    }

    private int currentPage() {
        return viewingWritten() ? writtenPage : writablePage;
    }

    private void setCurrentPage(int page) {
        clearPendingFocus();
        if (viewingWritten()) writtenPage = clampPage(page, writtenPages.size());
        else writablePage = clampPage(page, writablePages.size());
    }

    private void applyRequestedContentFocus() {
        if (!requestedContentFocus) return;
        ImGui.setKeyboardFocusHere();
        requestedContentFocus = false;
    }

    private static int clampPage(int page, int count) {
        return count == 0 ? 0 : Math.clamp(page, 0, count - 1);
    }

    private void updateDirty() {
        dirty = writableDirty || writtenDirty;
    }

    @Override
    protected boolean hasPendingChanges() {
        ItemStack draft = host.draft();
        ItemStack edited = pendingStack(Minecraft.getInstance());
        writableDirty = !java.util.Objects.equals(
            draft.get(DataComponents.WRITABLE_BOOK_CONTENT),
            edited.get(DataComponents.WRITABLE_BOOK_CONTENT)
        );
        writtenDirty = !java.util.Objects.equals(
            draft.get(DataComponents.WRITTEN_BOOK_CONTENT),
            edited.get(DataComponents.WRITTEN_BOOK_CONTENT)
        );
        return !ItemStack.matches(draft, edited);
    }

    public Runnable prepareTextFormatChange(TextInputFormat targetFormat) {
        List<RichTextLine> fields = new ArrayList<>(writtenPages.size() + 2);
        fields.add(title);
        fields.add(author);
        writablePages.stream().map(page -> page.text).forEach(fields::add);
        writtenPages.stream().map(page -> page.text).forEach(fields::add);
        List<String> converted = fields.stream()
            .map(field -> field.convertedSource(targetFormat))
            .toList();
        return () -> {
            for (int index = 0; index < fields.size(); index++) {
                fields.get(index).switchFormat(targetFormat, converted.get(index));
            }
            textFormat = targetFormat;
        };
    }

    private static String formattedFieldProblem(RichTextLine field) {
        String problem = field.validationError();
        return problem.isBlank() ? "" : tr("book.formatting_invalid", field.format().label(), problem);
    }

    private static String legacyFieldProblem(RichTextLine field, int maximumLength) {
        String problem = formattedFieldProblem(field);
        if (!problem.isBlank()) return problem;
        return field.toLegacyPreviewString().length() > maximumLength
            ? tr("book.title_too_long", maximumLength)
            : "";
    }

    private static String legacyPageProblem(RichTextLine field) {
        String problem = formattedFieldProblem(field);
        if (!problem.isBlank()) return problem;
        return field.toLegacyPreviewString().length() > WritableBookContent.PAGE_EDIT_LENGTH
            ? tr("book.page_too_long", WritableBookContent.PAGE_EDIT_LENGTH)
            : "";
    }

    private static String generationLabel(int generation) {
        return switch (generation) {
            case 0 -> tr("book.generation.original");
            case 1 -> tr("book.generation.copy");
            case 2 -> tr("book.generation.copy_of_copy");
            default -> tr("book.generation.tattered");
        };
    }

    private static final class WritablePage {
        private final RichTextLine text;
        private Optional<String> filtered;

        private WritablePage(RichTextLine text, Optional<String> filtered) {
            this.text = text;
            this.filtered = filtered;
        }
    }

    private static final class WrittenPage {
        private final RichTextLine text;
        private Optional<Component> filtered;

        private WrittenPage(RichTextLine text, Optional<Component> filtered) {
            this.text = text;
            this.filtered = filtered;
        }
    }
}

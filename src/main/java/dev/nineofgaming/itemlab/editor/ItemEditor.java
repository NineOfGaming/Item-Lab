package dev.nineofgaming.itemlab.editor;

import com.google.gson.JsonElement;
import dev.nineofgaming.itemlab.config.ComponentBrowserScope;
import dev.nineofgaming.itemlab.config.ItemLabConfig;
import dev.nineofgaming.itemlab.editor.catalog.ComponentOption;
import dev.nineofgaming.itemlab.editor.catalog.ComponentState;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.catalog.ItemOption;
import dev.nineofgaming.itemlab.editor.component.ComponentFormPanel;
import dev.nineofgaming.itemlab.editor.component.ComponentSectionInitialState;
import dev.nineofgaming.itemlab.editor.component.ComponentRecommendations;
import dev.nineofgaming.itemlab.editor.component.ComponentValueFactory;
import dev.nineofgaming.itemlab.editor.component.DedicatedEditorSchema;
import dev.nineofgaming.itemlab.editor.component.EditorVersionProfile;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import dev.nineofgaming.itemlab.editor.component.InlineComponentEditorRegistry;
import dev.nineofgaming.itemlab.editor.component.VariantComponentEditorPanel;
import dev.nineofgaming.itemlab.editor.container.SourceInventorySnapshot;
import dev.nineofgaming.itemlab.editor.diamondfire.DiamondFireComponentSupport;
import dev.nineofgaming.itemlab.editor.diamondfire.DiamondFireContext;
import dev.nineofgaming.itemlab.editor.diamondfire.DiamondFireFeatures;
import dev.nineofgaming.itemlab.editor.diamondfire.DiamondFireTagEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.AppearanceEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.AdventureEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.AttributeEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.BannerEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.BeesEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.BookEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.CombatEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.CompassEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.ContainerEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.DataEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.DecoratedPotEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.EnchantmentEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.EquipmentEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.EditorSettingsPanel;
import dev.nineofgaming.itemlab.editor.panel.PendingStackEditor;
import dev.nineofgaming.itemlab.editor.panel.FlagEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.FireworkEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.LoreEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.MapEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.MusicEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.PotionEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.PlayerHeadEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.RawStackEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.RecipesEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.SulfurCubeContentEditorPanel;
import dev.nineofgaming.itemlab.editor.panel.UseEditorPanel;
import dev.nineofgaming.itemlab.editor.state.EditorItemTarget;
import dev.nineofgaming.itemlab.editor.state.ItemEditHistory;
import dev.nineofgaming.itemlab.editor.state.ItemStackSnapshot;
import dev.nineofgaming.itemlab.editor.state.OpeningItemSnapshot;
import dev.nineofgaming.itemlab.editor.state.PendingPanelHistory;
import dev.nineofgaming.itemlab.editor.ui.control.AutocompleteInput;
import dev.nineofgaming.itemlab.editor.ui.control.FieldHints;
import dev.nineofgaming.itemlab.editor.ui.preview.EquipmentPreviewBase;
import dev.nineofgaming.itemlab.editor.ui.text.EditorContentFont;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.control.EditorShortcutPolicy;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import dev.nineofgaming.itemlab.editor.ui.preview.ItemModelPreviewMode;
import dev.nineofgaming.itemlab.editor.ui.preview.PlayerSkinPreviewMode;
import dev.nineofgaming.itemlab.editor.ui.preview.NativeItemPreviews;
import dev.nineofgaming.itemlab.editor.ui.sound.SoundPreviewController;
import dev.nineofgaming.itemlab.editor.ui.sound.SoundPreviewKind;
import dev.nineofgaming.itemlab.editor.ui.text.TextInputFormat;
import foundry.imgui.api.ImGuiMC;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiTabItemFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static dev.nineofgaming.itemlab.editor.ui.EditorUi.beginCard;
import static dev.nineofgaming.itemlab.editor.ui.EditorUi.beginScrollableChild;
import static dev.nineofgaming.itemlab.editor.ui.EditorUi.endCard;
import static dev.nineofgaming.itemlab.editor.ui.EditorUi.minecraftText;
import static dev.nineofgaming.itemlab.editor.ui.EditorUi.readableMessage;
import static dev.nineofgaming.itemlab.editor.ui.EditorUi.resetCardMeasurements;
import static dev.nineofgaming.itemlab.editor.ui.EditorUi.resetCollapsibleStates;
import static dev.nineofgaming.itemlab.editor.ui.EditorUi.resetCurrentScrollOnFirstVisit;
import static dev.nineofgaming.itemlab.editor.ui.EditorUi.resetScrollStates;
import static dev.nineofgaming.itemlab.editor.ui.EditorUi.sameLineIfFits;
import static dev.nineofgaming.itemlab.editor.ui.EditorUi.setCollapsibleSessionId;
import static dev.nineofgaming.itemlab.editor.ui.EditorUi.textDisabledWrapped;
import static dev.nineofgaming.itemlab.editor.ui.control.EditorInput.inputTextWithHint;
import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * ImGui workbench for creating and editing complete Minecraft item stacks.
 */
public final class ItemEditor {
    private static final int MAX_ITEM_COUNT = 99;
    private static final int MAX_EDITOR_HISTORY = 64;
    private static final int MAX_NESTED_EDITOR_DEPTH = 8;
    private static final float COMPONENT_BROWSER_WIDTH = 245.0f;
    private static final float COMPONENT_EDITOR_MIN_WIDTH = 430.0f;
    private final ItemLabConfig preferences = ItemLabConfig.get();
    private final ImBoolean windowVisible = new ImBoolean(false);

    private final ImString itemSearch = new ImString(256);
    private final ImString itemId = new ImString(256);
    private final ImInt count = new ImInt(1);

    private final ImString componentSearch = new ImString(256);
    private ComponentBrowserScope componentScope = preferences.componentBrowserScope();
    private final ImBoolean includeNetworkOnlyComponents = new ImBoolean(preferences.includeNetworkOnlyComponents());
    private final ImBoolean excludeDedicatedComponents = new ImBoolean(preferences.excludeDedicatedComponents());
    private final ImBoolean minecraftComponentsOnly = new ImBoolean(preferences.minecraftComponentsOnly());
    private final AutocompleteInput autocomplete = new AutocompleteInput();
    private final NativeItemPreviews nativePreviews = new NativeItemPreviews();
    private final SoundPreviewController soundPreviews = new SoundPreviewController();
    private final ComponentFormPanel componentForm = new ComponentFormPanel(this);
    private final VariantComponentEditorPanel variantComponentEditor = new VariantComponentEditorPanel(this);
    private final LoreEditorPanel loreEditor = new LoreEditorPanel(this);
    private final EditorSettingsPanel settingsEditor = new EditorSettingsPanel(this, loreEditor, preferences);
    private final AttributeEditorPanel attributeEditor = new AttributeEditorPanel(this);
    private final EnchantmentEditorPanel enchantmentEditor = new EnchantmentEditorPanel(this);
    private final FlagEditorPanel flagEditor = new FlagEditorPanel(this);
    private final AdventureEditorPanel adventureEditor = new AdventureEditorPanel(this);
    private final UseEditorPanel useEditor = new UseEditorPanel(this);
    private final CombatEditorPanel combatEditor = new CombatEditorPanel(this);
    private final MusicEditorPanel musicEditor = new MusicEditorPanel(this);
    private final CompassEditorPanel compassEditor = new CompassEditorPanel(this);
    private final ContainerEditorPanel containerEditor = new ContainerEditorPanel(this);
    private final EquipmentEditorPanel equipmentEditor = new EquipmentEditorPanel(this);
    private final AppearanceEditorPanel appearanceEditor = new AppearanceEditorPanel(this);
    private final BookEditorPanel bookEditor = new BookEditorPanel(this);
    private final MapEditorPanel mapEditor = new MapEditorPanel(this);
    private final BannerEditorPanel bannerEditor = new BannerEditorPanel(this);
    private final DecoratedPotEditorPanel decoratedPotEditor = new DecoratedPotEditorPanel(this);
    private final PotionEditorPanel potionEditor = new PotionEditorPanel(this);
    private final FireworkEditorPanel fireworkEditor = new FireworkEditorPanel(this);
    private final PlayerHeadEditorPanel playerHeadEditor = new PlayerHeadEditorPanel(this);
    private final RecipesEditorPanel recipesEditor = new RecipesEditorPanel(this);
    private final BeesEditorPanel beesEditor = new BeesEditorPanel(this);
    private final SulfurCubeContentEditorPanel sulfurCubeContentEditor = new SulfurCubeContentEditorPanel(this);
    private final DataEditorPanel dataEditor = new DataEditorPanel(this);
    private final RawStackEditorPanel rawStackEditor = new RawStackEditorPanel(this);
    private final DiamondFireTagEditorPanel diamondFireTagEditor = new DiamondFireTagEditorPanel(this);
    private final ComponentValueFactory componentValueFactory = new ComponentValueFactory();
    private final PendingPanelHistory pendingPanelHistory = new PendingPanelHistory(List.of(
        new PendingPanelHistory.Panel("appearance", appearanceEditor),
        new PendingPanelHistory.Panel("adventure", adventureEditor),
        new PendingPanelHistory.Panel("attributes", attributeEditor),
        new PendingPanelHistory.Panel("banner", bannerEditor),
        new PendingPanelHistory.Panel("book", bookEditor),
        new PendingPanelHistory.Panel("combat", combatEditor),
        new PendingPanelHistory.Panel("compass", compassEditor),
        new PendingPanelHistory.Panel("container", containerEditor),
        new PendingPanelHistory.Panel("decorated_pot", decoratedPotEditor),
        new PendingPanelHistory.Panel("equipment", equipmentEditor),
        new PendingPanelHistory.Panel("firework", fireworkEditor),
        new PendingPanelHistory.Panel("map", mapEditor),
        new PendingPanelHistory.Panel("music", musicEditor),
        new PendingPanelHistory.Panel("player_head", playerHeadEditor),
        new PendingPanelHistory.Panel("potion", potionEditor),
        new PendingPanelHistory.Panel("recipes", recipesEditor),
        new PendingPanelHistory.Panel("bees", beesEditor),
        new PendingPanelHistory.Panel("sulfur_cube_content", sulfurCubeContentEditor),
        new PendingPanelHistory.Panel("use", useEditor)
    ));
    private ItemEditHistory history = new ItemEditHistory();
    private final Deque<EditorHistorySnapshot> editorUndo = new ArrayDeque<>();
    private final Deque<EditorHistorySnapshot> editorRedo = new ArrayDeque<>();
    private EditorHistorySnapshot lastEditorState;
    private EditorHistorySnapshot editorInteractionStart;
    private int editorInteractionControl;

    private List<ItemOption> itemOptions = List.of();
    private List<IdSuggestion> itemIdSuggestions = List.of();
    private List<ComponentOption> componentOptions = List.of();
    private ComponentOption selectedComponent;
    private ItemStack draft = ItemStack.EMPTY;
    private ItemStack pendingPreviewDraft;
    private ItemStack lastUiStateItem = ItemStack.EMPTY;
    private boolean hasLastUiStateItem;
    private ItemStackSnapshot cleanDraft;
    private OpeningItemSnapshot openingItem = new OpeningItemSnapshot();
    private EditorItemTarget target;
    private Screen returnScreen;
    private SourceInventorySnapshot sourceInventory = SourceInventorySnapshot.EMPTY;
    private final Deque<NestedEditorFrame> nestedEditorFrames = new ArrayDeque<>();
    private NestedOpenRequest nestedOpenRequest;
    private EditorTab requestedEditorTab;
    private final Map<String, Runnable> pendingFailureFocus = new HashMap<>();
    private PendingEditorRegistry pendingEditorRegistry;
    private PendingEditorRegistry buildingPendingEditorRegistry;
    private String pendingComponentRecovery;
    private String pendingComponentRecoveryTitle;
    private boolean focusPendingComponentRecovery;
    private boolean focusItemId;
    private long nextEditorUiSessionId = 1L;
    private long editorUiSessionId;

    private boolean open;
    private boolean focusSinkOnOpen;
    private boolean basicDirty;
    private boolean draftDirty;
    private boolean useGenericSelectedComponentEditor;
    private boolean focusSelectedComponentPane;
    private String componentCodecProblem = "";
    private String status = "";
    private boolean diamondFireActive;
    private boolean drawingInlineComponentEditor;
    private boolean collapseAbsentComponentsForSession;
    private boolean statusSuccess;
    private boolean historyEnabled;
    private boolean applyingHistory;
    private boolean capturingTextInput;

    public void toggle(Minecraft client) {
        if (open) {
            close(client);
        } else {
            open(client, false);
        }
    }

    public void openNewItem(Minecraft client) {
        if (!open) {
            open(client, true);
        }
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isCapturingTextInput() {
        return capturingTextInput;
    }

    public void tick(Minecraft client) {
        if (open && (client.player == null || client.level == null || hasScreen(client))) {
            close(client);
        }
    }

    public void render() {
        if (!open) {
            capturingTextInput = false;
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            close(client);
            return;
        }

        ImGuiViewport viewport = ImGui.getMainViewport();
        float workWidth = viewport.getWorkSizeX();
        float workHeight = viewport.getWorkSizeY();
        // Fullscreen windows can report a zero-sized viewport while minimized or switching focus.
        // Leave the editor session intact and wait for a drawable frame before handling UI input.
        if (!EditorWindowSizing.hasUsableWorkArea(workWidth, workHeight)) {
            capturingTextInput = false;
            return;
        }

        ImGui.getIO().addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
        ImGui.getIO().setWantCaptureMouse(true);
        ImGui.getIO().setWantCaptureKeyboard(true);
        capturingTextInput = ImGui.getIO().getWantTextInput();
        if (ImGui.isKeyPressed(ImGuiKey.Escape, false) && !editorShortcutsSuppressed()) {
            if (nestedEditorFrames.isEmpty()) {
                close(client);
            } else {
                cancelNestedItemEdit(client);
            }
            return;
        }

        EditorWindowSizing.Layout windowLayout = EditorWindowSizing.forWorkArea(workWidth, workHeight);
        ImGui.setNextWindowSize(windowLayout.width(), windowLayout.height(), ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(
            windowLayout.minimumWidth(),
            windowLayout.minimumHeight(),
            Float.MAX_VALUE,
            Float.MAX_VALUE
        );
        ImGui.setNextWindowPos(
            viewport.getWorkCenterX(),
            viewport.getWorkCenterY(),
            ImGuiCond.FirstUseEver,
            0.5f,
            0.5f
        );

        nativePreviews.beginImGuiFrame();
        ItemEditorTheme.push();
        boolean contentFontPushed = EditorContentFont.push();
        boolean contentVisible = ImGui.begin(
            "Item Lab###itemlab_editor",
            windowVisible,
            ImGuiWindowFlags.NoCollapse
                | ImGuiWindowFlags.NoScrollbar
                | ImGuiWindowFlags.NoScrollWithMouse
        );
        try {
            resetCurrentScrollOnFirstVisit("itemlab_editor");
            // The root window is only a frame for the header, tabs, and their independently
            // scrolling panels. Never retain overflow-induced root scrolling between frames.
            ImGui.setScrollX(0.0f);
            ImGui.setScrollY(0.0f);
            if (contentVisible) {
                int sessionDepth = nestedEditorFrames.size();
                ImGui.pushID("itemlab_session_" + sessionDepth);
                try {
                    setCollapsibleSessionId(editorUiSessionId);
                    ensureEditorHistoryInitialized(client);
                    handleHistoryShortcuts(client);
                    drawEditor(client);
                    openRequestedNestedItem(client);
                    captureEditorHistory(client);
                } finally {
                    ImGui.popID();
                }
            }
        } finally {
            nativePreviews.drawCompositeLayer();
            ImGui.end();
            if (contentFontPushed) {
                ImGui.popFont();
            }
            ItemEditorTheme.pop();
            nativePreviews.endImGuiFrame();
        }

        if (!windowVisible.get()) {
            close(client);
        }
    }

    private void drawEditor(Minecraft client) {
        if (focusSinkOnOpen) {
            float cursorX = ImGui.getCursorPosX();
            float cursorY = ImGui.getCursorPosY();
            ImGui.invisibleButton("##initial_focus_sink", 1.0f, 1.0f);
            ImGui.setItemDefaultFocus();
            ImGui.setCursorPos(cursorX, cursorY);
            focusSinkOnOpen = false;
        }
        boolean creative = hasCreativeAccess(client);
        drawHeader(client, creative);
        drawClearSlotConfirmation(client);
        drawStatusFooter();
        ImGui.separator();

        DiamondFireContext.State diamondFire = DiamondFireContext.current();
        diamondFireActive = diamondFireSupportActive(diamondFire);
        boolean showDiamondFireTab = diamondFireTabAvailable(diamondFire);
        if (requestedEditorTab == EditorTab.DIAMONDFIRE && !showDiamondFireTab) {
            requestedEditorTab = EditorTab.NAME;
        }
        if (ImGui.beginTabBar("itemlab_tabs", ImGuiTabBarFlags.FittingPolicyScroll)) {
            if (beginEditorTab(EditorTab.NAME, tabLabel("tab.name", "name", loreEditor.isDirty()))) {
                drawLoreTab(client);
                ImGui.endTabItem();
            }
            if (showDiamondFireTab
                && beginEditorTab(
                EditorTab.DIAMONDFIRE,
                tabLabel("tab.diamondfire", "diamondfire", diamondFireTagEditor.isDirty())
            )) {
                diamondFireTagEditor.draw(
                    client,
                    diamondFire.active() ? diamondFire.host() : null,
                    dataEditor.isCustomDataDirty()
                );
                ImGui.endTabItem();
            }
            if (beginEditorTab(
                EditorTab.PRESENTATION,
                tabLabel("tab.presentation", "presentation", appearanceEditor.isDirty())
            )) {
                appearanceEditor.draw(client);
                ImGui.endTabItem();
            }
            if (beginEditorTab(EditorTab.FLAGS, tabLabel("tab.flags", "flags", flagEditor.isDirty()))) {
                drawFlagsTab(client);
                ImGui.endTabItem();
            }
            if (beginEditorTab(
                EditorTab.ATTRIBUTES,
                tabLabel("tab.attributes", "attributes", attributeEditor.isDirty())
            )) {
                drawAttributesTab(client);
                ImGui.endTabItem();
            }
            if (beginEditorTab(
                EditorTab.ENCHANTMENTS,
                tabLabel("tab.enchantments", "enchantments", enchantmentEditor.isDirty())
            )) {
                drawEnchantmentsTab(client);
                ImGui.endTabItem();
            }
            if (beginEditorTab(EditorTab.COMBAT, tabLabel("tab.combat", "combat", combatEditor.isDirty()))) {
                combatEditor.draw(client);
                ImGui.endTabItem();
            }
            if (beginEditorTab(
                EditorTab.EQUIPMENT,
                tabLabel("tab.equipment", "equipment", equipmentEditor.isDirty())
            )) {
                equipmentEditor.draw(client);
                ImGui.endTabItem();
            }
            if (beginEditorTab(EditorTab.USE, tabLabel("tab.use", "use", useEditor.isDirty()))) {
                useEditor.draw(client);
                ImGui.endTabItem();
            }
            if (EditorNavigationGuard.shouldKeepRouteVisible(musicEditor.isAvailable(draft), musicEditor.isDirty())
                && beginEditorTab(EditorTab.MUSIC, tabLabel("tab.music", "music", musicEditor.isDirty()))) {
                musicEditor.draw(client);
                ImGui.endTabItem();
            }
            if (EditorNavigationGuard.shouldKeepRouteVisible(potionEditor.isAvailable(draft), potionEditor.isDirty())
                && beginEditorTab(EditorTab.POTION, tabLabel("tab.potion", "potion", potionEditor.isDirty()))) {
                potionEditor.draw(client);
                ImGui.endTabItem();
            }
            if (EditorNavigationGuard.shouldKeepRouteVisible(fireworkEditor.isAvailable(draft), fireworkEditor.isDirty())
                && beginEditorTab(
                EditorTab.FIREWORK,
                tabLabel("tab.firework", "firework", fireworkEditor.isDirty())
            )) {
                fireworkEditor.draw(client);
                ImGui.endTabItem();
            }
            if (EditorNavigationGuard.shouldKeepRouteVisible(
                playerHeadEditor.isAvailable(draft),
                playerHeadEditor.isDirty()
            ) && beginEditorTab(
                EditorTab.PLAYER_HEAD,
                tabLabel("tab.player_head", "player_head", playerHeadEditor.isDirty())
            )) {
                playerHeadEditor.draw(client);
                ImGui.endTabItem();
            }
            if (EditorNavigationGuard.shouldKeepRouteVisible(bookEditor.isAvailable(draft), bookEditor.isDirty())
                && beginEditorTab(EditorTab.BOOK, tabLabel("tab.book", "book", bookEditor.isDirty()))) {
                bookEditor.draw(client);
                ImGui.endTabItem();
            }
            if (EditorNavigationGuard.shouldKeepRouteVisible(compassEditor.isAvailable(draft), compassEditor.isDirty())
                && beginEditorTab(
                EditorTab.COMPASS,
                tabLabel("tab.compass", "compass", compassEditor.isDirty())
            )) {
                compassEditor.draw(client);
                ImGui.endTabItem();
            }
            if (EditorNavigationGuard.shouldKeepRouteVisible(mapEditor.isAvailable(draft), mapEditor.isDirty())
                && beginEditorTab(EditorTab.MAP, tabLabel("tab.map", "map", mapEditor.isDirty()))) {
                mapEditor.draw(client);
                ImGui.endTabItem();
            }
            if (EditorNavigationGuard.shouldKeepRouteVisible(bannerEditor.isAvailable(draft), bannerEditor.isDirty())
                && beginEditorTab(
                EditorTab.BANNER,
                tabLabel("tab.banner", "banner", bannerEditor.isDirty())
            )) {
                bannerEditor.draw(client);
                ImGui.endTabItem();
            }
            if (EditorNavigationGuard.shouldKeepRouteVisible(
                decoratedPotEditor.isAvailable(draft),
                decoratedPotEditor.isDirty()
            ) && beginEditorTab(EditorTab.DECORATED_POT, tabLabel(
                "tab.decorated_pot",
                "decorated_pot",
                decoratedPotEditor.isDirty()
            ))) {
                decoratedPotEditor.draw(client);
                ImGui.endTabItem();
            }
            if (EditorNavigationGuard.shouldKeepRouteVisible(
                containerEditor.isAvailable(draft),
                containerEditor.isDirty()
            )
                && beginEditorTab(EditorTab.CONTAINER, tabLabel("tab.container", "container", containerEditor.isDirty()))) {
                containerEditor.draw(client);
                ImGui.endTabItem();
            }
            if (beginEditorTab(EditorTab.DATA, tabLabel("tab.data", "data", dataEditor.isDirty()))) {
                dataEditor.draw(client, diamondFireTagEditor.isDirty());
                ImGui.endTabItem();
            }
            if (beginEditorTab(EditorTab.COMPONENTS, tabLabel("tab.components", "components", componentsTabDirty()))) {
                drawComponentsTab(client);
                ImGui.endTabItem();
            }
            if (beginEditorTab(EditorTab.RAW, tabLabel("tab.raw", "raw", rawStackEditor.isDirty()))) {
                drawRawTab(client);
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
    }

    private static String tabLabel(String translationKey, String id, boolean dirty) {
        return tr(translationKey) + (dirty ? " *" : "") + "###itemlab_tab_" + id;
    }

    private boolean beginEditorTab(EditorTab tab, String label) {
        EditorNavigationGuard.Decision decision = EditorNavigationGuard.decide(
            requestedEditorTab == tab
                ? EditorNavigationGuard.Source.PROGRAMMATIC
                : EditorNavigationGuard.Source.USER,
            EditorNavigationGuard.Transition.VIEW_ONLY,
            false,
            null
        );
        if (!decision.allowed()) return false;
        int flags = requestedEditorTab == tab ? ImGuiTabItemFlags.SetSelected : ImGuiTabItemFlags.None;
        boolean visible = ImGui.beginTabItem(label, flags);
        if (visible && requestedEditorTab == tab) requestedEditorTab = null;
        return visible;
    }

    private boolean diamondFireTabAvailable(DiamondFireContext.State connection) {
        return DiamondFireFeatures.available()
            && (diamondFireSupportActive(connection) || diamondFireTagEditor.isDirty());
    }

    private boolean diamondFireSupportActive(DiamondFireContext.State connection) {
        return DiamondFireFeatures.available()
            && (connection.active() || preferences.forceDiamondFireCompatibility());
    }

    private boolean componentsTabDirty() {
        return componentForm.isDirty()
            || selectedComponent != null && hasPendingEditorChanges(selectedComponent.type());
    }

    private void drawHeader(Minecraft client, boolean creative) {
        boolean openItemPicker = false;
        boolean openSettings = false;
        boolean refreshChangedItem = false;
        drawNestedEditorNavigation(client);
        float itemIconSize = nativePreviews.itemIconSize(client);
        if (beginCard("item_summary", 3, ImGuiTableFlags.SizingStretchProp)) {
            ImGui.tableSetupColumn("Preview", ImGuiTableColumnFlags.WidthFixed, itemIconSize);
            ImGui.tableSetupColumn("Item", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableSetupColumn("State", ImGuiTableColumnFlags.WidthFixed, 220.0f);
            ImGui.tableNextColumn();
            nativePreviews.drawItemIcon(client, headerPreviewStack());
            ImGui.tableNextColumn();
            ImGui.textWrapped(targetDescription());
            minecraftText(liveNamePreview(), Math.max(1.0f, ImGui.getContentRegionAvailX()));
            ImGui.tableNextColumn();
            ImGui.textWrapped(tr("header.components", draft.getComponents().size()));
            ImGui.textWrapped(tr("header.overrides", draft.getComponentsPatch().size()));
            ImGui.textWrapped(hasUnsavedFormChanges()
                ? tr("header.state.unsaved")
                : draftDirty ? tr("header.state.ready") : tr("header.state.clean"));
            drawHistoryActions(client);
            endCard();
        }

        ImGui.spacing();
        if (ImGui.beginTable("identity_fields", 3, ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.NoPadOuterX)) {
            ImGui.tableSetupColumn("Item ID", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableSetupColumn("Browse", ImGuiTableColumnFlags.WidthFixed, EditorIcons.buttonSize());
            ImGui.tableSetupColumn("Count", ImGuiTableColumnFlags.WidthFixed, 112.0f);
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text(tr("field.item_id"));
            ImGui.tableNextColumn();
            ImGui.text(" ");
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text(tr("field.count"));

            ImGui.tableNextColumn();
            if (focusItemId) {
                ImGui.setKeyboardFocusHere();
                focusItemId = false;
            }
            if (inputIdentifierWithSuggestions(
                "item_id",
                new FieldHints(
                    tr("field_purpose.item_identifier"),
                    "minecraft:diamond_pickaxe"
                ),
                itemId,
                itemIdSuggestions,
                itemIdentifierProblem(itemId.get())
            )) {
                basicDirty = true;
            }
            refreshChangedItem = autocomplete.wasLastInputCommitted();
            ImGui.tableNextColumn();
            if (EditorIcons.button("browse_items", EditorIcons.Icon.BROWSE, tr("action.browse"))) {
                openItemPicker = true;
            }
            ImGui.tableNextColumn();
            ImGui.setNextItemWidth(-1.0f);
            if (ImGui.inputInt("##count", count, 1, 8)) {
                count.set(Math.clamp(count.get(), 1, MAX_ITEM_COUNT));
                basicDirty = true;
            }
            ImGui.endTable();
        }
        if (refreshChangedItem) {
            refreshDraftForChangedItemId(client);
        }
        if (openItemPicker) {
            ImGui.openPopup("item_picker");
        }

        if (ImGui.beginTable(
            "item_actions",
            2,
            ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.NoPadOuterX
        )) {
            ImGui.tableSetupColumn("Actions", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableSetupColumn("Settings", ImGuiTableColumnFlags.WidthFixed, EditorIcons.buttonSize());
            ImGui.tableNextColumn();
            drawItemActions(client, creative);
            ImGui.tableNextColumn();
            if (EditorIcons.button("editor_settings", EditorIcons.Icon.SETTINGS, tr("action.settings"))) {
                openSettings = true;
            }
            ImGui.endTable();
        }

        if (drawItemPickerPopup()) {
            refreshDraftForChangedItemId(client);
        }
        if (openSettings) {
            ImGui.openPopup("editor_settings_popup");
        }
        settingsEditor.draw();
    }

    private void drawItemActions(Minecraft client, boolean creative) {
        if (!nestedEditorFrames.isEmpty()) {
            if (EditorIcons.button(
                "apply_nested_item",
                EditorIcons.Icon.SAVE,
                tr(switch (nestedEditorFrames.getLast().destinationKind()) {
                    case CONTAINER -> "nested.apply";
                    case BUNDLE -> "nested.apply.bundle";
                    case SULFUR_CUBE -> "nested.apply.sulfur_cube";
                })
            )) {
                applyNestedItemEdit(client);
            }
            sameLineIfFits(EditorIcons.buttonSize());
            if (EditorIcons.button(
                "cancel_nested_item",
                EditorIcons.Icon.DISCARD,
                tr("nested.cancel")
            )) {
                cancelNestedItemEdit(client);
            }
            sameLineIfFits(EditorIcons.buttonSize());
            if (EditorIcons.button(
                "restore_opening_item",
                EditorIcons.Icon.UNDO,
                tr("action.restore_opening_item")
            )) {
                revertDraft(client);
            }
            sameLineIfFits(EditorIcons.buttonSize());
            if (EditorIcons.button("reset_defaults", EditorIcons.Icon.DEFAULTS, tr("action.reset_defaults"))) {
                resetDraftToDefaults(client);
            }
            return;
        }
        ImGui.beginDisabled(!creative);
        if (EditorIcons.button("save_item", EditorIcons.Icon.SAVE, tr("action.save_item"))) {
            applyDraftToSlot(client);
        }
        sameLineIfFits(EditorIcons.buttonSize());
        if (EditorIcons.button("give_copy", EditorIcons.Icon.COPY, tr("action.give_copy"))) {
            giveDraftCopy(client);
        }
        ImGui.endDisabled();
        sameLineIfFits(EditorIcons.buttonSize());
        if (EditorIcons.button(
            "restore_opening_item",
            EditorIcons.Icon.UNDO,
            tr("action.restore_opening_item")
        )) {
            revertDraft(client);
        }
        sameLineIfFits(EditorIcons.buttonSize());
        if (EditorIcons.button("reset_defaults", EditorIcons.Icon.DEFAULTS, tr("action.reset_defaults"))) {
            resetDraftToDefaults(client);
        }
        sameLineIfFits(EditorIcons.buttonSize());
        if (EditorIcons.button("clear_slot", EditorIcons.Icon.REMOVE, tr("action.clear_slot"))) {
            ImGui.openPopup("clear_target");
        }
    }

    private void drawNestedEditorNavigation(Minecraft client) {
        if (nestedEditorFrames.isEmpty()) return;
        if (EditorIcons.smallButton(
            "nested_editor_back",
            EditorIcons.Icon.PREVIOUS,
            tr("nested.cancel")
        )) {
            cancelNestedItemEdit(client);
        }
        ImGui.sameLine();
        StringBuilder breadcrumb = new StringBuilder(tr("nested.root"));
        for (NestedEditorFrame frame : nestedEditorFrames) {
            breadcrumb.append(" > ").append(tr(
                switch (frame.destinationKind()) {
                    case CONTAINER -> "nested.slot";
                    case BUNDLE -> "nested.bundle_entry";
                    case SULFUR_CUBE -> "nested.sulfur_cube_content";
                },
                frame.destination() + 1
            ));
        }
        ImGui.alignTextToFramePadding();
        ImGui.textWrapped(breadcrumb.toString());
        ImGui.separator();
        ImGui.spacing();
    }


    private void drawHistoryActions(Minecraft client) {
        boolean undoEnabled = canUndoEditorHistory();
        ImGui.spacing();
        ImGui.beginDisabled(!undoEnabled);
        if (EditorIcons.smallButton("undo", EditorIcons.Icon.UNDO, null)) {
            undo(client);
        }
        ImGui.endDisabled();
        if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip(tr("history.undo.tooltip"));
        }

        sameLineIfFits(EditorIcons.smallButtonSize());
        boolean redoEnabled = canRedoEditorHistory();
        ImGui.beginDisabled(!redoEnabled);
        if (EditorIcons.smallButton("redo", EditorIcons.Icon.REDO, null)) {
            redo(client);
        }
        ImGui.endDisabled();
        if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip(tr("history.redo.tooltip"));
        }
    }

    private void handleHistoryShortcuts(Minecraft client) {
        if (!ImGui.getIO().getKeyCtrl() || editorShortcutsSuppressed()) {
            return;
        }

        boolean redoPressed = ImGui.isKeyPressed(ImGuiKey.Y, false)
            || ImGui.getIO().getKeyShift() && ImGui.isKeyPressed(ImGuiKey.Z, false);
        if (redoPressed) {
            redo(client);
        } else if (ImGui.isKeyPressed(ImGuiKey.Z, false)) {
            undo(client);
        }
    }

    private static boolean editorShortcutsSuppressed() {
        return EditorShortcutPolicy.suppresses(
            ImGui.isAnyItemActive(),
            ImGui.getIO().getWantTextInput()
        );
    }

    private void undo(Minecraft client) {
        finishEditorInteraction();
        if (editorUndo.isEmpty() || lastEditorState == null) {
            setStatus(false, tr("history.nothing_to_undo"));
            return;
        }
        pushEditorHistory(editorRedo, lastEditorState);
        restoreEditorSnapshot(client, editorUndo.removeLast());
        setStatus(true, tr("history.undone"));
    }

    private void redo(Minecraft client) {
        finishEditorInteraction();
        if (editorRedo.isEmpty() || lastEditorState == null) {
            setStatus(false, tr("history.nothing_to_redo"));
            return;
        }
        pushEditorHistory(editorUndo, lastEditorState);
        restoreEditorSnapshot(client, editorRedo.removeLast());
        setStatus(true, tr("history.redone"));
    }

    private boolean canUndoEditorHistory() {
        return editorInteractionStart != null || !editorUndo.isEmpty();
    }

    private boolean canRedoEditorHistory() {
        return editorInteractionStart == null && !editorRedo.isEmpty();
    }

    private void ensureEditorHistoryInitialized(Minecraft client) {
        if (lastEditorState != null) return;
        try {
            lastEditorState = captureEditorSnapshot(client);
        } catch (RuntimeException ignored) {
            // A malformed in-progress field remains dirty. History starts once the
            // complete editor state can be represented again.
        }
    }

    private void captureEditorHistory(Minecraft client) {
        EditorHistorySnapshot current;
        try {
            current = captureEditorSnapshot(client);
        } catch (RuntimeException ignored) {
            return;
        }
        recordEditorHistorySnapshot(current, lastEditorState == null ? 0 : imgui.internal.ImGui.getActiveID());
    }

    /**
     * Records a captured state independently of the native UI's active-control query.
     */
    private void recordEditorHistorySnapshot(EditorHistorySnapshot current, int activeControl) {
        if (lastEditorState == null) {
            lastEditorState = current;
            return;
        }
        if (editorInteractionStart != null
            && editorInteractionControl != 0
            && activeControl != 0
            && activeControl != editorInteractionControl) {
            finishEditorInteraction();
        }
        if (!current.sameState(lastEditorState)) {
            if (editorInteractionStart == null) {
                editorInteractionStart = lastEditorState;
                editorInteractionControl = activeControl;
            }
            lastEditorState = current;
            editorRedo.clear();
        } else {
            // Keep non-historical editor state (for example switching between two
            // equivalent custom-data views) current without creating an undo step.
            lastEditorState = current;
        }
        if (editorInteractionStart != null && activeControl == 0) {
            finishEditorInteraction();
        }
    }

    private void finishEditorInteraction() {
        if (editorInteractionStart == null || lastEditorState == null) return;
        if (!editorInteractionStart.sameState(lastEditorState)) {
            pushEditorHistory(editorUndo, editorInteractionStart);
        }
        editorInteractionStart = null;
        editorInteractionControl = 0;
    }

    private EditorHistorySnapshot captureEditorSnapshot(Minecraft client) {
        Map<String, String> panels = new HashMap<>(pendingPanelHistory.capture(client));
        // These two editors carry extra state alongside their item-stack snapshots.
        capturePendingPanel(panels, "enchantments", enchantmentEditor.isDirty(), () -> enchantmentEditor.pendingStack(client), client);
        capturePendingPanel(panels, "flags", flagEditor.isDirty(), () -> flagEditor.pendingStack(client), client);
        JsonElement componentValue = componentForm.isDirty() ? componentForm.pendingValue() : null;
        JsonElement variantComponentValue = variantComponentEditor.isDirty()
            ? variantComponentEditor.pendingValue(client)
            : null;
        String componentId = (componentValue != null || variantComponentValue != null)
            && selectedComponent != null
            ? selectedComponent.id()
            : "";
        String rawSource = rawStackEditor.isDirty() ? rawStackEditor.pendingSource() : null;
        return new EditorHistorySnapshot(
            ItemStackCodecs.encodeStackSnbt(client, draft),
            cleanDraft == null
                ? ""
                : ItemStackCodecs.encodeStackSnbt(client, cleanDraft.restore(client)),
            itemId.get(),
            count.get(),
            preferences.textInputFormat(),
            loreEditor.historyState(),
            dataEditor.historyState(),
            diamondFireTagEditor.historyState(),
            enchantmentEditor.historyTarget(),
            Map.copyOf(panels),
            panels.containsKey("flags") && flagEditor.isMaxStackSizeOverridden(),
            componentId,
            componentValue == null ? null : componentValue.deepCopy(),
            variantComponentValue == null ? null : variantComponentValue.deepCopy(),
            componentValue != null && useGenericSelectedComponentEditor,
            rawSource
        );
    }

    private static void capturePendingPanel(
        Map<String, String> destination,
        String id,
        boolean dirty,
        Supplier<ItemStack> stack,
        Minecraft client
    ) {
        if (dirty) {
            destination.put(id, ItemStackCodecs.encodeStackSnbt(client, stack.get()));
        }
    }

    private void restoreEditorSnapshot(Minecraft client, EditorHistorySnapshot snapshot) {
        applyingHistory = true;
        try {
            ItemStack restoredDraft = ItemStackCodecs.decodeStackSnbt(client, snapshot.draft());
            setDraft(client, restoredDraft, true);
            cleanDraft = snapshot.cleanDraft().isBlank()
                ? null
                : ItemStackSnapshot.capture(
                client,
                ItemStackCodecs.decodeStackSnbt(client, snapshot.cleanDraft()),
                componentOptions
            );
            if (preferences.textInputFormat() != snapshot.textFormat()) {
                switchTextInputFormat(
                    snapshot.textFormat(),
                    EditorNavigationGuard.Source.PROGRAMMATIC
                );
            }
            itemId.set(snapshot.itemId());
            count.set(snapshot.count());
            basicDirty = !itemId.get().trim().equals(getItemId(draft))
                || Math.clamp(count.get(), 1, MAX_ITEM_COUNT) != draft.getCount();

            loreEditor.restoreHistoryState(snapshot.lore());
            dataEditor.restoreHistoryState(snapshot.data());
            diamondFireTagEditor.restoreHistoryState(snapshot.diamondFire());
            restorePendingPanels(client, snapshot.panels(), snapshot.flagStackSizeOverride());
            restorePendingEnchantments(client, snapshot);
            restorePendingComponentEditors(client, snapshot);
            if (snapshot.rawSource() != null) {
                rawStackEditor.restorePendingSource(snapshot.rawSource());
            }
            draftDirty = cleanDraft == null || !cleanDraft.matches(client, draft);
            lastEditorState = snapshot;
            editorInteractionStart = null;
            editorInteractionControl = 0;
        } finally {
            applyingHistory = false;
        }
    }

    private void restorePendingPanels(Minecraft client, Map<String, String> panels, boolean flagStackSizeOverride) {
        pendingPanelHistory.restore(client, panels);
        restorePendingPanel(client, panels, "flags", stack -> flagEditor.restorePending(client, stack, flagStackSizeOverride));
    }

    private void restorePendingEnchantments(Minecraft client, EditorHistorySnapshot snapshot) {
        String encoded = snapshot.panels().get("enchantments");
        if (encoded != null) {
            enchantmentEditor.restorePending(
                client,
                ItemStackCodecs.decodeStackSnbt(client, encoded),
                snapshot.enchantmentTarget()
            );
        }
    }

    private static void restorePendingPanel(
        Minecraft client,
        Map<String, String> panels,
        String id,
        java.util.function.Consumer<ItemStack> restore
    ) {
        String encoded = panels.get(id);
        if (encoded != null) {
            restore.accept(ItemStackCodecs.decodeStackSnbt(client, encoded));
        }
    }

    private void restorePendingComponentEditors(Minecraft client, EditorHistorySnapshot snapshot) {
        if ((snapshot.componentValue() == null && snapshot.variantComponentValue() == null)
            || snapshot.componentId().isBlank()) return;
        ComponentOption option = componentOptions.stream()
            .filter(candidate -> candidate.id().equals(snapshot.componentId()))
            .findFirst()
            .orElse(null);
        if (option == null) return;
        selectedComponent = option;
        useGenericSelectedComponentEditor = snapshot.genericComponentEditor();
        refreshSelectedComponentEditor(client);
        if (snapshot.componentValue() != null) {
            JsonElement baseline = componentForm.pendingValue();
            componentForm.restorePending(baseline, snapshot.componentValue());
        }
        if (snapshot.variantComponentValue() != null) {
            variantComponentEditor.restorePending(snapshot.variantComponentValue());
        }
    }

    private void clearEditorHistory() {
        editorUndo.clear();
        editorRedo.clear();
        lastEditorState = null;
        editorInteractionStart = null;
        editorInteractionControl = 0;
    }

    private static void pushEditorHistory(
        Deque<EditorHistorySnapshot> destination,
        EditorHistorySnapshot snapshot
    ) {
        destination.addLast(snapshot);
        while (destination.size() > MAX_EDITOR_HISTORY) {
            destination.removeFirst();
        }
    }

    private ItemStack headerPreviewStack() {
        if (!isBasicDirty()) {
            return draft;
        }

        Identifier identifier = Identifier.tryParse(itemId.get().trim());
        if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
            return draft;
        }
        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        if (item == Items.AIR) {
            return draft;
        }

        try {
            ItemStack preview;
            if (!draft.isEmpty() && draft.getItem() == item) {
                preview = draft.copy();
            } else {
                preview = new ItemStack(item);
                if (!draft.isEmpty()) {
                    preview.applyComponents(draft.getComponentsPatch());
                }
            }
            preview.setCount(Math.clamp(count.get(), 1, MAX_ITEM_COUNT));
            loreEditor.applyName(preview);
            return preview;
        } catch (RuntimeException ignored) {
            return draft;
        }
    }

    private boolean drawItemPickerPopup() {
        if (!ImGui.beginPopup(tr("picker.title") + "###item_picker")) {
            return false;
        }
        boolean selectedItem = false;
        try {
            if (ImGui.isWindowAppearing()) {
                ImGui.setKeyboardFocusHere();
            }
            ImGui.setNextItemWidth(420.0f);
            inputTextWithHint("##item_search", tr("picker.search_hint"), itemSearch);
            beginScrollableChild("item_picker_results", 420.0f, 300.0f, ImGuiChildFlags.Borders);
            try {
                String query = itemSearch.get().trim().toLowerCase(Locale.ROOT);
                for (ItemOption option : itemOptions) {
                    if (!query.isEmpty() && !option.searchable().contains(query)) {
                        continue;
                    }
                    String label = option.displayName() + " (" + option.id() + ")";
                    float availableWidth = ImGui.getContentRegionAvailX();
                    if (ImGui.selectable(label + "###pick_" + option.id(), option.id().equals(itemId.get()))) {
                        itemId.set(option.id());
                        basicDirty = true;
                        selectedItem = true;
                        ImGui.closeCurrentPopup();
                    }
                    if (ImGui.isItemHovered() && ImGui.calcTextSizeX(label) > availableWidth) {
                        ImGui.setTooltip(label);
                    }
                }
            } finally {
                ImGui.endChild();
            }
        } finally {
            ImGui.endPopup();
        }
        return selectedItem;
    }

    public boolean inputIdentifierWithSuggestions(
        String id,
        String hint,
        ImString value,
        List<IdSuggestion> suggestions
    ) {
        return autocomplete.input(id, hint, value, suggestions);
    }

    public boolean inputIdentifierWithSuggestions(
        String id,
        FieldHints hints,
        ImString value,
        List<IdSuggestion> suggestions
    ) {
        return autocomplete.input(id, hints, value, suggestions);
    }

    public boolean inputIdentifierWithSuggestions(
        String id,
        String hint,
        ImString value,
        List<IdSuggestion> suggestions,
        String validationError
    ) {
        return autocomplete.input(id, hint, value, suggestions, validationError);
    }

    public boolean inputIdentifierWithSuggestions(
        String id,
        FieldHints hints,
        ImString value,
        List<IdSuggestion> suggestions,
        String validationError
    ) {
        return autocomplete.input(id, hints, value, suggestions, validationError);
    }

    public boolean inputSoundWithPreview(
        Minecraft client,
        String id,
        String hint,
        ImString value,
        List<IdSuggestion> suggestions,
        SoundPreviewKind kind
    ) {
        return inputSoundWithPreview(client, id, hint, value, suggestions, kind, "");
    }


    public boolean inputSoundWithPreview(
        Minecraft client,
        String id,
        String hint,
        ImString value,
        List<IdSuggestion> suggestions,
        SoundPreviewKind kind,
        String validationError
    ) {
        return inputSoundWithPreview(client, id, hint, value, suggestions, kind, validationError, false);
    }

    public boolean inputSoundWithPreview(
        Minecraft client,
        String id,
        FieldHints hints,
        ImString value,
        List<IdSuggestion> suggestions,
        SoundPreviewKind kind,
        String validationError
    ) {
        return inputSoundWithPreview(client, id, hints, value, suggestions, kind, validationError, false);
    }

    public boolean inputSoundWithPreview(
        Minecraft client,
        String id,
        String hint,
        ImString value,
        List<IdSuggestion> suggestions,
        SoundPreviewKind kind,
        String validationError,
        boolean requestFocus
    ) {
        return inputSoundWithPreview(
            client,
            id,
            new FieldHints(hint, ""),
            value,
            suggestions,
            kind,
            validationError,
            requestFocus
        );
    }

    public boolean inputSoundWithPreview(
        Minecraft client,
        String id,
        FieldHints hints,
        ImString value,
        List<IdSuggestion> suggestions,
        SoundPreviewKind kind,
        String validationError,
        boolean requestFocus
    ) {
        float controlsWidth = EditorIcons.smallButtonSize() * 2.0f + ImGui.getStyle().getItemSpacingX();
        float minimumInlineWidth = controlsWidth
            + 120.0f
            + ImGui.getStyle().getCellPaddingX() * 4.0f;
        if (ImGui.getContentRegionAvailX() < minimumInlineWidth) {
            if (requestFocus) ImGui.setKeyboardFocusHere();
            boolean changed = inputIdentifierWithSuggestions(id, hints, value, suggestions, validationError);
            soundPreviews.drawControls(client, id, kind, value.get());
            return changed;
        }
        if (!ImGui.beginTable(
            "sound_preview_row_" + id,
            2,
            ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.NoPadOuterX
        )) {
            if (requestFocus) ImGui.setKeyboardFocusHere();
            return inputIdentifierWithSuggestions(id, hints, value, suggestions, validationError);
        }
        ImGui.tableSetupColumn("Value", ImGuiTableColumnFlags.WidthStretch);
        ImGui.tableSetupColumn("Preview", ImGuiTableColumnFlags.WidthFixed, controlsWidth);
        ImGui.tableNextColumn();
        if (requestFocus) ImGui.setKeyboardFocusHere();
        boolean changed = inputIdentifierWithSuggestions(id, hints, value, suggestions, validationError);
        ImGui.tableNextColumn();
        soundPreviews.drawControls(client, id, kind, value.get());
        ImGui.endTable();
        return changed;
    }

    private static String itemIdentifierProblem(String value) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.isEmpty()) return tr("validation.identifier_required");
        Identifier identifier = Identifier.tryParse(candidate);
        if (identifier == null) return tr("validation.identifier_format");
        if (!BuiltInRegistries.ITEM.containsKey(identifier)
            || BuiltInRegistries.ITEM.getValue(identifier) == Items.AIR) {
            return tr("validation.identifier_unknown");
        }
        return "";
    }

    public void resetAutocomplete() {
        autocomplete.reset();
    }

    public boolean wasLastIdentifierInputSubmitted() {
        return autocomplete.wasLastInputSubmitted();
    }

    private void drawClearSlotConfirmation(Minecraft client) {
        if (ImGui.beginPopupModal(tr("clear.title") + "###clear_target", ImGuiWindowFlags.AlwaysAutoResize)) {
            try {
                ImGui.textWrapped(tr("clear.description", targetDestination()));
                textDisabledWrapped(tr("clear.server_warning"));
                ImGui.spacing();
                ImGui.beginDisabled(!hasCreativeAccess(client));
                if (ImGui.button(tr("clear.confirm"), 120.0f, 0.0f)) {
                    clearSelectedSlot(client);
                    ImGui.closeCurrentPopup();
                }
                ImGui.endDisabled();
                sameLineIfFits(tr("action.cancel"));
                if (ImGui.button(tr("action.cancel"), 100.0f, 0.0f)) {
                    ImGui.closeCurrentPopup();
                }
            } finally {
                ImGui.endPopup();
            }
        }
    }

    private void drawComponentsTab(Minecraft client) {
        if (pendingComponentRecovery != null) {
            drawPendingComponentRecovery(client);
            return;
        }
        ImGui.spacing();
        float workspaceHeight = Math.max(
            1.0f,
            ImGui.getContentRegionAvailY() - ImGui.getStyle().getCellPaddingY() * 2.0f - 1.0f
        );
        float splitWidth = COMPONENT_BROWSER_WIDTH
            + COMPONENT_EDITOR_MIN_WIDTH
            + ImGui.getStyle().getCellPaddingX() * 4.0f;
        if (ImGui.getContentRegionAvailX() < splitWidth) {
            float spacing = ImGui.getStyle().getItemSpacingY();
            float minimumEditorHeight = Math.min(180.0f, workspaceHeight * 0.5f);
            float maximumBrowserHeight = Math.max(1.0f, workspaceHeight - minimumEditorHeight - spacing);
            float preferredBrowserHeight = Math.clamp(workspaceHeight * 0.4f, 140.0f, 240.0f);
            float browserHeight = Math.min(preferredBrowserHeight, maximumBrowserHeight);
            drawComponentBrowserPane(client, browserHeight);
            ImGui.spacing();
            drawSelectedComponentPane(client, Math.max(1.0f, workspaceHeight - browserHeight - spacing));
            return;
        }

        if (ImGui.beginTable(
            "component_workspace",
            2,
            ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.BordersInnerV
        )) {
            ImGui.tableSetupColumn("Component browser", ImGuiTableColumnFlags.WidthFixed, COMPONENT_BROWSER_WIDTH);
            ImGui.tableSetupColumn("Editor", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableNextColumn();
            drawComponentBrowserPane(client, workspaceHeight);

            ImGui.tableNextColumn();
            drawSelectedComponentPane(client, workspaceHeight);
            ImGui.endTable();
        }
    }

    /**
     * Shows an owner without selecting/reloading a different component and losing pending buffers.
     */
    private void drawPendingComponentRecovery(Minecraft client) {
        if (ImGui.button(tr("pending.back_to_components"))) {
            pendingComponentRecovery = null;
            return;
        }
        ImGui.separatorText(pendingComponentRecoveryTitle);
        beginScrollableChild("pending_component_recovery_" + pendingComponentRecovery,
            0.0f, Math.max(1.0f, ImGui.getContentRegionAvailY()), false);
        boolean wasInline = drawingInlineComponentEditor;
        drawingInlineComponentEditor = true;
        try {
            if (focusPendingComponentRecovery) {
                ImGui.setScrollY(0.0f);
                focusPendingComponentRecovery = false;
            }
            switch (pendingComponentRecovery) {
                case "component_form" -> componentForm.draw(client);
                case "variant_component" -> variantComponentEditor.draw(client);
                case "recipes" -> recipesEditor.draw(client);
                case "bees" -> beesEditor.draw(client);
                case "sulfur_cube_content" -> sulfurCubeContentEditor.draw(client);
                default ->
                    throw new IllegalStateException("Unknown pending component owner: " + pendingComponentRecovery);
            }
        } finally {
            drawingInlineComponentEditor = wasInline;
            ImGui.endChild();
        }
    }

    private void requestPendingComponentRecovery(String id, String labelKey, Runnable focusRequest) {
        pendingComponentRecovery = id;
        pendingComponentRecoveryTitle = tr(labelKey);
        focusPendingComponentRecovery = true;
        focusRequest.run();
    }

    private void drawComponentBrowserPane(Minecraft client, float height) {
        ItemEditorTheme.pushPanel();
        beginScrollableChild(
            "component_browser",
            0.0f,
            height,
            ImGuiChildFlags.Borders | ImGuiChildFlags.AlwaysUseWindowPadding
        );
        try {
            ImGui.setNextItemWidth(-1.0f);
            inputTextWithHint("##component_search", tr("components.search_hint"), componentSearch);
            if (drawComponentScopeButton(tr("components.scope.on_item"), ComponentBrowserScope.ON_ITEM)) {
                setComponentScope(ComponentBrowserScope.ON_ITEM);
            }
            sameLineIfFits(tr("components.scope.modified"));
            if (drawComponentScopeButton(tr("components.scope.modified"), ComponentBrowserScope.MODIFIED)) {
                setComponentScope(ComponentBrowserScope.MODIFIED);
            }
            sameLineIfFits(tr("components.scope.all"));
            if (drawComponentScopeButton(tr("components.scope.all"), ComponentBrowserScope.ALL)) {
                setComponentScope(ComponentBrowserScope.ALL);
            }
            if (ImGui.checkbox(tr("components.filter.network_only"), includeNetworkOnlyComponents)) {
                preferences.setIncludeNetworkOnlyComponents(includeNetworkOnlyComponents.get());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(tr("components.filter.network_only.tooltip"));
            }
            if (ImGui.checkbox(tr("components.filter.exclude_dedicated"), excludeDedicatedComponents)) {
                preferences.setExcludeDedicatedComponents(excludeDedicatedComponents.get());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(tr("components.filter.exclude_dedicated.tooltip"));
            }
            if (ImGui.checkbox(tr("components.filter.minecraft_only"), minecraftComponentsOnly)) {
                preferences.setMinecraftComponentsOnly(minecraftComponentsOnly.get());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(tr("components.filter.minecraft_only.tooltip"));
            }
            if (diamondFireActive) {
                ImGui.textDisabled(tr("components.diamondfire_support"));
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(DiamondFireComponentSupport.badgeLegend());
                }
            }
            ImGui.separator();

            String query = componentSearch.get().trim().toLowerCase(Locale.ROOT);
            if (componentScope == ComponentBrowserScope.ALL) {
                ImGui.separatorText(tr("components.section.on_item"));
                drawComponentGroup(client, query, ComponentGroup.PRESENT);
                ImGui.spacing();
                ImGui.separatorText(tr("components.section.common"));
                drawComponentGroup(client, query, ComponentGroup.RECOMMENDED);
                ImGui.spacing();
                ImGui.separatorText(tr("components.section.available"));
                drawComponentGroup(client, query, ComponentGroup.AVAILABLE);
            } else if (componentScope == ComponentBrowserScope.ON_ITEM) {
                drawComponentGroup(client, query, ComponentGroup.PRESENT);
            } else {
                drawComponentGroup(client, query, ComponentGroup.MODIFIED);
            }
        } finally {
            ImGui.endChild();
            ItemEditorTheme.popPanel();
        }
    }

    private void drawComponentGroup(Minecraft client, String query, ComponentGroup group) {
        if (drawComponentRows(client, query, group) == 0) {
            ImGui.textDisabled(tr("components.empty.no_match"));
        }
    }

    private void drawSelectedComponentPane(Minecraft client, float height) {
        ItemEditorTheme.pushPanel();
        beginScrollableChild(
            "selected_component_editor",
            0.0f,
            height,
            ImGuiChildFlags.Borders | ImGuiChildFlags.AlwaysUseWindowPadding
        );
        try {
            if (focusSelectedComponentPane && selectedComponent != null) {
                ImGui.setScrollY(0.0f);
                ImGui.setKeyboardFocusHere();
                focusSelectedComponentPane = false;
            }
            if (selectedComponent == null) {
                ImGui.textWrapped(tr("components.select_prompt"));
            } else {
                drawSelectedComponentPanel(client);
            }
        } finally {
            ImGui.endChild();
            ItemEditorTheme.popPanel();
        }
    }

    private boolean drawComponentScopeButton(String label, ComponentBrowserScope scope) {
        boolean active = componentScope == scope;
        if (active) {
            ItemEditorTheme.pushActiveFilter();
        }
        boolean pressed = ImGui.button(label);
        if (active) {
            ItemEditorTheme.popActiveFilter();
        }
        return pressed;
    }

    private void setComponentScope(ComponentBrowserScope scope) {
        componentScope = scope;
        preferences.setComponentBrowserScope(scope);
    }

    private int drawComponentRows(Minecraft client, String query, ComponentGroup group) {
        int matches = 0;
        boolean showDiamondFireSupport = diamondFireActive;
        int columns = showDiamondFireSupport ? 2 : 1;
        List<ComponentOption> displayedOptions = componentOptions;
        if (group == ComponentGroup.RECOMMENDED) {
            displayedOptions = new ArrayList<>(componentOptions);
            displayedOptions.sort(Comparator
                .comparingInt((ComponentOption option) -> recommendationOrder(
                    ComponentRecommendations.classify(draft, option.id())
                ))
                .thenComparing(ComponentOption::id));
        }
        if (ImGui.beginTable(
            "component_rows_" + group,
            columns,
            ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.NoPadOuterX
        )) {
            ImGui.tableSetupColumn("Component", ImGuiTableColumnFlags.WidthStretch);
            if (showDiamondFireSupport) {
                float badgeWidth = ImGui.calcTextSizeX("DF+")
                    + ImGui.getStyle().getCellPaddingX() * 2.0f
                    + 4.0f;
                ImGui.tableSetupColumn("DF", ImGuiTableColumnFlags.WidthFixed, badgeWidth);
            }
            for (ComponentOption option : displayedOptions) {
                ComponentState state = getComponentState(option.type());
                ComponentRecommendations.Kind recommendation = ComponentRecommendations.classify(
                    draft,
                    option.id()
                );
                boolean groupMatches = switch (group) {
                    case PRESENT -> state == ComponentState.DEFAULT || state == ComponentState.OVERRIDDEN;
                    case RECOMMENDED -> (state == ComponentState.ABSENT || state == ComponentState.REMOVED)
                        && recommendation != ComponentRecommendations.Kind.NONE;
                    case AVAILABLE -> (state == ComponentState.ABSENT || state == ComponentState.REMOVED)
                        && recommendation == ComponentRecommendations.Kind.NONE;
                    case MODIFIED -> state == ComponentState.OVERRIDDEN || state == ComponentState.REMOVED;
                };
                if (!groupMatches
                    || !includeNetworkOnlyComponents.get() && option.type().codec() == null
                    || excludeDedicatedComponents.get() && shouldExcludeAsDedicatedComponent(option.type())
                    || minecraftComponentsOnly.get() && !option.id().startsWith("minecraft:")
                    || !query.isEmpty() && !option.searchable().contains(query)) {
                    continue;
                }
                matches++;
                ImGui.tableNextRow();
                ImGui.tableNextColumn();
                boolean selected = selectedComponent != null && selectedComponent.id().equals(option.id());
                String listLabel = option.id().startsWith("minecraft:")
                    ? option.id().substring("minecraft:".length())
                    : option.id();
                String prefix = state == ComponentState.ABSENT ? "+ " : state.marker() + " ";
                String visibleLabel = ellipsize(prefix + listLabel, Math.max(1.0f, ImGui.getContentRegionAvailX() - 8.0f));
                DiamondFireComponentSupport.Support support = showDiamondFireSupport
                    ? DiamondFireComponentSupport.forComponent(option.id(), option.type())
                    : null;
                if (ImGui.selectable(visibleLabel + "###component_" + option.id(), selected)) {
                    selectComponent(client, option, EditorNavigationGuard.Source.USER);
                }
                if (ImGui.isItemHovered()) {
                    String tooltip = option.id() + "\n" + state.description();
                    if (group == ComponentGroup.RECOMMENDED) {
                        tooltip += "\n\n" + tr(recommendation == ComponentRecommendations.Kind.RELATED
                            ? "components.recommendation.related"
                            : "components.recommendation.frequent", draft.getHoverName().getString());
                    }
                    if (support != null) tooltip += "\n\n" + support.tooltip();
                    ImGui.setTooltip(tooltip);
                }
                drawComponentRowContextMenu(client, option, state);
                if (support != null) {
                    ImGui.tableNextColumn();
                    drawDiamondFireSupportBadge(support);
                }
            }
            ImGui.endTable();
        }
        return matches;
    }

    private void drawComponentRowContextMenu(
        Minecraft client,
        ComponentOption option,
        ComponentState state
    ) {
        if (!ImGui.beginPopupContextItem("component_context_" + option.id())) return;
        try {
            ImGui.textDisabled(option.id());
            ImGui.separator();
            boolean present = state == ComponentState.DEFAULT || state == ComponentState.OVERRIDDEN;
            boolean pending = componentContextActionBlocked(option.type());
            boolean unavailable = !present && option.type().codec() == null;
            ImGui.beginDisabled(pending || unavailable);
            boolean activated = ImGui.menuItem(tr(present
                ? "components.action.remove"
                : "component_form.add_component"));
            ImGui.endDisabled();
            if ((pending || unavailable) && ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
                ImGui.setTooltip(tr(pending
                    ? "components.context.pending"
                    : "components.context.network_only"));
            }
            if (activated) {
                if (present) removeComponentFromBrowser(client, option);
                else addComponentFromBrowser(client, option);
            }
        } finally {
            ImGui.endPopup();
        }
    }

    private boolean addComponentFromBrowser(Minecraft client, ComponentOption option) {
        if (componentContextActionBlocked(option.type())) {
            setStatus(false, tr("error.component_pending"));
            return false;
        }
        if (option.type().codec() == null) {
            setStatus(false, tr("components.context.network_only"));
            return false;
        }
        beginHistoryTransaction(client);
        try {
            if (!prepareDraftForComponentEdit(client)) return false;
            ItemStack edited = draft.copy();
            JsonElement value = componentValueFactory.create(client, draft, option);
            ItemStackCodecs.decodeAndSetComponent(client, edited, option.type(), value);
            ItemStackCodecs.validateStack(edited);
            acceptComponentDraft(client, edited, option.type());
            setStatus(true, tr("message.component_saved", option.id()));
            return true;
        } catch (RuntimeException exception) {
            setStatus(false, tr("error.component_invalid", option.id(), readableMessage(exception)));
            return false;
        } finally {
            endHistoryTransaction(client);
        }
    }

    private boolean removeComponentFromBrowser(Minecraft client, ComponentOption option) {
        if (componentContextActionBlocked(option.type())) {
            setStatus(false, tr("error.component_pending"));
            return false;
        }
        beginHistoryTransaction(client);
        try {
            if (!prepareDraftForComponentEdit(client)) return false;
            ItemStack edited = draft.copy();
            edited.remove(option.type());
            acceptComponentDraft(client, edited, option.type());
            setStatus(true, tr("message.component_removed", option.id()));
            return true;
        } catch (RuntimeException exception) {
            setStatus(false, tr("error.component_invalid", option.id(), readableMessage(exception)));
            return false;
        } finally {
            endHistoryTransaction(client);
        }
    }

    private boolean componentContextActionBlocked(DataComponentType<?> type) {
        return hasPendingEditorChanges(type)
            || selectedComponent != null
            && selectedComponent.type() != type
            && hasPendingEditorChanges(selectedComponent.type());
    }

    private static int recommendationOrder(ComponentRecommendations.Kind kind) {
        return switch (kind) {
            case RELATED -> 0;
            case FREQUENTLY_USED -> 1;
            case NONE -> 2;
        };
    }

    private static void drawDiamondFireSupportBadge(DiamondFireComponentSupport.Support support) {
        switch (support.level()) {
            case CODEBLOCKS -> ImGui.pushStyleColor(ImGuiCol.Text, 0.38f, 0.86f, 0.48f, 1.0f);
            case PARTIAL -> ImGui.pushStyleColor(ImGuiCol.Text, 0.96f, 0.66f, 0.24f, 1.0f);
            case PASSIVE -> ImGui.pushStyleColor(ImGuiCol.Text, 0.94f, 0.76f, 0.30f, 1.0f);
            case VERSION_SENSITIVE -> ImGui.pushStyleColor(ImGuiCol.Text, 0.96f, 0.58f, 0.24f, 1.0f);
            case UNKNOWN -> ImGui.pushStyleColor(ImGuiCol.Text, 0.66f, 0.70f, 0.76f, 1.0f);
            case UNSUPPORTED -> ImGui.pushStyleColor(ImGuiCol.Text, 0.94f, 0.38f, 0.38f, 1.0f);
        }
        ImGui.text(support.badge());
        ImGui.popStyleColor();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("DiamondFire support\n\n" + support.tooltip());
        }
    }

    /**
     * Draws the same compact support hint used by the component browser.
     */
    public void drawDiamondFireComponentHint(DataComponentType<?> type) {
        DiamondFireComponentSupport.Support support = diamondFireComponentHint(type);
        if (support == null) return;
        sameLineIfFits(ImGui.calcTextSizeX(support.badge()));
        drawDiamondFireSupportBadge(support);
    }

    private DiamondFireComponentSupport.Support diamondFireComponentHint(DataComponentType<?> type) {
        if (!diamondFireActive || drawingInlineComponentEditor) return null;
        Identifier identifier = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        return identifier == null ? null : DiamondFireComponentSupport.forComponent(identifier.toString(), type);
    }

    /**
     * Reserves room for support beside a collapsible title, even while its section is closed.
     */
    public boolean drawComponentSectionHeader(String id, String title, DataComponentType<?> type, boolean revealForFocus) {
        DiamondFireComponentSupport.Support support = diamondFireComponentHint(type);
        boolean initiallyExpanded = componentSectionStartsExpanded(type);
        // Match the checkbox's baseline when this header follows a presence control.
        ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, ImGui.getStyle().getCellPaddingX(), 0.0f);
        try {
            if (!ImGui.beginTable(
                id + "_component_header",
                support == null ? 1 : 2,
                ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.NoPadOuterX
            )) {
                return EditorUi.collapsibleHeader(id, title, initiallyExpanded, revealForFocus);
            }
            ImGui.tableSetupColumn("Component", ImGuiTableColumnFlags.WidthStretch);
            if (support != null) {
                ImGui.tableSetupColumn("DF", ImGuiTableColumnFlags.WidthFixed, ImGui.calcTextSizeX(support.badge()));
            }
            ImGui.tableNextColumn();
            boolean expanded = EditorUi.collapsibleHeader(id, title, initiallyExpanded, revealForFocus);
            if (support != null) {
                ImGui.tableNextColumn();
                ImGui.alignTextToFramePadding();
                drawDiamondFireSupportBadge(support);
            }
            ImGui.endTable();
            return expanded;
        } finally {
            ImGui.popStyleVar();
        }
    }

    /**
     * Begins a component card with an always-visible title and compatibility badge.
     */
    public boolean beginComponentCard(String id, String title, DataComponentType<?> type, boolean revealForFocus) {
        if (!EditorUi.beginCard(id, 1, ImGuiTableFlags.SizingStretchProp, revealForFocus)) return false;
        ImGui.tableNextColumn();
        if (!drawComponentSectionHeader(id, title, type, revealForFocus)) {
            EditorUi.endCard();
            return false;
        }
        ImGui.spacing();
        return true;
    }

    private static String ellipsize(String text, float maxWidth) {
        if (ImGui.calcTextSizeX(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        float suffixWidth = ImGui.calcTextSizeX(suffix);
        int low = 0;
        int high = text.length();
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (ImGui.calcTextSizeX(text.substring(0, middle)) + suffixWidth <= maxWidth) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return text.substring(0, low) + suffix;
    }

    private void drawSelectedComponentPanel(Minecraft client) {
        if (!componentCodecProblem.isBlank()) {
            ImGui.textWrapped(tr("status.error", componentCodecProblem));
        }
        if (!componentForm.problem().isBlank()) {
            ImGui.textWrapped(tr("status.error", componentForm.problem()));
        }
        drawSelectedComponentActions(client);
        drawVersionedComponentEditorChoice();
        ImGui.separator();
        drawSelectedComponentEditor(client);
    }

    private void drawVersionedComponentEditorChoice() {
        DedicatedEditorSchema.Coverage coverage = DedicatedEditorSchema.coverage(selectedComponent.type());
        if (coverage.isExact()) return;

        if (!coverage.missingFields().isEmpty()) {
            textDisabledWrapped(tr("version.hidden_fields", String.join(", ", coverage.missingFields())));
            return;
        }

        textDisabledWrapped(tr("version.additional_fields", String.join(", ", coverage.extraFields())));
        boolean pending = hasPendingEditorChanges(selectedComponent.type());
        ImGui.beginDisabled(pending);
        if (ImGui.button(useGenericSelectedComponentEditor
            ? tr("components.action.use_dedicated")
            : tr("components.action.edit_all_fields"))) {
            useGenericSelectedComponentEditor = !useGenericSelectedComponentEditor;
            focusSelectedComponentPane = true;
            autocomplete.reset();
        }
        ImGui.endDisabled();
        if (pending && ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip(tr("components.action.switch_blocked"));
        }
    }

    private void drawSelectedComponentActions(Minecraft client) {
        DataComponentType<?> type = selectedComponent.type();
        boolean pendingChanges = hasPendingEditorChanges(type);
        boolean differsFromOpening = !componentMatchesOpening(client, type);

        ImGui.beginDisabled(!differsFromOpening && !pendingChanges);
        if (EditorIcons.smallButton(
            "restore_component_opening",
            EditorIcons.Icon.UNDO,
            tr("components.action.restore_opening")
        )) {
            restoreSelectedComponentOpening(client);
        }
        ImGui.endDisabled();
        sameLineIfFits(EditorIcons.smallButtonSize());
        ImGui.beginDisabled(!draft.hasNonDefault(type) && !pendingChanges);
        if (EditorIcons.smallButton(
            "restore_component_default",
            EditorIcons.Icon.DEFAULTS,
            tr("components.action.restore_default")
        )) {
            resetSelectedComponent(client);
        }
        ImGui.endDisabled();
        sameLineIfFits(EditorIcons.smallButtonSize());
        ImGui.beginDisabled(!draft.has(type));
        if (EditorIcons.smallButton(
            "remove_component",
            EditorIcons.Icon.REMOVE,
            tr("components.action.remove")
        )) {
            removeSelectedComponent(client);
        }
        ImGui.endDisabled();
    }

    private boolean hasPendingEditorChanges(DataComponentType<?> type) {
        if (type == DataComponents.CUSTOM_DATA) {
            return componentForm.isDirty()
                || dataEditor.isComponentDirty(type)
                || diamondFireTagEditor.isDirty();
        }
        InlineComponentEditorRegistry.Kind inlineKind = InlineComponentEditorRegistry.kind(type);
        if (inlineKind != null) {
            return switch (inlineKind) {
                case ADVENTURE_PREDICATE -> adventureEditor.isDirty();
                case RECIPES -> recipesEditor.isDirty();
                case BEES -> beesEditor.isDirty();
                case SULFUR_CUBE_CONTENT -> sulfurCubeContentEditor.isDirty();
                case ENTITY_VARIANT -> variantComponentEditor.isDirty();
            };
        }
        if (selectedComponent != null
            && selectedComponent.type() == type
            && (useGenericSelectedComponentEditor
            || !DedicatedEditorSchema.coverage(type).missingFields().isEmpty())) {
            return componentForm.isDirty();
        }
        EditorTab dedicatedTab = dedicatedTabFor(type);
        if (dedicatedTab == null) {
            return componentForm.isDirty();
        }
        return switch (dedicatedTab) {
            case NAME -> loreEditor.isDirty();
            case ENCHANTMENTS -> enchantmentEditor.isDirty();
            case ATTRIBUTES -> attributeEditor.isDirty();
            case FLAGS -> type == DataComponents.TOOLTIP_DISPLAY
                ? flagEditor.isComponentDirty(type)
                : componentForm.isDirty();
            case ADVENTURE -> adventureEditor.isDirty();
            case USE -> useEditor.isDirty();
            case COMBAT -> combatEditor.isDirty();
            case MUSIC -> musicEditor.isDirty();
            case COMPASS -> compassEditor.isDirty();
            case CONTAINER -> containerEditor.isDirty();
            case EQUIPMENT -> equipmentEditor.isDirty();
            case PRESENTATION -> appearanceEditor.isDirty();
            case BOOK -> bookEditor.isComponentDirty(type);
            case MAP -> mapEditor.isDirty();
            case BANNER -> bannerEditor.isDirty();
            case DECORATED_POT -> decoratedPotEditor.isDirty();
            case POTION -> potionEditor.isDirty();
            case FIREWORK -> fireworkEditor.isDirty();
            case PLAYER_HEAD -> playerHeadEditor.isDirty();
            case DATA -> dataEditor.isComponentDirty(type);
            default -> componentForm.isDirty();
        };
    }

    private void drawSelectedComponentEditor(Minecraft client) {
        drawingInlineComponentEditor = true;
        ImGui.pushID("focused_component_editor");
        try {
            drawSelectedComponentEditorContents(client);
        } finally {
            ImGui.popID();
            drawingInlineComponentEditor = false;
        }
    }

    private void drawSelectedComponentEditorContents(Minecraft client) {
        DataComponentType<?> type = selectedComponent.type();
        if (type.codec() == null) {
            return;
        }
        if (type == DataComponents.CUSTOM_DATA && diamondFireTagEditor.isDirty()) {
            ImGui.textWrapped(tr("components.diamondfire_pending"));
            return;
        }
        if (drawRegisteredInlineComponentEditor(client, type)) return;
        DedicatedEditorSchema.Coverage coverage = DedicatedEditorSchema.coverage(type);
        if (useGenericSelectedComponentEditor || !coverage.missingFields().isEmpty()) {
            drawGenericSelectedComponentEditor(client, type);
            return;
        }

        EditorTab dedicatedTab = dedicatedTabFor(type);
        if (dedicatedTab == null) {
            drawGenericSelectedComponentEditor(client, type);
            return;
        }
        switch (dedicatedTab) {
            case NAME -> loreEditor.drawComponent(client, type);
            case PRESENTATION -> appearanceEditor.drawComponent(client, type);
            case FLAGS -> drawFocusedFlagEditor(client, type);
            case ATTRIBUTES -> attributeEditor.drawComponent(client);
            case ENCHANTMENTS -> enchantmentEditor.drawComponent(client, type);
            case COMBAT -> combatEditor.drawComponent(client, type);
            case MUSIC -> musicEditor.drawComponent(client, type);
            case COMPASS -> compassEditor.drawComponent(client, type);
            case CONTAINER -> containerEditor.drawComponent(client, type);
            case ADVENTURE -> adventureEditor.drawComponent(client, type);
            case EQUIPMENT -> equipmentEditor.drawComponent(client, type);
            case USE -> useEditor.drawComponent(client, type);
            case BOOK -> bookEditor.drawComponent(client, type);
            case MAP -> mapEditor.drawComponent(client, type);
            case BANNER -> bannerEditor.drawComponent(client, type);
            case DECORATED_POT -> decoratedPotEditor.drawComponent(client, type);
            case POTION -> potionEditor.drawComponent(client, type);
            case FIREWORK -> fireworkEditor.drawComponent(client, type);
            case PLAYER_HEAD -> playerHeadEditor.drawComponent(client, type);
            case DATA -> dataEditor.drawComponent(client, type, diamondFireTagEditor.isDirty());
            case DIAMONDFIRE, COMPONENTS, RAW -> drawGenericSelectedComponentEditor(client, type);
        }
    }

    private void drawFocusedFlagEditor(Minecraft client, DataComponentType<?> type) {
        if (type == DataComponents.TOOLTIP_DISPLAY) {
            flagEditor.drawComponent(client, type);
            return;
        }
        // The other flag controls are scalar components, so their focused codec
        // forms are less ambiguous than duplicating the whole tab.
        componentForm.draw(client);
    }

    private boolean drawRegisteredInlineComponentEditor(
        Minecraft client,
        DataComponentType<?> type
    ) {
        InlineComponentEditorRegistry.Kind kind = InlineComponentEditorRegistry.kind(type);
        if (kind == null) return false;
        switch (kind) {
            case ADVENTURE_PREDICATE -> adventureEditor.drawComponent(client, type);
            case RECIPES -> recipesEditor.draw(client);
            case BEES -> beesEditor.draw(client);
            case SULFUR_CUBE_CONTENT -> sulfurCubeContentEditor.draw(client);
            case ENTITY_VARIANT -> variantComponentEditor.draw(client);
        }
        return true;
    }

    private void drawGenericSelectedComponentEditor(Minecraft client, DataComponentType<?> type) {
        ComponentState state = getComponentState(type);
        if (state == ComponentState.ABSENT || state == ComponentState.REMOVED) {
            textDisabledWrapped(state == ComponentState.REMOVED
                ? tr("components.preview_removed")
                : tr("components.preview_absent"));
            ImGui.spacing();
        }
        componentForm.draw(client);
    }

    private static EditorTab dedicatedTabFor(DataComponentType<?> type) {
        if (type.codec() == null) {
            return null;
        }
        if (DataEditorPanel.supports(type)) {
            return EditorTab.DATA;
        }
        if (type == DataComponents.CUSTOM_NAME || type == DataComponents.LORE) {
            return EditorTab.NAME;
        }
        if (type == DataComponents.ENCHANTMENTS || type == DataComponents.STORED_ENCHANTMENTS) {
            return EditorTab.ENCHANTMENTS;
        }
        if (type == DataComponents.ATTRIBUTE_MODIFIERS) {
            return EditorTab.ATTRIBUTES;
        }
        if (isFlagComponent(type)) {
            return EditorTab.FLAGS;
        }
        if (AdventureEditorPanel.supports(type)) {
            return EditorTab.ADVENTURE;
        }
        if (UseEditorPanel.supports(type)) {
            return EditorTab.USE;
        }
        if (CombatEditorPanel.supports(type)) {
            return EditorTab.COMBAT;
        }
        if (MusicEditorPanel.supports(type)) {
            return EditorTab.MUSIC;
        }
        if (CompassEditorPanel.supports(type)) {
            return EditorTab.COMPASS;
        }
        if (ContainerEditorPanel.supports(type)) {
            return EditorTab.CONTAINER;
        }
        if (EquipmentEditorPanel.supports(type)) {
            return EditorTab.EQUIPMENT;
        }
        if (AppearanceEditorPanel.supports(type)) {
            return EditorTab.PRESENTATION;
        }
        if (BookEditorPanel.supports(type)) {
            return EditorTab.BOOK;
        }
        if (MapEditorPanel.supports(type)) {
            return EditorTab.MAP;
        }
        if (BannerEditorPanel.supports(type)) {
            return EditorTab.BANNER;
        }
        if (DecoratedPotEditorPanel.supports(type)) {
            return EditorTab.DECORATED_POT;
        }
        if (PotionEditorPanel.supports(type)) {
            return EditorTab.POTION;
        }
        if (FireworkEditorPanel.supports(type)) {
            return EditorTab.FIREWORK;
        }
        return PlayerHeadEditorPanel.supports(type) ? EditorTab.PLAYER_HEAD : null;
    }

    /**
     * Whether the Components browser's "Exclude dedicated" filter should hide this type.
     */
    public static boolean shouldExcludeAsDedicatedComponent(DataComponentType<?> type) {
        if (InlineComponentEditorRegistry.supports(type)) {
            return false;
        }
        return dedicatedTabFor(type) != null
            && DedicatedEditorSchema.coverage(type).missingFields().isEmpty();
    }

    private void drawLoreTab(Minecraft client) {
        loreEditor.draw(client);
    }

    private Component liveNamePreview() {
        return loreEditor.liveNamePreview();
    }

    private void drawEnchantmentsTab(Minecraft client) {
        enchantmentEditor.draw(client);
    }

    private void drawAttributesTab(Minecraft client) {
        attributeEditor.draw(client);
    }

    private void drawFlagsTab(Minecraft client) {
        flagEditor.draw(client);
    }

    private void drawRawTab(Minecraft client) {
        rawStackEditor.draw(client);
    }

    private void drawStatusFooter() {
        if (!status.isBlank()) {
            ImGui.spacing();
            ImGui.textWrapped(statusSuccess ? status : tr("status.error", status));
        }
    }

    private void open(Minecraft client, boolean newItem) {
        if (client.player == null || client.level == null || !ImGuiMC.isImguiLoaded()) {
            return;
        }
        Screen screen = currentScreen(client);
        if (screen != null && !(screen instanceof AbstractContainerScreen<?>)) {
            return;
        }
        target = newItem
            ? EditorItemTarget.newItem(client)
            : EditorItemTarget.contextual(client, screen);
        sourceInventory = SourceInventorySnapshot.capture(client, screen);
        ItemStack openingStack = target.openingStack();
        if (!hasLastUiStateItem || !sameUiStateItem(lastUiStateItem, openingStack)) {
            beginEditorUiSession();
            resetCollapsibleStates();
            resetScrollStates();
            dataEditor.resetCodeEditorView();
            rawStackEditor.resetCodeEditorView();
        }
        returnScreen = screen;
        if (screen != null) {
            setCurrentScreen(client, null);
        }

        open = true;
        capturingTextInput = false;
        historyEnabled = false;
        nestedEditorFrames.clear();
        nestedOpenRequest = null;
        requestedEditorTab = null;
        pendingComponentRecovery = null;
        focusItemId = false;
        history = new ItemEditHistory();
        openingItem = new OpeningItemSnapshot();
        history.clear();
        clearEditorHistory();
        resetCardMeasurements();
        windowVisible.set(true);
        focusSinkOnOpen = true;
        autocomplete.reset();
        refreshCatalogs(client);
        client.mouseHandler.releaseMouse();
        containerEditor.beginItemSession();
        loadTargetItem(client);
        historyEnabled = true;
    }

    private void beginEditorUiSession() {
        editorUiSessionId = nextEditorUiSessionId++;
        collapseAbsentComponentsForSession = preferences.collapseAbsentComponents();
    }

    private void close(Minecraft client) {
        if (!open) {
            return;
        }

        open = false;
        capturingTextInput = false;
        rememberUiStateItem(client);
        historyEnabled = false;
        applyingHistory = false;
        history.clear();
        clearEditorHistory();
        nestedEditorFrames.clear();
        nestedOpenRequest = null;
        requestedEditorTab = null;
        pendingComponentRecovery = null;
        pendingFailureFocus.clear();
        focusItemId = false;
        cleanDraft = null;
        windowVisible.set(false);
        selectedComponent = null;
        focusSelectedComponentPane = false;
        componentForm.clear();
        soundPreviews.stop(client);
        nativePreviews.clear();
        Screen screenToRestore = returnScreen;
        returnScreen = null;
        sourceInventory = SourceInventorySnapshot.EMPTY;
        if (!hasScreen(client) && canRestoreScreen(client, screenToRestore)) {
            setCurrentScreen(client, screenToRestore);
        }
        if (client.player != null && client.level != null && !hasScreen(client)) {
            client.mouseHandler.grabMouse();
        }
    }

    private void rememberUiStateItem(Minecraft client) {
        try {
            lastUiStateItem = cleanDraft == null
                ? target.openingStack()
                : cleanDraft.restore(client);
        } catch (RuntimeException ignored) {
            lastUiStateItem = target.openingStack();
        }
        hasLastUiStateItem = true;
    }

    private static boolean sameUiStateItem(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) return first.isEmpty() && second.isEmpty();
        return ItemStack.matches(first.copyWithCount(1), second.copyWithCount(1));
    }

    private void refreshCatalogs(Minecraft client) {
        EditorCatalog catalog = EditorCatalog.load(client);
        EditorVersionProfile.refresh(catalog.components());
        componentValueFactory.setCatalog(catalog);
        componentForm.setCatalog(catalog);
        variantComponentEditor.setCatalog(catalog);
        adventureEditor.setCatalog(catalog);
        useEditor.setCatalog(catalog);
        combatEditor.setCatalog(catalog);
        musicEditor.setCatalog(catalog);
        compassEditor.setCatalog(catalog);
        containerEditor.setCatalog(catalog);
        equipmentEditor.setCatalog(catalog);
        appearanceEditor.setCatalog(catalog);
        mapEditor.setCatalog(catalog);
        bannerEditor.setCatalog(catalog);
        decoratedPotEditor.setCatalog(catalog);
        potionEditor.setCatalog(catalog);
        playerHeadEditor.setCatalog(catalog);
        recipesEditor.setCatalog(catalog);
        beesEditor.setCatalog(catalog);
        sulfurCubeContentEditor.setCatalog(catalog);
        dataEditor.setCatalog(catalog);
        itemOptions = catalog.items();
        itemIdSuggestions = catalog.itemIds();
        enchantmentEditor.setSuggestions(catalog.enchantmentIds());
        attributeEditor.setSuggestions(catalog.attributeIds());
        componentOptions = catalog.components();
    }

    private void loadTargetItem(Minecraft client) {
        setDraft(client, target.openingStack(), false);
        if (target.kind() == EditorItemTarget.Kind.NEW_ITEM) {
            setStatus(true, tr("open.new_item"));
        } else if (target.kind() == EditorItemTarget.Kind.HOVERED_COPY) {
            setStatus(true, tr("open.hovered_copy", targetDestination()));
        } else {
            status = "";
        }
        captureOpeningState(client);
    }

    private void revertDraft(Minecraft client) {
        beginHistoryTransaction(client);
        try {
            // Restore discards pending input, including invalid values and conflicting representations.
            if (!openingItem.isAvailable()) {
                loadTargetItem(client);
                return;
            }
            restoreOpeningItem(client);
        } finally {
            endHistoryTransaction(client);
        }
    }

    private void resetDraftToDefaults(Minecraft client) {
        beginHistoryTransaction(client);
        try {
            try {
                // Keep valid header item/count edits without parsing any component buffers.
                // An invalid item ID falls back to the existing draft's item so reset remains usable.
                Item currentItem = draft.getItem();
                Identifier requestedId = Identifier.tryParse(itemId.get().trim());
                if (requestedId != null && BuiltInRegistries.ITEM.containsKey(requestedId)) {
                    Item requestedItem = BuiltInRegistries.ITEM.getValue(requestedId);
                    if (requestedItem != Items.AIR) {
                        currentItem = requestedItem;
                    }
                }
                if (currentItem == Items.AIR) {
                    setStatus(false, tr("error.no_item"));
                    return;
                }
                ItemStack defaults = currentItem.getDefaultInstance();
                defaults.setCount(Math.clamp(count.get(), 1, MAX_ITEM_COUNT));
                setDraft(client, defaults, true);
                setStatus(true, tr("message.defaults_restored"));
            } catch (RuntimeException exception) {
                setStatus(false, tr("error.reset_defaults", readableMessage(exception)));
            }
        } finally {
            endHistoryTransaction(client);
        }
    }

    private void captureOpeningState(Minecraft client) {
        openingItem.capture(client, draft, componentOptions);
    }

    private boolean componentMatchesOpening(Minecraft client, DataComponentType<?> type) {
        return openingItem.componentMatches(client, draft, type);
    }

    private void setDraft(Minecraft client, ItemStack stack, boolean dirty) {
        recordHistoryReplacement(client, stack);
        draft = stack.copy();
        resetCardMeasurements();
        autocomplete.reset();
        syncCommonFields();
        loreEditor.syncLore(draft);
        enchantmentEditor.sync(draft);
        attributeEditor.sync(draft);
        flagEditor.sync(draft);
        adventureEditor.sync(client, draft);
        useEditor.sync(client, draft);
        combatEditor.sync(client, draft);
        musicEditor.sync(client, draft);
        compassEditor.sync(client, draft);
        containerEditor.sync(client, draft);
        equipmentEditor.sync(client, draft);
        appearanceEditor.sync(client, draft);
        bookEditor.sync(draft);
        mapEditor.sync(draft);
        bannerEditor.sync(client, draft);
        decoratedPotEditor.sync(draft);
        potionEditor.sync(client, draft);
        fireworkEditor.sync(client, draft);
        playerHeadEditor.sync(client, draft);
        recipesEditor.sync(client, draft);
        beesEditor.sync(client, draft);
        sulfurCubeContentEditor.sync(client, draft);
        dataEditor.sync(draft);
        diamondFireTagEditor.sync(draft);
        rawStackEditor.sync(client, draft);
        refreshSelectedComponentEditor(client);
        pendingComponentRecovery = null;
        focusItemId = false;
        basicDirty = false;
        if (dirty) {
            draftDirty = cleanDraft == null || !cleanDraft.matches(client, draft);
        } else {
            cleanDraft = ItemStackSnapshot.capture(client, draft, componentOptions);
            draftDirty = false;
        }
    }

    private void recordHistoryReplacement(Minecraft client, ItemStack replacement) {
        if (historyEnabled && !applyingHistory) {
            history.record(client, draft, replacement, componentOptions);
        }
    }

    private void beginHistoryTransaction(Minecraft client) {
        if (historyEnabled && !applyingHistory) {
            history.begin(client, draft, componentOptions);
        }
    }

    private void endHistoryTransaction(Minecraft client) {
        if (historyEnabled && !applyingHistory) {
            history.end(client, draft);
        }
    }

    /**
     * Restores the one untouched item copy captured when the editor opened.
     */
    public boolean restoreOpeningItem(Minecraft client) {
        if (!openingItem.isAvailable()) {
            setStatus(false, tr("error.no_opening_item"));
            return false;
        }

        try {
            setDraft(client, openingItem.restore(client), false);
            setStatus(true, tr("message.opening_restored"));
            return true;
        } catch (RuntimeException exception) {
            setStatus(false, tr("error.restore_opening", readableMessage(exception)));
            return false;
        }
    }

    private void restoreSelectedComponentOpening(Minecraft client) {
        if (selectedComponent == null || !openingItem.isAvailable()) {
            setStatus(false, tr("error.no_opening_component"));
            return;
        }

        DataComponentType<?> type = selectedComponent.type();
        try {
            ItemStack edited = openingItem.restoreComponent(client, draft, type);
            ItemStackCodecs.validateStack(edited);
            acceptComponentDraft(client, edited, type);
            setStatus(true, tr("message.component_restored", selectedComponent.id()));
        } catch (RuntimeException exception) {
            setStatus(false, tr("error.restore_component", selectedComponent.id(), readableMessage(exception)));
        }
    }

    private void syncCommonFields() {
        itemId.set(getItemId(draft));
        count.set(Math.max(1, draft.getCount()));

        loreEditor.syncName(draft);
    }

    private boolean refreshDraftForChangedItemId(Minecraft client) {
        beginHistoryTransaction(client);
        try {
            String requestedId = itemId.get().trim();
            Identifier identifier = Identifier.tryParse(requestedId);
            if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
                setStatus(false, tr("error.unknown_item", requestedId));
                return false;
            }

            Item requestedItem = BuiltInRegistries.ITEM.getValue(identifier);
            if (requestedItem == Items.AIR) {
                setStatus(false, tr("error.empty_item"));
                return false;
            }
            if (!draft.isEmpty() && draft.getItem() == requestedItem) {
                return false;
            }

            // Commit in-progress editors against the old prototype first. This keeps
            // intentional edits in the component patch without turning untouched old
            // defaults into overrides on the new item.
            if (!commitPendingFormsBeforeItemChange(client)) {
                return false;
            }

            basicDirty = true;
            if (!updateBasicDraft(client, false)) {
                return false;
            }

            ItemStack refreshed = draft.copy();
            setDraft(client, refreshed, true);
            nativePreviews.setItemIconStack(draft);
            setStatus(true, tr("message.item_changed"));
            return true;
        } finally {
            endHistoryTransaction(client);
        }
    }

    private boolean commitPendingFormsBeforeItemChange(Minecraft client) {
        String requestedId = itemId.get();
        int requestedCount = count.get();
        boolean requestedBasicDirty = isBasicDirty();

        itemId.set(getItemId(draft));
        count.set(Math.max(1, draft.getCount()));
        basicDirty = false;
        try {
            return commitPendingForms(client);
        } finally {
            itemId.set(requestedId);
            count.set(requestedCount);
            basicDirty = requestedBasicDirty;
        }
    }

    private boolean updateBasicDraft(Minecraft client, boolean announce) {
        String requestedId = itemId.get().trim();
        Identifier identifier = Identifier.tryParse(requestedId);
        if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
            setStatus(false, tr("error.unknown_item", requestedId));
            return false;
        }

        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        if (item == Items.AIR) {
            setStatus(false, tr("error.empty_item"));
            return false;
        }

        try {
            ItemStack edited;
            if (!draft.isEmpty() && draft.getItem() == item) {
                edited = draft.copy();
            } else {
                edited = new ItemStack(item);
                if (!draft.isEmpty()) {
                    edited.applyComponents(draft.getComponentsPatch());
                }
            }

            edited.setCount(Math.clamp(count.get(), 1, MAX_ITEM_COUNT));
            loreEditor.applyName(edited);
            recordHistoryReplacement(client, edited);
            draft = edited.copy();
            rawStackEditor.sync(client, draft);
            refreshSelectedComponentEditor(client);
            basicDirty = false;
            loreEditor.syncName(draft);
            draftDirty = cleanDraft == null || !cleanDraft.matches(client, draft);
            if (announce) {
                setStatus(true, tr("message.item_updated"));
            }
            return true;
        } catch (RuntimeException exception) {
            setStatus(false, tr("error.update", readableMessage(exception)));
            return false;
        }
    }

    public void acceptSectionDraft(Minecraft client, ItemStack edited) {
        boolean itemChanged = !draft.isEmpty() && draft.getItem() != edited.getItem();
        recordHistoryReplacement(client, edited);
        draft = edited.copy();
        if (itemChanged && !isBasicDirty()) {
            itemId.set(getItemId(draft));
            count.set(Math.max(1, draft.getCount()));
            basicDirty = false;
        }
        resetCardMeasurements();
        rawStackEditor.sync(client, draft);
        refreshSelectedComponentEditor(client);
        draftDirty = cleanDraft == null || !cleanDraft.matches(client, draft);
    }

    /**
     * Accepts one focused data component without discarding another pending Data view.
     */
    public void acceptDataDraft(
        Minecraft client,
        ItemStack edited,
        DataComponentType<?> type
    ) {
        acceptSectionDraft(client, edited);
        dataEditor.syncComponent(type, draft);
        if (type == DataComponents.CUSTOM_DATA) diamondFireTagEditor.sync(draft);
    }

    /**
     * Accepts custom data changed through the DiamondFire tag editor.
     */
    public void acceptCustomDataDraft(Minecraft client, ItemStack edited) {
        acceptDataDraft(client, edited, DataComponents.CUSTOM_DATA);
    }

    public boolean switchTextInputFormat(TextInputFormat targetFormat) {
        return switchTextInputFormat(targetFormat, EditorNavigationGuard.Source.USER);
    }

    private boolean switchTextInputFormat(
        TextInputFormat targetFormat,
        EditorNavigationGuard.Source source
    ) {
        if (preferences.textInputFormat() == targetFormat) return true;

        Runnable loreChange;
        try {
            loreChange = loreEditor.prepareTextFormatChange(targetFormat);
        } catch (RuntimeException exception) {
            return rejectTextFormatChange(
                source,
                EditorTab.NAME,
                "tab.name",
                "name_text",
                exception
            );
        }

        Runnable bookChange;
        try {
            bookChange = bookEditor.prepareTextFormatChange(targetFormat);
        } catch (RuntimeException exception) {
            return rejectTextFormatChange(
                source,
                EditorTab.BOOK,
                "tab.book",
                "book_content",
                exception
            );
        }

        Runnable attributeChange;
        try {
            attributeChange = attributeEditor.prepareTextFormatChange(targetFormat);
        } catch (RuntimeException exception) {
            return rejectTextFormatChange(
                source,
                EditorTab.ATTRIBUTES,
                "tab.attributes",
                "attribute_text",
                exception
            );
        }

        EditorNavigationGuard.Decision decision = EditorNavigationGuard.decide(
            source,
            EditorNavigationGuard.Transition.COMPATIBLE_RETARGET,
            false,
            null
        );
        if (!decision.allowed()) return false;

        loreChange.run();
        bookChange.run();
        attributeChange.run();
        preferences.setTextInputFormat(targetFormat);
        setStatus(true, tr("message.format_changed", targetFormat.label()));
        return true;
    }

    private boolean rejectTextFormatChange(
        EditorNavigationGuard.Source source,
        EditorTab ownerTab,
        String ownerTranslationKey,
        String focusTarget,
        RuntimeException exception
    ) {
        EditorNavigationGuard.Decision decision = EditorNavigationGuard.decide(
            source,
            EditorNavigationGuard.Transition.COMPATIBLE_RETARGET,
            false,
            new EditorNavigationGuard.Blocker(
                tr(ownerTranslationKey),
                ownerTab.name().toLowerCase(Locale.ROOT),
                EditorNavigationGuard.PendingState.INVALID,
                tr(
                    "navigation.invalid",
                    tr(ownerTranslationKey),
                    readableMessage(exception)
                ),
                new EditorNavigationGuard.FocusRequest(focusTarget)
            )
        );
        setStatus(false, decision.blocker().explanation());
        requestedEditorTab = ownerTab;
        return false;
    }

    /**
     * Accepts a complete stack decoded by the advanced raw editor.
     */
    public void acceptRawDraft(Minecraft client, ItemStack edited) {
        setDraft(client, edited, true);
    }

    private void applyDraftToSlot(Minecraft client) {
        if (!nestedEditorFrames.isEmpty()) {
            applyNestedItemEdit(client);
            return;
        }
        if (!hasCreativeAccess(client)) {
            setStatus(false, tr("error.creative_required"));
            return;
        }
        if (!commitPendingForms(client)) {
            return;
        }

        try {
            ItemStackCodecs.validateStack(draft);
            ItemStack localCopy = draft.copy();
            target.write(client, localCopy);

            setDraft(client, localCopy, false);
            setStatus(true, tr("message.applied", targetDestination()));
        } catch (RuntimeException exception) {
            setStatus(false, tr("error.validation", readableMessage(exception)));
        }
    }

    private void giveDraftCopy(Minecraft client) {
        if (!hasCreativeAccess(client)) {
            setStatus(false, tr("error.creative_required"));
            return;
        }
        if (!commitPendingForms(client)) {
            return;
        }

        try {
            ItemStackCodecs.validateStack(draft);
            int inventorySlot = Objects.requireNonNull(client.player).getInventory().getFreeSlot();
            if (inventorySlot < 0) {
                throw new IllegalStateException("The inventory has no empty slot");
            }
            int menuSlot = EditorItemTarget.creativeMenuSlot(inventorySlot);
            ItemStack copy = draft.copy();
            client.player.getInventory().setItem(inventorySlot, copy);
            Objects.requireNonNull(client.gameMode).handleCreativeModeItemAdd(copy.copy(), menuSlot);
            setStatus(true, tr("message.copy_given", inventorySlot + 1));
        } catch (RuntimeException exception) {
            setStatus(false, tr("error.give", readableMessage(exception)));
        }
    }

    private boolean commitPendingForms(Minecraft client) {
        List<PendingEditTransaction.Participant<ItemStack>> participants;
        try {
            participants = pendingParticipants(client);
        } catch (RuntimeException exception) {
            setStatus(false, tr("error.pending_registration", readableMessage(exception)));
            return false;
        }
        PendingEditTransaction.Result<ItemStack> prepared = PendingEditTransaction.prepare(
            draft,
            ItemStack::copy,
            ItemStackCodecs::validateStack,
            participants
        );
        if (!prepared.succeeded()) {
            reportPendingFailure(prepared.failure());
            return false;
        }
        if (prepared.included().isEmpty()) return true;

        EditorHistorySnapshot beforeCommit;
        try {
            beforeCommit = captureEditorSnapshot(client);
        } catch (RuntimeException exception) {
            setStatus(false, tr("error.pending_editor", tr("tab.components"), readableMessage(exception)));
            return false;
        }

        beginHistoryTransaction(client);
        try {
            setDraft(client, prepared.candidate(), true);
            return true;
        } catch (RuntimeException exception) {
            String problem = readableMessage(exception);
            try {
                restoreEditorSnapshot(client, beforeCommit);
            } catch (RuntimeException rollbackException) {
                problem += "; " + tr("error.pending_rollback", readableMessage(rollbackException));
            }
            setStatus(false, tr("error.validation", problem));
            return false;
        } finally {
            endHistoryTransaction(client);
        }
    }

    private List<PendingEditTransaction.Participant<ItemStack>> pendingParticipants(Minecraft client) {
        pendingFailureFocus.clear();
        buildingPendingEditorRegistry = new PendingEditorRegistry();
        List<PendingEditTransaction.Participant<ItemStack>> participants = new ArrayList<>();
        participants.add(pendingParticipant(
            "component_form", componentForm, "pending.owner.component_form", EditorTab.COMPONENTS, "selected_component_editor",
            componentForm::isDirty,
            componentOwnership(selectedComponent == null ? null : selectedComponent.type()),
            () -> pendingSelectedComponentStack(client, componentForm.validatedPendingValue()),
            () -> requestPendingComponentRecovery("component_form", "pending.owner.component_form", componentForm::requestPendingFocus)
        ));
        participants.add(pendingParticipant(
            "variant_component", variantComponentEditor, "pending.owner.variant_component", EditorTab.COMPONENTS, "variant_value",
            variantComponentEditor::isDirty,
            componentOwnership(variantComponentEditor.pendingType()),
            () -> pendingSelectedComponentStack(client, variantComponentEditor.pendingValue(client)),
            () -> requestPendingComponentRecovery("variant_component", "pending.owner.variant_component", variantComponentEditor::requestPendingFocus)
        ));
        participants.add(pendingParticipant(
            "basic_name_lore", loreEditor, "tab.name", EditorTab.NAME, "item_id",
            () -> isBasicDirty() || loreEditor.isDirty(),
            pendingBasicOwnership(),
            () -> pendingBasicNameAndLoreStack(client),
            this::requestBasicPendingFocus
        ));
        participants.add(pendingParticipant(
            "enchantments", enchantmentEditor, "tab.enchantments", EditorTab.ENCHANTMENTS, null,
            enchantmentEditor::isDirty,
            componentOwnership(enchantmentEditor.pendingComponentType()),
            () -> enchantmentEditor.pendingStack(client),
            enchantmentEditor::requestPendingFocus
        ));
        participants.add(pendingParticipant(
            "attributes", attributeEditor, "tab.attributes", EditorTab.ATTRIBUTES, null,
            attributeEditor::isDirty,
            componentOwnership(DataComponents.ATTRIBUTE_MODIFIERS),
            () -> attributeEditor.pendingStack(client),
            attributeEditor::requestPendingFocus
        ));
        participants.add(pendingParticipant(
            "flags", flagEditor, "tab.flags", EditorTab.FLAGS, null,
            flagEditor::isDirty,
            componentOwnership(ItemEditor::isFlagComponent),
            () -> flagEditor.pendingStack(client),
            flagEditor::requestPendingFocus
        ));
        participants.add(pendingParticipant(
            "adventure", adventureEditor, "tab.adventure", EditorTab.ADVENTURE, null,
            adventureEditor::isDirty,
            componentOwnership(AdventureEditorPanel::supports),
            () -> adventureEditor.pendingStack(client),
            adventureEditor::requestPendingFocus
        ));
        participants.add(pendingParticipant(
            "use", useEditor, "tab.use", EditorTab.USE, null,
            useEditor::isDirty,
            componentOwnership(UseEditorPanel::supports),
            () -> useEditor.pendingStack(client),
            useEditor::requestPendingFocus
        ));
        participants.add(pendingParticipant(
            "combat", combatEditor, "tab.combat", EditorTab.COMBAT, null,
            combatEditor::isDirty,
            componentOwnership(CombatEditorPanel::supports),
            () -> combatEditor.pendingStack(client),
            combatEditor::requestPendingFocus
        ));
        participants.add(pendingParticipant(
            "music", musicEditor, "tab.music", EditorTab.MUSIC, null,
            musicEditor::isDirty,
            componentOwnership(MusicEditorPanel::supports),
            () -> musicEditor.pendingStack(client),
            musicEditor::requestPendingFocus
        ));
        participants.add(pendingParticipant(
            "compass", compassEditor, "tab.compass", EditorTab.COMPASS, null,
            compassEditor::isDirty,
            componentOwnership(CompassEditorPanel::supports),
            () -> compassEditor.pendingStack(client),
            compassEditor::requestPendingFocus
        ));
        participants.add(pendingParticipant(
            "container", containerEditor, "tab.container", EditorTab.CONTAINER, null,
            containerEditor::isDirty,
            componentOwnership(ContainerEditorPanel::supports),
            () -> containerEditor.pendingStack(client),
            containerEditor::requestPendingFocus
        ));
        participants.add(pendingParticipant(
            "equipment", equipmentEditor, "tab.equipment", EditorTab.EQUIPMENT, null,
            equipmentEditor::isDirty,
            componentOwnership(EquipmentEditorPanel::supports),
            () -> equipmentEditor.pendingStack(client),
            equipmentEditor::requestPendingFocus
        ));
        participants.add(pendingParticipant(
            "appearance", appearanceEditor, "tab.presentation", EditorTab.PRESENTATION, null,
            appearanceEditor::isDirty,
            componentOwnership(AppearanceEditorPanel::supports),
            () -> appearanceEditor.pendingStack(client),
            appearanceEditor::requestPendingFocus
        ));
        participants.add(pendingParticipant(
            "book", bookEditor, "tab.book", EditorTab.BOOK, null,
            bookEditor::isDirty,
            componentOwnership(BookEditorPanel::supports),
            () -> bookEditor.pendingStack(client),
            bookEditor::requestPendingFocus
        ));
        participants.add(pendingParticipant(
            "map", mapEditor, "tab.map", EditorTab.MAP, null,
            mapEditor::isDirty,
            componentOwnership(MapEditorPanel::supports),
            () -> mapEditor.pendingStack(client),
            mapEditor::requestPendingFocus
        ));
        participants.add(pendingParticipant(
            "banner", bannerEditor, "tab.banner", EditorTab.BANNER, null,
            bannerEditor::isDirty,
            componentOwnership(BannerEditorPanel::supports),
            () -> bannerEditor.pendingStack(client),
            bannerEditor::requestPendingFocus
        ));
        participants.add(pendingParticipant(
            "decorated_pot", decoratedPotEditor, "tab.decorated_pot", EditorTab.DECORATED_POT, null,
            decoratedPotEditor::isDirty,
            componentOwnership(DecoratedPotEditorPanel::supports),
            () -> decoratedPotEditor.pendingStack(client),
            decoratedPotEditor::requestPendingFocus
        ));
        participants.add(pendingParticipant(
            "potion", potionEditor, "tab.potion", EditorTab.POTION, null,
            potionEditor::isDirty,
            componentOwnership(PotionEditorPanel::supports),
            () -> potionEditor.pendingStack(client),
            potionEditor::requestPendingFocus
        ));
        participants.add(pendingParticipant(
            "firework", fireworkEditor, "tab.firework", EditorTab.FIREWORK, null,
            fireworkEditor::isDirty,
            componentOwnership(FireworkEditorPanel::supports),
            () -> fireworkEditor.pendingStack(client),
            fireworkEditor::requestPendingFocus
        ));
        participants.add(pendingParticipant(
            "player_head", playerHeadEditor, "tab.player_head", EditorTab.PLAYER_HEAD, null,
            playerHeadEditor::isDirty,
            componentOwnership(PlayerHeadEditorPanel::supports),
            () -> playerHeadEditor.pendingStack(client),
            playerHeadEditor::requestPendingFocus
        ));
        participants.add(pendingParticipant(
            "recipes", recipesEditor, "panel.recipes.section.recipes.title", EditorTab.COMPONENTS, null,
            recipesEditor::isDirty,
            componentOwnership(RecipesEditorPanel::supports),
            () -> recipesEditor.pendingStack(client),
            () -> requestPendingComponentRecovery("recipes", "panel.recipes.section.recipes.title", recipesEditor::requestPendingFocus)
        ));
        participants.add(pendingParticipant(
            "bees", beesEditor, "panel.bees.section.occupants.title", EditorTab.COMPONENTS, null,
            beesEditor::isDirty,
            componentOwnership(BeesEditorPanel::supports),
            () -> beesEditor.pendingStack(client),
            () -> requestPendingComponentRecovery("bees", "panel.bees.section.occupants.title", beesEditor::requestPendingFocus)
        ));
        participants.add(pendingParticipant(
            "sulfur_cube_content", sulfurCubeContentEditor, "panel.sulfur_cube_content.section.content.title", EditorTab.COMPONENTS, null,
            sulfurCubeContentEditor::isDirty,
            componentOwnership(SulfurCubeContentEditorPanel::supports),
            () -> sulfurCubeContentEditor.pendingStack(client),
            () -> requestPendingComponentRecovery("sulfur_cube_content", "panel.sulfur_cube_content.section.content.title", sulfurCubeContentEditor::requestPendingFocus)
        ));
        participants.add(pendingParticipant(
            "data", dataEditor, "tab.data", EditorTab.DATA, null,
            dataEditor::isDirty,
            componentOwnership(dataEditor.pendingComponentType()),
            dataEditor::pendingStack,
            dataEditor::requestPendingFocus
        ));
        participants.add(pendingParticipant(
            "diamondfire", diamondFireTagEditor, "tab.diamondfire", EditorTab.DIAMONDFIRE, null,
            diamondFireTagEditor::isDirty,
            componentOwnership(DataComponents.CUSTOM_DATA),
            () -> diamondFireTagEditor.pendingStack(client),
            diamondFireTagEditor::requestPendingFocus
        ));
        participants.add(pendingParticipant(
            "raw", rawStackEditor, "tab.raw", EditorTab.RAW, "raw_source",
            rawStackEditor::isDirty,
            Set.of(PendingEditTransaction.WHOLE_VALUE),
            () -> rawStackEditor.pendingStack(client),
            rawStackEditor::requestPendingFocus
        ));
        buildingPendingEditorRegistry.verifyCoverage(this);
        pendingEditorRegistry = buildingPendingEditorRegistry;
        buildingPendingEditorRegistry = null;
        return List.copyOf(participants);
    }

    private PendingEditTransaction.Participant<ItemStack> pendingParticipant(
        String id,
        Object owner,
        String labelKey,
        EditorTab route,
        String focusTarget,
        Supplier<Boolean> dirty,
        Set<String> ownership,
        Supplier<ItemStack> preview,
        Runnable focusRequest
    ) {
        buildingPendingEditorRegistry.register(id, owner, dirty);
        pendingFailureFocus.put(id, Objects.requireNonNull(focusRequest));
        return new PendingEditTransaction.Participant<>(
            id,
            tr(labelKey),
            route.name(),
            focusTarget,
            dirty,
            ownership,
            input -> previewPendingStack(input, preview)
        );
    }

    private ItemStack previewPendingStack(ItemStack input, Supplier<ItemStack> preview) {
        ItemStack previous = pendingPreviewDraft;
        pendingPreviewDraft = input.copy();
        try {
            return preview.get().copy();
        } finally {
            pendingPreviewDraft = previous;
        }
    }

    private ItemStack pendingSelectedComponentStack(Minecraft client, JsonElement value) {
        if (selectedComponent == null) {
            throw new IllegalStateException(tr("error.no_component_selected"));
        }
        ItemStack edited = draft().copy();
        ItemStackCodecs.decodeAndSetComponent(client, edited, selectedComponent.type(), value);
        ItemStackCodecs.validateStack(edited);
        return edited;
    }

    private void requestBasicPendingFocus() {
        if (!itemIdentifierProblem(itemId.get()).isBlank() || !loreEditor.isDirty()) {
            focusItemId = true;
        } else {
            loreEditor.requestPendingFocus();
        }
    }

    private ItemStack pendingBasicNameAndLoreStack(Minecraft client) {
        String requestedId = itemId.get().trim();
        Identifier identifier = Identifier.tryParse(requestedId);
        if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
            throw new IllegalArgumentException(tr("error.unknown_item", requestedId));
        }
        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        if (item == Items.AIR) {
            throw new IllegalArgumentException(tr("error.empty_item"));
        }

        ItemStack base = draft();
        ItemStack edited;
        if (!base.isEmpty() && base.getItem() == item) {
            edited = base.copy();
        } else {
            edited = new ItemStack(item);
            if (!base.isEmpty()) edited.applyComponents(base.getComponentsPatch());
        }
        edited.setCount(Math.clamp(count.get(), 1, MAX_ITEM_COUNT));
        return previewPendingStack(edited, () -> loreEditor.pendingStack(client));
    }

    private Set<String> pendingBasicOwnership() {
        java.util.LinkedHashSet<String> ownership = new java.util.LinkedHashSet<>();
        if (isBasicDirty()) {
            ownership.add("$item");
            ownership.add("$count");
        }
        if (loreEditor.isNameDirty()) {
            ownership.add(componentOwnerId(DataComponents.CUSTOM_NAME));
        }
        if (loreEditor.isLoreDirty()) {
            ownership.add(componentOwnerId(DataComponents.LORE));
        }
        return Set.copyOf(ownership);
    }

    private static Set<String> componentOwnership(DataComponentType<?>... types) {
        java.util.LinkedHashSet<String> ownership = new java.util.LinkedHashSet<>();
        for (DataComponentType<?> type : types) {
            if (type != null) ownership.add(componentOwnerId(type));
        }
        return Set.copyOf(ownership);
    }

    private static Set<String> componentOwnership(Predicate<DataComponentType<?>> supports) {
        java.util.LinkedHashSet<String> ownership = new java.util.LinkedHashSet<>();
        for (DataComponentType<?> type : BuiltInRegistries.DATA_COMPONENT_TYPE) {
            if (supports.test(type)) ownership.add(componentOwnerId(type));
        }
        return Set.copyOf(ownership);
    }

    private static String componentOwnerId(DataComponentType<?> type) {
        Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        return id == null ? type.toString() : id.toString();
    }

    private void reportPendingFailure(PendingEditTransaction.Failure failure) {
        requestedEditorTab = EditorTab.valueOf(failure.route());
        pendingComponentRecovery = null;
        focusItemId = false;
        Runnable focusRequest = pendingFailureFocus.get(failure.participantId());
        if (focusRequest != null) focusRequest.run();
        if (failure.kind() == PendingEditTransaction.FailureKind.CONFLICT) {
            String owner = PendingEditTransaction.WHOLE_VALUE.equals(failure.detail())
                ? tr("pending.owner.whole_stack")
                : failure.detail();
            setStatus(false, tr(
                "error.pending_conflict",
                failure.participantLabel(),
                failure.conflictingParticipantLabel(),
                owner
            ));
            return;
        }
        setStatus(false, tr(
            "error.pending_editor",
            failure.participantLabel(),
            failure.detail()
        ));
    }

    private void clearSelectedSlot(Minecraft client) {
        if (!hasCreativeAccess(client)) {
            setStatus(false, tr("error.creative_required"));
            return;
        }

        target.write(client, ItemStack.EMPTY);
        setDraft(client, Items.STONE.getDefaultInstance(), false);
        setStatus(true, tr("message.cleared", targetDestination()));
    }

    private void refreshSelectedComponentEditor(Minecraft client) {
        componentCodecProblem = "";
        componentForm.clear();
        variantComponentEditor.clear();
        if (selectedComponent == null) {
            return;
        }

        DataComponentType<?> type = selectedComponent.type();
        InlineComponentEditorRegistry.Kind inlineKind = InlineComponentEditorRegistry.kind(type);
        if (inlineKind != null) {
            switch (inlineKind) {
                case RECIPES -> recipesEditor.syncComponent(client, type, draft);
                case BEES -> beesEditor.syncComponent(client, type, draft);
                case SULFUR_CUBE_CONTENT -> sulfurCubeContentEditor.syncComponent(client, type, draft);
                case ADVENTURE_PREDICATE, ENTITY_VARIANT -> {
                    // Their full draft state or encoded value is synchronized below.
                }
            }
        }
        if (type == DataComponents.ENCHANTMENTS || type == DataComponents.STORED_ENCHANTMENTS) {
            enchantmentEditor.selectComponent(type);
        }
        if (type.codec() == null) {
            componentCodecProblem = tr("components.codec.network_only");
            return;
        }
        try {
            Object value = draft.get(type);
            JsonElement encoded = value == null
                ? componentValueFactory.create(client, draft, selectedComponent)
                : ItemStackCodecs.encodeComponent(client, type, value);
            componentForm.load(encoded);
            variantComponentEditor.sync(selectedComponent, encoded);
        } catch (RuntimeException exception) {
            componentForm.clear();
            variantComponentEditor.clear();
            componentCodecProblem = tr("components.codec.encode_failed", readableMessage(exception));
        }
    }


    public void setSelectedComponent(Minecraft client, JsonElement value) {
        setSelectedComponent(client, value, true);
    }

    public boolean setSelectedComponent(Minecraft client, JsonElement value, boolean announce) {
        if (selectedComponent == null) {
            return false;
        }
        beginHistoryTransaction(client);
        try {
            if (!prepareDraftForComponentEdit(client)) {
                return false;
            }
            ItemStack edited = draft.copy();
            ItemStackCodecs.decodeAndSetComponent(client, edited, selectedComponent.type(), value);
            String id = selectedComponent.id();
            acceptComponentDraft(client, edited, selectedComponent.type());
            if (announce) {
                setStatus(true, tr("message.component_saved", id));
            }
            return true;
        } catch (RuntimeException exception) {
            setStatus(false, tr("error.component_invalid", selectedComponent.id(), readableMessage(exception)));
            return false;
        } finally {
            endHistoryTransaction(client);
        }
    }

    public void removeSelectedComponent(Minecraft client) {
        removeSelectedComponent(client, true);
    }

    private boolean removeSelectedComponent(Minecraft client, boolean announce) {
        if (selectedComponent == null) {
            return false;
        }
        beginHistoryTransaction(client);
        try {
            if (!prepareDraftForComponentEdit(client)) {
                return false;
            }
            ItemStack edited = draft.copy();
            edited.remove(selectedComponent.type());
            String id = selectedComponent.id();
            acceptComponentDraft(client, edited, selectedComponent.type());
            if (announce) {
                setStatus(true, tr("message.component_removed", id));
            }
            return true;
        } finally {
            endHistoryTransaction(client);
        }
    }

    public void resetSelectedComponent(Minecraft client) {
        if (selectedComponent == null) {
            return;
        }
        beginHistoryTransaction(client);
        try {
            if (!prepareDraftForComponentEdit(client)) {
                return;
            }
            DataComponentType<?> type = selectedComponent.type();
            String id = selectedComponent.id();
            try {
                ItemStack edited = draft.copy();
                ItemStackCodecs.resetComponentToPrototype(edited, type);
                acceptComponentDraft(client, edited, type);
                setStatus(true, tr("message.component_reset", id));
            } catch (RuntimeException exception) {
                setStatus(false, tr("error.reset_component", id, readableMessage(exception)));
            }
        } finally {
            endHistoryTransaction(client);
        }
    }

    public void reloadSelectedComponent(Minecraft client) {
        autocomplete.reset();
        componentForm.discardChanges();
        setStatus(true, tr("message.discarded"));
    }

    private void acceptComponentDraft(Minecraft client, ItemStack edited, DataComponentType<?> type) {
        acceptSectionDraft(client, edited);
        if (type == DataComponents.CUSTOM_NAME) {
            loreEditor.syncName(draft);
        } else if (type == DataComponents.LORE) {
            loreEditor.syncLore(draft);
        } else if (type == DataComponents.ENCHANTMENTS || type == DataComponents.STORED_ENCHANTMENTS) {
            enchantmentEditor.reloadComponent(type, draft);
        } else if (type == DataComponents.ATTRIBUTE_MODIFIERS) {
            attributeEditor.sync(draft);
        } else if (DataEditorPanel.supports(type)) {
            dataEditor.syncComponent(type, draft);
            if (type == DataComponents.CUSTOM_DATA) diamondFireTagEditor.sync(draft);
        } else if (isFlagComponent(type)) {
            flagEditor.syncComponent(type, draft);
        } else if (AdventureEditorPanel.supports(type)) {
            adventureEditor.syncComponent(client, type, draft);
        } else if (UseEditorPanel.supports(type)) {
            useEditor.syncComponent(client, type, draft);
        } else if (CombatEditorPanel.supports(type)) {
            combatEditor.syncComponent(client, type, draft);
        } else if (MusicEditorPanel.supports(type)) {
            musicEditor.syncComponent(client, type, draft);
        } else if (CompassEditorPanel.supports(type)) {
            compassEditor.syncComponent(client, type, draft);
        } else if (ContainerEditorPanel.supports(type)) {
            containerEditor.syncComponent(client, type, draft);
        } else if (EquipmentEditorPanel.supports(type)) {
            equipmentEditor.syncComponent(client, type, draft);
        } else if (AppearanceEditorPanel.supports(type)) {
            appearanceEditor.syncComponent(client, type, draft);
        } else if (BookEditorPanel.supports(type)) {
            bookEditor.syncComponent(type, draft);
        } else if (MapEditorPanel.supports(type)) {
            mapEditor.syncComponent(type, draft);
        } else if (BannerEditorPanel.supports(type)) {
            bannerEditor.syncComponent(client, type, draft);
        } else if (DecoratedPotEditorPanel.supports(type)) {
            decoratedPotEditor.syncComponent(type, draft);
        } else if (PotionEditorPanel.supports(type)) {
            potionEditor.syncComponent(client, type, draft);
        } else if (FireworkEditorPanel.supports(type)) {
            fireworkEditor.syncComponent(client, type, draft);
        } else if (PlayerHeadEditorPanel.supports(type)) {
            playerHeadEditor.syncComponent(client, type, draft);
        } else if (RecipesEditorPanel.supports(type)) {
            recipesEditor.syncComponent(client, type, draft);
        } else if (BeesEditorPanel.supports(type)) {
            beesEditor.syncComponent(client, type, draft);
        } else if (SulfurCubeContentEditorPanel.supports(type)) {
            sulfurCubeContentEditor.syncComponent(client, type, draft);
        }
    }

    private static boolean isFlagComponent(DataComponentType<?> type) {
        return type == DataComponents.MAX_STACK_SIZE
            || type == DataComponents.MAX_DAMAGE
            || type == DataComponents.DAMAGE
            || type == DataComponents.REPAIR_COST
            || type == DataComponents.UNBREAKABLE
            || type == DataComponents.GLIDER
            || type == DataComponents.INTANGIBLE_PROJECTILE
            || type == DataComponents.ENCHANTMENT_GLINT_OVERRIDE
            || type == DataComponents.RARITY
            || type == DataComponents.TOOLTIP_DISPLAY;
    }

    private boolean prepareDraftForComponentEdit(Minecraft client) {
        return !isBasicDirty() || updateBasicDraft(client, false);
    }

    private ComponentState getComponentState(DataComponentType<?> type) {
        boolean present = draft.has(type);
        boolean changed = draft.hasNonDefault(type);
        if (present && changed) {
            return ComponentState.OVERRIDDEN;
        }
        if (present) {
            return ComponentState.DEFAULT;
        }
        if (changed) {
            return ComponentState.REMOVED;
        }
        return ComponentState.ABSENT;
    }

    /**
     * Resolves a component section's expansion state for its first visit in this item session.
     */
    public boolean componentSectionStartsExpanded(DataComponentType<?>... types) {
        ComponentState[] states = new ComponentState[types.length];
        for (int index = 0; index < types.length; index++) {
            states[index] = getComponentState(types[index]);
        }
        return ComponentSectionInitialState.resolve(
            collapseAbsentComponentsForSession,
            drawingInlineComponentEditor,
            states
        );
    }

    private static String getItemId(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.toString();
    }

    private static boolean hasCreativeAccess(Minecraft client) {
        return client.player != null && client.gameMode != null && client.player.hasInfiniteMaterials();
    }

    private String targetDescription() {
        if (!nestedEditorFrames.isEmpty()) {
            NestedEditorFrame frame = nestedEditorFrames.getLast();
            return tr(
                switch (frame.destinationKind()) {
                    case CONTAINER -> "target.contained_item";
                    case BUNDLE -> "target.bundle_item";
                    case SULFUR_CUBE -> "target.sulfur_cube_item";
                },
                frame.destination() + 1,
                nestedEditorFrames.size()
            );
        }
        return switch (target.kind()) {
            case HELD_ITEM -> tr("target.held", targetDestination());
            case PLAYER_INVENTORY -> tr("target.hovered_inventory", targetDestination());
            case CONTAINER -> tr("target.container", targetDestination());
            case HOVERED_COPY -> tr("target.hovered_copy", targetDestination());
            case NEW_ITEM -> tr("target.new_item", targetDestination());
        };
    }

    private String targetDestination() {
        if (target.kind() == EditorItemTarget.Kind.CONTAINER) {
            return tr("target.slot.container", target.containerSlot() + 1);
        }
        int slot = target.inventorySlot();
        if (slot >= 0 && slot < 9) return tr("target.slot.hotbar", slot + 1);
        if (slot >= 9 && slot < 36) return tr("target.slot.inventory", slot - 8);
        return switch (slot) {
            case 36 -> tr("target.slot.boots");
            case 37 -> tr("target.slot.leggings");
            case 38 -> tr("target.slot.chestplate");
            case 39 -> tr("target.slot.helmet");
            case 40 -> tr("target.slot.offhand");
            default -> tr("target.slot.player_inventory", slot + 1);
        };
    }

    private static Screen currentScreen(Minecraft client) {
        //? if >=26.2 {
        return client.gui.screen();
        //?} else {
        /*return client.screen;
         *///?}
    }

    private static void setCurrentScreen(Minecraft client, Screen screen) {
        //? if >=26.2 {
        client.gui.setScreen(screen);
        //?} else {
        /*client.setScreen(screen);
         *///?}
    }

    private static boolean canRestoreScreen(Minecraft client, Screen screen) {
        return screen != null && client.player != null && client.level != null;
    }

    private static boolean hasScreen(Minecraft client) {
        return currentScreen(client) != null;
    }

    public ItemStack draft() {
        return pendingPreviewDraft == null ? draft : pendingPreviewDraft;
    }

    public boolean hasPendingDiamondFireTagEdits() {
        return diamondFireTagEditor.isDirty();
    }

    public void setPendingVanillaBookType(boolean signed) {
        Identifier currentId = Identifier.tryParse(itemId.get().trim());
        Item currentItem = currentId == null ? null : BuiltInRegistries.ITEM.getValue(currentId);
        if (currentItem != Items.WRITABLE_BOOK && currentItem != Items.WRITTEN_BOOK) {
            return;
        }
        Item targetItem = signed ? Items.WRITTEN_BOOK : Items.WRITABLE_BOOK;
        if (currentItem == targetItem) {
            return;
        }
        itemId.set(BuiltInRegistries.ITEM.getKey(targetItem).toString());
        basicDirty = true;
        resetCardMeasurements();
    }

    private ItemStack withLiveCount(ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }
        int previewCount = Math.clamp(count.get(), 1, MAX_ITEM_COUNT);
        if (stack.getCount() == previewCount) {
            return stack;
        }
        ItemStack counted = stack.copy();
        counted.setCount(previewCount);
        return counted;
    }

    public void drawTooltipPreview(Minecraft client, ItemStack stack, String id) {
        nativePreviews.drawTooltip(withLiveCount(stack), id);
    }

    public void setItemIconPreview(ItemStack stack) {
        nativePreviews.setItemIconStack(withLiveCount(stack));
    }

    public float itemSlotPreviewSize(Minecraft client) {
        return nativePreviews.itemSlotSize(client);
    }

    public NativeItemPreviews.ItemSlotInteraction drawItemSlotPreview(
        Minecraft client,
        String id,
        ItemStack stack,
        boolean selected,
        boolean overflow
    ) {
        return nativePreviews.drawItemSlot(client, id, stack, selected, overflow);
    }

    public void markLastDrawnItemSlotAsDropTarget() {
        nativePreviews.markLastDrawnItemSlotAsDropTarget();
    }

    public void markLastDrawnItemSlotAsDragSource() {
        nativePreviews.markLastDrawnItemSlotAsDragSource();
    }

    public void markLastDrawnItemSlotAsInsertionGuide() {
        nativePreviews.markLastDrawnItemSlotAsInsertionGuide();
    }

    public void markLastDrawnItemSlotAsInsertionTarget(boolean after) {
        nativePreviews.markLastDrawnItemSlotAsInsertionTarget(after);
    }

    public void drawDraggedItemPreview(Minecraft client, ItemStack stack) {
        nativePreviews.drawDraggedItem(client, stack);
    }

    public void requestContainedItemEdit(int index, ItemStack stack) {
        requestNestedItemEdit(index, stack, NestedItemDestination.CONTAINER);
    }

    public void requestBundleItemEdit(int index, ItemStack stack) {
        requestNestedItemEdit(index, stack, NestedItemDestination.BUNDLE);
    }

    public void requestSulfurCubeItemEdit(ItemStack stack) {
        requestNestedItemEdit(0, stack, NestedItemDestination.SULFUR_CUBE);
    }

    private void requestNestedItemEdit(int index, ItemStack stack, NestedItemDestination destinationKind) {
        if (stack == null || stack.isEmpty()) return;
        if (nestedEditorFrames.size() >= MAX_NESTED_EDITOR_DEPTH) {
            setStatus(false, tr("nested.depth_limit", MAX_NESTED_EDITOR_DEPTH));
            return;
        }
        nestedOpenRequest = new NestedOpenRequest(
            switch (destinationKind) {
                case CONTAINER -> Math.clamp(index, 0, 255);
                case BUNDLE -> Math.max(0, index);
                case SULFUR_CUBE -> 0;
            },
            stack,
            drawingInlineComponentEditor ? EditorTab.COMPONENTS : EditorTab.CONTAINER,
            destinationKind
        );
    }

    private void openRequestedNestedItem(Minecraft client) {
        NestedOpenRequest request = nestedOpenRequest;
        nestedOpenRequest = null;
        if (request == null) return;
        if (nestedEditorFrames.size() >= MAX_NESTED_EDITOR_DEPTH) {
            setStatus(false, tr("nested.depth_limit", MAX_NESTED_EDITOR_DEPTH));
            return;
        }

        NestedEditorFrame frame = null;
        try {
            finishEditorInteraction();
            EditorHistorySnapshot parentState = captureEditorSnapshot(client);
            frame = new NestedEditorFrame(
                request.index(),
                request.destinationKind(),
                parentState,
                history,
                openingItem,
                List.copyOf(editorUndo),
                List.copyOf(editorRedo),
                selectedComponent == null ? "" : selectedComponent.id(),
                useGenericSelectedComponentEditor,
                containerEditor.captureSessionUiState(),
                editorUiSessionId,
                collapseAbsentComponentsForSession,
                request.returnTab(),
                status,
                statusSuccess
            );
            nestedEditorFrames.addLast(frame);

            historyEnabled = false;
            history = new ItemEditHistory();
            openingItem = new OpeningItemSnapshot();
            clearEditorHistory();
            selectedComponent = null;
            useGenericSelectedComponentEditor = false;
            focusSelectedComponentPane = false;
            componentForm.clear();
            containerEditor.beginItemSession();
            beginEditorUiSession();
            setDraft(client, request.stack(), false);
            captureOpeningState(client);
            requestedEditorTab = EditorTab.NAME;
            focusSinkOnOpen = true;
            nativePreviews.clear();
            soundPreviews.stop(client);
            historyEnabled = true;
            setStatus(true, tr(
                switch (request.destinationKind()) {
                    case CONTAINER -> "nested.opened";
                    case BUNDLE -> "nested.opened.bundle";
                    case SULFUR_CUBE -> "nested.opened.sulfur_cube";
                },
                request.index() + 1
            ));
        } catch (RuntimeException exception) {
            if (frame != null && !nestedEditorFrames.isEmpty()
                && nestedEditorFrames.getLast() == frame) {
                nestedEditorFrames.removeLast();
                restoreNestedParent(client, frame);
            }
            historyEnabled = true;
            setStatus(false, tr("nested.open_failed", readableMessage(exception)));
        }
    }

    private void applyNestedItemEdit(Minecraft client) {
        if (nestedEditorFrames.isEmpty()) return;
        if (!commitPendingForms(client)) return;

        ItemStack child = draft.copy();
        try {
            ItemStackCodecs.validateStack(child);
        } catch (RuntimeException exception) {
            setStatus(false, tr("error.validation", readableMessage(exception)));
            return;
        }

        NestedEditorFrame frame = nestedEditorFrames.removeLast();
        try {
            restoreNestedParent(client, frame);
            switch (frame.destinationKind()) {
                case CONTAINER -> containerEditor.replaceContainedItemFromChild(frame.destination(), child);
                case BUNDLE -> containerEditor.replaceBundleItemFromChild(frame.destination(), child);
                case SULFUR_CUBE -> sulfurCubeContentEditor.replaceItemFromChild(child);
            }
            EditorHistorySnapshot appliedState = captureEditorSnapshot(client);
            if (!frame.parentState().sameState(appliedState)) {
                pushEditorHistory(editorUndo, frame.parentState());
                editorRedo.clear();
            }
            lastEditorState = appliedState;
            editorInteractionStart = null;
            editorInteractionControl = 0;
            setStatus(true, tr(
                switch (frame.destinationKind()) {
                    case CONTAINER -> "nested.applied";
                    case BUNDLE -> "nested.applied.bundle";
                    case SULFUR_CUBE -> "nested.applied.sulfur_cube";
                },
                frame.destination() + 1
            ));
        } catch (RuntimeException exception) {
            setStatus(false, tr("nested.apply_failed", readableMessage(exception)));
        }
    }

    private void cancelNestedItemEdit(Minecraft client) {
        if (nestedEditorFrames.isEmpty()) return;
        NestedEditorFrame frame = nestedEditorFrames.removeLast();
        try {
            restoreNestedParent(client, frame);
            switch (frame.destinationKind()) {
                case CONTAINER -> containerEditor.requestContainedItemFocus(frame.destination());
                case BUNDLE -> containerEditor.requestBundleItemFocus(frame.destination());
                case SULFUR_CUBE -> sulfurCubeContentEditor.requestItemFocus();
            }
            setStatus(true, tr(
                switch (frame.destinationKind()) {
                    case CONTAINER -> "nested.cancelled";
                    case BUNDLE -> "nested.cancelled.bundle";
                    case SULFUR_CUBE -> "nested.cancelled.sulfur_cube";
                },
                frame.destination() + 1
            ));
        } catch (RuntimeException exception) {
            setStatus(false, tr("nested.cancel_failed", readableMessage(exception)));
        }
    }

    private void restoreNestedParent(Minecraft client, NestedEditorFrame frame) {
        historyEnabled = false;
        try {
            history = frame.parentHistory();
            openingItem = frame.parentOpeningItem();
            selectedComponent = frame.selectedComponentId().isBlank()
                ? null
                : componentOptions.stream()
                .filter(option -> option.id().equals(frame.selectedComponentId()))
                .findFirst()
                .orElse(null);
            useGenericSelectedComponentEditor = frame.genericComponentEditor();
            editorUiSessionId = frame.editorUiSessionId();
            collapseAbsentComponentsForSession = frame.collapseAbsentComponents();
            restoreEditorSnapshot(client, frame.parentState());
            containerEditor.restoreSessionUiState(frame.containerUiState());
            editorUndo.clear();
            editorUndo.addAll(frame.editorUndo());
            editorRedo.clear();
            editorRedo.addAll(frame.editorRedo());
            lastEditorState = frame.parentState();
            editorInteractionStart = null;
            editorInteractionControl = 0;
            requestedEditorTab = frame.returnTab();
            status = frame.status();
            statusSuccess = frame.statusSuccess();
            focusSinkOnOpen = true;
            nativePreviews.clear();
            soundPreviews.stop(client);
        } finally {
            historyEnabled = true;
        }
    }

    public SourceInventorySnapshot sourceInventory() {
        return sourceInventory;
    }

    public void drawEquipmentPreview(Minecraft client, ItemStack stack, EquipmentPreviewBase base) {
        nativePreviews.drawEquipment(client, withLiveCount(stack), base);
    }

    public void drawPlayerHeadPreview(Minecraft client, ItemStack stack, PlayerSkinPreviewMode mode) {
        nativePreviews.drawPlayerHead(client, withLiveCount(stack), mode);
    }

    public void drawItemModelPreview(Minecraft client, ItemStack stack, ItemModelPreviewMode mode) {
        nativePreviews.drawItemModel(client, withLiveCount(stack), mode);
    }

    public void drawBookPreview(ItemStack stack, int page) {
        nativePreviews.drawBook(withLiveCount(stack), page);
    }

    public void drawMapPreview(ItemStack stack, Set<String> originalDecorationNames) {
        nativePreviews.drawMap(withLiveCount(stack), originalDecorationNames);
    }

    public boolean hasMapPreviewData(Minecraft client, ItemStack stack) {
        return nativePreviews.hasMapData(client, stack);
    }

    public void drawBannerPreview(ItemStack stack) {
        nativePreviews.drawBanner(withLiveCount(stack));
    }

    public void renderNativePreviewsForImGui() {
        if (open) {
            nativePreviews.renderForComposite();
        }
    }

    public ComponentOption selectedComponent() {
        return selectedComponent;
    }

    public boolean selectComponent(Minecraft client, ComponentOption option) {
        return selectComponent(client, option, EditorNavigationGuard.Source.PROGRAMMATIC);
    }

    private boolean selectComponent(
        Minecraft client,
        ComponentOption option,
        EditorNavigationGuard.Source source
    ) {
        if (selectedComponent != null && selectedComponent.id().equals(option.id())) {
            focusSelectedComponentPane = true;
            return true;
        }

        EditorNavigationGuard.Decision componentDecision = EditorNavigationGuard.decide(
            source,
            EditorNavigationGuard.Transition.DESTRUCTIVE_RETARGET,
            false,
            componentNavigationBlocker(client)
        );
        if (!componentDecision.allowed()) {
            reportComponentNavigationBlocked(componentDecision);
            return false;
        }
        if (DataEditorPanel.supports(option.type())) {
            EditorNavigationGuard.Decision dataDecision = dataEditor.navigationDecision(
                option.type(),
                source
            );
            if (!dataDecision.allowed()) {
                dataEditor.reportBlockedNavigation(dataDecision);
                return false;
            }
        }
        if (option.type() == DataComponents.CUSTOM_DATA && diamondFireTagEditor.isDirty()) {
            EditorNavigationGuard.Decision diamondFireDecision = EditorNavigationGuard.decide(
                source,
                EditorNavigationGuard.Transition.DESTRUCTIVE_RETARGET,
                false,
                diamondFireNavigationBlocker(client)
            );
            setStatus(false, diamondFireDecision.blocker().explanation());
            return false;
        }
        selectedComponent = option;
        useGenericSelectedComponentEditor = false;
        refreshSelectedComponentEditor(client);
        focusSelectedComponentPane = true;
        return true;
    }

    private EditorNavigationGuard.Blocker componentNavigationBlocker(Minecraft client) {
        if (selectedComponent == null || !hasPendingEditorChanges(selectedComponent.type())) {
            return null;
        }

        DataComponentType<?> type = selectedComponent.type();
        if (componentForm.isDirty()) {
            boolean invalid = !componentForm.problem().isBlank();
            return new EditorNavigationGuard.Blocker(
                tr("tab.components"),
                "components",
                invalid
                    ? EditorNavigationGuard.PendingState.INVALID
                    : EditorNavigationGuard.PendingState.VALID,
                invalid
                    ? tr("navigation.invalid", tr("tab.components"), componentForm.problem())
                    : tr("navigation.pending", tr("tab.components")),
                new EditorNavigationGuard.FocusRequest("selected_component_editor")
            );
        }
        if (DataEditorPanel.supports(type) && dataEditor.isComponentDirty(type)) {
            return dataEditor.pendingNavigationBlocker();
        }
        if ((type == DataComponents.ENCHANTMENTS || type == DataComponents.STORED_ENCHANTMENTS)
            && enchantmentEditor.isDirty()) {
            return enchantmentEditor.pendingNavigationBlocker();
        }
        if (type == DataComponents.CUSTOM_DATA && diamondFireTagEditor.isDirty()) {
            return diamondFireNavigationBlocker(client);
        }

        InlineComponentEditorRegistry.Kind inlineKind = InlineComponentEditorRegistry.kind(type);
        if (inlineKind != null) {
            return switch (inlineKind) {
                case RECIPES -> pendingStackNavigationBlocker(
                    client,
                    "tab.components",
                    "components",
                    recipesEditor::isDirty,
                    () -> recipesEditor.pendingStack(client)
                );
                case BEES -> pendingStackNavigationBlocker(
                    client,
                    "tab.components",
                    "components",
                    beesEditor::isDirty,
                    () -> beesEditor.pendingStack(client)
                );
                case SULFUR_CUBE_CONTENT -> pendingStackNavigationBlocker(
                    client,
                    "tab.components",
                    "components",
                    sulfurCubeContentEditor::isDirty,
                    () -> sulfurCubeContentEditor.pendingStack(client)
                );
                case ENTITY_VARIANT -> variantComponentEditor.pendingNavigationBlocker(client);
                case ADVENTURE_PREDICATE -> pendingStackNavigationBlocker(
                    client,
                    "tab.components",
                    "components",
                    adventureEditor::isDirty,
                    () -> adventureEditor.pendingStack(client)
                );
            };
        }

        EditorTab dedicatedTab = dedicatedTabFor(type);
        if (dedicatedTab == null) return null;
        return switch (dedicatedTab) {
            case NAME -> pendingStackNavigationBlocker(
                client, "tab.name", "name", loreEditor::isDirty, () -> loreEditor.pendingStack(client)
            );
            case ENCHANTMENTS -> enchantmentEditor.pendingNavigationBlocker();
            case DATA -> dataEditor.pendingNavigationBlocker();
            case DIAMONDFIRE -> diamondFireNavigationBlocker(client);
            case COMPONENTS, RAW -> null;
            default -> {
                PendingStackEditor editor = switch (dedicatedTab) {
                    case PRESENTATION -> appearanceEditor;
                    case FLAGS -> flagEditor;
                    case ATTRIBUTES -> attributeEditor;
                    case COMBAT -> combatEditor;
                    case MUSIC -> musicEditor;
                    case COMPASS -> compassEditor;
                    case CONTAINER -> containerEditor;
                    case ADVENTURE -> adventureEditor;
                    case EQUIPMENT -> equipmentEditor;
                    case USE -> useEditor;
                    case BOOK -> bookEditor;
                    case MAP -> mapEditor;
                    case BANNER -> bannerEditor;
                    case DECORATED_POT -> decoratedPotEditor;
                    case POTION -> potionEditor;
                    case FIREWORK -> fireworkEditor;
                    case PLAYER_HEAD -> playerHeadEditor;
                    default -> throw new IllegalStateException("No stack editor for " + dedicatedTab);
                };
                EditorTab route = dedicatedTab == EditorTab.ADVENTURE ? EditorTab.COMPONENTS : dedicatedTab;
                String routeId = route.name().toLowerCase(Locale.ROOT);
                yield pendingStackNavigationBlocker(
                    client, "tab." + routeId, routeId, editor::isDirty, () -> editor.pendingStack(client)
                );
            }
        };
    }

    private EditorNavigationGuard.Blocker diamondFireNavigationBlocker(Minecraft client) {
        return pendingStackNavigationBlocker(
            client,
            "tab.diamondfire",
            "diamondfire",
            diamondFireTagEditor::isDirty,
            () -> diamondFireTagEditor.pendingStack(client)
        );
    }

    private EditorNavigationGuard.Blocker pendingStackNavigationBlocker(
        Minecraft client,
        String ownerTranslationKey,
        String tab,
        Supplier<Boolean> dirty,
        Supplier<ItemStack> pendingStack
    ) {
        if (!dirty.get()) return null;

        EditorNavigationGuard.PendingState pendingState;
        String explanation;
        try {
            ItemStackCodecs.validateStack(pendingStack.get());
            pendingState = EditorNavigationGuard.PendingState.VALID;
            explanation = tr("navigation.pending", tr(ownerTranslationKey));
        } catch (RuntimeException exception) {
            pendingState = EditorNavigationGuard.PendingState.INVALID;
            explanation = tr(
                "navigation.invalid",
                tr(ownerTranslationKey),
                readableMessage(exception)
            );
        }
        return new EditorNavigationGuard.Blocker(
            tr(ownerTranslationKey),
            tab,
            pendingState,
            explanation,
            new EditorNavigationGuard.FocusRequest("selected_component_editor")
        );
    }

    private void reportComponentNavigationBlocked(EditorNavigationGuard.Decision decision) {
        setStatus(false, decision.blocker().explanation());
        if (decision.blocker().focusRequest() != null) {
            focusSelectedComponentPane = true;
        }
    }

    public void setStatus(boolean success, String message) {
        statusSuccess = success;
        status = message;
    }

    private boolean hasUnsavedFormChanges() {
        return pendingEditorRegistry().hasPending(id -> true);
    }

    /**
     * Whether applying a whole-stack Raw value would overwrite another editor's pending work.
     */
    public boolean hasPendingNonRawEditorChanges() {
        return pendingEditorRegistry().hasPending(id -> !id.equals("raw"));
    }

    private PendingEditorRegistry pendingEditorRegistry() {
        if (pendingEditorRegistry == null) pendingParticipants(Minecraft.getInstance());
        return pendingEditorRegistry;
    }

    private boolean isBasicDirty() {
        if (basicDirty) {
            basicDirty = !itemId.get().trim().equals(getItemId(draft))
                || Math.clamp(count.get(), 1, MAX_ITEM_COUNT) != draft.getCount();
        }
        return basicDirty;
    }

    private record NestedOpenRequest(
        int index,
        ItemStack stack,
        EditorTab returnTab,
        NestedItemDestination destinationKind
    ) {
        private NestedOpenRequest {
            stack = stack.copy();
        }

        @Override
        public ItemStack stack() {
            return stack.copy();
        }
    }

    private record NestedEditorFrame(
        int destination,
        NestedItemDestination destinationKind,
        EditorHistorySnapshot parentState,
        ItemEditHistory parentHistory,
        OpeningItemSnapshot parentOpeningItem,
        List<EditorHistorySnapshot> editorUndo,
        List<EditorHistorySnapshot> editorRedo,
        String selectedComponentId,
        boolean genericComponentEditor,
        ContainerEditorPanel.SessionUiState containerUiState,
        long editorUiSessionId,
        boolean collapseAbsentComponents,
        EditorTab returnTab,
        String status,
        boolean statusSuccess
    ) {
        private NestedEditorFrame {
            editorUndo = List.copyOf(editorUndo);
            editorRedo = List.copyOf(editorRedo);
        }
    }

    private enum NestedItemDestination {
        CONTAINER,
        BUNDLE,
        SULFUR_CUBE
    }

    private record EditorHistorySnapshot(
        String draft,
        String cleanDraft,
        String itemId,
        int count,
        TextInputFormat textFormat,
        LoreEditorPanel.HistoryState lore,
        DataEditorPanel.HistoryState data,
        DiamondFireTagEditorPanel.HistoryState diamondFire,
        int enchantmentTarget,
        Map<String, String> panels,
        boolean flagStackSizeOverride,
        String componentId,
        JsonElement componentValue,
        JsonElement variantComponentValue,
        boolean genericComponentEditor,
        String rawSource
    ) {
        private boolean sameState(EditorHistorySnapshot other) {
            return other != null
                && draft.equals(other.draft)
                && cleanDraft.equals(other.cleanDraft)
                && itemId.equals(other.itemId)
                && count == other.count
                && textFormat == other.textFormat
                && lore.sameState(other.lore)
                && data.sameState(other.data)
                && diamondFire.sameState(other.diamondFire)
                && panels.equals(other.panels)
                && flagStackSizeOverride == other.flagStackSizeOverride
                && componentId.equals(other.componentId)
                && Objects.equals(componentValue, other.componentValue)
                && Objects.equals(variantComponentValue, other.variantComponentValue)
                && genericComponentEditor == other.genericComponentEditor
                && Objects.equals(rawSource, other.rawSource);
        }
    }

    private enum EditorTab {
        NAME,
        PRESENTATION,
        FLAGS,
        ATTRIBUTES,
        ENCHANTMENTS,
        COMBAT,
        MUSIC,
        COMPASS,
        CONTAINER,
        ADVENTURE,
        EQUIPMENT,
        USE,
        BOOK,
        MAP,
        BANNER,
        DECORATED_POT,
        POTION,
        FIREWORK,
        PLAYER_HEAD,
        DATA,
        DIAMONDFIRE,
        COMPONENTS,
        RAW
    }

    private enum ComponentGroup {
        PRESENT,
        RECOMMENDED,
        AVAILABLE,
        MODIFIED
    }

}

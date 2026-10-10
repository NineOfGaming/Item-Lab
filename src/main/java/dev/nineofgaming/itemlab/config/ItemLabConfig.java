package dev.nineofgaming.itemlab.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.nineofgaming.itemlab.ItemLab;
import dev.nineofgaming.itemlab.editor.ui.preview.EquipmentPreviewBase;
import dev.nineofgaming.itemlab.editor.ui.preview.ItemModelPreviewMode;
import dev.nineofgaming.itemlab.editor.ui.preview.PlayerSkinPreviewMode;
import dev.nineofgaming.itemlab.editor.ui.text.TextInputFormat;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Small persistent config for editor preferences.
 */
public final class ItemLabConfig {
    private static final Logger LOGGER = ItemLab.createLogger("Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("itemlab.json");
    private static final ItemLabConfig INSTANCE = load();

    private ComponentBrowserPreferences componentBrowser = new ComponentBrowserPreferences();
    private TextPreferences text = new TextPreferences();
    private PreviewPreferences previews = new PreviewPreferences();
    private TooltipPreferences tooltip = new TooltipPreferences();
    private LayoutPreferences layout = new LayoutPreferences();
    private DiamondFirePreferences diamondFire = new DiamondFirePreferences();

    private ItemLabConfig() {
    }

    public static ItemLabConfig get() {
        return INSTANCE;
    }

    public ComponentBrowserScope componentBrowserScope() {
        return componentBrowser.scope;
    }

    public boolean includeNetworkOnlyComponents() {
        return componentBrowser.includeNetworkOnly;
    }

    public boolean excludeDedicatedComponents() {
        return componentBrowser.excludeDedicated;
    }

    public boolean minecraftComponentsOnly() {
        return componentBrowser.minecraftOnly;
    }

    public TextInputFormat textInputFormat() {
        return text.inputFormat;
    }

    public boolean removeDefaultItalics() {
        return text.removeDefaultItalics;
    }

    public boolean useWhiteDefaultLoreColor() {
        return text.useWhiteDefaultLoreColor;
    }

    public boolean hideLeadingFormattingPrefixes() {
        return text.hideLeadingFormattingPrefixes;
    }

    public EquipmentPreviewBase equipmentPreviewBase() {
        return previews.equipmentBase;
    }

    public ItemModelPreviewMode itemModelPreviewMode() {
        return previews.itemModelMode;
    }

    public PlayerSkinPreviewMode playerSkinPreviewMode() {
        return previews.playerSkinMode;
    }

    public boolean showAllTooltipComponents() {
        return tooltip.showAllRegisteredComponents;
    }

    public boolean showPresentTooltipComponentsOnly() {
        return tooltip.presentOnItemOnly;
    }

    public boolean showInventoryEditHint() {
        return tooltip.showInventoryEditHint;
    }

    public boolean collapseAbsentComponents() {
        return layout.collapseAbsentComponents;
    }

    public boolean forceDiamondFireCompatibility() {
        return diamondFire.forceCompatibility;
    }

    public void setComponentBrowserScope(ComponentBrowserScope scope) {
        if (componentBrowser.scope == scope) return;
        componentBrowser.scope = scope;
        save();
    }

    public void setIncludeNetworkOnlyComponents(boolean include) {
        if (componentBrowser.includeNetworkOnly == include) return;
        componentBrowser.includeNetworkOnly = include;
        save();
    }

    public void setExcludeDedicatedComponents(boolean exclude) {
        if (componentBrowser.excludeDedicated == exclude) return;
        componentBrowser.excludeDedicated = exclude;
        save();
    }

    public void setMinecraftComponentsOnly(boolean minecraftOnly) {
        if (componentBrowser.minecraftOnly == minecraftOnly) return;
        componentBrowser.minecraftOnly = minecraftOnly;
        save();
    }

    public void setTextInputFormat(TextInputFormat format) {
        if (text.inputFormat == format) return;
        text.inputFormat = format;
        save();
    }

    public void setRemoveDefaultItalics(boolean remove) {
        if (text.removeDefaultItalics == remove) return;
        text.removeDefaultItalics = remove;
        save();
    }

    public void setUseWhiteDefaultLoreColor(boolean useWhite) {
        if (text.useWhiteDefaultLoreColor == useWhite) return;
        text.useWhiteDefaultLoreColor = useWhite;
        save();
    }

    public void setHideLeadingFormattingPrefixes(boolean hide) {
        if (text.hideLeadingFormattingPrefixes == hide) return;
        text.hideLeadingFormattingPrefixes = hide;
        save();
    }

    public void setEquipmentPreviewBase(EquipmentPreviewBase base) {
        if (previews.equipmentBase == base) return;
        previews.equipmentBase = base;
        save();
    }

    public void setItemModelPreviewMode(ItemModelPreviewMode mode) {
        if (previews.itemModelMode == mode) return;
        previews.itemModelMode = mode;
        save();
    }

    public void setPlayerSkinPreviewMode(PlayerSkinPreviewMode mode) {
        if (previews.playerSkinMode == mode) return;
        previews.playerSkinMode = mode;
        save();
    }

    public void setShowAllTooltipComponents(boolean showAll) {
        if (tooltip.showAllRegisteredComponents == showAll) return;
        tooltip.showAllRegisteredComponents = showAll;
        save();
    }

    public void setShowPresentTooltipComponentsOnly(boolean presentOnly) {
        if (tooltip.presentOnItemOnly == presentOnly) return;
        tooltip.presentOnItemOnly = presentOnly;
        save();
    }

    public void setShowInventoryEditHint(boolean show) {
        if (tooltip.showInventoryEditHint == show) return;
        tooltip.showInventoryEditHint = show;
        save();
    }

    public void setCollapseAbsentComponents(boolean collapse) {
        if (layout.collapseAbsentComponents == collapse) return;
        layout.collapseAbsentComponents = collapse;
        save();
    }

    public void setForceDiamondFireCompatibility(boolean force) {
        if (diamondFire.forceCompatibility == force) return;
        diamondFire.forceCompatibility = force;
        save();
    }

    private static ItemLabConfig load() {
        if (!Files.isRegularFile(PATH)) {
            return new ItemLabConfig();
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            ItemLabConfig config = GSON.fromJson(reader, ItemLabConfig.class);
            if (config == null) config = new ItemLabConfig();
            config.repairMissingValues();
            return config;
        } catch (Exception exception) {
            LOGGER.warn("Could not read {}; using default preferences", PATH, exception);
            return new ItemLabConfig();
        }
    }

    private void repairMissingValues() {
        int version = 1;
        if (componentBrowser == null) componentBrowser = new ComponentBrowserPreferences();
        if (componentBrowser.scope == null) componentBrowser.scope = ComponentBrowserScope.ALL;
        if (text == null) text = new TextPreferences();
        if (text.inputFormat == null) text.inputFormat = TextInputFormat.COLOR_CODES;
        if (previews == null) previews = new PreviewPreferences();
        if (previews.equipmentBase == null) previews.equipmentBase = EquipmentPreviewBase.PLAYER;
        if (previews.itemModelMode == null) previews.itemModelMode = ItemModelPreviewMode.FIXED;
        if (previews.playerSkinMode == null) previews.playerSkinMode = PlayerSkinPreviewMode.HEAD_ONLY;
        if (tooltip == null) tooltip = new TooltipPreferences();
        if (layout == null) layout = new LayoutPreferences();
        if (diamondFire == null) diamondFire = new DiamondFirePreferences();
    }

    private synchronized void save() {
        Path temporary = PATH.resolveSibling(PATH.getFileName() + ".tmp");
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(this, writer);
            }
            try {
                Files.move(
                    temporary,
                    PATH,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            LOGGER.warn("Could not save {}", PATH, exception);
            try {
                Files.deleteIfExists(temporary);
            } catch (Exception ignored) {
                // Nothing useful can be done if cleanup also fails.
            }
        }
    }

    private static final class ComponentBrowserPreferences {
        private ComponentBrowserScope scope = ComponentBrowserScope.ALL;
        private boolean includeNetworkOnly = false;
        private boolean excludeDedicated = false;
        private boolean minecraftOnly = false;
    }

    private static final class TextPreferences {
        private TextInputFormat inputFormat = TextInputFormat.MINI_MESSAGE;
        private boolean removeDefaultItalics = true;
        private boolean useWhiteDefaultLoreColor = true;
        private boolean hideLeadingFormattingPrefixes = true;
    }

    private static final class PreviewPreferences {
        private EquipmentPreviewBase equipmentBase = EquipmentPreviewBase.PLAYER;
        private ItemModelPreviewMode itemModelMode = ItemModelPreviewMode.FIXED;
        private PlayerSkinPreviewMode playerSkinMode = PlayerSkinPreviewMode.HEAD_ONLY;
    }

    private static final class TooltipPreferences {
        private boolean showAllRegisteredComponents = false;
        private boolean presentOnItemOnly = false;
        private boolean showInventoryEditHint = true;
    }

    private static final class LayoutPreferences {
        private boolean collapseAbsentComponents = true;
    }

    private static final class DiamondFirePreferences {
        private boolean forceCompatibility = false;
    }
}

package net.irisshaders.iris.gui;

import net.irisshaders.iris.IrisCommon;
import net.irisshaders.iris.IrisVintage;
import net.irisshaders.iris.config.IrisConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Mouse;
import org.taumc.celeritas.CeleritasShaderVersionService;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static net.irisshaders.iris.IrisLogging.IRIS_LOGGER;

public class VintageShaderPackSelectionScreen extends GuiScreen {
    private static final int DONE_BUTTON_ID = 0;
    private static final int OPEN_FOLDER_BUTTON_ID = 1;
    private static final int REFRESH_BUTTON_ID = 2;
    private static final int OFF_BUTTON_ID = 3;
    private static final int UP_BUTTON_ID = 4;
    private static final int DOWN_BUTTON_ID = 5;
    private static final int OPTIONS_BUTTON_ID = 6;
    private static final int PACK_BUTTON_ID_BASE = 100;

    private final GuiScreen parent;
    private final List<String> shaderPacks = new ArrayList<>();
    private int scrollOffset;
    private int visiblePackRows;
    private String statusMessage = "";

    public VintageShaderPackSelectionScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.refreshShaderPackList();

        int listWidth = Math.min(340, this.width - 40);
        int left = (this.width - listWidth) / 2;
        int top = 52;
        int rowHeight = 24;
        this.visiblePackRows = Math.max(3, Math.min(8, (this.height - 174) / rowHeight));
        this.scrollOffset = clamp(this.scrollOffset, 0, this.getMaximumScrollOffset());

        this.buttonList.add(new GuiButton(OFF_BUTTON_ID, left, top, listWidth, 20,
                this.trimToButtonWidth(this.formatSelectionLabel("(off)", this.isOffSelected()), listWidth)));

        int packTop = top + rowHeight;
        for (int row = 0; row < this.visiblePackRows; row++) {
            int packIndex = this.scrollOffset + row;
            if (packIndex >= this.shaderPacks.size()) {
                break;
            }

            String packName = this.shaderPacks.get(packIndex);
            boolean selected = this.isPackSelected(packName);
            this.buttonList.add(new GuiButton(PACK_BUTTON_ID_BASE + row, left, packTop + row * rowHeight, listWidth, 20,
                    this.trimToButtonWidth(this.formatSelectionLabel(packName, selected), listWidth)));
        }

        int navX = left + listWidth + 6;
        this.buttonList.add(new GuiButton(UP_BUTTON_ID, navX, packTop, 24, 20, "^"));
        this.buttonList.add(new GuiButton(DOWN_BUTTON_ID, navX, packTop + 24, 24, 20, "v"));

        int bottomY = this.height - 28;
        GuiButton optionsButton = new GuiButton(OPTIONS_BUTTON_ID, this.width / 2 - 154, bottomY - 24, 308, 20, "Shader Pack Settings...");
        optionsButton.enabled = this.canOpenShaderPackOptions();
        this.buttonList.add(optionsButton);
        this.buttonList.add(new GuiButton(OPEN_FOLDER_BUTTON_ID, this.width / 2 - 154, bottomY, 100, 20, "Open Folder"));
        this.buttonList.add(new GuiButton(REFRESH_BUTTON_ID, this.width / 2 - 50, bottomY, 100, 20, "Refresh"));
        this.buttonList.add(new GuiButton(DONE_BUTTON_ID, this.width / 2 + 54, bottomY, 100, 20, I18n.format("gui.done")));
        this.updateScrollButtons();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRenderer, "Shader Packs", this.width / 2, 18, 0xFFFFFF);

        String selected = this.getSelectedPackName();
        String selectedLabel = selected == null || this.isOffSelected() ? "Current: Off" : "Current: " + selected;
        this.drawCenteredString(this.fontRenderer, selectedLabel, this.width / 2, 34, 0xA0A0A0);

        if (this.shaderPacks.isEmpty()) {
            this.drawCenteredString(this.fontRenderer, "No shader packs found in the shaderpacks folder.", this.width / 2, 86, 0xA0A0A0);
        }

        if (!this.statusMessage.isEmpty()) {
            this.drawCenteredString(this.fontRenderer, this.statusMessage, this.width / 2, this.height - 68, 0xFFFFA0);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) {
            return;
        }

        if (button.id == DONE_BUTTON_ID) {
            this.mc.displayGuiScreen(this.parent);
        } else if (button.id == OPEN_FOLDER_BUTTON_ID) {
            this.openShaderpacksFolder();
        } else if (button.id == REFRESH_BUTTON_ID) {
            this.statusMessage = "Shader pack list refreshed.";
            this.initGui();
        } else if (button.id == OFF_BUTTON_ID) {
            this.selectShaderPack(null);
        } else if (button.id == OPTIONS_BUTTON_ID) {
            this.openShaderPackOptions();
        } else if (button.id == UP_BUTTON_ID) {
            this.scrollOffset--;
            this.initGui();
        } else if (button.id == DOWN_BUTTON_ID) {
            this.scrollOffset++;
            this.initGui();
        } else if (button.id >= PACK_BUTTON_ID_BASE) {
            int packIndex = this.scrollOffset + button.id - PACK_BUTTON_ID_BASE;
            if (packIndex >= 0 && packIndex < this.shaderPacks.size()) {
                this.selectShaderPack(this.shaderPacks.get(packIndex));
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int direction = wheel > 0 ? -1 : 1;
            int nextOffset = clamp(this.scrollOffset + direction, 0, this.getMaximumScrollOffset());
            if (nextOffset != this.scrollOffset) {
                this.scrollOffset = nextOffset;
                this.initGui();
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void selectShaderPack(String packName) {
        IrisConfig config = IrisCommon.getIrisConfig();
        if (config == null) {
            this.statusMessage = "Shader configuration has not initialized yet.";
            this.initGui();
            return;
        }

        if (packName == null) {
            config.setShaderPackName(null);
            config.setShadersEnabled(false);
        } else {
            config.setShaderPackName(packName);
            config.setShadersEnabled(true);
        }

        try {
            config.save();
        } catch (IOException e) {
            IRIS_LOGGER.error("Error saving shader configuration file!", e);
            this.statusMessage = "Failed to save shader configuration.";
            this.initGui();
            return;
        }

        CeleritasShaderVersionService.INSTANCE.reload();
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world != null) {
            minecraft.renderGlobal.loadRenderers();
            if (packName == null) {
                IrisVintage.resetVanillaGlState();
            }
        } else if (packName == null) {
            IrisVintage.resetMenuGlState();
        }

        this.statusMessage = packName == null ? "Shaders disabled." : "Selected " + packName + ".";
        this.initGui();
    }

    private void openShaderPackOptions() {
        if (!this.canOpenShaderPackOptions()) {
            this.statusMessage = "Select a shader pack first.";
            this.initGui();
            return;
        }

        if (IrisCommon.getCurrentPack().isEmpty()) {
            CeleritasShaderVersionService.INSTANCE.reload();
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft.world != null) {
                minecraft.renderGlobal.loadRenderers();
            }
        }

        if (IrisCommon.getCurrentPack().isPresent()) {
            this.mc.displayGuiScreen(new VintageShaderPackOptionsScreen(this));
        } else {
            this.statusMessage = "Shader pack options are not available yet.";
            this.initGui();
        }
    }

    private void refreshShaderPackList() {
        this.shaderPacks.clear();
        Path shaderpacksDirectory = IrisCommon.getShaderpacksDirectory();

        try {
            Files.createDirectories(shaderpacksDirectory);

            try (Stream<Path> stream = Files.list(shaderpacksDirectory)) {
                stream
                        .filter(IrisCommon::isValidShaderpack)
                        .map(path -> path.getFileName().toString())
                        .sorted(Comparator.comparing(String::toLowerCase))
                        .forEach(this.shaderPacks::add);
            }
        } catch (IOException e) {
            IRIS_LOGGER.error("Failed to scan shaderpacks directory!", e);
            this.statusMessage = "Failed to scan the shaderpacks folder.";
        }
    }

    private void openShaderpacksFolder() {
        Path shaderpacksDirectory = IrisCommon.getShaderpacksDirectory();

        try {
            Files.createDirectories(shaderpacksDirectory);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(shaderpacksDirectory.toFile());
                this.statusMessage = "Opened shaderpacks folder.";
            } else {
                this.statusMessage = "Desktop folder opening is not supported.";
            }
        } catch (IOException e) {
            IRIS_LOGGER.error("Failed to open shaderpacks directory!", e);
            this.statusMessage = "Failed to open shaderpacks folder.";
        }
    }

    private void updateScrollButtons() {
        for (GuiButton button : this.buttonList) {
            if (button.id == UP_BUTTON_ID) {
                button.enabled = this.scrollOffset > 0;
            } else if (button.id == DOWN_BUTTON_ID) {
                button.enabled = this.scrollOffset < this.getMaximumScrollOffset();
            }
        }
    }

    private boolean isOffSelected() {
        IrisConfig config = IrisCommon.getIrisConfig();
        return config == null || !config.areShadersEnabled() || config.getShaderPackName().isEmpty();
    }

    private boolean isPackSelected(String packName) {
        IrisConfig config = IrisCommon.getIrisConfig();
        return config != null && config.areShadersEnabled() && config.getShaderPackName().filter(packName::equals).isPresent();
    }

    private boolean canOpenShaderPackOptions() {
        IrisConfig config = IrisCommon.getIrisConfig();
        return config != null && config.areShadersEnabled() && config.getShaderPackName().isPresent();
    }

    private String getSelectedPackName() {
        IrisConfig config = IrisCommon.getIrisConfig();
        return config == null ? null : config.getShaderPackName().orElse(null);
    }

    private String formatSelectionLabel(String name, boolean selected) {
        return selected ? "> " + name : "  " + name;
    }

    private String trimToButtonWidth(String label, int buttonWidth) {
        int maxTextWidth = buttonWidth - 12;
        if (this.fontRenderer.getStringWidth(label) <= maxTextWidth) {
            return label;
        }

        String trimmed = label;
        while (trimmed.length() > 3 && this.fontRenderer.getStringWidth(trimmed + "...") > maxTextWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        return trimmed + "...";
    }

    private int getMaximumScrollOffset() {
        return Math.max(0, this.shaderPacks.size() - this.visiblePackRows);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

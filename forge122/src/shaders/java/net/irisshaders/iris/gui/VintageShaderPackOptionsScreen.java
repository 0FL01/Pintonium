package net.irisshaders.iris.gui;

import net.irisshaders.iris.IrisCommon;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.option.Profile;
import net.irisshaders.iris.shaderpack.option.ProfileSet;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuBooleanOptionElement;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuElement;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuElementScreen;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuLinkElement;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuProfileElement;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuStringOptionElement;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Mouse;
import org.taumc.celeritas.CeleritasShaderVersionService;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VintageShaderPackOptionsScreen extends GuiScreen {
    private static final int DONE_BUTTON_ID = 0;
    private static final int RESET_BUTTON_ID = 1;
    private static final int APPLY_BUTTON_ID = 2;
    private static final int BACK_BUTTON_ID = 3;
    private static final int UP_BUTTON_ID = 4;
    private static final int DOWN_BUTTON_ID = 5;
    private static final int ELEMENT_BUTTON_ID_BASE = 100;

    private final GuiScreen parent;
    private final Deque<String> screenHistory = new ArrayDeque<>();
    private final List<Integer> visibleElementIndexes = new ArrayList<>();

    private ShaderPack shaderPack;
    private OptionMenuElementScreen currentScreen;
    private String currentScreenId;
    private int scrollRowOffset;
    private int visibleRows;
    private int columns = 1;
    private String statusMessage = "";

    public VintageShaderPackOptionsScreen(GuiScreen parent) {
        this.parent = parent;
        this.refreshPackContext();
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.visibleElementIndexes.clear();

        int top = 52;
        int rowHeight = 24;
        int controlsHeight = 88;
        this.visibleRows = Math.max(2, (this.height - top - controlsHeight) / rowHeight);

        if (this.currentScreen == null) {
            int bottomY = this.height - 28;
            this.buttonList.add(new GuiButton(DONE_BUTTON_ID, this.width / 2 - 50, bottomY, 100, 20, I18n.format("gui.done")));
            return;
        }

        this.columns = clamp(this.currentScreen.getColumnCount(), 1, 3);
        int totalWidth = Math.min(this.width - 52, this.columns == 1 ? 340 : 520);
        int spacing = 6;
        int buttonWidth = (totalWidth - spacing * (this.columns - 1)) / this.columns;
        int left = (this.width - totalWidth) / 2;

        this.scrollRowOffset = clamp(this.scrollRowOffset, 0, this.getMaximumScrollRowOffset());

        int firstElementIndex = this.scrollRowOffset * this.columns;
        int visibleSlots = this.visibleRows * this.columns;
        for (int slot = 0; slot < visibleSlots; slot++) {
            int elementIndex = firstElementIndex + slot;
            if (elementIndex >= this.currentScreen.elements.size()) {
                break;
            }

            OptionMenuElement element = this.currentScreen.elements.get(elementIndex);
            int column = slot % this.columns;
            int row = slot / this.columns;
            int x = left + column * (buttonWidth + spacing);
            int y = top + row * rowHeight;

            GuiButton elementButton = new GuiButton(ELEMENT_BUTTON_ID_BASE + this.visibleElementIndexes.size(), x, y, buttonWidth, 20,
                    this.trimToButtonWidth(this.getElementLabel(element), buttonWidth));
            elementButton.enabled = element != OptionMenuElement.EMPTY;
            this.visibleElementIndexes.add(elementIndex);
            this.buttonList.add(elementButton);
        }

        int navX = left + totalWidth + 6;
        this.buttonList.add(new GuiButton(UP_BUTTON_ID, navX, top, 24, 20, "^"));
        this.buttonList.add(new GuiButton(DOWN_BUTTON_ID, navX, top + 24, 24, 20, "v"));

        int bottomY = this.height - 28;
        this.buttonList.add(new GuiButton(BACK_BUTTON_ID, this.width / 2 - 154, bottomY, 74, 20, this.currentScreenId == null ? "Cancel" : "Back"));
        this.buttonList.add(new GuiButton(RESET_BUTTON_ID, this.width / 2 - 76, bottomY, 74, 20, "Reset"));
        this.buttonList.add(new GuiButton(APPLY_BUTTON_ID, this.width / 2 + 2, bottomY, 74, 20, "Apply"));
        this.buttonList.add(new GuiButton(DONE_BUTTON_ID, this.width / 2 + 80, bottomY, 74, 20, I18n.format("gui.done")));
        this.updateControlButtons();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRenderer, "Shader Pack Settings", this.width / 2, 16, 0xFFFFFF);

        String packName = IrisCommon.getCurrentPackName() == null ? "(unknown)" : IrisCommon.getCurrentPackName();
        this.drawCenteredString(this.fontRenderer, packName + " - " + this.getScreenTitle(), this.width / 2, 32, 0xA0A0A0);

        if (this.currentScreen == null || this.currentScreen.elements.isEmpty()) {
            this.drawCenteredString(this.fontRenderer, "This shader pack does not expose shader options.", this.width / 2, 86, 0xA0A0A0);
        }

        if (!this.statusMessage.isEmpty()) {
            this.drawCenteredString(this.fontRenderer, this.statusMessage, this.width / 2, this.height - 50, 0xFFFFA0);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) {
            return;
        }

        if (button.id == DONE_BUTTON_ID) {
            if (!IrisCommon.getShaderPackOptionQueue().isEmpty()) {
                this.applyChanges("Shader settings applied.");
            }
            this.mc.displayGuiScreen(this.parent);
        } else if (button.id == RESET_BUTTON_ID) {
            IrisCommon.getShaderPackOptionQueue().clear();
            IrisCommon.resetShaderPackOptionsOnNextReload();
            this.applyChanges("Shader settings reset.");
        } else if (button.id == APPLY_BUTTON_ID) {
            this.applyChanges("Shader settings applied.");
        } else if (button.id == BACK_BUTTON_ID) {
            this.goBack();
        } else if (button.id == UP_BUTTON_ID) {
            this.scrollRowOffset--;
            this.initGui();
        } else if (button.id == DOWN_BUTTON_ID) {
            this.scrollRowOffset++;
            this.initGui();
        } else if (button.id >= ELEMENT_BUTTON_ID_BASE) {
            int visibleIndex = button.id - ELEMENT_BUTTON_ID_BASE;
            if (visibleIndex >= 0 && visibleIndex < this.visibleElementIndexes.size()) {
                this.activateElement(this.currentScreen.elements.get(this.visibleElementIndexes.get(visibleIndex)));
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            if (this.currentScreenId != null) {
                this.goBack();
            } else {
                IrisCommon.getShaderPackOptionQueue().clear();
                this.mc.displayGuiScreen(this.parent);
            }
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
            int nextOffset = clamp(this.scrollRowOffset + direction, 0, this.getMaximumScrollRowOffset());
            if (nextOffset != this.scrollRowOffset) {
                this.scrollRowOffset = nextOffset;
                this.initGui();
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void activateElement(OptionMenuElement element) {
        if (element instanceof OptionMenuBooleanOptionElement) {
            OptionMenuBooleanOptionElement booleanElement = (OptionMenuBooleanOptionElement) element;
            boolean currentValue = booleanElement.getPendingOptionValues().getBooleanValueOrDefault(booleanElement.optionId);
            IrisCommon.getShaderPackOptionQueue().put(booleanElement.optionId, Boolean.toString(!currentValue));
            this.statusMessage = "Changed " + this.getOptionName(booleanElement.optionId, booleanElement.option.getComment().orElse(null)) + ".";
            this.initGui();
        } else if (element instanceof OptionMenuStringOptionElement) {
            OptionMenuStringOptionElement stringElement = (OptionMenuStringOptionElement) element;
            String currentValue = stringElement.getPendingOptionValues().getStringValueOrDefault(stringElement.optionId);
            List<String> values = stringElement.option.getAllowedValues();
            int index = values.indexOf(currentValue);
            String nextValue = values.get(Math.floorMod(index + 1, values.size()));
            IrisCommon.getShaderPackOptionQueue().put(stringElement.optionId, nextValue);
            this.statusMessage = "Changed " + this.getOptionName(stringElement.optionId, stringElement.option.getComment().orElse(null)) + ".";
            this.initGui();
        } else if (element instanceof OptionMenuProfileElement) {
            OptionMenuProfileElement profileElement = (OptionMenuProfileElement) element;
            ProfileSet.ProfileResult result = profileElement.profiles.scan(profileElement.options, profileElement.getPendingOptionValues());
            Profile nextProfile = result.next;
            if (nextProfile != null) {
                IrisCommon.queueShaderPackOptionsFromProfile(nextProfile);
                this.statusMessage = "Selected profile " + nextProfile.name + ".";
                this.initGui();
            }
        } else if (element instanceof OptionMenuLinkElement) {
            OptionMenuLinkElement linkElement = (OptionMenuLinkElement) element;
            if (this.shaderPack != null && this.shaderPack.getMenuContainer().subScreens.containsKey(linkElement.targetScreenId)) {
                this.screenHistory.push(this.currentScreenId == null ? "" : this.currentScreenId);
                this.setCurrentScreen(linkElement.targetScreenId);
                this.statusMessage = "";
                this.initGui();
            }
        }
    }

    private void goBack() {
        if (this.currentScreenId == null) {
            IrisCommon.getShaderPackOptionQueue().clear();
            this.mc.displayGuiScreen(this.parent);
            return;
        }

        String previousScreen = this.screenHistory.isEmpty() ? "" : this.screenHistory.pop();
        this.setCurrentScreen(previousScreen.isEmpty() ? null : previousScreen);
        this.statusMessage = "";
        this.initGui();
    }

    private void applyChanges(String successMessage) {
        CeleritasShaderVersionService.INSTANCE.reload();
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world != null) {
            minecraft.renderGlobal.loadRenderers();
        }

        this.refreshPackContext();
        this.statusMessage = successMessage;
        this.initGui();
    }

    private void refreshPackContext() {
        this.shaderPack = IrisCommon.getCurrentPack().orElse(null);
        if (this.shaderPack == null) {
            this.currentScreen = null;
            this.currentScreenId = null;
            return;
        }

        this.setCurrentScreen(this.currentScreenId);
    }

    private void setCurrentScreen(String screenId) {
        this.scrollRowOffset = 0;
        this.currentScreenId = screenId;

        if (this.shaderPack == null) {
            this.currentScreen = null;
        } else if (screenId == null) {
            this.currentScreen = this.shaderPack.getMenuContainer().mainScreen;
        } else {
            this.currentScreen = this.shaderPack.getMenuContainer().subScreens.get(screenId);
            if (this.currentScreen == null) {
                this.currentScreenId = null;
                this.currentScreen = this.shaderPack.getMenuContainer().mainScreen;
            }
        }
    }

    private String getElementLabel(OptionMenuElement element) {
        if (element instanceof OptionMenuBooleanOptionElement) {
            OptionMenuBooleanOptionElement booleanElement = (OptionMenuBooleanOptionElement) element;
            boolean value = booleanElement.getPendingOptionValues().getBooleanValueOrDefault(booleanElement.optionId);
            return this.getOptionName(booleanElement.optionId, booleanElement.option.getComment().orElse(null)) + ": " + (value ? "ON" : "OFF");
        }
        if (element instanceof OptionMenuStringOptionElement) {
            OptionMenuStringOptionElement stringElement = (OptionMenuStringOptionElement) element;
            String value = stringElement.getPendingOptionValues().getStringValueOrDefault(stringElement.optionId);
            return this.getOptionName(stringElement.optionId, stringElement.option.getComment().orElse(null)) + ": " + this.translate("value." + stringElement.optionId + "." + value, value);
        }
        if (element instanceof OptionMenuProfileElement) {
            OptionMenuProfileElement profileElement = (OptionMenuProfileElement) element;
            OptionValues values = profileElement.getPendingOptionValues();
            return "Profile: " + profileElement.profiles.scan(profileElement.options, values).current.map(profile -> profile.name).orElse("Custom");
        }
        if (element instanceof OptionMenuLinkElement) {
            OptionMenuLinkElement linkElement = (OptionMenuLinkElement) element;
            return "> " + this.translate("screen." + linkElement.targetScreenId, this.prettify(linkElement.targetScreenId)) + "...";
        }

        return "";
    }

    private String getScreenTitle() {
        if (this.currentScreenId == null) {
            return this.translate("screen", "Main");
        }

        return this.translate("screen." + this.currentScreenId, this.prettify(this.currentScreenId));
    }

    private String getOptionName(String optionId, String fallback) {
        return this.translate("option." + optionId, fallback == null || fallback.isEmpty() ? this.prettify(optionId) : fallback);
    }

    private String translate(String key, String fallback) {
        if (this.shaderPack == null || Minecraft.getMinecraft().gameSettings == null) {
            return fallback;
        }

        String language = Minecraft.getMinecraft().gameSettings.language;
        if (language == null || language.isEmpty()) {
            language = "en_us";
        }

        Map<String, String> translations = this.shaderPack.getLanguageMap().getTranslations(language.toLowerCase(Locale.ROOT));
        if (translations != null && translations.containsKey(key)) {
            return translations.get(key);
        }

        translations = this.shaderPack.getLanguageMap().getTranslations("en_us");
        if (translations != null && translations.containsKey(key)) {
            return translations.get(key);
        }

        return fallback;
    }

    private String prettify(String text) {
        String normalized = text.replace('_', ' ').replace('.', ' ').replace('-', ' ').toLowerCase(Locale.ROOT);
        String[] words = normalized.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(' ');
            }

            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1));
            }
        }

        return builder.length() == 0 ? text : builder.toString();
    }

    private void updateControlButtons() {
        for (GuiButton button : this.buttonList) {
            if (button.id == UP_BUTTON_ID) {
                button.enabled = this.scrollRowOffset > 0;
            } else if (button.id == DOWN_BUTTON_ID) {
                button.enabled = this.scrollRowOffset < this.getMaximumScrollRowOffset();
            } else if (button.id == APPLY_BUTTON_ID) {
                button.enabled = !IrisCommon.getShaderPackOptionQueue().isEmpty();
            } else if (button.id == RESET_BUTTON_ID) {
                button.enabled = this.shaderPack != null;
            }
        }
    }

    private int getMaximumScrollRowOffset() {
        if (this.currentScreen == null) {
            return 0;
        }

        int rows = (this.currentScreen.elements.size() + this.columns - 1) / this.columns;
        return Math.max(0, rows - this.visibleRows);
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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

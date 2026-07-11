package shit.zen.gui.panel.setting;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import shit.zen.ClientBase;
import shit.zen.gui.PanelClickGui;
import shit.zen.render.FontPresets;
import shit.zen.render.FontRenderer;
import shit.zen.render.GlHelper;
import shit.zen.render.TextGlow;
import shit.zen.settings.Setting;
import shit.zen.settings.impl.StringSetting;
import shit.zen.utils.render.RenderUtil;

public class StringSettingRenderer extends ClientBase implements SettingRenderer {
    private static StringSetting editingStringSetting;
    private static String editingText = "";
    private final Map<StringSetting, Boolean> hoverStates = new HashMap<>();

    @Override
    public int render(GuiGraphics guiGraphics, Setting<?> setting, int x, int y, int width, int mouseX, int mouseY, float alpha, float scale) {
        if (!(setting instanceof StringSetting stringSetting)) {
            return 0;
        }
        int rowHeight = Math.round(24.0f * scale);
        int sidePadding = Math.round(12.0f * scale);
        int widgetWidth = Math.max(120, width - Math.round(20.0f * scale));
        int widgetHeight = Math.round(16.0f * scale);
        int widgetX = x + width - widgetWidth;
        int widgetY = y + (rowHeight - widgetHeight) / 2;
        this.updateHoverState(stringSetting, mouseX, mouseY, widgetX, widgetY, widgetWidth, widgetHeight);
        this.drawWidget(guiGraphics, stringSetting, x, y, width, widgetX, widgetY, widgetWidth, widgetHeight, alpha, scale);
        return rowHeight;
    }

    @Override
    public boolean onClick(Setting<?> setting, int x, int y, int width, int mouseX, int mouseY, int button, float scale) {
        if (!(setting instanceof StringSetting stringSetting) || button != 0) {
            return false;
        }
        int rowHeight = Math.round(24.0f * scale);
        int widgetWidth = Math.max(120, width - Math.round(20.0f * scale));
        int widgetHeight = Math.round(16.0f * scale);
        int widgetX = x + width - widgetWidth;
        int widgetY = y + (rowHeight - widgetHeight) / 2;
        if (mouseX >= widgetX && mouseX <= widgetX + widgetWidth && mouseY >= widgetY && mouseY <= widgetY + widgetHeight) {
            this.startEditing(stringSetting);
            return true;
        }
        return false;
    }

    public static boolean onKeyPress(int keyCode, int scanCode, int modifiers) {
        if (editingStringSetting == null) {
            return false;
        }
        if (keyCode == 257 || keyCode == 335) {
            commitEdit();
            return true;
        }
        if (keyCode == 256) {
            cancelEdit();
            return true;
        }
        if (keyCode == 259) {
            if (!editingText.isEmpty()) {
                editingText = editingText.substring(0, editingText.length() - 1);
            }
            return true;
        }
        return false;
    }

    public static boolean onCharTyped(char c) {
        if (editingStringSetting == null) {
            return false;
        }
        if (Character.isISOControl(c)) {
            return false;
        }
        editingText = editingText + c;
        return true;
    }

    private void startEditing(StringSetting setting) {
        editingStringSetting = setting;
        editingText = setting.getValue() == null ? "" : setting.getValue();
    }

    private static void commitEdit() {
        if (editingStringSetting != null) {
            editingStringSetting.setValue(editingText);
            PanelClickGui.panelClickGui.addToast(editingStringSetting.getName() + " set to \"" + editingText + "\"");
        }
        cancelEdit();
    }

    private static void cancelEdit() {
        editingStringSetting = null;
        editingText = "";
    }

    private void updateHoverState(StringSetting setting, int mouseX, int mouseY, int widgetX, int widgetY, int widgetWidth, int widgetHeight) {
        boolean hovered = mouseX >= widgetX && mouseX <= widgetX + widgetWidth && mouseY >= widgetY && mouseY <= widgetY + widgetHeight;
        this.hoverStates.put(setting, hovered);
    }

    private void drawWidget(GuiGraphics guiGraphics, StringSetting setting, int x, int y, int width, int widgetX, int widgetY, int widgetWidth, int widgetHeight, float alpha, float scale) {
        FontRenderer nameFont = FontPresets.axiformaRegular(14.0f * scale);
        float nameY = (float) y + 12.0f * scale - nameFont.getMetrics().capHeight() / 2.0f;
        TextGlow.drawGlowText(setting.getName(), x, nameY, nameFont, this.applyAlpha(-1, alpha), this.applyAlpha(new Color(255, 255, 255, 120).getRGB(), alpha), 8.0f * scale);

        boolean hovered = this.hoverStates.getOrDefault(setting, false);
        int bgColor = hovered ? this.applyAlpha(0x50F5F5F5, alpha) : this.applyAlpha(0x30F5F5F5, alpha);
        RenderUtil.drawRoundedRect(guiGraphics.pose(), widgetX, widgetY, widgetWidth, widgetHeight, 4.0f * scale, bgColor);

        String displayText = editingStringSetting == setting ? editingText : (setting.getValue() == null ? "" : setting.getValue());
        if (displayText.isEmpty() && editingStringSetting != setting) {
            displayText = "";
        }
        FontRenderer valueFont = FontPresets.axiformaRegular(12.0f * scale);
        float textX = widgetX + Math.round(6.0f * scale);
        float textY = widgetY + widgetHeight / 2.0f - valueFont.getMetrics().capHeight() / 2.0f;
        if (editingStringSetting == setting) {
            long now = System.currentTimeMillis();
            float cyclePos = (float)(now % 1000L) / 1000.0f;
            float sineWave = (float)(Math.sin((double)cyclePos * Math.PI * 2.0) * 0.5 + 0.5);
            int textAlpha = (int)(255.0f * (0.7f + sineWave * 0.3f) * alpha);
            int textColor = textAlpha << 24 | 0xFFFFFF;
            GlHelper.drawText(displayText, textX, textY, valueFont, textColor);
            float caretX = textX + GlHelper.getStringWidth(displayText, valueFont) + 1.0f * scale;
            int caretAlpha = (int)(255.0f * sineWave * alpha);
            int caretColor = caretAlpha << 24 | 0xFFFFFF;
            RenderUtil.drawFilledRect(guiGraphics.pose(), caretX, widgetY + Math.round(2.0f * scale), Math.max(1.0f, Math.round(scale)), widgetHeight - Math.round(4.0f * scale), caretColor);
        } else {
            int textColor = this.applyAlpha(-1, alpha);
            GlHelper.drawText(displayText, textX, textY, valueFont, textColor);
        }
    }

    private int applyAlpha(int color, float alpha) {
        int origAlpha = color >> 24 & 0xFF;
        int newAlpha = (int)((float)origAlpha * alpha);
        return newAlpha << 24 | color & 0xFFFFFF;
    }

    @Override
    public boolean supports(Setting<?> setting) {
        return setting instanceof StringSetting;
    }

    @Override
    public int getHeight(Setting<?> setting, float scale) {
        return Math.round(24.0f * scale);
    }

    @Override
    public void onMouseRelease(double mouseX, double mouseY, int button) {
    }
}

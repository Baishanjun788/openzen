package shit.zen.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.Mth;
import shit.zen.ClientBase;
import shit.zen.modules.impl.movement.Scaffold;
import shit.zen.render.DrawContext;
import shit.zen.render.FontPresets;
import shit.zen.render.FontRenderer;
import shit.zen.render.Paint;

public class WatermarkHud
        extends ClientBase
        implements IHudElement {
    // 【修改点1】将专用的图标字体替换为通用的 Poppins 字体，防止渲染不出字母
    private static final FontRenderer logoFont = FontPresets.poppinsMedium(36.0f);
    private static final FontRenderer subFont = FontPresets.poppinsMedium(12.0f);
    private static final int primaryColor = new Color(170, 170, 170).getRGB();
    private static final int shadowColor = new Color(0, 0, 0, 100).getRGB();

    // 【修改点2】排版宽度计算基准字符从 "Z" 改为 "N"
    private static final float logoCharWidth = logoFont.getWidth("N");

    private static final float separatorCharWidth = subFont.getWidth("|");
    private static final float betaRawWidth = subFont.getWidth("newzamx");
    private static final float b1RawWidth = subFont.getWidth("v20");
    private static final float sep1Width = Math.max(betaRawWidth, b1RawWidth);
    private static final float betaWidth = logoCharWidth + separatorCharWidth * 2.0f + sep1Width + 48.0f;
    private static final float b1Width = logoFont.getMetrics().capHeight();
    private static final float subLineHeight = subFont.getMetrics().capHeight();
    private int lastTick = -1;
    private float maxSubWidth;
    private float line1Width;
    private float line2Width;
    private String line1Text;
    private String line2Text;

    private void updateCache() {
        if (mc == null || mc.player == null || this.lastTick == mc.player.tickCount) {
            return;
        }
        this.lastTick = mc.player.tickCount;
        String[] stringArray = this.getServerInfo();
        this.line1Text = stringArray[0];
        this.line2Text = stringArray[1];
        this.line1Width = subFont.getWidth(this.line1Text);
        this.line2Width = subFont.getWidth(this.line2Text);
        this.maxSubWidth = Math.max(this.line1Width, this.line2Width);
    }

    @Override
    public boolean hasBackground() {
        return true;
    }

    @Override
    public void renderGui(GuiGraphics guiGraphics, PoseStack poseStack, float x, float y, float width, float height, float alpha) {
    }

    @Override
    public void render(DrawContext drawContext, float x, float y, float width, float height, float alpha) {
        if (mc == null || mc.player == null || alpha <= 0.01f) {
            return;
        }
        this.updateCache();
        float totalWidth = betaWidth + this.maxSubWidth;
        float drawX = x + (width - totalWidth) / 2.0f - 1.0f;
        float centerY = y + height / 2.0f + 1.0f;
        int textColor = this.colorWithAlpha(Color.WHITE.getRGB(), alpha);
        int subColor = this.colorWithAlpha(primaryColor, alpha);
        int shadow = this.colorWithAlpha(shadowColor, alpha);
        try (Paint paint = new Paint()){
            // 【修改点3】将渲染输出的文本改为大写 "N"
            this.drawText(drawContext, paint, "N", drawX, centerY + 4.0f, logoFont, b1Width, textColor, shadow, true);
            drawX += logoCharWidth + 12.0f;
            this.drawText(drawContext, paint, "|", (drawX += 12.0f) - 13.0f, centerY, subFont, subLineHeight, subColor, shadow, true);
            float betaX = (drawX += separatorCharWidth + 12.0f) + (sep1Width - betaRawWidth) / 2.0f - 13.0f;
            float b1X = drawX + (sep1Width - b1RawWidth) / 2.0f - 13.0f;

            // 保留你修改过的名字和版本号
            this.drawText(drawContext, paint, "newzamx", betaX, centerY - 2.0f, subFont, 0.0f, textColor, shadow, false);
            this.drawText(drawContext, paint, "v20", b1X, centerY + 7.0f, subFont, 0.0f, subColor, shadow, false);

            drawX += sep1Width;
            this.drawText(drawContext, paint, "|", (drawX += 12.0f) - 13.0f, centerY, subFont, subLineHeight, subColor, shadow, true);
            float line1X = (drawX += separatorCharWidth + 12.0f) + (this.maxSubWidth - this.line1Width) / 2.0f - 13.0f;
            float line2X = drawX + (this.maxSubWidth - this.line2Width) / 2.0f - 13.0f;
            this.drawText(drawContext, paint, this.line1Text, line1X, centerY - 2.0f, subFont, 0.0f, textColor, shadow, false);
            this.drawText(drawContext, paint, this.line2Text, line2X, centerY + 7.0f, subFont, 0.0f, subColor, shadow, false);
        }
    }

    private void drawText(DrawContext drawContext, Paint paint, String text, float x, float y, FontRenderer fontRenderer, float lineHeight, int color, int shadowColor, boolean centerVertical) {
        float drawY = y;
        if (centerVertical) {
            drawY = y + lineHeight / 2.0f;
        }
        paint.setColor(shadowColor);
        drawContext.drawString(text, x + 0.5f, drawY + 0.5f, fontRenderer, paint);
        paint.setColor(color);
        drawContext.drawString(text, x, drawY, fontRenderer, paint);
    }

    private float getX() {
        this.updateCache();
        return betaWidth + this.maxSubWidth;
    }

    @Override
    public IHudElement.Size getHudAlignment() {
        return new IHudElement.Size(this.getX(), 25.0f);
    }

    private String[] getServerInfo() {
        PlayerInfo playerInfo;
        if (mc.isSingleplayer()) {
            return new String[]{"Singleplayer", "1ms"};
        }

        ServerData serverData = mc.getCurrentServer();
        String serverIp = serverData != null ? serverData.ip : "Multiplayer";

        // 保留 IP 混淆逻辑
        if (serverIp != null && serverIp.contains("118.31.71.254")) {
            serverIp = serverIp.replace("118.31.71.254", "127.0.0.1");
        }

        int ping = 0;
        if (mc.getConnection() != null && mc.player != null && (playerInfo = mc.getConnection().getPlayerInfo(mc.player.getUUID())) != null) {
            ping = playerInfo.getLatency();
        }
        ping = Mth.clamp(ping, 0, 9999);
        return new String[]{serverIp, ping + "ms"};
    }

    @Override
    public boolean isVisible() {
        return Scaffold.INSTANCE == null || !Scaffold.INSTANCE.isEnabled();
    }
}
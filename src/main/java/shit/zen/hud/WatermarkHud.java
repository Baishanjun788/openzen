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

/**
 * 灵动岛默认展示的水印条，参考截图里的布局重做：
 * [Logo] 客户端名字（ZENAMX） | [人形图标] 用户名 / 版本号 | [手柄图标] 服务器 / 延迟
 *
 * 用的都是项目里已经有的字体资源：
 * - Logo 用原来的 zenIcon 字体（"Z"字形图标）
 * - 人形/手柄图标用 Material Icons 字体自带的标准图标（\uE7FD 人形、\uE30D 手柄，
 *   这是 Material Icons Regular 官方字体表里的标准编码，不是这个项目自定义的）
 */
public class WatermarkHud
extends ClientBase
implements IHudElement {
    private static final FontRenderer logoFont = FontPresets.zenIcon(22.0f);
    private static final FontRenderer wordmarkFont = FontPresets.poppinsBold(15.0f);
    private static final FontRenderer iconFont = FontPresets.materialIcons(13.0f);
    private static final FontRenderer lineFont = FontPresets.poppinsMedium(11.0f);
    private static final FontRenderer separatorFont = FontPresets.poppinsMedium(12.0f);

    private static final String CLIENT_NAME = "ZENAMX";
    private static final String CLIENT_VERSION = "v1.0";
    private static final String PERSON_ICON = "\uE7FD";
    private static final String GAMEPAD_ICON = "\uE30D";

    private static final int primaryColor = new Color(170, 170, 170).getRGB();
    private static final int shadowColor = new Color(0, 0, 0, 100).getRGB();

    private static final float logoWidth = logoFont.getWidth("Z");
    private static final float wordmarkWidth = wordmarkFont.getWidth(CLIENT_NAME);
    private static final float separatorWidth = separatorFont.getWidth("|");
    private static final float personIconWidth = iconFont.getWidth(PERSON_ICON);
    private static final float gamepadIconWidth = iconFont.getWidth(GAMEPAD_ICON);
    private static final float lineHeight = lineFont.getMetrics().capHeight();

    private static final float gapAfterLogo = 8.0f;
    private static final float gapAroundSeparator = 12.0f;
    private static final float gapAfterIcon = 6.0f;

    private int lastTick = -1;
    private String usernameText;
    private float usernameWidth;
    private float versionWidth;
    private float personBlockWidth;
    private String serverText;
    private String pingText;
    private float serverWidth;
    private float pingWidth;
    private float gamepadBlockWidth;

    private void updateCache() {
        if (mc == null || mc.player == null || this.lastTick == mc.player.tickCount) {
            return;
        }
        this.lastTick = mc.player.tickCount;

        this.usernameText = mc.player.getGameProfile().getName();
        this.usernameWidth = lineFont.getWidth(this.usernameText);
        this.versionWidth = lineFont.getWidth(CLIENT_VERSION);
        this.personBlockWidth = Math.max(this.usernameWidth, this.versionWidth);

        String[] serverInfo = this.getServerInfo();
        this.serverText = serverInfo[0];
        this.pingText = serverInfo[1];
        this.serverWidth = lineFont.getWidth(this.serverText);
        this.pingWidth = lineFont.getWidth(this.pingText);
        this.gamepadBlockWidth = Math.max(this.serverWidth, this.pingWidth);
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

        float totalWidth = this.getContentWidth();
        float drawX = x + (width - totalWidth) / 2.0f;
        float centerY = y + height / 2.0f + 1.0f;

        int textColor = this.colorWithAlpha(Color.WHITE.getRGB(), alpha);
        int subColor = this.colorWithAlpha(primaryColor, alpha);
        int shadow = this.colorWithAlpha(shadowColor, alpha);

        try (Paint paint = new Paint()) {
            // Logo + 客户端名字
            this.drawText(drawContext, paint, "Z", drawX, centerY, logoFont, textColor, shadow, true);
            drawX += logoWidth + gapAfterLogo;
            this.drawText(drawContext, paint, CLIENT_NAME, drawX, centerY, wordmarkFont, textColor, shadow, true);
            drawX += wordmarkWidth;

            // 分隔符
            drawX += gapAroundSeparator;
            this.drawText(drawContext, paint, "|", drawX, centerY, separatorFont, subColor, shadow, true);
            drawX += separatorWidth + gapAroundSeparator;

            // 人形图标 + 用户名(上) / 版本号(下)
            this.drawText(drawContext, paint, PERSON_ICON, drawX, centerY, iconFont, subColor, shadow, true);
            drawX += personIconWidth + gapAfterIcon;
            float usernameX = drawX + (this.personBlockWidth - this.usernameWidth) / 2.0f;
            float versionX = drawX + (this.personBlockWidth - this.versionWidth) / 2.0f;
            this.drawText(drawContext, paint, this.usernameText, usernameX, centerY - lineHeight / 2.0f - 1.0f, lineFont, textColor, shadow, false);
            this.drawText(drawContext, paint, CLIENT_VERSION, versionX, centerY + lineHeight / 2.0f + 2.0f, lineFont, subColor, shadow, false);
            drawX += this.personBlockWidth;

            // 分隔符
            drawX += gapAroundSeparator;
            this.drawText(drawContext, paint, "|", drawX, centerY, separatorFont, subColor, shadow, true);
            drawX += separatorWidth + gapAroundSeparator;

            // 手柄图标 + 服务器/单机(上) / 延迟(下)
            this.drawText(drawContext, paint, GAMEPAD_ICON, drawX, centerY, iconFont, subColor, shadow, true);
            drawX += gamepadIconWidth + gapAfterIcon;
            float serverX = drawX + (this.gamepadBlockWidth - this.serverWidth) / 2.0f;
            float pingX = drawX + (this.gamepadBlockWidth - this.pingWidth) / 2.0f;
            this.drawText(drawContext, paint, this.serverText, serverX, centerY - lineHeight / 2.0f - 1.0f, lineFont, textColor, shadow, false);
            this.drawText(drawContext, paint, this.pingText, pingX, centerY + lineHeight / 2.0f + 2.0f, lineFont, subColor, shadow, false);
        }
    }

    private void drawText(DrawContext drawContext, Paint paint, String text, float x, float y, FontRenderer fontRenderer, int color, int shadowColor, boolean centerVertical) {
        float drawY = y;
        if (centerVertical) {
            drawY = y - fontRenderer.getMetrics().capHeight() / 2.0f;
        }
        paint.setColor(shadowColor);
        drawContext.drawString(text, x + 0.5f, drawY + 0.5f, fontRenderer, paint);
        paint.setColor(color);
        drawContext.drawString(text, x, drawY, fontRenderer, paint);
    }

    private float getContentWidth() {
        this.updateCache();
        float width = logoWidth + gapAfterLogo + wordmarkWidth;
        width += gapAroundSeparator + separatorWidth + gapAroundSeparator;
        width += personIconWidth + gapAfterIcon + this.personBlockWidth;
        width += gapAroundSeparator + separatorWidth + gapAroundSeparator;
        width += gamepadIconWidth + gapAfterIcon + this.gamepadBlockWidth;
        return width;
    }

    @Override
    public IHudElement.Size getHudAlignment() {
        return new IHudElement.Size(this.getContentWidth(), 25.0f);
    }

    private String[] getServerInfo() {
        PlayerInfo playerInfo;
        if (mc.isSingleplayer()) {
            return new String[]{"Singleplayer", "1ms"};
        }
        ServerData serverData = mc.getCurrentServer();
        String serverIp = serverData != null ? serverData.ip : "Multiplayer";
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

package shit.zen.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import shit.zen.event.impl.GlRenderEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Module;
import shit.zen.render.FontPresets;
import shit.zen.render.FontRenderer;
import shit.zen.render.GlHelper;
import shit.zen.render.Paint;
import shit.zen.utils.animation.SmoothAnimationTimer;
import shit.zen.utils.math.Easings;
import shit.zen.utils.render.ColorUtil;
import shit.zen.event.EventTarget;

/**
 * 和普通客户端一样的“模块开关提示”：某个模块被开启/关闭时，
 * 从右下角弹出一条 Toast，停留一小段时间后自动淡出。
 * 支持像其他 HUD 一样拖动位置；是否显示可以在模块面板（Render 分类）里单独开关。
 */
public class NotificationHud
extends HudElement {

    public static NotificationHud INSTANCE;

    private static final long DISPLAY_DURATION_MS = 1800L;
    private static final int MAX_VISIBLE_TOASTS = 5;

    private static final class Toast {
        final String moduleName;
        final boolean enabled;
        final long createdAt = System.currentTimeMillis();
        boolean removing = false;
        final SmoothAnimationTimer slideAnim = new SmoothAnimationTimer();
        final SmoothAnimationTimer alphaAnim = new SmoothAnimationTimer();

        Toast(String moduleName, boolean enabled) {
            this.moduleName = moduleName;
            this.enabled = enabled;
            this.slideAnim.setCurrentValue(50.0);
            this.alphaAnim.setCurrentValue(0.0);
            this.slideAnim.animate(0.0, 0.25, Easings.EASE_OUT_POW3);
            this.alphaAnim.animate(1.0, 0.25, Easings.EASE_OUT_POW3);
        }

        void startRemove() {
            if (this.removing) return;
            this.removing = true;
            this.slideAnim.animate(50.0, 0.25, Easings.EASE_IN_POW3);
            this.alphaAnim.animate(0.0, 0.25, Easings.EASE_IN_POW3);
        }

        void tick() {
            this.slideAnim.tick();
            this.alphaAnim.tick();
        }

        boolean isDone() {
            return this.removing && this.alphaAnim.isDone();
        }
    }

    private final List<Toast> toasts = new CopyOnWriteArrayList<>();
    private final FontRenderer nameFont = FontPresets.pingfang(15.0f);
    private final FontRenderer stateFont = FontPresets.pingfang(12.0f);
    private final Paint bgPaint = new Paint();
    private final Paint accentPaint = new Paint();
    private final Paint textPaint = new Paint();
    private boolean positionInitialized = false;

    public NotificationHud() {
        super("Notification");
        INSTANCE = this;
        this.setEnabled(true);
        this.setWidth(150.0f);
        this.setHeight(24.0f);
        this.bgPaint.setAntialias(true);
        this.accentPaint.setAntialias(true);
    }

    /**
     * 由 Module#setEnabled 调用，弹出一条“XXX 已开启/已关闭”的提示。
     */
    public static void notify(Module module, boolean enabled) {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return;
        if (mc == null || mc.player == null) return;
        if (module == null || module.getName() == null || module.getName().isEmpty()) return;
        if (module instanceof NotificationHud) return;
        INSTANCE.toasts.add(new Toast(module.getName(), enabled));
    }

    /**
     * 默认显示在右下角、稍微往上一点的位置（不是死死贴在屏幕最边缘）。
     * 只在第一次渲染时计算一次，之后拖动过的话就用玩家自己拖的位置。
     */
    private void initDefaultPosition() {
        if (this.positionInitialized) return;
        this.positionInitialized = true;
        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        this.setX(screenWidth - 170.0f);
        this.setY(screenHeight - 110.0f);
    }

    @EventTarget
    public void onTick(TickEvent tickEvent) {
        if (mc.player == null) {
            this.toasts.clear();
            return;
        }
        long now = System.currentTimeMillis();
        for (Toast toast : this.toasts) {
            if (!toast.removing && now - toast.createdAt >= DISPLAY_DURATION_MS) {
                toast.startRemove();
            }
        }
        this.toasts.removeIf(Toast::isDone);
    }

    @Override
    public void onRender2D(Render2DEvent render2DEvent, float x, float y) {
    }

    @Override
    public void onGlRender(GlRenderEvent glRenderEvent, float x, float y) {
        this.initDefaultPosition();

        if (this.toasts.isEmpty()) {
            this.setWidth(150.0f);
            this.setHeight(0.0f);
            return;
        }

        this.toasts.forEach(Toast::tick);

        // 只画最新的几条，太旧的直接丢弃，避免堆屏
        List<Toast> visibleToasts = new ArrayList<>(this.toasts);
        while (visibleToasts.size() > MAX_VISIBLE_TOASTS) {
            visibleToasts.remove(0);
        }

        float rowHeight = 22.0f;
        float rowGap = 4.0f;
        float rowWidth = 150.0f;
        float cursorY = y;

        for (Toast toast : visibleToasts) {
            float alpha = toast.alphaAnim.getValueF();
            if (alpha <= 0.01f) {
                cursorY += rowHeight + rowGap;
                continue;
            }
            float slideX = toast.slideAnim.getValueF();
            float rowX = x + slideX;

            this.bgPaint.setColor(ColorUtil.fromARGB(0, 0, 0, (int) (170.0f * alpha)));
            GlHelper.drawRoundedRect(rowX, cursorY, rowWidth, rowHeight, 5.0f, this.bgPaint);

            // 左侧一条小色块：开=绿色，关=灰红色
            int accentColor = toast.enabled
                    ? ColorUtil.fromARGB(80, 220, 120, (int) (255.0f * alpha))
                    : ColorUtil.fromARGB(220, 80, 80, (int) (255.0f * alpha));
            this.accentPaint.setColor(accentColor);
            GlHelper.drawRoundedRect(rowX + 4.0f, cursorY + 4.0f, 3.0f, rowHeight - 8.0f, 1.5f, this.accentPaint);

            int nameColor = ColorUtil.fromARGB(255, 255, 255, (int) (255.0f * alpha));
            this.textPaint.setColor(nameColor);
            float nameY = cursorY + (rowHeight - (float) GlHelper.getFontAscent(this.nameFont)) / 2.0f;
            GlHelper.drawTextWithShadow(toast.moduleName, rowX + 11.0f, nameY, this.nameFont, this.textPaint);

            String stateText = toast.enabled ? "已开启" : "已关闭";
            int stateColor = toast.enabled
                    ? ColorUtil.fromARGB(140, 235, 170, (int) (255.0f * alpha))
                    : ColorUtil.fromARGB(235, 150, 150, (int) (255.0f * alpha));
            this.textPaint.setColor(stateColor);
            float stateWidth = GlHelper.getStringWidth(stateText, this.stateFont);
            float stateY = cursorY + (rowHeight - (float) GlHelper.getFontAscent(this.stateFont)) / 2.0f;
            GlHelper.drawTextWithShadow(stateText, rowX + rowWidth - stateWidth - 8.0f, stateY, this.stateFont, this.textPaint);

            cursorY += rowHeight + rowGap;
        }

        this.setWidth(rowWidth);
        this.setHeight(cursorY - y);
    }

    @Override
    public void onSettings() {
    }
}

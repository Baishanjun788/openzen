package shit.zen.hud;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.util.Mth;
import shit.zen.ZenClient;
import shit.zen.event.impl.GlRenderEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Module;
import shit.zen.modules.impl.render.Interface;
import shit.zen.render.FontPresets;
import shit.zen.render.FontRenderer;
import shit.zen.render.GlHelper;
import shit.zen.utils.animation.SmoothAnimationTimer;
import shit.zen.utils.math.Easings;
import shit.zen.utils.render.ColorUtil;
import shit.zen.event.EventTarget;

/**
 * 模块列表 HUD。
 * 风格参考 Sigma "Jello"：纯白 + 半透明，无背景框，不用彩虹色。
 * 每一项在模块开启/关闭时都有渐进淡入 / 渐退淡出的动画。
 */
public class ModuleListHud
        extends HudElement {

    private static final class Entry {
        final Module module;
        final String name;
        boolean removing = false;
        final SmoothAnimationTimer alphaAnim = new SmoothAnimationTimer();
        final SmoothAnimationTimer slideAnim = new SmoothAnimationTimer();

        Entry(Module module) {
            this.module = module;
            this.name = module.getName();
            this.alphaAnim.setCurrentValue(0.0);
            this.slideAnim.setCurrentValue(10.0);
            this.alphaAnim.animate(1.0, 0.18, Easings.EASE_OUT_POW3);
            this.slideAnim.animate(0.0, 0.18, Easings.EASE_OUT_POW3);
        }

        void startRemove() {
            if (this.removing) return;
            this.removing = true;
            this.alphaAnim.animate(0.0, 0.18, Easings.EASE_IN_POW3);
            this.slideAnim.animate(10.0, 0.18, Easings.EASE_IN_POW3);
        }

        void cancelRemove() {
            if (!this.removing) return;
            this.removing = false;
            this.alphaAnim.animate(1.0, 0.18, Easings.EASE_OUT_POW3);
            this.slideAnim.animate(0.0, 0.18, Easings.EASE_OUT_POW3);
        }

        void tick() {
            this.alphaAnim.tick();
            this.slideAnim.tick();
        }

        boolean isRemoveDone() {
            return this.removing && this.alphaAnim.isDone();
        }
    }

    private final FontRenderer titleFont = FontPresets.pingfang(16.0f);
    private final FontRenderer entryFont = FontPresets.pingfang(16.0f);
    private final List<Entry> entries = new ArrayList<>();
    private final Map<Module, Entry> entryMap = new IdentityHashMap<>();

    public ModuleListHud() {
        super("ModuleList");
        this.setWidth(120.0f);
        this.setHeight(30.0f);
    }

    private List<Module> getVisibleModules() {
        return ZenClient.getInstance().getModuleManager().getModules().stream()
                .filter(module -> !(module instanceof ModuleListHud) && !(module instanceof Interface))
                .filter(Module::isEnabled)
                .filter(module -> !module.getName().isEmpty())
                .sorted((a, b) -> Mth.ceil(GlHelper.getStringWidth(b.getName(), this.entryFont) - GlHelper.getStringWidth(a.getName(), this.entryFont)))
                .collect(Collectors.toList());
    }

    @EventTarget
    public void onTick(TickEvent tickEvent) {
        if (mc.player == null) {
            this.entries.clear();
            this.entryMap.clear();
            return;
        }

        List<Module> visible = this.getVisibleModules();

        // 已经不在“启用列表”里的条目开始播放淡出动画（而不是直接消失）
        for (Entry entry : this.entries) {
            boolean stillVisible = false;
            for (Module module : visible) {
                if (module == entry.module) {
                    stillVisible = true;
                    break;
                }
            }
            if (!stillVisible) {
                entry.startRemove();
            }
        }

        // 新开启的模块加入列表并播放淡入动画；重新开启的（还没来得及消失就又点开了）取消淡出
        for (Module module : visible) {
            Entry existing = this.entryMap.get(module);
            if (existing == null) {
                Entry entry = new Entry(module);
                this.entries.add(entry);
                this.entryMap.put(module, entry);
            } else {
                existing.cancelRemove();
            }
        }

        // 按“启用中的模块”原本的排序重新排列，正在淡出的旧条目保持在末尾，直到动画播完再被移除
        List<Entry> ordered = new ArrayList<>();
        for (Module module : visible) {
            ordered.add(this.entryMap.get(module));
        }
        for (Entry entry : this.entries) {
            if (entry.removing && !ordered.contains(entry)) {
                ordered.add(entry);
            }
        }
        this.entries.clear();
        this.entries.addAll(ordered);
    }

    @Override
    public void onRender2D(Render2DEvent render2DEvent, float x, float y) {
    }

    @EventTarget
    public void onGlRenderDirect(GlRenderEvent glRenderEvent) {
        if (!this.isEnabled()) {
            return;
        }
        if (!ZenClient.getInstance().getModuleManager().getModule(Interface.class).isEnabled()) {
            return;
        }

        this.entries.removeIf(entry -> {
            boolean done = entry.isRemoveDone();
            if (done) {
                this.entryMap.remove(entry.module);
            }
            return done;
        });
        this.entries.forEach(Entry::tick);

        // 真正使用可拖动坐标渲染
        float baseX = this.getX();
        float baseY = this.getY();

        // 纯白半透明标题
        int titleColor = ColorUtil.fromARGB(255, 255, 255, 235);
        String titleText = "ZenAMX (" + mc.getFps() + "FPS)";
        GlHelper.drawTextShadowLegacy(titleText, baseX, baseY, this.titleFont, titleColor);

        // 🛠️ 修改点 1：如果你的 GlHelper 支持 getFontHeight()，直接用它。
        // 如果没有这个方法，请查看底层 FontRenderer 的获取高度函数（通常是 getHeight() 或常量 9.0f-14.0f）。
        // 这里提供了一个标准的安全 Fallback 机制：如果 Ascent 过小（比如小于 5），强行给一个与 16 磅字号匹配的固定基础行高（12像素）+ 4像素间距。
        float fontHeight = (float) GlHelper.getFontAscent(this.entryFont);
        if (fontHeight < 9.0f) {
            fontHeight = 12.0f;
        }
        float rowSpacing = fontHeight + 1.2f; // 🛠️ 适当增加字与字之间的空白间距（从 2.0f 改为 4.0f）

        float titleHeight = (float) GlHelper.getFontAscent(this.titleFont);
        if (titleHeight < 9.0f) {
            titleHeight = 12.0f;
        }
        float offsetY = titleHeight + 1.0f; // 🛠️ 标题和第一行模块名之间的间距调大，防止它们粘在一起

        float maxWidth = GlHelper.getStringWidth(titleText, this.titleFont);

        for (Entry entry : this.entries) {
            float alpha = entry.alphaAnim.getValueF();
            if (alpha <= 0.01f) {
                // 即使淡出隐藏了，为了防止列表乱跳，这一行在没有彻底移除前依然占用一个间距
                offsetY += rowSpacing;
                continue;
            }
            float slideX = entry.slideAnim.getValueF();
            // 纯白半透明：模块名比标题略淡一点，营造层次感
            int color = ColorUtil.fromARGB(255, 255, 255, (int) (190.0f * alpha));
            GlHelper.drawTextShadowLegacy(entry.name, baseX + slideX, baseY + offsetY, this.entryFont, color);

            float w = GlHelper.getStringWidth(entry.name, this.entryFont) + slideX;
            if (w > maxWidth) {
                maxWidth = w;
            }
            offsetY += rowSpacing;
        }

        this.setWidth(maxWidth + 4.0f);
        this.setHeight(offsetY);
    }

    @Override
    public void onGlRender(GlRenderEvent glRenderEvent, float x, float y) {
    }

    @Override
    public void onSettings() {
    }
}
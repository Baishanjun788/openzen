package shit.zen.modules.impl.render;

import shit.zen.event.EventTarget;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.settings.impl.ModeSetting;

/**
 * 本地修改游戏时间（白天 / 中午 / 夜晚），纯客户端视觉效果：
 * 只改自己看到的天空亮度和光照，不会发包给服务器，别人看到的时间还是服务器原本的时间，
 * 不提供任何游戏内的实际优势，纯粹是自己看着舒服（比如晚上不想开夜视也能亮堂堂）。
 *
 * 原理：Minecraft 一天是 24000 tick：
 *   0        = 日出
 *   6000     = 正午（最亮）
 *   12000    = 日落
 *   13000~18000 = 夜晚
 *   18000    = 午夜
 * 每个客户端 tick 都把当前 level 的时间强制改写成"当前天数 * 24000 + 目标时间点"，
 * 这样即使服务器一直在正常推进时间，也会被立刻覆盖回你想要的时间段，看起来就像是
 * 永远停在白天/中午/夜晚。
 */
public class FakeTime extends Module {

    public final ModeSetting mode = new ModeSetting("Time", "Day", "Noon", "Night").withDefault("Day");

    private static final long TICKS_PER_DAY = 24000L;
    private static final long DAY_TIME = 1000L;   // 白天（刚日出不久，亮度已经拉满）
    private static final long NOON_TIME = 6000L;  // 正午，太阳在正上方
    private static final long NIGHT_TIME = 14000L; // 夜晚（天已经全黑，怪物会正常生成）

    public FakeTime() {
        super("FakeTime", Category.RENDER);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.level == null) {
            return;
        }

        long targetTimeOfDay;
        if (this.mode.is("Noon")) {
            targetTimeOfDay = NOON_TIME;
        } else if (this.mode.is("Night")) {
            targetTimeOfDay = NIGHT_TIME;
        } else {
            targetTimeOfDay = DAY_TIME;
        }

        long currentDayTime = mc.level.getDayTime();
        long currentDayIndex = currentDayTime / TICKS_PER_DAY;
        long fixedDayTime = currentDayIndex * TICKS_PER_DAY + targetTimeOfDay;

        // 已经在目标时间点就不用重复设置，省一点没必要的调用
        if (currentDayTime != fixedDayTime) {
            mc.level.setDayTime(fixedDayTime);
        }
    }
}

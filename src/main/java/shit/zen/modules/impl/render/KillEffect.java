package shit.zen.modules.impl.render;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Squid;

import shit.zen.event.EventTarget;
import shit.zen.event.impl.EntityHurtEvent;
import shit.zen.event.impl.EntityRemoveEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.utils.misc.SoundUtil;

/**
 * 移植自 LiquidBounce 的 KillEffect（原注释：仿制"木糖醇"客户端的击杀特效）。
 *
 * 逻辑：只要是被你打过的生物（近战、弓箭/三叉戟等弹射物命中都算），死亡的瞬间会在它原本
 * 所在的位置生成一只本地假墨鱼，缓慢上升一段时间后原地炸开一圈火焰粒子并消失，
 * 同时播放一次击杀音效。这只墨鱼是纯客户端本地实体，不会发送到服务器，别人看不到。
 *
 * 和原版 1.8.9 Kotlin 代码的主要区别：
 * 1. 原版用一个可空的 `target` 变量只记录"最近一次攻击的目标"，同一时间只能有一只墨鱼在飘。
 *    这里改成用 Set<UUID> 记录所有"被我打过、还没死"的实体 + List 管理多只同时存在的墨鱼，
 *    这样连续击杀多个目标时，每个目标死亡都能各自触发一次效果，不会互相覆盖。
 * 2. 原版监听自定义的 AttackEvent（"我攻击了谁"）；Zen 里没有这个事件，
 *    所以改成监听 EntityHurtEvent，判断伤害来源是不是自己（damageSource.getEntity() == mc.player），
 *    这样近战 / 弓箭 / 三叉戟等只要是你造成的伤害都算数，覆盖面比原版单纯记"最近攻击目标"更广。
 * 3. 原版有一段 `anim`（ContinualAnimation）相关代码，但从头到尾都没有被实际用来影响任何
 *    渲染效果（只是重复把墨鱼设置成它自己当前的坐标），属于原版里的死代码，这里没有照搬。
 * 4. 原版直接摆弄了 EntitySquid 的 squidPitch/squidYaw/squidRotation 等 1.8.9 专属字段来让
 *    墨鱼保持固定姿态、不要自己乱游。新版 Minecraft 的 Squid 类字段命名不一样（而且具体名字
 *    依赖你项目用的 mapping 版本，我这边没法直接确认），所以这部分先没加；如果想要墨鱼完全
 *    保持固定姿势不摆动触手，把你项目里 Squid.java 反编译出来的源码发我，我再补上这部分。
 *
 * 需要你确认 / 按需调整的地方：
 * - SoundUtil.playSound("kill.wav", 1.0f) 里的文件名是占位符，需要换成你项目 config 目录下
 *   实际存在的音效文件名（参考 SoundUtil 的用法，同目录下别的模块是怎么传文件名的）。
 */
public class KillEffect extends Module {

    private static final double RISE_SPEED = 0.21145; // 原版上升速度，直接照搬
    private static final double PERCENT_STEP_MAX = 0.048; // 每 tick 上升进度最大随机增量，原版数值

    private static final class SquidEffect {
        final Squid squid;
        double percent = 0.0;

        SquidEffect(Squid squid) {
            this.squid = squid;
        }
    }

    // 记录"被我打过、还没死"的实体 UUID；实体真正死亡触发效果后会从这里移除，不会无限增长
    private final Set<UUID> damagedByMe = new HashSet<>();
    // 当前正在上升/等待爆炸的假墨鱼列表，支持同时存在多只
    private final List<SquidEffect> activeSquids = new ArrayList<>();

    public KillEffect() {
        super("KillEffect", Category.RENDER);
    }

    private static double easeInOutCirc(double x) {
        return x < 0.5
                ? (1.0 - Math.sqrt(1.0 - Math.pow(2.0 * x, 2.0))) / 2.0
                : (Math.sqrt(1.0 - Math.pow(-2.0 * x + 2.0, 2.0)) + 1.0) / 2.0;
    }

    /**
     * 只要伤害来源是自己，就把这个实体标记为"被我打过"。
     */
    @EventTarget
    public void onEntityHurt(EntityHurtEvent event) {
        if (mc.player == null) {
            return;
        }
        if (event.damageSource().getEntity() == mc.player) {
            this.damagedByMe.add(event.entity().getUUID());
        }
    }

    /**
     * 实体真正死亡时（dead = true），如果它被我打过，就在原地生成一只假墨鱼并开始上升。
     */
    @EventTarget
    public void onEntityRemove(EntityRemoveEvent event) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!event.dead()) {
            return;
        }

        Entity entity = event.entity();
        if (!(entity instanceof LivingEntity livingEntity)) {
            return;
        }
        if (!this.damagedByMe.remove(livingEntity.getUUID())) {
            return;
        }

        SoundUtil.playSound("kill.wav", 1.0f);

        Squid squid = new Squid(EntityType.SQUID, mc.level);
        squid.setPos(livingEntity.getX(), livingEntity.getY(), livingEntity.getZ());
        squid.setDeltaMovement(0.0, RISE_SPEED, 0.0);
        squid.setNoGravity(true);
        squid.setInvulnerable(true);
        mc.level.addFreshEntity(squid);

        this.activeSquids.add(new SquidEffect(squid));
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.level == null) {
            for (SquidEffect effect : this.activeSquids) {
                effect.squid.discard();
            }
            this.activeSquids.clear();
            return;
        }

        Iterator<SquidEffect> iterator = this.activeSquids.iterator();
        while (iterator.hasNext()) {
            SquidEffect effect = iterator.next();
            Squid squid = effect.squid;

            if (squid.isRemoved()) {
                iterator.remove();
                continue;
            }

            if (effect.percent < 1.0) {
                effect.percent += Math.random() * PERCENT_STEP_MAX;
            }

            if (effect.percent >= 1.0) {
                // 上升结束：原地炸开一圈火焰粒子，然后移除这只假墨鱼
                for (int i = 0; i < 8; i++) {
                    mc.level.addParticle(
                            ParticleTypes.FLAME,
                            squid.getX(), squid.getY(), squid.getZ(),
                            (Math.random() - 0.5) * 0.2,
                            Math.random() * 0.2,
                            (Math.random() - 0.5) * 0.2
                    );
                }
                squid.discard();
                iterator.remove();
                continue;
            }

            // 保持持续上升
            squid.setDeltaMovement(0.0, RISE_SPEED, 0.0);
        }
    }

    @Override
    public void onDisable() {
        for (SquidEffect effect : this.activeSquids) {
            effect.squid.discard();
        }
        this.activeSquids.clear();
        this.damagedByMe.clear();
    }
}

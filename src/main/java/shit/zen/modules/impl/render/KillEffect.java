package shit.zen.modules.impl.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Squid;

import shit.zen.event.EventTarget;
import shit.zen.event.impl.EntityHurtEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.utils.misc.SoundUtil;

/**
 * 移植自 LiquidBounce 的 KillEffect（原注释：仿制"木糖醇"客户端的击杀特效）。
 *
 * v2 修复说明（上一版完全不生效的原因）：
 * 1. 上一版用 EntityRemoveEvent 来判断"实体是否死亡"，但查了 PlayerPatch.java 才发现，
 *    这个事件其实是在本地玩家调用 Player.attack() 的前后触发的（dead=false 攻击前，
 *    dead=true 攻击后），跟目标实际死没死完全没关系，纯粹是命名有误导性，从一开始
 *    条件就没写对。
 * 2. 现在改成更直接、不依赖任何自定义事件语义的办法：被打过的实体直接用 UUID 存进一个
 *    Map 里持有引用，每 tick 检查它是不是真的从世界里消失了（entity.isRemoved()，
 *    这是原版 Minecraft Entity 自带的方法，跟任何自定义事件无关，不会判断错）。
 *
 * 另外提醒：这个文件本身只是加进项目里，还需要你自己在 ModuleManager 里加一行
 *     this.register(new KillEffect());
 * 并且默认是关闭的，进游戏后要去 ClickGui 里手动点开才会生效。
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

    // 被我打过、还没确认死亡的实体：UUID -> 实体引用
    private final Map<UUID, LivingEntity> damagedByMe = new HashMap<>();
    // 当前正在上升/等待爆炸的假墨鱼列表，支持同时存在多只
    private final List<SquidEffect> activeSquids = new ArrayList<>();

    public KillEffect() {
        super("KillEffect", Category.RENDER);
    }

    /**
     * 只要伤害来源是自己，就把这个实体记下来（持有引用，方便之后检查它是否被移除）。
     */
    @EventTarget
    public void onEntityHurt(EntityHurtEvent event) {
        if (mc.player == null) {
            return;
        }
        if (event.damageSource().getEntity() == mc.player) {
            this.damagedByMe.put(event.entity().getUUID(), event.entity());
        }
    }

    /**
     * 每 tick 检查"被我打过"的实体是不是已经真正从世界里消失了（isRemoved）。
     * 消失了就认为是被打死了，触发特效；如果它还活着就继续留在集合里等下一次检查。
     */
    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.level == null) {
            for (SquidEffect effect : this.activeSquids) {
                effect.squid.discard();
            }
            this.activeSquids.clear();
            this.damagedByMe.clear();
            return;
        }

        // 检查是否有目标真正死亡：
        // - 怪物：死亡后会被移除，isRemoved() 会变 true
        // - 玩家：死亡后通常不会被移除（会保持死亡姿势直到重生），要用血量/死亡状态来判断
        // 两个条件谁先满足就算死亡，避免玩家被打死后一直卡在集合里不触发
        Iterator<Map.Entry<UUID, LivingEntity>> trackedIterator = this.damagedByMe.entrySet().iterator();
        while (trackedIterator.hasNext()) {
            Map.Entry<UUID, LivingEntity> entry = trackedIterator.next();
            LivingEntity livingEntity = entry.getValue();

            boolean isDead = livingEntity.isRemoved()
                    || livingEntity.isDeadOrDying()
                    || livingEntity.getHealth() <= 0.0f;
            if (!isDead) {
                continue;
            }

            trackedIterator.remove();
            this.spawnSquid(livingEntity.getX(), livingEntity.getY(), livingEntity.getZ());
        }

        // 推进所有正在上升的假墨鱼
        Iterator<SquidEffect> squidIterator = this.activeSquids.iterator();
        while (squidIterator.hasNext()) {
            SquidEffect effect = squidIterator.next();
            Squid squid = effect.squid;

            if (squid.isRemoved()) {
                squidIterator.remove();
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
                squidIterator.remove();
                continue;
            }

            // 保持持续上升
            squid.setDeltaMovement(0.0, RISE_SPEED, 0.0);
        }
    }

    private void spawnSquid(double x, double y, double z) {
        SoundUtil.playSound("kill.wav", 1.0f);

        Squid squid = new Squid(EntityType.SQUID, mc.level);
        squid.setPos(x, y, z);
        squid.setDeltaMovement(0.0, RISE_SPEED, 0.0);
        squid.setNoGravity(true);
        squid.setInvulnerable(true);
        mc.level.addFreshEntity(squid);

        this.activeSquids.add(new SquidEffect(squid));
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

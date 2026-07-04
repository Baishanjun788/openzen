package shit.zen.modules.impl.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import shit.zen.event.EventTarget;
import shit.zen.event.impl.EntityHurtEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.utils.misc.SoundUtil;

public class KillEffect extends Module {

    // 减速0.5倍参数
    private static final double RISE_SPEED = 0.05;
    private static final double PERCENT_STEP = 0.01;

    private static final class SquidEffect {
        final Squid squid;
        double percent = 0.0;

        SquidEffect(Squid squid) {
            this.squid = squid;
        }
    }

    // 缓存：受伤实体 -> 受伤时坐标（解决玩家死亡实体消失无法读取位置）
    private final Map<UUID, Vec3> hurtPosCache = new HashMap<>();
    // 旧追踪表兜底
    private final Map<UUID, LivingEntity> damagedByMe = new HashMap<>();
    private final List<SquidEffect> activeSquids = new ArrayList<>();

    public KillEffect() {
        super("KillEffect", Category.RENDER);
    }

    @EventTarget
    public void onEntityHurt(EntityHurtEvent event) {
        if (mc.player == null) return;

        LivingEntity target = event.entity();
        // 攻击者是自己，目标不是自己
        if (event.damageSource().getEntity() == mc.player && target != mc.player) {
            UUID uuid = target.getUUID();
            damagedByMe.put(uuid, target);
            // 关键：受伤瞬间保存坐标，玩家死后实体消失也能拿到位置
            hurtPosCache.put(uuid, new Vec3(target.getX(), target.getY(), target.getZ()));
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.level == null || mc.player == null) {
            clearEffects();
            return;
        }

        // 1. 遍历受伤缓存，优先处理玩家（核心修复）
        Iterator<Map.Entry<UUID, Vec3>> cacheIter = hurtPosCache.entrySet().iterator();
        while (cacheIter.hasNext()) {
            Map.Entry<UUID, Vec3> entry = cacheIter.next();
            UUID uuid = entry.getKey();
            Vec3 pos = entry.getValue();
            LivingEntity target = damagedByMe.get(uuid);

            boolean trigger = false;

            // 情况A：实体还存在，正常死亡判定
            if (target != null && !target.isRemoved()) {
                if (target.isDeadOrDying() || target.getHealth() <= 0f) {
                    trigger = true;
                }
            }
            // 情况B：实体已经被移除（玩家死亡必走这里），直接触发特效
            else {
                trigger = true;
            }

            if (trigger) {
                // 用受伤时保存的坐标生成鱿鱼
                spawnSquid(pos.x, pos.y + 1.0, pos.z);
                // 清理两条记录
                damagedByMe.remove(uuid);
                cacheIter.remove();
            }
        }

        // 2. 清理残留失效实体（兜底防内存泄漏）
        Iterator<Map.Entry<UUID, LivingEntity>> trackIter = damagedByMe.entrySet().iterator();
        while (trackIter.hasNext()) {
            Map.Entry<UUID, LivingEntity> entry = trackIter.next();
            LivingEntity entity = entry.getValue();
            if (entity.isRemoved()) {
                trackIter.remove();
            }
        }

        // 3. 更新上升鱿鱼动画
        Iterator<SquidEffect> squidIterator = activeSquids.iterator();
        while (squidIterator.hasNext()) {
            SquidEffect effect = squidIterator.next();
            Squid squid = effect.squid;

            if (squid.isRemoved()) {
                squidIterator.remove();
                continue;
            }

            if (effect.percent < 1.0) {
                effect.percent += PERCENT_STEP;
            }

            if (effect.percent >= 1.0) {
                double explosionY = squid.getY() + 0.5;
                mc.level.addParticle(ParticleTypes.EXPLOSION, squid.getX(), explosionY, squid.getZ(), 0, 0, 0);

                for (int i = 0; i < 40; i++) {
                    double speedX = (Math.random() - 0.5) * 0.8;
                    double speedY = (Math.random() - 0.5) * 0.8;
                    double speedZ = (Math.random() - 0.5) * 0.8;
                    mc.level.addParticle(ParticleTypes.FIREWORK, squid.getX(), explosionY, squid.getZ(), speedX, speedY, speedZ);
                    mc.level.addParticle(ParticleTypes.FLAME, squid.getX(), explosionY, squid.getZ(), speedX, speedY, speedZ);
                }

                squid.discard();
                mc.level.removeEntity(squid.getId(), Entity.RemovalReason.DISCARDED);
                squidIterator.remove();
                continue;
            }

            squid.setPos(squid.getX(), squid.getY() + RISE_SPEED, squid.getZ());
            squid.setDeltaMovement(0.0, RISE_SPEED, 0.0);
        }
    }

    private void spawnSquid(double x, double y, double z) {
        SoundUtil.playSound("kill.wav", 1.0f);

        Squid squid = new Squid(EntityType.SQUID, mc.level);
        int fakeEntityId = -114514 - (int) (Math.random() * 100000);
        squid.setId(fakeEntityId);

        squid.setPos(x, y, z);
        squid.setNoGravity(true);
        squid.setInvulnerable(true);
        squid.setYRot(mc.player.getYRot());

        mc.level.putNonPlayerEntity(squid.getId(), squid);
        activeSquids.add(new SquidEffect(squid));
    }

    private void clearEffects() {
        if (mc.level != null) {
            for (SquidEffect effect : activeSquids) {
                effect.squid.discard();
                mc.level.removeEntity(effect.squid.getId(), Entity.RemovalReason.DISCARDED);
            }
        }
        activeSquids.clear();
        damagedByMe.clear();
        hurtPosCache.clear();
    }

    @Override
    public void onDisable() {
        clearEffects();
    }
}
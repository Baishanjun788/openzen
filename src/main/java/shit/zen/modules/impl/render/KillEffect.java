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

import shit.zen.event.EventTarget;
import shit.zen.event.impl.EntityHurtEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.utils.misc.SoundUtil;

public class KillEffect extends Module {

    // 原速度0.1，减速0.5倍 → 0.05 每tick上升格数
    private static final double RISE_SPEED = 0.05;
    // 进度步长同步减半，总上升时长翻倍，高度依旧5格不变
    // 原50tick，现在需要100tick完成上升，速度慢一半
    private static final double PERCENT_STEP = 0.01;

    private static final class SquidEffect {
        final Squid squid;
        double percent = 0.0;

        SquidEffect(Squid squid) {
            this.squid = squid;
        }
    }

    // 修复：使用实体UUID作为key，杜绝同名实体覆盖、玩家识别失效问题
    private final Map<UUID, LivingEntity> damagedByMe = new HashMap<>();

    // 当前正在上升的假墨鱼列表
    private final List<SquidEffect> activeSquids = new ArrayList<>();

    public KillEffect() {
        super("KillEffect", Category.RENDER);
    }

    @EventTarget
    public void onEntityHurt(EntityHurtEvent event) {
        if (mc.player == null) return;

        LivingEntity target = event.entity();
        // 攻击者是自己，且目标不是自己
        if (event.damageSource().getEntity() == mc.player && target != mc.player) {
            // 用唯一UUID存储，不会出现同名玩家覆盖
            damagedByMe.put(target.getUUID(), target);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.level == null || mc.player == null) {
            clearEffects();
            return;
        }

        // 1. 遍历所有被我攻击过的实体，检测是否死亡
        Iterator<Map.Entry<UUID, LivingEntity>> trackedIterator = damagedByMe.entrySet().iterator();
        while (trackedIterator.hasNext()) {
            Map.Entry<UUID, LivingEntity> entry = trackedIterator.next();
            LivingEntity entity = entry.getValue();

            // 实体已经被世界移除，直接清理记录
            if (entity.isRemoved()) {
                trackedIterator.remove();
                continue;
            }

            // 统一死亡判定：血量<=0 / 死亡动画播放中，玩家和生物通用
            boolean isDead = entity.isDeadOrDying() || entity.getHealth() <= 0f;

            // 修复玩家失效问题：不再遍历玩家列表，玩家死亡/下线后 isDeadOrDying 会正常标记
            if (isDead) {
                // 生成升天鱿鱼，Y+1防止卡地面
                spawnSquid(entity.getX(), entity.getY() + 1.0, entity.getZ());
                trackedIterator.remove();
            }
        }

        // 2. 更新所有上升鱿鱼动画
        Iterator<SquidEffect> squidIterator = activeSquids.iterator();
        while (squidIterator.hasNext()) {
            SquidEffect effect = squidIterator.next();
            Squid squid = effect.squid;

            if (squid.isRemoved()) {
                squidIterator.remove();
                continue;
            }

            // 匀速增加进度
            if (effect.percent < 1.0) {
                effect.percent += PERCENT_STEP;
            }

            // 到达最高点，爆炸销毁鱿鱼
            if (effect.percent >= 1.0) {
                double explosionY = squid.getY() + 0.5;
                // 爆炸核心粒子
                mc.level.addParticle(ParticleTypes.EXPLOSION, squid.getX(), explosionY, squid.getZ(), 0, 0, 0);

                // 烟花火花+火焰粒子
                for (int i = 0; i < 40; i++) {
                    double speedX = (Math.random() - 0.5) * 0.8;
                    double speedY = (Math.random() - 0.5) * 0.8;
                    double speedZ = (Math.random() - 0.5) * 0.8;
                    mc.level.addParticle(ParticleTypes.FIREWORK, squid.getX(), explosionY, squid.getZ(), speedX, speedY, speedZ);
                    mc.level.addParticle(ParticleTypes.FLAME, squid.getX(), explosionY, squid.getZ(), speedX, speedY, speedZ);
                }

                // 清理假实体
                squid.discard();
                mc.level.removeEntity(squid.getId(), Entity.RemovalReason.DISCARDED);
                squidIterator.remove();
                continue;
            }

            // 缓慢上升
            squid.setPos(squid.getX(), squid.getY() + RISE_SPEED, squid.getZ());
            squid.setDeltaMovement(0.0, RISE_SPEED, 0.0);
        }
    }

    private void spawnSquid(double x, double y, double z) {
        SoundUtil.playSound("kill.wav", 1.0f);

        Squid squid = new Squid(EntityType.SQUID, mc.level);
        // 随机负数假实体ID，避免和真实实体冲突
        int fakeEntityId = -114514 - (int) (Math.random() * 100000);
        squid.setId(fakeEntityId);

        squid.setPos(x, y, z);
        squid.setNoGravity(true);
        squid.setInvulnerable(true);
        squid.setYRot(mc.player.getYRot());

        mc.level.putNonPlayerEntity(squid.getId(), squid);
        activeSquids.add(new SquidEffect(squid));
    }

    // 清空所有特效
    private void clearEffects() {
        if (mc.level != null) {
            for (SquidEffect effect : activeSquids) {
                effect.squid.discard();
                mc.level.removeEntity(effect.squid.getId(), Entity.RemovalReason.DISCARDED);
            }
        }
        activeSquids.clear();
        damagedByMe.clear();
    }

    @Override
    public void onDisable() {
        clearEffects();
    }
}
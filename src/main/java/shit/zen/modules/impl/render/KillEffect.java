package shit.zen.modules.impl.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

    // 丝滑上升速度：每 tick 0.1 格（每秒 2 格）
    private static final double RISE_SPEED = 0.1;
    // 匀速进度：每 tick 增加 0.02 (共需 50 tick = 2.5 秒)
    // 最终高度 = 50 tick * 0.1 = 精准 5.0 格！
    private static final double PERCENT_STEP = 0.02;

    private static final class SquidEffect {
        final Squid squid;
        double percent = 0.0;

        SquidEffect(Squid squid) {
            this.squid = squid;
        }
    }

    // 用名字来记录实体：名字 -> 实体引用
    private final Map<String, LivingEntity> damagedByMe = new HashMap<>();

    // 当前正在上升的假墨鱼列表
    private final List<SquidEffect> activeSquids = new ArrayList<>();

    public KillEffect() {
        super("KillEffect", Category.RENDER);
    }

    @EventTarget
    public void onEntityHurt(EntityHurtEvent event) {
        if (mc.player == null) {
            return;
        }

        LivingEntity target = event.entity();

        // 如果是我打的，并且打的不是我自己
        if (event.damageSource().getEntity() == mc.player && target != mc.player) {
            // 用名字作为 Key 存进小本本
            this.damagedByMe.put(target.getName().getString(), target);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.level == null || mc.player == null) {
            clearEffects();
            return;
        }

        // 1. 检查“被我打过”的实体情况
        Iterator<Map.Entry<String, LivingEntity>> trackedIterator = this.damagedByMe.entrySet().iterator();
        while (trackedIterator.hasNext()) {
            Map.Entry<String, LivingEntity> entry = trackedIterator.next();
            String targetName = entry.getKey();
            LivingEntity entity = entry.getValue();

            // 基础判断：血量归零或者正在播放死亡动画
            boolean isDead = entity.isDeadOrDying() || entity.getHealth() <= 0.0f;

            // 针对玩家的特殊判断：看他是不是从世界里“消失”了
            if (!isDead && entity instanceof Player) {
                boolean stillInWorld = false;
                for (Player p : mc.level.players()) {
                    if (p.getName().getString().equals(targetName)) {
                        stillInWorld = true;
                        break;
                    }
                }
                // 如果在当前客户端的玩家列表里找不到他了，说明他死了/跑了/退了，直接触发！
                if (!stillInWorld) {
                    isDead = true;
                }
            }

            if (isDead) {
                // 触发升天鱿鱼，保持 Y+1.0 防止卡地
                this.spawnSquid(entity.getX(), entity.getY() + 1.0, entity.getZ());
                trackedIterator.remove();
                continue;
            }

            // 对于普通怪物，如果单纯是因为走太远被卸载(isRemoved)而不是死亡，移除记录防内存泄漏
            if (entity.isRemoved()) {
                trackedIterator.remove();
            }
        }

        // 2. 推进所有正在上升的假墨鱼
        Iterator<SquidEffect> squidIterator = this.activeSquids.iterator();
        while (squidIterator.hasNext()) {
            SquidEffect effect = squidIterator.next();
            Squid squid = effect.squid;

            if (squid.isRemoved()) {
                squidIterator.remove();
                continue;
            }

            // 【关键修改】：去掉了 Math.random()，改为绝对匀速增加，保证高度死死卡在 5 格
            if (effect.percent < 1.0) {
                effect.percent += PERCENT_STEP;
            }

            if (effect.percent >= 1.0) {
                // 上升结束：播放一个真实的大型烟花爆炸效果
                double explosionY = squid.getY() + 0.5;

                // 核心的爆炸巨响和烟雾粒子
                mc.level.addParticle(ParticleTypes.EXPLOSION, squid.getX(), explosionY, squid.getZ(), 0, 0, 0);

                // 炸开 40 个烟花火花和火焰，向四面八方扩散
                for (int i = 0; i < 40; i++) {
                    double speedX = (Math.random() - 0.5) * 0.8;
                    double speedY = (Math.random() - 0.5) * 0.8;
                    double speedZ = (Math.random() - 0.5) * 0.8;

                    mc.level.addParticle(
                            ParticleTypes.FIREWORK,
                            squid.getX(), explosionY, squid.getZ(),
                            speedX, speedY, speedZ
                    );
                    mc.level.addParticle(
                            ParticleTypes.FLAME,
                            squid.getX(), explosionY, squid.getZ(),
                            speedX, speedY, speedZ
                    );
                }

                // 彻底从客户端清理假实体
                squid.discard();
                mc.level.removeEntity(squid.getId(), Entity.RemovalReason.DISCARDED);

                squidIterator.remove();
                continue;
            }

            // 强制平滑修改 Y 轴坐标
            squid.setPos(squid.getX(), squid.getY() + RISE_SPEED, squid.getZ());
            squid.setDeltaMovement(0.0, RISE_SPEED, 0.0);
        }
    }

    private void spawnSquid(double x, double y, double z) {
        SoundUtil.playSound("kill.wav", 1.0f);

        Squid squid = new Squid(EntityType.SQUID, mc.level);

        int fakeEntityId = -114514 - (int)(Math.random() * 100000);
        squid.setId(fakeEntityId);

        squid.setPos(x, y, z);
        squid.setNoGravity(true);
        squid.setInvulnerable(true);
        squid.setYRot(mc.player.getYRot());

        mc.level.putNonPlayerEntity(squid.getId(), squid);

        this.activeSquids.add(new SquidEffect(squid));
    }

    private void clearEffects() {
        if (mc.level != null) {
            for (SquidEffect effect : this.activeSquids) {
                effect.squid.discard();
                mc.level.removeEntity(effect.squid.getId(), Entity.RemovalReason.DISCARDED);
            }
        }
        this.activeSquids.clear();
        this.damagedByMe.clear();
    }

    @Override
    public void onDisable() {
        clearEffects();
    }
}
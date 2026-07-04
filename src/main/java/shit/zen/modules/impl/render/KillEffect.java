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

import shit.zen.event.EventTarget;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.utils.misc.SoundUtil;

public class KillEffect extends Module {

    private static final double RISE_SPEED = 0.21145;
    private static final double PERCENT_STEP_MAX = 0.048;

    private static final class SquidEffect {
        final Squid squid;
        double percent = 0.0;

        SquidEffect(Squid squid) {
            this.squid = squid;
        }
    }

    // 记录上一 tick 还在视野内的活物：UUID -> 实体引用
    private final Map<UUID, LivingEntity> knownEntities = new HashMap<>();

    // 当前正在上升的假墨鱼列表
    private final List<SquidEffect> activeSquids = new ArrayList<>();

    public KillEffect() {
        super("KillEffect", Category.RENDER);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.level == null || mc.player == null) {
            clearEffects();
            return;
        }

        // 1. 检查上一 tick 记录的实体，有没有在这一 tick 死掉的
        Iterator<Map.Entry<UUID, LivingEntity>> knownIterator = this.knownEntities.entrySet().iterator();
        while (knownIterator.hasNext()) {
            LivingEntity entity = knownIterator.next().getValue();

            // 如果实体已经死亡，或者血量归零 (即使还没被立刻从世界移除)
            if (entity.isDeadOrDying() || entity.getHealth() <= 0.0f) {
                // 在它死亡的位置生成飞升鱿鱼
                this.spawnSquid(entity.getX(), entity.getY(), entity.getZ());
                knownIterator.remove();
                continue;
            }

            // 如果实体因为距离过远被卸载(isRemoved)但没死，单纯移出记录即可
            if (entity.isRemoved()) {
                knownIterator.remove();
            }
        }

        // 2. 将当前视野内还活着的实体加入记录，留给下一 tick 检查
        // 遍历所有客户端渲染的实体（包括玩家和怪物）
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && living != mc.player) {
                if (!living.isDeadOrDying() && living.getHealth() > 0.0f) {
                    this.knownEntities.put(living.getUUID(), living);
                }
            }
        }

        // 3. 推进所有正在上升的假墨鱼
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
                // 上升结束：原地炸开一圈火焰粒子
                for (int i = 0; i < 8; i++) {
                    mc.level.addParticle(
                            ParticleTypes.FLAME,
                            squid.getX(), squid.getY() + 0.5, squid.getZ(),
                            (Math.random() - 0.5) * 0.2,
                            Math.random() * 0.2,
                            (Math.random() - 0.5) * 0.2
                    );
                }

                // 彻底从客户端清理假实体
                squid.discard();
                mc.level.removeEntity(squid.getId(), Entity.RemovalReason.DISCARDED);

                squidIterator.remove();
                continue;
            }

            // 强制修改 Y 轴坐标，无视物理引擎
            squid.setPos(squid.getX(), squid.getY() + RISE_SPEED, squid.getZ());
            squid.setDeltaMovement(0.0, RISE_SPEED, 0.0);
        }
    }

    private void spawnSquid(double x, double y, double z) {
        SoundUtil.playSound("kill.wav", 1.0f);

        Squid squid = new Squid(EntityType.SQUID, mc.level);

        // 强制分配假实体 ID，防止冲突
        int fakeEntityId = -114514 - (int)(Math.random() * 100000);
        squid.setId(fakeEntityId);

        squid.setPos(x, y, z);
        squid.setNoGravity(true);
        squid.setInvulnerable(true);
        squid.setYRot(mc.player.getYRot());

        // 【关键修复】：1.20 官方映射下，往客户端塞假实体用 putNonPlayerEntity
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
        this.knownEntities.clear();
    }

    @Override
    public void onDisable() {
        clearEffects();
    }
}
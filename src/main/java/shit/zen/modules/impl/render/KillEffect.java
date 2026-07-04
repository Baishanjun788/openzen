package shit.zen.modules.impl.render;

import java.util.*;

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

    private static final double RISE_SPEED = 0.1;       // 每 tick 上升 0.1 格
    private static final double PERCENT_STEP = 0.02;     // 匀速进度，50 tick 正好 5 格

    private static final class SquidEffect {
        final Squid squid;
        double percent = 0.0;

        SquidEffect(Squid squid) {
            this.squid = squid;
        }
    }

    // 使用 UUID 作为唯一标识，彻底避免同名问题
    private final Map<UUID, LivingEntity> damagedByMe = new HashMap<>();

    // 正在播放升天效果的鱿鱼列表
    private final List<SquidEffect> activeSquids = new ArrayList<>();

    public KillEffect() {
        super("KillEffect", Category.RENDER);
    }

    @EventTarget
    public void onEntityHurt(EntityHurtEvent event) {
        if (mc.player == null) return;
        LivingEntity target = event.entity();
        if (target == mc.player) return;
        if (event.damageSource().getEntity() != mc.player) return;

        // 直接获取受伤前的血量，结合伤害量预判是否致死
        // 若事件提供了 getAmount() 就用它，否则我们这里用简单方法：
        // 记录受伤前的血量（此时 target.getHealth() 还是更新前的值），留到 tick 中对比
        // 如果一击致命则直接触发，不再等待 tick 检查
        float healthBefore = target.getHealth();

        // 尝试获取本次伤害量（如果框架支持），若没有此方法则跳过致死预判
        float damage = 0;
        try {
            damage = event.amount(); // 假设 EntityHurtEvent 有此方法
        } catch (NoSuchMethodError e) {
            // 忽略，damage 保持 0，预判将不生效
        }

        if (damage > 0 && healthBefore - damage <= 0.0F) {
            // 一击必杀：直接触发，不走记录流程
            spawnSquid(target.getX(), target.getY() + 1.0, target.getZ());
        } else {
            // 非致死伤害：记录下来，等待后续死亡判定（尤其是玩家和残血怪）
            damagedByMe.put(target.getUUID(), target);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.level == null || mc.player == null) {
            clearEffects();
            return;
        }

        // 1. 检查之前记录的实体是否死亡（适用于动物和未预判到的玩家）
        Iterator<Map.Entry<UUID, LivingEntity>> trackedIterator = damagedByMe.entrySet().iterator();
        while (trackedIterator.hasNext()) {
            Map.Entry<UUID, LivingEntity> entry = trackedIterator.next();
            LivingEntity entity = entry.getValue();

            // 统一死亡判定：死亡中、血量归零、实体被移除（均视为死亡）
            boolean isDead = entity.isDeadOrDying() ||
                    entity.getHealth() <= 0.0f ||
                    entity.isRemoved();

            if (isDead) {
                spawnSquid(entity.getX(), entity.getY() + 1.0, entity.getZ());
                trackedIterator.remove();
            }
            // 如果实体只是被卸载但没死（极远距离卸载），为避免内存泄漏也移除记录
            else if (entity.isRemoved()) {
                trackedIterator.remove();
            }
        }

        // 2. 处理正在上升的鱿鱼
        Iterator<SquidEffect> squidIterator = activeSquids.iterator();
        while (squidIterator.hasNext()) {
            SquidEffect effect = squidIterator.next();
            Squid squid = effect.squid;

            if (squid.isRemoved()) {
                squidIterator.remove();
                continue;
            }

            effect.percent += PERCENT_STEP;

            if (effect.percent >= 1.0) {
                // 爆炸特效：烟花 + 火焰粒子
                double explosionY = squid.getY() + 0.5;
                mc.level.addParticle(ParticleTypes.EXPLOSION, squid.getX(), explosionY, squid.getZ(), 0, 0, 0);

                for (int i = 0; i < 40; i++) {
                    double sx = (Math.random() - 0.5) * 0.8;
                    double sy = (Math.random() - 0.5) * 0.8;
                    double sz = (Math.random() - 0.5) * 0.8;
                    mc.level.addParticle(ParticleTypes.FIREWORK, squid.getX(), explosionY, squid.getZ(), sx, sy, sz);
                    mc.level.addParticle(ParticleTypes.FLAME, squid.getX(), explosionY, squid.getZ(), sx, sy, sz);
                }

                squid.discard(); // 内部已调用 remove，无需重复操作
                squidIterator.remove();
            } else {
                // 匀速上升
                squid.setPos(squid.getX(), squid.getY() + RISE_SPEED, squid.getZ());
                squid.setDeltaMovement(0.0, RISE_SPEED, 0.0);
            }
        }
    }

    private void spawnSquid(double x, double y, double z) {
        SoundUtil.playSound("kill.wav", 1.0f);

        Squid squid = new Squid(EntityType.SQUID, mc.level);
        int fakeId = -114514 - (int)(Math.random() * 100000);
        squid.setId(fakeId);
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
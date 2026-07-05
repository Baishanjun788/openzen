package shit.zen.modules.impl.render;

import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.Entity;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.settings.impl.NumberSetting;

public class FreeCam extends Module {

    public static FreeCam INSTANCE;

    public final NumberSetting speed = new NumberSetting("Speed", 1.0, 0.1, 5.0, 0.1);

    // 记录开启时的本体位置
    private double startX, startY, startZ;
    private float startYaw, startPitch;

    // 留在原地的假人
    private RemotePlayer dummy;
    private final int DUMMY_ENTITY_ID = -6969; // 负数ID防止和服务器实体冲突

    public FreeCam() {
        super("FreeCam", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    protected void onEnable() {
        if (mc.player == null || mc.level == null) return;

        // 1. 记录当前本体坐标
        this.startX = mc.player.getX();
        this.startY = mc.player.getY();
        this.startZ = mc.player.getZ();
        this.startYaw = mc.player.getYRot();
        this.startPitch = mc.player.getXRot();

        // 2. 在原地生成一个假人（代替你站在那里）
        this.dummy = new RemotePlayer(mc.level, mc.player.getGameProfile());
        this.dummy.setPos(this.startX, this.startY, this.startZ);
        this.dummy.setYRot(this.startYaw);
        this.dummy.setXRot(this.startPitch);
        this.dummy.setYHeadRot(mc.player.getYHeadRot());
        // 同步你手上的物品和装备给假人
        this.dummy.getInventory().replaceWith(mc.player.getInventory());

        // 将假人加入客户端世界渲染
    //    mc.level.getEntity(DUMMY_ENTITY_ID, this.dummy);

        super.onEnable();
    }

    @Override
    protected void onDisable() {
        if (mc.player != null && mc.level != null) {
            // 1. 玩家位置瞬移回开启时的坐标
            mc.player.setPos(this.startX, this.startY, this.startZ);
            mc.player.setYRot(this.startYaw);
            mc.player.setXRot(this.startPitch);

            // 2. 停止穿墙和飞行
            mc.player.noPhysics = false;
            mc.player.getAbilities().flying = false;
            mc.player.setDeltaMovement(0, 0, 0);

            // 3. 删除假人
            if (this.dummy != null) {
                mc.level.removeEntity(DUMMY_ENTITY_ID, Entity.RemovalReason.DISCARDED);
                this.dummy = null;
            }
        }
        super.onDisable();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null) return;

        // 强制开启飞行和穿墙 (noclip)
        mc.player.noPhysics = true;
        mc.player.getAbilities().flying = true;

        // 可选：根据设置动态调整飞行速度
        mc.player.getAbilities().setFlyingSpeed(speed.getValue().floatValue() * 0.05f);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null) return;

        // 核心：拦截发往服务器的移动数据包
        // 这样服务器会认为你一直站在原地（假人的位置），而你实际上在客户端里到处飞
        if (event.getPacket() instanceof ServerboundMovePlayerPacket) {
            event.isCancelled();
        }

        // 拦截疾跑、潜行等动作状态包，防止引起服务器行为异常检测
        if (event.getPacket() instanceof ServerboundPlayerCommandPacket) {
            event.isCancelled();
        }
    }
}
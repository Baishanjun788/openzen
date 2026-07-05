package shit.zen.modules.impl.render;

import net.minecraft.world.phys.Vec3;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.MotionEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.settings.impl.NumberSetting;

public class Freecam extends Module {
    public static Freecam INSTANCE;

    // 自由视角飞行速度调节
    public final NumberSetting speed = new NumberSetting("Speed", 1.0, 0.1, 4.0, 0.1);

    // 记录开启瞬间的坐标和角度
    private Vec3 oldPos;
    private float oldYaw, oldPitch;

    public Freecam() {
        super("Freecam", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (mc.player == null) {
            this.setEnabled(false);
            return;
        }

        // 1. 备份开启瞬间的坐标与视线角度
        this.oldPos = mc.player.position();
        this.oldYaw = mc.player.getYRot();
        this.oldPitch = mc.player.getXRot();

        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (mc.player == null) return;

        // 2. 关闭时，直接将你的视角和坐标闪现拉回原点
        if (this.oldPos != null) {
            mc.player.setPos(this.oldPos.x, this.oldPos.y, this.oldPos.z);
            mc.player.setYRot(this.oldYaw);
            mc.player.setXRot(this.oldPitch);
        }

        // 3. 恢复正常重力与物理判定
        mc.player.noPhysics = false;
        mc.player.setDeltaMovement(0, 0, 0);

        super.onDisable();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null) return;

        // 让本体在本地世界中可以无物理碰撞穿墙、不产生坠落重力
        mc.player.noPhysics = true;
        mc.player.setOnGround(false);
        mc.player.setDeltaMovement(0, 0, 0); // 清除原版摩擦力阻碍

        float flySpeed = this.speed.getValue().floatValue();
        double yMovement = 0.0;

        // 处理键盘键盘：空格键(飞升)和Shift键(下沉)
        if (mc.options.keyJump.isDown()) {
            yMovement = flySpeed * 0.6;
        } else if (mc.options.keyShift.isDown()) {
            yMovement = -flySpeed * 0.6;
        }

        // 根据你当前面朝的 Yaw 角度计算前向和侧向分速度
        Vec3 forwardMovement = Vec3.directionFromRotation(0, mc.player.getYRot());
        Vec3 strafeMovement = Vec3.directionFromRotation(0, mc.player.getYRot() + 90f);

        double xMovement = 0.0;
        double zMovement = 0.0;

        if (mc.options.keyUp.isDown()) {
            xMovement += forwardMovement.x * flySpeed;
            zMovement += forwardMovement.z * flySpeed;
        }
        if (mc.options.keyDown.isDown()) {
            xMovement -= forwardMovement.x * flySpeed;
            zMovement -= forwardMovement.z * flySpeed;
        }
        if (mc.options.keyLeft.isDown()) {
            xMovement -= strafeMovement.x * flySpeed * 0.8;
            zMovement -= strafeMovement.z * flySpeed * 0.8;
        }
        if (mc.options.keyRight.isDown()) {
            xMovement += strafeMovement.x * flySpeed * 0.8;
            zMovement += strafeMovement.z * flySpeed * 0.8;
        }

        // 应用本地飞行轨迹速度
        mc.player.setDeltaMovement(xMovement, yMovement, zMovement);
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        // 【核心过反作弊】不管你的视角在本地飘到了哪里，
        // 只要发包事件触发，永远把准备发给反作弊的位置修改成 oldPos（原地）
        if (event.isPre() && this.oldPos != null) {
            event.setX(this.oldPos.x);
            event.setY(this.oldPos.y);
            event.setZ(this.oldPos.z);
            event.setYaw(this.oldYaw);
            event.setPitch(this.oldPitch);
            event.setOnGround(true);
        }
    }
}
package shit.zen.modules.impl.render;

import net.minecraft.world.phys.Vec3;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.StrafeEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.settings.impl.NumberSetting;

/**
 * 自由视角（Freecam）：开启后镜头可以脱离身体自由飞行，方便你在原地按按钮/拉杆的同时
 * 飞到别的角度去看红石线路的运行状态，人物本体不会跟着移动。
 *
 * 操作方式（跟创造模式飞行一样）：
 *   W/A/S/D  水平移动（按你视角朝向的水平方向飞，不管你往上/下看都不影响水平飞行）
 *   空格      往上飞
 *   Shift    往下飞
 *   鼠标      正常看方向，不受影响
 *
 * 实现原理：
 *   - StrafeEvent 是每 tick 拿到的"WASD 换算出来的移动量"，这里读出来用来推动一个独立的
 *     自由视角坐标（this.position），然后把这个事件的移动量清零再交还给游戏，这样真实玩家
 *     的移动输入就被"拦截"了，本体不会跟着走。
 *   - 真正把镜头挪到 this.position 这个位置，需要在 Camera.setup() 之后强制覆盖一次相机坐标，
 *     这部分逻辑在新增的 CameraPatch.java 里（这个模块本身不摸渲染，只负责算"镜头应该在哪"）。
 *
 * 注意：这个不是 noclip 意义上的"允许穿墙走路"，只是镜头位置脱离了身体，
 * 如果想要镜头也能穿过方块自由飞（而不是被"卡"在原地一样只是看向别处），
 * 这个实现天生就是这样的（镜头本来就不受碰撞箱限制），可以直接飞进方块里看内部结构。
 *
 * ============ 调试版本 ============
 * 加了几处 System.out.println，配合 CameraPatch 的调试输出一起看，用来定位问题：
 *   [FreeCam] onEnable called       -> 开关是否真的触发了 onEnable
 *   [FreeCam] onDisable called      -> 关闭时是否触发
 *   [FreeCam] onStrafe position=... -> 每 tick 坐标有没有在正确更新
 * 排查完之后记得删掉或换成项目自己的 logger（onStrafe 每 tick 都会打印，日志量会很大，
 * 建议只在怀疑坐标计算有问题时临时打开）。
 */
public class FreeCam extends Module {

    public static FreeCam INSTANCE;

    public final NumberSetting speed = new NumberSetting("Speed", 0.5, 0.05, 3.0, 0.05);

    private Vec3 position;

    public FreeCam() {
        super("FreeCam", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        System.out.println("[FreeCam] onEnable called, player=" + (mc.player != null));
        if (mc.player != null) {
            this.position = mc.player.getEyePosition();
            System.out.println("[FreeCam] initial position=" + this.position);
        }
    }

    @Override
    public void onDisable() {
        System.out.println("[FreeCam] onDisable called");
        this.position = null;
    }

    /**
     * 供 CameraPatch 读取当前自由视角应该在的位置。
     */
    public Vec3 getPosition() {
        return this.position;
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        // 整个方法体包一层 try-catch：
        // 你的事件框架（OpenZen/asm.patchify）在调用这个方法出异常时，
        // 日志里只打印了一行 "invocation target ... InvocationTargetException"，
        // 没有打印 Caused by 的真实堆栈，等于把根因吞掉了。
        // 这里自己兜底捕获，把真正的异常类型、消息、堆栈行数完整打印出来，
        // 排查完问题后可以把这层 try-catch 去掉。
        try {
            this.doStrafe(event);
        } catch (Throwable t) {
            System.out.println("[FreeCam] onStrafe REAL EXCEPTION: " + t.getClass().getName() + ": " + t.getMessage());
            t.printStackTrace();
        }
    }

    private void doStrafe(StrafeEvent event) {
        if (mc.player == null) {
            return;
        }
        if (this.position == null) {
            this.position = mc.player.getEyePosition();
        }

        float forward = event.getForward();
        float strafe = event.getStrafe();
        // 这个事件里的字段名叫 isSprinting，但它实际对应的是跳跃键(空格)的按下状态
        boolean flyUp = event.isSprinting();
        boolean flyDown = mc.options.keyShift.isDown();

        double moveSpeed = (double) this.speed.getValue();
        double yawRad = Math.toRadians(mc.player.getYRot());

        // 只用水平朝向算移动方向，抬头/低头不影响飞行平面，飞起来更好控制
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double strafeX = Math.cos(yawRad);
        double strafeZ = Math.sin(yawRad);

        double moveX = (forwardX * forward + strafeX * strafe) * moveSpeed;
        double moveZ = (forwardZ * forward + strafeZ * strafe) * moveSpeed;
        double moveY = 0.0;
        if (flyUp) {
            moveY += moveSpeed;
        }
        if (flyDown) {
            moveY -= moveSpeed;
        }

        this.position = this.position.add(moveX, moveY, moveZ);

        // 调试：每 tick 坐标变化。日志量大，排查完记得注释掉或删掉。
        System.out.println("[FreeCam] onStrafe position=" + this.position + " forward=" + forward + " strafe=" + strafe + " flyUp=" + flyUp + " flyDown=" + flyDown);

        // 把这个tick的移动量清零，真实玩家本体就不会跟着走
        event.setForward(0.0f);
        event.setStrafe(0.0f);
        event.setSprinting(false);
    }
}
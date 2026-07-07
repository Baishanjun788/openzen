package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import shit.zen.ZenClient;
import shit.zen.modules.impl.render.FreeCam;
import shit.zen.utils.misc.ReflectionUtil;

/**
 * 在游戏原本算完这一帧的相机位置/朝向之后（Camera.setup 跑完），
 * 如果 FreeCam 开着，就把相机坐标强制换成 FreeCam 里维护的那个自由视角坐标。
 * 朝向（yaw/pitch）不动，还是跟着玩家实际的视角走，这样鼠标看方向完全正常，
 * 只有"人在哪"和"镜头在哪"这两件事被拆开了。
 *
 * 注意：Camera.setPosition(Vec3) 在原版里是 protected 的，没法从这里直接调用，
 * 所以改成用 ReflectionUtil 直接写 Camera 内部的 position / blockPosition 两个字段
 * （原版 setPosition 内部实际上也是同时改这两个字段，blockPosition 是给区块/光照相关
 * 查找用的派生值，这里一并同步，避免只改 position 导致细节上不一致）。
 *
 * ============ 调试版本 ============
 * 下面加了几处 System.out.println，用来定位"没反应"到底卡在哪一步：
 *   [CameraPatch] onSetup called          -> patch 本身有没有被注入 / 触发
 *   [CameraPatch] FreeCam not enabled     -> FreeCam 开关状态
 *   [CameraPatch] position is null        -> FreeCam.getPosition() 是否为 null
 *   [CameraPatch] reflection success      -> 反射写字段是否成功
 *   [CameraPatch] reflection FAILED       -> 反射写字段抛异常（会打印堆栈）
 * 排查完之后记得把这些 println 删掉或者换成你项目自己的 logger。
 */
@Patch(Camera.class)
public class CameraPatch {

    @Inject(
            method = "setup",
            desc = "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At(At.Type.TAIL)
    )
    public static void onSetup(Camera camera, BlockGetter blockGetter, Entity entity, boolean detached, boolean thirdPerson, float partialTick, CallbackInfo callbackInfo) {
        // 调试点 1：确认这个 patch 到底有没有被调用到
        System.out.println("[CameraPatch] onSetup called");

        if (!ZenClient.isReady()) {
            System.out.println("[CameraPatch] ZenClient not ready, skip");
            return;
        }

        if (FreeCam.INSTANCE == null) {
            System.out.println("[CameraPatch] FreeCam.INSTANCE is null, skip");
            return;
        }

        if (!FreeCam.INSTANCE.isEnabled()) {
            System.out.println("[CameraPatch] FreeCam not enabled, skip");
            return;
        }

        Vec3 freeCamPosition = FreeCam.INSTANCE.getPosition();
        if (freeCamPosition == null) {
            System.out.println("[CameraPatch] FreeCam position is null, skip");
            return;
        }

        System.out.println("[CameraPatch] applying freecam position: " + freeCamPosition);

        try {
            ReflectionUtil.setFieldValue(camera, freeCamPosition, "position");
            BlockPos blockPosition = BlockPos.containing(freeCamPosition.x, freeCamPosition.y, freeCamPosition.z);
            ReflectionUtil.setFieldValue(camera, blockPosition, "blockPosition");
            System.out.println("[CameraPatch] reflection success");
        } catch (Exception e) {
            System.out.println("[CameraPatch] reflection FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
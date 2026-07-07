package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
 * 调试信息直接发到游戏聊天栏（而不是控制台/日志文件），方便直接在游戏里看。
 * 因为 onSetup 每一帧都会执行（60~144次/秒），如果不节流，聊天栏会被刷爆，
 * 所以这里做了节流：正常状态每 1 秒最多输出一次；reflection 失败这种严重错误不节流，每次都发。
 * 排查完之后记得把这些 debugChat 调用删掉。
 */
@Patch(Camera.class)
public class CameraPatch {

    // 节流用：记录上一次输出调试信息的时间戳（毫秒）
    private static long lastDebugTime = 0L;
    private static final long DEBUG_INTERVAL_MS = 1000L;

    private static void debugChat(String message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal("[CameraPatch] " + message), false);
        }
    }

    /**
     * 节流版输出：1 秒内只发一次，避免刷屏。
     */
    private static void debugChatThrottled(String message) {
        long now = System.currentTimeMillis();
        if (now - lastDebugTime >= DEBUG_INTERVAL_MS) {
            lastDebugTime = now;
            debugChat(message);
        }
    }

    @Inject(
            method = "setup",
            desc = "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At(At.Type.TAIL)
    )
    public static void onSetup(Camera camera, BlockGetter blockGetter, Entity entity, boolean detached, boolean thirdPerson, float partialTick, CallbackInfo callbackInfo) {
        // 调试点 1：确认这个 patch 到底有没有被调用到（节流，每秒最多一条）
        debugChatThrottled("onSetup called");

        if (!ZenClient.isReady()) {
            debugChatThrottled("ZenClient not ready, skip");
            return;
        }

        if (FreeCam.INSTANCE == null) {
            debugChatThrottled("FreeCam.INSTANCE is null, skip");
            return;
        }

        if (!FreeCam.INSTANCE.isEnabled()) {
            // FreeCam 没开是正常情况，不用刷屏，直接跳过不发消息
            return;
        }

        Vec3 freeCamPosition = FreeCam.INSTANCE.getPosition();
        if (freeCamPosition == null) {
            debugChatThrottled("FreeCam position is null, skip");
            return;
        }

        debugChatThrottled("applying freecam position: " + freeCamPosition);

        try {
            ReflectionUtil.setFieldValue(camera, freeCamPosition, "position");
            BlockPos blockPosition = BlockPos.containing(freeCamPosition.x, freeCamPosition.y, freeCamPosition.z);
            ReflectionUtil.setFieldValue(camera, blockPosition, "blockPosition");
            debugChatThrottled("reflection success");
        } catch (Exception e) {
            // 反射失败是严重错误，不节流，每次都要看到
            debugChat("reflection FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
//操你妈改了两遍还是不行操你妈逼？
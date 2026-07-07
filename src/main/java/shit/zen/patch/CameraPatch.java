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

import java.lang.reflect.Field;

/**
 * 在游戏原本算完这一帧的相机位置/朝向之后（Camera.setup 跑完），
 * 如果 FreeCam 开着，就把相机坐标强制换成 FreeCam 里维护的那个自由视角坐标。
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
    // 【修复】移除了 CallbackInfo 参数！
    // OpenZen 不像 Mixin 那样强制要求带 CallbackInfo，
    // 如果目标方法是 m_91585_(BlockGetter, Entity, boolean, boolean, float)，
    // 这里多写一个参数会导致 OpenZen 判定参数数量不匹配，直接跳过注入。
    public static void onSetup(Camera camera, BlockGetter blockGetter, Entity entity, boolean detached, boolean thirdPerson, float partialTick) {
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
            // 【修复】不再依赖 ReflectionUtil 按名字找字段（SRG 环境下名字可能是 f_90557_），
            // 改成直接遍历 Camera 类的所有字段，按类型暴力替换。
            // Camera 类里只有一个 Vec3 类型的 position 和一个 BlockPos 类型的 blockPosition，
            // 这样做 100% 能命中，且不用管混淆映射。
            BlockPos blockPos = BlockPos.containing(freeCamPosition.x, freeCamPosition.y, freeCamPosition.z);

            for (Field field : Camera.class.getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType() == Vec3.class) {
                    field.set(camera, freeCamPosition);
                } else if (field.getType() == BlockPos.class) {
                    // 即使是 final 字段，在 JDK 17 下通过 setAccessible(true) 也能强行写入
                    field.set(camera, blockPos);
                }
            }
            debugChatThrottled("reflection success");
        } catch (Exception e) {
            // 反射失败是严重错误，不节流，每次都要看到
            debugChat("reflection FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
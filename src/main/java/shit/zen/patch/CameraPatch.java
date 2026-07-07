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

    private static long lastDebugTime = 0L;
    private static final long DEBUG_INTERVAL_MS = 1000L;

    private static void debugChat(String message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal("[CameraPatch] " + message), false);
        }
    }

    private static void debugChatThrottled(String message) {
        long now = System.currentTimeMillis();
        if (now - lastDebugTime >= DEBUG_INTERVAL_MS) {
            lastDebugTime = now;
            debugChat(message);
        }
    }

    // 【修复】method 名改为 SRG 名 m_91585_，因为运行时是混淆环境
    @Inject(
            method = "m_91585_", // 对应 Camera.setup
            desc = "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At(At.Type.TAIL)
    )
    public static void onSetup(Camera camera, BlockGetter blockGetter, Entity entity, boolean detached, boolean thirdPerson, float partialTick) {
        debugChatThrottled("onSetup called");

        if (!ZenClient.isReady()) {
            return;
        }

        if (FreeCam.INSTANCE == null || !FreeCam.INSTANCE.isEnabled()) {
            return;
        }

        Vec3 freeCamPosition = FreeCam.INSTANCE.getPosition();
        if (freeCamPosition == null) {
            debugChatThrottled("FreeCam position is null, skip");
            return;
        }

        try {
            BlockPos blockPos = BlockPos.containing(freeCamPosition.x, freeCamPosition.y, freeCamPosition.z);

            for (Field field : Camera.class.getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType() == Vec3.class) {
                    field.set(camera, freeCamPosition);
                } else if (field.getType() == BlockPos.class) {
                    field.set(camera, blockPos);
                }
            }
            debugChatThrottled("reflection success");
        } catch (Exception e) {
            debugChat("reflection FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
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

    @Inject(
            method = "m_91585_", // 1.20.1 Camera.setup 的 SRG 名
            desc = "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At(At.Type.TAIL)
    )
    // 第一个参数是目标类的实例(Camera)，中间是原方法的5个参数，最后一个是同包下的 CallbackInfo
    public static void onSetup(
            Camera camera,
            BlockGetter blockGetter,
            Entity entity,
            boolean detached,
            boolean thirdPerson,
            float partialTick,
            CallbackInfo callbackInfo) {

        debugChatThrottled("onSetup injected & called");

        if (!ZenClient.isReady()) {
            return;
        }

        if (FreeCam.INSTANCE == null || !FreeCam.INSTANCE.isEnabled()) {
            return;
        }

        Vec3 freeCamPosition = FreeCam.INSTANCE.getPosition();
        if (freeCamPosition == null) {
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


//caonima
package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import shit.zen.ClientBase;
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
        ClientBase.logger.info("[CameraPatch] {}", message);
    }

    private static void debugChatThrottled(String message) {
        long now = System.currentTimeMillis();
        if (now - lastDebugTime >= DEBUG_INTERVAL_MS) {
            lastDebugTime = now;
            debugChat(message);
        }
    }

    @Inject(
            method = "setup", // 换回可读名。如果这个也不触发，说明问题不在方法名，
            // 而是这个 patch 类本身没被框架扫描/注册。
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

        Vec3 freeCamPosition = FreeCam.INSTANCE.getInterpolatedPosition(partialTick);
        if (freeCamPosition == null) {
            return;
        }

        try {
            BlockPos blockPos = BlockPos.containing(freeCamPosition.x, freeCamPosition.y, freeCamPosition.z);
            setCameraField(camera, "position", freeCamPosition);
            setCameraField(camera, "blockPosition", blockPos);
            debugChatThrottled("camera position updated");
        } catch (Exception e) {
            debugChat("camera update FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void setCameraField(Camera camera, String fieldName, Object value) throws Exception {
        Field field = null;
        try {
            field = Camera.class.getDeclaredField(fieldName);
        } catch (NoSuchFieldException ignored) {
            // try fallback: find first field with compatible type
            for (Field f : Camera.class.getDeclaredFields()) {
                if (value != null && f.getType().isAssignableFrom(value.getClass())) {
                    field = f;
                    break;
                }
            }
            if (field == null) {
                throw new NoSuchFieldException(fieldName);
            }
        }
        field.setAccessible(true);
        field.set(camera, value);
    }
}
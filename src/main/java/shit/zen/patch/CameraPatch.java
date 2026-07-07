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

        Vec3 freeCamPosition = FreeCam.INSTANCE.getPosition();
        if (freeCamPosition == null) {
            return;
        }

        try {
            BlockPos blockPos = BlockPos.containing(freeCamPosition.x, freeCamPosition.y, freeCamPosition.z);

            // 按类型匹配字段。原版 Camera 类正常只有一个 Vec3 字段(position)和一个
            // BlockPos 字段(blockPosition)，所以这样按类型找通常没问题；但如果有其他
            // mod 通过 Mixin 给 Camera 加了别的 Vec3/BlockPos 字段，这里会连带把它们也覆盖掉。
            // 用下面的计数做个保险提示，count 不等于 1 就说明按类型匹配已经不安全了，
            // 需要改成按字段名精确匹配（比如 "position" / "blockPosition"）。
            int vec3Count = 0;
            int blockPosCount = 0;
            for (Field field : Camera.class.getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType() == Vec3.class) {
                    field.set(camera, freeCamPosition);
                    vec3Count++;
                } else if (field.getType() == BlockPos.class) {
                    field.set(camera, blockPos);
                    blockPosCount++;
                }
            }

            if (vec3Count != 1 || blockPosCount != 1) {
                debugChat("WARNING: matched " + vec3Count + " Vec3 field(s), " + blockPosCount + " BlockPos field(s) - expected exactly 1 each");
            }

            debugChatThrottled("reflection success");
        } catch (Exception e) {
            debugChat("reflection FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
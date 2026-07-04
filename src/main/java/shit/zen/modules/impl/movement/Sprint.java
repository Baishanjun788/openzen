package shit.zen.modules.impl.movement;

import shit.zen.event.EventTarget;
import shit.zen.event.impl.RotationEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.player.InventoryManager;
import net.minecraft.client.KeyMapping;

public class Sprint extends Module {

    public Sprint() {
        super("Sprint", Category.MOVEMENT);
        this.setEnabled(true);
    }

    @EventTarget
    public void onRotation(RotationEvent event) {
        if (mc.player == null) return;

        // 正在使用物品栏/其他动作时不处理
        if (GuiMove.INSTANCE.isEnabled() && InventoryManager.isPerformingAction) {
            return;
        }

        // 1. 判断是否按下了任意移动键
        boolean isMoving = mc.options.keyUp.isDown()
                || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown();

        // 2. Telly 桥核心修正：只要大前提满足（饥饿度够、没有蹲、没有用物品），并且在移动就允许保持疾跑意图
        // 剔除原本严格的 onGround 限制，允许在空中跳跃阶段、倒退转体阶段延续疾跑状态
        boolean canSprint = mc.player.getFoodData().getFoodLevel() > 6
                && !mc.player.isCrouching()
                && !mc.player.isUsingItem()
                && isMoving;

        if (canSprint) {
            // 如果满足疾跑条件，强制客户端激活疾跑状态
            mc.player.setSprinting(true);
            // 同时激活原版快捷键状态，确保发包和运动属性同步
            KeyMapping.set(mc.options.keySprint.getKey(), true);
        }
    }
}
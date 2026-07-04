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
        if (GuiMove.INSTANCE.isEnabled() && InventoryManager.isPerformingAction) {
            KeyMapping.set(mc.options.keySprint.getKey(), false);
            return;
        }
        KeyMapping.set(mc.options.keySprint.getKey(), true);
    }

    @Override
    public void onDisable() {
        // 关闭模块时，松开疾跑键
        if (mc.player != null) {
            KeyMapping.set(mc.options.keySprint.getKey(), false);
        }
        super.onDisable();
    }
}
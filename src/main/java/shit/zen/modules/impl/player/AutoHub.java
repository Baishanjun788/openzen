package shit.zen.modules.impl.player;

import shit.zen.event.EventTarget;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;

public class AutoHub extends Module {

    public static AutoHub INSTANCE;

    public AutoHub() {
        super("AutoHub", Category.PLAYER);
        INSTANCE = this;
    }

    @EventTarget
    public void onTick(TickEvent tickEvent) {
        if (mc.player == null) return;

        if (mc.player.getY() <= 30.0) {

            // 🌟 终极方案：直接调用连接对象的发送逻辑
            // 如果报错，请在代码里尝试下面其中一种，总有一个是你的客户端混淆后的正确写法：

// 使用客户端自带的聊天发送接口（如果它有的话）
            mc.player.connection.sendChat("/hub");

            // 选项 2 (如果选项1报错)：有些客户端会把指令方法命名为 execute
            // mc.player.connection.sendCommand("hub");

            this.toggle();
            mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal("§c[AutoHub] §f已自动执行 /hub"));
        }
    }
}
package shit.zen.modules.impl.render;

import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.settings.impl.BooleanSetting;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SwordNotifier extends Module {

    public final BooleanSetting glow = new BooleanSetting("Glow Target", true);
    public final BooleanSetting chatAlert = new BooleanSetting("Chat Alert", true);

    // 核心改变：改为存储玩家的 UUID。即使玩家切走武器或短暂消失，只要没触发新对局，就永远在黑名单里
    private final Set<UUID> markedPlayers = new HashSet<>();
    // 用于聊天栏去重，防止同一次拔剑重复刷屏
    private final Set<UUID> alertedPlayers = new HashSet<>();

    public SwordNotifier() {
        super("SwordNotifier", Category.MISC);
    }

    @Override
    public void onEnable() {
        this.clearMarkers();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.clearMarkers();
        super.onDisable();
    }

    /**
     * 提取出的公共清理方法：关闭模块或匹配新游戏时调用
     */
    private void clearMarkers() {
        if (mc.level != null) {
            for (Player player : mc.level.players()) {
                player.setGlowingTag(false);
            }
        }
        this.markedPlayers.clear();
        this.alertedPlayers.clear();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getPacket() == null) return;

        String chatMessage = "";

        // 兼容现代原版不同的聊天数据包类型
        if (event.getPacket() instanceof ClientboundSystemChatPacket packet) {
            chatMessage = packet.content().getString();
        } else if (event.getPacket() instanceof ClientboundDisguisedChatPacket packet) {
            chatMessage = packet.message().getString();
        }

        if (!chatMessage.isEmpty()) {
            // 检测指定的对局开始/匹配关键字
            if (chatMessage.contains("正在为您匹配可用的游戏服务器...") || chatMessage.contains("游戏开始! 存活到最后!")) {
                this.clearMarkers(); // 满足条件，彻底清除所有标记
                if (mc.player != null) {
                    mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(
                            "§a[SwordNotifier] §f检测到新对局，已重置标记列表。"
                    ));
                }
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) return;

        // 遍历视距内加载出来的所有玩家
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;

            UUID uuid = player.getUUID();

            // 检测该玩家当前是否拿着钻石剑
            boolean holdingSword = player.getMainHandItem().is(Items.DIAMOND_SWORD)
                                || player.getOffhandItem().is(Items.DIAMOND_SWORD);

            // 逻辑分支 1：只要他拿过剑，就永久加入 markedPlayers 集合
            if (holdingSword) {
                this.markedPlayers.add(uuid);

                // 聊天框只提示一次
                if (this.chatAlert.getValue() && !this.alertedPlayers.contains(uuid)) {
                    mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(
                            "§c[Warning] §f玩家 §e" + player.getGameProfile().getName() + " §f曾手持钻石剑，已被永久锁定！"
                    ));
                    this.alertedPlayers.add(uuid);
                }
            }

            // 逻辑分支 2：只要他在 markedPlayers 集合中（即便他现在切成了方块或空手），就一直强制让他发光
            if (this.markedPlayers.contains(uuid)) {
                if (this.glow.getValue()) {
                    player.setGlowingTag(true);
                }
            } else {
                // 如果不在标记里（比如新加入房间的无辜玩家），保持不发光
                player.setGlowingTag(false);
            }
        }
    }
}
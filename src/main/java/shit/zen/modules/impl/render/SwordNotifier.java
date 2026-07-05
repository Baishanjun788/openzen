package shit.zen.modules.impl.render;

import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.render.FontStore;
import shit.zen.settings.impl.BooleanSetting;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SwordNotifier extends Module {

    public static SwordNotifier INSTANCE;

    public final BooleanSetting chatAlert = new BooleanSetting("Chat Alert", true);
    public final BooleanSetting distanceText = new BooleanSetting("Distance Text", true);
    public final BooleanSetting redesp = new BooleanSetting("RED MARK", true);
    public final BooleanSetting sendToPublicChat = new BooleanSetting("Send To Public Chat", false);

    // 🌟 新增：听觉雷达警告（替代眼瞎红屏）
    public final BooleanSetting soundAlert = new BooleanSetting("Sound Alert", true);
    // 🌟 新增：原版光灵箭透视高亮（替代传统ESP方框）
    public final BooleanSetting nativeGlow = new BooleanSetting("Native Glow", true);

    private final Set<String> markedPlayerNames = new HashSet<>();
    private final Set<String> alertedPlayerNames = new HashSet<>();

    private int textIndex = 0;
    private String pendingMessage = "";
    private long retryTime = 0;
    private long lastSoundTime = 0; // 控制提示音的冷却时间

    public SwordNotifier() {
        super("SwordNotifier", Category.RENDER);
        INSTANCE = this;
    }

    public boolean isMarked(String playerName) {
        if (!this.isEnabled() || playerName == null) return false;
        return this.markedPlayerNames.contains(playerName.toLowerCase().trim());
    }

    @Override
    public void onEnable() {
        this.clearMarkers();
        this.pendingMessage = "";
        this.retryTime = 0;
        this.lastSoundTime = 0;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.clearMarkers();
        super.onDisable();
    }

    private void clearMarkers() {
        // 清理时，顺手把世界里所有被发光的玩家恢复原样
        if (mc.level != null) {
            for (Player player : mc.level.players()) {
                if (player != null && player.getGameProfile().getName() != null) {
                    if (this.markedPlayerNames.contains(player.getGameProfile().getName().toLowerCase().trim())) {
                        player.setGlowingTag(false);
                    }
                }
            }
        }
        this.markedPlayerNames.clear();
        this.alertedPlayerNames.clear();
    }

    private void sendNativeChatMessage(String text) {
        if (mc.player == null || text.isEmpty()) return;
        mc.player.connection.sendChat(text);
    }

    private void sendNextMessage(String name) {
        if (!this.sendToPublicChat.getValue()) return;

        String msg = "";
        switch (this.textIndex) {
            case 0 -> msg = "我看到了，" + name + "是杀手。";
            case 1 -> msg = name + "是杀手。";
            case 2 -> msg = "这个" + name + "是杀手!";
        }

        this.textIndex = (this.textIndex + 1) % 3;
        this.pendingMessage = msg;
        this.sendNativeChatMessage(msg);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) return;

        // 🌟 核心逻辑：给所有被标记的杀手挂上“光灵箭”的原版透视轮廓
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;

            String rawName = player.getGameProfile().getName();
            if (rawName != null && this.markedPlayerNames.contains(rawName.toLowerCase().trim())) {
                if (this.nativeGlow.getValue()) {
                    player.setGlowingTag(true);
                } else {
                    player.setGlowingTag(false); // 如果关了设置，随时取消发光
                }
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.level == null || event.getPacket() == null) return;

        // 1. 底层拦截拔剑动作
        if (event.getPacket() instanceof ClientboundSetEquipmentPacket equipmentPacket) {
            Entity entity = mc.level.getEntity(equipmentPacket.getEntity());
            if (entity instanceof Player targetPlayer && targetPlayer != mc.player) {

                for (Pair<EquipmentSlot, ItemStack> slotChange : equipmentPacket.getSlots()) {
                    EquipmentSlot slot = slotChange.getFirst();
                    ItemStack itemStack = slotChange.getSecond();

                    if ((slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND)
                            && itemStack.is(Items.DIAMOND_SWORD)) {

                        String rawName = targetPlayer.getGameProfile().getName();
                        if (rawName == null || rawName.isEmpty()) continue;

                        String lowerName = rawName.toLowerCase().trim();
                        this.markedPlayerNames.add(lowerName);

                        if (this.chatAlert.getValue() && !this.alertedPlayerNames.contains(lowerName)) {
                            mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(
                                    "§c[Warning] §f玩家 §e" + rawName + " §f曾手持钻石剑，已被全网永久锁定！"
                            ));
                            this.alertedPlayerNames.add(lowerName);

                            this.sendNextMessage(rawName);
                        }
                    }
                }
            }
        }

        // 2. 拦截服务器系统聊天返回
        String chatMessage = "";
        if (event.getPacket() instanceof ClientboundSystemChatPacket packet) {
            chatMessage = packet.content().getString();
        } else if (event.getPacket() instanceof ClientboundDisguisedChatPacket packet) {
            chatMessage = packet.message().getString();
        }

        if (!chatMessage.isEmpty()) {
            if (chatMessage.contains("请不要刷屏或者发送重复消息哦")) {
                Pattern pattern = Pattern.compile("\\((\\d+)\\s*秒\\)");
                Matcher matcher = pattern.matcher(chatMessage);
                if (matcher.find()) {
                    try {
                        int seconds = Integer.parseInt(matcher.group(1));
                        this.retryTime = System.currentTimeMillis() + (seconds + 1) * 1000L;
                    } catch (Exception e) {
                        this.retryTime = System.currentTimeMillis() + 2000L;
                    }
                }
            }

            if (chatMessage.contains("匹配") || chatMessage.contains("游戏开始") || chatMessage.contains("START")) {
                this.clearMarkers();
                this.pendingMessage = "";
                this.retryTime = 0;

                mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(
                        "§a[SwordNotifier] §f检测到新对局，已重置黑名单并强行终止挂起的重发任务。"
                ));
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;

        double nearestDistance = -1;
        String nearestName = "";

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;

            String rawName = player.getGameProfile().getName();
            if (rawName == null) continue;

            if (!this.markedPlayerNames.contains(rawName.toLowerCase().trim())) continue;

            double distance = mc.player.distanceTo(player);
            if (nearestDistance < 0 || distance < nearestDistance) {
                nearestDistance = distance;
                nearestName = rawName;
            }
        }

        // 自动冷却重发原版包
        if (this.sendToPublicChat.getValue() && !this.pendingMessage.isEmpty() && this.retryTime > 0) {
            if (System.currentTimeMillis() >= this.retryTime) {
                this.sendNativeChatMessage(this.pendingMessage);
                this.retryTime = 0;
                this.pendingMessage = "";
            }
        }

        if (nearestDistance < 0) return;

        // 🌟 新增：听觉雷达报警（距离 30 格以内触发，防刷屏 3 秒冷却）
        if (this.soundAlert.getValue() && nearestDistance <= 30.0) {
            if (System.currentTimeMillis() - this.lastSoundTime > 3000L) {
                // 播放极其清晰且不刺耳的提示音 (经验球吸收的声音)
                mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                this.lastSoundTime = System.currentTimeMillis();
            }
        }

        // 准星下方危险距离文本 (保留)
        if (this.distanceText.getValue()) {
            String text = String.format("⚠ 危险目标 [%s] 距你 %.1f 格 ⚠", nearestName, nearestDistance);

            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();

            float x = screenWidth / 2.0f;
            float y = screenHeight * 0.65f;

            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();

            if (event.poseStack() != null) {
                FontStore.PINGFANG_18.drawStringCenteredColor(
                        event.poseStack(),
                        text,
                        x,
                        y,
                        new java.awt.Color(255, 30, 30)
                );
            }
        }
    }
}
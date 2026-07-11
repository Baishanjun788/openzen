package shit.zen.command.impl;

import shit.zen.ZenClient;
import shit.zen.command.Command;
import shit.zen.modules.impl.player.FollowPlayer;
import shit.zen.utils.misc.ChatUtil;

public class FollowPlayerCommand
extends Command {
    public FollowPlayerCommand() {
        super("followplayer", new String[]{"fp"});
    }

    @Override
    public void onCommand(String[] args) {
        FollowPlayer module = FollowPlayer.INSTANCE;
        if (module == null) {
            try {
                module = ZenClient.getInstance().getModuleManager().getModule(FollowPlayer.class);
            } catch (Exception ignored) {
            }
        }
        if (module == null) {
            ChatUtil.print("FollowPlayer module not available.");
            return;
        }
        if (args.length == 0) {
            if (module.isEnabled()) {
                module.setEnabled(false);
                module.targetName.setValue("");
                ChatUtil.print("[FollowPlayer] Follow cancelled.");
            } else {
                ChatUtil.print("Usage: .followplayer <playerName>");
            }
            return;
        }
        String target = String.join(" ", args).trim();
        if (target.isEmpty()) {
            ChatUtil.print("Usage: .followplayer <playerName>");
            return;
        }
        module.targetName.setValue(target);
        if (!module.isEnabled()) {
            module.setEnabled(true);
            ChatUtil.print("[FollowPlayer] Now following " + target + ".");
        } else {
            ChatUtil.print("[FollowPlayer] Switched follow target to " + target + ".");
        }
    }

    @Override
    public String[] onTab(String[] args) {
        return new String[0];
    }
}

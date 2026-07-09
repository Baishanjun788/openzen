package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import shit.zen.utils.render.TextureUtil;

@Patch(DisconnectedScreen.class)
public class DisconnectedScreenPatch {
    private static ResourceLocation customBackgroundTexture;

    @Inject(
            method = "render",
            desc = "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(At.Type.HEAD)
    )
    public static void onRender(DisconnectedScreen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo callbackInfo) {
        Minecraft minecraft = Minecraft.getInstance();
        DynamicTexture bgTexture = TextureUtil.loadTexture("gb.png");
        if (bgTexture == null) {
            return;
        }

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        if (DisconnectedScreenPatch.customBackgroundTexture == null) {
            TextureManager textureManager = minecraft.getTextureManager();
            DisconnectedScreenPatch.customBackgroundTexture = textureManager.register("zen_disconnect_bg", bgTexture);
        }

        RenderSystem.setShaderTexture(0, DisconnectedScreenPatch.customBackgroundTexture);
        guiGraphics.blit(
                DisconnectedScreenPatch.customBackgroundTexture,
                0,
                0,
                screenWidth,
                screenHeight,
                0.0f,
                0.0f,
                screenWidth,
                screenHeight,
                screenWidth,
                screenHeight
        );
    }
}

package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.render.hud.HudPanel;
import com.shokiteufel.shokimod.render.hud.NearbyHud;
import com.shokiteufel.shokimod.render.hud.SafariHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Der Nearby-Kasten ueber jedem offenen Fenster, dort anklickbar.
 *
 * Waehrend des Spielens liegt er im HUD, kann dort aber keine Klicks bekommen - der
 * Zeiger ist gefangen. Sobald ein Fenster offen ist, ist er frei; dann zeichnet dieser
 * Mixin denselben Kasten obenauf und nimmt die Klicks entgegen.
 */
@Mixin(Screen.class)
public class NearbyOverlayMixin {

    private static boolean shown() {
        Screen screen = (Screen) (Object) Minecraft.getInstance().screen;
        // Im Verschiebe-Editor zeichnet der Kasten schon selbst
        return ModConfig.INSTANCE.mobVisuals.showNearbyHud && !(screen instanceof HudEditorScreen);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void shokimod$drawNearby(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                     float partialTick, CallbackInfo ci) {
        if (!shown()) return;
        SafariHud.draw(graphics, Minecraft.getInstance().font,
                SafariHud.Panel.NEARBY, NearbyHud.build());
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void shokimod$clickNearby(MouseButtonEvent event, boolean doubleClick,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (!shown()) return;

        SafariHud.Panel panel = SafariHud.Panel.NEARBY;
        HudPanel content = NearbyHud.build();
        int row = content.rowAt(Minecraft.getInstance().font,
                SafariHud.originX(panel), SafariHud.originY(panel), panel.scale(),
                event.x(), event.y());

        if (row >= 0 && NearbyHud.click(row)) cir.setReturnValue(true);
    }
}

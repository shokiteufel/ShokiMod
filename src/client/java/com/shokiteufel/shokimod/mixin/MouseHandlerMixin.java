package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.handler.NoCursorReset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fuer "No Cursor Reset" (Reiter Odin): merkt sich, wo der Zeiger stand, als sich das letzte Fenster schloss, und kann ihn dorthin zurueckstellen.
 *
 * Odin haengt sich dafuer mitten in {@code grabMouse} (an das erste Schreiben von {@code xpos}) und in {@code releaseMouse} (an den
 * Aufruf von {@code getWindow}) - Stellen, die sich mit jeder Umformung der Methoden verschieben. Hier stehen die Einspruenge an
 * den Raendern der Methoden: am Anfang von {@code grabMouse} (die Maus wird gleich gegriffen, wenn das Fenster aktiv ist und sie noch
 * nicht gegriffen war - dann steht der Zeiger noch dort, wo der Spieler ihn zuletzt hatte) und am Anfang und Ende von
 * {@code releaseMouse} (war die Maus vorher gegriffen, oeffnet sich gerade ein Fenster). Alle mit {@code require = 0}, und die Datei
 * steht in der nicht zwingenden Mixin-Konfiguration: Passt eine Methode nicht, fehlt nur diese Funktion.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin implements NoCursorReset.Restorer {

    @Shadow
    private double xpos;
    @Shadow
    private double ypos;
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    public abstract boolean isMouseGrabbed();

    @Unique
    private boolean shokimod$wasGrabbed;

    @Inject(method = "grabMouse", at = @At("HEAD"), require = 0)
    private void shokimod$rememberCursor(CallbackInfo ci) {
        if (!this.isMouseGrabbed() && this.minecraft.isWindowActive()) NoCursorReset.onGrab(this.xpos, this.ypos);
    }

    @Inject(method = "releaseMouse", at = @At("HEAD"), require = 0)
    private void shokimod$noteGrabbed(CallbackInfo ci) {
        this.shokimod$wasGrabbed = this.isMouseGrabbed();
    }

    @Inject(method = "releaseMouse", at = @At("RETURN"), require = 0)
    private void shokimod$noteRelease(CallbackInfo ci) {
        if (!this.shokimod$wasGrabbed) return;
        this.shokimod$wasGrabbed = false;
        NoCursorReset.onRelease();
    }

    @Override
    public void shokimod$restoreCursor(double x, double y) {
        this.xpos = x;
        this.ypos = y;
        GLFW.glfwSetCursorPos(this.minecraft.getWindow().handle(), x, y);
    }
}

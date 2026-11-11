package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.sprung.SprungMenue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Das Leap Menu ersetzt das Fenster des Spirit Leaps: Es zeichnet statt des Fensters, nimmt den Klick und die Taste. Bei NoammAddons sind das die
 * Ereignisse {@code ScreenEvent.PreRender}, {@code ContainerEvent.MouseClick} und {@code ContainerEvent.Keyboard}.
 *
 * <p>Alle drei Methoden stehen in {@code AbstractContainerScreen} selbst und haben auf 26.1, 26.2 und 26.3 dieselbe Signatur (in den Jars nachgesehen).
 * Die Signatur steht vollstaendig im Ziel; fehlt sie in einer anderen Spielversion, wird der Einsprung uebersprungen ({@code require = 0}), und
 * diese Datei steht in der Mixin-Konfiguration, die nicht zwingend ist: dann fehlt nur das Leap Menu.
 *
 * <p>Ohne Leap Menu (oder in einem anderen Fenster) fragt jeder Einsprung nur {@link SprungMenue#uebernimmt} und tut nichts.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class SprungMenueMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void shokimod$zeichnen(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (SprungMenue.uebernimmt(screen)) {
            // Ein Fehler im Menue soll nie das Spiel beenden: dann gilt von jetzt an wieder das Fenster des Servers
            try {
                SprungMenue.zeichnen(graphics, screen, mouseX, mouseY);
                ci.cancel();
            } catch (RuntimeException e) {
                SprungMenue.gestoert("drawing", e);
            }
        }
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void shokimod$klick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (SprungMenue.klick((AbstractContainerScreen<?>) (Object) this, event.button(), event.x(), event.y())) cir.setReturnValue(true);
        } catch (RuntimeException e) {
            SprungMenue.gestoert("click", e);
        }
    }

    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void shokimod$taste(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (SprungMenue.taste((AbstractContainerScreen<?>) (Object) this, event.key())) cir.setReturnValue(true);
        } catch (RuntimeException e) {
            SprungMenue.gestoert("key", e);
        }
    }
}

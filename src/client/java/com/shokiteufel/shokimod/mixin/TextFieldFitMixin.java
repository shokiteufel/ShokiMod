package com.shokiteufel.shokimod.mixin;

import io.github.notenoughupdates.moulconfig.gui.component.TextFieldComponent;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Eingabefelder der Einstellungen, die ihren Text ganz zeigen.
 *
 * MoulConfig gibt dem Zahlenfeld neben einem Schieberegler fest 20 Pixel Breite (und dem
 * Textfeld 80). Abzueglich des Rands bleiben beim Zahlenfeld zwei Zeichen: Aus "0.4" wurde
 * "0." zu sehen. Breiter wird das Feld dort nur, solange man tippt.
 *
 * Hier waechst jedes Feld immer so weit, dass sein Text hineinpasst - nie schmaler als vorher,
 * und nur bis zu einer Grenze, damit es nicht in die Beschreibung daneben ragt. Was dann noch
 * nicht passt, scrollt wie bisher.
 */
@Mixin(TextFieldComponent.class)
public abstract class TextFieldFitMixin {

    /** Breiter als das (in GUI-Pixeln) wird ein Feld nicht, solange es nicht schon breiter ist */
    private static final int MAX_FIT = 88;
    /** Rand links und rechts, wie ihn das Feld selbst beim Tippen rechnet */
    private static final int PADDING = 10;

    @Inject(method = "getWidth()I", at = @At("RETURN"), cancellable = true, require = 0)
    private void shokimod$fitText(CallbackInfoReturnable<Integer> cir) {
        String text = ((TextFieldComponent) (Object) this).getText().get();
        if (text == null || text.isEmpty()) return;
        int current = cir.getReturnValueI();
        int needed = Minecraft.getInstance().font.width(text) + PADDING;
        if (needed > current) cir.setReturnValue(Math.min(needed, Math.max(current, MAX_FIT)));
    }
}

package com.shokiteufel.shokimod.paket.mixin;

import com.shokiteufel.shokimod.handler.SignCalculator;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * UI & Visuals > Input Calculator: der Einsprung in das Schildfenster. Zwei Stellen, beide in {@code AbstractSignEditScreen} (per javap in den Jars von 26.3 nachgesehen):
 * Der Anfang von {@code extractRenderState(GuiGraphicsExtractor, int, int, float)} zeichnet die gruene Zeile unter den Titel, der Anfang der privaten {@code onDone()}
 * ersetzt eine Rechnung in der ersten Zeile durch ihr Ergebnis, bevor das Fenster die Zeilen an den Server schickt ({@code removed()}). Das private Feld
 * {@code messages} ist das Feld der vier Zeilen.
 *
 * <p>Nicht erforderlich ({@code shokimod-pakete.mixins.json}, {@code require = 0}): Passt eine Stelle nicht, fehlt nur das Rechnen. Die Regeln stehen in
 * {@link SignCalculator}.
 */
@Mixin(AbstractSignEditScreen.class)
public abstract class SignCalculatorMixin {

    @Shadow
    @Final
    private String[] messages;

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"), require = 0)
    private void shokimod$drawResult(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        SignCalculator.draw(graphics, messages);
    }

    @Inject(method = "onDone()V", at = @At("HEAD"), require = 0)
    private void shokimod$replaceInput(CallbackInfo ci) {
        SignCalculator.onDone(messages);
    }
}

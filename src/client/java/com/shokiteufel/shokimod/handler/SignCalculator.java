package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.util.InputCalculator;
import com.shokiteufel.shokimod.util.SignInputRules;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * UI & Visuals > Input Calculator: unter "Edit Sign Message" steht in Gruen "Eingabe = Ergebnis" (mit Tausenderkommas), solange man auf einem Zahlenschild von SkyBlock
 * tippt (Gebot, Preis, Menge) - und beim Fertigmachen ersetzt das Ergebnis eine Rechnung.
 *
 * Herkunft der Idee: Skyblocker (LGPL-3.0, {@code SignCalculator}, {@code AbstractSignEditScreenMixin}). Wie gerechnet wird, steht in {@link InputCalculator}, welches Schild
 * ein Zahlenschild ist, in {@link SignInputRules}. Die Umsetzung ist eigene Arbeit: Java; der Einsprung ist ein Mixin in der nicht erforderlichen Konfiguration
 * ({@link com.shokiteufel.shokimod.paket.mixin.SignCalculatorMixin}): Ein Fehler hier kann den Start nicht verhindern, es fehlt dann nur die Anzeige.
 *
 * <p>Abweichungen: Keine Funktionen (sqrt, log ...) und keine Option "Requires Equals" und "Close Signs With Enter"; nur die Schilder, keine Gebots-Eingabe der Auktion
 * und keine Bazaar-Schnellmengen (die kennt die Mod nicht).
 */
public final class SignCalculator {

    /** Der Text steht auf Hoehe 55, mittig - wie bei Skyblocker, unter dem Titel "Edit Sign Message" */
    private static final int TEXT_Y = 55;

    /** Die Eingabe, fuer die der Text unten gebaut wurde, und der Text (null: nichts zu zeigen). Neu gebaut wird nur bei einer anderen Eingabe, nie je Bild */
    private static String lastInput = null;
    private static Component shown = null;
    private static boolean failedOnce = false;

    private SignCalculator() {
    }

    private static boolean active() {
        return FeatureGate.inputCalculator() && GameState.Server.isSkyblock();
    }

    /** Vom Mixin aus, in jedem Bild des Schildfensters: zeichnet die Zeile unter dem Titel */
    public static void draw(GuiGraphicsExtractor graphics, String[] lines) {
        try {
            if (!active() || !SignInputRules.isNumberSign(lines)) return;
            String input = lines[0] == null ? "" : lines[0];
            if (!input.equals(lastInput)) {
                lastInput = input;
                shown = build(input);
            }
            if (shown != null) graphics.centeredText(Minecraft.getInstance().font, shown, graphics.guiWidth() / 2, TEXT_Y, 0xFFFFFFFF);
        } catch (RuntimeException e) {
            warnOnce("drawing the result failed", e);
        }
    }

    /** Vom Mixin aus, beim Fertigmachen des Schildes: Eine Rechnung wird durch ihr Ergebnis ersetzt (die Zeile ist ein Feld des Fensters, das hier geaendert wird) */
    public static void onDone(String[] lines) {
        try {
            if (!active() || !SignInputRules.isNumberSign(lines) || lines[0] == null) return;
            lines[0] = InputCalculator.replacement(lines[0], SignInputRules.isPriceSign(lines));
        } catch (RuntimeException e) {
            warnOnce("replacing the input failed", e);
        }
    }

    /** Der Text zur Eingabe: gruen "Eingabe = Ergebnis", rot der Fehler, null bei leerer Eingabe */
    static Component build(String input) {
        InputCalculator.Result result = InputCalculator.evaluate(input);
        if (result.isEmpty()) return null;
        if (!result.ok()) return Component.literal(result.error()).withStyle(ChatFormatting.RED);
        return Component.literal(input.trim() + " = " + InputCalculator.format(result.value())).withStyle(ChatFormatting.GREEN);
    }

    private static void warnOnce(String what, RuntimeException e) {
        if (failedOnce) return;
        failedOnce = true;
        ShokiMod.LOGGER.warn("[InputCalculator] {}: {}", what, e.toString());
    }
}

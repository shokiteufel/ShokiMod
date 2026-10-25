package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.IqCategory;
import com.shokiteufel.shokimod.handler.FireVeil;

import net.minecraft.client.Minecraft;

import java.util.Locale;

/**
 * Der Zeitkasten zum Fire Veil: wie lange der Zauber noch wirkt, dann zwei Sekunden "READY".
 *
 * Herkunft: IQ (Apache-2.0, PeHenrii und DarkJota), {@code FireVeilOverlayWidget}. Uebernommen sind der Wortlaut ("Fire Veil: 4.83s",
 * "Fire Veil: READY"), die Farbstufen der Zahl (gruen, gelb, orange, rot bei mehr als 75, 50, 25 Prozent der Zeit) und dass der Kasten
 * nur waehrend des Zaubers und der Bereit-Anzeige da ist. Anders: Er ist hier ein Kasten des HUD-Editors ({@code /shoki hud}) wie
 * die anderen, mit Lage, Groesse und Deckkraft; im Editor ist er immer da, sonst liesse er sich nie setzen.
 */
public final class FireVeilHud {

    private FireVeilHud() {
    }

    private static IqCategory cfg() {
        return ModConfig.INSTANCE.iq;
    }

    /** Ist gerade der HUD-Editor offen? Die eine Stelle, die je Minecraft-Version anders heisst (26.1: screen, 26.2/26.3: gui.screen()) */
    private static boolean editorOpen() {
        return Minecraft.getInstance().gui.screen() instanceof com.shokiteufel.shokimod.gui.HudEditorScreen;
    }

    /** Soll der Kasten jetzt gezeichnet werden? */
    public static boolean shownNow() {
        return shownAt(System.currentTimeMillis(), editorOpen());
    }

    /** Dieselbe Frage zu einer vorgegebenen Zeit */
    public static boolean shownAt(long now, boolean editorOpen) {
        IqCategory c = cfg();
        if (!c.fireVeil || !c.fireVeilCountdown) return false;
        return editorOpen || FireVeil.shouldDisplayCountdown(now);
    }

    /** Die Farbe der Zahl nach dem Rest der Zeit */
    static int colourFor(long remainingMs) {
        double ratio = Math.min(1.0, Math.max(0.0, remainingMs / (double) FireVeil.ABILITY_MS));
        if (ratio > 0.75) return HudColours.GREEN;
        if (ratio > 0.50) return HudColours.YELLOW;
        if (ratio > 0.25) return HudColours.GOLD;
        return HudColours.RED;
    }

    /** "4.83s" - wie bei IQ auf zwei Stellen; die Zahl wechselt mit jedem Tick, nicht mit jedem Bild */
    public static String seconds(long remainingMs) {
        return String.format(Locale.ROOT, "%.2fs", remainingMs / 1000.0);
    }

    /**
     * Der Kasten, wie er jetzt gezeichnet wird - nach der Uhr des letzten Ticks, nicht der des Bildes (siehe
     * {@link FireVeil#tickTime()}). Die Auswahl der Kaesten baut ihn mit jedem neuen Tick neu.
     */
    public static HudPanel build() {
        return build(FireVeil.tickTime(), editorOpen());
    }

    public static HudPanel build(long now, boolean editorOpen) {
        HudPanel panel = new HudPanel();
        if (FireVeil.isReadyVisible(now)) {
            panel.pair("Fire Veil", "READY", HudColours.AQUA, HudColours.GREEN);
        } else if (editorOpen && !FireVeil.shouldDisplayCountdown(now)) {
            // Im Editor steht ein Beispiel da, damit man den Kasten sieht und setzen kann
            panel.pair("Fire Veil", seconds(4830L), HudColours.AQUA, colourFor(4830L));
        } else {
            long left = FireVeil.remainingMs(now);
            panel.pair("Fire Veil", seconds(left), HudColours.AQUA, colourFor(left));
        }
        return panel;
    }
}

package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.handler.LegionBobbin;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Der Kasten zum Legion/Bobbin Overlay: je eine Zeile fuer Legion (Spieler in der Naehe und was sie geben) und fuer Bobbin' Time (Haken
 * in der Naehe und was sie geben), etwa "Legion: 12 (4.2%)".
 *
 * Herkunft der Idee: SkyHanni ("Legion/Bobbin Overlay"). Mit "Hide without enchant" steht eine Zeile nur da, wenn die Ruestung die
 * Verzauberung traegt; im HUD-Editor ({@code /shoki hud}) immer, mit Beispielwerten, damit sich der Kasten setzen laesst.
 */
public final class LegionBobbinHud {

    private LegionBobbinHud() {
    }

    /** Steht die Zeile da? Ohne "Hide without enchant" immer, sonst nur mit der Verzauberung an der Ruestung */
    static boolean showsLine(boolean hideWithoutEnchant, boolean wearing) {
        return !hideWithoutEnchant || wearing;
    }

    /** Soll der Kasten jetzt gezeichnet werden? In SkyBlock, solange er an ist und eine Zeile zu sagen hat; im HUD-Editor immer */
    public static boolean shownNow() {
        ModConfig.LegionBobbinCategory c = ModConfig.INSTANCE.misc.legionBobbin;
        if (!c.enabled) return false;
        if (HudEditorScreen.isOpen()) return true;
        return GameState.Server.isSkyblock()
                && (showsLine(c.hideWithoutEnchant, LegionBobbin.wearingLegion()) || showsLine(c.hideWithoutEnchant, LegionBobbin.wearingBobbin()));
    }

    /** Eine Zahl auf zwei Stellen gerundet, wie SkyHanni sie schreibt: 7.0, 4.2, 0.35 */
    static String number(double value) {
        return Double.toString(BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue());
    }

    /** "Legion: 12 (4.2%)" - die Beschriftung fett in Hellviolett, die Anzahl in Aqua, der Wert in Grau */
    static String legionLine(int players, double percent) {
        return "§d§lLegion: §r§b" + players + " §r§7(" + number(percent) + "%)";
    }

    /** "Bobbin': 3 (1.8%)" - die Beschriftung fett in Dunkelaqua, die Anzahl in Aqua, der Wert in Grau */
    static String bobbinLine(int bobbers, double percent) {
        return "§3§lBobbin': §r§b" + bobbers + " §r§7(" + number(percent) + "%)";
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        ModConfig.LegionBobbinCategory c = ModConfig.INSTANCE.misc.legionBobbin;
        boolean legion = showsLine(c.hideWithoutEnchant, LegionBobbin.wearingLegion());
        boolean bobbin = showsLine(c.hideWithoutEnchant, LegionBobbin.wearingBobbin());
        // Im Editor ohne etwas zu sagen stehen Beispielwerte da: Legion V (0,35 je Spieler) mit 12 Spielern, Bobbin' III (0,6 je Haken) mit 3 Haken
        if (!legion && !bobbin && HudEditorScreen.isOpen()) {
            panel.line(legionLine(12, 12 * 0.35), HudColours.WHITE);
            panel.line(bobbinLine(3, 3 * 0.6), HudColours.WHITE);
            return panel;
        }
        if (legion) panel.line(legionLine(LegionBobbin.players(), LegionBobbin.legionPercent()), HudColours.WHITE);
        if (bobbin) panel.line(bobbinLine(LegionBobbin.bobbers(), LegionBobbin.bobbinPercent()), HudColours.WHITE);
        return panel;
    }
}

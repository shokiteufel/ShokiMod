package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.handler.SeaCreatureHp;

import net.minecraft.client.Minecraft;

/**
 * Die HP der Meeresbewohner in der Naehe: eine Zeile je Bewohner, Name und Lebenspunkte in den Farben
 * ihres Namensschilds - wie bei Feesh, nur ohne die Symbole links und das Herz rechts. Eine Ueberschrift
 * gibt es dort auch nicht.
 *
 * Welche Bewohner hier stehen, stellt man in /shoki seacreatures ein (Spalte HP).
 */
public final class SeaHpHud {

    private SeaHpHud() {
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        var rows = SeaCreatureHp.rows();
        if (!rows.isEmpty()) {
            for (SeaCreatureHp.Mob mob : rows) panel.line(mob.display(), HudColours.WHITE);
            return panel;
        }
        // Im HUD-Editor soll man den Kasten fassen koennen, auch wenn nichts in der Naehe steht
        if (Minecraft.getInstance().gui.screen() instanceof HudEditorScreen) {
            for (String line : SeaCreatureHp.sample()) panel.line(line, HudColours.WHITE);
        }
        return panel;
    }
}

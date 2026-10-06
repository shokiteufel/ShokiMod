package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.handler.LootshareTracker;
import com.shokiteufel.shokimod.handler.LootshareTracker.Target;

import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

/**
 * Der kleine Kasten mit dem eigenen Schaden: "Lord Jawbus  6.5M dmg (13%)  ✓".
 *
 * Er steht, solange der Mob lebt, und zaehlt nach dem Haeckchen weiter - der Anteil an
 * den Lebenspunkten ist auch danach interessant, denn Hypixel verteilt die Beute an die
 * fuenf staerksten, und ein Prozent ist nur die Eintrittskarte.
 */
public final class LootshareHud {

    private static final int NAME_COLOUR = HudColours.GOLD;
    private static final int VALUE_COLOUR = HudColours.WHITE;
    private static final int READY_COLOUR = HudColours.GREEN;

    private LootshareHud() {
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        List<Target> list = LootshareTracker.targets();

        if (list.isEmpty()) {
            // Ohne Mob gibt es nichts zu zeigen - ausser beim Einrichten: Ein leerer Kasten
            // laesst sich im HUD-Editor nicht anfassen, also steht dort ein Beispiel
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.gui.screen() instanceof HudEditorScreen) {
                panel.pair("Lord Jawbus", "6.5M dmg (13%) ✓", NAME_COLOUR, READY_COLOUR);
            }
            return panel;
        }

        for (Target t : list) {
            Double percent = t.percent();
            StringBuilder value = new StringBuilder(HudNumbers.amount(t.damage())).append(" dmg");
            if (percent != null) value.append(" (").append(percent(percent)).append(')');
            if (t.eligible()) value.append(" ✓");
            panel.pair(t.name, value.toString(), NAME_COLOUR, t.eligible() ? READY_COLOUR : VALUE_COLOUR);
        }
        return panel;
    }

    /** Unter zehn Prozent mit einer Stelle - dort zaehlt jedes Zehntel -, darueber ganz */
    public static String percent(double value) {
        return value < 10.0
                ? String.format(Locale.ROOT, "%.1f%%", value)
                : String.format(Locale.ROOT, "%.0f%%", value);
    }
}

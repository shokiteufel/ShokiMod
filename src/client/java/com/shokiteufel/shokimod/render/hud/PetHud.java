package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.scanner.PetState;

import java.util.Locale;

/**
 * Das aktive Pet: Name, Stufe und wie weit es zur naechsten ist.
 *
 * Den Fortschritt kennt nur das Pet-Menue (siehe {@link PetState}); ist es noch nie offen
 * gewesen, steht hier eben nur Name und Stufe. Ein Kasten, der dann gar nichts zeigte,
 * waere unangenehmer als einer, der zeigt, was er weiss.
 */
public final class PetHud {

    private static final int LABEL_COLOUR = HudColours.GOLD;
    private static final int VALUE_COLOUR = HudColours.WHITE;

    private PetHud() {
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        if (!PetState.known()) {
            panel.pair("Pet:", "-", LABEL_COLOUR, HudColours.GRAY);
            return panel;
        }

        panel.pair("Pet:", PetState.name(), LABEL_COLOUR, colour(PetState.rarity()));
        panel.pair("Level:", String.valueOf(PetState.level()), LABEL_COLOUR, VALUE_COLOUR);

        double prozent = PetState.percent();
        if (prozent >= 0) {
            // Der Balken rechnet in ganzen Zahlen; ein Zehntel Prozent reicht als Feinheit
            panel.bar(String.format(Locale.US, "%.1f%%", prozent),
                    (int) Math.round(prozent * 10), 1000, LABEL_COLOUR, HudColours.GREEN);
            double haben = PetState.xpHave();
            double noetig = PetState.xpNeed();
            if (haben >= 0 && noetig > 0) {
                panel.pair("XP:", compact(haben) + " / " + compact(noetig), LABEL_COLOUR, VALUE_COLOUR);
            }
        }
        return panel;
    }

    /** 23200 liest sich als 23.2k, 1400000 als 1.4M */
    private static String compact(double value) {
        if (value >= 1_000_000_000d) return String.format(Locale.US, "%.1fB", value / 1_000_000_000d);
        if (value >= 1_000_000d) return String.format(Locale.US, "%.1fM", value / 1_000_000d);
        if (value >= 1_000d) return String.format(Locale.US, "%.1fk", value / 1_000d);
        return String.format(Locale.US, "%.0f", value);
    }

    /** Die Farbe der Seltenheit, wie im Spiel */
    private static int colour(String rarity) {
        return switch (rarity.toUpperCase(Locale.ROOT)) {
            case "UNCOMMON" -> HudColours.GREEN;
            case "RARE" -> HudColours.AQUA;
            case "EPIC" -> HudColours.LIGHT_PURPLE;
            case "LEGENDARY" -> HudColours.GOLD;
            case "MYTHIC" -> HudColours.LIGHT_PURPLE;
            default -> HudColours.WHITE;
        };
    }
}

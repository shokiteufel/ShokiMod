package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.scanner.PetState;

import java.util.Locale;

/**
 * Das aktive Pet: Bild, Name, Stufe und was darueber hinausgeht.
 *
 * Was davon erscheint, steht unter HUD &gt; Pet - vom blossen Namen bis zur vollen Zeile
 * "[Lvl 200] [332] Golden Dragon" mit Ueberschuss-Erfahrung darunter. Alles in einer
 * Zeile zusammenzufassen ist Absicht: der Kasten soll neben dem Fadenkreuz nicht mehr
 * Platz brauchen, als er muss.
 *
 * Den Fortschritt kennt nur das Pet-Menue (siehe {@link PetState}); war es noch nie
 * offen, steht hier eben nur, was die Wechselmeldung im Chat hergab. Ein Kasten, der
 * dann gar nichts zeigte, waere unangenehmer als einer, der zeigt, was er weiss.
 */
public final class PetHud {

    private static final int LABEL_COLOUR = HudColours.GOLD;
    private static final int VALUE_COLOUR = HudColours.WHITE;
    /** Der Stern hinter den Ueberschuss-Stufen, wie ihn die Mods im Spiel setzen */
    private static final String STAR = "⭐";

    private PetHud() {
    }

    private static ModConfig.PetHudCategory cfg() {
        return ModConfig.INSTANCE.hud.pet;
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        ModConfig.PetHudCategory c = cfg();

        if (!PetState.known()) {
            panel.pair("Pet:", "-", LABEL_COLOUR, HudColours.GRAY);
            return panel;
        }

        String zeile = headline(c);
        // Das zuletzt gesehene Bild, sonst das gemerkte aus frueheren Sitzungen
        net.minecraft.world.item.ItemStack bild = PetState.icon();
        if (bild.isEmpty()) {
            bild = com.shokiteufel.shokimod.util.PetIcons.iconFor(PetState.name());
        }
        // Das Bild traegt die Zeile mit; ohne Bild bleibt es bei der gewohnten Paarzeile
        if (c.showIcon && !bild.isEmpty()) {
            panel.icon(bild, "Pet:", zeile, LABEL_COLOUR, colour(PetState.rarity()));
        } else {
            panel.pair("Pet:", zeile, LABEL_COLOUR, colour(PetState.rarity()));
        }

        if (c.showHeldItem && !PetState.heldItem().isEmpty()) {
            panel.pair("Item:", PetState.heldItem(), LABEL_COLOUR, VALUE_COLOUR);
        }

        if (c.showOverflowXp && PetState.overflowXp() > 0) {
            panel.pair("Overflow:", "+" + compact(PetState.overflowXp()) + " XP",
                    LABEL_COLOUR, HudColours.AQUA);
        }

        // Ein Balken ergibt nur Sinn, solange es noch eine naechste Stufe gibt
        double prozent = PetState.percent();
        if (c.showProgress && !PetState.atMaxLevel() && prozent >= 0) {
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

    /**
     * Die Hauptzeile, aus den gewaehlten Teilen zusammengesetzt:
     * "[Lvl 200] [332] Golden Dragon"
     */
    private static String headline(ModConfig.PetHudCategory c) {
        StringBuilder out = new StringBuilder();
        if (c.showLevel && PetState.level() > 0) {
            out.append("[Lvl ").append(PetState.level()).append(']');
        }
        if (c.showOverflowLevel && PetState.overflowLevel() > 0) {
            if (out.length() > 0) out.append(' ');
            // Stufe und Ueberschuss zusammen: aus 200 und 332 darueber wird 532
            out.append('[').append(PetState.combinedLevel()).append(STAR).append(']');
        }
        if (c.showName && !PetState.name().isEmpty()) {
            if (out.length() > 0) out.append(' ');
            out.append(PetState.name());
        }
        // Wer alles abschaltet, soll keinen leeren Kasten bekommen
        return out.length() == 0 ? PetState.name() : out.toString();
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

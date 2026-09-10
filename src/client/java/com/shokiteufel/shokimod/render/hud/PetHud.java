package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.scanner.PetState;
import com.shokiteufel.shokimod.util.PetIcons;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Das aktive Pet: Bild, Name, Stufe und was darueber hinausgeht.
 *
 * Welche Teile erscheinen, in welcher Reihenfolge und ob neben- oder untereinander,
 * stellt der Baukasten ein (/shoki pet). Die Reihenfolge kommt aus der Einstellung und
 * nicht aus dem Quelltext - wer den Namen vor die Stufe ziehen will, soll das tun
 * koennen, ohne dass jemand die Mod neu baut.
 *
 * Den Fortschritt kennt nur das Pet-Menue (siehe {@link PetState}); war es noch nie
 * offen, steht hier eben nur, was die Wechselmeldung im Chat hergab. Das Bild wird
 * ueber das Spielende hinaus gemerkt ({@link PetIcons}).
 */
public final class PetHud {

    private static final int LABEL_COLOUR = HudColours.GOLD;
    private static final int VALUE_COLOUR = HudColours.WHITE;
    /** Der Stern hinter den Ueberschuss-Stufen, wie ihn die Mods im Spiel setzen */
    private static final String STAR = "⭐";

    /** Die Teile, aus denen sich der Kasten zusammensetzt */
    public enum Part {
        ICON("Icon"),
        LEVEL("Level"),
        OVERFLOW_LEVEL("Overflow level"),
        NAME("Name"),
        OVERFLOW_XP("Overflow XP"),
        HELD_ITEM("Held item"),
        PROGRESS("Progress");

        public final String label;

        Part(String label) {
            this.label = label;
        }

        public boolean enabled(ModConfig.PetHudCategory c) {
            return switch (this) {
                case ICON -> c.showIcon;
                case LEVEL -> c.showLevel;
                case OVERFLOW_LEVEL -> c.showOverflowLevel;
                case NAME -> c.showName;
                case OVERFLOW_XP -> c.showOverflowXp;
                case HELD_ITEM -> c.showHeldItem;
                case PROGRESS -> c.showProgress;
            };
        }

        public void toggle(ModConfig.PetHudCategory c) {
            switch (this) {
                case ICON -> c.showIcon = !c.showIcon;
                case LEVEL -> c.showLevel = !c.showLevel;
                case OVERFLOW_LEVEL -> c.showOverflowLevel = !c.showOverflowLevel;
                case NAME -> c.showName = !c.showName;
                case OVERFLOW_XP -> c.showOverflowXp = !c.showOverflowXp;
                case HELD_ITEM -> c.showHeldItem = !c.showHeldItem;
                case PROGRESS -> c.showProgress = !c.showProgress;
            }
        }
    }

    private PetHud() {
    }

    /** Die Beschriftung vor einem Wert - leer, wenn sie abgeschaltet ist */
    private static String label(ModConfig.PetHudCategory c, String text) {
        return c.showLabels ? text : "";
    }

    private static ModConfig.PetHudCategory cfg() {
        return ModConfig.INSTANCE.hud.pet;
    }

    /**
     * Die Teile in der eingestellten Reihenfolge.
     *
     * Was in der Einstellung fehlt, kommt hinten dazu: sonst waere ein Teil, das eine
     * spaetere Fassung mitbringt, fuer alle unsichtbar, die schon einmal sortiert haben.
     */
    public static List<Part> order(ModConfig.PetHudCategory c) {
        List<Part> out = new ArrayList<>();
        if (c.order != null) {
            for (String name : c.order) {
                try {
                    Part part = Part.valueOf(name);
                    if (!out.contains(part)) out.add(part);
                } catch (IllegalArgumentException ignored) {
                    // ein Teil, das es nicht mehr gibt - stillschweigend weglassen
                }
            }
        }
        for (Part part : Part.values()) {
            if (!out.contains(part)) out.add(part);
        }
        return out;
    }

    /** Die Reihenfolge festhalten, wie sie im Baukasten steht */
    public static void saveOrder(ModConfig.PetHudCategory c, List<Part> parts) {
        List<String> names = new ArrayList<>(parts.size());
        for (Part part : parts) names.add(part.name());
        c.order = names;
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        ModConfig.PetHudCategory c = cfg();
        panel.iconScale(c.iconScale);

        if (!PetState.known()) {
            panel.pair("Pet:", "-", LABEL_COLOUR, HudColours.GRAY);
            return panel;
        }

        ItemStack bild = icon();
        boolean bildAn = c.showIcon && !bild.isEmpty();
        // Nebeneinander heisst: alles Geschriebene in eine Zeile, das Bild davor
        StringBuilder zeile = c.sameLine ? new StringBuilder() : null;
        boolean bildOffen = bildAn;

        for (Part part : order(c)) {
            if (!part.enabled(c)) continue;
            if (part == Part.ICON) {
                // Auf eigener Zeile - oder untereinander, wo jede Zeile fuer sich steht
                if (bildOffen && (c.iconPlace == ModConfig.IconPlace.OWN_LINE || !c.sameLine)) {
                    panel.icon(bild, label(c, "Pet:"), "", LABEL_COLOUR, VALUE_COLOUR);
                    bildOffen = false;
                }
                continue;
            }
            if (part == Part.PROGRESS) {
                // Der Balken passt in keine gemeinsame Zeile und steht immer fuer sich
                if (zeile != null && zeile.length() > 0) {
                    bildOffen = flush(panel, c, bild, bildOffen, zeile.toString());
                    zeile.setLength(0);
                }
                addProgress(panel, c);
                continue;
            }
            String text = textOf(part);
            if (text.isEmpty()) continue;
            if (zeile != null) {
                if (zeile.length() > 0) zeile.append(' ');
                zeile.append(text);
            } else {
                panel.pair(label(c, part.label + ":"), text, LABEL_COLOUR, colourOf(part));
            }
        }
        if (zeile != null && zeile.length() > 0) flush(panel, c, bild, bildOffen, zeile.toString());
        // Wer alles abschaltet, soll trotzdem sehen, welches Pet draussen ist
        if (panel.isEmpty()) {
            panel.pair(label(c, "Pet:"), PetState.name(), LABEL_COLOUR, colour(PetState.rarity()));
        }
        return panel;
    }

    /**
     * Die gesammelte Zeile in den Kasten schreiben - mit Bild, wenn noch eines aussteht.
     *
     * @return ob danach noch ein Bild aussteht
     */
    private static boolean flush(HudPanel panel, ModConfig.PetHudCategory c, ItemStack bild,
                                 boolean bildOffen, String text) {
        String vorne = label(c, "Pet:");
        if (bildOffen) {
            // Hinten heisst: der Text steht links, das Bild rechts davon
            if (c.iconPlace == ModConfig.IconPlace.RIGHT) {
                panel.icon(bild, vorne.isEmpty() ? text : vorne + " " + text, "",
                           LABEL_COLOUR, colour(PetState.rarity()));
            } else {
                panel.icon(bild, vorne, text, LABEL_COLOUR, colour(PetState.rarity()));
            }
            return false;
        }
        panel.pair(vorne, text, LABEL_COLOUR, colour(PetState.rarity()));
        return false;
    }

    private static void addProgress(HudPanel panel, ModConfig.PetHudCategory c) {
        double prozent = PetState.percent();
        // Ein Balken ergibt nur Sinn, solange es noch eine naechste Stufe gibt
        if (PetState.atMaxLevel() || prozent < 0) return;
        panel.bar(String.format(Locale.US, "%.1f%%", prozent),
                (int) Math.round(prozent * 10), 1000, LABEL_COLOUR, HudColours.GREEN);
        double haben = PetState.xpHave();
        double noetig = PetState.xpNeed();
        if (haben >= 0 && noetig > 0) {
            panel.pair("XP:", compact(haben) + " / " + compact(noetig), LABEL_COLOUR, VALUE_COLOUR);
        }
    }

    /** Was ein Teil anzeigt, oder leer wenn es dazu gerade nichts gibt */
    private static String textOf(Part part) {
        return switch (part) {
            case LEVEL -> PetState.level() > 0 ? "[Lvl " + PetState.level() + "]" : "";
            // Stufe und Ueberschuss zusammen: aus 200 und 332 darueber wird 532
            // Auch bei null Ueberschuss-Stufen anzeigen, sobald ueberhaupt Erfahrung
            // darueber gesammelt wurde: Sonst sieht es aus, als waere die Rechnung
            // ausgefallen, dabei reicht die Erfahrung nur noch nicht fuer eine Stufe
            case OVERFLOW_LEVEL -> PetState.overflowXp() > 0
                    ? "[" + PetState.combinedLevel() + STAR + "]" : "";
            case NAME -> PetState.name();
            case OVERFLOW_XP -> PetState.overflowXp() > 0
                    ? "+" + compact(PetState.overflowXp()) + " XP" : "";
            case HELD_ITEM -> PetState.heldItem();
            default -> "";
        };
    }

    private static int colourOf(Part part) {
        return switch (part) {
            case NAME -> colour(PetState.rarity());
            case OVERFLOW_XP, OVERFLOW_LEVEL -> HudColours.AQUA;
            default -> VALUE_COLOUR;
        };
    }

    /** Das zuletzt gesehene Bild, sonst das gemerkte aus frueheren Sitzungen */
    private static ItemStack icon() {
        ItemStack aktuell = PetState.icon();
        return aktuell.isEmpty() ? PetIcons.iconFor(PetState.name()) : aktuell;
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

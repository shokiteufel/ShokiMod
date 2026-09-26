package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.scanner.MiningState;

import java.util.List;

/**
 * Der Mining-Kasten: laufende Auftraege, die Spitzhacken-Faehigkeit mit ihrer Abklingzeit
 * und der Sky-Mall-Buff des Tages.
 *
 * Jede Zeile laesst sich einzeln abschalten. Steht nichts an, bleibt der Kasten leer -
 * das faengt {@link com.shokiteufel.shokimod.render.hud.SafariHud} ab und zeichnet ihn
 * dann gar nicht erst, damit kein leerer Rahmen herumsteht.
 */
public final class MiningHud {

    private static final int TITLE_COLOUR = HudColours.GOLD;
    private static final int LABEL_COLOUR = HudColours.WHITE;
    private static final int VALUE_COLOUR = HudColours.GREEN;
    private static final int READY_COLOUR = HudColours.AQUA;

    /** Ab diesem Fortschritt wird die Zahl orange statt rot */
    private static final double ORANGE_FROM = 30.0;

    private MiningHud() {
    }

    /**
     * Die Zahl aus "56.9%", oder -1 wenn dort keine steht.
     *
     * Hypixel schreibt je nach Sprache mal Punkt, mal Komma - beides wird gelesen.
     */
    private static double percent(String progress) {
        String clean = progress.replace("%", "").replace(',', '.').trim();
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        ModConfig.MiningHudCategory cfg = ModConfig.INSTANCE.mining.hud;
        panel.title("Mining", TITLE_COLOUR);

        if (cfg.showCommissions) {
            List<String> commissions = MiningState.commissions();
            if (commissions.isEmpty()) {
                panel.pair("Commissions:", "-", LABEL_COLOUR, LABEL_COLOUR);
            } else {
                for (String entry : commissions) {
                    int split = entry.lastIndexOf(':');
                    if (split <= 0) {
                        panel.pair(entry, "", LABEL_COLOUR, VALUE_COLOUR);
                        continue;
                    }
                    String name = entry.substring(0, split).trim();
                    String progress = entry.substring(split + 1).trim();
                    // Fertig ist gruen, alles andere rot - so sieht man im Vorbeigehen,
                    // was noch offen ist, ohne die Zahlen zu lesen
                    boolean done = "DONE".equalsIgnoreCase(progress) || "100%".equals(progress);
                    // Drei Stufen: rot bis 30 Prozent, dann orange, fertig gruen. Der Name
                    // bleibt weiss, damit er lesbar bleibt - erst wenn der Auftrag fertig
                    // ist, faerbt er sich mit, weil dann die ganze Zeile erledigt ist
                    panel.pair(name, progress,
                            done ? HudColours.GREEN : LABEL_COLOUR,
                            done ? HudColours.GREEN
                                 : percent(progress) >= ORANGE_FROM ? HudColours.GOLD
                                 : HudColours.RED);
                }
            }
        }

        boolean ability = cfg.showAbility && !MiningState.ability().isBlank();
        boolean cooldown = cfg.showCooldown && !MiningState.cooldown().isBlank();
        if (ability || cooldown) {
            if (cfg.showCommissions) panel.blank();
            if (ability) panel.pair("Ability:", MiningState.ability(), LABEL_COLOUR, VALUE_COLOUR);
            if (cooldown) {
                String value = MiningState.cooldown();
                boolean ready = value.toLowerCase(java.util.Locale.ROOT).contains("ready");
                panel.pair("Cooldown:", value, LABEL_COLOUR,
                        ready ? HudColours.GREEN : HudColours.RED);
            }
        }

        List<MiningState.Corpse> corpses = MiningState.corpses();
        if (!corpses.isEmpty()) {
            if (cfg.showCommissions || ability || cooldown) panel.blank();
            for (MiningState.Corpse corpse : corpses) {
                // Offen ist rot, gepluendert gruen - dieselbe Lesart wie bei den Auftraegen
                boolean done = corpse.open() == 0;
                int looted = corpse.total() - corpse.open();
                // Ohne Schluessel steht die Leiche da wie eine Wand: das gehoert in die Zeile
                boolean noKey = !done && com.shokiteufel.shokimod.scanner.CorpseKeys.missing(corpse.type());
                panel.pair(corpse.type() + (noKey ? " (no key)" : ""), looted + "/" + corpse.total(),
                        LABEL_COLOUR, done ? HudColours.GREEN : HudColours.RED);
            }

            String keys = com.shokiteufel.shokimod.scanner.CorpseKeys.summary();
            if (!keys.isEmpty()) {
                panel.pair("Keys:", keys, LABEL_COLOUR, VALUE_COLOUR);
            }
        }

        // Die Adern des Schachts, je Sorte zusammengezaehlt. Die Frage beim Ausminen ist,
        // wie viel drin liegt - nicht, was ein einzelner Block bringt
        java.util.List<com.shokiteufel.shokimod.scanner.OreVeins.Vein> veins =
                com.shokiteufel.shokimod.scanner.OreVeins.veins();
        if (!veins.isEmpty()) {
            java.util.Map<String, int[]> perKind = new java.util.LinkedHashMap<>();
            for (var vein : veins) {
                int[] sum = perKind.computeIfAbsent(vein.kind().label(), k -> new int[2]);
                sum[0] += vein.size();
                sum[1]++;
            }
            panel.blank();
            for (var entry : perKind.entrySet()) {
                int[] sum = entry.getValue();
                panel.pair(entry.getKey(), sum[0] + " blocks in " + sum[1]
                        + (sum[1] == 1 ? " vein" : " veins"), LABEL_COLOUR, VALUE_COLOUR);
            }
        }

        if (cfg.showSkyMall && !MiningState.skyMall().isBlank()) {
            if (cfg.showCommissions || ability || cooldown || !corpses.isEmpty()) panel.blank();
            panel.pair("Sky Mall:", MiningState.skyMall(), LABEL_COLOUR, READY_COLOUR);
        }

        return panel;
    }
}

package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.render.DropBanner;
import com.shokiteufel.shokimod.util.ScoreboardUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Meldet, dass der eigene Slayer-Boss erschienen ist.
 *
 * Hypixel schreibt es in die Seitenleiste: Im Kasten "Slayer Quest" steht erst der Fortschritt
 * ("(2/12) Kills"), und sobald der Boss da ist, an derselben Stelle "Slay the boss!". Der Kasten
 * ist der einzige Hinweis, den der Client bekommt - die Partikel des Spawns taugen nicht dazu: SkyHanni
 * nutzt sie nur, um sie auszublenden. Dieselbe Zeile lesen SkyHanni und Skyblocker, um den Boss-Kampf
 * zu erkennen.
 *
 * Gemeldet wird einmal, wenn die Zeile erscheint. Damit nichts doppelt kommt:
 * - Was beim ersten Lesen schon da ist (Welt betreten, Schalter eingeschaltet), ist nur die Ausgangslage.
 * - Eine kurze Luecke in der Seitenleiste (sie wird Zeile fuer Zeile neu gebaut) zaehlt nicht als Ende;
 *   erst wenn die Zeile eine Sekunde fehlt, gilt der naechste Boss wieder als neu.
 * - Fehlt die Seitenleiste ganz, wird nichts geschlossen.
 */
public final class SlayerBossSpawn {

    private static final String BOSS_LINE = "Slay the boss!";
    private static final String HEADER = "Slayer Quest";
    /** Alle fuenf Ticks lesen reicht: Das Banner kommt hoechstens eine Viertelsekunde nach der Zeile */
    private static final int READ_EVERY_TICKS = 5;
    /** So viele Lesungen ohne die Zeile, bis der naechste Boss wieder neu ist: eine Sekunde */
    private static final int ABSENT_READS = 4;
    private static final int COLOUR = 0xFF5555;

    private static int ticks = 0;
    /** Ist die Ausgangslage gelesen? */
    private static boolean known = false;
    /** Darf der naechste Spawn gemeldet werden? */
    private static boolean armed = false;
    private static int absentReads = 0;
    private static Object lastLevel = null;

    private SlayerBossSpawn() {
    }

    public static void tick(Minecraft client) {
        // Abgeschaltet oder nicht in SkyBlock: nichts lesen, und beim naechsten Mal neu anfangen
        if (client == null || client.level == null || !FeatureGate.slayerSpawn() || !GameState.Server.isSkyblock()) {
            known = false;
            return;
        }
        // Eine neue Welt (Insel gewechselt) ist eine neue Ausgangslage
        if (client.level != lastLevel) {
            lastLevel = client.level;
            known = false;
        }
        if (++ticks % READ_EVERY_TICKS != 0) return;

        List<String> lines = ScoreboardUtils.getSidebarLines(client);
        // Ist die Seitenleiste gerade nicht da, ist das keine Antwort
        if (lines.isEmpty()) return;

        int at = bossLine(lines);
        boolean present = at >= 0;
        if (!known) {
            known = true;
            armed = !present;
            absentReads = 0;
            return;
        }
        if (present) {
            if (armed) {
                armed = false;
                announce(bossName(lines, at));
            }
            absentReads = 0;
        } else if (++absentReads >= ABSENT_READS) {
            armed = true;
        }
    }

    /** Die Stelle der Zeile "Slay the boss!" in der Seitenleiste, oder -1 */
    static int bossLine(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (BOSS_LINE.equals(ScoreboardUtils.stripColor(lines.get(i)).trim())) return i;
        }
        return -1;
    }

    /** Der Name des Bosses: die Zeile darueber im Kasten ("Voidgloom Seraph III"), oder leer */
    static String bossName(List<String> lines, int bossLine) {
        if (bossLine <= 0) return "";
        String above = ScoreboardUtils.stripColor(lines.get(bossLine - 1)).trim();
        return above.equals(HEADER) ? "" : above;
    }

    private static void announce(String boss) {
        DropBanner.show(ModConfig.INSTANCE.chat.banner.designOrDefault(ModConfig.INSTANCE.slayer.spawnBannerDesign),
                "Slayer boss spawned!", boss, "", COLOUR, ItemStack.EMPTY);
        ShokiMod.LOGGER.info("[Slayer] boss spawned: '{}' - banner shown", boss);
    }

    /** Der Testknopf: einmal mit einem Beispiel, im eingestellten Design */
    public static void test() {
        announce("Voidgloom Seraph III");
    }
}

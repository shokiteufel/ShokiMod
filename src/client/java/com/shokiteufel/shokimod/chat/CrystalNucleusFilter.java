package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig.ChatFilterTypesCategory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat Filter Types > Crystal Nucleus: die Meldungen der Laeufe im Crystal Hollows, ausgeblendet oder zu einer kurzen Zeile gemacht. Die Blocks mit den
 * langen Linien ({@link WrapperBlocks}) rufen {@link #crystalCollectedLine} und {@link #runCompletedLine}; alles andere sind einzelne Zeilen
 * ({@link #single}), von denen sich einige etwas merken (der Kristall, der Hueter, die Liste der Bauteile).
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code CrystalNucleusChatFilter}). Von dort stammen die Regeln: ein gefundener Kristall wird zu
 * "Kristall found! (n/5)", ein gesetzter zu "Kristall placed!", die Zusammenfassung eines Laufs zu "Crystal Nucleus Run complete!"; die Hueter, Professor
 * Robot, King Yolkar und die Goblin-Waechter schweigen bis auf eine Zeile ("All tools submitted.", "X submitted. N components left."); die Funde des
 * Metalldetektors, die kein Werkzeug sind, fallen weg. Die Umsetzung ist eigene Arbeit: Java, Muster fuer die Zeile ohne Farbcodes, Zeitablauf ueber
 * eine Uhr, die man hineingibt (so laesst sich der Filter ohne Spiel pruefen). Zwei Abweichungen: Die Liste der Bauteile nach "That's not one of the
 * components I need!" faellt hier tatsaechlich weg (bei SkyHanni wird sie nie erreicht, weil die Zeilen nicht mit "[NPC]" beginnen), und "one more
 * component" steht in der Einzahl.
 */
final class CrystalNucleusFilter {

    /** Wie lange nach "That's not one of the components I need!" die Zeilen mit den Bauteilen weggehen (bei SkyHanni zwei Sekunden) */
    static final long COMPONENT_LIST_MS = 2000L;

    private static final Pattern CRYSTAL_COUNT = Pattern.compile(" *✦ CRYSTAL FOUND \\((\\d)/5\\)");
    private static final Pattern CRYSTAL_NAME = Pattern.compile("\\s*(\\S.* Crystal)\\s*");
    private static final Pattern CRYSTAL_PLACED = Pattern.compile("✦ You placed the (.* Crystal)!");
    private static final Pattern SCAVENGE = Pattern.compile("You found (.*) with your Metal Detector!");
    private static final Pattern KEEPER = Pattern.compile("\\[NPC] Keeper of (.*): (.*)");
    private static final Pattern COMPONENT = Pattern.compile("me the (.*)! Bring me (\\d|one) more");

    private static final String RUN_END = "Pick it up near the Nucleus Vault!";
    private static final String KEEP_EXPLORING = "  Keep exploring the Crystal Hollows to find the rest!";
    private static final String COMPONENT_PREAMBLE_END = "Bring me one of the missing components:";

    private static final ChatRule[] GUARD_EXIT = {
            ChatRule.exact("Whew! That was a close one, better get out of here..."),
            ChatRule.exact("The Goblin King's foul stench has dissipated!"),
    };

    private int crystalCount = 0;
    private String lastKeeper = "";
    private long componentListUntil = 0L;

    /** Alles vergessen (Weltwechsel) */
    void reset() {
        crystalCount = 0;
        lastKeeper = "";
        componentListUntil = 0L;
    }

    /** Ist die Zeile die Zahl der gefundenen Kristalle ("CRYSTAL FOUND (1/5)")? Sie zeigt, dass der Block einen Kristall meldet */
    static boolean isCrystalCount(String line) {
        return line.contains("CRYSTAL FOUND") && CRYSTAL_COUNT.matcher(line).matches();
    }

    /** Eine Zeile im Block "CRYSTAL FOUND": die Zahl wird gemerkt, der Name wird zur kurzen Zeile, alles andere faellt weg */
    ChatVerdict crystalCollectedLine(String line) {
        Matcher count = CRYSTAL_COUNT.matcher(line);
        if (line.contains("CRYSTAL FOUND") && count.matches()) {
            crystalCount = Integer.parseInt(count.group(1));
            return ChatVerdict.HIDE;
        }
        Matcher name = CRYSTAL_NAME.matcher(line);
        if (name.matches()) {
            return ChatVerdict.replace("§5§l✦ " + name.group(1) + " §5found§d! §7(§a" + crystalCount + "§7/§a5§7)");
        }
        return ChatVerdict.HIDE;
    }

    /** Eine Zeile im Block der Zusammenfassung eines Laufs: "Pick it up near the Nucleus Vault!" wird zur kurzen Zeile, alles andere faellt weg */
    ChatVerdict runCompletedLine(String line) {
        if (line.equals(RUN_END)) return runComplete();
        return ChatVerdict.HIDE;
    }

    private static ChatVerdict runComplete() {
        return ChatVerdict.replace("§5Crystal Nucleus Run complete§d!");
    }

    /**
     * Eine einzelne Zeile, ausserhalb der Bloecke. Nur im Crystal Hollows (das prueft der Aufrufer); null heisst, der Filter hat dazu nichts zu sagen.
     *
     * @param now die Uhr in Millisekunden (fuer die Liste der Bauteile)
     */
    ChatVerdict single(String line, ChatFilterTypesCategory c, long now) {
        // Die Zusammenfassung eines Laufs endet mit dieser Zeile - auch ausserhalb eines Blocks wird sie zur kurzen
        if (c.nucleusRunCompleted && line.equals(RUN_END)) return runComplete();

        if (c.nucleusCrystalPlaced) {
            if (line.equals(KEEP_EXPLORING)) return ChatVerdict.HIDE;
            if (line.contains("You placed the ")) {
                Matcher m = CRYSTAL_PLACED.matcher(line);
                if (m.matches()) return ChatVerdict.replace("§5§l✦ " + m.group(1) + " §5placed§d!");
            }
        }

        if (c.nucleusNonToolScavenge) {
            if (line.equals("PICK IT UP!")) return ChatVerdict.HIDE;
            if (line.contains(" with your Metal Detector!")) {
                Matcher m = SCAVENGE.matcher(line);
                if (m.matches() && !m.group(1).startsWith("Scavenged")) return ChatVerdict.HIDE;
            }
        }

        if (c.nucleusGoblinGuards) {
            if (line.startsWith("[GUARD]") || ChatRule.any(GUARD_EXIT, line)) return ChatVerdict.HIDE;
        }

        if (c.nucleusProfRobot && now < componentListUntil && line.startsWith("  ")) return ChatVerdict.HIDE;

        if (line.startsWith("[NPC]")) {
            ChatVerdict v = professorRobot(line, c, now);
            if (v != null) return v;
            v = kingYolkar(line, c);
            if (v != null) return v;
            return keepers(line, c);
        }
        return null;
    }

    private ChatVerdict professorRobot(String line, ChatFilterTypesCategory c, long now) {
        if (!c.nucleusProfRobot || !line.startsWith("[NPC] Professor Robot")) return null;
        if (line.endsWith(COMPONENT_PREAMBLE_END)) {
            componentListUntil = now + COMPONENT_LIST_MS;
            return ChatVerdict.HIDE;
        }
        if (line.contains("Wait a minute. This will work just fine.") || line.contains("You've brought me all")) {
            return ChatVerdict.replace("§e[NPC] Professor Robot§f: §rAll components submitted.");
        }
        Matcher m = COMPONENT.matcher(line);
        if (m.find()) {
            String left = m.group(2);
            String count = left.equals("one") ? "1 component" : left + " components";
            return ChatVerdict.replace("§e[NPC] Professor Robot§f: §9" + m.group(1) + "§f submitted. " + count + " left.");
        }
        return ChatVerdict.HIDE;
    }

    private static ChatVerdict kingYolkar(String line, ChatFilterTypesCategory c) {
        if (!c.nucleusKingYolkar || !line.startsWith("[NPC] King Yolkar")) return null;
        if (line.contains("*rumble* *rumble*")) return ChatVerdict.replace("§e[NPC] §6King Yolkar§f: ...");
        if (line.contains("Bring me back a Goblin Egg")) {
            return ChatVerdict.replace("§e[NPC] §6King Yolkar§f: §rBring me a §9Goblin Egg §rof any type.");
        }
        if (line.contains("This egg will help me stomach my pain.")) {
            return ChatVerdict.replace("§e[NPC] §6King Yolkar§f: §2King's Scent§r applied.");
        }
        return ChatVerdict.HIDE;
    }

    private ChatVerdict keepers(String line, ChatFilterTypesCategory c) {
        if (!c.nucleusKeepers || !line.startsWith("[NPC] Keeper of ")) return null;
        Matcher m = KEEPER.matcher(line);
        if (m.matches()) lastKeeper = m.group(1);
        if (line.contains("You found all of the items!")) {
            return ChatVerdict.replace("§e[NPC] §6Keeper of §6" + lastKeeper + "§f: §rAll tools submitted.");
        }
        return ChatVerdict.HIDE;
    }
}

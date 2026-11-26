package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig.ChatFilterTypesCategory;
import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;
import com.shokiteufel.shokimod.data.ModConfig.PowderGemstones;
import com.shokiteufel.shokimod.data.ModConfig.PowderGoblinEggs;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static com.shokiteufel.shokimod.chat.ChatTestSupport.HUB;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.WRAP;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.at;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.types;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chat Filter Types > Powder Mining: die einzelnen Zeilen, die Belohnungen (mit den "WRAPPED-REGEX-TEST"-Zeilen aus SkyHannis Quelltext) und der
 * ganze Block einer Truhe als Zeilenfolge durch den Zustand.
 */
class PowderMiningFilterTest {

    private static final ChatPlace HOLLOWS = at("Crystal Hollows");

    /** Der Block wird mit Zeitstempeln durch EINEN Filter gespielt: wie im Spiel, wo der Zustand von Zeile zu Zeile bleibt */
    private static final class Run {
        final ChatFilterEngine engine = new ChatFilterEngine();
        final ChatTabCategory config;
        final ChatPlace place;
        long now = 1000L;

        Run(ChatTabCategory config, ChatPlace place) {
            this.config = config;
            this.place = place;
        }

        ChatVerdict line(String line) {
            now += 20;
            return engine.decide(line, config, place, now);
        }
    }

    private static ChatTabCategory powder(Consumer<ChatFilterTypesCategory> more) {
        return types(f -> {
            f.powderEnabled = true;
            more.accept(f);
        });
    }

    private static void assertKind(ChatVerdict.Kind kind, ChatVerdict v, String line) {
        assertEquals(kind, v.kind(), line);
    }

    @Test
    void thePlainLinesAroundAChestGoWhenTheFilterIsOn() {
        ChatTabCategory c = powder(f -> {
        });
        for (String line : new String[] {"You uncovered a treasure chest!", "You have successfully picked the lock on this chest!", "This chest has already been looted."}) {
            assertKind(ChatVerdict.Kind.HIDE, new Run(c, HOLLOWS).line(line), line);
            assertKind(ChatVerdict.Kind.SHOW, new Run(new ChatTabCategory(), HOLLOWS).line(line), "aus: " + line);
        }
    }

    @Test
    void theBreakingPowerWarningFollowsItsOwnSwitch() {
        String ruby = "You need a tool with a Breaking Power of 6 to mine Ruby Gemstone Block! Speak to Fragilis by the entrance to the Crystal Hollows to learn more!";
        String mithril = "You need a tool with a Breaking Power of 4 to mine Mithril! Speak to Fragilis by the entrance to the Crystal Hollows to learn more!";
        for (String line : new String[] {ruby, mithril}) {
            assertKind(ChatVerdict.Kind.HIDE, new Run(powder(f -> {
            }), HOLLOWS).line(line), line);
            assertKind(ChatVerdict.Kind.SHOW, new Run(powder(f -> f.powderStrongerTool = false), HOLLOWS).line(line), line);
        }
    }

    @Test
    void theFilterIsOnlyForTheMiningIslands() {
        ChatTabCategory c = powder(f -> {
        });
        assertKind(ChatVerdict.Kind.SHOW, new Run(c, HUB).line("You uncovered a treasure chest!"), "Hub");
        for (String area : new String[] {"Dwarven Mines", "Crystal Hollows", "Gold Mine", "Deep Caverns", "Glacite Mineshafts"}) {
            assertKind(ChatVerdict.Kind.HIDE, new Run(c, at(area)).line("You uncovered a treasure chest!"), area);
        }
    }

    @Test
    void aWholeChestAsALineSequence() {
        Run run = new Run(powder(f -> {
        }), HOLLOWS);
        // Der Block: lange Linie, Kopf, Belohnungen, lange Linie
        assertKind(ChatVerdict.Kind.HIDE, run.line(WRAP), "open");
        assertKind(ChatVerdict.Kind.HIDE, run.line("  CHEST LOCKPICKED"), "header");
        assertKind(ChatVerdict.Kind.HIDE, run.line(""), "blank");
        assertKind(ChatVerdict.Kind.HIDE, run.line("  REWARDS"), "rewards");
        assertKind(ChatVerdict.Kind.HIDE, run.line("    Gemstone Powder x537"), "powder under 1000");
        assertKind(ChatVerdict.Kind.HIDE, run.line("    Gold Essence x3"), "essence under 5");
        assertKind(ChatVerdict.Kind.HIDE, run.line("    Wishing Compass x3"), "listed item");
        assertKind(ChatVerdict.Kind.HIDE, run.line("    Goblin Egg"), "plain egg");
        assertKind(ChatVerdict.Kind.HIDE, run.line("    Green Goblin Egg"), "green egg under yellow");
        ChatVerdict yellow = run.line("    Yellow Goblin Egg");
        assertKind(ChatVerdict.Kind.REPLACE, yellow, "yellow egg stays");
        assertEquals("§a+§r §eYellow Goblin Egg", yellow.lines().get(0).text());
        assertKind(ChatVerdict.Kind.HIDE, run.line("    ⸕ Flawed Amber Gemstone x2"), "flawed under fine");
        ChatVerdict fine = run.line("    ⸕ Fine Amber Gemstone");
        assertKind(ChatVerdict.Kind.REPLACE, fine, "fine stays");
        assertEquals("§a+§r §9⸕ Fine Amber Gemstone", fine.lines().get(0).text());
        assertKind(ChatVerdict.Kind.HIDE, run.line(WRAP), "close");
        // Danach ist der Block zu: eine gewoehnliche Zeile bleibt, und eine neue lange Linie oeffnet wieder
        assertKind(ChatVerdict.Kind.SHOW, run.line("Hello there"), "after");
        assertKind(ChatVerdict.Kind.HIDE, run.line(WRAP), "open again");
    }

    @Test
    void whatStaysIsWrittenAsOneShortLine() {
        Run run = new Run(powder(f -> f.powderThreshold = 500), HOLLOWS);
        run.line(WRAP);
        run.line("  LOOT CHEST COLLECTED");
        ChatVerdict powder = run.line("    Gemstone Powder x537");
        assertKind(ChatVerdict.Kind.REPLACE, powder, "powder");
        assertEquals("§a+ §b537§r §dGemstone Powder", powder.lines().get(0).text());
        ChatVerdict mithril = run.line("    Mithril Powder x1,153");
        assertEquals("§a+ §b1,153§r §2Mithril Powder", mithril.lines().get(0).text());
        // Mit Pickonimbus in der Liste der Unwichtigen waere er weg; von Haus aus ist er es nicht
        ChatVerdict pickonimbus = run.line("    Pickonimbus 2000");
        assertEquals("§a+§r §5Pickonimbus 2000", pickonimbus.lines().get(0).text());
        // Eine Zeile, die der Filter nicht kennt, bleibt wie sie ist
        assertNull(PowderMiningFilter.reward("    Some New Thing", new ChatTabCategory().filterTypes));
        assertKind(ChatVerdict.Kind.SHOW, run.line("    Some New Thing"), "unknown reward");
    }

    @Test
    void theThresholdsHaveTheirEnds() {
        ChatFilterTypesCategory f = new ChatFilterTypesCategory();
        f.powderThreshold = 60000;
        assertEquals(ChatVerdict.HIDE, PowderMiningFilter.reward("    Gemstone Powder x59,999", f));
        f.powderThreshold = 0;
        // Null heisst "alles zeigen": die Zeile bleibt, wie sie ist
        assertNull(PowderMiningFilter.reward("    Gemstone Powder x537", f));
        f.powderThreshold = 1000;
        assertEquals(ChatVerdict.HIDE, PowderMiningFilter.reward("    Gemstone Powder", f));
        assertEquals(ChatVerdict.Kind.REPLACE, PowderMiningFilter.reward("    Gemstone Powder x1,000", f).kind());
        f.essenceThreshold = 20;
        assertEquals(ChatVerdict.HIDE, PowderMiningFilter.reward("    Diamond Essence x19", f));
        f.essenceThreshold = 0;
        assertNull(PowderMiningFilter.reward("    Diamond Essence x2", f));
        f.essenceThreshold = 5;
        assertEquals(ChatVerdict.HIDE, PowderMiningFilter.reward("    Diamond Essence", f));
        assertEquals(ChatVerdict.Kind.REPLACE, PowderMiningFilter.reward("    Diamond Essence x5", f).kind());
    }

    @Test
    void theListOfUnimportantItemsDecidesPerItem() {
        ChatFilterTypesCategory f = new ChatFilterTypesCategory();
        // Von Haus aus in der Liste: Seil, Kompass, Barrel, Herz, Saft, Yoggie, Treasurite
        for (String hidden : new String[] {"Ascension Rope", "Wishing Compass", "Oil Barrel", "Jungle Heart", "Sludge Juice", "Yoggie", "Treasurite"}) {
            assertEquals(ChatVerdict.HIDE, PowderMiningFilter.reward("    " + hidden, f), hidden);
        }
        // Nicht in der Liste: Ei, Pickonimbus und die Roboterteile
        for (String kept : new String[] {"Prehistoric Egg", "Pickonimbus 2000", "FTX 3070", "Synthetic Heart", "Control Switch", "Robotron Reflector",
                "Electron Transmitter", "Superlite Motor"}) {
            assertEquals(ChatVerdict.Kind.REPLACE, PowderMiningFilter.reward("    " + kept, f).kind(), kept);
        }
        f.powderRobotParts = true;
        f.powderPrehistoricEgg = true;
        f.powderYoggie = false;
        assertEquals(ChatVerdict.HIDE, PowderMiningFilter.reward("    FTX 3070", f));
        assertEquals(ChatVerdict.HIDE, PowderMiningFilter.reward("    Prehistoric Egg", f));
        assertEquals(ChatVerdict.Kind.REPLACE, PowderMiningFilter.reward("    Yoggie", f).kind());
    }

    @Test
    void goblinEggsByColour() {
        String[] eggs = {"Goblin Egg", "Green Goblin Egg", "Yellow Goblin Egg", "Red Goblin Egg", "Blue Goblin Egg"};
        // je Stellung: welche Eier bleiben (K = bleibt, H = weg)
        check(PowderGoblinEggs.SHOW_ALL, eggs, "KKKKK");
        check(PowderGoblinEggs.HIDE_ALL, eggs, "HHHHH");
        check(PowderGoblinEggs.GREEN_UP, eggs, "HKKKK");
        check(PowderGoblinEggs.YELLOW_UP, eggs, "HHKKK");
        check(PowderGoblinEggs.RED_UP, eggs, "HHHKK");
        check(PowderGoblinEggs.BLUE_ONLY, eggs, "HHHHK");
        assertEquals("§a+§r §3Blue Goblin Egg", PowderMiningFilter.reward("    Blue Goblin Egg", new ChatTabCategory().filterTypes).lines().get(0).text());
        assertEquals("§a+ §b2§r §9Goblin Egg", PowderMiningFilter.reward("    Goblin Egg x2", withEggs(PowderGoblinEggs.SHOW_ALL)).lines().get(0).text());
    }

    private static ChatFilterTypesCategory withEggs(PowderGoblinEggs e) {
        ChatFilterTypesCategory f = new ChatFilterTypesCategory();
        f.powderGoblinEggs = e;
        return f;
    }

    private static void check(PowderGoblinEggs entry, String[] eggs, String expected) {
        for (int i = 0; i < eggs.length; i++) {
            ChatVerdict v = PowderMiningFilter.reward("    " + eggs[i], withEggs(entry));
            boolean kept = v != null && v.kind() == ChatVerdict.Kind.REPLACE;
            assertEquals(expected.charAt(i) == 'K', kept, entry + " / " + eggs[i]);
        }
    }

    @Test
    void gemstonesByQuality() {
        String[] gems = {"❈ Rough Amethyst Gemstone x24", "❈ Flawed Amethyst Gemstone x4", "⸕ Fine Amber Gemstone", "⸕ Flawless Amber Gemstone"};
        check(PowderGemstones.SHOW_ALL, gems, "KKKK");
        check(PowderGemstones.HIDE_ALL, gems, "HHHH");
        check(PowderGemstones.FLAWED_UP, gems, "HKKK");
        check(PowderGemstones.FINE_UP, gems, "HHKK");
        check(PowderGemstones.FLAWLESS_ONLY, gems, "HHHK");
    }

    private static void check(PowderGemstones entry, String[] gems, String expected) {
        for (int i = 0; i < gems.length; i++) {
            ChatFilterTypesCategory f = new ChatFilterTypesCategory();
            f.powderRuby = entry;
            f.powderAmber = entry;
            f.powderAmethyst = entry;
            ChatVerdict v = PowderMiningFilter.reward("    " + gems[i], f);
            boolean kept = v != null && v.kind() == ChatVerdict.Kind.REPLACE;
            assertEquals(expected.charAt(i) == 'K', kept, entry + " / " + gems[i]);
        }
    }

    @Test
    void everyGemSortHasItsOwnChoice() {
        ChatFilterTypesCategory f = new ChatFilterTypesCategory();
        f.powderJade = PowderGemstones.HIDE_ALL;
        assertEquals(ChatVerdict.HIDE, PowderMiningFilter.reward("    ☘ Flawless Jade Gemstone", f));
        // Die anderen Sorten stehen auf "Fine or higher"
        assertEquals(ChatVerdict.Kind.REPLACE, PowderMiningFilter.reward("    ❁ Fine Jasper Gemstone", f).kind());
        assertEquals(ChatVerdict.HIDE, PowderMiningFilter.reward("    ❁ Rough Jasper Gemstone x24", f));
        assertEquals(ChatVerdict.HIDE, PowderMiningFilter.reward("    ❤ Rough Ruby Gemstone x24", f));
        assertEquals(ChatVerdict.HIDE, PowderMiningFilter.reward("    ❤ Flawed Sapphire Gemstone", f));
        assertEquals(ChatVerdict.Kind.REPLACE, PowderMiningFilter.reward("    ❤ Fine Topaz Gemstone", f).kind());
    }

    @Test
    void theGemColoursOfTheShortLine() {
        ChatFilterTypesCategory f = new ChatFilterTypesCategory();
        f.powderRuby = PowderGemstones.SHOW_ALL;
        assertEquals("§a+ §b24§r §f❤ Rough Ruby Gemstone", PowderMiningFilter.reward("    ❤ Rough Ruby Gemstone x24", f).lines().get(0).text());
        assertEquals("§a+§r §a❤ Flawed Ruby Gemstone", PowderMiningFilter.reward("    ❤ Flawed Ruby Gemstone", f).lines().get(0).text());
        assertEquals("§a+§r §5❤ Flawless Ruby Gemstone", PowderMiningFilter.reward("    ❤ Flawless Ruby Gemstone", f).lines().get(0).text());
    }

    @Test
    void everyWrappedSampleOfSkyHanniIsRecognised() {
        // Aus SkyHannis Tests (ohne Farbcodes): jede Belohnungszeile ist bekannt - sie wird weggelassen, gekuerzt oder bleibt, aber nie "unbekannt"
        String[] samples = {"    Green Goblin Egg", "    Goblin Egg", "    Diamond Essence", "    Gold Essence", "    Gold Essence x3", "    Gemstone Powder x537",
                "    Diamond Essence x2", "    Mithril Powder x153", "    Treasurite", "    ⸕ Rough Amber Gemstone x24", "    ❤ Rough Ruby Gemstone x24",
                "    ❈ Rough Amethyst Gemstone x24", "    Yellow Goblin Egg", "    ⸕ Flawed Amber Gemstone", "    Wishing Compass x3",
                "    ⸕ Flawed Amber Gemstone x2"};
        ChatFilterTypesCategory f = new ChatFilterTypesCategory();
        f.powderThreshold = 100;
        f.essenceThreshold = 1;
        for (String s : samples) assertNotNull(PowderMiningFilter.reward(s, f), s);
        for (String header : new String[] {"  CHEST LOCKPICKED", "  LOOT CHEST COLLECTED", "  REWARDS"}) {
            assertTrue(PowderMiningFilter.isBlockHeader(header), header);
        }
    }

    @Test
    void aBlockThatNeverClosesEndsByItself() {
        Run run = new Run(powder(f -> {
        }), HOLLOWS);
        assertKind(ChatVerdict.Kind.HIDE, run.line(WRAP), "open");
        assertKind(ChatVerdict.Kind.HIDE, run.line("  REWARDS"), "header");
        // Elf Sekunden spaeter: Der Block gilt als beendet, die Zeile bleibt
        run.now += 11_000L;
        assertKind(ChatVerdict.Kind.SHOW, run.line("Some chat line"), "after the timeout");
    }

    @Test
    void aBlockThatNeverClosesEndsAfterSixtyLinesEvenIfChatKeepsRunning() {
        Run run = new Run(powder(f -> {
        }), HOLLOWS);
        run.line(WRAP);
        run.line("  REWARDS");
        // Ein lebhafter Chat: Jede Zeile kommt kurz nach der vorigen, der Zeitablauf allein griffe nie. Die Zeilen des Blocks gehen weg,
        // doch nach sechzig ist Schluss: Von da an bleibt auch eine leere Zeile
        for (int i = 0; i < WrapperBlocks.MAX_LINES; i++) assertKind(ChatVerdict.Kind.HIDE, run.line(""), "inside the block, line " + i);
        run.line("");
        assertKind(ChatVerdict.Kind.SHOW, run.line(""), "after the line limit");
        assertKind(ChatVerdict.Kind.SHOW, run.line("A normal line"), "after the line limit");
    }

    @Test
    void aForeignBlockKeepsItsContentAndItsClosingLine() {
        Run run = new Run(powder(f -> {
        }), HOLLOWS);
        // Die obere Linie ist weg (sie war nicht von einer fremden zu unterscheiden), der Inhalt bleibt, und auch die untere
        assertKind(ChatVerdict.Kind.HIDE, run.line(WRAP), "open");
        assertKind(ChatVerdict.Kind.SHOW, run.line("SKYBLOCK LEVEL UP!"), "content");
        assertKind(ChatVerdict.Kind.SHOW, run.line("  You are now level 5"), "content 2");
        assertKind(ChatVerdict.Kind.SHOW, run.line(WRAP), "closing line stays");
    }

    @Test
    void withoutThePowderSwitchAChestBlockIsLeftAlone() {
        Run run = new Run(new ChatTabCategory(), HOLLOWS);
        assertKind(ChatVerdict.Kind.SHOW, run.line(WRAP), "open");
        assertKind(ChatVerdict.Kind.SHOW, run.line("  REWARDS"), "header");
        assertKind(ChatVerdict.Kind.SHOW, run.line("    Gold Essence"), "reward");
        assertKind(ChatVerdict.Kind.SHOW, run.line(WRAP), "close");
    }
}

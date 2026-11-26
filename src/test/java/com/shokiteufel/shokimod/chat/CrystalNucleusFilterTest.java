package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig.ChatFilterTypesCategory;
import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static com.shokiteufel.shokimod.chat.ChatTestSupport.HUB;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.WRAP;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.at;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.types;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Chat Filter Types > Crystal Nucleus: die einzelnen Zeilen, die Zeilen der Bloecke (Kristall gefunden, Lauf beendet) und die Unterscheidung der
 * Bloecke im Klartext. Die Beispielzeilen sind SkyHannis "REGEX-TEST"-Zeilen ohne Farbcodes.
 */
class CrystalNucleusFilterTest {

    private static final ChatPlace HOLLOWS = at("Crystal Hollows");

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

    private static ChatTabCategory nucleus(Consumer<ChatFilterTypesCategory> more) {
        return types(f -> {
            f.nucleusEnabled = true;
            more.accept(f);
        });
    }

    private static void assertKind(ChatVerdict.Kind kind, ChatVerdict v, String line) {
        assertEquals(kind, v.kind(), line);
    }

    private static String replaced(ChatVerdict v) {
        assertEquals(ChatVerdict.Kind.REPLACE, v.kind());
        return v.lines().get(0).text();
    }

    @Test
    void aFoundCrystalIsOneLineWithTheCount() {
        Run run = new Run(nucleus(f -> {
        }), HOLLOWS);
        assertKind(ChatVerdict.Kind.HIDE, run.line(WRAP), "open");
        assertKind(ChatVerdict.Kind.HIDE, run.line("                       ✦ CRYSTAL FOUND (1/5)"), "count");
        assertEquals("§5§l✦ Amethyst Crystal §5found§d! §7(§a1§7/§a5§7)", replaced(run.line("                              Amethyst Crystal")));
        assertKind(ChatVerdict.Kind.HIDE, run.line(""), "blank inside");
        assertKind(ChatVerdict.Kind.HIDE, run.line(WRAP), "close");
        assertKind(ChatVerdict.Kind.SHOW, run.line("Hello"), "after");
    }

    @Test
    void theCrystalNamesOfSkyHanniAreAllRecognised() {
        for (String name : new String[] {"Amethyst Crystal", "Sapphire Crystal", "Amber Crystal", "Topaz Crystal", "Jade Crystal"}) {
            Run run = new Run(nucleus(f -> {
            }), HOLLOWS);
            run.line(WRAP);
            run.line("                       ✦ CRYSTAL FOUND (3/5)");
            assertEquals("§5§l✦ " + name + " §5found§d! §7(§a3§7/§a5§7)", replaced(run.line("                                " + name)));
        }
    }

    @Test
    void aFinishedRunIsOneLine() {
        Run run = new Run(nucleus(f -> {
        }), HOLLOWS);
        assertKind(ChatVerdict.Kind.HIDE, run.line(WRAP), "open");
        assertKind(ChatVerdict.Kind.HIDE, run.line("                         Crystal Nucleus run summary"), "inside");
        assertKind(ChatVerdict.Kind.HIDE, run.line("  Something else"), "inside 2");
        assertEquals("§5Crystal Nucleus Run complete§d!", replaced(run.line("Pick it up near the Nucleus Vault!")));
        assertKind(ChatVerdict.Kind.HIDE, run.line(WRAP), "close");
        assertKind(ChatVerdict.Kind.SHOW, run.line("Hello"), "after");
    }

    @Test
    void theRunEndLineIsShortenedOutsideABlockToo() {
        Run run = new Run(nucleus(f -> {
        }), HOLLOWS);
        assertEquals("§5Crystal Nucleus Run complete§d!", replaced(run.line("Pick it up near the Nucleus Vault!")));
    }

    @Test
    void theTwoBlocksAreToldApartByTheirFirstLine() {
        // Mit nur "Crystal Collected" an bleibt ein Lauf-Block unberuehrt (bis auf die obere Linie)
        Run onlyCrystals = new Run(nucleus(f -> f.nucleusRunCompleted = false), HOLLOWS);
        assertKind(ChatVerdict.Kind.HIDE, onlyCrystals.line(WRAP), "open");
        assertKind(ChatVerdict.Kind.SHOW, onlyCrystals.line("                         Crystal Nucleus run summary"), "run content stays");
        assertKind(ChatVerdict.Kind.SHOW, onlyCrystals.line(WRAP), "closing line stays");
        // Mit nur "Run Completed" an bleibt ein Kristall-Block stehen
        Run onlyRun = new Run(nucleus(f -> f.nucleusCrystalCollected = false), HOLLOWS);
        assertKind(ChatVerdict.Kind.HIDE, onlyRun.line(WRAP), "open");
        assertKind(ChatVerdict.Kind.SHOW, onlyRun.line("                       ✦ CRYSTAL FOUND (2/5)"), "crystal content stays");
    }

    @Test
    void aCrystalBlockAndAChestBlockAtTheSamePlaceAreToldApart() {
        // Beides an, im selben Gebiet: Kristall -> Nucleus, Truhe -> Powder
        ChatTabCategory both = types(f -> {
            f.nucleusEnabled = true;
            f.powderEnabled = true;
        });
        Run run = new Run(both, HOLLOWS);
        run.line(WRAP);
        run.line("");
        assertKind(ChatVerdict.Kind.HIDE, run.line("                       ✦ CRYSTAL FOUND (4/5)"), "crystal");
        assertEquals("§5§l✦ Jade Crystal §5found§d! §7(§a4§7/§a5§7)", replaced(run.line("                              Jade Crystal")));
        run.line(WRAP);
        // jetzt eine Truhe
        assertKind(ChatVerdict.Kind.HIDE, run.line(WRAP), "chest open");
        assertKind(ChatVerdict.Kind.HIDE, run.line("  LOOT CHEST COLLECTED"), "loot chest header");
        assertKind(ChatVerdict.Kind.HIDE, run.line("    Gold Essence"), "essence under 5");
        assertKind(ChatVerdict.Kind.HIDE, run.line(WRAP), "chest close");
    }

    @Test
    void onlyInTheCrystalHollows() {
        ChatTabCategory c = nucleus(f -> {
        });
        assertKind(ChatVerdict.Kind.SHOW, new Run(c, HUB).line(WRAP), "Hub");
        assertKind(ChatVerdict.Kind.SHOW, new Run(c, HUB).line("✦ You placed the Sapphire Crystal!"), "Hub, placed");
        assertKind(ChatVerdict.Kind.SHOW, new Run(c, at("Dwarven Mines")).line("PICK IT UP!"), "Dwarven Mines");
    }

    @Test
    void crystalPlaced() {
        ChatTabCategory c = nucleus(f -> {
        });
        assertEquals("§5§l✦ Sapphire Crystal §5placed§d!", replaced(new Run(c, HOLLOWS).line("✦ You placed the Sapphire Crystal!")));
        assertKind(ChatVerdict.Kind.HIDE, new Run(c, HOLLOWS).line("  Keep exploring the Crystal Hollows to find the rest!"), "keep exploring");
        ChatTabCategory off = nucleus(f -> f.nucleusCrystalPlaced = false);
        assertKind(ChatVerdict.Kind.SHOW, new Run(off, HOLLOWS).line("✦ You placed the Sapphire Crystal!"), "switch off");
    }

    @Test
    void metalDetectorLootThatIsNotATool() {
        ChatTabCategory c = nucleus(f -> {
        });
        assertKind(ChatVerdict.Kind.SHOW, new Run(c, HOLLOWS).line("You found Scavenged Diamond Axe with your Metal Detector!"), "tool");
        assertKind(ChatVerdict.Kind.SHOW, new Run(c, HOLLOWS).line("You found Scavenged Emerald Hammer with your Metal Detector!"), "tool 2");
        assertKind(ChatVerdict.Kind.HIDE, new Run(c, HOLLOWS).line("You found ☘ Flawed Jade Gemstone x2 with your Metal Detector!"), "gem");
        assertKind(ChatVerdict.Kind.HIDE, new Run(c, HOLLOWS).line("PICK IT UP!"), "pick it up");
    }

    @Test
    void goblinGuards() {
        ChatTabCategory c = nucleus(f -> {
        });
        for (String line : new String[] {"[GUARD] Ooblak: THEY'RE STEALING THE CRYSTAL! GET THEM!", "Whew! That was a close one, better get out of here...",
                "The Goblin King's foul stench has dissipated!"}) {
            assertKind(ChatVerdict.Kind.HIDE, new Run(c, HOLLOWS).line(line), line);
        }
        assertKind(ChatVerdict.Kind.SHOW, new Run(nucleus(f -> f.nucleusGoblinGuards = false), HOLLOWS).line("[GUARD] Ooblak: hi"), "switch off");
    }

    @Test
    void theKeepersSayOnlyOneLine() {
        Run run = new Run(nucleus(f -> {
        }), HOLLOWS);
        assertKind(ChatVerdict.Kind.HIDE, run.line("[NPC] Keeper of Gold: Excellent! You have returned the Scavenged Golden Hammer to its rightful place!"), "gold");
        assertKind(ChatVerdict.Kind.HIDE, run.line("[NPC] Keeper of Diamond: Excellent! You have returned the Scavenged Diamond Axe to its rightful place!"), "diamond");
        assertEquals("§e[NPC] §6Keeper of §6Lapis§f: §rAll tools submitted.",
                replaced(run.line("[NPC] Keeper of Lapis: You found all of the items! Behold... the Jade Crystal!")));
    }

    @Test
    void professorRobotAndTheComponentList() {
        Run run = new Run(nucleus(f -> {
        }), HOLLOWS);
        assertEquals("§e[NPC] Professor Robot§f: §9Synthetic Heart§f submitted. 5 components left.",
                replaced(run.line("[NPC] Professor Robot: Thanks for bringing me the Synthetic Heart! Bring me 5 more components to fix the giant!")));
        assertEquals("§e[NPC] Professor Robot§f: §9Electron Transmitter§f submitted. 1 component left.",
                replaced(run.line("[NPC] Professor Robot: Thanks for bringing me the Electron Transmitter! Bring me one more component to fix the giant!")));
        assertEquals("§e[NPC] Professor Robot§f: §rAll components submitted.",
                replaced(run.line("[NPC] Professor Robot: You've brought me all of the components!")));
        assertEquals("§e[NPC] Professor Robot§f: §rAll components submitted.",
                replaced(run.line("[NPC] Professor Robot: Wait a minute. This will work just fine.")));
        assertKind(ChatVerdict.Kind.HIDE, run.line("[NPC] Professor Robot: Something else he says"), "other");

        // Die Liste nach "That's not one of the components I need!": zwei Sekunden lang
        assertKind(ChatVerdict.Kind.HIDE,
                run.line("[NPC] Professor Robot: That's not one of the components I need! Bring me one of the missing components:"), "preamble");
        assertKind(ChatVerdict.Kind.HIDE, run.line("  FTX 3070"), "list 1");
        assertKind(ChatVerdict.Kind.HIDE, run.line("  Control Switch"), "list 2");
        run.now += CrystalNucleusFilter.COMPONENT_LIST_MS;
        assertKind(ChatVerdict.Kind.SHOW, run.line("  A later line with two spaces"), "after two seconds");
    }

    @Test
    void kingYolkar() {
        Run run = new Run(nucleus(f -> {
        }), HOLLOWS);
        assertEquals("§e[NPC] §6King Yolkar§f: ...", replaced(run.line("[NPC] King Yolkar: *rumble* *rumble*")));
        assertEquals("§e[NPC] §6King Yolkar§f: §rBring me a §9Goblin Egg §rof any type.",
                replaced(run.line("[NPC] King Yolkar: Bring me back a Goblin Egg of any type and we can teach her a lesson!")));
        assertEquals("§e[NPC] §6King Yolkar§f: §2King's Scent§r applied.", replaced(run.line("[NPC] King Yolkar: This egg will help me stomach my pain.")));
        assertKind(ChatVerdict.Kind.HIDE, run.line("[NPC] King Yolkar: Anything else"), "other");
    }

    @Test
    void eachNpcFilterIsBehindItsOwnSwitch() {
        ChatTabCategory c = nucleus(f -> {
            f.nucleusKeepers = false;
            f.nucleusProfRobot = false;
            f.nucleusKingYolkar = false;
        });
        for (String line : new String[] {"[NPC] Keeper of Gold: hi", "[NPC] Professor Robot: hi", "[NPC] King Yolkar: hi"}) {
            assertKind(ChatVerdict.Kind.SHOW, new Run(c, HOLLOWS).line(line), line);
        }
    }

    @Test
    void theSwitchInsideTheFoldOutDecidesEverything() {
        ChatTabCategory off = new ChatTabCategory();
        off.filterTypes.nucleusEnabled = false;
        assertKind(ChatVerdict.Kind.SHOW, new Run(off, HOLLOWS).line("[NPC] King Yolkar: *rumble* *rumble*"), "enabled is off");
    }
}

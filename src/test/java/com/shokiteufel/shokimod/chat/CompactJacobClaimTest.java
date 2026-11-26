package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.chat.Chat2Support.Place;
import com.shokiteufel.shokimod.chat.Chat2Support.Run;
import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;

import net.minecraft.network.chat.HoverEvent;

import org.junit.jupiter.api.Test;

import static com.shokiteufel.shokimod.chat.Chat2Support.assertKind;
import static com.shokiteufel.shokimod.chat.Chat2Support.config;
import static com.shokiteufel.shokimod.chat.Chat2Support.normal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat > Compact Jacob Claim: die Beispielzeilen von SkyHanni, eine ganze Folge mit den Bits am Ende, eine ohne (Pause), und die Zeile mit allen Preisen. */
class CompactJacobClaimTest {

    private static final String OPENING = "§6§l  FARMING CONTEST REWARDS CLAIMED";
    private static final String CONTEST = "§e   » Sunflower Contest on Autumn 31st, Year 472";
    private static final String REWARDS = "§a§l  REWARDS";
    private static final String TICKETS = "§9    Jacob's Ticket x271";
    private static final String CARNIVAL = "§9    Carnival Ticket x21";
    private static final String BOOK_SUNFLOWER = "§a    1x Turbo-Sunflower I Book";
    private static final String BOOK_WHEAT = "§a    4x Turbo-Wheat I Book";
    private static final String GOLD = "§6    +8 gold medals";
    private static final String SILVER = "§f    +7 silver medals";
    private static final String BRONZE = "§c    +5 bronze medals";
    private static final String OVERCLOCKER = "§9    Overclocker 3000 x4";
    private static final String BITS = "§b    +2,293 Bits";

    private static Run garden() {
        return new Run(config(c -> c.compactJacobClaim = true), new Place("Garden"));
    }

    @Test
    void aWholeClaimBecomesOneLineWhenTheBitsComeLast() {
        Run run = garden();
        for (String line : new String[] {OPENING, CONTEST, REWARDS, TICKETS, CARNIVAL, BOOK_SUNFLOWER, BOOK_WHEAT, GOLD, SILVER, BRONZE, OVERCLOCKER}) {
            assertKind(ChatVerdict.Kind.HIDE, run.line(line), line);
        }
        ChatVerdict v = run.line(BITS);
        assertKind(ChatVerdict.Kind.REPLACE, v, "the bits are always the last line");
        assertEquals(1, v.lines().size());
        String expected = "§6§lContest Rewards §8§l| §rTickets: §a271 Ja§7, §a21 Ca"
                // Die Buecher stehen nach ihrem Text ohne Farbcodes sortiert ("1 Sf" vor "4 Wh"), nicht in der Reihenfolge der Zeilen
                + " §8§l| §rBooks: §61 Sf§7, §64 Wh"
                + " §8§l| §rMedals: §68§7, §f7§7, §c5"
                + " §8§l| §rBits: §b2293"
                + " §8§l| §rOverclockers: §64";
        assertEquals(normal(expected), v.lines().get(0).legacyText());
    }

    @Test
    void theWholeBlockStandsOnHover() {
        Run run = garden();
        for (String line : new String[] {OPENING, CONTEST, REWARDS, TICKETS}) run.line(line);
        ChatVerdict v = run.line(BITS);
        HoverEvent hover = StyledText.of(v.lines().get(0).component()).firstHover();
        String tip = ((HoverEvent.ShowText) hover).value().getString();
        assertEquals("  FARMING CONTEST REWARDS CLAIMED\n   » Sunflower Contest on Autumn 31st, Year 472\n  REWARDS\n    Jacob's Ticket x271\n    +2,293 Bits", tip);
    }

    @Test
    void withoutBitsTheBlockEndsAfterThreeHundredMilliseconds() {
        Run run = garden();
        run.line(OPENING);
        run.line(REWARDS);
        run.line(TICKETS);
        assertKind(ChatVerdict.Kind.SHOW, run.poll(CompactJacobClaim.QUIET_MS - 1), "299 ms after the last line");
        ChatVerdict v = run.poll(1);
        assertKind(ChatVerdict.Kind.REPLACE, v, "300 ms after the last line");
        assertEquals(normal("§6§lContest Rewards §8§l| §rTickets: §a271 Ja"), v.lines().get(0).legacyText());
        assertKind(ChatVerdict.Kind.SHOW, run.poll(5000), "nothing left");
    }

    @Test
    void aLineInsideTheBlockThatIsNoRewardStaysButKeepsTheBlockOpen() {
        Run run = garden();
        run.line(OPENING);
        run.line(TICKETS);
        assertKind(ChatVerdict.Kind.SHOW, run.line("§7Something else"), "not a reward");
        assertKind(ChatVerdict.Kind.SHOW, run.poll(299), "the timer starts again with that line");
        assertKind(ChatVerdict.Kind.REPLACE, run.poll(1), "and ends 300 ms later");
    }

    @Test
    void rewardLinesOutsideABlockStay() {
        Run run = garden();
        assertKind(ChatVerdict.Kind.SHOW, run.line(TICKETS), "no opening line");
        assertKind(ChatVerdict.Kind.SHOW, run.line(BITS), "bits alone");
    }

    @Test
    void theSampleLinesOfSkyHanniAreRecognised() {
        for (String reward : new String[] {"    Jacob's Ticket x271", "    Carnival Ticket x21", "    Carnival Ticket", "    1x Turbo-Cacti I Book", "    1x Turbo-Pumpkin I Book",
                "    4x Turbo-Wheat I Book", "    6x Turbo-Mushrooms I Book", "    1x Turbo-Warts I Book", "    1x Turbo-Sunflower I Book", "    +8 gold medals",
                "    +7 silver medals", "    +5 bronze medals", "    +1 gold medal", "    Overclocker 3000", "    Overclocker 3000 x4"}) {
            Run run = garden();
            run.line(OPENING);
            assertKind(ChatVerdict.Kind.HIDE, run.line(reward), reward);
        }
        Run run = garden();
        assertKind(ChatVerdict.Kind.HIDE, run.line("  FARMING CONTEST REWARDS CLAIMED"), "opening");
        assertKind(ChatVerdict.Kind.HIDE, run.line("   » Sunflower Contest on Autumn 31st, Year 472"), "contest 1");
        assertKind(ChatVerdict.Kind.HIDE, run.line("   » Melon Slice Contest on Early Spring 17th, Year 473"), "contest 2");
        assertKind(ChatVerdict.Kind.HIDE, run.line("  REWARDS"), "header");
    }

    @Test
    void singularsAndSingleRewardsAreWrittenRight() {
        Run run = garden();
        run.line(OPENING);
        run.line("    Carnival Ticket");
        run.line("    Overclocker 3000");
        run.line("    +1 gold medal");
        run.line("    1x Turbo-Cacti I Book");
        ChatVerdict v = run.poll(CompactJacobClaim.QUIET_MS);
        assertEquals(normal("§6§lContest Rewards §8§l| §rTickets: §a1 Ca §8§l| §rBooks: §a1 Ca §8§l| §rMedals: §61 §8§l| §rOverclocker: §61"), v.lines().get(0).legacyText());
    }

    @Test
    void booksAreSortedByTheirTextWithoutColours() {
        Run run = garden();
        run.line(OPENING);
        run.line("    5x Turbo-Wheat I Book");
        run.line("    5x Turbo-Cacti I Book");
        run.line("    3x Turbo-Pumpkin I Book");
        run.line("    4x Turbo-Warts I Book");
        run.line("    9x Turbo-Rose I Book");
        ChatVerdict v = run.poll(CompactJacobClaim.QUIET_MS);
        // "3 Pu", "4 Ne", dann die beiden Fuenfer: "5 Ca" vor "5 Wh", obwohl der Code von Gold (6) vor dem von Gruen (a) steht; zuletzt "9 Wr"
        assertEquals(normal("§6§lContest Rewards §8§l| §rBooks: §63 Pu§7, §c4 Ne§7, §a5 Ca§7, §65 Wh§7, §c9 Wr"), v.lines().get(0).legacyText());
    }

    @Test
    void anUnknownCropMakesNoReward() {
        Run run = garden();
        run.line(OPENING);
        assertKind(ChatVerdict.Kind.SHOW, run.line("    1x Turbo-Banana I Book"), "unknown crop");
        assertKind(ChatVerdict.Kind.SHOW, run.line("    +1 platinum medal"), "unknown medal");
    }

    @Test
    void twoClaimsInARowAreBothWritten() {
        Run run = garden();
        run.line(OPENING);
        run.line(TICKETS);
        ChatVerdict second = run.line(OPENING);
        assertKind(ChatVerdict.Kind.REPLACE, second, "the first block is written when the second begins");
        assertEquals(normal("§6§lContest Rewards §8§l| §rTickets: §a271 Ja"), second.lines().get(0).legacyText());
        run.line(CARNIVAL);
        ChatVerdict later = run.poll(CompactJacobClaim.QUIET_MS);
        assertEquals(normal("§6§lContest Rewards §8§l| §rTickets: §a21 Ca"), later.lines().get(0).legacyText());
    }

    @Test
    void onlyInTheGardenAndOnlyWhenOn() {
        Run hub = new Run(config(c -> c.compactJacobClaim = true), new Place("Hub"));
        assertKind(ChatVerdict.Kind.SHOW, hub.line(OPENING), "not in the garden");
        Run off = new Run(new ChatTabCategory(), new Place("Garden"));
        assertKind(ChatVerdict.Kind.SHOW, off.line(OPENING), "off");
        Place lobby = new Place("Garden");
        lobby.skyblock = false;
        assertKind(ChatVerdict.Kind.SHOW, new Run(config(c -> c.compactJacobClaim = true), lobby).line(OPENING), "not SkyBlock");
    }

    @Test
    void aNewWorldForgetsTheBlock() {
        Run run = garden();
        run.line(OPENING);
        run.line(TICKETS);
        run.engine.worldChanged(run.config);
        assertKind(ChatVerdict.Kind.SHOW, run.poll(5000), "nothing is written for a block of another world");
        assertTrue(!run.engine.hasPending());
    }
}

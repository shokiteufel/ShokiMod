package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;
import org.junit.jupiter.api.Test;

import static com.shokiteufel.shokimod.chat.ChatTestSupport.DUNGEON;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.HUB;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.WRAP;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.assertChatHides;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.assertChatShows;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.at;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.chat;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.types;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chat: Arachne Hider, Sack Change Hider, Hide Far Deaths, Watchdog, Event Level Up (der Block) und der Weg ins Spiel ({@code ChatFilters.hides}) -
 * die Teile, die sich etwas merken oder vom Ort abhaengen.
 */
class ChatFiltersMiscTest {

    private static final ChatPlace SPIDER = at("Spider's Den");

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

    private static void assertKind(ChatVerdict.Kind kind, ChatVerdict v, String line) {
        assertEquals(kind, v.kind(), line);
    }

    // ---- Arachne ----

    @Test
    void arachneVenomShotGoesEverywhereInTheSpiderDen() {
        String[] venom = {
                "Arachne's Keeper used Venom Shot on you hitting you for 87.7 damage and infecting you with venom.",
                "Arachne used Venom Shot on you hitting you for 58 damage and infecting you with venom.",
                "Arachne's Brood used Venom Shot on you hitting you for 19.8 damage and infecting you with venom."};
        assertChatHides(c -> c.hideArachne = true, SPIDER, venom);
        // auch im Heiligtum
        for (String line : venom) assertChatHides(c -> c.hideArachne = true, SPIDER.inSanctuary(), line);
        // aber nur im Spider's Den
        for (String line : venom) assertChatShows(c -> c.hideArachne = true, HUB, line);
    }

    @Test
    void arachneAnnouncementsGoOutsideTheSanctuary() {
        String[] lines = {
                "☄ littlegremlins placed an Arachne's Calling! Something is awakening! (4/4)",
                "☄ SultanHakeem placed an Arachne Crystal! Something is awakening!",
                "[BOSS] Arachne: The Era of Spiders begins now.",
                "[BOSS] Arachne: Ahhhh...A Calling...",
                " ".repeat(30) + "ARACHNE DOWN!"};
        assertChatHides(c -> c.hideArachne = true, SPIDER, lines);
        for (String line : lines) assertChatShows(c -> c.hideArachne = true, SPIDER.inSanctuary(), line);
        for (String line : lines) assertChatShows(c -> c.hideArachne = true, HUB, line);
    }

    @Test
    void theArachneDownBlockGoesFromLineToLine() {
        Run run = new Run(chat(c -> c.hideArachne = true), SPIDER);
        assertKind(ChatVerdict.Kind.HIDE, run.line(WRAP), "open");
        assertKind(ChatVerdict.Kind.HIDE, run.line(""), "blank");
        assertKind(ChatVerdict.Kind.HIDE, run.line(" ".repeat(30) + "ARACHNE DOWN!"), "down");
        assertKind(ChatVerdict.Kind.HIDE, run.line("   Top damage: Foo"), "ranking");
        assertKind(ChatVerdict.Kind.HIDE, run.line(WRAP), "close");
        assertKind(ChatVerdict.Kind.SHOW, run.line("Hello"), "after");
    }

    @Test
    void inTheSanctuaryTheArachneBlockStays() {
        Run run = new Run(chat(c -> c.hideArachne = true), SPIDER.inSanctuary());
        assertKind(ChatVerdict.Kind.SHOW, run.line(WRAP), "open");
        assertKind(ChatVerdict.Kind.SHOW, run.line(" ".repeat(30) + "ARACHNE DOWN!"), "down");
        assertKind(ChatVerdict.Kind.SHOW, run.line(WRAP), "close");
    }

    // ---- Sack Change Hider ----

    @Test
    void theSackChangeLine() {
        String[] lines = {"[Sacks] +269 items. (Last 5s.)", "[Sacks] +1 item. (Last 5s.)", "[Sacks] +10,144 items, -812,640 items. (Last 10s.)",
                "[Sacks] -3 items. (Last 5s.)"};
        assertChatHides(c -> c.hideSacksChange = true, HUB, lines);
        // Andere Zeilen ueber die Saecke bleiben
        assertChatShows(c -> c.hideSacksChange = true, HUB, "Moved 9 Enchanted Bone from your Sacks to your inventory.", "[Sacks] something else",
                "Foo: [Sacks] +269 items. (Last 5s.)");
    }

    @Test
    void theSackChangeLineCanBeLimitedToTheGarden() {
        String line = "[Sacks] +269 items. (Last 5s.)";
        assertChatHides(c -> {
            c.hideSacksChange = true;
            c.onlyHideSacksChangeOnGarden = true;
        }, at("Garden"), line);
        assertChatShows(c -> {
            c.hideSacksChange = true;
            c.onlyHideSacksChangeOnGarden = true;
        }, HUB, line);
        // Die Beschraenkung allein blendet nichts aus
        assertChatShows(c -> c.onlyHideSacksChangeOnGarden = true, at("Garden"), line);
    }

    // ---- Hide Far Deaths ----

    @Test
    void aDeathOfSomeoneWhoWasNotNearGoes() {
        ChatFilterEngine engine = new ChatFilterEngine();
        ChatTabCategory c = chat(x -> x.hideFarDeaths = true);
        String death = " ☠ ZeroHazel was killed by Ashfang.";
        assertKind(ChatVerdict.Kind.HIDE, engine.decide(death, c, HUB, 100_000L), "never seen");
        // Gesehen vor fuenf Sekunden: bleibt
        engine.playerSeen("ZeroHazel", 100_000L);
        assertKind(ChatVerdict.Kind.SHOW, engine.decide(death, c, HUB, 105_000L), "seen five seconds ago");
        assertKind(ChatVerdict.Kind.SHOW, engine.decide(death, c, HUB, 130_000L), "seen exactly 30 seconds ago");
        // Mehr als dreissig Sekunden her: weg
        assertKind(ChatVerdict.Kind.HIDE, engine.decide(death, c, HUB, 130_001L), "seen 30.001 seconds ago");
        // Ein anderer Spieler war nie da
        assertKind(ChatVerdict.Kind.HIDE, engine.decide(" ☠ Someone burned to death.", c, HUB, 105_000L), "someone else");
    }

    @Test
    void yourOwnDeathAndTheDeathsInDungeonAndKuudraStay() {
        ChatFilterEngine engine = new ChatFilterEngine();
        ChatTabCategory c = chat(x -> x.hideFarDeaths = true);
        assertKind(ChatVerdict.Kind.SHOW, engine.decide(" ☠ You fell into the void.", c, HUB, 1000L), "yourself");
        assertKind(ChatVerdict.Kind.SHOW, engine.decide(" ☠ You were killed by Bladesoul.", c, HUB, 1000L), "yourself 2");
        assertKind(ChatVerdict.Kind.SHOW, engine.decide(" ☠ Foo was killed by Bar.", c, DUNGEON, 1000L), "in a dungeon");
        assertKind(ChatVerdict.Kind.SHOW, engine.decide(" ☠ Foo was killed by Bar.", c, at("Kuudra's Hollow"), 1000L), "in Kuudra");
        assertKind(ChatVerdict.Kind.SHOW, engine.decide("Foo: ☠ Bar was killed by Baz.", c, HUB, 1000L), "player chat");
        assertKind(ChatVerdict.Kind.HIDE, engine.decide(" ☠ Foo was killed by Bar.", c, HUB, 1000L), "elsewhere");
        // Nicht in SkyBlock (Lobby): nichts
        assertKind(ChatVerdict.Kind.SHOW, engine.decide(" ☠ Foo was killed by Bar.", c, ChatPlace.of(false, "Unknown", false), 1000L), "not SkyBlock");
    }

    @Test
    void aNewWorldForgetsWhoWasNear() {
        ChatFilterEngine engine = new ChatFilterEngine();
        ChatTabCategory c = chat(x -> x.hideFarDeaths = true);
        engine.playerSeen("Foo", 1000L);
        assertKind(ChatVerdict.Kind.SHOW, engine.decide(" ☠ Foo was killed by Bar.", c, HUB, 2000L), "seen");
        engine.worldChanged(c);
        assertKind(ChatVerdict.Kind.HIDE, engine.decide(" ☠ Foo was killed by Bar.", c, HUB, 3000L), "forgotten");
    }

    // ---- Watchdog ----

    @Test
    void theWatchdogAnnouncement() {
        Run run = new Run(types(f -> f.watchdog = true), HUB);
        assertKind(ChatVerdict.Kind.SHOW, run.line(""), "the empty line before it stays (no deleting of shown lines)");
        assertKind(ChatVerdict.Kind.HIDE, run.line("[WATCHDOG ANNOUNCEMENT]"), "head");
        assertKind(ChatVerdict.Kind.HIDE, run.line("Watchdog has banned 7,000 players in the last 7 days."), "1");
        assertKind(ChatVerdict.Kind.HIDE, run.line("Staff have banned an additional 2,000 in the last 7 days."), "2");
        assertKind(ChatVerdict.Kind.HIDE, run.line("Anyone caught cheating will be removed."), "3");
        assertKind(ChatVerdict.Kind.HIDE, run.line(""), "the closing empty line");
        assertKind(ChatVerdict.Kind.SHOW, run.line("Hello"), "after");
        assertKind(ChatVerdict.Kind.SHOW, run.line(""), "an empty line afterwards is not part of it");
    }

    @Test
    void theWatchdogEndsEvenWhenEmptyLinesAreHiddenToo() {
        Run run = new Run(types(f -> {
            f.watchdog = true;
            f.empty = true;
        }), HUB);
        run.line("[WATCHDOG ANNOUNCEMENT]");
        run.line("Watchdog has banned 7,000 players in the last 7 days.");
        assertKind(ChatVerdict.Kind.HIDE, run.line(""), "end");
        assertKind(ChatVerdict.Kind.SHOW, run.line("Hello"), "after");
    }

    @Test
    void theWatchdogGivesUpAfterTenLines() {
        Run run = new Run(types(f -> f.watchdog = true), HUB);
        run.line("[WATCHDOG ANNOUNCEMENT]");
        for (int i = 0; i < 9; i++) assertKind(ChatVerdict.Kind.HIDE, run.line("line " + i), "line " + i);
        assertKind(ChatVerdict.Kind.HIDE, run.line("the tenth"), "limit");
        assertKind(ChatVerdict.Kind.SHOW, run.line("Hello"), "after the limit");
    }

    // ---- Event Level Up: der Block ----

    @Test
    void anEventLevelUpBlockLosesItsContentAndItsClosingLine() {
        Run run = new Run(types(f -> f.eventLevelUp = true), HUB);
        // Die obere Linie bleibt: Man kann sie noch nicht von der eines fremden Blocks unterscheiden
        assertKind(ChatVerdict.Kind.SHOW, run.line(WRAP), "open");
        assertKind(ChatVerdict.Kind.HIDE, run.line("           # LEVEL UP! #"), "head");
        assertKind(ChatVerdict.Kind.HIDE, run.line("     You are now Event Level 5!"), "level");
        assertKind(ChatVerdict.Kind.HIDE, run.line("     You earned 5 Event Silver!"), "silver");
        assertKind(ChatVerdict.Kind.HIDE, run.line(WRAP), "close");
        assertKind(ChatVerdict.Kind.SHOW, run.line("Hello"), "after");
    }

    @Test
    void aForeignBlockIsLeftAloneByTheEventSwitch() {
        Run run = new Run(types(f -> f.eventLevelUp = true), HUB);
        assertKind(ChatVerdict.Kind.SHOW, run.line(WRAP), "open");
        assertKind(ChatVerdict.Kind.SHOW, run.line("SKYBLOCK LEVEL UP!"), "content");
        assertKind(ChatVerdict.Kind.SHOW, run.line(WRAP), "close");
    }

    @Test
    void longLinesAreLeftAloneWhenNothingWantsThem() {
        Run run = new Run(new ChatTabCategory(), SPIDER);
        assertKind(ChatVerdict.Kind.SHOW, run.line(WRAP), "off");
        // Auch eine 63 Zeichen lange Linie ist keine
        assertFalse(WrapperBlocks.isWrapper("▬".repeat(63)));
        assertFalse(WrapperBlocks.isWrapper("-".repeat(64)));
        assertTrue(WrapperBlocks.isWrapper(WRAP));
    }

    // ---- der Weg ins Spiel ----

    @Test
    void theWayIntoTheGameCostsNothingWhileEverythingIsOff() {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig.INSTANCE = new ModConfig();
            assertFalse(ChatFilters.hides(""));
            assertFalse(ChatFilters.hides("You earned 2 GEXP from playing SkyBlock!"));
            ModConfig.INSTANCE.chatTab.filterTypes.empty = true;
            assertTrue(ChatFilters.hides(""), "an empty line goes with the switch on");
            assertFalse(ChatFilters.hides("Hello"));
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }
}

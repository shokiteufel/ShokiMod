package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.chat.Chat4Support.Place;
import com.shokiteufel.shokimod.data.ModConfig.MessagePart;
import com.shokiteufel.shokimod.data.ModConfig.PlayerMessagesCategory;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.shokiteufel.shokimod.chat.Chat4Support.format;
import static com.shokiteufel.shokimod.chat.Chat4Support.line;
import static com.shokiteufel.shokimod.chat.Chat4Support.normal;
import static com.shokiteufel.shokimod.chat.Chat4Support.on;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chat (Teil 4) > Player Messages: Die Zeile eines Spielers wird aus ihren Teilen neu gebaut. Jede Zeile steht im Test als Klartext UND mit Farbcodes (in der Normalform:
 * ein Code nur dort, wo sich der Stil aendert; ein Leerzeichen zwischen den Teilen hat keinen Stil und steht deshalb hinter einem {@code §r}).
 */
class PlayerChatFormatterTest {

    private static final Place HUB = new Place("Hub").own("Me");

    private static final String RANKED = "§8[§6290§8] §b[MVP§d+§b] lrg89§f: a";

    private static PlayerMessagesCategory parts(MessagePart... order) {
        return on(c -> c.partsOrder = new ArrayList<>(List.of(order)));
    }

    private static void assertFormat(String in, PlayerMessagesCategory c, String expectedLegacy) {
        assertFormat(in, c, expectedLegacy, HUB);
    }

    private static void assertFormat(String in, PlayerMessagesCategory c, String expectedLegacy, Place place) {
        StyledText out = format(in, c, place);
        assertNotNull(out, "veraendert: " + in);
        assertEquals(StyledText.ofLegacy(expectedLegacy).plain(), out.plain(), "Klartext von: " + in);
        assertEquals(normal(expectedLegacy), out.legacy(), "mit Farbcodes: " + in);
    }

    private static void assertUnchanged(String in, PlayerMessagesCategory c) {
        assertNull(format(in, c, HUB), "bleibt: " + in);
    }

    // ---- die Teile und ihre Reihenfolge ----

    @Test
    void theDefaultOrderOfSkyHanniLeavesAnOrdinaryLineAsItIs() {
        assertUnchanged(RANKED, on(c -> {
        }));
        assertEquals(List.of(MessagePart.SKYBLOCK_LEVEL, MessagePart.PRIVATE_ISLAND_RANK, MessagePart.PRIVATE_ISLAND_GUEST, MessagePart.PLAYER_NAME,
                MessagePart.GUILD_RANK, MessagePart.EMBLEM), new PlayerMessagesCategory().partsOrder);
    }

    @Test
    void thePartsComeInTheOrderOfTheSetting() {
        // Emblem, Name, Stufe - mit einem Leerzeichen dazwischen
        assertFormat("§8[§6290§8] §d♫ §b[MVP§d+§b] lrg89§f: a", parts(MessagePart.EMBLEM, MessagePart.PLAYER_NAME, MessagePart.SKYBLOCK_LEVEL),
                "§d♫§r §b[MVP§d+§b] lrg89§r §8[§6290§8]§f: a");
        // Name vor der Stufe
        assertFormat(RANKED, parts(MessagePart.PLAYER_NAME, MessagePart.SKYBLOCK_LEVEL), "§b[MVP§d+§b] lrg89§r §8[§6290§8]§f: a");
    }

    @Test
    void aPartThatIsNotInTheListIsHidden() {
        // Ohne den Namen: nur die Stufe
        assertFormat(RANKED, parts(MessagePart.SKYBLOCK_LEVEL), "§8[§6290§8]§f: a");
        // Ohne die Stufe: nur der Name
        assertFormat(RANKED, parts(MessagePart.PLAYER_NAME), "§b[MVP§d+§b] lrg89§f: a");
        // Das Emblem fehlt in der Liste: es faellt weg
        assertFormat("§8[§6290§8] §d♫ §b[MVP§d+§b] lrg89§f: a", parts(MessagePart.SKYBLOCK_LEVEL, MessagePart.PLAYER_NAME), "§8[§6290§8]§r §b[MVP§d+§b] lrg89§f: a");
    }

    @Test
    void theEmblemDefaultsToTheEnd() {
        assertFormat("§8[§6290§8] §d♫ §b[MVP§d+§b] lrg89§f: a", on(c -> {
        }), "§8[§6290§8]§r §b[MVP§d+§b] lrg89§r §d♫§f: a");
    }

    // ---- die Schalter ----

    @Test
    void hideLevelBracketsLeavesTheNumber() {
        assertFormat(RANKED, on(c -> c.hideLevelBrackets = true), "§6290§r §b[MVP§d+§b] lrg89§f: a");
    }

    @Test
    void levelColorAsNameColoursTheNameButKeepsTheRank() {
        assertFormat(RANKED, on(c -> c.useLevelColorForName = true), "§8[§6290§8]§r §b[MVP§d+§b] §6lrg89§f: a");
    }

    @Test
    void thePlayerRankHiderTakesTheRankAndMakesTheNameAqua() {
        assertFormat(RANKED, on(c -> c.playerRankHider = true), "§8[§6290§8]§r §blrg89§f: a");
        // Ein Name ohne Rang wird ebenfalls aqua
        assertFormat("§8[§6290§8] §7nea89o§7: hello", on(c -> c.playerRankHider = true), "§8[§6290§8]§r §bnea89o§7: hello");
    }

    @Test
    void levelColorAsNameBeatsTheRankHiderForTheColour() {
        assertFormat(RANKED, on(c -> {
            c.useLevelColorForName = true;
            c.playerRankHider = true;
        }), "§8[§6290§8]§r §6lrg89§f: a");
    }

    @Test
    void anAdminIsLeftAlone() {
        assertUnchanged("§8[§6290§8] §c[ADMIN] Bob§f: a", on(c -> c.playerRankHider = true));
        assertUnchanged("§8[§6290§8] §c[ADMIN] Bob§f: a", on(c -> c.useLevelColorForName = true));
    }

    @Test
    void theYouTubeRankGoesUnlessItIsIgnored() {
        String line = "§8[§6290§8] §c[§fYOUTUBE§c] Bob§f: a";
        assertFormat(line, on(c -> c.playerRankHider = true), "§8[§6290§8]§r §bBob§f: a");
        assertUnchanged(line, on(c -> {
            c.playerRankHider = true;
            c.ignoreYouTube = true;
        }));
    }

    // ---- Chat Filter und Same Chat Color ----

    @Test
    void aMessageWithAWordOfTheBlockListIsGray() {
        PlayerMessagesCategory c = on(x -> x.chatFilter = true);
        assertFormat("§8[§6290§8] §b[MVP§d+§b] lrg89§f: free coins here", c, "§8[§6290§8]§r §b[MVP§d+§b] lrg89§7: free coins here");
        // Ohne den Schalter bleibt sie, wie sie ist; eine harmlose Nachricht auch mit dem Schalter
        assertUnchanged("§8[§6290§8] §b[MVP§d+§b] lrg89§f: free coins here", on(x -> {
        }));
        assertUnchanged("§8[§6290§8] §b[MVP§d+§b] lrg89§f: good morning", c);
    }

    @Test
    void sameChatColorMakesTheMessageWhite() {
        assertFormat("§8[§6290§8] §7nea89o§7: hello", on(c -> c.sameChatColor = true), "§8[§6290§8]§r §7nea89o§7: §fhello");
    }

    @Test
    void aGrayedOutMessageStaysGrayWithSameChatColor() {
        assertFormat("§8[§6290§8] §b[MVP§d+§b] lrg89§f: free coins here", on(c -> {
            c.chatFilter = true;
            c.sameChatColor = true;
        }), "§8[§6290§8]§r §b[MVP§d+§b] lrg89§7: free coins here");
    }

    @Test
    void theChatFilterTouchesOnlyThePublicChat() {
        PlayerMessagesCategory c = on(x -> x.chatFilter = true);
        assertUnchanged("§9Party §8> §b[MVP§d+§b] lrg89§f: free coins here", c);
        assertUnchanged("§2Guild > §b[MVP§d+§b] infave§f: free coins here", c);
    }

    // ---- die anderen Chats ----

    @Test
    void theCoopLine() {
        assertUnchanged("§bCo-op > §7nea89o§f: hallooooo", on(c -> {
        }));
        assertFormat("§bCo-op > §7nea89o§f: hallooooo", on(c -> c.playerRankHider = true), "§bCo-op > §bnea89o§f: hallooooo");
    }

    @Test
    void thePartyLine() {
        assertUnchanged("§9Party §8> §b[MVP§d+§b] lrg89§f: peee", on(c -> {
        }));
        assertFormat("§9Party §8> §b[MVP§d+§b] lrg89§f: peee", on(c -> c.playerRankHider = true), "§9Party §8> §blrg89§f: peee");
    }

    @Test
    void theGuildLineWithTheGuildRankMovedInFrontOfTheName() {
        String line = "§2Guild > §b[MVP§d+§b] infave §e[Em]§f: CEMENT";
        assertUnchanged(line, on(c -> {
        }));
        assertFormat(line, parts(MessagePart.GUILD_RANK, MessagePart.PLAYER_NAME), "§2Guild > §e[Em]§r §b[MVP§d+§b] infave§f: CEMENT");
        // Ohne den Gildenrang in der Liste faellt er weg
        assertFormat(line, parts(MessagePart.PLAYER_NAME), "§2Guild > §b[MVP§d+§b] infave§f: CEMENT");
    }

    @Test
    void aPrivateMessageLosesItsRankAndKeepsTheDirection() {
        assertFormat("§dTo §b[MVP§c+§b] Eisengolem§7: danke", on(c -> {
        }), "§dTo§r §bEisengolem§f: §7danke");
        assertFormat("§dFrom §b[MVP§c+§b] Eisengolem§7: danke", on(c -> {
        }), "§dFrom§r §bEisengolem§f: §7danke");
        assertFormat("§dTo §b[MVP§c+§b] Eisengolem§7: danke", on(c -> c.sameChatColor = true), "§dTo§r §bEisengolem§f: danke");
    }

    @Test
    void theShowLine() {
        String line = "§8[§b209§8] §b[MVP§d+§b] lrg89§f§7 is holding §8[§5Heroic Aspect of the Void§8]";
        assertUnchanged(line, on(c -> {
        }));
        assertFormat(line, parts(MessagePart.PLAYER_NAME, MessagePart.SKYBLOCK_LEVEL),
                "§b[MVP§d+§b] lrg89§r §8[§b209§8]§r §7is holding§r §8[§5Heroic Aspect of the Void§8]");
        assertFormat(line, on(c -> c.hideLevelBrackets = true), "§b209§r §b[MVP§d+§b] lrg89§r §7is holding§r §8[§5Heroic Aspect of the Void§8]");
    }

    // ---- die Privatinsel ----

    @Test
    void theIslandPartsStandWhereTheOrderPutsThem() {
        Place island = new Place("Private Island").own("Me");
        assertFormat("§8[§6100§8] §d♫ §a[✌] §c[Buddy ツ] §b[MVP§d+§b] lrg89§f: hi", on(c -> {
        }), "§8[§6100§8]§r §c[Buddy ツ]§r §a[✌]§r §b[MVP§d+§b] lrg89§r §d♫§f: hi", island);
        // Nur das Gast-Zeichen und der Name
        assertFormat("§8[§6100§8] §d♫ §a[✌] §c[Buddy ツ] §b[MVP§d+§b] lrg89§f: hi", parts(MessagePart.PRIVATE_ISLAND_GUEST, MessagePart.PLAYER_NAME),
                "§a[✌]§r §b[MVP§d+§b] lrg89§f: hi", island);
    }

    // ---- Ironman und Fraktion aus der Tab-Liste ----

    @Test
    void ironmanAndFactionComeFromTheTabList() {
        Place tab = new Place("Crimson Isle").own("Me").tab(Map.of("lrg89", new TabPlayers.Extra(true, TabPlayers.Faction.BARBARIAN)));
        assertFormat(RANKED, parts(MessagePart.SKYBLOCK_LEVEL, MessagePart.PLAYER_NAME, MessagePart.MODE_IRONMAN, MessagePart.CRIMSON_FACTION),
                "§8[§6290§8]§r §b[MVP§d+§b] lrg89§r §7♲§r §c⚒§f: a", tab);
        Place mage = new Place("Crimson Isle").own("Me").tab(Map.of("lrg89", new TabPlayers.Extra(false, TabPlayers.Faction.MAGE)));
        assertFormat(RANKED, parts(MessagePart.SKYBLOCK_LEVEL, MessagePart.PLAYER_NAME, MessagePart.MODE_IRONMAN, MessagePart.CRIMSON_FACTION),
                "§8[§6290§8]§r §b[MVP§d+§b] lrg89§r §5ቾ§f: a", mage);
    }

    @Test
    void withoutAnEntryInTheTabListThoseTwoPartsAreLeftOut() {
        assertNull(format(RANKED, parts(MessagePart.SKYBLOCK_LEVEL, MessagePart.PLAYER_NAME, MessagePart.MODE_IRONMAN, MessagePart.CRIMSON_FACTION), HUB),
                "eine Zeile ohne diese Teile sieht aus wie vorher");
    }

    // ---- Klick und Text beim Zeigen ----

    @Test
    void clickAndHoverOfTheLineStayOnTheParts() {
        ClickEvent click = new ClickEvent.RunCommand("/x");
        HoverEvent hover = new HoverEvent.ShowText(Component.literal("tip"));
        StyledText in = line(RANKED).withEvents(click, hover);
        PlayerChatParser.Parsed p = PlayerChatParser.parse(in, "Me", false);
        StyledText out = PlayerChatFormatter.format(in, p, parts(MessagePart.PLAYER_NAME, MessagePart.SKYBLOCK_LEVEL), HUB);
        assertNotNull(out);
        int withEvents = 0;
        for (StyledText.Run run : out.runs()) {
            // Das Leerzeichen zwischen den Teilen und der Doppelpunkt sind neu und tragen nichts
            if (run.text().equals(" ") || run.text().equals(": ")) continue;
            assertEquals(click, run.style().getClickEvent(), "Klick auf " + run.text());
            assertEquals(hover, run.style().getHoverEvent(), "Hover auf " + run.text());
            withEvents++;
        }
        assertTrue(withEvents >= 5, "Teile mit Klick: " + withEvents);
    }

    // ---- keine Spielerzeile ----

    @Test
    void aLineThatIsNotPlayerChatIsNotTouched() {
        assertNull(format("§aYou found a thing", on(c -> {
        }), HUB));
        assertNull(format("§e[NPC] Sirius§f: The highest bidder was Bob", on(c -> {
        }), HUB));
    }

    // ---- der Rang-Verberger in Zeilen, die kein Spielerchat sind ----

    private static void assertHidden(String in, String expectedLegacy) {
        StyledText out = PlayerChatFormatter.hideRanks(line(in));
        assertNotNull(out, "veraendert: " + in);
        assertEquals(StyledText.ofLegacy(expectedLegacy).plain(), out.plain());
        assertEquals(normal(expectedLegacy), out.legacy());
    }

    @Test
    void theRankHiderTakesTheRankOutOfOtherLines() {
        assertHidden("§aFriend > §b[MVP§c+§b] Bob §ejoined.", "§aFriend > §bBob §ejoined.");
        // Das Leerzeichen hinter dem Namen behaelt seine alte Farbe (gruen) - man sieht es nicht
        assertHidden("§aFriend > §a[VIP] Alice §ejoined.", "§aFriend > §bAlice§a §ejoined.");
        assertHidden("§aFriend > §6[MVP§c++§6] Carl §ejoined.", "§aFriend > §bCarl§6 §ejoined.");
        // Zwei Leerzeichen hinter dem Rang
        assertHidden("§b[MVP§c+§b]  Bob §ejoined.", "§bBob §ejoined.");
        // Mehrere in einer Zeile
        assertHidden("§b[MVP§c+§b] Bob §eand §a[VIP] Alice", "§bBob §eand §bAlice");
    }

    @Test
    void theRankHiderLeavesWhatIsNotARankedName() {
        // Nicht gruen, aqua oder golden
        assertNull(PlayerChatFormatter.hideRanks(line("§c[MVP+] Bob §ejoined.")));
        // Ein Name unter zwei Zeichen
        assertNull(PlayerChatFormatter.hideRanks(line("§b[MVP§c+§b] X §ejoined.")));
        // Kein Rang von Hypixel
        assertNull(PlayerChatFormatter.hideRanks(line("§b[YOUTUBE] Bob")));
        assertNull(PlayerChatFormatter.hideRanks(line("§aYou sold 5 items")));
    }

    @Test
    void theRankHiderKeepsClickAndHoverOfTheRestOfTheLine() {
        ClickEvent click = new ClickEvent.RunCommand("/p accept Bob");
        StyledText in = line("§b[MVP§c+§b] Bob §einvited you").withEvents(click, null);
        StyledText out = PlayerChatFormatter.hideRanks(in);
        assertNotNull(out);
        assertEquals("Bob invited you", out.plain());
        for (StyledText.Run run : out.runs()) assertEquals(click, run.style().getClickEvent(), run.text());
    }

    // ---- die Sperrliste ----

    @Test
    void theBlockListMatchesTheKindsOfLinesItIsMeantFor() {
        for (String bad : new String[] {"Join my discord.gg/abc now", "FREE COINS!!", "selling account, dm me", "guild recruiting, apply now", "giveaway at spawn",
                "come to the giveaways", "buy my account please"}) {
            assertTrue(PlayerChatBlacklist.matches(bad), bad);
        }
        for (String fine : new String[] {"good morning", "anyone want to do a run?", "I like coins", "", "my guild is recruiting someone"}) {
            assertFalse(PlayerChatBlacklist.matches(fine), fine);
        }
    }

    @Test
    void aConsumerHelperKeepsTheTestsReadable() {
        Consumer<PlayerMessagesCategory> nothing = c -> {
        };
        assertTrue(on(nothing).enable);
    }
}

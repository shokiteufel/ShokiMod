package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.chat.PlayerChatParser.Kind;
import com.shokiteufel.shokimod.chat.PlayerChatParser.Parsed;

import org.junit.jupiter.api.Test;

import static com.shokiteufel.shokimod.chat.Chat4Support.line;
import static com.shokiteufel.shokimod.chat.Chat4Support.parse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chat (Teil 4): Die Zeilen eines Spielers werden in ihre Teile zerlegt. Die Beispiele sind die {@code REGEX-TEST}-Zeilen von SkyHanni
 * ({@code PlayerChatManager}), mit den Farbcodes, die dort stehen, und - wo SkyHanni einen Stil statt eines Codes meint - mit Codes dazu.
 */
class PlayerChatParserTest {

    private static String plain(StyledText t) {
        return t == null ? null : t.plain();
    }

    // ---- Chat an alle ----

    @Test
    void theSampleLinesOfTheAllChatAreSplit() {
        Parsed p = parse("[266] ♫ §b[MVP§d+§b] lrg89§f: a");
        assertNotNull(p);
        assertEquals(Kind.ALL, p.kind());
        assertEquals("266", plain(p.level()));
        assertEquals("♫ [MVP+] lrg89", plain(p.author()));
        assertEquals("a", plain(p.message()));
        assertEquals('f', p.chatColour());

        p = parse("[58] §7nea89o§7: haiiiii");
        assertEquals(Kind.ALL, p.kind());
        assertEquals("58", plain(p.level()));
        assertEquals("nea89o", plain(p.author()));
        assertEquals("haiiiii", plain(p.message()));
        assertEquals('7', p.chatColour());

        p = parse("[233] §6✿ §a[VIP] dawnbound§f: thats not mine too");
        assertEquals("✿ [VIP] dawnbound", plain(p.author()));
        assertEquals("thats not mine too", plain(p.message()));

        p = parse("[323] §b[MVP§2+§b] xatarna§f: can somebody get these beggars out of here");
        assertEquals("323", plain(p.level()));
        assertEquals("[MVP+] xatarna", plain(p.author()));
        assertEquals("can somebody get these beggars out of here", plain(p.message()));
    }

    @Test
    void theLevelColourIsTheStyleOfTheDigits() {
        Parsed p = parse("§8[§6290§8] §b[MVP§d+§b] lrg89§f: a");
        assertEquals("290", plain(p.level()));
        assertEquals('6', StyledText.colourCharOf(p.level().styleAt(0)), "die Zahl ist golden, die Klammern grau");
    }

    @Test
    void aLineWithoutAColourBeforeTheColonIsOnlyYourOwn() {
        // SkyHannis Beispiel "[302] ♫ [MVP+] lrg89: problematic": ohne Farbcode vor dem Doppelpunkt
        StyledText l = line("[302] ♫ [MVP+] lrg89: problematic");
        Parsed own = PlayerChatParser.parse(l, "lrg89", false);
        assertNotNull(own);
        assertEquals(Kind.ALL, own.kind());
        assertEquals((char) 0, own.chatColour());
        assertEquals("problematic", plain(own.message()));
        assertNull(PlayerChatParser.parse(l, "someoneelse", false), "von einem anderen ist das keine Spielerzeile");
        assertNull(PlayerChatParser.parse(l, "", false), "ohne bekannten eigenen Namen auch nicht");
    }

    @Test
    void theFailingSamplesAreNotPlayerLines() {
        assertNull(parse("Finding player..."));
        assertNull(parse("From stash: Pufferfish"));
        assertNull(parse("From stash: Wheat"));
        // Ein NPC ist kein Spieler
        assertNull(parse("§e[NPC] Sirius§f: The highest bidder was LOMENJUICE"));
        // Eine andere Farbe vor dem Doppelpunkt: eine Meldung des Servers
        assertNull(parse("§e[Auction] §bBob§e: foo"));
        assertNull(parse(""));
        assertNull(parse("You earned 5 coins!"));
    }

    @Test
    void aSentenceWithHasInAPlayersMessageIsNotAShowLine() {
        // "Bob: has [x]" - der Satz steht in der Nachricht, vor "has" beginnt kein neues Stueck
        Parsed p = parse("[58] §7nea89o§7: has [x]");
        assertNotNull(p);
        assertEquals(Kind.ALL, p.kind());
        assertEquals("has [x]", plain(p.message()));
    }

    // ---- Co-op, Party, Gilde ----

    @Test
    void theCoopLine() {
        Parsed p = parse("§bCo-op > §7nea89o§f: hallooooo");
        assertEquals(Kind.COOP, p.kind());
        assertEquals("nea89o", plain(p.author()));
        assertEquals("hallooooo", plain(p.message()));
        assertNull(p.level());
        // Mit einem anderen Doppelpunkt (nicht weiss) ist es nicht diese Zeile
        assertNull(parse("§bCo-op > §7nea89o§e: hallooooo"));
    }

    @Test
    void thePartyLines() {
        Parsed p = parse("§9Party §8> §b[MVP§d+§b] lrg89§f: peee");
        assertEquals(Kind.PARTY, p.kind());
        assertEquals("[MVP+] lrg89", plain(p.author()));
        assertEquals("peee", plain(p.message()));

        p = parse("§9Party §8> §7nea89o§f: peee");
        assertEquals(Kind.PARTY, p.kind());
        assertEquals("nea89o", plain(p.author()));
    }

    @Test
    void theGuildLinesWithAndWithoutAGuildRank() {
        Parsed p = parse("§2Guild > §b[MVP§d+§b] infave §e[Em]§f: CEMENT DRINKERS INCORPORATED");
        assertEquals(Kind.GUILD, p.kind());
        assertEquals("[MVP+] infave", plain(p.author()));
        assertEquals("[Em]", plain(p.guildRank()));
        assertEquals("CEMENT DRINKERS INCORPORATED", plain(p.message()));

        p = parse("§2Guild > §6⚔ §6[MVP§3++§6] RealBacklight§f: !warp");
        assertEquals("⚔ [MVP++] RealBacklight", plain(p.author()));
        assertNull(p.guildRank());
        assertEquals("!warp", plain(p.message()));

        p = parse("§2Guild > §b[MVP§d+§b] lrg89 §e[Iron]§f: h");
        assertEquals("[MVP+] lrg89", plain(p.author()));
        assertEquals("[Iron]", plain(p.guildRank()));

        p = parse("§2Guild > §b[MVP§c+§b] B2Square1 §3[IRON]§f: §r700 to go");
        assertEquals("[IRON]", plain(p.guildRank()));
        assertEquals("700 to go", plain(p.message()));

        p = parse("§2Guild > §6[MVP§5++§6] Throwpo §3[IRON]§f: §rbat pet clueless");
        assertEquals("[MVP++] Throwpo", plain(p.author()));
        assertEquals("bat pet clueless", plain(p.message()));
    }

    // ---- private Nachrichten ----

    @Test
    void thePrivateMessagesLoseTheirRankInTheAuthor() {
        Parsed p = parse("To nea89o: lol");
        assertEquals(Kind.PRIVATE, p.kind());
        assertTrue(p.outgoing());
        assertEquals("nea89o", plain(p.author()));
        assertEquals("lol", plain(p.message()));

        p = parse("From nea89o: hiii");
        assertFalse(p.outgoing());
        assertEquals("nea89o", plain(p.author()));

        p = parse("To [MVP+] Eisengolem: Boop!");
        assertTrue(p.outgoing());
        assertEquals("Eisengolem", plain(p.author()), "der Rang gehoert nicht zum Namen");
        assertEquals("Boop!", plain(p.message()));

        p = parse("§dFrom §b[MVP§c+§b] Eisengolem§7: Boop!");
        assertFalse(p.outgoing());
        assertEquals("Eisengolem", plain(p.author()));

        p = parse("From [MVP+] Eisengolem: danke");
        assertEquals("danke", plain(p.message()));
    }

    // ---- /show ----

    @Test
    void theShowLines() {
        Parsed p = parse("§b[MVP§c+§b] hannibal2§f§7 is holding §8[§6Heroic Aspect of the Void§8]");
        assertEquals(Kind.SHOW_ITEM, p.kind());
        assertEquals("[MVP+] hannibal2", plain(p.author()));
        assertEquals("is holding", plain(p.action()));
        assertEquals("[Heroic Aspect of the Void]", plain(p.message()));
        assertNull(p.level());

        p = parse("§b[MVP§c+§b] hannibal2§f§7 is holding §8[§7[Lvl 2] §dSpider§8]");
        assertEquals(Kind.SHOW_ITEM, p.kind());
        assertEquals("[[Lvl 2] Spider]", plain(p.message()));

        p = parse("§b[MVP§c+§b] hannibal2§f§7 is friends with a §8[§7[Lvl 200] §8[§6103§8§4✦§8] §6Golden Dragon§8]");
        assertEquals("is friends with a", plain(p.action()));
        assertEquals("[[Lvl 200] [103✦] Golden Dragon]", plain(p.message()));

        p = parse("§b[MVP§c+§b] hannibal2§f§7 is wearing §8[§5Glistening Implosion Belt§8]");
        assertEquals("is wearing", plain(p.action()));

        p = parse("§b[MVP§c+§b] hannibal2§f§7 has §8[§6Heroic Aspect of the Void§8]");
        assertEquals("has", plain(p.action()));

        p = parse("§8[§b209§8] §b[MVP§d+§b] lrg89§f§7 is holding §8[§5Heroic Aspect of the Void§8]");
        assertEquals("209", plain(p.level()));
        assertEquals('b', StyledText.colourCharOf(p.level().styleAt(0)));
        assertEquals("[MVP+] lrg89", plain(p.author()));
    }

    @Test
    void theShowLinesWithAnEmblemAndTheIronmanSign() {
        Parsed p = parse("§8[§2179§8] §r§b[MVP§c+§b] Frogthink§f §7♲§7 is holding §r§8[§dBlessed Melon Dicer 3.0§8]");
        assertEquals(Kind.SHOW_ITEM, p.kind());
        assertEquals("179", plain(p.level()));
        assertEquals("[MVP+] Frogthink", plain(p.author()), "das Zeichen des Ironman-Modus gehoert nicht zum Namen");
        assertEquals("[Blessed Melon Dicer 3.0]", plain(p.message()));

        p = parse("§8[§2164§8] §6§lᛝ §r§7Vinc1x§7§7 is holding §r§8[§dStellar Titanium Drill DR-X655§8]");
        assertEquals(Kind.SHOW_ITEM, p.kind());
        assertEquals("ᛝ Vinc1x", plain(p.author()));
    }

    // ---- die Privatinsel ----

    @Test
    void theGuestSignAndTheIslandRankAreTakenOutOfTheNameOnThePrivateIsland() {
        StyledText l = line("§8[§6100§8] §d♫ §a[✌] §c[Buddy ツ] §b[MVP§d+§b] lrg89§f: hi");
        Parsed p = PlayerChatParser.parse(l, "Me", true);
        assertNotNull(p);
        assertEquals("♫ [MVP+] lrg89", plain(p.author()));
        assertEquals("[✌]", plain(p.privateIslandGuest()));
        assertEquals("[Buddy ツ]", plain(p.privateIslandRank()));
        assertEquals('c', StyledText.colourCharOf(p.privateIslandRank().styleAt(0)));
        // Anderswo ist die Zeile mit zwei Klammern vor dem Namen keine, die die Mod versteht
        assertNull(PlayerChatParser.parse(l, "Me", false));
    }

    @Test
    void anIslandRankThatIsNotOneOfHypixelsIsRecognisedButMvpIsNot() {
        // SkyHannis REGEX-TEST: "♫ §c[Buddy ツ] §b[MVP§d+§b] lrg89" ja; "℻ §b[MVP§5+§b] Alea1337" nein
        Parsed p = PlayerChatParser.parse(line("♫ §c[Buddy ツ] §b[MVP§d+§b] lrg89§f: x"), "Me", true);
        assertEquals("[Buddy ツ]", plain(p.privateIslandRank()));
        assertEquals("♫ [MVP+] lrg89", plain(p.author()));

        p = PlayerChatParser.parse(line("℻ §b[MVP§5+§b] Alea1337§f: x"), "Me", true);
        assertNull(p.privateIslandRank());
        assertNull(p.privateIslandGuest());
        assertEquals("℻ [MVP+] Alea1337", plain(p.author()));

        for (String hypixel : new String[] {"[VIP]", "[VIP+]", "[MVP]", "[MVP++]", "[YOUTUBE]", "[ADMIN]", "[MOD]", "[GM]"}) {
            p = PlayerChatParser.parse(line("§b" + hypixel + " Name§f: x"), "Me", true);
            assertNull(p.privateIslandRank(), hypixel + " ist ein Rang von Hypixel");
        }
    }

    @Test
    void theGuestSignIsOnlyOneWhenItIsGreen() {
        Parsed p = PlayerChatParser.parse(line("§c[✌] §b[MVP§d+§b] lrg89§f: x"), "Me", true);
        assertNull(p.privateIslandGuest(), "nur das gruene [✌] ist der Hinweis");
    }

    // ---- der Vorblick ----

    @Test
    void theCheapLookBeforeThePatternsRun() {
        assertTrue(PlayerChatParser.mayBePlayerChat("[58] nea89o: haiiiii"));
        assertTrue(PlayerChatParser.mayBePlayerChat("[MVP+] hannibal2 is holding [Heroic Aspect of the Void]"));
        assertTrue(PlayerChatParser.mayBePlayerChat("[MVP+] hannibal2 has [Heroic Aspect of the Void]"));
        assertFalse(PlayerChatParser.mayBePlayerChat("You found a Glacite Mineshaft portal!"));
        assertFalse(PlayerChatParser.mayBePlayerChat("It is 10:30 now"), "ein Doppelpunkt ohne Leerzeichen dahinter");
    }

    @Test
    void onlyTheFiveChatKindsAreTranslatable() {
        assertTrue(parse("[58] §7nea89o§7: haiiiii").translatable());
        assertTrue(parse("§bCo-op > §7nea89o§f: hallooooo").translatable());
        assertTrue(parse("§9Party §8> §7nea89o§f: peee").translatable());
        assertTrue(parse("§2Guild > §b[MVP§d+§b] lrg89§f: h").translatable());
        assertTrue(parse("From nea89o: hiii").translatable());
        assertFalse(parse("§b[MVP§c+§b] hannibal2§f§7 is holding §8[§6Heroic Aspect of the Void§8]").translatable());
    }
}

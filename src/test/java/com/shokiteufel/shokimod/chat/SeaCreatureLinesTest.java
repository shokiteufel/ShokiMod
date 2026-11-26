package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.chat.Chat2Support.Place;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;
import com.shokiteufel.shokimod.data.ModConfig.CompactDoubleHookPosition;
import com.shokiteufel.shokimod.data.ModConfig.FishingCategory;
import com.shokiteufel.shokimod.util.SeaCreatures;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static com.shokiteufel.shokimod.chat.Chat2Support.component;
import static com.shokiteufel.shokimod.chat.Chat2Support.normal;
import static com.shokiteufel.shokimod.chat.Chat2Support.plain;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fishing > Shorten Fishing Message und Compact Double Hook (nach SkyHanni, {@code SeaCreatureManager}): die Zeile "It's a Double Hook!" und die Fang-Zeile
 * des Meeresbewohners, mit dem Zeitfenster von 100 Millisekunden, links und rechts, "Shorten" allein, beide und beides aus.
 */
class SeaCreatureLinesTest {

    private static final String DOUBLE_HOOK = "§aIt's a Double Hook! Woot woot!";
    private static final String SQUID = "§fA Squid appeared.";

    private static FishingCategory fishing(Consumer<FishingCategory> on) {
        FishingCategory f = new FishingCategory();
        on.accept(f);
        return f;
    }

    /** Eine Folge von Zeilen durch die Maschine des Chats, mit eigener Uhr */
    private static final class Way {
        final ChatFilterEngine engine = new ChatFilterEngine();
        final ChatTabCategory chat = new ChatTabCategory();
        final FishingCategory fishing;
        final Place place = new Place("Hub");
        long now = 100_000L;

        Way(FishingCategory fishing) {
            this.fishing = fishing;
        }

        /** Eine Zeile (mit Farbcodes), so viele Millisekunden nach der vorigen */
        ChatVerdict line(String legacy, long afterMillis) {
            now += afterMillis;
            return engine.decide(plain(legacy), component(legacy), chat, fishing, place, now);
        }
    }

    private static String shown(ChatVerdict v) {
        assertEquals(ChatVerdict.Kind.REPLACE, v.kind(), "die Zeile wird ersetzt");
        assertEquals(1, v.lines().size());
        return v.lines().get(0).legacyText();
    }

    // ---- die Zeile "It's a Double Hook!" ----

    @Test
    void theDoubleHookLineIsHiddenWithCompactDoubleHook() {
        for (String line : new String[] {DOUBLE_HOOK, "§aIt's a Double Hook!", "It's a Double Hook! Woot woot!", "It's a Double Hook!"}) {
            Way way = new Way(fishing(f -> f.compactDoubleHook = true));
            assertEquals(ChatVerdict.Kind.HIDE, way.line(line, 20).kind(), "ausgeblendet: " + line);
        }
    }

    @Test
    void theDoubleHookLineStaysWithoutTheSwitchAndNothingIsRemembered() {
        // Shorten allein: die Zeile bleibt, und die Fang-Zeile bekommt keinen Zusatz
        Way way = new Way(fishing(f -> f.shortenFishingMessage = true));
        assertEquals(ChatVerdict.Kind.SHOW, way.line(DOUBLE_HOOK, 20).kind());
        assertEquals(normal("§9You caught a §fSquid§9!"), shown(way.line(SQUID, 10)));
        // Alles aus: nichts greift
        Way off = new Way(new FishingCategory());
        assertEquals(ChatVerdict.Kind.SHOW, off.line(DOUBLE_HOOK, 20).kind());
        assertEquals(ChatVerdict.Kind.SHOW, off.line(SQUID, 10).kind());
    }

    @Test
    void otherLinesWithTheSameWordsAreNotTheDoubleHookLine() {
        Way way = new Way(fishing(f -> f.compactDoubleHook = true));
        assertEquals(ChatVerdict.Kind.SHOW, way.line("§7Someone: It's a Double Hook!", 20).kind(), "ein Satz im Chat eines Spielers");
        assertEquals(ChatVerdict.Kind.SHOW, way.line("It's a Double Hook! Woot woot! Really!", 20).kind(), "mit Zusatz ist es nicht die Zeile");
        assertEquals(ChatVerdict.Kind.SHOW, way.line(SQUID, 10).kind(), "nichts gemerkt: die Fang-Zeile bleibt");
    }

    // ---- die Fang-Zeile mit dem Zeitfenster ----

    @Test
    void theCatchLineGetsDoubleHookInFrontWithLeft() {
        Way way = new Way(fishing(f -> f.compactDoubleHook = true));
        way.line(DOUBLE_HOOK, 20);
        assertEquals(normal("§e§lDOUBLE HOOK! §fA Squid appeared."), shown(way.line(SQUID, 30)));
    }

    @Test
    void theCatchLineGetsDoubleHookBehindWithRight() {
        Way way = new Way(fishing(f -> {
            f.compactDoubleHook = true;
            f.compactDoubleHookPosition = CompactDoubleHookPosition.RIGHT;
        }));
        way.line(DOUBLE_HOOK, 20);
        ChatVerdict v = way.line(SQUID, 30);
        assertEquals(normal("§fA Squid appeared.§r §e§lDOUBLE HOOK!"), shown(v));
        StyledText styled = StyledText.of(v.lines().get(0).component());
        assertEquals("A Squid appeared. DOUBLE HOOK!", styled.plain());
        assertTrue(styled.styleAt(styled.plain().indexOf("DOUBLE")).isBold(), "der Zusatz ist fett");
    }

    @Test
    void aMissingPositionCountsAsLeft() {
        Way way = new Way(fishing(f -> {
            f.compactDoubleHook = true;
            f.compactDoubleHookPosition = null;
        }));
        way.line(DOUBLE_HOOK, 20);
        assertEquals(normal("§e§lDOUBLE HOOK! §fA Squid appeared."), shown(way.line(SQUID, 30)));
    }

    @Test
    void theWindowIsAHundredMillisecondsLong() {
        Way exactly = new Way(fishing(f -> f.compactDoubleHook = true));
        exactly.line(DOUBLE_HOOK, 20);
        assertEquals(normal("§e§lDOUBLE HOOK! §fA Squid appeared."), shown(exactly.line(SQUID, 100)), "genau 100 ms: noch dabei");

        Way late = new Way(fishing(f -> f.compactDoubleHook = true));
        late.line(DOUBLE_HOOK, 20);
        assertEquals(ChatVerdict.Kind.SHOW, late.line(SQUID, 101).kind(), "101 ms: zu spaet, die Zeile bleibt, wie sie ist");
    }

    @Test
    void shortenAloneReplacesTheCatchLine() {
        Way way = new Way(fishing(f -> f.shortenFishingMessage = true));
        assertEquals(normal("§9You caught a §fSquid§9!"), shown(way.line(SQUID, 20)));
    }

    @Test
    void shortenAndDoubleHookTogetherLeftAndRight() {
        Way left = new Way(fishing(f -> {
            f.shortenFishingMessage = true;
            f.compactDoubleHook = true;
        }));
        assertEquals(ChatVerdict.Kind.HIDE, left.line(DOUBLE_HOOK, 20).kind());
        assertEquals(normal("§e§lDOUBLE HOOK! §9You caught a §fSquid§9!"), shown(left.line(SQUID, 20)));

        Way right = new Way(fishing(f -> {
            f.shortenFishingMessage = true;
            f.compactDoubleHook = true;
            f.compactDoubleHookPosition = CompactDoubleHookPosition.RIGHT;
        }));
        right.line(DOUBLE_HOOK, 20);
        assertEquals(normal("§9You caught a §fSquid§9!§r §e§lDOUBLE HOOK!"), shown(right.line(SQUID, 20)));
    }

    @Test
    void theNameKeepsTheColourOfTheCreatureAndTheArticleFollowsTheLetter() {
        Way way = new Way(fishing(f -> f.shortenFishingMessage = true));
        assertEquals(normal("§9You caught an §aInkling§9!"), shown(way.line("§eYou get an inkling that you've caught... an Inkling!", 20)));
        assertEquals(normal("§9You caught a §6Yeti§9!"), shown(way.line("§fWhat is this creature!?", 20)));
        assertEquals(normal("§9You caught §6The Loch Emperor§9!"), shown(way.line("The Loch Emperor arises from the depths.", 20)),
                "ein Name mit The davor bekommt keinen Artikel");
    }

    @Test
    void theArticleIsAOrAn() {
        assertEquals("a ", SeaCreatureLines.articleOf("Squid"));
        assertEquals("an ", SeaCreatureLines.articleOf("Inkling"));
        assertEquals("an ", SeaCreatureLines.articleOf("Agarimoo"));
        assertEquals("an ", SeaCreatureLines.articleOf("Oasis Rabbit"));
        assertEquals("an ", SeaCreatureLines.articleOf("Ent"));
        assertEquals("a ", SeaCreatureLines.articleOf("Yeti"));
        assertEquals("a ", SeaCreatureLines.articleOf("gorF"));
        assertEquals("an ", SeaCreatureLines.articleOf("Abyssal Miner"));
        assertEquals("", SeaCreatureLines.articleOf("The Loch Emperor"));
        assertEquals("", SeaCreatureLines.articleOf(""));
        assertEquals("", SeaCreatureLines.articleOf(null));
    }

    @Test
    void everyCreatureColourIsOneOfTheSixteenColours() {
        for (SeaCreatures.Creature c : SeaCreatures.all()) {
            String code = SeaCreatureLines.legacyCodeOf(c.colour());
            assertNotNull(code, c.name() + " hat eine Farbe, die kein Farbcode ist: " + Integer.toHexString(c.colour()));
            assertEquals(2, code.length());
        }
        assertNull(SeaCreatureLines.legacyCodeOf(0x123456));
        assertEquals("§6", SeaCreatureLines.legacyCodeOf(0xFFAA00));
    }

    // ---- das Gedaechtnis ----

    @Test
    void theTimeIsForgottenAfterACatchLineButNotAfterAnotherLine() {
        Way way = new Way(fishing(f -> f.compactDoubleHook = true));
        way.line(DOUBLE_HOOK, 20);
        // Eine Zeile dazwischen, die kein Fang ist (zum Beispiel die einer Auto-Pet-Regel): der Zeitpunkt bleibt
        assertEquals(ChatVerdict.Kind.SHOW, way.line("§aAutopet equipped your §7[Lvl 100] Squid§a! §c§lVIEW RULE", 10).kind());
        assertEquals(normal("§e§lDOUBLE HOOK! §fA Squid appeared."), shown(way.line(SQUID, 10)));
        // Danach ist er vergessen: der naechste Fang kommt ohne Zusatz
        assertEquals(ChatVerdict.Kind.SHOW, way.line(SQUID, 10).kind(), "ohne eigene Double-Hook-Zeile kein Zusatz");
    }

    @Test
    void aNewWorldForgetsTheDoubleHook() {
        Way way = new Way(fishing(f -> f.compactDoubleHook = true));
        way.line(DOUBLE_HOOK, 20);
        way.engine.worldChanged(way.chat);
        assertEquals(ChatVerdict.Kind.SHOW, way.line(SQUID, 10).kind());
    }

    @Test
    void theCocoonLineAndPartyLinesAreNoCatchLine() {
        Way way = new Way(fishing(f -> {
            f.shortenFishingMessage = true;
            f.compactDoubleHook = true;
        }));
        way.line(DOUBLE_HOOK, 20);
        assertEquals(ChatVerdict.Kind.SHOW, way.line("§aCAUGHT! You cocooned a Yeti!", 10).kind());
        assertEquals(ChatVerdict.Kind.SHOW, way.line("§9Party §8> §b[MVP+] Someone§f: A Squid appeared.", 10).kind());
        assertEquals(ChatVerdict.Kind.SHOW, way.line("Hello there", 10).kind());
        // Der Zeitpunkt ist noch da
        assertEquals(normal("§e§lDOUBLE HOOK! §9You caught a §fSquid§9!"), shown(way.line(SQUID, 10)));
    }

    // ---- der Ort und die alten Wege ----

    @Test
    void outsideSkyBlockNothingHappens() {
        Way way = new Way(fishing(f -> {
            f.shortenFishingMessage = true;
            f.compactDoubleHook = true;
        }));
        way.place.skyblock = false;
        assertEquals(ChatVerdict.Kind.SHOW, way.line(DOUBLE_HOOK, 20).kind());
        assertEquals(ChatVerdict.Kind.SHOW, way.line(SQUID, 10).kind());
    }

    @Test
    void theOldCallWithoutFishingSettingsLeavesTheLinesAlone() {
        ChatFilterEngine engine = new ChatFilterEngine();
        ChatTabCategory chat = new ChatTabCategory();
        Place place = new Place("Hub");
        assertEquals(ChatVerdict.Kind.SHOW, engine.decide(plain(DOUBLE_HOOK), component(DOUBLE_HOOK), chat, place, 1000L).kind());
        assertEquals(ChatVerdict.Kind.SHOW, engine.decide(plain(SQUID), component(SQUID), chat, place, 1010L).kind());
        assertEquals(ChatVerdict.Kind.SHOW, engine.decide(plain(SQUID), null, chat, place, 1020L).kind());
    }

    @Test
    void theChatSwitchesStillWorkNextToTheFishingLines() {
        Way way = new Way(fishing(f -> f.compactDoubleHook = true));
        way.chat.shortenCoinAmounts = true;
        ChatVerdict v = way.line("§aYou earned §650,000 coins§a!", 20);
        assertEquals(normal("§aYou earned §650k coins§a!"), shown(v));
        way.chat.filterTypes.empty = true;
        assertEquals(ChatVerdict.Kind.HIDE, way.line("", 20).kind());
    }

    @Test
    void withoutAComponentTheLineHasNoStyleButStillWorks() {
        Way way = new Way(fishing(f -> f.shortenFishingMessage = true));
        ChatVerdict v = way.engine.decide("A Squid appeared.", null, way.chat, way.fishing, way.place, 5000L);
        assertEquals(normal("§9You caught a §fSquid§9!"), shown(v));
    }

    // ---- der Weg ins Spiel (ChatFilters.hides) ----

    private static void with(Consumer<ModConfig> change, Runnable then) {
        ModConfig saved = ModConfig.INSTANCE;
        String savedType = GameState.Server.gametype;
        try {
            ModConfig.INSTANCE = new ModConfig();
            GameState.Server.gametype = com.shokiteufel.shokimod.data.ModConstants.GAME_TYPE_SKYBLOCK;
            change.accept(ModConfig.INSTANCE);
            then.run();
        } finally {
            ModConfig.INSTANCE = saved;
            GameState.Server.gametype = savedType;
        }
    }

    /**
     * Nur die Zeile, die ausgeblendet wird (HIDE schreibt nichts in den Chat): Eine ersetzte Zeile schriebe ueber das Spiel, das es hier nicht gibt - die Urteile dazu
     * pruefen die Tests oben.
     */
    @Test
    void theWayIntoTheGameHidesTheDoubleHookLineOnlyWithTheSwitchInSkyBlock() {
        Component line = Component.literal("It's a Double Hook! Woot woot!");
        with(c -> {
        }, () -> assertFalse(ChatFilters.hides("It's a Double Hook! Woot woot!", line), "alles aus: die Zeile bleibt"));
        with(c -> c.fishing.shortenFishingMessage = true, () -> assertFalse(ChatFilters.hides("It's a Double Hook! Woot woot!", line), "Shorten allein blendet sie nicht aus"));
        with(c -> c.fishing.compactDoubleHook = true, () -> {
            ChatFilters.worldChanged();
            assertTrue(ChatFilters.hides("It's a Double Hook! Woot woot!", line));
            ChatFilters.worldChanged();
        });
        with(c -> c.fishing.compactDoubleHook = true, () -> {
            GameState.Server.gametype = "Unknown";
            assertFalse(ChatFilters.hides("It's a Double Hook! Woot woot!", line), "ausserhalb von SkyBlock bleibt sie");
        });
    }
}

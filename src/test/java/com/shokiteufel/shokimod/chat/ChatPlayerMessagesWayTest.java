package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;
import com.shokiteufel.shokimod.data.ModConfig.MessagePart;

import net.minecraft.network.chat.ClickEvent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.shokiteufel.shokimod.chat.Chat4Support.line;
import static com.shokiteufel.shokimod.chat.Chat4Support.normal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chat (Teil 4): Player Messages und Translator im Chat-Weg ({@link ChatFilterEngine#decide}): wann eine Zeile ersetzt wird, wann sie bleibt, und was der Klick traegt.
 */
class ChatPlayerMessagesWayTest {

    private static final ChatPlace SKYBLOCK = ChatPlace.of(true, "Hub", false);
    private static final String RANKED = "§8[§6290§8] §b[MVP§d+§b] lrg89§f: a";

    @BeforeEach
    void before() {
        TranslateLines.clear();
    }

    @AfterEach
    void after() {
        TranslateLines.clear();
    }

    private static ChatTabCategory config(Consumer<ChatTabCategory> on) {
        return ChatTestSupport.chat(on);
    }

    private static ChatVerdict decide(StyledText line, ChatTabCategory c, ChatPlace place) {
        return new ChatFilterEngine().decide(line.plain(), line.toComponent(), c, place, 1000L);
    }

    private static ChatVerdict decide(String legacy, ChatTabCategory c, ChatPlace place) {
        return decide(line(legacy), c, place);
    }

    private static StyledText replaced(ChatVerdict v) {
        assertEquals(ChatVerdict.Kind.REPLACE, v.kind());
        assertEquals(1, v.lines().size());
        return StyledText.of(v.lines().get(0).component());
    }

    private static void reversed(ChatTabCategory c) {
        c.playerMessages.enable = true;
        c.playerMessages.partsOrder = new ArrayList<>(List.of(MessagePart.PLAYER_NAME, MessagePart.SKYBLOCK_LEVEL));
    }

    @Test
    void everythingIsOffFromTheStart() {
        for (String l : new String[] {RANKED, "§bCo-op > §7nea89o§f: hallooooo", "§dFrom §b[MVP§c+§b] Eisengolem§7: danke",
                "§aFriend > §b[MVP§c+§b] Bob §ejoined."}) {
            assertEquals(ChatVerdict.Kind.SHOW, decide(l, new ChatTabCategory(), SKYBLOCK).kind(), l);
        }
    }

    @Test
    void theLineIsRewrittenInSkyBlockOnly() {
        ChatTabCategory c = config(ChatPlayerMessagesWayTest::reversed);
        assertEquals(normal("§b[MVP§d+§b] lrg89§r §8[§6290§8]§f: a"), replaced(decide(RANKED, c, SKYBLOCK)).legacy());
        assertEquals(ChatVerdict.Kind.SHOW, decide(RANKED, c, ChatPlace.of(false, "Hub", false)).kind(), "ausserhalb von SkyBlock bleibt jede Zeile");
    }

    @Test
    void aLineThatLooksTheSameStays() {
        ChatTabCategory c = config(x -> x.playerMessages.enable = true);
        assertEquals(ChatVerdict.Kind.SHOW, decide(RANKED, c, SKYBLOCK).kind(), "die Vorgabe von SkyHanni laesst eine gewoehnliche Zeile, wie sie ist");
    }

    @Test
    void theOwnLineWithoutAColourBeforeTheColonIsRecognisedByTheName() {
        ChatPlace me = new Chat4Support.Place("Hub").own("lrg89");
        ChatTabCategory c = config(ChatPlayerMessagesWayTest::reversed);
        StyledText out = replaced(decide("§8[§6290§8] §b[MVP§d+§b] lrg89§r: hi", c, me));
        assertEquals("[MVP+] lrg89 [290]: hi", out.plain());
        // Wer der Spieler nicht ist, dessen Zeile ohne Farbcode ist keine Spielerzeile
        assertEquals(ChatVerdict.Kind.SHOW, decide("§8[§6290§8] §b[MVP§d+§b] lrg89§r: hi", c, new Chat4Support.Place("Hub").own("other")).kind());
    }

    // ---- der Rang-Verberger ----

    @Test
    void theRankHiderAloneChangesOtherLinesButNotPlayerChat() {
        ChatTabCategory c = config(x -> x.playerMessages.playerRankHider = true);
        assertEquals(normal("§aFriend > §bBob §ejoined."), replaced(decide("§aFriend > §b[MVP§c+§b] Bob §ejoined.", c, SKYBLOCK)).legacy());
        assertEquals(ChatVerdict.Kind.SHOW, decide(RANKED, c, SKYBLOCK).kind(), "eine Spielerzeile fasst er nur mit Enable Chat Formatting an");
        assertEquals(ChatVerdict.Kind.SHOW, decide("§9Party §8> §b[MVP§d+§b] lrg89§f: peee", c, SKYBLOCK).kind());
    }

    @Test
    void withTheFormattingOnTheRankHiderWorksOnPlayerChatToo() {
        ChatTabCategory c = config(x -> {
            x.playerMessages.enable = true;
            x.playerMessages.playerRankHider = true;
        });
        assertEquals(normal("§8[§6290§8]§r §blrg89§f: a"), replaced(decide(RANKED, c, SKYBLOCK)).legacy());
    }

    // ---- der Klick zum Uebersetzen ----

    private static int numberIn(StyledText line) {
        ClickEvent click = line.firstClick();
        assertInstanceOf(ClickEvent.RunCommand.class, click);
        String command = ((ClickEvent.RunCommand) click).command();
        assertTrue(command.startsWith("/shoki translateline "), command);
        return Integer.parseInt(command.substring("/shoki translateline ".length()));
    }

    @Test
    void aPlayerLineGetsTheClickAndTheTextIsRemembered() {
        ChatTabCategory c = config(x -> x.translator.translateOnClick = true);
        StyledText out = replaced(decide("[58] §7nea89o§7: haiiiii", c, SKYBLOCK));
        assertEquals("[58] nea89o: haiiiii", out.plain(), "der Text der Zeile bleibt");
        assertEquals("haiiiii", TranslateLines.get(numberIn(out)), "gemerkt wird nur die Nachricht");
    }

    @Test
    void everyKindOfPlayerChatGetsTheClickExceptShow() {
        ChatTabCategory c = config(x -> x.translator.translateOnClick = true);
        for (String l : new String[] {RANKED, "§bCo-op > §7nea89o§f: hallooooo", "§9Party §8> §b[MVP§d+§b] lrg89§f: peee",
                "§2Guild > §b[MVP§d+§b] infave §e[Em]§f: CEMENT", "§dFrom §b[MVP§c+§b] Eisengolem§7: danke"}) {
            StyledText out = replaced(decide(l, c, SKYBLOCK));
            assertNotNull(out.firstClick(), l);
        }
        assertEquals(ChatVerdict.Kind.SHOW,
                decide("§b[MVP§c+§b] hannibal2§f§7 is holding §8[§6Heroic Aspect of the Void§8]", c, SKYBLOCK).kind(), "eine /show-Zeile wird nicht uebersetzt");
    }

    @Test
    void aLineThatIsNoPlayerChatGetsNoClick() {
        ChatTabCategory c = config(x -> x.translator.translateOnClick = true);
        assertEquals(ChatVerdict.Kind.SHOW, decide("§aYou found a thing", c, SKYBLOCK).kind());
        assertEquals(ChatVerdict.Kind.SHOW, decide("§e[NPC] Sirius§f: The highest bidder was Bob", c, SKYBLOCK).kind());
    }

    @Test
    void aLineWithAClickOfItsOwnIsNotTouched() {
        ChatTabCategory c = config(x -> x.translator.translateOnClick = true);
        StyledText mine = line(RANKED).withEvents(new ClickEvent.RunCommand("/other"), null);
        assertEquals(ChatVerdict.Kind.SHOW, decide(mine, c, SKYBLOCK).kind());
        // und nichts wurde gemerkt
        assertNull(TranslateLines.get(0));
    }

    @Test
    void theClickComesOnTheReformattedLine() {
        ChatTabCategory c = config(x -> {
            reversed(x);
            x.translator.translateOnClick = true;
        });
        StyledText out = replaced(decide(RANKED, c, SKYBLOCK));
        assertEquals("[MVP+] lrg89 [290]: a", out.plain());
        assertEquals("a", TranslateLines.get(numberIn(out)));
    }

    @Test
    void theSwitchesKeepTheirOwnEffects() {
        // Der Klick allein aendert den Text nicht; das Umbauen allein haengt keinen Klick an
        StyledText clickOnly = replaced(decide(RANKED, config(x -> x.translator.translateOnClick = true), SKYBLOCK));
        assertEquals(line(RANKED).plain(), clickOnly.plain());
        StyledText formatOnly = replaced(decide(RANKED, config(ChatPlayerMessagesWayTest::reversed), SKYBLOCK));
        assertNull(formatOnly.firstClick());
        assertFalse(formatOnly.plain().equals(line(RANKED).plain()));
    }

    // ---- die Schalter melden sich an ----

    @Test
    void theSwitchesTurnOnTheRewritePath() {
        assertFalse(new ChatTabCategory().anyRewrite());
        assertTrue(config(x -> x.playerMessages.enable = true).anyRewrite());
        assertTrue(config(x -> x.playerMessages.playerRankHider = true).anyRewrite());
        assertTrue(config(x -> x.translator.translateOnClick = true).anyRewrite());
        // Die Schalter allein, ohne Hauptschalter, tun nichts
        assertFalse(config(x -> x.playerMessages.sameChatColor = true).anyRewrite());
        assertFalse(config(x -> x.playerMessages.chatFilter = true).anyRewrite());
        assertFalse(config(x -> x.playerMessages.hideLevelBrackets = true).anyRewrite());
    }
}

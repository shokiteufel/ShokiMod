package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.chat.Chat2Support.Place;
import com.shokiteufel.shokimod.chat.Chat2Support.Run;
import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;

import org.junit.jupiter.api.Test;

import static com.shokiteufel.shokimod.chat.Chat2Support.assertKind;
import static com.shokiteufel.shokimod.chat.Chat2Support.config;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Chat > Compact Potion Messages: die sechs Meldungen, die kurze Zeile mit den Farben von Wirkung und Name, der Klick, und was nicht trifft. */
class CompactPotionMessagesTest {

    private static final String TAIL = "§f! Press TAB or type /effects to view your active effects!";

    private static final String SPLASHED_BY = "§a§lBUFF! §fYou were splashed by §b[MVP§c+§b] Empa §fwith §r§aSpeed IV§r" + TAIL;
    private static final String GAINED = "§a§lBUFF! §fYou have gained §r§9Haste III§r" + TAIL;
    private static final String YOURSELF = "§a§lBUFF! §fYou splashed yourself with §r§aSpeed IV§r" + TAIL;

    private static Run on() {
        return new Run(config(c -> c.compactPotion.enabled = true), new Place("Hub"));
    }

    @Test
    void aSplashByAPlayerNamesTheEffectAndThePlayerInTheirColours() {
        ChatVerdict v = on().line(SPLASHED_BY);
        assertKind(ChatVerdict.Kind.REPLACE, v, "splashed by");
        ChatVerdict.Line line = v.lines().get(0);
        assertEquals("§a§lPotion Effect! §r§aSpeed IV §aby §b[MVP§c+§b] Empa", line.text());
        assertEquals("/effects", line.command());
        assertEquals("§eClick to view your potion effects.", line.hover());
    }

    @Test
    void gainingAndSplashingYourselfNameOnlyTheEffect() {
        assertEquals("§a§lPotion Effect! §r§9Haste III", on().line(GAINED).lines().get(0).text());
        assertEquals("§a§lPotion Effect! §r§aSpeed IV", on().line(YOURSELF).lines().get(0).text());
    }

    @Test
    void thePoisonedCandyHasItsOwnShorterMessages() {
        assertEquals("§a§lPotion Effect! §r§2Poisoned Candy I", on().line("§a§lBUFF! §fYou have gained §r§2Poisoned Candy I§r§f!").lines().get(0).text());
        assertEquals("§a§lPotion Effect! §r§2Poisoned Candy I",
                on().line("§a§lBUFF! §fYou splashed yourself with §r§2Poisoned Candy I§r§f!").lines().get(0).text());
        assertEquals("§a§lPotion Effect! §r§2Poisoned Candy I §aby §bNeo",
                on().line("§a§lBUFF! §fYou were splashed by §bNeo §fwith §r§2Poisoned Candy I§r§f!").lines().get(0).text());
    }

    @Test
    void theClickIsOptional() {
        Run run = new Run(config(c -> {
            c.compactPotion.enabled = true;
            c.compactPotion.clickableChatMessage = false;
        }), new Place("Hub"));
        ChatVerdict.Line line = run.line(GAINED).lines().get(0);
        assertEquals("§a§lPotion Effect! §r§9Haste III", line.text());
        assertNull(line.command());
        assertNull(line.hover());
    }

    @Test
    void anythingElseStays() {
        Run run = on();
        assertKind(ChatVerdict.Kind.SHOW, run.line("§a§lBUFF! §fYou have gained §r§9Haste III§r§f."), "no ending");
        assertKind(ChatVerdict.Kind.SHOW, run.line("§aBUFF! You can't do that"), "other buff line");
        assertKind(ChatVerdict.Kind.SHOW, run.line("§7Hello BUFF! You have gained X! Press TAB or type /effects to view your active effects!"), "not at the start");
        assertKind(ChatVerdict.Kind.SHOW, run.line("§fHello"), "chat");
        assertKind(ChatVerdict.Kind.SHOW, run.line("§a§lBUFF! §fYou have gained §r§9Haste III§r§f! Press TAB twice!"), "wrong tail");
    }

    @Test
    void offByDefaultAndOnlyInSkyBlock() {
        Run off = new Run(new ChatTabCategory(), new Place("Hub"));
        assertKind(ChatVerdict.Kind.SHOW, off.line(SPLASHED_BY), "off");
        Place lobby = new Place("Hub");
        lobby.skyblock = false;
        Run elsewhere = new Run(config(c -> c.compactPotion.enabled = true), lobby);
        assertKind(ChatVerdict.Kind.SHOW, elsewhere.line(SPLASHED_BY), "not SkyBlock");
    }

    @Test
    void theLineWorksWithoutAComponentToo() {
        // Ohne Stile (der alte Aufruf ohne Komponente) bleibt der Text, nur ohne Farben
        ChatVerdict v = new ChatFilterEngine().decide(Chat2Support.plain(GAINED), config(c -> c.compactPotion.enabled = true), new Place("Hub"), 1000L);
        assertEquals("§a§lPotion Effect! §rHaste III", v.lines().get(0).text());
    }
}

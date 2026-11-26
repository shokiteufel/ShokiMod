package com.shokiteufel.shokimod.chat;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static com.shokiteufel.shokimod.chat.Chat4Support.line;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chat (Teil 4) > Translator: der Klick auf einer Zeile, die Nummern der gemerkten Zeilen und die Zeilen, die die Mod als Antwort schreibt. Das Senden ans Netz ist hier nicht
 * zu pruefen (und wird nicht aufgerufen).
 */
class TranslateOnClickTest {

    @BeforeEach
    void cleanBefore() {
        TranslateLines.clear();
    }

    @AfterEach
    void cleanAfter() {
        TranslateLines.clear();
    }

    // ---- die Nummern ----

    @Test
    void aRememberedLineIsFoundByItsNumber() {
        int a = TranslateLines.remember("Hallo Welt");
        int b = TranslateLines.remember("Wie geht's?");
        assertTrue(b > a);
        assertEquals("Hallo Welt", TranslateLines.get(a));
        assertEquals("Wie geht's?", TranslateLines.get(b));
    }

    @Test
    void anUnknownNumberIsNothing() {
        assertNull(TranslateLines.get(0));
        assertNull(TranslateLines.get(-1));
        int a = TranslateLines.remember("x");
        assertNull(TranslateLines.get(a + 1));
    }

    @Test
    void theHundredAndFirstLineMakesTheFirstForgotten() {
        int first = TranslateLines.remember("line 0");
        for (int i = 1; i < TranslateLines.CAPACITY; i++) TranslateLines.remember("line " + i);
        assertEquals("line 0", TranslateLines.get(first), "genau hundert passen");
        int last = TranslateLines.remember("line " + TranslateLines.CAPACITY);
        assertNull(TranslateLines.get(first), "die erste ist raus");
        assertEquals("line " + TranslateLines.CAPACITY, TranslateLines.get(last));
        assertEquals("line 1", TranslateLines.get(first + 1));
    }

    @Test
    void aVeryLongLineIsCut() {
        int id = TranslateLines.remember("x".repeat(5000));
        assertEquals(TranslateLines.MAX_LENGTH, TranslateLines.get(id).length());
    }

    // ---- der Klick ----

    @Test
    void theClickNamesTheNumberNotTheText() {
        assertEquals("/shoki translateline 7", TranslateOnClick.commandFor(7));
        StyledText wrapped = TranslateOnClick.wrap(line("§9Party §8> §b[MVP§d+§b] lrg89§f: peee"), 7);
        assertNotNull(wrapped);
        assertEquals("Party > [MVP+] lrg89: peee", wrapped.plain());
        for (StyledText.Run run : wrapped.runs()) {
            ClickEvent click = run.style().getClickEvent();
            assertInstanceOf(ClickEvent.RunCommand.class, click, run.text());
            assertEquals("/shoki translateline 7", ((ClickEvent.RunCommand) click).command());
            HoverEvent hover = run.style().getHoverEvent();
            assertInstanceOf(HoverEvent.ShowText.class, hover);
            assertEquals("Click to translate!", ((HoverEvent.ShowText) hover).value().getString());
        }
        assertTrue(wrapped.legacy().contains("peee"), "die Farben bleiben");
        assertEquals(line("§9Party §8> §b[MVP§d+§b] lrg89§f: peee").legacy(), wrapped.legacy());
    }

    @Test
    void aPartWithItsOwnHoverKeepsIt() {
        HoverEvent own = new HoverEvent.ShowText(Component.literal("item"));
        StyledText in = StyledText.ofRuns(java.util.List.of(
                new StyledText.Run("Bob: ", net.minecraft.network.chat.Style.EMPTY),
                new StyledText.Run("[Sword]", net.minecraft.network.chat.Style.EMPTY.withHoverEvent(own))));
        StyledText wrapped = TranslateOnClick.wrap(in, 1);
        assertNotNull(wrapped);
        assertEquals(own, wrapped.runs().get(1).style().getHoverEvent());
        assertInstanceOf(ClickEvent.RunCommand.class, wrapped.runs().get(1).style().getClickEvent());
        assertInstanceOf(HoverEvent.ShowText.class, wrapped.runs().get(0).style().getHoverEvent());
    }

    @Test
    void aLineWithAClickOfItsOwnIsLeftAlone() {
        StyledText in = line("§aClick me").withEvents(new ClickEvent.RunCommand("/other"), null);
        assertNull(TranslateOnClick.wrap(in, 1));
        assertNull(TranslateOnClick.wrap(line(""), 1), "eine leere Zeile");
    }

    // ---- die Zeilen der Mod ----

    @Test
    void theAnswerLineHasTheTranslationAndCopiesItOnClick() {
        GoogleTranslate.Result r = new GoogleTranslate.Result("Good luck!", "de");
        Component line = Translator.resultLine("Viel Glück!", r, "en", false);
        assertEquals("[ShokiMod] Found translation: Good luck!", line.getString());
        ClickEvent click = line.getStyle().getClickEvent();
        assertInstanceOf(ClickEvent.CopyToClipboard.class, click);
        assertEquals("Good luck!", ((ClickEvent.CopyToClipboard) click).value());
        HoverEvent hover = line.getStyle().getHoverEvent();
        assertInstanceOf(HoverEvent.ShowText.class, hover);
        String tip = ((HoverEvent.ShowText) hover).value().getString();
        assertTrue(tip.contains("Original message: Viel Glück!"), tip);
        assertTrue(tip.contains("(Language: de)"), tip);
    }

    @Test
    void theCommandAnswerSaysThatItCopied() {
        GoogleTranslate.Result r = new GoogleTranslate.Result("Viel Glück!", "en");
        assertEquals("[ShokiMod] Copied de translation to clipboard: Viel Glück!", Translator.resultLine("Good luck!", r, "de", true).getString());
        assertTrue(((HoverEvent.ShowText) Translator.resultLine("x", new GoogleTranslate.Result("y", ""), "de", true).getStyle().getHoverEvent()).value().getString()
                .contains("(Language: unknown)"));
    }

    @Test
    void aTranslationWithColourSignsIsNotReadAsColours() {
        // Der Text kommt aus dem Netz: ein Paragraphzeichen darin faerbt nichts
        Component line = Translator.resultLine("x", new GoogleTranslate.Result("§cred", "de"), "en", false);
        assertEquals("[ShokiMod] Found translation: §cred", line.getString());
    }

    @Test
    void aShortNoteHasThePrefix() {
        assertEquals("[ShokiMod] A translation is still running.", Translator.note("A translation is still running.").getString());
    }

    @Test
    void theButtonOpensGooglesListOfLanguageCodes() {
        assertEquals("https://cloud.google.com/translate/docs/languages#try-it-for-yourself", Translator.LANGUAGE_LIST_URL);
        URI uri = URI.create(Translator.LANGUAGE_LIST_URL);
        assertEquals("https", uri.getScheme());
        assertEquals("cloud.google.com", uri.getHost());
    }
}

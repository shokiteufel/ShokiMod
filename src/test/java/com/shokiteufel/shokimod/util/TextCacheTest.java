package com.shokiteufel.shokimod.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Die gemerkten Antworten der Oberflaeche: gleicher Text, gleiche Antwort, ohne neues Objekt - und die Tabellen bleiben begrenzt */
class TextCacheTest {

    @AfterEach
    void aufraeumen() {
        TextCache.leeren();
    }

    @Test
    void colourCodesAreTakenOutButFormattingStays() {
        assertEquals("Hello §lbold", TextCache.stripColours("§aHello §lbold"));
        assertEquals("plain", TextCache.stripColours("plain"));
        assertEquals("", TextCache.stripColours(null));
        assertEquals("a§", TextCache.stripColours("a§"));
    }

    @Test
    void theSameTextGivesTheSameAnswerObject() {
        String text = "§cRed §7grey";
        String erste = TextCache.ohneFarben(text);
        assertEquals("Red grey", erste);
        assertSame(erste, TextCache.ohneFarben(text), "beim zweiten Mal kommt dasselbe Objekt, nicht ein neues");
        assertSame(erste, TextCache.ohneFarben(new String(text)), "auch fuer eine gleiche, aber andere Zeichenkette");
    }

    @Test
    void aTextWithoutColourCodesComesBackAsItIs() {
        String text = "no codes here";
        assertSame(text, TextCache.ohneFarben(text));
    }

    @Test
    void aCellIsSplitOnceAndTheSplitIsShared() {
        char trenner = '\u0001';
        String zelle = "1,234" + trenner + "56%";
        String[] a = TextCache.zelle(zelle, trenner);
        assertEquals("1,234", a[0]);
        assertEquals("56%", a[1]);
        assertSame(a, TextCache.zelle(new String(zelle), trenner), "dieselbe Zelle: dieselben Teile");
    }

    @Test
    void aCellWithoutTheSeparatorHasNoParts() {
        assertNull(TextCache.zelle("no split", '\u0001'));
    }

    @Test
    void theTablesStayBounded() {
        for (int i = 0; i < TextCache.GRENZE * 3; i++) {
            TextCache.ohneFarben("§a" + i);
            TextCache.zelle("a\u0001" + i, '\u0001');
        }
        assertTrue(TextCache.groesse() <= TextCache.GRENZE * 2, "gemerkt: " + TextCache.groesse());
    }

    @Test
    void clearingForgetsEverything() {
        TextCache.ohneFarben("§aone");
        assertTrue(TextCache.groesse() > 0);
        TextCache.leeren();
        assertEquals(0, TextCache.groesse());
    }
}

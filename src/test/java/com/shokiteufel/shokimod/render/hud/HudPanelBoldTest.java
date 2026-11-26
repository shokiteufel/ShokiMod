package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.render.hud.HudPanel.Segment;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HUD > Bold numbers im Kasten: Der Wert einer markierten Zeile wird fett gezeichnet UND gemessen, der gespeicherte Text bleibt, ein fettes Zeichen ist einen
 * Punkt breiter - und mit dem Schalter aus ist alles Byte fuer Byte wie vorher.
 *
 * Eine Schrift gibt es im Test nicht; {@link #FONT} rechnet wie Minecraft in einfachster Form: sechs Punkte je sichtbares Zeichen, ein Zeichen in Fettdruck einen mehr.
 */
class HudPanelBoldTest {

    /** "§l" setzt Fettdruck; jeder Farbcode und "§r" nimmt ihn weg; Codes selbst haben keine Breite */
    private static final ToIntFunction<String> FONT = text -> {
        int width = 0;
        boolean bold = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                if (code == 'l') bold = true;
                else if (code == 'r' || (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) bold = false;
                continue;
            }
            width += 6 + (bold ? 1 : 0);
        }
        return width;
    };

    private static final char SPLIT = HudPanel.CELL_SPLIT;
    private static final int COLOUR = 0xFFFFFFFF;

    private static HudPanel pairPanel(boolean switchOn, boolean marked) {
        HudPanel panel = new HudPanel(switchOn);
        panel.pair("Total:", "1.5m", COLOUR, COLOUR);
        if (marked) panel.boldValue();
        return panel;
    }

    // ---- Schalter aus: wie vorher ----

    @Test
    void withTheSwitchOffTheTextsAndTheWidthAreExactlyWhatTheyWere() {
        HudPanel off = new HudPanel(false);
        off.pair("Total:", "1.5m", COLOUR, COLOUR).boldValue();
        off.table("Name", "§f4" + SPLIT + "§7 5%", "§f3" + SPLIT + "§7 1%", COLOUR, COLOUR).boldValue();
        off.segments(1.0f, List.of(Segment.withNumber("DH: ", "4", " 14,29%", COLOUR)));
        off.aligned("Held:", "12k", COLOUR, COLOUR).boldValue();

        // Dasselbe, nur ohne einen einzigen Aufruf von boldValue/withNumber: die Messung des alten Wegs
        HudPanel plain = new HudPanel(false);
        plain.pair("Total:", "1.5m", COLOUR, COLOUR);
        plain.table("Name", "§f4" + SPLIT + "§7 5%", "§f3" + SPLIT + "§7 1%", COLOUR, COLOUR);
        plain.segments(1.0f, List.of(new Segment("DH: 4 14,29%", COLOUR)));
        plain.aligned("Held:", "12k", COLOUR, COLOUR);

        assertEquals(plain.contentWidth(FONT), off.contentWidth(FONT));
        assertEquals("1.5m", off.shownValue(0));
        assertEquals("§f4" + SPLIT + "§7 5%", off.shownValue(1));
        assertEquals("§f3" + SPLIT + "§7 1%", off.shownExtra(1));
        assertEquals("DH: 4 14,29%", off.shownPart(2, 0));
        assertEquals("12k", off.shownValue(3));
    }

    @Test
    void theDefaultConstructorReadsTheSwitchOfTheConfig() {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig.INSTANCE = new ModConfig();
            ModConfig.INSTANCE.hud.boldNumbers = false;
            HudPanel off = new HudPanel();
            off.pair("Total:", "1.5m", COLOUR, COLOUR).boldValue();
            off.contentWidth(FONT);
            assertEquals("1.5m", off.shownValue(0));

            ModConfig.INSTANCE.hud.boldNumbers = true;
            HudPanel on = new HudPanel();
            on.pair("Total:", "1.5m", COLOUR, COLOUR).boldValue();
            on.contentWidth(FONT);
            assertEquals("§l1.5m", on.shownValue(0));
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }

    // ---- Schalter an ----

    @Test
    void aMarkedPairHasABoldValueAndTheLabelStaysPlain() {
        HudPanel on = pairPanel(true, true);
        int plainWidth = pairPanel(true, false).contentWidth(FONT);
        int boldWidth = on.contentWidth(FONT);

        assertEquals("§l1.5m", on.shownValue(0));
        // "Total:" 6 Zeichen (36) + Luecke 8 + "1.5m" 4 Zeichen: schlicht 24, fett 28. Waere die Beschriftung mit fett, kaemen 6 dazu
        assertEquals(36 + 8 + 24, plainWidth);
        assertEquals(36 + 8 + 28, boldWidth);
    }

    @Test
    void aPairWithoutTheMarkStaysPlainEvenWithTheSwitchOn() {
        HudPanel panel = pairPanel(true, false);
        panel.contentWidth(FONT);
        assertEquals("1.5m", panel.shownValue(0));
    }

    @Test
    void theMarkIsOnlyForTheLastRow() {
        HudPanel panel = new HudPanel(true);
        panel.pair("A:", "1k", COLOUR, COLOUR).boldValue();
        panel.pair("Time:", "1:23", COLOUR, COLOUR);
        panel.contentWidth(FONT);
        assertEquals("§l1k", panel.shownValue(0));
        assertEquals("1:23", panel.shownValue(1), "eine Zeit ist kein Betrag und wird nicht markiert");
    }

    @Test
    void colourCodesInsideAValueKeepItBold() {
        HudPanel panel = new HudPanel(true);
        panel.pair("L:", "§6100k", COLOUR, COLOUR).boldValue();
        panel.contentWidth(FONT);
        // "§l§6100k" waere nicht fett: der Farbcode nimmt den Fettdruck wieder weg
        assertEquals("§6§l100k", panel.shownValue(0));
        // "L:" 2 Zeichen (12) + Luecke 8 + "100k" fett (28); die Farbcodes selbst haben keine Breite
        assertEquals(12 + 8 + 28, panel.contentWidth(FONT));
    }

    @Test
    void anAlignedRowIsMeasuredWithItsBoldValue() {
        HudPanel off = new HudPanel(true);
        off.aligned("Held:", "12k", COLOUR, COLOUR);
        HudPanel on = new HudPanel(true);
        on.aligned("Held:", "12k", COLOUR, COLOUR).boldValue();
        assertEquals(off.contentWidth(FONT) + 3, on.contentWidth(FONT));
        assertEquals("§l12k", on.shownValue(0));
    }

    // ---- Tabellen: Spalten richten sich nach der fetten Zahl ----

    @Test
    void theTableColumnsGrowWithTheBoldNumbersButTheShareStaysPlain() {
        HudPanel plain = tablePanel(true, false);
        HudPanel bold = tablePanel(true, true);
        int plainWidth = plain.contentWidth(FONT);
        int boldWidth = bold.contentWidth(FONT);

        // Zeile 1: "Name" (24) + 8 + erste Spalte + 8 + zweite Spalte.
        // schlicht: Zahlen 12 und 6 -> 12, Anteile " 5%" (18) und " 50%" (24) -> 24, Spalte = max(12 + 4 + 24, "12 5%"-breit 36) = 40;
        //           zweite Spalte: Zahl 6, Anteil 18, zusammengesetzt "3 1%" mit Leerzeichen 30 -> 30  => 24 + 8 + 40 + 8 + 30 = 110
        // fett:     Zahlen 14 und 7 -> 14, Anteile gleich 24, Spalte = max(14 + 4 + 24, 39) = 42;
        //           zweite Spalte: Zahl 7, Anteil 18, zusammengesetzt (fettes Leerzeichen) 32 -> 32 => 24 + 8 + 42 + 8 + 32 = 114
        assertEquals(110, plainWidth);
        assertEquals(114, boldWidth);

        assertEquals("§f§l12" + SPLIT + "§7 5%", bold.shownValue(0), "die Zahl fett, der Anteil hinter dem Trenner unveraendert");
        assertEquals("§f§l3" + SPLIT + "§7 1%", bold.shownExtra(0));
        assertEquals("§f§l3" + SPLIT + "§7 50%", bold.shownValue(1));
        assertEquals("", bold.shownExtra(1), "eine leere Zelle bleibt leer");
    }

    private static HudPanel tablePanel(boolean switchOn, boolean marked) {
        HudPanel panel = new HudPanel(switchOn);
        panel.table("Name", "§f12" + SPLIT + "§7 5%", "§f3" + SPLIT + "§7 1%", COLOUR, COLOUR);
        if (marked) panel.boldValue();
        panel.table("X", "§f3" + SPLIT + "§7 50%", "", COLOUR, COLOUR);
        if (marked) panel.boldValue();
        return panel;
    }

    @Test
    void aTableRowThatIsNotMarkedKeepsItsCells() {
        HudPanel panel = new HudPanel(true);
        panel.table("Time:", "1:23", "", COLOUR, COLOUR);
        panel.contentWidth(FONT);
        assertEquals("1:23", panel.shownValue(0));
        assertEquals("", panel.shownExtra(0));
    }

    // ---- Segmente: nur die Zahl ----

    @Test
    void aSegmentBoldensOnlyItsNumberAndTheTextStaysTheSame() {
        Segment dh = Segment.withNumber("DH: ", "4", " 14,29%", COLOUR);
        assertEquals("DH: 4 14,29%", dh.text(), "der gespeicherte Text ist der alte");
        assertEquals("DH: 4 14,29%", dh.shown(false));
        assertEquals("DH: §l4§r 14,29%", dh.shown(true));

        Segment total = Segment.withNumber("Total: ", "5", "", COLOUR);
        assertEquals("Total: §l5", total.shown(true), "am Ende braucht es kein Zuruecksetzen");

        Segment plain = new Segment("Sea", COLOUR);
        assertEquals("Sea", plain.shown(true), "ein Stueck ohne Zahl bleibt, wie es ist");
    }

    @Test
    void aSegmentRowIsMeasuredWithTheBoldNumber() {
        HudPanel off = new HudPanel(true);
        off.segments(1.0f, List.of(new Segment("DH: 4 14,29%", COLOUR)));
        HudPanel on = new HudPanel(true);
        on.segments(1.0f, List.of(Segment.withNumber("DH: ", "4", " 14,29%", COLOUR)));

        // 12 Zeichen schlicht = 72; die eine fette Ziffer ist einen Punkt breiter
        assertEquals(72, off.contentWidth(FONT));
        assertEquals(73, on.contentWidth(FONT));
        assertEquals("DH: §l4§r 14,29%", on.shownPart(0, 0));
    }

    @Test
    void theOutlineOfASegmentKeepsTheResetThatEndsTheBold() {
        // stripColours nimmt "§r" mit; ohne es liefe der Umriss hinter der Zahl fett weiter
        assertEquals("DH: §l4§r 14,29%", HudPanel.outlineOfBold("DH: §l4§r 14,29%"));
        assertEquals("X§l4§r", HudPanel.outlineOfBold("§cX§l4§r"));
        assertEquals("", HudPanel.outlineOfBold(null));
    }

    // ---- Zeilenarten ohne Wert-Spalte ----

    @Test
    void aMarkOnATextLineATitleOrABarChangesNothing() {
        HudPanel plain = new HudPanel(true);
        plain.title("Profit", COLOUR);
        plain.line("Total: 5", COLOUR);
        plain.bar("Party", 3, 10, COLOUR, COLOUR);
        plain.blank();

        HudPanel marked = new HudPanel(true);
        marked.title("Profit", COLOUR).boldValue();
        marked.line("Total: 5", COLOUR).boldValue();
        marked.bar("Party", 3, 10, COLOUR, COLOUR).boldValue();
        marked.blank().boldValue();

        assertEquals(plain.contentWidth(FONT), marked.contentWidth(FONT));
        for (int row = 0; row < 4; row++) {
            assertEquals(plain.shownValue(row), marked.shownValue(row), "Zeile " + row);
        }
        assertEquals("3/10", marked.shownValue(2), "der Wert eines Balkens ist 'x/y' und bleibt");
    }

    @Test
    void markingWithoutAnyRowDoesNothing() {
        HudPanel panel = new HudPanel(true);
        panel.boldValue();
        assertTrue(panel.isEmpty());
    }

    @Test
    void theWidthIsMeasuredAgainAfterTheMark() {
        // Erst messen, dann markieren: die Breite darf nicht stehen bleiben
        HudPanel panel = new HudPanel(true);
        panel.pair("Total:", "1.5m", COLOUR, COLOUR);
        int before = panel.contentWidth(FONT);
        panel.boldValue();
        assertNotEquals(before, panel.contentWidth(FONT));
    }
}

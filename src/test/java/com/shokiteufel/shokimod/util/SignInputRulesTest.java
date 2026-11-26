package com.shokiteufel.shokimod.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Welche Schilder sind Zahlenschilder: die beiden aus den Bildern des Users (Auktion, Basar), kurze Form, Suchschilder, Namensschild, gewoehnliche Schilder. */
class SignInputRulesTest {

    private static final String ARROWS = "^^^^^^^^^^^^^^^";

    private static String[] sign(String typed, String marker, String third, String fourth) {
        return new String[]{typed, marker, third, fourth};
    }

    @Test
    void theAuctionStartingBidSignIsANumberSign() {
        String[] lines = sign("434234324", ARROWS, "Your auction", "starting bid");
        assertTrue(SignInputRules.isNumberSign(lines));
        assertFalse(SignInputRules.isPriceSign(lines), "ein Gebot ist eine ganze Zahl");
    }

    @Test
    void theBazaarPriceSignIsANumberSignAndAPriceSign() {
        String[] lines = sign("45656445465", ARROWS, "Enter price", "per unit");
        assertTrue(SignInputRules.isNumberSign(lines));
        assertTrue(SignInputRules.isPriceSign(lines));
    }

    @Test
    void anAmountSignIsANumberSignButNoPriceSign() {
        String[] lines = sign("64", ARROWS, "Enter amount", "to order");
        assertTrue(SignInputRules.isNumberSign(lines));
        assertFalse(SignInputRules.isPriceSign(lines));
    }

    @Test
    void theShortArrowFormCountsToo() {
        assertTrue(SignInputRules.isNumberSign(sign("", "^^^^^^", "Enter amount", "")));
        assertEquals(6, SignInputRules.ALT_INPUT_MARKER.length());
        assertEquals(15, SignInputRules.INPUT_MARKER.length());
    }

    @Test
    void searchSignsAreNoNumberSigns() {
        assertFalse(SignInputRules.isNumberSign(sign("", ARROWS, "Enter your", "search query")));
        assertFalse(SignInputRules.isNumberSign(sign("", ARROWS, "Enter search query", "")), "die dritte Zeile entscheidet: endet sie auf query");
        assertTrue(SignInputRules.isNumberSign(sign("", ARROWS, "Enter price", "query")), "nur die dritte Zeile zaehlt, nicht die vierte");
        assertFalse(SignInputRules.isNumberSign(sign("", "^^^^^^", "Enter your", "")));
        assertFalse(SignInputRules.isNumberSign(sign("", ARROWS, "Type your", "recipe")));
    }

    @Test
    void theNameSignIsNoNumberSign() {
        assertFalse(SignInputRules.isNumberSign(sign("", ARROWS, "Enter name", "")));
    }

    @Test
    void anOrdinarySignOrBrokenInputIsNoNumberSign() {
        assertFalse(SignInputRules.isNumberSign(sign("Hello", "", "", "")));
        assertFalse(SignInputRules.isNumberSign(sign("5", "^^^", "Enter price", "per unit")), "andere Zahl von Pfeilen");
        assertFalse(SignInputRules.isNumberSign(new String[]{"5", ARROWS, "Enter price"}), "zu wenige Zeilen");
        assertFalse(SignInputRules.isNumberSign(new String[]{"5", null, "Enter price", "per unit"}));
        assertFalse(SignInputRules.isNumberSign(new String[]{"5", ARROWS, null, "per unit"}));
        assertFalse(SignInputRules.isNumberSign(null));
        assertFalse(SignInputRules.isPriceSign(null));
        assertFalse(SignInputRules.isPriceSign(new String[]{"5", ARROWS}));
    }
}

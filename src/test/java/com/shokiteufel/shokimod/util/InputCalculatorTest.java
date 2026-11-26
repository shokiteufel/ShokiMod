package com.shokiteufel.shokimod.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Der Rechner fuer die Zahlenschilder: Zahlen, k/m/b/t, Rangfolge, Klammern, Fehler, die Schreibweise des Ergebnisses und was beim Fertigmachen ins Schild kommt. */
class InputCalculatorTest {

    private static double value(String input) {
        InputCalculator.Result result = InputCalculator.evaluate(input);
        assertTrue(result.ok(), input + " -> " + result);
        return result.value();
    }

    private static String error(String input) {
        InputCalculator.Result result = InputCalculator.evaluate(input);
        assertFalse(result.ok(), input + " sollte ein Fehler sein: " + result);
        assertFalse(result.isEmpty(), input + " ist nicht leer");
        return result.error();
    }

    // ---- Zahlen ----

    @Test
    void aPlainNumberIsItself() {
        assertEquals(45656445465d, value("45656445465"));
        assertEquals(0.5, value(".5"));
        assertEquals(5d, value("5."));
        assertEquals(1234.5, value("1234.5"));
    }

    @Test
    void theLettersKMBTMeanThousandMillionBillionTrillion() {
        assertEquals(2_000d, value("2k"));
        assertEquals(1_500_000d, value("1.5m"));
        assertEquals(3_000_000_000d, value("3B"));
        assertEquals(1e12, value("1t"));
        assertEquals(2_500d, value("2.5K"), "gross und klein ist gleich");
    }

    @Test
    void aLetterBelongsToTheNumberBeforeItOnly() {
        assertEquals(5_000_000d, value("1m*5"));
        assertEquals(3_000_000d, value("1.5m * 2"));
        error("k5");
        error("5kk");
        error("5 k");
    }

    // ---- Rechenarten, Rangfolge ----

    @Test
    void thePrecedenceIsPowerThenTimesThenPlus() {
        assertEquals(14d, value("2+3*4"));
        assertEquals(20d, value("(2+3)*4"));
        assertEquals(1d, value("1+2*3-6"));
        assertEquals(9d, value("3^2"));
        assertEquals(11d, value("2+3^2"));
    }

    @Test
    void sameRankGoesLeftToRight() {
        assertEquals(3d, value("10-4-3"));
        assertEquals(5d, value("100/4/5"));
        assertEquals(2d, value("10%4"));
        assertEquals(3d, value("17%10%4"), "(17 % 10) % 4");
        assertEquals(12d, value("2*3*2"));
    }

    @Test
    void aPowerGoesRightToLeftAndTheSignComesBeforeIt() {
        assertEquals(512d, value("2^3^2"));
        assertEquals(-4d, value("-2^2"));
        assertEquals(0.5, value("2^-1"));
        assertEquals(8d, value("5--3"));
        assertEquals(5d, value("+5"));
        assertEquals(-5d, value("-5"));
    }

    @Test
    void spacesBetweenThePartsDoNotMatter() {
        assertEquals(3d, value(" 1 + 2 "));
        assertEquals(6d, value("( 1 + 2 ) * 2"));
    }

    @Test
    void theResultOfZeroHasNoSign() {
        assertEquals(0d, value("0*5"));
        assertEquals(0d, value("-0"));
        assertEquals("0", InputCalculator.format(value("-0")));
    }

    // ---- Fehler ----

    @Test
    void anIncompleteOrWrongEquationIsAnError() {
        assertEquals("Invalid equation", error("5*"));
        assertEquals("Invalid equation", error("*5"));
        assertEquals("Invalid equation", error("5 5"));
        assertEquals("Invalid equation", error("1 000"));
        assertEquals("Invalid equation", error("()"));
        assertTrue(error("abc").startsWith("Invalid character"));
        assertTrue(error("5&3").startsWith("Invalid character"));
    }

    @Test
    void bracketsMustBalance() {
        assertEquals("Unbalanced parenthesis", error("(2+3"));
        assertEquals("Unbalanced parenthesis", error("2+3)"));
        assertEquals("Unbalanced parenthesis", error(")"));
    }

    @Test
    void separatorsAndDoubleDotsAreNotNumbers() {
        assertTrue(error("1,000").length() > 0);
        assertTrue(error(",5").startsWith("Invalid number"));
        assertTrue(error("_5").startsWith("Invalid number"));
        assertEquals("Invalid number", error("1..2"));
        assertEquals("Invalid number", error("."));
    }

    @Test
    void divisionByZeroAndAnHugeResultAreErrors() {
        assertEquals("Division by zero", error("1/0"));
        assertEquals("Division by zero", error("5%0"));
        assertEquals("Division by zero", error("1/(2-2)"));
        assertEquals("Result is too large", error("9^999"));
    }

    @Test
    void nothingTypedIsNotAnError() {
        assertTrue(InputCalculator.evaluate("").isEmpty());
        assertTrue(InputCalculator.evaluate("   ").isEmpty());
        assertTrue(InputCalculator.evaluate(null).isEmpty());
        assertFalse(InputCalculator.evaluate("5").isEmpty());
    }

    // ---- Schreibweise des Ergebnisses ----

    @Test
    void theResultHasThousandsSeparatorsAndUpToTwoDecimals() {
        assertEquals("45,656,445,465", InputCalculator.format(45656445465d));
        assertEquals("1,500.5", InputCalculator.format(1500.5));
        assertEquals("1,234.57", InputCalculator.format(1234.567));
        assertEquals("1,000,000,000,000", InputCalculator.format(1e12));
        assertEquals("0", InputCalculator.format(0d));
        assertEquals("-5", InputCalculator.format(-5d));
        assertEquals("999", InputCalculator.format(999d));
        assertEquals("1,000", InputCalculator.format(1000d));
    }

    // ---- Was beim Fertigmachen ins Schild kommt ----

    @Test
    void aPlainNumberOrOneWithALetterIsLeftAsTyped() {
        assertEquals("45656445465", InputCalculator.replacement("45656445465", false));
        assertEquals("1.5m", InputCalculator.replacement("1.5m", true), "das versteht Hypixel selbst");
        assertEquals("-5", InputCalculator.replacement("-5", false));
        assertEquals("  7 ", InputCalculator.replacement("  7 ", false));
        assertNull(InputCalculator.replacement(null, false));
        assertEquals("", InputCalculator.replacement("", false));
    }

    @Test
    void anEquationIsReplacedByItsResult() {
        assertEquals("5000000", InputCalculator.replacement("1m*5", false));
        assertEquals("14", InputCalculator.replacement("2+3*4", false));
        assertEquals("1000000000000", InputCalculator.replacement("1b*1000", false));
    }

    @Test
    void anAmountIsRoundedAndAPriceKeepsTwoDecimals() {
        assertEquals("333333", InputCalculator.replacement("1m/3", false));
        assertEquals("333333.33", InputCalculator.replacement("1m/3", true));
        assertEquals("5", InputCalculator.replacement("2*2.5", true), "keine unnuetzen Nullen");
        assertEquals("0.3", InputCalculator.replacement("0.1+0.2", true), "kein Rest der Binaerrechnung");
        assertEquals("3", InputCalculator.replacement("5/2+0.5", false) , "2,5 + 0,5 = 3");
        assertEquals("3", InputCalculator.replacement("5/2", false), "2,5 wird auf 3 gerundet");
    }

    @Test
    void whatCannotBeUsedStaysAsTyped() {
        assertEquals("5*", InputCalculator.replacement("5*", false), "Fehler");
        assertEquals("(2+3", InputCalculator.replacement("(2+3", false), "Fehler");
        assertEquals("5-10", InputCalculator.replacement("5-10", false), "ein negativer Betrag geht nicht");
        assertEquals("1t*1000", InputCalculator.replacement("1t*1000", false), "16 Stellen passen nicht in eine Zeile von 15");
        assertEquals(15, InputCalculator.SIGN_LINE_LENGTH);
    }

    @Test
    void isExpressionSeesOperatorsAndBracketsButNotALeadingMinus() {
        assertTrue(InputCalculator.isExpression("5*3"));
        assertTrue(InputCalculator.isExpression("5-3"));
        assertTrue(InputCalculator.isExpression("(5)"));
        assertTrue(InputCalculator.isExpression("2^3"));
        assertTrue(InputCalculator.isExpression("7/2"));
        assertTrue(InputCalculator.isExpression("7%2"));
        assertTrue(InputCalculator.isExpression("1+1"));
        assertFalse(InputCalculator.isExpression("1.5m"));
        assertFalse(InputCalculator.isExpression("45656445465"));
        assertFalse(InputCalculator.isExpression("-5"));
        assertFalse(InputCalculator.isExpression("  7 "));
        assertFalse(InputCalculator.isExpression(""));
        assertFalse(InputCalculator.isExpression(null));
    }
}

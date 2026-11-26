package com.shokiteufel.shokimod.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Estimated Item Value (2.4.18): wie Zahlen geschrieben werden. */
class EivFormatTest {

    @Test
    void smallNumbersStayAsTheyAre() {
        assertEquals("0", EivFormat.shortFormat(0));
        assertEquals("7", EivFormat.shortFormat(7));
        assertEquals("999", EivFormat.shortFormat(999.9));
        assertEquals("0.5", EivFormat.shortFormat(0.5));
    }

    @Test
    void thousandsGetAKAndOneDecimalBelowTenK() {
        assertEquals("1k", EivFormat.shortFormat(1_000));
        assertEquals("1.5k", EivFormat.shortFormat(1_500));
        assertEquals("9.9k", EivFormat.shortFormat(9_999));
        assertEquals("12k", EivFormat.shortFormat(12_345), "ab zehntausend keine Nachkommastelle");
        assertEquals("99k", EivFormat.shortFormat(99_999));
        assertEquals("123k", EivFormat.shortFormat(123_456));
    }

    @Test
    void millionsAndBillionsCutInsteadOfRounding() {
        assertEquals("56.1M", EivFormat.shortFormat(56_123_456));
        assertEquals("99.9M", EivFormat.shortFormat(99_999_999));
        assertEquals("150M", EivFormat.shortFormat(150_000_000));
        assertEquals("1.1B", EivFormat.shortFormat(1_150_000_000L));
        assertEquals("2B", EivFormat.shortFormat(2_000_000_000L));
        assertEquals("1.2T", EivFormat.shortFormat(1_234_000_000_000L));
    }

    @Test
    void aNegativeNumberKeepsItsSign() {
        assertEquals("-1.5k", EivFormat.shortFormat(-1_500));
        assertEquals("-12", EivFormat.shortFormat(-12));
    }

    @Test
    void theExactFormGroupsDigits() {
        assertEquals("1,234,568", EivFormat.withSeparators(1_234_567.8));
        assertEquals("0", EivFormat.withSeparators(0.2));
    }

    @Test
    void romanNumeralsGoUpToTheBigOnes() {
        assertEquals("I", EivFormat.roman(1));
        assertEquals("VI", EivFormat.roman(6));
        assertEquals("X", EivFormat.roman(10));
        assertEquals("XIV", EivFormat.roman(14));
        assertEquals("7000", EivFormat.roman(7000), "ausser Reichweite bleibt die Zahl");
        assertEquals("0", EivFormat.roman(0));
    }

    @Test
    void aSumInBracketsIsGreyAroundGold() {
        assertEquals("§7(§6150k§7)", EivFormat.coinsInBrackets(150_000, false));
        assertEquals("§7(§7150k§7)", EivFormat.coinsInBrackets(150_000, true), "grau, wenn der Preis nicht zaehlt");
        assertEquals("§c-1.5k", EivFormat.coin(-1_500, false), "rot unter null");
    }
}

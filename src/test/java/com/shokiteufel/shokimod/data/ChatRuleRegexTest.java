package com.shokiteufel.shokimod.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ein ungueltiger Regex einer Chat-Regel wird nur einmal uebersetzt (der Fehlschlag wird gemerkt), und eine Aenderung des Musters oder der
 * Gross-/Kleinschreibung wird trotzdem erkannt. Vorher uebersetzte jeder Aufruf - jede Chatzeile - das ungueltige Muster neu.
 */
class ChatRuleRegexTest {

    @Test
    void anInvalidPatternStaysInvalidOnEveryCall() {
        ChatRule rule = new ChatRule();
        rule.regex = true;
        rule.filter = "(";
        assertFalse(rule.filterIsValid());
        assertFalse(rule.filterIsValid());
        assertFalse(rule.filterIsValid());
    }

    @Test
    void aChangedPatternIsPickedUpAfterAFailure() {
        ChatRule rule = new ChatRule();
        rule.regex = true;
        rule.filter = "(";
        assertFalse(rule.filterIsValid());
        rule.filter = "drop (\\d+)";
        assertTrue(rule.filterIsValid(), "ein neues, gueltiges Muster gilt sofort");
        rule.filter = "[";
        assertFalse(rule.filterIsValid(), "und ein neues ungueltiges ebenso");
    }

    @Test
    void theExceptionPatternIsHandledTheSameWay() {
        ChatRule rule = new ChatRule();
        rule.regex = true;
        rule.except = "(";
        assertFalse(rule.exceptIsValid());
        assertFalse(rule.exceptIsValid());
        rule.except = "ok";
        assertTrue(rule.exceptIsValid());
    }

    @Test
    void aValidPatternIsStillValidAfterCalls() {
        ChatRule rule = new ChatRule();
        rule.regex = true;
        rule.filter = "a+b";
        assertTrue(rule.filterIsValid());
        assertTrue(rule.filterIsValid());
    }
}

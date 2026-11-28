package com.shokiteufel.shokimod.scanner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Einträge der Tab-Liste, die nur ihren rohen Profilnamen zeigen (2.6.14: sie kamen als "Besucher" in die Einkaufsliste).
 */
class TabTextBareNameTest {

    @Test
    void anEntryWithoutDisplayNameShowsItsProfileName() {
        assertTrue(TabText.isBareProfileName(false, "mmo7fa864z", "mmo7fa864z"));
        // Der Scanner trimmt, Hypixel schreibt gern geschuetzte Leerzeichen
        assertTrue(TabText.isBareProfileName(false, " mmo7fa864z ", "mmo7fa864z"));
    }

    @Test
    void anEntryWithADisplayNameIsNeverBare() {
        assertFalse(TabText.isBareProfileName(true, "Sam", "Sam"));
        assertFalse(TabText.isBareProfileName(true, "mmo7fa864z", "mmo7fa864z"));
    }

    @Test
    void textThatDiffersFromTheProfileNameIsARealLine() {
        // Ein Widget, das seinen Text ueber das Team setzt (Anzeigename fehlt), steht nicht im Weg
        assertFalse(TabText.isBareProfileName(false, " Sam", "mmo7fa864z"));
        assertFalse(TabText.isBareProfileName(false, "Visitors: (2)", "kelzcty991"));
    }

    @Test
    void missingValuesAreNotBare() {
        assertFalse(TabText.isBareProfileName(false, null, "abc"));
        assertFalse(TabText.isBareProfileName(false, "abc", null));
        assertFalse(TabText.isBareProfileName(false, "", ""));
    }
}

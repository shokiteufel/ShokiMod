package com.shokiteufel.shokimod.gui;

import io.github.notenoughupdates.moulconfig.gui.MoulConfigEditor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Stellen, an denen das Einstellungsfenster auf MoulConfig aufsetzt, um mit dem 26.3-Schalter aus Eintraege auszublenden (seit 2.6.3).
 *
 * Den Editor selbst baut ein Test nicht auf: MoulConfig holt dabei die Schrift des laufenden Spiels. Was sich pruefen laesst, ist das, worauf die Mod sich stuetzt - ein
 * privates Feld und eine ueberschreibbare Methode von MoulConfig. Aendert sich das bei einer neuen MoulConfig-Fassung, schlaegt dieser Test an, statt dass das Ausblenden
 * still aufhoert. Die Zusammenarbeit mit dem echten Editor ist am laufenden Client geprueft.
 */
class ShokiConfigEditorUpdate263Test {

    @Test
    void moulConfigStillKeepsItsVisibleOptionsInTheFieldWeRead() throws NoSuchFieldException {
        Field field = MoulConfigEditor.class.getDeclaredField("currentlyVisibleOptions");
        assertTrue(Set.class.isAssignableFrom(field.getType()), "ein Set, aus dem wir Eintraege herausnehmen");
        assertFalse(Modifier.isFinal(field.getModifiers()) && Modifier.isStatic(field.getModifiers()));
    }

    @Test
    void theMethodThatRebuildsTheEntriesCanBeOverridden() throws NoSuchMethodException {
        Method method = MoulConfigEditor.class.getMethod("updateSearchResults", boolean.class);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertFalse(Modifier.isFinal(method.getModifiers()), "sonst greift unser Einsprung nicht");
        // und die Mod ueberschreibt genau sie
        assertEquals(ShokiConfigEditor.class, ShokiConfigEditor.class.getMethod("updateSearchResults", boolean.class).getDeclaringClass());
    }

    @Test
    void theCodeWordCheckGivesTheSameAnswerTwice() {
        assertFalse(ShokiConfigEditor.isSecret("some search"));
        assertFalse(ShokiConfigEditor.isSecret("some search"), "aus dem Merker");
        assertFalse(ShokiConfigEditor.isSecret(""));
        assertFalse(ShokiConfigEditor.isSecret("fishing"));
    }
}

package com.shokiteufel.shokimod.data;

import io.github.notenoughupdates.moulconfig.processor.ConfigProcessorDriver;
import io.github.notenoughupdates.moulconfig.processor.MoulConfigProcessor;
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategory;
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wie der Editor die Konfiguration sieht (derselbe Weg wie beim Oeffnen des Fensters): ikohS hat die Unterkategorien Dungeon und The Mist; die
 * Funny Map und die Stern-Mobs sind einklappbare Gruppen in Dungeon, keine Kategorien. MoulConfig kennt nur zwei Ebenen von Kategorien und
 * meldet eine dritte als Warnung ("double recursive sub category"), die Gruppen dagegen duerfen verschachtelt sein.
 */
class ModConfigDungeonStrukturTest {

    private static final class Ergebnis {
        LinkedHashMap<String, ? extends ProcessedCategory> kategorien;
        String fehlerausgabe;
    }

    private static Ergebnis verarbeiten() {
        ModConfig config = new ModConfig();
        PrintStream alt = System.err;
        ByteArrayOutputStream puffer = new ByteArrayOutputStream();
        Ergebnis e = new Ergebnis();
        try {
            System.setErr(new PrintStream(puffer, true));
            MoulConfigProcessor<ModConfig> prozessor = MoulConfigProcessor.withDefaults(config);
            new ConfigProcessorDriver(prozessor).processConfig(config);
            e.kategorien = prozessor.getAllCategories();
        } finally {
            System.setErr(alt);
        }
        e.fehlerausgabe = puffer.toString();
        return e;
    }

    private static ProcessedCategory nach(Ergebnis e, String teilDesFeldnamens) {
        for (var k : e.kategorien.entrySet()) {
            if (k.getKey().contains(teilDesFeldnamens)) return k.getValue();
        }
        return null;
    }

    @Test
    void dungeonIsASubCategoryOfIkohSAndFunnyMapAndStarMobsAreNoCategories() {
        Ergebnis e = verarbeiten();
        ProcessedCategory ikohS = nach(e, "ModConfig.ikohS");
        ProcessedCategory dungeon = nach(e, "IkohSCategory.dungeon");
        assertNotNull(ikohS, "der Reiter ikohS");
        assertNotNull(dungeon, "die Unterkategorie Dungeon");
        assertEquals(ikohS.getIdentifier(), dungeon.getParentCategoryId(), "Dungeon haengt unter ikohS");
        assertNotNull(nach(e, "IkohSCategory.mist"), "The Mist bleibt neben Dungeon");

        assertEquals(null, nach(e, "DungeonCategory.funnyMap"), "die Funny Map ist keine Kategorie mehr");
        assertEquals(null, nach(e, "DungeonCategory.starMobs"), "die Stern-Mobs sind keine Kategorie mehr");
        assertEquals(null, nach(e, "IkohSCategory.funnyMap"), "und die alte Stelle ist nicht im Editor");
        assertEquals(null, nach(e, "IkohSCategory.starMobs"), "und die alte Stelle ist nicht im Editor");
    }

    @Test
    void noWarningsAboutTheStructureAreRaised() {
        Ergebnis e = verarbeiten();
        assertFalse(e.fehlerausgabe.contains("double recursive"), e.fehlerausgabe);
        assertFalse(e.fehlerausgabe.contains("sub categories inside of an accordion"), e.fehlerausgabe);
        assertFalse(e.fehlerausgabe.contains("Cannot mix"), e.fehlerausgabe);
    }

    @Test
    void theGroupsAreFoldersInsideDungeonAndTheirOptionsSitInsideThem() {
        Ergebnis e = verarbeiten();
        ProcessedCategory dungeon = nach(e, "IkohSCategory.dungeon");
        assertNotNull(dungeon);

        int gruppen = 0;
        int optionenInGruppen = 0;
        for (ProcessedOption o : dungeon.getOptions()) {
            String editor = o.getEditor() == null ? "" : o.getEditor().getClass().getSimpleName();
            if (editor.contains("Accordion") && o.getAccordionId() < 0) gruppen++;
            if (o.getAccordionId() >= 0) optionenInGruppen++;
        }
        assertTrue(gruppen >= 3, "Funny Map, Stern-Mobs und I hate doors: gezaehlt " + gruppen);
        assertTrue(optionenInGruppen > 10, "die Optionen stehen in den Gruppen: " + optionenInGruppen);
    }

    @Test
    void theNestedFoldersOfTheFunnyMapStayInsideItsGroup() {
        Ergebnis e = verarbeiten();
        ProcessedCategory dungeon = nach(e, "IkohSCategory.dungeon");
        assertNotNull(dungeon);
        // Die Gruppen der Funny Map (Position and size, General ...) haengen in der Gruppe Funny Map: ihre accordionId ist die der aeusseren Gruppe
        int verschachtelt = 0;
        for (ProcessedOption o : dungeon.getOptions()) {
            String editor = o.getEditor() == null ? "" : o.getEditor().getClass().getSimpleName();
            if (editor.contains("Accordion") && o.getAccordionId() >= 0) verschachtelt++;
        }
        assertTrue(verschachtelt >= 2, "die Ordner der Funny Map sind Gruppen in der Gruppe: " + verschachtelt);
    }
}

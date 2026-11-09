package com.shokiteufel.shokimod.paket.kern;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernProtokollTest {

    @AfterEach
    void zu() {
        KernProtokoll.schliessen();
    }

    @Test
    void decisionsAndFindsAreWrittenAndSummedUp(@TempDir Path dir) throws IOException {
        Path datei = dir.resolve("kern-protokoll.jsonl");
        KernProtokoll.init(datei);
        assertTrue(KernProtokoll.an());

        KernProtokoll.neu(new Kern.Zugang(100L, Kern.Ursache.MOB_DROP, "REVENANT_FLESH", 3, null, "Revenant \"Flesh\"", 5, 40, "ein Mob-Tod"));
        KernProtokoll.fund(Map.of("REVENANT_FLESH", 3), "mob drop");
        KernProtokoll.summe();
        KernProtokoll.schliessen();

        List<String> zeilen = Files.readAllLines(datei, StandardCharsets.UTF_8);
        assertEquals(3, zeilen.size());
        assertTrue(zeilen.get(0).contains("\"q\":\"kern\"") && zeilen.get(0).contains("\"ursache\":\"MOB_DROP\"") && zeilen.get(0).contains("\"menge\":3"));
        assertTrue(zeilen.get(1).contains("\"q\":\"fund\"") && zeilen.get(1).contains("\"quelle\":\"mob drop\""));
        assertTrue(zeilen.get(2).contains("\"MOB_DROP REVENANT_FLESH\":3") && zeilen.get(2).contains("\"funde\":{\"REVENANT_FLESH\":3}"), zeilen.get(2));
    }

    @Test
    void offUntilInitialisedAndTextIsEscaped() {
        assertFalse(KernProtokoll.an());
        KernProtokoll.neu(new Kern.Zugang(1L, Kern.Ursache.UNBEKANNT, "X", 1, null, null, 0, 1, "b")); // ohne Datei: ohne Wirkung, ohne Fehler
        assertEquals("null", KernProtokoll.text(null));
        assertEquals("\"a\\\"b\\\\c\\n\"", KernProtokoll.text("a\"b\\c\n"));
    }
}

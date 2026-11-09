package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.scanner.ZeilenFunde.Anspruch;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Alle 68 Vorlagen von Chatzeilen mit Menge und Beute-Wort aus Schiggys Aufnahmen vom 09.11. (113246, 131537, 150546; rund neun Stunden):
 * Jede Familie wird richtig gelesen oder richtig uebergangen. Kommt eine neue Form dazu, steht sie hier nicht - dann fehlt sie dem Parser auch.
 */
class ZeilenFundeMitschnittTest {

    private static List<String> zeilen() throws IOException {
        List<String> out = new ArrayList<>();
        try (InputStream in = ZeilenFundeMitschnittTest.class.getResourceAsStream("/chat/schiggy-20261109-beute-zeilen.tsv")) {
            assertNotNull(in);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                out.add(line.substring(line.indexOf('\t') + 1));
            }
        }
        return out;
    }

    @Test
    void everyPestKillLineGivesExactlyOneClaimWithItsAmount() throws IOException {
        int n = 0;
        for (String z : zeilen()) {
            if (!z.startsWith("You received ")) continue;
            n++;
            List<Anspruch> a = ZeilenFunde.lesen(z, s -> true);
            assertEquals(1, a.size(), z);
            int x = z.indexOf('x');
            assertEquals(Integer.parseInt(z.substring("You received ".length(), x).replace(",", "")), a.get(0).menge(), z);
            assertTrue(z.contains(a.get(0).name()), z);
            assertFalse(a.get(0).name().isBlank(), z);
        }
        assertTrue(n >= 15, "die Aufnahme hat viele Kill-Zeilen: " + n);
    }

    @Test
    void theLinesThatHaveTheirOwnWayOrAreNoLootGiveNoClaim() throws IOException {
        for (String z : zeilen()) {
            boolean eigenerWeg = z.startsWith("RARE DROP!") || z.startsWith("CHARM!") || z.contains("GEXP") || z.startsWith("Event rewards")
                    || z.startsWith("[MVP+]") || z.startsWith("You caught x");
            if (!eigenerWeg) continue;
            assertTrue(ZeilenFunde.lesen(z, s -> true).isEmpty(), z);
        }
    }

    @Test
    void noLineEverGivesAClaimWithAnEmptyOrNumericName() throws IOException {
        for (String z : zeilen()) {
            for (Anspruch a : ZeilenFunde.lesen(z, s -> !s.contains(" and "))) {
                assertFalse(a.name().isBlank(), z);
                assertFalse(a.name().matches("[\\d,.]+"), z);
                assertTrue(a.menge() >= 1, z);
            }
        }
    }

    @Test
    void theHiveAndCatchLinesAreRead() throws IOException {
        boolean hive = false;
        boolean fang = false;
        for (String z : zeilen()) {
            if (z.startsWith("HIVE!")) {
                List<Anspruch> a = ZeilenFunde.lesen(z, s -> !s.contains(" and "));
                assertFalse(a.isEmpty(), z);
                assertEquals("Honeycomb", a.get(0).name(), z);
                hive = true;
            }
            if (z.contains("GOOD CATCH!")) {
                assertEquals(List.of(new Anspruch("Sponge", 16)), ZeilenFunde.lesen(z, s -> true));
                fang = true;
            }
        }
        assertTrue(hive && fang, "beide Familien stehen in der Aufnahme");
    }
}

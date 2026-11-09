package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.paket.zustand.TabListeZustand;
import com.shokiteufel.shokimod.paket.zustand.TeamZustand;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Standort-Zeilen aus zwei echten Sitzungen (Mitschnitt vom 08.11., Hypixel SkyBlock) durch den Tab-Zustand: Was der Zustand meldet,
 * ist genau das, was der {@link LocationScanner} aus Paketen erfaehrt. Gepruft wird, dass jede Aenderung ankommt, nichts doppelt und
 * dass die Reihenfolge der Gebietswechsel stimmt.
 */
class StandortMitschnittTest {
    private record Zeile(char art, UUID id, String text, int wiederholungen) {
    }

    private static List<List<Zeile>> laden() throws IOException {
        List<List<Zeile>> sitzungen = new ArrayList<>();
        List<Zeile> aktuell = new ArrayList<>();
        try (InputStream in = StandortMitschnittTest.class.getResourceAsStream("/standort/schiggy-20261108-tab.tsv");
             BufferedReader leser = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String zeile; (zeile = leser.readLine()) != null; ) {
                if (zeile.startsWith("#") || zeile.isBlank()) {
                    continue;
                }
                if (zeile.equals("---")) {
                    sitzungen.add(aktuell);
                    aktuell = new ArrayList<>();
                    continue;
                }
                String[] f = zeile.split("\t", -1);
                aktuell.add(new Zeile(f[0].charAt(0), UUID.fromString(f[1]), f[2], Integer.parseInt(f[3])));
            }
        }
        sitzungen.add(aktuell);
        return sitzungen;
    }

    private static List<String> ohneNachbarDubletten(List<String> werte) {
        List<String> aus = new ArrayList<>();
        for (String w : werte) {
            if (aus.isEmpty() || !aus.get(aus.size() - 1).equals(w)) {
                aus.add(w);
            }
        }
        return aus;
    }

    @Test
    void theRecordingHasBothSessionsWithLocationLines() throws IOException {
        List<List<Zeile>> sitzungen = laden();
        assertEquals(2, sitzungen.size());
        for (List<Zeile> s : sitzungen) {
            assertTrue(s.stream().anyMatch(z -> z.art() == 'A'), "Gebiets-Zeilen");
            assertTrue(s.stream().anyMatch(z -> z.art() == 'S'), "Server-Zeilen");
        }
    }

    @Test
    void everyChangeArrivesOnceAndTheAreasComeInOrder() throws IOException {
        for (List<Zeile> sitzung : laden()) {
            TabListeZustand tab = new TabListeZustand(new TeamZustand());
            List<String> gebiete = new ArrayList<>();
            List<String> server = new ArrayList<>();
            int meldungen = 0;
            int[] zaehler = {0};
            tab.hoeren((id, name, alt, neu) -> {
                zaehler[0]++;
                String g = Standort.gebiet(neu);
                if (g != null) {
                    gebiete.add(g);
                }
                String s = Standort.serverId(neu);
                if (s != null) {
                    server.add(s);
                }
            });
            Map<UUID, String> letzter = new HashMap<>();
            List<String> erwarteteGebiete = new ArrayList<>();
            List<String> erwarteteServer = new ArrayList<>();
            for (Zeile z : sitzung) {
                if (!letzter.containsKey(z.id())) {
                    tab.hinzufuegen(z.id(), "widget-" + z.id());
                    tab.gelistet(z.id(), true);
                }
                for (int i = 0; i < z.wiederholungen(); i++) {
                    tab.anzeigename(z.id(), z.text());
                }
                // Erwartung unabhaengig vom Zustand: eine Meldung je echter Aenderung eines Eintrags
                if (!z.text().equals(letzter.put(z.id(), z.text()))) {
                    meldungen++;
                    (z.art() == 'A' ? erwarteteGebiete : erwarteteServer).add(z.art() == 'A'
                            ? z.text().trim().substring("Area:".length()).trim()
                            : z.text().trim().substring("Server:".length()).trim());
                }
            }
            assertEquals(meldungen, zaehler[0], "Meldungen insgesamt: jede Aenderung einmal, Wiederholungen gar nicht");
            assertEquals(erwarteteGebiete, gebiete);
            assertEquals(erwarteteServer, server);
            assertFalse(gebiete.isEmpty());
            assertFalse(server.isEmpty());
            for (String g : gebiete) {
                assertFalse(g.isBlank());
            }
            for (String s : server) {
                assertTrue(s.matches("[A-Za-z0-9]+"), "Server-Kennung ohne Leerraum: '" + s + "'");
            }
            assertEquals(ohneNachbarDubletten(erwarteteGebiete), ohneNachbarDubletten(gebiete));
        }
    }

    @Test
    void theVisitedAreasAreTheOnesTheRecordingWasMadeIn() throws IOException {
        Set<String> besucht = new LinkedHashSet<>();
        for (List<Zeile> sitzung : laden()) {
            for (Zeile z : sitzung) {
                String g = z.art() == 'A' ? Standort.gebiet(z.text()) : null;
                if (g != null) {
                    besucht.add(g);
                }
            }
        }
        for (String erwartet : List.of("Private Island", "Garden", "Crimson Isle", "The End", "Dungeon Hub", "Crystal Hollows", "Jerry's Workshop")) {
            assertTrue(besucht.contains(erwartet), erwartet + " fehlt in " + besucht);
        }
    }
}

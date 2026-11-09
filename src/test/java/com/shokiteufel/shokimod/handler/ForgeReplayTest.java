package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.ForgeTracker.Row;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schiggys echter Verlauf des Forge-Widgets (Mitschnitt 09.11.2026, 13:16 bis 13:34, sieben Plaetze Glacite Amalgamation, erst beim
 * Ghost-Farmen mit dem Gauntlet, dann in Ruhe) wird dem Kasten so vorgespielt, wie ihn der Tab geliefert hat - und der Kasten dabei
 * viermal je Sekunde abgefragt, wie es der Bildschirm tut. Gemeldet hatte Schiggy: "die Sekunden sollen wieder fluessig runter laufen,
 * nicht alle 3 Sekunden".
 */
class ForgeReplayTest {

    private static final String DATEI = "/forge/schiggy-20261109-forge-tab.tsv";
    private static final long SCHRITT = 250L;
    /** 13:19:00 und 13:21:15 Uhr des Mitschnitts */
    private static final long UM_1319 = 1_794_226_740_000L;
    private static final long UM_1321 = 1_794_226_875_000L;
    /** Das Farmen: von der ersten Messung bis kurz vor der Ruhe um 13:20 */
    private static final long FARMEN_VON = 1_794_226_680_000L; // 13:18:00
    private static final long FARMEN_BIS = 1_794_226_790_000L; // 13:19:50

    private record Ereignis(long t, List<String> zeilen) {
    }

    @BeforeEach
    void frischeConfig() {
        ModConfig.INSTANCE = new ModConfig();
        ForgeTracker.speichern = () -> {
        };
        ForgeTracker.forget();
    }

    private static List<Ereignis> laden() throws Exception {
        List<Ereignis> out = new ArrayList<>();
        try (InputStream in = ForgeReplayTest.class.getResourceAsStream(DATEI)) {
            assertNotNull(in, DATEI);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] teile = line.split("\t", 3);
                long t = Long.parseLong(teile[1]);
                // Der Tab liefert die Plaetze einer Runde innerhalb weniger Millisekunden: das ist ein Ereignis
                if (out.isEmpty() || t - out.get(out.size() - 1).t() > 10L) out.add(new Ereignis(t, new ArrayList<>()));
                out.get(out.size() - 1).zeilen().add(teile[2]);
            }
        }
        return out;
    }

    /** Was beim Abfragen auffaellt */
    private static final class Messung {
        final long[] letzte = new long[ForgeTab.MAX_SLOTS + 1];
        final long[] angezeigt = new long[ForgeTab.MAX_SLOTS + 1];
        final long[] letzteAenderung = new long[ForgeTab.MAX_SLOTS + 1];
        long letztesEreignis;
        long maxHoch;
        String hochWo = "";
        long maxRunter;
        String runterWo = "";
        long laengsterStillstand;
        String stillWo = "";
        int ausserhalb;
        String ausserhalbWo = "";
        final List<long[]> tempi = new ArrayList<>();

        Messung() {
            Arrays.fill(letzte, -1L);
        }

        void abfragen(long t) {
            for (Row row : ForgeTracker.rows(t)) {
                if (row.empty() || row.ready()) continue;
                int s = row.slot();
                long rest = row.remainingMs();
                if (letzte[s] >= 0L) {
                    long diff = rest - letzte[s];
                    if (diff > maxHoch) {
                        maxHoch = diff;
                        hochWo = "Platz " + s + " bei +" + (t - 1_794_226_612_615L) + " ms";
                    }
                    if (-diff > maxRunter) {
                        maxRunter = -diff;
                        runterWo = "Platz " + s + " bei +" + (t - 1_794_226_612_615L) + " ms";
                    }
                    if (rest != letzte[s]) letzteAenderung[s] = t;
                }
                if (letzteAenderung[s] == 0L) letzteAenderung[s] = t;
                // Stillstand der Anzeige waehrend des Farmens (die Zeit laeuft dort ja)
                if (t >= FARMEN_VON && t <= FARMEN_BIS && t - letzteAenderung[s] > laengsterStillstand) {
                    laengsterStillstand = t - letzteAenderung[s];
                    stillWo = "Platz " + s + " bei +" + (t - 1_794_226_612_615L) + " ms";
                }
                letzte[s] = rest;
                // Solange der Tab frisch ist, liegt die Restzeit in der Spanne, die seine Anzeige zulaesst - oben darf sie nach einem
                // Tempowechsel noch einen Wechsel lang ueberstehen (die Anzeige gleitet dann nach unten, statt zu springen)
                if (t - letztesEreignis <= 5_000L && angezeigt[s] > 0L) {
                    long[] g = ForgeTab.spanne(angezeigt[s]);
                    if (rest < g[0] || rest > g[1] + (g[1] - g[0])) {
                        if (ausserhalb++ == 0) {
                            ausserhalbWo = "Platz " + s + " bei +" + (t - 1_794_226_612_615L) + " ms: " + rest + " ausserhalb "
                                    + g[0] + ".." + g[1];
                        }
                    }
                }
            }
            tempi.add(new long[] {t, Math.round(ForgeTracker.tempo() * 1000.0)});
        }

        double tempoBei(long t) {
            double out = 1.0;
            for (long[] e : tempi) {
                if (e[0] > t) break;
                out = e[1] / 1000.0;
            }
            return out;
        }

        @Override
        public String toString() {
            return "hoch " + maxHoch + " ms (" + hochWo + "), runter " + maxRunter + " ms (" + runterWo + "), laengster Stillstand "
                    + laengsterStillstand + " ms (" + stillWo + "), ausserhalb der Spanne " + ausserhalb + " (" + ausserhalbWo + ")";
        }
    }

    private static Messung abspielen() throws Exception {
        List<Ereignis> events = laden();
        Messung m = new Messung();
        String[] aktuell = new String[ForgeTab.MAX_SLOTS + 1];
        long now = events.get(0).t();
        for (Ereignis e : events) {
            // Zwischen den Ereignissen liest der Scanner jeden Tick die unveraenderte Liste (Lebenszeichen). Eine Luecke von ueber anderthalb
            // Minuten ist kein Stillstand der Anzeige - in Ruhe wechselt sie jede Minute -, sondern das Widget war weg (hier: Schiggy war nicht
            // in den Minen, 13:26 bis 13:34): drei Sekunden nach dem letzten Ereignis aendert sich die Liste ohne Widget.
            boolean weg = m.letztesEreignis > 0L && e.t() - m.letztesEreignis > 90_000L;
            boolean wegGemeldet = false;
            for (long t = now; t < e.t(); t += SCHRITT) {
                if (weg && t >= m.letztesEreignis + 3_000L) {
                    if (!wegGemeldet) {
                        ForgeTracker.widgetWeg();
                        wegGemeldet = true;
                    }
                } else {
                    ForgeTracker.lebenszeichen(t);
                }
                m.abfragen(t);
            }
            for (String zeile : e.zeilen()) {
                if (zeile.startsWith("Forges:")) continue;
                aktuell[Character.digit(zeile.charAt(0), 10)] = zeile;
            }
            List<String> widget = new ArrayList<>();
            widget.add("Forges:");
            for (String z : aktuell) if (z != null) widget.add(z);
            List<ForgeTab.Slot> slots = ForgeTab.parse(widget);
            for (ForgeTab.Slot s : slots) m.angezeigt[s.number()] = s.shownMs();
            ForgeTracker.processSlots(slots, e.t());
            m.letztesEreignis = e.t();
            m.abfragen(e.t());
            now = e.t() + SCHRITT;
        }
        return m;
    }

    @Test
    void theRealSeriesCountsDownWithoutJumps() throws Exception {
        Messung m = abspielen();
        // Nie nach oben, und ein einzelner Schritt (eine Viertelsekunde) nie um mehr als zwei Sekunden nach unten
        assertTrue(m.maxHoch <= 1000L, m.toString());
        assertTrue(m.maxRunter <= 2500L, m.toString());
        assertEquals(0, m.ausserhalb, m.toString());
    }

    @Test
    void theDisplayKeepsMovingWhileFarming() throws Exception {
        Messung m = abspielen();
        // Waehrend des Farmens steht die Anzeige eines Platzes nie laenger still, als der Tab seine Zeilen verspaetet: bis zu drei Sekunden
        // kommt ein Wechsel spaeter an, als er geschah - bis dahin wartet die Anzeige an der unteren Grenze, statt vorzulaufen
        assertTrue(m.laengsterStillstand <= 4000L, m.toString());
    }

    @Test
    void theTempoIsMeasuredFromTheFlips() throws Exception {
        Messung m = abspielen();
        // Beim Farmen springt die Anzeige alle 21 bis 27 Sekunden um eine Minute (etwa 2,5-fach), in Ruhe alle 60 (einfach)
        double farmen = m.tempoBei(UM_1319);
        double ruhe = m.tempoBei(UM_1321);
        assertTrue(farmen > 1.8 && farmen < 3.2, "Tempo beim Farmen: " + farmen);
        assertTrue(ruhe > 0.8 && ruhe < 1.5, "Tempo in Ruhe: " + ruhe);
    }
}

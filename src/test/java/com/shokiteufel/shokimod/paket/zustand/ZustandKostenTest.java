package com.shokiteufel.shokimod.paket.zustand;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Belegt, was die Zustandsspiegel je Paket kosten (Nutzerregel "nur das Noetige", Wirkung messen statt behaupten). Die Mengen
 * sind die aus dem Hypixel-Mitschnitt: rund 800 Spieler in der Tab-Liste, 13 Zeilen in der Seitenleiste.
 */
class ZustandKostenTest {
    private static final int DURCHLAEUFE = 200_000;
    private final com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    private record Messung(long nanosJeAufruf, long bytesJeAufruf) {
    }

    private Messung messen(Runnable aufruf) {
        for (int i = 0; i < 30_000; i++) {
            aufruf.run();
        }
        long bytes = threads.getCurrentThreadAllocatedBytes();
        long start = System.nanoTime();
        for (int i = 0; i < DURCHLAEUFE; i++) {
            aufruf.run();
        }
        long nanos = System.nanoTime() - start;
        return new Messung(nanos / DURCHLAEUFE, (threads.getCurrentThreadAllocatedBytes() - bytes) / DURCHLAEUFE);
    }

    @Test
    void aTeamUpdateOfAPlayerOutsideTheSidebarIsCheap() {
        TeamZustand teams = new TeamZustand();
        SeitenleisteZustand seite = new SeitenleisteZustand(teams);
        seite.ziel("SBScoreboard", "SKYBLOCK", 0);
        seite.anzeigeZiel("SBScoreboard");
        for (int i = 0; i < 13; i++) {
            teams.anlegen("zeile" + i, "Zeile " + i, "", List.of("§" + (char) ('a' + i)));
            seite.punkte("SBScoreboard", "§" + (char) ('a' + i), 13 - i, null);
        }
        for (int i = 0; i < 800; i++) {
            teams.anlegen("p" + i, "[MVP] ", " [X]", List.of("Spieler" + i));
        }
        int[] zaehler = {0};

        Messung m = messen(() -> {
            int i = zaehler[0]++ % 800;
            Set<String> betroffen = teams.aendern("p" + i, "[MVP] ", " [Y" + (zaehler[0] & 7) + "]");
            seite.teamGeaendert(betroffen);
        });

        System.out.println("Kosten Team-Paket (Spieler ausserhalb der Seitenleiste): " + m.nanosJeAufruf + " ns, " + m.bytesJeAufruf + " Byte je Paket");
        assertTrue(m.nanosJeAufruf < 20_000, "gemessen: " + m.nanosJeAufruf + " ns");
    }

    @Test
    void aSidebarLineChangeIsCheapAndReportsExactlyOneLine() {
        TeamZustand teams = new TeamZustand();
        SeitenleisteZustand seite = new SeitenleisteZustand(teams);
        seite.ziel("SBScoreboard", "SKYBLOCK", 0);
        seite.anzeigeZiel("SBScoreboard");
        for (int i = 0; i < 13; i++) {
            teams.anlegen("zeile" + i, "Zeile " + i, "", List.of("§" + (char) ('a' + i)));
            seite.punkte("SBScoreboard", "§" + (char) ('a' + i), 13 - i, null);
        }
        long[] aenderungen = {0};
        seite.hoeren(a -> aenderungen[0] += a.geaenderteZeilen().size());
        int[] zaehler = {0};

        Messung m = messen(() -> {
            teams.aendern("zeile3", "Uhr " + (zaehler[0]++ & 1023), "");   // die Uhr tickt
            seite.teamGeaendert(Set.of("§d"));
        });

        System.out.println("Kosten Zeilenaenderung der Seitenleiste (13 Zeilen): " + m.nanosJeAufruf + " ns, " + m.bytesJeAufruf + " Byte je Paket");
        assertTrue(aenderungen[0] >= DURCHLAEUFE, "jede Aenderung meldet genau eine Zeile");
        assertTrue(m.nanosJeAufruf < 50_000, "gemessen: " + m.nanosJeAufruf + " ns");
    }

    @Test
    void aWidgetNameUpdateInTheTabListIsCheap() {
        TeamZustand teams = new TeamZustand();
        TabListeZustand tab = new TabListeZustand(teams);
        UUID[] ids = new UUID[800];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = UUID.nameUUIDFromBytes(("p" + i).getBytes());
            tab.hinzufuegen(ids[i], "Spieler" + i);
            tab.gelistet(ids[i], true);
        }
        long[] meldungen = {0};
        tab.hoeren((id, name, alt, neu) -> meldungen[0]++);
        int[] zaehler = {0};

        Messung m = messen(() -> tab.anzeigename(ids[zaehler[0] % 160], "Widget " + (zaehler[0]++ & 3)));

        System.out.println("Kosten Anzeigename-Aenderung in der Tab-Liste (800 Eintraege): " + m.nanosJeAufruf + " ns, " + m.bytesJeAufruf + " Byte je Paket");
        assertTrue(meldungen[0] > 0);
        assertTrue(m.nanosJeAufruf < 20_000, "gemessen: " + m.nanosJeAufruf + " ns");
    }
}

package com.shokiteufel.shokimod.paket.kern;

import com.shokiteufel.shokimod.paket.kern.Kern.Ursache;
import com.shokiteufel.shokimod.paket.kern.Kern.Zugang;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Zugaenge, die der Kern in zwei echten Sitzungen gemeldet hat (Mitschnitt vom 08.11., vor der Ruestungsregel), durch die heutige
 * {@link FundRegel}. Damals gingen 661 davon als Fund an den Profit-Tracker, fast alle Ruestungsteile, die der Server fast jede Sekunde
 * abwechselnd voll (mit uuid) und gekuerzt (ohne uuid) neu schickt. Was bleibt, sind die echten Beutestuecke und ein paar offene Faelle.
 */
class FundRegelMitschnittTest {

    private static List<Zugang> laden() throws IOException {
        List<Zugang> aus = new ArrayList<>();
        try (InputStream in = FundRegelMitschnittTest.class.getResourceAsStream("/kern/schiggy-20261108-zugaenge.tsv");
             BufferedReader leser = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            long nr = 0;
            for (String zeile; (zeile = leser.readLine()) != null; ) {
                if (zeile.startsWith("#") || zeile.isBlank()) {
                    continue;
                }
                String[] f = zeile.split("\t", -1);
                aus.add(new Zugang(++nr, Ursache.valueOf(f[0]), f[3], Integer.parseInt(f[4]), f[5].equals("1") ? "uuid" : null, null,
                        Integer.parseInt(f[1]), Integer.parseInt(f[2]), "Mitschnitt"));
            }
        }
        return aus;
    }

    /** Die Regel vor der Ruestungsausnahme: dieselbe Entscheidung nach Ursache, ohne den Platz anzusehen. */
    private static boolean frueher(Zugang z) {
        return switch (z.ursache()) {
            case MOB_DROP, MEHRERE_TODE, AUFGESAMMELT -> true;
            case UNBEKANNT -> z.container() == 0;
            default -> false;
        };
    }

    @Test
    void theRecordingHasAllArrivalsOfBothSessions() throws IOException {
        assertEquals(1481, laden().size());
    }

    @Test
    void beforeTheArmourRuleMostFindsWereArmourPiecesResentByTheServer() throws IOException {
        List<Zugang> alle = laden();
        long frueherFunde = alle.stream().filter(FundRegelMitschnittTest::frueher).count();
        long davonRuestung = alle.stream().filter(FundRegelMitschnittTest::frueher)
                .filter(z -> z.container() == 0 && Kern.AUSRUESTUNGS_PLAETZE.contains(z.platz())).count();
        assertEquals(661, frueherFunde);
        assertEquals(643, davonRuestung, "so viele 'Funde' waren Ruestung am Koerper");
    }

    @Test
    void armourOnTheBodyIsNeverAFindToday() throws IOException {
        for (Zugang z : laden()) {
            if (z.container() == 0 && Kern.AUSRUESTUNGS_PLAETZE.contains(z.platz())) {
                assertFalse(FundRegel.istFund(z), z.id() + " Platz " + z.platz() + " " + z.ursache());
            }
        }
    }

    @Test
    void onlyTheRealFindsAndAFewOpenCasesRemainToday() throws IOException {
        List<Zugang> funde = new ArrayList<>();
        for (Zugang z : laden()) {
            if (FundRegel.istFund(z)) {
                funde.add(z);
            }
        }
        assertEquals(18, funde.size());
        long unbekannt = funde.stream().filter(z -> z.ursache() == Ursache.UNBEKANNT).count();
        // 5 davon sind Nether Brick, die beim Abbau direkt ins Inventar kamen (Block-Abbau-Pakete davor, kein Aufsammeln): echte Beute.
        // Offen bleiben Clipped Wings, zwei Teile End-Ruestung und ein Vampirism-Buch ohne erkennbare Ursache.
        assertEquals(9, unbekannt, "Quelle unbekannt");
        // Alles andere hat einen Beleg: Mob-Tod oder Aufsammeln
        assertEquals(9, funde.stream().filter(z -> z.ursache() != Ursache.UNBEKANNT).count());
        for (String ruestung : List.of("MAGMA_LORD_CHESTPLATE", "MAGMA_LORD_LEGGINGS", "MAGMA_LORD_BOOTS", "INFERNAL_CRIMSON_BOOTS", "SORROW_CHESTPLATE")) {
            assertTrue(funde.stream().noneMatch(z -> z.id().equals(ruestung)), ruestung + " ist kein Fund");
        }
    }
}

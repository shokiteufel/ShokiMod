package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.paket.kern.Kern;
import java.util.ArrayList;

/**
 * Was man aus dem Sack nimmt und wieder hineinlegt, ist nie ein Fund.
 *
 * <p>Die Sack-Meldung fasst eine Spanne zusammen ("Last 5s") und kommt erst an ihrem Ende. Beginnt die Spanne mit dem Zuruecklegen, ist der
 * Abgang aus dem Inventar bei der Meldung schon so alt wie ihr Fenster oder knapp aelter, und der Abgleich gegen die Abgaenge greift nicht
 * (Schiggy 09.11., 19:37: 13 Lotus Gold geholt, auf die Meldung der Entnahme gewartet, wieder eingelegt: gebucht als Fund). Zwei Dinge fangen das:
 *
 * <ul>
 *   <li>Ein Spielraum von {@link #ABGANG_SPIELRAUM_NANOS} auf das Fenster der Meldung (das Fenster ist auf Sekunden genau, ihr Zeitpunkt streut).</li>
 *   <li>Das Konto der Entnahmen: Was der Sack kuerzlich als herausgenommen gemeldet hat ("Removed items"), darf bei einer spaeteren "+"-Meldung
 *       gegen einen Abgang derselben Ware aus dem Inventar bis {@link #RUECKGABE_FENSTER_NANOS} davor verrechnet werden. Es braucht beides,
 *       die Entnahme <b>und</b> den Abgang: Wer nur Beute in den Sack bekommt, ohne dass etwas das Inventar verlaesst, verliert nichts.</li>
 * </ul>
 */
final class SackRueckgabe {

    /** Zugabe zum Fenster der Sack-Meldung beim Abgleich gegen die Abgaenge aus dem Inventar */
    static final long ABGANG_SPIELRAUM_NANOS = 3_000_000_000L;
    /** So lange gilt eine gemeldete Entnahme als Grundlage fuer eine Rueckgabe */
    static final long ENTNOMMEN_LEBEN_NANOS = 600_000_000_000L;
    /** So weit darf der Abgang der Rueckgabe vor der Meldung liegen, wenn die Ware zuvor aus dem Sack genommen wurde */
    static final long RUECKGABE_FENSTER_NANOS = 30_000_000_000L;

    private static final int MAX_POSTEN = 128;

    private static final class Posten {
        final String id;
        int menge;
        final long nanos;

        Posten(String id, int menge, long nanos) {
            this.id = id;
            this.menge = menge;
            this.nanos = nanos;
        }
    }

    private final ArrayList<Posten> entnommen = new ArrayList<>();

    /** Der Sack hat gemeldet, dass {@code menge} dieser Ware herausgenommen wurde (Zeile unter "Removed items"). */
    void entnommen(String id, int menge, long nanos) {
        if (id == null || menge <= 0) {
            return;
        }
        entnommen.removeIf(p -> nanos - p.nanos > ENTNOMMEN_LEBEN_NANOS || p.menge <= 0);
        if (entnommen.size() >= MAX_POSTEN) {
            entnommen.remove(0);
        }
        entnommen.add(new Posten(id, menge, nanos));
    }

    /** Wie viel dieser Ware der Sack kuerzlich als herausgenommen gemeldet hat und noch nicht zurueckgerechnet ist */
    int entnommenOffen(String id, long nanos) {
        int summe = 0;
        for (Posten p : entnommen) {
            if (p.id.equals(id) && nanos - p.nanos <= ENTNOMMEN_LEBEN_NANOS) {
                summe += p.menge;
            }
        }
        return summe;
    }

    /**
     * Rechnet eine "+"-Meldung ({@code rest} Stueck) gegen Waren auf, die zuvor aus dem Sack genommen wurden und jetzt wieder aus dem Inventar
     * verschwunden sind. Nur so viel, wie die Entnahmen hergeben, und nur gegen Abgaenge der letzten {@link #RUECKGABE_FENSTER_NANOS}.
     *
     * @return was uebrig bleibt, also was als Fund gilt
     */
    int verrechnen(Kern kern, String id, int rest, long nanos) {
        if (kern == null || rest <= 0) {
            return rest;
        }
        int erlaubt = entnommenOffen(id, nanos);
        if (erlaubt <= 0) {
            return rest;
        }
        int versuch = Math.min(rest, erlaubt);
        int uebrig = kern.abgangAbziehen(id, versuch, RUECKGABE_FENSTER_NANOS);
        int verrechnet = versuch - uebrig;
        if (verrechnet > 0) {
            abbuchen(id, verrechnet);
        }
        return rest - verrechnet;
    }

    private void abbuchen(String id, int menge) {
        int offen = menge;
        for (int i = 0; i < entnommen.size() && offen > 0; i++) {
            Posten p = entnommen.get(i);
            if (!p.id.equals(id)) {
                continue;
            }
            int genommen = Math.min(p.menge, offen);
            p.menge -= genommen;
            offen -= genommen;
        }
        entnommen.removeIf(p -> p.menge <= 0);
    }

    void leeren() {
        entnommen.clear();
    }
}

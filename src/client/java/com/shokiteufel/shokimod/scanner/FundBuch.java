package com.shokiteufel.shokimod.scanner;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Das Gegenstueck zwischen dem, was der Chat meldet, und dem, was der Paket-Kern als Fund zaehlt. Keine Minecraft-Typen.
 *
 * <p><b>Drop-Zeilen</b> ("RARE DROP! ..."): Steht der Drop im Chat, ist er gefallen. Normalerweise kommt dazu auch das Platz-Update des
 * Inventars, und dann zaehlt der Fund einmal, mit seiner genauen Kennung. Kommt es nicht (der Server hat es verschluckt, das Inventar war
 * gerade im Umbau), gilt die Chatzeile. Die Zuordnung laeuft ueber Paketnummern, in beiden Reihenfolgen:
 * <ul>
 *   <li>Zeile zuerst: sie wartet {@code fenster} Pakete; kommt in der Zeit ein Fund dieser Ware, ist sie belegt. Sonst wird sie nachgetragen
 *       ({@link #takt}).</li>
 *   <li>Fund zuerst: er merkt sich, dass noch keine Zeile ihn belegt hat; die Zeile findet ihn im Rueckblick von {@code fenster} Paketen.</li>
 *   <li>Fund nach dem Nachtragen: er ist schon gebucht und zaehlt nicht noch einmal ({@link #fund} gibt weniger zurueck). Der Abstand
 *       zeigt, dass das Fenster zu klein war, und das Fenster waechst auf diesen Abstand (wie das Klick-Fenster des Kerns).</li>
 * </ul>
 *
 * <p>Der Abstand zwischen Chatzeile und Platz-Update ist an den Mitschnitten noch nicht gemessen (kein Mitschnitt enthaelt eine Drop-Zeile).
 * Die Startwerte sind die groessten gemessenen Server-Abstaende ({@code Kern.TOD_FENSTER_STANDARD} und {@code Kern.KLICK_FENSTER_STANDARD});
 * jede Paarung und jede Nachtragung steht im Protokoll, damit der erste Mitschnitt mit einer Drop-Zeile die Zahl liefert.
 *
 * <p><b>Stash</b>: "You have successfully transferred your items from this stash to your sacks!" schuettet den Stash in die Saecke; die
 * naechste Sack-Meldung gehoert dem Stash. Welche Meldung das ist, sagt die Meldung selbst: Sie fasst die letzten {@code N} Sekunden
 * zusammen, und gehoert dem Stash, wenn die Stash-Zeile in diesem Zeitraum liegt.
 */
public final class FundBuch {

    /** Eine Drop-Zeile, die nachgetragen werden muss, weil das Inventar sie nicht belegt hat. */
    public record Rettung(List<String> ids, int menge, long zeilenNr) {
    }

    private static final class Posten {
        final List<String> ids;
        int menge;
        final long nr;

        Posten(List<String> ids, int menge, long nr) {
            this.ids = ids;
            this.menge = menge;
            this.nr = nr;
        }
    }

    private int fenster;
    private final int spaet;
    private final List<Posten> offen = new ArrayList<>();
    private final List<Posten> unbelegt = new ArrayList<>();
    private final List<Posten> nachgetragen = new ArrayList<>();

    private boolean stashDa;
    private long stashNanos;

    /**
     * @param fenster so viele Pakete darf zwischen Drop-Zeile und Platz-Update liegen (Startwert, waechst mit jeder verspaeteten Paarung)
     * @param spaet   so lange (in Paketen ab der Zeile) bleibt ein Nachtrag stehen, um ein verspaetetes Platz-Update zu erkennen
     */
    public FundBuch(int fenster, int spaet) {
        this.fenster = fenster;
        this.spaet = Math.max(spaet, fenster);
    }

    public int fenster() {
        return fenster;
    }

    // ------------------------------------------------------------------ Drop-Zeilen

    /**
     * Die Chat-Zeile meldet einen Drop.
     *
     * @param ids   die moeglichen Kennungen der Ware
     * @param menge die gemeldete Menge
     * @param nr    Paketnummer der Zeile
     * @return wie viel davon schon ein gezaehlter Fund belegt hat (0, wenn die Zeile warten muss)
     */
    public int dropZeile(List<String> ids, int menge, long nr) {
        if (ids == null || ids.isEmpty() || menge <= 0) {
            return 0;
        }
        verfall(nr);
        int rest = menge;
        for (Iterator<Posten> it = unbelegt.iterator(); it.hasNext() && rest > 0; ) {
            Posten g = it.next();
            if (ids.contains(g.ids.get(0))) {
                int n = Math.min(g.menge, rest);
                g.menge -= n;
                rest -= n;
                if (g.menge <= 0) {
                    it.remove();
                }
            }
        }
        if (rest > 0) {
            offen.add(new Posten(List.copyOf(ids), rest, nr));
        }
        return menge - rest;
    }

    /**
     * Der Kern hat einen Fund gezaehlt.
     *
     * @return wie viel davon noch zaehlt: weniger als {@code menge}, wenn ein Nachtrag aus der Chatzeile den Fund schon gebucht hat
     */
    public int fund(String id, int menge, long nr) {
        if (id == null || menge <= 0) {
            return menge;
        }
        verfall(nr);
        int rest = menge;
        // 1. Eine wartende Zeile: der Fund belegt sie und zaehlt normal, mit seiner genauen Kennung
        rest -= abziehen(offen, id, rest);
        // 2. Eine schon nachgetragene Zeile: gebucht ist gebucht, der Fund kommt nur spaet
        int nachgebucht = 0;
        for (Iterator<Posten> it = nachgetragen.iterator(); it.hasNext() && rest - nachgebucht > 0; ) {
            Posten b = it.next();
            if (b.ids.contains(id)) {
                int n = Math.min(b.menge, rest - nachgebucht);
                b.menge -= n;
                nachgebucht += n;
                int abstand = (int) Math.min(Integer.MAX_VALUE, nr - b.nr);
                if (abstand > fenster) {
                    fenster = abstand;
                }
                if (b.menge <= 0) {
                    it.remove();
                }
            }
        }
        rest -= nachgebucht;
        // 3. Was keine Zeile belegt hat, merkt sich der Rueckblick der naechsten Zeile
        if (rest > 0) {
            unbelegt.add(new Posten(List.of(id), rest, nr));
        }
        return menge - nachgebucht;
    }

    /**
     * Schliesst die Zeilen, auf deren Platz-Update lange genug vergeblich gewartet wurde.
     *
     * @param nr Paketnummer des letzten Pakets
     * @return die Zeilen, die nachzutragen sind
     */
    public List<Rettung> takt(long nr) {
        return schliessen(nr, false);
    }

    /** Beim Verlassen der Welt: alles, was noch wartet, gilt als nicht mehr belegbar. */
    public List<Rettung> alleSchliessen(long nr) {
        List<Rettung> r = schliessen(nr, true);
        unbelegt.clear();
        nachgetragen.clear();
        return r;
    }

    private List<Rettung> schliessen(long nr, boolean alle) {
        if (offen.isEmpty()) {
            return List.of();
        }
        List<Rettung> out = new ArrayList<>(2);
        for (Iterator<Posten> it = offen.iterator(); it.hasNext(); ) {
            Posten o = it.next();
            if (alle || nr - o.nr > fenster) {
                out.add(new Rettung(o.ids, o.menge, o.nr));
                if (!alle) {
                    nachgetragen.add(new Posten(o.ids, o.menge, o.nr));
                }
                it.remove();
            }
        }
        return out;
    }

    private static int abziehen(List<Posten> liste, String id, int menge) {
        int genommen = 0;
        for (Iterator<Posten> it = liste.iterator(); it.hasNext() && genommen < menge; ) {
            Posten o = it.next();
            if (o.ids.contains(id)) {
                int n = Math.min(o.menge, menge - genommen);
                o.menge -= n;
                genommen += n;
                if (o.menge <= 0) {
                    it.remove();
                }
            }
        }
        return genommen;
    }

    /**
     * So lange bleibt ein Nachtrag stehen, um sein verspaetetes Platz-Update zu erkennen: mindestens {@code spaet}, und immer doppelt so
     * lange wie das Fenster. Mit festem {@code spaet} konnte das Fenster nie ueber {@code spaet} hinauslernen (ein Update spaeter als
     * {@code spaet} fand seinen Nachtrag nicht mehr und zaehlte doppelt, jedes Mal). So waechst die Aufbewahrung mit dem Fenster.
     */
    private long behalten() {
        return Math.max(spaet, 2L * fenster);
    }

    private void verfall(long nr) {
        unbelegt.removeIf(g -> nr - g.nr > fenster);
        long frist = behalten();
        nachgetragen.removeIf(b -> nr - b.nr > frist);
    }

    /** Wie viele Zeilen gerade auf ihr Platz-Update warten (Diagnose, Tests). */
    public int wartend() {
        return offen.size();
    }

    // ------------------------------------------------------------------ Stash

    /** Die Zeile "transferred your items from this stash to your sacks" steht im Chat. */
    public void stashZeile(long nanos) {
        stashDa = true;
        stashNanos = nanos;
    }

    /**
     * Gehoert diese Sack-Meldung dem Stash? Ja, wenn die Stash-Zeile in dem Zeitraum liegt, den die Meldung zusammenfasst.
     *
     * @param nanos       Uhr der Meldung
     * @param fensterNanos der Zeitraum, den die Meldung nennt ("Last 5s")
     */
    public boolean stashGehoert(long nanos, long fensterNanos) {
        return stashDa && nanos >= stashNanos && nanos - stashNanos <= fensterNanos;
    }

    /** Beim Verlassen der Welt. */
    public void leeren() {
        offen.clear();
        unbelegt.clear();
        nachgetragen.clear();
        stashDa = false;
    }
}

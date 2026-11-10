package com.shokiteufel.shokimod.dungeonkarte;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Mitspieler eines Dungeons - auch die, die nicht in der Naehe stehen. Das Spiel kennt nur Spieler, die im Sichtbereich geladen sind; wer
 * weiter weg ist, fehlte deshalb auf der Funny Map. Hypixel legt aber alle Mitspieler als Markierungen in die Karte im Hotbar-Platz 9 (die
 * Kopf-Pfeile der gewoehnlichen Karte). Von dort kommt ihr Ort, wie bei NoammAddons (CC0, {@code MapUpdater.updatePlayers}):
 * <ul>
 *   <li>Die Mitspieler stehen in der Tab-Liste ("[420] [MVP+] Name (Mage XXV)", "(DEAD)" statt der Klasse bei einem Toten), in der
 *       Reihenfolge, in der sie zuerst dort erschienen.</li>
 *   <li>Die Markierung "player-N" der Karte gehoert dem N-ten <b>lebenden</b> Mitspieler ohne einen selbst (N ist die letzte Ziffer ihres
 *       Schluessels); die Markierung vom Typ Rahmen ist man selbst.</li>
 *   <li>Ort und Blickrichtung: Kartenpunkt = (x + 128) / 2, Drehung = rot * 22,5 Grad.</li>
 * </ul>
 * Die Karte kommt nur ein paar Mal je Sekunde; damit ein Kopf nicht ruckt, geht er in 350 ms vom alten zum neuen Ort (wie bei Noamm).
 *
 * <p>Dieser Teil rechnet nur; das Lesen der Karte und der Tab-Liste steht in {@link FunnyKarte}.
 */
public final class Mitspieler {

    private static final Pattern ZEILE = Pattern.compile("^\\[\\d+\\] (?:\\[[^\\]]+\\] )*([A-Za-z0-9_]{1,16}) .*\\((\\w+)(?: (\\w+))?\\)$");
    /** So lange dauert der Weg vom alten zum neuen Ort */
    static final long GLEITEN_NANOS = 350_000_000L;

    private static final List<Eintrag> TEAM = new ArrayList<>();
    private static final List<Eintrag> TEAM_ANSICHT = Collections.unmodifiableList(TEAM);

    private Mitspieler() {
    }

    /** Eine Markierung der Karte: {@code rahmen} ist man selbst; x, y sind die Rohwerte (-128 bis 127), rot die Drehung (0 bis 15) */
    public record Marke(boolean rahmen, int x, int y, int rot) {
    }

    /** Ein Mitspieler: Name, Zustand und der Ort (Welt, Bloecke) und die Blickrichtung (Grad) aus der Karte */
    public static final class Eintrag {
        public final String name;
        public boolean tot;
        public boolean ich;
        /** Ob schon ein Ort aus der Karte da ist */
        public boolean hatOrt;
        private float vonX;
        private float vonZ;
        private float vonGier;
        private float zielX;
        private float zielZ;
        private float zielGier;
        private long start;
        /** Der Ort und die Richtung jetzt (von {@link #fortschreiben} gesetzt) */
        public float x;
        public float z;
        public float gier;
        /** Wofuer das Bild des Kopfes gelesen wurde und wann - das Bild kommt aus der Spielerliste und aendert sich selten */
        public Object haut;
        public long hautGelesen;

        Eintrag(String name) {
            this.name = name;
        }

        /** Ein neuer Ort aus der Karte: der Kopf gleitet vom Ort, an dem er gerade zu sehen ist, dorthin */
        void ziel(float neuX, float neuZ, float neuGier, long jetzt) {
            if (!hatOrt) {
                vonX = neuX;
                vonZ = neuZ;
                vonGier = neuGier;
                x = neuX;
                z = neuZ;
                gier = neuGier;
                hatOrt = true;
            } else {
                if (neuX == zielX && neuZ == zielZ && neuGier == zielGier) return;
                fortschreiben(jetzt);
                vonX = x;
                vonZ = z;
                vonGier = gier;
            }
            zielX = neuX;
            zielZ = neuZ;
            zielGier = neuGier;
            start = jetzt;
        }

        /** Setzt {@link #x}, {@link #z} und {@link #gier} fuer den Zeitpunkt {@code jetzt} (Nanosekunden) */
        public void fortschreiben(long jetzt) {
            if (!hatOrt) return;
            float p = Math.min(1f, Math.max(0f, (jetzt - start) / (float) GLEITEN_NANOS));
            x = vonX + (zielX - vonX) * p;
            z = vonZ + (zielZ - vonZ) * p;
            gier = vonGier + kuerzesterWinkel(zielGier - vonGier) * p;
        }
    }

    /** Der Winkelunterschied in Grad auf dem kuerzesten Weg: -180 bis 180 */
    static float kuerzesterWinkel(float unterschied) {
        float u = unterschied % 360f;
        if (u >= 180f) u -= 360f;
        if (u < -180f) u += 360f;
        return u;
    }

    // ------------------------------------------------------------------ Tab-Liste

    /**
     * Die Mitspieler aus den Zeilen der Tab-Liste: neue kommen hinten an (Reihenfolge des ersten Erscheinens), bekannte werden auf lebend oder
     * tot gestellt. {@code eigenerName} ist man selbst.
     */
    public static void tabLesen(List<String> zeilen, String eigenerName) {
        if (zeilen == null) return;
        for (String z : zeilen) {
            if (z == null) continue;
            Matcher m = ZEILE.matcher(z.strip());
            if (!m.matches()) continue;
            String name = m.group(1);
            boolean tot = "DEAD".equals(m.group(2));
            Eintrag e = finden(name);
            if (e == null) {
                e = new Eintrag(name);
                TEAM.add(e);
            }
            e.tot = tot;
            e.ich = name.equals(eigenerName);
        }
    }

    private static Eintrag finden(String name) {
        for (Eintrag e : TEAM) {
            if (e.name.equals(name)) return e;
        }
        return null;
    }

    // ------------------------------------------------------------------ Karte

    /**
     * Die Markierungen der Karte den Mitspielern zuordnen und ihre Orte setzen.
     *
     * @param marken Schluessel ("player-0" ...) -> Markierung, in der Reihenfolge der Karte
     * @param k      die Eichung der Karte; ohne sie ist kein Ort zu rechnen
     * @param jetzt  Nanosekunden (System.nanoTime)
     * @return wie vielen Mitspielern ein Ort gegeben wurde
     */
    public static int markenLesen(Map<String, Marke> marken, KartenLeser.Kalibrierung k, long jetzt) {
        if (marken == null || k == null) return 0;
        List<Eintrag> lebende = new ArrayList<>();
        for (Eintrag e : TEAM) {
            if (!e.tot && !e.ich) lebende.add(e);
        }
        int gesetzt = 0;
        for (Map.Entry<String, Marke> m : marken.entrySet()) {
            Marke marke = m.getValue();
            if (marke.rahmen()) continue;
            String schluessel = m.getKey();
            if (schluessel == null || schluessel.isEmpty()) continue;
            int index = Character.digit(schluessel.charAt(schluessel.length() - 1), 10);
            if (index < 0 || index >= lebende.size()) continue;
            Eintrag e = lebende.get(index);
            int kartenX = (marke.x() + 128) >> 1;
            int kartenZ = (marke.y() + 128) >> 1;
            e.ziel(weltX(kartenX, k), weltZ(kartenZ, k), marke.rot() * 22.5f, jetzt);
            gesetzt++;
        }
        return gesetzt;
    }

    /** Die Weltkoordinate (Bloecke, x) eines Kartenpunkts: die Umkehrung dessen, wie die Karte die Welt abbildet (Raumgroesse plus 4 Punkte je 32 Bloecke) */
    static float weltX(int kartenPunkt, KartenLeser.Kalibrierung k) {
        return (kartenPunkt - KartenGeometrie.noammEcke(k.startX())) / malFaktor(k) + Raster.START - 15;
    }

    static float weltZ(int kartenPunkt, KartenLeser.Kalibrierung k) {
        return (kartenPunkt - KartenGeometrie.noammEcke(k.startY())) / malFaktor(k) + Raster.START - 15;
    }

    /** Kartenpunkte je Block */
    static float malFaktor(KartenLeser.Kalibrierung k) {
        return (k.raumGroesse() + 4f) / Raster.RAUM;
    }

    // ------------------------------------------------------------------ Zugriff fuer die Anzeige

    /** Die Mitspieler in der Reihenfolge der Tab-Liste (nicht aendern) */
    public static List<Eintrag> eintraege() {
        return TEAM_ANSICHT;
    }

    /** Alles vergessen (neuer Lauf, andere Welt) */
    public static void zuruecksetzen() {
        TEAM.clear();
    }
}

package com.shokiteufel.shokimod.dungeonkarte;

import static com.shokiteufel.shokimod.dungeonkarte.Raster.KANTE;

/**
 * Der Welt-Scan: aus den Chunks, die der Server schon geschickt hat, entsteht das Raster des ganzen Dungeons - auch der Raeume, die
 * noch niemand betreten hat. Das ist der Kern der Funny Map.
 *
 * Je Rasterplatz (an der Weltstelle {@link Raster#weltX}/{@link Raster#weltZ}) gilt, wenn sein Chunk geladen ist:
 * <ul>
 *   <li>gerade Zeile und gerade Spalte: ein Raum - sein {@link KernHash} nennt in der {@link RaumTabelle} den Raum; kennt sie ihn nicht,
 *       bleibt der Platz offen und wird beim naechsten Durchgang noch einmal versucht,</li>
 *   <li>ungerade Zeile und Spalte: die Mitte eines 2 x 2 grossen Raums (nur, wenn links oben ein Raum steht),</li>
 *   <li>sonst: liegt das Dach auf Hoehe 73, 74, 81 oder 82, ist es eine Tuer (ihre Art sagt der Block auf Hoehe 69), sonst die Verbindung
 *       zweier Kacheln desselben grossen Raums.</li>
 * </ul>
 * Die Rechnung ist die von NoammAddons (CC0). An Schiggys Dungeon-Lauf (09.11., Floor V) nachgerechnet: 27 von 36 Raumplaetzen erkannt.
 */
public final class WeltScanner {

    /** Was der Scan von der Welt wissen muss - getrennt von Minecraft, damit er sich mit einer gebauten Welt pruefen laesst */
    public interface Welt {
        boolean chunkGeladen(int x, int z);

        /** Hoehe des obersten Blocks, der weder Luft noch ein Goldblock ist; 0, wenn die Saeule leer ist */
        int hoechsterBlock(int x, int z);

        /** Der {@link KernHash} der Saeule an dieser Stelle */
        int kernHash(int x, int z);

        /** Der Registername des Blocks an dieser Stelle ("minecraft:air", "minecraft:coal_block" ...) */
        String blockName(int x, int y, int z);
    }

    private static final int TUER_HOEHE = 69;

    private final RaumTabelle tabelle;
    private final Raster raster;
    private boolean alleChunks;
    /**
     * Ein Platz, dessen Chunk geladen ist und der trotzdem nichts ergab (leere Saeule, Raum, den die Tabelle nicht kennt, Tuer ohne Nachbarn),
     * wird nicht bei jedem Durchgang neu gefragt: Jede Abfrage ist eine Hoehenabfrage von bis zu 257 Bloecken, und die Antwort aendert sich nicht.
     * Hier steht je Platz der Durchgang, ab dem er wieder gefragt wird. Bringt ein Durchgang etwas Neues, gilt das nicht mehr (siehe
     * {@link #durchgang}): Ein neuer Raum kann einer Tuer den Nachbarn liefern.
     */
    private final int[] ruhtBis = new int[Raster.PLAETZE];
    private int durchgaenge;
    /**
     * Sind alle Chunks des Rasters geladen und hat der Scan zweimal hintereinander nichts Neues gefunden, ist er fertig: NoammAddons macht es
     * ebenso ({@code hasScanned}). Danach fragt {@link #durchgang} die Welt nicht mehr. Eine neue Welt braucht einen neuen Scanner.
     */
    private boolean fertig;
    private int ruhigeDurchgaenge;
    /** Wie viele Durchgaenge (je ein viertel Sekunde) ein erfolgloser Platz ruht: etwa drei Sekunden */
    static final int RUHE = 12;
    private final java.util.Map<Integer, Integer> unbekannteKerne = new java.util.TreeMap<>();

    /** Raumplaetze, deren Chunk geladen ist, deren Hash die Tabelle aber nicht kennt: Platz (Zeile mal 11 plus Spalte) -> Hash */
    public java.util.Map<Integer, Integer> unbekannteKerne() {
        return java.util.Collections.unmodifiableMap(unbekannteKerne);
    }

    public WeltScanner(RaumTabelle tabelle, Raster raster) {
        this.tabelle = tabelle;
        this.raster = raster;
    }

    /** Waren beim letzten Durchgang alle Chunks des Rasters geladen? Dann gibt es nichts mehr nachzutragen, ausser unbekannten Raeumen */
    public boolean alleChunksGeladen() {
        return alleChunks;
    }

    /** Ist der Scan abgeschlossen (alle Chunks geladen, zweimal nichts Neues)? Dann kostet {@link #durchgang} nichts mehr */
    public boolean fertig() {
        return fertig;
    }

    /**
     * Ein Durchgang ueber das Raster. Bekannte Plaetze werden uebersprungen; was noch fehlt, kommt hinzu, sobald sein Chunk da ist.
     *
     * @return wie viele Plaetze neu belegt wurden
     */
    public int durchgang(Welt welt) {
        if (fertig) return 0;
        int neu = 0;
        boolean alle = true;
        durchgaenge++;
        for (int x = 0; x < KANTE; x++) {
            for (int z = 0; z < KANTE; z++) {
                int wx = Raster.weltX(x);
                int wz = Raster.weltZ(z);
                if (!welt.chunkGeladen(wx, wz)) {
                    alle = false;
                    continue;
                }
                int platz = z * KANTE + x;
                Kachel schon = raster.get(platz);
                if (schon != null && !(schon.art == Kachel.Art.RAUM && schon.daten != null && schon.daten.istUnbekannt())) continue;
                if (ruhtBis[platz] > durchgaenge) continue;
                int dach = welt.hoechsterBlock(wx, wz);
                if (dach <= 0) {
                    ruhtBis[platz] = durchgaenge + RUHE;
                    continue;
                }
                Kachel k = kachel(welt, wx, wz, z, x, dach);
                if (k != null) {
                    raster.set(platz, k);
                    neu++;
                } else {
                    ruhtBis[platz] = durchgaenge + RUHE;
                }
            }
        }
        alleChunks = alle;
        // Etwas Neues kann den ruhenden Plaetzen helfen (eine Tuer bekommt ihren Nachbarraum): dann alle wieder fragen
        if (neu > 0) java.util.Arrays.fill(ruhtBis, 0);
        if (alle && neu == 0) {
            if (++ruhigeDurchgaenge >= 2) fertig = true;
        } else {
            ruhigeDurchgaenge = 0;
        }
        return neu;
    }

    private Kachel kachel(Welt welt, int wx, int wz, int zeile, int spalte, int dach) {
        boolean zeileGerade = (zeile & 1) == 0;
        boolean spalteGerade = (spalte & 1) == 0;

        if (zeileGerade && spalteGerade) {
            int hash = welt.kernHash(wx, wz);
            RaumDaten daten = tabelle.nachKern(hash);
            int platz = zeile * KANTE + spalte;
            if (daten == null) {
                // Fuer das Log: so sieht man, welche Raeume der Tabelle fehlen
                unbekannteKerne.put(platz, hash);
                return null;
            }
            unbekannteKerne.remove(platz);
            return Kachel.raum(wx, wz, daten);
        }
        if (!zeileGerade && !spalteGerade) {
            // Die Mitte eines 2 x 2 grossen Raums: gehoert zum Raum links oben
            Kachel links = raster.get((zeile - 1) * KANTE + (spalte - 1));
            return links != null && links.istRaumTeil() ? Kachel.trenner(wx, wz, links.daten) : null;
        }
        if (dach == 73 || dach == 74 || dach == 81 || dach == 82) {
            return Kachel.tuer(wx, wz, TuerTyp.vonBlock(welt.blockName(wx, TUER_HOEHE, wz)));
        }
        // Verbindung zweier Kacheln eines grossen Raums
        Kachel nachbar = raster.get(zeileGerade ? zeile * KANTE + spalte - 1 : (zeile - 1) * KANTE + spalte);
        if (nachbar == null || !nachbar.istRaumTeil()) return null;
        if (nachbar.daten != null && nachbar.daten.typ() == RaumTyp.EINGANG) return Kachel.tuer(wx, wz, TuerTyp.EINGANG);
        return Kachel.trenner(wx, wz, nachbar.daten);
    }

    /**
     * Merkt, welche Wither- und Blut-Tueren schon offen sind: Dort, wo auf Hoehe 69 Luft oder eine Barriere steht, ist die Tuer
     * aufgemacht. Nur fuer Plaetze, deren Chunk geladen ist.
     */
    public void tuerenPruefen(Welt welt) {
        for (int i = 0; i < Raster.PLAETZE; i++) {
            Kachel k = raster.get(i);
            if (k == null || k.art != Kachel.Art.TUER || k.offen) continue;
            if (k.tuer != TuerTyp.WITHER && k.tuer != TuerTyp.BLUT && k.tuer != TuerTyp.EINGANG) continue;
            if (!welt.chunkGeladen(k.x, k.z)) continue;
            String block = welt.blockName(k.x, TUER_HOEHE, k.z);
            if ("minecraft:air".equals(block) || "minecraft:cave_air".equals(block) || "minecraft:barrier".equals(block)) {
                k.offen = true;
                raster.geaendert();
            }
        }
    }
}

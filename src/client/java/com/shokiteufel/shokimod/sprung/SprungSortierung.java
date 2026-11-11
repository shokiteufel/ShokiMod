package com.shokiteufel.shokimod.sprung;

import com.shokiteufel.shokimod.data.ModConfig.LeapCustomType;
import com.shokiteufel.shokimod.data.ModConfig.LeapOrder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Die Reihenfolge der Mitspieler im Leap Menu, eins zu eins nach {@code LeapMenu.sortTeammates} und {@code odinSorting} von NoammAddons (CC0).
 * Reine Rechnung.
 */
public final class SprungSortierung {

    /** Die Plaetze, die Odin je eigener Klasse vorsieht: erst die Klasse an Platz 1, dann die an Platz 2 und so weiter */
    private static final Map<SprungKlasse, List<SprungKlasse>> ODIN = Map.of(
            SprungKlasse.ARCHER, List.of(SprungKlasse.MAGE, SprungKlasse.BERSERK, SprungKlasse.HEALER, SprungKlasse.TANK),
            SprungKlasse.MAGE, List.of(SprungKlasse.ARCHER, SprungKlasse.BERSERK, SprungKlasse.HEALER, SprungKlasse.TANK),
            SprungKlasse.BERSERK, List.of(SprungKlasse.ARCHER, SprungKlasse.MAGE, SprungKlasse.HEALER, SprungKlasse.TANK),
            SprungKlasse.HEALER, List.of(SprungKlasse.ARCHER, SprungKlasse.BERSERK, SprungKlasse.MAGE, SprungKlasse.TANK),
            SprungKlasse.TANK, List.of(SprungKlasse.ARCHER, SprungKlasse.BERSERK, SprungKlasse.HEALER, SprungKlasse.MAGE));

    private SprungSortierung() {
    }

    /**
     * @param spieler     wer sortiert werden soll (bleibt unveraendert)
     * @param modus       die Wahl "Leap Order"
     * @param eigene      die eigene Klasse (fuer Odin), oder null
     * @param typ         ob die eigene Reihenfolge Namen oder Klassen nennt
     * @param reihenfolge die eigene Reihenfolge, klein geschrieben (siehe {@link #liste})
     */
    public static List<SprungSpieler> sortieren(List<SprungSpieler> spieler, LeapOrder modus, SprungKlasse eigene, LeapCustomType typ,
                                                List<String> reihenfolge) {
        List<SprungSpieler> aus = new ArrayList<>(spieler);
        switch (modus) {
            case CLASS -> aus.sort(Comparator.<SprungSpieler>comparingInt(s -> s.klasse.ordinal()).thenComparing(s -> s.name));
            case NAME -> aus.sort(Comparator.comparing(s -> s.name));
            case ODIN -> {
                return odin(spieler, eigene);
            }
            case CUSTOM -> aus.sort(eigene(typ, reihenfolge));
            case NONE -> {
                // die Reihenfolge der Tab-Liste
            }
        }
        return aus;
    }

    /**
     * Die eigene Reihenfolge: wer in der Liste steht, kommt vor allen anderen, und zwar in ihrer Reihenfolge; wer gleich weit vorn steht (oder gar
     * nicht), nach Name oder Klasse A-Z.
     */
    private static Comparator<SprungSpieler> eigene(LeapCustomType typ, List<String> reihenfolge) {
        boolean nachName = typ == LeapCustomType.NAME;
        return (a, b) -> {
            String ka = schluessel(a, nachName);
            String kb = schluessel(b, nachName);
            int ia = reihenfolge.indexOf(ka);
            int ib = reihenfolge.indexOf(kb);
            int pa = ia == -1 ? Integer.MAX_VALUE : ia;
            int pb = ib == -1 ? Integer.MAX_VALUE : ib;
            if (pa != pb) return Integer.compare(pa, pb);
            return ka.compareTo(kb);
        };
    }

    private static String schluessel(SprungSpieler s, boolean nachName) {
        return nachName ? s.name.toLowerCase(Locale.ROOT) : s.klasse.klein();
    }

    /**
     * Odins Reihenfolge: Die vier Plaetze gehoeren je einer Klasse (nach der eigenen Klasse); wer dort nicht hinpasst oder dessen Platz schon
     * besetzt ist, rueckt in die freien nach. Ohne bekannte eigene Klasse bleibt es, wie es ist.
     */
    static List<SprungSpieler> odin(List<SprungSpieler> spieler, SprungKlasse eigene) {
        List<SprungKlasse> noetig = eigene == null ? null : ODIN.get(eigene);
        if (noetig == null) return new ArrayList<>(spieler);

        SprungSpieler[] viertel = new SprungSpieler[4];
        List<SprungSpieler> zweite = new ArrayList<>();
        for (SprungSpieler s : spieler) {
            int bevorzugt = noetig.indexOf(s.klasse);
            if (bevorzugt != -1 && bevorzugt < 4 && viertel[bevorzugt] == null) {
                viertel[bevorzugt] = s;
            } else {
                zweite.add(s);
            }
        }
        for (int i = 0; i < viertel.length; i++) {
            if (viertel[i] == null && !zweite.isEmpty()) viertel[i] = zweite.remove(0);
        }
        List<SprungSpieler> aus = new ArrayList<>(spieler.size());
        for (SprungSpieler s : viertel) {
            if (s != null) aus.add(s);
        }
        aus.addAll(zweite);
        return aus;
    }

    /** Die Zeile "Anna, bob ,, Mage" als Liste in Kleinbuchstaben, ohne Leeres: [anna, bob, mage] */
    public static List<String> liste(String text) {
        List<String> aus = new ArrayList<>();
        if (text == null) return aus;
        for (String teil : text.split(",")) {
            String t = teil.strip().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) aus.add(t);
        }
        return aus;
    }
}

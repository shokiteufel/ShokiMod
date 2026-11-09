package com.shokiteufel.shokimod.scanner;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Was die Sack-Meldung nicht aufzaehlt. Hypixel listet an ihrem Mauszeiger hoechstens etwa fuenfzehn Posten, den Rest nennt es nur als
 * Zahl ("+36 other items."). Gemessen an Schiggys Aufnahme vom 09.11. (13:15 bis 14:46): 26 von 108 Meldungen waren abgeschnitten, im
 * Ganzen 1,9 Prozent der Ware, in der Angelphase bis zu 28 Prozent einer Meldung.
 *
 * <p>Die Zeilen im Chat ({@link ZeilenFunde}) nennen oft genau diese Ware - bei den Pest-Traps jeden Kill einzeln. Hier werden die Ansprueche
 * der Zeilen gegen die abgeschnittene Menge gelegt. Nichts wird doppelt gebucht, weil
 * <ul>
 *   <li>ein Anspruch auf eine Ware, die die Aufstellung nennt, nie zaehlt (die Aufstellung ist genau);</li>
 *   <li>von jedem Anspruch abgeht, was ein anderer Weg im selben Fenster schon gebucht hat (Inventar, Drop-Zeile, Leichen-Beute);</li>
 *   <li>zusammen hoechstens die abgeschnittene Menge dazukommt;</li>
 *   <li>ein Name, der in der Warenliste nicht steht, nichts bucht.</li>
 * </ul>
 * Was dann von der abgeschnittenen Menge bleibt, ist wirklich unbekannt und wird als solches gemeldet, statt zu verschwinden.
 */
public final class SackErgaenzung {
    private SackErgaenzung() {
    }

    /**
     * @param dazu      Kennung -> Menge, die zusaetzlich zu den aufgezaehlten Posten gebucht wird
     * @param ungeklaert so viele Stueck der abgeschnittenen Menge sind durch keine Zeile belegt
     */
    public record Ergebnis(Map<String, Integer> dazu, int ungeklaert) {
    }

    /**
     * @param versteckt     die Menge der "other items" der Meldung
     * @param gelistet      die Namen, die die Aufstellung nennt
     * @param ansprueche    die Ansprueche der Chatzeilen im Fenster der Meldung (aeltester zuerst)
     * @param ids           Name -> Kennungen der Warenliste, leer wenn unbekannt
     * @param schonGebucht  Kennung -> Menge, die andere Wege im selben Fenster schon gebucht haben; wird dabei verbraucht
     */
    public static Ergebnis ergaenzen(int versteckt, Collection<String> gelistet, List<ZeilenFunde.Anspruch> ansprueche,
                                     Function<String, List<String>> ids, Map<String, Integer> schonGebucht) {
        Map<String, Integer> dazu = new LinkedHashMap<>();
        if (versteckt <= 0) {
            return new Ergebnis(dazu, 0);
        }
        Set<String> gelisteteIds = new LinkedHashSet<>();
        for (String name : gelistet) {
            gelisteteIds.addAll(ids.apply(name));
        }
        int rest = versteckt;
        for (ZeilenFunde.Anspruch a : ansprueche) {
            if (rest <= 0) {
                break;
            }
            List<String> kennungen = ids.apply(a.name());
            if (kennungen.isEmpty() || sichtbar(kennungen, gelisteteIds)) {
                continue;
            }
            int offen = a.menge();
            // Was ein anderer Weg schon gebucht hat, ist dieser Anspruch selbst: erst das abziehen
            for (String id : kennungen) {
                Integer schon = schonGebucht.get(id);
                if (schon != null && schon > 0 && offen > 0) {
                    int genommen = Math.min(schon, offen);
                    schonGebucht.put(id, schon - genommen);
                    offen -= genommen;
                }
            }
            int mehr = Math.min(offen, rest);
            if (mehr > 0) {
                dazu.merge(kennungen.get(0), mehr, Integer::sum);
                rest -= mehr;
            }
        }
        return new Ergebnis(dazu, rest);
    }

    private static boolean sichtbar(List<String> kennungen, Set<String> gelisteteIds) {
        for (String id : kennungen) {
            if (gelisteteIds.contains(id)) {
                return true;
            }
        }
        return false;
    }
}

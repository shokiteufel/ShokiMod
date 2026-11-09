package com.shokiteufel.shokimod.scanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Eine Drop-Zeile, die das Inventar nicht belegt hat und deshalb aus dem Chat gezaehlt wurde ({@code FundBuch.Rettung}), bekommt eine
 * zweite Pruefung: Hat die Ware ihr Ziel erreicht? Ein "RARE DROP!" nennt, was gefallen ist, nicht, was beim Spieler ankam. Der Personal
 * Deletor zerstoert gelistete Ware im Augenblick des Aufsammelns (Schiggy, 09.11.: "Rampart Helmet" und "Rampart Leggings" standen im Chat und
 * im Tracker, waren aber nie im Inventar).
 *
 * <p>Die Ware kommt entweder ins Inventar (dann sah der Kern einen Zugang dieser Ware) oder direkt in einen Sack (dann nennt die naechste
 * Sack-Meldung sie, vom Vinyl bis zum Enchanted Cactus). Die erste Sack-Meldung, die das Fenster der Zeile deckt, entscheidet:
 * <ul>
 *   <li>Die Aufstellung nennt die Ware, oder der Kern sah einen Zugang: bestaetigt.</li>
 *   <li>Die Meldung hat einen abgeschnittenen Rest ("+N other items"): unentschieden, die Zeile bleibt gezaehlt (im Zweifel fuer den Fund).</li>
 *   <li>Sonst: widerrufen - die Ware kam nirgends an.</li>
 * </ul>
 * Ohne Sack-Meldung (der Spieler hat sie ausgeschaltet) wird nie widerrufen: es gibt dann kein Urteil.
 */
public final class DropWiderruf {
    /**
     * So lange darf eine nachgetragene Zeile einer Ware warten, die nie in einen Sack faellt (Ruestung, Waffe, Pet): Sie kommt ins Inventar
     * oder gar nicht, ein Urteil einer Sack-Meldung braucht es nicht (Schiggy, 09.11. 18:15: die Rampart-Teile standen bis zur naechsten
     * Sack-Meldung im Tracker, obwohl der Deletor sie schon vernichtet hatte).
     */
    public static final long GERAET_FRIST_NANOS = 6_000_000_000L;
    /**
     * So lange wartet eine Zeile einer Ware, die auch in einen Sack fallen kann, auf ihre Sack-Meldung (sie kommt bis zu dreissig Sekunden
     * spaeter). Danach gilt: Wer Sack-Meldungen sieht und keine nannte die Ware, hat sie nie bekommen.
     */
    public static final long SACK_FRIST_NANOS = 60_000_000_000L;

    /** Kennungen, die nie in einem Sack liegen: Ruestungsteile, Waffen und Werkzeuge, Pets ("BABY_YETI;0") */
    private static final java.util.regex.Pattern NIE_IM_SACK = java.util.regex.Pattern.compile(
            ".*_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)|.*_(SWORD|BOW|ROD|PICKAXE|DRILL|AXE|SCYTHE|DAGGER|STAFF|WAND)|.*;\\d");

    private DropWiderruf() {
    }

    /** Sind alle Kennungen, die der Name haben kann, Dinge, die nie in einen Sack fallen? */
    public static boolean nieImSack(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        for (String id : ids) {
            if (!NIE_IM_SACK.matcher(id).matches()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Das Urteil nach Ablauf der Frist, ohne dass eine Sack-Meldung die Zeile gedeckt haette. Eine Ware, die nie in einen Sack faellt, wartet
     * {@link #GERAET_FRIST_NANOS}, jede andere {@link #SACK_FRIST_NANOS} - und nur, wenn der Spieler Sack-Meldungen sieht (sonst gibt es nie
     * ein Urteil). Sah der Kern einen Zugang der Ware, ist sie angekommen; sonst war sie weg.
     */
    public static Urteil veraltet(List<Offen> offene, long jetzt, boolean sackMeldungGesehen, Predicate<Offen> geraet,
                                  Predicate<Offen> zugangGesehen) {
        List<Offen> bestaetigt = new ArrayList<>();
        List<Offen> widerrufen = new ArrayList<>();
        for (Offen o : offene) {
            long alter = jetzt - o.nanos();
            boolean frist = geraet.test(o) ? alter >= GERAET_FRIST_NANOS : (sackMeldungGesehen && alter >= SACK_FRIST_NANOS);
            if (!frist) {
                continue;
            }
            if (zugangGesehen.test(o)) {
                bestaetigt.add(o);
            } else {
                widerrufen.add(o);
            }
        }
        return new Urteil(bestaetigt, widerrufen);
    }

    /**
     * @param gebuchtAls die Kennung, unter der die Zeile gezaehlt wurde
     * @param ids        alle Kennungen, die der Name haben kann (die Sack-Meldung kennt die Ware unter einer davon)
     * @param nanos      wann die Zeile nachgetragen wurde
     */
    public record Offen(String gebuchtAls, List<String> ids, int menge, long nanos) {
    }

    /** Das Urteil: bestaetigt und widerrufen werden aus dem Buch genommen, der Rest wartet auf eine spaetere Meldung */
    public record Urteil(List<Offen> bestaetigt, List<Offen> widerrufen) {
    }

    /**
     * @param offene        die nachgetragenen Zeilen, deren Urteil noch aussteht
     * @param von           Beginn des Fensters der Sack-Meldung (mit Spielraum), in Nanosekunden
     * @param bis           Ende (die Meldung selbst)
     * @param gelisteteIds  die Kennungen aller Waren, die die Aufstellung der Meldung nennt
     * @param zugangGesehen ob der Kern fuer diese Zeile einen Zugang derselben Ware sah (jede Ursache, jedes Fenster)
     * @param abgeschnitten ob die Meldung einen abgeschnittenen Rest hat
     */
    public static Urteil urteilen(List<Offen> offene, long von, long bis, Set<String> gelisteteIds, Predicate<Offen> zugangGesehen,
                                  boolean abgeschnitten) {
        List<Offen> bestaetigt = new ArrayList<>();
        List<Offen> widerrufen = new ArrayList<>();
        for (Offen o : offene) {
            // Spaeter als die Meldung: ein spaeteres Fenster urteilt. Frueher als ihr Fenster: kein Urteil mehr moeglich, es bleibt gezaehlt
            if (o.nanos() > bis) {
                continue;
            }
            if (o.nanos() < von) {
                bestaetigt.add(o);
                continue;
            }
            boolean genannt = false;
            for (String id : o.ids()) {
                if (gelisteteIds.contains(id)) {
                    genannt = true;
                    break;
                }
            }
            // Eine Ware, die nie in einen Sack faellt, steckt auch nicht im abgeschnittenen Rest: Nur ihr Zugang im Inventar zaehlt
            boolean imSackMoeglich = !nieImSack(o.ids());
            if (zugangGesehen.test(o) || (imSackMoeglich && (genannt || abgeschnitten))) {
                bestaetigt.add(o);
            } else {
                widerrufen.add(o);
            }
        }
        return new Urteil(bestaetigt, widerrufen);
    }
}

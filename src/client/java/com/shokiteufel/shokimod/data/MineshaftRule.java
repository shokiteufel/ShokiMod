package com.shokiteufel.shokimod.data;

import com.google.gson.annotations.Expose;
import com.shokiteufel.shokimod.scanner.MiningState;

import java.util.List;
import java.util.Locale;

/**
 * Wann ein Schacht die Muehe wert ist.
 *
 * Je Bauplan eine Regel: wie viele Leichen welcher Sorte drin sein muessen, damit
 * die Stellen-Marker erscheinen. Null heisst "ist mir egal" - wer nur auf Lapis aus
 * ist, traegt dort eine Zwei ein und laesst den Rest stehen.
 *
 * Ob alle eingetragenen Zahlen erreicht sein muessen oder eine genuegt, entscheidet
 * eine Einstellung daneben. Beides ist sinnvoll: "mindestens zwei Lapis und ein
 * Tungsten" ist eine andere Frage als "irgendetwas Gutes drin".
 */
public class MineshaftRule {

    @Expose
    public int lapis = 0;
    @Expose
    public int umber = 0;
    @Expose
    public int tungsten = 0;
    @Expose
    public int vanguard = 0;

    public MineshaftRule() {
    }

    public MineshaftRule(int lapis, int umber, int tungsten, int vanguard) {
        this.lapis = lapis;
        this.umber = umber;
        this.tungsten = tungsten;
        this.vanguard = vanguard;
    }

    /** Steht ueberhaupt eine Bedingung drin? Eine leere Regel laesst alles durch */
    public boolean empty() {
        return lapis <= 0 && umber <= 0 && tungsten <= 0 && vanguard <= 0;
    }

    public int minimum(String type) {
        return switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
            case "lapis" -> lapis;
            case "umber" -> umber;
            case "tungsten" -> tungsten;
            case "vanguard" -> vanguard;
            default -> 0;
        };
    }

    public void setMinimum(String type, int value) {
        int clamped = Math.max(0, value);
        switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
            case "lapis" -> lapis = clamped;
            case "umber" -> umber = clamped;
            case "tungsten" -> tungsten = clamped;
            case "vanguard" -> vanguard = clamped;
            default -> {
                // Eine fuenfte Sorte gibt es nicht - und wenn doch, wird sie nicht geraten
            }
        }
    }

    /**
     * Passt der Schacht zu dieser Regel?
     *
     * Gezaehlt wird, was der Schacht insgesamt hat, nicht was noch offen ist: Die
     * Frage ist, ob er sich lohnt, nicht wie weit man schon ist.
     *
     * @param all true: jede eingetragene Zahl muss erreicht sein. false: eine genuegt
     */
    public boolean matches(List<MiningState.Corpse> corpses, boolean all) {
        if (empty()) return true;

        boolean irgendeine = false;
        for (String type : TYPES) {
            int needed = minimum(type);
            if (needed <= 0) continue;

            int found = count(corpses, type);
            if (found >= needed) irgendeine = true;
            else if (all) return false;
        }
        return all || irgendeine;
    }

    /** Die vier Sorten, in der Reihenfolge, in der sie im Menue stehen */
    public static final String[] TYPES = {"Lapis", "Umber", "Tungsten", "Vanguard"};

    private static int count(List<MiningState.Corpse> corpses, String type) {
        int sum = 0;
        for (int i = 0; i < corpses.size(); i++) {
            if (corpses.get(i).type().equalsIgnoreCase(type)) sum += corpses.get(i).total();
        }
        return sum;
    }

    /** Eine Zeile fuer das Menue: "2 Lapis, 1 Tungsten" oder "egal" */
    public String describe() {
        if (empty()) return "any";
        StringBuilder out = new StringBuilder();
        for (String type : TYPES) {
            int needed = minimum(type);
            if (needed <= 0) continue;
            if (!out.isEmpty()) out.append(", ");
            out.append(needed).append(' ').append(type);
        }
        return out.toString();
    }
}

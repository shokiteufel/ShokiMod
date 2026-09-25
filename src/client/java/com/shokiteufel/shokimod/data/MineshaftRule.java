package com.shokiteufel.shokimod.data;

import com.google.gson.annotations.Expose;
import com.shokiteufel.shokimod.scanner.MiningState;

import java.util.List;

/**
 * Wann ein Schacht die Muehe wert ist.
 *
 * Eine Zahl und zwei Haken, je Bauplan: ab wie vielen Leichen die Marker erscheinen,
 * und ob Umber und Tungsten dabei mitgezaehlt werden. Null heisst "ist mir egal" -
 * dann zeigt der Bauplan seine Stellen wie vorher.
 *
 * Bis 1.7.8 standen hier vier Mindestzahlen und daneben ein Schalter "alle oder eine".
 * Das war eine Frage zu viel: Gefragt ist "ab zwei Lapis", nicht eine Matrix. Die
 * alten Zahlen bleiben als Felder stehen, damit eine bestehende Config einmal
 * uebernommen werden kann - danach sind sie leer.
 *
 * Vanguard steht nicht mehr dabei: Es gibt ihn nur in einem Bauplan, und dort ist er
 * kein Kriterium, sondern der Grund, ueberhaupt hinzugehen.
 */
public class MineshaftRule {

    /** Ab wie vielen Leichen die Stellen erscheinen. 0 heisst: immer */
    @Expose
    public int min = 0;

    /** Zaehlen Umber-Leichen mit? */
    @Expose
    public boolean withUmber = false;

    /** Zaehlen Tungsten-Leichen mit? */
    @Expose
    public boolean withTungsten = false;

    /**
     * Die vier Zahlen aus 1.7.7/1.7.8.
     *
     * Sie werden nur noch gelesen, einmal, beim Uebernehmen der alten Config. Ihre
     * Namen und Typen bleiben unveraendert - eine Zahl, die ploetzlich ein Haken ist,
     * wuerde Gson beim Einlesen zerreissen und die ganze Config mitnehmen.
     */
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

    public MineshaftRule(int min, boolean withUmber, boolean withTungsten) {
        this.min = min;
        this.withUmber = withUmber;
        this.withTungsten = withTungsten;
    }

    /** Steht ueberhaupt eine Bedingung drin? Eine leere Regel laesst alles durch */
    public boolean empty() {
        return min <= 0;
    }

    /**
     * Die alten vier Zahlen in die neue Form bringen.
     *
     * Die Lapis-Zahl war die gemeinte Schwelle; stand dort nichts, nimmt die groesste
     * der anderen ihren Platz. Welche Sorten mitzaehlen, sagt, wo ueberhaupt eine Zahl
     * stand. Vanguard fliegt dabei heraus - so war er nie gemeint.
     *
     * @return true, wenn etwas uebernommen wurde
     */
    public boolean adopt() {
        if (min > 0 || (lapis <= 0 && umber <= 0 && tungsten <= 0 && vanguard <= 0)) return false;

        min = lapis > 0 ? lapis : Math.max(umber, Math.max(tungsten, vanguard));
        withUmber = umber > 0;
        withTungsten = tungsten > 0;
        lapis = umber = tungsten = vanguard = 0;
        return true;
    }

    /**
     * Passt der Schacht zu dieser Regel?
     *
     * Gezaehlt wird, was der Schacht insgesamt hat, nicht was noch offen ist: Die
     * Frage ist, ob er sich lohnt, nicht wie weit man schon ist.
     */
    public boolean matches(List<MiningState.Corpse> corpses) {
        if (empty()) return true;

        int sum = count(corpses, "Lapis");
        if (withUmber) sum += count(corpses, "Umber");
        if (withTungsten) sum += count(corpses, "Tungsten");
        return sum >= min;
    }

    private static int count(List<MiningState.Corpse> corpses, String type) {
        int sum = 0;
        for (int i = 0; i < corpses.size(); i++) {
            if (corpses.get(i).type().equalsIgnoreCase(type)) sum += corpses.get(i).total();
        }
        return sum;
    }

    /** Eine Zeile fuer Menue und Chat: "2 Lapis", "3 Lapis/Umber" oder "any" */
    public String describe() {
        if (empty()) return "any";
        StringBuilder out = new StringBuilder().append(min).append(" Lapis");
        if (withUmber) out.append("/Umber");
        if (withTungsten) out.append("/Tungsten");
        return out.toString();
    }
}

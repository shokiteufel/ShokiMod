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

    /**
     * Ab wie vielen Leichen die Marker erscheinen.
     *
     * Null heisst immer, minus eins heisst nie: Es gibt Bauplaene, in denen man gar
     * nicht ausminen will, und "nie" ist etwas anderes als "ab einer sehr hohen Zahl" -
     * das waere geraten und ginge bei einem vollen Schacht doch wieder an.
     */
    @Expose
    public int min = 0;

    /** Dieser Wert in {@link #min} heisst: in diesem Bauplan nie */
    public static final int NEVER = -1;
    /**
     * Mehr Leichen als das hat kein Schacht.
     *
     * Vier ist die Obergrenze des Spiels, nicht der Mod - und sie gilt fuer alle
     * Leichen zusammen. Ob Umber und Tungsten mitzaehlen, aendert also nur, was in
     * die vier hineinzaehlt, nicht wie viele es sein koennen.
     */
    public static final int MAX = 4;

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
        return min == 0;
    }

    /** Soll dieser Bauplan gar nichts zeigen? */
    public boolean never() {
        return min == NEVER;
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
    /**
     * Eine zu hohe Zahl auf das Moegliche zurueckholen.
     *
     * Bis 1.9.5 liess sich mit angehakten Sorten bis acht stellen - eine Zahl, die kein
     * Schacht je erreicht, also zeigte die Regel nie etwas an. Wer sie stehen hat,
     * bekommt die hoechste sinnvolle.
     *
     * @return true, wenn etwas geaendert wurde
     */
    public boolean clamp() {
        if (min <= MAX) return false;
        min = MAX;
        return true;
    }

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
        if (never()) return false;
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

    /**
     * Eine Zeile fuer Menue und Chat: "3 corpses (Lapis + Umber)" oder "any".
     *
     * Die Zahl ist eine Leichenzahl, keine Lapis-Zahl: Sind die Haken gesetzt, zaehlen
     * Umber und Tungsten mit hinein. In Klammern steht deshalb, was mitgezaehlt wird -
     * sonst liest man "3 Lapis" und wundert sich, warum zwei Lapis und eine Umber
     * genuegen.
     */
    public String describe() {
        if (never()) return "nothing here";
        if (empty()) return "any";
        StringBuilder sorten = new StringBuilder("Lapis");
        if (withUmber) sorten.append(" + Umber");
        if (withTungsten) sorten.append(" + Tungsten");
        return min + " corpses (" + sorten + ")";
    }
}

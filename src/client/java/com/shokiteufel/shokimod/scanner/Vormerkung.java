package com.shokiteufel.shokimod.scanner;

import java.util.Collection;
import java.util.List;

/**
 * Eine Menge, die ein Weg schon gebucht hat und die ein anderer Weg nicht noch einmal zaehlen darf. Keine Minecraft-Typen.
 *
 * <p>Die Beute einer Leiche kommt auf drei Wegen an: als Chatzeile ("UMBER CORPSE LOOT!" ... je Ware eine Zeile), als Zugang ins Inventar
 * (der Kern sieht das Platz-Update) und - was in einen Sack faellt - als Posten der Sack-Meldung. Die Chatzeile bucht sofort und legt eine
 * Vormerkung an; wer die Ware danach auf einem anderen Weg sieht, zieht von der Vormerkung ab, statt noch einmal zu zaehlen. Bis 2.3.7
 * zog nur die Sack-Meldung ab: Beute, die direkt ins Inventar fiel, stand zweimal im Kasten und in der Bilanz des Schachts (User 16.11.).
 *
 * <p>Beide Reihenfolgen laufen: Chat zuerst ({@link #vomInventarAbziehen}) und Inventar zuerst ({@link #gedeckt}).
 */
final class Vormerkung {
    final String id;
    int menge;
    final long nanos;
    /**
     * Die Chatzeile der Leiche, aus der die Vormerkung stammt. Eine Zeile mit mehreren moeglichen Kennungen legt je Kennung einen Eintrag an,
     * es ist aber eine Menge: Wer einen davon verbraucht, verbraucht alle. {@code null}: keine Leichen-Zeile (Craft, Nachtrag).
     */
    final Object linie;

    Vormerkung(String id, int menge, long nanos, Object linie) {
        this.id = id;
        this.menge = menge;
        this.nanos = nanos;
        this.linie = linie;
    }

    /** Zieht {@code n} von dieser Vormerkung ab, und von allen Eintraegen derselben Chatzeile */
    private void abbuchen(List<Vormerkung> alle, int n) {
        for (Vormerkung v : alle) {
            if (v == this || (linie != null && v.linie == linie)) {
                v.menge = Math.max(0, v.menge - n);
            }
        }
    }

    /**
     * Die Sack-Meldung nennt eine Ware: von der Vormerkung abziehen, was ein anderer Weg schon gebucht hat.
     *
     * @param leben so lange bleibt eine Vormerkung stehen; aeltere fallen heraus
     * @return was von {@code menge} noch zaehlt
     */
    static int fuerSack(List<Vormerkung> vorgebucht, Collection<String> ids, int menge, long nanos, long leben) {
        int rest = menge;
        vorgebucht.removeIf(g -> nanos - g.nanos > leben);
        for (Vormerkung g : List.copyOf(vorgebucht)) {
            if (rest > 0 && g.menge > 0 && ids.contains(g.id)) {
                int genommen = Math.min(g.menge, rest);
                g.abbuchen(vorgebucht, genommen);
                rest -= genommen;
            }
        }
        vorgebucht.removeIf(g -> g.menge <= 0);
        return rest;
    }

    /**
     * Chat zuerst: Der Kern zaehlt einen Zugang ins Inventar. Hat eine Leichen-Zeile diese Ware gerade gebucht, ist es dieselbe Beute.
     *
     * <p>Nur Vormerkungen einer Leichen-Zeile ({@code linie != null}) und nur mit genau dieser Kennung: Craft-Zeilen und Nachtraege haben
     * ihren eigenen Weg. Das Fenster ist kurz - Chatzeile und Platz-Update liegen Ticks auseinander, und eine fremde Ware gleicher Sorte
     * soll nicht lange auf eine Vormerkung treffen.
     *
     * @return was von {@code menge} noch als Fund zaehlt
     */
    static int vomInventarAbziehen(List<Vormerkung> vorgebucht, String id, int menge, long nanos, long fenster) {
        int rest = menge;
        if (id == null) {
            return rest;
        }
        for (Vormerkung g : List.copyOf(vorgebucht)) {
            if (rest > 0 && g.linie != null && g.menge > 0 && g.id.equals(id) && nanos - g.nanos <= fenster) {
                int genommen = Math.min(g.menge, rest);
                g.abbuchen(vorgebucht, genommen);
                rest -= genommen;
            }
        }
        vorgebucht.removeIf(g -> g.menge <= 0);
        return rest;
    }

    /**
     * Inventar zuerst: Eine Leichen-Zeile kommt, und der Kern hat die Ware schon als Fund gezaehlt. Die Menge ist gedeckt und wird aus der
     * Liste genommen, damit sie nicht eine zweite Zeile deckt.
     *
     * @param inventarFunde was der Kern als Fund gezaehlt hat und noch keine Leichen-Zeile gedeckt hat
     * @param ids           die moeglichen Kennungen der Ware der Zeile
     * @return wie viel der Zeile das Inventar schon gezaehlt hat
     */
    static int gedeckt(List<Vormerkung> inventarFunde, Collection<String> ids, int menge, long nanos, long fenster) {
        int rest = menge;
        inventarFunde.removeIf(f -> nanos - f.nanos > fenster);
        for (Vormerkung f : inventarFunde) {
            if (rest > 0 && f.menge > 0 && ids.contains(f.id)) {
                int genommen = Math.min(f.menge, rest);
                f.menge -= genommen;
                rest -= genommen;
            }
        }
        inventarFunde.removeIf(f -> f.menge <= 0);
        return menge - rest;
    }
}

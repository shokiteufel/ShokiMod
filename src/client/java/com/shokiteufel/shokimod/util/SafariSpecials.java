package com.shokiteufel.shokimod.util;

import java.util.Locale;
import java.util.Set;

/**
 * Die Spezialitems der Safari: Critter Capsule, Gems, Shining Coin und so weiter.
 *
 * Sie stehen in keiner Preisliste (kein Basar, keine Auktion, kein Haendler) und standen im Profit Tracker als Zeilen mit "?" - jede Safari-Runde
 * ein halbes Dutzend davon, ohne dass sie etwas zum Gewinn beitragen. Sie gehoeren nicht in den Tracker (User, 11.11.: "die ganzen items mit ?
 * sind special items fuer die safari, diese sollen nicht im tracker auftauchen").
 *
 * Zwei Wege, sie zu erkennen, beide nur fuer Waren OHNE Preis - wer einer Ware selbst einen Preis eintraegt, will sie im Tracker haben:
 * <ul>
 *   <li>der Name steht in der Liste unten (aus dem Kasten des Users: acht Stueck) - gilt auf jeder Insel, auch wenn sie nach der Runde
 *       ankommen (zum Beispiel aus einem Sack);</li>
 *   <li>die Insel ist die Safari: dort ist jede Ware ohne Preis ein Spezialitem, auch eine, die noch nicht in der Liste steht. Das gilt erst,
 *       wenn die Preislisten geladen sind - vorher haette jede Ware "keinen Preis", auch die Essence.</li>
 * </ul>
 * Ein Pet nimmt keiner der beiden Wege: Seine Preise kommen aus anderen Listen, und ein Drop-Pet ist echte Beute. Ebenso Shards und Essence:
 * ein gefangener Shard (aus der Safari-Zeile) ist der Fund, den man dort sucht, auch wenn der Basar ihn nicht fuehrt.
 */
public final class SafariSpecials {

    /** Namen in Kleinbuchstaben; abgelesen am Profit Tracker des Users (Kasten vom 11.11.2026) */
    private static final Set<String> NAMEN = Set.of(
            "critter capsule",
            "masterful critter capsule",
            "icebreaker",
            "lime gem",
            "orange gem",
            "purple gem",
            "shining coin",
            "soothing incense");

    private SafariSpecials() {
    }

    /**
     * Ob eine Ware ein Safari-Spezialitem ist und darum nicht in den Tracker gehoert.
     *
     * @param id            die Kennung der Ware
     * @param name          ihr Name, wie der Tracker ihn zeigt (darf fehlen)
     * @param inSafari      ob man gerade auf der Safari-Insel ist
     * @param hatPreis      ob die Ware einen Preis hat (Markt, Haendler oder selbst eingetragen)
     * @param preiseGeladen ob die Preislisten schon da sind
     */
    public static boolean keinFund(String id, String name, boolean inSafari, boolean hatPreis, boolean preiseGeladen) {
        if (id == null || hatPreis || id.indexOf(';') >= 0) return false;
        // Shards und Essence sind echte Funde, auch ohne Preis: ein Safari-Fang bringt Shards (User, 11.11.: "sie sollen in den Profit Tracker"),
        // und die Safari Essence ist die Waehrung der Runde. Ohne Preis stehen sie mit "?" im Kasten, aber sie stehen
        if (id.contains("SHARD") || id.startsWith("ESSENCE_")) return false;
        if (name != null && NAMEN.contains(name.trim().toLowerCase(Locale.ROOT))) return true;
        return inSafari && preiseGeladen;
    }
}

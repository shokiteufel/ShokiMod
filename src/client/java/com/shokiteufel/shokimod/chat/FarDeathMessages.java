package com.shokiteufel.shokimod.chat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat > Hide Far Deaths: die Todesmeldung eines Spielers, den man in den letzten 30 Sekunden nicht in der Naehe (25 Bloecke) gesehen hat, wird nicht
 * angezeigt. Nicht im Dungeon und nicht in Kuudra - das entscheidet der Aufrufer.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code PlayerDeathMessages}, {@code PlayerDeathManager}). Von dort stammen die Regeln: Die Zeile heisst
 * " ☠ Name was killed by Ashfang." ("You" bin ich selbst); jede Sekunde wird gemerkt, wer im Umkreis von 25 Bloecken steht; wer seit mehr als 30 Sekunden
 * nicht gesehen wurde (oder nie), dessen Tod geht nicht in den Chat. Die Umsetzung ist eigene Arbeit: Java; {@link ChatFilters} liest die Spieler im Tick
 * und gibt sie hier hinein, der Rest (Zeile lesen, Uhr, Entscheidung) laeuft ohne Spiel.
 */
final class FarDeathMessages {

    /** Wie lange ein gesehener Spieler als "in der Naehe" gilt */
    static final long NEAR_MS = 30_000L;

    private static final Pattern DEATH = Pattern.compile(" ☠ (\\w+) (.+)");

    private final HashMap<String, Long> seen = new HashMap<>();

    void reset() {
        seen.clear();
    }

    /** Der Spieler steht jetzt in der Naehe */
    void seen(String name, long now) {
        if (name != null) seen.put(name, now);
    }

    /** Alte Eintraege wegwerfen, damit die Liste nicht waechst */
    void prune(long now) {
        Iterator<Map.Entry<String, Long>> it = seen.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() > NEAR_MS * 2) it.remove();
        }
    }

    /** Ist die Zeile die Todesmeldung eines Spielers, der nicht in der Naehe war? */
    boolean hides(String line, long now) {
        if (!line.startsWith(" ☠ ")) return false;
        Matcher m = DEATH.matcher(line);
        if (!m.matches()) return false;
        String name = m.group(1);
        // " ☠ You fell into the void." - die eigene Meldung bleibt immer
        if (name.equals("You")) return false;
        Long last = seen.get(name);
        return last == null || now - last > NEAR_MS;
    }
}

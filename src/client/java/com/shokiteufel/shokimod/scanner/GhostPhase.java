package com.shokiteufel.shokimod.scanner;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tot im Schacht: Solange der Spieler ein Geist ist, gehoert sein Inventar dem Server.
 *
 * Wer in Kuudra (und aehnlichen Instanzen) stirbt, bekommt fuer die Wartezeit ein anderes
 * Inventar: Haunt, Open Shop und Purchase Revive in der Schnellleiste, alles Eigene weg.
 * Mit der Wiederbelebung kommt das echte Inventar zurueck - und zwar in Haeppchen ueber
 * mehrere Ticks. Der Vergleich in {@link ItemChanges} sah darin Zugaenge: im Log vom 05.11.
 * (Kuudra, drei Tode) standen Terminator, Hyperion, Fire Veil Wand, Gemstone Gauntlet und
 * Aspect of the Void als Fund im Kasten, dazu der Fund-Alarm samt Nachricht in Party und
 * Gilde ("OMG!! RARE DROP! Terminator"). Der Sammelschutz ({@code BULK_IDS}) hat nur den
 * grossen Teil der Waren abgefangen; das erste Haeppchen hatte fuenf Sorten und lag
 * darunter. Die drei Gegenstaende des Geistes zaehlten bei jedem Tod ausserdem selbst als
 * Funde (Open Shop zu 600k).
 *
 * Hier steht nur die Frage "ist der Spieler gerade ein Geist?", ohne Minecraft-Klassen,
 * damit sie sich mit dem Zeitverlauf aus dem Log pruefen laesst. Zwei Zeichen zaehlen:
 *
 * <ul>
 *   <li><b>Die Gegenstaende des Geistes</b> in der Schnellleiste. Das ist das genaue
 *       Zeichen: Es steht da, solange der Spieler tot ist, und ist weg, sobald das echte
 *       Inventar zurueck ist.</li>
 *   <li><b>Die Todesmeldung</b> "You died! You will be respawned in N seconds." Sie kommt
 *       vor dem Inventar und deckt auch den Fall, dass die Gegenstaende anders heissen.
 *       Sie gilt N Sekunden plus drei, und ein Verschwinden der Gegenstaende beendet sie
 *       vorzeitig - wer sich mit Purchase Revive zurueckkauft, soll nicht warten.</li>
 * </ul>
 */
final class GhostPhase {

    /** Was der Server dem Geist in die Schnellleiste legt: Bedienelemente, nie Beute */
    private static final Set<String> GHOST_ITEMS = Set.of(
            "HAUNT_ABILITY", "KUUDRA_SHOP_ITEM", "AUTO_REVIVE_PERK_ITEM");

    /** "You died! You will be respawned in 20 seconds." - so, und nur so, im Log vom 05.11. */
    private static final Pattern DIED =
            Pattern.compile("^You died! You will be respawned in (\\d{1,3}) seconds?\\.?$");

    /** Zugabe auf die angesagte Zeit: das Inventar kommt kurz vor oder nach ihrem Ablauf zurueck */
    static final long MARGIN_MILLIS = 3_000L;
    /** Gesehen wurden 20 bis 26 Sekunden. Eine Zahl weit darueber ist ein Irrtum und haelt den Zustand nicht minutenlang */
    static final long MAX_SECONDS = 120L;

    /** Was sich im Zustand geaendert hat, seit zuletzt gefragt wurde */
    enum Step { OFF, ENTERED, ON, LEFT }

    private boolean active;
    /** Liegen die Gegenstaende des Geistes gerade im Inventar? */
    private boolean marker;
    /** Bis wann die Todesmeldung den Zustand haelt */
    private long until;

    /** Ist das ein Gegenstand, den nur ein Geist bei sich hat? */
    static boolean isGhostItem(String itemId) {
        return itemId != null && GHOST_ITEMS.contains(itemId);
    }

    /** Eine Chatzeile. Gibt zurueck, ob es die eigene Todesmeldung war */
    boolean onChat(String plain, long now) {
        if (plain == null) return false;
        Matcher m = DIED.matcher(plain.trim());
        if (!m.matches()) return false;
        long seconds = Math.min(Long.parseLong(m.group(1)), MAX_SECONDS);
        until = now + seconds * 1_000L + MARGIN_MILLIS;
        return true;
    }

    /** Das Inventar hat sich geaendert: Liegen die Gegenstaende des Geistes darin? */
    void onInventory(boolean ghostItemsPresent) {
        if (ghostItemsPresent) {
            marker = true;
        } else if (marker) {
            marker = false;
            // Das echte Inventar ist zurueck. Die angesagte Zeit gilt nicht mehr
            until = 0L;
        }
    }

    /** Der Zustand dieses Ticks. ENTERED und LEFT kommen je einmal, beim Wechsel */
    Step step(long now) {
        boolean on = marker || now < until;
        if (on == active) return on ? Step.ON : Step.OFF;
        active = on;
        return on ? Step.ENTERED : Step.LEFT;
    }

    boolean active() {
        return active;
    }

    void reset() {
        active = false;
        marker = false;
        until = 0L;
    }
}

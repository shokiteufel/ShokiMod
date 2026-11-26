package com.shokiteufel.shokimod.chat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Merkt sich, in welchem Chat man schreibt - aus den Meldungen, die Hypixel dazu schickt. Rein: keine Minecraft-Typen, die Uhrzeit kommt von aussen.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code CurrentChatDisplay}). Von dort stammt die Regel: Hypixel sagt "You are now in the PARTY channel" (jedes
 * {@code /chat ...}), "You are not in a party and were moved to the ALL channel." und "The conversation you were in expired and you have been moved back to
 * the ALL channel." (zurueck zu allen), und "Opened a chat conversation with [MVP+] Name for the next 5 minutes." (ein Gespraech, das fuenf Minuten haelt und
 * mit jeder Nachricht "From Name: ..." oder "To Name: ..." neu beginnt). Die Beispiele der Tests sind SkyHannis {@code REGEX-TEST}-Zeilen ohne Farbcodes. Die
 * Umsetzung ist eigene Arbeit: Java; die Zeilen werden im Klartext gelesen. Anders als bei SkyHanni gilt eine unbekannte Chat-Art nicht als Fehler: Die Meldung
 * wird uebergangen.
 *
 * <p>Eigene Befehle ({@code /chat}, {@code /pc}) liest auch SkyHanni nicht: Hypixel bestaetigt jeden Wechsel mit einer Zeile, und diese Zeile zaehlt.
 */
public final class ChatChannelTracker {

    /** Wie lange ein Gespraech unter vier Augen haelt, ohne dass jemand schreibt */
    public static final long PRIVATE_MILLIS = 5L * 60_000L;

    private static final Pattern CHANGED = Pattern.compile("^You are now in the (?<chat>.+) channel$");
    private static final Pattern BACK_TO_ALL = Pattern.compile(
            "^You are not in a party and were moved to the ALL channel\\.$"
                    + "|^The conversation you were in expired and you have been moved back to the ALL channel\\.$");
    private static final Pattern PRIVATE_OPENED = Pattern.compile(
            "^Opened a chat conversation with (?:\\[[^\\]]+\\]\\s*)?(?<player>\\S+) for the next 5 minutes\\. Use /chat a to leave.*$");
    private static final Pattern PRIVATE_MESSAGE = Pattern.compile("^(?:From|To) (?:\\[[^\\]]+\\]\\s*)?(?<player>[A-Za-z0-9_]{1,16}): .*$");

    private ChatChannel channel;
    private String privatePlayer;
    private long privateEnd;

    /** Der Chat, soweit bekannt; null heisst: noch nichts gelesen */
    public ChatChannel channel() {
        return channel;
    }

    /** Mit wem das Gespraech laeuft (nur im Chat PRIVATE), sonst null */
    public String privatePlayer() {
        return privatePlayer;
    }

    /** Wann das Gespraech ablaeuft (Millisekunden der Uhr, die an {@link #onLine} ging) */
    public long privateEnd() {
        return privateEnd;
    }

    /** Vergisst alles: der naechste Wechsel, den Hypixel meldet, ist wieder der erste */
    public void reset() {
        channel = null;
        privatePlayer = null;
        privateEnd = 0L;
    }

    /**
     * Eine Chatzeile (Klartext, ohne Farbcodes) lesen.
     *
     * @param now Uhrzeit in Millisekunden
     * @return ob sich dadurch etwas geaendert hat
     */
    public boolean onLine(String plain, long now) {
        if (plain == null) return false;
        String line = plain.trim();
        if (line.isEmpty()) return false;

        Matcher changed = CHANGED.matcher(line);
        if (changed.matches()) {
            ChatChannel next = ChatChannel.fromServerName(changed.group("chat"));
            if (next == null) return false;
            set(next, null);
            return true;
        }
        if (BACK_TO_ALL.matcher(line).matches()) {
            set(ChatChannel.ALL, null);
            return true;
        }
        Matcher opened = PRIVATE_OPENED.matcher(line);
        if (opened.matches()) {
            privateEnd = now + PRIVATE_MILLIS;
            set(ChatChannel.PRIVATE, opened.group("player"));
            return true;
        }
        if (channel == ChatChannel.PRIVATE && privatePlayer != null) {
            Matcher message = PRIVATE_MESSAGE.matcher(line);
            if (message.matches() && message.group("player").equalsIgnoreCase(privatePlayer)) {
                privateEnd = now + PRIVATE_MILLIS;
                return true;
            }
        }
        return false;
    }

    private void set(ChatChannel next, String player) {
        channel = next;
        privatePlayer = player;
        if (next != ChatChannel.PRIVATE) privateEnd = 0L;
    }

    /**
     * Die Zeile fuer den Kasten, mit Farbcodes - leer, solange noch kein Chat gelesen wurde.
     *
     * @param now          Uhrzeit in Millisekunden
     * @param partyOthers  wie viele andere Spieler die Mod in der Party kennt (ohne einen selbst)
     */
    public String text(long now, int partyOthers) {
        if (channel == null) return "";
        StringBuilder out = new StringBuilder("§aChat: ");
        if (channel == ChatChannel.PRIVATE) {
            out.append(privatePlayer != null ? "§6" + privatePlayer + " " : "§cUnknown ");
            out.append(privateEnd <= now ? "§c(EXPIRED)" : "§b" + remaining(privateEnd - now));
            return out.toString();
        }
        out.append(channel.display());
        if (channel == ChatChannel.PARTY) {
            // Einen mehr als bekannt: Die Liste der Mod enthaelt einen selbst nicht
            out.append(partyOthers <= 0 ? " §c(NOT IN PARTY)" : " §a(" + (partyOthers + 1) + " members)");
        }
        return out.toString();
    }

    /** "4m 59s", "59s" */
    static String remaining(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        long minutes = seconds / 60L;
        return minutes > 0 ? minutes + "m " + (seconds % 60L) + "s" : seconds + "s";
    }
}

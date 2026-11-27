package com.shokiteufel.shokimod.chat;

import java.util.Locale;
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
 * <p>Eigene Befehle liest SkyHanni nicht: Hypixel bestaetigt jeden Wechsel mit einer Zeile, und diese Zeile zaehlt. Seit 2.5.12 liest die Mod zusaetzlich
 * {@code /chat <Chat>} als Vorgriff ({@link #onOwnCommand}): Kommt die Bestaetigung nicht an oder in anderer Form, steht der Kasten trotzdem richtig; kommt sie, gilt sie
 * (auch die Meldung "moved to the ALL channel", wenn der Wechsel nicht ging). Der Anlass war der Bericht "der Kasten steht die ganze Zeit auf Chat: All".
 */
public final class ChatChannelTracker {

    /** Wie lange ein Gespraech unter vier Augen haelt, ohne dass jemand schreibt */
    public static final long PRIVATE_MILLIS = 5L * 60_000L;

    /** Leerraum jeder Art, auch das geschuetzte Leerzeichen, das ein Server statt eines gewoehnlichen schicken kann */
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00a0\\u202f]+");
    private static final Pattern CHANGED = Pattern.compile("^You are now in the (?<chat>.+?) channel\\.?$", Pattern.CASE_INSENSITIVE);
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

    /**
     * Nach dem Betreten des Servers, solange noch nichts gelesen wurde: Es gilt der Chat "All" (Hypixel setzt einen dorthin; eine spaetere Meldung ueberschreibt
     * das). Ohne das stuende der Kasten leer, bis man das erste Mal {@code /chat} tippt - und wer ihn einschaltet, will ihn sehen. Ein bekannter Chat bleibt unberuehrt:
     * Das Betreten kommt bei einem Wechsel zwischen Servern noch einmal.
     */
    public void assumeAllIfUnknown() {
        if (channel == null) set(ChatChannel.ALL, null);
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
        // Mehrfacher und geschuetzter Leerraum zaehlt wie ein Leerzeichen: Die Zeile soll an dem erkannt werden, was sie sagt, nicht an ihrer Schreibweise
        String line = WHITESPACE.matcher(plain).replaceAll(" ").trim();
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

    /**
     * Ein Befehl, den man selbst schickt (ohne Schraegstrich, ein fuehrender wird verziehen): {@code chat party} und seine Kurzformen wechseln den Chat. Ein Vorgriff:
     * Hypixel antwortet mit "You are now in the PARTY channel" (oder, ohne Party, mit "moved to the ALL channel"), und diese Zeile ueberschreibt ihn.
     *
     * <p>Nur die vier Chats, deren Kurznamen sicher sind (all/a, party/p, guild/g, officer/o). Alles andere - der Coop, ein Gespraech unter vier Augen, {@code /pc}
     * ohne Wort - bleibt der Meldung von Hypixel ueberlassen.
     *
     * @return ob der Befehl einen Chat gewaehlt hat
     */
    public boolean onOwnCommand(String command) {
        if (command == null) return false;
        String text = WHITESPACE.matcher(command).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
        while (text.startsWith("/")) text = text.substring(1);
        String[] words = text.split(" ");
        if (words.length != 2 || !words[0].equals("chat")) return false;
        ChatChannel next = switch (words[1]) {
            case "a", "all" -> ChatChannel.ALL;
            case "p", "party" -> ChatChannel.PARTY;
            case "g", "guild" -> ChatChannel.GUILD;
            case "o", "officer" -> ChatChannel.OFFICER;
            default -> null;
        };
        if (next == null) return false;
        set(next, null);
        return true;
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

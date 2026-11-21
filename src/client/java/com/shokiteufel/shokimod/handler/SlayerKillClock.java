package com.shokiteufel.shokimod.handler;

/**
 * Die Uhr eines Slayer-Kampfes: wann der Boss erschien, wann die Quest angenommen wurde und wann der Boss starb. Keine Minecraft-Typen,
 * die Zeit kommt von aussen (nanoTime) - so laesst sie sich ohne Spiel pruefen.
 *
 * <p><b>Start</b> des Boss-Kampfes: Die Seitenleiste zeigt "Slay the boss!" ({@link #bossSeen}) oder der Chat meldet "SLAYER BOSS! The X
 * spawned!" ({@link #chatSpawn}) - was zuerst kommt. Beide Wege sind noetig: Die Chatzeile kam in den Mitschnitten vom 20./21.11. nicht bei jeder Quest
 * (28 von 35, 23 von 30), die Seitenleiste immer.
 *
 * <p><b>Ende</b>: "SLAYER QUEST COMPLETE!" im Chat ({@link #finished}). Hat die Seitenleiste die Zeile "Slay the boss!" kurz davor verloren
 * ({@link #bossGone}), zaehlt dieser fruehere Zeitpunkt: Die Meldung kommt ein bis zwei Sekunden nach dem Tod. Eine Luecke in der Seitenleiste
 * (sie wird Zeile fuer Zeile neu gebaut) beendet nichts - nur die Meldung beendet. "SLAYER QUEST FAILED!" ({@link #failed}) oder eine Zeile,
 * die zu lange fehlt ({@link #timeout}), verwirft den Kampf.
 *
 * <p><b>Quest</b>: "SLAYER QUEST STARTED!" ({@link #questStarted}) setzt den Beginn der Quest; sie laeuft bis zum Tod des Bosses. Fehlt der
 * Beginn (die Funktion wurde mitten in der Quest eingeschaltet), gibt es keine Questzeit, nur die Bosszeit.
 */
public final class SlayerKillClock {

    /** Ein beendeter Kampf: Name, Zeit seit dem Erscheinen des Bosses, Zeit seit der Quest-Annahme (-1, wenn unbekannt) */
    public record Kill(String key, long bossNanos, long questNanos) {
    }

    /** So lange nach dem Verschwinden der Zeile zaehlt eine Abschlussmeldung noch zum Tod: die Meldung kommt 1 bis 2 s danach */
    static final long LOSS_NANOS = 6_000_000_000L;
    /** Fehlt die Zeile so lange ohne Abschlussmeldung, ist der Kampf aus anderem Grund zu Ende (Boss weg, Quest abgebrochen) */
    static final long GIVE_UP_NANOS = 15_000_000_000L;

    private long bossStart = -1L;
    private String key = null;
    private boolean keyFromSidebar = false;
    private long questStart = -1L;
    /** Seit wann die Zeile "Slay the boss!" fehlt, oder -1 */
    private long lostAt = -1L;
    /**
     * Die Seitenleiste darf erst wieder einen Kampf beginnen lassen, wenn die Zeile einmal gefehlt hat. Sonst finge der Kampf an, wenn die Funktion
     * mitten im Kampf eingeschaltet wird (der Beginn waere falsch), und gleich nach dem Tod, solange die Seitenleiste die Zeile noch zeigt
     * (ein Phantom-Kampf, der die Zeit des naechsten verfaelschte). Eine Chatmeldung "spawned!" ist dagegen ein ausdruecklicher Beginn.
     */
    private boolean waitAbsent = true;

    /** Laeuft gerade ein Boss-Kampf? */
    public boolean running() {
        return bossStart >= 0L;
    }

    /**
     * Der Name des Bosses samt Stufe aus der Seitenleiste: die Zeile direkt ueber "Slay the boss!" ("Tarantula Broodfather V"). Die Zeiten werden
     * je Boss und Stufe gefuehrt, denn eine Stufe 3 und eine Stufe 5 lassen sich nicht vergleichen; so gilt es auch fuer jeden kuenftigen Boss,
     * ohne dass eine Liste gepflegt werden muss.
     *
     * @param lines die Zeilen der Seitenleiste von oben nach unten, ohne Farbcodes
     * @param at    die Stelle von "Slay the boss!"
     * @return der Name, oder null, wenn dort etwas anderes steht (die Kopfzeile "Slayer Quest", nichts)
     */
    public static String keyAbove(java.util.List<String> lines, int at) {
        if (lines == null || at <= 0 || at > lines.size()) {
            return null;
        }
        String above = lines.get(at - 1) == null ? "" : lines.get(at - 1).trim();
        if (above.isEmpty() || above.equalsIgnoreCase("Slayer Quest")) {
            return null;
        }
        return above;
    }

    /** "SLAYER QUEST STARTED!": die Quest beginnt. Ein noch laufender Kampf der vorigen Quest war dann keiner mehr */
    public void questStarted(long now) {
        questStart = now;
        cancelBoss();
    }

    /**
     * Die Seitenleiste zeigt "Slay the boss!".
     *
     * @param sidebarKey die Zeile davor ("Tarantula Broodfather V": Name und Stufe), oder null
     */
    public void bossSeen(String sidebarKey, long now) {
        lostAt = -1L;
        if (bossStart < 0L) {
            if (waitAbsent) {
                return;
            }
            bossStart = now;
        }
        // Die Seitenleiste nennt die Stufe mit; der Chat-Name allein taugt nur, solange sie fehlt
        if (sidebarKey != null && !sidebarKey.isBlank() && !keyFromSidebar) {
            key = sidebarKey.trim();
            keyFromSidebar = true;
        }
    }

    /** "SLAYER BOSS! The X spawned!": der Boss ist da. Kam die Seitenleiste zuerst, bleibt ihr Beginn */
    public void chatSpawn(String bossName, long now) {
        waitAbsent = false;
        if (bossStart < 0L) {
            bossStart = now;
            lostAt = -1L;
        }
        if (!keyFromSidebar && bossName != null && !bossName.isBlank()) {
            key = bossName.trim();
        }
    }

    /** Die Seitenleiste zeigt "Slay the boss!" nicht (mehr) */
    public void bossGone(long now) {
        waitAbsent = false;
        if (bossStart >= 0L && lostAt < 0L) {
            lostAt = now;
        }
    }

    /** Aufruf im Takt: ein Kampf, dessen Zeile ohne Abschlussmeldung zu lange fehlt, wird verworfen */
    public void timeout(long now) {
        if (bossStart >= 0L && lostAt >= 0L && now - lostAt > GIVE_UP_NANOS) {
            cancelBoss();
        }
    }

    /** "SLAYER QUEST FAILED!": weder Boss- noch Questzeit zaehlen */
    public void failed() {
        cancelBoss();
        questStart = -1L;
    }

    /** Neue Lobby: der Boss gehoert zur alten, die Quest laeuft weiter (ihre Zeit zaehlt, die Sitzung nicht) */
    public void lobbyChanged() {
        cancelBoss();
    }

    /**
     * "SLAYER QUEST COMPLETE!": der Boss ist tot.
     *
     * @return der Kampf, oder null, wenn kein Beginn bekannt ist (die Funktion wurde mitten im Kampf eingeschaltet)
     */
    public Kill finished(long now) {
        long start = bossStart;
        String name = key;
        long quest = questStart;
        long end = (lostAt >= 0L && now - lostAt <= LOSS_NANOS) ? lostAt : now;
        cancelBoss();
        questStart = -1L;
        if (start < 0L || end <= start) {
            return null;
        }
        long questNanos = quest >= 0L && end > quest ? end - quest : -1L;
        return new Kill(name == null || name.isEmpty() ? "Slayer boss" : name, end - start, questNanos);
    }

    /** Beendet den Kampf (Tod, Misserfolg, neue Quest, neue Lobby): die Zeile muss erst fehlen, bevor ein neuer beginnt */
    private void cancelBoss() {
        bossStart = -1L;
        key = null;
        keyFromSidebar = false;
        lostAt = -1L;
        waitAbsent = true;
    }
}

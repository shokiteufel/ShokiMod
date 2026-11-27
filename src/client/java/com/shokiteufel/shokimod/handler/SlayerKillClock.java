package com.shokiteufel.shokimod.handler;

/**
 * Die Uhr eines Slayer-Kampfes: wann der Boss erschien, wann die Quest angenommen wurde und wann der Boss starb. Keine Minecraft-Typen,
 * die Zeit kommt von aussen (nanoTime) - so laesst sie sich ohne Spiel pruefen.
 *
 * <p><b>Start</b> des Boss-Kampfes: Die Seitenleiste zeigt "Slay the boss!" ({@link #bossSeen}) oder der Chat meldet "SLAYER BOSS! The X
 * spawned!" ({@link #chatSpawn}) - was zuerst kommt. Beide Wege sind noetig: Die Chatzeile kam in den Mitschnitten vom 20./21.11. nicht bei jeder Quest
 * (28 von 35, 23 von 30), die Seitenleiste immer.
 *
 * <p><b>Ende</b> - der Tod des Bosses. Hypixel schreibt dazu mehrere Zeilen, und sie liegen nicht beieinander. Gemessen an den drei Mitschnitten
 * (69 Kaempfe, Zeitstempel auf die Sekunde):
 * <ul>
 *   <li>"Your Slayer Kill gave you ... HP healing" ({@link #killMark}) kam bei allen 69, im Moment des Todes (im Zombie-Log: Boss-Schild "0/10M" und
 *       diese Zeile in derselben Sekunde). Sie ist die genaueste Marke, aber ein Vorteil der Slayer-Stufe: Wer ihn nicht hat, sieht sie nicht.</li>
 *   <li>"SLAYER QUEST COMPLETE!" kommt 1 bis 2 s nach dem Tod, und nach "COCOONED" erst 5 bis 7 s danach.</li>
 *   <li>"YOU COCOONED YOUR SLAYER BOSS" kam bei 20 der 69 Kaempfe: bei 15 im Moment des Todes, bei 5 aber 8 bis 9 s davor (der Boss war nur
 *       eingesponnen und starb spaeter). Als Ende allein taugt sie also nicht - Skyblocker nimmt sie trotzdem als erste Meldung.</li>
 *   <li>"NICE! SLAYER BOSS SLAIN!" kam in keinem Kampf.</li>
 * </ul>
 * Darum gilt: Ist die Zeile "Slayer Kill" da, ist sie das Ende. Fehlt sie, gilt die erste der Todesmeldungen. Eine Meldung wie "COCOONED" wartet
 * dazu kurz auf die Zeile ({@link #deathMessage}) und wird mit der Abschlussmeldung ({@link #finished}) oder nach {@link #GIVE_UP_NANOS} gezaehlt.
 *
 * <p>Die Seitenleiste entscheidet das Ende nicht: Eine Luecke in ihr (sie wird Zeile fuer Zeile neu gebaut) beendet nichts, und ihr Raster (alle fuenf
 * Ticks gelesen = 0,25 s) hatte die Zeiten in 2.4.5 auf ,00 ,25 ,99 gerastert. Sie dient nur dem Beginn und dem Verwerfen: "SLAYER QUEST FAILED!"
 * ({@link #failed}) oder eine Zeile, die zu lange fehlt ({@link #timeout}), verwirft den Kampf.
 *
 * <p><b>Quest</b>: "SLAYER QUEST STARTED!" ({@link #questStarted}) setzt den Beginn der Quest. Zwei Zeiten laufen davon aus: bis zum Tod des Bosses
 * ({@code Kill.questNanos}, nur noch fuers Log) und - die, die gezeigt wird - bis zur Zeile "SLAYER QUEST COMPLETE!" ({@link #questCompleted}), wie bei SkyHanni
 * ("Slayer quest took 9s to complete": von {@code SlayerApi.questStartTime} bis zum Ereignis der Abschlussmeldung). Seit 2.5.17 gilt die zweite, weil beide Mods sonst
 * fuer dieselbe Quest verschiedene Werte nennen (Tod und Abschlussmeldung liegen 1 bis 2 s, nach "COCOONED" 5 bis 7 s auseinander). Fehlt der Beginn (die Funktion wurde
 * mitten in der Quest eingeschaltet), gibt es keine Questzeit, nur die Bosszeit.
 */
public final class SlayerKillClock {

    /**
     * Ein beendeter Kampf: Name, Zeit seit dem Erscheinen des Bosses, Zeit seit der Quest-Annahme (-1, wenn unbekannt) und ob das Ende die Zeile
     * "Your Slayer Kill ..." war (sonst die erste Todesmeldung)
     */
    public record Kill(String key, long bossNanos, long questNanos, boolean byKillLine) {
    }

    /** Die Quest ist zu Ende ("SLAYER QUEST COMPLETE!"): Name des Bosses (mit Stufe) und die Zeit seit der Annahme bis zu dieser Zeile */
    public record QuestDone(String key, long nanos) {
    }

    /** So lange wartet eine Todesmeldung auf die Zeile "Slayer Kill" oder die Abschlussmeldung; fehlt eine Zeile so lange, ist der Kampf aus anderem Grund zu Ende */
    static final long GIVE_UP_NANOS = 15_000_000_000L;

    private long bossStart = -1L;
    private String key = null;
    private boolean keyFromSidebar = false;
    private long questStart = -1L;
    /**
     * Der Beginn der Quest fuer die Gesamtzeit der Quest - wie bei SkyHanni (SlayerApi.questStartTime bis zum Ereignis der Abschlussmeldung): Sie laeuft bis zur Zeile
     * "SLAYER QUEST COMPLETE!", nicht bis zum Tod des Bosses. Der Tod liegt davor, meist 1 bis 2 s, nach "COCOONED" 5 bis 7 s. Getrennt von {@link #questStart}, der
     * mit dem Kampf endet: Die Bosszeit und die Zeit bis zum Tod aendern sich dadurch nicht.
     */
    private long completeStart = -1L;
    /** Name (mit Stufe) des Bosses der letzten Quest, oder null, solange kein Kampf gezaehlt wurde */
    private String lastKey = null;
    /** Seit wann die Zeile "Slay the boss!" fehlt, oder -1 */
    private long lostAt = -1L;
    /** Wann die Zeile "Your Slayer Kill gave you ..." kam, oder -1 */
    private long killAt = -1L;
    /** Wann die erste Todesmeldung kam, oder -1 */
    private long deathAt = -1L;
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
        completeStart = now;
        lastKey = null;
        cancelBoss();
    }

    /**
     * "SLAYER QUEST COMPLETE!": die Quest ist zu Ende. Die Zeit laeuft - wie bei SkyHanni ("Slayer quest took 9s to complete") - von der Annahme bis zu dieser Zeile,
     * also etwas laenger als bis zum Tod des Bosses. Aufruf nach {@link #finished}/{@link #deathMessage}: Erst dort steht fest, welcher Boss es war.
     *
     * @return die Zeit und der Name des Bosses, oder null, wenn kein Beginn bekannt ist (die Funktion wurde mitten in der Quest eingeschaltet) oder die Zeit nicht
     *         zaehlt (die Zeile kam vor dem Beginn); danach ist die Quest abgehakt - ein zweiter Aufruf gibt null
     */
    public QuestDone questCompleted(long now) {
        long start = completeStart;
        String key = lastKey == null || lastKey.isEmpty() ? "Slayer boss" : lastKey;
        completeStart = -1L;
        lastKey = null;
        if (start < 0L || now <= start) {
            return null;
        }
        return new QuestDone(key, now - start);
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

    /**
     * Aufruf im Takt: Ein Kampf, dessen Zeile ohne Todesmeldung zu lange fehlt, wird verworfen; ein Kampf, dessen erste Todesmeldung zu lange auf die
     * Abschlussmeldung wartet (Lobbywechsel, Abbruch), wird mit dem, was da ist, gezaehlt.
     *
     * @return der Kampf, wenn er jetzt gezaehlt wird, sonst null
     */
    public Kill timeout(long now) {
        if (bossStart < 0L) {
            return null;
        }
        // Eine Todesmarke ohne Abschlussmeldung (Lobbywechsel, Abbruch): der Boss ist gestorben, der Kampf zaehlt
        long marked = deathAt >= 0L && killAt >= 0L ? Math.min(deathAt, killAt) : Math.max(deathAt, killAt);
        if (marked >= 0L && now - marked > GIVE_UP_NANOS) {
            return finishAt(killAt >= 0L ? killAt : deathAt, killAt >= 0L);
        }
        if (lostAt >= 0L && now - lostAt > GIVE_UP_NANOS) {
            cancelBoss();
        }
        return null;
    }

    /** "SLAYER QUEST FAILED!": weder Boss- noch Questzeit zaehlen */
    public void failed() {
        cancelBoss();
        questStart = -1L;
        completeStart = -1L;
        lastKey = null;
    }

    /** Neue Lobby: der Boss gehoert zur alten, die Quest laeuft weiter (ihre Zeit zaehlt, die Sitzung nicht) */
    public void lobbyChanged() {
        cancelBoss();
    }

    /**
     * "Your Slayer Kill gave you ... HP healing": der Boss ist gerade gestorben. War schon eine Todesmeldung da, die auf diese Zeile wartete
     * ("COCOONED"), wird der Kampf jetzt gezaehlt, mit dem Zeitpunkt dieser Zeile.
     *
     * @return der Kampf, wenn er jetzt gezaehlt wird, sonst null (ausserhalb eines Kampfes, oder die Todesmeldung kommt noch)
     */
    public Kill killMark(long now) {
        if (bossStart < 0L || killAt >= 0L) {
            return null;
        }
        killAt = now;
        return deathAt >= 0L ? finishAt(killAt, true) : null;
    }

    /**
     * Eine Todesmeldung im Chat: "YOU COCOONED YOUR SLAYER BOSS" ({@code last} = false: der Boss kann danach noch leben) oder "NICE! SLAYER BOSS
     * SLAIN!" und "SLAYER QUEST COMPLETE!" ({@code last} = true: der Kampf ist zu Ende).
     *
     * <p>Ohne die Zeile "Slayer Kill" zaehlt die erste Todesmeldung. Eine nicht endgueltige Meldung wartet auf die Zeile oder die endgueltige.
     *
     * @return der Kampf, oder null, wenn kein Beginn bekannt ist (die Funktion wurde mitten im Kampf eingeschaltet), der Kampf schon gezaehlt ist
     *         (die Abschlussmeldung nach "Slayer Kill") oder noch auf die Zeile wartet
     */
    public Kill deathMessage(long now, boolean last) {
        if (bossStart < 0L) {
            // Nichts laeuft: die Abschlussmeldung beendet nur noch die Quest (die Seitenleiste zeigt die Zeile noch einen Augenblick)
            if (last) {
                cancelBoss();
                questStart = -1L;
            }
            return null;
        }
        if (deathAt < 0L) {
            deathAt = now;
        }
        if (!last && killAt < 0L) {
            return null;
        }
        return finishAt(killAt >= 0L ? killAt : deathAt, killAt >= 0L);
    }

    /** "SLAYER QUEST COMPLETE!": die endgueltige Todesmeldung */
    public Kill finished(long now) {
        return deathMessage(now, true);
    }

    private Kill finishAt(long end, boolean byKillLine) {
        long start = bossStart;
        String name = key;
        long quest = questStart;
        cancelBoss();
        questStart = -1L;
        if (start < 0L || end <= start) {
            return null;
        }
        long questNanos = quest >= 0L && end > quest ? end - quest : -1L;
        String shownName = name == null || name.isEmpty() ? "Slayer boss" : name;
        // Fuer die Gesamtzeit der Quest: welcher Boss es war (die Zeit selbst laeuft bis zur Abschlussmeldung weiter)
        lastKey = shownName;
        return new Kill(shownName, end - start, questNanos, byKillLine);
    }

    /** Beendet den Kampf (Tod, Misserfolg, neue Quest, neue Lobby): die Zeile muss erst fehlen, bevor ein neuer beginnt */
    private void cancelBoss() {
        bossStart = -1L;
        key = null;
        keyFromSidebar = false;
        lostAt = -1L;
        killAt = -1L;
        deathAt = -1L;
        waitAbsent = true;
    }
}

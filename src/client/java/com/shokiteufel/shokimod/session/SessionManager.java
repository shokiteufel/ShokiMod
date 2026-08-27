package com.shokiteufel.shokimod.session;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Beginn und Ende eines Safari-Laufs, und die Weiterleitung der Chatzeilen.
 *
 * Ein Lauf beginnt beim Betreten der Safari, nachdem man laenger weg war - so zaehlt
 * ein kurzer Ausflug zum Haendler nicht als neuer Lauf. Er endet, wenn man weg ist
 * und laengere Zeit nichts mehr passiert ist; sonst wuerde eine Pause am Rand des
 * Gebiets den Lauf abschneiden.
 */
public final class SessionManager {

    /** So lange muss man weg gewesen sein, damit die Rueckkehr einen neuen Lauf startet */
    private static final int AWAY_TICKS_FOR_NEW_RUN = 100;
    /** So lange muss man weg sein, bevor ein Lauf ueberhaupt enden darf */
    private static final int AWAY_TICKS_BEFORE_END = 40;
    /** Und so lange muss dabei Ruhe geherrscht haben */
    private static final long QUIET_MILLIS_BEFORE_END = 60_000L;

    private static SafariSession current = null;
    private static SafariSession last = null;
    private static int ticksAway = AWAY_TICKS_FOR_NEW_RUN;
    private static boolean arrivalStartsRun = true;
    private static long lastEventMillis = 0L;

    private SessionManager() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    public static SafariSession current() {
        return current;
    }

    /** Der laufende Lauf, sonst der letzte - fuer die Anzeige nach dem Verlassen */
    public static SafariSession currentOrLast() {
        return current != null ? current : last;
    }

    public static void reset() {
        current = null;
        last = null;
        ticksAway = AWAY_TICKS_FOR_NEW_RUN;
        arrivalStartsRun = true;
    }

    private static String selfName() {
        Minecraft client = Minecraft.getInstance();
        return client.getUser() == null ? null : client.getUser().getName();
    }

    private static void tick() {
        if (!ModConfig.INSTANCE.safari.trackRuns) return;

        if (GameState.Server.isSafari()) {
            if (ticksAway >= AWAY_TICKS_FOR_NEW_RUN) arrivalStartsRun = true;
            ticksAway = 0;
            if (arrivalStartsRun) {
                arrivalStartsRun = false;
                startSession();
            }
            return;
        }

        ticksAway++;
        if (current == null) return;
        if (System.currentTimeMillis() - lastEventMillis < QUIET_MILLIS_BEFORE_END) return;
        if (ticksAway < AWAY_TICKS_BEFORE_END) return;
        endSession();
    }

    private static void startSession() {
        if (current != null) endSession();
        current = new SafariSession(selfName(), System.currentTimeMillis());
        lastEventMillis = System.currentTimeMillis();
    }

    private static void endSession() {
        if (current == null) return;
        current.end(System.currentTimeMillis());
        last = current;
        current = null;
    }

    /** Wird beim Weltwechsel gerufen: der Lauf gehoert zur verlassenen Welt */
    public static void onWorldChange() {
        endSession();
        ticksAway = AWAY_TICKS_FOR_NEW_RUN;
        arrivalStartsRun = true;
    }

    /**
     * @param formatted die Zeile mit Farbcodes, wie sie im Chat steht
     */
    public static void onChatMessage(String formatted) {
        if (!ModConfig.INSTANCE.safari.trackRuns) return;

        String text = ChatParser.clean(formatted);
        // Von Spielern getippte Zeilen duerfen nichts ausloesen
        if (ChatParser.playerSaid(text)) return;

        ChatParser.Event event = ChatParser.parse(text, selfName());
        if (event == null) return;

        lastEventMillis = System.currentTimeMillis();

        if (event.type() == ChatParser.Type.ENTERED_SAFARI) {
            startSession();
            return;
        }

        if (current == null) startSession();

        switch (event.type()) {
            case OWN_CATCH -> current.record(null, event.critter(), event.shards());
            case SHARED_CATCH -> current.record(event.catcher(), event.critter(), event.shards());
            case ATTEMPT -> current.countAttempt();
            default -> {
            }
        }
    }
}

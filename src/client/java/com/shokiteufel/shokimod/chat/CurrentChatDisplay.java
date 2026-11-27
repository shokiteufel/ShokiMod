package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * Chat > Current Chat Display: ein Kasten im HUD-Editor, der sagt, in welchem Chat man schreibt (All, Party, Guild, Officer, Private, Co-op).
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code CurrentChatDisplay}). Von dort stammen die Regel (Hypixels Meldungen sagen, in welchem Chat man ist: siehe
 * {@link ChatChannelTracker}), die Zeile "Chat: Party (3 members)" und dass der Kasten nur bei offenem Chat und kurz danach steht - bei SkyHanni ist der Teil
 * "kurz danach" im Code vorbereitet, aber nie angeschlossen ("this is currently never called"); hier gilt er: zwei Sekunden nach dem Schliessen des Chats. Die
 * Umsetzung ist eigene Arbeit: Java, der Kasten ist ein Kasten des HUD-Editors ({@code SafariHud.Panel.CURRENT_CHAT}).
 *
 * <p>Ort: Vorgabe wie bei SkyHanni kurz ueber der Chat-Eingabe (siehe {@code CurrentChatHud}). Nach dem Betreten gilt "All", bis Hypixel etwas anderes meldet.
 *
 * <p>Abweichungen: Der Chat wird nicht bei jedem Welt- oder Serverwechsel vergessen, sondern nur, wenn die Verbindung endet. Auf Hypixel bleibt der Chat ueber die
 * Wechsel zwischen den Servern bestehen (die Party laeuft weiter); ein Vergessen bei jedem Wechsel liesse den Kasten in SkyBlock fast immer leer. Gemerkt wird nur
 * im Speicher - SkyHanni speichert den Chat in der Datei des Spielers.
 */
public final class CurrentChatDisplay {

    /** So lange nach dem Schliessen des Chats steht der Kasten noch */
    static final long LINGER_MILLIS = 2000L;

    private static final ChatChannelTracker TRACKER = new ChatChannelTracker();
    private static boolean chatWasOpen = false;
    private static long closedAt = 0L;

    private CurrentChatDisplay() {
    }

    /** So viele Zeilen, die nach einer Kanal-Meldung aussehen und doch nicht erkannt werden, schreibt die Mod je Start ins Log - mehr braucht die Diagnose nicht */
    private static final int MAX_UNRECOGNISED_LOGGED = 20;
    private static int unrecognisedLogged = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(CurrentChatDisplay::tick);
        // Die Verbindung endet: der naechste Wechsel, den Hypixel meldet, ist wieder der erste
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        // Neu verbunden: bis Hypixel etwas anderes meldet, ist man im Chat "All"
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> TRACKER.assumeAllIfUnknown());
        // Der eigene Befehl "/chat party": ein Vorgriff, den Hypixels Antwort bestaetigt oder zurechtrueckt (nur beobachtet, am Befehl aendert sich nichts)
        ClientSendMessageEvents.COMMAND.register(CurrentChatDisplay::onOwnCommand);
    }

    /** Eine Chatzeile (Klartext): sagt sie, in welchem Chat man ist? */
    public static void onChatMessage(String plain) {
        ChatChannel before = TRACKER.channel();
        boolean changed = TRACKER.onLine(plain, System.currentTimeMillis());
        if (changed) {
            ShokiMod.LOGGER.info("[Chat] Chat gelesen: {} (vorher {}) aus \"{}\"", TRACKER.channel(), before, plain);
        } else if (unrecognisedLogged < MAX_UNRECOGNISED_LOGGED && plain != null && plain.contains(" channel") && plain.startsWith("You")) {
            // Sieht nach einer Meldung ueber den Chat aus, wurde aber nicht gelesen: genau die Zeile, die man braucht, wenn der Kasten falsch steht
            unrecognisedLogged++;
            ShokiMod.LOGGER.info("[Chat] Zeile ueber einen Chat nicht erkannt: \"{}\"", plain);
        }
    }

    /** Ein Befehl, den man selbst schickt (ohne Schraegstrich) */
    static void onOwnCommand(String command) {
        if (!FeatureGate.currentChatDisplay()) return;
        ChatChannel before = TRACKER.channel();
        if (TRACKER.onOwnCommand(command)) {
            ShokiMod.LOGGER.info("[Chat] Befehl /{}: Chat jetzt {} (vorher {}, Hypixel bestaetigt gleich)", command, TRACKER.channel(), before);
        }
    }

    /** Alles vergessen */
    public static void reset() {
        TRACKER.reset();
        chatWasOpen = false;
        closedAt = 0L;
    }

    /** Die Zeile fuer den Kasten, mit Farbcodes; leer, solange nichts gelesen wurde */
    public static String line() {
        return TRACKER.text(System.currentTimeMillis(), PartyRoster.INSTANCE.members().size());
    }

    /** Steht der Kasten jetzt: bei offenem Chat, und kurz danach? */
    public static boolean visibleNow() {
        Minecraft client = Minecraft.getInstance();
        boolean open = client != null && client.gui.screen() instanceof ChatScreen;
        return visible(open, closedAt, System.currentTimeMillis());
    }

    /** Die Regel, rein: offen, oder vor weniger als zwei Sekunden geschlossen */
    static boolean visible(boolean chatOpen, long closedAt, long now) {
        return chatOpen || (closedAt > 0L && now - closedAt <= LINGER_MILLIS);
    }

    private static void tick(Minecraft client) {
        if (!FeatureGate.currentChatDisplay()) {
            chatWasOpen = false;
            return;
        }
        boolean open = client.gui.screen() instanceof ChatScreen;
        if (chatWasOpen && !open) closedAt = System.currentTimeMillis();
        chatWasOpen = open;
    }
}

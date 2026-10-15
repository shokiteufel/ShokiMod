package com.shokiteufel.shokimod.handler;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.Shortcut;
import com.shokiteufel.shokimod.gui.ShortcutScreen;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tasten, die einen Befehl oder Satz abschicken: Taste druecken, Text geht in den Chat.
 *
 * Genau einmal je Druck. Gehalten wird nichts wiederholt, und zwischen zwei Sendungen liegt
 * mindestens eine kurze Pause - die Taste ist eine Abkuerzung fuer das, was man sonst tippt, kein
 * Dauerfeuer. Es zaehlt nur der Augenblick, in dem die Taste unten ankommt.
 *
 * Gesendet wird nur, wenn der Mauszeiger vom Spiel gehalten wird, also kein Fenster offen ist:
 * Wer im Chat, im Inventar oder in einem Schild tippt, loest nichts aus. Eine noch gehaltene
 * Taste zaehlt nach dem Schliessen eines Fensters nicht als neuer Druck.
 *
 * Die Tasten werden abgefragt, nicht als Tastenbelegung des Spiels eingetragen: So sind sie frei
 * waehlbar und tauchen nicht in den Steuerungs-Einstellungen auf.
 */
public final class ShortcutHandler {

    /** So viel Pause liegt mindestens zwischen zwei Sendungen */
    private static final long MIN_GAP_MILLIS = 400L;

    /** Welche Tasten im letzten Tick unten waren - fuer den Augenblick des Druecks */
    private static Map<Integer, Boolean> wasDown = new HashMap<>();
    private static long lastSent = 0L;
    private static boolean openRequested = false;

    // Die Abfrage "ist die Taste unten?" heisst in 26.1 und 26.2 isKeyDown(Window, int), ab 26.3
    // isKeyDown(int). Nachgeschaut wird einmal, gerufen je Tick.
    private static boolean resolved = false;
    private static Method isDownKey;
    private static Method isDownWindowKey;
    private static boolean warnedKeys = false;

    private ShortcutHandler() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ShortcutHandler::tick);
    }

    /** /shoki shortcut: Das Fenster geht im naechsten Tick auf, wenn der Chat zu ist */
    public static void requestOpen() {
        openRequested = true;
    }

    /** Der Name einer Taste, wie ihn das Spiel in der Steuerung schreibt */
    public static String keyName(int key) {
        if (key == 0) return "None";
        try {
            return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
        } catch (RuntimeException e) {
            return "Key " + key;
        }
    }

    private static void tick(Minecraft client) {
        if (openRequested) {
            openRequested = false;
            client.setScreenAndShow(new ShortcutScreen());
            return;
        }

        List<Shortcut> list = ModConfig.INSTANCE.shortcuts;
        ClientPacketListener connection = client.getConnection();
        if (list == null || list.isEmpty() || client.player == null || connection == null) {
            if (!wasDown.isEmpty()) wasDown = new HashMap<>();
            return;
        }

        // Kein Fenster offen und das Spiel im Vordergrund: Dann gilt ein Druck
        boolean canFire = client.mouseHandler.isMouseGrabbed() && client.isWindowActive();
        for (Shortcut shortcut : due(list, ShortcutHandler::isDown, canFire)) send(connection, shortcut);
    }

    /**
     * Welche Eintraege diesen Tick abgeschickt werden.
     *
     * Ein Eintrag ist faellig, wenn seine Taste jetzt unten ist, im Tick davor nicht war und
     * gesendet werden darf. Der Stand der Tasten wird in jedem Fall weitergefuehrt - auch wenn ein
     * Fenster offen ist -, damit eine noch gehaltene Taste beim Schliessen nicht als neuer Druck
     * zaehlt. Je Taste kommt hoechstens ein Eintrag in Frage: der erste angeschaltete.
     */
    static List<Shortcut> due(List<Shortcut> list, java.util.function.IntPredicate isDown, boolean canFire) {
        List<Shortcut> out = new java.util.ArrayList<>();
        Map<Integer, Boolean> now = new HashMap<>();
        Set<Integer> seen = new HashSet<>();
        for (Shortcut shortcut : list) {
            if (shortcut == null || !shortcut.isActive() || !seen.add(shortcut.key)) continue;

            boolean down = isDown.test(shortcut.key);
            now.put(shortcut.key, down);
            boolean before = wasDown.getOrDefault(shortcut.key, false);
            if (down && !before && canFire) out.add(shortcut);
        }
        wasDown = now;
        return out;
    }

    private static void send(ClientPacketListener connection, Shortcut shortcut) {
        long time = System.currentTimeMillis();
        if (time - lastSent < MIN_GAP_MILLIS) return;

        String text = shortcut.command.trim();
        if (text.isEmpty() || text.equals("/")) return;
        lastSent = time;
        if (text.startsWith("/")) connection.sendCommand(text.substring(1));
        else connection.sendChat(text);
    }

    private static boolean isDown(int key) {
        try {
            if (!resolved) resolve();
            if (isDownKey != null) return (boolean) isDownKey.invoke(null, key);
            if (isDownWindowKey != null) {
                Window window = Minecraft.getInstance().getWindow();
                return window != null && (boolean) isDownWindowKey.invoke(null, window, key);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warnedKeys) {
                warnedKeys = true;
                ShokiMod.LOGGER.warn("[Shortcut] could not read the keyboard", e);
            }
        }
        return false;
    }

    private static void resolve() {
        resolved = true;
        for (Method method : InputConstants.class.getMethods()) {
            if (!method.getName().equals("isKeyDown") || !Modifier.isStatic(method.getModifiers())) continue;
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 1 && types[0] == int.class) isDownKey = method;
            else if (types.length == 2 && types[0] == Window.class && types[1] == int.class) isDownWindowKey = method;
        }
        if (isDownKey == null && isDownWindowKey == null) {
            ShokiMod.LOGGER.warn("[Shortcut] no keyboard query found - shortcuts will not fire");
        }
    }
}

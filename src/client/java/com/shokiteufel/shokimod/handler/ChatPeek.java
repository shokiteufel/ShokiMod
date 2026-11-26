package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.function.BooleanSupplier;

/**
 * Chat > Peek Chat: Solange die Taste gehalten wird, zeigt der Chat sich wie bei offenem Chatfenster - alle Zeilen in der hohen Fassung -, ohne dass die Tastatur
 * vom Spiel genommen wird.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code ChatPeek}). Von dort stammt die Regel: Taste gewaehlt und gehalten, Spieler in der Welt, nicht beim Tippen auf einem
 * Schild und nicht in den Einstellungsfenstern; dann zeichnet das Spiel den Chat im Vordergrund-Modus und rechnet mit der Hoehe des offenen Chats. Die
 * Umsetzung ist eigene Arbeit: Java; die Frage steht hier, die zwei Einspruenge ins Spiel sind duenn ({@code ChatPeekHudMixin}, {@code ChatPeekHeightMixin} in der
 * nicht zwingenden Mixin-Konfiguration: Passt eine Stelle nicht, fehlt nur diese Funktion).
 *
 * <p>Abweichung: Der Chat zeigt sich nur ohne Fenster oder bei einem Inventar oder Behaelter - in jedem anderen Fenster (Einstellungen, HUD-Editor, Amboss, Schild)
 * kann man tippen, und die Taste soll dann nichts tun. SkyHanni schliesst nur Schild, Einstellungen und HUD-Editor aus.
 */
public final class ChatPeek {

    private ChatPeek() {
    }

    /**
     * Soll der Chat jetzt offen gezeigt werden? Wird in jedem Bild mehrfach gefragt: Ohne gewaehlte Taste kostet es einen Feldzugriff.
     */
    public static boolean peek() {
        int key = ModConfig.INSTANCE.chatTab.peekChat;
        if (!isKey(key)) return false;
        Minecraft client = Minecraft.getInstance();
        boolean playerPresent = client != null && client.player != null;
        return peeking(key, playerPresent, playerPresent && screenAllows(client.gui.screen()), () -> ShortcutHandler.isDown(key));
    }

    /** Die Hoehe des offenen Chats in Pixeln (die der Spieler eingestellt hat) - mit ihr wird gerechnet, solange der Chat gezeigt wird */
    public static int focusedHeight() {
        return ChatComponent.getHeight(Minecraft.getInstance().options.chatHeightFocused().get());
    }

    /** Ist der Wert eine Taste? (Werte unter 32 sind keine Tasten; -1 heisst: keine gewaehlt) */
    static boolean isKey(int key) {
        return key >= 32;
    }

    /** Hinter welchem Fenster darf der Chat gezeigt werden? Ohne Fenster und hinter Inventar und Behaeltern */
    private static boolean screenAllows(Screen screen) {
        return screen == null || screen instanceof AbstractContainerScreen<?>;
    }

    /**
     * Die Regel, rein: gewaehlte Taste, Spieler in der Welt, ein Fenster, das das Tippen nicht braucht - und erst dann wird gefragt, ob die Taste unten ist.
     *
     * @param screenAllows ob das offene Fenster (oder keines) den Chat zulaesst
     * @param keyDown      fragt die Tastatur
     */
    static boolean peeking(int key, boolean playerPresent, boolean screenAllows, BooleanSupplier keyDown) {
        return isKey(key) && playerPresent && screenAllows && keyDown.getAsBoolean();
    }
}

package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.chat.CurrentChatDisplay;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.gui.HudEditorScreen;

import net.minecraft.client.Minecraft;

/**
 * Der Kasten zu Chat > Current Chat Display: "Chat: Party (3 members)".
 *
 * Herkunft der Idee: SkyHanni ("Current Chat Display"). Er steht in SkyBlock bei offenem Chat und zwei Sekunden danach, sobald die Mod gelesen hat, in welchem
 * Chat man ist; im HUD-Editor ({@code /shoki hud}) immer, mit einem Beispiel, damit er sich setzen laesst.
 *
 * <p>Ort: Standard ist wie bei SkyHanni kurz ueber der Chat-Eingabe ("Current Chat Display Free Position" aus): links am Rand des Chats, mit zwei Punkten Luft ueber dem
 * Eingabefeld. Das Feld des Spiels beginnt {@value #INPUT_HEIGHT} Punkte ueber dem unteren Rand des Bildschirms; die Rechnung haengt also an der Hoehe des Bildschirms
 * (in Spielpunkten), nicht an einem festen Bruchteil - der waere bei jeder Aufloesung und Skalierung ein anderer Ort. Mit "Free Position" an gilt die Lage des Kastens
 * aus dem HUD-Editor. (Die Einstellung heisst so herum, weil jeder Schalter dieser Gruppe von Haus aus aus ist.)
 */
public final class CurrentChatHud {

    static final String SAMPLE = "§aChat: §eAll";

    /** Das Eingabefeld des Chats beginnt so weit ueber dem unteren Rand des Bildschirms ({@code height - 14}, wie in {@code ChatScreen}) */
    static final int INPUT_HEIGHT = 14;
    /** Luft zwischen dem Kasten und dem Eingabefeld */
    static final int GAP = 2;
    /** Der linke Rand des Chats: Der Text der Chatzeilen beginnt bei vier Punkten */
    static final int LEFT_EDGE = 4;

    private CurrentChatHud() {
    }

    /** Soll der Kasten jetzt gezeichnet werden? */
    public static boolean shownNow() {
        if (!ModConfig.INSTANCE.chatTab.currentChatDisplay) return false;
        return HudEditorScreen.isOpen() || CurrentChatDisplay.visibleNow();
    }

    public static HudPanel build() {
        String line = CurrentChatDisplay.line();
        if (line.isEmpty() && HudEditorScreen.isOpen()) line = SAMPLE;
        HudPanel panel = new HudPanel();
        if (!line.isEmpty()) panel.line(line, HudColours.WHITE);
        return panel;
    }

    // ------------------------------------------------------------------ Ort

    /**
     * Der linke Rand als Bruchteil der Bildschirmbreite: (Vorgabe) am Rand des Chats, mit "Free Position" aus der Einstellung. Gibt es noch kein Fenster (ein Aufruf vor
     * dem Start des Spiels, oder ein Test), gilt die Einstellung.
     */
    public static float x() {
        ModConfig.ChatTabCategory c = ModConfig.INSTANCE.chatTab;
        int width = c.currentChatFreePosition ? 0 : guiWidth();
        return width > 0 ? anchoredX(width) : c.currentChatHudX;
    }

    /** Der obere Rand als Bruchteil der Bildschirmhoehe: (Vorgabe) so, dass der Kasten auf dem Eingabefeld aufsitzt, mit "Free Position" aus der Einstellung */
    public static float y() {
        ModConfig.ChatTabCategory c = ModConfig.INSTANCE.chatTab;
        int height = c.currentChatFreePosition ? 0 : guiHeight();
        return height > 0 ? anchoredY(height, boxHeight(c.currentChatHudScale)) : c.currentChatHudY;
    }

    private static int guiWidth() {
        Minecraft client = Minecraft.getInstance();
        return client == null || client.getWindow() == null ? 0 : client.getWindow().getGuiScaledWidth();
    }

    private static int guiHeight() {
        Minecraft client = Minecraft.getInstance();
        return client == null || client.getWindow() == null ? 0 : client.getWindow().getGuiScaledHeight();
    }

    /** Die Hoehe des einzeiligen Kastens auf dem Bildschirm (mit Skalierung), gerundet wie beim Zeichnen */
    static int boxHeight(float scale) {
        return Math.round(HudPanel.singleLineHeight() * scale);
    }

    /** Rein: der linke Rand (als Bruchteil), {@link #LEFT_EDGE} Punkte vom Rand */
    static float anchoredX(int guiWidth) {
        return (float) LEFT_EDGE / guiWidth;
    }

    /** Rein: der obere Rand (als Bruchteil) eines Kastens dieser Hoehe, der {@link #GAP} Punkte ueber dem Eingabefeld endet */
    static float anchoredY(int guiHeight, int boxHeight) {
        return (float) (guiHeight - INPUT_HEIGHT - GAP - boxHeight) / guiHeight;
    }
}

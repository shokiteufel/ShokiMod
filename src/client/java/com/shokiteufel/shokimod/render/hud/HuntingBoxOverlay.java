package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.gui.ShardProfitScreen;
import com.shokiteufel.shokimod.scanner.ShardStock;
import com.shokiteufel.shokimod.util.ShardProfitData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Was beim Blick in die Hunting-Box ueber ihr steht.
 *
 * Zwei Angaben und ein Knopf. Die Angaben beantworten die Frage, die man sich genau
 * hier stellt - "habe ich schon alles?" -, und der Knopf fuehrt zurueck zur Liste,
 * ohne den Umweg ueber den Chat.
 *
 * <p>Gelesen wird nur, was im Fenster steht: Eine Seite, die niemand aufgeschlagen
 * hat, sieht die Mod nicht. Automatisch durchzublaettern waere die bequemere Loesung
 * und kommt nicht in Frage - Klicks in Menues zu schicken, die niemand ausgeloest
 * hat, gilt bei Hypixel als Makro. Also sagt die Anzeige stattdessen offen, was noch
 * fehlt.
 */
public final class HuntingBoxOverlay {

    /** Wo der Kasten sitzt: ueber dem Fenster, in der Mitte */
    private static final int WIDTH = 260;
    private static final int HEIGHT = 34;
    private static final int BUTTON_WIDTH = 74;
    private static final int BUTTON_HEIGHT = 14;

    private HuntingBoxOverlay() {
    }

    /** Die beiden Textzeilen, gerechnet fuer einen Stand des Bestands - neu nur, wenn sich der Bestand aendert, nicht in jedem Bild */
    private static int textStand = -1;
    private static int erkanntGemerkt;
    private static int sortenGemerkt;
    private static String zeileGemerkt = "";
    private static String seitenGemerkt = "";
    private static boolean seitenUnvollstaendig;
    private static int fusionenBreite;
    private static Font textSchrift;

    private static void textePruefen(Font font) {
        // Auch die Fusionsdaten gehoeren zum Stand: kommen sie spaeter als der Bestand, aendert sich die Zahl der erkannten Sorten
        int stand = ShardStock.stand() * 31 + ShardProfitData.combinations();
        if (stand == textStand && font == textSchrift) return;
        textStand = stand;
        textSchrift = font;
        erkanntGemerkt = ShardProfitData.matched(ShardStock.counts());
        sortenGemerkt = ShardStock.kinds();
        zeileGemerkt = sortenGemerkt == 0
                ? "No shards read yet"
                : erkanntGemerkt + " of " + sortenGemerkt + " shards recognised";
        String note = ShardStock.pageNote();
        seitenUnvollstaendig = ShardStock.incomplete();
        seitenGemerkt = note.isEmpty() ? "" : note + (seitenUnvollstaendig ? " - page on!" : " - all read");
        fusionenBreite = font.width("Fusions");
    }

    /** Nur sichtbar, solange in der Box gelesen wird */
    public static boolean shown() {
        return ShardStock.inBox();
    }

    private static int left() {
        return (Minecraft.getInstance().getWindow().getGuiScaledWidth() - WIDTH) / 2;
    }

    private static int top() {
        return 4;
    }

    private static int buttonLeft() {
        return left() + WIDTH - BUTTON_WIDTH - 6;
    }

    private static int buttonTop() {
        return top() + HEIGHT - BUTTON_HEIGHT - 5;
    }

    public static void draw(GuiGraphicsExtractor graphics) {
        if (!shown()) return;
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        int left = left();
        int top = top();

        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xD0101010);
        graphics.fill(left, top, left + WIDTH, top + 1, 0xFF55FF55);

        textePruefen(font);
        int erkannt = erkanntGemerkt;
        int sorten = sortenGemerkt;
        graphics.text(font, zeileGemerkt, left + 6, top + 6,
                erkannt == 0 && sorten > 0 ? 0xFFFF5555 : 0xFF55FF55, false);

        // Was noch fehlt. Die Box nennt ihre Seitenzahl im eigenen Titel, deshalb
        // laesst sich das genau sagen statt nur zu ahnen
        if (!seitenGemerkt.isEmpty()) {
            graphics.text(font, seitenGemerkt,
                    left + 6, top + 19,
                    seitenUnvollstaendig ? 0xFFFFAA00 : 0xFF55FF55, false);
        } else if (sorten > 0) {
            graphics.text(font, "page on to read the rest", left + 6, top + 19, 0xFFAAAAAA, false);
        }

        boolean drauf = overButton(mouseX(), mouseY());
        int bl = buttonLeft();
        int bt = buttonTop();
        graphics.fill(bl, bt, bl + BUTTON_WIDTH, bt + BUTTON_HEIGHT, drauf ? 0xFF3C6E3C : 0xFF2A2A2A);
        graphics.fill(bl, bt, bl + BUTTON_WIDTH, bt + 1, 0xFF55FF55);
        graphics.text(font, "Fusions", bl + (BUTTON_WIDTH - fusionenBreite) / 2, bt + 3,
                0xFFFFFFFF, false);
    }

    private static double mouseX() {
        Minecraft client = Minecraft.getInstance();
        return client.mouseHandler.xpos() * client.getWindow().getGuiScaledWidth()
                / Math.max(1, client.getWindow().getScreenWidth());
    }

    private static double mouseY() {
        Minecraft client = Minecraft.getInstance();
        return client.mouseHandler.ypos() * client.getWindow().getGuiScaledHeight()
                / Math.max(1, client.getWindow().getScreenHeight());
    }

    private static boolean overButton(double x, double y) {
        int bl = buttonLeft();
        int bt = buttonTop();
        return x >= bl && x <= bl + BUTTON_WIDTH && y >= bt && y <= bt + BUTTON_HEIGHT;
    }

    /**
     * Ein Klick auf den Knopf fuehrt zur Liste zurueck.
     *
     * @return wahr, wenn der Klick hier verbraucht wurde - dann darf ihn das Fenster
     *         darunter nicht noch einmal bekommen, sonst wandert nebenbei ein Shard
     */
    public static boolean handleClick(double mouseX, double mouseY) {
        if (!shown() || !overButton(mouseX, mouseY)) return false;
        Minecraft client = Minecraft.getInstance();
        client.setScreen(new ShardProfitScreen(null));
        return true;
    }
}

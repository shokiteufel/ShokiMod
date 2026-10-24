package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.ReminderCategory;
import com.shokiteufel.shokimod.handler.DarkAuctionReminder;

import net.minecraft.client.Minecraft;

import java.util.Locale;

/**
 * Der Zeitkasten zur Dark Auction: wie lange es noch dauert, als Minuten und Sekunden.
 *
 * Die Zeit kommt von {@link DarkAuctionReminder#millisUntilOpening}, also allein aus der Uhr. Der Kasten ist
 * entweder immer da oder nur in den letzten Sekunden vor der Eroeffnung - dann ist er die meiste Zeit weg
 * und stoert nicht. Nach der Eroeffnung zaehlt er die naechste Stunde herunter (oder ist, im Modus "nur kurz
 * vorher", wieder weg).
 */
public final class DarkAuctionHud {

    private static final int LABEL_COLOUR = HudColours.GOLD;
    /** Ab hier wird die Zahl gelb, ab zehn Sekunden rot */
    private static final long WARN_MILLIS = 60_000L;
    private static final long URGENT_MILLIS = 10_000L;

    private DarkAuctionHud() {
    }

    private static ReminderCategory cfg() {
        return ModConfig.INSTANCE.chat.reminder;
    }

    /** Soll der Kasten jetzt gezeichnet werden? */
    public static boolean shownNow() {
        boolean editor = Minecraft.getInstance().screen instanceof com.shokiteufel.shokimod.gui.HudEditorScreen;
        return shownAt(System.currentTimeMillis(), editor);
    }

    /**
     * Dieselbe Frage zu einer vorgegebenen Zeit. Im HUD-Editor ist er immer da, sonst liesse er sich im
     * Modus "nur kurz vorher" nie setzen.
     */
    public static boolean shownAt(long now, boolean editorOpen) {
        ReminderCategory c = cfg();
        if (!c.darkAuctionHud) return false;
        if (!c.darkAuctionHudOnlyLast || editorOpen) return true;
        return DarkAuctionReminder.millisUntilOpening(now) <= seconds() * 1000L;
    }

    /** Die eingestellten Sekunden, auf 10 bis 60 zurechtgerueckt */
    private static int seconds() {
        return Math.max(10, Math.min(60, cfg().darkAuctionHudSeconds));
    }

    /** "5:07" - aufgerundet, damit die Anzeige erst auf 0:00 steht, wenn es wirklich so weit ist */
    public static String countdown(long millis) {
        long s = Math.max(0L, (millis + 999L) / 1000L);
        return String.format(Locale.ROOT, "%d:%02d", s / 60L, s % 60L);
    }

    public static HudPanel build() {
        return build(System.currentTimeMillis());
    }

    public static HudPanel build(long now) {
        long left = DarkAuctionReminder.millisUntilOpening(now);
        int colour = left <= URGENT_MILLIS ? HudColours.RED : left <= WARN_MILLIS ? HudColours.YELLOW : HudColours.WHITE;
        HudPanel panel = new HudPanel();
        panel.pair("Dark Auction", countdown(left), LABEL_COLOUR, colour);
        return panel;
    }
}

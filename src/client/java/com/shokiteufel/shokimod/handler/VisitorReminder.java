package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.GardenVisitorsCategory;
import com.shokiteufel.shokimod.scanner.GardenVisitors;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Die Erinnerung, wenn die Besucher im Garten Schlange stehen.
 *
 * Ist die Warteschlange voll, kommt kein weiterer Besucher, bis einer bedient ist - jede Minute
 * danach ist verschenkt. Wann gewarnt wird, und warum nur im Garten und beim Verlassen, steht in
 * {@link GardenReminder}; hier steht, was nur die Besucher betrifft.
 */
public final class VisitorReminder extends GardenReminder {

    private static final VisitorReminder INSTANCE = new VisitorReminder();

    private VisitorReminder() {
    }

    private static GardenVisitorsCategory cfg() {
        return ModConfig.INSTANCE.garden.visitors;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(INSTANCE::tick);
    }

    public static void reset() {
        INSTANCE.clear();
    }

    /** Knopf in den Einstellungen: die Zeilen mit einem Beispiel */
    public static void test() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        client.player.sendSystemMessage(INSTANCE.message(GardenVisitors.MAXIMUM, false));
        client.player.sendSystemMessage(INSTANCE.awayMessage(AWAY_MINUTES, 3, GardenVisitors.MAXIMUM));
        INSTANCE.sound(client);
    }

    @Override
    String tag() {
        return "[Visitors]";
    }

    @Override
    boolean isOn() {
        return cfg().alert;
    }

    @Override
    boolean available() {
        return GardenVisitors.available();
    }

    @Override
    int count() {
        return GardenVisitors.count();
    }

    /** Der Platz in der Warteschlange. Ohne freigeschaltete Besucher gibt es nichts, was voll werden koennte */
    @Override
    int total() {
        return GardenVisitors.notUnlocked() ? 0 : GardenVisitors.MAXIMUM;
    }

    /** "Queue Full!" zaehlt immer: Dann ist sie voll, auch wenn "Warn at" hoeher steht */
    @Override
    boolean reached() {
        if (GardenVisitors.notUnlocked()) return false;
        return GardenVisitors.queueFull() || GardenVisitors.count() >= Math.max(1, cfg().warnAt);
    }

    @Override
    boolean awayOn() {
        return cfg().alertOutside;
    }

    @Override
    long repeatMillis() {
        return Math.max(0, cfg().repeatMinutes) * 60_000L;
    }

    @Override
    boolean soundOn() {
        return cfg().sound;
    }

    @Override
    String soundFile() {
        return cfg().soundFile;
    }

    @Override
    String command() {
        return cfg().command;
    }

    @Override
    boolean waitingForWidget() {
        return GardenVisitors.waitingForWidget();
    }

    @Override
    void hintIfMissing() {
        GardenVisitors.hintIfMissing();
    }

    @Override
    String widgetHint() {
        return "The visitor warning needs Hypixel's Visitors widget. "
                + "Switch it on here in the garden - the tab list settings only offer it here.";
    }

    @Override
    void resetScanner() {
        GardenVisitors.reset();
    }

    /** Die Zeile: wie viele warten, und ein Knopf zum Hinreisen */
    @Override
    Component message(int count, boolean afterLeaving) {
        StringBuilder hover = new StringBuilder("Visitors waiting: ").append(count)
                .append(" of ").append(GardenVisitors.MAXIMUM);
        if (afterLeaving && lastSeenAt > 0) hover.append(readAt());

        String text = afterLeaving
                ? "You left the garden with " + count + (count == 1 ? " visitor waiting" : " visitors waiting")
                : (count == 1 ? "1 visitor is waiting" : count + " visitors are waiting");

        return line(text, hover.toString());
    }

    /** Die Zeile nach der Frist: Die Warteschlange ist voraussichtlich voll */
    @Override
    Component awayMessage(long minutes, int count, int total) {
        String hover = "When you left: " + count + " of " + total + " visitors waiting"
                + "\nThe garden does not report while you are away - go and look";
        String text = "You left the garden " + minutes + (minutes == 1 ? " minute" : " minutes")
                + " ago - the visitors are probably queued up by now";

        return line(text, hover);
    }
}

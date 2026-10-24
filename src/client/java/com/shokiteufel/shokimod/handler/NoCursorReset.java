package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;

/**
 * Laesst den Mauszeiger stehen, wenn sich ein Kisten-Fenster schliesst und gleich wieder eines oeffnet - nach Odins "No Cursor Reset".
 *
 * Herkunft: Odin (BSD-3-Clause, Odtheking), {@code features/impl/skyblock/NoCursorReset.kt} und {@code MouseHandlerMixin}. Das
 * Spiel setzt den Zeiger bei jedem Oeffnen eines Fensters in die Mitte; auf Servern wie Hypixel schliesst sich ein Menue aber oft
 * und ein anderes oeffnet sich im selben Atemzug, und der Zeiger springt jedes Mal weg. Dieselbe Regel wie bei Odin: Oeffnet sich ein
 * Kisten-Fenster ({@link ContainerScreen}) weniger als die eingestellte Zeit, nachdem sich das letzte Fenster geschlossen hat, geht
 * der Zeiger an die Stelle zurueck, an der er beim Schliessen stand.
 *
 * Anders als bei Odin: Die Zeit wird nicht jeden Tick nachgefuehrt, sondern im Mixin beim Schliessen genommen (das ist genauer, und
 * ein Fenster, das nur einen Tick offen war, zaehlt trotzdem); der Zeiger wird ueber GLFW gesetzt, weil es {@code InputConstants.releaseMouse}
 * nicht in jeder Version gibt; und welches Fenster sich oeffnet, erfaehrt dieser Handler aus dem Ereignis von Fabric - der Zugriff auf das
 * aktuelle Fenster heisst je Version anders, das Ereignis nicht.
 *
 * Kostet nichts, solange die Funktion aus ist: ein Schalter im Mixin, mehr wird nicht gelesen.
 */
public final class NoCursorReset {

    /** Vom Mixin in {@code MouseHandler} umgesetzt: setzt den Zeiger (und was das Spiel von ihm weiss) an eine Stelle */
    public interface Restorer {
        void shokimod$restoreCursor(double x, double y);
    }

    private static double beforeX;
    private static double beforeY;
    private static long closedAt = 0L;
    private static boolean pending = false;

    private NoCursorReset() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> onScreenInit(client, screen));
    }

    /** Vom Mixin: Die Maus wird gleich gegriffen, ein Fenster schliesst sich. Merkt Stelle und Zeit */
    public static void onGrab(double x, double y) {
        beforeX = x;
        beforeY = y;
        closedAt = System.currentTimeMillis();
        pending = false;
    }

    /** Vom Mixin: Die Maus wurde freigegeben, ein Fenster oeffnet sich. Ob der Zeiger zurueck soll, wird beim Aufbau des Fensters entschieden */
    public static void onRelease() {
        ModConfig.OdinCategory cfg = ModConfig.INSTANCE.odin;
        pending = cfg.noCursorReset && System.currentTimeMillis() - closedAt < cfg.noCursorResetTimeout;
    }

    private static void onScreenInit(Minecraft client, Screen screen) {
        if (!pending) return;
        pending = false;
        if (!(screen instanceof ContainerScreen)) return;
        if (client.mouseHandler instanceof Restorer restorer) restorer.shokimod$restoreCursor(beforeX, beforeY);
    }
}

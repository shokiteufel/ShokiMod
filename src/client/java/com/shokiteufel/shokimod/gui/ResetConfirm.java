package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Die Sicherheitsfrage vor dem Reset eines Trackers.
 *
 * Ein Klick auf [Reset] im Kasten oder auf "Reset" in den Einstellungen loescht Zaehlstand und Zeit, und das laesst sich nicht
 * rueckgaengig machen. Darum fragt jeder Tracker erst nach, in demselben Fenster, egal woher der Klick kommt: Die Kaesten nehmen
 * Klicks nur im eigenen Inventar an, die Knoepfe sitzen im Einstellungsfenster - in beiden Faellen liegt der Chat hinter dem Fenster,
 * eine Frage dort waere nicht zu erreichen. Nach der Antwort geht es dorthin zurueck, wo der Klick herkam.
 *
 * Ausschalten laesst sich die Frage mit "Ask before reset" im Reiter HUD - dann geschieht der Reset wieder sofort. Die Frage vor dem
 * Entfernen einer einzelnen Ware ({@link #confirm}) laesst sich nicht abschalten.
 *
 * Alle Tracker gehen durch diese eine Stelle: Ein neuer braucht nur den Aufruf, keine eigene Frage und keinen eigenen Schalter.
 */
public final class ResetConfirm {

    private ResetConfirm() {
    }

    /**
     * @param tracker der Name fuer die Frage, etwa "Hunting tracker"
     * @param effect  was verschwindet - ein Satz, hinter den "This cannot be undone." kommt
     * @param reset   was bei "Yes" geschieht (oder sofort, wenn die Frage aus ist)
     */
    public static void ask(String tracker, String effect, Runnable reset) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || !ModConfig.INSTANCE.hud.confirmReset) {
            reset.run();
            return;
        }
        show(client, "Reset the " + tracker + "?", effect, reset);
    }

    /**
     * Die Frage vor einer einzelnen Sache, die ein Klick aus einem Tracker nimmt - etwa das rote Kreuz an einer Ware im Profit Tracker.
     * Dasselbe Fenster wie beim Reset, aber ohne Schalter: Diese Frage gab es schon, bevor es "Ask before reset" gab, und sie
     * ging nie ab - ein Klick daneben soll die Ware nicht kosten.
     *
     * @param question die Frage als Ueberschrift, etwa "Remove Enchanted Hay Bale x120?"
     * @param effect   was verschwindet - ein Satz, hinter den "This cannot be undone." kommt
     * @param yes      was bei "Yes" geschieht
     */
    public static void confirm(String question, String effect, Runnable yes) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        show(client, question, effect, yes);
    }

    private static void show(Minecraft client, String title, String effect, Runnable yes) {
        Screen back = currentScreen(client);
        client.setScreenAndShow(new ConfirmScreen(answer -> {
            // Erst zurueck, dann die Tat: So steht der Kasten schon wieder da, wenn sich sein Stand aendert
            client.setScreenAndShow(back);
            if (answer) yes.run();
        }, Component.literal(title), Component.literal(effect + " This cannot be undone.")));
    }

    /** Das Fenster, in dem der Klick geschah - oder null, wenn gerade keines offen ist */
    private static Screen currentScreen(Minecraft client) {
        return client.screen;
    }
}

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
 * Ausschalten laesst sich die Frage mit "Ask before reset" im Reiter HUD - dann geschieht der Reset wieder sofort.
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
        Screen back = currentScreen(client);
        client.setScreenAndShow(new ConfirmScreen(yes -> {
            // Erst zurueck, dann der Reset: So steht der Kasten schon wieder da, wenn er auf null springt
            client.setScreenAndShow(back);
            if (yes) reset.run();
        }, Component.literal("Reset the " + tracker + "?"), Component.literal(effect + " This cannot be undone.")));
    }

    /** Das Fenster, in dem der Klick geschah - oder null, wenn gerade keines offen ist */
    private static Screen currentScreen(Minecraft client) {
        return client.gui.screen();
    }
}

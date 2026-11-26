package com.shokiteufel.shokimod.chat;

import net.minecraft.network.chat.Component;

/**
 * Eine eingehende Chatzeile fuer die Regeln, die sie umschreiben: der Klartext (damit laufen die Muster) und - erst wenn eine Regel es braucht - die Zeile als
 * {@link StyledText} mit Farben, Klick und Text beim Zeigen. So kostet eine Zeile, die keine Regel trifft, keinen Aufbau der Stuecke.
 *
 * Kommt die Komponente nicht mit (null) oder weicht ihr Klartext vom uebergebenen ab, besteht die Zeile aus dem Klartext allein, ohne Stil.
 */
final class Incoming {

    final String plain;
    private final Component message;
    private StyledText styled;

    Incoming(String plain, Component message) {
        this.plain = plain;
        this.message = message;
    }

    /** Die Zeile mit Stilen; einmal gebaut */
    StyledText styled() {
        if (styled == null) {
            StyledText fromMessage = message == null ? null : StyledText.of(message);
            styled = fromMessage != null && fromMessage.plain().equals(plain) ? fromMessage : StyledText.ofPlain(plain);
        }
        return styled;
    }
}

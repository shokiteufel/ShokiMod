package com.shokiteufel.shokimod.paket;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;

/**
 * Bestimmt die Verbindungsphase eines <b>ausgehenden</b> Pakets.
 *
 * <p>Der Empfaenger der Verbindung allein genuegt nicht: Beim Phasenwechsel tauscht das Spiel den Empfaenger aus, bevor es das
 * letzte Paket der alten Phase sendet (LoginAcknowledged geht in der Login-Phase raus, der Empfaenger ist aber schon der der
 * Konfiguration). Deshalb entscheidet zuerst das Paket selbst: Die Klassen der Phasen liegen in eigenen Paketen
 * ({@code ...protocol.login}, {@code ...configuration}, {@code ...game}, ...). Nur Pakete, die in mehreren Phasen vorkommen
 * ({@code ...common}, {@code ...cookie}, {@code ...ping}, zum Beispiel KeepAlive und CustomPayload), fragen den Empfaenger.
 */
final class PaketProtokoll {
    private static final ClassValue<ConnectionProtocol> NACH_KLASSE = new ClassValue<>() {
        @Override
        protected ConnectionProtocol computeValue(Class<?> klasse) {
            return nachName(klasse.getName());
        }
    };

    private PaketProtokoll() {
    }

    static ConnectionProtocol von(Packet<?> paket, PacketListener empfaenger) {
        ConnectionProtocol eigene = NACH_KLASSE.get(paket.getClass());
        if (eigene != null) {
            return eigene;
        }
        return empfaenger == null ? null : empfaenger.protocol();
    }

    /** Die Phase, die der Paketname festlegt; {@code null}, wenn das Paket in mehreren Phasen vorkommt oder unbekannt ist. */
    static ConnectionProtocol nachName(String klassenName) {
        String wurzel = "net.minecraft.network.protocol.";
        if (!klassenName.startsWith(wurzel)) {
            return null;
        }
        String rest = klassenName.substring(wurzel.length());
        if (rest.startsWith("handshake.")) {
            return ConnectionProtocol.HANDSHAKING;
        }
        if (rest.startsWith("login.")) {
            return ConnectionProtocol.LOGIN;
        }
        if (rest.startsWith("status.")) {
            return ConnectionProtocol.STATUS;
        }
        if (rest.startsWith("configuration.")) {
            return ConnectionProtocol.CONFIGURATION;
        }
        if (rest.startsWith("game.")) {
            return ConnectionProtocol.PLAY;
        }
        return null;
    }
}

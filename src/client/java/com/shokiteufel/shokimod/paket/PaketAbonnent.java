package com.shokiteufel.shokimod.paket;

import net.minecraft.network.protocol.Packet;

/**
 * Empfaenger der Paket-Ereignisse. Wird auf dem Haupt-Thread (Client-Thread) aufgerufen, in der Reihenfolge, in der das
 * Spiel die Pakete bearbeitet, und darf deshalb den Spielzustand lesen.
 *
 * <p>Der {@link PaketKopf} gilt nur fuer diesen Aufruf; wer ihn behalten will, kopiert die Zahlen. Das Paket selbst
 * darf nicht veraendert werden (es gehoert dem Spiel, das es danach noch anwendet).
 *
 * <p>Ein geworfener Fehler stoert weder das Spiel noch die anderen Abonnenten: Der Bus faengt ihn, zaehlt ihn und schaltet
 * den Abonnenten nach {@value PaketBus#MAX_FEHLER_JE_ABONNENT} Fehlern ab.
 */
@FunctionalInterface
public interface PaketAbonnent<P extends Packet<?>> {

    void bei(P paket, PaketKopf kopf);
}

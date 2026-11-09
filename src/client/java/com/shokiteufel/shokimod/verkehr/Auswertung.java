package com.shokiteufel.shokimod.verkehr;

import net.minecraft.network.protocol.Packet;

/**
 * Die Einordnung der Pakete, die der Paket-Bus jedem Paket mitgibt: gehoert es zum eigenen Spieler, zu anderen, zu beiden oder zu keinem
 * ({@link Einordnung})? Die Regeln stecken im {@code Klassifizierer}.
 *
 * Die Schnittstelle gibt es, damit Tests eine Attrappe einsetzen koennen, die aufschreibt, wann gelernt und eingeordnet wird.
 */
public interface Auswertung {

    /**
     * Ordnet ein <b>vom Server kommendes</b> Paket in eigen, fremd, gemischt oder Welt ein (siehe {@link Einordnung}). Wird vom
     * Paket-Bus im Haupt-Thread aufgerufen, in genau der Reihenfolge, in der das Spiel die Pakete bearbeitet. {@code kontext}
     * ist dabei der Zustand des Busses (eigene ID und UUID, so gelernt, wie die Pakete in dieser Reihenfolge ankommen).
     */
    Einordnung einordnen(Packet<?> paket, Kontext kontext);

    /**
     * Lernt aus einem vom Server kommenden Paket den Kontext (die eigene Entity-ID aus dem Login-Paket, die eigene UUID aus der
     * Spielerliste), ohne es einzuordnen. Der Paket-Bus ruft das fuer <b>jedes</b> eingehende Paket auf, auch wenn niemand es
     * abonniert hat, damit der Kontext bei einem spaeteren Abonnenten stimmt.
     */
    void lernen(Packet<?> paket, Kontext kontext);
}

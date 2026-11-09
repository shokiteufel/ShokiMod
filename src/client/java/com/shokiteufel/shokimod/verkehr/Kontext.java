package com.shokiteufel.shokimod.verkehr;

import java.util.UUID;

/**
 * Was die Einordnung ueber den eigenen Spieler weiss. Wird waehrend der Verbindung gelernt:
 * die eigene Entity-ID aus dem Login-Paket, die eigene UUID aus der Spielerliste (ueber den Namen).
 * Die Werte werden auf dem Netzwerk-Thread geschrieben und anderswo gelesen, deshalb volatile.
 */
public final class Kontext {
    private volatile int eigeneId = -1;
    private volatile UUID eigeneUuid;
    private volatile String eigenerName;
    private volatile UUID kontoUuid;

    public int eigeneId() {
        return eigeneId;
    }

    public void setEigeneId(int id) {
        this.eigeneId = id;
    }

    /** Eigene UUID so, wie der Server sie in der Spielerliste fuehrt (auf Offline-Servern anders als das Konto). */
    public UUID eigeneUuid() {
        return eigeneUuid;
    }

    public void setEigeneUuid(UUID uuid) {
        this.eigeneUuid = uuid;
    }

    public String eigenerName() {
        return eigenerName;
    }

    /** UUID des angemeldeten Kontos, Ersatzwert, solange die Spielerliste die eigene UUID nicht genannt hat. */
    public UUID kontoUuid() {
        return kontoUuid;
    }

    public void setKonto(String name, UUID uuid) {
        this.eigenerName = name;
        this.kontoUuid = uuid;
    }

    /** Die UUID, mit der eigene Eintraege erkannt werden: erst die aus der Spielerliste, sonst die des Kontos. */
    public UUID wirksameUuid() {
        UUID aus = eigeneUuid;
        return aus != null ? aus : kontoUuid;
    }

    public void zuruecksetzen() {
        eigeneId = -1;
        eigeneUuid = null;
    }
}

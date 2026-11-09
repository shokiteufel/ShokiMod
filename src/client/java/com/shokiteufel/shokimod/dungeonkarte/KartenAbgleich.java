package com.shokiteufel.shokimod.dungeonkarte;

/**
 * Der Abgleich der Karteneinstellungen zwischen NoammAddons und der Funny Map in beide Richtungen: Aendert sich bei Noamm etwas, folgt die Funny
 * Map; aendert man bei der Funny Map etwas, folgt Noamm. Zu Beginn gilt, was bei Noamm steht.
 *
 * Der Abgleich merkt sich, wie beide Seiten beim letzten Mal aussahen ({@code letzterNoamm}, {@code letzterEigener}) und vergleicht jede Seite
 * mit ihrem eigenen Stand von damals. So gilt als "geaendert", was jemand geaendert hat - nicht, was durch Rundung zwischen den beiden Seiten
 * abweicht (die Funny Map hat manche Werte als ganze Zahl, Noamm als Zahl mit Komma). Reine Rechnung, ohne Minecraft.
 */
public final class KartenAbgleich {

    private KartenAbgleich() {
    }

    /** Alle Einstellungen der Karte samt Lage (x, y und Groesse der Anzeige) */
    public record Stand(KartenEinstellungen einstellungen, float x, float y, float scale) {
        boolean gleich(Stand anderer) {
            return anderer != null
                    && einstellungen.gleich(anderer.einstellungen)
                    && Math.abs(x - anderer.x) <= 0.01f && Math.abs(y - anderer.y) <= 0.01f && Math.abs(scale - anderer.scale) <= 0.001f;
        }
    }

    public enum Richtung {
        /** Nichts hat sich geaendert */
        NICHTS,
        /** Bei Noamm hat sich etwas geaendert (oder es ist der Anfang): die Funny Map uebernimmt */
        NOAMM_NACH_UNS,
        /** Bei der Funny Map hat sich etwas geaendert: Noamm bekommt es */
        UNS_NACH_NOAMM
    }

    /**
     * Was ist zu tun? Am Anfang (noch nichts gemerkt) gilt Noamm. Haben sich beide Seiten im selben Augenblick geaendert, gewinnt Noamm: Dort stehen
     * die Einstellungen, die man zuerst hatte.
     */
    public static Richtung entscheide(Stand letzterNoamm, Stand letzterEigener, Stand noammJetzt, Stand eigenerJetzt) {
        if (noammJetzt == null) return Richtung.NICHTS;
        if (letzterNoamm == null || letzterEigener == null) return Richtung.NOAMM_NACH_UNS;
        if (!noammJetzt.gleich(letzterNoamm)) return Richtung.NOAMM_NACH_UNS;
        if (eigenerJetzt != null && !eigenerJetzt.gleich(letzterEigener)) return Richtung.UNS_NACH_NOAMM;
        return Richtung.NICHTS;
    }
}

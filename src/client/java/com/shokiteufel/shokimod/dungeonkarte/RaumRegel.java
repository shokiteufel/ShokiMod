package com.shokiteufel.shokimod.dungeonkarte;

import java.util.List;

/**
 * Die Raum-Regel der Stern-Mobs ("Only in my room"): Ein Stern-Mob zaehlt nur, wenn er im selben Raum steht wie man selbst. Ein Raum ist eine
 * Gruppe von Plaetzen des Rasters (ein grosser Raum hat mehrere), ein Mob gehoert zu dem Platz, auf dem er steht ({@link SecretsLeiste#platz}).
 * Reine Rechnung ohne Minecraft.
 */
public final class RaumRegel {

    private RaumRegel() {
    }

    /**
     * Darf ein Mob an dieser Stelle gezeigt werden?
     *
     * @param meinRaum die Plaetze des Raums, in dem man steht; {@code null}, wenn er nicht bekannt ist (dann zeigen alle, damit nichts verschwindet,
     *                 nur weil der Scan noch nicht so weit ist oder man im Boss-Raum steht)
     */
    public static boolean erlaubt(List<Integer> meinRaum, double mobX, double mobZ) {
        if (meinRaum == null) return true;
        int platz = SecretsLeiste.platz(mobX, mobZ);
        return platz >= 0 && meinRaum.contains(platz);
    }
}

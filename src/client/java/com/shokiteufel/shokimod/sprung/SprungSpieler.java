package com.shokiteufel.shokimod.sprung;

import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Ein Mitspieler im Leap Menu: Name, Klasse und ob er tot ist - aus der Tab-Liste (siehe {@link SprungTeam}). Wie {@code DungeonPlayer} bei
 * NoammAddons bleibt die Klasse erhalten, wenn er stirbt: Die Tab-Liste schreibt dann "(DEAD)" statt der Klasse.
 */
public final class SprungSpieler {

    public final String name;
    public SprungKlasse klasse;
    public boolean tot;
    /** Das Bild des Kopfes aus der Spielerliste (null, solange es keins gibt) */
    public PlayerSkin haut;
    /** Wann das Bild gelesen wurde (Nanosekunden) - es aendert sich selten und wird nur alle paar Sekunden neu gelesen */
    long hautGelesen;

    public SprungSpieler(String name, SprungKlasse klasse, boolean tot) {
        this.name = name;
        this.klasse = klasse;
        this.tot = tot;
    }
}

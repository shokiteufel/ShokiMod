package com.shokiteufel.shokimod.data;

import com.google.gson.annotations.Expose;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Eine Taste, die einen Befehl oder Chattext abschickt - einmal je Druck.
 *
 * Auf einer Taste kann mehr als ein Eintrag liegen, aber nur einer davon ist an: Wer den Haken
 * bei einem setzt, nimmt ihn bei den anderen auf derselben Taste heraus. So bleiben alle
 * Varianten gespeichert, und ein Klick wechselt, welche gilt.
 */
public class Shortcut {

    /** Der Tastencode (GLFW), 0 heisst: noch keine Taste gewaehlt */
    @Expose
    public int key = 0;

    /** Was geschickt wird. Mit "/" am Anfang ein Befehl, sonst ein Satz in den Chat */
    @Expose
    public String command = "";

    @Expose
    public boolean enabled = true;

    /** Noch nichts eingetragen: So ein Eintrag wird beim Schliessen des Fensters verworfen */
    public boolean isUntouched() {
        return key == 0 && (command == null || command.isBlank());
    }

    /** Gilt dieser Eintrag gerade? Ohne Taste und ohne Text gibt es nichts zu tun */
    public boolean isActive() {
        return enabled && key != 0 && command != null && !command.isBlank();
    }

    /**
     * Macht diesen Eintrag zum einzigen angeschalteten auf seiner Taste.
     *
     * Gilt fuer jede Beruehrung, die den Eintrag an lassen oder anmachen will: den Haken setzen
     * und auch eine Taste waehlen, auf der schon ein anderer liegt. Der zuletzt angefasste
     * gewinnt - was man gerade tut, ist das, was man meint.
     */
    public void takeOverKey(List<Shortcut> all) {
        if (key == 0) return;
        for (Shortcut other : all) {
            if (other != this && other.key == key) other.enabled = false;
        }
    }

    /**
     * Nach dem Laden: Auf jeder Taste bleibt hoechstens der erste angeschaltete Eintrag an.
     * Eine von Hand bearbeitete Datei soll nicht zwei Befehle auf einen Druck legen.
     */
    public static void normalize(List<Shortcut> all) {
        all.removeIf(java.util.Objects::isNull);
        Set<Integer> taken = new HashSet<>();
        for (Shortcut shortcut : all) {
            if (shortcut.command == null) shortcut.command = "";
            if (!shortcut.enabled || shortcut.key == 0) continue;
            if (!taken.add(shortcut.key)) shortcut.enabled = false;
        }
    }
}

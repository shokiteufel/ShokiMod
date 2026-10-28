package com.shokiteufel.shokimod.util;

import com.shokiteufel.shokimod.data.ModConfig;

import java.util.Iterator;
import java.util.Map;

/**
 * Die Lautstaerke je Sounddatei im Ordner (config/shokimod/sounds), eingestellt im Fenster "Sound Settings" unter Alerts.
 *
 * Sie gilt fuer die Datei, nicht fuer eine Regel: CustomSoundPlayer.play schickt jeden Ton einer Datei durch diesen Faktor, ganz gleich
 * wer ihn spielt - Chatregel, seltener Fund, Contest, Deployable. Ist eine Datei insgesamt zu laut, dreht man sie einmal herunter und
 * nicht in jeder Regel. Der Faktor kommt zum Alert Volume (AlertVolume) und zur Lautstaerke der einzelnen Regel hinzu und ersetzt
 * beides nicht.
 *
 * Gespeichert wird nur, was von 100 abweicht - eine Datei ohne Eintrag spielt wie immer. Der Bereich ist 0 bis 100: Der Regler
 * macht eine Datei leiser, nie lauter, denn der Abspieler deckelt jede Lautstaerke bei 1,0 (CustomSoundPlayer.applyVolume).
 */
public final class SoundVolumes {

    public static final int FULL = 100;

    private SoundVolumes() {
    }

    private static Map<String, Integer> table() {
        return ModConfig.INSTANCE.chat.soundVolumes;
    }

    private static int clamp(int percent) {
        return Math.max(0, Math.min(FULL, percent));
    }

    /** Die Lautstaerke dieser Datei in Prozent, 0 bis 100; ohne Eintrag 100 */
    public static int percent(String fileName) {
        if (fileName == null || fileName.isBlank()) return FULL;
        Map<String, Integer> map = table();
        if (map == null || map.isEmpty()) return FULL;

        Integer found = map.get(fileName);
        if (found == null) {
            // Windows unterscheidet bei Dateinamen nicht zwischen gross und klein
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(fileName)) {
                    found = entry.getValue();
                    break;
                }
            }
        }
        return found == null ? FULL : clamp(found);
    }

    /** Der Faktor fuer den Abspieler, 0,0 bis 1,0 */
    public static float factor(String fileName) {
        return percent(fileName) / 100f;
    }

    /** Stellt die Lautstaerke einer Datei ein; 100 loescht den Eintrag */
    public static void set(String fileName, int percent) {
        if (fileName == null || fileName.isBlank()) return;
        Map<String, Integer> map = table();
        if (map == null) return;

        remove(map, fileName);
        int value = clamp(percent);
        if (value < FULL) map.put(fileName, value);
    }

    public static void reset(String fileName) {
        Map<String, Integer> map = table();
        if (map != null && fileName != null) remove(map, fileName);
    }

    public static void resetAll() {
        Map<String, Integer> map = table();
        if (map != null) map.clear();
    }

    /** Wie viele Dateien weichen von 100 ab */
    public static int changed() {
        Map<String, Integer> map = table();
        return map == null ? 0 : map.size();
    }

    private static void remove(Map<String, Integer> map, String fileName) {
        for (Iterator<String> it = map.keySet().iterator(); it.hasNext(); ) {
            if (it.next().equalsIgnoreCase(fileName)) it.remove();
        }
    }
}

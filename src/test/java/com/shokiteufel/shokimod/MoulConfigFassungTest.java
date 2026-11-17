package com.shokiteufel.shokimod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die MoulConfig-Fassung in der Jar muss zur Spielversion passen (User 17.11.: "bei 26.3 freezed das Game, wenn man /shoki benutzt").
 *
 * Ursache war: Der Zweig 26.3.x lieferte {@code modern-26.2} mit. Die ruft {@code InputConstants.isKeyDown(Window, int)} auf, das es auf
 * 26.3 (SDL statt GLFW) nicht gibt - {@code NoSuchMethodError} in jedem Frame, sobald das Einstellungsfenster offen ist. Beim Bauen und beim
 * Start faellt das nicht auf, nur beim Oeffnen des Fensters. Dieser Test liest die Baudatei und die Versionsdatei und vergleicht
 * "Haupt.Neben" der Spielversion mit dem Namen der Fassung ({@code modern-26.3} zu {@code minecraft_version=26.3}).
 */
class MoulConfigFassungTest {

    private static final Pattern FASSUNG = Pattern.compile("org\\.notenoughupdates\\.moulconfig:modern-(\\d+\\.\\d+):");
    private static final Pattern SPIELVERSION = Pattern.compile("^minecraft_version=(\\d+\\.\\d+)", Pattern.MULTILINE);

    private static String lesen(String datei) throws IOException {
        return Files.readString(Path.of(datei));
    }

    private static List<String> fassungen(String baudatei) {
        List<String> gefunden = new ArrayList<>();
        Matcher m = FASSUNG.matcher(baudatei);
        while (m.find()) gefunden.add(m.group(1));
        return gefunden;
    }

    @Test
    void theBundledMoulConfigBelongsToTheGameVersion() throws IOException {
        Matcher spiel = SPIELVERSION.matcher(lesen("gradle.properties"));
        assertTrue(spiel.find(), "minecraft_version nicht gefunden");
        String version = spiel.group(1);

        List<String> fassungen = fassungen(lesen("build.gradle"));
        assertFalse(fassungen.isEmpty(), "keine MoulConfig-Abhaengigkeit gefunden");
        for (String f : fassungen) {
            assertEquals(version, f, "MoulConfig modern-" + f + " passt nicht zu Minecraft " + version
                    + " - das Einstellungsfenster stuerzt/friert dort ein");
        }
    }

    @Test
    void implementationAndIncludeNameTheSameVersion() throws IOException {
        List<String> fassungen = fassungen(lesen("build.gradle"));
        assertEquals(2, fassungen.size(), "erwartet: implementation und include, je einmal");
        assertEquals(fassungen.get(0), fassungen.get(1), "implementation und include muessen dieselbe Fassung nennen");
    }
}

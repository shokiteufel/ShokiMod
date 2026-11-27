package com.shokiteufel.shokimod.handler;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Die Maustasten auf 26.3: Das Spiel zaehlt sie nach SDL (links = 1, Mitte = 2, rechts = 3), nicht mehr nach GLFW (links = 0, rechts = 1). Bis 2.5.18 stand in
 * Copy Chat und im HUD-Editor "button() == 1" fuer rechts - dort traf es die LINKE Taste.
 */
class MouseButtonsTest {

    @Test
    void thisGameVersionCountsTheButtonsTheSdlWay() {
        // Der Anker: aendert eine kuenftige Version die Zahlen wieder, soll dieser Test auffallen, bevor ein Klick danebengeht
        assertEquals(1, InputConstants.MOUSE_BUTTON_LEFT);
        assertEquals(2, InputConstants.MOUSE_BUTTON_MIDDLE);
        assertEquals(3, InputConstants.MOUSE_BUTTON_RIGHT);
    }

    @Test
    void rightIsTheRightButtonAndNotTheLeftOne() {
        assertTrue(MouseButtons.isRight(InputConstants.MOUSE_BUTTON_RIGHT));
        assertFalse(MouseButtons.isRight(InputConstants.MOUSE_BUTTON_LEFT), "die Taste 1 ist auf 26.3 die linke");
        assertFalse(MouseButtons.isRight(InputConstants.MOUSE_BUTTON_MIDDLE));
    }

    @Test
    void leftIsTheLeftButtonAndNotTheRightOne() {
        assertTrue(MouseButtons.isLeft(InputConstants.MOUSE_BUTTON_LEFT));
        assertFalse(MouseButtons.isLeft(InputConstants.MOUSE_BUTTON_RIGHT));
        assertFalse(MouseButtons.isLeft(InputConstants.MOUSE_BUTTON_MIDDLE));
        assertFalse(MouseButtons.isLeft(0), "die 0 war links bei GLFW und ist es hier nicht");
    }

    @Test
    void sideButtonsAndNonsenseAreNeitherLeftNorRight() {
        for (int button : new int[] {0, 4, 5, 8, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertFalse(MouseButtons.isRight(button), "Taste " + button);
            assertFalse(MouseButtons.isLeft(button), "Taste " + button);
        }
    }

    /**
     * Wacht darueber, dass keine Stelle der Mod wieder eine Maustaste als Zahl vergleicht ("button() == 1", "!= 0" ...): Das ging bei der Umstellung auf SDL still
     * schief. Wer die Taste braucht, fragt MouseButtons.
     */
    @Test
    void noPlaceInTheModComparesAMouseButtonWithANumber() throws IOException {
        Path root = Path.of("src", "client", "java");
        assumeTrue(Files.isDirectory(root), "Quelltext nicht da (Test laeuft ausserhalb des Projekts)");
        Pattern raw = Pattern.compile("\\.button\\(\\)\\s*[=!<>]=?\\s*-?\\d");
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (!line.trim().startsWith("//") && !line.trim().startsWith("*") && raw.matcher(line).find()) {
                        found.add(file + ":" + (i + 1) + "  " + line.trim());
                    }
                }
            }
        }
        assertTrue(found.isEmpty(), "Maustaste als Zahl verglichen (MouseButtons.isLeft/isRight nehmen): " + found);
    }
}

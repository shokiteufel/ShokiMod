package com.shokiteufel.shokimod.util;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Waechter: Kein Ton der Mod darf am {@link ModSound} vorbei in Minecrafts Tonsystem gehen. Sonst haelt ihn der Alarm aus dem Biss-Ton fuer einen Biss, und die
 * Lautstaerke-Einstellung wuerde ihn leiser stellen (Vorgabe des Users, 28.11.: die Sounds von ShokiMod sollen weiterhin abgespielt werden).
 *
 * Gesucht wird im Quelltext der Mod nach den Wegen, einen Ton zu starten. Erlaubt sind sie nur in {@code ModSound} selbst. Wer einen neuen Ton braucht, nimmt
 * {@code ModSound.play} oder {@code ModSound.playUi}; die eigenen Audiodateien ({@code CustomSoundPlayer}) laufen ueber Javas Audio und sind davon nicht betroffen.
 */
class ModSoundSourceTest {

    private static final List<String> FORBIDDEN = List.of(".playSound(", ".playLocalSound(", "getSoundManager().play(", "SimpleSoundInstance.forUI(", "SimpleSoundInstance.forMusic(");

    @Test
    void everySoundOfTheModGoesThroughModSound() throws IOException {
        Path root = Path.of("src", "client", "java");
        Assumptions.assumeTrue(Files.isDirectory(root), "Quelltext nicht da, wo der Test laeuft: " + root.toAbsolutePath());

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                if (file.getFileName().toString().equals("ModSound.java")) continue;
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    // Kommentare und Dokumentation duerfen davon reden
                    if (line.startsWith("*") || line.startsWith("//") || line.startsWith("/*")) continue;
                    for (String pattern : FORBIDDEN) {
                        if (line.contains(pattern)) offenders.add(file.getFileName() + ":" + (i + 1) + " " + line);
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Diese Stellen spielen einen Ton an ModSound vorbei (ModSound.play / ModSound.playUi nehmen):\n" + String.join("\n", offenders));
    }
}

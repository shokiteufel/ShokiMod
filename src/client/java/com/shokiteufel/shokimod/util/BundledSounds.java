package com.shokiteufel.shokimod.util;

import com.shokiteufel.shokimod.ShokiMod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Toene, die der Mod beiliegen.
 *
 * Der Soundspieler liest nur aus config/shokimod/sounds - so kann jeder seine eigenen
 * Dateien dazulegen. Was die Mod selbst mitbringt, wird beim Start einmal dorthin
 * kopiert, sofern dort noch nichts gleichen Namens liegt. Wer die Datei ersetzt,
 * behaelt seine; wer sie loescht, bekommt beim naechsten Start wieder die Vorlage.
 */
public final class BundledSounds {

    private static final String RESOURCE_ROOT = "/assets/shokimod/sounds/";
    private static final List<String> FILES = List.of("shiny-alert.mp3");

    private BundledSounds() {
    }

    public static void seed() {
        Path directory = CustomSoundPlayer.SOUND_DIRECTORY;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            ShokiMod.LOGGER.warn("[ShokiMod] Could not create sound folder {}: {}", directory, e.toString());
            return;
        }

        for (String name : FILES) {
            Path target = directory.resolve(name);
            if (Files.exists(target)) continue;

            try (InputStream in = BundledSounds.class.getResourceAsStream(RESOURCE_ROOT + name)) {
                if (in == null) {
                    ShokiMod.LOGGER.warn("[ShokiMod] Bundled sound {} is missing from the jar.", name);
                    continue;
                }
                Files.copy(in, target);
                ShokiMod.LOGGER.info("[ShokiMod] Placed bundled sound {} into {}", name, directory);
            } catch (IOException e) {
                ShokiMod.LOGGER.warn("[ShokiMod] Could not place bundled sound {}: {}", name, e.toString());
            }
        }
    }
}

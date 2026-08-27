package com.shokiteufel.shokimod.util;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * Wo diese Mod ihre Dateien ablegt.
 *
 * <p>Bis zur Umbenennung lag alles in {@code config/gankura}. Der eigene Ordner ist Pflicht,
 * sobald das Original GanKura danebenliegt - sonst schreiben beide Mods in dieselben Dateien
 * und überschreiben sich gegenseitig die Einstellungen.
 *
 * <p>Damit dabei nichts verloren geht, wird der alte Ordner beim ersten Zugriff einmalig
 * kopiert, nicht verschoben: das Original findet seine Einstellungen anschließend unverändert
 * vor. Die beiden Dateien mit altem Namen bekommen dabei den neuen Namen.
 */
public final class ModPaths {

    private static final Logger LOGGER = LoggerFactory.getLogger("ShokiModPaths");

    private static final String DIRECTORY = "shokimod";
    private static final String LEGACY_DIRECTORY = "gankura";

    private static Path directory;

    private ModPaths() {
    }

    /** Der Ordner dieser Mod. Beim ersten Aufruf wird ein alter GanKura-Ordner übernommen. */
    public static synchronized Path configDir() {
        if (directory != null) return directory;

        Path root = FabricLoader.getInstance().getConfigDir();
        Path target = root.resolve(DIRECTORY);
        if (!Files.exists(target)) {
            adoptLegacy(root.resolve(LEGACY_DIRECTORY), target);
        }
        try {
            Files.createDirectories(target);
        } catch (IOException e) {
            LOGGER.error("[ShokiTeufel] Could not create {}", target, e);
        }
        directory = target;
        return target;
    }

    public static Path resolve(String first, String... more) {
        Path path = configDir().resolve(first);
        for (String part : more) path = path.resolve(part);
        return path;
    }

    private static void adoptLegacy(Path legacy, Path target) {
        if (!Files.isDirectory(legacy)) return;

        try (Stream<Path> entries = Files.walk(legacy)) {
            for (Path source : entries.toList()) {
                Path copy = target.resolve(legacy.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(copy);
                } else {
                    Files.createDirectories(copy.getParent());
                    Files.copy(source, renamed(copy), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            LOGGER.info("[ShokiTeufel] Took over the settings from {}", legacy);
        } catch (IOException e) {
            LOGGER.error("[ShokiTeufel] Could not take over {}", legacy, e);
        }
    }

    /** Die zwei Dateien, die den alten Modnamen im Dateinamen tragen */
    private static Path renamed(Path copy) {
        String name = copy.getFileName().toString();
        if (!name.startsWith(LEGACY_DIRECTORY + "_")) return copy;
        return copy.resolveSibling(DIRECTORY + name.substring(LEGACY_DIRECTORY.length()));
    }
}

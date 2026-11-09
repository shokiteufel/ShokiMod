package com.shokiteufel.shokimod.verkehr.logik;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vollstaendigkeits-Test: Jede Clientbound-Spielpaketklasse in der Minecraft-Jar muss in der Tabelle des
 * Klassifizierers stehen. Kommt mit einer neuen Minecraft-Version ein Paket dazu, scheitert dieser Test,
 * bis es eingeordnet ist. So bleibt keine Luecke unbemerkt.
 */
class AbdeckungTest {
    private static final String PAKET_PFAD = "net/minecraft/network/protocol/game/";

    @Test
    void everyClientboundGamePacketIsClassified() throws Exception {
        List<String> klassen = spielPaketKlassen();
        assertTrue(klassen.size() > 100, "Zu wenige Pakete gefunden, Suche fehlerhaft: " + klassen.size());

        List<String> fehlend = new ArrayList<>();
        for (String name : klassen) {
            Class<?> typ = Class.forName(name, false, ClientboundLoginPacket.class.getClassLoader());
            if (!Klassifizierer.bekannt(typ)) {
                fehlend.add(typ.getSimpleName());
            }
        }
        Collections.sort(fehlend);
        assertTrue(fehlend.isEmpty(), "Nicht eingeordnete Pakete (in Klassifizierer eintragen): " + fehlend);
    }

    private static List<String> spielPaketKlassen() throws IOException, URISyntaxException {
        List<String> namen = new ArrayList<>();
        URL ort = ClientboundLoginPacket.class.getProtectionDomain().getCodeSource().getLocation();
        Path wurzel = Path.of(ort.toURI());
        if (Files.isDirectory(wurzel)) {
            try (Stream<Path> dateien = Files.list(wurzel.resolve(PAKET_PFAD))) {
                dateien.map(p -> PAKET_PFAD + p.getFileName()).forEach(eintrag -> sammle(eintrag, namen));
            }
        } else {
            try (JarFile jar = new JarFile(wurzel.toFile())) {
                jar.stream().map(JarEntry::getName).forEach(eintrag -> sammle(eintrag, namen));
            }
        }
        return namen;
    }

    private static void sammle(String eintrag, List<String> namen) {
        if (eintrag.startsWith(PAKET_PFAD + "Clientbound") && eintrag.endsWith("Packet.class") && eintrag.indexOf('$') < 0) {
            namen.add(eintrag.substring(0, eintrag.length() - ".class".length()).replace('/', '.'));
        }
    }
}

package com.shokiteufel.shokimod.dungeonkarte;

import com.shokiteufel.shokimod.dungeonkarte.NoammLage.Lage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Wo NoammAddons seine Karte hinlegt: Aufbau der Datei nach seinem ConfigManager (Zweig multiversion, CC0). */
class NoammLageTest {

    private static final String CONFIG = """
            {"version":1,
             "config":[{"name":"Dungeon Map","enabled":true,"configSettings":{}}],
             "hud":[{"name":"Other","x":1.0,"y":2.0,"scale":1.0},
                    {"name":"Dungeon Map","x":40.5,"y":12.0,"scale":1.4}]}
            """;

    private static void schreiben(Path datei, String text) throws IOException {
        Files.createDirectories(datei.getParent());
        Files.writeString(datei, text, StandardCharsets.UTF_8);
    }

    @Test
    void thePositionAndScaleOfTheMapAreRead() {
        Lage lage = NoammLage.ausJson(CONFIG, NoammLage.HUD_NAME);
        assertNotNull(lage);
        assertEquals(40.5f, lage.x());
        assertEquals(12.0f, lage.y());
        assertEquals(1.4f, lage.scale());
    }

    @Test
    void theLineUnderTheMapIsOffUnlessNoammSwitchedItOn() {
        assertEquals(false, NoammLage.ausJson(CONFIG, NoammLage.HUD_NAME).extraInfo(), "Vorgabe bei Noamm: aus");
        String an = CONFIG.replace("\"configSettings\":{}", "\"configSettings\":{\"Show Extra Info Under Map\":true}");
        assertEquals(true, NoammLage.ausJson(an, NoammLage.HUD_NAME).extraInfo());
        String aus = CONFIG.replace("\"configSettings\":{}", "\"configSettings\":{\"Show Extra Info Under Map\":false}");
        assertEquals(false, NoammLage.ausJson(aus, NoammLage.HUD_NAME).extraInfo());
        String andereAnzeige = CONFIG.replace("\"name\":\"Dungeon Map\",\"enabled\"", "\"name\":\"Other\",\"enabled\"")
                .replace("\"configSettings\":{}", "\"configSettings\":{\"Show Extra Info Under Map\":true}");
        assertEquals(false, NoammLage.ausJson(andereAnzeige, NoammLage.HUD_NAME).extraInfo(), "nur der Eintrag der Karte zaehlt");
    }

    @Test
    void noammCountsInAReferenceResolutionOf960By540() {
        // GUI-Groesse 2 auf Full HD = 960 x 540: Faktor 1, sonst weicht die Lage ab
        assertEquals(1f, NoammLage.referenzFaktor(960, 540));
        // GUI-Groesse 3 auf Full HD = 640 x 360: seine Koordinaten schrumpfen auf zwei Drittel
        assertEquals(2f / 3f, NoammLage.referenzFaktor(640, 360), 0.0001f);
        // GUI-Groesse 1 auf Full HD = 1920 x 1080: doppelt
        assertEquals(2f, NoammLage.referenzFaktor(1920, 1080));
        // Das kleinere Verhaeltnis gilt: ein breites Fenster wird von der Hoehe begrenzt
        assertEquals(1f, NoammLage.referenzFaktor(1280, 540));
        assertEquals(1f, NoammLage.referenzFaktor(0, 0), "ohne Fenstermasse: nicht umrechnen");
    }

    @Test
    void aMissingMapEntryOrBrokenFileGivesNothing() {
        assertNull(NoammLage.ausJson("{\"hud\":[{\"name\":\"Other\",\"x\":1,\"y\":1,\"scale\":1}]}", NoammLage.HUD_NAME));
        assertNull(NoammLage.ausJson("{\"version\":1}", NoammLage.HUD_NAME));
        assertNull(NoammLage.ausJson("kein json", NoammLage.HUD_NAME));
        assertNull(NoammLage.ausJson("", NoammLage.HUD_NAME));
        assertNull(NoammLage.ausJson(null, NoammLage.HUD_NAME));
        assertNull(NoammLage.ausJson("[1,2]", NoammLage.HUD_NAME));
    }

    @Test
    void aScaleOfZeroIsNoUsableMap() {
        assertNull(NoammLage.ausJson("{\"hud\":[{\"name\":\"Dungeon Map\",\"x\":5,\"y\":5,\"scale\":0}]}", NoammLage.HUD_NAME));
    }

    @Test
    void theDefaultConfigFileIsFoundWhateverTheFolderIsCalled(@TempDir Path config) throws IOException {
        schreiben(config.resolve("NoammAddons").resolve("config.json"), CONFIG);
        schreiben(config.resolve("anderemod").resolve("config.json"), "{\"hud\":[]}");
        Lage lage = NoammLage.lesen(config);
        assertNotNull(lage);
        assertEquals(40.5f, lage.x());
    }

    @Test
    void theFolderNameIsMatchedWithoutCaseAndAsAPart(@TempDir Path config) throws IOException {
        schreiben(config.resolve("noammaddons-cheat").resolve("config.json"), CONFIG);
        assertNotNull(NoammLage.lesen(config));
    }

    @Test
    void aSelectedNamedConfigWinsOverTheDefault(@TempDir Path config) throws IOException {
        Path ordner = config.resolve("NoammAddons");
        schreiben(ordner.resolve("config.json"), CONFIG);
        schreiben(ordner.resolve("currentConfig.json"), "\"meine\"");
        schreiben(ordner.resolve("configs").resolve("meine.json"),
                "{\"hud\":[{\"name\":\"Dungeon Map\",\"x\":300,\"y\":50,\"scale\":2.0}]}");
        Lage lage = NoammLage.lesen(config);
        assertNotNull(lage);
        assertEquals(300f, lage.x());
        assertEquals(2.0f, lage.scale());
    }

    @Test
    void aSelectedConfigThatIsMissingFallsBackToTheDefault(@TempDir Path config) throws IOException {
        Path ordner = config.resolve("NoammAddons");
        schreiben(ordner.resolve("config.json"), CONFIG);
        schreiben(ordner.resolve("currentConfig.json"), "\"weg\"");
        assertEquals(40.5f, NoammLage.lesen(config).x());
    }

    @Test
    void aSelectedNameWithPathPartsIsNeverFollowed(@TempDir Path config) throws IOException {
        Path ordner = config.resolve("NoammAddons");
        schreiben(ordner.resolve("config.json"), CONFIG);
        schreiben(ordner.resolve("currentConfig.json"), "\"../geheim\"");
        schreiben(ordner.resolve("geheim.json"), "{\"hud\":[{\"name\":\"Dungeon Map\",\"x\":9,\"y\":9,\"scale\":9}]}");
        assertEquals(40.5f, NoammLage.lesen(config).x());
    }

    @Test
    void theChangeValueMovesWhenNoammSavesItsConfig(@TempDir Path config) throws IOException {
        // Noamm schreibt beim Schliessen seines HUD-Editors (/na hud): die Funny Map fragt das jede Sekunde und liest dann neu
        Path datei = config.resolve("NoammAddons").resolve("config.json");
        schreiben(datei, CONFIG);
        Files.setLastModifiedTime(datei, java.nio.file.attribute.FileTime.fromMillis(1_000_000L));
        assertEquals(1_000_000L, NoammLage.aenderung(config));
        Files.setLastModifiedTime(datei, java.nio.file.attribute.FileTime.fromMillis(2_000_000L));
        assertEquals(2_000_000L, NoammLage.aenderung(config));
    }

    @Test
    void theChangeValueAlsoMovesWhenAnotherConfigIsChosen(@TempDir Path config) throws IOException {
        Path ordner = config.resolve("NoammAddons");
        schreiben(ordner.resolve("config.json"), CONFIG);
        schreiben(ordner.resolve("configs").resolve("meine.json"), CONFIG);
        schreiben(ordner.resolve("currentConfig.json"), "\"meine\"");
        Files.setLastModifiedTime(ordner.resolve("configs").resolve("meine.json"), java.nio.file.attribute.FileTime.fromMillis(1_000_000L));
        Files.setLastModifiedTime(ordner.resolve("currentConfig.json"), java.nio.file.attribute.FileTime.fromMillis(5_000_000L));
        assertEquals(5_000_000L, NoammLage.aenderung(config), "der Zeiger zaehlt mit");
    }

    @Test
    void theChangeValueIsZeroWithoutNoamm(@TempDir Path config) throws IOException {
        assertEquals(0L, NoammLage.aenderung(config));
        assertEquals(0L, NoammLage.aenderung(config.resolve("gibt-es-nicht")));
        assertEquals(0L, NoammLage.aenderung(null));
    }

    @Test
    void withoutNoammThereIsNoPosition(@TempDir Path config) throws IOException {
        schreiben(config.resolve("shokimod").resolve("config.json"), CONFIG);
        assertNull(NoammLage.lesen(config));
        assertNull(NoammLage.lesen(config.resolve("gibt-es-nicht")));
        assertNull(NoammLage.lesen(null));
    }

    @Test
    void theRememberedFolderLookupDoesNotListTheConfigFolderAgainWithinHalfAMinute(@TempDir Path config) throws IOException {
        NoammLage.gemerktesVergessen();
        // Ohne Noamm: "keine Datei" wird gemerkt, auch wenn kurz danach ein Ordner entsteht (so spart die Funny Map das Auflisten jede Sekunde)
        assertEquals(0L, NoammLage.aenderungGemerkt(config));
        Path datei = config.resolve("NoammAddons").resolve("config.json");
        schreiben(datei, CONFIG);
        Files.setLastModifiedTime(datei, java.nio.file.attribute.FileTime.fromMillis(3_000_000L));
        assertEquals(0L, NoammLage.aenderungGemerkt(config), "innerhalb der 30 Sekunden gilt das Gemerkte");

        // Nach dem Vergessen (neue Welt) wird neu aufgelistet und die Datei gefunden
        NoammLage.gemerktesVergessen();
        assertEquals(3_000_000L, NoammLage.aenderungGemerkt(config));
        // Eine gefundene Datei: die Zeit wird weiter jedes Mal gefragt (zwei Abfragen), nur das Auflisten entfaellt
        Files.setLastModifiedTime(datei, java.nio.file.attribute.FileTime.fromMillis(4_000_000L));
        assertEquals(4_000_000L, NoammLage.aenderungGemerkt(config));
        NoammLage.gemerktesVergessen();
    }

    @Test
    void theRememberedLookupIsPerFolder(@TempDir Path eins, @TempDir Path zwei) throws IOException {
        NoammLage.gemerktesVergessen();
        Path datei = eins.resolve("NoammAddons").resolve("config.json");
        schreiben(datei, CONFIG);
        Files.setLastModifiedTime(datei, java.nio.file.attribute.FileTime.fromMillis(7_000_000L));
        assertEquals(7_000_000L, NoammLage.aenderungGemerkt(eins));
        assertEquals(0L, NoammLage.aenderungGemerkt(zwei), "ein anderer Ordner nimmt nicht das Gemerkte des ersten");
        NoammLage.gemerktesVergessen();
    }
}

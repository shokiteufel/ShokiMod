package com.shokiteufel.shokimod.dungeonkarte;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.FunnyMapCategory;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Funny Map kann alles einstellen, was die Dungeon Map von NoammAddons kann, und uebernimmt seine Werte (Schiggy, 09.11.: "die
 * Einstellungsmoeglichkeiten von Noamm 1:1 uebernommen, und die Einstellungen so, wie sie aktuell bei Noamm sind").
 */
class KartenEinstellungenTest {

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void everyNoammSettingHasAFieldOfTheRightKindAndTheSameDefault() throws Exception {
        FunnyMapCategory eigene = new FunnyMapCategory();
        KartenEinstellungen ausFeldern = KartenEinstellungen.ausFeldern(eigene);
        KartenEinstellungen standard = KartenEinstellungen.standard();
        for (KartenOption o : KartenOption.values()) {
            Field f = FunnyMapCategory.class.getField(o.feld());
            Class<?> typ = f.getType();
            switch (o.art()) {
                case JANEIN -> assertEquals(boolean.class, typ, o + " ist ein Schalter");
                case KOMMA -> assertTrue(typ == float.class || typ == int.class, o + " ist eine Zahl: " + typ);
                case GANZ -> assertTrue(typ == int.class || typ.isEnum(), o + " ist eine ganze Zahl oder eine Auswahl: " + typ);
                case FARBE -> assertEquals(String.class, typ, o + " ist eine Farbe (Text des Farbwaehlers)");
            }
            assertEquals(standard.roh(o), ausFeldern.roh(o), "die Vorgabe von " + o + " ist bei uns und bei Noamm dieselbe");
        }
    }

    @Test
    void aSettingOfNoammIsTakenOverAsItStands() {
        JsonObject s = obj("""
                {"Hide In Boss":true,"Show Extra Info Under Map":true,"Map Text Scale":1.3,"Door Width":9,
                 "Checkmark Style":4,"Show Player Names":2,"Center Checkmark":false,
                 "Map Background Color":-16711936,"Normal Room":-15188197,"Border Thickness":3}
                """);
        KartenEinstellungen e = KartenEinstellungen.ausNoamm(s);
        assertTrue(e.janein(KartenOption.HIDE_IN_BOSS));
        assertTrue(e.janein(KartenOption.EXTRA_INFO));
        assertEquals(1.3f, e.komma(KartenOption.TEXT_SCALE));
        assertEquals(9, e.ganz(KartenOption.DOOR_WIDTH));
        assertEquals(4, e.ganz(KartenOption.CHECKMARK_STYLE));
        assertEquals(2, e.ganz(KartenOption.PLAYER_NAMES));
        assertEquals(false, e.janein(KartenOption.CENTER), "bei ihm 'Center Checkmark'");
        assertEquals(0xFF00FF00, e.farbe(KartenOption.BACKGROUND), "seine Farbe ist ein vorzeichenbehaftetes ARGB");
        assertEquals(-15188197, e.farbe(KartenOption.ROOM_NORMAL), "die Farbe eines Raums, unveraendert als ARGB");
        assertEquals(3, e.ganz(KartenOption.BORDER_WIDTH));
    }

    @Test
    void whatTheFileDoesNotNameStaysOnTheDefaultOfNoammAndOddValuesAreHeldInRange() {
        KartenEinstellungen e = KartenEinstellungen.ausNoamm(obj("{\"Door Width\":99,\"Map Text Scale\":-3,\"Hide In Boss\":\"yes\",\"Blood Room\":\"rot\"}"));
        assertEquals(10, e.ganz(KartenOption.DOOR_WIDTH), "ueber dem Bereich: auf das Hoechste gesetzt");
        assertEquals(0.4f, e.komma(KartenOption.TEXT_SCALE), "unter dem Bereich: auf das Kleinste gesetzt");
        assertEquals(false, e.janein(KartenOption.HIDE_IN_BOSS), "kein Schalter: bleibt aus");
        assertEquals(0xFFFF0000, e.farbe(KartenOption.ROOM_BLOOD), "kein Zahlenwert: bleibt die Vorgabe");
        assertEquals(KartenEinstellungen.standard().roh(KartenOption.BACKGROUND), e.roh(KartenOption.BACKGROUND));
    }

    @Test
    void noConfigSettingsGivesTheDefaults() {
        KartenEinstellungen e = KartenEinstellungen.ausNoamm(null);
        for (KartenOption o : KartenOption.values()) {
            assertEquals(o.vorgabe() instanceof Float ? o.begrenzt(o.vorgabe()) : o.vorgabe(), e.roh(o), o.toString());
        }
    }

    @Test
    void theEntryOfTheMapInTheConfigFileGivesItsSettings() {
        String json = """
                {"version":1,"config":[{"name":"Other","enabled":true,"configSettings":{"Hide In Boss":true}},
                 {"name":"Dungeon Map","enabled":true,"configSettings":{"Door Width":8,"Hide In Boss":false}}],
                 "hud":[]}
                """;
        KartenEinstellungen e = NoammLage.einstellungenAusJson(json, NoammLage.HUD_NAME);
        assertNotNull(e);
        assertEquals(8, e.ganz(KartenOption.DOOR_WIDTH));
        assertEquals(false, e.janein(KartenOption.HIDE_IN_BOSS), "nur der Eintrag der Karte zaehlt, nicht der einer anderen Anzeige");
    }

    @Test
    void withoutAnEntryForTheMapThereAreNoSettingsOfNoamm() {
        assertNull(NoammLage.einstellungenAusJson("{\"config\":[{\"name\":\"Other\",\"configSettings\":{}}]}", NoammLage.HUD_NAME));
        assertNull(NoammLage.einstellungenAusJson("kein json", NoammLage.HUD_NAME));
        assertNull(NoammLage.einstellungenAusJson("", NoammLage.HUD_NAME));
        assertNull(NoammLage.einstellungenAusJson(null, NoammLage.HUD_NAME));
        assertNotNull(NoammLage.einstellungenAusJson("{\"config\":[{\"name\":\"Dungeon Map\"}]}", NoammLage.HUD_NAME),
                "ein Eintrag ohne einzelne Werte: seine Vorgaben");
    }

    @Test
    void theOwnFieldsAreReadByTheirNameAndKind() {
        FunnyMapCategory f = new FunnyMapCategory();
        f.doorWidth = 9;
        f.style = ModConfig.FunnyStyle.SECRETS_AND_PUZZLE_NAMES;
        f.playerNames = ModConfig.FunnyPlayerNames.ALWAYS;
        f.darken = 80;
        f.backgroundColour = com.shokiteufel.shokimod.data.ColourText.ofArgb(0x80123456);
        KartenEinstellungen e = KartenEinstellungen.ausFeldern(f);
        assertEquals(9, e.ganz(KartenOption.DOOR_WIDTH));
        assertEquals(4, e.ganz(KartenOption.CHECKMARK_STYLE), "die Auswahl wird zu ihrer Nummer, wie bei Noamm");
        assertEquals(2, e.ganz(KartenOption.PLAYER_NAMES));
        assertEquals(80f, e.komma(KartenOption.UNDISCOVERED_TINT));
        assertEquals(0x80123456, e.farbe(KartenOption.BACKGROUND), "die Deckkraft des Farbwaehlers zaehlt");
    }

    @Test
    void theColoursOfTheMapFollowTheSettings() {
        KartenEinstellungen e = KartenEinstellungen.ausNoamm(obj("{\"Normal Room\":-65281,\"Opened Wither Door\":-16776961}"));
        assertEquals(0xFFFF00FF, KartenFarben.raum(RaumTyp.NORMAL, e));
        assertEquals(0xFF72431B, KartenFarben.raum(RaumTyp.NORMAL), "ohne Einstellungen gelten seine Vorgaben");
    }
}

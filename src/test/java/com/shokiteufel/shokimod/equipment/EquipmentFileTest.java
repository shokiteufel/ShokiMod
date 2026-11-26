package com.shokiteufel.shokimod.equipment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Das Format der Datei equipment.json: je Profil bis zu drei Saetze zu vier Gegenstaenden - und wie eine beschaedigte Datei ertragen wird. */
class EquipmentFileTest {

    private static JsonObject item(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("count", 1);
        return o;
    }

    private static List<JsonElement> set(JsonElement... items) {
        List<JsonElement> list = new ArrayList<>();
        for (JsonElement e : items) list.add(e);
        return list;
    }

    @Test
    void aMissingOrBrokenFileGivesAnEmptyMap() {
        assertTrue(EquipmentFile.parse(null).isEmpty());
        assertTrue(EquipmentFile.parse("").isEmpty());
        assertTrue(EquipmentFile.parse("   \n ").isEmpty());
        assertTrue(EquipmentFile.parse("not json at all").isEmpty());
        assertTrue(EquipmentFile.parse("{\"profiles\":").isEmpty(), "mitten abgebrochen");
        assertTrue(EquipmentFile.parse("[1,2,3]").isEmpty());
        assertTrue(EquipmentFile.parse("\"text\"").isEmpty());
        assertTrue(EquipmentFile.parse("{}").isEmpty());
        assertTrue(EquipmentFile.parse("{\"profiles\":5}").isEmpty());
        assertTrue(EquipmentFile.parse("{\"profiles\":[]}").isEmpty());
        assertTrue(EquipmentFile.parse("{\"profiles\":{\"abc\":5}}").isEmpty());
    }

    @Test
    void aSetWithFewerThanFourPlacesIsFilledUpWithEmptyOnes() {
        Map<String, Map<EquipmentSet, List<JsonElement>>> data = EquipmentFile.parse(
                "{\"version\":1,\"profiles\":{\"abc\":{\"normal\":[{\"id\":\"minecraft:stone\"}]}}}");
        List<JsonElement> normal = data.get("abc").get(EquipmentSet.NORMAL);
        assertEquals(4, normal.size());
        assertEquals("minecraft:stone", normal.get(0).getAsJsonObject().get("id").getAsString());
        for (int i = 1; i < 4; i++) assertEquals(0, normal.get(i).getAsJsonObject().size(), "Platz " + i + " ist leer");
    }

    @Test
    void aSetWithMoreThanFourPlacesIsCutOff() {
        Map<String, Map<EquipmentSet, List<JsonElement>>> data = EquipmentFile.parse(
                "{\"profiles\":{\"abc\":{\"rift\":[{},{},{},{},{\"id\":\"minecraft:stone\"}]}}}");
        List<JsonElement> rift = data.get("abc").get(EquipmentSet.RIFT);
        assertEquals(4, rift.size());
        for (JsonElement e : rift) assertEquals(0, e.getAsJsonObject().size());
    }

    @Test
    void anEntryThatIsNoObjectBecomesAnEmptyPlace() {
        Map<String, Map<EquipmentSet, List<JsonElement>>> data = EquipmentFile.parse(
                "{\"profiles\":{\"abc\":{\"safari\":[5,null,\"text\",{\"id\":\"minecraft:stone\"}]}}}");
        List<JsonElement> safari = data.get("abc").get(EquipmentSet.SAFARI);
        assertEquals(4, safari.size());
        assertEquals(0, safari.get(0).getAsJsonObject().size());
        assertEquals(0, safari.get(1).getAsJsonObject().size());
        assertEquals(0, safari.get(2).getAsJsonObject().size());
        assertEquals("minecraft:stone", safari.get(3).getAsJsonObject().get("id").getAsString());
    }

    @Test
    void unknownSetsAndSetsThatAreNoArrayFallAway() {
        Map<String, Map<EquipmentSet, List<JsonElement>>> data = EquipmentFile.parse(
                "{\"profiles\":{\"abc\":{\"weird\":[{}],\"normal\":5,\"rift\":[{}]}}}");
        assertEquals(1, data.get("abc").size());
        assertTrue(data.get("abc").containsKey(EquipmentSet.RIFT));
    }

    @Test
    void aProfileWithoutAnyUsableSetFallsAway() {
        Map<String, Map<EquipmentSet, List<JsonElement>>> data = EquipmentFile.parse(
                "{\"profiles\":{\"abc\":{\"weird\":[{}]},\"def\":{\"normal\":[{}]},\"  \":{\"normal\":[{}]}}}");
        assertEquals(List.of("def"), new ArrayList<>(data.keySet()), "ohne Namen und ohne Satz gibt es kein Profil");
    }

    @Test
    void profileNamesAreLowerCased() {
        Map<String, Map<EquipmentSet, List<JsonElement>>> data = EquipmentFile.parse(
                "{\"profiles\":{\"ABC-DEF\":{\"normal\":[{}]}}}");
        assertTrue(data.containsKey("abc-def"));
    }

    @Test
    void theProfilesKeepTheirOrderFromTheFile() {
        Map<String, Map<EquipmentSet, List<JsonElement>>> data = EquipmentFile.parse(
                "{\"profiles\":{\"c\":{\"normal\":[]},\"a\":{\"normal\":[]},\"b\":{\"normal\":[]}}}");
        assertEquals(List.of("c", "a", "b"), new ArrayList<>(data.keySet()));
    }

    @Test
    void tooManyProfilesKeepTheLastOnes() {
        StringBuilder text = new StringBuilder("{\"profiles\":{");
        int total = EquipmentFile.MAX_PROFILES + 5;
        for (int i = 0; i < total; i++) {
            if (i > 0) text.append(',');
            text.append("\"p").append(i).append("\":{\"normal\":[{}]}");
        }
        text.append("}}");
        Map<String, Map<EquipmentSet, List<JsonElement>>> data = EquipmentFile.parse(text.toString());
        assertEquals(EquipmentFile.MAX_PROFILES, data.size());
        assertFalse(data.containsKey("p0"));
        assertFalse(data.containsKey("p4"));
        assertTrue(data.containsKey("p5"), "das aelteste, das bleibt");
        assertTrue(data.containsKey("p" + (total - 1)));
    }

    @Test
    void writingAndReadingAgainGivesTheSameData() {
        Map<String, Map<EquipmentSet, List<JsonElement>>> data = new LinkedHashMap<>();
        Map<EquipmentSet, List<JsonElement>> first = new EnumMap<>(EquipmentSet.class);
        first.put(EquipmentSet.NORMAL, set(item("minecraft:player_head"), new JsonObject(), item("minecraft:leather"), new JsonObject()));
        first.put(EquipmentSet.RIFT, EquipmentFile.blankSet());
        data.put("833f3e3c-aaaa-bbbb-cccc-000000000001", first);
        Map<EquipmentSet, List<JsonElement>> second = new EnumMap<>(EquipmentSet.class);
        second.put(EquipmentSet.SAFARI, set(new JsonObject(), new JsonObject(), new JsonObject(), item("minecraft:stone")));
        data.put("unknown", second);

        String text = EquipmentFile.write(data);
        assertEquals(data, EquipmentFile.parse(text));
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        assertEquals(EquipmentFile.VERSION, root.get("version").getAsInt());
    }

    @Test
    void writingSkipsSetsThatWereNeverSetAndProfilesWithoutAny() {
        Map<String, Map<EquipmentSet, List<JsonElement>>> data = new LinkedHashMap<>();
        Map<EquipmentSet, List<JsonElement>> one = new EnumMap<>(EquipmentSet.class);
        one.put(EquipmentSet.NORMAL, EquipmentFile.blankSet());
        data.put("one", one);
        data.put("none", new EnumMap<>(EquipmentSet.class));

        JsonObject profiles = JsonParser.parseString(EquipmentFile.write(data)).getAsJsonObject().getAsJsonObject("profiles");
        assertTrue(profiles.has("one"));
        assertFalse(profiles.has("none"));
        JsonObject sets = profiles.getAsJsonObject("one");
        assertTrue(sets.has("normal"));
        assertFalse(sets.has("rift"));
        assertFalse(sets.has("safari"));
    }

    @Test
    void writingKeepsTheNewestProfilesWhenThereAreTooMany() {
        Map<String, Map<EquipmentSet, List<JsonElement>>> data = new LinkedHashMap<>();
        for (int i = 0; i < EquipmentFile.MAX_PROFILES + 3; i++) {
            Map<EquipmentSet, List<JsonElement>> sets = new EnumMap<>(EquipmentSet.class);
            sets.put(EquipmentSet.NORMAL, EquipmentFile.blankSet());
            data.put("p" + i, sets);
        }
        JsonObject profiles = JsonParser.parseString(EquipmentFile.write(data)).getAsJsonObject().getAsJsonObject("profiles");
        assertEquals(EquipmentFile.MAX_PROFILES, profiles.size());
        assertFalse(profiles.has("p0"));
        assertFalse(profiles.has("p2"));
        assertTrue(profiles.has("p3"));
    }

    @Test
    void aBlankSetHasFourEmptyAndSeparateObjects() {
        List<JsonElement> blank = EquipmentFile.blankSet();
        assertEquals(4, blank.size());
        for (JsonElement e : blank) assertEquals(0, e.getAsJsonObject().size());
        assertNotSame(blank.get(0), blank.get(1), "kein geteiltes Objekt: Aendert man eines, bleiben die anderen leer");
        assertNotSame(blank.get(0), EquipmentFile.blankSet().get(0));
    }
}

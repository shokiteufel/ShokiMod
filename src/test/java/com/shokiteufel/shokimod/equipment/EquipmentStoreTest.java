package com.shokiteufel.shokimod.equipment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Das Gedaechtnis der Equipment-Plaetze mit echten Gegenstaenden des Spiels (nach dem Start des Spiels ohne Fenster): merken, anders merken, je Profil und je Satz
 * getrennt, auf die Platte schreiben und nach einem Neustart wiederfinden, eine kaputte Datei ertragen.
 */
class EquipmentStoreTest {

    @BeforeAll
    static void minecraftStarten() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Seit 26.1 bekommen die Gegenstaende ihre Standard-Komponenten erst, wenn die Registrierungen geladen werden (beim Start des Spiels, nicht beim Bootstrap):
        // Ohne sie wirft schon "new ItemStack(...)" ("Components not bound yet"). Fuer die zwei Gegenstaende dieser Probe genuegt eine leere Zuordnung - es geht um
        // das, was die Mod daran haengt (Name, Beschreibung, eigene Daten), nicht um die Standardwerte des Spiels
        bindEmpty(Items.PLAYER_HEAD);
        bindEmpty(Items.STONE);
    }

    private static void bindEmpty(Item item) {
        Holder.Reference<Item> holder = item.builtInRegistryHolder();
        if (!holder.areComponentsBound()) holder.bindComponents(DataComponentMap.EMPTY);
    }

    @TempDir
    Path dir;

    private static HolderLookup.Provider lookup() {
        return RegistryAccess.EMPTY;
    }

    /** Ein Stueck, wie Hypixel es schickt: ein Kopf mit Namen, Beschreibung und der Kennung in den eigenen Daten */
    private static ItemStack piece(String name, String id) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Intelligence: +25"), Component.literal("LEGENDARY CLOAK"))));
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static ItemStack[] four(ItemStack a, ItemStack b, ItemStack c, ItemStack d) {
        return new ItemStack[] {a, b, c, d};
    }

    private static ItemStack[] set1() {
        return four(piece("Hollow Cloak", "HOLLOW_CLOAK"), ItemStack.EMPTY, piece("Zombie Belt", "ZOMBIE_BELT"), piece("Ancient Gauntlet", "ANCIENT_GAUNTLET"));
    }

    private Path file() {
        return dir.resolve("config").resolve("equipment.json");
    }

    private static void assertSameStacks(ItemStack[] expected, ItemStack[] actual) {
        assertEquals(EquipmentRules.SLOTS, actual.length);
        for (int i = 0; i < EquipmentRules.SLOTS; i++) {
            assertNotNull(actual[i], "Platz " + i);
            assertTrue(ItemStack.isSameItemSameComponents(expected[i], actual[i]), "Platz " + i + ": " + expected[i] + " gegen " + actual[i]);
        }
    }

    @Test
    void aNewStoreIsEmptyEverywhere() {
        EquipmentStore store = new EquipmentStore(file());
        for (EquipmentSet set : EquipmentSet.values()) {
            ItemStack[] items = store.get("abc", set, lookup());
            assertEquals(4, items.length);
            for (ItemStack stack : items) assertTrue(stack.isEmpty());
        }
        assertFalse(store.isDirty());
    }

    @Test
    void withoutARegistryNothingIsShownAndNothingIsRemembered() {
        EquipmentStore store = new EquipmentStore(file());
        assertFalse(store.put("abc", EquipmentSet.NORMAL, set1(), null));
        assertFalse(store.isDirty());
        ItemStack[] none = store.get("abc", EquipmentSet.NORMAL, null);
        assertEquals(4, none.length);
        for (ItemStack stack : none) assertTrue(stack.isEmpty());
    }

    @Test
    void whatIsRememberedComesBackWithEverythingThatHangsOnIt() {
        EquipmentStore store = new EquipmentStore(file());
        assertTrue(store.put("abc", EquipmentSet.NORMAL, set1(), lookup()));
        assertTrue(store.isDirty());
        assertSameStacks(set1(), store.get("abc", EquipmentSet.NORMAL, lookup()));
    }

    @Test
    void theStoreKeepsACopyNotTheStackOfTheWindow() {
        EquipmentStore store = new EquipmentStore(file());
        ItemStack[] items = set1();
        store.put("abc", EquipmentSet.NORMAL, items, lookup());
        // Das Fenster aendert sich weiter: Was gemerkt ist, bleibt
        items[0].set(DataComponents.CUSTOM_NAME, Component.literal("Something else"));
        assertSameStacks(set1(), store.get("abc", EquipmentSet.NORMAL, lookup()));
        assertNotSame(items[0], store.get("abc", EquipmentSet.NORMAL, lookup())[0]);
    }

    @Test
    void theSameThingAgainChangesNothingAndMarksNothing() throws IOException {
        EquipmentStore store = new EquipmentStore(file());
        assertTrue(store.put("abc", EquipmentSet.NORMAL, set1(), lookup()));
        store.saveIfDirty();
        assertFalse(store.isDirty());
        assertFalse(store.put("abc", EquipmentSet.NORMAL, set1(), lookup()), "dasselbe noch einmal: keine Aenderung");
        assertFalse(store.isDirty(), "und deshalb nichts zu schreiben");
    }

    @Test
    void aChangedNameOrADifferentPieceIsAChange() {
        EquipmentStore store = new EquipmentStore(file());
        store.put("abc", EquipmentSet.NORMAL, set1(), lookup());
        ItemStack[] other = set1();
        other[0] = piece("Hollow Cloak II", "HOLLOW_CLOAK");
        assertTrue(store.put("abc", EquipmentSet.NORMAL, other, lookup()), "nur der Name ist anders");
        ItemStack[] removed = set1();
        removed[2] = ItemStack.EMPTY;
        store.put("abc", EquipmentSet.NORMAL, set1(), lookup());
        assertTrue(store.put("abc", EquipmentSet.NORMAL, removed, lookup()), "ein Teil abgelegt");
        assertTrue(store.get("abc", EquipmentSet.NORMAL, lookup())[2].isEmpty());
    }

    @Test
    void nullPlacesAreTreatedAsEmpty() {
        EquipmentStore store = new EquipmentStore(file());
        assertTrue(store.put("abc", EquipmentSet.NORMAL, four(piece("A", "A"), null, null, null), lookup()));
        ItemStack[] got = store.get("abc", EquipmentSet.NORMAL, lookup());
        assertFalse(got[0].isEmpty());
        for (int i = 1; i < 4; i++) assertTrue(got[i].isEmpty());
        assertFalse(store.put("abc", EquipmentSet.NORMAL, new ItemStack[] {piece("A", "A")}, lookup()), "zu kurz: nichts");
        assertFalse(store.put("abc", EquipmentSet.NORMAL, null, lookup()));
    }

    @Test
    void eachProfileAndEachSetHasItsOwnPlaces() {
        EquipmentStore store = new EquipmentStore(file());
        store.put("profile-a", EquipmentSet.NORMAL, set1(), lookup());
        store.put("profile-a", EquipmentSet.RIFT, four(piece("Rift Cloak", "RIFT_CLOAK"), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY), lookup());
        for (ItemStack stack : store.get("profile-b", EquipmentSet.NORMAL, lookup())) assertTrue(stack.isEmpty(), "ein anderes Profil");
        for (ItemStack stack : store.get("profile-a", EquipmentSet.SAFARI, lookup())) assertTrue(stack.isEmpty(), "ein anderer Satz");
        assertSameStacks(set1(), store.get("profile-a", EquipmentSet.NORMAL, lookup()));
        assertFalse(store.get("profile-a", EquipmentSet.RIFT, lookup())[0].isEmpty());
        assertTrue(store.get("profile-a", EquipmentSet.RIFT, lookup())[2].isEmpty());
    }

    @Test
    void anUnknownProfileHasItsOwnName() {
        assertEquals("unknown", EquipmentStore.key(null));
        assertEquals("unknown", EquipmentStore.key(""));
        assertEquals("unknown", EquipmentStore.key("   "));
        assertEquals("abc", EquipmentStore.key("abc"));
        EquipmentStore store = new EquipmentStore(file());
        store.put("", EquipmentSet.NORMAL, set1(), lookup());
        // Ohne Namen und null sind dasselbe Profil
        assertSameStacks(set1(), store.get(null, EquipmentSet.NORMAL, lookup()));
        for (ItemStack stack : store.get("abc", EquipmentSet.NORMAL, lookup())) assertTrue(stack.isEmpty());
    }

    @Test
    void clearingTakesEverythingOffAndSaysWhetherThereWasSomething() {
        EquipmentStore store = new EquipmentStore(file());
        assertFalse(store.clear("abc", EquipmentSet.NORMAL, lookup()), "nichts da, nichts zu leeren");
        store.put("abc", EquipmentSet.NORMAL, set1(), lookup());
        assertTrue(store.clear("abc", EquipmentSet.NORMAL, lookup()));
        for (ItemStack stack : store.get("abc", EquipmentSet.NORMAL, lookup())) assertTrue(stack.isEmpty());
        assertFalse(store.clear("abc", EquipmentSet.NORMAL, lookup()));
    }

    @Test
    void whatWasSavedIsThereAfterARestart() {
        EquipmentStore store = new EquipmentStore(file());
        store.put("abc", EquipmentSet.NORMAL, set1(), lookup());
        store.put("abc", EquipmentSet.SAFARI, four(ItemStack.EMPTY, piece("Safari Belt", "SAFARI_BELT"), ItemStack.EMPTY, ItemStack.EMPTY), lookup());
        store.saveIfDirty();
        assertTrue(Files.isRegularFile(file()));

        EquipmentStore again = new EquipmentStore(file());
        assertSameStacks(set1(), again.get("abc", EquipmentSet.NORMAL, lookup()));
        ItemStack[] safari = again.get("abc", EquipmentSet.SAFARI, lookup());
        assertTrue(safari[0].isEmpty());
        assertTrue(ItemStack.isSameItemSameComponents(piece("Safari Belt", "SAFARI_BELT"), safari[1]));
        for (ItemStack stack : again.get("abc", EquipmentSet.RIFT, lookup())) assertTrue(stack.isEmpty());
        assertFalse(again.isDirty(), "ein frisch gelesenes Gedaechtnis hat nichts zu schreiben");
    }

    @Test
    void theFileHoldsReadableJsonWithTheVersion() throws IOException {
        EquipmentStore store = new EquipmentStore(file());
        store.put("abc", EquipmentSet.NORMAL, set1(), lookup());
        store.saveIfDirty();
        String text = Files.readString(file(), StandardCharsets.UTF_8);
        assertTrue(text.contains("\"version\":1"), text);
        assertTrue(text.contains("\"abc\""), text);
        assertTrue(text.contains("\"normal\""), text);
        assertTrue(text.contains("minecraft:player_head"), text);
        assertTrue(text.contains("HOLLOW_CLOAK"), text);
        // Und keine Nebendatei bleibt liegen
        assertFalse(Files.exists(dir.resolve("config").resolve("equipment.json.tmp")));
    }

    @Test
    void savingOnlyWritesWhenSomethingChanged() throws IOException {
        EquipmentStore store = new EquipmentStore(file());
        store.saveIfDirty();
        assertFalse(Files.exists(file()), "nichts geaendert: keine Datei");
        store.put("abc", EquipmentSet.NORMAL, set1(), lookup());
        store.saveIfDirty();
        assertTrue(Files.exists(file()));
        Files.delete(file());
        store.saveIfDirty();
        assertFalse(Files.exists(file()), "nach dem Schreiben ist nichts mehr vorgemerkt");
    }

    @Test
    void anotherProfilesDataSurvivesASaveOfThisOneEvenIfItWasNeverDecoded() {
        EquipmentStore first = new EquipmentStore(file());
        first.put("profile-a", EquipmentSet.NORMAL, set1(), lookup());
        first.saveIfDirty();

        // Ein neuer Start: profile-a wird nie angefragt, nur profile-b veraendert
        EquipmentStore second = new EquipmentStore(file());
        second.put("profile-b", EquipmentSet.RIFT, four(piece("B", "B"), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY), lookup());
        second.saveIfDirty();

        EquipmentStore third = new EquipmentStore(file());
        assertSameStacks(set1(), third.get("profile-a", EquipmentSet.NORMAL, lookup()));
        assertFalse(third.get("profile-b", EquipmentSet.RIFT, lookup())[0].isEmpty());
    }

    @Test
    void theOtherSetsOfAProfileSurviveChangingOne() {
        EquipmentStore first = new EquipmentStore(file());
        first.put("abc", EquipmentSet.NORMAL, set1(), lookup());
        first.saveIfDirty();
        EquipmentStore second = new EquipmentStore(file());
        second.put("abc", EquipmentSet.RIFT, four(piece("R", "R"), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY), lookup());
        second.saveIfDirty();
        EquipmentStore third = new EquipmentStore(file());
        assertSameStacks(set1(), third.get("abc", EquipmentSet.NORMAL, lookup()));
        assertFalse(third.get("abc", EquipmentSet.RIFT, lookup())[0].isEmpty());
    }

    @Test
    void aBrokenFileStartsEmptyAndIsReplacedOnTheNextSave() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "{\"profiles\": {\"abc\": {\"normal\": [", StandardCharsets.UTF_8);
        EquipmentStore store = new EquipmentStore(file());
        for (ItemStack stack : store.get("abc", EquipmentSet.NORMAL, lookup())) assertTrue(stack.isEmpty());
        store.put("abc", EquipmentSet.NORMAL, set1(), lookup());
        store.saveIfDirty();
        assertSameStacks(set1(), new EquipmentStore(file()).get("abc", EquipmentSet.NORMAL, lookup()));
    }

    @Test
    void anEntryTheGameCannotReadAnymoreBecomesAnEmptyPlace() throws IOException {
        Files.createDirectories(file().getParent());
        // Ein Gegenstand, den es nicht gibt (etwa aus einer anderen Fassung des Spiels), neben einem, den es gibt
        Files.writeString(file(), "{\"version\":1,\"profiles\":{\"abc\":{\"normal\":["
                + "{\"id\":\"minecraft:no_such_item\",\"count\":1},"
                + "{\"id\":\"minecraft:stone\",\"count\":1},{},{}]}}}", StandardCharsets.UTF_8);
        EquipmentStore store = new EquipmentStore(file());
        ItemStack[] items = store.get("abc", EquipmentSet.NORMAL, lookup());
        assertTrue(items[0].isEmpty(), "unlesbar: leer statt eines Absturzes");
        assertTrue(items[1].is(Items.STONE));
        assertTrue(items[2].isEmpty());
        assertTrue(items[3].isEmpty());
    }

    @Test
    void theMostRecentlyChangedProfilesStayWhenTheFileGetsTooBig() throws IOException {
        EquipmentStore store = new EquipmentStore(file());
        for (int i = 0; i < EquipmentFile.MAX_PROFILES + 2; i++) {
            store.put("p" + i, EquipmentSet.NORMAL, four(piece("N" + i, "N" + i), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY), lookup());
        }
        // p0 wird noch einmal veraendert: Es ist damit das juengste und bleibt
        store.put("p0", EquipmentSet.NORMAL, four(piece("changed", "CH"), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY), lookup());
        store.saveIfDirty();
        EquipmentStore again = new EquipmentStore(file());
        assertFalse(again.get("p0", EquipmentSet.NORMAL, lookup())[0].isEmpty(), "das zuletzt benutzte bleibt");
        assertTrue(again.get("p1", EquipmentSet.NORMAL, lookup())[0].isEmpty(), "das aelteste faellt weg");
        assertFalse(again.get("p" + (EquipmentFile.MAX_PROFILES + 1), EquipmentSet.NORMAL, lookup())[0].isEmpty());
    }

    // ---- der Codec allein ----

    @Test
    void anEmptyPlaceIsAnEmptyObjectAndComesBackEmpty() {
        JsonElement json = EquipmentCodec.encode(ItemStack.EMPTY, lookup());
        assertTrue(json.isJsonObject());
        assertEquals(0, json.getAsJsonObject().size());
        assertTrue(EquipmentCodec.decode(json, lookup()).isEmpty());
        assertTrue(EquipmentCodec.encode(null, lookup()).getAsJsonObject().size() == 0);
    }

    @Test
    void aPieceSurvivesTheRoundTripThroughTheCodec() {
        ItemStack original = piece("Hollow Cloak", "HOLLOW_CLOAK");
        JsonElement json = EquipmentCodec.encode(original, lookup());
        assertTrue(json.isJsonObject());
        JsonObject object = json.getAsJsonObject();
        assertEquals("minecraft:player_head", object.get("id").getAsString());
        assertTrue(object.has("components"));
        assertTrue(ItemStack.isSameItemSameComponents(original, EquipmentCodec.decode(json, lookup())));
    }

    @Test
    void whatTheCodecCannotReadBecomesEmptyAndNothingThrows() {
        assertTrue(EquipmentCodec.decode(null, lookup()).isEmpty());
        assertTrue(EquipmentCodec.decode(new JsonObject(), lookup()).isEmpty());
        assertTrue(EquipmentCodec.decode(new com.google.gson.JsonPrimitive(5), lookup()).isEmpty());
        assertTrue(EquipmentCodec.decode(new com.google.gson.JsonArray(), lookup()).isEmpty());
        JsonObject broken = new JsonObject();
        broken.addProperty("id", 12345);
        assertTrue(EquipmentCodec.decode(broken, lookup()).isEmpty());
        assertTrue(EquipmentCodec.decode(new JsonObject(), null).isEmpty(), "ohne Registrierung");
    }
}

package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.chat.Chat2Support.Place;
import com.shokiteufel.shokimod.chat.Chat2Support.Run;
import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.shokiteufel.shokimod.chat.Chat2Support.assertKind;
import static com.shokiteufel.shokimod.chat.Chat2Support.config;
import static com.shokiteufel.shokimod.chat.Chat2Support.normal;
import static com.shokiteufel.shokimod.chat.Chat2Support.texts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chat > Rare Drop Messages: die Beispielzeilen von SkyHanni fuer die Seltenheit der Pets, der zurueckgehaltene Buchname mit seinem Inventar, und die fehlende
 * Meldung fuer ein Slayer-Buch.
 */
class RareDropMessagesTest {

    private static final String GLYPH = "";

    private static Run pets() {
        return new Run(config(c -> c.rareDrop.petRarity = true), new Place("Hub"));
    }

    private static void assertRewritten(String before, String after) {
        ChatVerdict v = pets().line(before);
        assertKind(ChatVerdict.Kind.REPLACE, v, before);
        assertEquals(after, v.lines().get(0).legacyText());
    }

    // ---- Pet Drop Rarity ----

    @Test
    void thePetDropShowsItsRarityBeforeTheName() {
        assertRewritten("§6§lPET DROP! §r§5Baby Yeti §r§b(+§r§b168% §r§b✯ Magic Find§r§b)", "§6§lPET DROP! §5§lEPIC §5Baby Yeti §b(+168% ✯ Magic Find)");
        assertRewritten("§6§lPET DROP! §r§5Baby Yeti §r§b(+§r§b168 §r§b✯ Magic Find§r§b)", "§6§lPET DROP! §5§lEPIC §5Baby Yeti §b(+168 ✯ Magic Find)");
        assertRewritten("§6§lPET DROP! §r§5Slug §6(§6+1300☘)", "§6§lPET DROP! §5§lEPIC §5Slug §6(+1300☘)");
        // Gold ist Legendary - und die beiden Teile "PET DROP! " und "LEGENDARY " haben denselben Stil: dazwischen steht kein Code
        assertRewritten("§6§lPET DROP! §r§6Rat", "§6§lPET DROP! LEGENDARY §6Rat");
    }

    @Test
    void aPetYouCatchShowsItsRarityToo() {
        assertRewritten("§6" + GLYPH + " §r§6§lGREAT CATCH! §r§fYou caught a §r§7[Lvl 1] §r§aSquid§r§f!",
                "§6" + GLYPH + " §6§lGREAT CATCH! §fYou caught a §7[Lvl 1] §a§lUNCOMMON §aSquid§f!");
    }

    @Test
    void aPetYouClaimShowsItsRarityAndTheArticleFollowsTheWord() {
        // EPIC beginnt mit einem Vokal: aus "a " wird "an "
        assertRewritten("§aYou claimed a §5Tarantula Pet§a! §r§aYou can manage your Pets in the §r§fPets Menu§r§a in your §r§fSkyBlock Menu§r§a.",
                "§aYou claimed an §5§lEPIC §5Tarantula Pet§a! You can manage your Pets in the §fPets Menu§a in your §fSkyBlock Menu§a.");
        // RARE nicht
        assertRewritten("§aYou claimed a §9Bee Pet§a! §r§aYou can manage your Pets in the §r§fPets Menu§r§a in your §r§fSkyBlock Menu§r§a.",
                "§aYou claimed a §9§lRARE §9Bee Pet§a! You can manage your Pets in the §fPets Menu§a in your §fSkyBlock Menu§a.");
    }

    @Test
    void aPetAnotherPlayerObtainedShowsItsRarity() {
        assertRewritten("§b[MVP§r§c+§r§b] Empa_§r§f §r§ehas obtained §r§a§r§7[Lvl 1] §r§6Bal§r§e!",
                "§b[MVP§c+§b] Empa_§f §ehas obtained §7[Lvl 1] §6§lLEGENDARY §6Bal§e!");
    }

    @Test
    void theOringoListShowsTheRarityOfEachPet() {
        assertRewritten("§e[NPC] Oringo§f: §b✆ §f§r§8• §fBlue Whale Pet", "§e[NPC] Oringo§f: §b✆ §8• §f§lCOMMON §fBlue Whale Pet");
        assertRewritten("§e[NPC] Oringo§f: §b✆ §f§r§8• §5Giraffe Pet", "§e[NPC] Oringo§f: §b✆ §8• §5§lEPIC §5Giraffe Pet");
    }

    @Test
    void aColourThatIsNoRarityOrNoStyleAtAllLeavesTheLineAlone() {
        Run run = pets();
        assertKind(ChatVerdict.Kind.SHOW, run.line("§6§lPET DROP! §r§8Baby Yeti"), "dark grey is no rarity");
        assertKind(ChatVerdict.Kind.SHOW, run.line("§7Hello PET DROP"), "chat");
        assertKind(ChatVerdict.Kind.SHOW, run.line("§aYou claimed a §5Tarantula Pet§a!"), "claim without the Pets Menu sentence");
        // Der alte Aufruf ohne Komponente hat keine Farben: dann bleibt die Zeile
        ChatVerdict plain = new ChatFilterEngine().decide("PET DROP! Baby Yeti (+168% ✯ Magic Find)", config(c -> c.rareDrop.petRarity = true), new Place("Hub"), 1000L);
        assertEquals(ChatVerdict.Kind.SHOW, plain.kind());
    }

    @Test
    void thePetsAreOnlyChangedInSkyBlockAndWhenOn() {
        String line = "§6§lPET DROP! §r§6Rat";
        assertKind(ChatVerdict.Kind.SHOW, new Run(new ChatTabCategory(), new Place("Hub")).line(line), "off by default");
        Place lobby = new Place("Hub");
        lobby.skyblock = false;
        assertKind(ChatVerdict.Kind.SHOW, new Run(config(c -> c.rareDrop.petRarity = true), lobby).line(line), "not SkyBlock");
    }

    @Test
    void theRarityWordsFollowTheColours() {
        assertEquals("COMMON", RareDropMessages.rarityOf('f'));
        assertEquals("UNCOMMON", RareDropMessages.rarityOf('a'));
        assertEquals("RARE", RareDropMessages.rarityOf('9'));
        assertEquals("EPIC", RareDropMessages.rarityOf('5'));
        assertEquals("LEGENDARY", RareDropMessages.rarityOf('6'));
        assertEquals("MYTHIC", RareDropMessages.rarityOf('d'));
        assertEquals("DIVINE", RareDropMessages.rarityOf('b'));
        assertEquals("SPECIAL", RareDropMessages.rarityOf('c'));
        assertEquals("ULTIMATE", RareDropMessages.rarityOf('4'));
        assertNull(RareDropMessages.rarityOf('8'));
        assertNull(RareDropMessages.rarityOf((char) 0));
    }

    // ---- Enchanted Book Name ----

    private static final String BOOK_LINE = "§6§lRARE DROP! §r§fEnchanted Book §r§b(+§r§b208% §r§b✯ Magic Find§r§b)";

    private static Run books(boolean name, boolean missing) {
        return new Run(config(c -> {
            c.rareDrop.enchantedBook = name;
            c.rareDrop.enchantedBookMissingMessage = missing;
        }), new Place("Hub"));
    }

    @Test
    void theBookLineIsHeldBackUntilTheBookIsInTheInventoryAndThenCarriesItsName() {
        Run run = books(true, false);
        assertKind(ChatVerdict.Kind.HIDE, run.line(BOOK_LINE), "held back");
        assertKind(ChatVerdict.Kind.SHOW, run.poll(100), "no book yet");
        run.place.books.put("§9Angler VI", 1);
        ChatVerdict v = run.poll(100);
        assertKind(ChatVerdict.Kind.REPLACE, v, "the book came");
        assertEquals("§6§lRARE DROP! §9Angler VI§f §b(+208% ✯ Magic Find)", v.lines().get(0).legacyText());
        assertKind(ChatVerdict.Kind.SHOW, run.poll(2000), "nothing left to write");
    }

    @Test
    void aBookThatAlreadyLayInTheInventoryIsNotTheNewOne() {
        Run run = books(true, false);
        run.place.books.put("§9Angler VI", 1);
        assertKind(ChatVerdict.Kind.HIDE, run.line(BOOK_LINE), "held back");
        run.poll(100);
        // Eine zweite Kopie waere neu, die erste war schon da
        run.place.books.put("§9Angler VI", 2);
        ChatVerdict v = run.poll(100);
        assertEquals("§6§lRARE DROP! §9Angler VI§f §b(+208% ✯ Magic Find)", v.lines().get(0).legacyText());
    }

    @Test
    void withoutABookTheLineIsWrittenUnchangedAfterOneAndAHalfSeconds() {
        Run run = books(true, false);
        run.line(BOOK_LINE);
        assertKind(ChatVerdict.Kind.SHOW, run.poll(RareDropMessages.BOOK_WAIT_MS - 100 - 20), "waiting");
        ChatVerdict v = run.poll(200);
        assertKind(ChatVerdict.Kind.REPLACE, v, "after the wait");
        assertEquals(normal(BOOK_LINE), v.lines().get(0).legacyText());
        assertFalse(run.engine.hasPending());
    }

    @Test
    void aSecondBookLineFreesTheFirstOneUnchanged() {
        Run run = books(true, false);
        run.line(BOOK_LINE);
        ChatVerdict v = run.line(BOOK_LINE);
        assertKind(ChatVerdict.Kind.REPLACE, v, "the first line is written when the second comes");
        assertEquals(normal(BOOK_LINE), v.lines().get(0).legacyText());
        assertTrue(run.engine.hasPending(), "the second one is held back");
    }

    @Test
    void otherLinesAndOtherRareDropsStay() {
        Run run = books(true, false);
        assertKind(ChatVerdict.Kind.SHOW, run.line("§6§lRARE DROP! §r§9Wolf Tooth"), "another rare drop");
        assertKind(ChatVerdict.Kind.SHOW, run.line("§7Hello"), "chat");
        assertFalse(run.engine.hasPending());
    }

    @Test
    void aNewWorldForgetsTheHeldBackLine() {
        Run run = books(true, false);
        run.line(BOOK_LINE);
        run.engine.worldChanged(run.config);
        assertFalse(run.engine.hasPending());
        assertKind(ChatVerdict.Kind.SHOW, run.poll(5000), "nothing is written for the old world");
    }

    @Test
    void theNameIsReadFromTheLineWithoutAnyStyleToo() {
        // Ohne Komponente: Die Zeile hat keine Farben, der Name kommt trotzdem hinein
        ChatFilterEngine engine = new ChatFilterEngine();
        ChatTabCategory config = config(c -> c.rareDrop.enchantedBook = true);
        Place place = new Place("Hub");
        ChatVerdict held = engine.decide("RARE DROP! Enchanted Book", config, place, 1000L);
        assertEquals(ChatVerdict.Kind.HIDE, held.kind());
        place.books.put("§9Angler VI", 1);
        ChatVerdict v = engine.poll(config, place, 1200L);
        assertEquals("RARE DROP! §9Angler VI", v.lines().get(0).legacyText());
    }

    // ---- Missing Enchanted Book Message ----

    @Test
    void aSlayerBookWithoutAnyLineGetsOne() {
        Run run = books(false, true);
        assertKind(ChatVerdict.Kind.SHOW, run.poll(0), "the first look only notes what is there");
        run.place.books.put("§9Smite VII", 1);
        ChatVerdict v = run.poll(RareDropMessages.SCAN_WATCHING_MS);
        assertKind(ChatVerdict.Kind.REPLACE, v, "a new slayer book");
        assertEquals(List.of("§r§6§lRARE DROP! §9Smite VII"), texts(v));
        assertKind(ChatVerdict.Kind.SHOW, run.poll(RareDropMessages.SCAN_WATCHING_MS), "once");
    }

    @Test
    void aBookThatHypixelAnnouncedNeedsNoSecondLine() {
        Run run = books(false, true);
        run.poll(0);
        run.line("§6§lRARE DROP! §r§9Smite VII");
        run.place.books.put("§9Smite VII", 1);
        assertKind(ChatVerdict.Kind.SHOW, run.poll(RareDropMessages.SCAN_WATCHING_MS), "the line with the name came within a second");
    }

    @Test
    void aBookWithTheLineOfEnchantedBookNeedsNoSecondLineEither() {
        Run run = books(false, true);
        run.poll(0);
        run.line(BOOK_LINE);
        run.place.books.put("§9Smite VII", 1);
        assertKind(ChatVerdict.Kind.SHOW, run.poll(RareDropMessages.SCAN_WATCHING_MS), "RARE DROP! Enchanted Book counts as the message");
    }

    @Test
    void anOldRareDropLineDoesNotCount() {
        Run run = books(false, true);
        run.poll(0);
        run.line("§6§lRARE DROP! §r§9Smite VII");
        run.place.books.put("§9Smite VII", 1);
        ChatVerdict v = run.poll(RareDropMessages.RECENT_MS + 500);
        assertEquals(List.of("§r§6§lRARE DROP! §9Smite VII"), texts(v));
    }

    @Test
    void otherBooksWindowsAndPlacesMakeNoMessage() {
        Run run = books(false, true);
        run.poll(0);
        run.place.books.put("§9Angler VI", 1);
        assertKind(ChatVerdict.Kind.SHOW, run.poll(RareDropMessages.SCAN_WATCHING_MS), "not a slayer book");

        run.place.menuOpen = true;
        run.place.books.put("§9Smite VII", 1);
        assertKind(ChatVerdict.Kind.SHOW, run.poll(RareDropMessages.SCAN_WATCHING_MS), "bought or moved in a window");
        run.place.menuOpen = false;
        assertKind(ChatVerdict.Kind.SHOW, run.poll(RareDropMessages.SCAN_WATCHING_MS), "and the window closing is no gain");

        run.place.area = "Dark Auction";
        run.place.books.put("§9Critical VI", 1);
        assertKind(ChatVerdict.Kind.SHOW, run.poll(RareDropMessages.SCAN_WATCHING_MS), "in the Dark Auction");
        run.place.area = "Hub";
        run.place.skyblock = false;
        run.place.books.put("§9Mana Steal I", 1);
        assertKind(ChatVerdict.Kind.SHOW, run.poll(RareDropMessages.SCAN_WATCHING_MS), "not SkyBlock");
    }

    @Test
    void aNewWorldMakesTheNextLookThePointOfComparisonAgain() {
        Run run = books(false, true);
        run.place.books.put("§9Smite VII", 1);
        run.poll(0);
        run.engine.worldChanged(run.config);
        // Nach dem Weltwechsel liegt das Buch schon im Inventar: kein Zugang
        assertKind(ChatVerdict.Kind.SHOW, run.poll(RareDropMessages.SCAN_WATCHING_MS), "the book was there before");
    }

    @Test
    void bothSwitchesTogetherWriteTheNamedLineOnly() {
        Run run = books(true, true);
        run.poll(0);
        run.line(BOOK_LINE);
        run.place.books.put("§9Smite VII", 1);
        ChatVerdict v = run.poll(RareDropMessages.SCAN_WAITING_MS);
        assertEquals(1, v.lines().size(), "no extra message: the line was there");
        assertEquals("§6§lRARE DROP! §9Smite VII§f §b(+208% ✯ Magic Find)", v.lines().get(0).legacyText());
    }

    @Test
    void theListOfSlayerBooksIsTheOneOfSkyHanni() {
        for (String name : List.of("Smite VI", "Smite VII", "Ender Slayer VI", "Ender Slayer VII", "Mana Steal I", "Smarty Pants I", "Bane of Arthropods VI", "Critical VI",
                "Fire Aspect III", "Ultimate Reiterate I", "Reiterate I")) {
            assertTrue(RareDropMessages.isSlayerBook(name), name);
        }
        for (String name : List.of("Smite V", "Angler VI", "Critical VII", "Fire Aspect II", "Mana Steal II", "Sharpness VII")) {
            assertFalse(RareDropMessages.isSlayerBook(name), name);
        }
    }

    @Test
    void theIncreasedBookIsTheOneWithMoreThanBefore() {
        assertEquals("a", RareDropMessages.increased(Map.of(), Map.of("a", 1)));
        assertEquals("b", RareDropMessages.increased(Map.of("a", 1, "b", 1), Map.of("a", 1, "b", 2)));
        assertNull(RareDropMessages.increased(Map.of("a", 2), Map.of("a", 2)));
        assertNull(RareDropMessages.increased(Map.of("a", 2), Map.of("a", 1)));
        assertNull(RareDropMessages.increased(Map.of("a", 2), Map.of()));
    }

    @Test
    void offByDefault() {
        Run run = new Run(new ChatTabCategory(), new Place("Hub"));
        assertKind(ChatVerdict.Kind.SHOW, run.line(BOOK_LINE), "book line");
        assertKind(ChatVerdict.Kind.SHOW, run.line("§6§lPET DROP! §r§6Rat"), "pet line");
        assertKind(ChatVerdict.Kind.SHOW, run.poll(5000), "nothing pending");
    }
}

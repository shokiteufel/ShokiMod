package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.scanner.ZeilenFunde.Anspruch;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Familien der Zeilen, die eine Ware mit Menge melden - so, wie sie in Schiggys Aufnahmen vom 09.11. standen (rund neun Stunden Chat,
 * 68 verschiedene Vorlagen mit Menge und Beute-Wort; alle Familien sind hier).
 */
class ZeilenFundeTest {

    /** Jede einfache Ware ist "bekannt"; eine Aufzaehlung ("A x2 and B") ist es nicht - wie bei der echten Warenliste */
    private static final Predicate<String> ALLES = n -> !n.contains(" and ") && !n.contains(", ");
    private static final Predicate<String> NUR_SLOW_AND_GROOVY = Set.of("Slow and Groovy Vinyl")::contains;

    @Test
    void aPestKillLineIsRead() {
        assertEquals(List.of(new Anspruch("Enchanted Wheat", 22)),
                ZeilenFunde.lesen("You received 22x Enchanted Wheat for killing a Field Mouse!", ALLES));
        assertEquals(List.of(new Anspruch("Enchanted Melon Slice", 76)),
                ZeilenFunde.lesen("You received 76x Enchanted Melon Slice for killing an Earthworm!", ALLES));
        assertEquals(List.of(new Anspruch("Overclocker 3000", 1)),
                ZeilenFunde.lesen("You received 1x Overclocker 3000 for killing a Dragonfly!", ALLES));
    }

    @Test
    void aThousandsSeparatorInTheAmountIsRead() {
        assertEquals(List.of(new Anspruch("Enchanted Carrot", 1202)),
                ZeilenFunde.lesen("You received 1,202x Enchanted Carrot for killing a Cricket!", ALLES));
    }

    @Test
    void theHiveLineNamesTwoItemsAndASecondOneWithoutAnAmount() {
        assertEquals(List.of(new Anspruch("Honeycomb", 29), new Anspruch("Enchanted Honeycomb", 1)),
                ZeilenFunde.lesen("HIVE! You found Honeycomb x29 and Enchanted Honeycomb!", ALLES));
    }

    @Test
    void coinsAndShardsAreNotSackGoods() {
        assertEquals(List.of(new Anspruch("Honeycomb", 44)),
                ZeilenFunde.lesen("HIVE! You found Honeycomb x44 and 2,160 Coins!", ALLES));
        assertEquals(List.of(new Anspruch("Honeycomb", 30)),
                ZeilenFunde.lesen("HIVE! You found Honeycomb x30 and Honeybuzz Shard!", ALLES));
        assertTrue(ZeilenFunde.lesen("You caught x3 Honeybuzz Shards!", ALLES).isEmpty());
        assertTrue(ZeilenFunde.lesen("CHARM! You charmed the Ghost and received 3 Ghost Shards!", ALLES).isEmpty(), "der Shard-Weg hat sie schon");
    }

    @Test
    void theSafariCaptureNamesAnAnimalAndAShardButNoGood() {
        // Ohne die Ausnahme las "and" die Zeile in "Bloodbat" und "gained 2x ... Shard" auf, und "Bloodbat" waere ein Anspruch geworden
        assertTrue(ZeilenFunde.lesen("CAPTURE! You caught a Bloodbat and gained 2x Bloodbat Shard! (3)", ALLES).isEmpty());
    }

    @Test
    void powderIsACurrencyNotAnItem() {
        assertTrue(ZeilenFunde.lesen("GRUB! You received 800 ᠅ Mithril Powder from killing a Mithril Grubber!", ALLES).isEmpty());
    }

    @Test
    void aGoodCatchWithTheAmountBehindTheName() {
        assertEquals(List.of(new Anspruch("Sponge", 16)), ZeilenFunde.lesen(" GOOD CATCH! You caught a Sponge x16!", ALLES));
    }

    @Test
    void aPrivateUseSymbolInFrontOfTheShoutDoesNotHideTheLine() {
        // So steht es im Mitschnitt: vor "GOOD CATCH!" liegt U+E025
        assertEquals(List.of(new Anspruch("Sponge", 16)), ZeilenFunde.lesen(" GOOD CATCH! You caught a Sponge x16!", ALLES));
    }

    @Test
    void anAdditionalDropOfARat() {
        assertEquals(List.of(new Anspruch("Honey Jar", 1)), ZeilenFunde.lesen("RAT! You dropped an additional Honey Jar!", ALLES));
    }

    @Test
    void anotherPlayersLineIsNeverRead() {
        assertTrue(ZeilenFunde.lesen("[MVP+] TestPlayer has obtained Superboom TNT x2!", ALLES).isEmpty());
        assertTrue(ZeilenFunde.lesen("Party > [MVP+] Someone: You found a Diamond x3!", ALLES).isEmpty());
    }

    @Test
    void linesWithoutAWareAreIgnored() {
        assertTrue(ZeilenFunde.lesen("You earned 108 GEXP from playing SkyBlock!", ALLES).isEmpty());
        assertTrue(ZeilenFunde.lesen("RARE DROP! Volta (+487  Magic Find)", ALLES).isEmpty(), "die Drop-Zeile hat ihren eigenen Weg");
        assertTrue(ZeilenFunde.lesen("You released all Pests from Trap #2!", ALLES).isEmpty());
        assertTrue(ZeilenFunde.lesen("", ALLES).isEmpty());
        assertTrue(ZeilenFunde.lesen(null, ALLES).isEmpty());
    }

    @Test
    void aNameWithAndInItIsOneItemWhenTheListKnowsIt() {
        assertEquals(List.of(new Anspruch("Slow and Groovy Vinyl", 1)),
                ZeilenFunde.lesen("You found Slow and Groovy Vinyl!", NUR_SLOW_AND_GROOVY));
    }
}

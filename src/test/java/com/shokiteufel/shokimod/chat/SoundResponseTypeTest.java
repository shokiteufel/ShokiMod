package com.shokiteufel.shokimod.chat;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat (Teil 3) > Sound Responses: welches Wort im Chat welchen Tierlaut spielt. */
class SoundResponseTypeTest {

    private static SoundResponseType first(String line) {
        return SoundResponseType.match(line, t -> true);
    }

    @Test
    void everyAnimalHasItsWord() {
        Map<SoundResponseType, String> words = new EnumMap<>(SoundResponseType.class);
        words.put(SoundResponseType.CAT, "meow");
        words.put(SoundResponseType.CATPURR, "purr");
        words.put(SoundResponseType.CATPURREOW, "mrow");
        words.put(SoundResponseType.CATHISS, "hiss");
        words.put(SoundResponseType.DOG, "bark");
        words.put(SoundResponseType.DOGGROWL, "grrr");
        words.put(SoundResponseType.DOGHOWL, "awooo");
        words.put(SoundResponseType.SHEEP, "baaah");
        words.put(SoundResponseType.COW, "moo");
        words.put(SoundResponseType.PIG, "oink");
        words.put(SoundResponseType.CHICKEN, "cluck");
        words.put(SoundResponseType.FROG, "ribbit");
        words.put(SoundResponseType.HORSE, "neigh");
        words.put(SoundResponseType.GOAT, "bleat");
        words.put(SoundResponseType.FOX, "yip");
        assertEquals(SoundResponseType.values().length, words.size(), "alle fuenfzehn Arten");
        for (Map.Entry<SoundResponseType, String> e : words.entrySet()) {
            assertEquals(e.getKey(), first(e.getValue()), e.getValue());
            assertTrue(e.getKey().matches(e.getValue()), e.getValue());
        }
    }

    @Test
    void stretchedAndCapitalisedWordsCountToo() {
        assertEquals(SoundResponseType.CAT, first("MEOOOW"));
        assertEquals(SoundResponseType.CAT, first("meeeow"));
        assertEquals(SoundResponseType.COW, first("Mooooo"));
        assertEquals(SoundResponseType.PIG, first("oiiiink"));
        assertEquals(SoundResponseType.DOG, first("woof"));
        assertEquals(SoundResponseType.DOG, first("arf"));
        assertEquals(SoundResponseType.DOG, first("ruff"));
        assertEquals(SoundResponseType.DOGHOWL, first("awoo"));
    }

    @Test
    void theWordCanStandInALineOfAPlayerOrAmongOtherWords() {
        assertEquals(SoundResponseType.CAT, first("[MVP+] Foo: meow"));
        assertEquals(SoundResponseType.COW, first("Guild > [VIP] Bar: I love moo moo"));
        assertEquals(SoundResponseType.COW, first("moo is what cows say"));
        assertEquals(SoundResponseType.FROG, first("ribbit!"));
        assertEquals(SoundResponseType.HORSE, first("neigh?! really"));
        assertEquals(SoundResponseType.GOAT, first("bleat."));
    }

    @Test
    void theWordMustStandAlone() {
        assertNull(first("smooth"));
        assertNull(first("meowing"));
        assertNull(first("mooing"));
        assertNull(first("ribbits"));
        assertNull(first("a barking dog"), "bark ist nur als ganzes Wort gemeint");
        assertNull(first("hello"));
        assertNull(first(""));
        assertNull(first(null));
    }

    @Test
    void onlyTheEnabledKindsSpeakAndTheFirstOneInTheOrderOfSkyHanniWins() {
        assertNull(SoundResponseType.match("meow", t -> t == SoundResponseType.COW));
        assertEquals(SoundResponseType.CAT, SoundResponseType.match("meow", t -> t == SoundResponseType.CAT || t == SoundResponseType.COW));
        // Eine Zeile mit zwei Woertern: es spielt nur die erste Art der Liste
        assertEquals(SoundResponseType.CAT, first("meow moo"));
        assertEquals(SoundResponseType.COW, SoundResponseType.match("meow moo", t -> t != SoundResponseType.CAT));
    }

    @Test
    void everySoundHasAnIdAndAName() {
        Set<String> ids = new HashSet<>();
        for (SoundResponseType t : SoundResponseType.values()) {
            assertFalse(t.displayName().isBlank(), t.name());
            assertTrue(t.soundId().startsWith("entity."), t.name());
            assertTrue(ids.add(t.soundId()), "jeder Laut nur einmal: " + t.soundId());
        }
        assertEquals("entity.cat.ambient", SoundResponseType.CAT.soundId());
        assertEquals("entity.goat.screaming.ambient", SoundResponseType.GOAT.soundId());
        assertEquals("Cat Meow", SoundResponseType.CAT.displayName());
    }
}

package com.shokiteufel.shokimod.scanner;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Das Pet ueber den Weltwechsel: Im Dungeon steht in der Tab-Liste kein Pet-Widget (Schiggy, 09.11.: "im Dungeon soll das Pet erkannt werden,
 * welches man draussen hat"). Es bleibt, bis ein anderes gemeldet wird; ein anderes Profil vergisst es.
 */
class PetStateWeltwechselTest {

    private static final String PROFIL_A = "833f3e3c-e6e3-43ca-89eb-043e37f80b07";
    private static final String PROFIL_B = "11111111-2222-3333-4444-555555555555";

    @BeforeEach
    void leer() {
        PetState.reset();
        PetState.profilGesehen("");
    }

    @AfterEach
    void aufraeumen() {
        PetState.reset();
    }

    private static void autopet(String zeile) {
        PetState.onChatMessage(Component.literal(zeile), zeile);
    }

    @Test
    void thePetStaysAcrossAWorldChange() {
        autopet("Autopet equipped your [Lvl 200] Golden Dragon! VIEW RULE");
        assertTrue(PetState.known());
        assertEquals(200, PetState.level());

        PetState.worldJoined();   // Insel -> Dungeon

        assertTrue(PetState.known(), "im Dungeon kennt die Tab-Liste kein Pet - trotzdem bleibt es");
        assertEquals(200, PetState.level());
        assertTrue(PetState.status().contains("Golden Dragon"));
    }

    @Test
    void aNewAutopetLineInTheDungeonReplacesTheRememberedPet() {
        autopet("Autopet equipped your [Lvl 200] Golden Dragon! VIEW RULE");
        PetState.worldJoined();
        autopet("Autopet equipped your [Lvl 100] Flying Fish! VIEW RULE");
        assertEquals(100, PetState.level());
        assertTrue(PetState.status().contains("Flying Fish"));
    }

    @Test
    void theSameProfileKeepsThePetAndAnotherOneForgetsIt() {
        PetState.onChatMessage(Component.literal("Profile ID: " + PROFIL_A), "Profile ID: " + PROFIL_A);
        autopet("Autopet equipped your [Lvl 200] Golden Dragon! VIEW RULE");

        PetState.worldJoined();
        PetState.onChatMessage(Component.literal("Profile ID: " + PROFIL_A), "Profile ID: " + PROFIL_A);
        assertTrue(PetState.known(), "dasselbe Profil: das Pet bleibt");

        PetState.worldJoined();
        PetState.onChatMessage(Component.literal("Profile ID: " + PROFIL_B), "Profile ID: " + PROFIL_B);
        assertFalse(PetState.known(), "ein anderes Profil hat ein anderes Pet: vergessen");
    }

    @Test
    void theFirstProfileLineOfASessionForgetsNothing() {
        autopet("Autopet equipped your [Lvl 200] Golden Dragon! VIEW RULE");
        PetState.onChatMessage(Component.literal("Profile ID: " + PROFIL_A), "Profile ID: " + PROFIL_A);
        assertTrue(PetState.known(), "noch kein Profil gemerkt: kein Wechsel");
    }
}

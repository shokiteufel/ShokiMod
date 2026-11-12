package com.shokiteufel.shokimod.paket.kern;

import com.shokiteufel.shokimod.paket.kern.Kern.Ursache;
import com.shokiteufel.shokimod.paket.kern.Kern.Zugang;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pet Item getauscht: Das neue wird per Rechtsklick auf das Pet verbraucht, das alte kommt ohne Chatzeile, Klick und Mob-Tod zurueck.
 * Das sah wie ein Fund aus und stand im Profit-Tracker (Rueckmeldung 12.11.).
 */
class PetItemTauschTest {
    private Kern kern;
    private List<Zugang> zugaenge;

    @BeforeEach
    void aufbauen() {
        PetItems.vergessen();
        kern = new Kern();
        zugaenge = new ArrayList<>();
        kern.hoeren(new Kern.Hoerer() {
            @Override
            public void zugang(Zugang z) {
                zugaenge.add(z);
            }
        });
    }

    @AfterEach
    void aufraeumen() {
        PetItems.vergessen();
    }

    private static Stapel neuesItem() {
        return new Stapel("PET_ITEM_TIER_BOOST", "minecraft:enchanted_book", "Tier Boost", 1, null);
    }

    private static Stapel altesItem() {
        return new Stapel("PET_ITEM_TEXTBOOK", "minecraft:book", "Textbook", 1, null);
    }

    private static Stapel diamant() {
        return new Stapel("DIAMOND_THING", "minecraft:diamond", "Shiny Diamond Thing", 1, null);
    }

    /** Container 0 mit den angegebenen Plaetzen (Rest leer) als Ausgangsstand */
    private void inventarSetzen(long nr, Map<Integer, Stapel> plaetze) {
        List<Stapel> l = new ArrayList<>(Collections.nCopies(Kern.PLAETZE, Stapel.LEER));
        plaetze.forEach(l::set);
        kern.bei(new Ereignis.Welt(nr));
        kern.bei(new Ereignis.Inhalt(nr + 1, 0, l));
        zugaenge.clear();
    }

    private Zugang einziger() {
        assertEquals(1, zugaenge.size(), "genau ein Zugang erwartet: " + zugaenge);
        return zugaenge.get(0);
    }

    @Test
    void theOldItemTakingTheSlotOfTheNewOneIsASwapAndNoFind() {
        inventarSetzen(1_000L, Map.of(38, neuesItem()));
        // Ein Paket, ein Platz: erst weg das neue, dann da das alte
        kern.bei(new Ereignis.Platz(2_000L, 0, 38, altesItem()));

        Zugang z = einziger();
        assertEquals(Ursache.UMZUG, z.ursache());
        assertTrue(z.beleg().contains("Pet-Item-Tausch"), z.beleg());
        assertFalse(FundRegel.istFund(z));
    }

    @Test
    void theOldItemComingToAnotherSlotAfterTheNewOneLeftIsASwapToo() {
        inventarSetzen(1_000L, Map.of(38, neuesItem()));
        kern.bei(new Ereignis.Platz(2_000L, 0, 38, Stapel.LEER));
        kern.bei(new Ereignis.Platz(2_060L, 0, 12, altesItem()));

        Zugang z = einziger();
        assertEquals(Ursache.UMZUG, z.ursache());
        assertFalse(FundRegel.istFund(z));
    }

    @Test
    void theSwapExplainsOnlyOneArrival() {
        inventarSetzen(1_000L, Map.of(38, neuesItem()));
        kern.bei(new Ereignis.Platz(2_000L, 0, 38, Stapel.LEER));
        kern.bei(new Ereignis.Platz(2_010L, 0, 12, altesItem()));
        kern.bei(new Ereignis.Platz(2_020L, 0, 13, new Stapel("PET_ITEM_LUCKY_CLOVER", "minecraft:lime_dye", "Lucky Clover", 1, null)));
        assertEquals(2, zugaenge.size());
        assertEquals(Ursache.UMZUG, zugaenge.get(0).ursache());
        assertEquals(Ursache.UNBEKANNT, zugaenge.get(1).ursache(), "der zweite hat keinen Beleg mehr");
        assertFalse(FundRegel.istFund(zugaenge.get(1)), "und ein Pet Item ohne Beleg zaehlt auch dann nicht");
    }

    @Test
    void aPetItemWithoutAnyEvidenceIsNoFindButOtherThingsStillAre() {
        inventarSetzen(1_000L, Map.of());
        kern.bei(new Ereignis.Platz(2_000L, 0, 12, altesItem()));
        Zugang pet = einziger();
        assertEquals(Ursache.UNBEKANNT, pet.ursache());
        assertFalse(FundRegel.istFund(pet), "ein Pet Item ohne Beleg ist kein Fund");

        zugaenge.clear();
        kern.bei(new Ereignis.Platz(3_000L, 0, 13, diamant()));
        Zugang sonst = einziger();
        assertEquals(Ursache.UNBEKANNT, sonst.ursache());
        assertTrue(FundRegel.istFund(sonst), "alles andere ohne Beleg bleibt ein Fund, wie bisher");
    }

    @Test
    void aPetItemThatAMobDroppedStillCounts() {
        inventarSetzen(1_000L, Map.of());
        kern.bei(new Ereignis.MobTod(2_000L, 77));
        kern.bei(new Ereignis.Platz(2_005L, 0, 12, altesItem()));
        Zugang z = einziger();
        assertEquals(Ursache.MOB_DROP, z.ursache());
        assertTrue(FundRegel.istFund(z), "ein Mob-Tod belegt die Beute");
    }

    @Test
    void anOtherThingArrivingRightAfterThePetItemLeftIsNoSwap() {
        inventarSetzen(1_000L, Map.of(38, neuesItem()));
        kern.bei(new Ereignis.Platz(2_000L, 0, 38, Stapel.LEER));
        kern.bei(new Ereignis.Platz(2_010L, 0, 12, diamant()));
        Zugang z = einziger();
        assertEquals(Ursache.UNBEKANNT, z.ursache(), "nur ein Pet Item wird gegen ein Pet Item getauscht");
        assertTrue(FundRegel.istFund(z));
    }

    @Test
    void aPetItemMuchLaterThanTheWindowIsNoSwap() {
        inventarSetzen(1_000L, Map.of(38, neuesItem()));
        kern.bei(new Ereignis.Platz(2_000L, 0, 38, Stapel.LEER));
        kern.bei(new Ereignis.Platz(2_000L + Kern.PET_TAUSCH_FENSTER_STANDARD + 1, 0, 12, altesItem()));
        Zugang z = einziger();
        assertEquals(Ursache.UNBEKANNT, z.ursache());
        assertFalse(FundRegel.istFund(z), "trotzdem kein Fund: ein Pet Item ohne Beleg zaehlt nie");
    }

    // ------------------------------------------------------------------ Erkennung

    @Test
    void theLastDescriptionLineMakesAPetItem() {
        assertTrue(PetItems.beschriftung("LEGENDARY PET ITEM"));
        assertTrue(PetItems.beschriftung("§6§lLEGENDARY PET ITEM"));
        assertTrue(PetItems.beschriftung("  common pet item "));
        assertFalse(PetItems.beschriftung("LEGENDARY PET"), "ein Pet selbst ist kein Pet Item");
        assertFalse(PetItems.beschriftung("EPIC SWORD"));
        assertFalse(PetItems.beschriftung(null));
    }

    @Test
    void aWareWithoutThePrefixIsLearnedFromItsDescription() {
        assertFalse(PetItems.ist("BIGGER_TEETH"));
        assertTrue(PetItems.lesenNoetig("BIGGER_TEETH"));
        PetItems.pruefen("BIGGER_TEETH", "§a§lUNCOMMON PET ITEM");
        assertTrue(PetItems.ist("BIGGER_TEETH"));
        assertFalse(PetItems.lesenNoetig("BIGGER_TEETH"), "einmal je Kennung");

        PetItems.pruefen("HYPERION", "§6§lLEGENDARY DUNGEON SWORD");
        assertFalse(PetItems.ist("HYPERION"));
        assertFalse(PetItems.lesenNoetig("HYPERION"), "auch ein Nein wird nicht jedes Mal neu gelesen");
        assertFalse(PetItems.ist(null));
        assertTrue(PetItems.ist("PET_ITEM_ANYTHING"), "die Vorsilbe genuegt");
    }

    @Test
    void aLearnedPetItemIsSwappedLikeAnyOther() {
        PetItems.pruefen("BIGGER_TEETH", "UNCOMMON PET ITEM");
        PetItems.pruefen("REINFORCED_SCALES", "UNCOMMON PET ITEM");
        inventarSetzen(1_000L, Map.of(38, new Stapel("BIGGER_TEETH", "minecraft:feather", "Bigger Teeth", 1, null)));
        kern.bei(new Ereignis.Platz(2_000L, 0, 38, new Stapel("REINFORCED_SCALES", "minecraft:iron_ingot", "Reinforced Scales", 1, null)));
        Zugang z = einziger();
        assertEquals(Ursache.UMZUG, z.ursache());
        assertFalse(FundRegel.istFund(z));
    }
}

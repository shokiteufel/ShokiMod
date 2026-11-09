package com.shokiteufel.shokimod.paket.kern;

import com.shokiteufel.shokimod.paket.kern.Ereignis.Kurz;
import com.shokiteufel.shokimod.paket.kern.Kern.Abgang;
import com.shokiteufel.shokimod.paket.kern.Kern.Ursache;
import com.shokiteufel.shokimod.paket.kern.Kern.Zugang;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Szenarien aus dem Mitschnitt {@code verkehr-20261107-222043-927}: Ender-Chest-Test (seq 10 078 274 bis 10 108 993, Container 5),
 * Kauf beim Lumber Merchant (seq 8 770 823 bis 8 770 901, Container 1), Mob-Drops (Zombie #708), Inselprobe (Kartoffel).
 */
class KernTest {
    private static final int ENDER = 5;
    private Kern kern;
    private List<Zugang> zugaenge;
    private List<Abgang> abgaenge;

    @BeforeEach
    void aufbauen() {
        kern = new Kern();
        zugaenge = new ArrayList<>();
        abgaenge = new ArrayList<>();
        kern.hoeren(new Kern.Hoerer() {
            @Override
            public void zugang(Zugang z) {
                zugaenge.add(z);
            }

            @Override
            public void abgang(Abgang a) {
                abgaenge.add(a);
            }
        });
    }

    // ------------------------------------------------------------------ Hilfen

    private static Stapel ware(String id, String vanilla, String name, int menge, String uuid) {
        return new Stapel(id, vanilla, name, menge, uuid);
    }

    private static Stapel fleisch(int menge) {
        return ware("ROTTEN_FLESH", "minecraft:rotten_flesh", "Rotten Flesh", menge, null);
    }

    private static Stapel revenant(int menge) {
        return ware("REVENANT_FLESH", "minecraft:rotten_flesh", "Revenant Flesh", menge, null);
    }

    private static Stapel schwert(String uuid) {
        return ware("EARTH_SWORD", "minecraft:stone_sword", "Legendary Earthen Blade", 1, uuid);
    }

    private static List<Stapel> leer(int n) {
        return new ArrayList<>(Collections.nCopies(n, Stapel.LEER));
    }

    /** Container 0 mit den angegebenen Plaetzen (Rest leer) als Ausgangsstand. */
    private void inventarSetzen(long nr, Map<Integer, Stapel> plaetze) {
        List<Stapel> l = leer(Kern.PLAETZE);
        plaetze.forEach(l::set);
        kern.bei(new Ereignis.Welt(nr));
        kern.bei(new Ereignis.Inhalt(nr + 1, 0, l));
        zugaenge.clear();
        abgaenge.clear();
    }

    /**
     * Menue mit 90 Plaetzen (54 Menue, 36 Spieler): erst alle Plaetze einzeln, dann der Komplettbestand, wie Hypixel es beim Oeffnen tut.
     * {@code teil} = Inhalt des Menues, {@code spieler} = Plaetze 0 bis 35 des Spielerteils.
     */
    private void menueOeffnen(long nr, int id, Map<Integer, Stapel> teil, Map<Integer, Stapel> spieler) {
        kern.bei(new Ereignis.MenueAuf(nr, id, "Menue"));
        List<Stapel> l = leer(90);
        teil.forEach(l::set);
        spieler.forEach((idx, s) -> l.set(54 + idx, s));
        for (int i = 0; i < 90; i++) {
            kern.bei(new Ereignis.Platz(nr + 1, id, i, l.get(i)));
        }
        kern.bei(new Ereignis.Inhalt(nr + 2, id, l));
    }

    private Zugang einziger() {
        assertEquals(1, zugaenge.size(), () -> "erwartet genau einen Zugang, gesehen: " + zugaenge);
        return zugaenge.get(0);
    }

    // ------------------------------------------------------------------ Ender-Chest-Fall

    @Test
    void wordlessMobDropWhileEnderChestIsOpenIsAMobDrop() {
        inventarSetzen(10_078_000L, Map.of(39, fleisch(12)));
        menueOeffnen(10_078_274L, ENDER, Map.of(), Map.of(30, fleisch(12)));
        assertTrue(zugaenge.isEmpty(), "Oeffnen und Komplettbestand sind kein Zugang");

        kern.bei(new Ereignis.MobTod(10_079_408L, 9255));
        // Menue-Platz 85 = Spielerteil Index 31 = Hotbar-Platz 4 = Container-0-Platz 40
        kern.bei(new Ereignis.Platz(10_079_416L, ENDER, 85, revenant(3)));

        Zugang z = einziger();
        assertEquals(Ursache.MOB_DROP, z.ursache());
        assertEquals("REVENANT_FLESH", z.id());
        assertEquals(3, z.menge());
        assertEquals(ENDER, z.container());
        assertEquals(40, z.platz());
    }

    @Test
    void closingAndTheContainerZeroUpdateAfterwardsAddsNothing() {
        inventarSetzen(10_078_000L, Map.of());
        menueOeffnen(10_078_274L, ENDER, Map.of(), Map.of());
        kern.bei(new Ereignis.MobTod(10_079_408L, 9255));
        kern.bei(new Ereignis.Platz(10_079_416L, ENDER, 85, revenant(3)));
        assertEquals(1, zugaenge.size());

        kern.bei(new Ereignis.MenueZu(10_079_900L, ENDER));
        // 800 Pakete spaeter dasselbe nochmal ueber Container 0
        kern.bei(new Ereignis.Platz(10_080_216L, 0, 40, revenant(3)));
        assertEquals(1, zugaenge.size(), "kein zweiter Zugang beim Resync");
    }

    @Test
    void anotherStackGrowingCountsOnlyTheDifference() {
        inventarSetzen(10_078_000L, Map.of(39, fleisch(12)));
        menueOeffnen(10_078_274L, ENDER, Map.of(), Map.of(30, fleisch(12)));
        kern.bei(new Ereignis.MobTod(10_079_408L, 9255));
        // Menue-Platz 84 = Index 30 = Container-0-Platz 39
        kern.bei(new Ereignis.Platz(10_079_416L, ENDER, 84, fleisch(15)));
        Zugang z = einziger();
        assertEquals(Ursache.MOB_DROP, z.ursache());
        assertEquals(3, z.menge());
    }

    @Test
    void shiftClicksMoveItemsWithTheirIdentityAndAreNoGain() {
        inventarSetzen(10_078_000L, Map.of());
        menueOeffnen(10_078_274L, ENDER, Map.of(50, schwert("U-1")), Map.of());

        // Shift-Klick: Platz 50 leer, Hotbar-Platz 88 (Index 34 = Container-0-Platz 43) gefuellt; der Server antwortet nicht
        kern.bei(new Ereignis.Klick(10_078_500L, ENDER, 50, "QUICK_MOVE",
                Map.of(50, Kurz.LEER, 88, new Kurz("minecraft:stone_sword", 1)), Kurz.LEER));
        assertEquals(1, zugaenge.size());
        assertEquals(Ursache.UMZUG, zugaenge.get(0).ursache());
        assertEquals("U-1", kern.inventar(43).uuid(), "die Identitaet wandert mit");
        assertTrue(kern.imBesitz("U-1"));

        // Schliessen, danach der volle Stapel ueber Container 0: gleich dem Spiegel, also nichts Neues
        zugaenge.clear();
        kern.bei(new Ereignis.MenueZu(10_078_900L, ENDER));
        kern.bei(new Ereignis.Platz(10_079_700L, 0, 43, schwert("U-1")));
        assertTrue(zugaenge.isEmpty());
    }

    @Test
    void mobDropAfterShiftClicksFarEarlierIsStillAMobDrop() {
        inventarSetzen(10_078_000L, Map.of());
        menueOeffnen(10_078_274L, ENDER, Map.of(50, schwert("U-1")), Map.of());
        kern.bei(new Ereignis.Klick(10_078_500L, ENDER, 50, "QUICK_MOVE",
                Map.of(50, Kurz.LEER, 88, new Kurz("minecraft:stone_sword", 1)), Kurz.LEER));
        zugaenge.clear();
        kern.bei(new Ereignis.MobTod(10_079_408L, 9255));
        kern.bei(new Ereignis.Platz(10_079_416L, ENDER, 85, revenant(3)));
        assertEquals(Ursache.MOB_DROP, einziger().ursache());
    }

    // ------------------------------------------------------------------ Kauf

    @Test
    void purchaseIsExplainedByClickAndChatAndNeverAMobDrop() {
        inventarSetzen(8_769_000L, Map.of());
        Stapel angebot = ware("WOOD_AXE", "minecraft:wooden_axe", "Wooden Axe", 1, null);
        menueOeffnen(8_769_547L, 1, Map.of(30, angebot), Map.of());
        // Kauf-Klick: PICKUP auf Platz 30, der Client sagt voraus: Platz leer, Cursor = Axt
        kern.bei(new Ereignis.Klick(8_770_823L, 1, 30, "PICKUP", Map.of(30, Kurz.LEER), new Kurz("minecraft:wooden_axe", 1)));
        kern.bei(new Ereignis.Chat(8_770_896L, "You bought Wooden Axe!"));
        kern.bei(new Ereignis.Platz(8_770_901L, 1, 83, ware("WOOD_AXE", "minecraft:wooden_axe", "Wooden Axe", 1, "U-AXE")));

        Zugang z = einziger();
        assertEquals(Ursache.KAUF, z.ursache());
        assertEquals(78, kern.klickFenster(), "das gemessene Fenster bleibt");
    }

    @Test
    void purchaseWhileAMobDiesIsStillAPurchaseBecauseTheNameMatches() {
        inventarSetzen(8_769_000L, Map.of());
        Stapel angebot = ware("WOOD_AXE", "minecraft:wooden_axe", "Wooden Axe", 1, null);
        menueOeffnen(8_769_547L, 1, Map.of(30, angebot), Map.of());
        kern.bei(new Ereignis.Klick(8_770_823L, 1, 30, "PICKUP", Map.of(30, Kurz.LEER), new Kurz("minecraft:wooden_axe", 1)));
        kern.bei(new Ereignis.MobTod(8_770_890L, 77));
        kern.bei(new Ereignis.Chat(8_770_896L, "You bought Wooden Axe!"));
        kern.bei(new Ereignis.Platz(8_770_901L, 1, 83, ware("WOOD_AXE", "minecraft:wooden_axe", "Wooden Axe", 1, "U-AXE")));
        assertEquals(Ursache.KAUF, einziger().ursache());
    }

    @Test
    void purchaseSlowerThanTheWindowIsStillAPurchaseAndWidensTheWindow() {
        inventarSetzen(1L, Map.of());
        Stapel angebot = ware("WOOD_AXE", "minecraft:wooden_axe", "Wooden Axe", 1, null);
        menueOeffnen(10L, 1, Map.of(30, angebot), Map.of());
        kern.bei(new Ereignis.Klick(1_000L, 1, 30, "PICKUP", Map.of(30, Kurz.LEER), new Kurz("minecraft:wooden_axe", 1)));
        kern.bei(new Ereignis.Chat(1_150L, "You bought Wooden Axe!"));
        kern.bei(new Ereignis.Platz(1_200L, 1, 83, ware("WOOD_AXE", "minecraft:wooden_axe", "Wooden Axe", 1, "U-AXE")));
        assertEquals(Ursache.KAUF, einziger().ursache());
        assertEquals(200, kern.klickFenster());
    }

    @Test
    void answerToAClickWithoutChatIsAClickAnswerWhenTheItemMatchesTheClickedSlot() {
        inventarSetzen(1L, Map.of());
        Stapel angebot = ware("WOOD_AXE", "minecraft:wooden_axe", "Wooden Axe", 1, null);
        menueOeffnen(10L, 1, Map.of(30, angebot), Map.of());
        kern.bei(new Ereignis.Klick(1_000L, 1, 30, "PICKUP", Map.of(30, Kurz.LEER), new Kurz("minecraft:wooden_axe", 1)));
        kern.bei(new Ereignis.MobTod(1_040L, 77));
        kern.bei(new Ereignis.Platz(1_050L, 1, 83, ware("WOOD_AXE", "minecraft:wooden_axe", "Wooden Axe", 1, "U-AXE")));
        assertEquals(Ursache.KLICK_ANTWORT, einziger().ursache(), "Item passt zum angeklickten Platz: kein Mob-Drop, auch nicht mehrdeutig");
    }

    // ------------------------------------------------------------------ Mehrdeutigkeit und fehlende Belege

    @Test
    void mobDeathAndAClickThatDoesNotExplainTheItemAreAmbiguous() {
        inventarSetzen(1L, Map.of());
        menueOeffnen(10L, ENDER, Map.of(50, schwert("U-1")), Map.of());
        kern.bei(new Ereignis.Klick(1_000L, ENDER, 50, "PICKUP", Map.of(50, Kurz.LEER), new Kurz("minecraft:stone_sword", 1)));
        kern.bei(new Ereignis.MobTod(1_020L, 9255));
        kern.bei(new Ereignis.Platz(1_030L, ENDER, 85, revenant(3)));
        assertEquals(Ursache.MEHRDEUTIG, einziger().ursache());
    }

    @Test
    void twoMobDeathsInTheWindowAreAmbiguousButTheSameMobTwiceIsOne() {
        inventarSetzen(1L, Map.of());
        menueOeffnen(10L, ENDER, Map.of(), Map.of());
        kern.bei(new Ereignis.MobTod(1_000L, 9255));
        kern.bei(new Ereignis.MobTod(1_005L, 9300));
        kern.bei(new Ereignis.Platz(1_010L, ENDER, 85, revenant(3)));
        assertEquals(Ursache.MEHRERE_TODE, einziger().ursache(), "der Server hat es gegeben, welcher Mob bleibt offen");

        zugaenge.clear();
        kern.bei(new Ereignis.MobTod(2_000L, 9255));
        kern.bei(new Ereignis.MobTod(2_005L, 9255));
        kern.bei(new Ereignis.Platz(2_010L, ENDER, 86, revenant(1)));
        assertEquals(Ursache.MOB_DROP, einziger().ursache());
    }

    @Test
    void withoutAnyEvidenceTheSourceIsUnknownAndADeathOutsideTheWindowIsNoEvidence() {
        inventarSetzen(1L, Map.of());
        menueOeffnen(10L, ENDER, Map.of(), Map.of());
        kern.bei(new Ereignis.Platz(1_000L, ENDER, 85, revenant(3)));
        assertEquals(Ursache.UNBEKANNT, einziger().ursache());

        zugaenge.clear();
        kern.bei(new Ereignis.MobTod(2_000L, 9255));
        kern.bei(new Ereignis.Platz(2_000L + Kern.TOD_FENSTER_STANDARD + 1, ENDER, 86, revenant(1)));
        assertEquals(Ursache.UNBEKANNT, einziger().ursache());
    }

    @Test
    void menuPartUpdatesAreNotInventoryGains() {
        inventarSetzen(1L, Map.of());
        menueOeffnen(10L, ENDER, Map.of(), Map.of());
        kern.bei(new Ereignis.MobTod(1_000L, 9255));
        kern.bei(new Ereignis.Platz(1_005L, ENDER, 10, revenant(3)));
        assertTrue(zugaenge.isEmpty(), "Platz 10 ist Ender-Chest-Inhalt");
    }

    // ------------------------------------------------------------------ Ohne Menue (Container 0)

    @Test
    void mobDropWithoutAMenuHasTheSameChain() {
        inventarSetzen(2_279_000L, Map.of());
        kern.bei(new Ereignis.MobTod(2_300_000L, 708));
        kern.bei(new Ereignis.Platz(2_300_003L, 0, 22, ware("FESTERING_FLY", "minecraft:spider_eye", "Festering Fly", 1, null)));
        kern.bei(new Ereignis.Platz(2_300_006L, 0, 23, ware("LAPIS_ARMOR_HELMET", "minecraft:sea_lantern", "Lapis Armor Helmet", 1, "U-HELM")));
        assertEquals(2, zugaenge.size());
        assertEquals(Ursache.MOB_DROP, zugaenge.get(0).ursache());
        assertEquals(Ursache.MOB_DROP, zugaenge.get(1).ursache());
        assertEquals(0, zugaenge.get(0).container());
    }

    @Test
    void ownPickupJustBeforeIsPickedUpAndAThrownStackComingBackIsAReturn() {
        // Insel: 2 Kartoffeln auf Platz 38, eine weggeworfen (THROW Taste 0), wieder aufgehoben
        inventarSetzen(2_200_000L, Map.of(38, ware("POTATO", "minecraft:potato", "Potato", 2, null)));
        kern.bei(new Ereignis.Klick(2_227_670L, 0, 38, "THROW", Map.of(38, new Kurz("minecraft:potato", 1)), Kurz.LEER));
        assertEquals(1, abgaenge.size());
        assertEquals(Ursache.WURF, abgaenge.get(0).ursache());
        kern.bei(new Ereignis.ItemEntity(2_227_680L, 5000));
        kern.bei(new Ereignis.Platz(2_227_690L, 0, 38, ware("POTATO", "minecraft:potato", "Potato", 1, null))); // Antwort des Servers, gleich dem Spiegel
        assertTrue(zugaenge.isEmpty());

        kern.bei(new Ereignis.Aufsammeln(2_229_702L, 5000));
        kern.bei(new Ereignis.Platz(2_229_704L, 0, 38, ware("POTATO", "minecraft:potato", "Potato", 2, null)));
        assertEquals(Ursache.RUECKKEHR, einziger().ursache());
    }

    @Test
    void aFloorItemPickedUpWithoutOurThrowIsPickedUp() {
        inventarSetzen(1L, Map.of());
        kern.bei(new Ereignis.Aufsammeln(1_000L, 777));
        kern.bei(new Ereignis.Platz(1_002L, 0, 12, ware("POTATO", "minecraft:potato", "Potato", 1, null)));
        assertEquals(Ursache.AUFGESAMMELT, einziger().ursache());
    }

    // ------------------------------------------------------------------ Identitaet statt Zaehlen

    @Test
    void armourSwapInEitherOrderIsNeverAFind() {
        // Helm mit uuid steckt im Ruestungsplatz 5; Hypixel zerlegt den Wechsel in zwei Pakete, manchmal eine Sekunde dazwischen
        Stapel helm = ware("HELM", "minecraft:diamond_helmet", "Helm", 1, "U-H");
        inventarSetzen(1L, Map.of(5, helm));
        kern.bei(new Ereignis.Platz(100L, 0, 12, helm));        // erst das Inventar, der Ruestungsplatz hat ihn noch
        kern.bei(new Ereignis.Platz(900L, 0, 5, Stapel.LEER));  // dann der Ruestungsplatz
        assertEquals(1, zugaenge.size());
        assertEquals(Ursache.UMZUG, zugaenge.get(0).ursache());
        assertEquals(Ursache.UMZUG, abgaenge.get(abgaenge.size() - 1).ursache());

        zugaenge.clear();
        abgaenge.clear();
        // umgekehrt: erst der Ruestungsplatz, dann das Inventar
        kern.bei(new Ereignis.Platz(2_000L, 0, 5, helm));
        kern.bei(new Ereignis.Platz(2_100L, 0, 12, Stapel.LEER));
        zugaenge.clear();
        kern.bei(new Ereignis.Platz(2_200L, 0, 13, helm));
        assertTrue(zugaenge.stream().noneMatch(z -> z.ursache() == Ursache.MOB_DROP || z.ursache() == Ursache.UNBEKANNT));
    }

    @Test
    void anItemWithAKnownUuidComingBackLaterIsAReturnNotAFind() {
        inventarSetzen(1L, Map.of(12, schwert("U-1")));
        kern.bei(new Ereignis.Platz(100L, 0, 12, Stapel.LEER));   // weg
        kern.bei(new Ereignis.MobTod(5_000L, 1));
        kern.bei(new Ereignis.Platz(5_005L, 0, 14, schwert("U-1"))); // Jahre spaeter wieder da, sogar mit Mob-Tod davor
        assertEquals(Ursache.RUECKKEHR, einziger().ursache());
    }

    @Test
    void anUnclearIdentityFromAPredictionIsReplacedByTheFullStackWithoutAGain() {
        inventarSetzen(1L, Map.of());
        menueOeffnen(10L, ENDER, Map.of(), Map.of());
        // Klick fuellt Platz 85 mit einem Item, dessen Quelle der Kern nicht kennt (nur Vanilla-ID und Menge)
        kern.bei(new Ereignis.Klick(1_000L, ENDER, 60, "PICKUP", Map.of(85, new Kurz("minecraft:paper", 2)), Kurz.LEER));
        assertTrue(zugaenge.isEmpty(), "unklare Ware zaehlt nie");
        kern.bei(new Ereignis.Platz(1_500L, ENDER, 85, ware("PAPER_X", "minecraft:paper", "Paper X", 2, null)));
        assertTrue(zugaenge.isEmpty());
        assertEquals("PAPER_X", kern.inventar(40).id());
    }

    // ------------------------------------------------------------------ Welt und Anzeige-Platz

    @Test
    void theFirstCompleteInventoryAfterANewWorldIsABaselineNotAGain() {
        List<Stapel> l = leer(Kern.PLAETZE);
        l.set(9, fleisch(30));
        l.set(10, schwert("U-1"));
        kern.bei(new Ereignis.Welt(1L));
        kern.bei(new Ereignis.Inhalt(2L, 0, l));
        assertTrue(zugaenge.isEmpty());
        // ein spaeterer Komplettbestand mit einem Unterschied ist ein Resync, kein Fund
        List<Stapel> l2 = new ArrayList<>(l);
        l2.set(11, revenant(5));
        kern.bei(new Ereignis.Inhalt(500L, 0, l2));
        assertEquals(Ursache.RESYNC, einziger().ursache());
    }

    @Test
    void theDisplaySlotNeverCounts() {
        inventarSetzen(1L, Map.of());
        kern.bei(new Ereignis.MobTod(100L, 1));
        kern.bei(new Ereignis.Platz(105L, 0, 44, ware("SOMETHING", "minecraft:nether_star", "Menu", 1, null)));
        assertFalse(zugaenge.stream().anyMatch(z -> "SOMETHING".equals(z.id())));
    }

    /** Der Server schickt die getragene Ruestung dauernd neu, abwechselnd voll (mit uuid) und gekuerzt (ohne): nie ein Fund. */
    @Test
    void wornArmourNeverCountsWhenTheServerResendsItInTwoVersions() {
        Stapel voll = ware("MAGMA_LORD_CHESTPLATE", "minecraft:leather_chestplate", "Festive Magma Lord Chestplate", 1,
                "5f6c2bf6-933e-437c-8f20-7bbbc5593f32");
        Stapel gekuerzt = ware("MAGMA_LORD_CHESTPLATE", "minecraft:leather_chestplate", null, 1, null);
        inventarSetzen(1L, Map.of(6, voll));
        long nr = 100L;
        for (int i = 0; i < 60; i++) {
            kern.bei(new Ereignis.Platz(nr++, 0, 6, gekuerzt));
            kern.bei(new Ereignis.Platz(nr++, 0, 6, voll));
        }
        assertFalse(zugaenge.isEmpty(), "der Kern sieht den Wechsel (sonst prueft der Test nichts)");
        assertTrue(zugaenge.stream().noneMatch(FundRegel::istFund), () -> "Ruestung darf nie ein Fund sein, gesehen: " + zugaenge);
    }

    /** Ein Mob-Tod kurz davor machte aus dem Neusenden der Ruestung einen "Mob-Drop" (beim Angeln stirbt dauernd etwas). */
    @Test
    void wornArmourResentRightAfterAMobDeathIsNoMobDrop() {
        Stapel voll = ware("MAGMA_LORD_BOOTS", "minecraft:leather_boots", "Festive Magma Lord Boots", 1, "378bfd79-d81a-4ebb-b587-b87db6036322");
        Stapel gekuerzt = ware("MAGMA_LORD_BOOTS", "minecraft:leather_boots", null, 1, null);
        inventarSetzen(1L, Map.of(8, voll));
        kern.bei(new Ereignis.MobTod(100L, 7));
        kern.bei(new Ereignis.Platz(105L, 0, 8, gekuerzt));
        assertFalse(zugaenge.isEmpty(), "der Kern sieht den Wechsel (sonst prueft der Test nichts)");
        assertTrue(zugaenge.stream().noneMatch(FundRegel::istFund), () -> "Ruestung darf nie ein Fund sein, gesehen: " + zugaenge);
    }

    @Test
    void armourPutOnOrSwappedByTheServerIsNoFindOnAnyOfTheFourSlots() {
        inventarSetzen(1L, Map.of());
        long nr = 100L;
        String[] vanilla = {"minecraft:player_head", "minecraft:leather_chestplate", "minecraft:leather_leggings", "minecraft:leather_boots"};
        for (int slot = 5; slot <= 8; slot++) {
            // kein Mob-Tod, kein Klick davor: bei jedem anderen Platz waere das "server (source unknown)" und damit ein Fund
            kern.bei(new Ereignis.Platz(nr++, 0, slot, ware("INFERNAL_CRIMSON_PIECE_" + slot, vanilla[slot - 5], "Infernal Crimson", 1, "uuid-" + slot)));
            kern.bei(new Ereignis.Platz(nr++, 0, slot, ware("SORROW_PIECE_" + slot, vanilla[slot - 5], "Sorrow", 1, "uuid-s" + slot)));
            kern.bei(new Ereignis.Platz(nr++, 0, slot, Stapel.LEER));
        }
        assertFalse(zugaenge.isEmpty(), "der Kern sieht die Wechsel (sonst prueft der Test nichts)");
        assertTrue(zugaenge.stream().noneMatch(FundRegel::istFund), () -> "Ruestung darf nie ein Fund sein, gesehen: " + zugaenge);
    }

    @Test
    void anItemNextToTheArmourSlotsStillCounts() {
        // Gegenprobe: der Fix gilt nur fuer 5 bis 8, Platz 9 (erster Hauptplatz) bleibt ein normaler Platz
        inventarSetzen(1L, Map.of());
        kern.bei(new Ereignis.MobTod(100L, 1));
        kern.bei(new Ereignis.Platz(105L, 0, 9, fleisch(2)));
        assertEquals(Ursache.MOB_DROP, einziger().ursache());
        assertEquals(9, einziger().platz());
        assertTrue(FundRegel.istFund(einziger()), "ein Mob-Drop auf einem Hauptplatz bleibt ein Fund");
    }

    @Test
    void thePlayerPartOfAMenuMapsOntoTheContainerZeroNumbers() {
        assertEquals(9, Kern.invPlatz(0));
        assertEquals(35, Kern.invPlatz(26));
        assertEquals(36, Kern.invPlatz(27));
        assertEquals(44, Kern.invPlatz(35));
    }

    // ------------------------------------------------------------------ Befehl, Menue-Nummer, Abgang-Konto

    @Test
    void anOwnCommandExplainsTheAnswerLikeAClickAndAMobDeathMakesItAmbiguous() {
        inventarSetzen(1L, Map.of());
        kern.bei(new Ereignis.Befehl(1_000L));
        kern.bei(new Ereignis.Platz(1_010L, 0, 12, ware("COBBLESTONE", "minecraft:cobblestone", "Cobblestone", 64, null)));
        assertEquals(Ursache.KLICK_ANTWORT, einziger().ursache(), "z. B. /gfs holt Ware aus dem Sack");

        zugaenge.clear();
        kern.bei(new Ereignis.Befehl(5_000L));
        kern.bei(new Ereignis.MobTod(5_005L, 77));
        kern.bei(new Ereignis.Platz(5_010L, 0, 13, ware("COBBLESTONE", "minecraft:cobblestone", "Cobblestone", 64, null)));
        assertEquals(Ursache.MEHRDEUTIG, einziger().ursache());
    }

    @Test
    void containerZeroUpdatesWhileAMenuIsOpenCarryTheMenuNumber() {
        inventarSetzen(1L, Map.of());
        menueOeffnen(10L, ENDER, Map.of(), Map.of());
        kern.bei(new Ereignis.MobTod(1_000L, 9255));
        kern.bei(new Ereignis.Platz(1_005L, 0, 12, revenant(1)));
        assertEquals(ENDER, einziger().container(), "Menue offen heisst Container ungleich 0");
    }

    @Test
    void whatLeftTheInventoryIsCreditedAndASackMessageDrawsFromIt() {
        inventarSetzen(1L, Map.of(12, ware("RAW_FISH", "minecraft:cod", "Raw Fish", 5, null)));
        kern.uhr(1_000_000_000L);
        kern.bei(new Ereignis.Platz(100L, 0, 12, Stapel.LEER)); // der Server legt den Fang in den Sack: Abgang ohne eigenen Schritt
        assertEquals(1, abgaenge.size());
        assertEquals(Ursache.UNBEKANNT, abgaenge.get(0).ursache());

        kern.uhr(4_000_000_000L);
        assertEquals(2, kern.abgangAbziehen("RAW_FISH", 7, 5_000_000_000L), "5 kamen aus dem Inventar, 2 gingen direkt in den Sack");
        assertEquals(3, kern.abgangAbziehen("RAW_FISH", 3, 5_000_000_000L), "dasselbe Stueck zaehlt nur einmal");
    }

    @Test
    void shardsPutIntoASackByAClickInAMenuAreCreditedAndTheSackMessageDrawsFromThem() {
        // Schiggy 09.11., 18:39: 36 Snoozling-Shards vom Basar, 12 verbraucht, die restlichen 24 per Klick in einem Menue eingelagert
        Stapel shards = ware("SNOOZLING", "minecraft:player_head", "Snoozling Shard", 24, null);
        inventarSetzen(1L, Map.of(37, shards));
        menueOeffnen(10L, 1, Map.of(), Map.of(28, shards));
        kern.uhr(1_000_000_000L);
        kern.bei(new Ereignis.Klick(1_000L, 1, 82, "QUICK_MOVE", Map.of(82, Kurz.LEER), Kurz.LEER));
        kern.bei(new Ereignis.Platz(1_032L, 1, 82, Stapel.LEER));
        assertEquals(1, abgaenge.size(), () -> "gesehen: " + abgaenge);
        assertEquals(24, abgaenge.get(0).menge());

        kern.uhr(3_000_000_000L);
        assertEquals(0, kern.abgangAbziehen("SNOOZLING", 24, 5_000_000_000L), "die eingelagerten 24 sind kein Fund");
    }

    @Test
    void aDepositByHandCountsForAMinuteEvenWhenTheWindowOfTheMessageIsShort() {
        Stapel shards = ware("SNOOZLING", "minecraft:player_head", "Snoozling Shard", 24, null);
        inventarSetzen(1L, Map.of(37, shards));
        menueOeffnen(10L, 1, Map.of(), Map.of(28, shards));
        kern.uhr(1_000_000_000L);
        kern.bei(new Ereignis.Klick(1_000L, 1, 82, "QUICK_MOVE", Map.of(82, Kurz.LEER), Kurz.LEER));
        kern.bei(new Ereignis.Platz(1_032L, 1, 82, Stapel.LEER));

        kern.uhr(21_000_000_000L);
        assertEquals(24, kern.abgangAbziehen("SNOOZLING", 24, 5_000_000_000L), "20 s alt bei einem Fenster von 5 s: das Konto der Meldung greift nicht");
        assertEquals(0, kern.eigeneAbgangAbziehen("SNOOZLING", 24, 60_000_000_000L), "die Einlagerung von Hand gilt eine Minute");
        assertEquals(24, kern.eigeneAbgangAbziehen("SNOOZLING", 24, 60_000_000_000L), "dieselben 24 zaehlen nicht zweimal");
    }

    @Test
    void whatWasMovedInTheInventoryOrThrownIsNoDepositByHand() {
        // ohne offenes Menue ist ein Abgang keine Einlagerung von Hand (der Server legt selbst in den Sack, oder man schiebt Stapel herum)
        inventarSetzen(1L, Map.of(12, ware("RAW_FISH", "minecraft:cod", "Raw Fish", 5, null)));
        kern.uhr(1_000_000_000L);
        kern.bei(new Ereignis.Platz(100L, 0, 12, Stapel.LEER));
        assertEquals(5, kern.eigeneAbgangAbziehen("RAW_FISH", 5, 60_000_000_000L));
    }

    @Test
    void anOutflowOlderThanTheWindowOfTheMessageIsNotUsed() {
        inventarSetzen(1L, Map.of(12, ware("RAW_FISH", "minecraft:cod", "Raw Fish", 5, null)));
        kern.uhr(1_000_000_000L);
        kern.bei(new Ereignis.Platz(100L, 0, 12, Stapel.LEER));
        kern.uhr(10_000_000_000L);
        assertEquals(5, kern.abgangAbziehen("RAW_FISH", 5, 5_000_000_000L), "9 s alt bei einem Fenster von 5 s");
    }

    @Test
    void whatCameIntoTheInventoryByAnOwnStepIsCreditedAndARemovedLineDrawsFromIt() {
        inventarSetzen(1L, Map.of());
        kern.uhr(1_000_000_000L);
        kern.bei(new Ereignis.Befehl(100L)); // /gfs: der Koeder wandert aus dem Sack ins Inventar
        kern.bei(new Ereignis.Platz(104L, 0, 12, ware("BLESSED_BAIT", "minecraft:lime_dye", "Blessed Bait", 10, null)));
        assertEquals(Ursache.KLICK_ANTWORT, einziger().ursache());

        kern.uhr(4_000_000_000L);
        assertEquals(20, kern.zugangAbziehen("BLESSED_BAIT", 30, 5_000_000_000L), "10 wurden geholt, 20 beim Angeln verbraucht");
        assertEquals(5, kern.zugangAbziehen("BLESSED_BAIT", 5, 5_000_000_000L), "dasselbe Stueck zaehlt nur einmal");
    }

    @Test
    void anIntakeOlderThanTheWindowOfTheMessageIsNotUsed() {
        inventarSetzen(1L, Map.of());
        kern.uhr(1_000_000_000L);
        kern.bei(new Ereignis.Befehl(100L));
        kern.bei(new Ereignis.Platz(104L, 0, 12, ware("BLESSED_BAIT", "minecraft:lime_dye", "Blessed Bait", 10, null)));
        kern.uhr(10_000_000_000L);
        assertEquals(10, kern.zugangAbziehen("BLESSED_BAIT", 10, 5_000_000_000L), "9 s alt bei einem Fenster von 5 s");
    }

    @Test
    void aMobDropIsNotCreditedAsMovedIn() {
        inventarSetzen(1L, Map.of());
        kern.uhr(1_000_000_000L);
        kern.bei(new Ereignis.MobTod(1_000L, 9255));
        kern.bei(new Ereignis.Platz(1_005L, 0, 12, revenant(1)));
        assertEquals(Ursache.MOB_DROP, einziger().ursache());
        assertEquals(1, kern.zugangAbziehen("REVENANT_FLESH", 1, 10_000_000_000L), "ein Drop kam nicht aus einem Sack");
    }

    @Test
    void throwsAndResyncsAreNotCreditedBecauseTheyDoNotGoToASack() {
        inventarSetzen(1L, Map.of(38, ware("POTATO", "minecraft:potato", "Potato", 2, null)));
        kern.uhr(1_000_000_000L);
        kern.bei(new Ereignis.Klick(10L, 0, 38, "THROW", Map.of(38, new Kurz("minecraft:potato", 1)), Kurz.LEER));
        assertEquals(Ursache.WURF, abgaenge.get(0).ursache());
        assertEquals(1, kern.abgangAbziehen("POTATO", 1, 10_000_000_000L), "ein Wurf geht nicht in einen Sack");

        // ein Komplettbestand, dem der Rest fehlt: Resync, auch kein Sack
        kern.bei(new Ereignis.Inhalt(500L, 0, leer(Kern.PLAETZE)));
        assertEquals(Ursache.RESYNC, abgaenge.get(abgaenge.size() - 1).ursache());
        assertEquals(1, kern.abgangAbziehen("POTATO", 1, 10_000_000_000L));
    }

    // ------------------------------------------------------------------ Deko auf der Insel (Mitschnitt Schiggy 09.11., 11:55)

    private static Stapel torte() {
        return ware("CHOCOLATE_CENTURY_CAKE", "minecraft:player_head", "Chocolate Century Cake", 1, null);
    }

    @Test
    void aDecorationTakenBackFromTheIslandIsAReturnAndNeverAFind() {
        // Chat "removed" 10 Pakete vor dem Platz-Update, wie am 09.11. (seq 1 044 157 und 1 044 167); kein Aufsammeln, kein Klick, kein Mob-Tod
        inventarSetzen(1_044_000L, Map.of());
        kern.bei(new Ereignis.Chat(1_044_157L, "You removed a Chocolate Century Cake. (19/20)"));
        kern.bei(new Ereignis.Platz(1_044_167L, 0, 41, torte()));

        Zugang z = einziger();
        assertEquals(Ursache.RUECKKEHR, z.ursache());
        assertFalse(FundRegel.istFund(z), "das eigene Deko-Item ist kein Gewinn");
    }

    @Test
    void theRemovedLineExplainsOnlyOneArrival() {
        inventarSetzen(1L, Map.of());
        kern.bei(new Ereignis.Chat(1_000L, "You removed a Chocolate Century Cake. (19/20)"));
        kern.bei(new Ereignis.Platz(1_005L, 0, 41, torte()));
        kern.bei(new Ereignis.Platz(1_010L, 0, 42, ware("SOMETHING_ELSE", "minecraft:stone", "Chocolate Century Cake", 1, null)));
        assertEquals(2, zugaenge.size());
        assertEquals(Ursache.RUECKKEHR, zugaenge.get(0).ursache());
        assertEquals(Ursache.UNBEKANNT, zugaenge.get(1).ursache(), "ein zweiter Zugang hat keinen Beleg mehr");
    }

    @Test
    void aRemovedLineOfAnotherItemOrFarBeforeIsNoEvidence() {
        inventarSetzen(1L, Map.of());
        kern.bei(new Ereignis.Chat(1_000L, "You removed a Chocolate Century Cake. (19/20)"));
        kern.bei(new Ereignis.Platz(1_005L, 0, 41, ware("DIAMOND_THING", "minecraft:diamond", "Shiny Diamond Thing", 1, null)));
        assertEquals(Ursache.UNBEKANNT, einziger().ursache(), "anderer Name");

        zugaenge.clear();
        kern.bei(new Ereignis.Chat(2_000L, "You removed a Chocolate Century Cake. (18/20)"));
        kern.bei(new Ereignis.Platz(2_000L + Kern.ENTFERNEN_FENSTER_STANDARD + 1, 0, 42, torte()));
        assertEquals(Ursache.UNBEKANNT, einziger().ursache(), "zu lange her");
    }

    @Test
    void placingAndTakingBackFiveTimesGivesNoFindAtAll() {
        // 11:55:07 bis 11:55:19: fuenfmal abnehmen und wieder hinstellen
        inventarSetzen(1_044_000L, Map.of());
        long nr = 1_044_100L;
        for (int i = 0; i < 5; i++) {
            kern.bei(new Ereignis.Chat(nr, "You removed a Chocolate Century Cake. (19/20)"));
            kern.bei(new Ereignis.Platz(nr + 10, 0, 41, torte()));
            kern.bei(new Ereignis.Chat(nr + 3_000, "You placed a Chocolate Century Cake. (20/20)"));
            kern.bei(new Ereignis.Platz(nr + 3_003, 0, 41, Stapel.LEER));
            nr += 6_000;
        }
        assertEquals(5, zugaenge.size());
        for (Zugang z : zugaenge) {
            assertFalse(FundRegel.istFund(z), z.beleg());
        }
    }
}

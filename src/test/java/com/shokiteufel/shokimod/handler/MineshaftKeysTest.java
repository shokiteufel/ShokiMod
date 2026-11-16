package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.scanner.MiningState;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Die Schluessel fuer den Schacht: was gezaehlt wird, was der Klick holt und wie die Zeile aussieht. */
class MineshaftKeysTest {

    private static MiningState.Corpse corpse(String type, int open, int total) {
        return new MiningState.Corpse(type, open, total);
    }

    private static int carrying(Map<String, Integer> have, String keyId) {
        return have.getOrDefault(keyId, 0);
    }

    @BeforeEach
    void reset() {
        MineshaftKeys.resetForTests();
    }

    @Test
    void twoUmberAndOneTungstenGiveTwoCommandsInThatOrder() {
        List<MineshaftKeys.Need> plan = MineshaftKeys.plan(
                List.of(corpse("Lapis", 2, 2), corpse("Tungsten", 1, 1), corpse("Umber", 2, 2)), id -> 0);
        assertEquals(List.of("gfs UMBER_KEY 2", "gfs TUNGSTEN_KEY 1"), MineshaftKeys.commands(plan));
        assertEquals("2 Umber, 1 Tungsten", MineshaftKeys.corpsesText(plan));
        assertEquals("", MineshaftKeys.carriedText(plan));
    }

    @Test
    void lapisAndVanguardNeedNothingHere() {
        List<MineshaftKeys.Need> plan = MineshaftKeys.plan(List.of(corpse("Lapis", 3, 3), corpse("Vanguard", 1, 1)), id -> 0);
        assertTrue(plan.isEmpty());
        assertTrue(MineshaftKeys.commands(plan).isEmpty());
    }

    @Test
    void onlyTheStillOpenCorpsesCount() {
        List<MineshaftKeys.Need> plan = MineshaftKeys.plan(List.of(corpse("Umber", 1, 3)), id -> 0);
        assertEquals(List.of("gfs UMBER_KEY 1"), MineshaftKeys.commands(plan));
        assertTrue(MineshaftKeys.plan(List.of(corpse("Umber", 0, 2)), id -> 0).isEmpty());
    }

    @Test
    void keysAlreadyCarriedAreTakenOffTheNumber() {
        Map<String, Integer> have = Map.of("UMBER_KEY", 1, "TUNGSTEN_KEY", 4);
        List<MineshaftKeys.Need> plan = MineshaftKeys.plan(
                List.of(corpse("Umber", 2, 2), corpse("Tungsten", 1, 1)), id -> carrying(have, id));
        // Tungsten ist gedeckt, bei Umber fehlt einer
        assertEquals(List.of("gfs UMBER_KEY 1"), MineshaftKeys.commands(plan));
        // die Zeile nennt trotzdem, was im Schacht liegt, und der Mauszeiger, was man schon hat
        assertEquals("2 Umber, 1 Tungsten", MineshaftKeys.corpsesText(plan));
        assertEquals("1 Umber, 4 Tungsten", MineshaftKeys.carriedText(plan));
    }

    @Test
    void whenEveryKeyIsCarriedThereIsNothingToFetch() {
        List<MineshaftKeys.Need> plan = MineshaftKeys.plan(List.of(corpse("Umber", 2, 2)), id -> 2);
        assertEquals(1, plan.size());
        assertTrue(MineshaftKeys.commands(plan).isEmpty());
    }

    @Test
    void sameKindOnSeveralLinesIsAddedUp() {
        List<MineshaftKeys.Need> plan = MineshaftKeys.plan(List.of(corpse("Umber", 1, 1), corpse("Umber", 1, 1)), id -> 0);
        assertEquals(List.of("gfs UMBER_KEY 2"), MineshaftKeys.commands(plan));
    }

    @Test
    void theLineShowsTheCorpsesAndAClickThatRunsTheOwnCommand() {
        List<MineshaftKeys.Need> plan = MineshaftKeys.plan(
                List.of(corpse("Umber", 2, 2), corpse("Tungsten", 1, 1)), id -> 0);
        Component line = MineshaftKeys.message(plan);
        assertEquals("[ShokiMod] Corpses here: 2 Umber, 1 Tungsten [Get Keys from Sacks]", line.getString());
        Component click = line.getSiblings().get(line.getSiblings().size() - 1);
        assertEquals("[Get Keys from Sacks]", click.getString());
        // Der Klick traegt keine Menge: die steht in der Mod, nicht in einer Zeile, die jemand nachbauen koennte
        assertEquals("/shoki sackkeys", ((ClickEvent.RunCommand) click.getStyle().getClickEvent()).command());
    }
}

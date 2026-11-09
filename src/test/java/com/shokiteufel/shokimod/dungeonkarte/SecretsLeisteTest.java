package com.shokiteufel.shokimod.dungeonkarte;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Die Geheimnisse des Raums aus der Aktionsleiste (Mitschnitt Schiggy, 09.11.): "... §4127/127          §70/3 Secrets".
 */
class SecretsLeisteTest {

    @Test
    void readsFoundAndTotalFromARealActionBar() {
        String leiste = "782/10,782❤     §a2,905❈ Defense     §b18,002/18,002✎ Mana     §3600ʬ     §4124/124          §70/6 Secrets"
                .replaceAll("§[0-9a-fk-or]", "");
        assertEquals(new SecretsLeiste.Stand(0, 6), SecretsLeiste.lesen(leiste));
    }

    @Test
    void readsAPartlyFoundRoom() {
        assertEquals(new SecretsLeiste.Stand(3, 9), SecretsLeiste.lesen("944/10,944❤     127/127          3/9 Secrets"));
    }

    @Test
    void theHealthNumbersAreNotTheSecrets() {
        // "127/127" steht vor dem Wort Leerzeichen und Zahlen - nur die Zahl direkt vor "Secrets" zaehlt
        assertEquals(new SecretsLeiste.Stand(1, 3), SecretsLeiste.lesen("17,214/17,214✎ Mana     127/127          1/3 Secrets"));
    }

    @Test
    void aBarWithoutSecretsGivesNothing() {
        assertNull(SecretsLeiste.lesen("782/10,782❤     2,905❈ Defense     18,002/18,002✎ Mana"));
        assertNull(SecretsLeiste.lesen(""));
        assertNull(SecretsLeiste.lesen(null));
    }

    @Test
    void moreFoundThanThereAreIsNoStand() {
        assertNull(SecretsLeiste.lesen("5/3 Secrets"));
        assertNull(SecretsLeiste.lesen("0/0 Secrets"));
    }

    @Test
    void theRoomOfAWorldPlaceIsTheCentreOfTheGridCell() {
        // Mitte des ersten Raums (-185|-185) ist Platz 0 (Zeile 0, Spalte 0)
        assertEquals(0, SecretsLeiste.platz(-185, -185));
        // 15 Bloecke daneben ist man noch im selben Raum, 17 schon im naechsten (Spalte 2)
        assertEquals(0, SecretsLeiste.platz(-185 + 15, -185));
        assertEquals(2, SecretsLeiste.platz(-185 + 17, -185));
        // Zeile 2 = naechster Raum in z-Richtung: 2 * 11
        assertEquals(22, SecretsLeiste.platz(-185, -185 + 32));
        // Raum Nummer 6 (Index 5) ist der letzte: Zeile 10, Spalte 10
        assertEquals(10 * 11 + 10, SecretsLeiste.platz(-185 + 5 * 32, -185 + 5 * 32));
    }

    @Test
    void outsideTheGridIsNoPlace() {
        assertEquals(-1, SecretsLeiste.platz(-185 - 17, -185));
        assertEquals(-1, SecretsLeiste.platz(-185, -185 + 5 * 32 + 17));
        assertEquals(-1, SecretsLeiste.platz(500, 500));
    }
}

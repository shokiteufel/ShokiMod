package com.shokiteufel.shokimod.handler;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Der Drill-Tank: die Meldung erkennen, die Barrels rechnen, die Zeile und der Klick. */
class DrillRefuelTest {

    @Test
    void theMessageFromTheScreenshotGivesFiveBarrels() {
        DrillRefuel.Refill refill = DrillRefuel.parse("Your Auspicious Divan's Drill has 50% fuel remaining!");
        assertNotNull(refill);
        assertEquals("Auspicious Divan's Drill", refill.drill());
        assertEquals(50, refill.percent());
        assertEquals(5, refill.barrels());
        assertEquals(List.of("gfs OIL_BARREL 5", "call jotraeline"), DrillRefuel.commands(refill));
    }

    @Test
    void anotherDrillAndAnotherPercentWork() {
        DrillRefuel.Refill refill = DrillRefuel.parse("Your Titanium Drill DR-X555 has 25% fuel remaining!");
        assertNotNull(refill);
        assertEquals("Titanium Drill DR-X555", refill.drill());
        // 75.000 fehlen: sieben ganze Barrels
        assertEquals(7, refill.barrels());
    }

    @Test
    void barrelsAreRoundedDownSoNoneOverflows() {
        assertEquals(5, DrillRefuel.barrels(50));
        assertEquals(6, DrillRefuel.barrels(37));
        assertEquals(9, DrillRefuel.barrels(10));
        assertEquals(9, DrillRefuel.barrels(5));
        assertEquals(1, DrillRefuel.barrels(90));
        assertEquals(0, DrillRefuel.barrels(91));
        assertEquals(0, DrillRefuel.barrels(100));
        assertEquals(0, DrillRefuel.barrels(150));
        assertEquals(10, DrillRefuel.barrels(0));
    }

    @Test
    void aTankTooFullForABarrelGivesNoLine() {
        assertNull(DrillRefuel.parse("Your Divan's Drill has 95% fuel remaining!"));
    }

    @Test
    void otherLinesAndPlayerChatAreNotTheMessage() {
        assertNull(DrillRefuel.parse(null));
        assertNull(DrillRefuel.parse("Your Divan's Drill is out of fuel!"));
        assertNull(DrillRefuel.parse("[MVP+] Someone: Your Divan's Drill has 50% fuel remaining!"));
        assertNull(DrillRefuel.parse("Guild > Someone: Your Divan's Drill has 50% fuel remaining!"));
        assertNull(DrillRefuel.parse("Your Divan's Drill has 50% fuel remaining! Hurry"));
    }

    @Test
    void theLineShowsDrillAndPercentAndAClickThatRunsTheOwnCommand() {
        DrillRefuel.Refill refill = DrillRefuel.parse("Your Auspicious Divan's Drill has 50% fuel remaining!");
        Component line = DrillRefuel.message(refill);
        assertEquals("[ShokiMod] Auspicious Divan's Drill at 50% fuel. [Refuel: 5 Oil Barrels]", line.getString());
        Component click = line.getSiblings().get(line.getSiblings().size() - 1);
        assertEquals("[Refuel: 5 Oil Barrels]", click.getString());
        assertEquals("[Refuel: 1 Oil Barrel]", DrillRefuel.clickText(new DrillRefuel.Refill("Drill", 90, 1)));
        assertEquals("/shoki refuel", ((ClickEvent.RunCommand) click.getStyle().getClickEvent()).command());
    }
}

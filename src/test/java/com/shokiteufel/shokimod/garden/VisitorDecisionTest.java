package com.shokiteufel.shokimod.garden;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.garden.VisitorDecision.BlockReason;
import com.shokiteufel.shokimod.garden.VisitorDecision.Button;
import com.shokiteufel.shokimod.garden.VisitorDecision.Facts;
import com.shokiteufel.shokimod.garden.VisitorDecision.Rules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Ablehnen sperren?": die Regel von SkyHanni Punkt fuer Punkt - Belohnung, neuer Besucher, Copper-Preis, Verlust - mit der Reihenfolge, der Umgehungstaste und den
 * Knoepfen des Fensters.
 */
class VisitorDecisionTest {

    private static final Rules OFF = new Rules(false, false, false, false, false, false, 6000, 150_000, false);

    private static Rules with(boolean reward, boolean fresh, boolean cheap, boolean expensive, boolean lowLoss, boolean highLoss) {
        return new Rules(reward, fresh, cheap, expensive, lowLoss, highLoss, 6000, 150_000, false);
    }

    private static Facts facts(boolean warned, int offers, boolean copper, int perCopper, double price, double reward) {
        return new Facts(warned, offers, copper, perCopper, price, reward);
    }

    private static final Facts PLAIN = facts(false, 5, true, 6000, 100_000, 100_000);

    @Test
    void withNothingOnNothingIsBlocked() {
        assertNull(VisitorDecision.decide(OFF, facts(true, 0, true, 1, 9_999_999, 0)));
        assertNull(VisitorDecision.decide(null, PLAIN));
        assertNull(VisitorDecision.decide(OFF, null));
        assertFalse(OFF.any());
    }

    @Test
    void aChosenRewardBlocksRefusing() {
        Rules r = with(true, false, false, false, false, false);
        assertEquals(BlockReason.RARE_REWARD, VisitorDecision.decide(r, facts(true, 5, true, 6000, 1, 1)));
        assertNull(VisitorDecision.decide(r, PLAIN), "ohne gewaehlte Belohnung im Angebot");
        assertTrue(BlockReason.RARE_REWARD.blockRefusing());
        assertTrue(r.any());
    }

    @Test
    void aNeverServedVisitorBlocksRefusing() {
        Rules r = with(false, true, false, false, false, false);
        assertEquals(BlockReason.NEVER_ACCEPTED, VisitorDecision.decide(r, facts(false, 0, true, 6000, 1, 1)));
        assertNull(VisitorDecision.decide(r, facts(false, 1, true, 6000, 1, 1)), "schon einmal bedient");
        assertNull(VisitorDecision.decide(r, facts(false, -1, true, 6000, 1, 1)), "unbekannt ist nicht 'nie'");
        // In einem Bingo-Profil gilt es nicht
        Rules bingo = new Rules(false, true, false, false, false, false, 6000, 150_000, true);
        assertNull(VisitorDecision.decide(bingo, facts(false, 0, true, 6000, 1, 1)));
    }

    @Test
    void theRewardComesBeforeTheNewVisitor() {
        Rules r = with(true, true, false, false, false, false);
        assertEquals(BlockReason.RARE_REWARD, VisitorDecision.decide(r, facts(true, 0, true, 6000, 1, 1)));
    }

    @Test
    void cheapCopperBlocksRefusingAtOrBelowTheLimit() {
        Rules r = with(false, false, true, false, false, false);
        assertEquals(BlockReason.CHEAP_COPPER, VisitorDecision.decide(r, facts(false, 5, true, 6000, 1, 1)), "gleich ist billig");
        assertEquals(BlockReason.CHEAP_COPPER, VisitorDecision.decide(r, facts(false, 5, true, 10, 1, 1)));
        assertNull(VisitorDecision.decide(r, facts(false, 5, true, 6001, 1, 1)));
    }

    @Test
    void expensiveCopperBlocksAcceptingAboveTheLimit() {
        Rules r = with(false, false, false, true, false, false);
        assertEquals(BlockReason.EXPENSIVE_COPPER, VisitorDecision.decide(r, facts(false, 5, true, 6001, 1, 1)));
        assertNull(VisitorDecision.decide(r, facts(false, 5, true, 6000, 1, 1)), "gleich ist nicht teuer");
        assertFalse(BlockReason.EXPENSIVE_COPPER.blockRefusing());
    }

    @Test
    void aLowLossBlocksRefusingAndAHighLossBlocksAccepting() {
        Rules low = with(false, false, false, false, true, false);
        // Verlust = Preis minus Wert: genau 150.000 ist noch niedrig
        assertEquals(BlockReason.LOW_LOSS, VisitorDecision.decide(low, facts(false, 5, true, 6000, 200_000, 50_000)));
        assertNull(VisitorDecision.decide(low, facts(false, 5, true, 6000, 200_001, 50_000)));
        assertEquals(BlockReason.LOW_LOSS, VisitorDecision.decide(low, facts(false, 5, true, 6000, 10_000, 50_000)), "ein Gewinn ist ein negativer Verlust");

        Rules high = with(false, false, false, false, false, true);
        assertEquals(BlockReason.HIGH_LOSS, VisitorDecision.decide(high, facts(false, 5, true, 6000, 200_001, 50_000)));
        assertNull(VisitorDecision.decide(high, facts(false, 5, true, 6000, 200_000, 50_000)));
        assertFalse(BlockReason.HIGH_LOSS.blockRefusing());
    }

    @Test
    void thePriceRulesNeedCopperAndKnownPrices() {
        Rules r = with(false, false, true, true, true, true);
        assertNull(VisitorDecision.decide(r, facts(false, 5, false, 0, 0, 0)));
    }

    @Test
    void copperRulesComeBeforeLossRules() {
        Rules r = with(false, false, true, false, true, false);
        assertEquals(BlockReason.CHEAP_COPPER, VisitorDecision.decide(r, facts(false, 5, true, 10, 10, 0)));
    }

    @Test
    void onlyTheButtonTheReasonHitsIsBlocked() {
        assertTrue(VisitorDecision.blocks(BlockReason.RARE_REWARD, Button.REFUSE, false));
        assertFalse(VisitorDecision.blocks(BlockReason.RARE_REWARD, Button.ACCEPT, false));
        assertTrue(VisitorDecision.blocks(BlockReason.EXPENSIVE_COPPER, Button.ACCEPT, false));
        assertFalse(VisitorDecision.blocks(BlockReason.EXPENSIVE_COPPER, Button.REFUSE, false));
        assertFalse(VisitorDecision.blocks(BlockReason.RARE_REWARD, Button.OTHER, false), "ein anderer Platz bleibt frei");
        assertFalse(VisitorDecision.blocks(null, Button.REFUSE, false));
        assertFalse(VisitorDecision.blocks(BlockReason.RARE_REWARD, null, false));
    }

    @Test
    void theBypassKeyLetsTheClickThrough() {
        for (BlockReason reason : BlockReason.values()) {
            Button blocked = reason.blockRefusing() ? Button.REFUSE : Button.ACCEPT;
            assertTrue(VisitorDecision.blocks(reason, blocked, false), reason.name());
            assertFalse(VisitorDecision.blocks(reason, blocked, true), reason.name() + " mit Umgehungstaste");
        }
    }

    @Test
    void theButtonsAreRecognisedByTheirNames() {
        assertEquals(Button.ACCEPT, Button.of("Accept Offer"));
        assertEquals(Button.ACCEPT, Button.of("§aAccept Offer"));
        assertEquals(Button.REFUSE, Button.of("Refuse Offer"));
        assertEquals(Button.REFUSE, Button.of("§cRefuse Offer "));
        assertEquals(Button.OTHER, Button.of("Close"));
        assertEquals(Button.OTHER, Button.of(null));
    }

    @Test
    void theSlotsAreTheOnesOfSkyHanni() {
        assertEquals(29, VisitorOffer.ACCEPT_SLOT);
        assertEquals(33, VisitorOffer.REFUSE_SLOT);
        assertEquals(33, VisitorDecision.blockedSlot(BlockReason.RARE_REWARD));
        assertEquals(29, VisitorDecision.bestSlot(BlockReason.RARE_REWARD));
        assertEquals(29, VisitorDecision.blockedSlot(BlockReason.HIGH_LOSS));
        assertEquals(33, VisitorDecision.bestSlot(BlockReason.HIGH_LOSS));
        assertEquals(-1, VisitorDecision.blockedSlot(null));
        assertEquals(-1, VisitorDecision.bestSlot(null));
    }

    @Test
    void theExplanationNamesTheNumberForThePriceRules() {
        java.util.function.DoubleFunction<String> plain = v -> Long.toString(Math.round(v));
        assertEquals("Rare visitor reward found", VisitorDecision.describe(BlockReason.RARE_REWARD, 0, 0, plain));
        assertEquals("Cheap copper (paying 4500 per)", VisitorDecision.describe(BlockReason.CHEAP_COPPER, 4500, 0, plain));
        assertEquals("Low loss (120000 loss)", VisitorDecision.describe(BlockReason.LOW_LOSS, 0, 120_000, plain));
        assertEquals("High loss (5000 profit)", VisitorDecision.describe(BlockReason.HIGH_LOSS, 0, -5000, plain));
        assertEquals("", VisitorDecision.describe(null, 0, 0, plain));
    }

    @Test
    void theRulesComeFromTheConfigAndStartOff() {
        ModConfig.GardenRewardWarningCategory c = new ModConfig.GardenRewardWarningCategory();
        Rules rules = VisitorDecision.rulesOf(c);
        assertFalse(rules.any());
        assertEquals(6000, rules.coinsPerCopper());
        assertEquals(150_000, rules.lossThreshold());
        assertFalse(rules.bingo());
        c.preventRefusing = true;
        c.coinsPerCopperPrice = 123;
        Rules changed = VisitorDecision.rulesOf(c);
        assertTrue(changed.preventRefusing());
        assertEquals(123, changed.coinsPerCopper());
    }

    @Test
    void onlyChosenRewardsOfTheOfferAreWarnedAbout() {
        ModConfig.GardenRewardWarningCategory c = new ModConfig.GardenRewardWarningCategory();
        List<VisitorReward> warned = VisitorDecision.warnedRewards(List.of("Overgrown Grass", "Delicate V", "Enchanted Carrot", "Dedication IV", "Overgrown Grass"), c);
        // Delicate V ist nicht in der Vorauswahl, die Karotte keine Belohnung, das Gras steht nur einmal da
        assertEquals(List.of(VisitorReward.OVERGROWN_GRASS, VisitorReward.DEDICATION), warned);
        c.rewardDelicate = true;
        assertTrue(VisitorDecision.warnedRewards(List.of("Delicate V"), c).contains(VisitorReward.DELICATE));
        assertTrue(VisitorDecision.warnedRewards(null, c).isEmpty());
    }
}

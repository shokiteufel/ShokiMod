package com.shokiteufel.shokimod.garden;

import com.shokiteufel.shokimod.data.ModConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * "Visitor Reward Warning": ob ein Knopf im Besucherfenster gesperrt wird.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code VisitorApi.blockReason}, {@code VisitorRewardWarning}). Von dort stammt die Regel, Punkt fuer Punkt und in dieser
 * Reihenfolge, der erste Treffer gewinnt:
 * <ol>
 * <li>"Block Refusing Reward" und eine der gewaehlten Belohnungen steht im Angebot: Ablehnen gesperrt.</li>
 * <li>"Block Refusing New Visitors" und man hat mit ihm noch nie ein Angebot erfuellt ("Offers Accepted: 0"; nicht in einem Bingo-Profil): Ablehnen gesperrt.</li>
 * <li>"Block Refusing Copper" und der Preis je Copper liegt bei oder unter "Coins per Copper": Ablehnen gesperrt (billiges Copper).</li>
 * <li>"Block Accepting Copper" und der Preis je Copper liegt ueber "Coins per Copper": Annehmen gesperrt (teures Copper).</li>
 * <li>"Block Refusing Low Loss" und der Verlust (Preis der Gegenstaende minus Wert des Copper) liegt bei oder unter "Acceptable Coin Loss": Ablehnen gesperrt.</li>
 * <li>"Block Accepting High Loss" und der Verlust liegt darueber: Annehmen gesperrt.</li>
 * </ol>
 * Die Umsetzung ist eigene Arbeit: Java, alles hier ist rein (ohne Spielzustand), die Regeln kommen als {@link Rules} aus der Einstellung. Anders als bei SkyHanni, wo
 * ein Angebot ohne Copper-Zeile einen Fehler wirft, zaehlen die Punkte drei bis sechs dann einfach nicht.
 */
public final class VisitorDecision {

    /** Warum ein Knopf gesperrt ist, mit dem Text fuer den Spieler und der Seite, die es trifft */
    public enum BlockReason {
        NEVER_ACCEPTED("Never accepted", true),
        RARE_REWARD("Rare visitor reward found", true),
        CHEAP_COPPER("Cheap copper", true),
        EXPENSIVE_COPPER("Expensive copper", false),
        LOW_LOSS("Low loss", true),
        HIGH_LOSS("High loss", false);

        private final String description;
        private final boolean blockRefusing;

        BlockReason(String description, boolean blockRefusing) {
            this.description = description;
            this.blockRefusing = blockRefusing;
        }

        public String description() {
            return description;
        }

        /** Wahr: gesperrt ist das Ablehnen. Falsch: gesperrt ist das Annehmen */
        public boolean blockRefusing() {
            return blockRefusing;
        }
    }

    /** Welcher Knopf des Fensters gemeint ist */
    public enum Button {
        ACCEPT, REFUSE, OTHER;

        /** Der Knopf zu dem Namen, den der Platz traegt (ohne Farbcodes); alles andere ist {@link #OTHER} */
        public static Button of(String plainName) {
            if (plainName == null) return OTHER;
            String name = plainName.replaceAll("§.", "").trim();
            if (name.equals(VisitorOffer.ACCEPT_NAME)) return ACCEPT;
            if (name.equals(VisitorOffer.REFUSE_NAME)) return REFUSE;
            return OTHER;
        }
    }

    /**
     * Die Schalter und Zahlen aus der Einstellung, die die Regel braucht.
     *
     * @param bingo ob das Profil ein Bingo-Profil ist (dort gilt "Block Refusing New Visitors" nicht); die Mod kennt es nicht und uebergibt falsch
     */
    public record Rules(boolean preventRefusing, boolean preventRefusingNew, boolean preventRefusingCopper, boolean preventAcceptingCopper,
                        boolean preventRefusingLowLoss, boolean preventAcceptingHighLoss, int coinsPerCopper, int lossThreshold, boolean bingo) {

        /** Ist ueberhaupt eine Sperre eingeschaltet? */
        public boolean any() {
            return preventRefusing || preventRefusingNew || preventRefusingCopper || preventAcceptingCopper || preventRefusingLowLoss || preventAcceptingHighLoss;
        }
    }

    /**
     * Was ueber ein Angebot bekannt ist.
     *
     * @param hasWarnedReward  steht eine der gewaehlten Belohnungen im Angebot?
     * @param offersAccepted   wie viele Angebote man ihm schon erfuellt hat, oder -1, wenn das nicht bekannt ist
     * @param hasCopper        nannte die Beschreibung Copper und sind die Preise der verlangten Gegenstaende und der Wert des Copper bekannt (sonst gibt es keinen
     *                         Preis je Copper und keinen Verlust, auf den man sich verlassen koennte)
     * @param pricePerCopper   Coins je Copper
     * @param totalPrice       der Preis der verlangten Gegenstaende abzueglich der Belohnungen, nie unter null
     * @param totalReward      der Wert des Copper in Coins
     */
    public record Facts(boolean hasWarnedReward, int offersAccepted, boolean hasCopper, int pricePerCopper, double totalPrice, double totalReward) {

        public double loss() {
            return totalPrice - totalReward;
        }
    }

    private VisitorDecision() {
    }

    /** Die Regeln aus der Einstellung */
    public static Rules rulesOf(ModConfig.GardenRewardWarningCategory c) {
        return new Rules(c.preventRefusing, c.preventRefusingNew, c.preventRefusingCopper, c.preventAcceptingCopper,
                c.preventRefusingLowLoss, c.preventAcceptingHighLoss, c.coinsPerCopperPrice, c.coinsLossThreshold, false);
    }

    /** Warum das Angebot einen Knopf sperrt, oder null, wenn es keinen sperrt */
    public static BlockReason decide(Rules r, Facts f) {
        if (r == null || f == null) return null;
        if (r.preventRefusing() && f.hasWarnedReward()) return BlockReason.RARE_REWARD;
        if (r.preventRefusingNew() && !r.bingo() && f.offersAccepted() == 0) return BlockReason.NEVER_ACCEPTED;
        if (f.hasCopper()) {
            if (r.preventRefusingCopper() && f.pricePerCopper() <= r.coinsPerCopper()) return BlockReason.CHEAP_COPPER;
            if (r.preventAcceptingCopper() && f.pricePerCopper() > r.coinsPerCopper()) return BlockReason.EXPENSIVE_COPPER;
            if (r.preventRefusingLowLoss() && f.loss() <= r.lossThreshold()) return BlockReason.LOW_LOSS;
            if (r.preventAcceptingHighLoss() && f.loss() > r.lossThreshold()) return BlockReason.HIGH_LOSS;
        }
        return null;
    }

    /**
     * Wird ein Klick auf diesen Knopf geschluckt? Nur der Knopf, den der Grund trifft, und nur ohne gehaltene Umgehungstaste.
     */
    public static boolean blocks(BlockReason reason, Button button, boolean bypassHeld) {
        if (reason == null || bypassHeld || button == null || button == Button.OTHER) return false;
        return reason.blockRefusing() ? button == Button.REFUSE : button == Button.ACCEPT;
    }

    /**
     * Der Satz fuer den Spieler: der Grund und, bei den Preisregeln, die Zahl dazu ("Cheap copper (paying 4.5k per)", "Low loss (120k loss)").
     *
     * @param format schreibt eine Summe kurz ("12.3k")
     */
    public static String describe(BlockReason reason, int pricePerCopper, double loss, java.util.function.DoubleFunction<String> format) {
        if (reason == null) return "";
        return switch (reason) {
            case CHEAP_COPPER, EXPENSIVE_COPPER -> reason.description() + " (paying " + format.apply(pricePerCopper) + " per)";
            case LOW_LOSS, HIGH_LOSS -> reason.description() + " (" + format.apply(Math.abs(loss)) + (loss > 0 ? " loss" : " profit") + ")";
            default -> reason.description();
        };
    }

    /** Der Platz des gesperrten Knopfes im Fenster, oder -1 ohne Grund */
    public static int blockedSlot(BlockReason reason) {
        if (reason == null) return -1;
        return reason.blockRefusing() ? VisitorOffer.REFUSE_SLOT : VisitorOffer.ACCEPT_SLOT;
    }

    /** Der Platz des anderen Knopfes (der bessere Weg): der, der nicht gesperrt ist; -1 ohne Grund */
    public static int bestSlot(BlockReason reason) {
        if (reason == null) return -1;
        return reason.blockRefusing() ? VisitorOffer.ACCEPT_SLOT : VisitorOffer.REFUSE_SLOT;
    }

    /** Die Belohnungen des Angebots, die der Spieler gewaehlt hat, in der Reihenfolge der Beschreibung und ohne Doppelte */
    public static List<VisitorReward> warnedRewards(Collection<String> rewardNames, ModConfig.GardenRewardWarningCategory chosen) {
        List<VisitorReward> out = new ArrayList<>();
        if (rewardNames == null) return out;
        for (String name : rewardNames) {
            VisitorReward reward = VisitorReward.byName(name);
            if (reward != null && reward.chosen(chosen) && !out.contains(reward)) out.add(reward);
        }
        return out;
    }
}

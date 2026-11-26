package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.EivPriceSource;
import com.shokiteufel.shokimod.util.EivCalculator;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Estimated Item Value (2.4.18): was keine Schaetzung bekommt, wie der Kasten und die Kleiderschrank-Zeilen aussehen. */
class EstimatedItemValueTest {

    private static EivCalculator.Subject item(String id, String rawId, String name, String... lore) {
        return new EivCalculator.Subject(id, rawId, id, new CompoundTag(), List.of(lore), name);
    }

    @Test
    void anOrdinaryItemGetsAnEstimate() {
        assertFalse(EstimatedItemValue.ignored(item("HYPERION", "HYPERION", "Hyperion"), "Your Inventory", true));
    }

    @Test
    void noSubjectMeansNothingToShow() {
        assertTrue(EstimatedItemValue.ignored(null, "x", true));
    }

    @Test
    void booksRunesPassesAndMapsAreLeftOut() {
        assertTrue(EstimatedItemValue.ignored(item("ENCHANTMENT_SHARPNESS_6", "ENCHANTED_BOOK", "Enchanted Book"), "x", true));
        assertTrue(EstimatedItemValue.ignored(item("BITE_RUNE;3", "RUNE", "Bite Rune"), "x", true));
        assertTrue(EstimatedItemValue.ignored(item("UNIQUE_RUNE_X", "UNIQUE_RUNE_X", "Rune"), "x", true));
        assertTrue(EstimatedItemValue.ignored(item("ULTIMATE_ULTIMATE_WISE", "X", "Book"), "x", true));
        assertTrue(EstimatedItemValue.ignored(item("CATACOMBS_PASS_5", "X", "Pass"), "x", true));
        assertTrue(EstimatedItemValue.ignored(item("MASTER_CATACOMBS_PASS_3", "X", "Pass"), "x", true));
        assertTrue(EstimatedItemValue.ignored(item("MAP-1234", "X", "Map"), "x", true));
        assertTrue(EstimatedItemValue.ignored(item("WISP_POTION", "WISP_POTION", "Wisp"), "x", true));
    }

    @Test
    void shopSalespeopleAndPassiveAbilitiesAreLeftOut() {
        assertTrue(EstimatedItemValue.ignored(item("X", "X", "Bazaar Salesperson"), "x", true));
        assertTrue(EstimatedItemValue.ignored(item("X", "X", "☘ Category: Item Ability (Passive)"), "x", true));
    }

    @Test
    void theWardrobeChoiceOnlySkipsWhatIsNotInYourInventory() {
        EivCalculator.Subject piece = item("HYPERION", "HYPERION", "Hyperion");
        assertTrue(EstimatedItemValue.ignored(piece, "Choose a wardrobe slot", false));
        assertFalse(EstimatedItemValue.ignored(piece, "Choose a wardrobe slot", true));
    }

    @Test
    void anArmorSetInTheMuseumAndACollectableDeliveryAreLeftOut() {
        assertTrue(EstimatedItemValue.ignored(item("X", "X", "Set", "Armor Set Donation"), "Museum Armor", true));
        assertFalse(EstimatedItemValue.ignored(item("X", "X", "Item", "Something else"), "Museum Armor", true));
        assertFalse(EstimatedItemValue.ignored(item("X", "X", "Set", "Armor Set Donation"), "Not a museum", true));
        assertTrue(EstimatedItemValue.ignored(item("X", "X", "Delivery", "Click to collect!"), "Island Deliveries", true));
        assertFalse(EstimatedItemValue.ignored(item("X", "X", "Delivery", "Click to collect!"), "Something", true));
    }

    @Test
    void theBoxStaysEmptyWhenTheItemIsWorthJustItsBase() {
        EivCalculator.Result plain = new EivCalculator.Result(100.0, 100.0, List.of("§7Base item: x"));
        assertEquals(List.of(), EstimatedItemValue.displayOf(plain, false));
    }

    @Test
    void theBoxHasAHeaderTheLinesAndTheTotal() {
        EivCalculator.Result r = new EivCalculator.Result(1_234_567.0, 1_000_000.0, List.of("§7Base item: x", "§7Stars: y"));
        assertEquals(List.of("§aEstimated Item Value:", "§7Base item: x", "§7Stars: y", "§aTotal: §6§l1.2M coins"), EstimatedItemValue.displayOf(r, false));
        assertEquals("§aTotal: §6§l1,234,567 coins", EstimatedItemValue.displayOf(r, true).get(3), "Show Exact Price");
    }

    @Test
    void aWardrobeSetListsItsPiecesAndTheSum() {
        List<String> lines = EstimatedItemValue.wardrobeLines("Armor", List.of("§6Helmet", "§6Boots"), java.util.Arrays.asList(1_500_000.0, 500_000.0));
        assertEquals(List.of("§aEstimated Armor Value:", "  §7- §6Helmet§7: §61.5M", "  §7- §6Boots§7: §6500k", " §aTotal Value: §6§l2M coins"), lines);
    }

    @Test
    void anEmptyWardrobeSetHasNoLines() {
        assertEquals(List.of(), EstimatedItemValue.wardrobeLines("Armor", List.of(), List.of()));
    }

    @Test
    void theOptionsFollowTheSettings() {
        ModConfig.EstimatedItemValueCategory c = new ModConfig.EstimatedItemValueCategory();
        c.priceSource = EivPriceSource.NPC_SELL;
        c.enchantmentsCap = 12;
        c.starMaterialCap = 4;
        c.ignoreRunes = true;
        EivCalculator.Options o = EstimatedItemValue.options(c);
        assertEquals(EivPriceSource.NPC_SELL, o.source());
        assertEquals(12, o.enchantmentsCap());
        assertEquals(4, o.starMaterialCap());
        assertTrue(o.ignoreRunes());
        assertFalse(o.ignoreHelmetSkins());
    }
}

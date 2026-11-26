package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.chat.Chat2Support.Place;
import com.shokiteufel.shokimod.chat.Chat2Support.Run;
import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import org.junit.jupiter.api.Test;

import static com.shokiteufel.shokimod.chat.Chat2Support.assertKind;
import static com.shokiteufel.shokimod.chat.Chat2Support.config;
import static com.shokiteufel.shokimod.chat.Chat2Support.normal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat > Shorten Coin Amounts: die Beispielzeilen von SkyHanni (mit den Teilen, in denen Hypixel die Muenzen golden schreibt) und die Gegenbeispiele. */
class ShortenCoinsTest {

    private static StyledText rewrite(String legacy) {
        return ShortenCoins.rewrite(StyledText.ofLegacy(legacy));
    }

    private static void assertShortened(String before, String after) {
        StyledText result = rewrite(before);
        assertNotNull(result, "gekuerzt: " + before);
        assertEquals(normal(after), result.legacy());
    }

    private static void assertUnchanged(String legacy) {
        assertNull(rewrite(legacy), "bleibt: " + legacy);
    }

    @Test
    void theSampleLinesOfSkyHanniAreShortened() {
        assertShortened("§e[Auction] §bEuropaPlus §ebought §aAtmospheric Filter §efor §62,650,000 coins §e§lCLICK",
                "§e[Auction] §bEuropaPlus §ebought §aAtmospheric Filter §efor §62.6M coins §e§lCLICK");
        assertShortened("§aYou sold Cicada Symphony Vinyl x1 for §6650,000 Coins§a!", "§aYou sold Cicada Symphony Vinyl x1 for §6650k Coins§a!");
        assertShortened("§aALLOWANCE! You earned §650,000 coins§a!", "§aALLOWANCE! You earned §650k coins§a!");
        assertShortened("§e[Bazaar] §6Sell Offer Setup! §a5x Enchanted Melon Block §7for §6250,303 coins§7.", "§e[Bazaar] §6Sell Offer Setup! §a5x Enchanted Melon Block §7for §6250k coins§7.");
        assertShortened("§e[NPC] Sirius§f: The highest bidder was LOMENJUICE with a bid of §68,620,000 Coins§f!",
                "§e[NPC] Sirius§f: The highest bidder was LOMENJUICE with a bid of §68.6M Coins§f!");
    }

    @Test
    void theFailingSamplesOfSkyHanniStayAsTheyAre() {
        // Schon gekuerzt (10.5k, 991.1M): keine Zahl mit Leerzeichen dahinter, die das Muster nimmt
        assertUnchanged("§aYou have withdrawn §610.5k coins§r§a! You now have §6991.1M coins §ain your account!");
        assertUnchanged(":typing:  -  ✎...");
        assertUnchanged("Profile ID: 23a8da75-5655-49b2-89e7-31b9d2a7ab7b");
    }

    @Test
    void onlyANumberAtTheStartOfAGoldenPartIsShortened() {
        // Steht die Zahl mitten in einem goldenen Teil, ist es keine Muenze im Sinne der Regel
        assertUnchanged("§6You earned 50,000 coins!");
        // Nicht golden
        assertUnchanged("§e50,000 coins");
        assertUnchanged("§f50,000 coins");
        // Ohne Stil (kein Komponente) gibt es kein Gold
        assertNull(ShortenCoins.rewrite(StyledText.ofPlain("You earned 50,000 coins!")));
    }

    @Test
    void numbersThatStayTheSameMakeNoNewLine() {
        assertUnchanged("§6250 coins");
        assertUnchanged("§6999 Coins");
    }

    @Test
    void severalAmountsInOneLineAreAllShortened() {
        assertShortened("§7Paid §6100,000 coins §7and got §6200,000 coins §7back", "§7Paid §6100k coins §7and got §6200k coins §7back");
    }

    @Test
    void aPunctuationMarkBehindTheNumberStaysWhereItIs() {
        assertShortened("§7Pay §65,100,000. §7Thanks", "§7Pay §65.1M. §7Thanks");
        assertShortened("§7Pay §65,100,000, §7thanks", "§7Pay §65.1M, §7thanks");
    }

    @Test
    void clickAndHoverStayOnTheShortenedPart() {
        MutableComponent line = Component.literal("You sold X for ")
                .append(Component.literal("650,000 Coins").withStyle(ChatFormatting.GOLD)
                        .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/bz")).withHoverEvent(new HoverEvent.ShowText(Component.literal("tip")))))
                .append(Component.literal("!"));
        StyledText result = ShortenCoins.rewrite(StyledText.of(line));
        assertNotNull(result);
        assertEquals("You sold X for 650k Coins!", result.plain());
        assertEquals("/bz", ((ClickEvent.RunCommand) result.firstClick()).command());
        assertNotNull(result.firstHover());
        assertEquals("You sold X for 650k Coins!", result.toComponent().getString());
    }

    @Test
    void theCheapLookOnlyAsksForADigit() {
        assertTrue(ShortenCoins.mayApply("a 5"));
        assertFalse(ShortenCoins.mayApply("no numbers here"));
        assertFalse(ShortenCoins.mayApply(""));
    }

    @Test
    void throughTheEngineTheLineIsReplacedAndOtherLinesStay() {
        Run run = new Run(config(c -> c.shortenCoinAmounts = true), new Place("Hub"));
        ChatVerdict v = run.line("§aALLOWANCE! You earned §650,000 coins§a!");
        assertKind(ChatVerdict.Kind.REPLACE, v, "shortened");
        assertEquals(normal("§aALLOWANCE! You earned §650k coins§a!"), v.lines().get(0).legacyText());
        assertKind(ChatVerdict.Kind.SHOW, run.line("§aALLOWANCE! You earned §e50,000 coins§a!"), "not gold");
        assertKind(ChatVerdict.Kind.SHOW, run.line("§7Hello"), "no number");
        Run off = new Run(new ChatTabCategory(), new Place("Hub"));
        assertKind(ChatVerdict.Kind.SHOW, off.line("§aALLOWANCE! You earned §650,000 coins§a!"), "off by default");
        Place lobby = new Place("Hub");
        lobby.skyblock = false;
        assertKind(ChatVerdict.Kind.SHOW, new Run(config(c -> c.shortenCoinAmounts = true), lobby).line("§aALLOWANCE! You earned §650,000 coins§a!"), "not SkyBlock");
    }
}

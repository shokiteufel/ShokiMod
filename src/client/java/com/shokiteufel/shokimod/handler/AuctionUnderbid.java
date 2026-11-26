package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.mixin.AbstractContainerScreenAccessor;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Der Preis fuer ein Gebot knapp unter dem niedrigsten, in die Zwischenablage.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code AuctionHouseCopyUnderbidPrice}). Die Regel von dort:
 * <ul>
 * <li>Im Fenster "Create BIN Auction" liegt das Stueck, das man einstellt, im Platz 13. Sein Preis (niedrigster BIN mal Stapelgroesse) minus ein Coin
 * kommt in die Zwischenablage, einmal je Stueck; ist kein Preis bekannt, wird die Zwischenablage geleert.</li>
 * <li>In "Auctions Browser", "Manage Auctions" und "Auctions: ..." kopiert eine Taste den Preis der Auktion unter dem Zeiger minus ein Coin: die Zeile
 * "Buy it now", "Starting bid" oder "Top bid" aus der Beschreibung.</li>
 * </ul>
 * Die Umsetzung ist eigene Arbeit: Java, die Preise sind die niedrigsten BIN des Profit-Trackers ({@link ItemValue#LOWEST_BIN}), die Taste eine Taste dieser
 * Mod (steigende Flanke), eigener Wortlaut. Nicht gleich ist, dass das Fenster beim erneuten Oeffnen mit demselben Stueck wieder kopiert: SkyHanni
 * merkt sich das letzte Stueck bis zu einem anderen.
 */
public final class AuctionUnderbid {

    static final String CREATE_TITLE = "Create BIN Auction";
    /** Der Platz des Stuecks im Fenster zum Einstellen */
    static final int ITEM_SLOT = 13;
    private static final Pattern PRICE_LINE = Pattern.compile("^(?:Buy it now|Starting bid|Top bid): ([0-9,]+) coins$");
    private static final Pattern BROWSE_TITLE = Pattern.compile("Auctions Browser|Manage Auctions|Auctions: \".*\"?");

    /** Welches Stueck zuletzt kopiert wurde (Kennung und Anzahl), solange das Fenster offen ist */
    private static String lastCopied = null;
    private static boolean keyWasDown = false;
    private static boolean fetched = false;

    private AuctionUnderbid() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(AuctionUnderbid::tick);
    }

    private static ModConfig.AuctionHouseHelpersCategory cfg() {
        return ModConfig.INSTANCE.inventory.auctions;
    }

    /** Ein Coin darunter - nie unter null */
    static long underbid(long price) {
        return Math.max(0L, price - 1L);
    }

    /** Der Preis aus der Beschreibung einer Auktion ("Buy it now: 1,000,000 coins"), oder null. Die Zeilen sind ohne Farbcodes */
    static Long priceInLore(List<String> lore) {
        if (lore == null) return null;
        for (String line : lore) {
            Matcher m = PRICE_LINE.matcher(line.trim());
            if (!m.matches()) continue;
            try {
                return Long.parseLong(m.group(1).replace(",", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Ist das ein Fenster, in dem die Taste gilt? */
    static boolean isBrowseTitle(String title) {
        return title != null && BROWSE_TITLE.matcher(title).matches();
    }

    /** Der niedrigste BIN eines Stuecks mal die Anzahl, oder -1, wenn er nicht bekannt ist */
    static long totalPrice(String itemId, int count) {
        if (itemId == null || count <= 0) return -1L;
        for (String candidate : SkyBlockItems.priceCandidates(itemId)) {
            Double bin = ItemValue.LOWEST_BIN.get(candidate);
            if (bin != null && bin > 0) return Math.round(bin * count);
        }
        return -1L;
    }

    private static String cleanTitle(AbstractContainerScreen<?> screen) {
        return screen.getTitle().getString().replaceAll("§.", "").trim();
    }

    private static void tick(Minecraft client) {
        if (!FeatureGate.auctionUnderbid() || client.player == null || !GameState.Server.isSkyblock()
                || !(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            lastCopied = null;
            keyWasDown = false;
            fetched = false;
            return;
        }
        String title = cleanTitle(screen);
        if (title.equals(CREATE_TITLE)) {
            keyWasDown = false;
            if (cfg().autoCopyUnderbid) copyForCreate(client, screen);
            return;
        }
        lastCopied = null;
        fetched = false;
        if (!cfg().hasCopyKey() || !isBrowseTitle(title)) {
            keyWasDown = false;
            return;
        }
        boolean down = ShortcutHandler.isDown(cfg().copyUnderbidKey);
        if (down && !keyWasDown) copyHovered(client, screen);
        keyWasDown = down;
    }

    /** Das Stueck im Fenster zum Einstellen: einmal je Stueck kopieren */
    private static void copyForCreate(Minecraft client, AbstractContainerScreen<?> screen) {
        if (!fetched) {
            fetched = true;
            ItemValue.LOWEST_BIN.prefetch();
        }
        if (screen.getMenu().slots.size() <= ITEM_SLOT) return;
        ItemStack stack = screen.getMenu().slots.get(ITEM_SLOT).getItem();
        if (stack.isEmpty()) return;
        String id = SkyBlockItems.idOf(stack);
        if (id == null) return;
        String key = id + "x" + stack.getCount();
        if (key.equals(lastCopied)) return;
        long total = totalPrice(id, stack.getCount());
        // Ohne Preis ist die Liste vielleicht nur noch nicht da: dann beim naechsten Tick noch einmal versuchen, nicht gleich leeren
        if (total <= 0 && !ItemValue.LOWEST_BIN.ready()) return;
        lastCopied = key;
        if (total <= 0) {
            client.keyboardHandler.setClipboard("");
            tell(client, Component.literal("No lowest BIN price is known for " + stack.getHoverName().getString().replaceAll("§.", "")
                    + " - your clipboard is empty.").withStyle(ChatFormatting.GRAY));
            return;
        }
        copy(client, underbid(total));
    }

    /** Die Auktion unter dem Zeiger: ihr Preis minus ein Coin */
    private static void copyHovered(Minecraft client, AbstractContainerScreen<?> screen) {
        Slot slot = ((AbstractContainerScreenAccessor) screen).shokimod$hoveredSlot();
        if (slot == null) return;
        Long price = priceInLore(SkyBlockItems.loreOf(slot.getItem()));
        if (price == null) return;
        copy(client, underbid(price));
    }

    private static void copy(Minecraft client, long price) {
        client.keyboardHandler.setClipboard(Long.toString(price));
        tell(client, Component.literal("Copied " + String.format(Locale.US, "%,d", price) + " to your clipboard (one coin below the price).")
                .withStyle(ChatFormatting.WHITE));
        ShokiMod.LOGGER.info("[Auction] copied underbid price {}", price);
    }

    private static void tell(Minecraft client, Component text) {
        if (client.player == null) return;
        client.player.sendSystemMessage(Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA).append(text));
    }
}

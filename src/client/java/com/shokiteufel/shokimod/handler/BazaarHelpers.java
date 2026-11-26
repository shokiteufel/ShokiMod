package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.mixin.AbstractContainerScreenAccessor;
import com.shokiteufel.shokimod.util.ItemNames;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ItemValue.BazaarPrice;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Hilfen beim Handeln im Basar: Purchase helper, Order helper, Best sell method und die Zwischenablage nach einem abgebrochenen Kaufauftrag.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code BazaarApi}, {@code BazaarOrderHelper}, {@code BazaarBestSellMethod},
 * {@code BazaarCancelledBuyOrderClipboard}, {@code BazaarOrderApi}). Die Regeln von dort:
 * <ul>
 * <li><b>Purchase helper:</b> Das gesuchte Stueck wird in den Listen des Basars (Plaetze 9 bis 44) gruen markiert; es ist der Name, nach dem man gesucht hat. Er
 * geht aus, sobald man es gekauft hat oder einen Auftrag dafuer stellt ("[Bazaar] Bought ...", "Buy Order Setup! ...").</li>
 * <li><b>Order helper:</b> In "Your Bazaar Orders" (und der Liste mit der Gruppe) heisst jeder Auftrag "BUY Name" oder "SELL Name"; die Beschreibung nennt
 * Menge, "Filled: x/y (z%)", "You have n items to claim!" und "Price per unit: p coins". Ein Auftrag mit "100%!" ist fertig (gruen); ein Kaufauftrag unter
 * dem besten Kaufauftrag oder ein Verkaufsangebot ueber dem billigsten Angebot ist ueberboten (gold).</li>
 * <li><b>Best sell method:</b> Im Fenster eines Produkts (Platz 10 "Buy Instantly", Platz 13 das Stueck) steht, wie viel man durch Sofortverkauf gegenueber einem
 * eigenen Angebot verliert: (billigstes Angebot - bester Kaufauftrag) mal die Menge im Inventar.</li>
 * <li><b>Cancelled buy order clipboard:</b> In "Order options" nennt der Platz 11 ("Cancel Order") die fehlenden Stuecke ("... from 50x missing items."). Nach
 * "[Bazaar] Cancelled! Refunded N coins from cancelling Buy Order!" steht eine anklickbare Zeile da; der Klick sucht das Stueck im Basar neu, die Menge liegt in der
 * Zwischenablage.</li>
 * </ul>
 * Die Umsetzung ist eigene Arbeit: Java, die Markierungen ueber {@link WindowHighlights} (Einsprung vor dem Stueck), die Preise aus den Basarpreisen des
 * Profit-Trackers, welcher Auftrag gemeint ist, merkt sich der Zeiger (statt eines Klick-Ereignisses), die Zeile klickt ueber {@code /shoki bzreorder}, eigener Wortlaut.
 */
public final class BazaarHelpers {

    private static final int TICKS_BETWEEN_READS = 5;

    private static final Pattern ORDER_NAME = Pattern.compile("^(BUY|SELL) (.+)$");
    private static final Pattern ORDER_AMOUNT = Pattern.compile("^(?:Order|Offer) amount: ([\\d,]+)x$");
    private static final Pattern FILLED = Pattern.compile("^Filled: ([\\d,.kKmMbB]+)/[\\d,.kKmMbB]+ (\\S+)$");
    private static final Pattern CLAIMABLE = Pattern.compile("^You have ([\\d,]+) items? to claim!$");
    private static final Pattern PRICE_PER_UNIT = Pattern.compile("^Price per unit: ([\\d,.]+) coins$");
    /**
     * Die Zahl vor "x missing items." am Ende einer Zeile, gleich was davor steht: Die Beschreibung bricht je nach Breite verschieden um
     * ("from 50x missing items.", "22x missing items.", "coins from 2,000x missing items.").
     */
    private static final Pattern MISSING = Pattern.compile("(?:^|\\s)([\\d,]+)x missing items\\.$");
    private static final Pattern CANCELLED = Pattern.compile("^\\[Bazaar] Cancelled! Refunded ([\\d,.]+) coins from cancelling Buy Order!$");
    private static final Pattern TRANSACTION = Pattern.compile(
            "^\\[Bazaar] (?:Bought|Buy Order Setup!|Sold|Sell Offer Setup!|Order Flipped!) [\\d,]+x (.+) for [\\d,.]+ coins.*$");
    private static final Pattern SEARCH_COMMAND = Pattern.compile("^(?:bz|bazaar)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDERS_TITLE = Pattern.compile("Your Bazaar Orders|Co-op Bazaar Orders");
    private static final Pattern BAZAAR_TITLE = Pattern.compile(
            "Bazaar ➜ .*|How many do you want\\?|How much do you want to pay\\?|Confirm Buy Order|Confirm Instant Buy|At what price are you selling\\?|Confirm Sell Offer|Order options");

    /** Die Plaetze, in denen die Listen des Basars ihre Stuecke zeigen (SkyHanni: 9 bis 44) */
    static final int LIST_FIRST = 9;
    static final int LIST_LAST = 44;
    private static final int BUY_INSTANTLY_SLOT = 10;
    private static final int PRODUCT_SLOT = 13;
    private static final int CANCEL_SLOT = 11;

    /** Ein Auftrag aus "Your Bazaar Orders" */
    record Order(boolean buy, String product, int amount, boolean full, int claimable, double pricePerUnit) {
    }

    /** Was das Fenster "Cancel Order" nannte und was danach beim Neu-Bestellen gebraucht wird */
    record Reorder(String product, int amount, String refunded) {
    }

    private static String searched = null;
    private static int ticks = 0;
    private static String lastTitle = null;
    /** Der zuletzt unter dem Zeiger gesehene Auftrag der Liste: Er ist der, der mit dem naechsten Klick gemeint ist */
    private static String hoveredOrderProduct = null;
    /** Der Auftrag, dessen Fenster "Order options" gerade offen ist, und was in ihm als fehlend steht */
    private static String optionProduct = null;
    private static int latestAmount = -1;
    private static Reorder pending = null;
    private static String sellDifference = null;

    private BazaarHelpers() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(BazaarHelpers::tick);
        // Eine Suche mit /bz oder /bazaar merken: nach ihr richtet sich der Purchase helper
        ClientSendMessageEvents.COMMAND.register(BazaarHelpers::onCommand);
    }

    private static ModConfig.BazaarHelpersCategory cfg() {
        return ModConfig.INSTANCE.inventory.bazaar;
    }

    // ------------------------------------------------------------------ reine Teile

    static String clean(String text) {
        return text == null ? "" : text.replaceAll("§.", "").trim();
    }

    private static int number(String text) {
        try {
            return Integer.parseInt(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Ein Auftrag aus Name ("BUY Wheat") und Zeilen darunter, alles ohne Farbcodes; null, wenn es kein Auftrag ist oder Menge oder Preis fehlen */
    static Order parseOrder(String name, List<String> lore) {
        Matcher n = ORDER_NAME.matcher(name == null ? "" : name.trim());
        if (!n.matches()) return null;
        int amount = -1;
        double price = -1;
        boolean full = false;
        int claimable = 0;
        for (String raw : lore) {
            String line = raw.trim();
            Matcher a = ORDER_AMOUNT.matcher(line);
            if (a.matches()) {
                amount = number(a.group(1));
                continue;
            }
            Matcher p = PRICE_PER_UNIT.matcher(line);
            if (p.matches()) {
                try {
                    price = Double.parseDouble(p.group(1).replace(",", ""));
                } catch (NumberFormatException e) {
                    // bleibt -1
                }
                continue;
            }
            Matcher f = FILLED.matcher(line);
            if (f.matches()) {
                full = f.group(2).equals("100%!");
                continue;
            }
            Matcher c = CLAIMABLE.matcher(line);
            if (c.matches()) claimable = number(c.group(1));
        }
        if (amount < 0 || price < 0) return null;
        return new Order(n.group(1).equals("BUY"), n.group(2), amount, full, claimable, price);
    }

    /** Wie ein Auftrag markiert wird: fertig gruen, ueberboten gold, sonst gar nicht (0) */
    static int colourFor(Order order, BazaarPrice price) {
        if (order.full()) return WindowHighlights.GREEN;
        if (price == null) return 0;
        boolean outbid = order.buy()
                ? price.highestBid() > 0 && order.pricePerUnit() < price.highestBid()
                : price.cheapestOffer() > 0 && order.pricePerUnit() > price.cheapestOffer();
        return outbid ? WindowHighlights.GOLD : 0;
    }

    /** Wie viele Stuecke beim Abbrechen als fehlend dastehen, oder -1 */
    static int missingAmount(List<String> lore) {
        for (String raw : lore) {
            Matcher m = MISSING.matcher(raw.trim());
            if (m.find()) return number(m.group(1));
        }
        return -1;
    }

    /** Die Suche aus einem Befehl ("bz coal", "bazaar Enchanted Coal"), sonst null */
    static String searchIn(String command) {
        if (command == null) return null;
        Matcher m = SEARCH_COMMAND.matcher(command.trim());
        if (!m.matches()) return null;
        String text = m.group(1).trim();
        return text.isEmpty() ? null : text;
    }

    /** Der Name, den eine Zeile der Liste traegt, ist der gesuchte (Gross- und Kleinschreibung egal) */
    static boolean matchesSearch(String slotName, String search) {
        return search != null && !search.isEmpty() && clean(slotName).equalsIgnoreCase(search.trim());
    }

    /** Das Stueck, das eine Kauf- oder Verkaufszeile des Chats nennt, sonst null */
    static String transactionItem(String plain) {
        if (plain == null) return null;
        Matcher m = TRANSACTION.matcher(plain.trim());
        return m.matches() ? m.group(1).trim() : null;
    }

    /** Die Erstattung aus der Abbruchzeile von Hypixel ("12,345"), sonst null */
    static String refundedIn(String plain) {
        if (plain == null) return null;
        Matcher m = CANCELLED.matcher(plain.trim());
        return m.matches() ? m.group(1) : null;
    }

    /** Der Text des Kastens: "Name sell difference: 1.2m coins" */
    static String sellDifferenceText(String name, double cheapestOffer, double highestBid, int having) {
        if (having <= 0) return null;
        double diff = (cheapestOffer - highestBid) * having;
        return "§f" + name + "§7 sell difference: §6" + ItemValue.format(diff) + " coins";
    }

    /** Heisst das Fenster so, dass es ein Fenster des Basars ist? */
    static boolean isBazaarTitle(String title) {
        return title != null && (BAZAAR_TITLE.matcher(title).matches() || ORDERS_TITLE.matcher(title).matches());
    }

    static boolean isOrdersTitle(String title) {
        return title != null && ORDERS_TITLE.matcher(title).matches();
    }

    // ------------------------------------------------------------------ Zustand

    public static String searched() {
        return searched;
    }

    /** Der Text des Kastens "Best sell method", oder null, wenn gerade nichts zu sagen ist */
    public static String sellDifference() {
        return sellDifference;
    }

    static void resetForTest() {
        searched = null;
        ticks = 0;
        lastTitle = null;
        hoveredOrderProduct = null;
        optionProduct = null;
        latestAmount = -1;
        pending = null;
        sellDifference = null;
        WindowHighlights.clear();
    }

    private static void onCommand(String command) {
        if (!FeatureGate.bazaarHelpers() || !cfg().purchaseHelper) return;
        String search = searchIn(command);
        if (search != null) searched = search;
    }

    // ------------------------------------------------------------------ Tick

    private static void tick(Minecraft client) {
        if (!FeatureGate.bazaarHelpers() || client.player == null || !GameState.Server.isSkyblock()
                || !(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            leaveWindow();
            return;
        }
        String title = clean(screen.getTitle().getString());
        boolean changed = !title.equals(lastTitle);
        if (changed) {
            // Von der Liste der Auftraege zu "Order options": Der Auftrag unter dem Zeiger ist der gemeinte
            if ("Order options".equals(title) && isOrdersTitle(lastTitle) && hoveredOrderProduct != null) {
                optionProduct = hoveredOrderProduct;
            }
            lastTitle = title;
            ticks = 0;
            WindowHighlights.clear();
            sellDifference = null;
            latestAmount = "Order options".equals(title) ? -1 : latestAmount;
        }
        if (!isBazaarTitle(title)) {
            WindowHighlights.clear();
            sellDifference = null;
            return;
        }
        if (isOrdersTitle(title)) rememberHoveredOrder(screen);
        if (!changed && ++ticks < TICKS_BETWEEN_READS) return;
        ticks = 0;
        read(client, screen, title);
    }

    private static void leaveWindow() {
        if (lastTitle != null || WindowHighlights.count() > 0 || sellDifference != null) {
            lastTitle = null;
            WindowHighlights.clear();
            sellDifference = null;
        }
    }

    private static void rememberHoveredOrder(AbstractContainerScreen<?> screen) {
        Slot slot = ((AbstractContainerScreenAccessor) screen).shokimod$hoveredSlot();
        if (slot == null || slot.container instanceof Inventory) return;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;
        Matcher m = ORDER_NAME.matcher(clean(stack.getHoverName().getString()));
        if (m.matches()) hoveredOrderProduct = m.group(2);
    }

    /** Das Fenster lesen: Markierungen setzen, den Kasten fuellen, die fehlende Menge merken */
    private static void read(Minecraft client, AbstractContainerScreen<?> screen, String title) {
        List<Slot> slots = screen.getMenu().slots;
        ModConfig.BazaarHelpersCategory c = cfg();
        WindowHighlights.clear();

        if (c.purchaseHelper && searched != null) {
            for (Slot slot : slots) {
                if (slot.container instanceof Inventory || slot.index < LIST_FIRST || slot.index > LIST_LAST) continue;
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty() && matchesSearch(stack.getHoverName().getString(), searched)) {
                    WindowHighlights.mark(slot.index, WindowHighlights.GREEN);
                }
            }
        }
        if (c.orderHelper && isOrdersTitle(title)) {
            for (Slot slot : slots) {
                if (slot.container instanceof Inventory) continue;
                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) continue;
                Order order = parseOrder(clean(stack.getHoverName().getString()), SkyBlockItems.loreOf(stack));
                if (order == null) continue;
                int colour = colourFor(order, priceOf(stack, order.product()));
                if (colour != 0) WindowHighlights.mark(slot.index, colour);
            }
        }
        if (c.bestSellMethod) readProduct(client, slots);
        if (c.cancelledBuyOrderClipboard && "Order options".equals(title)) readCancel(slots);
    }

    /** Der Basarpreis zu einem Auftrag: aus der Kennung des Stuecks, sonst aus dem Namen */
    private static BazaarPrice priceOf(ItemStack stack, String product) {
        String id = SkyBlockItems.idOf(stack);
        if (id != null) {
            BazaarPrice p = ItemValue.bazaarPrice(id);
            if (p != null) return p;
        }
        for (String candidate : ItemNames.idsFor(product)) {
            BazaarPrice p = ItemValue.bazaarPrice(candidate);
            if (p != null) return p;
        }
        return null;
    }

    private static String idOfProduct(ItemStack stack, String name) {
        String id = SkyBlockItems.idOf(stack);
        if (id != null && ItemValue.bazaarPrice(id) != null) return id;
        for (String candidate : ItemNames.idsFor(name)) {
            if (ItemValue.bazaarPrice(candidate) != null) return candidate;
        }
        return null;
    }

    /** Das Fenster eines Produkts: Platz 10 "Buy Instantly", Platz 13 das Stueck - dann der Unterschied zwischen Sofortverkauf und Angebot */
    private static void readProduct(Minecraft client, List<Slot> slots) {
        sellDifference = null;
        if (slots.size() <= PRODUCT_SLOT) return;
        if (!clean(slots.get(BUY_INSTANTLY_SLOT).getItem().getHoverName().getString()).equals("Buy Instantly")) return;
        ItemStack product = slots.get(PRODUCT_SLOT).getItem();
        if (product.isEmpty()) return;
        String name = clean(product.getHoverName().getString());
        String id = idOfProduct(product, name);
        int having = amountOf(client, id, name);
        if (having <= 0) return;
        BazaarPrice price = id == null ? null : ItemValue.bazaarPrice(id);
        if (price == null) {
            sellDifference = "§cUnknown Bazaar item!";
            return;
        }
        sellDifference = sellDifferenceText(name, price.cheapestOffer(), price.highestBid(), having);
    }

    /** Wie viele davon im eigenen Inventar liegen - und in der Hand der Maus, denn ein eben angeklicktes Stueck liegt dort */
    private static int amountOf(Minecraft client, String id, String name) {
        if (client.player == null) return 0;
        int total = 0;
        Inventory inv = client.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) total += countIf(inv.getItem(i), id, name);
        if (client.player.containerMenu != null) total += countIf(client.player.containerMenu.getCarried(), id, name);
        return total;
    }

    private static int countIf(ItemStack stack, String id, String name) {
        if (stack.isEmpty()) return 0;
        String stackId = SkyBlockItems.idOf(stack);
        if (id != null && id.equals(stackId)) return stack.getCount();
        if (id == null && clean(stack.getHoverName().getString()).equalsIgnoreCase(name)) return stack.getCount();
        return 0;
    }

    /** "Order options": Platz 11 "Cancel Order" nennt, wie viele Stuecke noch fehlen */
    private static void readCancel(List<Slot> slots) {
        if (slots.size() <= CANCEL_SLOT) return;
        ItemStack cancel = slots.get(CANCEL_SLOT).getItem();
        if (cancel.isEmpty() || !clean(cancel.getHoverName().getString()).contains("Cancel Order")) return;
        int missing = missingAmount(SkyBlockItems.loreOf(cancel));
        if (missing >= 0) latestAmount = missing;
    }

    // ------------------------------------------------------------------ Chat

    /**
     * Eine Chatzeile. Gibt wahr zurueck, wenn es die Abbruchzeile von Hypixel war und diese Mod sie durch ihre eigene ersetzt hat - dann bleibt
     * die Originalzeile weg.
     */
    public static boolean onChatMessage(String plain) {
        if (!FeatureGate.bazaarHelpers() || plain == null || !GameState.Server.isSkyblock()) return false;
        if (plain.indexOf('[') < 0) return false;
        // Der Purchase helper geht aus, sobald das Stueck gekauft oder bestellt ist
        String item = transactionItem(plain);
        if (item != null) {
            if (searched != null && searched.equalsIgnoreCase(item)) searched = null;
            return false;
        }
        String refunded = refundedIn(plain);
        if (refunded == null || !cfg().cancelledBuyOrderClipboard) return false;
        if (latestAmount < 0 || optionProduct == null) return false;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return false;
        pending = new Reorder(optionProduct, latestAmount, refunded);
        client.keyboardHandler.setClipboard(Integer.toString(latestAmount));
        client.player.sendSystemMessage(reorderLine(pending));
        ShokiMod.LOGGER.info("[Bazaar] buy order cancelled: {}x {} - re-order offered", latestAmount, optionProduct);
        latestAmount = -1;
        return true;
    }

    /** Die Zeile mit dem Klick: "Bazaar buy order cancelled. Click to order it again." und darunter die Menge */
    static Component reorderLine(Reorder reorder) {
        MutableComponent head = Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal("Bazaar buy order cancelled. ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("[Click to order again]").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent.RunCommand("/shoki bzreorder"))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal(
                                        "Searches the bazaar for " + reorder.product() + " and keeps " + reorder.amount() + " in your clipboard")))));
        return head.append(Component.literal("\n  " + String.format(Locale.US, "%,d", reorder.amount()) + "x " + reorder.product()
                + " for " + reorder.refunded() + " coins").withStyle(ChatFormatting.GRAY));
    }

    /** Der Klick: das Stueck im Basar suchen, die Menge liegt schon in der Zwischenablage */
    public static void reorder() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.getConnection() == null) return;
        Reorder reorder = pending;
        if (reorder == null) {
            client.player.sendSystemMessage(Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA)
                    .append(Component.literal("Nothing to order - that line was used already.").withStyle(ChatFormatting.GRAY)));
            return;
        }
        client.keyboardHandler.setClipboard(Integer.toString(reorder.amount()));
        searched = reorder.product();
        client.getConnection().sendCommand("bazaar " + reorder.product());
        ShokiMod.LOGGER.info("[Bazaar] re-ordering {}x {}", reorder.amount(), reorder.product());
    }
}

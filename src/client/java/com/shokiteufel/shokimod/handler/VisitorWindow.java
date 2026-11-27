package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.garden.PlotGrid;
import com.shokiteufel.shokimod.garden.ShoppingListText;
import com.shokiteufel.shokimod.garden.VisitorBook;
import com.shokiteufel.shokimod.garden.VisitorDecision;
import com.shokiteufel.shokimod.garden.VisitorOffer;
import com.shokiteufel.shokimod.garden.VisitorReward;
import com.shokiteufel.shokimod.garden.VisitorTab;
import com.shokiteufel.shokimod.mixin.AbstractContainerScreenAccessor;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ScoreboardUtils;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Das Fenster eines Besuchers im Garden: Hier lernen die Einkaufsliste und die Belohnungswarnung, was ein Besucher will und bietet.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code VisitorListener}, {@code VisitorRewardWarning}, {@code GardenVisitorTooltip}). Von dort stammen die Regeln: Das Fenster
 * eines Besuchers erkennt man am Schild auf Platz 13 ("Offers Accepted: N") und am Knopf "Accept Offer" auf Platz 29; "Refuse Offer" liegt auf Platz 33. Beim Oeffnen
 * wird das Angebot gelesen; steht eine gewaehlte Belohnung darin, kommt einmal eine Chatzeile ("Found Visitor Reward ...!"). Ist ein Knopf gesperrt, wird ein Klick darauf
 * geschluckt, solange die Umgehungstaste nicht gehalten wird; der gesperrte Knopf ist abgedunkelt, der bessere hat einen gruenen oder roten Rand, und die Beschreibung
 * des gesperrten nennt den Grund. Die Umsetzung ist eigene Arbeit: Java, die Entscheidung ist rein ({@link VisitorDecision}), das Lesen des Fensters alle fuenf Ticks
 * statt bei einem Ereignis, der Klick kommt aus dem Ereignis der Fabric-API ({@code ScreenMouseEvents.allowMouseClick}) und die Tasten fuer Hotbar, Werfen und Tauschen
 * aus {@code ScreenKeyboardEvents.allowKeyPress} (es gibt kein Mixin ausser dem Zugriff auf Lage und Platz unter dem Zeiger); gezeichnet wird nach dem Fenster
 * ({@code ScreenEvents.afterExtract}). Welcher Besucher gemeint ist, sagt der Name auf dem Schild, nicht die Entitaet, die man angeklickt hat.
 */
public final class VisitorWindow {

    private static final int TICKS_BETWEEN_READS = 5;
    private static final int TICKS_BETWEEN_ZONE_READS = 20;
    private static final int TICKS_BETWEEN_PRICE_LOADS = 200;
    /** Die Deckkraft des Randes um den besseren Knopf (SkyHanni: 200) */
    private static final int OUTLINE_ALPHA = 200;
    private static final int DARK_GREY = 0x555555;
    private static final int GREEN = 0x55FF55;
    private static final int RED = 0xFF5555;

    private static Screen openScreen = null;
    private static String openName = null;
    private static int ticks = 0;
    private static int zoneTicks = 0;
    private static int priceTicks = TICKS_BETWEEN_PRICE_LOADS;
    private static String zone = null;
    private static boolean failedOnce = false;

    private VisitorWindow() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(VisitorWindow::tick);
        // Die Ereignisse eines Fensters werden bei jedem Aufbau neu angelegt: Jedes Mal wieder anmelden
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> onScreenInit(screen));
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> addBlockedLines(stack, lines));
    }

    private static ModConfig.GardenRewardWarningCategory cfg() {
        return ModConfig.INSTANCE.garden.rewardWarning;
    }

    private static void onScreenInit(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?>)) return;
        ScreenMouseEvents.allowMouseClick(screen).register(VisitorWindow::allowClick);
        ScreenKeyboardEvents.allowKeyPress(screen).register(VisitorWindow::allowKey);
        ScreenEvents.afterExtract(screen).register(VisitorWindow::afterExtract);
    }

    // ------------------------------------------------------------------ Tab-Liste

    /** Die Zeilen der Tab-Liste (ohne Farbcodes): die Namen der Besucher ins Buch - nur im Garden und nur, wenn das Widget "Visitors" in der Liste steht */
    public static void onTabList(List<String> lines) {
        if (!ShoppingListText.isGarden(GameState.Server.map)) return;
        VisitorTab.Result read = VisitorTab.read(lines);
        if (!read.found()) return;
        VisitorBook.INSTANCE.sync(read.names(), read.complete());
    }

    // ------------------------------------------------------------------ Tick

    private static void tick(Minecraft client) {
        if (!FeatureGate.visitorBook() || client.player == null || !GameState.Server.isSkyblock()) {
            leave();
            zone = null;
            return;
        }
        // Die Preislisten holen sich selbst nach, wenn sie alt sind; hier wird nur dafuer gesorgt, dass sie angefragt werden
        if (++priceTicks >= TICKS_BETWEEN_PRICE_LOADS) {
            priceTicks = 0;
            ItemValue.prefetch();
        }
        updateZone(client);
        if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            leave();
            return;
        }
        if (screen != openScreen) {
            openScreen = screen;
            openName = null;
            ticks = TICKS_BETWEEN_READS;
        }
        // Solange nicht klar ist, ob es das Fenster eines Besuchers ist, jeden Tick: Die Plaetze fuellen sich erst nach dem Oeffnen, und der erste Klick soll nicht
        // vor der ersten Lesung fallen. Ist es eines, genuegt alle fuenf Ticks (das Angebot aendert sich kaum)
        if (openName != null && ++ticks < TICKS_BETWEEN_READS) return;
        ticks = 0;
        try {
            read(client, screen);
        } catch (RuntimeException e) {
            openName = null;
            if (!failedOnce) {
                failedOnce = true;
                ShokiMod.LOGGER.warn("[Visitors] reading the visitor window failed: {}", e.toString());
            }
        }
    }

    private static void leave() {
        openScreen = null;
        openName = null;
        ticks = 0;
    }

    /** Die Zone aus der Seitenleiste, einmal je Sekunde - nur im Hub und nur, wenn die Liste dort stehen soll */
    private static void updateZone(Minecraft client) {
        ModConfig.GardenShoppingListCategory c = ModConfig.INSTANCE.garden.shoppingList;
        if (!c.enabled || !ShoppingListText.needsZone(c.inBazaarAlley, c.inFarmingAreas, GameState.Server.map)) {
            zone = null;
            zoneTicks = TICKS_BETWEEN_ZONE_READS;
            return;
        }
        if (++zoneTicks < TICKS_BETWEEN_ZONE_READS) return;
        zoneTicks = 0;
        zone = SoundMute.zoneOf(ScoreboardUtils.getSidebarLines(client));
    }

    /** Das Fenster lesen: Ist es das eines Besuchers, geht sein Angebot ins Buch */
    private static void read(Minecraft client, AbstractContainerScreen<?> screen) {
        List<Slot> slots = screen.getMenu().slots;
        if (slots.size() <= VisitorOffer.REFUSE_SLOT) {
            openName = null;
            return;
        }
        ItemStack info = slots.get(VisitorOffer.INFO_SLOT).getItem();
        ItemStack accept = slots.get(VisitorOffer.ACCEPT_SLOT).getItem();
        // Zuerst das Billige: der Name des Knopfes. Jede Kiste wird so je Tick nur mit einem Vergleich abgetan
        if (info.isEmpty() || accept.isEmpty() || VisitorDecision.Button.of(accept.getHoverName().getString()) != VisitorDecision.Button.ACCEPT) {
            openName = null;
            return;
        }
        List<String> infoLore = SkyBlockItems.loreOf(info);
        if (!VisitorOffer.isInfoLore(infoLore)) {
            openName = null;
            return;
        }
        String name = BazaarHelpers.clean(info.getHoverName().getString());
        if (name.isEmpty()) {
            openName = null;
            return;
        }

        ModConfig.GardenRewardWarningCategory c = cfg();
        VisitorBook.Entry entry = VisitorBook.INSTANCE.getOrCreate(name);
        entry.offersAccepted(VisitorOffer.offersAccepted(infoLore));
        List<String> offerLore = SkyBlockItems.loreOf(accept);
        List<String> namesBefore = entry.rewardNames();
        List<VisitorReward> fresh = entry.applyOffer(offerLore, VisitorOffer.parse(offerLore), VisitorPrices::buyPrice, VisitorPrices.copperValue(), c);
        entry.decide(VisitorDecision.rulesOf(c));
        // Einmal je Angebot ins Log: was als Belohnung gelesen wurde und welche davon gewaehlt sind. Steht dort "Visitors' Gratitude" und bei gewarnt nichts, passt der
        // Name nicht zur Auswahl (oder der Schalter ist aus) - die Zeile, die man braucht, wenn eine Belohnung nicht sperrt
        if (!entry.rewardNames().equals(namesBefore)) {
            ShokiMod.LOGGER.info("[Visitors] {}: rewards read {} - warned {}, block {}", name, entry.rewardNames(), entry.warned(), entry.block());
        }
        openName = name;
        if (c.notifyInChat && !fresh.isEmpty()) announce(client, fresh);
    }

    /** "Found Visitor Reward X!" - eine Zeile je Belohnung, die man gewaehlt hat */
    private static void announce(Minecraft client, List<VisitorReward> rewards) {
        for (VisitorReward reward : rewards) {
            client.player.sendSystemMessage(Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA)
                    .append(Component.literal("Found Visitor Reward ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(reward.displayName()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("!").withStyle(ChatFormatting.GRAY)));
        }
    }

    // ------------------------------------------------------------------ Fragen an den Zustand

    /** Ist gerade das Fenster eines Besuchers offen? */
    public static boolean isOpen() {
        return openName != null;
    }

    /** Die Zone aus der Seitenleiste (nur im Hub gelesen), sonst null */
    static String zone() {
        return zone;
    }

    /** Steht die Einkaufsliste an dieser Stelle, vor diesem Fenster? (Ohne den Schalter fragt niemand) */
    public static boolean listShownHere() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return false;
        if (!screenAllowsList(client.gui.screen())) return false;
        ModConfig.GardenShoppingListCategory c = ModConfig.INSTANCE.garden.shoppingList;
        String map = GameState.Server.map;
        boolean onBarn = ShoppingListText.isGarden(map) && PlotGrid.onBarnPlot(client.player.getX(), client.player.getZ());
        return ShoppingListText.locationAllows(c.inBazaarAlley, c.inFarmingAreas, c.onlyWhenClose, map, zone, onBarn);
    }

    /**
     * Hinter welchem Fenster steht die Liste? Ohne Fenster, bei offenem Chat, im eigenen Inventar, im Fenster eines Besuchers und in dem des Basars (dort kauft man,
     * was auf ihr steht); ein anderer Behaelter - eine Kiste, ein Menue - bekommt sie nicht.
     */
    static boolean screenAllowsList(Screen screen) {
        if (screen == null || screen instanceof ChatScreen || screen instanceof InventoryScreen) return true;
        if (screen instanceof AbstractContainerScreen<?>) {
            return screen == openScreen && openName != null || BazaarHelpers.isBazaarTitle(BazaarHelpers.clean(screen.getTitle().getString()));
        }
        return true;
    }

    /** Ist die Umgehungstaste unten? Ohne gewaehlte Taste nie */
    static boolean bypassHeld() {
        int key = cfg().bypassKey;
        return key >= 32 && ShortcutHandler.isDown(key);
    }

    private static Slot hoveredSlot(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?>)) return null;
        Slot slot = ((AbstractContainerScreenAccessor) screen).shokimod$hoveredSlot();
        return slot == null || slot.container instanceof Inventory ? null : slot;
    }

    /** Der Besucher des offenen Fensters, wenn der Klick in genau diesem Fenster fiel */
    private static VisitorBook.Entry current(Screen screen) {
        if (screen != openScreen || openName == null) return null;
        return VisitorBook.INSTANCE.get(openName);
    }

    // ------------------------------------------------------------------ Klick, Tasten, Zeichnen

    /** Ein Klick im Fenster: der gesperrte Knopf bekommt ihn nicht; sonst merkt sich das Buch, was aus dem Besucher wird */
    private static boolean allowClick(Screen screen, MouseButtonEvent click) {
        if (!FeatureGate.visitorBook()) return true;
        try {
            VisitorBook.Entry entry = current(screen);
            Slot slot = entry == null ? null : hoveredSlot(screen);
            if (slot == null) return true;
            VisitorDecision.Button button = VisitorDecision.Button.of(slot.getItem().getHoverName().getString());
            if (button == VisitorDecision.Button.OTHER) return true;
            if (VisitorDecision.blocks(entry.block(), button, bypassHeld())) return false;
            // Mit Umschalt ist es ein Schnellverschieben, keine Antwort an den Besucher (SkyHanni: QUICK_MOVE)
            if (!click.hasShiftDown()) {
                if (button == VisitorDecision.Button.REFUSE) entry.status(VisitorBook.Status.REFUSED);
                else if (entry.status() == VisitorBook.Status.READY) entry.status(VisitorBook.Status.ACCEPTED);
            }
        } catch (RuntimeException e) {
            warnOnce(e);
        }
        return true;
    }

    /**
     * Eine Taste im Fenster: Solange die Maus auf dem gesperrten Knopf liegt, tun auch die Tasten nichts, die einen Klick darauf ersetzen - die Hotbar-Tasten, Werfen
     * und Tauschen mit der zweiten Hand (der Server liest sie als Klick auf den Platz).
     */
    private static boolean allowKey(Screen screen, KeyEvent key) {
        if (!FeatureGate.visitorBlocking()) return true;
        try {
            VisitorBook.Entry entry = current(screen);
            if (entry == null || entry.block() == null) return true;
            Slot slot = hoveredSlot(screen);
            if (slot == null) return true;
            VisitorDecision.Button button = VisitorDecision.Button.of(slot.getItem().getHoverName().getString());
            if (!VisitorDecision.blocks(entry.block(), button, bypassHeld())) return true;
            Options options = Minecraft.getInstance().options;
            if (options.keySwapOffhand.matches(key) || options.keyDrop.matches(key)) return false;
            for (KeyMapping hotbar : options.keyHotbarSlots) {
                if (hotbar.matches(key)) return false;
            }
        } catch (RuntimeException e) {
            warnOnce(e);
        }
        return true;
    }

    /** Nach dem Fenster: den gesperrten Knopf abdunkeln und den besseren umranden */
    private static void afterExtract(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        if (!FeatureGate.visitorBlocking()) return;
        try {
            VisitorBook.Entry entry = current(screen);
            if (entry == null || entry.block() == null || !(screen instanceof AbstractContainerScreen<?> container)) return;
            ModConfig.GardenRewardWarningCategory c = cfg();
            List<Slot> slots = container.getMenu().slots;
            AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
            int left = accessor.shokimod$leftPos();
            int top = accessor.shokimod$topPos();
            int blocked = VisitorDecision.blockedSlot(entry.block());
            int best = VisitorDecision.bestSlot(entry.block());
            if (!bypassHeld() && blocked >= 0 && blocked < slots.size()) {
                Slot slot = slots.get(blocked);
                int x = left + slot.x;
                int y = top + slot.y;
                graphics.fill(x, y, x + 16, y + 16, (c.transparency << 24) | DARK_GREY);
            }
            if (c.optionOutline && best >= 0 && best < slots.size()) {
                Slot slot = slots.get(best);
                int colour = (OUTLINE_ALPHA << 24) | (entry.block().blockRefusing() ? GREEN : RED);
                border(graphics, left + slot.x, top + slot.y, colour);
            }
        } catch (RuntimeException e) {
            warnOnce(e);
        }
    }

    /** Ein Rand, einen Punkt breit, um einen Platz (sechzehn mal sechzehn) */
    private static void border(GuiGraphicsExtractor graphics, int x, int y, int colour) {
        graphics.fill(x, y, x + 16, y + 1, colour);
        graphics.fill(x, y + 15, x + 16, y + 16, colour);
        graphics.fill(x, y + 1, x + 1, y + 15, colour);
        graphics.fill(x + 15, y + 1, x + 16, y + 15, colour);
    }

    /** Die Beschreibung des gesperrten Knopfes: der Grund und wie man ihn umgeht */
    private static void addBlockedLines(ItemStack stack, List<Component> lines) {
        if (openName == null || lines == null || stack == null || !FeatureGate.visitorBlocking()) return;
        VisitorDecision.Button button = VisitorDecision.Button.of(stack.getHoverName().getString());
        if (button == VisitorDecision.Button.OTHER) return;
        VisitorBook.Entry entry = VisitorBook.INSTANCE.get(openName);
        if (entry == null || !VisitorDecision.blocks(entry.block(), button, bypassHeld())) return;
        lines.add(Component.literal(""));
        lines.add(Component.literal("Blocked: " + VisitorDecision.describe(entry.block(), entry.pricePerCopper(),
                entry.totalPrice() - entry.totalReward(), ItemValue::format)).withStyle(ChatFormatting.RED));
        lines.add(Component.literal("(Bypass by holding " + ShortcutHandler.keyName(cfg().bypassKey) + ")").withStyle(ChatFormatting.GRAY));
    }

    private static void warnOnce(RuntimeException e) {
        if (failedOnce) return;
        failedOnce = true;
        ShokiMod.LOGGER.warn("[Visitors] the visitor window handler failed: {}", e.toString());
    }
}

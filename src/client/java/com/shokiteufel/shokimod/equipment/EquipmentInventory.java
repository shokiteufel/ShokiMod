package com.shokiteufel.shokimod.equipment;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.mixin.AbstractContainerScreenAccessor;
import com.shokiteufel.shokimod.scanner.PetState;
import com.shokiteufel.shokimod.util.ModPaths;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * UI & Visuals > Skyblock Inventory Screen > Show Equipment in Inventory: die vier Equipment-Plaetze neben dem Inventarfenster (Taste E), mit dem, was man angelegt hat.
 *
 * Herkunft der Idee: Skyblocker (LGPL-3.0, {@code SkyblockInventoryScreen}, {@code SkyBlockEquipmentUpdater}, {@code InventoryScreenMixin}, {@code MinecraftMixin}).
 * Von dort stammt die Regel: Das Equipment steht als Anzeige im Inventarfenster und ist ein Zwischenspeicher - gefuellt wird er, wenn man das Menue /equipment
 * (oder den Kleiderschrank "Equipment Sets", oder "Loadouts") oeffnet; er ist nicht in Echtzeit. Ein Klick auf einen Platz oeffnet /equipment (in der Rift und in der
 * Safari, wo es den Kleiderschrank nicht gibt, /stats); der Tooltip des Platzes ist der des Gegenstandes. Wie gelesen wird, steht in {@link EquipmentRules}, was
 * gemerkt wird, in {@link EquipmentStore}. Die Umsetzung ist eigene Arbeit: Java; Skyblocker ersetzt das ganze Inventarfenster durch ein eigenes ({@code Minecraft.handleKeybinds}
 * baut statt des {@code InventoryScreen} den eigenen, ein Mixin verschiebt dazu Platz 45 und den Knopf des Rezeptbuchs). Das tut diese Mod nicht: Das Fenster bleibt das
 * des Spiels, und die Plaetze stehen in einem schmalen Streifen links daneben ({@link EquipmentLayout}), gezeichnet nach dem Fenster ({@code ScreenEvents.afterExtract}),
 * der Klick kommt aus {@code ScreenMouseEvents.allowMouseClick}. Es gibt kein neues Mixin (nur der Zugriff auf Lage des Fensters und Platz unter dem Zeiger, den es
 * schon gibt): Ein Fehler hier kann den Start nicht verhindern.
 *
 * <p>Abweichungen: Keine Symbole fuer leere Plaetze (der Streifen zeigt sie leer; der Tooltip nennt den Platz), kein Eintrag fuer das Wiki-Nachschlagen, und kein Schalter
 * "Open Equipment To '/stats'" (der gehoert bei Skyblocker nicht zu diesem Schalter). Ein Klick im Streifen schickt den Befehl hoechstens alle 0,4 Sekunden.
 */
public final class EquipmentInventory {

    /** Alle so viele Ticks wird das offene Fenster gelesen (zwei Mal in der Sekunde): Die Plaetze fuellen sich erst nach dem Oeffnen, und aendern sich kaum */
    static final int READ_EVERY_TICKS = 10;
    /** Nach einem Abnehmen des Satzes im Kleiderschrank wartet das Lesen so lange, bis der Server das Fenster nachgezogen hat */
    static final int PAUSE_AFTER_TAKE_OFF_TICKS = 30;
    private static final long CLICK_GAP_MILLIS = 400L;

    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
    private static final Identifier HIGHLIGHT_BACK = Identifier.withDefaultNamespace("container/slot_highlight_back");
    private static final Identifier HIGHLIGHT_FRONT = Identifier.withDefaultNamespace("container/slot_highlight_front");
    /** Die Farben des Streifens: wie die Flaeche der Fenster des Spiels (schwarzer Rand, hell, hell oben und links, dunkel unten und rechts) */
    private static final int BORDER = 0xFF000000;
    private static final int FACE = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;

    /** Was auf einem leeren Platz steht, wenn man mit der Maus darueber faehrt: einmal gebaut, nie je Bild */
    private static final Component[] EMPTY_TIPS = new Component[EquipmentRules.SLOTS];

    static {
        for (int i = 0; i < EquipmentRules.SLOTS; i++) {
            EMPTY_TIPS[i] = Component.literal(EquipmentRules.SLOT_NAMES[i]).append(Component.literal(" (empty)").withStyle(ChatFormatting.GRAY));
        }
    }

    private static EquipmentStore store = null;
    private static Screen watched = null;
    private static EquipmentRules.Window window = EquipmentRules.Window.NONE;
    private static int ticks = 0;
    private static long lastClickAt = 0L;
    private static boolean failedOnce = false;

    private EquipmentInventory() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(EquipmentInventory::tick);
        // Die Ereignisse eines Fensters werden bei jedem Aufbau neu angelegt: Jedes Mal wieder anmelden
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> onScreenInit(screen));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (store != null) store.saveIfDirty();
        });
    }

    private static void onScreenInit(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?>)) return;
        ScreenMouseEvents.allowMouseClick(screen).register(EquipmentInventory::allowClick);
        if (screen instanceof InventoryScreen) ScreenEvents.afterExtract(screen).register(EquipmentInventory::afterExtract);
    }

    /** Laeuft die Funktion jetzt? Eingeschaltet und in SkyBlock - sonst zeichnet und liest die Mod auf anderen Servern */
    private static boolean active() {
        return FeatureGate.equipmentInInventory() && GameState.Server.isSkyblock();
    }

    private static EquipmentStore store() {
        if (store == null) store = new EquipmentStore(ModPaths.resolve("equipment.json"));
        return store;
    }

    private static HolderLookup.Provider lookup(Minecraft client) {
        return client.getConnection() == null ? null : client.getConnection().registryAccess();
    }

    // ------------------------------------------------------------------ Lesen

    private static void tick(Minecraft client) {
        if (!active() || client.player == null) {
            endWatch();
            return;
        }
        Screen screen = client.gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?> container) || screen instanceof InventoryScreen) {
            endWatch();
            return;
        }
        if (screen != watched) {
            endWatch();
            watched = screen;
            window = EquipmentRules.windowOf(screen.getTitle().getString());
            ticks = READ_EVERY_TICKS;
        }
        if (window == EquipmentRules.Window.NONE || ++ticks < READ_EVERY_TICKS) return;
        ticks = 0;
        try {
            read(client, container);
        } catch (RuntimeException e) {
            warnOnce("reading the equipment window failed", e);
        }
    }

    /** Das beobachtete Fenster ist zu (oder die Funktion aus): vergessen, und was sich geaendert hat, auf die Platte */
    private static void endWatch() {
        if (watched == null) return;
        watched = null;
        window = EquipmentRules.Window.NONE;
        if (store != null) store.saveIfDirty();
    }

    private static EquipmentRules.Kind kindOf(ItemStack stack) {
        if (stack.isEmpty()) return EquipmentRules.Kind.AIR;
        if (stack.is(Items.STAINED_GLASS_PANE.black())) return EquipmentRules.Kind.BLACK_PANE;
        if (stack.is(Items.DYE.lime())) return EquipmentRules.Kind.LIME_DYE;
        return EquipmentRules.Kind.OTHER;
    }

    /** Die Plaetze des Fensters ohne den eigenen Rucksack unten, als Zellen fuer die Regeln */
    private static List<EquipmentRules.Cell> cellsOf(Minecraft client, List<Slot> slots) {
        Inventory own = client.player.getInventory();
        List<EquipmentRules.Cell> cells = new ArrayList<>(slots.size());
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.container == own) continue;
            ItemStack stack = slot.getItem();
            cells.add(EquipmentRules.cell(slot.index, kindOf(stack), stack.isEmpty() ? "" : stack.getHoverName().getString()));
        }
        return cells;
    }

    private static void read(Minecraft client, AbstractContainerScreen<?> screen) {
        HolderLookup.Provider lookup = lookup(client);
        if (lookup == null) return;
        List<Slot> slots = screen.getMenu().slots;
        List<EquipmentRules.Cell> cells = cellsOf(client, slots);
        List<EquipmentRules.Pick> picks = EquipmentRules.equipmentSlots(cells, EquipmentRules.columnOf(window, cells));
        if (picks == null) return;
        ItemStack[] read = new ItemStack[EquipmentRules.SLOTS];
        for (int i = 0; i < EquipmentRules.SLOTS; i++) {
            EquipmentRules.Pick pick = picks.get(i);
            if (pick.empty()) {
                read[i] = ItemStack.EMPTY;
                continue;
            }
            // Die Nummer eines Platzes ist seine Stelle in der Liste; stimmt das ausnahmsweise nicht, wird nichts gemerkt
            if (pick.index() < 0 || pick.index() >= slots.size() || slots.get(pick.index()).index != pick.index()) return;
            read[i] = slots.get(pick.index()).getItem();
        }
        // Im Kleiderschrank gilt immer der gewoehnliche Satz (in der Rift und der Safari gibt es ihn nicht); sonst der des Gebiets
        EquipmentSet set = window == EquipmentRules.Window.SETS ? EquipmentSet.NORMAL : EquipmentSet.forMap(GameState.Server.map);
        store().put(PetState.profileId(), set, read, lookup);
    }

    // ------------------------------------------------------------------ Klick

    private static boolean allowClick(Screen screen, MouseButtonEvent click) {
        if (!active()) return true;
        try {
            if (screen instanceof InventoryScreen) return !panelClick(screen, click);
            takeSetOff(screen);
        } catch (RuntimeException e) {
            warnOnce("handling a click failed", e);
        }
        return true;
    }

    /**
     * Ein Klick im Inventarfenster: Liegt er im Streifen, ist er hier erledigt und das Fenster bekommt ihn nicht - es sieht einen Klick links neben seinem Rand als
     * "ausserhalb" und liesse den Gegenstand am Zeiger fallen. Auf einem Platz kommt der Befehl dazu.
     *
     * @return ob der Klick im Streifen lag
     */
    private static boolean panelClick(Screen screen, MouseButtonEvent click) {
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
        int panelX = EquipmentLayout.panelX(accessor.shokimod$leftPos(), screen.width);
        if (panelX == EquipmentLayout.NO_ROOM) return false;
        int panelY = EquipmentLayout.panelY(accessor.shokimod$topPos());
        if (!EquipmentLayout.inPanel(panelX, panelY, click.x(), click.y())) return false;
        if (EquipmentLayout.slotAt(panelX, panelY, click.x(), click.y()) >= 0) sendCommand();
        return true;
    }

    private static void sendCommand() {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) return;
        long now = System.currentTimeMillis();
        if (now - lastClickAt < CLICK_GAP_MILLIS) return;
        lastClickAt = now;
        client.getConnection().sendCommand(EquipmentRules.clickCommand(EquipmentSet.forMap(GameState.Server.map)));
    }

    /** Im Kleiderschrank: Ein Klick auf das Zeichen des gewaehlten Satzes nimmt ihn ab - dann ist nichts mehr angelegt */
    private static void takeSetOff(Screen screen) {
        if (screen != watched || window != EquipmentRules.Window.SETS) return;
        Slot slot = ((AbstractContainerScreenAccessor) screen).shokimod$hoveredSlot();
        Minecraft client = Minecraft.getInstance();
        if (slot == null || client.player == null || slot.container == client.player.getInventory()) return;
        if (!EquipmentRules.takesSetOff(window, slot.index, kindOf(slot.getItem()))) return;
        HolderLookup.Provider lookup = lookup(client);
        if (lookup == null) return;
        store().clear(PetState.profileId(), EquipmentSet.NORMAL, lookup);
        // Der Server zieht das Fenster erst nach: Bis dahin zeigt es den Satz noch, und eine Lesung brachte ihn zurueck
        ticks = -PAUSE_AFTER_TAKE_OFF_TICKS;
    }

    // ------------------------------------------------------------------ Zeichnen

    /** Nach dem Inventarfenster: der Streifen mit den vier Plaetzen. Im Bild wird nichts angelegt: Zahlen, Farben und Texte stehen fertig da */
    private static void afterExtract(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        if (!active()) return;
        try {
            AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
            int panelX = EquipmentLayout.panelX(accessor.shokimod$leftPos(), screen.width);
            if (panelX == EquipmentLayout.NO_ROOM) return;
            int panelY = EquipmentLayout.panelY(accessor.shokimod$topPos());
            Minecraft client = Minecraft.getInstance();
            ItemStack[] items = store().get(PetState.profileId(), EquipmentSet.forMap(GameState.Server.map), lookup(client));
            int hovered = EquipmentLayout.slotAt(panelX, panelY, mouseX, mouseY);

            drawPanel(graphics, panelX, panelY);
            for (int i = 0; i < EquipmentRules.SLOTS; i++) {
                int x = EquipmentLayout.slotX(panelX);
                int y = EquipmentLayout.slotY(panelY, i);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, x, y, EquipmentLayout.SLOT, EquipmentLayout.SLOT);
                if (i == hovered) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_BACK, x - 3, y - 3, 24, 24);
                if (!items[i].isEmpty()) graphics.item(items[i], x + 1, y + 1);
                if (i == hovered) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_FRONT, x - 3, y - 3, 24, 24);
            }
            if (hovered >= 0) {
                // Der Tooltip des Gegenstandes ist der des Spiels (mit den Zeilen, die andere Funktionen dazugeben); ein leerer Platz nennt nur seinen Namen
                if (items[hovered].isEmpty()) graphics.setTooltipForNextFrame(client.font, EMPTY_TIPS[hovered], mouseX, mouseY);
                else graphics.setTooltipForNextFrame(client.font, items[hovered], mouseX, mouseY);
            }
        } catch (RuntimeException e) {
            warnOnce("drawing the equipment slots failed", e);
        }
    }

    /** Die Flaeche des Streifens wie die der Fenster des Spiels: schwarzer Rand, hellgrau, oben und links hell, unten und rechts dunkel */
    private static void drawPanel(GuiGraphicsExtractor graphics, int x, int y) {
        int w = EquipmentLayout.PANEL_WIDTH;
        int h = EquipmentLayout.PANEL_HEIGHT;
        graphics.fill(x, y, x + w, y + h, BORDER);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, FACE);
        graphics.fill(x + 1, y + 1, x + w - 2, y + 3, LIGHT);
        graphics.fill(x + 1, y + 1, x + 3, y + h - 2, LIGHT);
        graphics.fill(x + 3, y + h - 3, x + w - 1, y + h - 1, SHADOW);
        graphics.fill(x + w - 3, y + 3, x + w - 1, y + h - 1, SHADOW);
    }

    private static void warnOnce(String what, RuntimeException e) {
        if (failedOnce) return;
        failedOnce = true;
        ShokiMod.LOGGER.warn("[Equipment] {}: {}", what, e.toString());
    }
}

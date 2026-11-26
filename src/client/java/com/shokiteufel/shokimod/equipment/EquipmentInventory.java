package com.shokiteufel.shokimod.equipment;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.mixin.AbstractContainerScreenAccessor;
import com.shokiteufel.shokimod.paket.mixin.SlotXAccessor;
import com.shokiteufel.shokimod.scanner.PetState;
import com.shokiteufel.shokimod.util.ModPaths;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
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
 * des Spiels. Die vier Plaetze stehen im Fenster rechts neben der Spielfigur ({@link EquipmentLayout}); gezeichnet werden sie von einem Einsprung in
 * {@code InventoryScreen.extractLabels} ({@link com.shokiteufel.shokimod.paket.mixin.InventoryEquipmentMixin}), nach dem Hintergrund und vor den Gegenstaenden des
 * Fensters. Der Zweithand-Platz rueckt dazu nach rechts (Zugriff {@link com.shokiteufel.shokimod.paket.mixin.SlotXAccessor}), der Knopf des Rezeptbuchs auch (er ist ein
 * Bauteil des Fensters, seine Lage wird vor jedem Bild gesetzt). Der Klick kommt aus {@code ScreenMouseEvents.allowMouseClick}. Beide Mixins stehen in der nicht
 * erforderlichen Konfiguration ({@code shokimod-pakete.mixins.json}): Ein Fehler hier kann den Start nicht verhindern, es fehlt dann nur die Anzeige.
 *
 * <p>Die Farben macht das Spiel: Der Fensterhintergrund und der Rahmen ({@code container/slot}) kommen aus dem gewaehlten Aussehen (Texturpaket, dunkle oder helle
 * Oberflaeche); die Mod legt nur die Farbe des Sechsecks auf leeren Plaetzen fest, halbdurchsichtig, damit es auf hellem und dunklem Grund zu sehen ist.
 *
 * <p>Abweichungen: Ein schlichtes Sechseck statt eines Bildes je Platz, kein Eintrag fuer das Wiki-Nachschlagen, und kein Schalter "Open Equipment To '/stats'" (der gehoert
 * bei Skyblocker nicht zu diesem Schalter). Ein Klick auf einen Platz schickt den Befehl hoechstens alle 0,4 Sekunden. Gilt nur fuer das Inventarfenster des Spiels selbst
 * (nicht fuer ein Fenster, das von ihm erbt, wie das von Skyblocker: dort stuende alles doppelt).
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
    /** Das Sechseck auf einem leeren Platz: mittelgrau, zu sechzig vom Hundert deckend - auf dunklem Grund heller, auf hellem dunkler als der Platz selbst */
    private static final int EMPTY_OUTLINE = 0x99B8B8B8;

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
    /** Ging der Zugriff auf die Lage der Plaetze schief (der Mixin fehlt)? Dann bleibt die Anzeige fuer die Sitzung aus, statt halb zu stehen */
    private static boolean broken = false;
    /** Das Inventarfenster, in dem die Plaetze zuletzt gezeichnet wurden: Nur dort fangen sie Klicks ab, nur dort ist der Zweithand-Platz verschoben */
    private static InventoryScreen placedFor = null;
    /** Der verschobene Zweithand-Platz (null: nicht verschoben) und der verschobene Knopf des Rezeptbuchs (null: nicht gefunden oder nicht verschoben) */
    private static Slot movedOffhand = null;
    private static AbstractWidget movedButton = null;

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
        // Beim Schliessen kommt alles an seinen Platz: Das Inventar des Spielers (und mit ihm der Zweithand-Platz) lebt weiter, das Fenster nicht
        if (screen instanceof InventoryScreen) {
            ScreenEvents.remove(screen).register(closed -> restoreLayout());
            ScreenEvents.afterExtract(screen).register(EquipmentInventory::afterExtract);
        }
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
        // Falls das Schliessen des Fensters die Rueckmeldung nicht gebracht hat: Ist kein Inventarfenster mehr offen, kommt der Zweithand-Platz an seinen Ort zurueck
        if (placedFor != null && !(client.gui.screen() instanceof InventoryScreen)) restoreLayout();
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
            // Im Inventarfenster zaehlt nur, was die Mod selbst gezeichnet hat: Steht nichts da (Mixin fehlt, anderes Fenster), geht der Klick ans Spiel
            if (screen instanceof InventoryScreen) return !(screen == placedFor && equipmentClick(screen, click));
            takeSetOff(screen);
        } catch (RuntimeException e) {
            warnOnce("handling a click failed", e);
        }
        return true;
    }

    /**
     * Ein Klick im Inventarfenster: Liegt er auf einem der vier Plaetze, ist er hier erledigt (das Fenster bekommt ihn nicht) und der Befehl geht los.
     *
     * @return ob der Klick auf einem Platz lag
     */
    private static boolean equipmentClick(Screen screen, MouseButtonEvent click) {
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
        if (EquipmentLayout.slotAt(accessor.shokimod$leftPos(), accessor.shokimod$topPos(), click.x(), click.y()) < 0) return false;
        sendCommand();
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

    /**
     * Vom Einsprung in {@code InventoryScreen.extractLabels} aus, in jedem Bild: Rahmen und Inhalt der vier Plaetze im Fenster. Die Zeichenflaeche ist hier schon auf die
     * linke obere Ecke des Fensters verschoben (die Zahlen aus {@link EquipmentLayout} gelten also direkt); Maus und Fenster-Ecke kommen in Bildschirmkoordinaten.
     * Im Bild wird nichts angelegt: Zahlen, Farben und Texte stehen fertig da.
     */
    public static void drawInWindow(InventoryScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (screen.getClass() != InventoryScreen.class) return;
        if (broken || !active()) {
            restoreLayout();
            return;
        }
        try {
            AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
            int left = accessor.shokimod$leftPos();
            int top = accessor.shokimod$topPos();
            if (!placeLayout(screen, left, top)) return;
            ItemStack[] items = store().get(PetState.profileId(), EquipmentSet.forMap(GameState.Server.map), lookup(Minecraft.getInstance()));
            int hovered = EquipmentLayout.slotAt(left, top, mouseX, mouseY);

            int x = EquipmentLayout.FRAME_X;
            for (int i = 0; i < EquipmentRules.SLOTS; i++) {
                int y = EquipmentLayout.FRAME_Y + i * EquipmentLayout.SLOT;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, x, y, EquipmentLayout.SLOT, EquipmentLayout.SLOT);
                if (i == hovered) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_BACK, x - 3, y - 3, 24, 24);
                if (items[i].isEmpty()) drawHexagon(graphics, x + 1, y + 1);
                else graphics.item(items[i], x + 1, y + 1);
                if (i == hovered) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_FRONT, x - 3, y - 3, 24, 24);
            }
            // Der Zweithand-Platz hat an seinem neuen Ort keinen Rahmen im Hintergrund des Fensters: hier unter seinem Gegenstand (der kommt danach)
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, EquipmentLayout.movedOffhandFrameX(), EquipmentLayout.offhandFrameY(),
                    EquipmentLayout.SLOT, EquipmentLayout.SLOT);
        } catch (RuntimeException e) {
            warnOnce("drawing the equipment slots failed", e);
        }
    }

    /** Das Sechseck eines leeren Platzes: x und y sind die linke obere Ecke des Gegenstandsfeldes */
    private static void drawHexagon(GuiGraphicsExtractor graphics, int x, int y) {
        for (int[] part : EquipmentLayout.HEXAGON) {
            graphics.fill(x + part[0], y + part[1], x + part[0] + part[2], y + part[1] + part[3], EMPTY_OUTLINE);
        }
    }

    /**
     * Zweithand-Platz und Knopf des Rezeptbuchs an ihre neuen Orte (jedes Bild, aber ohne Folgen, wenn schon geschehen: der Knopf stellt das Spiel beim Auf- und Zuklappen
     * des Buches wieder an seine alte Stelle).
     *
     * @return ob alles steht und gezeichnet werden darf
     */
    private static boolean placeLayout(InventoryScreen screen, int left, int top) {
        Slot shield = screen.getMenu().getSlot(InventoryMenu.SHIELD_SLOT);
        if (shield != movedOffhand) {
            // Steht der Platz nicht dort, wo ihn das Spiel hinsetzt, hat ihn schon jemand verschoben: Dann bleibt alles, wie es ist
            if (shield.x != EquipmentLayout.OFFHAND_X) return false;
            try {
                ((SlotXAccessor) (Object) shield).shokimod$setX(EquipmentLayout.OFFHAND_X + EquipmentLayout.SHIFT);
            } catch (ClassCastException e) {
                broken = true;
                warnOnce("moving the off-hand slot failed (mixin missing), the equipment slots stay off", e);
                return false;
            }
            movedOffhand = shield;
        }
        placedFor = screen;
        if (movedButton != null && !screen.children().contains(movedButton)) movedButton = null;
        if (movedButton == null) {
            for (GuiEventListener child : screen.children()) {
                if (child instanceof Button button && button.getX() == left + EquipmentLayout.BUTTON_X
                        && Math.abs(button.getY() - (top + EquipmentLayout.BUTTON_Y)) <= 2) {
                    movedButton = button;
                    break;
                }
            }
        }
        if (movedButton != null) movedButton.setX(left + EquipmentLayout.BUTTON_X + EquipmentLayout.SHIFT);
        return true;
    }

    /** Alles zurueck an den Ort des Spiels: beim Schliessen des Fensters, beim Ausschalten und wenn der Zugriff nicht geht */
    private static void restoreLayout() {
        Slot shield = movedOffhand;
        AbstractWidget button = movedButton;
        InventoryScreen screen = placedFor;
        movedOffhand = null;
        movedButton = null;
        placedFor = null;
        try {
            if (shield != null) ((SlotXAccessor) (Object) shield).shokimod$setX(EquipmentLayout.OFFHAND_X);
            if (button != null && screen != null) button.setX(((AbstractContainerScreenAccessor) screen).shokimod$leftPos() + EquipmentLayout.BUTTON_X);
        } catch (RuntimeException e) {
            warnOnce("putting the off-hand slot back failed", e);
        }
    }

    /** Nach dem Fenster: der Tooltip des Platzes unter dem Zeiger (er gehoert nicht in die verschobene Zeichenflaeche des Fensters) */
    private static void afterExtract(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        if (screen != placedFor || !active()) return;
        try {
            // Mit einem Gegenstand am Zeiger zeigt das Spiel auch sonst keinen Tooltip
            if (!((InventoryScreen) screen).getMenu().getCarried().isEmpty()) return;
            AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
            int hovered = EquipmentLayout.slotAt(accessor.shokimod$leftPos(), accessor.shokimod$topPos(), mouseX, mouseY);
            if (hovered < 0) return;
            Minecraft client = Minecraft.getInstance();
            ItemStack[] items = store().get(PetState.profileId(), EquipmentSet.forMap(GameState.Server.map), lookup(client));
            // Der Tooltip des Gegenstandes ist der des Spiels (mit den Zeilen, die andere Funktionen dazugeben); ein leerer Platz nennt nur seinen Namen
            if (items[hovered].isEmpty()) graphics.setTooltipForNextFrame(client.font, EMPTY_TIPS[hovered], mouseX, mouseY);
            else graphics.setTooltipForNextFrame(client.font, items[hovered], mouseX, mouseY);
        } catch (RuntimeException e) {
            warnOnce("drawing the equipment tooltip failed", e);
        }
    }

    private static void warnOnce(String what, RuntimeException e) {
        if (failedOnce) return;
        failedOnce = true;
        ShokiMod.LOGGER.warn("[Equipment] {}: {}", what, e.toString());
    }
}

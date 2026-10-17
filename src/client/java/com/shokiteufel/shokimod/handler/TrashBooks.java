package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.TrashBooksCategory;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Markiert die verzauberten Buecher im Inventar, die man loswerden will.
 *
 * <p>Nachgebaut aus Feesh (Apache 2.0, Sleepy-Panda/Feesh, {@code TrashBooksHighlighter}): Beim Angeln
 * fluten Buecher das Inventar, und man will schnell die finden, die weg koennen - zum Wegwerfen oder
 * Verkaufen. Der Slot bekommt einen roten Untergrund, in Feeshs Farbe.
 *
 * <p>Ein Unterschied zu Feesh: Dort tippt man die Namen, durch Komma getrennt, in ein Textfeld. Hier gibt es
 * ein Fenster mit einer Liste der Buecher, die schon im Inventar lagen, zum An- und Abhaken - und ein Feld,
 * in das man einen Namen eintippt, den man noch nicht gesehen hat. Ein Buch ist ein genaues Buch, mit Stufe:
 * "Corruption I" ist nicht "Corruption V".
 *
 * <p>Markiert wird nur, was im eigenen Inventar liegt - und nur, solange ein Fenster mit dem Inventar offen
 * ist (in der Welt gibt es nichts, worauf man einen Untergrund legen koennte). Gemerkt werden nur Buecher aus
 * dem eigenen Inventar, nicht aus jedem Fenster: In den Menues von Basar und Auktionshaus stehen hunderte, und
 * die Liste soll die zeigen, die einem tatsaechlich in die Taschen fallen.
 */
public final class TrashBooks {

    /**
     * Knallrot: die Flaeche mit gut zwei Dritteln Deckkraft, dazu ein ganz deckender Rand.
     *
     * Feeshs Farbe ist 0x90FF0000 - Rot mit etwas mehr als halber Deckkraft. Auf einem hellen oder bunten Slot
     * ging das unter; Rueckmeldung: "soll sehr knall rot leuchten". Der Rand traegt die Markierung auch dort.
     */
    private static final int COLOUR = 0xB0FF0000;
    private static final int EDGE = 0xFFFF0000;
    private static final int SCAN_TICKS = 20;
    private static final Pattern COLOUR_CODE = Pattern.compile("§.");

    /** Was ohne eigene Wahl markiert ist: Feeshs Voreinstellung */
    static final Set<String> DEFAULT = Set.of("Corruption I");

    private static int ticks = 0;
    /** Zaehlt jede Aenderung der Liste mit - daran haengt, wann die kleingeschriebene Fassung neu gebaut wird */
    private static int version = 0;
    private static int builtVersion = -1;
    private static Map<String, Boolean> builtFor = null;
    private static Set<String> markedLower = Set.of();
    private static Set<String> knownLower = Set.of();

    private TrashBooks() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(TrashBooks::tick);
    }

    private static TrashBooksCategory cfg() {
        return ModConfig.INSTANCE.fishing.books;
    }

    // ---- Das Buch lesen ----

    /**
     * Der Name eines verzauberten Buches, wie er im Spiel steht ("Corruption I") - oder null.
     *
     * Wie bei Feesh: Das Stueck heisst "Enchanted Book", und die erste Zeile der Beschreibung (ohne den Hinweis
     * "Combinable in Anvil") ist die Verzauberung.
     */
    public static String bookName(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.ENCHANTED_BOOK)) return null;
        if (!isBookTitle(stack.getHoverName().getString())) return null;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return null;
        List<String> lines = new ArrayList<>(lore.lines().size());
        for (Component line : lore.lines()) lines.add(line.getString());
        return nameFromLore(lines);
    }

    /**
     * Heisst das Stueck "Enchanted Book"?
     *
     * Hypixel schreibt Farbcodes manchmal als Zeichen in den Namen ("§9Enchanted Book"). Ohne sie zu entfernen
     * stimmte der Vergleich nie - und kein Buch wurde je erkannt, weder zum Markieren noch fuer die Liste.
     * Feesh entfernt sie vorher.
     */
    static boolean isBookTitle(String hoverName) {
        return "Enchanted Book".equals(clean(hoverName));
    }

    /** Die erste Zeile, die etwas sagt - nicht leer und nicht der Hinweis "Combinable in Anvil" */
    static String nameFromLore(List<String> lines) {
        for (String line : lines) {
            String text = clean(line);
            if (text.isEmpty() || text.contains("Combinable in Anvil")) continue;
            return text;
        }
        return null;
    }

    private static String clean(String text) {
        if (text == null) return "";
        return (text.indexOf('§') >= 0 ? COLOUR_CODE.matcher(text).replaceAll("") : text).trim();
    }

    // ---- Die Liste ----

    /** Ist dieses Buch angehakt? Ohne eigene Wahl gilt Feeshs Voreinstellung */
    public static boolean isMarked(String name) {
        Boolean chosen = cfg().books.get(name);
        return chosen != null ? chosen : DEFAULT.contains(name);
    }

    /** Alle bekannten Namen, nach dem Alphabet - die gesehenen und die aus der Voreinstellung */
    public static List<String> known() {
        Set<String> names = new HashSet<>(cfg().books.keySet());
        names.addAll(DEFAULT);
        List<String> out = new ArrayList<>(names);
        out.sort(Comparator.comparing((String s) -> s.toLowerCase(Locale.ROOT)).thenComparing(Comparator.naturalOrder()));
        return out;
    }

    public static int markedCount() {
        int n = 0;
        for (String name : known()) {
            if (isMarked(name)) n++;
        }
        return n;
    }

    public static void toggle(String name) {
        if (name == null || name.isBlank()) return;
        cfg().books.put(name, !isMarked(name));
        changed();
    }

    /**
     * Ein Buch von Hand eintragen - angehakt, denn wer es tippt, will es markiert haben.
     *
     * @return der Name, unter dem es steht (ein schon bekanntes Buch behaelt seine Schreibweise)
     */
    public static String add(String typed) {
        if (typed == null) return null;
        String name = typed.trim();
        if (name.isEmpty()) return null;
        String existing = existing(name);
        if (existing != null) name = existing;
        cfg().books.put(name, true);
        changed();
        return name;
    }

    /** Ein Buch aus der Liste nehmen. Taucht es wieder im Inventar auf, steht es wieder darin - nicht angehakt */
    public static void forget(String name) {
        if (name == null) return;
        cfg().books.remove(name);
        changed();
    }

    private static String existing(String name) {
        for (String key : cfg().books.keySet()) {
            if (key.equalsIgnoreCase(name)) return key;
        }
        for (String key : DEFAULT) {
            if (key.equalsIgnoreCase(name)) return key;
        }
        return null;
    }

    private static void changed() {
        version++;
        ModConfig.INSTANCE.saveNow();
    }

    /** Die kleingeschriebenen Mengen neu bauen, wenn sich etwas geaendert hat oder die Config neu geladen wurde */
    private static void sync() {
        Map<String, Boolean> map = cfg().books;
        if (builtFor == map && builtVersion == version) return;
        Set<String> marked = new HashSet<>();
        Set<String> known = new HashSet<>();
        for (Map.Entry<String, Boolean> e : map.entrySet()) {
            known.add(e.getKey().toLowerCase(Locale.ROOT));
            if (Boolean.TRUE.equals(e.getValue())) marked.add(e.getKey().toLowerCase(Locale.ROOT));
        }
        for (String name : DEFAULT) {
            String lower = name.toLowerCase(Locale.ROOT);
            known.add(lower);
            if (!map.containsKey(name)) marked.add(lower);
        }
        markedLower = marked;
        knownLower = known;
        builtFor = map;
        builtVersion = version;
    }

    /** Soll ein Buch dieses Namens markiert werden? Gross- und Kleinschreibung ist egal */
    public static boolean wanted(String name) {
        if (name == null) return false;
        sync();
        return markedLower.contains(name.toLowerCase(Locale.ROOT));
    }

    // ---- Merken, was im Inventar liegt ----

    private static void tick(Minecraft client) {
        if (!FeatureGate.trashBooks()) return;
        if (++ticks < SCAN_TICKS) return;
        ticks = 0;
        scan(client);
    }

    /** Die Buecher im eigenen Inventar merken - auch fuer das Auswahlfenster, wenn es aufgeht */
    public static void scan(Minecraft client) {
        if (client == null || client.player == null || !GameState.Server.isSkyblock()) return;
        Inventory inventory = client.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            remember(bookName(inventory.getItem(i)));
        }
    }

    /**
     * Wie viele verzauberte Buecher im eigenen Inventar liegen und wie viele davon gelesen werden konnten.
     * Das Auswahlfenster zeigt es an: Liegen drei da und keins ist lesbar, liegt es am Lesen, nicht an der Auswahl.
     *
     * @return {Buecher, lesbare}
     */
    public static int[] inventoryBooks(Minecraft client) {
        int total = 0;
        int readable = 0;
        if (client == null || client.player == null) return new int[]{0, 0};
        Inventory inventory = client.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !stack.is(Items.ENCHANTED_BOOK)) continue;
            total++;
            if (bookName(stack) != null) readable++;
        }
        return new int[]{total, readable};
    }

    /** Ein Buch, das noch nicht in der Liste steht, kommt hinein - nicht angehakt, ausser es ist Feeshs */
    static void remember(String name) {
        if (name == null) return;
        sync();
        if (knownLower.contains(name.toLowerCase(Locale.ROOT))) return;
        cfg().books.put(name, DEFAULT.contains(name));
        changed();
    }

    // ---- Zeichnen ----

    /**
     * Der Untergrund eines Slots - vom Mixin gerufen, kurz bevor das Stueck gezeichnet wird.
     *
     * Nur fuer Slots des eigenen Inventars: Was in einer Kiste oder im Basar liegt, gehoert einem noch nicht.
     */
    public static void onSlot(GuiGraphicsExtractor graphics, Slot slot) {
        if (!FeatureGate.trashBooks() || !GameState.Server.isSkyblock()) return;
        if (!(slot.container instanceof Inventory)) return;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty() || !stack.is(Items.ENCHANTED_BOOK)) return;
        paint(graphics, slot, bookName(stack));
    }

    /** Der Untergrund selbst: nur im eigenen Inventar und nur fuer ein Buch, das markiert sein soll */
    static void paint(GuiGraphicsExtractor graphics, Slot slot, String name) {
        if (name == null || !(slot.container instanceof Inventory) || !wanted(name)) return;
        int x = slot.x;
        int y = slot.y;
        graphics.fill(x, y, x + 16, y + 16, COLOUR);
        // Der Rand, einen Punkt breit, ganz deckend
        graphics.fill(x, y, x + 16, y + 1, EDGE);
        graphics.fill(x, y + 15, x + 16, y + 16, EDGE);
        graphics.fill(x, y + 1, x + 1, y + 15, EDGE);
        graphics.fill(x + 15, y + 1, x + 16, y + 15, EDGE);
    }

}

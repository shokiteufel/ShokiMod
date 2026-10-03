package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.SkyBlockItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Welche Farben in einem SkyBlock-Jahr haeufiger fallen - gelesen aus dem Menue von /dyes.
 *
 * Hypixel begünstigt in jedem Jahr drei Farben; welche, steht nirgends in einer
 * Schnittstelle, sondern nur in der Kiste, die Vincent oeffnet. Jeder Kopf darin traegt
 * in seiner Beschreibung die Zeile "This dye is 3x as common during SkyBlock Year 517".
 * Mehr braucht es nicht: Farbe, Faktor, Jahr.
 *
 * <p>Gelesen wird von selbst, sobald die Kiste offen ist - ein Befehl waere zwecklos, weil
 * sich bei offenem Fenster nichts tippen laesst. Dafuer merkt sich diese Klasse, welches
 * Menue sie zuletzt gesehen hat, und wartet, bis der Server die Felder gefuellt hat; beim
 * Oeffnen sind sie noch leer.
 *
 * <p>Das Verfahren stammt aus DyeAddons von AlexanderMartens (Apache 2.0), das denselben
 * Satz liest. Nachgebaut, nicht uebernommen.
 */
public final class DyeRotation {

    /** "This dye is 3x as common during SkyBlock Year 517" */
    private static final Pattern BOOST = Pattern.compile(
            "This dye is\\s*(\\d+)x as common during SkyBlock Year\\s*(\\d+)");

    /** Das Fenster, das zuletzt gelesen wurde - damit jedes Menue nur einmal drankommt */
    private static Screen gelesen = null;
    /** Auf Wunsch wandert das naechste Menue vollstaendig ins Log */
    private static boolean dumpNext = false;

    private DyeRotation() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(DyeRotation::tick);
    }

    /**
     * Beim naechsten Menue alles ins Log schreiben.
     *
     * Der Befehl dafuer laesst sich nur bei geschlossenem Fenster tippen, das Menue ist
     * aber genau dann zu. Also wird vorgemerkt und beim Oeffnen ausgeloest.
     */
    public static void dumpNextMenu() {
        dumpNext = true;
        gelesen = null;
    }

    private static void tick(Minecraft client) {
        if (client == null) return;
        if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            gelesen = null;
            return;
        }
        if (screen == gelesen) return;
        // Beim Oeffnen sind die Felder noch leer; der Server schickt den Inhalt nach
        List<ItemStack> inhalt = contents(screen);
        if (inhalt.isEmpty()) return;

        gelesen = screen;
        String titel = screen.getTitle().getString();
        if (dumpNext) {
            dumpNext = false;
            dump(titel, screen);
        }
        read(titel, inhalt);
    }

    private static List<ItemStack> contents(AbstractContainerScreen<?> screen) {
        List<ItemStack> out = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack != null && !stack.isEmpty()) out.add(stack);
        }
        return out;
    }

    /**
     * Die Koepfe im Menue durchgehen und jede Zeile mitnehmen, die den Satz traegt.
     *
     * Der Titel wird nicht geprueft. "Dyes" steht auch ueber einer Bazaar-Seite, und
     * umgekehrt heisst die Kiste je nach Weg anders; der Satz selbst ist das sichere
     * Kennzeichen. Findet sich keiner, passiert nichts.
     */
    /** Faktor und Jahr aus einer Beschreibung - getrennt, damit es ohne Spiel pruefbar ist */
    record Boost(int factor, int year) {
    }

    /** Die erste Zeile, die den Satz traegt. Keine passt: null */
    static Boost boostIn(List<String> lore) {
        for (String zeile : lore) {
            Matcher treffer = BOOST.matcher(zeile);
            if (!treffer.find()) continue;
            try {
                return new Boost(Integer.parseInt(treffer.group(1)), Integer.parseInt(treffer.group(2)));
            } catch (NumberFormatException ignored) {
                // eine kaputte Zahl macht die uebrigen Zeilen nicht unbrauchbar
            }
        }
        return null;
    }

    private static void read(String titel, List<ItemStack> inhalt) {
        Map<String, Integer> gefunden = new LinkedHashMap<>();
        int jahr = 0;

        for (ItemStack stack : inhalt) {
            String name = stack.getHoverName().getString().replaceAll("§[0-9a-fk-or]", "").trim();
            Boost boost = boostIn(SkyBlockItems.loreOf(stack));
            if (boost == null || name.isEmpty() || name.equalsIgnoreCase("Bucket of Dye")) continue;
            gefunden.put(name, boost.factor());
            jahr = boost.year();
        }

        if (jahr <= 0 || gefunden.isEmpty()) return;

        ModConfig.ProfitCategory cfg = ModConfig.INSTANCE.profit;
        Map<String, Integer> bisher = cfg.dyeYears.get(jahr);
        if (gefunden.equals(bisher)) return;

        cfg.dyeYears.put(jahr, gefunden);
        trim(cfg.dyeYears);
        ModConfig.INSTANCE.saveNow();
        ShokiMod.LOGGER.info("[ShokiMod] Boosted dyes in SkyBlock year {}: {} (from \"{}\")",
                jahr, describe(gefunden), titel);
    }

    /** Aelteste Jahre fallen heraus - die Datei soll nicht ewig wachsen */
    private static void trim(Map<Integer, Map<String, Integer>> jahre) {
        while (jahre.size() > ModConfig.ProfitCategory.MAX_DYE_YEARS) {
            int aeltestes = Integer.MAX_VALUE;
            for (Integer jahr : jahre.keySet()) aeltestes = Math.min(aeltestes, jahr);
            jahre.remove(aeltestes);
        }
    }

    /** "Celeste 3x, Flame 2x, Mango 2x" - oder "", wenn fuer das Jahr nichts bekannt ist */
    public static String of(int jahr) {
        Map<String, Integer> gefunden = ModConfig.INSTANCE.profit.dyeYears.get(jahr);
        return gefunden == null ? "" : describe(gefunden);
    }

    static String describe(Map<String, Integer> gefunden) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Integer> e : gefunden.entrySet()) {
            if (!out.isEmpty()) out.append(", ");
            // "Celeste Dye" steht im Menue, "Celeste" genuegt in der Zeile
            out.append(e.getKey().replaceAll("(?i)\\s+Dye$", "")).append(' ').append(e.getValue()).append('x');
        }
        return out.toString();
    }

    private static void dump(String titel, AbstractContainerScreen<?> screen) {
        ShokiMod.LOGGER.info("[ShokiMod] Menu \"{}\":", titel);
        List<Slot> slots = screen.getMenu().slots;
        int gezeigt = 0;
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack == null || stack.isEmpty()) continue;
            gezeigt++;
            ShokiMod.LOGGER.info("[ShokiMod]   {}: {}", i, stack.getHoverName().getString());
            for (String zeile : SkyBlockItems.loreOf(stack)) {
                ShokiMod.LOGGER.info("[ShokiMod]        {}", zeile);
            }
        }
        ShokiMod.LOGGER.info("[ShokiMod] {} items written.", gezeigt);
    }
}

package com.shokiteufel.shokimod.data;

import com.shokiteufel.shokimod.handler.RareCatchAlert;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Welche Meldungen einen Banner zeigen - und welches Design jede davon gerade nimmt.
 *
 * Frueher stand das Design bei jeder Meldung in ihren eigenen Einstellungen, vier Stufen des Fundalarms im
 * Sandkasten ausgenommen. Der Sandkasten fuehrt jetzt eine Liste aller Meldungen mit Haken: Wer ein Design
 * waehlt, hakt an, fuer welche Meldungen es gilt. Diese Klasse ist die eine Stelle, die weiss, wo jede Meldung
 * ihr Design ablegt.
 *
 * Die Zugriffe lesen {@code ModConfig.INSTANCE} erst beim Aufruf: Die Einstellungen werden beim Laden ersetzt,
 * und eine Liste, die das Alte festhielte, schriebe ins Leere.
 */
public final class BannerUses {

    /**
     * Eine Meldung mit Banner.
     *
     * @param label     so steht sie in der Liste
     * @param tag       die kurze Marke hinter dem Namen eines Designs in der Liste links
     * @param stored    was in den Einstellungen steht (kann leer sein)
     * @param store     schreibt den Namen dorthin
     * @param fallback  der Name, auf den "nicht mehr dieses Design" zurueckfaellt; leer heisst: die Vorgabe der Meldung
     * @param effective das Design, das die Meldung wirklich zieht
     */
    public record Use(String label, String tag, String hint, Supplier<String> stored, Consumer<String> store,
                      String fallback, Supplier<String> effective) {

        /** Zieht diese Meldung das Design mit diesem Namen? */
        public boolean uses(String design) {
            return design != null && design.trim().equalsIgnoreCase(effective.get());
        }

        /** Nimmt die Meldung dieses Design schon von sich aus, wenn nichts eingetragen ist? */
        public boolean isDefault(String design) {
            String saved = stored.get();
            store.accept(fallback);
            boolean same = uses(design);
            store.accept(saved);
            return same;
        }

        /** Haken setzen: dieses Design nehmen. Haken nehmen: zurueck auf die Vorgabe der Meldung */
        public void toggle(String design) {
            if (uses(design)) store.accept(fallback);
            else store.accept(design);
        }
    }

    private BannerUses() {
    }

    private static ModConfig.BannerCategory banner() {
        return ModConfig.INSTANCE.chat.banner;
    }

    /** Der Name des Designs, das ein eingetragener Name wirklich meint - ein unbekannter Name faellt aufs erste */
    private static String resolved(String name) {
        return banner().designOrDefault(name).name.trim().toLowerCase(Locale.ROOT);
    }

    /** Alle Meldungen mit Banner, in der Reihenfolge der Liste */
    public static List<Use> all() {
        List<Use> out = new ArrayList<>();
        for (int tier = 1; tier <= ModConfig.RareLootCategory.TIERS; tier++) {
            int n = tier;
            out.add(new Use("Rare loot T" + n, "T" + n, "Rare loot, tier " + n,
                    () -> ModConfig.INSTANCE.chat.rareLoot.designFor(n),
                    name -> ModConfig.INSTANCE.chat.rareLoot.setDesign(n, name),
                    "Classic band",
                    () -> resolved(ModConfig.INSTANCE.chat.rareLoot.designFor(n))));
        }
        out.add(new Use("Dye alert", "Dye", "The dye alert in Rare loot",
                () -> ModConfig.INSTANCE.chat.rareLoot.dyeDesign,
                name -> ModConfig.INSTANCE.chat.rareLoot.dyeDesign = name,
                "Dye alert",
                () -> resolved(ModConfig.INSTANCE.chat.rareLoot.dyeDesign)));
        out.add(new Use("Rare catch", "Catch", "Rare sea creature catches",
                () -> ModConfig.INSTANCE.fishing.rareCatch.bannerDesign,
                name -> ModConfig.INSTANCE.fishing.rareCatch.bannerDesign = name,
                "",
                () -> RareCatchAlert.design().name.trim().toLowerCase(Locale.ROOT)));
        out.add(new Use("Out of air", "Air", "The warning when you are about to drown",
                () -> ModConfig.INSTANCE.fishing.air.bannerDesign,
                name -> ModConfig.INSTANCE.fishing.air.bannerDesign = name,
                "Feesh",
                () -> resolved(ModConfig.INSTANCE.fishing.air.bannerDesign)));
        out.add(new Use("Hotspot gone", "Spot", "The warning when your hotspot despawns",
                () -> ModConfig.INSTANCE.fishing.hotspot.warningDesign,
                name -> ModConfig.INSTANCE.fishing.hotspot.warningDesign = name,
                "Classic band",
                () -> resolved(ModConfig.INSTANCE.fishing.hotspot.warningDesign)));
        out.add(new Use("Pet maxed", "Pet", "A pet reaches its top level",
                () -> ModConfig.INSTANCE.chat.petMax.bannerDesign,
                name -> ModConfig.INSTANCE.chat.petMax.bannerDesign = name,
                "",
                () -> resolved(ModConfig.INSTANCE.chat.petMax.bannerDesign)));
        out.add(new Use("Guild events", "Guild", "The guild event start and end banners",
                () -> ModConfig.INSTANCE.guild.events.bannerDesign,
                name -> ModConfig.INSTANCE.guild.events.bannerDesign = name,
                "Classic band",
                () -> resolved(ModConfig.INSTANCE.guild.events.bannerDesign)));
        return out;
    }

    /**
     * Die kurzen Marken der Meldungen, die dieses Design ziehen: "T1, 2, 3, 4, Catch".
     * Leer, wenn keine es zieht.
     */
    public static String tags(String design) {
        StringBuilder tiers = new StringBuilder();
        StringBuilder others = new StringBuilder();
        for (Use use : all()) {
            if (!use.uses(design)) continue;
            if (use.tag().length() == 2 && use.tag().charAt(0) == 'T' && Character.isDigit(use.tag().charAt(1))) {
                tiers.append(tiers.length() == 0 ? "T" : ", ").append(use.tag().charAt(1));
            } else {
                others.append(others.length() == 0 ? "" : ", ").append(use.tag());
            }
        }
        if (tiers.length() == 0) return others.toString();
        return others.length() == 0 ? tiers.toString() : tiers + ", " + others;
    }

    /**
     * Ein Design wird umbenannt: Die Meldungen, die es unter dem alten Namen ziehen, ziehen es danach unter dem
     * neuen. Sonst fielen sie still aufs erste Design zurueck, sobald man ein Zeichen im Namen aendert.
     */
    public static void rename(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.equalsIgnoreCase(newName)) return;
        for (Use use : all()) {
            String saved = use.stored().get();
            if (saved != null && saved.trim().equalsIgnoreCase(oldName.trim())) use.store().accept(newName);
        }
    }
}

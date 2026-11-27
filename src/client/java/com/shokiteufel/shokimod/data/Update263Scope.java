package com.shokiteufel.shokimod.data;

import io.github.notenoughupdates.moulconfig.gui.GuiOptionEditor;
import io.github.notenoughupdates.moulconfig.gui.editors.GuiOptionEditorAccordion;
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Was im Einstellungsmenue zu 26.3 gehoert - und damit verschwindet, solange der Schalter im Reiter "26.3" aus ist (Auftrag des Users, 27.11.: "wenn man es
 * ausmacht soll es auch alle Funktionen ausblenden die dazu gehoeren, so dass es wieder alt aussieht").
 *
 * <p>Zwei Arten von Eintraegen: ganze Reiter und Unter-Reiter (gefuehrt unter dem Feld in {@link ModConfig}, so nennt sie MoulConfig - {@code Field.toString()}) und
 * einzelne Eintraege in Reitern, die auch Aelteres enthalten (Fishing, Slayer). Zu den Unter-Reitern eines ausgeblendeten Reiters gilt dasselbe wie zu ihm. Ein
 * Ordner (Accordion) nimmt seine Eintraege mit.
 *
 * <p>Die Liste steht nur hier. Wer eine Funktion seit 2.4.10 neu baut, traegt ihren Reiter oder ihre Eintraege hier ein (und im Test der Liste); der Test
 * schlaegt an, wenn ein Feld umbenannt wird und die Liste ins Leere zeigt. Die Einstellungen selbst werden nie angefasst: nur die Anzeige.
 */
public final class Update263Scope {

    /** Ein Feld der Liste: die Klasse, in der es steht, und sein Name */
    private record Entry(Class<?> owner, String field) {
    }

    /** Reiter und Unter-Reiter, die ganz zu 26.3 gehoeren. Ihre Unter-Reiter (Chat: Tab Complete ...) gehen mit */
    private static final List<Entry> CATEGORIES = List.of(
            // Ganze Reiter: alles darin kam seit 2.4.10 dazu (Misc und UI & Visuals bestehen nur aus 26.3-Funktionen)
            new Entry(ModConfig.class, "chatTab"),
            new Entry(ModConfig.class, "skillProgress"),
            new Entry(ModConfig.class, "misc"),
            new Entry(ModConfig.class, "uiVisuals"),
            // Fishing: die drei Unter-Reiter
            new Entry(ModConfig.FishingCategory.class, "hookDisplay"),
            new Entry(ModConfig.FishingCategory.class, "baitDisplay"),
            new Entry(ModConfig.FishingCategory.class, "cocoon"),
            // Garden
            new Entry(ModConfig.GardenCategory.class, "shoppingList"),
            new Entry(ModConfig.GardenCategory.class, "rewardWarning"),
            new Entry(ModConfig.GardenCategory.class, "plots"),
            // Inventory (Bazaar flips ist aelter und bleibt)
            new Entry(ModConfig.InventoryCategory.class, "auctions"),
            new Entry(ModConfig.InventoryCategory.class, "bazaar"),
            new Entry(ModConfig.InventoryCategory.class, "itemValue"));

    /** Einzelne Eintraege in Reitern, die auch Aelteres enthalten. Ein Ordner steht mit seinem Kopf hier und nimmt alles mit, was in ihm liegt */
    private static final List<Entry> OPTIONS = List.of(
            new Entry(ModConfig.FishingCategory.class, "shortenFishingMessage"),
            new Entry(ModConfig.FishingCategory.class, "compactDoubleHook"),
            new Entry(ModConfig.FishingCategory.class, "compactDoubleHookPosition"),
            // Slayer: die Ordner "Cocoon" und "Spawn particles"
            new Entry(ModConfig.SlayerCategory.class, "cocoonFolder"),
            new Entry(ModConfig.SlayerCategory.class, "spawnParticleFolder"));

    private static final List<Field> CATEGORY_FIELDS = resolve(CATEGORIES);
    private static final List<Field> OPTION_FIELDS = resolve(OPTIONS);
    private static final Set<String> CATEGORY_IDS = identifiers(CATEGORY_FIELDS);

    private Update263Scope() {
    }

    /**
     * Die Felder zu den Eintraegen. Ein Feld, das es nicht mehr gibt, fehlt hier einfach (das Menue bleibt bedienbar); der Test der Liste schlaegt dann an.
     */
    private static List<Field> resolve(List<Entry> entries) {
        List<Field> fields = new ArrayList<>();
        for (Entry entry : entries) {
            try {
                fields.add(entry.owner().getField(entry.field()));
            } catch (NoSuchFieldException e) {
                System.err.println("26.3 scope: " + entry.owner().getSimpleName() + "." + entry.field() + " no longer exists.");
            }
        }
        return List.copyOf(fields);
    }

    private static Set<String> identifiers(List<Field> fields) {
        Set<String> ids = new HashSet<>();
        for (Field field : fields) ids.add(field.toString());
        return Set.copyOf(ids);
    }

    /** Die Felder der Reiter und Unter-Reiter der Liste (fuer den Test) */
    public static List<Field> categoryFields() {
        return CATEGORY_FIELDS;
    }

    /** Die Felder der einzelnen Eintraege der Liste (fuer den Test) */
    public static List<Field> optionFields() {
        return OPTION_FIELDS;
    }

    /**
     * Ist dieser Reiter ausgeblendet? Nur mit dem 26.3-Schalter aus, und nur fuer die Reiter der Liste und ihre Unter-Reiter.
     *
     * Je Bild mehrmals gefragt: nur ein Feld lesen und in einer Menge nachsehen, nichts bauen.
     *
     * @param identifier       die Kennung des Reiters (MoulConfig: {@code ProcessedCategory.getIdentifier()})
     * @param parentIdentifier die Kennung seines Ober-Reiters, oder null
     */
    public static boolean categoryHidden(String identifier, String parentIdentifier) {
        if (FeatureGate.update263()) return false;
        return identifier != null && CATEGORY_IDS.contains(identifier) || parentIdentifier != null && CATEGORY_IDS.contains(parentIdentifier);
    }

    /**
     * Die einzelnen Eintraege, die zu 26.3 gehoeren: die der Liste und alles, was in einem ihrer Ordner liegt (auch Ordner in Ordnern).
     *
     * Rein: sie fragt den Schalter nicht. Wer sie ruft, entscheidet, ob sie gelten.
     *
     * @param all     alle Eintraege des Menues
     * @param byField sucht den Eintrag zu einem Feld (MoulConfig: {@code getOptionFromField}); null, wenn es keinen gibt
     */
    public static Set<ProcessedOption> optionsToHide(Collection<? extends ProcessedOption> all, Function<Field, ProcessedOption> byField) {
        Set<ProcessedOption> hide = new HashSet<>();
        Set<Integer> folders = new HashSet<>();
        for (Field field : OPTION_FIELDS) {
            ProcessedOption option = byField.apply(field);
            if (option == null) continue;
            hide.add(option);
            addFolder(option, folders);
        }
        // Was in einem Ordner liegt, geht mit. Ein Ordner darin bringt seine Eintraege mit: so lange wiederholen, bis nichts mehr dazukommt
        boolean grew = !folders.isEmpty();
        while (grew) {
            grew = false;
            for (ProcessedOption option : all) {
                int folder = option.getAccordionId();
                if (folder >= 0 && folders.contains(folder) && hide.add(option)) {
                    addFolder(option, folders);
                    grew = true;
                }
            }
        }
        return hide;
    }

    private static void addFolder(ProcessedOption option, Set<Integer> folders) {
        GuiOptionEditor editor = option.getEditor();
        if (editor instanceof GuiOptionEditorAccordion accordion) folders.add(accordion.getAccordionId());
    }
}

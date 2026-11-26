package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.SkillProgressCategory;
import com.shokiteufel.shokimod.data.ModConfig.SkillProgressCategory.SkillInfo;
import com.shokiteufel.shokimod.scanner.TabListScanner;
import com.shokiteufel.shokimod.util.SkillLevels;
import com.shokiteufel.shokimod.util.SkillLevels.Skill;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stufe und Fortschritt der Faehigkeit, die gerade Erfahrung bekommt - samt Overflow ueber die Obergrenze hinaus.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, "Skill Progress", {@code SkillApi}, {@code SkillProgress}, {@code SkillTooltip}). Von dort stammt die Regel:
 * <ul>
 * <li>Die Aktionsleiste schreibt bei jedem Zugewinn "+3.5 Mining (12,345/40,000)" - Stand und Preis der naechsten Stufe - oder
 * "+1.1 Mining (48.39%)". Aus dem Preis der naechsten Stufe folgt die Stufe (die Tabelle der Preise ist eindeutig), aus der Prozentform
 * die Stufe aus dem Widget der Tab-Liste ("Farming 35: 12.4%").</li>
 * <li>Bei einer vollen Faehigkeit steht als Ziel 0 und als Stand die Erfahrung ueber der Obergrenze; zur Gesamterfahrung kommt dann die Erfahrung bis zur Obergrenze.</li>
 * <li>Das Menue der Faehigkeiten ("Your Skills") liefert den Stand, bevor die erste Erfahrung kommt.</li>
 * <li>Overflow: Stufen ueber der Obergrenze zaehlen weiter ({@link SkillLevels}).</li>
 * </ul>
 * Die Umsetzung ist eigene Arbeit: Java, die Anzeige in zwei Kaesten des HUD-Editors, die Zahlen in der Mod-Einstellung gespeichert, eigener
 * Wortlaut und eigene Tests. Nicht uebernommen sind die Zeile mit allen Faehigkeiten, die Zeit bis zur naechsten Stufe, die Wunschstufe und die
 * Erfahrung aus Geschenken (Jerry-Box, Lily-Splosion).
 */
public final class SkillProgress {

    /** Zwei oder mehr Leerzeichen trennen die Teile der Aktionsleiste (Leben, Mana, Erfahrung ...) */
    private static final Pattern PART_SPLIT = Pattern.compile("\\s{2,}");
    private static final Pattern PERCENT = Pattern.compile(
            "^\\+(?<gained>[\\d.,]+) (?<skill>.+?) \\((?<progress>[\\d.,]+)%\\)$");
    private static final Pattern FRACTION = Pattern.compile(
            "^\\+(?<gained>[\\d.,]+) (?<skill>.+?) \\((?<current>[\\d.,]+)/(?<needed>[\\d,.]+[kKmMbB]?)\\)$");
    private static final Pattern TAB_PERCENT = Pattern.compile(
            "^(?<type>\\w+)(?: (?<level>\\d+))?: (?<progress>[0-9.]+)%$");
    private static final Pattern TAB_MAX = Pattern.compile("^(?<type>\\w+) (?<level>\\d+): MAX$");
    private static final Pattern TAB_FRACTION = Pattern.compile(
            "^(?<type>\\w+)(?: (?<level>\\d+))?: (?<current>[0-9,.]+)/(?<needed>[\\d,.]+[kKmMbB]?)$");
    private static final Pattern MENU_FRACTION = Pattern.compile("^[\\d,.]+/[\\d,.]+[kKmMbB]?$");
    private static final Pattern MENU_NUMBER = Pattern.compile("^[\\d,.]+$");

    private static final String MAX_REACHED = "Max Skill level reached!";
    private static final String SKILLS_MENU = "Your Skills";
    /** So lange bleibt das Feld nach einem Zugewinn stehen */
    public static final long SHOW_MILLIS = 3_000L;
    /** Der Preis der naechsten Stufe jenseits der Tabelle, wenn die Tab-Liste nichts anderes sagt (SkyHanni: 7,6 Mio.) */
    private static final long FALLBACK_NEXT_COST = 7_600_000L;
    private static final long SAVE_EVERY_MILLIS = 60_000L;
    private static final int MENU_WAIT_TICKS = 20;
    private static final int MAX_STACK_COUNT = 99_999;

    /** Was ein gelesener Zugewinn geaendert hat */
    record Update(Skill skill, SkillInfo info, int oldOverflowLevel, boolean overflowLevelUp) {
    }

    private static Skill active = null;
    private static long lastUpdateAt = 0L;
    private static boolean dirty = false;
    private static long savedAt = 0L;
    private static String menuTitle = null;
    private static int menuTicks = 0;
    /** Zaehlt jede Aenderung, damit der Kasten sich genau dann neu baut */
    private static int version = 0;
    /** Zaehlt mit jedem Tick - nach ihr baut sich der Balken im Regenbogen neu */
    private static long tickClock = 0L;

    private SkillProgress() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SkillProgress::tick);
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> adjustTooltip(stack, lines));
    }

    private static SkillProgressCategory cfg() {
        return ModConfig.INSTANCE.skillProgress;
    }

    // ------------------------------------------------------------------ Zustand

    /** Die Faehigkeit, die zuletzt Erfahrung bekam - aus dieser Sitzung oder, solange keine kam, aus der Einstellung */
    public static Skill activeSkill() {
        if (active == null) active = Skill.byName(cfg().lastSkill);
        return active;
    }

    public static long lastUpdateAt() {
        return lastUpdateAt;
    }

    public static int version() {
        return version;
    }

    /** Die Uhr des letzten Ticks */
    public static long tickTime() {
        return tickClock;
    }

    /** Der gelesene Stand einer Faehigkeit, oder null, wenn noch nichts gelesen wurde */
    public static SkillInfo infoOf(Skill skill) {
        if (skill == null) return null;
        SkillInfo info = cfg().skills.get(key(skill));
        return info == null || (info.level == 0 && info.currentXpMax == 0L && info.totalXp == 0L) ? null : info;
    }

    private static String key(Skill skill) {
        return skill.displayName().toLowerCase(Locale.ROOT);
    }

    /** Fuer die Probe: alles vergessen */
    static void resetForTest() {
        active = null;
        lastUpdateAt = 0L;
        dirty = false;
        savedAt = 0L;
        version = 0;
        menuTitle = null;
        menuTicks = 0;
    }

    // ------------------------------------------------------------------ Lesen der Aktionsleiste

    /** Eine Aktionsleiste ist gekommen (der Text ohne Farbcodes). Bei ausgeschaltetem Skill progress wird sie gar nicht erst gelesen */
    public static void onActionBar(String plain) {
        if (plain == null || plain.indexOf('+') < 0) return;
        Update update = apply(plain, TabListScanner.lastLines(), cfg().skills);
        if (update == null) return;
        long now = System.currentTimeMillis();
        active = update.skill();
        cfg().lastSkill = update.skill().displayName();
        lastUpdateAt = now;
        version++;
        dirty = true;
        if (update.overflowLevelUp() && cfg().overflow.inChat) announceLevelUp(update.skill(), update.oldOverflowLevel(), update.info().overflowLevel);
    }

    /**
     * Liest eine Aktionsleiste und aendert den Stand der betroffenen Faehigkeit in {@code store}. Null, wenn nichts Passendes darin steht.
     *
     * @param tabLines die Tab-Liste ohne Farbcodes - gebraucht fuer die Prozentform, die die Stufe nicht nennt
     */
    static Update apply(String plain, List<String> tabLines, Map<String, SkillInfo> store) {
        for (String part : PART_SPLIT.split(plain.trim())) {
            Matcher fraction = FRACTION.matcher(part);
            if (fraction.matches()) {
                Skill skill = Skill.byName(fraction.group("skill"));
                if (skill == null) continue;
                return fromFraction(skill, store, fraction.group("gained"), fraction.group("current"), fraction.group("needed"));
            }
            Matcher percent = PERCENT.matcher(part);
            if (percent.matches()) {
                Skill skill = Skill.byName(percent.group("skill"));
                if (skill == null) continue;
                Update update = fromPercent(skill, store, percent.group("gained"), percent.group("progress"), tabLines);
                if (update != null) return update;
            }
        }
        return null;
    }

    private static SkillInfo infoIn(Map<String, SkillInfo> store, Skill skill) {
        return store.computeIfAbsent(skill.displayName().toLowerCase(Locale.ROOT), k -> new SkillInfo());
    }

    /** "(12,345/40,000)": Stand und Preis der naechsten Stufe - daraus die Stufe, dann die Gesamterfahrung */
    private static Update fromFraction(Skill skill, Map<String, SkillInfo> store, String gained, String currentText, String neededText) {
        Long current = SkillLevels.parseAmount(currentText);
        Long needed = SkillLevels.parseAmount(neededText);
        if (current == null || needed == null) return null;
        SkillInfo info = infoIn(store, skill);
        // Bei einer vollen Faehigkeit steht das Ziel auf 0 - dann gibt es keine "naechste Stufe" abzuziehen
        int minus = needed == 0L ? 0 : 1;
        int costLevel = SkillLevels.levelWithCost(needed);
        if (costLevel < 0) costLevel = skill.maxLevel();
        int level = costLevel - minus;
        long total = SkillLevels.xpRequiredForLevel(level) + current;
        SkillLevels.Level calc = SkillLevels.calculate(total, skill.maxLevel());

        int oldOverflow = info.overflowLevel;
        boolean up = info.overflowLevel > skill.maxLevel() && calc.level() == info.overflowLevel + 1;
        info.overflowCurrentXp = calc.xpCurrent();
        info.overflowCurrentXpMax = calc.xpForNext();
        info.overflowTotalXp = calc.overflowXp();
        info.overflowLevel = calc.level();
        info.currentXp = current;
        info.currentXpMax = needed;
        info.totalXp = total;
        info.level = level;
        info.lastGain = gained;
        return new Update(skill, info, oldOverflow, up);
    }

    /** "(48.39%)": die Stufe steht im Widget der Tab-Liste, der Fortschritt in Prozent der naechsten Stufe */
    private static Update fromPercent(Skill skill, Map<String, SkillInfo> store, String gained, String progressText, List<String> tabLines) {
        double percent;
        try {
            percent = Double.parseDouble(progressText.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
        Integer tabLevel = null;
        boolean percentFound = false;
        long current = 0L;
        long needed = 0L;
        for (String line : tabLines) {
            Matcher tp = TAB_PERCENT.matcher(line);
            if (tp.matches() && tp.group("type").equals(skill.displayName())) {
                tabLevel = tp.group("level") == null ? null : Integer.valueOf(tp.group("level"));
                percentFound = true;
                break;
            }
            // "Farming 60: MAX": ueber die Obergrenze sagt die Prozentform nichts Verlaessliches - die Bruchform ("(.../0)") dagegen schon
            Matcher tm = TAB_MAX.matcher(line);
            if (tm.matches() && tm.group("type").equals(skill.displayName())) return null;
            Matcher tf = TAB_FRACTION.matcher(line);
            if (tf.matches() && tf.group("type").equals(skill.displayName())) {
                tabLevel = tf.group("level") == null ? null : Integer.valueOf(tf.group("level"));
                Long c = SkillLevels.parseAmount(tf.group("current"));
                Long n = SkillLevels.parseAmount(tf.group("needed"));
                if (c != null) current = c;
                if (n != null) needed = n;
                break;
            }
        }
        if (tabLevel == null) return null;
        int level = tabLevel;
        SkillInfo info = infoIn(store, skill);
        long levelXp = SkillLevels.xpRequiredForLevel(level);
        long xpNow;
        long xpMax;
        long total;
        if (percentFound) {
            long nextCost = SkillLevels.costOfLevelUncapped(level + 1);
            if (nextCost == 0L) nextCost = FALLBACK_NEXT_COST;
            xpMax = nextCost;
            xpNow = Math.round(nextCost * percent / 100.0);
            total = levelXp + xpNow;
        } else {
            xpNow = current;
            xpMax = needed;
            total = levelXp + current;
        }
        return finish(skill, info, level, xpNow, xpMax, total, gained);
    }

    /** Die Felder setzen und den Overflow rechnen; {@code total} ist die gesamte Erfahrung, nicht die ueber der Obergrenze */
    private static Update finish(Skill skill, SkillInfo info, int level, long xpNow, long xpMax, long total, String gained) {
        SkillLevels.Level calc = SkillLevels.calculate(total, skill.maxLevel());
        int oldOverflow = info.overflowLevel;
        boolean up = info.overflowLevel > skill.maxLevel() && calc.level() == info.overflowLevel + 1;
        info.totalXp = total;
        info.currentXp = xpNow;
        info.currentXpMax = xpMax;
        info.level = level;
        info.overflowTotalXp = calc.overflowXp();
        info.overflowCurrentXp = calc.xpCurrent();
        info.overflowCurrentXpMax = calc.xpForNext();
        info.overflowLevel = calc.level();
        info.lastGain = gained;
        return new Update(skill, info, oldOverflow, up);
    }

    // ------------------------------------------------------------------ das Menue der Faehigkeiten

    /** Ein Gegenstand des Menues: sein Name und die Zeilen darunter, beides ohne Farbcodes */
    record MenuItem(String name, List<String> lore) {
    }

    /**
     * Liest einen Gegenstand des Menues "Your Skills": Name "Mining XIV" (Faehigkeit und Stufe als roemische Zahl) und die Zeilen
     * "Progress to Level XV: 48.4%" und darunter "12,345/40,000" - oder, bei einer vollen Faehigkeit, "Max Skill level reached!" und darunter
     * die gesamte Erfahrung. Null, wenn es kein Eintrag einer Faehigkeit ist.
     */
    static Skill readMenuItem(MenuItem item, Map<String, SkillInfo> store) {
        List<String> lore = item.lore();
        boolean clickable = false;
        for (String line : lore) {
            if (line.contains("Click to view!") || line.contains("Not unlocked!")) {
                clickable = true;
                break;
            }
        }
        if (!clickable) return null;
        String[] words = item.name().trim().split(" ");
        Skill skill = Skill.byName(words[0]);
        if (skill == null) return null;
        int level = words.length > 1 ? SkillLevels.romanToDecimal(words[words.length - 1]) : 0;
        SkillInfo info = infoIn(store, skill);
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i).trim();
            String previous = i > 0 ? lore.get(i - 1).trim() : "";
            if (previous.equals(MAX_REACHED) && MENU_NUMBER.matcher(line).matches()) {
                Long totalXp = SkillLevels.parseAmount(line);
                if (totalXp == null) continue;
                menuMax(skill, info, level, totalXp);
                return skill;
            }
            if (MENU_FRACTION.matcher(line).matches()) {
                String[] parts = line.split("/");
                Long current = SkillLevels.parseAmount(parts[0]);
                Long needed = SkillLevels.parseAmount(parts[1]);
                if (current == null || needed == null) continue;
                menuNotMax(skill, info, level, current, needed);
                return skill;
            }
        }
        return null;
    }

    /** Eine volle Faehigkeit: die Zahl im Menue ist die gesamte Erfahrung */
    private static void menuMax(Skill skill, SkillInfo info, int level, long totalXp) {
        long atCap = SkillLevels.xpRequiredForLevel(skill.maxLevel());
        SkillLevels.Level calc = SkillLevels.calculate(totalXp, skill.maxLevel());
        info.overflowLevel = calc.level();
        info.overflowCurrentXp = calc.xpCurrent();
        info.overflowCurrentXpMax = calc.xpForNext();
        info.overflowTotalXp = calc.overflowXp();
        info.totalXp = totalXp;
        info.level = level;
        info.currentXp = Math.max(0L, totalXp - atCap);
        info.currentXpMax = 0L;
    }

    private static void menuNotMax(Skill skill, SkillInfo info, int level, long current, long needed) {
        long levelXp = SkillLevels.xpRequiredForLevel(level);
        info.currentXp = current;
        info.level = level;
        info.currentXpMax = needed;
        info.totalXp = levelXp + current;
        info.overflowCurrentXp = current;
        info.overflowLevel = level;
        info.overflowCurrentXpMax = needed;
        info.overflowTotalXp = levelXp + current;
    }

    // ------------------------------------------------------------------ Tick

    private static void tick(Minecraft client) {
        tickClock++;
        if (!FeatureGate.skillProgress()) {
            menuTitle = null;
            return;
        }
        long now = System.currentTimeMillis();
        // Was gelesen wurde, wird hoechstens einmal je Minute gespeichert (und beim Beenden) - die Leiste kommt in jedem Tick
        if (dirty && now - savedAt >= SAVE_EVERY_MILLIS) {
            dirty = false;
            savedAt = now;
            ModConfig.INSTANCE.saveNow();
        }
        if (client.player == null || !(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            menuTitle = null;
            return;
        }
        String title = screen.getTitle().getString().replaceAll("§.", "").trim();
        if (!title.equals(SKILLS_MENU) || !GameState.Server.isSkyblock()) {
            menuTitle = null;
            return;
        }
        if (!title.equals(menuTitle)) {
            menuTitle = title;
            menuTicks = 0;
        }
        // Eine Sekunde nach dem Oeffnen einmal lesen: der Server liefert die Plaetze kurz nach dem Fenster
        if (menuTicks >= 0 && ++menuTicks >= MENU_WAIT_TICKS) {
            menuTicks = -1;
            readMenu(client, screen);
        }
        if (menuTicks < 0 && cfg().overflow.inSkillMenuStackSize) applyStackSizes(client, screen);
    }

    private static void readMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        int read = 0;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == client.player.getInventory()) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            MenuItem item = new MenuItem(stack.getHoverName().getString().replaceAll("§.", "").trim(), SkyBlockItems.loreOf(stack));
            if (readMenuItem(item, cfg().skills) != null) read++;
        }
        if (read > 0) {
            version++;
            dirty = true;
            ShokiMod.LOGGER.info("[SkillProgress] read {} skill(s) from the skills menu", read);
        }
    }

    /** Die Zahl auf dem Gegenstand ist die Stufe (mit Overflow): im Menue ist sie sonst 1 */
    private static void applyStackSizes(Minecraft client, AbstractContainerScreen<?> screen) {
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == client.player.getInventory()) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            String[] words = stack.getHoverName().getString().replaceAll("§.", "").trim().split(" ");
            Skill skill = Skill.byName(words[0]);
            if (skill == null) continue;
            SkillInfo info = infoOf(skill);
            if (info == null || info.overflowLevel <= 0) continue;
            int count = Math.max(1, Math.min(MAX_STACK_COUNT, info.overflowLevel));
            if (stack.getCount() != count) stack.setCount(count);
        }
    }

    // ------------------------------------------------------------------ Tooltips im Menue

    private static void adjustTooltip(ItemStack stack, List<Component> lines) {
        if (!FeatureGate.skillProgress() || !cfg().overflow.inSkillMenuTooltip) return;
        Minecraft client = Minecraft.getInstance();
        if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)
                || !screen.getTitle().getString().replaceAll("§.", "").trim().equals(SKILLS_MENU)) {
            return;
        }
        String[] words = stack.getHoverName().getString().replaceAll("§.", "").trim().split(" ");
        Skill skill = Skill.byName(words[0]);
        if (skill == null) return;
        SkillInfo info = infoOf(skill);
        if (info == null) return;
        boolean roman = words.length > 1 && SkillLevels.romanToDecimal(words[words.length - 1]) > 0
                && !words[words.length - 1].chars().allMatch(Character::isDigit);
        List<String> plain = new ArrayList<>(lines.size());
        for (Component line : lines) plain.add(line.getString().replaceAll("§.", ""));
        List<Component> out = overflowTooltip(plain, lines, skill, info, roman);
        if (out != null) {
            lines.clear();
            lines.addAll(out);
        }
    }

    /**
     * Die Zeilen des Tooltips mit dem Overflow: "Max Skill level reached!" wird zum Fortschritt zur naechsten Stufe, darunter steht der Balken mit
     * den Zahlen und die Erfahrung ueber der Obergrenze. Null, wenn die Faehigkeit noch nicht voll ist (dann gilt der Tooltip des Spiels).
     */
    static List<Component> overflowTooltip(List<String> plain, List<Component> original, Skill skill, SkillInfo info, boolean roman) {
        int at = -1;
        for (int i = 0; i < plain.size(); i++) {
            if (plain.get(i).contains(MAX_REACHED)) {
                at = i;
                break;
            }
        }
        if (at < 0 || info.overflowCurrentXpMax <= 0L) return null;
        double fraction = (double) info.overflowCurrentXp / info.overflowCurrentXpMax;
        int level = info.overflowLevel;
        String now = roman ? SkillLevels.toRoman(level) : Integer.toString(level);
        String next = roman ? SkillLevels.toRoman(level + 1) : Integer.toString(level + 1);
        List<Component> out = new ArrayList<>(original);
        out.set(at, Component.literal("§7Progress to Level " + next + ": §e" + percentText(fraction)));
        // Der Balken steht in der Zeile darunter (Hypixel schreibt ihn mit durchgestrichenen Leerzeichen)
        for (int i = at + 1; i < plain.size(); i++) {
            if (plain.get(i).startsWith("          ") || plain.get(i).contains("/")) {
                out.set(i, Component.literal(bar(fraction) + " §e" + format(info.overflowCurrentXp) + "§6/§e" + format(info.overflowCurrentXpMax)));
                out.add(i + 1, Component.empty());
                out.add(i + 2, Component.literal("§b§lOVERFLOW XP:"));
                out.add(i + 3, Component.literal("§7▸ " + format(info.overflowTotalXp)));
                break;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ Text

    /** Ein Balken aus Zeichen fuer den Tooltip: zwanzig Stellen, soviel davon grün wie der Anteil */
    static String bar(double fraction) {
        int filled = (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * 20);
        return "§2§m" + " ".repeat(filled) + "§f§m" + " ".repeat(20 - filled) + "§r";
    }

    static String percentText(double fraction) {
        return String.format(Locale.US, "%.1f%%", fraction * 100.0);
    }

    static String format(long number) {
        return String.format(Locale.US, "%,d", number);
    }

    /** Der Anteil des Balkens: bei Ziel 0 voll, sonst Stand durch Ziel (je nach Einstellung mit oder ohne Overflow) */
    public static double barFraction(SkillInfo info, boolean overflow) {
        long cur = overflow ? info.overflowCurrentXp : info.currentXp;
        long max = overflow ? info.overflowCurrentXpMax : info.currentXpMax;
        if (max == 0L) return 1.0;
        return Math.max(0.0, Math.min(1.0, (double) cur / max));
    }

    /**
     * Der Text des Kastens: "[35] +12.4 Mining (12,345/40,000)". Die Teile ergeben sich aus den Einstellungen; {@code name} (mit Farbe) steht nur, wenn
     * "Use skill name" an ist, Stufe und Fortschritt je nach "Overflow > Display" bis zur Obergrenze oder mit Overflow.
     */
    public static String displayText(Skill skill, SkillInfo info, SkillProgressCategory c) {
        boolean overflow = c.overflow.inDisplay;
        int level = overflow ? info.overflowLevel : info.level;
        long cur = overflow ? info.overflowCurrentXp : info.currentXp;
        long max = overflow ? info.overflowCurrentXpMax : info.currentXpMax;
        StringBuilder sb = new StringBuilder();
        if (c.showLevel) sb.append("§9[§d").append(level).append("§9] ");
        sb.append("§b+").append(info.lastGain).append(' ');
        if (c.useSkillName) sb.append(skill.displayName()).append(' ');
        double percent = max == 0L ? 100.0 : 100.0 * cur / max;
        if (c.usePercentage) {
            sb.append("§7(§6").append(String.format(Locale.US, "%.2f", percent)).append("%§7)");
        } else if (max == 0L) {
            sb.append("§7(§6").append(format(cur)).append("§7)");
        } else {
            sb.append("§7(§6").append(format(cur)).append("§7/§6").append(format(max)).append("§7)");
        }
        if (c.showActionLeft && percent != 100.0) sb.append(" - ").append(actionsLeft(info.lastGain, max, cur));
        return sb.toString();
    }

    /** Wie viele Handlungen von der Groesse des letzten Zugewinns noch bis zur naechsten Stufe fehlen */
    static String actionsLeft(String lastGain, long max, long cur) {
        if (lastGain != null && !lastGain.isEmpty()) {
            try {
                double gain = Double.parseDouble(lastGain.replace(",", ""));
                if (gain > 0.0) {
                    long left = (long) (Math.ceil(max - cur) / gain) + 1;
                    if (left >= 0) return "§6" + format(left) + " Left";
                }
            } catch (NumberFormatException e) {
                // fallt durch
            }
        }
        return "§6∞ Left";
    }

    // ------------------------------------------------------------------ Meldung bei einer neuen Stufe ueber der Obergrenze

    private static void announceLevelUp(Skill skill, int oldLevel, int newLevel) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        client.player.sendSystemMessage(Component.literal("§3§l---------------------------------------------"));
        client.player.sendSystemMessage(Component.literal("  §b§lSKILL LEVEL UP §3" + skill.displayName() + " §8" + oldLevel + "➜§3" + newLevel));
        client.player.sendSystemMessage(Component.literal("§3§l---------------------------------------------"));
        client.player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
        ShokiMod.LOGGER.info("[SkillProgress] {} overflow level {} -> {}", skill.displayName(), oldLevel, newLevel);
    }
}

package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.SkillProgressCategory;
import com.shokiteufel.shokimod.data.ModConfig.SkillProgressCategory.SkillInfo;
import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.handler.SkillProgress;
import com.shokiteufel.shokimod.util.SkillLevels.Skill;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Die Zeile zum Fortschritt der Faehigkeit: "[35] +12.4 Mining (12,345/40,000)", mit dem Symbol der Faehigkeit davor.
 *
 * Herkunft der Idee: SkyHanni ("Skill Progress Display"). Er steht nach jedem Zugewinn drei Sekunden da (mit "Always show" immer) und nur, wenn
 * der Stand der Faehigkeit gelesen ist; im HUD-Editor ({@code /shoki hud}) immer, mit einem Beispiel, damit er sich setzen laesst. Der Balken darunter
 * ist ein eigener Kasten ({@link SkillBarHud}).
 */
public final class SkillProgressHud {

    private SkillProgressHud() {
    }

    /** Hat die Anzeige jetzt etwas zu sagen? Ohne den Editor: eine Faehigkeit mit gelesenem Stand, frisch oder mit "Always show" */
    static boolean hasContent(long now) {
        SkillProgressCategory c = ModConfig.INSTANCE.skillProgress;
        Skill skill = SkillProgress.activeSkill();
        if (skill == null || SkillProgress.infoOf(skill) == null) return false;
        return c.alwaysShow || now - SkillProgress.lastUpdateAt() <= SkillProgress.SHOW_MILLIS;
    }

    /** Soll der Kasten jetzt gezeichnet werden? */
    public static boolean shownNow() {
        if (!ModConfig.INSTANCE.skillProgress.enabled) return false;
        return hasContent(System.currentTimeMillis()) || HudEditorScreen.isOpen();
    }

    /** Das Symbol der Faehigkeit: dasselbe, das SkyHanni nimmt */
    static ItemStack iconOf(Skill skill) {
        return new ItemStack(switch (skill) {
            case COMBAT -> Items.GOLDEN_SWORD;
            case FARMING -> Items.GOLDEN_HOE;
            case FISHING -> Items.FISHING_ROD;
            case MINING -> Items.GOLDEN_PICKAXE;
            case FORAGING -> Items.GOLDEN_AXE;
            case ENCHANTING -> Items.ENCHANTING_TABLE;
            case ALCHEMY -> Items.BREWING_STAND;
            case CARPENTRY -> Items.CRAFTING_TABLE;
            case TAMING -> Items.POLAR_BEAR_SPAWN_EGG;
            case HUNTING -> Items.LEAD;
        });
    }

    /** Das Beispiel im Editor, solange noch nichts gelesen ist */
    static SkillInfo sample() {
        SkillInfo info = new SkillInfo();
        info.level = 35;
        info.currentXp = 12_345L;
        info.currentXpMax = 40_000L;
        info.overflowLevel = 35;
        info.overflowCurrentXp = 12_345L;
        info.overflowCurrentXpMax = 40_000L;
        info.lastGain = "12.4";
        return info;
    }

    public static HudPanel build() {
        SkillProgressCategory c = ModConfig.INSTANCE.skillProgress;
        Skill skill = SkillProgress.activeSkill();
        SkillInfo info = skill == null ? null : SkillProgress.infoOf(skill);
        if (info == null) {
            skill = Skill.MINING;
            info = sample();
        }
        String text = SkillProgress.displayText(skill, info, c);
        HudPanel panel = new HudPanel();
        if (c.useIcon) {
            panel.icon(iconOf(skill), text, "", HudColours.WHITE, HudColours.WHITE);
        } else {
            panel.line(text, HudColours.WHITE);
        }
        return panel;
    }
}

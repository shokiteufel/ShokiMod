package com.shokiteufel.shokimod.data;

import java.util.List;

/**
 * Was ist eingeschaltet, und was darf deshalb ueberhaupt Arbeit machen.
 *
 * Jeder Scanner fragt hier zuerst nach. Ist sein Bereich aus, kehrt er sofort um -
 * ohne Listen zu bauen, ohne die Tab-Liste zu lesen, ohne die Welt anzufassen. Eine
 * abgeschaltete Funktion soll sich nicht bemerkbar machen, auch nicht in der
 * Bildrate.
 *
 * Die Abfragen laufen jeden Tick, deshalb legen sie nichts an: gezaehlte Schleifen
 * statt Streams, billige Schalter vor teuren Listen.
 */
public final class FeatureGate {

    private FeatureGate() {
    }

    /** Soll noch irgendein Mob markiert werden - eigener Eintrag oder seltenes Exemplar? */
    public static boolean mobMarkers() {
        if (!ModConfig.INSTANCE.mobVisuals.masterEnabled) return false;
        if (ModConfig.INSTANCE.mobVisuals.hideyhoFinder) return true;
        List<CustomMob> targets = ModConfig.INSTANCE.mobVisuals.customTargets;
        for (int i = 0; i < targets.size(); i++) {
            CustomMob mob = targets.get(i);
            if (mob != null && mob.isUsable() && mob.anyEnabled()) return true;
        }
        return SparklingTarget.INSTANCE.anyEnabled();
    }

    /** Der Contest: der Kasten selbst oder der Warnton vor Schluss */
    public static boolean contest() {
        ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
        return c.showContestHud || c.contestWarning;
    }

    /** Irgendetwas aus der Safari - Kaesten, Markierungen oder das Mitschreiben */
    public static boolean safari() {
        ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
        return c.trackRuns || c.showProgressHud || c.showMissingHud
                || c.shinyAlertEnabled || c.sparklingEnabled
                || c.highlightSafariWalls || c.highlightMounds
                || c.highlightNests || c.enableFloorDrops;
    }

    /** Der Kasten mit den Mobs in der Naehe */
    public static boolean nearbyPanel() {
        return ModConfig.INSTANCE.mobVisuals.masterEnabled && ModConfig.INSTANCE.mobVisuals.showNearbyHud;
    }

    /** Mindestens eine Chatregel, die auch greifen kann */
    public static boolean chatRules() {
        List<ChatRule> rules = ModConfig.INSTANCE.chat.chatRules;
        for (int i = 0; i < rules.size(); i++) {
            ChatRule rule = rules.get(i);
            if (rule != null && rule.isUsable()) return true;
        }
        return false;
    }

    /** Der Hunting Tracker: nur wenn er zaehlt, braucht er Ort und Preise */
    public static boolean huntingTracker() {
        return ModConfig.INSTANCE.hunting.tracker.enabled;
    }

    /** Seltene Funde: Alarm oder Teilen. Aus heisst: kein Preis, kein Request */
    public static boolean rareLoot() {
        ModConfig.RareLootCategory c = ModConfig.INSTANCE.chat.rareLoot;
        return c.enabled || c.shareEnabled;
    }

    /**
     * Braucht noch irgendetwas zu wissen, wo man ist?
     *
     * Gebiet und Server stehen in der Tab-Liste. Die zu lesen heisst, jede Zeile in
     * Text zu verwandeln - der teuerste Handgriff dieser Mod, und ohne Abnehmer
     * reine Verschwendung. Billige Schalter stehen deshalb vorn.
     */
    public static boolean location() {
        return contest() || safari() || nearbyPanel() || rareLoot() || huntingTracker() || mobMarkers() || chatRules();
    }
}

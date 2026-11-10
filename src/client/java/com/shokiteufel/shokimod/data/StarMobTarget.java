package com.shokiteufel.shokimod.data;

import java.util.Set;

/**
 * Die Stern-Mobs im Dungeon: ein fester Eintrag, der nicht in der Mob-Liste steht. Die Erkennung folgt NoammAddons (CC0, {@code StarMobESP.kt}).
 *
 * <p>Drei Arten von Stern-Mobs:
 * <ul>
 *   <li><b>Mit Namensschild:</b> Die Mobs, die ein Raum wirklich bewacht, tragen ein Sternchen vor dem Namen und enden auf das rote Herz
 *       ("✯ Zombie Soldier 240k❤", Mitschnitt Schiggy 09.11., Floor V). Hypixel haengt das Schild als eigenen Ruestungsstaender ueber den Mob,
 *       der Koerper steht darunter (die Suche macht {@code EntityHighlightManager}). Ein Enderman darunter ist ein Fel: rosa.</li>
 *   <li><b>Ohne Schild, am Namen in der Spielerliste:</b> Shadow Assassin, Lost Adventurer, Diamond Guy und King Midas sind Spieler-Figuren ohne
 *       Sternchen-Schild; sie zaehlen immer als Stern-Mobs - ausser in den Raeumen, in denen dieselben Namen nur Figuren am Rand sind
 *       ({@link #raumErlaubt}).</li>
 *   <li><b>Fledermaeuse:</b> die des Dungeons (Leben 100, 200, 400 oder 800), auf Wunsch, gruen.</li>
 * </ul>
 * Der Umriss (Glow) ist der Vanilla-Umriss und wird durch Waende gezeichnet - auch bei einem unsichtbaren Mob wie dem Shadow Assassin; dazu auf
 * Wunsch eine Linie und ein Kasten. Ohne Dungeon bedeutet das Sternchen nichts, darum wird nur dort gesucht, und nicht im Boss-Raum.
 */
public final class StarMobTarget implements MobVisual {

    public static final StarMobTarget INSTANCE = new StarMobTarget();
    /** Fels: Endermen unter einem Stern-Schild - eigene Farbe (bei Noamm "Fel Color", rosa) */
    public static final MobVisual FEL = new Variante(false);
    /** Die Fledermaeuse des Dungeons: nur Umriss, gruen */
    public static final MobVisual BAT = new Variante(true);

    /** Das Zeichen vor dem Namen: U+272F, ein schwarzer Stern mit Rand */
    private static final char STAR = '✯';
    private static final char HEART = '❤';

    /** Spieler-Figuren, die immer Stern-Mobs sind, auch ohne Schild (Spielerliste, wie bei Noamm) */
    static final Set<String> NAMED_STARS = Set.of("Shadow Assassin", "Lost Adventurer", "Diamond Guy", "King Midas");
    /**
     * Raeume, in denen diese Namen nur Figuren am Rand sind (Noamm: {@code fakeMiniRooms}, "thanks clear sim"); in "Stairs" nur die oberhalb von
     * Hoehe 80.
     */
    static final Set<String> FAKE_MINI_ROOMS = Set.of("Duncan", "Balcony", "Wizard", "Pit", "Waterfall", "Atlas", "Chambers", "Layers",
            "Perch", "Golden Oasis");
    /** Die Lebenspunkte der Fledermaeuse des Dungeons (Noamm: {@code batHealths}) */
    private static final Set<Float> BAT_HEALTHS = Set.of(100f, 200f, 400f, 800f);

    /** Gesetzt vom Tick des Markierers: zweimal je Sekunde gelesen, ob man in den Catacombs steht */
    private static volatile boolean inDungeon = false;

    private StarMobTarget() {
    }

    private static ModConfig.StarMobsCategory cfg() {
        return ModConfig.INSTANCE.ikohS.dungeon.starMobs;
    }

    /** Ist die Funktion an? Eigener Schalter unter ikohS, ohne den Hauptschalter der Mob-Markierungen (wie bei den Pests und den Runic Ghosts) */
    public static boolean enabled() {
        return cfg().enabled;
    }

    /** Nur die Stern-Mobs des Raums, in dem man steht? */
    public static boolean onlyCurrentRoom() {
        return enabled() && cfg().onlyCurrentRoom;
    }

    /** Sollen auch die Fledermaeuse des Dungeons leuchten? */
    public static boolean highlightBats() {
        return enabled() && cfg().highlightBats;
    }

    /** Lohnt sich der Durchgang durch die Mobs: an, im Dungeon und nicht im Boss-Raum (dort gibt es keine Stern-Mobs) */
    public static boolean scanning() {
        return enabled() && inDungeon && !com.shokiteufel.shokimod.dungeonkarte.FunnyKarte.imBoss();
    }

    public static void setInDungeon(boolean value) {
        inDungeon = value;
    }

    /** Traegt der Name das Sternchen eines Stern-Mobs? (Nur das Sternchen; das Schild eines Mobs endet zusaetzlich auf das Herz: {@link #isStarTag}) */
    public static boolean isStar(String rawName) {
        return rawName != null && rawName.indexOf(STAR) >= 0;
    }

    /**
     * Das Schild eines Stern-Mobs: Sternchen im Namen und das Herz am Ende ("✯ Zombie Soldier 240k❤"). Wie bei Noamm ({@code name.endsWith("§c❤") &&
     * name.contains("✯")}); ohne das Herz am Ende ist es ein anderer Ruestungsstaender.
     */
    public static boolean isStarTag(String rawName) {
        if (!isStar(rawName)) return false;
        String s = rawName.stripTrailing();
        return !s.isEmpty() && s.charAt(s.length() - 1) == HEART;
    }

    /** Heisst die Spieler-Figur so, dass sie immer ein Stern-Mob ist (Name aus der Spielerliste)? */
    public static boolean isNamedStar(String profileName) {
        return profileName != null && NAMED_STARS.contains(profileName);
    }

    /**
     * Gilt eine benannte Figur in diesem Raum als Stern-Mob? In den Raeumen, in denen dieselben Namen nur Figuren am Rand sind, nicht; in "Stairs"
     * nur unterhalb von Hoehe 80. {@code raumName} ist null, wenn der Raum nicht bekannt ist: dann gilt sie.
     */
    public static boolean raumErlaubt(String raumName, double y) {
        if (raumName == null) return true;
        if (FAKE_MINI_ROOMS.contains(raumName)) return false;
        return !("Stairs".equals(raumName) && y > 80);
    }

    /** Sind das die Lebenspunkte einer Fledermaus des Dungeons? */
    public static boolean isDungeonBatHealth(float maxHealth) {
        return BAT_HEALTHS.contains(maxHealth);
    }

    /**
     * Um wie viel kleiner als die ID des Namensschilds die ID des Mobs ist: 1, beim Withermancer 3 (dazwischen stehen seine Wither-Schaedel).
     * Gemessen am Mitschnitt von Schiggy (09.11.): bei allen 46 Schildern des Laufs stimmte es.
     */
    public static int bodyIdOffset(String rawName) {
        return rawName != null && rawName.toUpperCase(java.util.Locale.ROOT).contains("WITHERMANCER") ? 3 : 1;
    }

    @Override
    public String label() {
        return "Star mob";
    }

    @Override
    public int glowColorRGB() {
        return ColourText.rgb(cfg().starColour, 0xFFFF00);
    }

    // Eigene Schalter, nicht die der Mob-Liste
    @Override
    public boolean highlight() {
        return enabled();
    }

    @Override
    public boolean tracer() {
        return enabled() && cfg().line;
    }

    @Override
    public boolean nameplate() {
        return false;
    }

    @Override
    public boolean box() {
        return enabled() && cfg().box;
    }

    /** Dieselben Schalter wie die Stern-Mobs, nur eine andere Farbe (Fel: mit Linie und Kasten) - oder nur der Umriss (Fledermaeuse) */
    private static final class Variante implements MobVisual {
        private final boolean fledermaus;

        Variante(boolean fledermaus) {
            this.fledermaus = fledermaus;
        }

        @Override
        public String label() {
            return fledermaus ? "Dungeon bat" : "Fel";
        }

        @Override
        public int glowColorRGB() {
            return fledermaus ? ColourText.rgb(cfg().batColour, 0x00FF00) : ColourText.rgb(cfg().felColour, 0xFFAFAF);
        }

        @Override
        public boolean highlight() {
            return fledermaus ? highlightBats() : enabled();
        }

        @Override
        public boolean tracer() {
            return !fledermaus && enabled() && cfg().line;
        }

        @Override
        public boolean nameplate() {
            return false;
        }

        @Override
        public boolean box() {
            return !fledermaus && enabled() && cfg().box;
        }
    }
}

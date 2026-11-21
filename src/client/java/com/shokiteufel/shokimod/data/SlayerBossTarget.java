package com.shokiteufel.shokimod.data;

/**
 * Der eigene Slayer-Boss: ein fester Eintrag, der nicht in der Mob-Liste steht.
 *
 * Woran der eigene Boss erkannt wird, steht in EntityHighlightManager.markOwnSlayerBoss. Hypixel haengte frueher unter jeden
 * Slayer-Boss eine Zeile "Spawned by: <Name>"; heute tragen sie vor allem die gewoehnlichen Mobs der Quest, und beim eigenen
 * Boss steht nur "SLAYER BOSS" und die Restzeit da. Die Zeile wird deshalb nur noch neben dem Schild eines Bosses gelesen, und
 * fehlt sie, entscheidet die Seitenleiste ("Slay the boss!"). SkyHanni und Skyblocker lesen dieselbe Zeile.
 *
 * Gezeigt wird der Umriss und, wenn man will, eine Linie zum Boss. Kasten und Namensschild gehoeren nicht dazu.
 */
public final class SlayerBossTarget implements MobVisual {

    public static final SlayerBossTarget INSTANCE = new SlayerBossTarget();

    private static final String MARKER = "Spawned by:";

    /**
     * Die Namen der Slayer-Bosse. Nur sie machen ein Schild zum Schild eines Bosses: Der Totenkopf davor steht auf jedem
     * Untoten, auch auf den gewoehnlichen Mobs der Quest, und die tragen ebenfalls "Spawned by: <ich>"
     * (so gesehen: "Crypt Ghoul" mit der Zeile des Spielers).
     *
     * <p>"Packmaster Soul" ist der Boss der Stufe 5 von Sven (Alpha-Server, 21.11.): Schild "[Lv487] ☠ Packmaster Soul 13.1M/40M❤". Er
     * steht in fuenf Gestalten da - dem echten Boss und vier Doppelgaengern, die denselben Besitzer ("Name's SLAYER BOSS") tragen, aber
     * eigene Namen und Stufen ("[Lv356] ☠ Ranger Wolf ..."). Der Besitzer unterscheidet sie also nicht, der Name tut es: Nur das Schild
     * des echten Bosses nennt "Packmaster Soul", die der Doppelgaenger tragen andere Namen und fallen aus der Suche.
     */
    private static final String[] BOSS_NAMES = {
            "Revenant Horror", "Atoned Horror", "Tarantula Broodfather", "Conjoined Brood", "Sven Packmaster", "Packmaster Soul",
            "Voidgloom Seraph", "Inferno Demonlord", "Riftstalker Bloodfiend", "Bloodfiend"};

    private SlayerBossTarget() {
    }

    /** Nennt der Text einen Slayer-Boss? Der Totenkopf allein sagt nichts: Er steht auf jedem Untoten */
    public static boolean isBossName(String text) {
        if (text == null) return false;
        for (int n = 0; n < BOSS_NAMES.length; n++) {
            if (text.contains(BOSS_NAMES[n])) return true;
        }
        return false;
    }

    /** Soll der Boss umrissen werden? */
    public static boolean enabled() {
        return ModConfig.INSTANCE.slayer.highlightOwnBoss;
    }

    /** Muss ueberhaupt nach ihm gesucht werden - fuer den Umriss oder die Linie? */
    public static boolean scanning() {
        return ModConfig.INSTANCE.slayer.highlightOwnBoss || ModConfig.INSTANCE.slayer.lineToBoss;
    }

    /**
     * Der Name hinter "Spawned by:" - ohne Farbcodes und ohne einen Rang in eckigen Klammern.
     * Null, wenn die Zeile keine solche ist.
     */
    public static String owner(String rawName) {
        if (rawName == null) return null;
        String clean = COLOUR_CODE.matcher(rawName).replaceAll("");
        int at = clean.indexOf(MARKER);
        if (at < 0) return null;

        String name = clean.substring(at + MARKER.length()).trim();
        // Ein Rang gehoert nicht zum Namen: "[MVP+] Name"
        while (name.startsWith("[")) {
            int close = name.indexOf(']');
            if (close < 0) break;
            name = name.substring(close + 1).trim();
        }
        // Spielernamen haben kein Leerzeichen; was dahinter steht, ist Beiwerk
        int space = name.indexOf(' ');
        if (space > 0) name = name.substring(0, space);
        return name.isEmpty() ? null : name;
    }

    /** Gehoert die Zeile "Spawned by: ..." dem Spieler mit diesem Namen? */
    public static boolean isOwn(String rawName, String ownName) {
        if (ownName == null || ownName.isEmpty()) return false;
        String owner = owner(rawName);
        return owner != null && owner.equalsIgnoreCase(ownName);
    }

    @Override
    public String label() {
        return "Own Slayer boss";
    }

    @Override
    public int glowColorRGB() {
        return ModConfig.INSTANCE.slayer.highlightColourRGB();
    }

    @Override
    public boolean highlight() {
        return enabled();
    }

    @Override
    public boolean tracer() {
        return ModConfig.INSTANCE.slayer.lineToBoss;
    }

    @Override
    public boolean nameplate() {
        return false;
    }

    @Override
    public boolean box() {
        return false;
    }
}

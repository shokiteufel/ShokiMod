package com.shokiteufel.shokimod.data;

/**
 * Die Runic Ghosts in der Mist (Dwarven Mines): ein fester Eintrag, der nicht in der Mob-Liste steht.
 *
 * Die Ghosts der Mist sind Creeper ohne Namensschild. Das Wort "Runic" kommt in nichts vor, was der Server schickt (Mitschnitt Schiggy
 * 09.11., 13:16 bis 14:01) - unterscheiden laesst sich nur das Leben: ein Ghost hat 1.000.000, drei Creeper hatten 4.000.000
 * (13:17:13, 13:22:18, 13:22:29), also das Vierfache. Wer mehr als {@link #RUNIC_ABOVE} Leben gezeigt hat, gilt als Runic - und bleibt
 * es, auch wenn er danach heruntergeschlagen wird ({@code EntityHighlightManager} merkt sich den Mob selbst, nicht die Zahl).
 *
 * Eigene Schalter, ohne den Hauptschalter der Mob-Markierungen (wie bei den Pests). Von Haus aus aus.
 */
public final class RunicGhostTarget implements MobVisual {

    public static final RunicGhostTarget INSTANCE = new RunicGhostTarget();

    private static final int COLOUR = 0xAA55FF;

    /** Das Leben eines gewoehnlichen Ghosts in der Mist (Mitschnitt 09.11.) */
    static final float NORMAL_HEALTH = 1_000_000f;
    /** Ab hier gilt ein Ghost als Runic: anderthalbmal das Leben eines gewoehnlichen - die gesehenen Runic hatten das Vierfache */
    static final float RUNIC_ABOVE = 1.5f * NORMAL_HEALTH;

    /** Der Koerper eines Ghosts: ein Creeper (die Kennung der Art, ohne dass die Klasse der Version gebraucht wird) */
    private static final String GHOST_BODY = "entity.minecraft.creeper";

    private RunicGhostTarget() {
    }

    private static ModConfig.MistCategory cfg() {
        return ModConfig.INSTANCE.ikohS.mist;
    }

    public static boolean enabled() {
        return cfg().runicGhosts;
    }

    /** Lohnt sich der Durchgang durch die Mobs: an, und in SkyBlock (die Mist liegt in den Dwarven Mines, die Karte selbst wird nicht gefragt) */
    public static boolean scanning() {
        return enabled() && GameState.Server.isSkyblock();
    }

    /** Ist das die Art, aus der ein Ghost besteht? */
    public static boolean isGhostBody(String typeId) {
        return GHOST_BODY.equals(typeId);
    }

    /**
     * Soll ein Wesen dieser Art sichtbar gezeichnet werden, obwohl Hypixel es unsichtbar macht? Ja bei einem Creeper, wenn der Schalter
     * "Show Ghosts" an ist und man in SkyBlock steht. Gefragt wird fuer jede unsichtbare Entity in jedem Bild: Der Schalter steht vorn.
     */
    public static boolean showGhosts(String typeId) {
        return cfg().showGhosts && isGhostBody(typeId) && GameState.Server.isSkyblock();
    }

    /** Zeigt dieses Leben einen Runic Ghost? */
    public static boolean isRunicHealth(float health) {
        return health > RUNIC_ABOVE;
    }

    @Override
    public String label() {
        return "Runic ghost";
    }

    @Override
    public int glowColorRGB() {
        return COLOUR;
    }

    // Eigene Schalter, nicht die der Mob-Liste
    @Override
    public boolean highlight() {
        return enabled();
    }

    @Override
    public boolean tracer() {
        return enabled() && cfg().runicGhostsLine;
    }

    @Override
    public boolean nameplate() {
        return false;
    }

    @Override
    public boolean box() {
        return enabled() && cfg().runicGhostsBox;
    }
}

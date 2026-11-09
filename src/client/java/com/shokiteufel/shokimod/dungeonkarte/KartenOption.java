package com.shokiteufel.shokimod.dungeonkarte;

/**
 * Jede Einstellung der Dungeon Map von NoammAddons (Klasse {@code MapConfig}, Zweig multiversion) - mit dem Namen, unter dem er sie in seiner
 * Config ablegt, ihrer Art, seinem Vorgabewert und dem Feld, das die Funny Map dafuer in ihren eigenen Einstellungen hat.
 *
 * Eine Tabelle treibt alles: die Vorgaben, das Lesen seiner Config ({@link NoammLage}), das Lesen der eigenen ({@link KartenEinstellungen})
 * und den Test, dass zu jeder Einstellung von ihm ein Feld bei uns steht. Kommt bei ihm eine dazu, kommt sie hier dazu.
 *
 * Nicht dabei: "Map Enabled" (die Funny Map hat ihren eigenen Schalter), "Cheater Map" (die Funny Map ist immer die Karte mit allen Raeumen),
 * "Predict Unopened 1x1s" (gilt nur ohne diese Karte), die Mimic-Einstellungen und "Player Clear Info" (brauchen Daten, die die Funny Map nicht hat).
 */
public enum KartenOption {

    HIDE_IN_BOSS("Hide In Boss", Art.JANEIN, false, "hideInBoss"),
    EXTRA_INFO("Show Extra Info Under Map", Art.JANEIN, false, "extraLine"),

    TEXT_SCALE("Map Text Scale", Art.KOMMA, 1.0f, "textScale", 0.4f, 1.5f),
    CHECKMARK_SCALE("Map Checkmark Scale", Art.KOMMA, 1.0f, "checkmarkScale", 0.3f, 1.5f),
    DOOR_WIDTH("Door Width", Art.GANZ, 6, "doorWidth", 6, 10),
    BACKGROUND("Map Background Color", Art.FARBE, 0x32FFFFFF, "backgroundColour"),
    BORDER_COLOR("Map Border Color", Art.FARBE, 0xFFFFFFFF, "borderColour"),
    BORDER_WIDTH("Border Thickness", Art.GANZ, 1, "borderWidth", 1, 5),

    CHECKMARK_STYLE("Checkmark Style", Art.GANZ, 0, "style", 0, 4),
    CENTER("Center Checkmark", Art.JANEIN, true, "centerCheckmark"),
    HIDE_BLOOD_TEXT("Hide text/checkmark of Blood room", Art.JANEIN, false, "hideBloodRoomText"),
    HIDE_UNKNOWN_CHECK("Hide Unknown Room Checkmark", Art.JANEIN, false, "hideUnknownCheckmark"),
    LIMIT_NAME_SIZE("Limit Room Name Size", Art.JANEIN, true, "limitRoomNameSize"),
    UNDISCOVERED_TINT("Undiscovered Room Tint", Art.KOMMA, 50.0f, "darken", 0f, 100f),
    UNDISCOVERED_COLOR("Undiscovered Room", Art.FARBE, 0xFF000000, "undiscoveredColour"),

    PLAYER_NAMES("Show Player Names", Art.GANZ, 0, "playerNames", 0, 2),
    HEAD_SCALE("Player Heads Scale", Art.KOMMA, 1.0f, "headScale", 0.3f, 1.5f),
    NAME_SCALE("Player Name Scale", Art.KOMMA, 0.5f, "nameScale", 0.3f, 1.5f),
    HEAD_BORDER("Head Border", Art.FARBE, 0xFF000000, "headBorderColour"),
    HEAD_BORDER_CLASS("Head Border Class Base", Art.JANEIN, false, "headBorderClass"),
    NAMES_CLASS("Player Names Class Base", Art.JANEIN, false, "namesClass"),
    VANILLA_MARKER("Vanilla Head Marker", Art.JANEIN, false, "vanillaMarker"),
    VANILLA_MARKER_COLOR("Vanilla Head Marker Color", Art.FARBE, 0xFF00FF00, "vanillaMarkerColour"),

    ROOM_UNOPENED("Unopened Room", Art.FARBE, 0xFF414141, "roomUnopened"),
    ROOM_BLOOD("Blood Room", Art.FARBE, 0xFFFF0000, "roomBlood"),
    ROOM_FAIRY("Fairy Room", Art.FARBE, 0xFFF27FA5, "roomFairy"),
    ROOM_RARE("Rare Room", Art.FARBE, 0xFFB2B2B2, "roomRare"),
    ROOM_MINIBOSS("Miniboss Room", Art.FARBE, 0xFFE5E533, "roomMiniboss"),
    ROOM_PUZZLE("Puzzle Room", Art.FARBE, 0xFFB24CD8, "roomPuzzle"),
    ROOM_TRAP("Trap Room", Art.FARBE, 0xFFD87F33, "roomTrap"),
    ROOM_NORMAL("Normal Room", Art.FARBE, 0xFF72431B, "roomNormal"),
    ROOM_ENTRANCE("Entrance Room", Art.FARBE, 0xFF007C00, "roomEntrance"),

    DOOR_UNOPENED("Unopened Door", Art.FARBE, 0xFF414141, "doorUnopened"),
    DOOR_BLOOD("Blood Door", Art.FARBE, 0xFFFF0000, "doorBlood"),
    DOOR_WITHER("Wither Door", Art.FARBE, 0xFF101010, "doorWither"),
    DOOR_NORMAL("Normal Door", Art.FARBE, 0xFF72431B, "doorNormal"),
    DOOR_OPEN_WITHER("Opened Wither Door", Art.FARBE, 0xFF72431B, "doorOpenWither"),
    DOOR_ENTRANCE("Entrance Door", Art.FARBE, 0xFF007C00, "doorEntrance");

    /** Wie ein Wert bei ihm gespeichert wird: Schalter, Zahl mit Komma, ganze Zahl (auch die Auswahl einer Liste) oder Farbe (ARGB als Ganzzahl) */
    public enum Art {
        JANEIN, KOMMA, GANZ, FARBE
    }

    private final String jsonName;
    private final Art art;
    private final Object vorgabe;
    private final String feld;
    private final double min;
    private final double max;

    KartenOption(String jsonName, Art art, Object vorgabe, String feld) {
        this(jsonName, art, vorgabe, feld, Double.NaN, Double.NaN);
    }

    KartenOption(String jsonName, Art art, Object vorgabe, String feld, double min, double max) {
        this.jsonName = jsonName;
        this.art = art;
        this.vorgabe = vorgabe;
        this.feld = feld;
        this.min = min;
        this.max = max;
    }

    /** Der Name der Einstellung in seiner Config ({@code config[Dungeon Map].configSettings}) */
    public String jsonName() {
        return jsonName;
    }

    public Art art() {
        return art;
    }

    /** Sein Vorgabewert: {@link Boolean}, {@link Float} oder {@link Integer} (bei Farben ARGB) */
    public Object vorgabe() {
        return vorgabe;
    }

    /** Das Feld der Funny Map in ihren Einstellungen ({@code FunnyMapCategory}), das dieselbe Einstellung traegt */
    public String feld() {
        return feld;
    }

    /** Haelt eine Zahl in den Grenzen, die er der Einstellung gibt; Schalter und Farben bleiben, wie sie sind */
    public Object begrenzt(Object wert) {
        if (wert instanceof Number n && !Double.isNaN(min)) {
            double d = Math.max(min, Math.min(max, n.doubleValue()));
            return art == Art.KOMMA ? (Object) (float) d : (Object) (int) Math.round(d);
        }
        return wert;
    }
}

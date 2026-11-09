package com.shokiteufel.shokimod.dungeonkarte;

import com.google.gson.JsonObject;
import com.shokiteufel.shokimod.ShokiMod;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Die Verbindung zur laufenden NoammAddons: seine Karteneinstellungen im Speicher lesen und schreiben, ohne dass seine Klassen beim Bau
 * gebraucht werden (Reflection). Seine Datei liest er nur beim Start und schreibt sie beim Schliessen des HUD-Editors, alle fuenf Minuten und
 * beim Beenden; eine Aenderung dort kaeme also nie bei ihm an. Im Speicher dagegen wirkt sie sofort.
 *
 * Gelesen und geschrieben wird, was in {@link KartenOption} steht, ueber den Namen, den er in seiner Config benutzt, dazu die Lage seiner Karte
 * (x, y, Groesse). Ist etwas nicht zu finden (andere Fassung, anders gebaut), ist die Bruecke nicht da, und die Funny Map faellt auf seine
 * Datei zurueck - nur lesen, wie bisher. Jede Ausnahme wird zu einer Zeile im Log, nie zu einem Absturz.
 */
public final class NoammBruecke {

    private static final String KARTE = "com.github.noamm9.features.impl.dungeon.map.MapConfig";
    private static final String ANZEIGE = "com.github.noamm9.features.impl.dungeon.map.MapRenderer";
    private static final String HALTER = "com.github.noamm9.config.ConfigHolder";
    private static final String VERWALTER = "com.github.noamm9.config.ConfigManager";

    private static boolean geprueft;
    private static boolean fehlerGemeldet;
    private static Object konfig;
    private static Object anzeige;
    private static Object verwalter;
    private static Method einstellungenMethode;
    private static Method jsonName;
    private static Method wertLesen;
    private static Method wertSetzen;
    private static Method getX;
    private static Method setX;
    private static Method getY;
    private static Method setY;
    private static Method getScale;
    private static Method setScale;
    private static Method speichern;

    private NoammBruecke() {
    }

    /** Ist NoammAddons da, und lassen sich seine Karteneinstellungen erreichen? */
    public static synchronized boolean verfuegbar() {
        if (!geprueft) {
            geprueft = true;
            try {
                suchen();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                konfig = null;
                // ohne NoammAddons ist das der Normalfall und keine Zeile wert
                if (!(e instanceof ClassNotFoundException)) {
                    meldeEinmal("NoammAddons' map settings are not reachable (" + e + ") - only reading its file");
                }
            }
        }
        return konfig != null;
    }

    private static void suchen() throws ReflectiveOperationException {
        Class<?> karte = Class.forName(KARTE);
        konfig = karte.getField("INSTANCE").get(null);
        einstellungenMethode = karte.getMethod("getConfigSettings");
        Class<?> halter = Class.forName(HALTER);
        jsonName = halter.getMethod("getJsonName");
        wertLesen = halter.getMethod("getValue");
        wertSetzen = halter.getMethod("setValue", Object.class);

        Class<?> a = Class.forName(ANZEIGE);
        anzeige = a.getField("INSTANCE").get(null);
        getX = a.getMethod("getX");
        setX = a.getMethod("setX", float.class);
        getY = a.getMethod("getY");
        setY = a.getMethod("setY", float.class);
        getScale = a.getMethod("getScale");
        setScale = a.getMethod("setScale", float.class);

        Class<?> v = Class.forName(VERWALTER);
        verwalter = v.getField("INSTANCE").get(null);
        speichern = v.getMethod("save");
        // Probe: lesbar?
        Set<?> menge = (Set<?>) einstellungenMethode.invoke(konfig);
        if (menge == null || menge.isEmpty()) throw new IllegalStateException("no settings");
    }

    private static void meldeEinmal(String text) {
        if (fehlerGemeldet) return;
        fehlerGemeldet = true;
        ShokiMod.LOGGER.info("[FunnyMap] {}", text);
    }

    /** Die Halter seiner Einstellungen nach ihrem Namen in der Config */
    private static Map<String, Object> halter() throws ReflectiveOperationException {
        Map<String, Object> nachName = new HashMap<>();
        for (Object h : (Set<?>) einstellungenMethode.invoke(konfig)) {
            Object name = jsonName.invoke(h);
            if (name instanceof String s) nachName.put(s, h);
        }
        return nachName;
    }

    /** Die Karteneinstellungen und die Lage, wie sie bei Noamm jetzt gerade gelten; null, wenn die Bruecke nicht geht */
    public static synchronized KartenAbgleich.Stand lesen() {
        if (!verfuegbar()) return null;
        try {
            JsonObject roh = new JsonObject();
            for (Map.Entry<String, Object> e : halter().entrySet()) {
                Object wert = wertLesen.invoke(e.getValue());
                if (wert instanceof Boolean b) roh.addProperty(e.getKey(), b);
                else if (wert instanceof Number n) roh.addProperty(e.getKey(), n);
                else if (wert instanceof java.awt.Color c) roh.addProperty(e.getKey(), c.getRGB());
            }
            return new KartenAbgleich.Stand(KartenEinstellungen.ausNoamm(roh), ((Number) getX.invoke(anzeige)).floatValue(),
                    ((Number) getY.invoke(anzeige)).floatValue(), ((Number) getScale.invoke(anzeige)).floatValue());
        } catch (ReflectiveOperationException | RuntimeException e) {
            meldeEinmal("reading NoammAddons' map settings failed (" + e + ")");
            return null;
        }
    }

    /**
     * Schreibt die Karteneinstellungen und die Lage in NoammAddons und laesst es sie speichern. Nur, was sich unterscheidet, wird gesetzt (jedes
     * Setzen ruft seine Listener); die Art der Zahl bleibt die, die der Wert dort hat.
     *
     * @return ob es gelang
     */
    public static synchronized boolean schreiben(KartenAbgleich.Stand stand) {
        if (!verfuegbar() || stand == null) return false;
        try {
            boolean geaendert = false;
            Map<String, Object> halter = halter();
            for (KartenOption o : KartenOption.values()) {
                Object h = halter.get(o.jsonName());
                if (h == null) continue;
                Object alt = wertLesen.invoke(h);
                Object neu = inDerArtVon(alt, stand.einstellungen().fuerNoamm(o), o);
                if (neu == null || gleichWert(alt, neu)) continue;
                wertSetzen.invoke(h, neu);
                geaendert = true;
            }
            geaendert |= setzeFloat(getX, setX, stand.x());
            geaendert |= setzeFloat(getY, setY, stand.y());
            geaendert |= setzeFloat(getScale, setScale, stand.scale());
            if (geaendert) speichern.invoke(verwalter);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            meldeEinmal("writing NoammAddons' map settings failed (" + e + ")");
            return false;
        }
    }

    /**
     * Oeffnet das Menue von NoammAddons mit dem Einstellungsfenster seiner Dungeon Map (der Knopf "NoammAddons map settings" in den Einstellungen
     * der Funny Map). Das Menue ist sein {@code ClickGuiScreen}; das Fenster der Karte oeffnet {@code openFeatureWindow}, den Bildschirm setzt
     * er selbst ({@code GuiUtils.setScreen}), damit hier kein Aufruf steht, der je Minecraft-Fassung anders heisst.
     *
     * @return ob es sich oeffnete; sonst steht eine Zeile im Chat
     */
    public static synchronized boolean oeffnen() {
        try {
            Class<?> menue = Class.forName("com.github.noamm9.ui.clickgui.ClickGuiScreen");
            Object bild = menue.getConstructor().newInstance();
            Class<?> merkmal = Class.forName("com.github.noamm9.features.Feature");
            Object karte = Class.forName("com.github.noamm9.features.impl.dungeon.map.DungeonMap").getField("INSTANCE").get(null);
            menue.getMethod("openFeatureWindow", merkmal, Float.class, Float.class).invoke(bild, karte, null, null);
            Class<?> hilfe = Class.forName("com.github.noamm9.utils.GuiUtils");
            Object hilfeInstanz = hilfe.getField("INSTANCE").get(null);
            hilfe.getMethod("setScreen", net.minecraft.client.gui.screens.Screen.class).invoke(hilfeInstanz, bild);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            boolean fehlt = e instanceof ClassNotFoundException;
            ShokiMod.LOGGER.info("[FunnyMap] could not open the map settings of NoammAddons ({})", e.toString());
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(fehlt
                        ? "§e[ShokiMod]§r NoammAddons is not installed - there are no map settings to open."
                        : "§e[ShokiMod]§r The map settings of NoammAddons could not be opened (see latest.log, line [FunnyMap]). Use /na instead."));
            }
            return false;
        }
    }

    private static boolean setzeFloat(Method lesen, Method schreiben, float soll) throws ReflectiveOperationException {
        float ist = ((Number) lesen.invoke(anzeige)).floatValue();
        if (Math.abs(ist - soll) <= 0.01f) return false;
        schreiben.invoke(anzeige, soll);
        return true;
    }

    /** Den Wert in die Art bringen, die der Halter schon hat: Float bleibt Float, Int bleibt Int, eine Farbe wird zur Color */
    static Object inDerArtVon(Object alt, Object neu, KartenOption o) {
        if (alt instanceof Boolean) return neu instanceof Boolean ? neu : null;
        if (alt instanceof java.awt.Color) return neu instanceof Number n ? new java.awt.Color(n.intValue(), true) : null;
        if (alt instanceof Float) return neu instanceof Number n ? (Object) n.floatValue() : null;
        if (alt instanceof Double) return neu instanceof Number n ? (Object) n.doubleValue() : null;
        if (alt instanceof Integer) return neu instanceof Number n ? (Object) (int) Math.round(n.doubleValue()) : null;
        if (alt instanceof Long) return neu instanceof Number n ? (Object) Math.round(n.doubleValue()) : null;
        return null;
    }

    static boolean gleichWert(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) return Math.abs(x.doubleValue() - y.doubleValue()) <= 0.001;
        return Objects.equals(a, b);
    }
}

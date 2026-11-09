package com.shokiteufel.shokimod.dungeonkarte;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.shokiteufel.shokimod.data.ColourText;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.Map;

/**
 * Alle Einstellungen der Karte auf einmal, so wie sie gelten: entweder die von NoammAddons ({@link #ausNoamm}) oder die eigenen der
 * Funny Map ({@link #ausFeldern}). Die Anzeige fragt nur hier und weiss nicht, woher ein Wert kommt.
 */
public final class KartenEinstellungen {

    private final Map<KartenOption, Object> werte = new EnumMap<>(KartenOption.class);

    private KartenEinstellungen() {
    }

    /** Noamms Vorgaben, wie sie ohne jede Aenderung gelten */
    public static KartenEinstellungen standard() {
        KartenEinstellungen e = new KartenEinstellungen();
        for (KartenOption o : KartenOption.values()) {
            e.werte.put(o, o.vorgabe());
        }
        return e;
    }

    // ------------------------------------------------------------------ Zugriff

    public boolean janein(KartenOption o) {
        return Boolean.TRUE.equals(werte.get(o));
    }

    public float komma(KartenOption o) {
        return ((Number) werte.get(o)).floatValue();
    }

    public int ganz(KartenOption o) {
        return ((Number) werte.get(o)).intValue();
    }

    /** Eine Farbe als ARGB */
    public int farbe(KartenOption o) {
        return ((Number) werte.get(o)).intValue();
    }

    /** Fuer Tests: der Wert unveraendert */
    public Object roh(KartenOption o) {
        return werte.get(o);
    }

    // ------------------------------------------------------------------ Vergleichen

    /** Sind alle Werte gleich? Zahlen mit Komma auf drei Stellen genau (Gleitkomma-Reste zaehlen nicht als Aenderung) */
    public boolean gleich(KartenEinstellungen anderer) {
        if (anderer == null) return false;
        for (KartenOption o : KartenOption.values()) {
            Object a = werte.get(o);
            Object b = anderer.werte.get(o);
            if (a instanceof Number x && b instanceof Number y) {
                if (Math.abs(x.doubleValue() - y.doubleValue()) > 0.001) return false;
            } else if (!java.util.Objects.equals(a, b)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ Schreiben in die eigenen Felder

    /**
     * Gegenstueck zu {@link #ausFeldern}: schreibt die Werte in die Felder der Funny Map (Schalter, Zahlen, Auswahl ueber ihre Nummer, Farben als
     * Text des Farbwaehlers). Was nicht passt, bleibt, wie es ist.
     *
     * @return ob sich ein Feld geaendert hat
     */
    public boolean inFelder(Object felder) {
        if (felder == null) return false;
        boolean geaendert = false;
        for (KartenOption o : KartenOption.values()) {
            try {
                Field f = felder.getClass().getField(o.feld());
                Object alt = f.get(felder);
                Object neu = switch (o.art()) {
                    case JANEIN -> werte.get(o);
                    case KOMMA -> f.getType() == int.class ? (Object) Math.round(komma(o)) : (Object) komma(o);
                    case GANZ -> {
                        if (f.getType().isEnum()) {
                            Object[] konstanten = f.getType().getEnumConstants();
                            int i = Math.max(0, Math.min(konstanten.length - 1, ganz(o)));
                            yield konstanten[i];
                        }
                        yield (Object) ganz(o);
                    }
                    case FARBE -> {
                        // Der Text des Waehlers bleibt, wenn er dieselbe Farbe nennt (er traegt noch Tempo und Schreibweise)
                        String text = alt instanceof String s ? s : null;
                        yield text != null && ColourText.argb(text, ~farbe(o)) == farbe(o) ? text : ColourText.ofArgb(farbe(o));
                    }
                };
                if (!java.util.Objects.equals(alt, neu)) {
                    if (alt instanceof Number x && neu instanceof Number y && Math.abs(x.doubleValue() - y.doubleValue()) <= 0.001) continue;
                    f.set(felder, neu);
                    geaendert = true;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // kein Feld oder nicht beschreibbar: bleibt
            }
        }
        return geaendert;
    }

    /** Eine Kopie mit einem geaenderten Wert - fuer Tests und den Abgleich */
    public KartenEinstellungen mit(KartenOption o, Object wert) {
        KartenEinstellungen e = new KartenEinstellungen();
        e.werte.putAll(werte);
        e.werte.put(o, o.begrenzt(wert));
        return e;
    }

    /** Der Wert einer Einstellung so, wie er bei Noamm gespeichert wird (Schalter, Zahl, ARGB als Ganzzahl) */
    public Object fuerNoamm(KartenOption o) {
        return werte.get(o);
    }

    // ------------------------------------------------------------------ Quelle 1: die Config von Noamm

    /**
     * Die Einstellungen aus dem Teil {@code configSettings} seiner Config. Was dort fehlt (er schreibt alles, aber eine alte Datei kennt neuere
     * Einstellungen nicht), bleibt auf seinem Vorgabewert; was nicht zu lesen ist, ebenso.
     */
    public static KartenEinstellungen ausNoamm(JsonObject configSettings) {
        KartenEinstellungen e = standard();
        if (configSettings == null) {
            return e;
        }
        for (KartenOption o : KartenOption.values()) {
            JsonElement j = configSettings.get(o.jsonName());
            if (j == null || !j.isJsonPrimitive()) {
                continue;
            }
            try {
                Object wert = switch (o.art()) {
                    case JANEIN -> j.getAsJsonPrimitive().isBoolean() ? (Object) j.getAsBoolean() : null;
                    case KOMMA -> (Object) j.getAsFloat();
                    case GANZ -> (Object) (int) Math.round(j.getAsDouble());
                    // Seine Farben stehen als Color.getRGB(), also ARGB als vorzeichenbehaftete Ganzzahl
                    case FARBE -> (Object) (int) j.getAsLong();
                };
                if (wert != null) {
                    e.werte.put(o, o.begrenzt(wert));
                }
            } catch (RuntimeException ignored) {
                // nicht lesbar: der Vorgabewert bleibt
            }
        }
        return e;
    }

    // ------------------------------------------------------------------ Quelle 2: die eigenen Einstellungen der Funny Map

    /**
     * Die Einstellungen aus den Feldern der Funny Map. Zu jeder {@link KartenOption} steht dort ein Feld unter dem Namen {@link KartenOption#feld()}:
     * ein Schalter, eine Zahl, eine Auswahl (ihre Nummer) oder eine Farbe als Text des Farbwaehlers.
     */
    public static KartenEinstellungen ausFeldern(Object felder) {
        KartenEinstellungen e = standard();
        if (felder == null) {
            return e;
        }
        for (KartenOption o : KartenOption.values()) {
            try {
                Field f = felder.getClass().getField(o.feld());
                Object v = f.get(felder);
                if (v == null) {
                    continue;
                }
                Object wert = switch (o.art()) {
                    case JANEIN -> v instanceof Boolean ? v : null;
                    case KOMMA -> v instanceof Number n ? (Object) n.floatValue() : null;
                    case GANZ -> v instanceof Number n ? (Object) n.intValue() : v instanceof Enum<?> en ? (Object) en.ordinal() : null;
                    case FARBE -> v instanceof String s ? (Object) ColourText.argb(s, ((Number) o.vorgabe()).intValue()) : null;
                };
                if (wert != null) {
                    e.werte.put(o, o.begrenzt(wert));
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // kein Feld oder nicht lesbar: der Vorgabewert bleibt (der Test stellt sicher, dass es zu jeder Einstellung ein Feld gibt)
            }
        }
        return e;
    }
}

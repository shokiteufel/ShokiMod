package com.shokiteufel.shokimod.paket.kern;

import java.util.List;
import java.util.Map;

/**
 * Die neutralen Ereignisse, aus denen der {@link Kern} seinen Zustand baut. Der Adapter ({@code KernAnschluss}) uebersetzt Minecraft-Pakete
 * hierhin; Aufnahmen und Tests fuettern den Kern mit denselben Ereignissen. Jedes Ereignis traegt die laufende Paketnummer des Bus.
 */
public sealed interface Ereignis {

    long nr();

    /** Kurzform eines Stapels in der Vorhersage eines eigenen Klicks: nur Vanilla-Item und Menge. */
    record Kurz(String vanilla, int menge) {
        public static final Kurz LEER = new Kurz(null, 0);

        public boolean leer() {
            return vanilla == null || menge <= 0;
        }
    }

    /** Ein neues Menue ist aufgegangen (der Server hat es geoeffnet). */
    record MenueAuf(long nr, int container, String titel) implements Ereignis {
    }

    /** Das Menue ist zu (Server oder eigene Taste). */
    record MenueZu(long nr, int container) implements Ereignis {
    }

    /** Ein einzelner Platz wurde gesetzt (Container 0 = Spielerinventar, sonst das offene Menue). */
    record Platz(long nr, int container, int platz, Stapel stapel) implements Ereignis {
    }

    /** Der komplette Inhalt eines Containers. */
    record Inhalt(long nr, int container, List<Stapel> stapel) implements Ereignis {
    }

    /**
     * Ein eigener Klick.
     *
     * @param art       {@code PICKUP}, {@code QUICK_MOVE}, {@code THROW} ... (Name der Eingabeart)
     * @param geaendert die Plaetze, die der Client selbst vorhergesagt hat, mit ihrem neuen Inhalt
     * @param cursor    der vorhergesagte Cursor nach dem Klick
     */
    record Klick(long nr, int container, int platz, String art, Map<Integer, Kurz> geaendert, Kurz cursor) implements Ereignis {
    }

    /** Ein eigener Befehl ({@code /gfs}, {@code /pickupstash} ...): kann Items vom Server holen, wie ein Klick. */
    record Befehl(long nr) implements Ereignis {
    }

    /** Ein Mob ist gestorben (kein Spieler, Armorstand oder Item). */
    record MobTod(long nr, int entity) implements Ereignis {
    }

    /** Ein Item-Entity ist aufgetaucht. */
    record ItemEntity(long nr, int entity) implements Ereignis {
    }

    /** Der eigene Spieler hat ein Item-Entity aufgesammelt. */
    record Aufsammeln(long nr, int itemEntity) implements Ereignis {
    }

    /** Eine Chatzeile (Klartext, ohne Aktionsleiste). */
    record Chat(long nr, String klartext) implements Ereignis {
    }

    /** Neue Welt: der Zustand faengt von vorn an (Login, Weltwechsel). */
    record Welt(long nr) implements Ereignis {
    }
}

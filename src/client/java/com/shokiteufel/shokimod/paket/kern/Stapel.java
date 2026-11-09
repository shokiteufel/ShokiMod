package com.shokiteufel.shokimod.paket.kern;

import java.util.Objects;

/**
 * Eine Ware an einem Platz, so wie der Kern sie kennt (keine Minecraft-Typen).
 *
 * @param id      SkyBlock-Kennung ({@code custom_data.id}); {@code null} fuer Bedienelemente und alles, was nie zaehlt
 * @param vanilla Vanilla-Item ({@code minecraft:rotten_flesh}); {@code null} heisst leer
 * @param name    Anzeigename ohne Farbcodes, {@code null} wenn unbekannt
 * @param menge   Stueckzahl
 * @param uuid    {@code custom_data.uuid} nicht stapelbarer Items, sonst {@code null}
 * @param unklar  Die Identitaet (Kennung, uuid, Name) ist unbekannt, weil der Stapel nur aus der Vorhersage eines eigenen Klicks
 *                stammt (Vanilla-Item und Menge, sonst nichts). Der naechste volle Stapel am Platz ersetzt ihn ohne Zugang.
 */
public record Stapel(String id, String vanilla, String name, int menge, String uuid, boolean unklar) {

    public static final Stapel LEER = new Stapel(null, null, null, 0, null, false);

    public Stapel(String id, String vanilla, String name, int menge, String uuid) {
        this(id, vanilla, name, menge, uuid, false);
    }

    /** Kein Item am Platz. */
    public boolean leer() {
        return menge <= 0 || vanilla == null;
    }

    /** Eine Ware, die der Profit-Tracker kennt (SkyBlock-Kennung vorhanden). */
    public boolean zaehlt() {
        return !leer() && id != null;
    }

    /** Dieselbe Ware (Kennung, Vanilla-Item, uuid), die Menge ist egal. */
    public boolean gleicheWare(Stapel o) {
        return Objects.equals(vanilla, o.vanilla) && Objects.equals(id, o.id) && Objects.equals(uuid, o.uuid);
    }

    public Stapel mitMenge(int neueMenge) {
        return neueMenge <= 0 ? LEER : new Stapel(id, vanilla, name, neueMenge, uuid, unklar);
    }

    /** Derselbe Stapel, aber als Bedienelement (zaehlt nie). */
    public Stapel ohneKennung() {
        return leer() ? LEER : new Stapel(null, vanilla, name, menge, null, false);
    }
}

package com.shokiteufel.shokimod.handler;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Der eigene Angelhaken - auch dann, wenn das Feld {@code player.fishing} ihn nicht (mehr) nennt.
 *
 * Anlass (28.11.2026, Fishing log des Users, "letzter Hook wurde nicht erkannt"): Wirft man gleich nach dem Einholen wieder aus, kam im Log der neue Haken
 * (id 288094) und 100 ms spaeter {@code player.fishing == null}, obwohl der Haken im Spiel noch da war (Screenshot: Schnur und "!!" am Haken). Minecraft setzt das Feld
 * beim Entfernen eines Hakens auf der Client-Seite unbedingt auf {@code null} ({@code FishingHook.onClientRemoval}); trifft das Entfernen des alten Hakens nach dem
 * neuen ein, loescht es den Zeiger auf den neuen. Alles, was am Feld hing - die Hook display, der Alarm aus dem Ton, die Lautstaerke beim Angeln -, sah den neuen
 * Haken dann nicht.
 *
 * Deshalb: Nennt das Feld einen lebenden Haken, gilt er. Sonst wird die Welt nach Haken abgesucht, die dem Spieler gehoeren ({@code getPlayerOwner() == player}); gibt es
 * mehrere (der alte, noch nicht entfernte und der neue), gewinnt der juengste - Entitaetsnummern wachsen mit der Zeit. Die Suche laeuft hoechstens einmal je Tick
 * (Zwischenspeicher), und nur wenn das Feld leer ist.
 */
public final class OwnHook {

    /** So weit um den Spieler wird gesucht (ein Haken mit der Riesenangel kann weit draussen liegen) */
    private static final double SEARCH_RANGE = 96.0;

    private static Object cachedFor = null;
    private static long cachedAt = Long.MIN_VALUE;
    private static FishingHook cached = null;

    private OwnHook() {
    }

    /** Der eigene Haken, oder null, wenn keiner draussen ist */
    public static FishingHook of(LocalPlayer player) {
        if (player == null) return null;
        FishingHook field = player.fishing;
        if (field != null && !field.isRemoved()) return field;

        Level level = player.level();
        long now = level.getGameTime();
        if (cachedFor == player && cachedAt == now) return cached;
        List<FishingHook> own = level.getEntitiesOfClass(FishingHook.class, player.getBoundingBox().inflate(SEARCH_RANGE),
                hook -> !hook.isRemoved() && hook.getPlayerOwner() == player);
        cached = newest(own, FishingHook::getId);
        cachedFor = player;
        cachedAt = now;
        return cached;
    }

    /** Kam der Haken nur durch die Suche zustande - nennt das Feld ihn nicht? Fuers Fishing log */
    public static boolean foundBySearch(LocalPlayer player, FishingHook hook) {
        return player != null && hook != null && player.fishing != hook;
    }

    /** Das juengste Stueck der Liste (die hoechste Nummer); null bei einer leeren Liste. Rein, damit sich die Wahl ohne Spiel pruefen laesst */
    static <T> T newest(List<T> items, ToIntFunction<T> id) {
        T best = null;
        for (T item : items) {
            if (best == null || id.applyAsInt(item) > id.applyAsInt(best)) best = item;
        }
        return best;
    }
}

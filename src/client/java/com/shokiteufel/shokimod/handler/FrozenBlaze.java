package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Traegt der Spieler das ganze Frozen-Blaze-Set? Davon haengen die Funktionen im Reiter Fishing > Frozen Blaze ab.
 *
 * Herkunft: Feesh (Apache-2.0, MoonTheSadFisher), {@code utils/PlayerUtils.kt} ({@code isInFrozenBlaze}). Uebernommen ist die Regel: Alle vier
 * Ruestungsplaetze tragen ein Stueck der Frozen Blaze - erkannt an der Kennung {@code FROZEN_BLAZE_HELMET/CHESTPLATE/LEGGINGS/BOOTS}, ersatzweise
 * am Namen - und das alle fuenf Ticks, nicht bei jeder Abfrage. Nachgebaut in Java; anders ist nur, dass das Ergebnis ausserhalb der Funktion
 * nirgends gebraucht wird: Ist keine der Funktionen an, liest der Tick gar nichts.
 */
public final class FrozenBlaze {

    private static final int CHECK_TICKS = 5;
    private static final String[] PIECE_IDS = {
            "FROZEN_BLAZE_HELMET", "FROZEN_BLAZE_CHESTPLATE", "FROZEN_BLAZE_LEGGINGS", "FROZEN_BLAZE_BOOTS"};
    private static final String[] PIECE_NAMES = {
            "frozen blaze helmet", "frozen blaze chestplate", "frozen blaze leggings", "frozen blaze boots"};
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static boolean wearing = false;
    private static int ticks = 0;

    private FrozenBlaze() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(FrozenBlaze::tick);
    }

    private static void tick(Minecraft client) {
        if (!FeatureGate.frozenBlaze()) {
            wearing = false;
            return;
        }
        if (++ticks < CHECK_TICKS) return;
        ticks = 0;
        wearing = client.player != null && fullSet(client.player);
    }

    /** Traegt der Spieler gerade das ganze Set? Nur zuverlaessig, solange eine der Funktionen an ist - sonst bleibt es falsch */
    public static boolean isWearing() {
        return wearing;
    }

    /** Sollen fremde Spieler gerade ausgeblendet werden? */
    public static boolean hidesPlayers() {
        return wearing && ModConfig.INSTANCE.fishing.frozenBlaze.hidePlayers;
    }

    /**
     * Die Schadenszahl, die ueber einem getroffenen Mob schwebt: ein Ruestungsstaender mit einem Namen, der nur aus einer Zahl besteht, davor und danach hoechstens ein
     * Stern oder ein paar Zeichen (Schwert, Pluszeichen, Herz, Pferd, Komet, ...). Beispiele: "86761", "1234567" mit Sternen links und rechts.
     *
     * Herkunft: SkyHanni (LGPL-2.1), {@code DamageIndicatorManager.isDamageSplash} - dort steht dieselbe Beobachtung als Muster
     * ({@code damagePattern}); hier selbst hingeschrieben, mit denselben Zeichen, weil es die Form ist, in der Hypixel die Namen schickt. Die Kommas
     * der Tausender fallen vorher weg, und eine Zahl, die aelter als 300 Ticks ist, ist keine Schadenszahl mehr (die schweben nur kurz).
     */
    private static final java.util.regex.Pattern DAMAGE = java.util.regex.Pattern.compile(
            "[✧✯]?\\d+[⚔+✧❤♞☄✷ﬗ✯]*");
    private static final int DAMAGE_MAX_AGE_TICKS = 300;

    /**
     * Soll dieser Ruestungsstaender nicht gezeichnet werden, weil er eine Schadenszahl traegt? Vom Einsprung in {@code EntityRenderer.shouldRender}
     * fuer jede Figur in jedem Bild gerufen - erst die billigen Schalter, dann der Name.
     */
    public static boolean hidesDamage(ArmorStand stand) {
        if (!wearing || !ModConfig.INSTANCE.fishing.frozenBlaze.hideDamage) return false;
        if (stand.tickCount > DAMAGE_MAX_AGE_TICKS || !stand.hasCustomName()) return false;
        Component name = stand.getCustomName();
        if (name == null) return false;
        String text = name.getString();
        if (text.isEmpty()) return false;
        return DAMAGE.matcher(text.replaceAll("§.", "").replace(",", "")).matches();
    }

    /**
     * "LOOT SHARE You received 2 Titanoboa Shards for assisting CuzImCrzz!" - die Zeile, die jeder Kill der Party in der Naehe bringt.
     *
     * Herkunft: Feesh, {@code features/chat/HideLootshareMessages.kt}: Dort steht dieselbe Zeile als Muster
     * ({@code LOOTSHARE_ASSIST_PATTERN}), und nur die Zeilen "for assisting" werden ausgeblendet. Hier zusaetzlich die Schreibweise, die
     * Hypixel heute verwendet (ein Ausrufezeichen hinter "LOOT SHARE", ein Zaehler in Klammern am Ende) - dieselbe, die auch der
     * Rare-Loot-Leser und der Jagd-Zaehler kennen. Die Zeilen "from ... catching ..." bleiben.
     */
    private static final java.util.regex.Pattern LOOTSHARE_ASSIST = java.util.regex.Pattern.compile(
            "^LOOT SHARE!? +You received .+ for assisting [A-Za-z0-9_]{1,16}!?(?: *[(][0-9]+[)])?$");

    /**
     * Soll diese Chatzeile ausgeblendet werden? Der Aufruf kommt fuer jede Zeile - erst die billigen Schalter, dann der Text.
     * Die Zeile ist ohne Farbcodes zu uebergeben.
     */
    public static boolean hidesLootshare(String unformatted) {
        if (!wearing || !ModConfig.INSTANCE.fishing.frozenBlaze.hideLootshare) return false;
        return unformatted != null && LOOTSHARE_ASSIST.matcher(unformatted.trim()).matches();
    }

    static boolean fullSet(Player player) {
        for (EquipmentSlot slot : SLOTS) {
            if (!isPiece(player.getItemBySlot(slot))) return false;
        }
        return true;
    }

    /** Ein Stueck der Frozen Blaze? Kennung zuerst, sonst der Name - wie bei Feesh, fuer jeden Platz eines der vier Stuecke */
    public static boolean isPiece(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String id = SkyBlockItems.idOf(stack);
        if (id != null) {
            for (String piece : PIECE_IDS) {
                if (piece.equals(id)) return true;
            }
        }
        String name = stack.getHoverName().getString().replaceAll("§.", "").toLowerCase(Locale.ROOT);
        for (String piece : PIECE_NAMES) {
            if (name.contains(piece)) return true;
        }
        return false;
    }
}

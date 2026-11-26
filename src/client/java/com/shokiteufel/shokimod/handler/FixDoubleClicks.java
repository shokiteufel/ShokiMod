package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

/**
 * Misc > Fix item double clicks: ein Rechtsklick auf einen Block mit einem Blaze-Dolch oder einer Angel (mit Autopet) zaehlt bei Hypixel doppelt.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, "Fix item double clicks"). Von dort stammt die Regel: Das Spiel schickt bei so einem Klick zwei Pakete,
 * eines fuer den Block ({@code ServerboundUseItemOnPacket}) und eines fuer den Gegenstand; Hypixel wertet beide. Haelt man einen der sechs
 * Blaze-Dolche oder eine Angel, wird nur das Block-Paket nicht gesendet - das Gegenstands-Paket geht weiter, der Gegenstand laesst sich also
 * weiter benutzen. SkyHanni hat dazu angemerkt, dass Hypixel den Fehler kennt und dass das Auslassen des Pakets in diesem Fall unbedenklich ist;
 * hier ist das nicht nachgeprueft.
 *
 * <p>Die Umsetzung ist eigene Arbeit: Java, der Einsprung ist {@code DoppelklickMixin} (Senden eines Pakets in der Verbindung, nicht erforderlich: passt
 * die Stelle in einer Version nicht, laeuft die Mod ohne dieses Auslassen weiter).
 */
public final class FixDoubleClicks {

    /** Die sechs Blaze-Dolche (SkyBlock-Kennungen) */
    static final Set<String> BLAZE_DAGGERS = Set.of(
            "BURSTFIRE_DAGGER", "BURSTMAW_DAGGER", "FIREDUST_DAGGER", "HEARTFIRE_DAGGER", "HEARTMAW_DAGGER", "MAWDUST_DAGGER");

    private FixDoubleClicks() {
    }

    /** Ist der Gegenstand einer, bei dem das Block-Paket entfallen soll? */
    static boolean affected(boolean rod, String skyBlockId) {
        return rod || (skyBlockId != null && BLAZE_DAGGERS.contains(skyBlockId));
    }

    /** Soll dieses Paket nicht gesendet werden? Aufgerufen von {@code DoppelklickMixin} bei jedem Block-Paket */
    public static boolean shouldCancel(ServerboundUseItemOnPacket packet) {
        if (!FeatureGate.fixDoubleClicks() || !GameState.Server.isSkyblock()) return false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        ItemStack stack = player.getItemInHand(packet.hand());
        if (stack.isEmpty()) return false;
        return affected(stack.is(Items.FISHING_ROD), SkyBlockItems.idOf(stack));
    }
}

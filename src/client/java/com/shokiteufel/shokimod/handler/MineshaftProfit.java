package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.scanner.ItemChanges;
import com.shokiteufel.shokimod.scanner.MineshaftState;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ItemValue.SellMode;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Was ein Schacht gebracht hat - eine Zeile beim Hinausgehen, sonst nichts.
 *
 * Kein Kasten, keine Einstellung, kein Zaehler, der irgendwo mitlaeuft: Die Frage
 * kommt genau einmal auf, naemlich wenn man den Schacht verlaesst, und dann soll die
 * Antwort dastehen. Gezaehlt wird dasselbe, was auch der Profit-Tracker zaehlt - was
 * wirklich im Inventar oder in einem Sack ankommt -, bewertet zur Verkaufsorder.
 *
 * Die Verkaufsorder, nicht der Sofortverkauf: Der Schacht ist eine Stunde Arbeit, und
 * wer eine Stunde gearbeitet hat, stellt seine Ware ein, statt sie zu verschleudern.
 */
public final class MineshaftProfit {

    /** Was seit dem Betreten hereingekommen ist */
    private static final Map<String, Integer> gains = new LinkedHashMap<>();
    /** Der Schacht, in dem gezaehlt wird - leer heisst: gerade keiner */
    private static String shaft = "";

    private MineshaftProfit() {
    }

    public static void register() {
        ItemChanges.listen(MineshaftProfit::onGains);
    }

    /** Ein neuer Schacht: was vom letzten uebrig ist, ist erledigt */
    public static void enter(String type, String variant) {
        String name = type + "_" + variant;
        if (name.equals(shaft)) return;

        leave();
        shaft = name;
        gains.clear();
    }

    /**
     * Raus aus dem Schacht: einmal Bescheid sagen.
     *
     * Wird bei jedem Tick ausserhalb eines Schachts gerufen, darf also nichts tun, wenn
     * gerade nichts laeuft - sonst stuende die Zeile zwanzigmal je Sekunde da.
     */
    public static void leave() {
        if (shaft.isEmpty()) return;

        String gewesen = shaft;
        shaft = "";
        if (gains.isEmpty()) return;

        double coins = worth();
        Map<String, Integer> beute = Map.copyOf(gains);
        gains.clear();
        ShokiMod.LOGGER.info("[Mineshaft] {} brought {} at sell order: {}",
                gewesen, ItemValue.format(coins), beute);
        if (coins <= 0) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        client.player.sendSystemMessage(Component.literal("[ShokiMod] ")
                .withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal("You got ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(ItemValue.format(coins)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" coins in that mineshaft.").withStyle(ChatFormatting.YELLOW)));
    }

    /** Was die Beute zur Verkaufsorder wert ist. Was keinen Preis hat, zaehlt null */
    static double worth() {
        double sum = 0.0;
        for (Map.Entry<String, Integer> entry : gains.entrySet()) {
            double unit = ItemValue.unitPrice(SkyBlockItems.priceCandidates(entry.getKey()), SellMode.SELL_ORDER);
            if (unit > 0) sum += unit * entry.getValue();
        }
        return sum;
    }

    /** Was der Inventar-Vergleich meldet, solange man in einem Schacht steht */
    private static void onGains(Map<String, Integer> found) {
        if (shaft.isEmpty() || !FeatureGate.mineshaftKnown() || !MineshaftState.inMineshaft()) return;

        for (Map.Entry<String, Integer> entry : found.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) continue;
            gains.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }

    /** Nur fuer die Probe: der Zaehlstand */
    static Map<String, Integer> gains() {
        return gains;
    }

    /** Nur fuer die Probe: den Schacht setzen, ohne Seitenleiste */
    static void openFor(String name) {
        shaft = name;
        gains.clear();
    }
}

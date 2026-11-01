package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.util.BazaarLive;
import com.shokiteufel.shokimod.util.CorpseProfit;
import com.shokiteufel.shokimod.util.ItemValue;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * /shoki corpseprofitcheck - was eine Leiche im Glacite Mineshaft gerade im Schnitt abwirft.
 *
 * Die Rechnung steht in {@link CorpseProfit}; hier werden nur die Preise geholt und das Ergebnis in
 * den Chat geschrieben. Geholt wird frisch: der Bazaar direkt bei Hypixel (wie in den Profit-Fenstern),
 * die niedrigsten BINs aus der Liste, die die Mod ohnehin fuehrt. Gewartet wird in einem eigenen Thread,
 * nie im Tick.
 */
public final class CorpseProfitCheck {

    /** So lange wird auf einen frischen Bazaar-Stand gewartet, danach gilt der vorhandene */
    private static final long WAIT_MILLIS = 20_000L;
    /** Ein Stand bis zu dieser Sekundenzahl gilt als frisch genug */
    private static final int FRESH_SECONDS = 20;

    private static final AtomicBoolean running = new AtomicBoolean(false);

    private CorpseProfitCheck() {
    }

    public static void run() {
        if (!running.compareAndSet(false, true)) {
            ProfitTracker.say(Component.literal("The corpse profit check is already running - one moment.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        ItemValue.prefetch();
        BazaarLive.wanted();
        ProfitTracker.say(Component.literal("Corpse profit check - fetching the current prices ...")
                .withStyle(ChatFormatting.GRAY));

        Thread worker = new Thread(CorpseProfitCheck::work, "ShokiMod corpse profit");
        worker.setDaemon(true);
        worker.start();
    }

    private static void work() {
        try {
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < WAIT_MILLIS) {
                // Das Gesuch muss erneuert werden, solange wir warten - es laeuft sonst nach 30 s aus
                BazaarLive.wanted();
                int age = BazaarLive.ageSeconds();
                if (age >= 0 && age <= FRESH_SECONDS && ItemValue.LOWEST_BIN.ready()) break;
                Thread.sleep(500L);
            }

            boolean live = BazaarLive.fresh();
            int age = BazaarLive.ageSeconds();
            CorpseProfit.Result result = CorpseProfit.compute(prices(live));
            Component text = describe(result, live, age);
            // Dieselbe Auskunft ins Log: Der Tester schickt Logs, keine Bilder des Chats
            ShokiMod.LOGGER.info("[CorpseProfit] {}", text.getString().replace("\n", " | "));
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> ProfitTracker.say(text));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            ShokiMod.LOGGER.warn("[CorpseProfit] check failed: {}", e.toString());
            Minecraft.getInstance().execute(() -> ProfitTracker.say(
                    Component.literal("The corpse profit check failed - the game log has the reason.")
                            .withStyle(ChatFormatting.RED)));
        } finally {
            running.set(false);
        }
    }

    /** Frischer Bazaar-Stand, wo es einen gibt, sonst der gespeicherte; BINs aus der Liste der Mod */
    private static CorpseProfit.Prices prices(boolean live) {
        return new CorpseProfit.Prices() {
            @Override
            public double offer(String id) {
                double v = live ? BazaarLive.cheapestOfferExact(id) : 0.0;
                if (v > 0) return v;
                ItemValue.BazaarPrice stored = ItemValue.BAZAAR.get(id);
                return stored == null ? 0.0 : stored.cheapestOffer();
            }

            @Override
            public double bid(String id) {
                double v = live ? BazaarLive.highestBidExact(id) : 0.0;
                if (v > 0) return v;
                ItemValue.BazaarPrice stored = ItemValue.BAZAAR.get(id);
                return stored == null ? 0.0 : stored.highestBid();
            }

            @Override
            public double bin(String id) {
                Double v = ItemValue.LOWEST_BIN.get(id);
                return v == null ? 0.0 : v;
            }
        };
    }

    private static Component describe(CorpseProfit.Result result, boolean live, int age) {
        MutableComponent out = Component.literal("Corpse profit per corpse")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" (max stats, keys buy order, items sell order, 5 crystals blocked)")
                        .withStyle(ChatFormatting.GRAY));

        for (CorpseProfit.Row row : result.rows()) {
            MutableComponent line = Component.literal("\n" + row.name() + ": ").withStyle(ChatFormatting.WHITE);
            if (row.hasMeter()) {
                line.append(amount(row.metered())).append(Component.literal(" with RNG meter, ").withStyle(ChatFormatting.GRAY));
            }
            line.append(amount(row.plain())).append(Component.literal(" without").withStyle(ChatFormatting.GRAY));
            if (row.name().equals("Vanguard")) {
                line.append(Component.literal(" (natural Locket "
                        + String.format(java.util.Locale.ROOT, "%.2f", result.naturalLocketChance() * 100) + " % incl.)")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            out.append(line);
            ShokiMod.LOGGER.info("[CorpseProfit] {}: loot {} key {} without meter {} with meter {}",
                    row.name(), Math.round(row.loot()), Math.round(row.key()), Math.round(row.plain()),
                    row.hasMeter() ? Math.round(row.metered()) : "n/a");
        }

        if (result.locket() > 0) {
            out.append(Component.literal("\nRNG meter: shard +10 %, cycles start at 0 XP, "
                    + ItemValue.format(CorpseProfit.METER_NEED) + " XP = Shattered Locket ("
                    + ItemValue.format(result.locket()) + " lowest BIN).").withStyle(ChatFormatting.GRAY));
        } else {
            out.append(Component.literal("\nRNG meter: not calculated - no price for the Shattered Locket.")
                    .withStyle(ChatFormatting.RED));
        }
        out.append(Component.literal("\nPrices: bazaar " + (live ? "live (" + age + "s old)" : "stored, no fresh data")
                + ", BINs up to 10 min old").withStyle(ChatFormatting.DARK_GRAY));
        if (!result.missing().isEmpty()) {
            out.append(Component.literal("\nNo price, counted as 0: " + String.join(", ", result.missing()))
                    .withStyle(ChatFormatting.RED));
        }
        return out;
    }

    /** +353k gruen, -5.9m rot */
    private static Component amount(double coins) {
        String text = (coins >= 0 ? "+" : "") + ItemValue.format(coins);
        return Component.literal(text).withStyle(coins >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
    }
}

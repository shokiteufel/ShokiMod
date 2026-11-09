package com.shokiteufel.shokimod.paket.anschluss;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.paket.PaketBus;
import com.shokiteufel.shokimod.paket.PaketKopf;
import com.shokiteufel.shokimod.paket.PaketPhase;
import com.shokiteufel.shokimod.paket.kern.Ereignis;
import com.shokiteufel.shokimod.paket.kern.Kern;
import com.shokiteufel.shokimod.paket.kern.KernProtokoll;
import com.shokiteufel.shokimod.paket.kern.Stapel;
import com.shokiteufel.shokimod.paket.zustand.PaketAnsicht;
import com.shokiteufel.shokimod.paket.zustand.PaketZustaende;
import com.shokiteufel.shokimod.scanner.ItemChanges;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Anschluss des Paket-Kerns an den {@link PaketBus}: die einzige Stelle, die Minecraft-Pakete fuer das Inventar kennt. Uebersetzt sie in
 * neutrale {@link Ereignis}se; was der {@link Kern} daraus liest, geht an den Profit-Tracker ({@link ItemChanges#zugang}) und in das
 * Protokoll ({@link KernProtokoll}, Datei {@code logs/shokimod-kern.jsonl}, von Haus aus aus: Schalter im Reiter Logs oder
 * {@code -Dshokimod.kern.protokoll=an}).
 *
 * <p>Der Kern ist die einzige Quelle fuer Funde aus dem Inventar. {@code -Dshokimod.paketzugaenge=aus} schaltet ihn ab (dann zaehlt
 * nichts aus dem Inventar). Braucht {@link PaketZustaende} (Chat und Weltwechsel). Nur auf dem Haupt-Thread (der Bus ruft dort auf).
 */
public final class KernAnschluss {
    private static final Kern KERN = new Kern();
    private static boolean aus;
    private static boolean initialisiert;
    private static boolean playerInventoryGemeldet;

    private KernAnschluss() {
    }

    public static Kern kern() {
        return KERN;
    }

    static boolean ausgeschaltet(String wert) {
        return wert != null && "aus".equalsIgnoreCase(wert.trim());
    }

    /** Einmal beim Start, nach {@link PaketZustaende#init()}. */
    public static void init() {
        if (initialisiert) {
            return;
        }
        aus = ausgeschaltet(System.getProperty("shokimod.paketzugaenge"));
        if (aus) {
            ShokiMod.LOGGER.warn("[Kern] -Dshokimod.paketzugaenge=aus: der Profit-Tracker bekommt keine Funde aus dem Inventar");
            return;
        }
        initialisiert = true;
        PaketBus.init();
        protokollAbgleichen();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++protokollTakt >= 20) {
                protokollTakt = 0;
                protokollAbgleichen();
            }
        });
        ItemChanges.verbinden(KERN);
        KERN.hoeren(new Kern.Hoerer() {
            @Override
            public void zugang(Kern.Zugang z) {
                KernProtokoll.neu(z);
                ItemChanges.zugang(z);
            }

            @Override
            public void abgang(Kern.Abgang a) {
                KernProtokoll.neu(a);
            }
        });
        anmelden();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            KernProtokoll.summe();
            KERN.bei(new Ereignis.Welt(PaketBus.laufendeNummer()));
        });
        ShokiMod.LOGGER.info("[Kern] Paket-Kern laeuft, Protokoll: {}", KernProtokoll.an() ? KernProtokoll.datei() : "aus (Schalter im Reiter Logs)");
    }

    private static int protokollTakt;

    // ------------------------------------------------------------------ Bedarf

    /** Die Frage, ob jemand die Funde aus dem Inventar braucht; nur Tests ersetzen sie */
    private static java.util.function.BooleanSupplier bedarfFrage = com.shokiteufel.shokimod.data.FeatureGate::itemChanges;
    private static boolean bedarfLief;

    /**
     * Braucht jemand die Funde aus dem Inventar (Profit Tracker, Rare-Loot-Inventarueberwachung, Schacht-Bilanz)? Nur dann rechnet der Kern
     * mit: Von Haus aus ist alles davon aus, und dann kostet ein Inventar-Paket nichts. Geht der Bedarf an, startet der Kern frisch und fuellt
     * seinen Spiegel aus dem eigenen Inventar (sonst zaehlte das ganze Inventar als neu); geht er aus, wird das Zwischengemerkte verworfen.
     */
    static boolean bedarf() {
        boolean soll = bedarfFrage.getAsBoolean();
        if (soll != bedarfLief) {
            bedarfLief = soll;
            if (soll) {
                anlaufen();
            } else {
                ItemChanges.verwerfen();
            }
        }
        return soll;
    }

    private static void anlaufen() {
        ItemChanges.verwerfen();
        KERN.uhr(System.nanoTime());
        List<Stapel> lokal = new ArrayList<>();
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null && client.player.inventoryMenu != null) {
            for (net.minecraft.world.inventory.Slot platz : client.player.inventoryMenu.slots) {
                lokal.add(stapel(platz.getItem()));
            }
        }
        spiegelFuellen(KERN, PaketBus.laufendeNummer(), lokal);
        ShokiMod.LOGGER.info("[Kern] the finds of the inventory are needed now - the core starts with {} slots read from the inventory", lokal.size());
    }

    /**
     * Setzt den Kern zurueck und gibt ihm den Stand des Spielerinventars (Container 0) als Ausgangslage: was schon im Inventar liegt, ist kein
     * Zugang. Ohne Plaetze (nicht in einer Welt) bleibt es beim Zuruecksetzen; den Ausgangsstand liefert dann der naechste Komplettbestand.
     */
    static void spiegelFuellen(Kern kern, long nr, List<Stapel> lokal) {
        kern.bei(new Ereignis.Welt(nr));
        if (!lokal.isEmpty()) {
            kern.bei(new Ereignis.Inhalt(nr, 0, lokal));
        }
    }

    /** Nur fuer Tests: ersetzt die Frage nach dem Bedarf (null = wieder die Einstellungen) und setzt den Stand zurueck */
    static void bedarfFuerTests(java.util.function.BooleanSupplier frage) {
        bedarfFrage = frage != null ? frage : com.shokiteufel.shokimod.data.FeatureGate::itemChanges;
        bedarfLief = false;
    }

    /** Haelt das Protokoll im Einklang mit dem Schalter im Reiter Logs (von Haus aus aus): an = Datei offen, aus = zu. */
    static void protokollAbgleichen() {
        boolean soll = com.shokiteufel.shokimod.data.ModConfig.INSTANCE.logs.kernLog
                || "an".equalsIgnoreCase(System.getProperty("shokimod.kern.protokoll", "").trim());
        if (soll && !KernProtokoll.an()) {
            KernProtokoll.init(FabricLoader.getInstance().getGameDir().resolve("logs").resolve("shokimod-kern.jsonl"));
        } else if (!soll && KernProtokoll.an()) {
            KernProtokoll.summe();
            KernProtokoll.schliessen();
        }
    }

    /** Ein Ereignis an den Kern, mit der Uhr des Abgriffs. */
    private static void bei(PaketKopf kopf, Ereignis e) {
        KERN.uhr(kopf.nanos());
        KERN.bei(e);
        // Der Takt der Pakete schliesst Drop-Zeilen, auf deren Platz-Update vergeblich gewartet wurde
        ItemChanges.takt(kopf.laufendeNummer(), kopf.nanos());
    }

    /** Ein Abonnent des Kerns: laeuft nur, wenn jemand die Funde braucht (siehe {@link #bedarf}) */
    private static <P extends net.minecraft.network.protocol.Packet<?>> void abo(Class<P> typ, PaketPhase phase, String name,
                                                                                  com.shokiteufel.shokimod.paket.PaketAbonnent<? super P> abonnent) {
        PaketBus.abonnieren(typ, phase, name, (p, k) -> {
            if (bedarf()) {
                abonnent.bei(p, k);
            }
        });
    }

    static void anmelden() {
        abo(ClientboundOpenScreenPacket.class, PaketPhase.VOR, "Kern Menue auf",
                (p, k) -> bei(k, new Ereignis.MenueAuf(k.laufendeNummer(), p.getContainerId(), p.getTitle().getString())));
        abo(ClientboundContainerClosePacket.class, PaketPhase.VOR, "Kern Menue zu",
                (p, k) -> bei(k, new Ereignis.MenueZu(k.laufendeNummer(), p.getContainerId())));
        abo(ServerboundContainerClosePacket.class, PaketPhase.VOR, "Kern Menue zu (eigen)",
                (p, k) -> bei(k, new Ereignis.MenueZu(k.laufendeNummer(), p.getContainerId())));
        abo(ServerboundContainerClickPacket.class, PaketPhase.VOR, "Kern Klick", KernAnschluss::klick);
        abo(ServerboundChatCommandPacket.class, PaketPhase.VOR, "Kern Befehl",
                (p, k) -> bei(k, new Ereignis.Befehl(k.laufendeNummer())));
        abo(ServerboundChatCommandSignedPacket.class, PaketPhase.VOR, "Kern Befehl (signiert)",
                (p, k) -> bei(k, new Ereignis.Befehl(k.laufendeNummer())));
        abo(ClientboundContainerSetSlotPacket.class, PaketPhase.VOR, "Kern Platz",
                (p, k) -> bei(k, new Ereignis.Platz(k.laufendeNummer(), p.getContainerId(), p.getSlot(), stapel(p.getItem()))));
        abo(ClientboundContainerSetContentPacket.class, PaketPhase.VOR, "Kern Inhalt", KernAnschluss::inhalt);
        abo(ClientboundEntityEventPacket.class, PaketPhase.VOR, "Kern Mob-Tod", KernAnschluss::ereignis);
        abo(ClientboundAddEntityPacket.class, PaketPhase.VOR, "Kern Item-Entity", KernAnschluss::entitaet);
        abo(ClientboundTakeItemEntityPacket.class, PaketPhase.VOR, "Kern Aufsammeln", KernAnschluss::aufsammeln);
        abo(ClientboundSetPlayerInventoryPacket.class, PaketPhase.VOR, "Kern Spielerinventar (nur gemeldet)", (p, k) -> {
            // Hypixel nutzt dieses Paket in keinem Mitschnitt; seine Platznummern weichen von Container 0 ab, darum nicht verarbeitet
            if (!playerInventoryGemeldet) {
                playerInventoryGemeldet = true;
                ShokiMod.LOGGER.warn("[Kern] SetPlayerInventory gesehen (Platz {}), der Kern wertet es noch nicht aus", p.slot());
            }
        });
        PaketZustaende.chatHoeren((klartext, inhalt, kopf) -> {
            if (!bedarf()) {
                return;
            }
            bei(kopf, new Ereignis.Chat(kopf.laufendeNummer(), klartext));
            ItemChanges.chat(inhalt, klartext, kopf.nanos(), kopf.laufendeNummer());
        });
        PaketZustaende.weltHoeren((weltart, zwischenwelt, neueWelt, kopf) -> {
            if (neueWelt && bedarf()) {
                bei(kopf, new Ereignis.Welt(kopf.laufendeNummer()));
            }
        });
    }

    // ------------------------------------------------------------------ Uebersetzen

    static Stapel stapel(ItemStack s) {
        if (s == null || s.isEmpty()) {
            return Stapel.LEER;
        }
        String vanilla = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
        String id = ItemChanges.countedId(s);
        String uuid = null;
        if (id != null) {
            ItemChanges.beobachte(id, s);
            CustomData daten = s.get(DataComponents.CUSTOM_DATA);
            if (daten != null) {
                String u = daten.copyTag().getStringOr("uuid", "");
                uuid = u.isEmpty() ? null : u;
            }
        }
        return new Stapel(id, vanilla, s.getHoverName().getString(), s.getCount(), uuid);
    }

    private static Ereignis.Kurz kurz(HashedStack h) {
        if (h instanceof HashedStack.ActualItem a) {
            return new Ereignis.Kurz(BuiltInRegistries.ITEM.getKey(a.item().value()).toString(), a.count());
        }
        return Ereignis.Kurz.LEER;
    }

    private static void klick(ServerboundContainerClickPacket p, PaketKopf kopf) {
        if (p.containerId() != 0 && !KERN.offen(p.containerId())) {
            return;
        }
        Map<Integer, Ereignis.Kurz> geaendert = new HashMap<>();
        for (Int2ObjectMap.Entry<HashedStack> e : p.changedSlots().int2ObjectEntrySet()) {
            geaendert.put(e.getIntKey(), kurz(e.getValue()));
        }
        bei(kopf, new Ereignis.Klick(kopf.laufendeNummer(), p.containerId(), p.slotNum(), p.containerInput().name(), geaendert, kurz(p.carriedItem())));
    }

    private static void inhalt(ClientboundContainerSetContentPacket p, PaketKopf kopf) {
        List<ItemStack> items = p.items();
        List<Stapel> liste = new ArrayList<>(items.size());
        for (ItemStack s : items) {
            liste.add(stapel(s));
        }
        bei(kopf, new Ereignis.Inhalt(kopf.laufendeNummer(), p.containerId(), liste));
    }

    private static void ereignis(ClientboundEntityEventPacket p, PaketKopf kopf) {
        if (p.getEventId() != 3) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        Entity tier = p.getEntity(client.level);
        // Ein Spieler, ein Armorstand (Namensschild, Lebensbalken) oder eine unbekannte Entity laesst nichts fallen
        if (tier instanceof LivingEntity && !(tier instanceof Player) && !(tier instanceof ArmorStand)) {
            bei(kopf, new Ereignis.MobTod(kopf.laufendeNummer(), tier.getId()));
        }
    }

    private static void entitaet(ClientboundAddEntityPacket p, PaketKopf kopf) {
        if (PaketAnsicht.istItemEntity(p)) {
            bei(kopf, new Ereignis.ItemEntity(kopf.laufendeNummer(), p.getId()));
        }
    }

    private static void aufsammeln(ClientboundTakeItemEntityPacket p, PaketKopf kopf) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && p.getPlayerId() == client.player.getId()) {
            bei(kopf, new Ereignis.Aufsammeln(kopf.laufendeNummer(), p.getItemId()));
        }
    }
}

package com.shokiteufel.shokimod.equipment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

/**
 * Ein Gegenstand als JSON und zurueck - mit dem Codec des Spiels, also mit allem, was an ihm haengt (Name, Beschreibung, Kopf-Textur, Farbe, Glanz ...).
 *
 * Herkunft der Idee: Skyblocker (LGPL-3.0, {@code SkyblockInventoryScreen}, {@code ItemUtils}). Von dort stammt der Weg: Der Gegenstand wird nicht in eigene Felder
 * zerlegt, sondern als Ganzes mit dem Codec gespeichert (Kennung, Anzahl, Komponenten), damit er nach dem Neustart genauso aussieht und dieselbe Beschreibung hat.
 * Die Umsetzung ist eigene Arbeit: Java, JSON statt NBT, und ein leerer Platz ist {@code {}}.
 *
 * Wer einen Gegenstand nicht mehr lesen kann (etwa nach einem Spiel-Update, das eine Komponente aendert), bekommt einen leeren Platz: Die Anzeige ist ein Gedaechtnis,
 * und ein Blick ins Menue /equipment fuellt sie neu. Die Registrierung ({@code lookup}) braucht der Codec fuer Komponenten, die auf Eintraege der Welt zeigen
 * (Verzauberungen); im Spiel ist es die der Verbindung.
 */
public final class EquipmentCodec {

    private EquipmentCodec() {
    }

    /** Der Gegenstand als JSON-Objekt; ein leerer Platz (oder einer, den der Codec nicht schreiben kann) wird {@code {}} */
    public static JsonElement encode(ItemStack stack, HolderLookup.Provider lookup) {
        if (stack == null || stack.isEmpty() || lookup == null) return new JsonObject();
        try {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, lookup);
            return ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack).result().filter(JsonElement::isJsonObject).orElseGet(JsonObject::new);
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    /** Der Gegenstand aus dem JSON-Objekt; {@code {}} und alles, was sich nicht lesen laesst, ergibt einen leeren Platz */
    public static ItemStack decode(JsonElement json, HolderLookup.Provider lookup) {
        if (json == null || lookup == null || !json.isJsonObject() || json.getAsJsonObject().size() == 0) return ItemStack.EMPTY;
        try {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, lookup);
            return ItemStack.OPTIONAL_CODEC.parse(ops, json).result().orElse(ItemStack.EMPTY);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }
}

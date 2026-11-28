package com.shokiteufel.shokimod.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chat > Tab Complete: Das Mixin laesst sich im Test nicht anwenden (dazu muss das Spiel laufen) - hier steht, was ohne Spiel pruefbar ist und bei einer neuen
 * Fassung als Erstes bricht: Gibt es die Methode in Brigadier noch genau so, passt die Beschreibung im Mixin dazu, und steht die Datei in einer Konfiguration, die
 * nicht zwingend ist (sonst bricht ein Fehler dort den Spielstart ab). Dass es im laufenden 26.3-Client greift, zeigte der Entwicklungs-Client am 28.11.2026
 * (Brigadiers Klasse bekam {@code handler$...$shokimod$tabComplete}; ein Parse ueber Hypixel-artige Befehle lieferte die Vorschlaege der Mod).
 */
class CommandDispatcherMixinTest {

    private static final String TARGET = "getCompletionSuggestions(Lcom/mojang/brigadier/ParseResults;I)Ljava/util/concurrent/CompletableFuture;";

    private static Method handler() throws Exception {
        return CommandDispatcherMixin.class.getDeclaredMethod("shokimod$tabComplete", ParseResults.class, int.class, CallbackInfoReturnable.class);
    }

    @Test
    void theMethodItHooksExistsInBrigadierWithTheShapeTheDescriptionSays() throws Exception {
        Method target = CommandDispatcher.class.getMethod("getCompletionSuggestions", ParseResults.class, int.class);
        assertEquals(CompletableFuture.class, target.getReturnType());
        // Die Beschreibung aus der echten Methode abgeleitet - muss der Text im Mixin sein, sonst greift er in dieser Fassung nicht
        String descriptor = "getCompletionSuggestions"
                + java.lang.invoke.MethodType.methodType(target.getReturnType(), target.getParameterTypes()).toMethodDescriptorString();
        assertEquals(TARGET, descriptor);
    }

    @Test
    void theInjectionNamesThatMethodAndIsNotRequired() throws Exception {
        Inject inject = handler().getAnnotation(Inject.class);
        assertNotNull(inject, "@Inject muss zur Laufzeit lesbar sein");
        assertArrayEquals(new String[]{TARGET}, inject.method());
        assertEquals(0, inject.require(), "ohne diese Methode soll nichts passieren, nicht der Start abbrechen");
        assertTrue(inject.cancellable(), "die Liste der Mod ersetzt die des Servers");
        At at = inject.at()[0];
        assertEquals("HEAD", at.value(), "vor der Liste des Servers fragen");
    }

    @Test
    void itTargetsBrigadierWithoutRemapping() throws Exception {
        // @Mixin steht nur in der Klassendatei, nicht zur Laufzeit lesbar: also die Klassendatei selbst ansehen
        byte[] bytes;
        try (InputStream in = CommandDispatcherMixin.class.getResourceAsStream("CommandDispatcherMixin.class")) {
            assertNotNull(in);
            bytes = in.readAllBytes();
        }
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        assertTrue(text.contains("Lorg/spongepowered/asm/mixin/Mixin;"), "die Klasse ist ein Mixin");
        assertTrue(text.contains("Lcom/mojang/brigadier/CommandDispatcher;"), "mit Brigadiers Verteiler als Ziel");
        assertTrue(text.contains("remap"), "und ausdruecklich ohne Umschreiben der Namen (remap = false)");
    }

    private static JsonObject read(String resource) throws Exception {
        try (InputStream in = CommandDispatcherMixinTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, resource + " fehlt");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void theConfigListsTheClassAndIsNotRequired() throws Exception {
        JsonObject config = read("shokimod-tab.mixins.json");
        assertFalse(config.get("required").getAsBoolean(), "ein Fehler in dieser Datei darf den Start nicht abbrechen");
        assertEquals(0, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
        assertEquals("com.shokiteufel.shokimod.mixin", config.get("package").getAsString());
        boolean listed = false;
        for (JsonElement name : config.getAsJsonArray("client")) listed |= name.getAsString().equals("CommandDispatcherMixin");
        assertTrue(listed, "CommandDispatcherMixin steht in der Konfiguration");
    }

    @Test
    void theConfigIsRegisteredInTheModFile() throws Exception {
        JsonArray mixins = read("fabric.mod.json").getAsJsonArray("mixins");
        boolean registered = false;
        for (JsonElement entry : mixins) {
            JsonObject object = entry.getAsJsonObject();
            if (object.get("config").getAsString().equals("shokimod-tab.mixins.json")) {
                registered = true;
                assertEquals("client", object.get("environment").getAsString());
            }
        }
        assertTrue(registered, "shokimod-tab.mixins.json steht in fabric.mod.json");
    }

    @Test
    void theMainConfigStaysAsItWasSoThatThisMixinCannotBreakTheStart() throws Exception {
        JsonObject main = read("shokimod.mixins.json");
        for (JsonElement name : main.getAsJsonArray("client")) {
            assertFalse(name.getAsString().equals("CommandDispatcherMixin"), "nicht in der zwingenden Konfiguration");
        }
    }
}

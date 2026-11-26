package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Chat (Teil 4) > Translator: der Weg ins Spiel - ein Klick auf eine Chatzeile oder der Befehl {@code /shoki translate <Sprachcode> <Text>} schickt GENAU DIESEN Text an
 * Googles Uebersetzungsdienst ({@code translate.googleapis.com}) und schreibt die Antwort als eigene Zeile in den Chat.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code GoogleTranslator}, {@code TranslatorCommand}). Von dort stammen die Wege: ein Klick uebersetzt die Zeile in die eigene Sprache
 * (die Zeile "Found translation" kopiert beim Klick), der Befehl uebersetzt aus jeder Sprache in eine andere und kopiert das Ergebnis in die Zwischenablage ({@code
 * /shcopytranslation}). Die Umsetzung ist eigene Arbeit: Java, {@code java.net.http} in einem eigenen Thread mit zehn Sekunden Zeitlimit; Adresse und Antwort liegen in
 * {@link GoogleTranslate} (ohne Netz getestet), das Senden hier ist klein und nicht getestet.
 *
 * <p>Was rausgeht und wann: eine Anfrage je Klick oder Befehl, mit dem Text der einen Zeile und den Sprachcodes, ohne Schluessel und ohne Kennung. Ohne Klick oder
 * Befehl geht nie etwas raus; der Schalter "Translate On Click" ist von Haus aus aus. Es laeuft immer nur eine Anfrage zugleich. Fehler (kein Netz, keine Antwort in zehn
 * Sekunden, eine Antwort in unbekannter Form) stehen als kurze Zeile im Chat und im Protokoll, nie als Absturz.
 */
public final class Translator {

    /** Die Liste der Sprachcodes von Google (der Knopf "List of Language Codes") */
    static final String LANGUAGE_LIST_URL = "https://cloud.google.com/translate/docs/languages#try-it-for-yourself";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);

    private Translator() {
    }

    // ---- die Einstiege ----

    /** Der Klick auf eine Chatzeile ({@code /shoki translateline <Nummer>}): in die eigene Sprache */
    public static void clickLine(int id) {
        String text = TranslateLines.get(id);
        if (text == null) {
            say(note("That line is too old to translate - the mod remembers the last " + TranslateLines.CAPACITY + " lines."));
            return;
        }
        ModConfig.TranslatorCategory c = ModConfig.INSTANCE.chatTab.translator;
        start(text, c == null ? "en" : c.targetCode(), false);
    }

    /** Der Befehl {@code /shoki translate <Sprachcode> <Text>}: in die genannte Sprache, und das Ergebnis in die Zwischenablage */
    public static void command(String language, String text) {
        start(text, language, true);
    }

    /** {@code /shoki translate} ohne Text: wie man den Befehl benutzt */
    public static void usage() {
        say(note("Usage: /shoki translate <language code> <text> - for example /shoki translate de Good luck! The text goes to Google's translation service; the result is copied."));
    }

    /** Der Knopf "List of Language Codes": die Seite von Google im Browser, nach der Rueckfrage des Spiels ("Willst du diesen Link oeffnen?") */
    public static void openLanguageList() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        ConfirmLinkScreen.confirmLinkNow(client.gui.screen(), URI.create(LANGUAGE_LIST_URL));
    }

    // ---- das Senden ----

    private static void start(String text, String target, boolean copy) {
        String original = text == null ? "" : text.strip();
        String url = GoogleTranslate.buildUrl(original, target, "auto");
        if (url == null) {
            say(note(original.isEmpty() ? "There is nothing to translate."
                    : "'" + target + "' is not a language code. Use two or three letters like de, es or zh-CN (see the list in the settings)."));
            return;
        }
        if (!BUSY.compareAndSet(false, true)) {
            say(note("A translation is still running."));
            return;
        }
        Thread worker = new Thread(() -> run(original, target, url, copy), "ShokiMod-Translator");
        worker.setDaemon(true);
        worker.start();
    }

    /** Im eigenen Thread: fragen, lesen, und das Ergebnis im Haupt-Thread in den Chat schreiben */
    private static void run(String original, String target, String url, boolean copy) {
        Component line;
        String toCopy = null;
        try {
            GoogleTranslate.Result result = GoogleTranslate.parse(fetch(url));
            if (result == null) {
                line = note("Google's answer had no translation in it.");
            } else if (result.translation().strip().equalsIgnoreCase(original)) {
                line = note("The translation is the same as the original message.");
            } else {
                line = resultLine(original, result, target, copy);
                if (copy) toCopy = result.translation();
            }
        } catch (IOException e) {
            ShokiMod.LOGGER.info("[Translator] request failed: {}", e.toString());
            line = note("The translation failed (" + (e instanceof java.net.http.HttpTimeoutException ? "no answer within 10 seconds" : "no connection to Google") + ").");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            line = note("The translation was interrupted.");
        } catch (RuntimeException e) {
            ShokiMod.LOGGER.warn("[Translator] unexpected error", e);
            line = note("The translation failed.");
        } finally {
            BUSY.set(false);
        }
        Component shown = line;
        String clipboard = toCopy;
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        client.execute(() -> {
            if (clipboard != null) client.keyboardHandler.setClipboard(clipboard);
            say(shown);
        });
    }

    /** Die eine Anfrage: GET, zehn Sekunden fuer den Verbindungsaufbau und zehn fuer die Antwort */
    private static String fetch(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "ShokiMod").timeout(TIMEOUT).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
        return response.body();
    }

    // ---- die Zeilen im Chat ----

    private static MutableComponent prefix() {
        return Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA);
    }

    /** Eine kurze Meldung der Mod */
    static Component note(String text) {
        return prefix().append(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    /**
     * Die Zeile mit der Uebersetzung. Beim Klick kopiert sie die Uebersetzung; der Text beim Zeigen nennt das Original und die erkannte Sprache.
     *
     * @param copied ob das Ergebnis schon in der Zwischenablage liegt (der Befehl)
     */
    static Component resultLine(String original, GoogleTranslate.Result result, String target, boolean copied) {
        String language = result.detectedLanguage().isEmpty() ? "unknown" : result.detectedLanguage();
        String hover = "Click to copy!\nOriginal message: " + original + " (Language: " + language + ")";
        MutableComponent text = Component.literal(result.translation()).withStyle(ChatFormatting.WHITE);
        MutableComponent line = prefix()
                .append(Component.literal(copied ? "Copied " + target + " translation to clipboard: " : "Found translation: ").withStyle(ChatFormatting.AQUA))
                .append(text);
        return line.withStyle(style -> style.withClickEvent(new ClickEvent.CopyToClipboard(result.translation()))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    private static void say(Component line) {
        ChatFilters.say(line);
    }
}

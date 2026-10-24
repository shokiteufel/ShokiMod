# Herkunft der Funktionen

Diese Datei beantwortet eine einzige Frage: **Was in ShokiMod kommt woher?**

Sie ist keine Höflichkeit, sondern Pflicht. ShokiMod steht unter LGPL-3.0, und wer
fremde Arbeit einbaut, nennt sie beim Namen — auch dann, wenn nur die Idee übernommen
wurde und keine einzige Zeile.

Gemessen am Stand vom 13.09.2026 (Version 1.6.3).

---

## Kurzfassung

ShokiMod ist ein **Fork von [GanKura](https://github.com/GanKuraDee/GanKura)**. Das
bleibt es auch: Die Vorgeschichte steckt im Repo, die Lizenz ist von dort geerbt, und
ein paar Dateien stehen noch fast unverändert da.

Ein *Abklatsch* von GanKura ist es nicht mehr. Der Vergleich gegen den Abzweigpunkt
(`39d3901`, GanKura 6.4.0 vom 22.08.2026) über 143 eigene Commits:

| | Zeilen | Anteil |
|---|---:|---:|
| praktisch unverändert aus GanKura | 532 | 2,6 % |
| aus GanKura, überarbeitet | 475 | 2,3 % |
| von ShokiTeufel | 19 337 | **95,1 %** |

Von GanKuras 111 Java-Dateien sind 83 gelöscht, 89 neue sind dazugekommen. Sechs
Dateien sind noch zu über 95 % Original.

Die treffendere Beschreibung ist deshalb: **ein Fork von GanKura, in dem von GanKura
kaum noch etwas steckt — und ein Zusammenschnitt von Funktionen, die anderen Mods
nachgebaut sind, plus einem größeren eigenen Teil.**

---

## Was von GanKura übrig ist

Fast unverändert (über 95 % gleich):

| Datei | Was sie tut |
|---|---|
| `ColorPickerScreen`, `ColorPalette`, `ColorSwatchButton` | Der Farbwähler, den alle Marker benutzen |
| `ScoreboardUtils` | Sidebar auslesen |
| `FloorDropHandler` | Boden-Fundstücke markieren (GanKura hatte es seinerseits von Skyblocker) |
| `HudRenderMixin`, `GizmoTextFogMixin` | Zeichen-Einhängepunkte |

Überarbeitet, aber erkennbar GanKura:

`BossNameplateRenderer`, `EntityTracerRenderer`, `PlayerGlowingMixin`,
`ArmorStandHeadOnlyGlowMixin`, `FloorDropParticleMixin`, `LocationScanner`,
`TabListScanner`, `ModMenuIntegration`, `WorldTextRenderMixin`.

Umgeschrieben bis zur Unkenntlichkeit, Idee von GanKura:

`EntityHighlightManager` (2007 → 388 Zeilen), `MobVisual` (432 → 46),
`WorldTextRenderer` (259 → 73), `GameState` (307 → 29), `ModConstants` (233 → 22),
`HudRenderer`, `NetworkHandler`, `EntityGlowingMixin`, `DayHud`.

**Was es aus GanKura nicht mehr gibt:** die kompletten Area-Minibosse (Broodmother,
Ashfang, Golem, Dragon, Magma Boss …) mit Stage Announce, HP-HUD, DPS-Rechner und
Loot-Quality; die Custom Waypoints; Armor-, Equipment-, Quiver-, Ferocity-, Yaw/Pitch-
und Armor-Stack-HUD; Warp-Cooldown-Queue; Server-Reboot-Alarm; die Menü-Tastenkürzel.
Nachgeprüft: Keines dieser Wörter kommt im heutigen Quelltext noch vor.

---

## Aus anderen Mods nachgebaut

### Critter Safari Tracker (MIT, Rok)

Die gesamte Safari-Seite geht auf crittermod zurück.

| Funktion | Was übernommen wurde |
|---|---|
| Critter-Artenliste | Arten und Stückzahlen je Biom (`Critters`) |
| „Fehlt noch"-Anzeige | Aufbau und Zeilen (`MissingHud`) |
| Fortschritts-Anzeige | Nachbau des CritterHud (`ProgressHud`) |
| Nest-Zähler | Nachbau des NestTracker |
| Wände und Rockmite Mounds | Koordinaten und Erkennungsregel (`SafariExtras`) |
| Vollbild-Einblendung | Aufbau des FullScreenAlert (Vorlage „Alert band“ in `BannerDesign`, früher `AlertBanner`) |
| Kasten-Layout | Der Panel-Aufbau beider Anzeigen (`HudPanel`) |
| Biom-Farben | Reihenfolge und Farben (`SafariBiome`) |
| Shiny-Erkennung | Die Prüfung hinter dem Präfix (`SparklingTarget`) |
| Welttext-Umriss | Nur Rahmen, keine Füllung |

### Skysoft (LGPL-3.0-only / LGPL-2.1-only, Akinsoft)

| Funktion | Was übernommen wurde |
|---|---|
| Rare Drop Titles | Der Grundaufbau des Fund-Alarms (`RareLootHandler`) |
| Rare Loot Sharing | Das Teilen in Party und Gilde, samt Vorlagen-Wortlaut |
| Fund-Erkennung | Portiert aus RareLootChatParser, RareLootItemIds, RareLootDisplayNames |
| Preis-Auflösung | Reihenfolge und Quellen aus RareLootValueResolver (`ItemValue`) |
| Profit/h | Die Idee, Pausen nicht mitzuzählen (`HuntingTracker`) |
| Fund-Erkennung am Inventar | Der Weg selbst: jeden Tick nachzählen statt den Chat zu lesen, mit Beruhigungszeit nach einem Wechsel und Blindheit bei offenem Behälter — aus SkyBlockInventoryChanges / ProfitTrackerItemTracking (`ItemChanges`) |
| Profit-Tracker | Aufbau und Bedienidee: Auswahl je Item, eigene Verkaufsart je Item, Preisquellen Instant Sell / Sell Order / NPC (`ProfitTracker`, `ProfitItemScreen`) |
| Item aus dem Inventar in den Tracker nehmen | Der Weg „Add Item“, Betriebsart „Inventory“: ein Item im eigenen Inventar anklicken (`ProfitTrackerHudControls.wasInventoryItemAdded`, `TrackedItemSelectionPanel`). Gebucht wird anders: Skysoft nimmt das Item in eine feste Liste auf, hier kommt der Stapel als Fund in den Kasten (`InventoryPick`, `ProfitTracker.takeIn`) |

### Skyblocker (LGPL-3.0-or-later, SkyblockerMod)

| Funktion | Was übernommen wurde |
|---|---|
| Chat-Regeln | Funktionsumfang: ausblenden, ersetzen, Action Bar, Einblendung, Toast, Ton |
| Gebiets-Liste | Location-Enum als Grundstock (`KnownAreas`) |
| Toast | Aufbau des BasicToast (`ShokiModToast`) |
| Special Effects | Die Erkenntnis, welche zwei Spielfunktionen das können (`SpecialEffects`) |
| Dye-Drop-Zeile | Das Muster, an dem Hypixels Meldung abzulesen ist |
| Preis-Rückfall | hysky.de als Tiefstpreis-Quelle |
| Floor Drops | Mittelbar — GanKura hatte es von dort |
| Eigener Slayer-Boss | Woran man ihn erkennt: die Zeile „Spawned by: <Name>“ unter dem Boss, und die Namensschilder des Bosses als Gegenprobe (`SlayerBossTarget`, `EntityHighlightManager`) |
| Slayer-Boss erschienen | Woran man es sieht: die Zeile „Slay the boss!“ im Kasten „Slayer Quest“ der Seitenleiste (`SlayerBossSpawn`) |
| Slayer-Minibosse | Die Namen der Minibosse je Slayer (`SlayerMinibossTarget`) - in Skyblockers `SlayerType` und SkyHanni gleich |

### SkyOcean (SkyOcean License v1 — Quelltext MIT, meowdding)

Der Hotspot-Kreis beim Angeln: Erkennung über den unsichtbaren Armorstand, Kreis in
der Farbe des Bonus, Warnung beim Verschwinden (`HotspotTracker`, `HotspotRenderer`).

Die Leichen-Stellen im Glacite Mineshaft: die Funktionsidee aus `CorpseWaypoint` —
Bauplan aus der Seitenleiste lesen und die möglichen Stellen dieses Schachts
einblenden. Eigene Umsetzung in Java (`MineshaftState`, `MineshaftCorpses`); von
SkyOcean stammt der Gedanke, nicht der Code.

### SkyblockCollectionTracker (LGPL-2.1-only, ChindeaOne)

Der Gedanke, die Schacht-Marker an eine Bedingung zu haengen: Dort erscheinen die
Routen nur in Schaechten mit genug Lapis-Leichen. Übernommen ist die Idee, nicht der
Code — und hier steht die Bedingung nicht fest, sondern je Bauplan mit eigener Zahl
je Leichen-Sorte (`MineshaftRule`, `MineshaftRuleScreen`).

Dazu der Gedanke, neben Zuwachs und Tempo auch die naechste Stufe der Collection zu
zeigen und aus dem Tempo zu sagen, wann sie erreicht ist. Wie die Stufe gefunden wird,
ist hier anders: SCT holt den Stand ueber seinen eigenen Server
(`api.skyblockcollections.com`, Anmeldung per Mojang-Sitzung) und stellt das Zaehlen ein,
wenn der nicht erreichbar ist; hier stehen die Stufen in Hypixels eigener, schluessellos
abrufbarer Liste, die die Mod fuer die Namen ohnehin schon holt (`CollectionData`).

### Meowdding-Repo (MIT, meowdding)

Die Koordinaten der Leichen-Stellen selbst: `repo/mining/mineshaft_corpses` — sechzehn
Baupläne mit ein bis drei Ausführungen, von Spielern zusammengetragen. Die Datei wird
zur Laufzeit geholt und liegt im Config-Ordner, statt mitgeliefert zu werden: So kommen
neu gefundene Stellen von selbst dazu.

### SkyBlock API (MIT, thatgravyboat)

Wo Hypixel den Schacht hinschreibt: in der untersten Zeile der Seitenleiste, hinter
Datum und Server, als `TUNG_1` (Bauplan und Ausführung). Das ist eine Tatsache über
Hypixels Anzeige — übernommen wurde das Muster, kein Code.

### RiccioFishingUtils (GPL-3.0-or-later, Riccio)

Die Kuchen-Erinnerung (`CakeReminder`): „Yum! You gain … for 48 hours!" mitlesen, je
Kuchen den Zeitpunkt merken, nach 48 Stunden melden, und die Liste leeren, sobald die
Tab-Liste wieder die volle Zahl Century Cakes zeigt.

**Abgeglichen am 13.09.2026** gegen RFUs
[`CakeExpiredAlert.kt`](https://github.com/Ricciow/RiccioFishingUtils-Modern/blob/main/src/main/kotlin/cloud/glitchdev/rfu/feature/other/CakeExpiredAlert.kt),
Zeile für Zeile:

| | RFU | ShokiMod |
|---|---|---|
| Ablauf-Logik | 48 h, `lastOutdated`-Differenz, Tab-Abgleich bei `current == total` | **gleich** |
| Muster (Chat, Tab) | zwei Regexe | **zeichengleich** (nur `^`/`$` ergänzt) |
| Sprache und Aufbau | Kotlin, drei getrennte Tick-Schleifen (20/300/3000) | Java, eine Schleife alle 20 Ticks mit Verzweigung |
| Datenhaltung | eigene Datenklassen `CakesEntry`/`Cake` mit `Instant` | `Map<String, Long>` in der Mod-Einstellung |
| Wiederholung | fest alle 3000 Ticks | einstellbar in Minuten |
| Zurücksetzen | eigener Befehl `/rfuclearcakes` | Knopf in den Einstellungen |
| Zusätzlich | — | `[Get Cakes!]` mit Klick-Befehl, Ton, Testknopf |

**Einordnung: inspiriert, nicht übernommen.** Der Ablauf ist RFUs, die Umsetzung ist
eigene Arbeit — andere Sprache, anderer Aufbau, andere Datenhaltung, andere
Bedienung. Wörtlich gleich sind allein die beiden Muster, und die beschreiben
Hypixels Chat- und Tab-Zeilen, nicht RFUs Erfindung.

Beim Abgleich fiel auf, dass die **Meldungstexte** wörtlich RFUs waren („… of your
cakes just expired!", „You have … expired cakes!", „Expired Cakes:"). Das sind
Riccios Sätze, keine Tatsachen über Hypixel. Sie sind durch eigene ersetzt — damit
steht nichts Geschriebenes aus einer GPL-Mod in dieser LGPL-Mod, und die Frage
erledigt sich, statt offen zu bleiben.

---

### Odin (BSD-3-Clause, Odtheking, `OdinFabric`)

Aus Odin kommen die Funktionen im Reiter **Odin**. Jede ist in Java nachgebaut; die Regel stammt aus Odins Kotlin-Quelltext, und was
anders ist, steht dabei. Der Lizenztext folgt unten, wie die Lizenz es fuer die ausgelieferte Mod verlangt (Bedingung 2).

- **Hide Players** (`features/impl/render/HidePlayers.kt`, `EntityRendererMixin`) — Version 1.18.2. Ein Spieler wird nicht gezeichnet, wenn er
  ein echter Spieler ist (Kennung der Version 4; NPCs haben eine andere), nicht man selbst, und entweder alle ausgeblendet werden oder er
  naeher als die eingestellte Entfernung steht; auf Wunsch nur im Dungeon. Nachgebaut als `HidePlayers` und `HidePlayersMixin`. Anders: Der
  Dungeon wird aus der Zeile der Tab-Liste ("Area: ..." oder "Dungeon: ..." mit "Catacombs") **und** aus dem Kasten der Seitenleiste
  gelesen, zweimal je Sekunde zwischengespeichert; der Einsprung versucht beide Formen von `shouldRender` (mit und ohne Teilticks) und steht in
  einer eigenen, nicht zwingenden Mixin-Konfiguration, damit eine geaenderte Methode das Spiel nicht beim Start abbricht.
- **Camera** (`features/impl/render/Camera.kt`) — Version 1.18.3. Steht die Kamera am Ende eines Ticks auf der dritten Person von vorn, wird sie auf
  die erste Person zurueckgesetzt. Nachgebaut als `FrontCamera`; die Regel ist dieselbe, die Einstellung ist ein Schalter in der Klappe "Camera"
  im Reiter Odin.
- **No Cursor Reset** (`features/impl/skyblock/NoCursorReset.kt`, `MouseHandlerMixin`) — Version 1.18.4. Oeffnet sich ein Kisten-Fenster weniger als die
  eingestellte Zeit (Standard 150 ms, 0 bis 1000 in Zehnerschritten) nach dem Schliessen des letzten Fensters, wird der Mauszeiger an die Stelle gestellt, an
  der er beim Schliessen stand, statt in die Mitte. Nachgebaut als `NoCursorReset` und `MouseHandlerMixin`. Anders: Die Zeit nimmt der Mixin beim Schliessen
  selbst (Odin fuehrt sie jeden Tick nach); die Einspruenge sitzen an den Raendern von `grabMouse` und `releaseMouse` statt mitten in den Methoden; der
  Zeiger wird ueber GLFW gesetzt (`InputConstants.releaseMouse` gibt es nicht in jeder Version); und welches Fenster sich oeffnet, kommt aus dem Fabric-Ereignis
  `ScreenEvents.AFTER_INIT`, weil der Zugriff auf das aktuelle Fenster je Version anders heisst.
- **Player Size** und die Ping-Anzeige: siehe weiter unten unter "Nur nachgeschlagen". Seit 1.18.1 steht Player Size als Unterreiter im Reiter Odin.

Der Lizenztext von Odin (Quelle: `LICENSE` im Odin-Repository):

    BSD 3-Clause License

    Copyright (c) 2025, odtheking

    Redistribution and use in source and binary forms, with or without
    modification, are permitted provided that the following conditions are met:

    1. Redistributions of source code must retain the above copyright notice, this
       list of conditions and the following disclaimer.

    2. Redistributions in binary form must reproduce the above copyright notice,
       this list of conditions and the following disclaimer in the documentation
       and/or other materials provided with the distribution.

    3. Neither the name of the copyright holder nor the names of its
       contributors may be used to endorse or promote products derived from
       this software without specific prior written permission.

    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
    AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
    IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
    DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
    FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
    DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
    SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
    CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
    OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
    OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

### Feesh (Apache-2.0, MoonTheSadFisher, `Sleepy-Panda/Feesh`)

Feesh ist die Fischerei-Mod, aus der die Funktionen unter **Fishing** (Rare catch, Sea creature HP, Trash books, Mute, Out of air, Frozen Blaze) und
Teile des Profit-Trackers stammen. Die Lizenz ist die **Apache License 2.0**: `LICENSE` im Repository und `"license": "Apache 2.0"` in `fabric.mod.json`
sagen dasselbe. Der Text ist derselbe wie beim Abschnitt IQ weiter unten und steht dort einmal vollstaendig. Ein eigener Urheberhinweis steht weder in
Feeshs `LICENSE` (die Vorlage am Ende ist nicht ausgefuellt) noch in den Quelldateien, und es gibt keine NOTICE-Datei; als Autor nennt `fabric.mod.json`
MoonTheSadFisher. Die Funktionen sind in Java nachgebaut; uebernommen sind nur Daten und Muster, und wo das so ist, steht es bei der Funktion
(zum Beispiel die Tabelle der Meeresbewohner bei "Rare catch").

Die aelteren Feesh-Eintraege (Pet-Stufen, Rare catch, HP-Anzeige, Banner, Buecher, Mute, Out of air, Hotspot teilen) stehen weiter unten unter
"Nur nachgeschlagen"; fuer sie gilt dieselbe Lizenz auf dieselbe Weise.

- **Frozen Blaze erkennen** (`utils/PlayerUtils.kt`, `isInFrozenBlaze`, `FROZEN_BLAZE_ARMOR_IDS`) — Version 1.18.7. Das Set gilt als getragen, wenn alle vier
  Ruestungsplaetze ein Stueck der Frozen Blaze tragen: erkannt an der Kennung `FROZEN_BLAZE_HELMET`, `_CHESTPLATE`, `_LEGGINGS` oder `_BOOTS`, ersatzweise am Namen,
  und das alle fuenf Ticks statt bei jeder Abfrage. Nachgebaut als `FrozenBlaze`. Anders: Die Erkennung laeuft nur, solange eine der Funktionen unter
  Fishing > Frozen Blaze an ist. Darauf setzt die erste eigene Funktion dieses Unterreiters auf, "Hide players": Wer das Set traegt, sieht keine anderen
  Spieler (Feesh blendet an dieser Stelle nur Chatzeilen aus; das Ausblenden der Spieler ist eigen und nutzt den Einsprung von "Hide Players" aus dem Reiter Odin).
- **Hide lootshare messages when in Frozen Blaze armor** (`features/chat/HideLootshareMessages.kt`, `settings/categories/Chat.kt`) — Version 1.18.8. Solange das ganze
  Set getragen wird, werden die Chatzeilen "LOOT SHARE You received ... for assisting <Spieler>!" ausgeblendet, die jeder Kill der Party bringt. Feesh hat dafuer ein
  Muster (`LOOTSHARE_ASSIST_PATTERN`) und blendet nur diese Zeilen aus - das README sagt zwar "all LOOT SHARE messages", der Quelltext tut es nicht; hier gilt der
  Quelltext. Nachgebaut als `FrozenBlaze.hidesLootshare`. Anders: Das Muster kennt auch die Schreibweise, die Hypixel heute verwendet (ein Ausrufezeichen hinter "LOOT SHARE",
  ein Zaehler in Klammern am Ende), und die Zeile wird erst ausgeblendet, nachdem der Jagd-Zaehler und der Rare-Loot-Alarm sie gelesen haben - bei Shards ist sie die einzige
  Meldung. Die Zeilen "from ... catching ..." bleiben.
- **Nearby rare** (`features/overlays/SeaCreatureHpTracker.kt`, `utils/EntityUtils.kt`: Namensschild "[Lv600] Name 69M/100M", Mob-Kennung = Kennung des Schildes minus eins) — Version 1.18.12.
  Die Erkennung der Bewohner aus den Namensschildern ist die der HP-Anzeige (siehe unten, "die HP-Anzeige der Meeresbewohner"), nachgebaut als `SeaCreatureHp.parse`.
  Neu ist, was darauf aufsetzt: eine Ansage je Mob, wenn ein seltener in den Umkreis kommt, und ein Kasten um seine Trefferbox (`SeaCreatureNear`,
  `SeaCreatureBoxRenderer`). Feesh hat beides nicht - sein Alarm liest nur den Chat. Die Tabelle der seltenen Bewohner ist dieselbe wie bei "Rare catch".

### IQ (Apache-2.0, PeHenrii und DarkJota, `iqaddons/IQ`)

Aus IQ, einer Kuudra-Mod fuer Hypixel SkyBlock, kommen die Funktionen im Reiter **IQ**. Sie sind in Java nachgebaut; die Regeln, Zahlen und der
Wortlaut stammen aus IQs Quelltext (Zweig `ver/26.1.x`), und was anders ist, steht dabei. Die Entwickler sind PeHenrii (Lead) und DarkJota.

**Zur Lizenz:** IQ ist nicht eindeutig ausgezeichnet. Die Datei `LICENSE` im Repository und die in IQs Jar mitgelieferte `LICENSE_IQ` sind der
Text der **Apache License 2.0**, und GitHub erkennt sie so; `fabric.mod.json` und das README nennen dagegen "MIT". Befolgt wird die strengere, die Apache
License 2.0 - was sie verlangt, erfuellt auch die MIT-Bedingung. Verlangt (Abschnitt 4): den Lizenztext mitzugeben (unten, und diese Datei liegt im
Jar), geaenderte Dateien als geaendert zu kennzeichnen (alle Dateien unten sind neu geschrieben und tragen den Hinweis auf IQ im Kopf), die Hinweise
des Originals zu behalten (die Quelltextdateien tragen keinen Urheberhinweis; die Entwickler stehen hier und im Kopf jeder Datei) und eine
NOTICE-Datei zu uebernehmen, falls es eine gibt (im IQ-Repository gibt es keine). Die Namen "IQ", PeHenrii und DarkJota stehen nur als
Herkunftsangabe da (Abschnitt 6).

- **Fire Veil Overlay** (`FireVeilOverlayFeature`, `FireVeilOverlayManager`, `FireVeilOverlayWidget`, `PhaseTwoConfig.FireVeilOverlayConfig`,
  `WorldRenderUtils.drawThickCircleOutline` und `drawCircleWireframeWall`) — Version 1.18.5. Nach einem Rechtsklick mit dem Fire Veil Wand (Item
  `FIRE_VEIL_WAND`, Rueckfall: der Name enthaelt "fire veil") zeichnet die Mod fuenf Sekunden lang den Wirkungskreis um die Spielerfigur: ein dicker
  Ring (Radius 3,5 Bloecke, Dicke 0,08, 60 Segmente) oder eine Wand (drei Ringe auf Boden, halber und voller Hoehe von einem Block, dazu senkrechte
  Striche), neun Sekunden lang werden die Flammen des Spiels ausgeblendet, ein Kasten zaehlt die Zeit herunter ("Fire Veil: 4.83s", Farbstufen
  gruen, gelb, orange, rot; danach zwei Sekunden "READY"), und beim Wiederwerfen kommt ein Ton (Notenblock-Pling, Lautstaerke 2, Tonhoehe 1,6).
  Einstellungen wie bei IQ: Render (Default / Wall / Circle, Standard Circle), Farbe (Standard orange, 255/170/0), Deckkraft (IQ: 200 von 255), durch
  Waende, Countdown, Ton. Nachgebaut als `FireVeil`, `FireVeilRenderer`, `FireVeilHud` und die Klappe "Fire Veil Overlay" im Reiter IQ.
  Anders: Der Hauptschalter steht standardmaessig **aus** (bei IQ an); die Deckkraft ist ein eigener Regler in Prozent (Standard 78), weil der
  Farbwaehler hier keine Deckkraft kennt; gezeichnet wird mit Minecrafts Gizmos statt mit IQs eigenen Zeichenschichten; der Wurf kommt aus dem
  Rechtsklick-Ereignis von Fabric; ausgeblendet werden die Flammen, die der Server schickt (das Partikel-Paket), nicht jede Flamme im Spiel - Fackeln und
  Feuer in der Welt bleiben; der Kasten ist ein Kasten des HUD-Editors (`/shoki hud`) mit Lage, Groesse und Deckkraft.

Der Lizenztext von IQ (Quelle: `LICENSE` im IQ-Repository, gleichlautend mit `LICENSE_IQ` in IQs Jar; die Vorlage am Ende ist von der Lizenz selbst
und nicht ausgefuellt, ein eigener Urheberhinweis steht dort nicht):

    Apache License
                               Version 2.0, January 2004
                            http://www.apache.org/licenses/

       TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

       1. Definitions.

          "License" shall mean the terms and conditions for use, reproduction,
          and distribution as defined by Sections 1 through 9 of this document.

          "Licensor" shall mean the copyright owner or entity authorized by
          the copyright owner that is granting the License.

          "Legal Entity" shall mean the union of the acting entity and all
          other entities that control, are controlled by, or are under common
          control with that entity. For the purposes of this definition,
          "control" means (i) the power, direct or indirect, to cause the
          direction or management of such entity, whether by contract or
          otherwise, or (ii) ownership of fifty percent (50%) or more of the
          outstanding shares, or (iii) beneficial ownership of such entity.

          "You" (or "Your") shall mean an individual or Legal Entity
          exercising permissions granted by this License.

          "Source" form shall mean the preferred form for making modifications,
          including but not limited to software source code, documentation
          source, and configuration files.

          "Object" form shall mean any form resulting from mechanical
          transformation or translation of a Source form, including but
          not limited to compiled object code, generated documentation,
          and conversions to other media types.

          "Work" shall mean the work of authorship, whether in Source or
          Object form, made available under the License, as indicated by a
          copyright notice that is included in or attached to the work
          (an example is provided in the Appendix below).

          "Derivative Works" shall mean any work, whether in Source or Object
          form, that is based on (or derived from) the Work and for which the
          editorial revisions, annotations, elaborations, or other modifications
          represent, as a whole, an original work of authorship. For the purposes
          of this License, Derivative Works shall not include works that remain
          separable from, or merely link (or bind by name) to the interfaces of,
          the Work and Derivative Works thereof.

          "Contribution" shall mean any work of authorship, including
          the original version of the Work and any modifications or additions
          to that Work or Derivative Works thereof, that is intentionally
          submitted to Licensor for inclusion in the Work by the copyright owner
          or by an individual or Legal Entity authorized to submit on behalf of
          the copyright owner. For the purposes of this definition, "submitted"
          means any form of electronic, verbal, or written communication sent
          to the Licensor or its representatives, including but not limited to
          communication on electronic mailing lists, source code control systems,
          and issue tracking systems that are managed by, or on behalf of, the
          Licensor for the purpose of discussing and improving the Work, but
          excluding communication that is conspicuously marked or otherwise
          designated in writing by the copyright owner as "Not a Contribution."

          "Contributor" shall mean Licensor and any individual or Legal Entity
          on behalf of whom a Contribution has been received by Licensor and
          subsequently incorporated within the Work.

       2. Grant of Copyright License. Subject to the terms and conditions of
          this License, each Contributor hereby grants to You a perpetual,
          worldwide, non-exclusive, no-charge, royalty-free, irrevocable
          copyright license to reproduce, prepare Derivative Works of,
          publicly display, publicly perform, sublicense, and distribute the
          Work and such Derivative Works in Source or Object form.

       3. Grant of Patent License. Subject to the terms and conditions of
          this License, each Contributor hereby grants to You a perpetual,
          worldwide, non-exclusive, no-charge, royalty-free, irrevocable
          (except as stated in this section) patent license to make, have made,
          use, offer to sell, sell, import, and otherwise transfer the Work,
          where such license applies only to those patent claims licensable
          by such Contributor that are necessarily infringed by their
          Contribution(s) alone or by combination of their Contribution(s)
          with the Work to which such Contribution(s) was submitted. If You
          institute patent litigation against any entity (including a
          cross-claim or counterclaim in a lawsuit) alleging that the Work
          or a Contribution incorporated within the Work constitutes direct
          or contributory patent infringement, then any patent licenses
          granted to You under this License for that Work shall terminate
          as of the date such litigation is filed.

       4. Redistribution. You may reproduce and distribute copies of the
          Work or Derivative Works thereof in any medium, with or without
          modifications, and in Source or Object form, provided that You
          meet the following conditions:

          (a) You must give any other recipients of the Work or
              Derivative Works a copy of this License; and

          (b) You must cause any modified files to carry prominent notices
              stating that You changed the files; and

          (c) You must retain, in the Source form of any Derivative Works
              that You distribute, all copyright, patent, trademark, and
              attribution notices from the Source form of the Work,
              excluding those notices that do not pertain to any part of
              the Derivative Works; and

          (d) If the Work includes a "NOTICE" text file as part of its
              distribution, then any Derivative Works that You distribute must
              include a readable copy of the attribution notices contained
              within such NOTICE file, excluding those notices that do not
              pertain to any part of the Derivative Works, in at least one
              of the following places: within a NOTICE text file distributed
              as part of the Derivative Works; within the Source form or
              documentation, if provided along with the Derivative Works; or,
              within a display generated by the Derivative Works, if and
              wherever such third-party notices normally appear. The contents
              of the NOTICE file are for informational purposes only and
              do not modify the License. You may add Your own attribution
              notices within Derivative Works that You distribute, alongside
              or as an addendum to the NOTICE text from the Work, provided
              that such additional attribution notices cannot be construed
              as modifying the License.

          You may add Your own copyright statement to Your modifications and
          may provide additional or different license terms and conditions
          for use, reproduction, or distribution of Your modifications, or
          for any such Derivative Works as a whole, provided Your use,
          reproduction, and distribution of the Work otherwise complies with
          the conditions stated in this License.

       5. Submission of Contributions. Unless You explicitly state otherwise,
          any Contribution intentionally submitted for inclusion in the Work
          by You to the Licensor shall be under the terms and conditions of
          this License, without any additional terms or conditions.
          Notwithstanding the above, nothing herein shall supersede or modify
          the terms of any separate license agreement you may have executed
          with Licensor regarding such Contributions.

       6. Trademarks. This License does not grant permission to use the trade
          names, trademarks, service marks, or product names of the Licensor,
          except as required for reasonable and customary use in describing the
          origin of the Work and reproducing the content of the NOTICE file.

       7. Disclaimer of Warranty. Unless required by applicable law or
          agreed to in writing, Licensor provides the Work (and each
          Contributor provides its Contributions) on an "AS IS" BASIS,
          WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
          implied, including, without limitation, any warranties or conditions
          of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
          PARTICULAR PURPOSE. You are solely responsible for determining the
          appropriateness of using or redistributing the Work and assume any
          risks associated with Your exercise of permissions under this License.

       8. Limitation of Liability. In no event and under no legal theory,
          whether in tort (including negligence), contract, or otherwise,
          unless required by applicable law (such as deliberate and grossly
          negligent acts) or agreed to in writing, shall any Contributor be
          liable to You for damages, including any direct, indirect, special,
          incidental, or consequential damages of any character arising as a
          result of this License or out of the use or inability to use the
          Work (including but not limited to damages for loss of goodwill,
          work stoppage, computer failure or malfunction, or any and all
          other commercial damages or losses), even if such Contributor
          has been advised of the possibility of such damages.

       9. Accepting Warranty or Additional Liability. While redistributing
          the Work or Derivative Works thereof, You may choose to offer,
          and charge a fee for, acceptance of support, warranty, indemnity,
          or other liability obligations and/or rights consistent with this
          License. However, in accepting such obligations, You may act only
          on Your own behalf and on Your sole responsibility, not on behalf
          of any other Contributor, and only if You agree to indemnify,
          defend, and hold each Contributor harmless for any liability
          incurred by, or claims asserted against, such Contributor by reason
          of your accepting any such warranty or additional liability.

       END OF TERMS AND CONDITIONS

       APPENDIX: How to apply the Apache License to your work.

          To apply the Apache License to your work, attach the following
          boilerplate notice, with the fields enclosed by brackets "[]"
          replaced with your own identifying information. (Don't include
          the brackets!)  The text should be enclosed in the appropriate
          comment syntax for the file format. We also recommend that a
          file or class name and description of purpose be included on the
          same "printed page" as the copyright notice for easier
          identification within third-party archives.

       Copyright [yyyy] [name of copyright owner]

       Licensed under the Apache License, Version 2.0 (the "License");
       you may not use this file except in compliance with the License.
       You may obtain a copy of the License at

           http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing, software
       distributed under the License is distributed on an "AS IS" BASIS,
       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
       See the License for the specific language governing permissions and
       limitations under the License.

## Nur nachgeschlagen — kein Code

Diese Mods stehen hier, weil gegen sie geprüft wurde, nicht weil etwas von ihnen
drinsteckt.

- **SkyHanni** (LGPL-2.1, `SkyHanniStudios/SkyHanni`) — die Schadenszahl über einem getroffenen Mob (`HideDamageSplash`,
  `DamageIndicatorManager.isDamageSplash`, `damagePattern`), Version 1.18.9: ein Rüstungsständer mit Namen, höchstens 300 Ticks alt, dessen Name ohne
  Kommas nur aus einer Zahl mit ein paar Zeichen davor und dahinter besteht (✧ ✯ ⚔ + ❤ ♞ ☄ ✷ ﬗ). Das ist eine Beobachtung darüber, wie Hypixel diese Namen
  schickt, und kein Code: Das Muster ist hier selbst hingeschrieben (`FrozenBlaze.hidesDamage`), nur die Zeichen sind dieselben. Anders: SkyHanni blendet die
  Zahlen immer aus, wenn man es einschaltet; hier nur, solange man das ganze Frozen-Blaze-Set trägt (Fishing > Frozen Blaze > Hide damage numbers).
  *Korrektur:* An dieser Stelle stand bisher "AGPL-3.0". Die `LICENSE` im Repository ist die GNU Lesser General Public License v2.1 (am 24.10.2026 gelesen).
- **SkyHanni** (LGPL-2.1) — dagegen gegengeprüft wurden: die Sky-Mall-Platzierung, die
  Mengen in der Hunting Box, das Whale-Bait-Rezept und die vier Helme, an denen eine
  gefrorene Leiche zu erkennen ist (`LAPIS_ARMOR_HELMET`, `ARMOR_OF_YOG_HELMET`,
  `MINERAL_HELMET`, `VANGUARD_HELMET` — Lapis, Umber, Tungsten, Vanguard) sowie die
  Farben, an denen eine Edelstein-Ader zu erkennen ist (gefärbtes Glas bzw. Glasscheibe:
  rot = Ruby, lila = Amethyst, hellblau = Sapphire, blau = Aquamarine, braun = Citrine,
  grün = Peridot, lime = Jade, magenta = Jasper, weiß = Opal, schwarz = Onyx, gelb =
  Topaz, orange = Amber — nachgesehen in `OreBlock`). Aus `CorpseType` stammt zusätzlich,
  welcher Schlüssel zu welcher Leiche gehört: Tungsten und Umber je ihren, Vanguard den
  `SKELETON_KEY`, Lapis keinen — und die Farbe je Sorte (Lapis blau, Tungsten grau, Umber
  gold, Vanguard weiß). Das sind Tatsachen über Hypixel; Scanner und Marker hier sind
  eigener Code. Dagegen gehalten wurde außerdem das **Aussehen** seiner Wegweiser: Die
  Führungslinie beginnt zwei Blöcke vor der Kamera — am Fadenkreuz, nicht an den Füßen —
  und das Ziel wird gefüllt statt nur umrissen, mit einer Deckkraft, die mit der
  Entfernung wächst (`WorldRenderUtils.drawLineToCrosshair`, `drawWaypointFilled`).
  Abgelesen, nicht übernommen: Die Umsetzung hier nutzt die Gizmos der Spielversion,
  und die Steigung der Deckkraft ist flacher, weil eine Ader mehr als einen Block füllt.
  Die AGPL wäre mit der LGPL **nicht** vereinbar, deshalb ist hier bewusst nie Code
  übernommen worden, nur Tatsachen abgelesen. Auch der eigene Slayer-Boss ist so entstanden:
  Aus `Mob`, `MobFilter` und `MobUtils` stammt, dass Hypixel den Mob und seine Namensschilder
  nacheinander anlegt und der Besitzer in der Zeile „Spawned by: <Name>“ steht, zwei Kennungen
  hinter dem ersten Namensschild. Hier wird bis vier Kennungen zurück gesucht, weil nicht geprüft
  ist, ob es immer genau zwei sind, und danach in der Nähe. Kein Code übernommen. Dazu stammt aus
  `SlayerApi` die Zeile „Slay the boss!“ in der Seitenleiste als Zeichen, dass der eigene Boss da
  ist. Partikel nutzt SkyHanni dafür nicht, nur um die Spawn-Effekte auszublenden
  (`HideSlayerSpawnParticles`); hier gilt allein die Seitenleiste. Die Namen der Slayer-Minibosse
  (`SlayerMiniBossType`) stammen ebenfalls von dort und stimmen mit denen in Skyblockers
  `SlayerType` überein; Erkennung und Umriss sind eigener Code. SkyHanni zeichnet außerdem eine Linie
  zum Miniboss (`SlayerMiniBossFeatures`); die Idee ist dieselbe, die Umsetzung (nur die nächste Linie
  je Art, über die Linien der Mob-Markierung) ist eigen.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — dort wird ein Pet, das die Hoechststufe
  erreicht, in den Tracker gelegt: Die Zeile "Your … leveled up to level 100!" wird
  gelesen, und nur 100 und 200 zaehlen. Auch die Schreibweise der Kennung ist von dort
  uebernommen (`FLYING_FISH;4+100`) — zwei Mods, die dieselbe Ware gleich benennen,
  ersparen dem naechsten Leser eine Uebersetzung. Der Preis kommt hier aus der eigenen
  Auktions-Auswertung, nicht aus einer fremden Schnittstelle.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — die Einblendung, wenn ein Pet seine
  Höchststufe erreicht (`features/alerts/PetLevelUpAlert.kt`): ein Titel „X is maxed"
  mit der Stufe darunter, ein Ton, und wahlweise eine Zeile dazu, was das Hochziehen
  wert war. Nachgebaut, nicht übernommen — und mit einem Unterschied: Feesh meldet bei
  Stufe 100 und 200 gleichermaßen, hier zählt die Höchststufe des jeweiligen Pets. Ein
  Golden Dragon auf 100 ist nicht fertig, sondern halb fertig, und „is maxed" wäre dort
  falsch. Die beiden Preise kommen aus der eigenen Auktions-Auswertung statt aus zwei
  Einzelabfragen.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — die Form der Pet-Beutezeile
  (`events/publishers/RareDropsPublisher.kt`): "PET DROP!", davor optional die Seltenheit als
  Wort. Abgelesen als Zeilenformat, die Umsetzung (Kennung `NAME;Stufe`, Rettung aus dem Chat)
  ist eigen.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — der Alarm bei seltenen Meeresbewohnern
  (`features/alerts/RareCatchAlert.kt` und `constants/SeaCreatures.kt`). Hier ist zum
  ersten Mal nicht nur die Idee übernommen, sondern **Daten**: die Tabelle der neunzig
  Meeresbewohner mit dem Satz, mit dem Hypixel jeden Fang meldet, dazu Seltenheit und
  die Angabe, welche als selten gelten (26 von 90). Diese Sammlung *ist* die Arbeit —
  ohne sie erkennt eine Mod gar nichts, und sie Zeile für Zeile neu abzuschreiben
  brächte niemandem etwas. Übernommen sind außerdem die Muster, mit denen Feesh und
  SkyHanni ihre Funde in den Gruppenchat schreiben, damit der Alarm auch dann greift,
  wenn die anderen in der Gruppe eine andere Mod benutzen. Die Einblendung selbst ist
  die dieser Mod: dasselbe Banner, derselbe Ton und dieselbe Lautstärkeregel wie bei
  einem seltenen Fund.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — dort steht auch, dass George für einen
  Baby Yeti eine Million zahlt (`npcPrice = 1_000_000.0`). Nachgebaut ist das anders:
  Feesh trägt die Zahl je Fund von Hand ein und nimmt sie nur, wenn Basar und Auktionshaus
  nichts hergeben; hier kommt die ganze Liste aus dem NEU-Repo und gilt als Untergrenze,
  nicht als Rückfall — steht im Auktionshaus weniger, zählt trotzdem George.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — dort wurde nachgesehen, woher ein
  Essence-Zugang überhaupt zu erfahren ist. Feesh liest ihn für Ice Essence aus der
  Action Bar (`+100 Ice Essence`) und behilft sich gegen das Segment, das mehrere
  Sekunden stehen bleibt, mit der zuletzt gesehenen Zahl. Dass Hypixel den Zugang dort
  ankündigt, ist eine Tatsache über Hypixel; der Umgang mit dem Dauer-Segment ist
  derselbe Gedanke, hier je Sorte statt für eine. Der Stand aus dem Essence-Widget der
  Tab-Liste hat hier Vorrang, weil er nichts verlieren kann — Feesh liest ihn nicht.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — die Zeilen zu Deployables
  (`features/overlays/DeployablesTimer.kt`): „Your flare disappeared because you were too far
  away!" und „Your previous … was removed!". Das sind Texte von Hypixel, kein Code. Die Meldung,
  wenn ein Deployable abläuft (`DeployableReminder`), liest daneben „Your … despawned." Die
  Warnung davor (`DeployableCountdown`, seit 1.15.19) geht den Weg, den SkyHanni (`Deployable.kt`),
  Riccio's Fishing Utils und SkyCubed gehen: die Restzeit aus dem Namen des Rüstungsständers über
  dem Deployable, „Umberella 298s". Sie gilt deshalb für jedes Deployable in Reichweite, auch ein
  fremdes — der Ständer sagt nicht, wem es gehört.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — die HP-Anzeige der Meeresbewohner
  (`features/overlays/SeaCreatureHpTracker.kt`, Fassung 1.13.0, und das Lesen der Namensschilder in
  `utils/EntityUtils.kt`): die Namensschilder der Rüstungsständer im Umkreis von 30 Blöcken, der
  Reindrake auch weiter weg, die Zeilen nach niedrigsten Lebenspunkten, die Liste der Bewohner mit
  der Voreinstellung (achtundzwanzig), die Immune-Anzeige für die ersten fünf Sekunden und Nessies
  Flucht, der Ton beim Neuzugang (nicht beim Wiederkehren des Reindrake). Nachgebaut, nicht
  übernommen — und mit vier Unterschieden: Die Symbole links vom Namen und das Herz rechts fehlen,
  die Reihenfolge lässt sich auch nach Seltenheit wählen, die Namen stehen in der Farbe ihrer
  Seltenheit (abschaltbar; die Farben der Lebenspunkte bleiben die des Namensschilds), und die Auswahl
  der Bewohner steht im selben Fenster wie die des Alarms (`/shoki seacreatures`, Spalte HP) statt in
  einer eigenen Liste.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — der Banner bei seltenen Fängen
  (`utils/CommonUtils.kt` `showTitle`, `constants/SeaCreatures.kt` `getTitle`): Feesh zeigt den Fang als
  Titel des Spiels — der Name fett in der Farbe der Seltenheit, bei einem Double Hook ein rotes „X2",
  bei einem Mythic ein flackerndes goldenes „x" davor und dahinter, darunter der Name des Fängers; zwei
  Sekunden stehen, eine halbe Sekunde ausblenden. Die Voreinstellung „Feesh" (Hintergrund „Like the game's
  title") zeichnet es mit demselben Aufruf wie das Spiel selbst und stimmt Pixel für Pixel mit dessen
  Titel überein. Übernommen ist die Gestalt und der Aufbau der Zeilen, kein Code.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — die Markierung der Bücher, die man loswerden will
  (`features/items/background/TrashBooksHighlighter.kt`, `BackgroundHighlighterManager.kt`,
  `ItemUtils.getEnchantedBookName`): Ein Slot mit einem „Enchanted Book" bekommt einen roten Untergrund
  (`0x90FF0000`), den Namen des Buches liest Feesh aus der ersten Beschreibungszeile ohne „Combinable in
  Anvil", gezeichnet wird in `extractSlot` vor dem Aufruf, der das Stück zeichnet (`SlotBackgroundMixin`).
  Nachgebaut — mit vier Unterschieden: Die Bücher stehen in einem Fenster (die bisher im Inventar gesehenen
  und ein Feld für einen neuen Namen) statt in einem Textfeld mit Komma, und jedes hat drei Zustände —
  neutral, rot, grün —, wobei ein neu gefundenes immer neutral beginnt (Feesh hat nur Rot und „Corruption I"
  von Haus aus); gemerkt wird nur, was im eigenen Inventar liegt, und gefärbt wird nur dort, nicht in jedem
  Fenster.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — die beiden Stummschaltungen
  (`features/sounds/MuteJadeDragonSound.kt`, `MuteReindrakeGifts.kt`, `mixin/SoundEngineMixin.java`):
  Der Mixin in `SoundEngine.play` verschluckt alle Töne `entity.ender_dragon.*` im Moonglade Marsh in den
  Zonen „Dragon's Lair" und „Murkwater Depths" und den Ton `item.totem.use` mit der Lautstärke 0,5 in Jerry's
  Workshop. Nachgebaut — mit drei Unterschieden: Der Mixin meldet „nicht gestartet" statt nichts
  zurückzugeben, die Zone wird einmal in der Sekunde aus der Seitenleiste gelesen statt bei jedem Ton, und
  beide Schalter stehen unter Fishing > Mute.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — die Warnung vor dem Ertrinken
  (`features/alerts/OutOfAirAlert.kt`): Einmal in der Sekunde die Luft des Spielers im Verhältnis zum vollen
  Vorrat, bei 15 Prozent oder weniger eine Meldung — Titel „Out of air soon!", Chatzeile „You are almost out of
  air!" und der Ertrink-Ton —, nur im Moonglade Marsh und im Torrhus Canyon, einmal, bis die Luft wieder über
  der Schwelle war. Nachgebaut — mit einem Unterschied: Banner, Chatzeile und Ton lassen sich einzeln
  abschalten, der Ton lässt sich durch eine eigene Datei ersetzen, und der Banner nimmt ein Design (Voreinstellung
  „Feesh", das den Titel des Spiels nachzeichnet).
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — der Vorschlag, einen gefundenen Hotspot zu teilen
  (`features/chat/HotspotFoundMessage.kt`, `utils/HotspotUtils.kt`): Alle zehn Ticks der nächste Rüstungsständer
  namens „HOTSPOT" in zehn Blöcken (mit einer Angel in der Hotbar, in einer der acht Welten mit Hotspots), der
  Bonus vom Ständer knapp darunter (gleiches X und Z, bis ein Block tiefer, gleicher Neigungswinkel); jeder
  Hotspot einmal, gemerkt werden die letzten zwei. Im Chat „You found <Bonus> Hotspot." und darunter
  „[Share to PARTY chat] or [Share to ALL chat]"; ein Klick schickt „x: …, y: …, z: … | <Bonus> Hotspot at <Zone>"
  an `/pchat`, an `/achat` mit einer zufälligen Kennung dahinter. Nachgebaut — mit drei Unterschieden: Der
  Ton lässt sich einzeln abschalten, die Einstellungen haben einen Testknopf, dessen Zeile nicht anklickbar
  ist, und Feeshs Vergessen eines Hotspots, wenn sein Ständer in der Nähe verschwindet, entfällt (ein neuer
  Hotspot ist ein neuer Ständer mit neuer Kennung, gemerkt werden ohnehin nur die letzten zwei). Dazu Feeshs
  Taste „Share hotspot", die den nächsten Hotspot in zehn Blöcken ohne Chat an die Gruppe schickt (`sendMessageWithNearestHotspot`):
  übernommen nur für die Gruppe, ohne Angel-Pflicht, mit einer Hinweiszeile, wenn keiner in Reichweite ist. Nicht
  übernommen: Feeshs Taste für alle und das selbsttätige Teilen.
- **Odin** — die Ping-Anzeige wurde gegen seine gehalten, um den eigenen Messfehler zu
  finden.
- **Odin** (BSD-3-Clause, Odtheking, `OdinFabric`) — die eigene Figur in anderer Größe
  (`features/impl/render/PlayerSize.kt`, `AvatarRendererMixin`): kurz vor dem Körper das
  Matrix-Blatt skalieren, bei negativer Höhe um die doppelte Höhe nach oben schieben, damit
  die umgekippte Figur auf dem Boden bleibt, und der Wertebereich von −1 bis 3. Das ist
  der Handgriff, und er ist hier nachgebaut (`PlayerSize`, `PlayerSizeMixin`). **Nicht**
  übernommen ist, was Odin daneben vom Entwickler-Server holt (feste Größen und Namen für
  einzelne Spieler). Anders ist auch das Erkennen der eigenen Figur: Odin merkt sich das
  Spielerprofil im Render-Zustand über einen Fabric-Schlüssel, hier genügt die
  Entitätsnummer. Der Mixin zielt auf `AvatarRenderer.scale(AvatarRenderState, PoseStack)`,
  das in 26.1, 26.2 und 26.3 dieselbe Signatur hat.

## Daten von außen

- **Spawn-Runde in den Glacite Tunnels** — die 25 Punkte in
  `assets/shokimod/routes/glacite-spawn.json` stammen nicht aus dieser Mod: Sie wurden im
  Spiel abgelaufen und aufgeschrieben und für ShokiMod beigesteuert. Eigene Runden legt man
  als `.json` in `config/shokimod/routes`; die Mod nimmt die, in deren Nähe man steht.


Keine Mod-Funktionen, sondern Zahlen, die die Mod holt:

- **[NotEnoughUpdates-REPO](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO)**
  (MIT) — Pet-Stufen, Item-Namen und -Bilder, Handwerks- und Forge-Rezepte. Dazu
  `constants/george.json` — was George für ein Pet zahlt, je Pet und Seltenheit. Diese
  Datei stammt laut ihrem eigenen Kopf aus dem [Hypixel-SkyBlock-Wiki](https://hypixelskyblock.minecraft.wiki/w/George/Prices)
  und steht unter CC BY-NC-SA 3.0; sie wird zur Laufzeit geholt und liegt im
  Config-Ordner, statt mitgeliefert zu werden.
- **[SkyShards](https://github.com/Campionnn/SkyShards)** (MIT, Campionnn) — die
  Fusionsdaten für `/shoki shardprofit`.
- **Hypixel API** — Bazaar, Auktionen, Item-Liste.
- **hysky.de** — Auktions-Tiefstpreise.

---

## Ohne Vorbild in einer anderen Mod

Das hier hat keinen Ursprung in einer fremden Mod — Idee, Aufbau und Umsetzung sind
in diesem Projekt entstanden.

**Marktdaten und Gewinn**
- Fusions-Gewinn (`/shoki shardprofit`) samt Bestand aus der geöffneten Hunting Box
- Handwerks- und Forge-Gewinn (`/shoki craftprofit`) mit Forge-Zeit und Coins/h
- Pet-Gewinn (`/shoki petprofit`)
- Der Live-Bazaar mit gzip und 15-Sekunden-Takt
- Die GitHub-Action, die alle zehn Minuten die Listen vorrechnet

**Banner**
- Das ganze Banner-Modell: ein Design statt zwanzig fester Stile
- Die Sandbox mit vier Karteikarten, sechs Umrissen und den vier Schriftschnitten
- Die Kisten-Animation
- Banner teilen und einlesen als Code über die Zwischenablage

**Anzeigen**
- Mining-HUD: Aufträge, Spitzhacken-Fähigkeit mit Abklingzeit, Sky-Mall-Buff
- Performance-HUD mit selbst gemessenem Ping über ein eigenes Ping-Paket
- Pet-HUD mit Overflow-Stufen und der Chat-Meldung beim Aufstieg
- Collection-Tracker
- Contest-HUD mit eigener Uhr
- Nearby-Übersicht der Mobs in der Nähe

**Gilde**
- Die Gilden-Events im Spiel und der ShokiTeufelBot dahinter — eigenes Projekt

**Sonstiges**
- Eigene Audiodateien für jeden Alarm (`CustomSoundPlayer`)
- HUD-Editor und die MoulConfig-Oberfläche
- Shiny-Alarm mit Party-Ruf
- Hideyho-Finder

### Eigene Erweiterungen an übernommenen Funktionen

Der Kern kam von woanders, das hier nicht:

- **Rare Loot:** vier Stufen statt Skysofts einer, jede mit eigener Reaktion und
  eigenem Banner; die Beutebündel aus dem Crystal Nucleus und aus den Leichen der
  Gletscherschächte; der eigene Dye-Alarm.
- **Chat-Regeln:** eigene Audiodateien, die Skyblocker nicht abspielen kann; umgesetzt
  mit Vanilla-APIs statt Skyblockers Innereien.
- **Hunting-Tracker:** der Bestand aus der Hunting Box und die Verbindung zum
  Fusions-Gewinn.

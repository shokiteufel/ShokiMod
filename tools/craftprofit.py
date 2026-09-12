"""Legt die Handwerks-Rezepte ab, aus denen die Mod den Gewinn rechnet.

Laeuft als GitHub Action. Geholt wird das Verzeichnis von NotEnoughUpdates - rund
neun Megabyte gepackt, achttausend Item-Dateien, davon gut dreitausend mit Rezept.
Das gehoert nicht auf den Rechner eines Spielers; heraus faellt eine Datei, die die
Mod in einem Zug holt.

Preise stehen hier bewusst nicht drin. Die Mod fuehrt laengst beide Quellen, die es
dafuer braucht - den Bazaar und die Auktions-Tiefstpreise -, und beide sind dort
frischer als alles, was hier alle paar Minuten entstehen koennte. Rezepte aendern
sich nur, wenn Hypixel etwas aendert.

Zwei Formen kommen vor. Die aeltere legt ein einzelnes Rezept unter "recipe" ab,
die neuere eine Liste unter "recipes" - mit Typ, Ausbeute und mitunter einem
abweichenden Ergebnis. Beide werden gelesen.

Die Zutaten stehen ihrerseits auf zwei Weisen da. Am Handwerkstisch sind es die
neun Felder A1 bis C3; in der Forge eine schlichte Liste unter "inputs", weil dort
kein Gitter steht, sondern nur ein Trichter. Wer nur die Felder liest, sieht von
der Forge kein einziges Rezept - und genau das war bisher der Fall.

Die Forge zaehlt ausserdem in Kommazahlen: "GLOSSY_GEMSTONE:32.0". Wer daraus
stumpf eine ganze Zahl machen will, bekommt einen Fehler und faellt auf eins
zurueck - zweiunddreissig Gemstones waeren zu einem geworden, und die Rechnung
haette einen Gewinn behauptet, den es nicht gibt.

Mit den Forge-Rezepten kommt die Dauer mit, in Sekunden. Ohne sie waere ein
Gewinn nichtssagend: Zehn Millionen in dreissig Sekunden und zehn Millionen in
fuenfzig Stunden sind nicht dasselbe Geschaeft.

Die neun Felder eines Rezepts werden zusammengefasst: Fuenfmal "STRING:32" in
verschiedenen Feldern ist einmal 160 String. Das spart Platz und ist ohnehin die
Zahl, die zaehlt.

    python craftprofit.py [ziel.json]
"""
from __future__ import annotations

import hashlib
import io
import json
import os
import re
import sys
import tarfile
import tempfile
import urllib.error
import urllib.request
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

REPO = ("https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO"
        "/archive/refs/heads/master.tar.gz")
KOPF = {"User-Agent": "ShokiMod-craftprofit (+https://github.com/shokiteufel/ShokiMod)"}
SLOTS = ["A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"]
# "RAW_FISH-1" - alte Minecraft-Kennung mit Schadenswert, im Bazaar "RAW_FISH:1"
VARIANTE = re.compile(r"^(?P<basis>.+)-(?P<nr>\d+)$")
# Rezepte mit mehr Zutatenarten als das passen nicht in ein Handwerksfeld und sind
# meist Sonderformen, die sich nicht rechnen lassen
MAX_ZUTATEN = 9


def holen() -> Path:
    """Das Verzeichnis holen und auspacken. Gibt den Ordner mit den Items zurueck."""
    ziel = Path(tempfile.mkdtemp(prefix="neu-repo-"))
    anfrage = urllib.request.Request(REPO, headers=KOPF)
    with urllib.request.urlopen(anfrage, timeout=300) as antwort:
        daten = antwort.read()
    with tarfile.open(fileobj=io.BytesIO(daten), mode="r:gz") as tar:
        # Nur die Item-Dateien - alles andere waere Ballast
        mitglieder = [m for m in tar.getmembers()
                      if "/items/" in m.name and m.name.endswith(".json")]
        tar.extractall(ziel, members=mitglieder, filter="data")
    for wurzel, ordner, _ in os.walk(ziel):
        if "items" in ordner:
            return Path(wurzel) / "items"
    raise SystemExit("Im Verzeichnis ist kein items-Ordner")


def anzahl_aus(text: str) -> int:
    """"32" und "32.0" sind beide zweiunddreissig.

    Die Forge schreibt Kommazahlen. int() wirft darauf, und der alte Rueckfall auf
    eins haette aus zweiunddreissig Gemstones einen gemacht.
    """
    try:
        return int(float(text))
    except (TypeError, ValueError):
        return 1


def kennung_aus(text: str) -> str:
    """Die nackte Kennung aus "STRING:32" oder "RAW_FISH-1;3"."""
    kennung = text.split(":")[0].split(";")[0].strip()
    # Das Verzeichnis schreibt alte Minecraft-Varianten mit Bindestrich, der
    # Bazaar mit Doppelpunkt: RAW_FISH-1 dort ist RAW_FISH:1 hier, beides Raw
    # Salmon. Wer das nicht umschreibt, findet fuer ein Viertel aller Varianten
    # keinen Preis - bei Whale Bait war genau das der Grund fuer "no price".
    # Abschneiden waere falsch: RAW_FISH ist Raw Cod und kostet etwas anderes
    m = VARIANTE.match(kennung)
    return m.group("basis") + ":" + m.group("nr") if m else kennung


def zutaten(feld: dict) -> dict:
    """Die Zutaten zu "Kennung -> Anzahl" zusammenfassen.

    Am Handwerkstisch stehen sie in neun Feldern, in der Forge in einer Liste.
    """
    out = defaultdict(int)

    # Die Forge: eine Liste, weil dort kein Gitter steht, sondern ein Trichter
    if isinstance(feld.get("inputs"), list):
        for eintrag in feld["inputs"]:
            if not isinstance(eintrag, str):
                continue
            text = eintrag.strip()
            if not text:
                continue
            kennung = kennung_aus(text)
            if not kennung:
                continue
            teile = text.split(":")
            menge = anzahl_aus(teile[1]) if len(teile) > 1 and teile[1] else 1
            if menge > 0:
                out[kennung] += menge
        return dict(out)

    for slot in SLOTS:
        wert = feld.get(slot)
        if not wert:
            continue
        text = str(wert).strip()
        if not text:
            continue
        # "STRING:32" - und manche tragen eine Variante hinter einem Semikolon
        teile = text.split(":")
        kennung = kennung_aus(text)
        if not kennung:
            continue
        anzahl = anzahl_aus(teile[1]) if len(teile) > 1 and teile[1] else 1
        if anzahl > 0:
            out[kennung] += anzahl
    return dict(out)


def lesen(ordner: Path) -> dict:
    """Alle Rezepte einsammeln, nach Ergebnis geordnet."""
    rezepte = defaultdict(list)
    namen = {}
    for datei in sorted(os.listdir(ordner)):
        if not datei.endswith(".json"):
            continue
        try:
            d = json.load(io.open(ordner / datei, encoding="utf-8"))
        except (ValueError, OSError):
            continue
        eigen = d.get("internalname")
        if not eigen:
            continue
        # Auch hier die Varianten-Schreibweise angleichen, sonst steht der Name unter
        # "RAW_FISH-1" und die Zutat heisst "RAW_FISH:1" - und im Fenster stuende die
        # nackte Kennung statt "Raw Salmon"
        m = VARIANTE.match(eigen)
        if m:
            eigen = m.group("basis") + ":" + m.group("nr")
        anzeige = d.get("displayname") or eigen
        # Die Farbcodes von Minecraft haben in einer Datenablage nichts zu suchen
        namen[eigen] = "".join(
            c for i, c in enumerate(anzeige)
            if c != "§" and (i == 0 or anzeige[i - 1] != "§")).strip()

        formen = []
        if isinstance(d.get("recipe"), dict):
            formen.append((d["recipe"], "crafting", 1, eigen, 0))
        for r in d.get("recipes") or []:
            if not isinstance(r, dict):
                continue
            formen.append((r, r.get("type") or "crafting",
                           anzahl_aus(r.get("count") or 1),
                           r.get("overrideOutputId") or eigen,
                           anzahl_aus(r.get("duration") or 0)))

        for feld, art, menge, ergebnis, dauer in formen:
            z = zutaten(feld)
            if not z or len(z) > MAX_ZUTATEN or menge <= 0:
                continue
            # Ein Rezept, das sich selbst als Zutat hat, fuehrt zu nichts
            if ergebnis in z:
                continue
            eintrag = {"a": art, "c": menge, "z": z}
            # Nur wo es eine gibt: Neunzehn Zwanzigstel der Rezepte haben keine
            # Dauer, und eine Null je Zeile waere unnoetiger Ballast in der Datei
            if dauer > 0:
                eintrag["d"] = dauer
            rezepte[ergebnis].append(eintrag)
    return rezepte, namen


def main() -> int:
    ziel = Path(sys.argv[1] if len(sys.argv) > 1 else "craftrecipes.json")
    ziel.parent.mkdir(parents=True, exist_ok=True)
    try:
        ordner = holen()
    except (urllib.error.URLError, tarfile.TarError, OSError) as e:
        print("Verzeichnis holen fehlgeschlagen: %s" % e, file=sys.stderr)
        return 1

    rezepte, namen = lesen(ordner)
    anzahl = sum(len(v) for v in rezepte.values())
    if anzahl < 500:
        print("Nur %d Rezepte - da stimmt etwas nicht" % anzahl, file=sys.stderr)
        return 1

    # Nur Namen behalten, die wirklich vorkommen: als Ergebnis oder als Zutat.
    # Achttausend Anzeigenamen waeren dreimal so viel Datei fuer nichts
    gebraucht = set(rezepte)
    for liste in rezepte.values():
        for r in liste:
            gebraucht.update(r["z"])
    namen = {k: v for k, v in namen.items() if k in gebraucht and v}

    roh = json.dumps(rezepte, separators=(",", ":"), sort_keys=True)
    kennung = hashlib.sha256(roh.encode("utf-8")).hexdigest()[:16]

    ziel.write_text(json.dumps({
        "aktualisiert": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "kennung": kennung,
        "rezepte_gesamt": anzahl,
        "quelle": "https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO",
        "namen": namen,
        "rezepte": rezepte,
    }, separators=(",", ":"), ensure_ascii=False), encoding="utf-8")

    print("%d Rezepte fuer %d Ergebnisse, %d Namen -> %s (%.1f MB, %s)"
          % (anzahl, len(rezepte), len(namen), ziel,
             ziel.stat().st_size / 1e6, kennung))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

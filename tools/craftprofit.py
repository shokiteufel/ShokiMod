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


def zutaten(feld: dict) -> dict:
    """Die neun Felder zu "Kennung -> Anzahl" zusammenfassen."""
    out = defaultdict(int)
    for slot in SLOTS:
        wert = feld.get(slot)
        if not wert:
            continue
        text = str(wert).strip()
        if not text:
            continue
        # "STRING:32" - und manche tragen eine Variante hinter einem Semikolon
        teile = text.split(":")
        kennung = teile[0].split(";")[0].strip()
        if not kennung:
            continue
        try:
            anzahl = int(teile[1]) if len(teile) > 1 and teile[1] else 1
        except ValueError:
            anzahl = 1
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
        anzeige = d.get("displayname") or eigen
        # Die Farbcodes von Minecraft haben in einer Datenablage nichts zu suchen
        namen[eigen] = "".join(
            c for i, c in enumerate(anzeige)
            if c != "§" and (i == 0 or anzeige[i - 1] != "§")).strip()

        formen = []
        if isinstance(d.get("recipe"), dict):
            formen.append((d["recipe"], "crafting", 1, eigen))
        for r in d.get("recipes") or []:
            if not isinstance(r, dict):
                continue
            formen.append((r, r.get("type") or "crafting",
                           int(r.get("count") or 1),
                           r.get("overrideOutputId") or eigen))

        for feld, art, menge, ergebnis in formen:
            z = zutaten(feld)
            if not z or len(z) > MAX_ZUTATEN or menge <= 0:
                continue
            # Ein Rezept, das sich selbst als Zutat hat, fuehrt zu nichts
            if ergebnis in z:
                continue
            rezepte[ergebnis].append({"a": art, "c": menge, "z": z})
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

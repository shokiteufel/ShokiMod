"""Sucht im Auktionshaus die Pets, bei denen sich das Hochleveln am meisten lohnt.

Laeuft nicht auf dem Spieler-PC, sondern als GitHub Action: dort werden die rund vierzig
Seiten des Auktionshauses geladen und ausgewertet, und heraus faellt eine kleine Datei,
die die Mod in einem Zug holt. So kostet die Auswertung niemanden Rechenzeit im Spiel.

Gerechnet wird je Auktion: Was kostet dieses Pet jetzt, was kostet dasselbe Pet auf
Hoechststufe, und wie viel Erfahrung liegt dazwischen? Der Gewinn je Erfahrungspunkt
sagt, wo sich die Arbeit am ehesten lohnt.

    python petprofit.py [ziel.json]
"""
from __future__ import annotations

import json
import re
import sys
import urllib.error
import urllib.request
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

AUKTIONEN = "https://api.hypixel.net/v2/skyblock/auctions?page={page}"
PET_DATEN = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/pets.json"
KOPF = {"User-Agent": "ShokiMod-petprofit (+https://github.com/shokiteufel/ShokiMod)"}

# "[Lvl 40] Rose Dragon Egg" - Stufe und Name stehen im Auktionstitel
NAME_MUSTER = re.compile(r"^\[Lvl (?<lvl>\d+)\] (?<name>.+)$".replace("?<", "?P<"))
# Aus der Beschreibung: "Farming Pet", "Combat Pet" - die Sparte des Pets
SPARTE = re.compile(r"§8(?P<sparte>[A-Za-z ]+) Pet")
FARBE = re.compile("§.")
# Die Sparten, nach denen sich die Liste filtern laesst. Was Hypixel sonst noch an
# Scherz-Pets fuehrt (etwa GABAGOOL), faellt unter SONSTIGE - sonst stuende in der
# Auswahl irgendwann ein Knopf fuer ein einziges Pet.
SPARTEN = {"ALCHEMY", "COMBAT", "ENCHANTING", "FARMING", "FISHING", "FORAGING", "MINING", "TAMING"}
SONSTIGE = "OTHER"

# Nur diese Stufen gelten als "fertig hochgezogen"
HOECHSTSTUFE = 100
# Weniger als das ist kein belastbarer Marktpreis, sondern ein Ausreisser
MIN_ANGEBOTE = 2


def hole(url: str, versuche: int = 3) -> dict:
    for versuch in range(versuche):
        try:
            req = urllib.request.Request(url, headers=KOPF)
            with urllib.request.urlopen(req, timeout=60) as antwort:
                return json.load(antwort)
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as e:
            if versuch == versuche - 1:
                raise
            print(f"  Versuch {versuch + 1} fehlgeschlagen ({e}), nochmal ...", file=sys.stderr)
    return {}


class Erfahrung:
    """Wie viel Erfahrung eine Stufe kostet - je nach Seltenheit und Pet-Art."""

    def __init__(self, daten: dict) -> None:
        self.stufen: list[int] = daten["pet_levels"]
        self.versatz: dict[str, int] = daten["pet_rarity_offset"]
        self.sonderfaelle: dict[str, dict] = daten.get("custom_pet_leveling", {})
        self.sparten: dict[str, str] = daten.get("pet_types", {})

    def tabelle(self, pet_id: str, seltenheit: str) -> tuple[list[int], int, int]:
        """Stufentabelle, Startversatz und Hoechststufe fuer dieses Pet."""
        sonder = self.sonderfaelle.get(pet_id, {})
        stufen = sonder.get("pet_levels", self.stufen)
        hoechste = sonder.get("max_level", HOECHSTSTUFE)
        versatz = self.versatz.get(seltenheit, 0)
        if "rarity_offset" in sonder:
            versatz = sonder["rarity_offset"].get(seltenheit, versatz)
        return stufen, versatz, hoechste

    def gesamt(self, pet_id: str, seltenheit: str, stufe: int) -> float:
        """Gesamte Erfahrung, die bis zu dieser Stufe schon gesammelt wurde."""
        stufen, versatz, hoechste = self.tabelle(pet_id, seltenheit)
        stufe = max(1, min(stufe, hoechste))
        bis = versatz + stufe - 1
        return float(sum(stufen[versatz:bis])) if bis > versatz else 0.0

    def hoechststufe(self, pet_id: str, seltenheit: str) -> int:
        return self.tabelle(pet_id, seltenheit)[2]


def pet_id(anzeigename: str) -> str:
    """Aus "Rose Dragon Egg" wird ROSE_DRAGON - so heisst es in den Pet-Daten."""
    name = FARBE.sub("", anzeigename).strip()
    name = re.sub(r"\s+Egg$", "", name, flags=re.IGNORECASE)
    return re.sub(r"[^A-Z0-9]+", "_", name.upper()).strip("_")


def sammle_pets() -> tuple[list[dict], int]:
    """Alle Sofortkauf-Angebote fuer Pets, ueber alle Seiten des Auktionshauses."""
    erste = hole(AUKTIONEN.format(page=0))
    seiten = int(erste.get("totalPages") or 1)
    gefunden: list[dict] = []
    gesamt = 0

    def verarbeite(daten: dict) -> None:
        nonlocal gesamt
        for a in daten.get("auctions", []):
            gesamt += 1
            if not a.get("bin"):
                continue  # Ein laufendes Gebot ist kein Preis, den man zahlen kann
            treffer = NAME_MUSTER.match(a.get("item_name", ""))
            if not treffer:
                continue
            sparte = SPARTE.search(a.get("item_lore", ""))
            gefunden.append({
                # Die Kennung der Auktion - damit laesst sie sich im Spiel direkt
                # oeffnen (/viewauction), ohne im Auktionshaus zu suchen
                "auktion": str(a.get("uuid") or ""),
                "id": pet_id(treffer.group("name")),
                "name": FARBE.sub("", treffer.group("name")).strip(),
                "stufe": int(treffer.group("lvl")),
                "seltenheit": (a.get("tier") or "").upper(),
                "preis": int(a.get("starting_bid") or 0),
                "sparte": (sparte.group("sparte").strip().upper() if sparte else ""),
            })

    verarbeite(erste)
    for seite in range(1, seiten):
        verarbeite(hole(AUKTIONEN.format(page=seite)))
    return gefunden, gesamt


def sparte_von(pet: dict, kennung: str, xp: Erfahrung) -> str:
    """Die Sparte des Pets, auf die bekannten Namen gebracht."""
    roh = (pet.get("sparte") or xp.sparten.get(kennung, "") or "").upper()
    return roh if roh in SPARTEN else SONSTIGE


def rechne(pets: list[dict], xp: Erfahrung) -> list[dict]:
    """Je Angebot: was es kostet, was es fertig wert ist, und was dazwischen liegt."""
    nach_art: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for p in pets:
        if p["preis"] > 0 and p["seltenheit"]:
            nach_art[(p["id"], p["seltenheit"])].append(p)

    ergebnis = []
    for (kennung, seltenheit), angebote in nach_art.items():
        hoechste = xp.hoechststufe(kennung, seltenheit)
        fertige = [p["preis"] for p in angebote if p["stufe"] >= hoechste]
        if len(fertige) < MIN_ANGEBOTE:
            continue  # Ohne Vergleichspreis auf Hoechststufe ist der Gewinn geraten
        zielpreis = min(fertige)
        xp_ziel = xp.gesamt(kennung, seltenheit, hoechste)

        for p in angebote:
            if p["stufe"] >= hoechste:
                continue
            fehlend = xp_ziel - xp.gesamt(kennung, seltenheit, p["stufe"])
            if fehlend <= 0:
                continue
            gewinn = zielpreis - p["preis"]
            if gewinn <= 0:
                continue
            ergebnis.append({
                "auktion": p.get("auktion", ""),
                "id": kennung,
                "name": p["name"],
                "sparte": sparte_von(p, kennung, xp),
                "seltenheit": seltenheit,
                "stufe": p["stufe"],
                "preis": p["preis"],
                "zielpreis": zielpreis,
                "zielstufe": hoechste,
                "gewinn": gewinn,
                "xp": round(fehlend),
                "proXp": round(gewinn / fehlend, 4),
            })

    # Das beste Angebot je Pet und Seltenheit reicht - sonst steht zehnmal dasselbe da
    beste: dict[tuple[str, str], dict] = {}
    for e in ergebnis:
        schluessel = (e["id"], e["seltenheit"])
        if schluessel not in beste or e["proXp"] > beste[schluessel]["proXp"]:
            beste[schluessel] = e
    return sorted(beste.values(), key=lambda e: -e["proXp"])


def main() -> int:
    ziel = Path(sys.argv[1] if len(sys.argv) > 1 else "petprofit.json")
    print("Pet-Daten holen ...")
    xp = Erfahrung(hole(PET_DATEN))
    print("Auktionshaus durchgehen ...")
    pets, gesamt = sammle_pets()
    print(f"  {gesamt:,} Angebote gesehen, {len(pets):,} davon Pets zum Sofortkauf")
    reihen = rechne(pets, xp)
    print(f"  {len(reihen):,} Pets mit Gewinnaussicht")

    ziel.parent.mkdir(parents=True, exist_ok=True)
    ziel.write_text(json.dumps({
        "aktualisiert": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "angebote": gesamt,
        "pets": reihen[:100],
    }, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"geschrieben: {ziel} ({ziel.stat().st_size:,} Bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

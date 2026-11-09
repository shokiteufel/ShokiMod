"""Baut die Jars fuer Mitspieler (Schiggy): ShokiMod mit eingebautem Upload-Ziel (Daten-Repo und Token) fuer /shoki git,
beide Fassungen (26.2 und 26.1.x).

Aufruf:
    python schiggy-jar-bauen.py besitzer/daten-repo [ausgabeordner]

Die Jars loeschen nach dem Upload die abgeschlossenen, vollstaendig hochgeladenen Aufnahmen auf dem Rechner des Spielers
(lokal_loeschen=ja), damit seine Platte nicht vollaeuft; die laufende Sitzung bleibt bis zum naechsten Mal.

Das Token wird abgefragt (Eingabe bleibt unsichtbar) oder aus der Umgebungsvariable SHOKIMOD_UPLOAD_TOKEN_BAU genommen und nie
ausgegeben. Es kommt nur als Datei upload-ziel.properties (nicht eingecheckt, siehe .gitignore) in die beiden Projektordner und von dort
in die Jar. Danach wird die Datei geloescht und beide Fassungen werden sofort noch einmal OHNE Token gebaut, damit in build/libs nie eine
Jar mit Token liegen bleibt. Die Jars mit Token landen im Ausgabeordner (Standard: ../schiggy-jars) und tragen -schiggy im Namen.

Die Tests laufen hier nicht (Aufruf mit -x test): vorher einmal normal bauen und testen.
Eine Jar mit Token darf nie veroeffentlicht, eingecheckt oder in ein oeffentliches Release gelegt werden: wer sie hat, kann das Token auslesen.
"""
import getpass
import os
import pathlib
import re
import shutil
import subprocess
import sys
import zipfile

WURZEL = pathlib.Path(__file__).resolve().parents[2]  # .../_projekte
BAEUME = [WURZEL / 'shokimod-v2', WURZEL / 'shokimod-v2-261']
JAVA_HOME = r'D:\Minecraft\toolchain\jdk-25.0.4.1+1'
GRADLE_USER_HOME = r'D:\Minecraft\gradle-home'
ZIEL_DATEI = 'upload-ziel.properties'
RESSOURCE = 'shokimod-upload-ziel.properties'


def bauen(baum):
    env = dict(os.environ, JAVA_HOME=JAVA_HOME)
    env.setdefault('GRADLE_USER_HOME', GRADLE_USER_HOME)
    r = subprocess.run([str(baum / 'gradlew.bat'), '--console=plain', 'build', '-x', 'test'], cwd=baum, env=env,
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    if r.returncode != 0:
        sys.exit(f'Bau in {baum.name} scheiterte:\n' + r.stdout[-2000:] + r.stderr[-1000:])


def jar_von(baum):
    jars = [p for p in (baum / 'build' / 'libs').glob('shokimod-*.jar') if not re.search(r'-(sources|dev)\.jar$', p.name)]
    if len(jars) != 1:
        sys.exit(f'Erwartet genau eine Jar in {baum.name}/build/libs, gefunden: {[p.name for p in jars]}')
    return jars[0]


def hat_ziel(jar):
    with zipfile.ZipFile(jar) as z:
        return RESSOURCE in z.namelist()


def main():
    if len(sys.argv) < 2 or not re.fullmatch(r'[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}', sys.argv[1]):
        sys.exit('Aufruf: python schiggy-jar-bauen.py besitzer/daten-repo [ausgabeordner]')
    repo = sys.argv[1]
    ausgabe = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else WURZEL / 'schiggy-jars'
    token = os.environ.get('SHOKIMOD_UPLOAD_TOKEN_BAU') or getpass.getpass('Token (github_pat_..., Eingabe unsichtbar): ')
    token = token.strip()
    if not token:
        sys.exit('Kein Token.')
    ausgabe.mkdir(parents=True, exist_ok=True)
    fertig = []
    try:
        for baum in BAEUME:
            (baum / ZIEL_DATEI).write_text(f'repo={repo}\ntoken={token}\nlokal_loeschen=ja\n', encoding='utf-8')
            bauen(baum)
            jar = jar_von(baum)
            if not hat_ziel(jar):
                sys.exit(f'Das Ziel steckt nicht in {jar.name}: Bau abgebrochen.')
            ziel = ausgabe / (jar.stem + '-schiggy.jar')
            shutil.copy2(jar, ziel)
            fertig.append(ziel)
    finally:
        for baum in BAEUME:  # nie eine Ziel-Datei liegen lassen, auch nicht nach einem Fehler
            (baum / ZIEL_DATEI).unlink(missing_ok=True)
    for baum in BAEUME:
        bauen(baum)  # sauber ueberschreiben: in build/libs darf kein Token bleiben
        if hat_ziel(jar_von(baum)):
            sys.exit(f'WARNUNG: {jar_von(baum)} enthaelt noch ein Ziel.')
    print('Fertig. Jars mit eingebautem Ziel (nur an Schiggy geben, nie veroeffentlichen):')
    for ziel in fertig:
        print('  ', ziel)
    print('In build/libs liegen wieder die sauberen Jars ohne Token.')


if __name__ == '__main__':
    main()

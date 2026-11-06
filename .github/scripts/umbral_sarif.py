#!/usr/bin/env python3
"""Puerta de CodeQL: falla si el SARIF trae hallazgos graves.

Uso: python3 .github/scripts/umbral_sarif.py <carpeta-o-fichero-sarif> [umbral]

Cuenta como grave un resultado cuya regla tenga `security-severity` >= umbral (7.0 por defecto: alta o crítica)
o, si la regla no tiene puntuación, uno de nivel `error`. Escribe la lista en el resumen de la ejecución
($GITHUB_STEP_SUMMARY) y deja los demás hallazgos como aviso. Así la comprobación vale aunque el repositorio no
tenga activado el «code scanning» de GitHub (ERPA1 es privado): el análisis no depende de subir el SARIF.
Sin dependencias: solo la biblioteca estándar.
"""
import glob
import json
import os
import sys

ruta = sys.argv[1] if len(sys.argv) > 1 else "sarif"
umbral = float(sys.argv[2]) if len(sys.argv) > 2 else 7.0
ficheros = sorted(glob.glob(os.path.join(ruta, "*.sarif"))) if os.path.isdir(ruta) else [ruta]
if not ficheros:
    print(f"No hay ficheros SARIF en {ruta}", file=sys.stderr)
    sys.exit(2)

graves, avisos = [], []
for f in ficheros:
    with open(f, encoding="utf-8") as fh:
        sarif = json.load(fh)
    for run in sarif.get("runs", []):
        reglas = {}
        for comp in [run.get("tool", {}).get("driver", {})] + run.get("tool", {}).get("extensions", []):
            for r in comp.get("rules", []) or []:
                reglas[r.get("id")] = r
        for res in run.get("results", []):
            if res.get("suppressions"):
                continue
            regla = reglas.get(res.get("ruleId"), {})
            props = regla.get("properties", {}) or {}
            sev = props.get("security-severity")
            nivel = res.get("level") or (regla.get("defaultConfiguration", {}) or {}).get("level", "warning")
            loc = (res.get("locations") or [{}])[0].get("physicalLocation", {})
            sitio = f'{loc.get("artifactLocation", {}).get("uri", "?")}:{loc.get("region", {}).get("startLine", "?")}'
            linea = f'{res.get("ruleId")} ({sev or nivel}) {sitio}: {res.get("message", {}).get("text", "").splitlines()[0][:200]}'
            if (sev is not None and float(sev) >= umbral) or (sev is None and nivel == "error"):
                graves.append(linea)
            else:
                avisos.append(linea)

resumen = [f"### CodeQL: {len(graves)} hallazgos graves (security-severity ≥ {umbral}), {len(avisos)} avisos", ""]
resumen += [f"- ❌ {x}" for x in graves] + [f"- ⚠️ {x}" for x in avisos]
texto = "\n".join(resumen)
print(texto)
if os.environ.get("GITHUB_STEP_SUMMARY"):
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as fh:
        fh.write(texto + "\n")
for x in graves:
    print(f"::error title=CodeQL::{x}")
sys.exit(1 if graves else 0)

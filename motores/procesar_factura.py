# -*- coding: utf-8 -*-
"""
procesar_factura.py — MOTOR 1: OCR del proyecto CPP (Sprint 3, paso 1)
------------------------------------------------------------------------
CONTRATO (MVP §6.4, fijado por contract-first ANTES de programar):
    CLI   : python procesar_factura.py <ruta_imagen>
    STDOUT: UNA línea JSON:
            {"proveedor": "...", "fecha": "YYYY-MM-DD",
             "monto": 4850.0, "folio": "F-1001", "texto": "<OCR crudo>"}
            (el campo "texto" llega en el Sprint 9, RF-D5 completo: guardar
             TODO lo que leyó el OCR, no solo los 4 campos; es ADITIVO, un
             consumidor antiguo que no lo mire sigue funcionando igual)
    ERRORES: imprime {"error": "..."} y sale con código 1. La factura NO se
            pierde: queda "Pendiente" en MySQL y PHP la reintentará (RNF-5).

Decisiones:
  · Motor OCR: pytesseract — ganador del spike del 30/09/2026 (100% de
    campos a 1.17 s/foto; EasyOCR dio 95% a 29 s y roza el KPI de 30 s).
  · extraer_campos() es la MISMA lógica validada al 100% en motores/spike.
  · Un campo que el OCR no logre leer viaja como null: decidir qué hacer
    con nulls (rechazar / revisión humana) le corresponde a PHP en S4.
"""
import json
import os
import re
import sys
import unicodedata

TESSERACT_EXE = r"C:\Program Files\Tesseract-OCR\tesseract.exe"
LANG = "spa"                # modelo en español instalado durante el spike
CONFIG_OCR = "--oem 3 --psm 6"

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")


# ------------------------------------------------- extracción (idéntica al spike)
def sin_acentos(t):
    return "".join(c for c in unicodedata.normalize("NFD", t or "")
                   if unicodedata.category(c) != "Mn")


def normalizar_fecha(t):
    m = re.search(r"(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})", t)
    if m:
        return "%s-%02d-%02d" % (m.group(1), int(m.group(2)), int(m.group(3)))
    m = re.search(r"(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})", t)
    if m:
        return "%s-%02d-%02d" % (m.group(3), int(m.group(2)), int(m.group(1)))
    return None


def extraer_campos(texto):
    t = sin_acentos(texto.upper())
    folio = None
    m = re.search(r"FOLIO\s*[:.]?\s*([A-Z]{0,4}-?\d{2,8})", t) \
        or re.search(r"\b([A-Z]{1,4}-\d{2,8})\b", t)
    if m:
        folio = m.group(1)

    fecha = normalizar_fecha(t)

    monto = None
    m = re.search(r"TOTAL[^\n\d]{0,15}\$?\s*([0-9][0-9,]*\.\d{2})", t)
    if not m:  # respaldo: la última cantidad con centavos del texto
        lineas = [l for l in t.splitlines() if re.search(r"\d\.\d{2}\s*$", l)]
        if lineas:
            m = re.search(r"([0-9][0-9,]*\.\d{2})\s*$", lineas[-1])
    if m:
        monto = float(m.group(1).replace(",", ""))

    proveedor = None
    blacklist = re.compile(r"FACTURA|FOLIO|FECHA|TOTAL|CONCEPTO|CANTIDAD|"
                           r"PRECIO|IMPORTE|RFC|CFDI")
    for linea in (l.strip() for l in t.splitlines()):
        if len(linea) >= 6 and re.search(r"[A-Z]{4,}", linea) \
                and not blacklist.search(linea):
            proveedor = linea
            break

    return {"proveedor": proveedor, "fecha": fecha, "monto": monto, "folio": folio}


# ------------------------------------------------------------------------ OCR
def ocr(ruta_imagen):
    import pytesseract
    from PIL import Image
    pytesseract.pytesseract.tesseract_cmd = TESSERACT_EXE
    return pytesseract.image_to_string(Image.open(ruta_imagen),
                                       lang=LANG, config=CONFIG_OCR)


# ----------------------------------------------------------------------- main
def main(argv):
    if len(argv) != 2:
        print(json.dumps({"error": "uso: python procesar_factura.py <ruta_imagen>"}))
        return 2
    ruta = argv[1]
    if not os.path.isfile(ruta):
        print(json.dumps({"error": "imagen no encontrada: %s" % ruta}))
        return 1
    try:
        texto = ocr(ruta)
    except Exception as e:
        print(json.dumps({"error": "fallo OCR: %r" % e}))
        return 1
    # S9 (RF-D5 completo): además de los 4 campos viaja el TEXTO CRUDO del
    # OCR (contrato v1.0 ADITIVO: quien no lo use lo ignora sin romperse).
    salida = extraer_campos(texto)
    salida["texto"] = (texto or "").strip()
    print(json.dumps(salida, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))

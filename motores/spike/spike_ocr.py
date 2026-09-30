# -*- coding: utf-8 -*-
"""
spike_ocr.py — SPIKE OCR: pytesseract vs EasyOCR (Sprint 3 paso 0, CPP)
------------------------------------------------------------------------
Corre ambos motores sobre las fotos de ./fotos, extrae los 4 campos del
contrato (proveedor, fecha, monto, folio) con la MISMA lógica de regex
(para que la comparación sea justa) y puntúa contra verdad.json.

Uso:
    python spike_ocr.py                    (ambos motores)
    python spike_ocr.py --solo-tesseract   (si EasyOCR no está instalado)
    python spike_ocr.py --fotos CARPETA    (otra carpeta de fotos)

Salida: tabla por consola + resultado_spike.json con el veredicto
(precisión de campos y tiempo medio por foto; el KPI pide <= 30 s).
"""
import argparse
import json
import os
import re
import sys
import time
import unicodedata

BASE = os.path.dirname(os.path.abspath(__file__))
TESSERACT_EXE = r"C:\Program Files\Tesseract-OCR\tesseract.exe"

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")


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
    """La MISMA lógica para ambos motores: se compara el OCR, no el parser."""
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
def puntuar(ext, verdad):
    e_prov = sin_acentos((ext["proveedor"] or "").upper())
    v_prov = sin_acentos(verdad["proveedor"].upper())
    return {
        "folio": ext["folio"] == verdad["folio"],
        "fecha": ext["fecha"] == verdad["fecha"],
        "monto": ext["monto"] is not None and abs(ext["monto"] - verdad["monto"]) < 0.01,
        "proveedor": len(e_prov) >= 6 and (v_prov[:14] in e_prov or e_prov[:14] in v_prov),
    }


def motor_tesseract():
    import pytesseract
    from PIL import Image
    pytesseract.pytesseract.tesseract_cmd = TESSERACT_EXE

    def run(ruta):
        return pytesseract.image_to_string(Image.open(ruta), lang="spa",
                                           config="--oem 3 --psm 6")
    run.init_s = 0.0
    return run


def motor_easyocr():
    import easyocr
    t0 = time.perf_counter()
    reader = easyocr.Reader(["es", "en"], gpu=False, verbose=False)
    init_s = time.perf_counter() - t0

    def run(ruta):
        return "\n".join(reader.readtext(ruta, detail=0))
    run.init_s = init_s
    return run


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--solo-tesseract", action="store_true")
    ap.add_argument("--fotos", default=os.path.join(BASE, "fotos"))
    args = ap.parse_args()

    with open(os.path.join(args.fotos, "verdad.json"), encoding="utf-8") as fh:
        verdad = json.load(fh)

    motores = {"pytesseract": motor_tesseract()}
    if not args.solo_tesseract:
        try:
            motores["easyocr"] = motor_easyocr()
        except Exception as e:  # no instalado o sin modelos
            print("[AVISO] EasyOCR no disponible (%r); solo pytesseract.\n" % e)

    resultados = {n: {"aciertos": 0, "campos": 0, "tiempos": [], "por_archivo": {}}
                  for n in motores}
    textos = {}

    print("%-16s %-13s %-9s %8s" % ("FOTO", "MOTOR", "ACIERTOS", "TIEMPO"))
    print("-" * 52)
    for archivo in sorted(verdad):
        ruta = os.path.join(args.fotos, archivo)
        for nombre, fn in motores.items():
            t0 = time.perf_counter()
            try:
                texto, err = fn(ruta), None
            except Exception as e:
                texto, err = "", repr(e)
            dt = time.perf_counter() - t0
            ext = extraer_campos(texto) if texto else \
                {"proveedor": None, "fecha": None, "monto": None, "folio": None}
            ok = puntuar(ext, verdad[archivo])
            n_ok = sum(ok.values())
            r = resultados[nombre]
            r["aciertos"] += n_ok
            r["campos"] += 4
            r["tiempos"].append(dt)
            r["por_archivo"][archivo] = {"ok": ok, "extraido": ext,
                                         "seg": round(dt, 2), "error": err}
            textos.setdefault(archivo, {})[nombre] = texto
            print("%-16s %-13s %d/4 %9.2fs %s" % (archivo, nombre, n_ok, dt,
                                                  "ERROR" if err else ""))

    print("\nRESUMEN")
    print("-" * 52)
    resumen = {}
    for nombre, r in resultados.items():
        prec = 100.0 * r["aciertos"] / max(1, r["campos"])
        t_med = sum(r["tiempos"]) / len(r["tiempos"])
        init = getattr(motores[nombre], "init_s", 0.0)
        resumen[nombre] = {"precision_pct": round(prec, 1),
                           "tiempo_medio_s": round(t_med, 2),
                           "init_modelos_s": round(init, 1),
                           "lento_para_kpi_30s": t_med > 30}
        print("%-13s precision %5.1f%%   tiempo medio %6.2fs   init %5.1fs"
              % (nombre, prec, t_med, init))

    mejor = max(resumen, key=lambda n: (resumen[n]["precision_pct"],
                                        -resumen[n]["tiempo_medio_s"]))
    print("\n>>> RECOMENDADO PARA EL PROYECTO: %s" % mejor)
    print("    (criterio: maxima precision; empate => menor tiempo. KPI: <=30 s/foto)")

    with open(os.path.join(BASE, "resultado_spike.json"), "w", encoding="utf-8") as fh:
        json.dump({"resumen": resumen, "recomendado": mejor,
                   "detalle": {n: r["por_archivo"] for n, r in resultados.items()},
                   "textos_ocr": textos}, fh, ensure_ascii=False, indent=2)
    print("Detalle completo en resultado_spike.json")


if __name__ == "__main__":
    main()

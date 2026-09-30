# -*- coding: utf-8 -*-
"""
generar_fotos_demo.py — Fotos del GUION DE DEMO del MVP (sección 9.2).
---------------------------------------------------------------------
El spike generó 5 facturas "genéricas", pero el guion de demo pide
exactamente:

    A: Papelería Central  $4,850.00  → APROBADA          (= factura_01)
    B: Papelería Central  $6,200.00  → MONTO_EXCEDE_ORDEN  (foto que faltaba)
    C: factura de OTRO proveedor     → PROVEEDOR_NO_COINCIDE
                                       (= factura_04 enviada contra la OC-002)

Este script genera SOLO la factura B (factura_06.jpg) reutilizando el
dibujante y la degradación fotográfica de generar_fotos_prueba.py, con la
misma semilla de aleatoriedad (reproducible).
"""
import json
import os

import generar_fotos_prueba as g

BASE = os.path.dirname(os.path.abspath(__file__))
FOTOS = os.path.join(BASE, "fotos")

FACTURA_B = dict(
    n=6, proveedor="PAPELERÍA CENTRAL, S.A. DE C.V.", rfc="PCA-990101-ABC",
    direccion="Av. Reforma 123, Col. Centro, CDMX", folio="F-1002",
    fecha_fmt="21/09/2026", fecha_iso="2026-09-21",
    monto_txt="6,200.00", monto=6200.00,
    conceptos=[("Resma papel carta 75g", "10", "480.00", "4,800.00"),
               ("Caja folders manila", "7", "200.00", "1,400.00")],
)


def main():
    f = FACTURA_B
    foto = g.como_foto(g.dibujar_factura(f), f["n"])
    ruta = os.path.join(FOTOS, "factura_06.jpg")
    foto.save(ruta, "JPEG", quality=87)
    print("creada %s (%d KB)" % (ruta, os.path.getsize(ruta) // 1024))

    verdad = os.path.join(FOTOS, "verdad.json")
    datos = {}
    if os.path.isfile(verdad):
        with open(verdad, encoding="utf-8") as fh:
            datos = json.load(fh)
    datos["factura_06.jpg"] = {"proveedor": f["proveedor"], "fecha": f["fecha_iso"],
                               "monto": f["monto"], "folio": f["folio"]}
    with open(verdad, "w", encoding="utf-8") as fh:
        json.dump(datos, fh, ensure_ascii=False, indent=2)
    print("verdad.json actualizado con factura_06.jpg")


if __name__ == "__main__":
    main()

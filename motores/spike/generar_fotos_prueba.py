# -*- coding: utf-8 -*-
"""
generar_fotos_prueba.py — SPIKE OCR, Sprint 3 paso 0 (proyecto CPP)
--------------------------------------------------------------------
Genera 5 FOTOS SINTÉTICAS de facturas para comparar pytesseract vs
EasyOCR sin depender de fotos reales (en uploads/ solo hay imágenes
dummy de 209 bytes de las pruebas C2).

¿Por qué sintéticas? El spike pide "5 fotos reales"; como no existen,
renderizamos facturas con datos del seed.sql (proveedores y montos
coherentes con la BD) y les aplicamos degradación FOTOGRÁFICA:
rotación, inclinación, desenfoque, ruido, brillo/contraste variables
y compresión JPEG, imitando lo que envía la app (<=1600 px, JPEG ~85).

Cuando existan fotos reales: ponerlas en fotos/ actualizando
verdad.json y re-ejecutar spike_ocr.py.

Salida:
    fotos/factura_01.jpg ... factura_05.jpg   (las 5 fotos)
    fotos/verdad.json                          (datos correctos esperados)
"""
import json
import os
import random

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

BASE = os.path.dirname(os.path.abspath(__file__))
FOTOS = os.path.join(BASE, "fotos")
os.makedirs(FOTOS, exist_ok=True)

# ---- 5 facturas con datos coherentes con database/seed.sql -----------------
# (Papelería Central promedia ~$3,941: $4,850 => riesgo bajo, $6,200 => alto)
FACTURAS = [
    dict(n=1, proveedor="PAPELERÍA CENTRAL, S.A. DE C.V.", rfc="PCA-990101-ABC",
         direccion="Av. Reforma 123, Col. Centro, CDMX", folio="F-1001",
         fecha_fmt="20/09/2026", fecha_iso="2026-09-20", monto_txt="4,850.00", monto=4850.00,
         conceptos=[("Resma papel carta 75g", "10", "485.00", "4,850.00")]),
    dict(n=2, proveedor="FERRETERÍA EL TORNILLO, S.A.", rfc="FTO-020515-XY9",
         direccion="Calle 5 de Mayo 45, Guadalajara", folio="F-2047",
         fecha_fmt="22/09/2026", fecha_iso="2026-09-22", monto_txt="6,200.00", monto=6200.00,
         conceptos=[("Taladro percutor 650W", "2", "3,100.00", "6,200.00")]),
    dict(n=3, proveedor="OFICINAS DEL NORTE, S. DE R.L.", rfc="ODN-871120-QWE",
         direccion="Blvd. Norte 88, Monterrey", folio="F-0315",
         fecha_fmt="25/09/2026", fecha_iso="2026-09-25", monto_txt="3,200.50", monto=3200.50,
         conceptos=[("Tóner compatible HP 85A", "3", "850.00", "2,550.00"),
                    ("Caja de grapas", "13", "50.04", "650.52")]),
    dict(n=4, proveedor="PAPELERÍA CENTRAL, S.A. DE C.V.", rfc="PCA-990101-ABC",
         direccion="Av. Reforma 123, Col. Centro, CDMX", folio="F-1042",
         fecha_fmt="2026-09-12", fecha_iso="2026-09-12", monto_txt="3,680.00", monto=3680.00,
         conceptos=[("Cuaderno profesional", "20", "184.00", "3,680.00")]),
    dict(n=5, proveedor="FERRETERÍA EL TORNILLO, S.A.", rfc="FTO-020515-XY9",
         direccion="Calle 5 de Mayo 45, Guadalajara", folio="F-2088",
         fecha_fmt="26/09/2026", fecha_iso="2026-09-26", monto_txt="8,990.00", monto=8990.00,
         conceptos=[("Kit llaves combinadas 24 pzas", "1", "8,990.00", "8,990.00")]),
]
def fuente(tam, negrita=False):
    ruta = r"C:\Windows\Fonts\arialbd.ttf" if negrita else r"C:\Windows\Fonts\arial.ttf"
    try:
        return ImageFont.truetype(ruta, tam)
    except OSError:
        return ImageFont.load_default()


def dibujar_factura(f):
    """Renderiza la factura 'limpia' como documento A4 aprox. a 150 dpi."""
    W, H = 1240, 1650
    img = Image.new("RGB", (W, H), (249, 249, 246))
    d = ImageDraw.Draw(img)
    y = 110

    d.text((90, y), f["proveedor"], font=fuente(33, True), fill=(15, 15, 15)); y += 50
    d.text((90, y), f["direccion"], font=fuente(26), fill=(80, 80, 80)); y += 42
    d.text((90, y), "RFC: " + f["rfc"], font=fuente(26), fill=(80, 80, 80)); y += 72

    d.text((90, y), "F A C T U R A", font=fuente(40, True), fill=(15, 15, 15))
    d.text((1150, y + 10), "Folio: " + f["folio"], font=fuente(30, True),
           fill=(15, 15, 15), anchor="ra")
    y += 64
    d.text((90, y), "Fecha de emisión: " + f["fecha_fmt"], font=fuente(28), fill=(20, 20, 20))
    y += 50
    d.line((90, y, 1150, y), fill=(140, 140, 140), width=2); y += 26

    d.text((90, y), "CONCEPTO", font=fuente(24, True), fill=(90, 90, 90))
    d.text((820, y), "CANT.", font=fuente(24, True), fill=(90, 90, 90), anchor="ra")
    d.text((980, y), "PRECIO", font=fuente(24, True), fill=(90, 90, 90), anchor="ra")
    d.text((1150, y), "IMPORTE", font=fuente(24, True), fill=(90, 90, 90), anchor="ra")
    y += 44
    d.line((90, y, 1150, y), fill=(180, 180, 180), width=1); y += 22

    for c in f["conceptos"]:
        d.text((90, y), c[0], font=fuente(28), fill=(20, 20, 20))
        d.text((820, y), c[1], font=fuente(28), fill=(20, 20, 20), anchor="ra")
        d.text((980, y), c[2], font=fuente(28), fill=(20, 20, 20), anchor="ra")
        d.text((1150, y), c[3], font=fuente(28), fill=(20, 20, 20), anchor="ra")
        y += 52
    y += 18
    d.line((90, y, 1150, y), fill=(140, 140, 140), width=2); y += 30

    d.text((930, y), "TOTAL:", font=fuente(36, True), fill=(0, 0, 0), anchor="ra")
    d.text((1150, y), "$ " + f["monto_txt"], font=fuente(36, True),
           fill=(0, 0, 0), anchor="ra")
    y += 90
    d.text((90, y), "Este documento es una representación impresa de una CFDI",
           font=fuente(22), fill=(130, 130, 130))
    return img


def como_foto(img, n):
    """Aplica degradación tipo 'foto de celular' (reproducible por foto)."""
    random.seed(1000 + n)
    ang = random.uniform(2.2, 4.5) * random.choice([-1, 1])
    img = img.rotate(ang, resample=Image.BICUBIC, expand=False, fillcolor=(202, 202, 202))

    w, h = img.size
    sh = random.uniform(0.012, 0.03) * random.choice([-1, 1])
    sv = random.uniform(0.004, 0.014) * random.choice([-1, 1])
    img = img.transform((w, h), Image.AFFINE,
                        (1, sh, -sh * h / 2, sv, 1, -sv * w / 2),
                        resample=Image.BICUBIC)

    img = img.filter(ImageFilter.GaussianBlur(random.uniform(0.6, 1.3)))
    ruido = Image.effect_noise(img.size, random.uniform(9, 20)).convert("RGB")
    img = Image.blend(img, ruido, random.uniform(0.05, 0.09))
    img = ImageEnhance.Brightness(img).enhance(random.uniform(0.86, 1.04))
    img = ImageEnhance.Contrast(img).enhance(random.uniform(0.92, 1.08))

    escala = 1600 / max(img.size)          # como llega desde la app (<=1600 px)
    if escala < 1:
        img = img.resize((round(w * escala), round(h * escala)), Image.LANCZOS)
    return img


def main():
    verdad = {}
    for f in FACTURAS:
        foto = como_foto(dibujar_factura(f), f["n"])
        archivo = "factura_%02d.jpg" % f["n"]
        ruta = os.path.join(FOTOS, archivo)
        foto.save(ruta, "JPEG", quality=random.randint(82, 90))
        verdad[archivo] = {"proveedor": f["proveedor"], "fecha": f["fecha_iso"],
                           "monto": f["monto"], "folio": f["folio"]}
        print("creada %-15s (%d KB)" % (archivo, os.path.getsize(ruta) // 1024))
    with open(os.path.join(FOTOS, "verdad.json"), "w", encoding="utf-8") as fh:
        json.dump(verdad, fh, ensure_ascii=False, indent=2)
    print("verdad.json escrito con", len(verdad), "registros")


if __name__ == "__main__":
    main()

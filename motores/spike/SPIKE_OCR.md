# SPIKE OCR — pytesseract vs EasyOCR (Sprint 3, paso 0) · 30/09/2026

**DECISIÓN: pytesseract (Tesseract-OCR 5.4.0 + spa.traineddata)**

## Resultados (5 facturas degradadas tipo foto · 4 campos c/u = 20 campos)

| Motor       | Precisión      | Tiempo/foto | Init modelos | Notas                                        |
|-------------|----------------|-------------|--------------|----------------------------------------------|
| pytesseract | **100 %** 20/20 | **1.17 s**  | 0 s          | spa.traineddata estándar (17.4 MB, repo oficial) |
| easyocr     | 95 %  19/20     | 29.10 s     | +21.5 s      | Falló 1 proveedor ("FERRETERHA"); CPU-only   |

KPI del PRD: procesar una factura en ≤ 30 s → EasyOCR (29.1 s) queda al borde
sin margen para los otros 2 motores + PHP; pytesseract usa ~4 % del presupuesto.

## Por qué no hubo fotos reales
En `uploads/` solo existían 3 JPEG "dummy" de 209 B (pruebas C2 de conectividad).
Se generaron 5 facturas sintéticas (`generar_fotos_prueba.py`) con datos del
seed.sql (Papelería Central $4,850 · Ferretería $6,200 · Oficinas del Norte
$3,200.50 · $3,680 · $8,990) y degradación fotográfica: rotación 2-4.5°,
inclinación (shear), desenfoque, ruido, brillo/contraste variables, JPEG 82-90
y lado mayor 1600 px — como llegan desde la app Android.

**Cuando haya fotos reales**: copiarlas a `fotos/`, actualizar `verdad.json`
y re-ejecutar `python spike_ocr.py` (acepta `--fotos CARPETA`).

## Cómo se comparó (justicia del experimento)
Ambos motores devuelven texto plano; la MISMA función `extraer_campos()`
(regex del contrato: proveedor/fecha/monto/folio) puntúa contra `verdad.json`.
Así se mide la calidad del OCR, no el parser. Detalle campo a campo y texto
crudo de cada OCR: `resultado_spike.json`. Reproducir: `python spike_ocr.py`.

## Entorno instalado para el spike (queda listo para los 3 motores)
- Python 3.12.10 (winget; pip bootstrapeado con `ensurepip`) en
  `C:\Users\acaba\AppData\Local\Programs\Python\Python312\`
- Paquetes: pillow 12.3, pytesseract, easyocr 1.7.2 (torch 2.14.1 CPU)
- Tesseract-OCR 5.4.0 (UB-Mannheim, winget) en `C:\Program Files\Tesseract-OCR`
  + `tessdata\spa.traineddata` 17.4 MB descargado del repo oficial tessdata
- GCC MinGW 6.3.0 (`C:\MinGW\bin`) y SWI-Prolog (`C:\Program Files\swipl`)
  ya existían — rutas reales fijadas en `api/config.php` (30/09/2026)

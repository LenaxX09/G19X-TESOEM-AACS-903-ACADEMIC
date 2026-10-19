package com.cpp.captura;

import android.graphics.Bitmap;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Motores — Sprint 10 «Sin servidor».
 *
 * Firebase no puede ejecutar procesos, así que los 3 motores del pipeline
 * se PORTAN AL TELÉFONO:
 *   · OCR ......... ML Kit de Google lee el texto EN EL DISPOSITIVO y
 *                   extraerCampos() aplica las MISMAS regex del spike
 *                   (extraer_campos de procesar_factura.py, 100% en su día).
 *   · RIESGO ...... clasificadorRiesgo() replica la fórmula de
 *                   clasificador_riesgo.c: base 10 + 0.75×alza% (0.25×baja),
 *                   tope [0,100], 50.0 si no hay historial.
 *   · CONCILIACIÓN  conciliar() replica regla.pl cláusula por cláusula y en
 *                   el MISMO orden de prioridad: 0) duplicado (RF-L3),
 *                   1) monto > autorizado, 2) riesgo ≥ 40, 3) proveedor
 *                   (comparación tolerante, prefijo de 8), 4) fecha fuera
 *                   de [orden−15d, orden+90d] (días julianos), 5) APROBADA.
 *
 * Los originales (motores/*.py/.c/.pl) quedan como REFERENCIA académica y
 * para la demo del pipeline con Laragon: los resultados son equivalentes.
 */
public final class Motores {

    /** Espejo de umbral_riesgo(40) de regla.pl / UMBRAL_RIESGO de config.php. */
    public static final double UMBRAL_RIESGO = 40.0;

    private Motores() {
        // clase utilitaria estática
    }

    // ========================================================================
    // MOTOR 1 · OCR (ML Kit) + extracción de campos (regex del spike)
    // ========================================================================

    /** Lee el texto de un bitmap EN EL TELÉFONO (modelo latino: español ok). */
    public static void ocr(Bitmap bitmap, Consumer<String> alExito, Consumer<String> alFallo) {
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener(texto -> alExito.accept(texto.getText()))
                .addOnFailureListener(e -> alFallo.accept("fallo OCR: " + e.getMessage()));
    }

    /** Campos extraídos por el OCR (null = el OCR no lo pudo leer). */
    public static class Campos {
        public String proveedor;
        public String fecha;        // YYYY-MM-DD
        public Double monto;
        public String folio;
    }

    /** Mismo extractor del spike: busca folio, fecha, TOTAL y proveedor. */
    public static Campos extraerCampos(String texto) {
        Campos c = new Campos();
        if (texto == null || texto.isEmpty()) {
            return c;
        }
        String t = sinAcentos(texto.toUpperCase(Locale.ROOT));

        Matcher m = Pattern.compile("FOLIO\\s*[:.]?\\s*([A-Z]{0,4}-?\\d{2,8})").matcher(t);
        boolean hay = m.find();
        if (!hay) {
            m = Pattern.compile("\\b([A-Z]{1,4}-\\d{2,8})\\b").matcher(t);
            hay = m.find();
        }
        if (hay) {
            c.folio = m.group(1);
        }

        c.fecha = normalizarFecha(t);

        m = Pattern.compile("TOTAL[^\\n\\d]{0,15}\\$?\\s*([0-9][0-9,]*\\.\\d{2})").matcher(t);
        hay = m.find();
        if (!hay) {
            // respaldo: la última línea que termina en cantidad con centavos
            Pattern fin = Pattern.compile("([0-9][0-9,]*\\.\\d{2})\\s*$");
            String[] lineas = t.split("\\n");
            for (int i = lineas.length - 1; i >= 0; i--) {
                Matcher lm = fin.matcher(lineas[i].trim());
                if (lm.find()) {
                    m = lm;
                    hay = true;
                    break;
                }
            }
        }
        if (hay) {
            try {
                c.monto = Double.parseDouble(m.group(1).replace(",", ""));
            } catch (NumberFormatException ignored) {
                // monto ilegible: queda null (la pantalla avisará)
            }
        }

        Pattern blacklist = Pattern.compile(
                "FACTURA|FOLIO|FECHA|TOTAL|CONCEPTO|CANTIDAD|PRECIO|IMPORTE|RFC|CFDI");
        for (String linea : t.split("\\n")) {
            String l = linea.trim();
            if (l.length() >= 6 && Pattern.compile("[A-Z]{4,}").matcher(l).find()
                    && !blacklist.matcher(l).find()) {
                c.proveedor = l;
                break;
            }
        }
        return c;
    }

    // ========================================================================
    // MOTOR 2 · RIESGO (fórmula de clasificador_riesgo.c)
    // ========================================================================

    /**
     * desviación d = (monto − promedio) / promedio × 100
     *   d ≥ 0 → riesgo = 10 + 0.75·d   ·   d < 0 → riesgo = 10 + 0.25·d
     * tope [0,100] · sin historial (promedio ≤ 0) → 50.0 (conservador).
     */
    public static double clasificadorRiesgo(double monto, double promedio) {
        if (promedio <= 0) {
            return 50.0;
        }
        double d = (monto - promedio) / promedio * 100.0;
        double riesgo = d >= 0 ? 10.0 + 0.75 * d : 10.0 + 0.25 * d;
        riesgo = Math.max(0.0, Math.min(100.0, riesgo));
        return Math.round(riesgo * 10.0) / 10.0;
    }

    // ========================================================================
    // MOTOR 3 · CONCILIACIÓN (regla.pl, cláusula por cláusula)
    // ========================================================================

    /** Datos de la orden que necesita la conciliación. */
    public static class Orden {
        public String codigo;
        public String negocio;      // proveedor de la orden
        public double monto;        // monto autorizado
        public String fechaEmision; // YYYY-MM-DD
    }

    /** Devuelve "APROBADA" o "DISCREPANCIA:<MOTIVO>" (contrato de regla.pl). */
    public static String conciliar(Orden orden, Campos factura,
                                   double riesgo, boolean duplicado) {
        if (duplicado) {
            return "DISCREPANCIA:FACTURA_DUPLICADA";           // regla 0 (RF-L3)
        }
        if (factura.monto != null && factura.monto > orden.monto) {
            return "DISCREPANCIA:MONTO_EXCEDE_ORDEN";          // regla 1
        }
        if (riesgo >= UMBRAL_RIESGO) {
            return "DISCREPANCIA:RIESGO_ALTO";                  // regla 2
        }
        if (factura.proveedor != null && !proveedorCoincide(factura.proveedor, orden.negocio)) {
            return "DISCREPANCIA:PROVEEDOR_NO_COINCIDE";        // regla 3
        }
        if (factura.fecha != null && orden.fechaEmision != null
                && !fechaEnRango(factura.fecha, orden.fechaEmision)) {
            return "DISCREPANCIA:FECHA_FUERA_DE_RANGO";         // regla 4
        }
        return "APROBADA";                                       // regla 5
    }

    /**
     * Comparación TOLERANTE de regla.pl: se normaliza (mayúsculas, sin
     * acentos, solo letras/números) y basta que un prefijo de 8 caracteres
     * de uno esté contenido en el otro ("PAPELERÍA CENTRAL, S.A." == "Papelería Central").
     */
    public static boolean proveedorCoincide(String pf, String po) {
        String nf = normaliza(pf);
        String no = normaliza(po);
        if (nf.isEmpty() || no.isEmpty()) {
            return false;
        }
        return contiene(nf, no) || contiene(no, nf);
    }

    private static boolean contiene(String a, String b) {
        String pref = a.length() >= 8 ? a.substring(0, 8) : a;
        return !pref.isEmpty() && b.contains(pref);
    }

    /** mayúsculas + sin acentos + solo [A-Z0-9] (igual que normaliza_cs de regla.pl). */
    public static String normaliza(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : sinAcentos(s.toUpperCase(Locale.ROOT)).toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Fecha de la factura dentro de [emisión−15 días, emisión+90 días]. */
    public static boolean fechaEnRango(String fechaFactura, String fechaEmision) {
        long jf = juliano(fechaFactura);
        long jo = juliano(fechaEmision);
        if (jf < 0 || jo < 0) {
            return true;    // alguna fecha ilegible: no dispara la regla
        }
        return jf >= jo - 15 && jf <= jo + 90;
    }

    /** "YYYY-MM-DD" → día juliano (misma fórmula de fecha_dias/2 de regla.pl). */
    public static long juliano(String yyyyMmDd) {
        Matcher m = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})").matcher(yyyyMmDd);
        if (!m.find()) {
            return -1;
        }
        long y = Long.parseLong(m.group(1));
        long mo = Long.parseLong(m.group(2));
        long d = Long.parseLong(m.group(3));
        long a = (14 - mo) / 12;
        long y2 = y + 4800 - a;
        long m2 = mo + 12 * a - 3;
        return d + (153 * m2 + 2) / 5 + 365 * y2 + y2 / 4 - y2 / 100 + y2 / 400 - 32045;
    }

    // ========================================================================
    // Utilidades compartidas
    // ========================================================================

    private static String normalizarFecha(String t) {
        Matcher m = Pattern.compile("(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})").matcher(t);
        if (m.find()) {
            return String.format(Locale.ROOT, "%s-%02d-%02d",
                    m.group(1), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        }
        m = Pattern.compile("(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})").matcher(t);
        if (m.find()) {
            return String.format(Locale.ROOT, "%s-%02d-%02d",
                    m.group(3), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
        }
        return null;
    }

    private static String sinAcentos(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{Mn}", "");
    }
}

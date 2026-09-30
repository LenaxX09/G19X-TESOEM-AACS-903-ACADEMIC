/* ============================================================================
   clasificador_riesgo.c — MOTOR 2: RIESGO del proyecto CPP (Sprint 3, paso 2)
   ----------------------------------------------------------------------------
   CONTRATO (MVP §6.4, fijado por contract-first ANTES de programar):
     CLI   : clasificador_riesgo <monto_factura> <promedio_historial>
     STDOUT: UNA línea con el porcentaje de riesgo, un decimal (ej. 37.5).
             Regla del proyecto: riesgo < 40 => confiable (el corte lo
             aplica regla.pl con UMBRAL_RIESGO de api/config.php).

   FORMULA (explicada para la demo):
     desviación  d = (monto - promedio) / promedio * 100   [% vs historial]
       d >= 0 :  riesgo = 10.0 + 0.75 * d   (cada % de alza suma 0.75)
       d <  0 :  riesgo = 10.0 + 0.25 * d   (facturar por debajo baja algo)
     Riesgo acotado a [0,100]. La base 10 es el riesgo residual "normal".
     Sin historial (promedio <= 0) devuelve 50.0: conservador, no aprueba solo.

   CASOS DEL SEED (Papelería Central promedia 3,941.67):
     4,850.00 (+23.1%) -> 27.3  riesgo BAJO (aprueba la OC-001)
     6,200.00 (+57.3%) -> 53.0  riesgo ALTO (alza repentina, RF-R3)

   Compilar (GCC MinGW, ya verificado en api/config.php como GCC_BIN):
     gcc -O2 -Wall -o clasificador_riesgo.exe clasificador_riesgo.c
   ========================================================================== */
#include <stdio.h>
#include <stdlib.h>

#define RIESGO_BASE   10.0
#define PESO_ALZA     0.75
#define PESO_BAJA     0.25
#define RIESGO_SIN_HISTORIAL 50.0

static double riesgo_de(double monto, double promedio)
{
    double d, r;
    if (promedio <= 0.0)
        return RIESGO_SIN_HISTORIAL;
    d = (monto - promedio) / promedio * 100.0;
    if (d >= 0.0)
        r = RIESGO_BASE + PESO_ALZA * d;
    else
        r = RIESGO_BASE + PESO_BAJA * d;
    if (r < 0.0)   r = 0.0;
    if (r > 100.0) r = 100.0;
    return r;
}

/* Convierte texto a número exigiendo que TODO el texto sea numérico. */
static int a_numero(const char *s, double *out)
{
    char *fin = NULL;
    if (s == NULL || *s == '\0')
        return 0;
    *out = strtod(s, &fin);
    return (fin != NULL && *fin == '\0');
}

int main(int argc, char *argv[])
{
    double monto, promedio;

    if (argc != 3
        || !a_numero(argv[1], &monto)
        || !a_numero(argv[2], &promedio)) {
        fprintf(stderr, "uso: %s <monto_factura> <promedio_historial>\n",
                argv[0]);
        return 1;
    }
    if (monto < 0.0) {
        fprintf(stderr, "error: el monto no puede ser negativo\n");
        return 1;
    }
    printf("%.1f\n", riesgo_de(monto, promedio));
    return 0;
}

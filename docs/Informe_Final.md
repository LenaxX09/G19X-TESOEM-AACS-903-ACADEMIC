# Informe Final — CPP
## Plataforma Inteligente para la Gestión de Cuentas por Pagar

> **Entregable de la actividad 10 del cronograma** (Progreso.docx: «Evaluar
> resultados y documentar lecciones aprendidas»). Versión 1.0 · 24/10/2026.
> Evidencia verificable: `Reporte_QA.md`, `Bitacora.txt`, `test_*.ps1` y el
> propio repositorio (10 commits de hitos + este cierre).

---

## 1. Resumen ejecutivo

CPP se entregó como una **app Android (0.19.0-v1)** que automatiza las cuentas
por pagar de una PyME: el proveedor registra lo que le deben, el cliente ve
sus cuentas al instante, paga con foto del comprobante y concilia facturas
fotografiándolas — OCR, riesgo y conciliación corren **EN EL TELÉFONO** sobre
Firebase. La pila PHP/MySQL + Python + C + Prolog de los sprints 1-9 se
conserva como **camino servidor** (demo académica de los motores originales)
con el MISMO veredicto por los dos caminos.

**Resultado global**: 10/10 checkpoints superados · 8/9 requisitos funcionales
del Incremento 1 · KPI de tiempo 1-3 s (meta ≤ 30 s) · precisión OCR 100 % con
foto original · dos revisiones externas de seguridad corregidas al 100 %.

## 2. Cumplimiento de objetivos (vs PRD_CPP.md)

| Objetivo específico (plan) | Estado | Evidencia |
|---|---|---|
| Arquitectura de comunicación app ↔ módulos | ✔ | Dos caminos de entrega (`Arquitectura.md` §3): Firebase sin servidor + Laragon para los motores |
| Captura móvil y procesamiento automático | ✔ | Cámara/galería + ML Kit en el teléfono (C2, C10); OCR 100 % campos clave con foto original |
| Reglas de conciliación (0-5) | ✔ | `regla.pl` (Prolog) y réplica exacta en `Motores.java`; trío A/B/C idéntico en ambos caminos (C4, C5) |
| Cuentas por pagar + comprobantes | ✔ | S8 (C8 12/12): por_pagar/pagadas, foto del comprobante obligatoria |
| Duplicados y alertas | ✔ | S9 (C9 14/14): `facturasLookup` RF-L3; alertas por vencer/vencida (RF-M7) |
| Seguridad | ✔ | Reglas Firestore/Storage revisadas 2 veces por un agente externo (22/10, 24/10) y corregidas; secretos fuera del repo |
| Validación de desempeño | ✔ | Veredicto 1-3 s en teléfono físico (meta ≤ 30 s, KPI-1) |
| Documentación técnica y de usuario | ✔ | Arquitectura, Manual_Tecnico (incl. mantenimiento), Manual_Usuario, Reporte_QA, esta acta de cierre |

## 3. Entregables del cronograma (Progreso.docx) — estado final

| # | Actividad | Entregable | Archivo | Estado |
|---|---|---|---|---|
| 1 | Detección de necesidades | Documento de requerimientos | `PRD_CPP.md` | ✔ |
| 2 | Propuesta | Propuesta técnica | `propuestas.txt` + `PRD_CPP.md`/`MVP_CPP.md` | ✔ |
| 3 | Arquitectura | Documento de arquitectura | `Arquitectura.md` | ✔ |
| 4 | Planificación | Plan del proyecto | `MVP_CPP.md` (alcance, sprints, hipótesis) | ✔ |
| 5 | Desarrollo | Sistema funcional | `android/` + `api/` + `motores/` (app 0.19.0-v1) | ✔ |
| 6 | Pruebas | Reporte QA | `Reporte_QA.md` (10/10 checkpoints) | ✔ |
| 7 | Documentación | Manual técnico y de usuario | `Manual_Tecnico.md` + `Manual_Usuario.md` | ✔ |
| 8 | Mantenimiento | Plan de mantenimiento | `Manual_Tecnico.md` §10 (soporte 3 niveles) | ✔ |
| 9 | Capacitación | Acta de capacitación | `Acta_Capacitacion.md` | ✔ |
| 10 | Cierre | Informe final | Este documento | ✔ |

## 4. Resultados cuantitativos

| Métrica | Meta | Resultado |
|---|---|---|
| Checkpoints por sprint | 10 | **10/10** (C5 15/15 · C7 15/15 · C8 12/12 · C9 14/14 · C10 7/7) |
| Requisitos funcionales Incremento 1 | 9 | **8/9** (RF-M8 cola offline, declarado) |
| Tiempo de procesamiento (KPI-1) | ≤ 30 s | **1-3 s** en teléfono físico |
| Precisión OCR campos clave (KPI-2) | Alta | **100 %** con foto original (~1202×1600 px) |
| Veredicto idéntico servidor vs teléfono | Exacto | **Exacto** (trío A/B/C, ambas vías) |
| Hallazgos de seguridad externos | 0 abiertos | **0 abiertos** (2 rondas corregidas al 100 %) |

## 5. Lecciones aprendidas

1. **Las reglas de seguridad son pruebas, no configuración**: el `allow
   update` con `||` suelto dejaba editar órdenes ajenas y solo salió en la
   2ª revisión externa. Lección: revisar la lógica booleana de las reglas
   como se revisa código, y no confiar en «ya lo probé y funcionó».
2. **Firestore no filtra resultados**: una consulta sin `where` se rechaza
   COMPLETA si un documento no pasa la regla. Las consultas se diseñaron
   filtradas por el dueño de los datos desde el inicio (no al final).
3. **Replicar los motores en Java** (`Motores.java`) fue la decisión clave:
   mismo veredicto regla por regla, sin servidor, y el pipeline original
   (Python/C/Prolog) quedó como demostración académica, no como dependencia.
4. **El OCR depende de la foto, no del modelo**: las imágenes reenviadas por
   chat (~473×631) confunden dígitos; con la foto ORIGINAL el resultado fue
   100 %. Se documentó en el manual de usuario en lugar de sobre-ingeniería.
5. **Los checkpoints re-ejecutables** (`test_*.ps1`) convirtieron las pruebas
   en evidencia: cualquier revisor puede reproducir 10/10 sin pedir nada.
6. **Los secretos nunca viven en el repo**: la clave demo salió del código y
   de los scripts (variable de entorno `CPP_DEMO_CLAVE`), rotándose en consola.

## 6. Deuda técnica declarada (v1.1)

1. RF-M8 — cola offline (único RF del Incremento 1 pendiente).
2. Verificación de correo al registrarse.
3. Camino servidor: imagen multipart (hoy Base64) y token cifrado en prefs.
4. Activación del release de reglas en la consola (paso del propietario;
   los rulesets correctos ya están subidos y verificados por API).

## 7. Cierre técnico

El sistema queda funcional en sus dos caminos, con las reglas de seguridad
versionadas y corregidas, la documentación formal completa (actividades 1-10
del cronograma) y las pruebas reproducibles por script. El repositorio
(`Bitacora.txt` §1-§7) registra la historia completa para mantenimiento
futuro.

*Fin del documento — Informe_Final.md v1.0 · cierre del proyecto CPP.*

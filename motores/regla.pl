/* ===========================================================================
   regla.pl — MOTOR 3: CONCILIACIÓN del proyecto CPP (Sprint 3, paso 3)
   ---------------------------------------------------------------------------
   CONTRATO (MVP §6.4, fijado por contract-first ANTES de programar):
     CLI   : swipl regla.pl <id_orden> <folio> <monto> <fecha_factura>
                         <proveedor_factura> <riesgo> <monto_autorizado>
                         <proveedor_orden> <fecha_orden>
             (id_orden solo para rastreabilidad; el veredicto no lo usa)
     STDOUT: UNA línea ->  APROBADA   ó   DISCREPANCIA:<motivo>

   REGLAS, en orden de revisión (la primera que falla define el motivo):
     1) monto_factura =< monto_autorizado        ó MONTO_EXCEDE_ORDEN
     2) riesgo < 40 (UMBRAL_RIESGO de config)    ó RIESGO_ALTO
     3) proveedor factura ~= proveedor orden     ó PROVEEDOR_NO_COINCIDE
     4) fecha factura en [orden-15d, orden+90d]  ó FECHA_FUERA_DE_RANGO
   =========================================================================== */

:- initialization(main, main).

/* Parámetros de negocio (espejo de api/config.php; S4 los pasa PHP). */
umbral_riesgo(40.0).
tolerancia_antes(15).    % días aceptables ANTES de la fecha de la orden
tolerancia_despues(90).  % días aceptables DESPUÉS de la fecha de la orden

main :-
    current_prolog_flag(argv, ArgvRaw),
    exclude(es_script, ArgvRaw,
            [_IdOrden, Folio, MontoS, FechaF, ProvF,
             RiesgoS, AuthS, ProvO, FechaO]),
    !,
    atom_number(MontoS, Monto),
    atom_number(RiesgoS, Riesgo),
    atom_number(AuthS, Auth),
    (   conciliar(factura(Folio, Monto, FechaF, ProvF),
                  orden(Auth, ProvO, FechaO), Riesgo, Veredicto)
    ->  true
    ;   Veredicto = 'DISCREPANCIA:ERROR_CONCILIACION'
    ),
    format("~w~n", [Veredicto]).
main :-
    format("DISCREPANCIA:ARGUMENTOS_INVALIDOS~n").

/* Al ejecutar "swipl regla.pl args...", argv puede traer el script:
   se descarta cualquier argumento que termine en ".pl". */
es_script(S) :- sub_atom(S, _, 3, 0, '.pl').

/* ---- Reglas de conciliación (orden = prioridad) ------------------------- */
conciliar(factura(_, Monto, _, _), orden(Auth, _, _), _,
          'DISCREPANCIA:MONTO_EXCEDE_ORDEN') :-
    Monto > Auth, !.
conciliar(_, _, Riesgo, 'DISCREPANCIA:RIESGO_ALTO') :-
    umbral_riesgo(U), Riesgo >= U, !.
conciliar(factura(_, _, _, ProvF), orden(_, ProvO, _),
          _, 'DISCREPANCIA:PROVEEDOR_NO_COINCIDE') :-
    \+ proveedor_coincide(ProvF, ProvO), !.
conciliar(factura(_, _, FechaF, _), orden(_, _, FechaO), _,
          'DISCREPANCIA:FECHA_FUERA_DE_RANGO') :-
    \+ fecha_en_rango(FechaF, FechaO), !.
conciliar(_, _, _, 'APROBADA').

/* ---- Proveedores: comparación tolerante ----------------------------------
   El OCR lee "PAPELERÍA CENTRAL, S.A. DE C.V." y la BD guarda
   "Papelería Central": se normalizan (mayúsculas, sin acentos, solo
   letras y números) y se exige que un prefijo de 8 caracteres de uno
   esté contenido en el otro. ------------------------------------------------ */
proveedor_coincide(PF, PO) :-
    normaliza(PF, NF), normaliza(PO, NO),
    prefijo(NF, 8, K), K \== '',
    (   sub_string(NO, _, _, _, K)
    ->  true
    ;   prefijo(NO, 8, K2), K2 \== '', sub_string(NF, _, _, _, K2)
    ).

prefijo(S, N, P) :-
    string_length(S, L),
    (   L >= N -> sub_string(S, 0, N, _, P)
    ;   P = S
    ).

normaliza(A, N) :-
    atom_codes(A, Cs),
    normaliza_cs(Cs, Cs2),
    atom_codes(N, Cs2).

normaliza_cs([], []).
normaliza_cs([C|Cs], Ns) :-
    (   acento(C, X)                    -> Ns = [X|Resto]
    ;   C >= 0'a, C =< 0'z              -> X is C - 32, Ns = [X|Resto]
    ;   C >= 0'A, C =< 0'Z              -> Ns = [C|Resto]
    ;   C >= 0'0, C =< 0'9              -> Ns = [C|Resto]
    ;   Ns = Resto   % espacios, comas, puntos, etc. se descartan
    ),
    normaliza_cs(Cs, Resto).

% mayúsculas        % minúsculas
acento(193, 0'A).   acento(225, 0'A).   % Á á
acento(201, 0'E).   acento(233, 0'E).   % É é
acento(205, 0'I).   acento(237, 0'I).   % Í í
acento(211, 0'O).   acento(243, 0'O).   % Ó ó
acento(218, 0'U).   acento(250, 0'U).   % Ú ú
acento(220, 0'U).   acento(252, 0'U).   % Ü ü
acento(209, 0'N).   acento(241, 0'N).   % Ñ ñ

/* ---- Fechas: rango válido [orden - 15 días, orden + 90 días] -------------
   "YYYY-MM-DD" se convierte a número de días juliano para poder sumar. ---- */
fecha_en_rango(FechaF, FechaO) :-
    fecha_dias(FechaF, Df),
    fecha_dias(FechaO, Do),
    tolerancia_antes(A),
    tolerancia_despues(D),
    Df >= Do - A,
    Df =< Do + D.

fecha_dias(Fecha, Dias) :-
    atomic_list_concat(Partes, '-', Fecha),
    maplist(atom_number, Partes, [Y, M, D]),
    A  is (14 - M) // 12,
    Y2 is Y + 4800 - A,
    M2 is M + 12 * A - 3,
    Dias is D + (153 * M2 + 2) // 5 + 365 * Y2
             + Y2 // 4 - Y2 // 100 + Y2 // 400 - 32045.

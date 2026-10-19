# test_s8.ps1 - Checkpoint C8 del Sprint 8 "Cuentas por pagar" (RF-P1..P3)
# -----------------------------------------------------------------------------
# Prueba por HTTP el apartado de pagos del CLIENTE: listado por pagar/pagadas,
# marcar y desmarcar pago, reglas de negocio (solo Aprobadas) y de roles.
# Requisitos: Apache + MySQL de Laragon activos, migrar_v1.sql aplicado y
# desplegar.ps1 ya corrido. Regla del proyecto: ASCII puro.
# -----------------------------------------------------------------------------
$base = 'http://localhost/cpp/api'

function Invoke-Cpp([string]$metodo, [string]$url, $body, [string]$token) {
    $h = @{}
    if ($token) { $h['X-CPP-Token'] = $token }
    $p = @{ Uri = $url; Method = $metodo; Headers = $h; ContentType = 'application/json' }
    if ($body -ne $null) { $p['Body'] = ($body | ConvertTo-Json -Compress -Depth 6) }
    try {
        $r = Invoke-RestMethod @p
        return [pscustomobject]@{ http = 200; codigo = $r.codigo; mensaje = $r.mensaje; datos = $r.datos }
    } catch {
        $resp = $_.Exception.Response
        if ($resp -eq $null) { return [pscustomobject]@{ http = 0; codigo = 'SIN_CONEXION'; mensaje = $_.Exception.Message; datos = $null } }
        $code = [int]$resp.StatusCode
        $texto = $_.ErrorDetails.Message
        if (-not $texto -and $resp.GetResponseStream()) {
            try { $sr = New-Object IO.StreamReader($resp.GetResponseStream()); $texto = $sr.ReadToEnd() } catch { }
        }
        try {
            $j = $texto | ConvertFrom-Json
            return [pscustomobject]@{ http = $code; codigo = $j.codigo; mensaje = $j.mensaje; datos = $j.datos }
        } catch {
            return [pscustomobject]@{ http = $code; codigo = 'SIN_JSON'; mensaje = "$texto"; datos = $null }
        }
    }
}

function Paso([string]$id, [string]$esperado, $real) {
    $ok = if ("$esperado" -eq "$($real.codigo)") { 'OK ' } else { 'FAIL' }
    Write-Host ("[{0}] {1}: esperado {2} / obtenido {3} (HTTP {4}) - {5}" -f $ok, $id, $esperado, $real.codigo, $real.http, $real.mensaje)
}

# ---------- T1: GET pagos como capturista ----------
$cap = Invoke-Cpp Post "$base/sesion.php" @{ usuario = 'capturista'; password = 'demo1234' } $null
$tokCap = $cap.datos.token
$t1 = Invoke-Cpp Get "$base/pagos.php" $null $tokCap
Paso 'T1 GET pagos (capturista)' 'OK' $t1
Write-Host ("      por pagar: {0} · pagadas: {1}" -f $t1.datos.total_por_pagar, $t1.datos.total_pagadas)

# ---------- T2/T3: revisor y proveedor NO pagan ----------
$rev = Invoke-Cpp Post "$base/sesion.php" @{ usuario = 'revisor'; password = 'demo1234' } $null
$tokRev = $rev.datos.token
$t2 = Invoke-Cpp Post "$base/pagos.php" @{ accion = 'pagar'; id = 1 } $tokRev
Paso 'T2 revisor intenta pagar' 'ROL_NO_PERMITIDO' $t2

$prov = Invoke-Cpp Post "$base/sesion.php" @{ usuario = 'proveedor'; password = 'demo1234' } $null
$tokProv = $prov.datos.token
$t3 = Invoke-Cpp Post "$base/pagos.php" @{ accion = 'pagar'; id = 1 } $tokProv
Paso 'T3 proveedor intenta pagar' 'ROL_NO_PERMITIDO' $t3

# ---------- T2b: el revisor SI puede CONSULTAR ----------
$t2b = Invoke-Cpp Get "$base/pagos.php" $null $tokRev
Paso 'T2b revisor consulta pagos' 'OK' $t2b

# ---------- T4: pagar una Aprobada ----------
$objetivo = $t1.datos.por_pagar | Select-Object -First 1
if ($objetivo -eq $null) { Write-Host '[FAIL] T4: no hay facturas Aprobadas por pagar (sube una primero)'; exit 1 }
$idA = $objetivo.id
$t4 = Invoke-Cpp Post "$base/pagos.php" @{ accion = 'pagar'; id = $idA } $tokCap
Paso "T4 pagar factura Aprobada #$idA" 'OK' $t4

$t4b = Invoke-Cpp Get "$base/pagos.php" $null $tokCap
$esta = $t4b.datos.pagadas | Where-Object { $_.id -eq $idA }
if ($esta -ne $null) {
    Write-Host ("[OK ] T4b la #$idA ahora esta en PAGADAS (pagada_en: {0})" -f $esta.pagada_en)
} else {
    Write-Host '[FAIL] T4b la factura no aparecio en pagadas'
}

# ---------- T5: pagar dos veces ----------
$t5 = Invoke-Cpp Post "$base/pagos.php" @{ accion = 'pagar'; id = $idA } $tokCap
Paso 'T5 pagar dos veces' 'YA_PAGADA' $t5

# ---------- T6: pagar una Pendiente (sin veredicto) ----------
$pends = Invoke-Cpp Get "$base/facturas.php?estado=Pendiente" $null $tokCap
$idP = ($pends.datos.facturas | Select-Object -First 1).id
if ($idP -ne $null) {
    $t6 = Invoke-Cpp Post "$base/pagos.php" @{ accion = 'pagar'; id = $idP } $tokCap
    Paso "T6 pagar Pendiente #$idP" 'FACTURA_SIN_VEREDICTO' $t6
} else {
    Write-Host '[INFO] T6 omitido: no hay facturas Pendientes'
}

# ---------- T6b: pagar una Con Discrepancia (resuelta a mano) ----------
$disc2 = Invoke-Cpp Get "$base/facturas.php?estado=Con+Discrepancia" $null $tokCap
$idDis = ($disc2.datos.facturas | Where-Object { $_.pagada -eq 0 } | Select-Object -First 1).id
if ($idDis -ne $null) {
    $t6b = Invoke-Cpp Post "$base/pagos.php" @{ accion = 'pagar'; id = $idDis } $tokCap
    Paso "T6b pagar Con Discrepancia #$idDis (resuelta)" 'OK' $t6b
} else {
    Write-Host '[INFO] T6b omitido: no hay discrepancias sin pagar'
}

# ---------- T7: despagar y volver a pagar (queda PAGADA para la demo) ----------
$t7 = Invoke-Cpp Post "$base/pagos.php" @{ accion = 'despagar'; id = $idA } $tokCap
Paso "T7 despagar #$idA" 'OK' $t7
$t7b = Invoke-Cpp Post "$base/pagos.php" @{ accion = 'despagar'; id = $idA } $tokCap
Paso 'T7b despagar lo no pagado' 'NO_ESTA_PAGADA' $t7b
$t7c = Invoke-Cpp Post "$base/pagos.php" @{ accion = 'pagar'; id = $idA } $tokCap
Paso "T7c re-pagar #$idA (queda pagada para la demo)" 'OK' $t7c

# ---------- T8: filtro ?pagada en facturas.php ----------
$t8 = Invoke-Cpp Get "$base/facturas.php?pagada=1" $null $tokCap
Paso 'T8 facturas.php?pagada=1' 'OK' $t8

# ---------- T9: no puedes pagar la cuenta de OTRO cliente ----------
$adm2 = Invoke-Cpp Post "$base/sesion.php" @{ usuario = 'admin'; password = 'demo1234' } $null
$tokAdm2 = $adm2.datos.token
$t9 = Invoke-Cpp Post "$base/pagos.php" @{ accion = 'pagar'; id = $idA } $tokAdm2
Paso "T9 admin intenta pagar la #$idA (subida por capturista)" 'FACTURA_DE_OTRO_CLIENTE' $t9

Write-Host ''
Write-Host 'Checkpoint C8 terminado.'


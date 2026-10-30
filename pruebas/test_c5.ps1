# test_c5.ps1 - Checkpoint C5 del Sprint 5 "Acceso y roles" (RF-M1/B6/B4)
# ---------------------------------------------------------------
# Prueba por HTTP el login, el control por rol, el CRUD y la regresion del
# pipeline contra http://localhost/cpp/api
# Requisitos: Apache + MySQL de Laragon activos y desplegar.ps1 ya corrido.
# Regla del proyecto: este .ps1 va en ASCII puro (PowerShell 5.1 sin BOM).
# ---------------------------------------------------------------
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
        # PS 5.1: Invoke-RestMethod ya consumio el stream; el cuerpo llega en ErrorDetails
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

# ---------- T1-T3: login de los 3 usuarios semilla ----------
$admin = Invoke-Cpp Post "$base/sesion.php" @{ usuario = 'admin'; password = 'demo1234' } $null
Paso 'T1 login admin' 'OK' $admin
$tokAdmin = $admin.datos.token

$capt = Invoke-Cpp Post "$base/sesion.php" @{ usuario = 'capturista'; password = 'demo1234' } $null
Paso 'T2 login capturista' 'OK' $capt
$tokCapt = $capt.datos.token

$rev = Invoke-Cpp Post "$base/sesion.php" @{ usuario = 'revisor'; password = 'demo1234' } $null
Paso 'T3 login revisor' 'OK' $rev
$tokRev = $rev.datos.token

# T3b: credenciales MALAS -> 401
$mal = Invoke-Cpp Post "$base/sesion.php" @{ usuario = 'admin'; password = 'otra-clave' } $null
Paso 'T3b login con clave mal' 'CREDENCIALES_INVALIDAS' $mal

# ---------- T4-T6: proteccion por token y por rol ----------
$t4 = Invoke-Cpp Get "$base/facturas.php" $null
Paso 'T4 facturas SIN token' 'NO_AUTENTICADO' $t4

$t5 = Invoke-Cpp Get "$base/usuarios.php" $null $tokCapt
Paso 'T5 capturista a usuarios.php' 'ROL_NO_PERMITIDO' $t5

$t6 = Invoke-Cpp Post "$base/facturas.php" @{ orden_id = 1; imagen_base64 = 'x' } $tokRev
Paso 'T6 revisor intenta ENVIAR factura' 'ROL_NO_PERMITIDO' $t6

# ---------- T7-T8: CRUD (solo admin) ----------
$t7 = Invoke-Cpp Post "$base/usuarios.php" @{ accion = 'crear'; nombre = 'Prueba C5'; usuario = 'c5demo'; password = 'clave123'; rol = 'capturista' } $tokAdmin
Paso 'T7 admin crea usuario c5demo' 'OK' $t7

$t7b = Invoke-Cpp Post "$base/usuarios.php" @{ accion = 'crear'; nombre = 'Dup'; usuario = 'c5demo'; password = 'clave123'; rol = 'capturista' } $tokAdmin
Paso 'T7b usuario duplicado' 'USUARIO_DUPLICADO' $t7b

$t8 = Invoke-Cpp Post "$base/ordenes.php" @{ accion = 'crear'; codigo = 'OC-C5'; proveedor = 'Prueba Sprint 5'; monto_autorizado = 1234.50; fecha_emision = '2026-10-09' } $tokAdmin
Paso 'T8 admin crea orden OC-C5' 'OK' $t8

# T8b: el usuario nuevo (bcrypt) SI puede entrar
$t8b = Invoke-Cpp Post "$base/sesion.php" @{ usuario = 'c5demo'; password = 'clave123' } $null
Paso 'T8b login del usuario NUEVO (bcrypt)' 'OK' $t8b

# T8c: nadie puede desactivar al ultimo admin
$t8c = Invoke-Cpp Post "$base/usuarios.php" @{ accion = 'actualizar'; id = 1; activo = 0 } $tokAdmin
Paso 'T8c proteger ultimo admin' 'ULTIMO_ADMIN' $t8c

# ---------- T9: regresion del pipeline (guion 9.2) como capturista ----------
function Enviar-Foto([string]$foto, [int]$ordenId, [string]$token) {
    $b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\CPP\motores\spike\fotos\$foto"))
    $r = Invoke-Cpp Post "$base/facturas.php" @{ orden_id = $ordenId; imagen_base64 = $b64 } $token
    if ($r.codigo -ne 'OK') { return $r }
    return Invoke-Cpp Get ("$base/procesar.php?id=" + $r.datos.factura_id) $null $token
}

$a = Enviar-Foto 'factura_01.jpg' 1 $tokCapt
Write-Host ("[INFO] A: {0} / estado {1} / riesgo {2} / motivo {3}" -f $a.codigo, $a.datos.estado, $a.datos.riesgo, $a.datos.motivo_discrepancia)

$b = Enviar-Foto 'factura_06.jpg' 1 $tokCapt
Write-Host ("[INFO] B: {0} / estado {1} / riesgo {2} / motivo {3}" -f $b.codigo, $b.datos.estado, $b.datos.riesgo, $b.datos.motivo_discrepancia)

$c = Enviar-Foto 'factura_04.jpg' 2 $tokCapt
Write-Host ("[INFO] C: {0} / estado {1} / motivo {2}" -f $c.codigo, $c.datos.estado, $c.datos.motivo_discrepancia)

if ($a.datos.estado -eq 'Aprobada' -and $b.datos.motivo_discrepancia -eq 'MONTO_EXCEDE_ORDEN' -and $c.datos.motivo_discrepancia -eq 'PROVEEDOR_NO_COINCIDE') {
    Write-Host '[OK ] T9 regresion 9.2: trio EXACTO (A aprobada / B MONTO_EXCEDE / C PROVEEDOR_NO_COINCIDE)'
} else {
    Write-Host '[FAIL] T9 regresion 9.2: el trio NO coincidio'
}

# ---------- T10: logout y token muerto ----------
$t10 = Invoke-Cpp Delete "$base/sesion.php" $null $tokCapt
Paso 'T10 logout capturista' 'OK' $t10

$t10b = Invoke-Cpp Get "$base/facturas.php" $null $tokCapt
Paso 'T10b token YA cerrado' 'NO_AUTENTICADO' $t10b

Write-Host ''
Write-Host 'Checkpoint C5 terminado.'

# test_s7.ps1 - Checkpoint C7 del Sprint 7 "Cuentas de proveedor" (RF-F5/F6/F7)
# -----------------------------------------------------------------------------
# Prueba por HTTP el registro/login de proveedores (con su negocio), el filtro
# de ordenes y facturas por negocio, el envio de facturas desde la cuenta del
# proveedor y las regresiones de roles, contra http://localhost/cpp/api
# Requisitos: Apache + MySQL de Laragon activos, migrar_v1.sql aplicado y
# desplegar.ps1 ya corrido.
# Regla del proyecto: este .ps1 va en ASCII puro (PowerShell 5.1 sin BOM).
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

# ---------- T1: registro publico de un proveedor NUEVO (RF-F5) ----------
$t1 = Invoke-Cpp Post "$base/registro_proveedor.php" @{ usuario = 'ferreteria87'; password = 'clave123'; negocio = 'Ferreteria El Tornillo' } $null
Paso 'T1 registro proveedor nuevo' 'OK' $t1
if ("$($t1.codigo)" -eq 'OK') {
    Write-Host ("      negocio devuelto: {0} / rol: {1}" -f $t1.datos.usuario.negocio, $t1.datos.usuario.rol)
}

$t1b = Invoke-Cpp Post "$base/registro_proveedor.php" @{ usuario = 'ferreteria87'; password = 'clave123'; negocio = 'Ferreteria El Tornillo' } $null
Paso 'T1b registro duplicado' 'USUARIO_DUPLICADO' $t1b

$t1c = Invoke-Cpp Post "$base/registro_proveedor.php" @{ usuario = 'otro'; password = 'clave123'; negocio = 'AB' } $null
Paso 'T1c negocio demasiado corto' 'ERROR_VALIDACION' $t1c

# ---------- T2: login del proveedor DEMO del seed (RF-F5) ----------
$t2 = Invoke-Cpp Post "$base/sesion.php" @{ usuario = 'proveedor'; password = 'demo1234' } $null
Paso 'T2 login proveedor demo' 'OK' $t2
$tokProv = $t2.datos.token
Write-Host ("      negocio en la sesion: {0}" -f $t2.datos.usuario.negocio)

# ---------- T3: el proveedor SOLO ve las ordenes de su negocio (RF-F7) ----------
$t3 = Invoke-Cpp Get "$base/ordenes.php" $null $tokProv
$codigos = ($t3.datos.ordenes | ForEach-Object { $_.codigo }) -join ','
Write-Host ("      ordenes visibles para el proveedor: {0}" -f $codigos)
if ("$codigos" -eq 'OC-001') {
    Write-Host '[OK ] T3 ordenes del proveedor: solo OC-001 (Papeleria Central)'
} else {
    Write-Host '[FAIL] T3 ordenes del proveedor: se esperaba solo OC-001'
}

$t3b = Invoke-Cpp Get "$base/ordenes.php?id=2" $null $tokProv
Paso 'T3b detalle de orden ajena' 'ORDEN_DE_OTRO_PROVEEDOR' $t3b

# ---------- T4: el proveedor ENVIA una factura a SU orden (RF-F6) ----------
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\CPP\motores\spike\fotos\factura_01.jpg"))
$t4 = Invoke-Cpp Post "$base/facturas.php" @{ orden_id = 1; imagen_base64 = $b64 } $tokProv
Paso 'T4 proveedor envia factura a SU orden' 'OK' $t4

# ---------- T5: ...y NO puede a una orden ajena (RF-F6) ----------
$t5 = Invoke-Cpp Post "$base/facturas.php" @{ orden_id = 2; imagen_base64 = $b64 } $tokProv
Paso 'T5 factura a orden de OTRO proveedor' 'ORDEN_DE_OTRO_PROVEEDOR' $t5

# ---------- T6: el listado de facturas solo trae SU negocio (RF-F7) ----------
$t6 = Invoke-Cpp Get "$base/facturas.php" $null $tokProv
$prov = (($t6.datos.facturas | ForEach-Object { $_.orden_proveedor } | Sort-Object -Unique) -join ',')
Write-Host ("      proveedores en el listado del proveedor: {0}" -f $prov)
if ("$prov" -like 'Papeler*') {
    Write-Host '[OK ] T6 listado de facturas: solo las de Papeleria Central'
} else {
    Write-Host '[FAIL] T6 listado de facturas: aparecen negocios ajenos'
}

# ---------- T7: regresion - el revisor sigue SIN poder enviar ----------
$rev = Invoke-Cpp Post "$base/sesion.php" @{ usuario = 'revisor'; password = 'demo1234' } $null
$tokRev = $rev.datos.token
$t7 = Invoke-Cpp Post "$base/facturas.php" @{ orden_id = 1; imagen_base64 = $b64 } $tokRev
Paso 'T7 regresion: revisor no envia' 'ROL_NO_PERMITIDO' $t7

# ---------- T8: el admin tambien puede crear proveedores (CRUD, RF-F5) ----------
$adm = Invoke-Cpp Post "$base/sesion.php" @{ usuario = 'admin'; password = 'demo1234' } $null
$tokAdm = $adm.datos.token
$t8 = Invoke-Cpp Post "$base/usuarios.php" @{ accion = 'crear'; nombre = 'Prov Norte'; usuario = 'provnorte'; password = 'clave123'; rol = 'proveedor'; negocio = 'Oficinas del Norte' } $tokAdm
Paso 'T8 admin crea proveedor con negocio' 'OK' $t8

$t8b = Invoke-Cpp Post "$base/sesion.php" @{ usuario = 'provnorte'; password = 'clave123' } $null
Paso 'T8b login del proveedor creado por admin' 'OK' $t8b
Write-Host ("      negocio: {0}" -f $t8b.datos.usuario.negocio)

$t8c = Invoke-Cpp Post "$base/usuarios.php" @{ accion = 'crear'; nombre = 'Sin Negocio'; usuario = 'sinnegocio'; password = 'clave123'; rol = 'proveedor' } $tokAdm
Paso 'T8c proveedor SIN negocio rechazado' 'ERROR_VALIDACION' $t8c

# ---------- T9: el proveedor NO toca el CRUD de usuarios ----------
$t9 = Invoke-Cpp Get "$base/usuarios.php" $null $tokProv
Paso 'T9 proveedor al CRUD de usuarios' 'ROL_NO_PERMITIDO' $t9

Write-Host ''
Write-Host 'Checkpoint C7 terminado.'

# test_s9.ps1 - Checkpoint C9 del Sprint 9 "Inteligencia del tablero" (RF-L3/D5/M7)
# -----------------------------------------------------------------------------
# Prueba por HTTP lo nuevo del S9: deteccion de DUPLICADOS (RF-L3, regla 0 de
# regla.pl + pre-check PHP), persistencia del TEXTO crudo del OCR (RF-D5),
# vencimientos de cuentas por pagar (RF-M7) y el pago CON comprobante de foto
# (S8.3, sincronizado con la app). Ademas los 2 motores tocados por CLI.
# Requisitos: Apache + MySQL de Laragon activos, migrar_v1.sql aplicado (hasta
# su seccion 7) y desplegar.ps1 corrido. Regla del proyecto: ASCII puro.
# NOTA: T1 asume que ya existe una factura con folio F-1001 procesada (p. ej.
# tras correr test_c5.ps1); si la BD esta vacia, sube una primero.
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

# ---------- T1: subir una factura YA registrada -> FACTURA_DUPLICADA ----------
$cap = Invoke-Cpp Post "$base/sesion.php" @{ usuario = 'capturista'; password = 'demo1234' } $null
$tokCap = $cap.datos.token
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\CPP\motores\spike\fotos\factura_01.jpg'))
$r1 = Invoke-Cpp Post "$base/facturas.php" @{ orden_id = 1; imagen_base64 = $b64 } $tokCap
$idDup = $r1.datos.factura_id
$p1 = Invoke-Cpp Get ("$base/procesar.php?id=" + $idDup) $null $tokCap
Paso "T1 procesar duplicada #$idDup" 'OK' $p1
if ($p1.datos.motivo_discrepancia -eq 'FACTURA_DUPLICADA' -and $p1.datos.duplicada_de) {
    Write-Host ("      [OK ] T1b motivo FACTURA_DUPLICADA - duplicada_de #{0} (RF-L3)" -f $p1.datos.duplicada_de)
} else {
    Write-Host ('[FAIL] T1b se esperaba FACTURA_DUPLICADA y vino: ' + $p1.datos.motivo_discrepancia)
}

# ---------- T2: el detalle de la factura trae el TEXTO crudo del OCR ----------
$d2 = Invoke-Cpp Get ("$base/facturas.php?id=" + $idDup) $null $tokCap
Paso 'T2 detalle de factura' 'OK' $d2
$texto = $d2.datos.factura.texto_ocr
if ($texto -and $texto.Length -gt 50) {
    Write-Host ("      [OK ] T2b texto_ocr persistido: {0} caracteres (RF-D5)" -f $texto.Length)
} else {
    Write-Host '[FAIL] T2b el detalle no trae texto_ocr'
}

# ---------- T3: por_pagar trae fecha_limite / dias_restantes / vence_pronto ----------
$t3 = Invoke-Cpp Get "$base/pagos.php" $null $tokCap
Paso 'T3 GET pagos (capturista)' 'OK' $t3
$ej = $t3.datos.por_pagar | Select-Object -First 1
if ($ej -and $ej.fecha_limite -and ($ej.dias_restantes -ne $null) -and ($ej.vence_pronto -ne $null)) {
    Write-Host ("      [OK ] T3b #{0}: limite {1} - dias {2} - vence_pronto {3} (RF-M7)" -f $ej.id, $ej.fecha_limite, $ej.dias_restantes, $ej.vence_pronto)
} else {
    Write-Host '[FAIL] T3b faltan los campos de vencimiento'
}

# ---------- T4: comprobar SIN foto -> rechazado (S8.3) ----------
$t4 = Invoke-Cpp Post "$base/pagos.php" @{ accion = 'comprobar'; id = $ej.id } $tokCap
Paso 'T4 comprobar sin imagen_base64' 'ERROR_VALIDACION' $t4

# ---------- T5: comprobar CON foto -> pagada con evidencia ----------
$t5 = Invoke-Cpp Post "$base/pagos.php" @{ accion = 'comprobar'; id = $ej.id; imagen_base64 = $b64 } $tokCap
Paso ("T5 pagar #{0} con comprobante" -f $ej.id) 'OK' $t5
$t5b = Invoke-Cpp Get "$base/pagos.php" $null $tokCap
$pag = $t5b.datos.pagadas | Where-Object { $_.id -eq $ej.id }
if ($pag -and $pag.evidencia_pago) {
    Write-Host ('[OK ] T5b en PAGADAS con evidencia: ' + $pag.evidencia_pago)
} else {
    Write-Host '[FAIL] T5b no aparecio en pagadas con evidencia'
}

# ---------- T6: pagar dos veces ----------
$t6 = Invoke-Cpp Post "$base/pagos.php" @{ accion = 'comprobar'; id = $ej.id; imagen_base64 = $b64 } $tokCap
Paso 'T6 pagar dos veces' 'YA_PAGADA' $t6

# ---------- T7: despagar (queda por pagar para la demo) ----------
$t7 = Invoke-Cpp Post "$base/pagos.php" @{ accion = 'despagar'; id = $ej.id } $tokCap
Paso "T7 despagar #$($ej.id)" 'OK' $t7

# ---------- T8: regla.pl por CLI - la regla 0 de duplicados ----------
$swipl = 'C:\Program Files\swipl\bin\swipl.exe'
$v1 = (& $swipl C:\CPP\motores\regla.pl 1 F-9999 100.00 2026-09-20 PAPELERIA 10.0 5000.00 PAPELERIA 2026-09-10 si) -join ''
$v2 = (& $swipl C:\CPP\motores\regla.pl 1 F-9999 100.00 2026-09-20 PAPELERIA 10.0 5000.00 PAPELERIA 2026-09-10 no) -join ''
$v3 = (& $swipl C:\CPP\motores\regla.pl 1 F-9999 100.00 2026-09-20 PAPELERIA 10.0 5000.00 PAPELERIA 2026-09-10) -join ''
if ($v1 -eq 'DISCREPANCIA:FACTURA_DUPLICADA') { Write-Host '[OK ] T8a dup=si  -> FACTURA_DUPLICADA' }
else { Write-Host ('[FAIL] T8a dup=si dio: ' + $v1) }
if ($v2 -eq 'APROBADA') { Write-Host '[OK ] T8b dup=no  -> APROBADA' }
else { Write-Host ('[FAIL] T8b dup=no dio: ' + $v2) }
if ($v3 -eq 'DISCREPANCIA:ARGUMENTOS_INVALIDOS') { Write-Host '[OK ] T8c 9 args (contrato viejo) -> ARGUMENTOS_INVALIDOS' }
else { Write-Host ('[FAIL] T8c contrato viejo dio: ' + $v3) }

# ---------- T9: el OCR devuelve el campo "texto" (RF-D5) ----------
$py = 'C:\Users\acaba\AppData\Local\Programs\Python\Python312\python.exe'
$j9 = (& $py C:\CPP\motores\procesar_factura.py C:\CPP\motores\spike\fotos\factura_01.jpg) -join '' | ConvertFrom-Json
if ($j9.texto -and $j9.texto.Length -gt 50) {
    Write-Host ("[OK ] T9 OCR con campo texto ({0} caracteres)" -f $j9.texto.Length)
} else {
    Write-Host '[FAIL] T9 el OCR no devuelve texto'
}

Write-Host ''
Write-Host 'Checkpoint C9 terminado.'

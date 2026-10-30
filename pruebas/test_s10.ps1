# test_s10.ps1 - Checkpoint C10 del Sprint 10 "Sin servidor" (Firebase puro)
# -----------------------------------------------------------------------------
# PRUEBA SIN LARAGON: NO necesita Apache ni MySQL iniciados. Solo requiere:
#   1) php.exe en el PATH (o el interprete de Laragon: C:\laragon\bin\php\*\php.exe
#      ... sin arrancar Laragon: solo se presta el .exe)
#   2) api\firebase-service-account.json (SECRETO, fuera de git)
#   3) la CLAVE de las cuentas demo (fuera del repo, correcciones 22/10):
#      -Clave <clave>  o  la variable de entorno CPP_DEMO_CLAVE
# Verifica por REST contra el proyecto Firebase (cpp-facturas) que la app S10
# tiene todo lo que necesita:
#   T1 Auth    : proveedor Email/Password HABILITADO (habilitar_email_auth.php)
#   T2 Auth    : el usuario demo cliente@cpp.demo existe (semilla)
#   T3 Firestore: su perfil usuarios/{uid} con rol cliente
#   T4 Firestore: perfil del proveedor (rol + negocio) y su cuenta en Auth
#   T5 Firestore: 3 cuentas (coleccion `ordenes`) para cliente@cpp.demo,
#                todas pendientes (OC-101/102/103) - subidas por el proveedor
#   T6 Datos M7: 1 cuenta VENCIDA y 1 que vence en <=3 dias (alertas rojas)
# USO:
#   powershell -File C:\CPP\test_s10.ps1             # solo verifica (lectura)
#   powershell -File C:\CPP\test_s10.ps1 -Semilla    # re-siembra demo antes
#   (en ambos casos: -Clave '...' o $env:CPP_DEMO_CLAVE = '...')
# Regla del proyecto: ASCII puro.
# -----------------------------------------------------------------------------
param([switch]$Semilla, [string]$Clave)

$apiDir = 'C:\CPP\api'

# ---------- clave demo (correcciones 22/10): -Clave o CPP_DEMO_CLAVE ----------
$claveDemo = $Clave
if (-not $claveDemo) { $claveDemo = $env:CPP_DEMO_CLAVE }

# ---------- php.exe: PATH o Laragon (solo el interprete, sin servicios) -------
$php = $null
$cmd = Get-Command php -ErrorAction SilentlyContinue
if ($cmd) { $php = $cmd.Source }
if (-not $php) {
    $php = (Get-ChildItem 'C:\laragon\bin\php\*\php.exe' -ErrorAction SilentlyContinue |
            Select-Object -First 1).FullName
}
if (-not $php) {
    Write-Host '[FAIL] Sin php.exe (ni en PATH ni en C:\laragon\bin\php).'
    exit 1
}
Write-Host ("php   : " + $php)

if (-not (Test-Path (Join-Path $apiDir 'firebase-service-account.json'))) {
    Write-Host '[FAIL] Falta api\firebase-service-account.json (secreto, fuera de git).'
    exit 1
}

# ---------- (opcional) re-sembrar los datos demo (re-ejecutable) ---------------
if ($Semilla) {
    if (-not $claveDemo) {
        Write-Host '[FAIL] -Semilla necesita la clave demo: -Clave <clave> o $env:CPP_DEMO_CLAVE.'
        exit 1
    }
    Write-Host '--- php api\semilla_firebase.php ---'
    & $php (Join-Path $apiDir 'semilla_firebase.php') $claveDemo
    if ($LASTEXITCODE -ne 0) { Write-Host '[FAIL] La semilla fallo.'; exit 1 }
    Write-Host ''
}

# ---------- token OAuth2 + proyecto (reusa el JWT RS256 de firebase.php) -------
$sa = ($apiDir -replace '\\', '/') + '/firebase-service-account.json'
$fb  = $sa -replace '/firebase-service-account\.json$', ''
$tok  = & $php -d display_errors=0 -r "define('FIREBASE_SA','$sa'); require '$fb/firebase.php'; echo fb_token();"
$proj = & $php -d display_errors=0 -r "define('FIREBASE_SA','$sa'); require '$fb/firebase.php'; echo fb_proyecto();"
if (-not $tok -or $tok.Length -lt 50) {
    Write-Host ('[FAIL] Sin token OAuth2 (revisa la clave de servicio): ' + $tok)
    exit 1
}
Write-Host ("proyecto: " + $proj + "  (token OAuth2 ok)")
$h = @{ Authorization = 'Bearer ' + $tok }

$total = 0; $okCount = 0
function Paso([string]$id, [bool]$ok, [string]$detalle) {
    $script:total++
    if ($ok) { $script:okCount++ }
    Write-Host ("[{0}] {1} - {2}" -f ($(if ($ok) {'OK '} else {'FAIL'})), $id, $detalle)
}

# ---------- T1: Email/Password habilitado + login demo funcional ----------------
# Se lee el config v2 (el v1 ya no existe) y, si esta habilitado, se hace un
# login REAL (signInWithPassword) con la Web API key del propio config y las
# credenciales demo: exactamente lo que necesita la app para entrar.
try {
    $cfg = Invoke-RestMethod -Uri ("https://identitytoolkit.googleapis.com/v2/projects/{0}/config" -f $proj) -Headers $h -TimeoutSec 20
    $on = ($cfg.signIn.email.enabled -eq $true)
    if (-not $on) {
        Paso 'T1 Email/Password habilitado + login demo' $false `
            ("signIn.email.enabled=" + $cfg.signIn.email.enabled + " => corre php C:\CPP\api\habilitar_email_auth.php")
    } elseif (-not $claveDemo) {
        # El login real necesita la clave demo: fuera del repo (correcciones 22/10).
        Paso 'T1 Email/Password habilitado + login demo' $false `
            'signIn.email OK, pero falta la clave demo: -Clave <clave> o $env:CPP_DEMO_CLAVE'
    } else {
        $body = @{ email = 'cliente@cpp.demo'; password = $claveDemo; returnSecureToken = $true } | ConvertTo-Json -Compress
        $r = Invoke-RestMethod -Method Post `
            -Uri ("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={0}" -f $cfg.client.apiKey) `
            -ContentType 'application/json' -Body $body -TimeoutSec 20
        Paso 'T1 Email/Password habilitado + login demo' ($r.idToken -ne $null) `
            ("enabled=true y signInWithPassword ok para " + $r.email)
    }
} catch {
    Paso 'T1 Email/Password habilitado + login demo' $false $_.Exception.Message
}

# ---------- T2/T4a: usuarios demo en Auth --------------------------------------
function Lookup-Uid([string]$correo) {
    $body = @{ email = @($correo) } | ConvertTo-Json -Compress
    $r = Invoke-RestMethod -Method Post `
        -Uri ("https://identitytoolkit.googleapis.com/v1/projects/{0}/accounts:lookup" -f $proj) `
        -Headers $h -ContentType 'application/json' -Body $body -TimeoutSec 20
    return $r.users[0].localId
}
$uidCap = $null; $uidProv = $null
try { $uidCap  = Lookup-Uid 'cliente@cpp.demo' } catch { }
try { $uidProv = Lookup-Uid 'proveedor@cpp.demo'  } catch { }
Paso 'T2 Auth cliente@cpp.demo' ($uidCap -ne $null) ("uid=" + $uidCap)
Paso 'T4a Auth proveedor@cpp.demo' ($uidProv -ne $null) ("uid=" + $uidProv)

# ---------- T3/T4b: perfiles de Firestore (usuarios/{uid}) ---------------------
function Perfil([string]$uid) {
    Invoke-RestMethod -Uri ("https://firestore.googleapis.com/v1/projects/{0}/databases/(default)/documents/usuarios/{1}" -f $proj, $uid) `
        -Headers $h -TimeoutSec 20
}
if ($uidCap) {
    try {
        $p = Perfil $uidCap
        Paso 'T3 perfil rol=cliente' ($p.fields.rol.stringValue -eq 'cliente') ("rol=" + $p.fields.rol.stringValue + " empresa=" + [string]$p.fields.empresa.stringValue)
    } catch { Paso 'T3 perfil rol=cliente' $false $_.Exception.Message }
} else { Paso 'T3 perfil rol=cliente' $false 'sin uid (fallo T2)' }

if ($uidProv) {
    try {
        $p = Perfil $uidProv
        $neg = [string]$p.fields.negocio.stringValue
        Paso 'T4b perfil proveedor+negocio' ($p.fields.rol.stringValue -eq 'proveedor' -and $neg -like 'Ferreter*') `
            ("rol=" + $p.fields.rol.stringValue + " negocio=" + $neg)
    } catch { Paso 'T4b perfil proveedor+negocio' $false $_.Exception.Message }
} else { Paso 'T4b perfil proveedor+negocio' $false 'sin uid (fallo T4a)' }

# ---------- T5/T6: cuentas que el proveedor subio para el cliente --------------
$q = @{
    structuredQuery = @{
        from  = @( @{ collectionId = 'ordenes' } )
        where = @{ fieldFilter = @{
            field = @{ fieldPath = 'clienteCorreo' }
            op    = 'EQUAL'
            value = @{ stringValue = 'cliente@cpp.demo' } } }
    }
} | ConvertTo-Json -Depth 8 -Compress

$filas = @()
try {
    $r = Invoke-RestMethod -Method Post `
        -Uri ("https://firestore.googleapis.com/v1/projects/{0}/databases/(default)/documents:runQuery" -f $proj) `
        -Headers $h -ContentType 'application/json' -Body $q -TimeoutSec 20
    $filas = @($r | Where-Object { $_.document })
    $codigos = @($filas | ForEach-Object { $_.document.fields.codigo.stringValue })
    $noPend = @($filas | Where-Object { $_.document.fields.estado.stringValue -ne 'pendiente' })
    $tres = ($filas.Count -eq 3 -and $codigos -contains 'OC-101' -and $codigos -contains 'OC-102' `
             -and $codigos -contains 'OC-103' -and $noPend.Count -eq 0)
    Paso 'T5 3 cuentas pendientes (OC-101/102/103)' $tres ("cuentas=" + $filas.Count + " [" + ($codigos -join ',') + "]")
} catch { Paso 'T5 3 cuentas pendientes (OC-101/102/103)' $false $_.Exception.Message }

if ($filas.Count -gt 0) {
    $hoy = Get-Date
    $vencidas = 0; $porVencer = 0
    foreach ($f in $filas) {
        $lim = [datetime]::ParseExact([string]$f.document.fields.fechaLimite.stringValue, 'yyyy-MM-dd', $null)
        $dias = [int]($lim - $hoy).TotalDays
        if ($dias -lt 0) { $vencidas++ } elseif ($dias -le 3) { $porVencer++ }
    }
    Paso 'T6 alertas M7: 1 vencida + 1 por vencer (<=3 dias)' ($vencidas -eq 1 -and $porVencer -eq 1) `
        ("vencidas=" + $vencidas + " porVencer=" + $porVencer)
} else { Paso 'T6 alertas M7: 1 vencida + 1 por vencer (<=3 dias)' $false 'sin cuentas (corre -Semilla)' }

# ---------- resumen -------------------------------------------------------------
Write-Host ''
if ($okCount -eq $total -and $total -eq 7) {
    Write-Host 'C10: 7/7 OK - Firebase queda listo para probar la app SIN Laragon.'
    Write-Host '     Entra con cliente@cpp.demo / la clave que configuraste (CPP_DEMO_CLAVE).'
    exit 0
} else {
    Write-Host ("C10: " + $okCount + "/" + $total + " - si faltan datos corre: powershell -File C:\CPP\test_s10.ps1 -Semilla")
    exit 1
}

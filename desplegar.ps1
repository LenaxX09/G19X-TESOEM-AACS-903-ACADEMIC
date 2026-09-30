# desplegar.ps1 — Sprint 4: publica el backend en el Document Root de Laragon.
#
# Copia api\ (con el nuevo procesar.php), database\ y motores\ (los 3 motores
# + el .exe compilado) a C:\laragon\www\cpp. uploads\ NO se toca (ahí viven
# las imágenes recibidas en runtime).
#
# Uso:  powershell -File C:\CPP\desplegar.ps1        (o .\desplegar.ps1)
$origen  = 'C:\CPP'
$destino = 'C:\laragon\www\cpp'

if (-not (Test-Path $destino)) {
    New-Item -ItemType Directory -Path $destino | Out-Null
}

foreach ($carpeta in @('api', 'database')) {
    robocopy "$origen\$carpeta" "$destino\$carpeta" /MIR /NFL /NDL /NJH /NJS | Out-Null
    if ($LASTEXITCODE -ge 8) { Write-Error "robocopy falló en $carpeta (código $LASTEXITCODE)"; exit 1 }
}

# motores: todo EXCEPTO spike\ (las fotos del spike son solo de desarrollo)
robocopy "$origen\motores" "$destino\motores" /E /XD spike /NFL /NDL /NJH /NJS | Out-Null
if ($LASTEXITCODE -ge 8) { Write-Error "robocopy falló en motores (código $LASTEXITCODE)"; exit 1 }

Write-Host 'Desplegado en' $destino '- api - database - motores (sin spike)'

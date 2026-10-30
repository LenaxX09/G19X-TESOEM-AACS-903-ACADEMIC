# desplegar.ps1 - Sprint 4: publica el backend en el Document Root de Laragon.
#
# Copia api\ (con el nuevo procesar.php), database\ y motores\ (los 3 motores
# + el .exe compilado) al Document Root. uploads\ NO se toca (ahí viven
# las imágenes recibidas en runtime).
#
# Correcciones 29/10: rutas PORTABLES - el origen es la carpeta donde vive
# este script ($PSScriptRoot, vale en cualquier equipo/carpeta) y el destino
# se puede cambiar con la variable de entorno CPP_WWW (omisión: Laragon).
#
# Uso:  powershell -File .\desplegar.ps1          (desde la raíz del repo)
#       $env:CPP_WWW = 'D:\www\cpp'; .\desplegar.ps1
$origen  = $PSScriptRoot
$destino = if ($env:CPP_WWW) { $env:CPP_WWW } else { 'C:\laragon\www\cpp' }

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

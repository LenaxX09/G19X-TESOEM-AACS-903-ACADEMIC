# Portable (correcciones 07/11): la ruta por omisión NO apunta al escritorio
# de un equipo concreto — si no se pasa -ruta, se busca ui.xml junto a este
# script y, si no está, en %TEMP% (donde lo deja un:
#   adb exec-out uiautomator dump /sdcard/uidump.xml + adb pull ... )
param([string]$ruta = (Join-Path (Split-Path $PScriptRoot -Parent) "ui.xml"))
if (-not (Test-Path $ruta)) { $ruta = Join-Path $env:TEMP "ui.xml" }
[xml]$doc = Get-Content $ruta -Encoding UTF8
$ns = New-Object System.Xml.XmlNamespaceManager($doc.NameTable)
$ns.AddNamespace("a", "http://schemas.android.com/apk/res/android")
foreach ($n in $doc.SelectNodes("//node", $ns)) {
    $t = $n.GetAttribute("text")
    $c = $n.GetAttribute("content-desc")
    $cls = $n.GetAttribute("class")
    $b = $n.GetAttribute("bounds")
    if (($t -or $c) -and $b) {
        if ($b -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
            $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
            $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
            $etiqueta = if ($t) { $t } else { "[$c]" }
            Write-Output ("{0} => {1},{2} ({3})" -f $etiqueta, $x, $y, ($cls -replace 'android.widget.',''))
        }
    }
}

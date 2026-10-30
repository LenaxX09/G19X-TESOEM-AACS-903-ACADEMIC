# repara_strings.ps1 — S10: repara strings.xml (doble codificacion por Get-Content ANSI)
# 1) invierte el doble-encode (UTF8 leido como CP1252 y regrabado como UTF8)
# 2) restaura lineas con emoji danado: bloque S9/S10 (frag) o version limpia de HEAD
$ErrorActionPreference = 'Stop'
$raiz  = Split-Path $PSScriptRoot -Parent
$p     = "$raiz\android\app\src\main\res\values\strings.xml"
$head  = "$env:TEMP\strings_head.xml"
$frag  = "$raiz\herramientas\s10_strings_frag.xml"

cmd /c "git -C "$raiz" show HEAD:android/app/src/main/res/values/strings.xml > `"$head`""

# 1) reversa del doble encode
$mal    = [IO.File]::ReadAllText($p, [Text.Encoding]::UTF8)
$fijo   = [Text.Encoding]::UTF8.GetString(
            [Text.Encoding]::GetEncoding(1252).GetBytes($mal))

# 2) mapas limpios
$hmap = @{}; $fmap = @{}
foreach ($l in [IO.File]::ReadAllLines($head, [Text.Encoding]::UTF8)) {
  if ($l -match '<string name="([^"]+)"') { $hmap[$Matches[1]] = $l.TrimEnd() }
}
foreach ($l in [IO.File]::ReadAllLines($frag, [Text.Encoding]::UTF8)) {
  if ($l -match '<string name="([^"]+)"') { $fmap[$Matches[1]] = $l.TrimEnd() }
}

$esDanada = { param($s) ($s.IndexOf([char]0x00F0) -ge 0) -or ($s.IndexOf([char]0x00C3) -ge 0) }

$salida  = New-Object System.Collections.Generic.List[string]
$deHead  = 0; $deFrag = 0; $irreparables = @()
foreach ($l in ($fijo -split "`r?`n")) {
  $linea = $l
  if ($linea -match '<string name="([^"]+)"') {
    $n = $Matches[1]
    if ($fmap.ContainsKey($n)) {
      if ((& $esDanada $linea) -or $fmap[$n] -ne $linea.Trim()) {
        # preferir MI version limpia del frag (S9/S10 editados esta sesion)
        $linea = '    ' + $fmap[$n]; $deFrag++
      }
    } elseif ((& $esDanada $linea) -and $hmap.ContainsKey($n)) {
      $linea = '    ' + $hmap[$n]; $deHead++
    } elseif (& $esDanada $linea) {
      $irreparables += $n
    }
  }
  $salida.Add($linea)
}
$utf8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText($p, ($salida -join "`r`n") + "`r`n", $utf8)
"ok: del frag=$deFrag, de HEAD=$deHead, irreparables=$($irreparables -join ',') ; total=$($salida.Count)"
# verificacion: no debe quedar danha visible
$chk = [IO.File]::ReadAllText($p, [Text.Encoding]::UTF8)
if ($chk.IndexOf([char]0x00F0) -ge 0 -or $chk.IndexOf([char]0x00C3) -ge 0) { 'AVISO: quedan rastros' } else { 'sin rastros de mojibake' }

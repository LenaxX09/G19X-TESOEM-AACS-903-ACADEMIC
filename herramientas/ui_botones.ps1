param([string]$ruta = "C:\Users\acaba\Desktop\Prueba_Telefono_0311\ui.xml")
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

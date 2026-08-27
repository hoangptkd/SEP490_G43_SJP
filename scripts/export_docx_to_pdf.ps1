param(
  [Parameter(Mandatory=$true)][string]$DocxPath,
  [Parameter(Mandatory=$true)][string]$PdfPath
)

$ErrorActionPreference = "Stop"
$outDir = Split-Path -Parent $PdfPath
if ($outDir) {
  New-Item -ItemType Directory -Force -Path $outDir | Out-Null
}
if (Test-Path -LiteralPath $PdfPath) {
  Remove-Item -LiteralPath $PdfPath -Force
}

$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
$document = $null
try {
  $document = $word.Documents.Open($DocxPath, $false, $true, $false)
  $document.ExportAsFixedFormat($PdfPath, 17)
}
finally {
  if ($document -ne $null) {
    $document.Close($false)
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($document) | Out-Null
  }
  $word.Quit()
  [System.Runtime.InteropServices.Marshal]::ReleaseComObject($word) | Out-Null
}

Get-Item -LiteralPath $PdfPath | Select-Object FullName, Length

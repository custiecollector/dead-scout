param([switch]$CheckOnly)
Write-Host 'DeadScout public Windows package: no third-party packet-driver bootstrap is bundled.'
Write-Host 'Use Import, operator-configured RTL/helper paths, or external rtl_tcp.'
if ($CheckOnly) { exit 0 }

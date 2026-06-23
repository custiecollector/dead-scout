param([ValidateSet('Status','Network')]$Mode='Status',[int]$Seconds=4,[string]$OutFile='')
Write-Host '{"ok":true,"note":"DeadScout public Windows helper status only. Use Import, RTL/helper paths, or external rtl_tcp."}'

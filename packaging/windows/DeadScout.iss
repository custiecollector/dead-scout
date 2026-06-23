; Native Windows installer definition for DeadScout Desktop.
;
; Build from Windows PowerShell with:
;   powershell -NoProfile -ExecutionPolicy Bypass -File packaging\windows\build-windows-native.ps1 -InnoDir C:\Path\To\InnoSetup6
;
; The build script creates a jpackage app image with a bundled Java runtime,
; then invokes Inno Setup Compiler to produce a per-user installer .exe.

#ifndef MyAppName
#define MyAppName "DeadScout Desktop"
#endif
#ifndef MyAppVersion
#define MyAppVersion "0.2.24"
#endif
#ifndef MyAppVersionNumeric
#define MyAppVersionNumeric "0.2.24.0"
#endif
#ifndef MyAppPublisher
#define MyAppPublisher "DeadScout"
#endif
#ifndef MyAppExeName
#define MyAppExeName "DeadScout Desktop.exe"
#endif
#ifndef SourceDir
#define SourceDir "..\..\build\windows-native\app-image\DeadScout Desktop"
#endif
#ifndef OutputDir
#define OutputDir "..\..\build\windows-native\installer"
#endif

[Setup]
AppId={{B0E69224-4C47-4E5C-8D20-B8F2019D5EAF}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={localappdata}\Programs\DeadScout Desktop
DefaultGroupName=DeadScout Desktop
DisableProgramGroupPage=yes
OutputDir={#OutputDir}
OutputBaseFilename=DeadScout-Desktop-{#MyAppVersion}-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayName={#MyAppName}
UninstallDisplayIcon={app}\{#MyAppExeName}
VersionInfoVersion={#MyAppVersionNumeric}
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription={#MyAppName}
VersionInfoProductName={#MyAppName}
VersionInfoProductVersion={#MyAppVersionNumeric}

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[INI]

[UninstallDelete]
Type: dirifempty; Name: "{app}"

[Icons]
Name: "{autoprograms}\DeadScout Desktop"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"
Name: "{autoprograms}\DeadScout Capture Helper Setup"; Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\setup-capture-helpers-windows.ps1"""; WorkingDir: "{app}"
Name: "{autoprograms}\DeadScout Native Capture Status"; Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\deadscout-windows-capture-helper.ps1"" -Mode Status"; WorkingDir: "{app}"
Name: "{autodesktop}\DeadScout Desktop"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"; Flags: unchecked

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch DeadScout Desktop"; Flags: nowait postinstall skipifsilent
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\setup-capture-helpers-windows.ps1"" -CheckOnly"; Description: "Check DeadScout capture helper status"; WorkingDir: "{app}"; Flags: postinstall skipifsilent waituntilterminated unchecked

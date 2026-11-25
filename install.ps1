<#
.SYNOPSIS
    Installs AG Widgets on Windows: Antigravity hook, collector sidecar, CLI
    status line, Electron dependencies, and launcher shortcuts.

.DESCRIPTION
    Runtime configuration and secrets are written to
    %LOCALAPPDATA%\ag-widgets, never into the repository.

.PARAMETER PythonPath
    Path to python.exe. Defaults to the first "python" on PATH.

.PARAMETER NoShortcut
    Skip desktop and Start Menu shortcut creation.

.EXAMPLE
    .\install.ps1
#>
param(
    [string]$PythonPath = "",
    [switch]$NoShortcut
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path

function Write-Step([string]$Message) {
    Write-Host "==> $Message" -ForegroundColor Cyan
}

if (-not $PythonPath) {
    $pythonCommand = Get-Command python -ErrorAction SilentlyContinue
    if (-not $pythonCommand) {
        throw "Python 3.10+ not found in PATH. Pass -PythonPath explicitly."
    }
    $PythonPath = $pythonCommand.Source
}

if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    throw "npm not found. Install Node.js 22.12+ and reopen the terminal."
}

Write-Step "Python: $PythonPath"
Write-Step "Project: $Root"

# 1. Runtime configuration (outside the repository).
Write-Step "Preparing runtime configuration"
$RuntimeDir = Join-Path $env:LOCALAPPDATA "ag-widgets"
$ConfigPath = Join-Path $RuntimeDir "config.json"
New-Item -ItemType Directory -Force -Path $RuntimeDir | Out-Null
if (-not (Test-Path $ConfigPath)) {
    Copy-Item (Join-Path $Root "config.example.json") $ConfigPath
    Write-Host "    Created $ConfigPath"
}

# 2. Register the Antigravity hook, sidecar, CLI status line, and Python path.
Write-Step "Registering Antigravity integration"
$setupScript = @'
import json
import os
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
python = sys.argv[2]
home = Path.home()


def load(path):
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}


def save(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


# (a) Context hook.
hooks_path = home / ".gemini" / "config" / "hooks.json"
hooks = load(hooks_path)
hook_command = f'"{python}" "{root / "hook" / "context_hook.py"}"'
hooks["ag-widgets-context"] = {
    "PostInvocation": [{"type": "command", "command": hook_command, "timeout": 5}],
    "Stop": [{"type": "command", "command": hook_command, "timeout": 5}],
}
save(hooks_path, hooks)
print(f"[OK] Hook registered in {hooks_path}")

# (b) Collector sidecar, scheduled once per minute.
sidecar_path = home / ".gemini" / "config" / "sidecars" / "ag-widgets-collector" / "sidecar.json"
sidecar = {
    "display_name": "AG Widgets collector",
    "description": "Collects Antigravity quota, ZCode status, and active-chat context",
    "builtin": "schedule",
    "args": ["* * * * *", python, str(root / "collector" / "collector.py")],
}
save(sidecar_path, sidecar)
print(f"[OK] Sidecar manifest written to {sidecar_path}")

# (c) Enable the sidecar.
antigravity_config_path = home / ".gemini" / "config" / "config.json"
antigravity_config = load(antigravity_config_path)
antigravity_config.setdefault("sidecars", {})["ag-widgets-collector"] = {"enabled": True}
save(antigravity_config_path, antigravity_config)
print(f"[OK] Sidecar enabled in {antigravity_config_path}")

# (d) CLI status line.
cli_config_path = home / ".gemini" / "antigravity-cli" / "settings.json"
cli_config = load(cli_config_path)
cli_config["statusLine"] = {
    "type": "command",
    "command": f'"{python}" "{root / "collector" / "agy_statusline.py"}"',
    "stack_with_default": True,
}
save(cli_config_path, cli_config)
print(f"[OK] Status line configured in {cli_config_path}")

# (e) Remember the interpreter for the widget's on-demand refresh.
local_app_data = os.environ.get("LOCALAPPDATA") or str(Path.home() / "AppData" / "Local")
config_path = Path(local_app_data) / "ag-widgets" / "config.json"
config_path.parent.mkdir(parents=True, exist_ok=True)
config = load(config_path)
config["python_path"] = python
save(config_path, config)
print(f"[OK] Python path saved in {config_path}")
'@

$setupScript | & $PythonPath - $Root $PythonPath
if ($LASTEXITCODE -ne 0) {
    throw "Failed to register Antigravity integration (exit code $LASTEXITCODE)"
}

# 3. Electron dependencies.
$DesktopDir = Join-Path $Root "desktop"
if (-not (Test-Path (Join-Path $DesktopDir "node_modules\electron"))) {
    Write-Step "Installing Electron dependencies"
    Push-Location $DesktopDir
    try {
        npm install
        if ($LASTEXITCODE -ne 0) { throw "npm install failed (exit code $LASTEXITCODE)" }
    } finally {
        Pop-Location
    }
}

# 4. Run the collector once to create the ntfy topic and verify the pipeline.
Write-Step "Running the collector for the first time"
$output = & $PythonPath (Join-Path $Root "collector\collector.py") --force 2>&1
Write-Host $output
$jsonLine = @($output | Where-Object { $_ -match '^\s*\{' } | Select-Object -Last 1)
if (-not $jsonLine) {
    throw "Collector did not return JSON. See the output above."
}
$result = $jsonLine | ConvertFrom-Json

# 5. Launcher shortcuts.
if (-not $NoShortcut) {
    Write-Step "Creating shortcuts"
    $iconPath = Join-Path $Root "desktop\assets\icon.ico"
    $launcher = Join-Path $Root "start-widget.vbs"
    $wscript = Join-Path $env:WINDIR "System32\wscript.exe"
    $shortcutPaths = @(
        (Join-Path ([Environment]::GetFolderPath("Desktop")) "AG Widgets.lnk"),
        (Join-Path ([Environment]::GetFolderPath("Programs")) "AG Widgets.lnk")
    )
    $shell = New-Object -ComObject WScript.Shell
    foreach ($shortcutPath in $shortcutPaths) {
        $shortcut = $shell.CreateShortcut($shortcutPath)
        $shortcut.TargetPath = $wscript
        $shortcut.Arguments = '"' + $launcher + '"'
        $shortcut.WorkingDirectory = $Root
        $shortcut.IconLocation = "$iconPath,0"
        $shortcut.Description = "AG Widgets - Antigravity and ZCode"
        $shortcut.Save()
    }
    Write-Host "[OK] Desktop and Start Menu shortcuts created"
}

Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "Installation complete" -ForegroundColor Green
Write-Host "Private ntfy topic: $($result.topic)" -ForegroundColor Yellow
Write-Host "(Keep it private. Enter the same topic in the Android app.)"
Write-Host ""
Write-Host "Next steps:"
Write-Host "  1. Optional: sign in to ZCode for vouchers and balance:"
Write-Host "       python collector\zcode_auth.py"
Write-Host "  2. Launch the desktop widget from the 'AG Widgets' shortcut"
Write-Host "     or run: cd desktop; npm start"
Write-Host "  3. Install the Android APK, enter the ntfy topic, and tap 'Sign in to Z.ai'"
Write-Host "=========================================" -ForegroundColor Cyan

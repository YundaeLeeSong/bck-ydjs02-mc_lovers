@echo off
@REM ===========================================================================
@REM File:         dev.bat
@REM Description:
@REM   This script checks for and installs a list of hard-coded applications
@REM   using the Windows Package Manager (winget). Set up initial configuration
@REM   of any windows machine.
@REM
@REM   The script includes:
@REM   - An administrative privilege check.
@REM   - A reusable function for installing applications by their winget ID.
@REM   - The color variables are now directly applied to the echo command.
@REM
@REM Usage:
@REM   Run this script as Administrator. It will automatically check for
@REM   privileges and exit if not met.
@REM
@REM Requirements:
@REM   - Windows 10 (1709 or newer) or Windows 11.
@REM   - winget (App Installer) must be installed from the Microsoft Store.
@REM
@REM Note:
@REM   For color to work correctly, this script MUST be run in a terminal that
@REM   supports ANSI escape codes (e.g., Windows Terminal, PowerShell).
@REM   The standard Windows Command Prompt (cmd.exe) may not display colors
@REM   correctly.
@REM 
@REM Author: 
@REM   Jaehoon Song
@REM Date: 
@REM   2025-09-14
@REM Version:
@REM   1.5
@REM Update History:
@REM   - (v1.0) 2025-09-14: Initial version created.
@REM   - (v1.1) 2025-10-20: VS Code detection and Automation of handling .bashrc and README.md added.
@REM   - (v1.2) 2026-06-19: Non-admin mode is status-only
@REM   - (v1.3) 2026-06-21: General script refactoring and optimizations applied
@REM   - (v1.4) 2026-06-21: Added Update-VCRedist function and immediate reboot prompt for VCRedist
@REM   - (v1.5) 2026-06-21: Added dynamic registry fix for MySQL Shell VCRedist 1603 installation error
@REM ===========================================================================

@REM --- Hard-coded ANSI/VT color variables ---
set "RED=[31m"
set "GREEN=[32m"
set "YELLOW=[33m"
set "CYAN=[36m"
set "RESET=[0m"

@REM Jump to the main execution block to keep the function definitions separate.
goto :main

@REM ---------------------------------------------------------------------------
@REM Function: Display-System-Info
@REM Purpose:
@REM   Displays information about the current system environment.
@REM Output:
@REM   - OS
@REM   - Version
@REM   - Architecture
@REM ---------------------------------------------------------------------------
:Display-System-Info
echo.%CYAN%=========================================================%RESET%
echo.%CYAN%Starting your script...%RESET%
echo.
echo.Script is running from: %~dp0%~nx0
echo.Filename of Script: %~n0
echo.Directory of Script: %~dp0
echo.Current Directory of Script Instance: %cd%
echo.
echo.System Information:
echo.  OS: %OS%
echo.  Kernel Version: | set /p="%VER_OUTPUT%" & ver | findstr /i "version"
echo.  Architecture: %PROCESSOR_ARCHITECTURE%
echo.  User: %USERNAME%
echo.%CYAN%=========================================================%RESET%
echo.
echo  --- Core User and System Paths ---
echo.
echo HOMEDRIVE:               %HOMEDRIVE%                                    (Drive letter of home directory)
echo ProgramData:             %ProgramData%                        (Common app data, all users)
echo ProgramFiles:            %ProgramFiles%                      (Default 64-bit install path)
echo ProgramFiles(x86):       %ProgramFiles(x86)%                (Default 32-bit install path)
echo USERPROFILE:             %USERPROFILE%                        (Main user profile path)
echo APPDATA:                 %APPDATA%        (User's roaming app data)
echo LOCALAPPDATA:            %LOCALAPPDATA%          (User's local app data)
set "USERPROGRAMS=%LOCALAPPDATA%\Programs"
echo USERPROGRAMS:            %USERPROGRAMS% (User-specific install path)
echo TEMP[TMP]:               %TEMP%     (User's temporary files directory)
@REM echo Path:                    %Path%                    (Semicolon-separated search path)
echo.
goto :EOF

@REM ---------------------------------------------------------------------------
@REM Function: Display-Virtualization-Info
@REM Purpose:
@REM   Checks and displays the status of virtualization features required for WSL.
@REM ---------------------------------------------------------------------------
:Display-Virtualization-Info
echo.%CYAN%=========================================================%RESET%
echo.Checking %YELLOW%Virtualization - "W"indows"S"ubsystemfor"L"inux (WSL) Status%RESET%...
echo.
echo.  --- WSL Status Output ---
echo.
@REM Check Legacy Hyper-V (Microsoft-Hyper-V)
echo. Legacy:
powershell -NoProfile -ExecutionPolicy Bypass -Command "if ((Get-WindowsOptionalFeature -Online -FeatureName Microsoft-Hyper-V).State -eq 'Enabled') { exit 0 } else { exit 1 }" >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo.  Hyper-V:                  %GREEN%Enabled%RESET%
) else (
    echo.  Hyper-V:                  %RED%Disabled%RESET%
)
@REM Check Virtual Machine Platform
echo. New:
powershell -NoProfile -ExecutionPolicy Bypass -Command "if ((Get-WindowsOptionalFeature -Online -FeatureName VirtualMachinePlatform).State -eq 'Enabled') { exit 0 } else { exit 1 }" >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo.  Virtual Machine Platform: %GREEN%Enabled%RESET%
) else (
    echo.  Virtual Machine Platform: %RED%Disabled%RESET%
)
@REM Check Windows Subsystem for Linux (Feature)
powershell -NoProfile -ExecutionPolicy Bypass -Command "if ((Get-WindowsOptionalFeature -Online -FeatureName Microsoft-Windows-Subsystem-Linux).State -eq 'Enabled') { exit 0 } else { exit 1 }" >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo.  WSL:                      %GREEN%Enabled%RESET%
) else (
    echo.  WSL:                      %RED%Disabled%RESET%
)
echo.
echo.
@REM Check WSL Status details
wsl --status 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo.%YELLOW%WSL is not installed or not running.%RESET%
)
@REM Check WSL Version
wsl --list --online >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
  echo.%YELLOW%No online distros found.%RESET%
) else (
  echo.%GREEN%Online distros found.%RESET%
  echo.
  echo.  --- Online distros ---
  echo.
  wsl --list --online
)
@REM Check Installed Distros
echo.  --- Installed distros ---
echo.
wsl --list --verbose >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
  echo.%YELLOW%No installed distros found.%RESET%
) else (
  echo.%GREEN%Installed distros found.%RESET%
  echo.
  wsl --list --verbose
)


@REM instructions for the user to install the online distros
echo.
echo.
echo.%YELLOW%Instructions for the user to install the online distros:%RESET%
echo.
echo.%YELLOW%To install the online distros, please run the following command:%RESET%
echo."wsl --install -d ^<distro-name^>"
echo.%YELLOW%To start the VM on WSL, please run the following command:%RESET%
echo."wsl -d ^<distro-name^>"
echo.%YELLOW%To verify the WSL kernel in the VM, please run the following command:%RESET%
echo."uname -r"
echo.%YELLOW%To delete an installed distro, please run the following command:%RESET%
echo."wsl --unregister ^<distro-name^>"
@REM wsl --unregister Ubuntu


echo.  %YELLOW%For example, to install Ubuntu, run the following command:%RESET%
echo.  "wsl --install -d Ubuntu"
echo.  %YELLOW%To install Debian, run the following command:%RESET%
echo.  "wsl --install -d Debian"
echo.  %YELLOW%To install Kali Linux, run the following command:%RESET%
echo.  "wsl --install -d Kali-Linux"



goto :EOF

@REM ---------------------------------------------------------------------------
@REM Function: Check-Admin
@REM ---------------------------------------------------------------------------
:Check-Admin
set "IS_ADMIN=0"
net session >nul 2>&1 && set "IS_ADMIN=1"
goto :EOF

@REM ---------------------------------------------------------------------------
@REM Function: Find-Directory
@REM ---------------------------------------------------------------------------
:Find-Directory
set "FOUND_DIR_PATH="
set "FOUND_DIR=0"
set "SEARCH_FOLDER=%~1"
if not defined SEARCH_FOLDER goto :EOF

if not defined USERPROGRAMS set "USERPROGRAMS=%LOCALAPPDATA%\Programs"
if not defined JETBRAINS_DIR set "JETBRAINS_DIR=%ProgramFiles%\JetBrains"

if exist "%ProgramFiles%\%SEARCH_FOLDER%\" (set "FOUND_DIR_PATH=%ProgramFiles%\%SEARCH_FOLDER%" & set "FOUND_DIR=1" & goto :EOF)
if exist "%ProgramFiles(x86)%\%SEARCH_FOLDER%\" (set "FOUND_DIR_PATH=%ProgramFiles(x86)%\%SEARCH_FOLDER%" & set "FOUND_DIR=1" & goto :EOF)
if exist "%HOMEDRIVE%\%SEARCH_FOLDER%\" (set "FOUND_DIR_PATH=%HOMEDRIVE%\%SEARCH_FOLDER%" & set "FOUND_DIR=1" & goto :EOF)
if exist "%LOCALAPPDATA%\%SEARCH_FOLDER%\" (set "FOUND_DIR_PATH=%LOCALAPPDATA%\%SEARCH_FOLDER%" & set "FOUND_DIR=1" & goto :EOF)
if exist "%ProgramData%\%SEARCH_FOLDER%\" (set "FOUND_DIR_PATH=%ProgramData%\%SEARCH_FOLDER%" & set "FOUND_DIR=1" & goto :EOF)
if exist "%USERPROGRAMS%\%SEARCH_FOLDER%\" (set "FOUND_DIR_PATH=%USERPROGRAMS%\%SEARCH_FOLDER%" & set "FOUND_DIR=1" & goto :EOF)
if exist "%JETBRAINS_DIR%\%SEARCH_FOLDER%\" (set "FOUND_DIR_PATH=%JETBRAINS_DIR%\%SEARCH_FOLDER%" & set "FOUND_DIR=1" & goto :EOF)

call :Find-Directory-ByPrefix "%ProgramFiles%"
if %FOUND_DIR% EQU 1 goto :EOF
call :Find-Directory-ByPrefix "%ProgramFiles(x86)%"
if %FOUND_DIR% EQU 1 goto :EOF
call :Find-Directory-ByPrefix "%HOMEDRIVE%"
if %FOUND_DIR% EQU 1 goto :EOF
call :Find-Directory-ByPrefix "%LOCALAPPDATA%"
if %FOUND_DIR% EQU 1 goto :EOF
call :Find-Directory-ByPrefix "%ProgramData%"
if %FOUND_DIR% EQU 1 goto :EOF
call :Find-Directory-ByPrefix "%USERPROGRAMS%"
if %FOUND_DIR% EQU 1 goto :EOF
call :Find-Directory-ByPrefix "%JETBRAINS_DIR%"
goto :EOF

:Find-Directory-ByPrefix
set "SEARCH_BASE=%~1"
if not exist "%SEARCH_BASE%\" goto :EOF
for /d %%D in ("%SEARCH_BASE%\%SEARCH_FOLDER%*") do (
  set "FOUND_DIR_PATH=%%~fD"
  set "FOUND_DIR=1"
  goto :break_found_dir
)
:break_found_dir
goto :EOF

@REM ---------------------------------------------------------------------------
@REM Function: Install-App
@REM ---------------------------------------------------------------------------
:Install-App
setlocal EnableDelayedExpansion
set "APP_ID=%~1"
set "APP_NAME=%~2"
set "APP_CMD=%~3"
set "PROGRAM_FOLDER=%~4"
set "SKIP_VERIFY=%~5"
set "REQUIRE_REBOOT=%~6"
set "_TEMP_REBOOT="

echo.
echo.%CYAN%=========================================================%RESET%
echo.Checking for !YELLOW!!APP_NAME!!RESET!...

if defined PROGRAM_FOLDER (
  call :Find-Directory "!PROGRAM_FOLDER!"
  if !FOUND_DIR! EQU 1 (
    echo.!YELLOW!!APP_NAME!!RESET! is !GREEN!already installed - directory check!RESET!.
    echo.Found at: "!FOUND_DIR_PATH!"
    goto :end_install_app
  )
)

if defined APP_CMD (
  where "!APP_CMD!" >nul 2>&1 && (
    echo.!YELLOW!!APP_NAME!!RESET! is !GREEN!already installed - command check!RESET!.
    goto :end_install_app
  )
)

winget list --id "!APP_ID!" --exact >nul 2>&1 && (
  echo.!YELLOW!!APP_NAME!!RESET! is !GREEN!already installed - winget check!RESET!.
  goto :end_install_app
)

echo.!YELLOW!!APP_NAME! not found. Attempting to install...!RESET!
winget install --id "!APP_ID!" --accept-package-agreements --accept-source-agreements --force
if !ERRORLEVEL! EQU 0 (
  echo.!GREEN!!APP_NAME! was installed successfully.!RESET!
  if /I "!REQUIRE_REBOOT!"=="REQUIRE_REBOOT" (
    echo.!RED!Restart is required to complete the installation for !APP_NAME!.!RESET!
    set "_TEMP_REBOOT=1"
  )
) else (
  echo.!RED!Installation of !APP_NAME! failed.!RESET!
)

:end_install_app
if /I "!SKIP_VERIFY!"=="SKIP_VERIFY" goto :end_install_app_done
if not defined APP_CMD goto :end_install_app_done

echo.%GREEN%Verifying installation...%RESET%
call "!APP_CMD!" --version >nul 2>&1 && (echo.!GREEN!!APP_NAME! --version:!RESET! & call "!APP_CMD!" --version)
call "!APP_CMD!" -version >nul 2>&1 && (echo.!GREEN!!APP_NAME! -version:!RESET! & call "!APP_CMD!" -version)
call "!APP_CMD!" -v >nul 2>&1 && (echo.!GREEN!!APP_NAME! -v:!RESET! & call "!APP_CMD!" -v)

:end_install_app_done
if defined _TEMP_REBOOT (
  endlocal
  set "GLOBAL_REBOOT_REQUIRED=1"
) else (
  endlocal
)
goto :EOF

@REM ---------------------------------------------------------------------------
@REM Function: Update-VCRedist
@REM ---------------------------------------------------------------------------
:Update-VCRedist
setlocal EnableDelayedExpansion
set "APP_ID=%~1"
set "APP_NAME=%~2"
set "REQUIRE_REBOOT=%~3"
set "_TEMP_REBOOT="

echo.
echo.%CYAN%=========================================================%RESET%
echo.Checking for updates for !YELLOW!!APP_NAME!!RESET!...

winget upgrade --id "!APP_ID!" --accept-package-agreements --accept-source-agreements > "%TEMP%\winget_upgrade_!APP_ID!.log" 2>&1
findstr /i /C:"No applicable update found" /C:"No available upgrade found" /C:"No newer package versions" "%TEMP%\winget_upgrade_!APP_ID!.log" >nul 2>&1
if !ERRORLEVEL! EQU 0 (
  echo.!GREEN!!APP_NAME! is up to date.!RESET!
  goto :end_update_vcredist
)
findstr /i /C:"No installed package found matching input criteria" "%TEMP%\winget_upgrade_!APP_ID!.log" >nul 2>&1
if !ERRORLEVEL! EQU 0 (
  echo.!YELLOW!!APP_NAME! is not installed. Skipping update.!RESET!
  goto :end_update_vcredist
)
echo.!GREEN!!APP_NAME! has been updated.!RESET!
if /I "!REQUIRE_REBOOT!"=="REQUIRE_REBOOT" (
  echo.!RED!Restart is required to complete the update for !APP_NAME!.!RESET!
  set "_TEMP_REBOOT=1"
)

:end_update_vcredist
if defined _TEMP_REBOOT (
  endlocal
  set "GLOBAL_REBOOT_REQUIRED=1"
) else (
  endlocal
)
goto :EOF

@REM ---------------------------------------------------------------------------
@REM Function: Duplicate-Make
@REM ---------------------------------------------------------------------------
:Duplicate-Make
setlocal EnableDelayedExpansion
make --version >nul 2>&1 && (echo.%GREEN%'make' is already available.%RESET% & endlocal & goto :EOF)

set "SRC="
where dmake.exe >nul 2>&1 && for /f "delims=" %%i in ('where dmake.exe') do set "SRC=%%i"
where gmake.exe >nul 2>&1 && for /f "delims=" %%i in ('where gmake.exe') do set "SRC=%%i"

if defined SRC (
  for %%d in ("!SRC!") do set "SDIR=%%~dpd"
  copy "!SRC!" "!SDIR!make.exe" /Y >nul
  echo.%GREEN%make.exe created at "!SDIR!".%RESET%
) else (
  echo.%CYAN%gmake.exe not found.%RESET%
)
endlocal
goto :EOF

@REM ---------------------------------------------------------------------------
@REM Function: Authenticate-gh
@REM ---------------------------------------------------------------------------
:Authenticate-gh
setlocal EnableDelayedExpansion
call gh auth status --hostname github.com >nul 2>&1
if !ERRORLEVEL! EQU 0 (
  set /p "confirm=Reset gh auth config? (y/N): "
  if /I "!confirm!"=="y" (
    echo.%YELLOW%Resetting configuration...%RESET%
    call gh auth logout --hostname github.com
  ) else (
    echo.%GREEN%Auth is initialized. Skipping login.%RESET%
    goto :end_auth_gh
  )
) else (
  echo.%RED%Auth is NOT initialized.%RESET%
)
echo.%CYAN%Starting login...%RESET%
call gh auth login

:end_auth_gh
endlocal
goto :EOF

@REM ---------------------------------------------------------------------------
@REM Function: Prompt-Path
@REM ---------------------------------------------------------------------------
:Prompt-Path
echo.
echo.%YELLOW%Add this to your PATH to use %~1 from any prompt:%RESET%
echo.%CYAN%%FOUND_DIR_PATH%%~2%RESET%
pause
goto :EOF

@REM ===========================================================================
:main
@REM ===========================================================================
call :Display-System-Info
call :Check-Admin

if "%IS_ADMIN%"=="0" (
  echo.
  echo.%CYAN%=========================================================%RESET%
  echo.%RED%Running without administrator privileges.%RESET%
  echo.%CYAN%=========================================================%RESET%
  echo.
  echo.%YELLOW%Status-only mode. Run as admin to install apps.%RESET%
  pause
  exit /b 0
)

call :Display-Virtualization-Info

@REM ---------------------------------------------------------------------------
@REM Platform Dependents - Web Browsers & Utilities
@REM ---------------------------------------------------------------------------
echo.%CYAN%========================================================= Utilities%RESET%
pause
call :Install-App "Mozilla.Firefox" "Firefox" "firefox" "Mozilla Firefox"
call :Install-App "Google.Chrome" "Google Chrome" "chrome" "Google\Chrome"
call :Install-App "Google.GoogleDrive" "Google Drive for Desktop" "GoogleDriveFS" "Google\Drive File Stream"

call :Find-Directory "Google\Drive File Stream"
if %FOUND_DIR% EQU 1 (
  if not exist "G:\" (
    echo.%CYAN%Opening Google Drive File Stream directory...%RESET%
    start "" "%FOUND_DIR_PATH%"
  )
)

call :Install-App "Microsoft.VisualStudioCode" "Visual Studio Code" "code" "Microsoft VS Code"
call :Install-App "Adobe.Acrobat.Reader.64-bit" "Adobe Acrobat Reader DC" "acrordc.exe" "Adobe"

@REM ---------------------------------------------------------------------------
@REM Platform Environments - POSIX Shells, Version Control, & Containers
@REM ---------------------------------------------------------------------------
echo.%CYAN%========================================================= Shell/Container%RESET%
pause
call :Install-App "Git.Git" "Git" "git" "Git"
call :Install-App "GitHub.cli" "GitHub CLI" "gh" "GitHub CLI"
call :Authenticate-gh
call :Install-App "GLab.GLab" "GitLab CLI" "glab" "glab"
call :Install-App "MSYS2.MSYS2" "MSYS2" "mintty" "msys64"
call :Install-App "Microsoft.WSL" "Windows Subsystem for Linux" "wsl" "WSL"
call :Install-App "Docker.DockerDesktop" "Docker Desktop" "docker" "Docker"

@REM ---------------------------------------------------------------------------
@REM Native Development - C/C++, Node.js, Python, & Scripting Tools
@REM ---------------------------------------------------------------------------
echo.%CYAN%========================================================= Natives Dev%RESET%
pause
call :Install-App "StrawberryPerl.StrawberryPerl" "Strawberry Perl" "perl" "Strawberry"
call :Install-App "MinGW.MinGW" "C compiler (MinGW)" "gcc" "MinGW"
call :Install-App "MinGW.MinGW" "C++ compiler (MinGW)" "g++" "MinGW"
call :Duplicate-Make
call :Install-App "MinGW.MinGW" "Make (MinGW)" "make" "MinGW"
call :Install-App "Kitware.CMake" "CMake" "cmake" "CMake"
call :Install-App "NSIS.NSIS" "NSIS (Installer Creator)" "makensis" "NSIS"

call :Find-Directory "NSIS"
if %FOUND_DIR% EQU 1 call :Prompt-Path "NSIS" "\Bin"

call :Install-App "MiKTeX.MiKTeX" "MiKTeX" "pdflatex" "MiKTeX"
call :Install-App "AutoHotkey.AutoHotkey" "AutoHotkey" "AutoHotkey" "AutoHotkey" "SKIP_VERIFY"
call :Install-App "OpenJS.NodeJS.LTS" "Node.js (LTS)" "node" "nodejs"
call :Install-App "astral-sh.uv" "uv" "uv" "uv"
call :Install-App "prefix-dev.pixi" "pixi" "pixi" "pixi"

@REM ---------------------------------------------------------------------------
@REM Platform Independent - IDEs & Development Tools
@REM ---------------------------------------------------------------------------
echo.%CYAN%========================================================= IDEs%RESET%
pause
call :Install-App "JetBrains.PyCharm" "PyCharm" "" "PyCharm" "SKIP_VERIFY"
call :Install-App "JetBrains.IntelliJIDEA.Community" "IntelliJ IDEA Community" "" "IntelliJ IDEA" "SKIP_VERIFY"
call :Install-App "Amazon.Kiro" "Kiro" "" "Kiro" "SKIP_VERIFY"
call :Install-App "Anysphere.Cursor" "Cursor" "" "Cursor" "SKIP_VERIFY"
call :Install-App "Google.AntigravityIDE" "Antigravity IDE" "" "Antigravity" "SKIP_VERIFY"
call :Install-App "Postman.Postman" "Postman" "" "Postman" "SKIP_VERIFY"
call :Install-App "DBeaver.DBeaver.Community" "DBeaver" "" "DBeaver" "SKIP_VERIFY"
@REM Install and Update Visual C++ Redistributable 2015+ (both x64 and x86)
call :Install-App "Microsoft.VCRedist.2015+.x64" "Visual C++ Redistributable 2015+ (x64)" "" "" "SKIP_VERIFY" "REQUIRE_REBOOT"
call :Update-VCRedist "Microsoft.VCRedist.2015+.x64" "Visual C++ Redistributable 2015+ (x64)" "REQUIRE_REBOOT"

call :Install-App "Microsoft.VCRedist.2015+.x86" "Visual C++ Redistributable 2015+ (x86)" "" "" "SKIP_VERIFY" "REQUIRE_REBOOT"
call :Update-VCRedist "Microsoft.VCRedist.2015+.x86" "Visual C++ Redistributable 2015+ (x86)" "REQUIRE_REBOOT"

if "%GLOBAL_REBOOT_REQUIRED%"=="1" (
  echo.
  echo.%RED%A reboot is required to complete the installation/update of Visual C++ Redistributable.%RESET%
  echo.%YELLOW%Please reboot your system and run this script again to continue with the remaining installations.%RESET%
  goto :Prompt-Reboot
)

@REM Fix for MySQL Shell MSI bug checking the wrong registry key for x64 VCRedist
powershell -NoProfile -ExecutionPolicy Bypass -Command "$v = (Get-ItemProperty 'HKLM:\SOFTWARE\WOW6432Node\Microsoft\DevDiv\vc\Servicing\14.0\RuntimeMinimum' -Name 'Version' -EA Ignore).Version; if ($v) { New-Item 'HKLM:\SOFTWARE\Microsoft\DevDiv\vc\Servicing\14.0\RuntimeMinimum' -Force -EA Ignore | Out-Null; Set-ItemProperty 'HKLM:\SOFTWARE\Microsoft\DevDiv\vc\Servicing\14.0\RuntimeMinimum' -Name 'Version' -Value $v -EA Ignore }"

call :Install-App "Oracle.MySQLShell" "MySQL Shell" "mysqlsh" "MySQL"

echo.
echo.%GREEN%All specified applications have been processed.%RESET%
echo.%YELLOW%It is recommended to reboot the system.%RESET%

:Prompt-Reboot
set /p REBOOT="Reboot now? (y/N): "
if /I "%REBOOT%"=="y" shutdown /r /t 0

pause
exit /b 0



@REM -----------------------------
@REM winget search <ID>
@REM -----------------------------
@echo off
@REM ===========================================================================
@REM File:         .bat
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
@REM   1.1
@REM Update History:
@REM   - (v1.0) 2025-09-14: Initial version created.
@REM   - (v1.1) 2025-10-20: VS Code detection and Automation of handling .bashrc and README.md added.
@REM ===========================================================================

@REM --- Hard-coded ANSI/VT color variables ---
@REM Define colors for console output.
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
set USERPROGRAMS=%LOCALAPPDATA%\Programs
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
@REM Purpose:
@REM   Reliably checks if the script is running with administrator privileges
@REM   using the net session command, which requires elevation.
@REM
@REM Output:
@REM   Sets the IS_ADMIN environment variable to 1 if running as admin,
@REM   otherwise 0.
@REM ---------------------------------------------------------------------------
:Check-Admin
set "IS_ADMIN=0"
net session >nul 2>&1
if %ERRORLEVEL% EQU 0 (
  set "IS_ADMIN=1"
)
goto :EOF





@REM ---------------------------------------------------------------------------
@REM Function: Check-Bashrc
@REM Purpose:
@REM   Checks for the existence of a .bashrc file in the user's home directory.
@REM   If it doesn't exist, it creates a new one with a simple comment.
@REM ---------------------------------------------------------------------------
:Check-Bashrc
echo.%CYAN%=========================================================%RESET%
echo.Checking for .bashrc...
set "BASHRC_PATH=%USERPROFILE%\.bashrc"
if exist "%BASHRC_PATH%" (
  echo.%GREEN%.bashrc exists%RESET%.
  echo.Found at: "%BASHRC_PATH%"
  echo.
) else (
  echo.%YELLOW%.bashrc not found. Creating a new one...%RESET%
  echo.# ~/.bashrc > "%BASHRC_PATH%"
  echo.%GREEN%.bashrc created successfully%RESET%.
  echo.Created at: "%BASHRC_PATH%"
)
goto :EOF


@REM ---------------------------------------------------------------------------
@REM Function: Check-Readme
@REM Purpose:
@REM   Checks for the existence of a README.md file in the current directory.
@REM   If it doesn't exist, it creates a new one with basic content.
@REM ---------------------------------------------------------------------------
:Check-Readme
echo.%CYAN%=========================================================%RESET%
echo.Checking for README.md...
set "README_PATH=%~dp0README.md"
if exist "%README_PATH%" (
  echo.%GREEN%README.md exists%RESET%.
  echo.Found at: "%README_PATH%"
  echo.
) else (
  echo.%YELLOW%README.md not found. Creating a new one...%RESET%
  (
    echo.# Project README
    echo.
    echo.This is a placeholder README.md file.
    echo.Please update this file with your project information.
    echo.
    echo.## Description
    echo.
    echo.## Installation
    echo.
    echo.## Usage
    echo.
    echo.## Contributing
  ) > "%README_PATH%"
  echo.%GREEN%README.md created successfully%RESET%.
  echo.Created at: "%README_PATH%"
)
goto :EOF


@REM ---------------------------------------------------------------------------
@REM Function: Check-VSCode
@REM Purpose:
@REM   Checks if VS Code is installed and available. If found, opens the current
@REM   directory, .bashrc, and README.md with 1-second intervals. If not found,
@REM   prompts the user to run the script in admin mode.
@REM ---------------------------------------------------------------------------
:Check-VSCode
echo.%CYAN%=========================================================%RESET%
echo.Checking for Visual Studio Code...

setlocal EnableDelayedExpansion
set APP_NAME="Visual Studio Code"
set PROGRAM_FOLDER="Microsoft VS Code"

@REM Check if VS Code command is available
if defined PROGRAM_FOLDER (
  if exist "!ProgramFiles!\!PROGRAM_FOLDER!" (
    echo.!YELLOW!!APP_NAME!!RESET! is !GREEN!already installed - Program Files check!RESET!.
    echo.Found at: "!ProgramFiles!\!PROGRAM_FOLDER!"
    echo.
    goto :vscode_found
  )
  if exist "!ProgramFiles(x86)!\!PROGRAM_FOLDER!" (
    echo.!YELLOW!!APP_NAME!!RESET! is !GREEN!already installed - Program Files x86 check!RESET!.
    echo.Found at: "!ProgramFiles(x86)!\!PROGRAM_FOLDER!"
    echo.
    goto :vscode_found
  )
  if exist "!HOMEDRIVE!\!PROGRAM_FOLDER!" (
    echo.!YELLOW!!APP_NAME!!RESET! is !GREEN!already installed - HOMEDRIVE check!RESET!.
    echo.Found at: "!HOMEDRIVE!\!PROGRAM_FOLDER!"
    echo.
    goto :vscode_found
  )
  if exist "!USERPROGRAMS!\!PROGRAM_FOLDER!" (
    echo.!YELLOW!!APP_NAME!!RESET! is !GREEN!already installed - USERPROGRAMS check!RESET!.
    echo.Found at: "!USERPROGRAMS!\!PROGRAM_FOLDER!"
    echo.
    goto :vscode_found
  )
)

@REM VS Code not found
echo.%RED%Visual Studio Code is not detected%RESET%.
echo.%YELLOW%VS Code command 'code' is not available in the current PATH.%RESET%
echo.
echo.%CYAN%To install VS Code and run this script with full functionality:%RESET%
echo.1. Right-click this script and select '%CYAN%Run as administrator%RESET%'
echo.2. The script will automatically install VS Code and other applications
echo.3. After installation, you can run this script in normal mode
echo.
echo.%YELLOW%Alternatively, you can manually install VS Code from:%RESET%
echo.https://code.visualstudio.com/
echo.
goto :end_check_vscode

:vscode_found
@REM Open current directory in VS Code
echo.%CYAN%=========================================================%RESET%
echo.%YELLOW%Opening files with VS Code...%RESET%
echo.Opening current directory: %~dp0
start "" code %~dp0

:end_check_vscode
endlocal
goto :EOF



















@REM ---------------------------------------------------------------------------
@REM Function: Install-App
@REM Usage: call :Install-App "<winget_ID>" "<Application_Name>" "<Executable_Command>" "<Program_Files_Folder_Name>"
@REM
@REM Parameters:
@REM   %1: The winget ID (e.g., "Git.Git").
@REM   %2: A descriptive name for the application (e.g., "Git").
@REM   %3: The executable command to check (e.g., "git").
@REM   %4: The folder name to check for in Program Files (e.g., "Git").
@REM
@REM Purpose:
@REM   This function checks if an application is installed via winget and,
@REM   if not, attempts to install it. It provides clear, colored feedback
@REM   to the user throughout the process.
@REM ---------------------------------------------------------------------------
:Install-App
setlocal EnableDelayedExpansion
set APP_ID=%~1
set APP_NAME=%~2
set APP_CMD=%~3
set PROGRAM_FOLDER=%~4

echo.
echo.
echo.%CYAN%=========================================================%RESET%
echo.Checking for !YELLOW!!APP_NAME!!RESET!...

@REM Check if the PROGRAM_FOLDER variable is defined.
if defined PROGRAM_FOLDER (
  if exist "!ProgramFiles!\!PROGRAM_FOLDER!" (
    echo.!YELLOW!!APP_NAME!!RESET! is !GREEN!already installed - Program Files check!RESET!.
    echo.!YELLOW!Found at%RESET%: "!ProgramFiles!\!PROGRAM_FOLDER!"
    echo.
    goto :end_install_app
  )
  if exist "!ProgramFiles(x86)!\!PROGRAM_FOLDER!" (
    echo.!YELLOW!!APP_NAME!!RESET! is !GREEN!already installed - Program Files x86 check!RESET!.
    echo.!YELLOW!Found at%RESET%: "!ProgramFiles(x86)!\!PROGRAM_FOLDER!"
    echo.
    goto :end_install_app
  )
  if exist "!HOMEDRIVE!\!PROGRAM_FOLDER!" (
    echo.!YELLOW!!APP_NAME!!RESET! is !GREEN!already installed - HOMEDRIVE check!RESET!.
    echo.!YELLOW!Found at%RESET%: "!HOMEDRIVE!\!PROGRAM_FOLDER!"
    echo.
    goto :end_install_app
  )
  if exist "!USERPROGRAMS!\!PROGRAM_FOLDER!" (
    echo.!YELLOW!!APP_NAME!!RESET! is !GREEN!already installed - USERPROGRAMS check!RESET!.
    echo.!YELLOW!Found at%RESET%: "!USERPROGRAMS!\!PROGRAM_FOLDER!"
    echo.
    goto :end_install_app
  )
)

@REM Check if the APP_CMD variable is defined.
if defined APP_CMD (
  "!APP_CMD!" --version >nul 2>&1
  if !ERRORLEVEL! EQU 0 (
    echo.!YELLOW!!APP_NAME!!RESET! is !GREEN!already installed - command check!RESET!.
    goto :end_install_app
  )
)

@REM Check with winget if the app is installed.
call winget list --id "!APP_ID!" --exact >nul 2>&1
if !ERRORLEVEL! EQU 0 (
  echo.!YELLOW!!APP_NAME!!RESET! is !GREEN!already installed - winget check!RESET!.
  goto :end_install_app
)

echo.!YELLOW!!APP_NAME! not found via any check.!RESET!
echo.!CYAN!=========================================================!RESET!
echo.!RED!!APP_NAME! not found.!RESET!
echo.!YELLOW!Attempting to install !APP_NAME!...!RESET!

@REM Install with non-interactive flags to avoid user prompts.
winget install --id "!APP_ID!" --accept-package-agreements --accept-source-agreements --force
if !ERRORLEVEL! EQU 0 (
  echo.!GREEN!!APP_NAME! was installed successfully.!RESET!
) else (
  echo.!RED!Installation of !APP_NAME! failed - Error: !ERRORLEVEL!!RESET!
  echo.!RED!Please ensure the winget ID is correct and you have an active internet connection.!RESET!
)

:end_install_app
"!APP_CMD!" -version >nul 2>&1
if !ERRORLEVEL! EQU 0 (
  echo.!GREEN!Verifying !APP_NAME! installation...!RESET!
  echo.!GREEN!!APP_NAME! with !RED!-version!RESET! flag:!RESET!
  "!APP_CMD!" -version
)
"!APP_CMD!" --version >nul 2>&1
if !ERRORLEVEL! EQU 0 (
  echo.!GREEN!Verifying !APP_NAME! installation...!RESET!
  echo.!GREEN!!APP_NAME! with !RED!--version!RESET! flag:!RESET!
  "!APP_CMD!" --version
)
"!APP_CMD!" -v >nul 2>&1
if !ERRORLEVEL! EQU 0 (
  echo.!GREEN!Verifying !APP_NAME! installation...!RESET!
  echo.!GREEN!!APP_NAME! with !RED!-v!RESET! flag:!RESET!
  "!APP_CMD!" -v
)

endlocal
goto :EOF






























@REM ---------------------------------------------------------------------------
@REM Function: Duplicate-Make
@REM Usage: 
@REM   call :Duplicate-Make
@REM
@REM Parameters: 
@REM   None
@REM 
@REM Purpose: 
@REM   Check if there is 'cmake' already, if so, end the function.
@REM   Detect 'gmake' or 'dmake' in the order.
@REM   If found any, copy the detected (in order) program as 'make' for compatibility with Makefile.
@REM   To find 'gmake.exe' or 'dmake' in the order, it uses 'where' command to locate it.
@REM ---------------------------------------------------------------------------

:Duplicate-Make
setlocal EnableDelayedExpansion
echo.
@REM Check if 'make' command is already available
make --version >nul 2>&1
if !ERRORLEVEL! EQU 0 (
  echo.%GREEN%'make' command is already available. No action needed.%RESET%
  endlocal
  goto :EOF
)
@REM Find the location of gmake.exe by cmd defualt PATH search command, 'where'.
for /f "delims=" %%i in ('where dmake.exe 2^>nul') do (
  set "MAKE_SRC_PATH=%%i"
)
for /f "delims=" %%i in ('where gmake.exe 2^>nul') do (
  set "MAKE_SRC_PATH=%%i"
)
@REM If gmake.exe is found, show the path for debugging.
if defined MAKE_SRC_PATH (
  for %%d in ("!MAKE_SRC_PATH!") do set "MAKE_SRC_DIR=%%~dpd"
  echo.%YELLOW%Found gmake.exe is located at: "!MAKE_SRC_DIR!"%RESET%
  echo.%YELLOW%Found gmake.exe at: "!MAKE_SRC_PATH!"%RESET%
  echo.%YELLOW%Copying gmake.exe to make.exe...%RESET%
  @REM The copy must be located at the same directory as the found gmake.exe.
  copy "!MAKE_SRC_PATH!" "!MAKE_SRC_DIR!make.exe" /Y >nul
  echo.%GREEN%make.exe created successfully at "!MAKE_SRC_DIR!make.exe".%RESET%
) else (
  echo.%CYAN%gmake.exe not found in the system PATH. No renaming needed.%RESET%
)
endlocal
goto :EOF




@REM ---------------------------------------------------------------------------
@REM Function: Authenticate-gh
@REM Purpose:
@REM   Checks GitHub CLI authentication status and handles login/reset flow.
@REM   Prompts user to reset authentication if already initialized, otherwise
@REM   initiates login process.
@REM ---------------------------------------------------------------------------
:Authenticate-gh
setlocal EnableDelayedExpansion

call gh auth status --hostname github.com >NUL 2>&1
if !ERRORLEVEL! NEQ 0 (
  @REM Auth is NOT initialized...
  set "confirm=n"
) else (
  @REM Auth is initialized...
  set /p "confirm=Reset gh auth config? (y/n): "
)
if /I "!confirm!"=="y" (
    echo Resetting configuration...
    call gh auth logout --hostname github.com
    if !ERRORLEVEL! NEQ 0 (
      echo.!RED!Failed to logout. Continuing...!RESET!
    )
)
pause
call gh auth status --hostname github.com >NUL 2>&1
if !ERRORLEVEL! NEQ 0 (
  echo Auth is !RED!NOT initialized!RESET!...
  echo Starting login...
  call gh auth login
) else (
  echo Auth is !GREEN!initialized!RESET!...
  echo Skipping login.
)
pause

endlocal
goto :EOF











@REM ===========================================================================
:main
@REM Main execution starts here
@REM ===========================================================================
call :Display-System-Info
call :Check-Admin


if "%IS_ADMIN%"=="0" (
  echo.
  echo.
  echo.
  echo.
  echo.%CYAN%=========================================================%RESET%
  echo.%CYAN%The script is running with %RED%normal user, non-administrator%RESET% privileges.%RESET%
  echo.%CYAN%=========================================================%RESET%
  echo.
  call :Check-Bashrc
  call :Check-Readme
  call :Check-VSCode

  @REM Open the .bashrc config file asynchronously
  timeout /t 2 /nobreak >nul
  start "" "%USERPROFILE%\.bashrc"
  echo.%GREEN%%USERPROFILE%\.bashrc opened successfully in VS Code%RESET%.
  start "" "%~dp0README.md"
  echo.%GREEN%%~dp0README.md opened successfully in VS Code%RESET%.

  echo.
  echo.%GREEN%Non-admin tasks completed.%RESET%
  pause
  exit /b 0
) else (
  echo.
  echo.
  echo.
  echo.
  echo.%CYAN%=========================================================%RESET%
  echo.%CYAN%The script is running with %GREEN%administrator%RESET% privileges.%RESET%
  echo.%CYAN%=========================================================%RESET%
  echo.
  call :Display-Virtualization-Info
)

echo.%CYAN%========================================================= Utilities, Install...%RESET%
pause
@REM ---------------------------------------------------------------------------
@REM Platform Dependents - Web Browsers & IDEs
@REM ---------------------------------------------------------------------------
call :Install-App "Mozilla.Firefox" "Firefox" "firefox" "Mozilla Firefox"
call :Install-App "Google.Chrome" "Google Chrome" "chrome" "Google"
call :Install-App "Microsoft.VisualStudioCode" "Visual Studio Code" "code" "Microsoft VS Code"
call :Install-App "Adobe.Acrobat.Reader.64-bit" "Adobe Acrobat Reader DC" "acrordc.exe" "Adobe"

echo.%CYAN%========================================================= Shell/Container Natives, Install...%RESET%
pause
@REM ---------------------------------------------------------------------------
@REM Platform Dependents - Lightweight POSIX "shell environment" for Windows, with Version Control Systems
@REM  1. A bash-based shell on Windows
@REM  2. None (No virtualized Linux environment)
@REM ---------------------------------------------------------------------------
call :Install-App "Git.Git" "Git" "git" "Git"
call :Install-App "GitHub.cli" "GitHub CLI" "gh" "GitHub CLI"
call :Authenticate-gh
call :Install-App "GLab.GLab" "GitLab CLI" "glab" "glab"
call :Check-Bashrc
@REM ---------------------------------------------------------------------------
@REM Platform Dependents - POSIX/Unix compatibility "layer (A full environment)" for Windows ("M"inimal "SYS"tem "2".)
@REM  1. A bash-based shell on Windows
@REM  2. A "Pacman" package manager (from Arch Linux)
@REM  3. GNU toolchains (GCC, make, autotools)
@REM  4. Build Windows-native on virtual Linux environment
@REM ---------------------------------------------------------------------------
call :Install-App "MSYS2.MSYS2" "MSYS2" "mintty" "msys64"
@REM ---------------------------------------------------------------------------
@REM Platform Independent - Container Platform (Docker Desktop)
@REM  1. Container runtime and orchestration
@REM  2. Docker Desktop includes Docker Engine, Docker CLI, Docker Compose
@REM  3. GUI for managing images and containers
@REM  4. Build Linux-native (WSL2 processor) on virtual Linux environment
@REM ---------------------------------------------------------------------------
call :Install-App "Microsoft.WSL" "Windows Subsystem for Linux" "wsl" "WSL"
call :Install-App "Docker.DockerDesktop" "Docker Desktop" "docker" "Docker"

echo.%CYAN%========================================================= Windows Natives Dev, Install...%RESET%
pause
@REM ---------------------------------------------------------------------------
@REM Platform Dependents - native C/C++ Development Tools with Perl
@REM ---------------------------------------------------------------------------
call :Install-App "StrawberryPerl.StrawberryPerl" "Strawberry Perl" "perl" "Strawberry"
call :Install-App "MinGW.MinGW" "C compiler (MinGW)" "gcc" "MinGW"
call :Install-App "MinGW.MinGW" "C++ compiler (MinGW)" "g++" "MinGW"
call :Duplicate-Make
call :Install-App "MinGW.MinGW" "Make (MinGW)" "make" "MinGW"
call :Install-App "Kitware.CMake" "CMake" "cmake" "CMake"
call :Install-App "NSIS.NSIS" "NSIS (Installer Creator)" "makensis" "NSIS"
call :Install-App "MiKTeX.MiKTeX" "MiKTeX" "pdflatex" "MiKTeX"

echo.
echo.%GREEN%All specified applications have been processed.%RESET%

@REM recommend to reboot the system
echo.%YELLOW%It is recommended to %RED%reboot%RESET% the system to apply the changes.%RESET%
echo.%YELLOW%Do you want to %RED%reboot%RESET% the system now? (y/n)%RESET%
set /p REBOOT=
if "%REBOOT%"=="y" (
  shutdown /r /t 0
)

pause
exit /b 0

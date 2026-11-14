@echo off
chcp 65001 >nul
cd /d "%~dp0"
where py >nul 2>nul
if %errorlevel%==0 (py START_JARVIS.py) else (python START_JARVIS.py)
pause

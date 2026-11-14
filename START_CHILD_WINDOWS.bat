@echo off
chcp 65001 >nul
cd /d "%~dp0"
set /p JARVIS_PARENT_URL=親機URL（例 http://192.168.1.25:8765）: 
set /p JARVIS_API_TOKEN=親機トークン: 
if not exist .venv python START_JARVIS.py
call .venv\Scripts\python.exe -m pip install -r requirements.txt
.venv\Scripts\python.exe client_proxy.py
pause

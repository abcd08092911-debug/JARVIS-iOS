@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not defined JARVIS_RELAY_TOKEN for /f "delims=" %%A in ('python -c "import secrets; print(secrets.token_urlsafe(32))"') do set "JARVIS_RELAY_TOKEN=%%A"
if not defined JARVIS_RELAY_ROOM for /f "delims=" %%A in ('python -c "import secrets; print(\"JARVIS-\"+secrets.token_hex(4).upper())"') do set "JARVIS_RELAY_ROOM=%%A"
start "JARVIS Relay" cmd /k "uvicorn relay_server:app --host 0.0.0.0 --port 9000"
start "JARVIS Relay Client" cmd /k "python relay_client.py"
start "JARVIS Core" cmd /k "START_JARVIS.bat"
echo JARVIS親機兼中継機を起動しました。
echo 子機画面: http://このPCのIPアドレス:9000/client
pause

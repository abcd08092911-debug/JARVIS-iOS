import os, subprocess, sys, time, webbrowser
from pathlib import Path
ROOT=Path(__file__).resolve().parent
VENV=ROOT/'.venv'
PY=VENV/'Scripts'/'python.exe' if os.name=='nt' else VENV/'bin'/'python'

def run(cmd):
    print('>', ' '.join(map(str,cmd))); subprocess.check_call(cmd,cwd=ROOT)

def main():
    if not PY.exists():
        print('初回セットアップを開始します。数分かかる場合があります。')
        run([sys.executable,'-m','venv',str(VENV)])
        run([str(PY),'-m','pip','install','--upgrade','pip'])
        run([str(PY),'-m','pip','install','-r','requirements.txt'])
        print('セットアップが完了しました。')
    env=os.environ.copy(); env.setdefault('JARVIS_BIND','127.0.0.1'); env.setdefault('JARVIS_PORT','8765')
    print('JARVISを起動します。停止はこの画面で Ctrl+C です。')
    time.sleep(1)
    webbrowser.open('http://127.0.0.1:8765/')
    subprocess.call([str(PY),'run_local.py'],cwd=ROOT,env=env)
if __name__=='__main__': main()

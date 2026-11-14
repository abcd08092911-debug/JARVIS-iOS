"""SC-56B側で実行するJARVIS独自中継クライアント。"""
import json, os, time, urllib.parse, urllib.request

RELAY=os.environ['JARVIS_RELAY_URL'].rstrip('/')
ROOM=os.environ['JARVIS_RELAY_ROOM']
TOKEN=os.environ['JARVIS_RELAY_TOKEN']
LOCAL=os.getenv('JARVIS_LOCAL_URL','http://127.0.0.1:8765')

def request(method,path,payload=None):
    data=None
    headers={'Content-Type':'application/json'}
    if payload is not None: data=json.dumps(payload,ensure_ascii=False).encode()
    req=urllib.request.Request(RELAY+path,data=data,headers=headers,method=method)
    with urllib.request.urlopen(req,timeout=30) as r: return json.loads(r.read())

def main():
    request('POST','/register',{'room':ROOM,'token':TOKEN})
    print('JARVIS relay connected:',RELAY,ROOM)
    while True:
        try:
            result=request('GET','/host/poll?'+urllib.parse.urlencode({'room':ROOM,'token':TOKEN}))
            for item in result.get('messages',[]):
                local=urllib.request.Request(LOCAL+'/chat',data=json.dumps({'text':item['text']}).encode(),headers={'Content-Type':'application/json'},method='POST')
                with urllib.request.urlopen(local,timeout=180) as r: body=json.loads(r.read())
                reply=body.get('reply') or body.get('message') or 'JARVISは応答できませんでした。'
                request('POST','/host/reply',{'room':ROOM,'token':TOKEN,'message_id':item['id'],'reply':reply})
        except Exception as e:
            print('relay retry:',e)
            time.sleep(5)
        time.sleep(1)
if __name__=='__main__': main()

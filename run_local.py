import uvicorn
from src.jarvis.config import BIND_HOST, PORT
if __name__=='__main__': uvicorn.run('src.jarvis.api:app',host=BIND_HOST,port=PORT,reload=False)

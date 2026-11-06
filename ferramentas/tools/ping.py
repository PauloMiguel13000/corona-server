import socket, struct, json, sys
def varint(n):
    out=b''
    while True:
        b=n&0x7f; n>>=7
        if n: out+=bytes([b|0x80])
        else: out+=bytes([b]); return out
def readvar(s):
    n=0; sh=0
    while True:
        b=s.recv(1)[0]; n|=(b&0x7f)<<sh; sh+=7
        if not b&0x80: return n
def pkt(pid, data): d=varint(pid)+data; return varint(len(d))+d
host='127.0.0.1'; port=int(sys.argv[1]) if len(sys.argv)>1 else 25599
s=socket.create_connection((host,port),5)
hs=varint(777)+varint(len(host))+host.encode()+struct.pack('>H',port)+varint(1)
s.sendall(pkt(0,hs)); s.sendall(pkt(0,b''))
ln=readvar(s); data=b''
while len(data)<ln: data+=s.recv(ln-len(data))
i=1
# skip packet id
l=0;sh=0
while True:
    b=data[i]; i+=1; l|=(b&0x7f)<<sh; sh+=7
    if not b&0x80: break
j=json.loads(data[i:i+l])
print('STATUS OK', j.get('version'), j.get('players',{}).get('online'), str(j.get('description'))[:80])
# login attempt
s2=socket.create_connection((host,port),5)
hs=varint(777)+varint(len(host))+host.encode()+struct.pack('>H',port)+varint(2)
s2.sendall(pkt(0,hs))
name=b'PingBot'
import uuid
s2.sendall(pkt(0,varint(len(name))+name+uuid.uuid4().bytes))
ln=readvar(s2); d=s2.recv(ln)
print('LOGIN resposta pacote id', d[0], d[1:120])

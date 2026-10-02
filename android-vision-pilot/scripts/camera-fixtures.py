"""Local CI cameras: ONVIF authentication/clock, RTSP 401 and a stalled socket.

Only fixed test credentials are used; this fixture never contacts Firebase or a real camera.
"""
import base64
import hashlib
import hmac
import json
import re
import socketserver
import threading
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from xml.etree import ElementTree as ET

SOAP = "http://www.w3.org/2003/05/soap-envelope"
DEVICE = "http://www.onvif.org/ver10/device/wsdl"
MEDIA = "http://www.onvif.org/ver10/media/wsdl"
SCHEMA = "http://www.onvif.org/ver10/schema"
USER = "onvif-test"
PASSWORD = "onvif-test-password"
REALM = "SMART24-ONVIF"
NONCE = "0123456789abcdef0123456789abcdef"
COUNTS = {}
LOCK = threading.Lock()


def count(name):
    with LOCK:
        COUNTS[name] = COUNTS.get(name, 0) + 1


def md5(value):
    return hashlib.md5(value.encode()).hexdigest()


def digest_valid(header, path):
    if not header.startswith("Digest "):
        return False
    pairs = dict((m[0].lower(), m[1] or m[2]) for m in
                 re.findall(r'(\w+)\s*=\s*(?:"([^"]*)"|([^,\s]+))', header[7:]))
    if pairs.get("username") != USER or pairs.get("nonce") != NONCE or pairs.get("uri") != path:
        return False
    if pairs.get("qop") != "auth" or pairs.get("realm") != REALM:
        return False
    response = md5(f'{md5(f"{USER}:{REALM}:{PASSWORD}")}:{NONCE}:{pairs.get("nc")}:{pairs.get("cnonce")}:auth:{md5(f"POST:{path}")}')
    return hmac.compare_digest(pairs.get("response", ""), response)


def token_valid(document):
    values = {node.tag.rsplit("}", 1)[-1]: (node.text or "") for node in document.iter()}
    try:
        nonce = base64.b64decode(values["Nonce"], validate=True)
        created = values["Created"]
        actual_time = datetime.fromisoformat(created.replace("Z", "+00:00"))
        expected_time = datetime.now(timezone.utc) - timedelta(hours=1)
        if abs((actual_time - expected_time).total_seconds()) > 30:
            return False
        expected = base64.b64encode(hashlib.sha1(nonce + created.encode() + PASSWORD.encode()).digest()).decode()
        return values.get("Username") == USER and hmac.compare_digest(values.get("Password", ""), expected)
    except (KeyError, ValueError):
        return False


def envelope(body):
    return (f'<s:Envelope xmlns:s="{SOAP}" xmlns:tds="{DEVICE}" xmlns:trt="{MEDIA}" xmlns:tt="{SCHEMA}" '
            f'xmlns:ter="http://www.onvif.org/ver10/error"><s:Body>{body}</s:Body></s:Envelope>').encode()


class OnvifHandler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def answer(self, status, data, content_type="application/soap+xml"):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/stats":
            with LOCK:
                data = json.dumps(COUNTS).encode()
            self.answer(200, data, "application/json")
        else:
            self.answer(404, b"")

    def do_POST(self):
        document = ET.fromstring(self.rfile.read(min(int(self.headers.get("Content-Length", 0)), 65536)))
        body = document.find(f'{{{SOAP}}}Body')
        operation = next(iter(body)).tag.rsplit("}", 1)[-1]
        authority = self.headers.get("Host", "10.0.2.2:8080")
        camera_host = authority.split(":", 1)[0]
        if operation == "GetSystemDateAndTime":
            now = datetime.now(timezone.utc) - timedelta(hours=1)
            result = (f'<tds:GetSystemDateAndTimeResponse><tds:SystemDateAndTime><tt:UTCDateTime>'
                      f'<tt:Time><tt:Hour>{now.hour}</tt:Hour><tt:Minute>{now.minute}</tt:Minute><tt:Second>{now.second}</tt:Second></tt:Time>'
                      f'<tt:Date><tt:Year>{now.year}</tt:Year><tt:Month>{now.month}</tt:Month><tt:Day>{now.day}</tt:Day></tt:Date>'
                      f'</tt:UTCDateTime></tds:SystemDateAndTime></tds:GetSystemDateAndTimeResponse>')
        else:
            if not digest_valid(self.headers.get("Authorization", ""), self.path):
                count("http_digest_challenge")
                self.send_response(401)
                self.send_header("WWW-Authenticate", f'Digest realm="{REALM}", nonce="{NONCE}", qop="auth", algorithm=MD5')
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
            if not token_valid(document):
                count("wsse_rejected")
                self.answer(500, envelope('<s:Fault><s:Code><s:Value>s:Sender</s:Value><s:Subcode><s:Value>ter:NotAuthorized</s:Value></s:Subcode></s:Code><s:Reason><s:Text xml:lang="en">NotAuthorized</s:Text></s:Reason></s:Fault>'))
                return
            count("http_digest_and_wsse_accepted")
            if operation == "GetServices":
                result = (f'<tds:GetServicesResponse><tds:Service><tds:Namespace>{MEDIA}</tds:Namespace>'
                          f'<tds:XAddr>http://{authority}/onvif/media_service</tds:XAddr></tds:Service></tds:GetServicesResponse>')
            elif operation == "GetProfiles":
                result = ('<trt:GetProfilesResponse>'
                          '<trt:Profiles token="primary"><tt:VideoEncoderConfiguration><tt:Resolution><tt:Width>1920</tt:Width><tt:Height>1080</tt:Height></tt:Resolution></tt:VideoEncoderConfiguration></trt:Profiles>'
                          '<trt:Profiles token="substream"><tt:VideoEncoderConfiguration><tt:Resolution><tt:Width>640</tt:Width><tt:Height>360</tt:Height></tt:Resolution></tt:VideoEncoderConfiguration></trt:Profiles>'
                          '</trt:GetProfilesResponse>')
            elif operation == "GetStreamUri":
                token = next(node.text for node in document.iter() if node.tag.endswith("}ProfileToken"))
                path = "testcam" if token == "substream" else "unlisted-primary"
                result = f'<trt:GetStreamUriResponse><trt:MediaUri><tt:Uri>rtsp://{camera_host}:8554/{path}</tt:Uri></trt:MediaUri></trt:GetStreamUriResponse>'
            else:
                self.answer(500, envelope('<s:Fault><s:Reason><s:Text xml:lang="en">ActionNotSupported</s:Text></s:Reason></s:Fault>'))
                return
        count(operation)
        print("ONVIF accepted", operation, flush=True)
        self.answer(200, envelope(result))


class Rtsp401(socketserver.BaseRequestHandler):
    def handle(self):
        self.request.settimeout(3)
        try:
            data = self.request.recv(8192)
            if not data:
                return
            count("rtsp_401")
            cseq = re.search(rb"CSeq:\s*(\d+)", data, re.I)
            sequence = cseq.group(1).decode() if cseq else "1"
            self.request.sendall(f'RTSP/1.0 401 Unauthorized\r\nCSeq: {sequence}\r\nWWW-Authenticate: Basic realm="SMART24 test camera"\r\nContent-Length: 0\r\n\r\n'.encode())
            print("RTSP test camera requested credentials", flush=True)
        except OSError:
            pass


class RtspStall(socketserver.BaseRequestHandler):
    def handle(self):
        self.request.settimeout(3)
        try:
            data = self.request.recv(8192)
            if data:
                count("rtsp_stalled")
                print("RTSP native stalled request accepted", flush=True)
                threading.Event().wait(30)
        except OSError:
            pass


class ThreadedTcp(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


if __name__ == "__main__":
    for port, handler in [(8555, Rtsp401), (8556, RtspStall)]:
        server = ThreadedTcp(("0.0.0.0", port), handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
    print("SMART24 camera fixtures ready", flush=True)
    ThreadingHTTPServer(("0.0.0.0", 8080), OnvifHandler).serve_forever()

"""Descoberta e teste local de câmera Yoosee/NVR para o SMART24.

A documentação oficial da Yoosee orienta habilitar "Conexão NVR", criar uma
senha RTSP, usar usuário ``administrator`` e porta 5000. O URI final pode variar
por firmware; por isso o assistente tenta primeiro obter o URI pelo ONVIF e só
depois usa sondas de compatibilidade claramente tratadas como testes.
"""
from __future__ import annotations

from dataclasses import dataclass
import ipaddress
import socket
from concurrent.futures import ThreadPoolExecutor, as_completed
from urllib.parse import quote, urlsplit, urlunsplit


@dataclass(slots=True)
class DiscoveryResult:
    ok: bool
    host: str
    port: int
    stream_url: str = ""
    method: str = ""
    message: str = ""


def mask_url(url: str) -> str:
    try:
        p = urlsplit(url)
        host = p.hostname or ""
        port = f":{p.port}" if p.port else ""
        netloc = f"***:***@{host}{port}" if p.username or p.password else f"{host}{port}"
        return urlunsplit((p.scheme, netloc, p.path, p.query, p.fragment))
    except Exception:
        return "<url protegida>"


def test_tcp(host: str, port: int, timeout: float = 0.8) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def local_ipv4() -> str:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("8.8.8.8", 80))
        return sock.getsockname()[0]
    except OSError:
        return ""
    finally:
        sock.close()


def scan_subnet(port: int = 5000, timeout: float = 0.35) -> list[str]:
    ip = local_ipv4()
    if not ip:
        return []
    network = ipaddress.ip_network(f"{ip}/24", strict=False)
    hosts = [str(h) for h in network.hosts()]
    found: list[str] = []
    with ThreadPoolExecutor(max_workers=48) as pool:
        futures = {pool.submit(test_tcp, host, port, timeout): host for host in hosts}
        for future in as_completed(futures):
            if future.result():
                found.append(futures[future])
    return sorted(found, key=lambda value: tuple(map(int, value.split("."))))


def get_onvif_stream_uri(host: str, port: int, username: str, password: str) -> str:
    try:
        from onvif import ONVIFCamera
        camera = ONVIFCamera(host, port, username, password)
        media = camera.create_media_service()
        profiles = media.GetProfiles()
        for profile in profiles:
            request = media.create_type("GetStreamUri")
            request.ProfileToken = profile.token
            request.StreamSetup = {"Stream": "RTP-Unicast", "Transport": {"Protocol": "RTSP"}}
            response = media.GetStreamUri(request)
            uri = str(response.Uri or "").strip()
            if uri:
                parsed = urlsplit(uri)
                user = quote(username, safe="")
                pwd = quote(password, safe="")
                netloc = f"{user}:{pwd}@{parsed.hostname or host}"
                if parsed.port:
                    netloc += f":{parsed.port}"
                return urlunsplit((parsed.scheme or "rtsp", netloc, parsed.path, parsed.query, ""))
    except Exception:
        return ""
    return ""


def candidate_urls(host: str, port: int, username: str, password: str) -> list[str]:
    user = quote(username, safe="")
    pwd = quote(password, safe="")
    base = f"rtsp://{user}:{pwd}@{host}:{port}"
    # Sondas de compatibilidade: não são declaradas como padrão oficial.
    return [
        base,
        f"{base}/onvif1",
        f"{base}/onvif2",
        f"{base}/live/ch00_0",
        f"{base}/live/ch00_1",
    ]


def test_stream(url: str, timeout_ms: int = 7000) -> tuple[bool, str]:
    try:
        import cv2
    except ImportError:
        return False, "OpenCV não instalado"
    capture = cv2.VideoCapture()
    try:
        if hasattr(cv2, "CAP_PROP_OPEN_TIMEOUT_MSEC"):
            capture.set(cv2.CAP_PROP_OPEN_TIMEOUT_MSEC, timeout_ms)
        if hasattr(cv2, "CAP_PROP_READ_TIMEOUT_MSEC"):
            capture.set(cv2.CAP_PROP_READ_TIMEOUT_MSEC, timeout_ms)
        if not capture.open(url) or not capture.isOpened():
            return False, "fluxo não abriu"
        ok, frame = capture.read()
        if not ok or frame is None:
            return False, "fluxo abriu sem frame"
        h, w = frame.shape[:2]
        return True, f"frame recebido ({w}x{h})"
    except Exception as exc:
        return False, type(exc).__name__
    finally:
        capture.release()


def discover_camera(host: str, password: str, port: int = 5000, username: str = "administrator") -> DiscoveryResult:
    host = host.strip()
    if not host:
        return DiscoveryResult(False, host, port, message="Informe o IP local da câmera.")
    if not test_tcp(host, port, 1.5):
        return DiscoveryResult(False, host, port, message=f"A porta {port} não respondeu. Confira se o celular/PC e a câmera estão no mesmo roteador e se Conexão NVR está ativada.")

    uri = get_onvif_stream_uri(host, port, username, password)
    if uri:
        ok, detail = test_stream(uri)
        if ok:
            return DiscoveryResult(True, host, port, uri, "ONVIF", detail)

    for url in candidate_urls(host, port, username, password):
        ok, detail = test_stream(url)
        if ok:
            return DiscoveryResult(True, host, port, url, "RTSP_TEST", detail)

    return DiscoveryResult(False, host, port, message="A câmera respondeu na rede, mas nenhum fluxo de vídeo foi confirmado. Confira a senha NVR/RTSP e o firmware.")

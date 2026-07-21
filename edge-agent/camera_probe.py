"""Leitura segura e mínima de câmera IP para o SMART24 Fusion.

Este módulo não faz reconhecimento de pessoas nem produtos. Ele apenas testa o
fluxo configurado, lê um frame e devolve um estado técnico sem registrar senha.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from time import time
from typing import Any
from urllib.parse import urlsplit, urlunsplit


@dataclass(slots=True)
class ProbeResult:
    ok: bool
    status: str
    checked_at_ms: int
    width: int = 0
    height: int = 0
    fps: float = 0.0
    message: str = ""
    frame: Any | None = None


def mask_stream_url(url: str) -> str:
    """Remove usuário e senha antes de uma URL aparecer em log."""
    if not url:
        return "<não configurada>"
    try:
        parsed = urlsplit(url)
        hostname = parsed.hostname or ""
        port = f":{parsed.port}" if parsed.port else ""
        netloc = f"***:***@{hostname}{port}" if parsed.username or parsed.password else f"{hostname}{port}"
        return urlunsplit((parsed.scheme, netloc, parsed.path, parsed.query, parsed.fragment))
    except Exception:
        return "<url protegida>"


class CameraProbe:
    def __init__(self, stream_url: str, open_timeout_ms: int = 5000, read_timeout_ms: int = 5000) -> None:
        self.stream_url = stream_url
        self.open_timeout_ms = max(1000, int(open_timeout_ms))
        self.read_timeout_ms = max(1000, int(read_timeout_ms))

    def read_once(self) -> ProbeResult:
        checked_at = int(time() * 1000)
        if not self.stream_url:
            return ProbeResult(False, "STOPPED", checked_at, message="CAMERA_RTSP_URL não configurada")

        try:
            import cv2  # import tardio para permitir testes sem OpenCV instalado
        except ImportError:
            return ProbeResult(False, "STOPPED", checked_at, message="OpenCV não instalado")

        capture = cv2.VideoCapture()
        try:
            if hasattr(cv2, "CAP_PROP_OPEN_TIMEOUT_MSEC"):
                capture.set(cv2.CAP_PROP_OPEN_TIMEOUT_MSEC, self.open_timeout_ms)
            if hasattr(cv2, "CAP_PROP_READ_TIMEOUT_MSEC"):
                capture.set(cv2.CAP_PROP_READ_TIMEOUT_MSEC, self.read_timeout_ms)

            opened = capture.open(self.stream_url)
            if not opened or not capture.isOpened():
                return ProbeResult(False, "OFFLINE", checked_at, message="Não foi possível abrir o fluxo")

            ok, frame = capture.read()
            if not ok or frame is None:
                return ProbeResult(False, "RECONNECTING", checked_at, message="Fluxo abriu, mas nenhum frame foi recebido")

            height, width = frame.shape[:2]
            fps = float(capture.get(cv2.CAP_PROP_FPS) or 0.0)
            return ProbeResult(True, "ONLINE", checked_at, width=width, height=height, fps=fps, message="Frame recebido", frame=frame)
        except Exception as exc:  # não inclui a URL no texto
            return ProbeResult(False, "RECONNECTING", checked_at, message=f"Falha de leitura: {type(exc).__name__}")
        finally:
            capture.release()

    @staticmethod
    def save_debug_frame(frame: Any, output_path: str | Path) -> bool:
        if frame is None:
            return False
        try:
            import cv2
            path = Path(output_path)
            path.parent.mkdir(parents=True, exist_ok=True)
            return bool(cv2.imwrite(str(path), frame))
        except Exception:
            return False

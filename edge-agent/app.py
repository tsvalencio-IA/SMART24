"""Agente local mínimo do SMART24 Fusion.

Fase atual: disponibilidade, heartbeat e reconexão. Não faz reconhecimento facial,
rastreamento de pessoa, retirada, devolução ou identificação de produto.
"""

from __future__ import annotations

import logging
import os
import signal
import sys
from pathlib import Path
from time import sleep

from dotenv import load_dotenv

from camera_probe import CameraProbe, mask_stream_url
from event_publisher import EventPublisher

LOGGER = logging.getLogger("smart24.edge")
RUNNING = True


def env_bool(name: str, default: bool = False) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "sim", "on"}


def stop_agent(_signum: int, _frame: object) -> None:
    global RUNNING
    RUNNING = False


def main() -> int:
    load_dotenv()
    logging.basicConfig(
        level=os.getenv("LOG_LEVEL", "INFO").upper(),
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )

    stream_url = os.getenv("CAMERA_RTSP_URL", "").strip()
    bridge_id = os.getenv("BRIDGE_ID", "bridge-loja-01").strip()
    store_id = os.getenv("STORE_ID", "loja-01").strip()
    camera_id = os.getenv("CAMERA_ID", "CAM-01").strip()
    camera_record_id = os.getenv("CAMERA_RECORD_ID", "").strip()
    dry_run = env_bool("DRY_RUN", True)
    heartbeat_seconds = max(5, int(os.getenv("HEARTBEAT_SECONDS", "15")))
    reconnect_seconds = max(2, int(os.getenv("RECONNECT_SECONDS", "5")))
    save_debug = env_bool("SAVE_DEBUG_FRAME", False)
    debug_path = Path(os.getenv("DEBUG_FRAME_PATH", "debug/latest.jpg"))

    if not stream_url:
        LOGGER.error("CAMERA_RTSP_URL não configurada. Use o arquivo .env privado.")
        return 2

    LOGGER.info("Iniciando bridge=%s store=%s camera=%s", bridge_id, store_id, camera_id)
    LOGGER.info("Fluxo protegido: %s", mask_stream_url(stream_url))
    LOGGER.info("Vídeo contínuo não será enviado ao Firebase.")

    publisher = EventPublisher(
        database_url=os.getenv("FIREBASE_DATABASE_URL", "").strip(),
        service_account_path=os.getenv("GOOGLE_APPLICATION_CREDENTIALS", "service-account.json").strip(),
        bridge_id=bridge_id,
        store_id=store_id,
        camera_id=camera_id,
        camera_record_id=camera_record_id,
        dry_run=dry_run,
    )
    try:
        publisher.connect()
    except Exception as exc:
        LOGGER.error("Não foi possível iniciar o publicador: %s", exc)
        return 3

    probe = CameraProbe(stream_url)
    last_status = ""
    signal.signal(signal.SIGINT, stop_agent)
    signal.signal(signal.SIGTERM, stop_agent)

    while RUNNING:
        result = probe.read_once()
        details = {
            "width": result.width,
            "height": result.height,
            "fps": round(result.fps, 2),
            "message": result.message,
        }
        try:
            publisher.publish_heartbeat(result.status, details)
            if result.status != last_status:
                publisher.publish_event(
                    "CAMERA_ONLINE" if result.status == "ONLINE" else "CAMERA_OFFLINE",
                    status=result.status,
                    confidence=1.0 if result.status == "ONLINE" else 0.0,
                    message=result.message,
                )
                last_status = result.status
        except Exception as exc:
            LOGGER.error("Falha ao publicar heartbeat: %s", exc)

        if result.ok:
            LOGGER.info("ONLINE %sx%s %.2f fps", result.width, result.height, result.fps)
            if save_debug and CameraProbe.save_debug_frame(result.frame, debug_path):
                LOGGER.info("Imagem de depuração salva localmente em %s", debug_path)
            sleep(heartbeat_seconds)
        else:
            LOGGER.warning("%s: %s", result.status, result.message)
            sleep(reconnect_seconds)

    try:
        publisher.publish_heartbeat("STOPPED", {"message": "Agente encerrado"})
    except Exception as exc:
        LOGGER.warning("Não foi possível publicar STOPPED: %s", exc)
    LOGGER.info("Agente encerrado.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

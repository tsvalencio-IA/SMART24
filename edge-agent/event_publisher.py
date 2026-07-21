"""Publicador de heartbeat e eventos no Firebase Realtime Database."""

from __future__ import annotations

import json
import logging
from pathlib import Path
from time import time
from typing import Any

LOGGER = logging.getLogger("smart24.publisher")


def epoch_ms() -> int:
    return int(time() * 1000)


class EventPublisher:
    def __init__(
        self,
        database_url: str,
        service_account_path: str,
        bridge_id: str,
        store_id: str,
        camera_id: str,
        camera_record_id: str = "",
        dry_run: bool = True,
    ) -> None:
        self.database_url = database_url
        self.service_account_path = service_account_path
        self.bridge_id = bridge_id
        self.store_id = store_id
        self.camera_id = camera_id
        self.camera_record_id = camera_record_id
        self.dry_run = dry_run
        self._db = None

    def connect(self) -> None:
        if self.dry_run:
            LOGGER.info("DRY_RUN ativo: eventos serão apenas exibidos localmente.")
            return
        if not self.database_url:
            raise ValueError("FIREBASE_DATABASE_URL não configurada")
        account = Path(self.service_account_path)
        if not account.is_file():
            raise FileNotFoundError("Arquivo de conta de serviço não encontrado")

        import firebase_admin
        from firebase_admin import credentials, db

        if not firebase_admin._apps:  # API interna usada apenas para evitar inicialização duplicada
            credential = credentials.Certificate(str(account))
            firebase_admin.initialize_app(credential, {"databaseURL": self.database_url})
        self._db = db
        if not self.camera_record_id:
            self.camera_record_id = self._resolve_camera_record_id()
        LOGGER.info("Firebase Admin inicializado. cameraRecordId=%s", self.camera_record_id or "não localizado")


    def _resolve_camera_record_id(self) -> str:
        if self._db is None:
            return ""
        try:
            value = self._db.reference("cameras").get() or {}
            for record_id, camera in value.items():
                if str((camera or {}).get("cameraId", "")).upper() == self.camera_id.upper():
                    return str(record_id)
        except Exception as exc:
            LOGGER.warning("Não foi possível localizar o cadastro da câmera: %s", type(exc).__name__)
        return ""

    def _write(self, path: str, payload: dict[str, Any]) -> None:
        if self.dry_run:
            LOGGER.info("DRY_RUN %s %s", path, json.dumps(payload, ensure_ascii=False, default=str))
            return
        if self._db is None:
            raise RuntimeError("Publisher ainda não conectado")
        self._db.reference(path).set(payload)

    def _update(self, path: str, payload: dict[str, Any]) -> None:
        if self.dry_run:
            LOGGER.info("DRY_RUN UPDATE %s %s", path, json.dumps(payload, ensure_ascii=False, default=str))
            return
        if self._db is None:
            raise RuntimeError("Publisher ainda não conectado")
        self._db.reference(path).update(payload)

    def publish_heartbeat(self, status: str, details: dict[str, Any] | None = None) -> None:
        payload = {
            "bridgeId": self.bridge_id,
            "storeId": self.store_id,
            "cameraId": self.camera_id,
            "status": status,
            "lastSeenAt": epoch_ms(),
            "details": details or {},
        }
        self._write(f"cameraBridges/{self.bridge_id}", payload)
        if self.camera_record_id:
            self._update(
                f"cameras/{self.camera_record_id}",
                {"status": status, "lastSeenAt": payload["lastSeenAt"], "bridgeId": self.bridge_id},
            )

    def publish_event(self, event_type: str, **fields: Any) -> str:
        payload = {
            "type": event_type,
            "storeId": self.store_id,
            "cameraId": self.camera_id,
            "bridgeId": self.bridge_id,
            "createdAt": epoch_ms(),
            **fields,
        }
        if self.dry_run:
            event_id = f"DRY-{payload['createdAt']}"
            self._write(f"events/{event_id}", payload)
            return event_id
        if self._db is None:
            raise RuntimeError("Publisher ainda não conectado")
        reference = self._db.reference("events").push(payload)
        return str(reference.key)

"""Lógica determinística independente de câmera e Firebase."""

from __future__ import annotations

from collections import defaultdict
from typing import Any, Iterable


def reconcile_items(events: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    """Reconcilia retiradas, devoluções, registro e pagamento por SKU.

    Valores desconhecidos ou eventos ambíguos não são inventados. Nesses casos o
    item é marcado para revisão humana.
    """
    by_sku: dict[str, dict[str, Any]] = defaultdict(
        lambda: {
            "pickedUp": 0,
            "returned": 0,
            "registered": 0,
            "paid": 0,
            "confidenceValues": [],
            "insufficient": False,
        }
    )

    for event in events:
        event_type = str(event.get("type", ""))
        sku = str(event.get("sku") or event.get("productId") or "UNKNOWN")
        quantity = event.get("quantity", 1)
        try:
            quantity = int(quantity)
        except (TypeError, ValueError):
            quantity = 0

        item = by_sku[sku]
        item["sku"] = sku
        item["productId"] = event.get("productId") or ""
        item["productName"] = event.get("productName") or sku

        confidence = event.get("confidence")
        if isinstance(confidence, (int, float)):
            item["confidenceValues"].append(float(confidence))

        if event_type == "PRODUCT_PICKUP":
            item["pickedUp"] += quantity
        elif event_type == "PRODUCT_RETURN":
            item["returned"] += quantity
        elif event_type == "CHECKOUT_ITEM_REGISTERED":
            item["registered"] += quantity
        elif event_type == "CHECKOUT_ITEM_REMOVED":
            item["registered"] -= quantity
        elif event_type == "PAYMENT_APPROVED":
            item["paid"] += quantity
        elif event_type in {"AMBIGUOUS_INTERACTION", "IMAGE_INSUFFICIENT", "CAMERA_OFFLINE"}:
            item["insufficient"] = True

    results: list[dict[str, Any]] = []
    for sku, item in by_sku.items():
        confidence_values = item.pop("confidenceValues")
        confidence = min(confidence_values) if confidence_values else 0.0
        if item["insufficient"]:
            expected: int | None = None
            difference: int | None = None
            review_required = True
            reason = "Imagem insuficiente ou interação ambígua"
        else:
            expected = item["pickedUp"] - item["returned"]
            difference = expected - item["paid"]
            review_required = difference != 0 or item["registered"] != item["paid"]
            reason = "Possível divergência por SKU" if review_required else "Sem divergência"

        results.append(
            {
                **item,
                "sku": sku,
                "expected": expected,
                "difference": difference,
                "confidence": confidence,
                "reviewRequired": review_required,
                "reason": reason,
            }
        )

    return sorted(results, key=lambda result: result["sku"])

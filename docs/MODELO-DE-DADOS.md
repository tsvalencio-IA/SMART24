# Modelo de dados

## Caminhos principais

```text
roles/
stores/
products/
tags/
cameras/
cameraBridges/
sessions/
events/
occurrences/
auditLogs/
```

## Produto

```json
{
  "name": "Água mineral 500 ml",
  "sku": "AGUA-500",
  "barcode": "789...",
  "category": "Bebidas",
  "status": "active",
  "createdAt": 0,
  "createdBy": "UID"
}
```

## Etiqueta

```json
{
  "serial": "TAG-LOJA01-000001",
  "productId": "PRODUCT-ID",
  "productName": "Água mineral 500 ml",
  "sku": "AGUA-500",
  "barcode": "789...",
  "storeId": "loja-01",
  "zoneId": "zona-a-definir",
  "status": "CADASTRADO",
  "qrPayload": "{...}",
  "rfidEpc": "",
  "createdAt": 0,
  "createdBy": "UID"
}
```

## Evento

Tipos previstos:

- `ACCESS_GRANTED`
- `PERSON_ENTERED`
- `PERSON_MOVED`
- `PRODUCT_PICKUP`
- `PRODUCT_RETURN`
- `PRODUCT_TRANSFER`
- `CHECKOUT_ITEM_REGISTERED`
- `CHECKOUT_ITEM_REMOVED`
- `PAYMENT_APPROVED`
- `PAYMENT_REJECTED`
- `PERSON_EXITED`
- `CAMERA_OFFLINE`
- `CAMERA_ONLINE`
- `AMBIGUOUS_INTERACTION`
- `IMAGE_INSUFFICIENT`
- `OCCURRENCE_CREATED`

## Ocorrência

Uma ocorrência deve armazenar loja, sessão, pessoa, produto, retiradas, devoluções, esperado, registrado, pago, diferença, confiança, câmeras, horários, motivo, status e revisão humana.

Classificações humanas:

- confirmada após revisão;
- falso alerta;
- produto devolvido em outro local;
- erro de cadastro;
- erro de quantidade;
- imagem insuficiente;
- outro.

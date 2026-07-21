import assert from "node:assert/strict";
import { parseCameraQrPayload } from "../assets/js/camera-qr.js";

const yoosee = parseCameraQrPayload("https://share.yoosee.com/share/index.html?Type=2&InviteCode=SEGREDO_DESCARTADO&DeviceID=1234567890&ExpireTime=1784748482");
assert.equal(yoosee.vendor, "YOOSEE");
assert.equal(yoosee.vendorDeviceId, "1234567890");
assert.ok(yoosee.shareExpiresAt > 0);
assert.equal("inviteCode" in yoosee, false);

const numeric = parseCameraQrPayload("1234567890");
assert.equal(numeric.vendor, "A_CONFIRMAR");
assert.equal(numeric.vendorDeviceId, "1234567890");

const json = parseCameraQrPayload(JSON.stringify({ vendor: "YOOSEE", deviceId: "1234567890", password: "nao-deve-sair" }));
assert.equal(json.vendor, "YOOSEE");
assert.equal(json.vendorDeviceId, "1234567890");
assert.equal("password" in json, false);

assert.throws(() => parseCameraQrPayload("texto sem id"));
console.log("4 testes do parser QR aprovados.");

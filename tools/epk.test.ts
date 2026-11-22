import { test } from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync } from "node:crypto";
import { readFileSync } from "node:fs";
import { buildEpk, extractEpk, HEADER_SIZE, inspectEpk, loadPublicKey } from "./epk.ts";

const { publicKey, privateKey } = generateKeyPairSync("rsa", { modulusLength: 1024 });

test("round-trip: build with public key, extract with private key", () => {
  const apk = Buffer.from("PK\x03\x04" + "q50".repeat(1000) + "end"); // length not a multiple of 16
  const lib = Buffer.alloc(32, 7); // exact multiple of 16 -> full padding block
  const epk = buildEpk(
    [
      { name: "app.apk", data: apk },
      { name: "extra.bin", data: lib },
    ],
    publicKey,
  );
  const out = extractEpk(epk, privateKey);
  assert.deepEqual(
    out.map((o) => o.name),
    ["app.apk", "extra.bin"],
  );
  assert.ok(out[0].data.equals(apk));
  assert.ok(out[1].data.equals(lib));
});

test("header layout matches the head unit's v2 format", () => {
  const epk = buildEpk([{ name: "x.apk", data: Buffer.alloc(100) }], publicKey);
  assert.equal(epk.toString("ascii", 0, 4), ".epk");
  assert.equal(epk.readUInt16BE(4), 2); // version
  assert.equal(epk.readUInt16BE(6), 2); // payload type
  assert.equal(epk.readUInt16BE(8), 1); // blocks
  assert.equal(epk.readUInt16BE(10), 128); // RSA-1024 wrapped key
  assert.ok(epk.subarray(12 + 128, HEADER_SIZE).every((b) => b === 0), "key field zero-padded");
  assert.equal(epk.toString("ascii", HEADER_SIZE, HEADER_SIZE + 5), "x.apk");
  assert.equal(epk.readInt32BE(HEADER_SIZE + 128), 112); // 100 B -> 7 AES blocks
  assert.equal(epk.length, HEADER_SIZE + 128 + 4 + 112);
});

test("data keys differ between builds", () => {
  const a = buildEpk([{ name: "a.apk", data: Buffer.alloc(16) }], publicKey);
  const b = buildEpk([{ name: "a.apk", data: Buffer.alloc(16) }], publicKey);
  assert.ok(!a.equals(b));
});

test("bundled OBU certificate is RSA-1024 and builds a parsable package", () => {
  const key = loadPublicKey(readFileSync(new URL("../keys/obu_cert.pem", import.meta.url)));
  assert.equal(key.asymmetricKeyDetails?.modulusLength, 1024);
  const info = inspectEpk(buildEpk([{ name: "t.apk", data: Buffer.alloc(10) }], key));
  assert.equal(info.keySize, 128);
  assert.equal(info.blocks[0].name, "t.apk");
});

test("inspect rejects broken files", () => {
  const good = buildEpk([{ name: "a.apk", data: Buffer.alloc(40) }], publicKey);
  assert.throws(() => inspectEpk(Buffer.from("nope")), /too short/);
  assert.throws(() => inspectEpk(Buffer.concat([Buffer.from("xepk"), good.subarray(4)])), /magic/);
  assert.throws(() => inspectEpk(good.subarray(0, good.length - 1)), /exceeds/);
  assert.throws(() => inspectEpk(Buffer.concat([good, Buffer.alloc(1)])), /trailing/);
});

test("rejects non-ASCII and over-long file names", () => {
  assert.throws(() => buildEpk([{ name: "zażółć.apk", data: Buffer.alloc(1) }], publicKey), /ASCII/);
  assert.throws(() => buildEpk([{ name: "a".repeat(129), data: Buffer.alloc(1) }], publicKey), /longer/);
});

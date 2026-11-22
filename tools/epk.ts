#!/usr/bin/env node
/**
 * epk.ts – build and inspect Infiniti InTouch "App Garage" packages (.epk, v2).
 *
 * Format (big-endian), as implemented by com.connexis.ivi.utils.epk.v2 on the head unit:
 *
 *   ".epk" | u16 version=2 | u16 payloadType=2 | u16 blockCount | u16 keySize
 *   256 B  wrapped data key (RSA PKCS#1 v1.5 with the unit's public OBU cert, zero-padded)
 *   per block: 128 B ASCII filename (zero-padded) | i32 length | AES-256-CBC ciphertext
 *
 * The data key is 20 ASCII characters, zero-padded to 32 bytes for AES-256; the IV is fixed.
 * There is no signature or MAC: building needs only the public certificate (keys/obu_cert.pem).
 *
 * Usage:
 *   node tools/epk.ts build <in.apk> [more files...] <out.epk> [--cert keys/obu_cert.pem]
 *   node tools/epk.ts inspect <file.epk>
 */
import {
  createCipheriv,
  createDecipheriv,
  createPublicKey,
  constants,
  privateDecrypt,
  publicEncrypt,
  randomBytes,
  X509Certificate,
  type KeyObject,
} from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";
import { basename } from "node:path";

export const MAGIC = ".epk";
export const VERSION = 2;
export const PAYLOAD_TYPE_APK = 2;
export const KEY_FIELD_SIZE = 256;
export const NAME_FIELD_SIZE = 128;
export const HEADER_SIZE = 4 + 2 + 2 + 2 + 2 + KEY_FIELD_SIZE; // 268
export const IV = Buffer.from([0x01, 0x23, 0x45, 0x67, 0x89, 0xab, 0xcd, 0xef, 0, 0, 0, 0, 0, 0, 0, 0]);

export interface EpkInput {
  name: string;
  data: Buffer;
}

export interface EpkBlockInfo {
  name: string;
  offset: number;
  length: number;
}

export interface EpkInfo {
  version: number;
  payloadType: number;
  blockCount: number;
  keySize: number;
  wrappedKey: Buffer;
  blocks: EpkBlockInfo[];
}

/** Accepts an X.509 certificate PEM or a public key PEM. */
export function loadPublicKey(pem: string | Buffer): KeyObject {
  const text = pem.toString();
  if (text.includes("BEGIN CERTIFICATE")) {
    const start = text.indexOf("-----BEGIN CERTIFICATE-----");
    return new X509Certificate(text.slice(start)).publicKey;
  }
  return createPublicKey(text);
}

function aesKey(dataKey: Buffer): Buffer {
  const key = Buffer.alloc(32);
  dataKey.copy(key);
  return key;
}

/** 20 random ASCII hex characters, like the head unit's own builder. */
export function newDataKey(): Buffer {
  return Buffer.from(randomBytes(10).toString("hex"), "ascii");
}

export function buildEpk(
  inputs: EpkInput[],
  publicKey: KeyObject,
  opts: { payloadType?: number; dataKey?: Buffer } = {},
): Buffer {
  if (inputs.length === 0) throw new Error("at least one input file is required");
  const dataKey = opts.dataKey ?? newDataKey();
  const wrapped = publicEncrypt({ key: publicKey, padding: constants.RSA_PKCS1_PADDING }, dataKey);
  if (wrapped.length > KEY_FIELD_SIZE) throw new Error(`RSA key too large: ${wrapped.length} B`);

  const header = Buffer.alloc(HEADER_SIZE);
  header.write(MAGIC, 0, "ascii");
  header.writeUInt16BE(VERSION, 4);
  header.writeUInt16BE(opts.payloadType ?? PAYLOAD_TYPE_APK, 6);
  header.writeUInt16BE(inputs.length, 8);
  header.writeUInt16BE(wrapped.length, 10);
  wrapped.copy(header, 12);

  const parts: Buffer[] = [header];
  for (const input of inputs) {
    const name = Buffer.from(input.name, "ascii");
    if (!/^[\x20-\x7e]+$/.test(input.name)) throw new Error(`file name must be printable ASCII: ${input.name}`);
    if (name.length > NAME_FIELD_SIZE) throw new Error(`file name longer than ${NAME_FIELD_SIZE} B: ${input.name}`);
    const cipher = createCipheriv("aes-256-cbc", aesKey(dataKey), IV);
    const ct = Buffer.concat([cipher.update(input.data), cipher.final()]);
    const blockHeader = Buffer.alloc(NAME_FIELD_SIZE + 4);
    name.copy(blockHeader, 0);
    blockHeader.writeInt32BE(ct.length, NAME_FIELD_SIZE);
    parts.push(blockHeader, ct);
  }
  return Buffer.concat(parts);
}

export function inspectEpk(buf: Buffer): EpkInfo {
  if (buf.length < HEADER_SIZE) throw new Error("file too short for an EPK header");
  if (buf.toString("ascii", 0, 4) !== MAGIC) throw new Error("bad magic (expected .epk)");
  const version = buf.readUInt16BE(4);
  const payloadType = buf.readUInt16BE(6);
  const blockCount = buf.readUInt16BE(8);
  const keySize = buf.readUInt16BE(10);
  if (keySize === 0 || keySize > KEY_FIELD_SIZE) throw new Error(`invalid key size ${keySize}`);
  const wrappedKey = buf.subarray(12, 12 + keySize);

  const blocks: EpkBlockInfo[] = [];
  let off = HEADER_SIZE;
  for (let i = 0; i < blockCount; i++) {
    if (off + NAME_FIELD_SIZE + 4 > buf.length) throw new Error(`block ${i}: truncated header`);
    const rawName = buf.subarray(off, off + NAME_FIELD_SIZE);
    const end = rawName.indexOf(0);
    const name = rawName.toString("ascii", 0, end < 0 ? NAME_FIELD_SIZE : end);
    const length = buf.readInt32BE(off + NAME_FIELD_SIZE);
    off += NAME_FIELD_SIZE + 4;
    if (length < 0 || off + length > buf.length) throw new Error(`block ${i} (${name}): length ${length} exceeds file`);
    blocks.push({ name, offset: off, length });
    off += length;
  }
  if (off !== buf.length) throw new Error(`${buf.length - off} trailing bytes after last block`);
  return { version, payloadType, blockCount, keySize, wrappedKey, blocks };
}

/**
 * Decrypts an EPK with a private key. Only useful for tests with your own key pair:
 * the head unit's private key is not part of this project.
 * (PKCS#1 v1.5 unwrapping is done by hand because Node disables it for privateDecrypt.)
 */
export function extractEpk(buf: Buffer, privateKey: KeyObject): EpkInput[] {
  const info = inspectEpk(buf);
  const em = privateDecrypt({ key: privateKey, padding: constants.RSA_NO_PADDING }, info.wrappedKey);
  if (em[0] !== 0x00 || em[1] !== 0x02) throw new Error("wrapped key: bad PKCS#1 v1.5 block");
  const sep = em.indexOf(0x00, 2);
  if (sep < 10) throw new Error("wrapped key: bad PKCS#1 v1.5 padding");
  const dataKey = em.subarray(sep + 1);
  return info.blocks.map((b) => {
    const decipher = createDecipheriv("aes-256-cbc", aesKey(dataKey), IV);
    const ct = buf.subarray(b.offset, b.offset + b.length);
    return { name: b.name, data: Buffer.concat([decipher.update(ct), decipher.final()]) };
  });
}

function main(argv: string[]): void {
  const [cmd, ...rest] = argv;
  if (cmd === "build") {
    let cert = new URL("../keys/obu_cert.pem", import.meta.url).pathname;
    const files: string[] = [];
    for (let i = 0; i < rest.length; i++) {
      if (rest[i] === "--cert") cert = rest[++i];
      else files.push(rest[i]);
    }
    const out = files.pop();
    if (!out || files.length === 0) throw new Error("usage: build <in...> <out.epk> [--cert file]");
    const inputs = files.map((f) => ({ name: basename(f), data: readFileSync(f) }));
    const epk = buildEpk(inputs, loadPublicKey(readFileSync(cert)));
    writeFileSync(out, epk);
    console.log(`built ${out} (${epk.length} B) from ${inputs.map((i) => i.name).join(", ")}`);
  } else if (cmd === "inspect" && rest.length === 1) {
    const info = inspectEpk(readFileSync(rest[0]));
    console.log(`version      ${info.version}`);
    console.log(`payload type ${info.payloadType}`);
    console.log(`key size     ${info.keySize} B (RSA-${info.keySize * 8})`);
    console.log(`blocks       ${info.blockCount}`);
    for (const b of info.blocks) console.log(`  ${b.name}  ${b.length} B encrypted @ 0x${b.offset.toString(16)}`);
  } else {
    console.error("usage:\n  epk.ts build <in...> <out.epk> [--cert keys/obu_cert.pem]\n  epk.ts inspect <file.epk>");
    process.exit(2);
  }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  try {
    main(process.argv.slice(2));
  } catch (e) {
    console.error(`error: ${(e as Error).message}`);
    process.exit(1);
  }
}

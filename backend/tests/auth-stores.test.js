import test from "node:test";
import assert from "node:assert/strict";
import worker, { AuthChallengeStore, AuthDeviceStore, verifyDeviceSignature, signSession, verifySession } from "../worker.js";

class MemoryStorage {
  constructor() { this.values = new Map(); this.alarmAt = null; }
  async get(key) { return this.values.get(key); }
  async put(key, value) { this.values.set(key, structuredClone(value)); }
  async delete(key) { return this.values.delete(key); }
  async setAlarm(value) { this.alarmAt = value; }
  async transaction(callback) {
    // Queue transactions so concurrent test requests cannot interleave storage operations.
    const previous = this.transactionTail || Promise.resolve();
    let release;
    this.transactionTail = new Promise(resolve => { release = resolve; });
    await previous;
    try {
      return await callback({
        get: async key => this.values.get(key),
        put: async (key, value) => this.values.set(key, structuredClone(value)),
        delete: async key => this.values.delete(key)
      });
    } finally {
      release();
    }
  }
}
const request = (path, body) => new Request("https://unit.test" + path, {
  method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body)
});
const read = async response => ({ status: response.status, body: await response.json() });

test("challenge can be consumed only once and only for its installation and purpose", async () => {
  const storage = new MemoryStorage();
  const store = new AuthChallengeStore({ storage });
  const challenge = { installId: "device-1", purpose: "session" };
  assert.equal((await read(await store.fetch(request("/create", challenge)))).body.ok, true);
  assert.equal((await read(await store.fetch(request("/consume", { ...challenge })))).body.ok, true);
  const replay = await read(await store.fetch(request("/consume", { ...challenge })));
  assert.equal(replay.status, 401);
  assert.equal(replay.body.ok, false);
});

test("challenge rejects a different installation or purpose without consuming the valid challenge", async () => {
  const storage = new MemoryStorage();
  const store = new AuthChallengeStore({ storage });
  const challenge = { installId: "device-1", purpose: "enroll" };
  await store.fetch(request("/create", challenge));
  assert.equal((await read(await store.fetch(request("/consume", { installId: "device-2", purpose: "enroll" })))).body.ok, false);
  assert.equal((await read(await store.fetch(request("/consume", challenge)))).body.ok, true);
});

test("expired challenges are rejected", async () => {
  const storage = new MemoryStorage();
  const store = new AuthChallengeStore({ storage });
  await store.fetch(request("/create", { installId: "device-1", purpose: "session" }));
  const saved = storage.values.get("challenge");
  saved.createdAt = Date.now() - 120001;
  storage.values.set("challenge", saved);
  const result = await read(await store.fetch(request("/consume", { installId: "device-1", purpose: "session" })));
  assert.equal(result.status, 401);
  assert.equal(result.body.ok, false);
});

test("challenge alarm removes stored challenge", async () => {
  const storage = new MemoryStorage();
  const store = new AuthChallengeStore({ storage });
  await store.fetch(request("/create", { installId: "device-1", purpose: "enroll" }));
  await store.alarm();
  assert.equal(await storage.get("challenge"), undefined);
});

test("device registration is first-write-wins and revocation is persistent", async () => {
  const storage = new MemoryStorage();
  const store = new AuthDeviceStore({ storage });
  const first = await read(await store.fetch(request("/register", { device: { publicKey: "key-A", status: "active" } })));
  const second = await read(await store.fetch(request("/register", { device: { publicKey: "key-B", status: "active" } })));
  assert.equal(first.body.ok, true);
  assert.equal(second.status, 409);
  assert.equal((await read(await store.fetch(request("/revoke", {})))).body.ok, true);
  const current = await read(await store.fetch(request("/get", {})));
  assert.equal(current.body.device.status, "revoked");
});

test("device registration rejects malformed records", async () => {
  const storage = new MemoryStorage();
  const store = new AuthDeviceStore({ storage });
  const result = await read(await store.fetch(request("/register", { device: { publicKey: "", status: "pending" } })));
  assert.equal(result.status, 409);
  assert.equal(result.body.ok, false);
});

test("concurrent challenge consumption has exactly one winner", async () => {
  const storage = new MemoryStorage();
  const store = new AuthChallengeStore({ storage });
  const challenge = { installId: "device-1", purpose: "session" };
  await store.fetch(request("/create", challenge));
  const results = await Promise.all([
    store.fetch(request("/consume", challenge)).then(read),
    store.fetch(request("/consume", challenge)).then(read)
  ]);
  assert.equal(results.filter(result => result.body.ok).length, 1);
  assert.equal(results.filter(result => result.status === 401).length, 1);
});

test("concurrent device registration cannot replace the first key", async () => {
  const storage = new MemoryStorage();
  const store = new AuthDeviceStore({ storage });
  const results = await Promise.all([
    store.fetch(request("/register", { device: { publicKey: "key-A", status: "active" } })).then(read),
    store.fetch(request("/register", { device: { publicKey: "key-B", status: "active" } })).then(read)
  ]);
  assert.equal(results.filter(result => result.body.ok).length, 1);
  assert.equal(results.filter(result => result.status === 409).length, 1);
  const current = await read(await store.fetch(request("/get", {})));
  assert.ok(["key-A", "key-B"].includes(current.body.device.publicKey));
});

test("health endpoint exposes only basic service status", async () => {
  const response = await worker.fetch(new Request("https://unit.test/v1/health"), {});
  const result = await read(response);
  assert.equal(result.status, 200);
  assert.equal(result.body.ok, true);
  assert.equal("secrets" in result.body, false);
});

test("authentication endpoints fail closed when required configuration is missing", async () => {
  const response = await worker.fetch(request("/v1/auth/challenge", {
    installId: "123e4567-e89b-42d3-a456-426614174000", purpose: "enroll"
  }), {});
  assert.equal(response.status, 503);
});

test("authentication endpoint rejects non-object JSON bodies cleanly", async () => {
  const allow = { limit: async () => ({ success: true }) };
  const unusedNamespace = {
    idFromName: name => name,
    get: () => ({ fetch: async () => new Response(JSON.stringify({ ok: false }), { status: 404 }) })
  };
  const env = {
    AUTH_DEVICES: unusedNamespace,
    AUTH_CHALLENGES: unusedNamespace,
    SESSION_SIGNING_SECRET: "x".repeat(48),
    ENROLL_LIMITER: allow,
    RATE_LIMITER: allow
  };
  const response = await worker.fetch(request("/v1/auth/challenge", null), env);
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error, "invalid_request");
});


const toBase64Url = bytes => Buffer.from(bytes).toString("base64url");
const derInteger = raw => {
  let value = Buffer.from(raw);
  while (value.length > 1 && value[0] === 0) value = value.subarray(1);
  if (value[0] & 0x80) value = Buffer.concat([Buffer.from([0]), value]);
  return Buffer.concat([Buffer.from([0x02, value.length]), value]);
};
const rawSignatureToDer = raw => {
  const r = derInteger(raw.subarray(0, 32));
  const s = derInteger(raw.subarray(32, 64));
  const body = Buffer.concat([r, s]);
  return Buffer.concat([Buffer.from([0x30, body.length]), body]);
};
const installId = "123e4567-e89b-42d3-a456-426614174000";
const makeDeviceNamespace = status => ({
  idFromName: id => id,
  get: () => ({
    fetch: async () => new Response(JSON.stringify({
      ok: true, device: { status, publicKey: "not-needed-by-session-test" }
    }), { headers: { "content-type": "application/json" } })
  })
});

test("P-256 device signature accepts the correct message and rejects tampering", async () => {
  const pair = await crypto.subtle.generateKey(
    { name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]
  );
  const spki = new Uint8Array(await crypto.subtle.exportKey("spki", pair.publicKey));
  const message = "session|" + installId + "|test-challenge";
  const raw = new Uint8Array(await crypto.subtle.sign(
    { name: "ECDSA", hash: "SHA-256" }, pair.privateKey, new TextEncoder().encode(message)
  ));
  assert.equal(raw.length, 64);
  const der = rawSignatureToDer(raw);
  assert.equal(await verifyDeviceSignature(toBase64Url(spki), toBase64Url(der), message), true);
  assert.equal(await verifyDeviceSignature(toBase64Url(spki), toBase64Url(der), message + "-changed"), false);
});

test("P-256 device signature rejects non-canonical DER INTEGER padding deterministically", async () => {
  const pair = await crypto.subtle.generateKey(
    { name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]
  );
  const spki = new Uint8Array(await crypto.subtle.exportKey("spki", pair.publicKey));
  const message = "enroll|" + installId + "|padding-test";
  // r=1 is positive and valid as an INTEGER, but the extra leading zero is redundant.
  // This makes the malformed DER case deterministic instead of depending on random ECDSA output.
  const nonCanonical = Buffer.from([
    0x30, 0x08,
    0x02, 0x02, 0x00, 0x01,
    0x02, 0x02, 0x00, 0x01
  ]);
  assert.equal(await verifyDeviceSignature(toBase64Url(spki), toBase64Url(nonCanonical), message), false);
});

test("P-256 device signature rejects malformed DER and invalid key data", async () => {
  assert.equal(await verifyDeviceSignature("not-a-key", "not-a-signature", "message"), false);
  const pair = await crypto.subtle.generateKey(
    { name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]
  );
  const spki = new Uint8Array(await crypto.subtle.exportKey("spki", pair.publicKey));
  assert.equal(await verifyDeviceSignature(toBase64Url(spki), toBase64Url(new Uint8Array([0x30, 0x01, 0x00])), "message"), false);
});

test("signed session token validates only while device is active", async () => {
  const secret = "S".repeat(48);
  const env = { AUTH_DEVICES: makeDeviceNamespace("active") };
  const now = Math.floor(Date.now() / 1000);
  const token = await signSession({
    iss: "mohamed-tv-ai", aud: "tv-app", sub: installId, iat: now, exp: now + 600
  }, secret);
  assert.equal((await verifySession(token, secret, env)).sub, installId);
  env.AUTH_DEVICES = makeDeviceNamespace("revoked");
  assert.equal(await verifySession(token, secret, env), null);
});

test("session verification rejects altered token, wrong secret and expired claims", async () => {
  const secret = "S".repeat(48);
  const env = { AUTH_DEVICES: makeDeviceNamespace("active") };
  const now = Math.floor(Date.now() / 1000);
  const good = await signSession({
    iss: "mohamed-tv-ai", aud: "tv-app", sub: installId, iat: now, exp: now + 600
  }, secret);
  assert.equal(await verifySession(good + "x", secret, env), null);
  assert.equal(await verifySession(good, "different-secret".padEnd(48, "x"), env), null);
  const expired = await signSession({
    iss: "mohamed-tv-ai", aud: "tv-app", sub: installId, iat: now - 900, exp: now - 1
  }, secret);
  assert.equal(await verifySession(expired, secret, env), null);
});

test("session verification rejects unsupported JWT header algorithms and extra claims", async () => {
  const secret = "S".repeat(48);
  const env = { AUTH_DEVICES: makeDeviceNamespace("active") };
  const now = Math.floor(Date.now() / 1000);
  const badAlgorithm = await signSession({
    iss: "mohamed-tv-ai", aud: "tv-app", sub: installId, iat: now, exp: now + 600,
    header: { alg: "none" }
  }, secret);
  // signSession always emits the fixed HS256 header; alter its first segment to test parser rejection.
  const parts = badAlgorithm.split(".");
  parts[0] = toBase64Url(Buffer.from(JSON.stringify({ alg: "none", typ: "JWT" })));
  const unsignedHeader = parts[0] + "." + parts[1];
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  parts[2] = toBase64Url(new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(unsignedHeader))));
  assert.equal(await verifySession(parts.join("."), secret, env), null);

  const extraClaim = await signSession({
    iss: "mohamed-tv-ai", aud: "tv-app", sub: installId, iat: now, exp: now + 600, admin: true
  }, secret);
  assert.equal(await verifySession(extraClaim, secret, env), null);
});

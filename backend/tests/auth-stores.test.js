import test from "node:test";
import assert from "node:assert/strict";
import worker, { AuthChallengeStore, AuthDeviceStore } from "../worker.js";

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

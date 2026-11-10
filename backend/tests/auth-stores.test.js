import test from "node:test";
import assert from "node:assert/strict";
import { AuthChallengeStore, AuthDeviceStore } from "../worker.js";

class MemoryStorage {
  constructor() { this.values = new Map(); this.alarmAt = null; }
  async get(key) { return this.values.get(key); }
  async put(key, value) { this.values.set(key, structuredClone(value)); }
  async delete(key) { return this.values.delete(key); }
  async setAlarm(value) { this.alarmAt = value; }
  async transaction(callback) {
    // Serialize transactions to model the Durable Object's single-threaded storage transaction.
    const result = await callback({
      get: async key => this.values.get(key),
      put: async (key, value) => this.values.set(key, structuredClone(value)),
      delete: async key => this.values.delete(key)
    });
    return result;
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

import test from "node:test";
import assert from "node:assert/strict";
import { getSportsLeadersInfo } from "../worker.js";

test("standings query uses SportScore and returns league rows with attribution", async () => {
  let url = "";
  const result = await getSportsLeadersInfo("egyptian-premier-league", "standings", async value => {
    url = String(value);
    return new Response(JSON.stringify({ standings: [{ team: "Al Ahly", points: 10, position: 1 }] }), { status: 200 });
  });
  assert.match(url, /sport=football/);
  assert.match(url, /slug=56ypq3nh01nmd7o/);
  assert.match(url, /standings/);
  assert.equal(result.ok, true);
  assert.equal(result.rows[0].team, "Al Ahly");
  assert.equal(result.attribution, "Powered by SportScore");
});

test("top scorers and assists use the requested statistic", async () => {
  const urls = [];
  for (const stat of ["scorers", "assists"]) {
    const result = await getSportsLeadersInfo("saudi-pro-league", stat, async value => {
      urls.push(String(value));
      return new Response(JSON.stringify({ players: [{ player: "Player One", goals: 5, assists: 2 }] }), { status: 200 });
    });
    assert.equal(result.ok, true);
    assert.equal(result.stat, stat);
    assert.equal(result.rows.length, 1);
  }
  assert.match(urls[0], /stat=goals/);
  assert.match(urls[1], /stat=assists/);
});

test("sports leaders rejects unsupported leagues and never calls provider", async () => {
  let called = false;
  const result = await getSportsLeadersInfo("unknown", "standings", async () => { called = true; });
  assert.deepEqual(result, { ok: false, error: "unsupported_sports_query" });
  assert.equal(called, false);
});

test("sports leaders reports upstream errors without fabricated statistics", async () => {
  await assert.rejects(() => getSportsLeadersInfo("spanish-la-liga", "scorers", async () => new Response("unavailable", { status: 503 })), /sports_stats_http_503/);
});

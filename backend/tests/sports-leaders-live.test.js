import test from "node:test";
import assert from "node:assert/strict";
import { getSportsLeadersInfo } from "../worker.js";

const leagues = ["egyptian-premier-league", "saudi-pro-league", "english-premier-league", "spanish-la-liga"];
test("LIVE SportScore API smoke test: standings, scorers and assists for all supported leagues", { timeout: 90000 }, async t => {
  for (const league of leagues) {
    await t.test(league, async () => {
      const standings = await getSportsLeadersInfo(league, "standings");
      assert.equal(standings.ok, true);
      assert.ok(standings.rows.length > 0, "provider returned no standings rows");
      assert.ok(standings.rows.some(row => row.team && row.team !== "غير معروف"), "team names missing");
      const scorers = await getSportsLeadersInfo(league, "scorers");
      assert.equal(scorers.ok, true);
      assert.ok(scorers.rows.length > 0, "provider returned no scorer rows");
      assert.ok(scorers.rows.some(row => row.player && row.player !== "غير معروف"), "player names missing");
      const assists = await getSportsLeadersInfo(league, "assists");
      assert.equal(assists.ok, true);
      assert.ok(assists.rows.length > 0, "provider returned no assists rows");
      assert.ok(assists.rows.some(row => row.player && row.player !== "غير معروف"), "assist player names missing");
    });
  }
});

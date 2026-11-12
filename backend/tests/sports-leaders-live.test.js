import test from "node:test";
import assert from "node:assert/strict";
import { getSportsLeadersInfo } from "../worker.js";

const leagues = ["egyptian-premier-league", "saudi-pro-league", "english-premier-league", "spanish-la-liga"];
test("LIVE SportScore API response inspection for supported leagues", { timeout: 90000 }, async t => {
  for (const league of leagues) {
    await t.test(league, async () => {
      const slug = ({ "egyptian-premier-league":"egyptian-premier-league", "saudi-pro-league":"saudi-professional-league", "english-premier-league":"english-premier-league", "spanish-la-liga":"spanish-la-liga" })[league];
      const url = "https://sportscore.com/api/widget/standings/?sport=football&slug=" + encodeURIComponent(slug) + "&src=mohamed-tv-ai-assistant";
      const response = await fetch(url, { headers: { accept: "application/json" }, signal: AbortSignal.timeout(15000) });
      const body = await response.text();
      console.log("SPORTS_LIVE", league, "HTTP", response.status, body.slice(0, 1800));
      assert.equal(response.ok, true, "SportScore standings endpoint must return HTTP 2xx");
      const data = JSON.parse(body);
      assert.ok(data && typeof data === "object", "JSON object expected");
      const parsed = await getSportsLeadersInfo(league, "standings");
      assert.equal(parsed.ok, true);
      assert.ok(parsed.rows.length > 0);
    });
  }
});

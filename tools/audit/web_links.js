// Browser Glowfin: every "#/..." link written in server/web/js must open a real page.
// A link that matches no page quietly sends you back to Home (that's how Music > Albums broke).
// Run: node tools/audit/web_links.js
const fs = require("fs");
const path = require("path");
const dir = path.join(__dirname, "..", "..", "server", "web", "js");
const routes = [];
const links = [];
for (const f of fs.readdirSync(dir).filter((n) => n.endsWith(".js"))) {
  const s = fs.readFileSync(path.join(dir, f), "utf8");
  for (const m of s.matchAll(/B\.route\((\/(?:\\.|[^/\n])+\/[a-z]*)/g)) routes.push(eval(m[1]));
  for (const m of s.matchAll(/["'`](#\/[^"'`\s]*)["'`]/g)) {
    const raw = m[1];
    if (raw.endsWith("/")) continue; // the start of a link that's added to (like "#/book/" + id)
    if (/^#\/\$\{/.test(raw)) continue; // a whole page name filled in later
    const line = s.slice(0, m.index).split("\n").length;
    links.push({ raw, where: `${f}:${line}` });
  }
}
// A filled-in part (like ${id}) could be a number, a word like movies or tv, or a 4-letter lobby code
const FILLS = ["1", "movie", "movies", "shows", "tv", "ABCD"];
const opens = (raw) => {
  const i = raw.indexOf("${");
  if (i < 0) return routes.some((r) => r.test(raw.slice(1)));
  const end = raw.indexOf("}", i);
  return FILLS.some((x) => opens(raw.slice(0, i) + x + raw.slice(end + 1)));
};
const broken = links.filter(({ raw }) => raw !== "#/" && !opens(raw));
broken.forEach((b) => console.log(`No page for ${b.raw} (${b.where})`));
console.log(`${routes.length} pages, ${links.length} links, ${broken.length} broken`);
process.exit(broken.length ? 1 : 0);

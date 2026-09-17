import { readFile, writeFile } from "node:fs/promises";
import path from "node:path";

const filePath = path.join(process.cwd(), "serverBootstrap.js");
const source = await readFile(filePath, "utf8");
const broken = "mediaUrl:`/api/social/media/${x.media_id}`";
const fixed = "mediaUrl:\\`/api/social/media/\\${x.media_id}\\`";

if (source.includes(broken)) {
  const patched = source.replaceAll(broken, fixed);
  await writeFile(filePath, patched, "utf8");
  console.log("FYNX Render startup source guard: repaired nested media URL template");
} else if (source.includes(fixed)) {
  console.log("FYNX Render startup source guard: nested media URL template already repaired");
} else {
  throw new Error("FYNX Render startup source guard: expected media URL template marker was not found");
}

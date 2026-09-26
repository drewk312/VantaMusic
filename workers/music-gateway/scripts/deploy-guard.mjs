import { spawnSync } from "node:child_process";

function run(command, args, options = {}) {
  const result = spawnSync(command, args, { stdio: "inherit", shell: process.platform === "win32", ...options });
  if (result.status !== 0) process.exit(result.status ?? 1);
}

const status = spawnSync("git", ["-C", "../..", "status", "--porcelain", "--", "workers/music-gateway"], {
  encoding: "utf8",
  shell: process.platform === "win32",
});
if (status.status !== 0) process.exit(status.status ?? 1);
if (status.stdout.trim() && process.env.VANTA_ALLOW_DIRTY_GATEWAY !== "1") {
  console.error("Refusing production deploy: workers/music-gateway has uncommitted changes.");
  console.error("Commit the reviewed gateway change first, or set VANTA_ALLOW_DIRTY_GATEWAY=1 for an explicit emergency deploy.");
  process.exit(2);
}

run("npm", ["run", "typecheck"]);
run("npm", ["test"]);
run("npx", ["wrangler", "deploy", "--dry-run"]);
run("npx", ["wrangler", "deploy"]);
run("npm", ["run", "smoke:live"]);

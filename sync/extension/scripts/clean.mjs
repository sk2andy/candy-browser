import fs from "node:fs";

fs.rmSync(new URL("../dist", import.meta.url), { recursive: true, force: true });
fs.rmSync(new URL("../.test-build", import.meta.url), { recursive: true, force: true });

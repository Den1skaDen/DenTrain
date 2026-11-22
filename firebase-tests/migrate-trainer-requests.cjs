// Dry run by default. Pass the signed-in Firebase CLI package directory as the first argument.
const assert = require("node:assert/strict");
const { randomBytes } = require("node:crypto");
const path = require("node:path");

const DAY = 86_400_000;
const WEEK = 7 * DAY;
const project = "dentrain-9af07";
const database = `projects/${project}/databases/(default)`;
const documentsPath = `/v1/${database}/documents`;
const string = (value) => ({ stringValue: value });
const timestamp = (value) => ({ timestampValue: new Date(value).toISOString() });
const nullableTime = (value) => value == null ? { nullValue: null } : timestamp(value);
const uidPattern = /^[A-Za-z0-9_-]{1,128}$/;

function field(doc, key) {
  return doc?.fields?.[key]?.stringValue;
}

function documentId(doc) {
  return doc.name.split("/").at(-1);
}

async function listDocuments(client, collection) {
  const documents = [];
  let pageToken;
  do {
    const page = (await client.get(`${documentsPath}/${collection}`, {
      queryParams: { pageSize: "200", ...(pageToken ? { pageToken } : {}) },
    })).body;
    documents.push(...(page.documents || []));
    pageToken = page.nextPageToken;
  } while (pageToken);
  return documents;
}

function planMigration(requests, profiles, now) {
  const canonical = new Set(requests.filter((doc) => doc.fields?.lastSent?.timestampValue).map(documentId));
  const users = new Map(profiles.map((doc) => [documentId(doc), doc]));
  const groups = new Map();
  for (const doc of requests) {
    if (canonical.has(documentId(doc))) continue;
    const athlete = field(doc, "athleteUid");
    const key = uidPattern.test(athlete || "") ? athlete : doc.name;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(doc);
  }

  return [...groups].map(([athlete, docs]) => {
    const profile = users.get(athlete);
    const linkedTrainer = field(profile, "trainerUid");
    const pending = docs.filter((doc) => field(doc, "status") === "pending");
    const wasPrelinked = field(profile, "role") === "athlete" && linkedTrainer &&
      pending.some((doc) => field(doc, "trainerUid") === linkedTrainer);
    const writes = docs.map((doc) => ({
      delete: doc.name, currentDocument: { updateTime: doc.updateTime },
    }));
    if (wasPrelinked) writes.push({
      update: { name: profile.name, fields: { trainerUid: { nullValue: null }, trainerName: { nullValue: null } } },
      updateMask: { fieldPaths: ["trainerUid", "trainerName"] },
      currentDocument: { updateTime: profile.updateTime },
    });

    if (field(profile, "role") === "athlete" && (!linkedTrainer || wasPrelinked)) {
      const byTrainer = new Map();
      for (const doc of pending) {
        const trainer = field(doc, "trainerUid");
        const time = Number(doc.fields?.timestamp?.integerValue);
        if (uidPattern.test(trainer || "") && trainer !== athlete &&
            ["trainer", "admin"].includes(field(users.get(trainer), "role")) &&
            time > now - WEEK && time <= now + 300_000) {
          if (!byTrainer.has(trainer)) byTrainer.set(trainer, []);
          byTrainer.get(trainer).push(Math.min(time, now));
        }
      }
      for (const [trainer, times] of byTrainer) {
        const id = `${athlete}~${trainer}`;
        if (canonical.has(id)) continue;
        const [last, second, third] = times.sort((a, b) => b - a);
        writes.push({
          update: {
            name: `${database}/documents/trainer_requests/${id}`,
            fields: {
              athleteUid: string(athlete), athleteName: string(field(profile, "nickname")),
              trainerUid: string(trainer), trainerName: string(field(users.get(trainer), "nickname")),
              status: string("pending"), lastSent: timestamp(last), secondSent: nullableTime(second),
              thirdSent: nullableTime(third), expiresAt: timestamp(last + WEEK),
              eventId: string(randomBytes(10).toString("hex")),
            },
          },
          currentDocument: { exists: false },
        });
      }
    }
    assert(writes.length <= 500, "More than 500 writes for one athlete. Review this group before migrating.");
    return writes;
  });
}

async function main() {
  assert(process.argv[2], "Pass the Firebase CLI package directory");
  const cli = path.resolve(process.argv[2], "lib");
  const account = require(path.join(cli, "auth")).getGlobalDefaultAccount();
  assert(account, "Sign in to Firebase CLI first");
  await require(path.join(cli, "requireAuth")).requireAuth({ ...account, project });
  const { Client } = require(path.join(cli, "apiv2"));
  const firestore = new Client({ urlPrefix: "https://firestore.googleapis.com" });
  const [requests, profiles] = await Promise.all([
    listDocuments(firestore, "trainer_requests"), listDocuments(firestore, "profiles"),
  ]);
  const groups = planMigration(requests, profiles, Date.now());
  const writes = groups.flat();
  const removed = writes.filter((write) => write.delete).length;
  const merged = writes.filter((write) => write.currentDocument.exists === false).length;
  const reset = writes.filter((write) => write.updateMask).length;
  const apply = process.argv.includes("--apply");
  if (apply) {
    for (const batch of groups) await firestore.post(`${documentsPath}:commit`, { writes: batch });
  }
  console.log(`${apply ? "Applied" : "Dry run"}: ${removed} legacy rows, ${merged} pending cards, ${reset} premature links.`);
}

if (require.main === module) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}

module.exports = { planMigration };

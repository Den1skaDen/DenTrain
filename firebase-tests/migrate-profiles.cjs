// Run with the signed-in Firebase CLI package path, then add --apply after the rules are live.
const path = require("node:path");
const assert = require("node:assert/strict");

async function main() {
  const cli = path.resolve(process.argv[2], "lib");
  const project = "dentrain-9af07";
  const account = require(path.join(cli, "auth")).getGlobalDefaultAccount();
  assert(account, "Sign in to Firebase CLI first");
  await require(path.join(cli, "requireAuth")).requireAuth({ ...account, project });
  const { Client } = require(path.join(cli, "apiv2"));
  const firestore = new Client({ urlPrefix: "https://firestore.googleapis.com" });
  const identity = new Client({ urlPrefix: "https://identitytoolkit.googleapis.com" });
  const database = `projects/${project}/databases/(default)`;
  const documentsPath = `/v1/${database}/documents`;

  const owner = (await identity.post(`/v1/projects/${project}/accounts:lookup`, {
    email: [account.user.email],
  })).body.users?.[0];
  assert(owner?.emailVerified && !owner.disabled && owner.localId === "4vXNYEIu1vVbx2heIstfEV6qIbD2",
    "Approved DenTrain owner identity could not be verified");

  const legacy = (await firestore.get(`${documentsPath}/users`, { queryParams: { pageSize: "100" } })).body;
  const profiles = (await firestore.get(`${documentsPath}/profiles`, { queryParams: { pageSize: "100" } })).body;
  assert(!legacy.nextPageToken && !profiles.nextPageToken, "More than 100 profiles, review migration size");
  assert(!(profiles.documents?.length), "Destination profiles already exist, review before migrating");
  const writes = [];
  const uidOf = (document) => document.name.split("/").at(-1);
  const byUid = new Map((legacy.documents || []).map((doc) => [uidOf(doc), doc.fields]));
  if (!byUid.has(owner.localId)) byUid.set(owner.localId, {});
  const accounts = (await identity.post(`/v1/projects/${project}/accounts:lookup`, {
    localId: [...byUid.keys()],
  })).body.users || [];
  for (const user of accounts) {
    const uid = user.localId;
    const source = byUid.get(uid);
    if (user.disabled || !source) continue;
    const string = (key, maximum) => source[key]?.stringValue?.slice(0, maximum) || "";
    const fields = {
      nickname: { stringValue: string("nickname", 80).trim() || "Пользователь" },
      username: { stringValue: string("username", 40) },
      avatarBase64: { stringValue: (source.avatarBase64?.stringValue || "").length <= 400000
        ? string("avatarBase64", 400000) : "" },
      role: { stringValue: uid === owner.localId ? "admin" : "athlete" },
      dobEpochDay: { integerValue: source.dobEpochDay?.integerValue || "0" },
      trainerUid: { nullValue: null },
      trainerName: { nullValue: null },
    };
    for (const [key, maximum] of [["clubId", 128], ["clubName", 120], ["gender", 24]]) {
      if (source[key]?.stringValue) fields[key] = { stringValue: string(key, maximum) };
    }
    writes.push({ update: { name: `${database}/documents/profiles/${uid}`, fields },
      currentDocument: { exists: false } });
    if (user.email) {
      writes.push({
        update: { name: `${database}/documents/accounts/${uid}`, fields: { email: { stringValue: user.email } } },
        updateMask: { fieldPaths: ["email"] },
      });
    }
  }
  assert(writes.some((write) => write.update.fields.role?.stringValue === "admin"));
  if (process.argv.includes("--apply")) {
    const result = await firestore.post(`${documentsPath}:commit`, { writes });
    assert.equal(result.body.writeResults.length, writes.length);
    console.log(`Migrated ${writes.filter((write) => write.update.fields.role).length} profiles. Owner assigned admin.`);
  } else {
    console.log(`Dry run: ${writes.filter((write) => write.update.fields.role).length} profiles, one approved admin.`);
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});

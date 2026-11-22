import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { after, before, beforeEach, describe, test } from "node:test";
import assert from "node:assert/strict";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  Bytes,
  Timestamp,
  collection,
  deleteDoc,
  disableNetwork,
  doc,
  enableNetwork,
  getDoc,
  getDocFromCache,
  getDocs,
  query,
  onSnapshot,
  orderBy,
  limit,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} from "firebase/firestore";

const projectId = "demo-dentrain-rules-test";
const testDirectory = path.dirname(fileURLToPath(import.meta.url));
const rules = fs.readFileSync(path.resolve(testDirectory, "../firestore.rules"), "utf8");
let testEnv;

const validUser = {
  nickname: "Athlete",
  username: "athlete_1",
  role: "athlete",
  avatarBase64: "",
  dobEpochDay: 20000,
};

const validProfile = (role = "athlete", trainerUid = null) => ({
  nickname: role === "trainer" ? "Coach" : "Athlete",
  username: `${role}_1`,
  avatarBase64: "",
  role,
  dobEpochDay: 20000,
  clubId: null,
  clubName: null,
  trainerUid,
  trainerName: trainerUid ? "Coach" : null,
  gender: "unspecified",
});

const validRecord = (uid) => ({
  athleteUid: uid,
  exerciseName: "Bench press",
  weight: 100,
  reps: 5,
  dateEpochDay: 20000,
  comment: "",
  isFavorite: false,
  updatedAt: Date.now(),
});

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId,
    firestore: { host: "127.0.0.1", port: Number(process.env.FIRESTORE_TEST_PORT || 8080), rules },
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

after(async () => {
  await testEnv.cleanup();
});

const HOUR = 3_600_000;
const eventId = "ab012345678901234567";
const respawnId = "cd012345678901234567";
const workoutId = "wo012345678901234567";

function pendingRequest(overrides = {}) {
  return {
    athleteUid: "alice", athleteName: "Athlete", trainerUid: "trainer", trainerName: "Coach",
    status: "pending", lastSent: serverTimestamp(), secondSent: null, thirdSent: null,
    expiresAt: Timestamp.fromMillis(Date.now() + 7 * 24 * HOUR), eventId, ...overrides,
  };
}

function activity(event, senderUid, recipientUid, subjectId, subject) {
  const timestamp = Date.now();
  return {
    event, senderUid, recipientUid, subjectId, subject, timestamp, isRead: false,
    occurredAt: serverTimestamp(), expiresAt: Timestamp.fromMillis(timestamp + 90 * 24 * HOUR),
  };
}

async function seedCoaching(trainerUid = null) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "profiles/alice"), validProfile("athlete", trainerUid));
    await setDoc(doc(db, "profiles/trainer"), validProfile("trainer"));
    await setDoc(doc(db, "profiles/other"), validProfile("trainer"));
    await setDoc(doc(db, "profiles/admin"), validProfile("admin"));
  });
}

describe("coaching workflow", () => {
  test("confirms booking with an administrator only when the athlete sends the request", async () => {
    await seedCoaching();
    const athlete = testEnv.authenticatedContext("alice").firestore();
    const trainer = testEnv.authenticatedContext("trainer").firestore();
    const key = "alice~admin";
    const receipt = doc(athlete, "notifications", eventId);
    const confirmation = () => activity("request_sent", "alice", "alice", key, "Athlete");
    const incoming = doc(athlete, "notifications", `request_${eventId}`);
    const notice = () => activity("request_received", "alice", "admin", key, "Athlete");
    await assertFails(setDoc(receipt, confirmation()));
    await assertFails(setDoc(incoming, notice()));
    await assertFails(setDoc(doc(trainer, "trainer_requests", "trainer~admin"), pendingRequest({
      athleteUid: "trainer", athleteName: "Coach", trainerUid: "admin", trainerName: "Athlete",
    })));
    const batch = writeBatch(athlete);
    batch.set(doc(athlete, "trainer_requests", key), pendingRequest({
      trainerUid: "admin", trainerName: "Athlete",
    }));
    batch.set(receipt, confirmation());
    batch.set(incoming, notice());
    await assertSucceeds(batch.commit());
    await assertSucceeds(getDoc(receipt));
    await assertFails(getDoc(incoming));
    await assertSucceeds(getDoc(doc(testEnv.authenticatedContext("admin").firestore(),
      "notifications", incoming.id)));
    await assertFails(getDoc(doc(trainer, "notifications", eventId)));
    await assertSucceeds(updateDoc(receipt, { isRead: true }));
    await assertSucceeds(deleteDoc(receipt));
    await assertFails(setDoc(receipt, confirmation()));
  });

  test("shows a coach only their own assignments and the administrator all assignments", async () => {
    await seedCoaching("trainer");
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, "assigned_workouts/own"), {
        athleteUid: "alice", trainerUid: "trainer", status: "completed",
      });
      await setDoc(doc(db, "assigned_workouts/another"), {
        athleteUid: "alice", trainerUid: "other", status: "pending",
      });
    });
    const trainer = testEnv.authenticatedContext("trainer").firestore();
    const admin = testEnv.authenticatedContext("admin").firestore();
    const byAthlete = (db, ...filters) => query(collection(db, "assigned_workouts"),
      where("athleteUid", "==", "alice"), ...filters);
    const own = await assertSucceeds(getDocs(byAthlete(trainer, where("trainerUid", "==", "trainer"))));
    assert.deepEqual(own.docs.map((workout) => workout.id), ["own"]);
    await assertFails(getDoc(doc(trainer, "assigned_workouts/another")));
    await assertFails(getDocs(byAthlete(trainer)));
    const all = await assertSucceeds(getDocs(byAthlete(admin)));
    assert.equal(all.size, 2);
    await updateDoc(doc(testEnv.authenticatedContext("alice").firestore(), "profiles/alice"), {
      trainerUid: null, trainerName: null,
    });
    await assertFails(getDoc(doc(trainer, "assigned_workouts/own")));
    await assertFails(getDocs(byAthlete(trainer, where("trainerUid", "==", "trainer"))));
  });

  test("keeps one pending card and reserves the coach link for an atomic decision", async () => {
    await seedCoaching();
    const athlete = testEnv.authenticatedContext("alice").firestore();
    const trainer = testEnv.authenticatedContext("trainer").firestore();
    const other = testEnv.authenticatedContext("other").firestore();
    const key = "alice~trainer";
    await assertSucceeds(getDoc(doc(athlete, "trainer_requests", key)));
    await assertFails(setDoc(doc(athlete, "trainer_requests", "random"), pendingRequest()));
    await assertFails(setDoc(doc(athlete, "trainer_requests", key), pendingRequest({ athleteName: "Fake" })));
    await assertFails(updateDoc(doc(athlete, "profiles/alice"), { trainerUid: "trainer", trainerName: "Coach" }));
    await assertSucceeds(setDoc(doc(athlete, "trainer_requests", key), pendingRequest()));
    await assertFails(getDoc(doc(other, "trainer_requests", key)));
    await assertFails(deleteDoc(doc(athlete, "trainer_requests", key)));
    await assertFails(setDoc(doc(athlete, "trainer_requests", key), pendingRequest({ eventId: respawnId })));
    await assertFails(updateDoc(doc(trainer, "trainer_requests", key), { status: "accepted" }));
    await assertFails(updateDoc(doc(trainer, "profiles/alice"), { trainerUid: "trainer", trainerName: "Coach" }));
    const lastSent = (await getDoc(doc(athlete, "trainer_requests", key))).get("lastSent").toMillis();
    const expiresAt = Timestamp.fromMillis(Math.floor(lastSent) + 24 * HOUR + 60_000);

    const accept = () => {
      const batch = writeBatch(trainer);
      batch.update(doc(trainer, "profiles/alice"), { trainerUid: "trainer", trainerName: "Coach" });
      batch.update(doc(trainer, "trainer_requests", key), { status: "accepted", expiresAt });
      batch.set(doc(trainer, "notifications", `decision_${eventId}`), activity("accepted", "trainer", "alice", key, "Coach"));
      return batch.commit();
    };
    await assertSucceeds(accept());
    await assertFails(accept());
    const pending = await assertSucceeds(getDocs(query(collection(trainer, "trainer_requests"),
      where("trainerUid", "==", "trainer"), where("status", "==", "pending"))));
    assert.equal(pending.size, 0);
    assert.equal((await getDoc(doc(athlete, "profiles/alice"))).get("trainerUid"), "trainer");
    await assertSucceeds(getDoc(doc(athlete, "notifications", `decision_${eventId}`)));
  });

  test("retains the hourly and rolling daily limits after rejection", async () => {
    await seedCoaching();
    const athlete = testEnv.authenticatedContext("alice").firestore();
    const trainer = testEnv.authenticatedContext("trainer").firestore();
    const key = "alice~trainer";
    const last = Timestamp.fromMillis(Date.now() - 2 * HOUR);
    const second = Timestamp.fromMillis(Date.now() - 4 * HOUR);
    const third = Timestamp.fromMillis(Date.now() - 6 * HOUR);
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "trainer_requests", key),
        pendingRequest({ lastSent: last, secondSent: second, thirdSent: third }));
    });
    const reject = writeBatch(trainer);
    reject.update(doc(trainer, "trainer_requests", key), {
      status: "rejected", expiresAt: Timestamp.fromMillis(last.toMillis() + 24 * HOUR + 60_000),
    });
    reject.set(doc(trainer, "notifications", `decision_${eventId}`), activity("rejected", "trainer", "alice", key, "Coach"));
    await assertSucceeds(reject.commit());
    await assertFails(setDoc(doc(athlete, "trainer_requests", key),
      pendingRequest({ eventId: respawnId, secondSent: last, thirdSent: second })));
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await updateDoc(doc(context.firestore(), "trainer_requests", key),
        { thirdSent: Timestamp.fromMillis(Date.now() - 25 * HOUR) });
    });
    await assertSucceeds(setDoc(doc(athlete, "trainer_requests", key),
      pendingRequest({ eventId: respawnId, secondSent: last, thirdSent: second })));
    const sent = (await getDoc(doc(athlete, "trainer_requests", key))).get("lastSent").toMillis();
    const withdrawn = { status: "withdrawn", expiresAt: Timestamp.fromMillis(Math.floor(sent) + 24 * HOUR + 60_000) };
    await assertFails(updateDoc(doc(trainer, "trainer_requests", key), withdrawn));
    await assertSucceeds(updateDoc(doc(athlete, "trainer_requests", key), withdrawn));
    await assertFails(setDoc(doc(athlete, "trainer_requests", key), pendingRequest()));
  });

  test("assigns a workout with one inbox event and records arrival once", async () => {
    await seedCoaching("trainer");
    const athlete = testEnv.authenticatedContext("alice").firestore();
    const trainer = testEnv.authenticatedContext("trainer").firestore();
    const data = {
      trainerUid: "trainer", trainerName: "Coach", athleteUid: "alice", title: "Leg day",
      dateEpochDay: 20000, description: "", exercises: [], createdAt: Date.now(), status: "pending", deliveryVersion: 1,
    };
    const ref = doc(trainer, "assigned_workouts", workoutId);
    await assertFails(setDoc(ref, data));
    const send = writeBatch(trainer);
    send.set(ref, data);
    send.set(doc(trainer, "notifications", `assigned_${workoutId}`),
      activity("assigned", "trainer", "alice", workoutId, "Leg day"));
    await disableNetwork(trainer);
    const locallyQueued = new Promise((resolve, reject) => {
      const stop = onSnapshot(ref, { includeMetadataChanges: true }, (snapshot) => {
        if (snapshot.exists() && snapshot.metadata.hasPendingWrites) {
          stop();
          resolve();
        }
      }, reject);
    });
    const sendWhenOnline = send.commit();
    try {
      await locallyQueued;
      assert.equal((await getDocFromCache(ref)).get("title"), "Leg day");
      const athleteRef = doc(athlete, "assigned_workouts", workoutId);
      await testEnv.withSecurityRulesDisabled(async (context) => {
        assert.equal((await getDoc(doc(context.firestore(), "assigned_workouts", workoutId))).exists(), false);
      });
      await enableNetwork(trainer);
      await assertSucceeds(sendWhenOnline);
      assert.equal((await getDoc(athleteRef)).get("title"), "Leg day");
    } finally {
      await enableNetwork(trainer);
    }
    await assertFails(updateDoc(doc(athlete, "assigned_workouts", workoutId), { receivedAt: serverTimestamp() }));
    const receive = () => {
      const batch = writeBatch(athlete);
      batch.update(doc(athlete, "assigned_workouts", workoutId), { receivedAt: serverTimestamp() });
      batch.set(doc(athlete, "notifications", `received_${workoutId}`),
        activity("received", "alice", "trainer", workoutId, "Leg day"));
      return batch.commit();
    };
    await assertSucceeds(receive());
    await assertFails(receive());
    await assertSucceeds(getDoc(doc(trainer, "notifications", `received_${workoutId}`)));
    await assertFails(setDoc(doc(athlete, "notifications", "fabricated"),
      activity("received", "alice", "trainer", workoutId, "Leg day")));
  });

  test("records admin assignments and restricts audit access and edits", async () => {
    await seedCoaching();
    const admin = testEnv.authenticatedContext("admin").firestore();
    const trainer = testEnv.authenticatedContext("trainer").firestore();
    const audit = {
      actorUid: "admin", action: "workout_assigned", targetId: "alice", label: "Leg day",
      occurredAt: serverTimestamp(),
    };
    const ref = doc(admin, "admin_audit", `workout_${workoutId}`);
    await assertFails(setDoc(ref, audit));
    const send = writeBatch(admin);
    send.set(doc(admin, "assigned_workouts", workoutId), {
      trainerUid: "admin", trainerName: "Admin", athleteUid: "alice", title: "Leg day",
      dateEpochDay: 20000, description: "", exercises: [], createdAt: Date.now(), status: "pending", deliveryVersion: 1,
    });
    send.set(doc(admin, "notifications", `assigned_${workoutId}`), activity("assigned", "admin", "alice", workoutId, "Leg day"));
    send.set(ref, audit);
    await assertSucceeds(send.commit());
    await assertSucceeds(getDoc(ref));
    await assertFails(getDoc(doc(trainer, "admin_audit", `workout_${workoutId}`)));
    await assertFails(updateDoc(ref, { label: "Rewritten" }));
    await assertFails(deleteDoc(ref));
  });

  test("creates a club and membership together and requires an audit for administrative deletion", async () => {
    await seedCoaching();
    const trainer = testEnv.authenticatedContext("trainer").firestore();
    const admin = testEnv.authenticatedContext("admin").firestore();
    const trainerProfile = doc(trainer, "profiles/trainer");
    await assertFails(updateDoc(trainerProfile, { clubId: "club", clubName: "Forged" }));

    const create = writeBatch(trainer);
    create.set(doc(trainer, "clubs/club"), {
      name: "DenTrain", address: "", phone: "", description: "", ownerUid: "trainer",
    });
    create.update(trainerProfile, { clubId: "club", clubName: "DenTrain" });
    await assertSucceeds(create.commit());
    await assertFails(deleteDoc(doc(trainer, "clubs/club")));
    await assertFails(deleteDoc(doc(admin, "clubs/club")));
    await assertSucceeds(updateDoc(trainerProfile, { clubId: null, clubName: null }));

    const entry = doc(admin, "admin_audit", eventId);
    const batch = writeBatch(admin);
    batch.delete(doc(admin, "clubs/club"));
    batch.set(entry, {
      actorUid: "admin", action: "club_deleted", targetId: "club", label: "DenTrain",
      occurredAt: serverTimestamp(),
    });
    batch.set(doc(admin, "admin_audit_heads/club/targets/club"), {
      auditId: entry.id, action: "club_deleted", occurredAt: serverTimestamp(),
    });
    await assertSucceeds(batch.commit());
  });
});

describe("private diary snapshots", () => {
  test("rejects unauthenticated access", async () => {
    const db = testEnv.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, "accounts/alice/diary/settings")));
  });

  test("allows the owner to create and read a valid snapshot", async () => {
    const db = testEnv.authenticatedContext("alice").firestore();
    const ref = doc(db, "accounts/alice/diary/workout_123e4567-e89b-12d3-a456-426614174000");
    await assertSucceeds(setDoc(ref, {
      version: 1,
      payload: Bytes.fromUint8Array(new Uint8Array([1, 2, 3])),
      updatedAt: Timestamp.now(),
    }));
    await assertSucceeds(getDoc(ref));
  });

  test("rejects cross-account reads and writes", async () => {
    const db = testEnv.authenticatedContext("mallory").firestore();
    const ref = doc(db, "accounts/alice/diary/settings");
    await assertFails(getDoc(ref));
    await assertFails(setDoc(ref, {
      version: 1,
      payload: Bytes.fromUint8Array(new Uint8Array([1])),
      updatedAt: Timestamp.now(),
    }));
  });

  test("keeps an athlete's diary private even from their selected trainer", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "profiles/alice"), validProfile("athlete", "trainer"));
      await setDoc(doc(context.firestore(), "profiles/trainer"), validProfile("trainer"));
    });
    const owner = testEnv.authenticatedContext("alice").firestore();
    const payload = { version: 1, payload: Bytes.fromUint8Array(new Uint8Array([1])), updatedAt: Timestamp.now() };
    const diaryPath = "accounts/alice/diary";
    await assertSucceeds(setDoc(doc(owner, diaryPath, "settings"), payload));
    await assertSucceeds(getDocs(collection(owner, diaryPath)));
    for (const uid of ["trainer", "outsider"]) {
      const db = testEnv.authenticatedContext(uid).firestore();
      const settings = doc(db, diaryPath, "settings");
      await assertFails(getDoc(settings));
      await assertFails(getDocs(collection(db, diaryPath)));
      await assertFails(updateDoc(settings, { updatedAt: Timestamp.now() }));
      await assertFails(deleteDoc(settings));
    }
    await assertSucceeds(deleteDoc(doc(owner, diaryPath, "settings")));
  });

  test("rejects wrong IDs, versions, payload types and extra fields", async () => {
    const db = testEnv.authenticatedContext("alice").firestore();
    const base = {
      version: 1,
      payload: Bytes.fromUint8Array(new Uint8Array([1])),
      updatedAt: Timestamp.now(),
    };
    await assertFails(setDoc(doc(db, "accounts/alice/diary/other"), base));
    await assertFails(setDoc(doc(db, "accounts/alice/diary/workout_not-a-uuid"), base));
    await assertFails(setDoc(doc(db, "accounts/alice/diary/settings"), { ...base, version: 2 }));
    await assertFails(setDoc(doc(db, "accounts/alice/diary/settings"), { ...base, payload: "not-bytes" }));
    await assertFails(setDoc(doc(db, "accounts/alice/diary/settings"), { ...base, unexpected: true }));
  });

  test("rejects payloads larger than 950000 bytes", async () => {
    const db = testEnv.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(db, "accounts/alice/diary/settings"), {
      version: 1,
      payload: Bytes.fromUint8Array(new Uint8Array(950001)),
      updatedAt: Timestamp.now(),
    }));
  });
});

describe("legacy paths", () => {
  test("legacy users are readable only by the document owner", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "users/mallory"), {
        ...validUser,
        role: "admin",
      });
      await setDoc(doc(context.firestore(), "users/alice"), validUser);
    });
    const db = testEnv.authenticatedContext("mallory").firestore();
    await assertSucceeds(getDoc(doc(db, "users/mallory")));
    await assertFails(getDoc(doc(db, "users/alice")));
    await assertFails(getDocs(collection(db, "users")));
  });

  test("allows authenticated users to read profiles and profile admins to list them", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "profiles/admin"), validProfile("admin"));
      await setDoc(doc(context.firestore(), "profiles/alice"), validProfile());
    });
    const athleteDb = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(getDoc(doc(athleteDb, "profiles/admin")));
    const adminDb = testEnv.authenticatedContext("admin").firestore();
    await assertSucceeds(getDocs(collection(adminDb, "profiles")));
  });

  test("prevents self-promotion", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "profiles/alice"), validProfile());
    });
    const db = testEnv.authenticatedContext("alice").firestore();
    await assertFails(updateDoc(doc(db, "profiles/alice"), { role: "admin" }));
  });

  test("requires the same admin operation to record a role change", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "profiles/admin"), validProfile("admin"));
      await setDoc(doc(context.firestore(), "profiles/alice"), validProfile());
    });
    const db = testEnv.authenticatedContext("admin").firestore();
    const profile = doc(db, "profiles/alice");
    const entry = doc(db, "admin_audit", eventId);
    const head = doc(db, "admin_audit_heads/profile/targets/alice");
    await assertFails(updateDoc(profile, { role: "trainer" }));
    const batch = writeBatch(db);
    batch.update(profile, { role: "trainer" });
    batch.set(entry, {
      actorUid: "admin", action: "role_changed", targetId: "alice", label: "athlete → trainer",
      occurredAt: serverTimestamp(),
    });
    batch.set(head, { auditId: entry.id, action: "role_changed", occurredAt: serverTimestamp() });
    await assertSucceeds(batch.commit());
    await assertSucceeds(updateDoc(profile, { nickname: "Updated athlete", username: "updated_athlete" }));
    const auditQuery = (store) => query(collection(store, "admin_audit"),
      orderBy("occurredAt", "desc"), limit(50));
    const audit = await assertSucceeds(getDocs(auditQuery(db)));
    assert.equal(audit.docs[0].id, entry.id);
    const athleteDb = testEnv.authenticatedContext("alice").firestore();
    await assertFails(getDocs(auditQuery(athleteDb)));
    await assertFails(updateDoc(profile, { role: "athlete" }));
  });

  test("allows an athlete to create and query only their own records", async () => {
    const db = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(setDoc(doc(db, "records/alice_bench_10000"), validRecord("alice")));
    await assertSucceeds(getDocs(query(
      collection(db, "records"),
      where("athleteUid", "==", "alice"),
    )));
    await assertFails(setDoc(doc(db, "records/bob_bench_10000"), validRecord("bob")));
  });

  test("allows only the notification recipient to mark it read", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "notifications/n1"), {
        recipientUid: "alice",
        title: "Assigned",
        message: "Workout assigned",
        timestamp: Date.now(),
        isRead: false,
        type: "info",
      });
    });
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(updateDoc(doc(aliceDb, "notifications/n1"), { isRead: true }));
    await assertFails(updateDoc(doc(aliceDb, "notifications/n1"), { title: "Changed" }));
    const bobDb = testEnv.authenticatedContext("bob").firestore();
    await assertFails(updateDoc(doc(bobDb, "notifications/n1"), { isRead: true }));
  });

  test("allows legacy request reads and reserves changes for migration", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "profiles/alice"), validProfile("athlete", "trainer"));
      await setDoc(doc(context.firestore(), "profiles/trainer"), validProfile("trainer"));
      await setDoc(doc(context.firestore(), "trainer_requests/r1"), {
        trainerUid: "trainer",
        trainerName: "Coach",
        athleteUid: "alice",
        athleteName: "Alice",
        status: "pending",
        timestamp: Date.now(),
      });
    });
    const fakeTrainerDb = testEnv.authenticatedContext("trainer").firestore();
    await assertSucceeds(getDoc(doc(fakeTrainerDb, "trainer_requests/r1")));
    await assertFails(updateDoc(doc(fakeTrainerDb, "trainer_requests/r1"), { status: "accepted" }));
  });

  test("allows the attached trainer to clear only their own student link", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "profiles/alice"), validProfile("athlete", "trainer"));
      await setDoc(doc(context.firestore(), "profiles/trainer"), validProfile("trainer"));
    });
    const trainerDb = testEnv.authenticatedContext("trainer").firestore();
    await assertSucceeds(updateDoc(doc(trainerDb, "profiles/alice"), {
      trainerUid: null,
      trainerName: null,
    }));
    await assertFails(updateDoc(doc(trainerDb, "profiles/alice"), { nickname: "Changed" }));
  });

  test("allows trainers to manage only clubs they own", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "profiles/trainer"), validProfile("trainer"));
      await setDoc(doc(context.firestore(), "profiles/otherTrainer"), validProfile("trainer"));
    });
    const ownedClub = {
      name: "DenTrain Club",
      address: "Main street",
      phone: "+10000000000",
      description: "Training club",
      ownerUid: "trainer",
    };
    const trainerDb = testEnv.authenticatedContext("trainer").firestore();
    await assertSucceeds(setDoc(doc(trainerDb, "clubs/owned"), ownedClub));
    await assertSucceeds(updateDoc(doc(trainerDb, "clubs/owned"), { description: "Updated" }));
    const otherDb = testEnv.authenticatedContext("otherTrainer").firestore();
    await assertFails(updateDoc(doc(otherDb, "clubs/owned"), { description: "Hijacked" }));
    await assertFails(setDoc(doc(otherDb, "clubs/forged"), ownedClub));
  });

  test("shares records only with the athlete's selected trainer", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "profiles/alice"), validProfile("athlete", "trainer"));
      await setDoc(doc(context.firestore(), "profiles/trainer"), validProfile("trainer"));
      await setDoc(doc(context.firestore(), "profiles/otherTrainer"), validProfile("trainer"));
      await setDoc(doc(context.firestore(), "records/alice_bench_10000"), validRecord("alice"));
    });
    const trainerDb = testEnv.authenticatedContext("trainer").firestore();
    const shared = doc(trainerDb, "records/alice_bench_10000");
    await assertSucceeds(getDoc(shared));
    await assertSucceeds(getDocs(query(collection(trainerDb, "records"), where("athleteUid", "==", "alice"))));
    await assertFails(updateDoc(shared, { reps: 99 }));
    await assertFails(deleteDoc(shared));
    const otherDb = testEnv.authenticatedContext("otherTrainer").firestore();
    await assertFails(getDoc(doc(otherDb, "records/alice_bench_10000")));
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await updateDoc(doc(context.firestore(), "profiles/alice"), { trainerUid: null, trainerName: null });
    });
    await assertFails(getDoc(shared));
  });

  test("allows the selected trainer to query only their athletes' completed workouts", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, "profiles/alice"), validProfile("athlete", "trainer"));
      await setDoc(doc(db, "profiles/bob"), validProfile("athlete", "otherTrainer"));
      await setDoc(doc(db, "profiles/trainer"), validProfile("trainer"));
    });
    for (const uid of ["alice", "bob"]) {
      const db = testEnv.authenticatedContext(uid).firestore();
      await assertSucceeds(setDoc(doc(db, "completed_workouts", uid), {
        athleteUid: uid, title: "Training", dateEpochDay: 20000, dayOfWeek: 5,
        description: "", exercises: [], createdAt: Date.now(),
      }));
    }
    const workoutsFor = (db, uid) => query(collection(db, "completed_workouts"), where("athleteUid", "==", uid));
    const trainer = testEnv.authenticatedContext("trainer").firestore();
    const visible = await assertSucceeds(getDocs(workoutsFor(trainer, "alice")));
    assert.deepEqual(visible.docs.map((row) => row.id), ["alice"]);
    await assertFails(getDocs(workoutsFor(trainer, "bob")));
    await assertFails(getDocs(collection(trainer, "completed_workouts")));
    const outsider = testEnv.authenticatedContext("outsider").firestore();
    await assertFails(getDocs(workoutsFor(outsider, "alice")));
  });

  test("keeps account email private", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "accounts/alice"), { email: "alice@example.com" });
    });
    const aliceDb = testEnv.authenticatedContext("alice", { email: "alice@example.com" }).firestore();
    await assertSucceeds(getDoc(doc(aliceDb, "accounts/alice")));
    const bobDb = testEnv.authenticatedContext("bob", { email: "bob@example.com" }).firestore();
    await assertFails(getDoc(doc(bobDb, "accounts/alice")));
  });

  test("denies unknown collections", async () => {
    const db = testEnv.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(db, "unknown/x"), { value: true }));
    await assertFails(deleteDoc(doc(db, "unknown/x")));
  });
});

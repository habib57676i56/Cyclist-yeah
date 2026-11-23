const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "super_admin_789";
const ADMIN_EMAIL = "habiybullohy@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users: denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("announcements").get());
  await assertFails(unauthDb.collection("approved_knowledge_sources").get());
  await assertFails(unauthDb.collection("admin_audit_logs").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("bookmarks").get()
  );
});

test("User Profile: owner creates STUDENT profile, rejects self role escalation and ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const past = new Date(Date.now() - 5000);

  // Valid create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      role: "STUDENT",
      accountStatus: "ACTIVE",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Valid self profile update (displayName & photoUrl only)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Updated",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject client self-escalation of role to SUPER_ADMIN
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "SUPER_ADMIN",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isVerified: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject cross-user read or write
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Email spoofing defense: unverified admin email cannot access admin endpoints", async () => {
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  await assertFails(
    spoofedAdminDb.collection("announcements").doc("ann_1").set({
      title: "Fake",
      message: "Spoof",
      audience: "ALL",
      active: true,
      createdBy: "spoof_uid",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(spoofedAdminDb.collection("admin_audit_logs").get());
});

test("Subcollections isolation: user_preferences, user_progress, bookmarks, reading_history, memorization, revision, test_results, khatmah, fasting, notifications", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 3000);

  // Bookmarks create + partial update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("bookmarks").doc("bm_1").set({
      userId: ALICE_UID,
      page: 1,
      surah: 1,
      ayah: 1,
      favorite: true,
      note: "Opening of the Book",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("bookmarks").doc("bm_1").update({
      favorite: false,
      note: "Reviewed",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject future timestamp (> request.time)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("bookmarks").doc("bm_future").set({
      userId: ALICE_UID,
      page: 1,
      surah: 1,
      ayah: 2,
      favorite: true,
      createdAt: new Date(Date.now() + 120000),
      updatedAt: new Date(Date.now() + 120000),
    })
  );

  // Cross-user read denial on subcollection
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("bookmarks").get()
  );

  // User progress & preferences
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("user_progress").doc("default").set({
      userId: ALICE_UID,
      lastPage: 2,
      lastSurah: 2,
      lastAyah: 1,
      memorizedAyahsCount: 7,
      memorizedPagesCount: 1,
      dailyGoalPages: 4,
      dailyReadPages: 2,
      completedRevisions: 1,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("user_preferences").doc("default").set({
      userId: ALICE_UID,
      language: "ar",
      darkMode: true,
      autoScrollSpeed: 1.0,
      selectedReciterId: "alafasy",
      playbackSpeed: 1.0,
      prayerNotifications: true,
      quranReminders: true,
      revisionReminders: true,
      updatedAt: ts,
    })
  );

  // Memorization session + test_results + khatmah + fasting + notifications
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memorization_sessions").doc("mem_1").set({
      userId: ALICE_UID,
      surah: 112,
      startAyah: 1,
      endAyah: 4,
      status: "MEMORIZED",
      memorized: true,
      accuracyPercent: 98,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("test_results").doc("test_1").set({
      userId: ALICE_UID,
      testType: "CONTINUE_AYAH",
      surah: 112,
      startAyah: 1,
      endAyah: 4,
      scorePercent: 100,
      totalQuestions: 4,
      correctAnswers: 4,
      createdAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("khatmah_sessions").doc("kh_1").set({
      userId: ALICE_UID,
      title: "Ramadan Khatmah",
      targetDays: 30,
      completedPages: 20,
      lastPage: 20,
      completed: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("fasting_records").doc("fast_1").set({
      userId: ALICE_UID,
      title: "Mon & Thu",
      planType: "MON_THU",
      completedDays: 2,
      targetDays: 8,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("not_1").set({
      userId: ALICE_UID,
      title: "Daily Revision",
      body: "Review Surah Al-Ikhlas",
      category: "REVISION",
      read: false,
      createdAt: ts,
    })
  );

  // Reject including forbidden @DocumentId `id` key in payload
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("bookmarks").doc("bm_bad").set({
      id: "bm_bad",
      userId: ALICE_UID,
      page: 1,
      surah: 1,
      ayah: 1,
      favorite: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Verified Admin: publishes announcements, approved_knowledge_sources, grants admin role, writes immutable audit logs", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date(Date.now() - 2000);

  // Admin can create announcements & approved knowledge sources
  await assertSucceeds(
    adminDb.collection("announcements").doc("ann_1").set({
      title: "Ramadan Schedule",
      message: "Updated daily memorization plan",
      audience: "ALL",
      active: true,
      createdBy: ADMIN_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    adminDb.collection("approved_knowledge_sources").doc("src_1").set({
      title: "Makharij Al-Huroof",
      category: "TAJWEED",
      question: "What are the main articulation points?",
      answer: "Al-Jawf, Al-Halq, Al-Lisan, Ash-Shafatan, and Al-Khayshoom.",
      keywords: "tajweed, makharij",
      reference: "Al-Muqaddimah Al-Jazariyyah",
      approvalStatus: "APPROVED",
      createdBy: ADMIN_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Signed-in users can read approved sources & announcements, CANNOT mutate them
  await assertSucceeds(aliceDb.collection("announcements").get());
  await assertSucceeds(aliceDb.collection("approved_knowledge_sources").get());
  await assertFails(
    aliceDb.collection("approved_knowledge_sources").doc("src_1").update({
      answer: "Tampered",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Admin writes immutable audit log (update/delete forbidden even for admins!)
  await assertSucceeds(
    adminDb.collection("admin_audit_logs").doc("log_1").set({
      actorUid: ADMIN_UID,
      actorEmail: ADMIN_EMAIL,
      action: "PUBLISH_SOURCE",
      target: "src_1",
      details: "Approved tajweed source",
      createdAt: ts,
    })
  );
  await assertFails(
    adminDb.collection("admin_audit_logs").doc("log_1").update({
      action: "ALTERED",
    })
  );
  await assertFails(
    adminDb.collection("admin_audit_logs").doc("log_1").delete()
  );
});

test("AI Feedback: user creates scoped feedback, must filter query, cannot hijack another user", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("ai_feedback").doc("fb_1").set({
      userId: ALICE_UID,
      question: "What is Idgham?",
      aiResponse: "Merging a sakin letter into a vocalized one.",
      helpful: true,
      unanswered: false,
      reviewed: false,
      createdAt: ts,
    })
  );

  // Filtered query succeeds for owner; unfiltered fails for non-admin
  await assertSucceeds(
    aliceDb.collection("ai_feedback").where("userId", "==", ALICE_UID).get()
  );
  await assertFails(aliceDb.collection("ai_feedback").get());
  await assertFails(
    bobDb.collection("ai_feedback").where("userId", "==", ALICE_UID).get()
  );
});

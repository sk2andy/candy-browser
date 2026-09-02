import assert from "node:assert/strict";
import test from "node:test";

import {
  createRecoveryEnvelope,
  decryptDeviceName,
  decryptDeviceIcon,
  decryptTabSnapshot,
  deriveDeviceIconDescriptor,
  encryptDeviceIcon,
  encryptDeviceName,
  encryptTabSnapshot,
  fingerprintDeviceKey,
  generateDeviceIdentity,
  randomBytes,
  unlockRecoveryEnvelope,
} from "../src/crypto/crypto.js";
import { base64UrlToBytes, bytesToBase64Url, utf8 } from "../src/crypto/encoding.js";
import { CandySyncApiClient } from "../src/protocol/api-client.js";

const endpoint = process.env.CANDY_SYNC_E2E_URL;
const username = process.env.CANDY_SYNC_E2E_USERNAME ?? "candy";
const password = process.env.CANDY_SYNC_E2E_PASSWORD ?? "integration-password-123";

test("real extension crypto survives server enroll, idempotent retry, revision advance, and pull", { skip: !endpoint }, async () => {
  const api = new CandySyncApiClient(endpoint!);
  const discovery = await api.discover();
  assert.equal(discovery.protocol, "candy-sync");
  const bootstrap = await api.bootstrap(username, password);
  assert.equal(bootstrap.initialized, false);

  const passphrase = utf8("integration-only-recovery-passphrase");
  const workspaceKey = randomBytes(32);
  const identity = await generateDeviceIdentity();
  try {
	let secondDevice: { deviceId: string; token: string } | undefined;
    const recoveryEnvelope = await createRecoveryEnvelope(passphrase, workspaceKey, bootstrap.workspaceId, bootstrap.kdf);
    const encryptedName = await encryptDeviceName(workspaceKey, bootstrap.workspaceId, identity.fingerprint, "E2E Device");
    const icon = deriveDeviceIconDescriptor("Mozilla/5.0 (X11; Linux x86_64)", identity.fingerprint);
    const encryptedIcon = await encryptDeviceIcon(workspaceKey, bootstrap.workspaceId, identity.fingerprint, icon);
    const enrolled = await api.enroll(username, password, {
      deviceName: encryptedName,
      deviceIcon: encryptedIcon,
      deviceKeyFingerprint: identity.fingerprint,
      publicKey: bytesToBase64Url(identity.publicKeySpki),
      capabilities: ["tabs"],
      recoveryEnvelope,
    });
    assert.equal(enrolled.workspaceId, bootstrap.workspaceId);

    const afterEnrollment = await api.bootstrap(username, password);
    assert.equal(afterEnrollment.initialized, true);
    assert.deepEqual(afterEnrollment.recoveryEnvelope, recoveryEnvelope);

    const recoveredWorkspaceKey = await unlockRecoveryEnvelope(
      passphrase,
      afterEnrollment.recoveryEnvelope!,
      afterEnrollment.workspaceId,
      afterEnrollment.kdf,
    );
    const secondIdentity = await generateDeviceIdentity();
    try {
      assert.deepEqual(recoveredWorkspaceKey, workspaceKey);
      const secondName = await encryptDeviceName(
        recoveredWorkspaceKey,
        afterEnrollment.workspaceId,
        secondIdentity.fingerprint,
        "Recovered E2E Device",
      );
      const secondIcon = deriveDeviceIconDescriptor("Mozilla/5.0 (X11; CrOS x86_64)", secondIdentity.fingerprint);
      const secondEncryptedIcon = await encryptDeviceIcon(recoveredWorkspaceKey, afterEnrollment.workspaceId, secondIdentity.fingerprint, secondIcon);
      const enrolledSecondDevice = await api.enroll(username, password, {
        deviceName: secondName,
        deviceIcon: secondEncryptedIcon,
        deviceKeyFingerprint: secondIdentity.fingerprint,
        publicKey: bytesToBase64Url(secondIdentity.publicKeySpki),
        capabilities: ["tabs"],
      });
      secondDevice = enrolledSecondDevice;
      assert.notEqual(enrolledSecondDevice.deviceId, enrolled.deviceId);
      const devices = await api.listDevices(enrolled.token);
      assert.equal(devices.length, 2);
      const firstRecord = devices.find((device) => device.deviceId === enrolled.deviceId);
      const secondRecord = devices.find((device) => device.deviceId === enrolledSecondDevice.deviceId);
      assert.ok(firstRecord?.encryptedIcon);
      assert.ok(secondRecord?.encryptedIcon);
      const firstFingerprint = await fingerprintDeviceKey(base64UrlToBytes(firstRecord.publicKey));
      const secondFingerprint = await fingerprintDeviceKey(base64UrlToBytes(secondRecord.publicKey));
      assert.equal(await decryptDeviceName(workspaceKey, bootstrap.workspaceId, firstFingerprint, firstRecord.encryptedName), "E2E Device");
      assert.equal(await decryptDeviceName(workspaceKey, bootstrap.workspaceId, secondFingerprint, secondRecord.encryptedName), "Recovered E2E Device");
      await assert.rejects(decryptDeviceName(workspaceKey, bootstrap.workspaceId, secondFingerprint, firstRecord.encryptedName));
      assert.deepEqual(await decryptDeviceIcon(workspaceKey, bootstrap.workspaceId, firstFingerprint, firstRecord.encryptedIcon), icon);
      assert.deepEqual(await decryptDeviceIcon(workspaceKey, bootstrap.workspaceId, secondFingerprint, secondRecord.encryptedIcon), secondIcon);
      await assert.rejects(decryptDeviceIcon(workspaceKey, bootstrap.workspaceId, secondFingerprint, firstRecord.encryptedIcon));
    } finally {
      recoveredWorkspaceKey.fill(0);
      secondIdentity.privateKeyPkcs8.fill(0);
      secondIdentity.publicKeySpki.fill(0);
    }

    const first = await encryptTabSnapshot(workspaceKey, {
      changeId: "e2e_change_1",
      deviceId: enrolled.deviceId,
      entity: "tabs",
      entityId: enrolled.deviceId,
      operation: "snapshot",
      baseRevision: "0",
      schemaVersion: 1,
      cryptoVersion: 1,
      keyVersion: 1,
    }, { tabs: [{ url: "https://plaintext-canary.invalid/", title: "PLAINTEXT_SYNC_CANARY" }] });
    const pushed = await api.push(enrolled.token, first);
    assert.equal(pushed.revisions[first.changeId], "1");
    const retried = await api.push(enrolled.token, first);
    assert.equal(retried.revisions[first.changeId], "1");

    const second = await encryptTabSnapshot(workspaceKey, {
      changeId: "e2e_change_2",
      deviceId: enrolled.deviceId,
      entity: "tabs",
      entityId: enrolled.deviceId,
      operation: "snapshot",
      baseRevision: "1",
      schemaVersion: 1,
      cryptoVersion: 1,
      keyVersion: 1,
    }, { tabs: [{ url: "https://second-canary.invalid/", title: "SECOND_SYNC_CANARY" }] });
    const pushedSecond = await api.push(enrolled.token, second);
    assert.equal(pushedSecond.revisions[second.changeId], "2");
	assert.ok(secondDevice);
	const remoteEdit = await encryptTabSnapshot(workspaceKey, {
	  changeId: "e2e_android_edits_desktop",
	  deviceId: secondDevice.deviceId,
	  entity: "tabs",
	  entityId: enrolled.deviceId,
	  operation: "snapshot",
	  baseRevision: "2",
	  schemaVersion: 1,
	  cryptoVersion: 1,
	  keyVersion: 1,
	}, { tabs: [{ url: "https://remote-edit-canary.invalid/", title: "REMOTE_EDIT_CANARY" }] });
	const remoteResult = await api.putTabSnapshot(secondDevice.token, enrolled.deviceId, remoteEdit);
	assert.equal(remoteResult.revision, "3");

    const firstPage = await api.pull(enrolled.token);
	assert.equal(firstPage.changes.length, 3);
	assert.equal(firstPage.hasMore, false);
    assert.deepEqual(await decryptTabSnapshot(workspaceKey, firstPage.changes[0]!), {
      tabs: [{ url: "https://plaintext-canary.invalid/", title: "PLAINTEXT_SYNC_CANARY" }],
    });
	assert.deepEqual(await decryptTabSnapshot(workspaceKey, firstPage.changes[1]!), {
      tabs: [{ url: "https://second-canary.invalid/", title: "SECOND_SYNC_CANARY" }],
    });
	assert.equal(firstPage.changes[2]!.deviceId, secondDevice.deviceId);
	assert.equal(firstPage.changes[2]!.entityId, enrolled.deviceId);
	assert.deepEqual(await decryptTabSnapshot(workspaceKey, firstPage.changes[2]!), {
	  tabs: [{ url: "https://remote-edit-canary.invalid/", title: "REMOTE_EDIT_CANARY" }],
	});
  } finally {
    passphrase.fill(0);
    workspaceKey.fill(0);
    identity.privateKeyPkcs8.fill(0);
    identity.publicKeySpki.fill(0);
  }
});

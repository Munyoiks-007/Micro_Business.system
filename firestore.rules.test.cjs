const fs = require('node:fs');
const path = require('node:path');
const { after, before, test } = require('node:test');
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require('@firebase/rules-unit-testing');
const { deleteDoc, doc, getDoc, setDoc, updateDoc } = require('firebase/firestore');

const projectId = 'demo-microbusinesssystem';

async function runRulesSuite(label, rulesFile, transitional) {
  let environment;

  before(async () => {
    environment = await initializeTestEnvironment({
      projectId,
      firestore: {
        rules: fs.readFileSync(path.join(__dirname, rulesFile), 'utf8'),
      },
    });
  });

  after(async () => {
    await environment?.cleanup();
  });

  test(`${label}: owner access and foreign/unauthenticated access`, async () => {
    await environment.clearFirestore();
    const alice = environment.authenticatedContext('alice').firestore();
    const bob = environment.authenticatedContext('bob').firestore();
    const unauthenticated = environment.unauthenticatedContext().firestore();
    const aliceItem = doc(alice, 'users/alice/inventory/item-1');
    const bobItem = doc(bob, 'users/alice/inventory/item-1');

    await assertSucceeds(setDoc(aliceItem, { name: 'Tea', ownerUid: 'alice' }));
    await assertSucceeds(getDoc(aliceItem));
    await assertFails(setDoc(doc(alice, 'users/alice/inventory/missing-owner'), { name: 'No owner' }));
    await assertFails(setDoc(doc(alice, 'users/alice/inventory/wrong-owner'), {
      name: 'Wrong owner', ownerUid: 'bob',
    }));
    await assertFails(updateDoc(aliceItem, { ownerUid: 'bob' }));
    await assertFails(getDoc(bobItem));
    await assertFails(updateDoc(bobItem, { name: 'Changed' }));
    await assertFails(deleteDoc(bobItem));
    await assertFails(setDoc(doc(bob, 'users/alice/inventory/foreign'), {
      name: 'Foreign', ownerUid: 'alice',
    }));
    await assertFails(getDoc(doc(unauthenticated, 'users/alice/inventory/item-1')));
  });

  test(`${label}: sales ownership and ownerUid validation`, async () => {
    await environment.clearFirestore();
    const alice = environment.authenticatedContext('alice').firestore();
    const aliceSale = doc(alice, 'users/alice/sales/sale-1');

    await assertSucceeds(setDoc(aliceSale, { amount: 500, ownerUid: 'alice' }));
    await assertFails(setDoc(doc(alice, 'users/alice/sales/missing-owner'), { amount: 500 }));
    await assertFails(setDoc(doc(alice, 'users/alice/sales/wrong-owner'), {
      amount: 500, ownerUid: 'bob',
    }));
    await assertFails(updateDoc(aliceSale, { ownerUid: 'bob' }));
    await assertFails(updateDoc(aliceSale, { ownerUid: null }));
  });

  test(`${label}: profile path remains tied to profile uid`, async () => {
    await environment.clearFirestore();
    const alice = environment.authenticatedContext('alice').firestore();
    const bob = environment.authenticatedContext('bob').firestore();
    const aliceProfile = doc(alice, 'users/alice');

    await assertSucceeds(setDoc(aliceProfile, {
      uid: 'alice', email: 'alice@example.test', businessName: 'A',
    }));
    await assertSucceeds(updateDoc(aliceProfile, { businessName: 'A Store' }));
    await assertFails(updateDoc(aliceProfile, { uid: 'bob' }));
    await assertFails(getDoc(doc(bob, 'users/alice')));
  });

  test(`${label}: legacy inventory and sales behavior`, async () => {
    await environment.clearFirestore();
    await environment.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'users/alice/inventory/legacy-item'), { name: 'Old item' });
      await setDoc(doc(db, 'users/alice/sales/legacy-sale'), { amount: 200 });
    });

    const alice = environment.authenticatedContext('alice').firestore();
    const bob = environment.authenticatedContext('bob').firestore();
    const legacyItem = doc(alice, 'users/alice/inventory/legacy-item');
    const legacySale = doc(alice, 'users/alice/sales/legacy-sale');

    if (transitional) {
      await assertSucceeds(getDoc(legacyItem));
      await assertSucceeds(getDoc(legacySale));
      await assertFails(getDoc(doc(bob, 'users/alice/inventory/legacy-item')));
      await assertSucceeds(updateDoc(legacyItem, { ownerUid: 'alice' }));
      await assertSucceeds(updateDoc(legacySale, { ownerUid: 'alice' }));
    } else {
      await assertFails(getDoc(legacyItem));
      await assertFails(getDoc(legacySale));
      await assertFails(updateDoc(legacyItem, { ownerUid: 'alice' }));
      await assertFails(updateDoc(legacySale, { ownerUid: 'alice' }));
    }
  });
}

runRulesSuite('Transitional rules', 'firestore.rules', true);
runRulesSuite('Final strict rules', 'firestore.strict.rules', false);

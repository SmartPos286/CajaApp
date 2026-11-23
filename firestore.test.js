const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_pos_owner";
const BOB_UID = "bob_other_store";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

function futureDate(msOffset = 60000) {
  return new Date(Date.now() + msOffset);
}

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

// --- PAYLOAD FACTORIES MIRRORING KOTLIN DTOs ---

function makeUserAccount(uid, ts = pastDate()) {
  return {
    userId: uid,
    email: "tienda@gmail.com",
    displayName: "Mi Tienda POS",
    lastActiveAt: ts,
    createdAt: ts,
    updatedAt: ts,
  };
}

function makeCompanyProfile(uid, ts = pastDate()) {
  return {
    userId: uid,
    googleEmail: "tienda@gmail.com",
    storeName: "Comercial Central C.A.",
    storeRif: "J-12345678-9",
    storePhone: "04141234567",
    storeAddress: "Av. Principal, Local 4",
    bcvRate: 52.45,
    minProfitMarginEnabled: true,
    minProfitMarginPercent: 30.0,
    cashRegisterCommissionEnabled: false,
    commissionEnabledRegisters: "1",
    createdAt: ts,
    updatedAt: ts,
  };
}

function makeProduct(uid, ts = pastDate()) {
  return {
    userId: uid,
    code: "7591234567890",
    name: "Harina de Maíz 1Kg",
    category: "Víveres",
    priceUsd: 1.5,
    stock: 25.0,
    costPriceUsd: 1.05,
    invoiceNumber: "FAC-001",
    taxExempt: true,
    hasCommission: false,
    commissionPercentage: 0.0,
    commissionAmountUsd: 0.0,
    recipe: false,
    combo: false,
    comboItemsJson: "",
    allowDecimalQuantity: false,
    priceModifiable: false,
    wholesalePriceUsd: 1.3,
    aisleNumber: "A1",
    aisleQuantity: 10.0,
    depotQuantity: 10.0,
    counterQuantity: 5.0,
    createdAt: ts,
    updatedAt: ts,
  };
}

function makeSale(uid, ts = pastDate()) {
  return {
    userId: uid,
    customerName: "Cliente Contado",
    customerDocument: "V-12345678",
    timestampMs: Date.now() - 1000,
    rateBcv: 52.45,
    totalUsd: 15.0,
    totalBs: 786.75,
    paymentCashUsd: 10.0,
    paymentMobileBs: 0.0,
    paymentCardBs: 0.0,
    paymentCreditUsd: 0.0,
    paymentCasheaUsd: 5.0,
    creditPaid: true,
    creditPaidAmountUsd: 0.0,
    creditDueDate: 0,
    creditNotes: "",
    igtfUsd: 0.3,
    igtfBs: 15.74,
    baseTaxableUsd: 0.0,
    baseExemptUsd: 14.7,
    ivaUsd: 0.0,
    discountUsd: 0.0,
    cashierName: "Admin",
    cashRegisterNumber: "Caja 1",
    totalCommissionUsd: 0.0,
    retencionIvaUsd: 0.0,
    retencionIvaPercentage: 0.0,
    retencionIslrUsd: 0.0,
    retencionIslrPercentage: 0.0,
    withholdingVoucherNumber: "",
    withholdingStatus: "NINGUNA",
    deviceId: "device_1",
    createdAt: ts,
    updatedAt: ts,
  };
}

function makeSaleItem(uid, saleCloudId = "sale_1", productCloudId = "prod_1", ts = pastDate()) {
  return {
    userId: uid,
    saleCloudId,
    productCloudId,
    productName: "Harina de Maíz 1Kg",
    quantity: 2.0,
    priceUsd: 1.5,
    taxExempt: true,
    commissionUsd: 0.0,
    createdAt: ts,
    updatedAt: ts,
  };
}

function makeCustomer(uid, ts = pastDate()) {
  return {
    userId: uid,
    name: "María Pérez",
    documentId: "V-20111222",
    address: "Calle 5",
    phone: "04125556677",
    clientType: "ORDINARIO",
    withholdingAgent: false,
    ivaRetentionPercentage: 0.0,
    islrRetentionPercentage: 0.0,
    createdAt: ts,
    updatedAt: ts,
  };
}

function makeQuote(uid, ts = pastDate()) {
  return {
    userId: uid,
    quoteNumber: "PRE-0001",
    customerName: "Empresa XYZ",
    customerDocument: "J-99988877-1",
    customerPhone: "04241112233",
    timestampMs: Date.now() - 1000,
    validityDays: 15,
    validUntil: Date.now() + 15 * 86400000,
    rateBcv: 52.45,
    baseTaxableUsd: 100.0,
    baseExemptUsd: 0.0,
    ivaUsd: 16.0,
    discountUsd: 0.0,
    totalUsd: 116.0,
    totalBs: 6084.2,
    notesOrConditions: "Precios sujetos a tasa BCV",
    status: "VIGENTE",
    cashierName: "Admin",
    cashRegisterNumber: "Caja 1",
    createdAt: ts,
    updatedAt: ts,
  };
}

function makeQuoteItem(uid, quoteCloudId = "quote_1", productCloudId = "prod_1", ts = pastDate()) {
  return {
    userId: uid,
    quoteCloudId,
    productCloudId,
    productName: "Harina de Maíz 1Kg",
    quantity: 5.0,
    priceUsd: 1.5,
    taxExempt: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

function makeDeliveryNote(uid, ts = pastDate()) {
  return {
    userId: uid,
    noteNumber: "NE-0001",
    customerName: "Distribuidora Norte",
    customerDocument: "J-55544433-2",
    recipientName: "Carlos Gómez",
    reasonOrObservation: "Despacho por encomienda",
    timestampMs: Date.now() - 1000,
    totalEstimatedUsd: 45.0,
    totalEstimatedBs: 2360.25,
    rateBcv: 52.45,
    status: "ENTREGADO",
    cashierName: "Admin",
    cashRegisterNumber: "Caja 1",
    createdAt: ts,
    updatedAt: ts,
  };
}

function makeDeliveryNoteItem(uid, deliveryNoteCloudId = "dn_1", productCloudId = "prod_1", ts = pastDate()) {
  return {
    userId: uid,
    deliveryNoteCloudId,
    productCloudId,
    productName: "Harina de Maíz 1Kg",
    quantity: 3.0,
    priceUsd: 1.5,
    createdAt: ts,
    updatedAt: ts,
  };
}

function makeExpense(uid, ts = pastDate()) {
  return {
    userId: uid,
    category: "Servicios",
    invoiceNumber: "G-001",
    amountUsd: 25.0,
    amountBs: 1311.25,
    bcvRateUsed: 52.45,
    paymentMethod: "Pago Móvil",
    description: "Internet fibra óptica",
    timestampMs: Date.now() - 1000,
    registeredBy: "Admin",
    createdAt: ts,
    updatedAt: ts,
  };
}

function makeSupplier(uid, ts = pastDate()) {
  return {
    userId: uid,
    name: "Alimentos del Sur",
    documentId: "J-30111222-3",
    phone: "02129998877",
    address: "Zona Industrial",
    createdAt: ts,
    updatedAt: ts,
  };
}

function makePurchaseInvoice(uid, supplierCloudId = "sup_1", ts = pastDate()) {
  return {
    userId: uid,
    supplierCloudId,
    invoiceNumber: "FC-9001",
    timestampMs: Date.now() - 1000,
    totalAmount: 250.0,
    description: "Reposición semanal",
    paid: false,
    paidTimestamp: 0,
    dueDate: Date.now() + 7 * 86400000,
    paymentNotes: "",
    subtotalUsd: 215.52,
    taxAmountUsd: 34.48,
    retencionIvaUsd: 25.86,
    retencionIvaPercentage: 75.0,
    retencionIslrUsd: 0.0,
    retencionIslrPercentage: 0.0,
    withholdingVoucherNumber: "",
    withholdingStatus: "PENDIENTE",
    createdAt: ts,
    updatedAt: ts,
  };
}

function makePosUser(uid, ts = pastDate()) {
  return {
    userId: uid,
    username: "Cajero 1",
    pin: "1234",
    role: "CAJERO",
    avatarUrl: "",
    earnCommission: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

function makeDevice(uid, ts = pastDate()) {
  return {
    userId: uid,
    deviceModel: "Samsung Galaxy Tab",
    googleEmail: "tienda@gmail.com",
    lastActiveAt: ts,
    createdAt: ts,
    updatedAt: ts,
  };
}

function makeEncryptedBackup(uid, ts = pastDate()) {
  return {
    userId: uid,
    googleEmail: "tienda@gmail.com",
    storeName: "Comercial Central C.A.",
    storeRif: "J-12345678-9",
    deviceId: "device_1",
    deviceModel: "Samsung Galaxy Tab",
    formatVersion: 2,
    productsCount: 25,
    salesCount: 10,
    customersCount: 8,
    exportedAtMs: Date.now() - 1000,
    encryptedPayload: "Q0FKQVBPU19FTkNfR1pJUF9WMg==",
    createdAt: ts,
    updatedAt: ts,
  };
}

// --- TESTS ---

test("Unauthenticated users: denied on root, users/{uid}, and all subcollections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("products").get());
  await assertFails(
    unauth.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").set(makeProduct(ALICE_UID))
  );
  await assertFails(unauth.collection("unknown_root").get());
});

test("Cross-user isolation: Bob cannot read, list, create, update, or delete Alice's store data", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(makeUserAccount(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").set(makeProduct(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sales").doc("sale_1").set(makeSale(ALICE_UID))
  );

  // Bob cannot list or get Alice's docs
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("products").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sales").get());

  // Bob cannot create or update or delete under Alice's UID (even with Bob's userId in payload)
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_2").set(makeProduct(BOB_UID))
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").update({
      stock: 0,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").delete()
  );
});

test("Owner creates and queries all 15 collections/subcollections matching Kotlin DTOs", async () => {
  const db = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userRef = db.collection("users").doc(ALICE_UID);

  await assertSucceeds(userRef.set(makeUserAccount(ALICE_UID)));
  await assertSucceeds(userRef.collection("company_profile").doc("main").set(makeCompanyProfile(ALICE_UID)));
  await assertSucceeds(userRef.collection("products").doc("prod_1").set(makeProduct(ALICE_UID)));
  await assertSucceeds(userRef.collection("sales").doc("sale_1").set(makeSale(ALICE_UID)));
  await assertSucceeds(userRef.collection("sale_items").doc("si_1").set(makeSaleItem(ALICE_UID)));
  await assertSucceeds(userRef.collection("customers").doc("cust_1").set(makeCustomer(ALICE_UID)));
  await assertSucceeds(userRef.collection("quotes").doc("quote_1").set(makeQuote(ALICE_UID)));
  await assertSucceeds(userRef.collection("quote_items").doc("qi_1").set(makeQuoteItem(ALICE_UID)));
  await assertSucceeds(userRef.collection("delivery_notes").doc("dn_1").set(makeDeliveryNote(ALICE_UID)));
  await assertSucceeds(userRef.collection("delivery_note_items").doc("dni_1").set(makeDeliveryNoteItem(ALICE_UID)));
  await assertSucceeds(userRef.collection("expenses").doc("exp_1").set(makeExpense(ALICE_UID)));
  await assertSucceeds(userRef.collection("suppliers").doc("sup_1").set(makeSupplier(ALICE_UID)));
  await assertSucceeds(userRef.collection("purchase_invoices").doc("pi_1").set(makePurchaseInvoice(ALICE_UID)));
  await assertSucceeds(userRef.collection("pos_users").doc("pu_1").set(makePosUser(ALICE_UID)));
  await assertSucceeds(userRef.collection("devices").doc("dev_1").set(makeDevice(ALICE_UID)));

  // List queries matching Kotlin snapshots() listeners
  const subcollections = [
    "company_profile",
    "products",
    "sales",
    "sale_items",
    "customers",
    "quotes",
    "quote_items",
    "delivery_notes",
    "delivery_note_items",
    "expenses",
    "suppliers",
    "purchase_invoices",
    "pos_users",
    "devices",
  ];
  for (const col of subcollections) {
    await assertSucceeds(userRef.collection(col).get());
  }
});

test("Multi-device partial updates, atomic stock deduction, and immutable field enforcement", async () => {
  const db = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userRef = db.collection("users").doc(ALICE_UID);

  await assertSucceeds(userRef.set(makeUserAccount(ALICE_UID)));
  await assertSucceeds(userRef.collection("company_profile").doc("main").set(makeCompanyProfile(ALICE_UID)));
  await assertSucceeds(userRef.collection("products").doc("prod_1").set(makeProduct(ALICE_UID)));
  await assertSucceeds(userRef.collection("sales").doc("sale_1").set(makeSale(ALICE_UID)));
  await assertSucceeds(userRef.collection("customers").doc("cust_1").set(makeCustomer(ALICE_UID)));
  await assertSucceeds(userRef.collection("quotes").doc("quote_1").set(makeQuote(ALICE_UID)));
  await assertSucceeds(userRef.collection("delivery_notes").doc("dn_1").set(makeDeliveryNote(ALICE_UID)));
  await assertSucceeds(userRef.collection("purchase_invoices").doc("pi_1").set(makePurchaseInvoice(ALICE_UID)));
  await assertSucceeds(userRef.collection("pos_users").doc("pu_1").set(makePosUser(ALICE_UID)));
  await assertSucceeds(userRef.collection("devices").doc("dev_1").set(makeDevice(ALICE_UID)));

  // Valid partial stock decrement (like concurrent POS sale transaction)
  await assertSucceeds(
    userRef.collection("products").doc("prod_1").update({
      stock: 23.0,
      updatedAt: pastDate(200),
    })
  );

  // Valid partial company BCV update
  await assertSucceeds(
    userRef.collection("company_profile").doc("main").update({
      bcvRate: 53.1,
      updatedAt: pastDate(200),
    })
  );

  // Valid partial sale credit payment & withholding update
  await assertSucceeds(
    userRef.collection("sales").doc("sale_1").update({
      creditPaid: true,
      creditPaidAmountUsd: 15.0,
      creditNotes: "Pagado en efectivo",
      updatedAt: pastDate(200),
    })
  );

  // Valid quote status transition VIGENTE -> ACEPTADO -> ANULADO, then locked
  await assertSucceeds(
    userRef.collection("quotes").doc("quote_1").update({
      status: "ACEPTADO",
      updatedAt: pastDate(200),
    })
  );
  await assertSucceeds(
    userRef.collection("quotes").doc("quote_1").update({
      status: "ANULADO",
      updatedAt: pastDate(150),
    })
  );
  // Once ANULADO, subsequent mutation must be rejected
  await assertFails(
    userRef.collection("quotes").doc("quote_1").update({
      status: "FACTURADO",
      updatedAt: pastDate(100),
    })
  );

  // Valid delivery note cancellation then locked
  await assertSucceeds(
    userRef.collection("delivery_notes").doc("dn_1").update({
      status: "ANULADO",
      updatedAt: pastDate(150),
    })
  );
  await assertFails(
    userRef.collection("delivery_notes").doc("dn_1").update({
      status: "FACTURADO",
      updatedAt: pastDate(100),
    })
  );

  // Valid purchase invoice payment update
  await assertSucceeds(
    userRef.collection("purchase_invoices").doc("pi_1").update({
      paid: true,
      paidTimestamp: Date.now(),
      paymentNotes: "Transferencia",
      updatedAt: pastDate(100),
    })
  );

  // Valid POS user update and device heartbeat
  await assertSucceeds(
    userRef.collection("pos_users").doc("pu_1").update({
      pin: "9999",
      updatedAt: pastDate(100),
    })
  );
  await assertSucceeds(
    userRef.collection("devices").doc("dev_1").update({
      lastActiveAt: pastDate(100),
      updatedAt: pastDate(100),
    })
  );

  // Reject mutating immutable createdAt or userId
  await assertFails(
    userRef.collection("products").doc("prod_1").update({
      createdAt: pastDate(50),
      updatedAt: pastDate(50),
    })
  );
  await assertFails(
    userRef.collection("products").doc("prod_1").update({
      userId: BOB_UID,
      updatedAt: pastDate(50),
    })
  );
});

test("Validation & security rejections: extra id key, future timestamp, negative numbers, invalid enum", async () => {
  const db = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userRef = db.collection("users").doc(ALICE_UID);

  // Reject extra @DocumentId 'id' field on create
  await assertFails(
    userRef.collection("products").doc("prod_extra_id").set({
      id: "prod_extra_id",
      ...makeProduct(ALICE_UID),
    })
  );

  // Reject future createdAt / updatedAt
  await assertFails(
    userRef.collection("products").doc("prod_future").set(makeProduct(ALICE_UID, futureDate()))
  );

  // Reject negative stock or negative price
  await assertFails(
    userRef.collection("products").doc("prod_neg_stock").set({
      ...makeProduct(ALICE_UID),
      stock: -1,
    })
  );
  await assertFails(
    userRef.collection("products").doc("prod_neg_price").set({
      ...makeProduct(ALICE_UID),
      priceUsd: -0.5,
    })
  );

  // Reject empty product name
  await assertFails(
    userRef.collection("products").doc("prod_empty_name").set({
      ...makeProduct(ALICE_UID),
      name: "",
    })
  );

  // Reject invalid quote status and role escalation / invalid role
  await assertFails(
    userRef.collection("quotes").doc("quote_bad_status").set({
      ...makeQuote(ALICE_UID),
      status: "INVALIDO",
    })
  );
  await assertFails(
    userRef.collection("pos_users").doc("pu_bad_role").set({
      ...makePosUser(ALICE_UID),
      role: "SUPERADMIN_HACK",
    })
  );

  // Reject userId spoofing on create
  await assertFails(
    userRef.collection("products").doc("prod_spoof").set(makeProduct(BOB_UID))
  );

  // Reject extra shadow key on update
  await assertSucceeds(userRef.collection("products").doc("prod_ok").set(makeProduct(ALICE_UID)));
  await assertFails(
    userRef.collection("products").doc("prod_ok").update({
      isAdmin: true,
      updatedAt: pastDate(100),
    })
  );
});

test("Encrypted JSON backups: owner creates, updates in-place (modifying previous doc), and cross-user/invalid payloads fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  const aliceBackupRef = aliceDb.collection("users").doc(ALICE_UID).collection("encrypted_backups").doc("latest");

  // Unauthenticated denied
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("encrypted_backups").doc("latest").get()
  );

  // Owner create succeeds
  await assertSucceeds(aliceBackupRef.set(makeEncryptedBackup(ALICE_UID)));

  // Owner read & list succeeds
  await assertSucceeds(aliceBackupRef.get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("encrypted_backups").get());

  // Owner updates in-place (every 20 min overwrite modifying the previous doc)
  await assertSucceeds(
    aliceBackupRef.update({
      deviceId: "device_2",
      deviceModel: "Xiaomi Pad 6",
      productsCount: 30,
      salesCount: 14,
      customersCount: 9,
      exportedAtMs: Date.now(),
      encryptedPayload: "VVBEQVRFRF9FTkNSWVBURURfUEFZTE9BRA==",
      updatedAt: pastDate(100),
    })
  );

  // Cross-user Bob cannot read, overwrite, or delete Alice's encrypted backup
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("encrypted_backups").doc("latest").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("encrypted_backups").doc("latest").update({
      encryptedPayload: "SElKQUNLRUQ=",
      updatedAt: pastDate(50),
    })
  );

  // Empty encryptedPayload or immutable createdAt change must fail
  await assertFails(
    aliceBackupRef.update({
      encryptedPayload: "",
      updatedAt: pastDate(50),
    })
  );
  await assertFails(
    aliceBackupRef.update({
      createdAt: pastDate(50),
      updatedAt: pastDate(50),
    })
  );
});

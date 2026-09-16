// SQLDelight web-worker protocol on top of @sqlite.org/sqlite-wasm, persisted in OPFS.
//
// Requests:  { id, action: "exec" | "begin_transaction" | "end_transaction" | "rollback_transaction", sql, params }
// Responses: { id, results: { values: [[...row], ...] } }  or  { id, error }
// Statements without a result set answer { values: [[rowsChanged]] }: the driver reads the
// change count from values[0][0].
//
// The opfs-sahpool VFS needs no COOP/COEP headers, but it holds an exclusive lock:
// a second tab of the app cannot open the database while the first one is running.
import sqlite3InitModule from "@sqlite.org/sqlite-wasm";

const DATABASE_FILE = "/readshelf.db";

const ready = (async () => {
  const sqlite3 = await sqlite3InitModule();
  const pool = await sqlite3.installOpfsSAHPoolVfs({ name: "readshelf" });
  return new pool.OpfsSAHPoolDb(DATABASE_FILE);
})();

// The driver reads every integer as a JS number; never hand it a BigInt.
function toDriverValue(value) {
  return typeof value === "bigint" ? Number(value) : value;
}

function exec(db, sql, params) {
  const statement = db.prepare(sql);
  try {
    if (params && params.length > 0) statement.bind(params);
    if (statement.columnCount === 0) {
      statement.step();
      return { values: [[db.changes()]] };
    }
    const values = [];
    while (statement.step()) values.push(statement.get([]).map(toDriverValue));
    return { values };
  } finally {
    statement.finalize();
  }
}

function run(db, statement) {
  db.exec(statement);
  return { values: [] };
}

self.onmessage = async (event) => {
  const { id, action, sql, params } = event.data ?? {};
  try {
    const db = await ready;
    let results;
    switch (action) {
      case "exec":
        if (!sql) throw new Error("exec: missing query string");
        results = exec(db, sql, params);
        break;
      case "begin_transaction":
        results = run(db, "BEGIN TRANSACTION;");
        break;
      case "end_transaction":
        results = run(db, "END TRANSACTION;");
        break;
      case "rollback_transaction":
        results = run(db, "ROLLBACK TRANSACTION;");
        break;
      default:
        throw new Error(`Unsupported action: ${action}`);
    }
    postMessage({ id, results });
  } catch (error) {
    postMessage({ id, error: String(error && error.message ? error.message : error) });
  }
};

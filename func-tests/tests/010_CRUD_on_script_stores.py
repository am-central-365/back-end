import requests
import json
import uuid
import time
from datetime import datetime

import lib.logger as logger
import lib.db as db

api_base = None
ts_fmt = "%Y-%m-%d %H:%M:%S.%f"
test_recs_count = 5

class ScriptStore:
    def __init__(self, tpl):
        hex_pk, self.store_name, self.store_type, self.description, \
        self.created_by, self.created_ts, self.modified_by, self.modified_ts = tpl
        self.script_store_id = uuid.UUID(hex_pk)


def main(cfg):
    global api_base
    logger.log("Testing CRUD operations on script_stores")
    api_base = cfg.api_base
    conn = db.connect(cfg)

    # cleanup test data
    for x in xrange(test_recs_count):
        _delete(conn, "store "+str(x+1))
    db.sql(conn, "commit")

    # ---------------------------------------- Create
    apiStoreIds = _testCreateNew(conn, test_recs_count)
    if not apiStoreIds:
        return False

    if not _testCreateDupName(conn):
        return False

    # ---------------------------------------- Read
    dbStores = [ ScriptStore(_read(conn, apiStoreId["uuid_pk"])[0]) for apiStoreId in apiStoreIds]

    for dbStore in dbStores:
        if not _testRead(conn, dbStore):
            return False

    # ---------------------------------------- Update
    time.sleep(1)  # ensure modified_ts is greater
    for dbStore in dbStores:
        if not _testUpdate(conn, dbStore):
            return False

    #rows = db.sql(conn, "select 1+%s", 44)
    #logger.log("rows: %s", rows)
    #db.sql(conn, "commit")
    db.disconnect(conn)
    return True


def _testCreateNew(conn, how_many):
    identities = []
    for k in xrange(how_many):
        ks = str(k+1)
        _insert_data = { "store_name": "store "+ks, "store_type": "LocalFile", "description": "test data "+ks}
        req = requests.post(api_base+"/admin/data/scriptStores", data = _insert_data)
        if not req.ok:
            return logger.failed("expected {200, OK}, got code:", req.status_code, "response:", req.text)
        rsp_msg = json.loads(req.text)["message"]
        id_obj = {
            "script_store_id": str(rsp_msg["pk"]["script_store_id"]),
            "modified_ts":     str(rsp_msg["optLock"]["modified_ts"]),
            "uuid_pk":         uuid.UUID(str(rsp_msg["pk"]["script_store_id"]))
        }
        identities.append(id_obj)

    db.sql(conn, "commit")
    return identities


def _testCreateDupName(conn):
    _insert_data = { "store_name": "store 1", "store_type": "GitHub", "description": "test data -1"}
    req = requests.post(api_base+"/admin/data/scriptStores", data = _insert_data)
    rsp_msg = json.loads(req.text)["message"]
    if not rsp_msg.startswith("Duplicate entry"):
        return logger.failed("expected dup failure, got code:", req.status_code, "message:", req.text)
    db.sql(conn, "rollback")
    return True


def _testRead(conn, scriptStore):

    def test(prms):
        req = requests.get(api_base+"/admin/data/scriptStores", params = prms)
        if not req.ok:
            return logger.failed(
                "for %s: expected {200, OK}, got code: %s, message %s" % (prms, req.status_code, req.text))
        apiObjs = json.loads(req.text)
        assert apiObjs and len(apiObjs) == 1
        apiObj = apiObjs[0]

        assert apiObj["script_store_id"] == str(scriptStore.script_store_id)
        assert apiObj["store_name" ] == scriptStore.store_name
        assert apiObj["store_type" ] == scriptStore.store_type
        assert apiObj["description"] == scriptStore.description
        assert apiObj["created_by"] == scriptStore.created_by
        assert apiObj["modified_by"] == scriptStore.modified_by
        assert datetime.strptime(apiObj["created_ts"],  ts_fmt) == scriptStore.created_ts
        assert datetime.strptime(apiObj["modified_ts"], ts_fmt) == scriptStore.modified_ts

    test({"script_store_id": str(scriptStore.script_store_id)})
    test({"store_name": scriptStore.store_name})
    test({"description": scriptStore.description})

    return True


def _testUpdate(conn, store):
    new_store_type = "GitHub"

    _update_data = {
        "script_store_id": str(store.script_store_id),
        "modified_ts": str(store.modified_ts),
        "store_type": new_store_type
    }
    req = requests.post(api_base+"/admin/data/scriptStores", data = _update_data)
    if not req.ok:
        return logger.failed("expected {200, OK}, got code:", req.status_code, "response:", req.text)

    rsp_msg = json.loads(req.text)["message"]
    rsp_script_store_id = str(rsp_msg["pk"]["script_store_id"])
    rsp_modified_ts     = datetime.strptime(rsp_msg["optLock"]["modified_ts"], ts_fmt)

    assert rsp_script_store_id == str(store.script_store_id)
    assert store.modified_ts < rsp_modified_ts

    scriptStore = ScriptStore(_read(conn, store.script_store_id)[0])
    assert store.script_store_id == scriptStore.script_store_id
    assert store.store_name      == scriptStore.store_name
    assert new_store_type        == scriptStore.store_type
    assert store.description     == scriptStore.description
    assert store.created_by      == scriptStore.created_by
    assert store.modified_by     == scriptStore.modified_by
    assert store.created_ts      == scriptStore.created_ts
    assert store.modified_ts     <  scriptStore.modified_ts
    assert rsp_modified_ts       == scriptStore.modified_ts

    return True


def _read(conn, pk):
    return db.sql(conn,"""
        select hex(script_store_id), store_name, store_type, description,
               created_by, created_ts, modified_by, modified_ts
          from script_stores
         where script_store_id = %s""", pk.bytes)


def _delete(conn, store_name):
    return db.sql(conn,"""delete from script_stores where store_name = %s""", store_name)

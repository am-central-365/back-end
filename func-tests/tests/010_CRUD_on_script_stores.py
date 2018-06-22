import requests
import json
import uuid

import lib.logger as logger
import lib.db as db

api_base = None

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

    apiStoreIds = _testCreateNew(conn, 5)
    if not apiStoreIds:
        return False

    if not _testCreateDupName(conn):
        return False

    dbStores = [ ScriptStore(_read(conn, apiStoreId["uuid_pk"])[0]) for apiStoreId in apiStoreIds]

    for dbStore in dbStores:
        if not _testRead(conn, dbStore):
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
            return logger.failed("expected {200, OK}, got code:", req.status_code, "reponse:", req.text)
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
        assert datetime.datetime(apiObj["created_by" ]) == scriptStore.created_by
        assert apiObj["created_ts" ] == scriptStore.created_ts
        assert apiObj["modified_by"] == scriptStore.modified_by
        assert apiObj["modified_ts"] == scriptStore.modified_ts


    test({"script_store_id": str(scriptStore.script_store_id)})
    test({"store_name": scriptStore.store_name})
    test({"description": scriptStore.description})

    return True


def _read(conn, pk):
    return db.sql(conn,"""
        select hex(script_store_id), store_name, store_type, description,
               created_by, created_ts, modified_by, modified_ts
          from script_stores
         where script_store_id = %s""", pk.bytes)

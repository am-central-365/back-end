import requests

import lib.logger as logger
import lib.db as db

PKS = ["9b8d769a-71ec-11e8-974c-3f57d5512921", "9d16ca84-71ec-11e8-bc6e-87c8fc6074e4",
       "b85df32a-71ed-11e8-aa85-4bf7ff3bcb33", "b85df4ba-71ed-11e8-aa86-a7b4b9ad6f87",
       "b85df514-71ed-11e8-aa87-9fc29e423ae5" ]

api_base = None

def main(cfg):
    global api_base
    logger.log("Testing CRUD operations on script_stores")
    api_base = cfg.api_base
    conn = db.connect(cfg)

    _prepare(conn)

    if not _testCreateNew(conn, PKS[:-2]):
        return False


    #rows = db.sql(conn, "select 1+%s", 44)
    #logger.log("rows: %s", rows)
    #db.sql(conn, "commit")
    db.disconnect(conn)
    return True


def _testCreateNew(conn, pks):
    for k,v in enumerate(pks):
        _insert_data = { "script_store_id": v, "store_name": "store $k", "store_type": "LocalFile", "description": "test data $k"}
        req = requests.post(api_base+"/admin/data/scriptStores", data = _insert_data)
        if not req.ok:
            return logger.failed("expected {200, OK}, got code:", req.status_code, "reponse:", req.text)
    db.sql(conn, "commit")
    return True


def _prepare(conn):
    for pk in PKS:
        db.sql(conn, "delete from script_stores where script_store_id = %s", pk)
    db.sql(conn, "commit")

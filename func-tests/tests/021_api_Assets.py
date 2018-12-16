import requests
import json
import time
import uuid
from datetime import datetime

import lib.logger as logger
import lib.db as db

api_path = None
ts_fmt = "%Y-%m-%d %H:%M:%S.%f"
test_rec_count = 5

class Asset:
    def __init__(self, tpl):
        self.hex_asset_id, self.name, self.description, \
        self.created_by, self.created_ts, self.modified_by, self.modified_ts = tpl

class AssetRoleValues:
    def __init__(self, tpl):
        self.hex_asset_id, self.role_name, self.asset_vals, \
        self.created_by, self.created_ts, self.modified_by, self.modified_ts = tpl

class AssetRecord:
    def __init__(self, asset, asset_role_values_arr = None):
        self.asset = asset
        self.asset_role_values_arr = asset_role_values_arr or []


def main(cfg):
    global api_path
    api_path = cfg.api_base + "/catalog/assets"
    logger.log("Testing API on assets, api path: %s", api_path)
    conn = db.connect(cfg)

    # cleanup test data
    for x in xrange(test_rec_count):
        _delete(conn, _asset_name(x+1))
    db.sql(conn, "commit")

    # ---------------------------------------- Create Asset
    asset_recs = _testCreateAsset(conn, test_rec_count)
    if not asset_recs:
        return False

    if not _testCreateDupAsset(conn):
        return False

    # ---------------------------------------- Read
    #print "++", id_recs
    #asset_recs = [ Role(_read(conn, id_rec["name"])[0]) for id_rec in id_recs ]

    logger.log("running Read")
    for asset_rec in asset_recs:
        if not _testRead(asset_rec):
            return False

    # ---------------------------------------- Update
    time.sleep(1)  # ensure modified_ts has been promoted
    logger.log("running Update")
    if not _testUpdate(conn, 2, asset_recs[1]):
        return False

    if not _testUpdateNonExisted(conn, asset_recs[0]):
        return False

    # ---------------------------------------- Delete
    logger.log("running Delete")
    for asset_rec in asset_recs:
        if not _testDelete(conn, asset_recs):
            return False

    if not _testDeleteNonExisted(conn, asset_recs[0]):
        return False

    #rows = db.sql(conn, "select 1+%s", 44)
    #logger.log("rows: %s", rows)
    #db.sql(conn, "commit")
    db.disconnect(conn)
    return True


def _asset_name(k):    return "test_asset_" + str(k)
def _asset_schema(k):  return "{" + '"a{num}": "string!", "b{num}": "boolean", "n{num}": "number"'.format(num=str(k)) + "}"
def _asset_schema2(k): return "{" + '"z{num}": "string!", "y{num}": "boolean", "x{num}": "number"'.format(num=str(k)) + "}"

def _testCreateAsset(conn, how_many):
    logger.log("running CreateAsset for %d entities", how_many)
    identities = []
    for k in xrange(how_many):
        role_name = _asset_name(k+1)
        _insert_data = { "role_name": role_name, "class": "test", "role_schema": _asset_schema(k+1), "description": "test role "+str(k+1)}
        req = requests.post(api_path, json = _insert_data)
        if not req.ok:
            return logger.failed("CreateNew expected {200, OK}, got code:", req.status_code, "response:", req.text)
        rsp_msg = json.loads(req.text)
        id_obj = {
            "name":         str(rsp_msg["pk"]["role_name"]),
            "modified_ts":  str(rsp_msg["optLock"]["modified_ts"])
        }
        identities.append(id_obj)

    db.sql(conn, "commit")
    return identities


def _testCreateDupAsset(conn):
    logger.log("running CreateDupName")
    _insert_data = { "role_name": _asset_name(1), "class": "test", "role_schema": _asset_schema(1)}
    req = requests.post(api_path, json = _insert_data)
    if req.status_code != 409:
        return logger.failed("CreateDupName expected dup failure, got code:", req.status_code, "message:", req.text)
    db.sql(conn, "rollback")
    return True


def _eq_json(ja, jb):
    sa = json.dumps(ja, sort_keys=True)
    sb = json.dumps(jb, sort_keys=True)
    return sa == sb
    #return json.dumps(ja, sort_keys=True) == json.dumps(jb, sort_keys=True)

def _eq_json_str(jsa, jsb): return _eq_json(json.loads(jsa), json.loads(jsb))

def _testRead(role):

    def test(role_name):
        req = requests.get(api_path+"/"+role_name)
        if not req.ok:
            return logger.failed(
                "for role %s: Read expected {200, OK}, got code: %s, message %s" % (role_name, req.status_code, req.text))
        apiObj = json.loads(req.text)
        assert apiObj

        assert apiObj["role_name"] == role.name
        assert apiObj["class" ] == role.clazz
        assert _eq_json(apiObj["role_schema" ], json.loads(role.role_schema))
        assert apiObj["description"] == role.description
        assert apiObj.get("created_by") == role.created_by
        assert apiObj.get("modified_by") == role.modified_by
        assert datetime.strptime(apiObj["created_ts"],  ts_fmt) == role.created_ts
        assert datetime.strptime(apiObj["modified_ts"], ts_fmt) == role.modified_ts

    test(role.name)

    return True


def _testUpdate(conn, num, role):
    _update_data = {
        "modified_ts": str(role.modified_ts),
        "role_schema": _asset_schema2(num)
    }
    req = requests.post(api_path+"/"+role.name, json = _update_data)
    if not req.ok:
        return logger.failed("Update expected {200, OK}, got code:", req.status_code, "response:", req.text)

    rsp_msg = json.loads(req.text)
    rsp_name = rsp_msg["pk"]["role_name"]
    rsp_modified_ts = datetime.strptime(rsp_msg["optLock"]["modified_ts"], ts_fmt)

    assert role.name == rsp_name
    assert role.modified_ts < rsp_modified_ts

    db_rec = Role(_read(conn, role.name)[0])
    assert db_rec.name        == role.name
    assert db_rec.clazz       == role.clazz
    assert _eq_json_str(db_rec.role_schema, role.role_schema)
    assert db_rec.description == role.description
    assert db_rec.created_by  == role.created_by
    assert db_rec.modified_by == role.modified_by
    assert db_rec.created_ts  == role.created_ts
    assert db_rec.modified_ts >  role.modified_ts
    assert db_rec.modified_ts == rsp_modified_ts

    role.modified_ts = rsp_modified_ts
    return True


def _testUpdateNonExisted(conn, role):
    logger.log("running UpdateNonExisted")
    _update_data = {
        "modified_ts": str(role.modified_ts),
        "role_schema": _asset_schema2(0)

    }
    rsp = requests.post(api_path+"/"+_asset_name(0), json = _update_data)
    if not rsp.status_code == 410:
        return logger.failed("UpdateNonExisted expected {410, ...}, got code:", rsp.status_code, "response:", rsp.text)
    return True


def _testDelete(conn, role):
    _delete_data = {
        "modified_ts": str(role.modified_ts)
    }
    rsp = requests.delete(api_path+"/"+role.name, params = _delete_data)
    if not rsp.ok:
        return logger.failed("Delete expected {200, OK}, got code:", rsp.status_code, "response:", rsp.text)

    asset_record = _read(conn, role.name)
    assert asset_record is None

    return True


def _testDeleteNonExisted(conn, role):
    logger.log("running DeleteNonExisted")
    _delete_data = {
        "modified_ts": str(role.modified_ts)
    }
    rsp = requests.delete(api_path+"/"+role.name, params = _delete_data)
    if not rsp.status_code == 410:
        return logger.failed("DeleteNonExisted expected {410, ...}, got code:", rsp.status_code, "response:", rsp.text)
    return True


def _read(conn, pk):
    recs = db.sql(conn,"""
        select hex(asset_id), name, description, created_by, created_ts, modified_by, modified_ts
          from assets
         where asset_id = %s""", pk.bytes)
    if recs == 0:
        return None
    asset_record = AssetRecord(Asset(recs[0]))
    recs = db.sql(conn,"""
        select hex(asset_id), role_name, asset_vals, created_by, created_ts, modified_by, modified_ts
          from asset_role_values
         where asset_id = %s""", pk.bytes)
    asset_record.asset_role_values_arr = [AssetRoleValues(r) for r in recs]
    return asset_record


def _delete(conn, asset_name):
    db.sql(conn,"""delete from asset_role_values where asset_id in
            (select asset_id from assets where name = %s)""" % asset_name)
    db.sql(conn,"""delete from assets where name = %s)""" % asset_name)

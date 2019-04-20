import requests
import json
import traceback

import lib.logger as logger
import lib.db as db

api_path = None
ts_fmt = "%Y-%m-%d %H:%M:%S.%f"
test_rec_count = 5

# Assuming these roles already defined:
# script
#   script_location
#   script_main
# script-executor-amc
# script-target-host

expected_output = "amc script execution is working"

script_command = "/bin/echo '"+expected_output+"'"
#script_command = "pwd"

script_role_value = {
    "location": {
      "content": {
        "body": script_command,
        "version": "1.0.0"
      }
    },
    "targetRoleName": "script-target-host",
    "executorRoleName": "script-executor-amc"
}


def main(cfg):
    global api_path
    api_path = cfg.api_base + "/executes"
    logger.log("Testing Executes API, api path: %s", api_path)
    conn = db.connect(cfg)

    test_asset_name = "script-test-inline-1x"

    _delete(conn, test_asset_name)

    try:
        asset_id = create_asset(cfg, test_asset_name)
        add_role(cfg, asset_id, "script", json.dumps(script_role_value))
        add_role(cfg, asset_id, "script-executor-amc", {"os": "linux"})

        actual_output = execute(cfg, asset_id)
        if actual_output != expected_output:
            return logger.failed("expected: '"+expected_output+"', actual: '"+actual_output+"'")

    except Exception, x:
        #traceback.print_exc()
        return logger.failed(x.message)

    _delete(conn, test_asset_name)
    db.disconnect(conn)
    return logger.passed()


def create_asset(cfg, asset_name):
    rsp = requests.post(cfg.api_base + "/catalog/assets",
                        json = {"name": asset_name, "description": "testing script execution by AMC"})
    if not rsp.ok:
        raise Exception("Creating test asset: expected {200, OK}, got code: "+str(rsp.status_code)+", response: "+rsp.text)

    rsp_msg = json.loads(rsp.text)
    return str(rsp_msg["pk"]["assetId"])


def add_role(cfg, asset_key, role_name, vals):
    rsp = requests.post(cfg.api_base + "/catalog/assets/"+asset_key+"/roles",
                        json = {"roleName": role_name, "assetVals": vals})
    if not rsp.ok:
        raise Exception("Adding role "+role_name+" to asset "+asset_key+": expected {200, OK}, got code: "+str(rsp.status_code)+", response: "+rsp.text)


def execute(cfg, asset_key):
    rsp = requests.post(cfg.api_base + "/executes", json = {"scriptKey": asset_key})
    if not rsp.ok:
        raise Exception("Executing asset "+asset_key+": expected {200, OK}, got code: "+str(rsp.status_code)+", response: "+rsp.text)

    return rsp.text


def _delete(conn, asset_name):
    db.sql(conn,"""delete from asset_role_values where asset_id in
            (select asset_id from assets where name = '%s')""" % asset_name)
    db.sql(conn,"""delete from assets where name = '%s'""" % asset_name)
    conn.commit()

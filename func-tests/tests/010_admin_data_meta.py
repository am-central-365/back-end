import requests
import json

import lib.logger as logger

def main(cfg):
    api_path = cfg.api_base + "/admin/data"
    logger.log("Testing Data Admin MetaData, API path: %s", api_path)
    rsp = requests.get(api_path)
    if not rsp.ok:
        return logger.failed(
            "expected {200, OK}, got code: %s, message %s" % (rsp.status_code, rsp.text))
    entry_point_names = json.loads(rsp.text)

    if len(entry_point_names) < 1:
        return logger.failed("no entry points were returned")

    if not "script_stores" in entry_point_names:
        return logger.failed("expected entry point 'script_stores' is not in the list")

    return True

import requests

import lib.logger as logger
import lib.db as db

def main(cfg):
    logger.log("requesting /publicKey")
    req = requests.get(cfg.api_base+"/publicKey")
    if not req.ok:
        logger.log("expected {200, OK}, got code:", req.status_code, "reponse:", req.text)

    return len(req.text) > 380

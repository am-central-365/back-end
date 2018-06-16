import lib.logger as logger
import lib.db as db

def main(cfg):
    logger.log("I am test 001")
    conn = db.connect(cfg)
    rows = db.sql(conn, "select 1+%s", 44)
    logger.log("rows: %s", rows)
    db.sql(conn, "commit")
    db.disconnect(conn)
    return True

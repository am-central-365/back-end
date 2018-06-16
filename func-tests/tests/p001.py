from lib.db import connect, disconnect, sql

def main(cfg):
    print "I am test 001"
    conn = connect(cfg)
    rows = sql(conn, "select 1+%s", 44)
    print "rows:", rows
    sql(conn, "commit")
    disconnect(conn)

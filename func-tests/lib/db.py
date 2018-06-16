import mysql.connector

import logger

def connect(cfg):
    db_config = { "user": cfg.db_usr, "password": cfg.db_pwd }
    u = cfg.db_url

    # parse db-name and port from host:port/db-name where port and db-name parts are optional
    u = u.split('/')
    if len(u) > 1:
        u, db_config["database"] = u[0], u[1]

    u = u.split(':')
    if len(u) > 1:
        u, db_config["port"] = u[0], u[1]

    db_config["host"] = u[0]
    logger.log("++ db_config: %s", db_config)

    return mysql.connector.connect(**db_config)

def disconnect(conn):
    conn.close()


def sql(conn, stmt, *vals):
    """ Runs the statement with binds and returns the list of tuples for select or None for a DML/DDL """
    c = conn.cursor()
    c.execute(stmt, tuple(vals))
    tuples = None
    if c.with_rows:
        tuples = c.fetchall()
    c.close()
    return tuples

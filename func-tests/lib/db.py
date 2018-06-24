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
    #logger.log("++ db_config: %s", db_config)

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


"""
private fun parseStatements(inputStream: InputStream): List<ScriptStatement> {
    val scriptStatements: MutableList<ScriptStatement> = mutableListOf()
    var lineNo = 0
    var scriptLine = 0
    fun inScript() = scriptLine > 0

    val sb = StringBuilder()
    for(line in inputStream.bufferedReader().lines()) {
        lineNo++

        if( !inScript() && (line.isBlank() || line.startsWith('#')) )  // skip blank and comment lines
            continue

        val trimmed = line.trimEnd()
        if( trimmed == ";" || trimmed == "/" || trimmed.isBlank() ) {
            if( inScript() ) {
                scriptStatements.add(ScriptStatement(scriptLine, sb.toString()))
                sb.setLength(0)
                scriptLine = 0
            } else {
                logger.warn { "line $lineNo: blank statement" }
            }
        } else {
            if( !inScript() )
                scriptLine = lineNo
            sb.append(line).append('\n')
        }
    }

    if( inScript() )
        scriptStatements.add(ScriptStatement(scriptLine, sb.toString()))

    return scriptStatements
}
"""

def parse_script(script_lines):
    script_statements = []  # list of tuples: (first_line_no, text)
    line_no = script_line = 0
    in_comment = False
    def in_script():
        return script_line > 0

    sb = ""
    for line_text in script_lines:
        line_no += 1
        trimmed = line_text.strip()

        # skip blank and comment lines
        if not in_script() and (not trimmed or trimmed.startswith('#') or trimmed.startswith("--") or trimmed.startswith("//")):
            continue

        # check for multi-line comment start
        if not in_comment and not in_script() and trimmed.startswith("/*"):
            in_comment = True           # Detect standalone comments (not within a statement

        # check for multi-line comment end
        if in_comment:
            eoc_pos = trimmed.find("*/")
            if eoc_pos == -1:
                continue                # no end of comment in this line
            trimmed = trimmed[eoc_pos+2:]
            in_comment = False
            if not trimmed:
                continue

        eos = trimmed.endswith(';')
        if eos:
            trimmed = trimmed[:-1]
            if not in_script() and trimmed:
                script_line = line_no

        if eos or not trimmed or trimmed == "/":
            if in_script():
                script_statements.append( (script_line, sb+trimmed) )
                sb = ""
                script_line = 0
            else:
               logger.log("warn: blank statement at line %s", line_no)
        else:
            if not in_script():
                script_line = line_no
            sb += line_text

    if in_script():
        script_statements.append( (script_line, sb) )

    return script_statements


def run_script(conn, script_file):
    with open(script_file, "r") as f:
        script_lines = f.readlines()
    sql_statements = parse_script(script_lines)
    for stmt_descr in sql_statements:
        logger.log("running statement defined on line %d" % stmt_descr[0])
        sql(conn, stmt_descr[1])

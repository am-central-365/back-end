#!/usr/bin/env kscript
//DEPS mysql:mysql-connector-java:5.1.46
//@file:DependsOn("mysql:mysql-connector-java:5.1.46")


import java.sql.DriverManager
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
import java.sql.ResultSet
import java.util.UUID

val dbUsr = "amcentral365"
val dbPwd = "a"
val dbUrl = "jdbc:mysql://127.0.0.1/amcentral365?useSSL=false"


fun getUUID() = UUID.randomUUID().toString().replace("-", "")
val assetAMCSite = getUUID()
val assetAMCWorkerCluster = getUUID()
val assetAMCWorker1 = getUUID()
val assetAMCWorker2 = getUUID()

var dlrSiteId = 0
var dlrWrkCId = 0
var dlrWrkNId = 0
var dlrGlrCId = 0
var dlrGlrNId = 0
var dlrGphCId = 0
var dlrGphNId = 0

var draAmcNodeHostnameId = 0
var draAmcNodeServicePortId = 0
var draAmcNodeHazelcastPortId = 0


println("Connecting to $dbUrl as $dbUsr")
Class.forName("com.mysql.jdbc.Driver")
val conn = DriverManager.getConnection(dbUrl, dbUsr, dbPwd)
conn.setAutoCommit(false)
println("connected")

println("clearing data")
runSql(conn, "delete from asset_values")
runSql(conn, "delete from asset_linkages")
runSql(conn, "delete from asset_roles")
runSql(conn, "delete from declared_linkage_roles")
runSql(conn, "delete from declared_role_attributes")
runSql(conn, "delete from linkages")
runSql(conn, "delete from roles")
runSql(conn, "delete from assets")

insertRoles(conn)
insertDeclaredRoleAttributes(conn)
insertAssets(conn)
insertAssetRoles(conn)
insertAssetValues(conn)

insertLinkages(conn)
insertDeclaredLinkageRoles(conn)
insertAssetLinkages(conn)


conn.commit()
conn.close()
println("connection closed")

fun runSql(conn: Connection, sqlText: String): Int {
    val stmt = conn.createStatement()
    var rs: ResultSet? = null
    var agi = 0
    try {
        stmt.executeUpdate(sqlText, Statement.RETURN_GENERATED_KEYS)
        rs = stmt.getGeneratedKeys();
        if( rs.next() )
            agi = rs.getInt(1)

    } catch (x: SQLException) {
        println("  Error running: $sqlText")
        println("  "+x.message)
    } finally {
        if( rs != null )
            rs.close()
        stmt.close()
    }
    return agi
}


fun insertRoles(conn: Connection) {
    println("inserting into roles...")
    runSql(conn, """
        insert into roles(class, name, description) values
            ('#internal', 'common',      'Implicitly applies to every asset'),
            /* -- */
            ('generic',   'host',        'A generic  version of a virtual machine'),
            ('generic',   'db-instance', 'A database instance running on a host: standalone or cluster node'),
            /* -- */
            ('application', 'site',           'Application service'),
            ('application', 'cluster',        'A generic cluster: App service, Web, Database, ZK, ...'),
            ('application', 'amc-cluster',    'An appication cluster, runs application process'),
            ('application', 'amc-node',       'An application instance running on a machine'),
            /* -- */
            ('database', 'galera-cluster',      'MySQL Galera cluster'),
            ('database', 'galera-node',         'MySQL Galera cluster node, usually there are 3 or more'),
            ('database', 'oracle-rac',          'Oracle Real Application Cluster'),
            ('database', 'oracle-db',           'A generic standalone Oracle database'),
            ('database', 'mysql-db',            'A generic standalone MySql database'),
            /* -- */
            ('metrics',  'graphite-cluster',    'Genuine Graphite instances woring together'),
            ('metrics',  'graphite-node',       'A single Graphite instance, part of a cluster'),
            ('metrics',  'graphite-db',         'A standalong Graphitedeployment')
        """.trimIndent()
    )
}

fun insertDeclaredRoleAttributes(conn: Connection) {
    println("inserting into declared_role_attributes...")
    draAmcNodeHostnameId = runSql(conn, """
        insert into declared_role_attributes(role_name, attr_name, attr_type, required, single, default_str_val)
        values('amc-node', 'hostname',       'string',  true, false, null)""".trimIndent())

    draAmcNodeServicePortId = runSql(conn, """
        insert into declared_role_attributes(role_name, attr_name, attr_type, required, single, default_str_val)
        values('amc-node', 'service-port',   'integer', true, true, '24941')""".trimIndent())

    draAmcNodeHazelcastPortId = runSql(conn, """
        insert into declared_role_attributes(role_name, attr_name, attr_type, required, single, default_str_val)
        values('amc-node', 'hazelcast-port', 'integer', true, true, '5701')""".trimIndent())
}

fun insertAssets(conn: Connection) {
    println("inserting into assets...")
    runSql(conn, """
        insert into assets(asset_id, name, description) values
          (unhex('$assetAMCSite'), 'amCentral365-prod', 'production deployment of amCentral365'),
          (unhex('$assetAMCWorkerCluster'), 'amCentral365-prod-wc', 'amCentral365 workers cluster'),
          (unhex('$assetAMCWorker1'), 'amCentral365-prod-wn1', 'amCentral365 worker node'),
          (unhex('$assetAMCWorker2'), 'amCentral365-prod-wn2', 'amCentral365 worker node')
        """.trimIndent()
    )
}

fun insertAssetRoles(conn: Connection) {
    println("inserting into asset_roles...")
    runSql(conn, """
        insert into asset_roles(asset_id, role_name) values
          (unhex('$assetAMCSite'),          'site'),
          (unhex('$assetAMCWorkerCluster'), 'amc-cluster'),
          (unhex('$assetAMCWorker1'),       'amc-node'),
          (unhex('$assetAMCWorker1'),       'host'),
          (unhex('$assetAMCWorker2'),       'amc-node'),
          (unhex('$assetAMCWorker2'),       'host')
        """.trimIndent()
    )
}

fun insertAssetValues(conn: Connection) {
    println("inserting into asset_values...")
    // assetAmCentralSite has no attributes
    // assetAMCWorkerCluster has no attributes
    runSql(conn, """
        insert into asset_values(asset_id, dra_id, str_val, int_val) values
          (unhex('$assetAMCWorker1'), $draAmcNodeHostnameId,     'localhost', null)
        , (unhex('$assetAMCWorker1'), $draAmcNodeServicePortId,   null, 24941)
        , (unhex('$assetAMCWorker1'), $draAmcNodeHazelcastPortId, null,  5701)
        , (unhex('$assetAMCWorker2'), $draAmcNodeHostnameId,     'localhost', null)
        , (unhex('$assetAMCWorker2'), $draAmcNodeServicePortId,   null, 24942)
        , (unhex('$assetAMCWorker2'), $draAmcNodeHazelcastPortId, null,  5702)
        """.trimIndent()
    )
}


fun insertLinkages(conn: Connection) {
    println("inserting into linkages...")
    runSql(conn, """
        insert into linkages(name, description) values
          ('amcentral365-site', 'An instance of amCentral-365 system installation')
        """.trimIndent()
    )
}

fun insertDeclaredLinkageRoles(conn: Connection) {
    println("inserting into declared_linkage_roles...")

    /*
        amcentral365-site
        |___ amc-cluster
        | |____ amc-node
        |
        |___ galera-cluster
        | |____ galera-node
        |
        |___ graphite-cluster
        | |____ graphite-node

     */

    dlrSiteId = runSql(conn, """
        insert into declared_linkage_roles(linkage_name, role_name, parent_dlr_id, required, single)
        values('amcentral365-site', 'site', null, true, true)""".trimIndent())

    dlrWrkCId = runSql(conn, """
        insert into  declared_linkage_roles(linkage_name, role_name, parent_dlr_id, required, single)
        select linkage_name, 'amc-cluster', dlr_id, true, true from declared_linkage_roles dlr
         where linkage_name = 'amcentral365-site' and role_name = 'site'""".trimIndent())
    dlrWrkNId = runSql(conn, """
        insert into  declared_linkage_roles(linkage_name, role_name, parent_dlr_id, required, single)
        select linkage_name, 'amc-node', dlr_id, true, false from declared_linkage_roles dlr
         where linkage_name = 'amcentral365-site' and role_name = 'amc-cluster'""".trimIndent())

    dlrGlrCId = runSql(conn, """
        insert into  declared_linkage_roles(linkage_name, role_name, parent_dlr_id, required, single)
        select linkage_name, 'galera-cluster', dlr_id, true, false from declared_linkage_roles dlr
         where linkage_name = 'amcentral365-site' and role_name = 'site'""".trimIndent())
    dlrGlrNId = runSql(conn, """
        insert into  declared_linkage_roles(linkage_name, role_name, parent_dlr_id, required, single)
        select linkage_name, 'galera-node', dlr_id, true, false from declared_linkage_roles dlr
         where linkage_name = 'amcentral365-site' and role_name = 'galera-cluster'""".trimIndent())

    dlrGphCId = runSql(conn, """
        insert into  declared_linkage_roles(linkage_name, role_name, parent_dlr_id, required, single)
        select linkage_name, 'graphite-cluster', dlr_id, true, false from declared_linkage_roles dlr
         where linkage_name = 'amcentral365-site' and role_name = 'site'""".trimIndent())
    dlrGphNId = runSql(conn, """
        insert into  declared_linkage_roles(linkage_name, role_name, parent_dlr_id, required, single)
        select linkage_name, 'graphite-node', dlr_id, true, false from declared_linkage_roles dlr
         where linkage_name = 'amcentral365-site' and role_name = 'graphite-cluster'""".trimIndent())
}

fun insertAssetLinkages(conn: Connection) {
    println("inserting into asset_linkages...")

    runSql(conn, """
        insert into asset_linkages(linkage_name, asset1_id, asset2_id, dlr1_id, dlr2_id) values
          ('amcentral365-site', unhex('$assetAMCSite'),    unhex('$assetAMCWorkerCluster'), $dlrSiteId, $dlrWrkCId)
        , ('amcentral365-site', unhex('$assetAMCWorkerCluster'), unhex('$assetAMCWorker1'), $dlrWrkCId, $dlrWrkNId)
        , ('amcentral365-site', unhex('$assetAMCWorkerCluster'), unhex('$assetAMCWorker2'), $dlrWrkCId, $dlrWrkNId)
        """.trimIndent()
    )
}

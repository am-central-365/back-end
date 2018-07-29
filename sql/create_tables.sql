/**
  MySQL script to create amcentral365-365 catalog schema
  The script drops existing objects and re-creates them.
  Before running the script, create 'amcentral365' user and database
  with commands below.

  Run as a power user (root or similar):
  {code sql}
    create database if not exists amcentral365;

    drop user amcentral365@'%';
    drop user amcentral365@localhost;
    create user amcentral365@'%' identified by 'a';
    create user amcentral365@localhost identified by 'a';

    grant all privileges on amcentral365.* to amcentral365@'%';
    grant all privileges on amcentral365.* to amcentral365@localhost;
    flush privileges;

    show grants for amcentral365;
  {code}
*/

/* --------- run as user amcentral365 */
/*use amcentral365;*/

drop table asset_linkages;
drop table asset_values;
drop table role_attributes;
drop table asset_roles;

drop table declared_linkage_roles;
drop table declared_role_attributes;

drop table linkages;
drop table roles;
drop table assets;

/* ============================================================================================ */
create table roles(
    name         varchar(100) not null
  ,   constraint role_pk primary key(name)
  , class        varchar(100) not null   /* Not used, but explains the role to humans. */
  /*, base_role    varchar(100)            /* A role may inherit its attributes from base" \*
  ,   constraint roles_fk1 foreign key(base_role) references roles(name)*/
  , description  varchar(65000)
  /* --- standard fields */
  , created_by   varchar(100)
  , created_ts   timestamp default current_timestamp not null
  , modified_by  varchar(100)
  , modified_ts  timestamp default current_timestamp not null
);

create table assets(
  asset_id binary(16)
  ,   constraint assets_pk primary key(asset_id)
  , name        varchar(100) not null
  /*,   constraint assets_uk unique(name)*/
  , description varchar(65000)
  /* --- standard fields */
  , created_by  varchar(100)
  , created_ts  timestamp default current_timestamp not null
  , modified_by varchar(100)
  , modified_ts timestamp default current_timestamp not null
);

create table linkages(
    name         varchar(100) not null
  ,   constraint linkages_pk primary key(name)
  , description  varchar(65000)
  /* --- standard fields */
  , created_by  varchar(100)
  , created_ts  timestamp default current_timestamp not null
  , modified_by varchar(100)
  , modified_ts timestamp default current_timestamp not null
);

/* -------------------------------------------------------------------------------------------- */
create table declared_role_attributes(   /* a role has declared attributes */
    role_name   varchar(100) not null
  ,   constraint declared_role_attributes_fk1 foreign key(role_name) references roles(name)
  , name        varchar(100) not null
  ,   constraint declared_role_attributes_pk primary key(role_name, name)
  , attr_type   enum('string', 'boolean', 'integer', 'real', 'timestamp', 'binary')
  , required    boolean not null default false
  , single      boolean not null default false
  , default_str_val varchar(100)
  , custom_prop varchar(100)   /* developer-defined property, opaque to amcentral365 */
  , description varchar(64000)
  /* --- standard fields */
  , created_by  varchar(100)
  , created_ts  timestamp default current_timestamp not null
  , modified_by varchar(100)
  , modified_ts timestamp default current_timestamp not null
);


create table declared_linkage_roles(    /* how roles are organized within linkages */
    dlr_id       int not null auto_increment
  , constraint declared_linkage_roles_pk primary key(dlr_id)
  , linkage_name varchar(100) not null
  ,   constraint linkage_roles_fk1 foreign key(linkage_name) references linkages(name)
  , role_name    varchar(100) not null
  ,   constraint declared_linkage_roles_fk2 foreign key(role_name) references roles(name)
/*,   constraint declared_linkage_roles_uk1 unique(linkage_name, role_name)*/
  , parent_dlr_id int
  ,   constraint declared_linkage_roles_fk3 foreign key(parent_dlr_id)
            references declared_linkage_roles(dlr_id) on delete cascade
/*, sibling_pos  int default 0 not null   \* position among children of the same parent *\
  ,   constraint linkage_roles_ck1 check(sibling_pos >= 0)*/
  , required     boolean not null default false
  , single       boolean not null default false
  , description  text
);

/* -------------------------------------------------------------------------------------------- */
create table asset_roles(      /* roles assigned to the asset */
    asset_id     binary(16)   not null
  ,   constraint asset_roles_fk1 foreign key(asset_id) references assets(asset_id)
/*, linkage_name varchar(100) not null*/
  , role_name    varchar(100) not null
  ,   constraint asset_roles_fk2 foreign key(role_name) references roles(name)
  ,   constraint asset_roles_pk primary key(asset_id/*, linkage_name*/, role_name)
);


create table asset_values(    /* the biggest table: attribute values for specific role of a specific asset */
    asset_id     binary(16)   not null
  ,   constraint asset_values_fk1 foreign key(asset_id) references assets(asset_id)
  , role_name    varchar(100) not null
  ,   constraint asset_values_fk2 foreign key(role_name) references roles(name)
  , attr_name    varchar(100) not null
  ,   constraint asset_values_fk3 foreign key(role_name, attr_name) references declared_role_attributes(role_name, name)
  ,   constraint asset_values_pk primary key(asset_id, role_name, attr_name)
  , str_val   varchar(65000)
  , bool_val  boolean
  , int_val   int
  , real_val  double
  , ts_val    timestamp
  , bin_val   blob
  ,  constraint asset_values_ck1 check(coalesce(str_val, bool_val, int_val, real_val, ts_val, bin_val) is not null)
  /* --- standard fields */
  , created_by  varchar(100)
  , created_ts  timestamp default current_timestamp not null
  , modified_by varchar(100)
  , modified_ts timestamp default current_timestamp not null
);


create table asset_linkages(  /*  */
    linkage_name varchar(100) not null
  ,   constraint asset_linkages_fk1 foreign key(linkage_name) references linkages(name)
  , asset1_id    binary(16)   not null
  ,   constraint asset_linkages_fk2 foreign key(asset1_id) references assets(asset_id)
  , asset2_id    binary(16)   not null
  ,   constraint asset_linkages_fk3 foreign key(asset1_id) references assets(asset_id)
  , dlr1_id      int          not null
  , dlr2_id      int          not null
  , sibling1_pos int default 0 not null
  , sibling2_pos int default 0 not null
  ,   constraint   asset_linkages_uk1 unique(linkage_name, asset1_id, dlr1_id, sibling1_pos, asset2_id, dlr2_id, sibling2_pos)
  ,   unique index asset_linkages_uk2       (linkage_name, asset2_id, dlr2_id, sibling2_pos, asset1_id, dlr1_id, sibling1_pos)
);



select hex(asset_id), name, description from assets;
select hex(asset_id), role_name from asset_roles;
select * from asset_linkages;
select * from declared_role_attributes;
select * from declared_linkage_roles;
select * from linkages;

select * from roles;
select * from declared_linkage_roles;
select hex(asset_id), t.* from asset_values t;

/* ============================================================================================ */

/*
create table execution_channels(
  name  varchar(100) not null
  ,  constraint execution_channels_pk primary key(name)
  , auth_method json
  , description varchar(2000)
  \* --- standard fields *\
  , created_by  varchar(100)
  , created_ts  timestamp default current_timestamp not null
  , modified_by varchar(100)
  , modified_ts timestamp default current_timestamp not null
);

insert into execution_channels(name, auth_method, descritption) values
  ('ssh',  '{"input": ["user-name", "key-pair-path"]}',
     'public/private ssh key authentication. The target host must list amCentral public key in its ~/.ssh/authorized_keys')
 ,('jdbc', '{ "input: ["user-name", "password"]" }',
     'connect to a JDBC-compliant database via JDBC using user name and password authentication')
;
*/



create table scripts(
  name         varchar(100) not null
  ,   constraint scripts_pk primary key(name)
  ...
  /* Consider a script controlling Oracle Listener via command-line calls to lsnrctl (not within lsnrctl shell)
     It applies to role 'oracle-listener' but runs on the host, and therefore its 'exec_role' is 'host'.
     The asset must possess one the host' role, otherwise the script won't run. */
  , applies_to_role varchar(100) not null
  , exec_role       varchar(100) /* defaults to applies_to_role */
  , exec_channel    enum('SSH', 'JDBC') default 'SSH' not null
      /* How scripts are executed on the target. TODO: make it a table */
  , ...
);




/*
  linkages, connections, and roles
      linkage: dc/cloud/node/container
      replication: master/slave
      service dependencies
      shards

Node, runs DB and ZK
  roles: galera-cluster-node, replication-master, backup-master, zk-node, shard

  galera-node <- galera-cluster <- shard <- sharded-db <- app-service

  role: galera-node, zk-node, telegraph, shard

     app-service:
        database-service
        cache-service
        queue-service

     queue-service:
        kafka -> kafka-node
        tibco -> tibco-node

     cache-service
        couchbase -> node
          couchbase-console
        megha-ache


     database-service:
        oracle-server
        oracle-rac


     galera-node <- galera-cluster <- dr-cluster
     zk-node <- zk-cluster


  linkages and connectors


*/




/*
drop table if exists script_tags;
drop table if exists scripts;
drop table if exists script_stores;
drop table if exists tags;
*/

create table if not exists tags(
  tag_id          binary(16) not null
  ,   constraint  tags_pk primary key(tag_id)
  , parent_tag_id binary(16)
  ,   constraint  tags_fk1 foreign key(parent_tag_id) references tags(tag_id)
  , tag_name      varchar(255) not null
  ,   constraint  tags_uk1 unique(tag_name)
  , description   varchar(2000)
  /* --- standard fields */
  , created_by  varchar(100)
  , created_ts  timestamp default current_timestamp not null
  , modified_by varchar(100)
  , modified_ts timestamp default current_timestamp not null
);


create table if not exists script_stores(
  script_store_id binary(16)
  ,   constraint  script_stores_pk primary key(script_store_id)
  , store_name    varchar(255) not null
  ,   constraint  script_stores_uk1 unique(store_name)
  , store_type    enum('LocalFile', 'GitHub', 'Nexus') not null
  , description   varchar(2000)
  /* --- standard fields */
  , created_by  varchar(100)
  , created_ts  timestamp default current_timestamp not null
  , modified_by varchar(100)
  , modified_ts timestamp default current_timestamp not null
);


create table if not exists scripts(
  script_id      binary(16)
  ,   constraint script_pk primary key(script_id)
  , target_role  varchar(100) not null
  ,   constraint scripts_fk1 foreign key(target_role) references roles(name)
  , script_name  varchar(255) not null
  ,   constraint script_uk1 unique(script_name)
  , description  text
  , store_id     binary(16) not null
  ,   constraint scripts_fk2 foreign key(store_id) references script_stores(script_store_id)
  , url          varchar(2100) not null   /* 'amc:' prefix  */
  , uncompress   enum('Dont', 'zip') not null default 'No'
  , script_main  varchar(255)   /* to resolve ambiguity for scripts consistsing of multiple files */
  , interpreter  varchar(255)   /* what runs hte script: python, groovy, ruby, etc. Null for sh */
  , default_args varchar(2000)  /* added to the scipt call after the self-info url. space-separated, may be quoted */
  , become_user   varchar(255)
  , become_method enum('sudo', 'su')
  /* --- standard fields */
  , created_by   varchar(100)
  , created_ts   timestamp default current_timestamp not null
  , modified_by  varchar(100)
  , modified_ts  timestamp default current_timestamp not null
);

create table if not exists script_tags(
  script_id binary(16) not null
  ,   constraint script_tags_fk1 foreign key(script_id) references scripts(script_id)
  , tag_id binary(16) not null
  ,   constraint script_tags_fk2 foreign key(tag_id) references tags(tag_id)
  , constraint script_tags_pk primary key(script_id, tag_id)
  /* --- standard fields are not needed */
);
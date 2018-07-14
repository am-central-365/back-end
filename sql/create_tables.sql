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

drop table hierarchies;
drop table roles;

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

/*
   Role is one of the three fundamental tables (assets, roles, and attributes)

   An asset assumes one or more roles. Role defines a single function or property of an asset.
   Role examples are: a host (bare metal or virtual), Galera cluster, Oracle listener.
   Roles have attributes specific to them, say VM has hostname and ip addresses, memory size, etc.

   Attribute inheritance
     A role's attributes may be derived from another role, which's attributes are added to this
     role. Note that inheritance only applies to the list of attributes, not their values
     as the inheritance is effective at declaration-time. In case of name clash, the current role's
     attribute takes precedence.
     Examples of role inheritance: "host" could be a base for "bare-metal-box" and "virtual-machine".
     host may define attributes such as host-name and ip-addresses. bare-metal-box may additionally
     define location attribute, while  VM may have "hypervisor" attribute of type bare-metal-box.

   Misc notes:
     * Role attributes can be copied from another role.
     *
     * Role names are unique system-wide, if needed, use dash to break them into parts:
         galera-cluster, oracle-listener.
     * Scripts, or actions, are bound to roles
     * When 'is_script_target' is true, the asset posessing the role may execute scripts.
       For example, a "host" may execute scripts, while "oracle-listener" can't (but scripts
       managing the listener may be executed on amCentral box or on the box hosting the listener)
     * Currently, "class" column isn't used ande serves as a comment classifier
*/
create table roles(
  /*  role_id    binary(16) not null
  ,   constraint role_pk primary key(role_id)*/
    name         varchar(100) not null
  ,   constraint role_pk primary key(name)
  , class        varchar(100) not null   /* Not used, but explains the role to humans. */
  , attr_parent  varchar(100)
  ,   constraint roles_fk1 foreign key(parent_role) references roles(name)
  , description  text
  /* --- standard fields */
  , created_by   varchar(100)
  , created_ts   timestamp default current_timestamp not null
  , modified_by  varchar(100)
  , modified_ts  timestamp default current_timestamp not null
);

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

--truncate table roles;
insert into roles(class, name, attr_parent, description) values
  ('root',    'root',         null,   'The topmost role. The intention is that all other roles inherit from it.'),
  ('generic', 'host',        'root', 'A generic  version of a virtual machine'),
  ('generic', 'db-instance', 'root', 'A database instance running on a host: standalone or cluster node'),
  /* -- */
  ('virtualization', 'container',        'root',      'A light version of a virtual machine'),
  ('virtualization', 'hypervisor',       'host',      'Hosts, runs, and manages virtual machines'),
  ('virtualization', 'docker-container', 'container', 'A Docker container'),
  /* -- */
  ('application', 'site',           'root', 'Application service'),
  ('application', 'cluster',        'root', 'A generic cluster: App service, Web, Database, ZK, ...'),
  ('application', 'cluster-worker', 'host', 'A generic cluster worker node'),
  /* -- */
  ('database', 'galera-cluster', 'cluster',     'MySQL Galera cluster'),
  ('database', 'oracle-rac',     'cluster',     'Oracle Real Application Cluster'),
  ('database', 'oracle-db',      'db-instance', 'A generic standalone Oracle database'),
  ('database', 'mysql-db',       'db-instance', 'A generic standalone MySql database'),
  /* -- */
  ('oneops', 'oneops-org',       'root', 'OneOps Organization'),
  ('oneops', 'oneops-assembly',  'root', 'OneOps Assembly'),
  ('oneops', 'oneops-platform',  'root', 'OneOps Platform'),
  ('oneops', 'oneops-component', 'root', 'OneOps Component'),
  ('oneops', 'oneops-env',       'root', 'OneOps Environment'),
  ('oneops', 'oneops-dc',        'root', 'OneOps Data Center'),
  ('oneops', 'oneops-cloud',     'root', 'OneOps Cloud, also Availability Zone (AZ)')
;


create table hierarchies(
    name         varchar(100) not null
  ,   constraint hierarchy_pk primary key(name)
  , description   varchar(2000)
  /* --- standard fields */
  , created_by  varchar(100)
  , created_ts  timestamp default current_timestamp not null
  , modified_by varchar(100)
  , modified_ts timestamp default current_timestamp not null
);

insert into hierarchies(name, description) values
  ('site',              'Application ecosystem: workers, database, web servers, etc'),
  ('oneops-catalog',    'OneOps org/assembly/platform/component')
  ('oneops-deployment', 'OneOps org/assembly/environment/platform/component')
  ('oneops-location',   'OneOps hierarchy: data center, cloud (az), hypervisor, VM, container')
;
commit;
/* select * from hierarchies; */

create table hierarchy_roles(
    hierarchy_name varchar(100) not null
  ,   constraint hierarchy_roles_fk1 foreign key(hierarchy_name) references hierarchies(hierarchy_name)
  , role_name      varchar(100) not null
  ,   constraint hierarchy_roles_fk2 foreign key(role_name) references roles(role_name)
  ,   constraint hierarchy_roles_pk  primary key(hierarchy_name, role_name)
  , parent_role  varchar(100)
  ,   constraint hierarchy_roles_fk3 foreign key(hierarchy_name, parent_role) references hierarchy_roles(hierarchy_name, role_name)
  , level        int not null
  ,   constraint hierarchy_roles_ck1 check(level > 0)
  ,   constraint hierarchy_roles_ck2 check((level = 1 and parent_role is null) or (level > 1 and parent_role is not null))
  , description  varchar(2000)
);

insert into hierarchy_roles(hierarchy_name, role_name, level, parent_role) values
  ('site', 'application',  1, null),
  ('site', 'cluster',      2, 'application'),   /* this level may be skipped for non-clustered architectures */
  ('site', 'cluster-node', 3, 'cluster'),
  ('site', 'container',    4, 'cluster-node'),
  /* -- */
  ('oneops-deployment', 'oneops-org',       1),
  ('oneops-deployment', 'oneops-assembly',  2),
  ('oneops-deployment', 'oneops-env',       3),
  ('oneops-deployment', 'oneops-platform',  4),
  ('oneops-deployment', 'oneops-component', 5),
  /* -- */
  ('oneops-catalog', 'oneops-org',       1),
  ('oneops-catalog', 'oneops-assembly',  2),
  ('oneops-catalog', 'oneops-platform',  3),
  ('oneops-catalog', 'oneops-component', 4),
  /* -- */
  ('oneops-location', 'oneops-dc',    1),
  ('oneops-location', 'oneops-cloud', 2),
  ('oneops-location', 'hypervisor',   3)
;


create table assets(
  asset_id binary(16)
  ,   constraint asset_pk primary key(asset_id)
  /* --- standard fields */
  , created_by  varchar(100)
  , created_ts  timestamp default current_timestamp not null
  , modified_by varchar(100)
  , modified_ts timestamp default current_timestamp not null
);

create table asset_hierarchies(
  asset_id binary(16)
  , hierarchy_name varchar(100) not null
  /* -- */
  ,   constraint asset_hierarchy_pk  primary key(asset_id, hierarchy_name)
  ,   constraint asset_hierarchy_uk1 unique(hierarchy_name, asset_id)
  ,   constraint asset_hierarchies_fk1 foreign key(asset_id) references assets(asset_id)
  ,   constraint asset_hierarchies_fk2 foreign key(hierarchy_name) references hierarchies(hierarchy_name)
);

create table asset_hierarchy_role_val(
  asset_id binary(16)
  , hierarchy_name varchar(100) not null
  , role_name      varchar(100) not null
  ,   constraint asset_hierarchy_role_pk  primary key(asset_id, hierarchy_name, role_name)
  ,   constraint asset_hierarchy_role_fk1 foreign key(asset_id)       references assets(asset_id)
  ,   constraint asset_hierarchy_role_fk2 foreign key(hierarchy_name) references hierarchies(name)
  ,   constraint asset_hierarchy_role_fk3 foreign key(role_name)      references roles(name)
  , str_val     varchar(2000)
  , int_val     int
  , double_val  double
  , date_val    date
  , ts_val      timestamp
  , bool_val    boolean
  , text_val    text
  /* --- standard fields */
  , created_by  varchar(100)
  , created_ts  timestamp default current_timestamp not null
  , modified_by varchar(100)
  , modified_ts timestamp default current_timestamp not null
);

create table attributes(
  name varchar(100) not null
  ,  constraint attributes_pk  primary key(name)
  , attr_type    enum('Varchar', 'Int', 'Double', 'Date', 'Timestamp', 'Bool', 'Text')
  , decription   varchar(2000)
  /* --- standard fields */
  , created_by  varchar(100)
  , created_ts  timestamp default current_timestamp not null
  , modified_by varchar(100)
  , modified_ts timestamp default current_timestamp not null
);

create table role_attributes(
    role_name varchar(100) not null
  , attr_name varchar(100) not null
  ,  constraint role_attribute_pk  primary key(role_name, attr_name)  /* !!! how about multiple ip addresses? */
);


/*
drop table if exists obj_synonyms;
drop table if exists managed_objects;
drop table if exists physical_locations;
*/

create table if not exists physical_locations(
  physical_location_id binary(16)
  ,   constraint physical_locations_pk primary key(physical_location_id)
  , region varchar(100) not null  /* US East/West, Japan East, ... */
  , dc     varchar(100) not null  /* datacenter1, datacenter2, ... */
  , zone   varchar(100) not null  /* Availability zone: azA, azB, ... */
  /* --- standard fields */
  , created_by  varchar(100)
  , created_ts  timestamp default current_timestamp not null
  , modified_by varchar(100)
  , modified_ts timestamp default current_timestamp not null
);


create table if not exists managed_objects(
  managed_object_id binary(16)
  ,   constraint managed_objects_pk primary key(managed_object_id)
  , physical_location_id binary(16) not null
  ,   constraint managed_objs_fk1 foreign key(physical_location_id) references physical_locations(physical_location_id)
  , site      varchar(255) not null  /* prod-main, prod-dr, minimal-qa ... */
  , cluster   varchar(255) not null  /* db, web-serivce, queue, coordinator, metrics-storage, ... */
  , host      varchar(255) not null  /* db-node1, db-node2, web-service-worker1, ... */
  , container varchar(255)           /* Docker or other container running on the host */
  /* --- standard fields */
  , created_by  varchar(100)
  , created_ts  timestamp default current_timestamp not null
  , modified_by varchar(100)
  , modified_ts timestamp default current_timestamp not null
);


create table if not exists obj_synonyms(
  managed_obj_id binary(16)
  ,   constraint obj_synonyms_fk1 foreign key(managed_obj_id) references managed_objects(managed_object_id)
  , synonym_name varchar(255) not null
  ,   constraint obj_synonyms_pk primary key(managed_obj_id, synonym_name)
  /* --- standard fields */
  , created_by  varchar(100)
  , created_ts  timestamp default current_timestamp not null
  , modified_by varchar(100)
  , modified_ts timestamp default current_timestamp not null
);


/*
  Hierarchies, connections, and roles
      hierarchy: dc/cloud/node/container
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


  hierarchies and connectors


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
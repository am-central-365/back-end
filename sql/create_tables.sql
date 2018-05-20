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
use amcentral365;

drop table if exists obj_synonyms;
drop table if exists managed_objects;
drop table if exists physical_locations;


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


create table obj_synonyms(
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


drop table if exists script_tags;
drop table if exists scripts;
drop table if exists script_stores;
drop table if exists tags;

create table tags(
  tag_id          binary(16) not null
  ,   constraint tags_pk primary key(tag_id)
  , parent_tag_id binary(16)
  ,   constraint tags_fk1 foreign key(parent_tag_id) references tags(tag_id)
  , name          varchar(255) not null
  ,   constraint tags_uk1 unique(name)
  , description   varchar(2000)
  /* --- standard fields */
  , created_by  varchar(100)
  , created_ts  timestamp default current_timestamp not null
  , modified_by varchar(100)
  , modified_ts timestamp default current_timestamp not null
);


create table script_stores(
  script_store_id binary(16)
  ,   constraint script_stores_pk primary key(script_store_id)
  , store_type  enum('LocalFile', 'GitHub', 'Nexus') not null
  /* --- standard fields */
  , created_by  varchar(100)
  , created_ts  timestamp default current_timestamp not null
  , modified_by varchar(100)
  , modified_ts timestamp default current_timestamp not null
);


create table scripts(
  script_id      binary(16)
  ,   constraint script_pk primary key(script_id)
  , name         varchar(255) not null
  ,   constraint script_uk1 unique(name)
  , description  text
  , store_id binary(16) not null
  ,   constraint scripts_fk1 foreign key(store_id) references script_stores(script_store_id)
  , url          varchar(2100) not null   /* 'amc:' prefix  */
  , uncompress   enum('No', 'zip') not null default 'No'
  , script_main  varchar(255)   /* to resolve ambiguity for scripts consistsing of multiple files */
  , interpreter  varchar(255)   /* what runs hte script: python, groovy, ruby, etc. Null for sh */
  , sudo         boolean not null default false
  , target_type  enum('Site', 'Cluster', 'Host', 'Container') not null
  /* --- standard fields */
  , created_by   varchar(100)
  , created_ts   timestamp default current_timestamp not null
  , modified_by  varchar(100)
  , modified_ts  timestamp default current_timestamp not null
);

create table script_tags(
  script_id binary(16) not null
  ,   constraint script_tags_fk1 foreign key(script_id) references scripts(script_id)
  , tag_id binary(16) not null
  ,   constraint script_tags_fk2 foreign key(tag_id) references tags(tag_id)
  , constraint script_tags_pk primary key(script_id, tag_id)
  /* --- standard fields are not needed */
);

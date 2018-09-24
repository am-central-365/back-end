/**
  The Core Tables describe assets, roles, and their linkages.

  * assets represent anything manageable
  * roles are assigned to assets to reflect their nature
  * linkages list role relations

  the "declared_" tables only contain declarations.
  * declared_role_attributes specify what properties a role can have
  * declared_linkage_roles describe how roles relate to each other within linkage

  * asset_roles list roles assigned to a specific asset
  * asset_values assets are assigned roles, roles have attributes, this table lists
                 the attribute values
  * asset_linkages link assets to each other, as declared in declared_linkage_roles
*/

create table assets(
  asset_id bigint not null auto_increment
  ,   constraint assets_pk primary key(asset_id)
  , name        varchar(100) not null
  ,   constraint assets_uk unique(name)
  , description varchar(64000)
  /* --- standard fields */
  , created_by  varchar(100)
  , created_ts  timestamp default current_timestamp not null
  , modified_by varchar(100)
  , modified_ts timestamp default current_timestamp not null
);


create table roles(
    name         varchar(100) not null
  ,   constraint role_pk primary key(name)
  , class        varchar(100) not null   /* Not used, but explains the role to humans. */
  , role_schema  json not null
  , description  varchar(64000)
  /* --- standard fields */
  , created_by   varchar(100)
  , created_ts   timestamp default current_timestamp not null
  , modified_by  varchar(100)
  , modified_ts  timestamp default current_timestamp not null
);


/* -------------------------------------------------------------------------------------------- */

/*create table role_schemas(
    role_name    varchar(100) not null
  ,   constraint role_schemas_fk1 foreign key(role_name) references roles(name)
  , schema_ver   int not null
  ,   constraint role_schemas_ck1 check(schema_ver > 0)
  ,   constraint role_schemas_pk primary key(role_name, schema_ver)
  , role_schema  json not null
  , schema_crc   int not null
  ,   index schema_crc_idx(role_name, schema_crc)
  , description  varchar(64000)
  *//* --- standard fields *//*
  , created_by   varchar(100)
  , created_ts   timestamp default current_timestamp not null
  , modified_by  varchar(100)
  , modified_ts  timestamp default current_timestamp not null
);*/



/* -------------------------------------------------------------------------------------------- */

/*create table asset_roles(      *//* roles assigned to an asset. /*
    asset_id     bigint       not null
  ,   constraint asset_roles_fk1 foreign key(asset_id) references assets(asset_id)
  , role_name    varchar(100) not null
  , schema_ver   int not null
  ,   constraint asset_roles_fk2 foreign key(role_name, schema_ver) references role_schemas(role_name, schema_ver)
  ,   constraint asset_roles_pk primary key(asset_id*//*, linkage_name*//*, role_name)
);*/


create table asset_values(    /* the biggest table: attribute values for specific role of a specific asset */
    asset_id     bigint not null
  ,   constraint asset_values_fk1 foreign key(asset_id) references assets(asset_id)
  , role_name    varchar(100) not null
  ,   constraint asset_values_pk primary key(asset_id, role_name)
  , asset_vals   json not null
  /* --- standard fields */
  , created_by  varchar(100)
  , created_ts  timestamp default current_timestamp not null
  , modified_by varchar(100)
  , modified_ts timestamp default current_timestamp not null
);


/*create table asset_values(    *//* the biggest table: attribute values for specific role of a specific asset *//*
    asset_id     bigint   not null
  ,   constraint asset_values_fk1 foreign key(asset_id) references assets(asset_id)
  , role_name    varchar(100) not null
  ,   constraint asset_values_fk2 foreign key(asset_id, role_name) references asset_roles(asset_id, role_name)
  , attr_name    varchar(200) not null
  ,  constraint asset_values_pk primary key(asset_id, role_name, attr_name)
  *//* A question: shall we honor data types, or store everything as varchar and let the app layer handle them? *//*
  , str_val   varchar(64000)
  , num_val   decimal(65,30)
  , bool_val  boolean
  *//* sub-objects and arrays reference their parent *//*
  , parent_attr  varchar(200)
  , arr_pos      int
  ,   constraint asset_values_fk3 foreign key(asset_id, role_name, parent_attr) references asset_values(asset_id, role_name, attr_name)
  *//*,   constraint asset_values_ck1 check(coalesce(str_val, num_val, bool_val, parent_attr, arr_pos) is not null)*//*
  *//* --- standard fields *//*
  , created_by  varchar(100)
  , created_ts  timestamp default current_timestamp not null
  , modified_by varchar(100)
  , modified_ts timestamp default current_timestamp not null
);*/


create table linkages(
    name         varchar(100) not null
  ,   constraint linkages_pk primary key(name)
  , description  varchar(64000)
  /* --- standard fields */
  , created_by  varchar(100)
  , created_ts  timestamp default current_timestamp not null
  , modified_by varchar(100)
  , modified_ts timestamp default current_timestamp not null
);


create table declared_linkage_roles(    /* how roles are organized within linkages: real linkgaes are in asset_linkages */
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


create table asset_linkages(  /* how assets relate to each other. dlrX_id must belong to the same linkage of declared_linkage_roles */
    linkage_name varchar(100)  not null
  ,   constraint asset_linkages_fk1 foreign key(linkage_name) references linkages(name)
  , asset1_id    bigint        not null
  ,   constraint asset_linkages_fk2 foreign key(asset1_id) references assets(asset_id)
  , asset2_id    bigint        not null
  ,   constraint asset_linkages_fk3 foreign key(asset1_id) references assets(asset_id)
  , dlr1_id      int           not null
  , dlr2_id      int           not null
  , sibling1_pos int default 0 not null
  , sibling2_pos int default 0 not null
  ,   constraint   asset_linkages_uk1 unique(linkage_name, asset1_id, dlr1_id, sibling1_pos, asset2_id, dlr2_id, sibling2_pos)
  ,   unique index asset_linkages_uk2       (linkage_name, asset2_id, dlr2_id, sibling2_pos, asset1_id, dlr1_id, sibling1_pos)
);

/*
select * from roles;
select hex(asset_id), name, description from assets;
select hex(asset_id), role_name from asset_roles;
select * from asset_linkages;
select * from declared_role_attributes;
select hex(asset_id), t.* from asset_values t;

select * from linkages;
select * from declared_linkage_roles;

select * from declared_linkage_roles;

*/

/* ============ */

drop table tj;
create table tj(n int auto_increment primary key, jsn json);
select * from tj;
delete  from tj;
insert into tj(jsn) values
  ('{ "a": [1, 4    ], "b": { "b1":  true, "b2": "zellers"}, "c": "alan"}'),
  ('{ "a": [2, 8, 24], "b": { "b1": false, "b2": "tj-max"},  "c": "joe"}'),
  ('{ "a": [4, 2,  4], "b": { "b1":  true, "b2": "sears"},   "c": "fred"}')
;
select n, jsn from tj;
select n, concat('x', jsn->'$.c') from tj where not jsn->'$.b.b1';
select n, convert(jsn->'$.c', char) from tj;
select n, convert(jsn, char) from tj where not jsn->'$.b.b1';
select n, jsn->'$.c' from tj where json_search(jsn, 'all', 'sears', null, '$') is not null;
select n, json_unquote(jsn->'$.a[1]') from tj;
select json_array(1, 4, 'x');

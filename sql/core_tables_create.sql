/**
  The Core Tables describe assets, roles, and their linkages.

  * assets   represent anything manageable
  * roles    carry attributes. An asset may have multiple roles.
  * linkages determine relations between assets, they are bound to roles.

  the "declared_" tables only contain declarations.
  * declared_role_attributes specify what properties a role can have
  * declared_linkage_roles describe how roles relate to each other within linkage

  * asset_roles list roles assigned to a specific asset
  * asset_role_values assets are assigned roles, roles have attributes, this table lists
                 the attribute values
  * asset_linkages link assets to each other, as declared in declared_linkage_roles
*/

create table if not exists roles(
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


create table if not exists assets(
  asset_id binary(16) not null
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

create table if not exists asset_role_values(    /* the biggest table: attribute values for specific role of a specific asset */
    asset_id     binary(16) not null
  ,   constraint asset_role_values_fk1 foreign key(asset_id) references assets(asset_id)
  , role_name    varchar(100) not null
  ,   constraint asset_role_values_pk primary key(asset_id, role_name)
  , asset_vals   json not null
  /* --- standard fields */
  , created_by  varchar(100)
  , created_ts  timestamp default current_timestamp not null
  , modified_by varchar(100)
  , modified_ts timestamp default current_timestamp not null
);

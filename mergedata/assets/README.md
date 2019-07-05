# Well known assets

Content of the file can be a single `asset` object, or an array of such objects.

The intended use for the array is to group related elements. All assets
in the file are processed as a single set: failure of one cancels
processing of all assets in the file. Their merge is handled in the same
database transaction and the transaction is rolled back on any error.
 
Each asset is structured like below:

```
  {
    "asset": { "assetId": "...", "name": "...", "description": "..." },
    "roles": [
      {
        "roleName": "...",
        "values": { ... }
      }
    ]
  }
```
Both `assetId` and `name` are optional.

When `assetId` missing, it is fetched from the database by its `name`.
If missing in the database, the id is generated. 

When `name` is omitted, it is fetched from the database by `assetId`.
When not in the database, it is constructed from the file name and its
path like Java packages: `com/google/static/name.json` translates to
asset name `com/google/static/name`.
<br>&nbsp;&nbsp;&nbsp;&nbsp;!!! FIXME: Currently a lie, "name" attribute
is required on insert.

When both `assetId` and `name` are specified and the `name` differs from
the one in the database, the database's `name` is changed.
The opposite is not true: having same `name` but `assetId` differing
in the file and in the database is considered an error. That is because
`assetId` is treated as the "primary key" of the asset.  

`roles` may be missing, assets without roles are allowed.


## Stale data
The merge is never deleting any roles present in the database, but not
mentioned in the file. In other words, only database INSERT and UPDATE
are performed, no DELETEs.

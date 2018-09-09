Google Cloud Platform definitions
=================================

Real definitions used by GCP.
The defiitions where slightly modified for amCentral schema syntax.

* "enum" types were replaced with array of strings with the real enum values
* { "key": string", "value": string" } were replaced with "map" type.

Source:
https://cloud.google.com/deployment-manager/docs/configuration/supported-resource-types


## Schema definition

{
  "attribute_name": "type" | "enum-values"
  "compound_attribute": {
     ...
  }
}

The types are:
* `string`    max length 64000
* `number`    integer and decimal
* `boolean`   true | false
* `map`       a special type of object {key1: value1, key2: value2, ...}.
              Both keys and values are strings.


* \[enum-val1, enum-val2, ...\] denotes an enum with specified values.
             The values must be strings.
* { ... }    nested sub-object
* "@schema"  reference to an external schema definition.
             Schemas may reference self.

### Attributes


Base types (string, number, boolean, map) and references may be suffixed
with special symbols for `required`, `multiple`, and `indexed`
attributes: '!', '+' and '^'.

E.g:
- `string`:   denotes an optional single value
- `string!`:  denotes a required single value
- `string+`:  dentotes an optional multi-value
- `string!+`: denotes a mandatory multi value
- `string+!`: is the same as `string!+`.
- `string^!`: denotes a required indexed value.

Example:
```
{
  "x": "string!"   -- 'x' is a required string attribute
, "y": "number+"   -- 'y' is an optional array of numbers
, "z": "@disk!+"   -- 'z' is a mandatory array of objects defined by role 'disk'
}
```

NB: The term `mandatory array` merely means the member must be present. The array may be empty.


`enum` attributes may be specified as the first enum member. I.e.
```
  "x": ["!", "ONE", "TWO"]   -- the enum value is mandatory
  "y": ["ONE", "TWO", "!"]   -- invalid, not the first member
```

Note:
Attributes may be specified in arbitrary order, but each attribute may
only appear once. I.e. "string!!" is illegal.
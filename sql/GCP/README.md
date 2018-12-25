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

### Attribute name
is a string.

Characters to avoid in names are: '$', '.', '[', ']', '\0'
(dollar sign, dot, square brackets, and the null character)

Characters allowed but not recommended are the ones used in
type attributes: '@', '!', '*', '+', '^'. While code handles
them without problems, they may confuse the human readers.

### Type
Recognized types:
* `string`    max length 64000
* `number`    integer and decimal
* `boolean`   true | false
* `map`       a special type of object {key1: value1, key2: value2, ...}.
              Both keys and values are strings.


* `[enum-val1, enum-val2, ...]` denotes an enum with specified values.
             The values must be strings.
* `{ ... }`  nested sub-object.
* `@schema`  reference to an external schema definition.
             There must be no circular references.

### Attributes
Base types (string, number, boolean, map) and references may be suffixed
with special symbols '!', '*', '+' and '^' for `required`, `zero-or-more`,
`one-or-more`, and `indexed` respectively.

* `!` means the element must be present and can't be null
* `*` means the element is an array, possibly empty
* `+` a non-empty array. But unless `!` was specified, the entire attribute may missing or null
* `^` will be "indexed", but currently ignored.

E.g:
- `string`:   denotes an optional single string value
- `string!`:  denotes a required single string value
- `string+`:  define either a null, or an array with one or more elements.
- `string!*`: the array element must be present, but can be empty
- `string!+`: is a mandatory array with one or more elements
- `string+!`: is the same as `string!+`.
- `string^!`: denotes a required indexed value.

Example:
```
{
  "x": "string!"   -- 'x' is a required string attribute
, "y": "number+"   -- 'y' is an optional array of one or more numbers
, "z": "@disk!*"   -- 'z' is a mandatory, possibly empty array of objects defined by role 'disk' (which isn't very practical)
}
```

`enum` attributes may be specified as the first enum member. I.e.
```
  "x": ["!", "ONE", "TWO"]   -- the enum value is mandatory
  "y": ["ONE", "TWO", "!"]   -- invalid, not the first member
  "z": ["!+", "ONE", "TWO"]  -- an array of one or more enums
```

Attributes of a nested sub-object are specified with a special element
wit hname `_attr`. In `{ "x": { "_attr": "!+", "y": "number", "z": "string!" } }`,
`x` is a required non-empty array of `{y,z}` structures.

Note:
Attributes may be specified in arbitrary order, but each attribute may
only appear once. Definitions like "string!!" are illegal.

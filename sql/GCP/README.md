Google Cloud Platform definitions
=================================

Real definitions used by GCP.
The defiitions where slightly modified for amCentral schema syntax.

* "enum" types were replaced with array of strings with the real enum values
* { "key": string", "value": string" } were replaced with "map" type.

Source:
https://cloud.google.com/deployment-manager/docs/configuration/supported-resource-types


Schema definition
-----------------

{
  "attribute_name": "type" | "enum-values"
  "compound_attribute": {
     ...
  }
}

The types are:
* string     max length 64000
* number     integer and decimal
* boolean    true | false
* map        a special type of object {key1: value1, key2: value2, ...}.
             Both keys and values are strings.
* \[enum-val1, enum-val2, ...\] denotes an enum with specified values
* { ... }    nested sub-object
* "@schema"  reference to an external schema definition

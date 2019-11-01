Json Naming Conventions
===

#### Names

All names are CamelCase.
* No underscores: ```{"idle_timeout_sec": 30}``` - bad
* No dashes: ```{"idle-timeout-sec": 30}``` - bad
* Camelcase: ```{"idleTimeoutSec": 30}``` - good

That's because objects are mapped to classes with reflection.

#### Values
Role names and other values may use dashes:
```{"roleName": "windows-host"}``` - ok


#### Semantic

Names of numeric attributes must include their dimension:
* `taskPollIntervalMsec`
* `scriptExecutionTimeoutSec`
* `maxRoleNameLen`
* `initialWorkerPoolSize`

Note `Sec`, `Msec`, `Len`, `Size`, `Count`, and the like.

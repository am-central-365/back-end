Json Naming Conventions
===

#### Names

All names are CamelCase.
* No underscores: ```{"idle_timeout_sec": 30}``` - bad
* No dashes: ```{"idle-timeout-sec": 30}``` - bad
* Camelcase: ```{"idleTimeoutSec": 30}``` - good


#### Values
Role names and other values amy use dashes:
```{"roleName": "windows-host"}``` - ok


#### Semantic

Names of all numeric values must include the dimension:
* `taskPollIntervalMsec`
* `scriptExecutionTimeoutSec`
* `maxRoleNameLen`
* `initialWorkerPoolSize`

Note `Sec`, `Msec`, `Len`, `Size`, `Count`, and the like.

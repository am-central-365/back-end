AM Central 365 back-end
===

Build
---
`mvn package`

Note:
when building for the first time or running with "clean" option, the
build fails on tests, complaining it can't find ssh-key.pub. Skip the
tests for the first time:

`mvn -Dmaven.test.skip=true package`

Subsequent builds (w/o "clean") run fine.

Run
---
`java -jar target/am-central-tbd.jar`

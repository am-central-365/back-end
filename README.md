AM Central 365 back-end service
===============================

Project status: under development

AM Central-365 is a DevOps service. Its approach is to assist without getting in the way. Existing automation scripts can be shared and used by others. Or not: run ad-hoc commands on selectd targets. There is no markup language or metadata, scripts are written on any language supported by the host running it. If a script needs to learn about its surroundings, it can make a REST call to amCentral. Sometimes it make sense to run scripts outside of the targets, amCentral365 supports that too. There is a built-in scheduler to run periodic jobs. Scripts may request amCentral to run other scripts for code reuse, this also allows automated self healing by reacting to events.


Build
-----
```
mvn package
gradle build
```

Note:
when building for the first time or running with `clean` option, the
build fails on tests, complaining it can't find ssh-key.pub. Skip the
tests for the first time:

`mvn -Dmaven.test.skip=true package`

Subsequent builds (w/o "clean") run fine.

Run
---
The command takes various commdn-line options. Run it with `--help`
for the list:

`java -jar target/am-central-service.jar --help`

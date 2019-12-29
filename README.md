# AM Central 365 back-end service

Project status: under development

AM Central-365 is a DevOps service. Its approach is to assist without getting in the way. Existing automation scripts can be shared and used by others. Or not: run ad-hoc commands on selectd targets. There is no markup language or metadata, scripts are written on any language supported by the host running it. If a script needs to learn about its surroundings, it can make a REST call to amCentral. Sometimes it make sense to run scripts outside of the targets, amCentral365 supports that too. There is a built-in scheduler to run periodic jobs. Scripts may request amCentral to run other scripts for code reuse, this also allows automated self healing by reacting to events.

## Configuring the developement environment
#### Library dependencies

The project needs library `pl4kotlin` which isn't found in public repos yet.
Get it from github and build:
   https://github.com/am-central-365/pl4kotlin

The other dependencies are downloaded automatically by `gradle` or `maven`

#### Gradle/Maven
The project was tested with Gradle 6.x. It may be incompatible with gradle 7.
Maven 3+ can also be used, but limited to building the jar and running the unit tests.

#### Docker and docker-compose
Used to start a development database. You may ignore it if you already have
a running `mariadb` instance.

## Building

##### Gradle (preferred)
```
gradle build
```

##### Maven
```
mvn package
```

## Running
### Creating and starting the database
#### Using a custom database
If you already have a working `mariadb` installation 10.2 or later,
create a user and a database. Grant all privileges on the database to
the user. Run scripts under `sql/schema` in alphabetical order.

#### Using the pre-configured database
If you have `docker` and `docker-compose` installed, you may chose to
use a prepared database.

At command prompt, navigate to `site/development` and run
```
$ docker-compose up
```
The database container is created and keeps running until you Ctrl-C or
close the terminal window.

Connection parameters are already hard-coded as the defaults, maeaning
you don't needd to specify user name, password, and the connection string  
when lauching the service.

### Launching the am-central365 service
For development, the recommended options are

* --develop      (makes changes to the Swagger API visible without rebuild)
* --merge-roles  (automatically merge data under `mergedata`)
* --merge-assets

```
$ java -jar ~the-amcentral365-service-jar~ --develop --merge-roles --merge-assets  
```

When used a custom database, additionally specify connection parameters:
* --user
* --pass
* --conn

##### Other command line arguments
The command takes various command-line options. Run it with `--help`
for the list:

```
java -jar ~the-amcentral365-service-jar~ --help
```

# Development database
Create and run Docker container with the amcentral database.
The database is needed for development.

To intialize (if necessary) and start it, use
```
$ docker-compose up
```
Naturally, you must have Docker and docker-compose installed first.

This creates and starts two containers:

0. [MariaDB 10.4](https://hub.docker.com/_/mariadb)  with user `amc` and password `a` and database `amcentral365`.
   The schema objects are also created.
0. [Adminer](https://hub.docker.com/r/amd64/adminer/) - GUI tool to browse and manage the tables.
   This container isn't needed for development, it is listed for convenience. Feel fre to remove
   it from `docker-compose.yml` file if using another tool.

`amCentral-365` needs some preloaded data, make sure to configure your IDE
to pass paramtere `--merge-roles` and `--merge-assets`. This automatically
populates/updates any roles and assets found under `~project-home~/mergedata`

## Shutdown
docker-compose automatically stops the contaier when you hit `Ctrl-C`
in the terminal running it. Or, use `docker-compose down`.

## Starting fresh
Stop and remove the container, and all files under `db-files`
```
$ docker-cmoposer down
$ sudo rm -rf db-files/*
```
If `db-files` directory is removed, it is auto-created by the Compose.

## More info
Beside the MariaDB config file, there is nothing custom about this
configuration. [docker-compose](https://docs.docker.com/compose/) site
has more details/

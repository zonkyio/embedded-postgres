# <img src="zonky.jpg" height="100"> Embedded Postgres

## Introduction

This project is a fork of [OpenTable Embedded PostgreSQL Component](https://github.com/opentable/otj-pg-embedded) created due to inactivity of maintainers.

The library allows embedding PostgreSQL into Java application code with no external dependencies.
Excellent for allowing you to unit test with a "real" Postgres without requiring end users to install and set up a database cluster.

If you are using `Spring` or `Spring Boot` framework you can also consider using the following more specialized [embedded-database-spring-test](https://github.com/zonkyio/embedded-database-spring-test) project.

## Features

* All features of `com.opentable:otj-pg-embedded:0.13.3`
* Configurable version of [PostgreSQL binaries](https://github.com/zonkyio/embedded-postgres-binaries)
* PostgreSQL 11+ support even for Linux platform
* Support for running inside Docker, including Alpine Linux

## Maven Configuration

Add the following Maven dependency:

```xml
<dependency>
    <groupId>io.zonky.test</groupId>
    <artifactId>embedded-postgres</artifactId>
    <version>1.2.6</version>
    <scope>test</scope>
</dependency>
```

The default version of the embedded postgres is `PostgreSQL 10.11`, but you can change it by following the instructions described in [Postgres version](#postgres-version).

## Basic Usage

In your JUnit test just add:

```java
@Rule
public SingleInstancePostgresRule pg = EmbeddedPostgresRules.singleInstance();
```

This simply has JUnit manage an instance of EmbeddedPostgres (start, stop). You can then use this to get a DataSource with: `pg.getEmbeddedPostgres().getPostgresDatabase();`  

Additionally you may use the [`EmbeddedPostgres`](src/main/java/io/zonky/test/db/postgres/embedded/EmbeddedPostgres.java) class directly by manually starting and stopping the instance; see [`EmbeddedPostgresTest`](src/test/java/io/zonky/test/db/postgres/embedded/EmbeddedPostgresTest.java) for an example.

Default username/password is: postgres/postgres and the default database is 'postgres'

## Migrators (Flyway or Liquibase)

You can easily integrate Flyway or Liquibase database schema migration:
##### Flyway
```java
@Rule 
public PreparedDbRule db =
    EmbeddedPostgresRules.preparedDatabase(
        FlywayPreparer.forClasspathLocation("db/my-db-schema"));
```

##### Liquibase
```java
@Rule
public PreparedDbRule db = 
    EmbeddedPostgresRules.preparedDatabase(
            LiquibasePreparer.forClasspathLocation("liqui/master.xml"));
```

This will create an independent database for every test with the given schema loaded from the classpath.
Database templates are used so the time cost is relatively small, given the superior isolation truly
independent databases gives you.

## Postgres version

The default version of the embedded postgres is `PostgreSQL 10.11`, but it can be changed by importing `embedded-postgres-binaries-bom` in a required version into your dependency management section.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.zonky.test.postgres</groupId>
            <artifactId>embedded-postgres-binaries-bom</artifactId>
            <version>11.6.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

<details>
  <summary>Using Maven BOMs in Gradle</summary>
  
  In Gradle, there are several ways how to import a Maven BOM.
  
  1. You can define a resolution strategy to check and change the version of transitive dependencies manually:
  
         configurations.all {
              resolutionStrategy.eachDependency { DependencyResolveDetails details ->
                  if (details.requested.group == 'io.zonky.test.postgres') {
                     details.useVersion '11.6.0'
                 }
             }
         }
  
  2. If you use Gradle 5+, [Maven BOMs are supported out of the box](https://docs.gradle.org/5.0/userguide/managing_transitive_dependencies.html#sec:bom_import), so you can import the bom:
  
         dependencies {
              implementation enforcedPlatform('io.zonky.test.postgres:embedded-postgres-binaries-bom:11.6.0')
         }
  
  3. Or, you can use [Spring's dependency management plugin](https://docs.spring.io/dependency-management-plugin/docs/current/reference/html/#dependency-management-configuration-bom-import) that provides Maven-like dependency management to Gradle:
  
         plugins {
             id "io.spring.dependency-management" version "1.0.6.RELEASE"
         }
         
         dependencyManagement {
              imports {
                   mavenBom 'io.zonky.test.postgres:embedded-postgres-binaries-bom:11.6.0'
              }
         }

</details><br/>

A list of all available versions of postgres binaries is here: https://mvnrepository.com/artifact/io.zonky.test.postgres/embedded-postgres-binaries-bom

Note that the release cycle of the postgres binaries is independent of the release cycle of this library, so you can upgrade to a new version of postgres binaries immediately after it is released.

## Additional architectures

By default, only the support for `amd64` architecture is enabled.
Support for other architectures can be enabled by adding the corresponding Maven dependencies as shown in the example below.

```xml
<dependency>
    <groupId>io.zonky.test.postgres</groupId>
    <artifactId>embedded-postgres-binaries-linux-i386</artifactId>
    <scope>test</scope>
</dependency>
```

**Supported platforms:** `Darwin`, `Windows`, `Linux`, `Alpine Linux`  
**Supported architectures:** `amd64`, `i386`, `arm32v6`, `arm32v7`, `arm64v8`, `ppc64le`

Note that not all architectures are supported by all platforms, look here for an exhaustive list of all available artifacts: https://mvnrepository.com/artifact/io.zonky.test.postgres
  
Since `PostgreSQL 10.0`, there are additional artifacts with `alpine-lite` suffix. These artifacts contain postgres binaries for Alpine Linux with disabled [ICU support](https://blog.2ndquadrant.com/icu-support-postgresql-10/) for further size reduction.

## Troubleshooting

### Process [/tmp/embedded-pg/PG-XYZ/bin/initdb, ...] failed

Check the console output for an `initdb: cannot be run as root` message. If the error is present, try to upgrade to a newer version of the library (1.2.8+), or ensure the build process to be running as a non-root user.

If the error is not present, try to clean up the `/tmp/embedded-pg/PG-XYZ` directory containing temporary binaries of the embedded database. 

### Running tests on Windows does not work

You probably need to install [Microsoft Visual C++ 2013 Redistributable Package](https://support.microsoft.com/en-us/help/3179560/update-for-visual-c-2013-and-visual-c-redistributable-package). The version 2013 is important, installation of other versions will not help. More detailed is the problem discussed [here](https://github.com/opentable/otj-pg-embedded/issues/65).

### Running tests in Docker does not work

Running builds inside a Docker container is fully supported, including Alpine Linux. However, PostgreSQL has a restriction the database process must run under a non-root user. Otherwise, the database does not start and fails with an error.  

So be sure to use a docker image that uses a non-root user. Or, since version `1.2.8` you can run the docker container with `--privileged` option, which allows taking advantage of `unshare` command to run the database process in a separate namespace.

Below are some examples of how to prepare a docker image running with a non-root user:

<details>
  <summary>Standard Dockerfile</summary>
  
  ```dockerfile
  FROM openjdk:8-jdk
  
  RUN groupadd --system --gid 1000 test
  RUN useradd --system --gid test --uid 1000 --shell /bin/bash --create-home test
  
  USER test
  WORKDIR /home/test
  ```

</details>

<details>
  <summary>Alpine Dockerfile</summary>
  
  ```dockerfile
  FROM openjdk:8-jdk-alpine
  
  RUN addgroup -S -g 1000 test
  RUN adduser -D -S -G test -u 1000 -s /bin/ash test
  
  USER test
  WORKDIR /home/test
  ```

</details>

## License
The project is released under version 2.0 of the [Apache License](http://www.apache.org/licenses/LICENSE-2.0.html).

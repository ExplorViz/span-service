# ExplorViz Span-Service

Scalable service that processes, persists, aggregates and queries the observed traces of method executions within
monitored software applications.

## Prerequisites

- Java 17 or higher
- Make sure to run the [ExplorViz software stack](https://git.se.informatik.uni-kiel.de/ExplorViz/code/deployment)
  before starting the service, as it provides the required database(s) and the Kafka broker

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./gradlew quarkusDev
```

This also enables the `dev` configuration profile, i.e. using the properties prefixed with `%dev` from
`src/main/resources/application.properties`.

**_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged and tested using:
```shell script
./gradlew build
```
It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory.

You can skip running the integration tests by adding `-x integrationTest`. To skip all tests and code analysis use the
`assemble` task instead of `build`.

The application is now runnable using `java -jar build/quarkus-app/quarkus-run.jar`.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `build/quarkus-app/lib/` directory.

If you want to build an _über-jar_, which includes the entire application in a single jar file, execute the following
command:
```shell script
./gradlew build -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using
`java -jar build/span-service-1.0-SNAPSHOT-runner.jar`.
You can add `-Dquarkus.profile=dev` to enable the `%dev` properties.

## Creating a native executable

You can create a native executable using:
```shell script
./gradlew build -Dquarkus.package.type=native
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:
```shell script
./gradlew build -Dquarkus.package.type=native -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./build/span-service-1.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult
https://quarkus.io/guides/gradle-tooling#building-a-native-executable.

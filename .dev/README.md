# Docker compose stack

This directory contains a small Docker compose stack for local development purposes. It provides an instance of the [OTel Collector](https://github.com/ExplorViz/otel-collector), the required ClickHouse database, and the [trace-generator](https://github.com/ExplorViz/trace-generator) development utility.

To run the compose stack, ensure Docker is correctly installed on your system, navigate into this directory, and run:

```shell
docker compose up
```

You can then access http://localhost:8079 to generate a landscape and populate the database with example data.

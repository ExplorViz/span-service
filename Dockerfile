FROM cassandra:3.11.14

COPY src/main/resources/init_script.cql ./init.cql

## To test with maven

The maven plugin will assign random high port numbers to the containers that have been started.
```
mvn install
```

## To run from the command line or in a debugger

### Make sure that the war is up to date
```
mvn install -Ddocker.skip -DskipITs -Dmaven.test.skip=true
```

### Launch Containers
```
MDIR=$(pwd) docker compose -f audit-it/src/test/docker/docker-compose.yml up -d
```

### Run Tests (command line or in VSCode)

Run a single test by method name
```bash
mvn -f audit-it/pom.xml failsafe:integration-test failsafe:verify -Dit.test=ServiceDriverIT#testJsonState
```

Run all tests
```bash
mvn -f audit-it/pom.xml failsafe:integration-test failsafe:verify -Dit.test=ServiceDriverIT
```

### Stop Containers
```
MDIR=$(pwd) docker compose -f audit-it/src/test/docker/docker-compose.yml down
```

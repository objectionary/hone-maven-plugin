# Stream rules on benchmark class

In this integration test, we apply stream rules to test benchmark java class.
This test case is executed by the `invoker-maven-plugin` in the main pom.xml`.

In order to run this test only use the following command:

```sh
mvn clean integration-test -Dinvoker.test=bench -DskipTests
```

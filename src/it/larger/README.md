# A Larger Use Case

In this integration test, we apply our optimizations to a
larger Java program and its unit tests. This test case is executed by the
`invoker-maven-plugin` in the main pom.xml`.

In order to run this test only use the following command:

```sh
mvn clean integration-test -Dinvoker.test=larger -DskipTests
```

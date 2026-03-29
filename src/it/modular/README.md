# Modular Project

In this integration test we check how `hone-maven-plugin` works with
multi-modular java project. Especially, we are interested in statistics:

https://github.com/objectionary/hone-maven-plugin/issues/440

In order to run this test only use the following command:

```sh
mvn clean integration-test -Dinvoker.test=modular -DskipTests
```

## Example: CSV

This example shows how to generate test cases for fuzzed tests. It fuzzes [opencsv](http://opencsv.sourceforge.net/)'s
`CSVReader` and then builds `JUnit 4` tests that can then be executed. The test writer needs the same version of
[javapoet](https://github.com/square/javapoet) that JWP uses. A version of the tests have been committed as
[MainTest.java](src/test/java/jwp/examples/csv/MainTest.java) with ~550 tests. To write a new version, run the following
from the JWP repo root:

    path/to/gradle --no-daemon :examples:csv:run

Since this requires a build of the project, those previous tests will be run. This will output new paths found in
addition to writing a new `MainTest.java` with them. To run the generated tests, run:

    path/to/gradle --no-daemon :examples:csv:test
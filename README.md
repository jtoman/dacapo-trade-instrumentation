## Requirements

Any recent version of Gradle.

## Building

Simply run `gradle shadowJar` in the root of the project. This will produce `build/libs/dacapo-trade-instrumentation-all.jar` which can be used as a Java agent (see below).

## Running

To run the Tradesoap benchmark with breaking instrumentation, execute:

```bash
java -javaagent:./build/libs/dacapo-trade-instrumentation-all.jar=clinit -jar /path/to/dacapo-9.12-MR1-bach.jar tradesoap
```
This inserts empty static initializers into classes that do not have one. To add an empty
public method instead, replace `clinit` with `pub-method`. To insert empty dummy
private methods which do _not_ break serialization, replace `clinit` with `priv-method`.

To run the Tradesoap benchmark with instrumentation and a precomputed serial UID, execute:

```bash
java -javaagent:./build/libs/dacapo-trade-instrumentation-all.jar=clinit,fix -jar /path/to/dacapo-9.12-MR1-bach.jar tradesoap
```
As above, `clinit` may be replaced with `pub-method` to instead insert an empty, public instance
method.

In the above examples, `tradesoap` may be substituted with `tradebeans`.

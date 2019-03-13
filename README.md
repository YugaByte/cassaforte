# Clojure client for YugaByte DB's Cassandra compatible YCQL API

A Clojure client for YugaByte DB that supports Cassandra Query Language and wire protocol derived from Cassaforte, which is a small, easy to use Clojure client for Apache Cassandra (2.0+) built around CQL 3.

For quickstart, please refer to [Getting Started with Clojure and Cassandra](http://clojurecassandra.info/articles/getting_started.html)
guide, but use `com.yugabyte/cassaforte` as a dependency.

## Project Goals

 * Provide a Clojure-friendly, easy to use API that reflects Cassandra's data model well. Dealing with the Cassandra Thrift API quirks is counterproductive.
 * Be well maintained.
 * Be well documented.
 * Be well tested.
 * Target modern Cassandra and Clojure releases.
 * Integrate with libraries like Joda Time.
 * Support URI connections to be friendly to Heroku and other PaaS providers.



## Project Maturity

Cassaforte is a moderately mature project. Started in June 2012, it
has reached `1.0` in July 2013 and `2.0` in December 2014.  It is
known to be used by dozens of companies, small and large.

Cassaforte is based on the [YugaByte Java Driver for YugaByte DB's Cassandra compatible YCQL API](https://github.com/YugaByte/cassandra-java-driver)
as well as [Hayt](https://github.com/mpenet/hayt), a battle tested CQL generation DSL library.



## Dependency Information (Artifacts)

Cassaforte artifacts are [released to Clojars](https://clojars.org/com.yugabyte/cassaforte). If you are using Maven, add the following repository
definition to your `pom.xml`:

```xml
<repository>
  <id>clojars.org</id>
  <url>http://clojars.org/repo</url>
</repository>
```

### The Most Recent Version

With Leiningen:

``` clojure
[com.yugabyte/cassaforte "3.0.0-alpha2-yb-1"]
```

With Maven:

``` xml
<dependency>
  <groupId>com.yugabyte</groupId>
  <artifactId>cassaforte</artifactId>
  <version>3.0.0-alpha2-yb-1</version>
</dependency>
```


## Supported Features

 * Connection to a single node or a cluster
 * _All_ CQL 3.1 operations
 * CQL queries, including prepared statements
 * Nice query DSL for Clojure
 * Automatic deserialization of column names and values according to the schema
 * TLS connections, Kerberos authentication (DataStax Enterprise)


## Supported Clojure Versions

Cassaforte supports Clojure 1.6+.



## Documentation & Examples

Please refer to [Getting Started with Clojure and Cassandra](http://clojurecassandra.info/articles/getting_started.html)
guide, but use `com.yugabyte/cassaforte` as a dependency.

[Documentation guides](http://clojurecassandra.info) are not
finished and will be improved over time.

[API reference](http://reference.clojurecassandra.info/) is also available.



## Cassaforte Is a ClojureWerkz Project

Cassaforte is part of the [group of libraries known as ClojureWerkz](http://clojurewerkz.org), together with
[Monger](http://clojuremongodb.info), [Elastisch](http://clojureelasticsearch.info), [Langohr](http://clojurerabbitmq.info),
[Welle](http://clojureriak.info), [Titanium](http://titanium.clojurewerkz.org) and several others.




## Development

Cassaforte uses [Leiningen 2](https://github.com/technomancy/leiningen/blob/master/doc/TUTORIAL.md). Make
sure you have it installed and then run tests against all supported Clojure versions using

```
lein all test
```

Then create a branch and make your changes on it. Once you are done with your changes and all
tests pass, submit a pull request on Github.



## License

Copyright (C) 2012-2015 Michael S. Klishin, Alex Petrov, and the ClojureWerkz team.

Double licensed under the [Eclipse Public License](http://www.eclipse.org/legal/epl-v10.html) (the same as Clojure) or
the [Apache Public License 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).

The following only applies to changes made to this file as part of YugaByte development.

Portions Copyright (c) YugaByte, Inc.

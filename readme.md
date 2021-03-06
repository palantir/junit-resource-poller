<p align="right">
<a href="https://autorelease.general.dmz.palantir.tech/palantir/junit-resource-poller"><img src="https://img.shields.io/badge/Perform%20an-Autorelease-success.svg" alt="Autorelease"></a>
</p>

[![Build Status](https://travis-ci.org/palantir/junit-resource-poller.svg?branch=develop)](https://travis-ci.org/palantir/junit-resource-poller.svg?branch=develop)
[ ![Download](https://api.bintray.com/packages/palantir/releases/junit-resource-poller/images/download.svg) ](https://bintray.com/palantir/releases/junit-resource-poller/_latestVersion)

JUnit Resource Poller
=====================

This library provides a JUnit rule that allows to suspend test execution until a set of services become available. For
example, an HTTP poller can be configured to wait until an HTTP service in a Docker container is reachable.

Usage
-----

Simple Gradle setup:

```gradle
buildscript {
    repositories {
        jcenter()
    }
}

dependencies {
    compile 'com.palantir.junit:junit-resource-poller:<version>'
}
```

In a Java JUnit test:

```java
@ClassRule  // Tries reaching the given services 100 times with a 100ms delay (plus connection timeout)
public static final HttpPollingResource SERVICE_POLLER = HttpPollingResource.of(
        Optional.absent(),  // no SSL required
        ImmutableList.of("http://my.host/my/service", "http://another.service"),
        100);
```

With JUnit5:

```java
@RegisterExtension
public static final HttpPollingExtension POLLER = HttpPollingExtension.builder()
        .pollUrls(ImmutableList.of("http://my.host/my/service", "http://another.service"))
        .numAttempts(2)
        .build();
```

License
-------
This repository is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).

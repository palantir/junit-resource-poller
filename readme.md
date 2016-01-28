JUnit Resource Poller
=====================

This library provides a JUnit rule that allows to suspend test execution until a set of services become available. For
example, an HTTP poller can be configured to wait until an HTTP service in a Docker container is reachable.

Usage
-----

Simple Gradle setup:

    buildscript {
        repositories {
            jcenter()
        }
    }
    
    dependencies {
        compile 'com.palantir.junit:junit-resource-poller:<version>'
    }

In a Java JUnit test:

    @ClassRule  // Waits at most 60 seconds for all services to be available
    public static final HttpPollingResource SERVICE_POLLER = HttpPollingResource.of(
            Optional.absent(),  // no SSL required
            ImmutableList.of("http://my.host/my/service", "http://another.service"),
            60, TimeUnit.SECONDS);

License
-------
This repository is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).

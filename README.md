Barker
======

Guided step-by-step demo to demonstrate [Cassandra](http://cassandra.apache.org/)'s resilience.

This demo works on Linux and OSX.

Intention of this demo is to show Cassandra's resilience against node failures on your machine.

Barker is a very simple Twitter clone. The load generation component just simulates a bunch of users
writing "barks" (comparable to tweets). A status page shows the status of the load generation component,
the number of barks, some histogram and error rates.

You can start as many barker instances as you want. The application tells you the URL you can paste
into the address bar of your web browser.

Please note that this demo is not a general-purpose load generation and stress testing tool.
It just gives you some impression about how resilient Cassandra is - without the need to install
a Cassandra cluster on real hardware.

# Requirements

You need the following software on your machine:

* Oracle Java 7 SDK - get it from [the download site](http://www.oracle.com/technetwork/java/index.html)
* [Apache Maven 3.2 or newer](https://maven.apache.org/download.cgi)
* [Python](https://www.python.org/downloads/) 2.7 or newer (as a requirement for `ccm`)
* pip (included in Python 2.7 since 2.7.9 and 3.4)
* ccm - install via `pip install ccm` or [build and install from source](https://github.com/pcmanus/ccm/)  

Hardware requirements:

* 16GB RAM recommended - a two node cluster might work on a 8 GB machine
* Intel CPU

**Important note for Mac users**

Before you can start a local cluster, you need additional IP addresses assigned to the local IP interface:

```
sudo ifconfig lo0 alias 127.0.0.2 up
sudo ifconfig lo0 alias 127.0.0.3 up
sudo ifconfig lo0 alias 127.0.0.4 up
sudo ifconfig lo0 alias 127.0.0.5 up
```

# Preparation and setup

[ccm](https://github.com/pcmanus/ccm/) is a neat tool to create a Cassandra cluster on your local machine.
It can use any recent Cassandra release.

Note: all following steps are performed by the script `create-cluster.sh`.

Cassandra's main command line tools are:

* `nodetool` for cluster and node management
* `cqlsh` to issue CQL statements

CQL means _Cassandra Query Language_.

Use the following command to create and start a 3-node Cassandra named `barker` cluster with vnodes
using Apache Cassandra 2.1.5:

```sh
$ ccm create -s -n 3 -v 2.1.5 --vnodes barker
Current cluster is now: barker
```

To see the status of the cluster, use `ccm node1 status`:

```sh
$ ccm node1 status
Datacenter: datacenter1
=======================
Status=Up/Down
|/ State=Normal/Leaving/Joining/Moving
--  Address    Load       Tokens  Owns (effective)  Host ID                               Rack
UN  127.0.0.1  51,27 KB   256     65,2%             f61f059a-476b-4e7d-94f5-60e956136161  rack1
UN  127.0.0.2  51,31 KB   256     64,8%             52022e41-2fac-4a9f-bd28-6728cb6d43d2  rack1
UN  127.0.0.3  51,31 KB   256     70,0%             ef44ba60-98fb-4d27-849a-4d9f4e81a735  rack1
```

Create the keyspace (aka. schema) and tables:

```
$ ccm node1 cqlsh
Connected to barker at 127.0.0.1:9042.
[cqlsh 5.0.1 | Cassandra 2.1.5-SNAPSHOT | CQL spec 3.2.0 | Native protocol v3]
Use HELP for help.
cqlsh> SOURCE 'create-tables.cql';
cqlsh> DESCRIBE KEYSPACE barker;
cqlsh> EXIT
```

# Build and start the barker demo




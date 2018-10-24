# akka-postgres-cluster-seed

Bootstrap an akka cluster without managing cluster nodes.

akka-postgres-cluster-seed is an akka extension that will handle the management of cluster seed nodes using postgres as a backend.
It uses postgres advisory locks mechanism in order to make sure the initial seed is safely added to the cluster and also
postgres event bus (push notifications) to publish the status of the cluster nodes.

## Instructions

In order to build the library just run a maven build:

```cd <root>```

```mvn clean install```

Assuming you already have the option of using postgres as a backend coordinator.

Install the postgres schema found at:

```<root>/docker/postgres/docker-entrypoint-initdb.d/01-setUp.sql```

A dockerized postgres with a pg-cron plugin installed can be run with docker-compose yml found in:

```<root>/docker/runDB.yml```

The option of using pg-cron postgres plugin which decommissions stale cluster nodes from the table can be ommited.


When starting your app, use the DynamicClusterSeed extension, instead of the Cluster extension to join your cluster.

```
ActorSystem actorSystem = ActorSystem.create(SYSTEM_NAME);
DynamicClusterSeed.instance().get(actorSystem).join();

```

### Configuration

```
akka.cluster {
     seed.postgres {
       lockId = 666 //The advisor lock value for postgres. Same value across cluster nodes in actor system.
       jdbcUrl = "jdbc:postgresql://0.0.0.0:5432/akka_cluster_seed?user=gandoo&password=poustrakos" //postgres jdbc url
    }
  }
```
The are also tuning settings (heartbeatInterval, grace priod, e.t.c) found in :

```<root>/src/main/java/akka/cluster/seed/settings/Settings.java```

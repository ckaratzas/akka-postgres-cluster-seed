package akka.cluster.seed.core;

import akka.actor.Address;
import akka.actor.AddressFromURIString;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;
import akka.cluster.Cluster;
import akka.cluster.seed.model.Observation;
import akka.cluster.seed.postgres.DBOperations;
import akka.cluster.seed.settings.Settings;
import com.google.gson.Gson;
import io.reactivex.disposables.Disposable;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Function0;
import scala.concurrent.Await$;
import scala.concurrent.Promise;
import scala.concurrent.duration.Duration;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * @author ckaratza
 */
public class PostgresClusterSeed implements Extension {

    private static final Logger logger = LoggerFactory.getLogger(PostgresClusterSeed.class);

    private final ExtendedActorSystem system;
    private final Cluster clusterSystem;
    private final Settings settings;
    private final DBOperations dbOperations;
    private Disposable heartBeatJob;
    private Disposable notificationsPollJob;
    private Observation.Status status;
    private final Gson gson;

    PostgresClusterSeed(ExtendedActorSystem system) throws SQLException, ClassNotFoundException {
        this.system = system;
        settings = new Settings(system);
        dbOperations = new DBOperations(settings);
        status = Observation.Status.PENDING;
        clusterSystem = Cluster.get(system);
        gson = new Gson();
    }

    private void scheduleHeartBeat() {
        heartBeatJob = Schedulers.single().createWorker().schedulePeriodically(() -> {
                    try {
                        dbOperations.upsertEntry(status.name(), System.currentTimeMillis());
                    } catch (SQLException e) {
                        logger.error("Error accessing db while sending heartbeat {}.", e);
                    }
                }, 0,
                settings.heartBeatInterval(), TimeUnit.MILLISECONDS);
    }

    private void scheduleNotificationsPoller(final PublishProcessor<String> notificationPublisher) {
        notificationsPollJob = Schedulers.single().createWorker().schedulePeriodically(() -> {
            List<String> notifications;
            try {
                notifications = dbOperations.pollForNotifications();
            } catch (SQLException e) {
                notificationPublisher.onError(e);
                return;
            }
            notifications.forEach(notificationPublisher::onNext);
        }, 0, settings.pollingInterval(), TimeUnit.MILLISECONDS);
    }

    private void cleanUp() {
        if (heartBeatJob != null && !heartBeatJob.isDisposed()) heartBeatJob.dispose();
        stopPolling();
    }

    private void stopPolling() {
        if (notificationsPollJob != null && !notificationsPollJob.isDisposed()) notificationsPollJob.dispose();
    }

    private void registerSystemHooks() {
        clusterSystem.registerOnMemberRemoved((Function0<Void>) () -> {
            try {
                dbOperations.deleteEntry();
                dbOperations.pushNotification(gson.toJson(new Observation(settings.address().toString(), Observation.Status.LEFT, new Date(System.currentTimeMillis()))));
                cleanUp();
            } catch (SQLException e) {
                logger.error("Exception while cleaning cluster member state {}-{}, {}.", settings.systemName(), settings.address(), e);
            }
            return null;
        });
        system.registerOnTermination((Function0<Void>) () -> {
            cleanUp();
            return null;
        });
    }

    private void shutDown() {
        system.registerOnTermination((Function0<Void>) () -> {
            System.exit(0);
            return null;
        });
        system.terminate();
        new Thread(() -> {
            try {
                Await$.MODULE$.ready(system.whenTerminated(), Duration.apply(settings.clusterOpTimeout(), TimeUnit.MILLISECONDS));
            } catch (InterruptedException | TimeoutException e) {
                System.exit(10);
            }
        }).start();
    }

    public Address join() {
        try {
            dbOperations.subscribeToNotifications();
            dbOperations.upsertEntry(status.name(), System.currentTimeMillis());

            final PublishProcessor<String> notificationPublisher = PublishProcessor.create();
            ObservationsRegistry observationsRegistry = new ObservationsRegistry(notificationPublisher, dbOperations, settings.gracePeriod());
            observationsRegistry.init();
            scheduleNotificationsPoller(notificationPublisher);
            registerSystemHooks();
            long start = System.currentTimeMillis();
            while (!tryJoin(observationsRegistry)) {
                logger.warn("Failed to join to cluster for {}-{}.", settings.systemName(), settings.address());
                if (System.currentTimeMillis() - start > settings.shutDownAfterIfNotJoined()) {
                    logger.error("Failed to join cluster after {}ms interval. Shutting down system and JVM.", settings.shutDownAfterIfNotJoined());
                    shutDown();
                }
                Thread.sleep(1000);
            }
            observationsRegistry.cleanUp();
            long joinedAt = System.currentTimeMillis();
            dbOperations.upsertEntry(Observation.Status.JOINED.name(), joinedAt);
            dbOperations.pushNotification(gson.toJson(new Observation(settings.address().toString(), Observation.Status.JOINED, new Date(joinedAt))));
            scheduleHeartBeat();
            stopPolling();
            dbOperations.unsubscribeFromNotifications();
            return settings.address();
        } catch (Exception ex) {
            logger.error("Failed to join->{}", ex.getMessage());
            try {
                shutDown();
            } catch (Exception e) {
                logger.error("Failure during shutdown->{}", e.getMessage());
            }
            return null;
        }
    }

    private boolean tryJoin(ObservationsRegistry observationsRegistry) throws SQLException, InterruptedException {
        String[] seedNodes = observationsRegistry.fetchAllCandidateSeedNodes();
        if (seedNodes.length == 0) {
            boolean lock = dbOperations.tryAcquireSessionScopeAdvisoryLock();
            if (!lock) {
                logger.warn("Failed to obtain lock to form the cluster for {}.", settings.address());
                return false;
            } else {
                clusterSystem.join(settings.address());
            }
        } else {
            List<Address> addresses = Arrays.stream(seedNodes).map(AddressFromURIString::apply)
                    .limit(settings.limitSeedCandidates()).collect(Collectors.toList());
            clusterSystem.joinSeedNodes(addresses);
        }
        Promise<Boolean> promise = new scala.concurrent.impl.Promise.DefaultPromise<>();
        clusterSystem.registerOnMemberUp((Function0<Void>) () -> {
            status = Observation.Status.JOINED;
            promise.trySuccess(true);
            return null;
        });
        try {
            Await$.MODULE$.ready(promise.future(), Duration.apply(settings.clusterOpTimeout(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            return false;
        }
        return true;
    }
}

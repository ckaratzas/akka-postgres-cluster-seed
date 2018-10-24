package akka.cluster.seed.core;

import akka.cluster.seed.model.Observation;
import akka.cluster.seed.postgres.DBOperations;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.reactivex.disposables.Disposable;
import io.reactivex.processors.PublishProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author ckaratza
 * ObservationsRegistry holds the state of the cluster members. Initially the state is provisioned by a query in the database and later kept in sync by using
 * push notifications coming from heartbeat updates from all cluster members.
 */
final class ObservationsRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ObservationsRegistry.class);

    private final PublishProcessor<String> observationsFeeder;
    private final DBOperations dbOperations;
    private final Set<Observation> observations;
    private final long gracePeriod;
    private final Gson gson;
    private Disposable subscribeToObservations;

    ObservationsRegistry(PublishProcessor<String> observationsFeeder, DBOperations dbOperations, long gracePeriod) {
        this.observationsFeeder = observationsFeeder;
        this.dbOperations = dbOperations;
        this.observations = new HashSet<>();
        this.gracePeriod = gracePeriod;
        gson = new Gson();
    }

    synchronized void init() throws SQLException {
        cleanUp();
        observations.addAll(dbOperations.getCurrentViewOfJoinedMembers());
        subscribeToObservations = this.observationsFeeder.doOnError(t -> logger.error("Error occurred while trying to receive observation entries {}.", t)).subscribe(in -> {
            try {
                synchronized (observations) {
                    logger.info("Received observation {}.", in);
                    Observation observationEntry = gson.fromJson(in, Observation.class);
                    if (Observation.Status.LEFT.equals(observationEntry.getStatus()))
                        observations.remove(observationEntry);
                    else
                        observations.add(observationEntry);
                }
            } catch (JsonSyntaxException jse) {
                logger.error("Malformed notification received {}.", in);
            } catch (Exception e) {
                logger.error("Unexpected exception occurred {}.", e);
            }
        });
    }

    synchronized void cleanUp() {
        observations.clear();
        if (subscribeToObservations != null && !subscribeToObservations.isDisposed()) subscribeToObservations.dispose();
    }

    synchronized String[] fetchAllCandidateSeedNodes() {
        return observations.stream().filter(observationEntry -> Observation.Status.JOINED.equals(observationEntry.getStatus())
                && (System.currentTimeMillis() - observationEntry.getHeartBeatTs().getTime() < gracePeriod)).map(Observation::getAddress).toArray(String[]::new);
    }
}

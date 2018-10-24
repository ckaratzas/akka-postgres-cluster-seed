package akka.cluster.seed.core;

import akka.actor.*;

import java.sql.SQLException;

/**
 * @author ckaratza
 */
public class DynamicClusterSeed extends AbstractExtensionId<PostgresClusterSeed> implements ExtensionIdProvider {

    private final static DynamicClusterSeed dcsProvider = new DynamicClusterSeed();

    public static DynamicClusterSeed instance() {
        return dcsProvider;
    }

    @Override
    public PostgresClusterSeed apply(ActorSystem system) {
        return super.apply(system);
    }

    @Override
    public PostgresClusterSeed get(ActorSystem system) {
        return super.get(system);
    }

    @Override
    public PostgresClusterSeed createExtension(ExtendedActorSystem system) {
        try {
            return new PostgresClusterSeed(system);
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ExtensionId<? extends Extension> lookup() {
        return dcsProvider;
    }
}

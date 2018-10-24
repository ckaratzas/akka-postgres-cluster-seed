package akka.cluster.seed.settings;

import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.cluster.Cluster;
import com.typesafe.config.Config;
import scala.Option;

import static java.lang.Long.valueOf;

/**
 * @author ckaratza
 * All available settings for cluster seed management with postgres backend.
 */
public class Settings {
    private final String CONFIG_BASE_PATH = "akka.cluster.seed.postgres";

    private final Config config;
    private final Cluster cluster;
    private final ActorSystem system;

    public Settings(ActorSystem system) {
        this.system = system;
        this.cluster = Cluster.get(system);
        this.config = system.settings().config();
    }

    public String systemName() {
        return system.name();
    }

    public long lockId() {
        return config.getLong(CONFIG_BASE_PATH + ".lockId");
    }

    public long pollingInterval() {
        return wrapDefaultValue(CONFIG_BASE_PATH + ".pollingInterval", 1000L, Long.class);
    }

    public long heartBeatInterval() {
        return wrapDefaultValue(CONFIG_BASE_PATH + ".heartBeatInterval", 30000L, Long.class);
    }

    public long shutDownAfterIfNotJoined() {
        return wrapDefaultValue(CONFIG_BASE_PATH + ".shutDownAfterIfNotJoined", 50000L, Long.class);
    }

    public long gracePeriod() {
        return wrapDefaultValue(CONFIG_BASE_PATH + ".gracePeriod", 120000L, Long.class);
    }

    public int limitSeedCandidates() {
        return wrapDefaultValue(CONFIG_BASE_PATH + ".limitSeedCandidates", 4, Integer.class);
    }

    public long clusterOpTimeout() {
        return wrapDefaultValue(CONFIG_BASE_PATH + ".clusterOpTimeout", 10000L, Long.class);
    }

    public Address address() {
        Address selfAddress = cluster.selfAddress();
        String host = wrapDefaultValue(CONFIG_BASE_PATH + ".host", null, String.class);
        Integer port = wrapDefaultValue(CONFIG_BASE_PATH + ".port", null, Integer.class);
        return cluster.selfAddress().copy(selfAddress.protocol(), selfAddress.system(),
                host != null ? Option.apply(host) : selfAddress.host(),
                port != null ? Option.apply(port) : selfAddress.port());
    }

    public String jdbcUrl() {
        return config.getString(CONFIG_BASE_PATH + ".jdbcUrl");
    }

    private <T> T wrapDefaultValue(final String path, final T defaultValue, Class<T> target) {
        try {
            Object obj = config.getAnyRef(path);
            if (Integer.class.equals(target)) {
                return (T) Integer.valueOf(obj.toString());
            } else if (Long.class.equals(target)) {
                return (T) valueOf(obj.toString());
            } else if (String.class.equals(target)) {
                return ((T) obj.toString());
            } else
                throw new IllegalArgumentException();
        } catch (Exception ex) {
            return defaultValue;
        }
    }
}

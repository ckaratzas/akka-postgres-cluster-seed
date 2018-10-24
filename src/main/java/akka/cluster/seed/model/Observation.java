package akka.cluster.seed.model;

import java.util.Date;
import java.util.Objects;

/**
 * @author ckaratza
 */
public class Observation {

    public enum Status {
        PENDING, JOINED, LEFT
    }

    private String address;
    private Status status;
    private Date heartBeatTs;

    public Observation(String address, Status status, Date heartBeatTs) {
        this.address = address;
        this.status = status;
        this.heartBeatTs = heartBeatTs;
    }

    public Observation() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Observation that = (Observation) o;
        return Objects.equals(address, that.address) &&
                Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, status);
    }

    public String getAddress() {
        return address;
    }

    public Status getStatus() {
        return status;
    }

    public Date getHeartBeatTs() {
        return heartBeatTs;
    }
}
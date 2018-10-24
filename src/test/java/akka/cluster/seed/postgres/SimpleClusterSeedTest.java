package akka.cluster.seed.postgres;

import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.cluster.seed.core.DynamicClusterSeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimpleClusterSeedTest {
    private static final Logger logger = LoggerFactory.getLogger(SimpleClusterSeedTest.class);

    public static void main(String[] args) throws InterruptedException {
        long start = System.currentTimeMillis();
        final String SYSTEM_NAME = "postgres_backend";
        final int NODES_NUM = 60;
        final CountDownLatch latch = new CountDownLatch(NODES_NUM);
        ExecutorService executorService = Executors.newFixedThreadPool(NODES_NUM);
        for (int i = 0; i < NODES_NUM; i++) {
            executorService.execute(() -> {
                ActorSystem actorSystem = ActorSystem.create(SYSTEM_NAME);
                Address join = DynamicClusterSeed.instance().get(actorSystem).join();
                logger.info("Node has joined successfully with address {}.", join);
                latch.countDown();
            });
        }
        latch.await();
        long end = System.currentTimeMillis();
        logger.info("Everybody joined after {}ms", end - start);
        executorService.shutdown();
        System.exit(0);
    }
}

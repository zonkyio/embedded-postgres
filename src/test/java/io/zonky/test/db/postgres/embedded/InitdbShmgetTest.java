package io.zonky.test.db.postgres.embedded;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class InitdbShmgetTest {

    @Test
    public void testEmbeddedPg() throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newCachedThreadPool();
        List<InitdbThread> futureList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            futureList.add(new InitdbThread());
        }
        try{
            List<Future<Void>> futures = executor.invokeAll(futureList);
            for(Future<Void> future : futures){
                future.get();
                assertTrue(future.isDone());
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    class InitdbThread implements Callable<Void> {

        public Void call() throws IOException, InterruptedException {
            EmbeddedPostgres.Builder databaseBuilder = EmbeddedPostgres.builder();
            EmbeddedPostgres pg = databaseBuilder.start();
            Thread.sleep(5000);
            pg.close();
            return null;
        }
    }
}

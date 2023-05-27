package org.axonframework.deadline.dbscheduler;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.Execution;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;

import static org.awaitility.Awaitility.await;
import static org.axonframework.utils.DbSchedulerTestUtil.getAndStartScheduler;
import static org.axonframework.utils.DbSchedulerTestUtil.reCreateTable;
import static org.junit.jupiter.api.Assertions.*;

@ContextConfiguration
@ExtendWith(SpringExtension.class)
class DbSchedulerDeadlineManagerTest {

    @Autowired
    protected DataSource dataSource;

    @BeforeEach
    void prepare() {
        reCreateTable(dataSource);
    }

    @Test
    void binaryShouldFailWhenNotinitialized() {
        Scheduler scheduler = getAndStartScheduler(dataSource, DbSchedulerDeadlineManager.binaryTask());
        try {
            TaskInstance<DbSchedulerBinaryDeadlineDetails> instance =
                    DbSchedulerDeadlineManager.binaryTask()
                                              .instance("id", new DbSchedulerBinaryDeadlineDetails());
            scheduler.schedule(instance, Instant.now());
            await().atMost(Duration.ofSeconds(1L)).untilAsserted(
                    () -> {
                        List<Execution> failures = scheduler.getFailingExecutions(Duration.ofHours(1L));
                        assertEquals(1, failures.size());
                        assertNotNull(failures.get(0).lastFailure);
                    }
            );
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void humanReadableShouldFailWhenNotinitialized() {
        Scheduler scheduler = getAndStartScheduler(dataSource, DbSchedulerDeadlineManager.humanReadableTask());
        try {
            TaskInstance<DbSchedulerHumanReadableDeadlineDetails> instance =
                    DbSchedulerDeadlineManager.humanReadableTask()
                                              .instance("id", new DbSchedulerHumanReadableDeadlineDetails());
            scheduler.schedule(instance, Instant.now());
            await().atMost(Duration.ofSeconds(1L)).untilAsserted(
                    () -> {
                        List<Execution> failures = scheduler.getFailingExecutions(Duration.ofHours(1L));
                        assertEquals(1, failures.size());
                        assertNotNull(failures.get(0).lastFailure);
                    }
            );
        } finally {
            scheduler.stop();
        }
    }

    @Configuration
    public static class Context {

        @SuppressWarnings("Duplicates")
        @Bean
        public DataSource dataSource() {
            JDBCDataSource dataSource = new JDBCDataSource();
            dataSource.setUrl("jdbc:hsqldb:mem:testdb");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
        }
    }
}

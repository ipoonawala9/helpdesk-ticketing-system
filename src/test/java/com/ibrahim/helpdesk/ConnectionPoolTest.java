package com.ibrahim.helpdesk;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The deployed database sleeps when nothing is using it and is billed for the
 * hours it is awake, so a pool that holds connections open forever would keep
 * it awake around the clock. These settings are load-bearing, and they are
 * written against Hikari fields that take plain milliseconds and reject an ISO
 * duration by refusing to start, so this checks the form they are written in
 * is one the pool actually accepts.
 */
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=5",
        "spring.datasource.hikari.minimum-idle=0",
        "spring.datasource.hikari.idle-timeout=120000",
        "spring.datasource.hikari.max-lifetime=1200000",
        "spring.datasource.hikari.connection-timeout=30000",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
})
class ConnectionPoolTest {

    @Autowired
    private HikariDataSource dataSource;

    @Test
    void keepsNoIdleConnectionsForASleepingDatabaseToWaitOn() {
        assertThat(dataSource.getMinimumIdle()).isZero();
        assertThat(dataSource.getIdleTimeout()).isEqualTo(Duration.ofMinutes(2).toMillis());
        assertThat(dataSource.getMaxLifetime()).isEqualTo(Duration.ofMinutes(20).toMillis());
    }

    @Test
    void waitsForASleepingDatabaseToWakeRatherThanFailingFast() {
        assertThat(dataSource.getConnectionTimeout()).isEqualTo(Duration.ofSeconds(30).toMillis());
        assertThat(dataSource.getInitializationFailTimeout()).isEqualTo(-1);
    }

    @Test
    void staysWithinASmallDatabasesConnectionBudget() {
        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(5);
    }
}

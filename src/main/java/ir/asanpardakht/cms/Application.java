package ir.asanpardakht.cms;

import com.typesafe.config.Config;
import com.zaxxer.hikari.HikariDataSource;
import ir.asanpardakht.cms.common.Configs;
import ir.asanpardakht.cms.common.HealthChecks;
import ir.asanpardakht.cms.common.Metrics;
import ir.asanpardakht.cms.server.CMSBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ir.asanpardakht.cms.common.db.ConnectionPool;

import javax.sql.DataSource;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    /*
     * Normally we would be using the app config but since this is an example
     * we will be using a localized example config.
     */
    private static final Config conf = new Configs.Builder()
            .withResource("hikaricp/pools.conf")
            .build();

    /*
     *  This pool is made for short quick transactions that the web application uses.
     *  Using enum singleton pattern for lazy singletons
     */
    private enum Transactional {
        INSTANCE(ConnectionPool.getDataSourceFromConfig(conf.getConfig("pools.transactional"), Metrics.registry(), HealthChecks.getHealthCheckRegistry()));
        private final HikariDataSource dataSource;

        private Transactional(HikariDataSource dataSource) {
            this.dataSource = dataSource;
        }

        public HikariDataSource getDataSource() {
            return dataSource;
        }
    }

    public static HikariDataSource getTransactional() {
        return Transactional.INSTANCE.getDataSource();
    }

    /*
     *  This pool is designed for longer running transactions / bulk inserts / background jobs
     *  Basically if you have any multithreading or long running background jobs
     *  you do not want to starve the main applications connection pool.
     *
     *  EX.
     *  You have an endpoint that needs to insert 1000 db records
     *  This will queue up all the connections in the pool
     *
     *  While this is happening a user tries to log into the site.
     *  If you use the same pool they may be blocked until the bulk insert is done
     *  By splitting pools you can give transactional queries a much higher chance to
     *  run while the other pool is backed up.
     */
    private enum Processing {
        INSTANCE(ConnectionPool.getDataSourceFromConfig(conf.getConfig("pools.processing"), Metrics.registry(), HealthChecks.getHealthCheckRegistry()));
        private final HikariDataSource dataSource;

        private Processing(HikariDataSource dataSource) {
            this.dataSource = dataSource;
        }

        public HikariDataSource getDataSource() {
            return dataSource;
        }
    }

    public static HikariDataSource getProcessing() {
        return Processing.INSTANCE.getDataSource();
    }

    public static void main(String[] args) {
        CMSBootstrap.run(() -> {
            //
        });

        wait(3000);
        logger.debug("starting");
        DataSource processing = Application.getProcessing();
        logger.debug("processing started");
        DataSource transactional = Application.getTransactional();
        logger.debug("transactional started");
        logger.debug("done");
    }

    private static void wait(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            logger.warn(e.getMessage(), e);
        }
    }
}

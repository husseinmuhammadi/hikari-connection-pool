package ir.asanpardakht.cms;

import com.typesafe.config.Config;
import com.zaxxer.hikari.HikariDataSource;
import ir.asanpardakht.cms.common.Configs;
import ir.asanpardakht.cms.common.HealthChecks;
import ir.asanpardakht.cms.common.Metrics;
import ir.asanpardakht.cms.common.db.ConnectionPool;
import ir.asanpardakht.cms.server.CMSBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

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

        ThreadHelper.wait(1000);
        logger.debug("starting");
        DataSource processing = Application.getProcessing();
        logger.debug("processing started");
        DataSource transactional = Application.getTransactional();
        logger.debug("transactional started");
        logger.debug("done");

        int next = Sequence.last() + 1;
        if (next == -1)
            return;
        for (int i = next; i < 100 + next; i++) {
            final int id = i;
            new Thread(() -> {
                ProductDataMgr.add(id, String.valueOf(1000000 + id));
            }).start();
        }
    }
}

class Sequence {
    private static final Logger logger = LoggerFactory.getLogger(Sequence.class);

    public static int last() {
        int last = -1;
        try (Connection connection = Application.getTransactional().getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement("SELECT MAX(ID) LAST_ID FROM ACCOUNT");
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                last = resultSet.getInt(1);
                logger.info("next ID: {}", last);
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return last;
    }
}

class ProductDataMgr {
    private static final Logger logger = LoggerFactory.getLogger(ProductDataMgr.class);

    public static void add(int id, String accountNo) {
        final String SQL_QUERY = "INSERT INTO ACCOUNT (ID, ACCOUNT_NO) VALUES (?, ?)";

        try {
            logger.info("Adding new account {} - waiting for getting connection ...", accountNo);
            Connection connection = Application.getTransactional().getConnection();
            logger.info("Connection allocated for account {} - progress is going on ...", accountNo);
            try (PreparedStatement preparedStatement = connection.prepareStatement(SQL_QUERY)) {
                connection.setAutoCommit(false);
                preparedStatement.setInt(1, id);
                preparedStatement.setString(2, accountNo);
                int affected = preparedStatement.executeUpdate();
                ThreadHelper.wait(3000);
                connection.commit();
                logger.info("Account {} added successfully", accountNo);
            } catch (SQLException e) {
                logger.error(e.getMessage(), e);
                connection.rollback();
            } finally {
                connection.setAutoCommit(true);
                connection.close();
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }
}

class ThreadHelper{
    private static final Logger logger = LoggerFactory.getLogger(ThreadHelper.class);

    public static void wait(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            logger.warn(e.getMessage(), e);
        }
    }
}
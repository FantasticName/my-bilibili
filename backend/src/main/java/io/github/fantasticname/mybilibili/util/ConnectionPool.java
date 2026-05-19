package io.github.fantasticname.mybilibili.util;

import io.github.fantasticname.mybilibili.annotation.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ConnectionPool implements DataSource {

    private static final Logger log = LoggerFactory.getLogger(ConnectionPool.class);
    private static final String VALIDATION_QUERY = "SELECT 1";

    private final BlockingQueue<Connection> idleConnections;
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final Semaphore semaphore;
    private final int maxPoolSize;
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public ConnectionPool() {
        Properties props = new Properties();
        try (InputStream is = ConnectionPool.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (is == null) {
                throw new RuntimeException("未找到数据库配置文件: db.properties");
            }
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("读取数据库配置文件失败", e);
        }

        // 优先从环境变量读取，回退到配置文件
        // 这样在生产环境可以通过环境变量注入敏感信息，避免明文存储在配置文件中
        this.jdbcUrl = getEnvOrProp("JDBC_URL", props, "jdbc.url");
        this.username = getEnvOrProp("JDBC_USERNAME", props, "jdbc.username");
        this.password = getEnvOrProp("JDBC_PASSWORD", props, "jdbc.password");
        this.maxPoolSize = Integer.parseInt(getEnvOrProp("JDBC_POOL_SIZE", props, "jdbc.poolSize", "10"));
        this.idleConnections = new ArrayBlockingQueue<>(maxPoolSize);
        this.semaphore = new Semaphore(maxPoolSize, true);

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL JDBC Driver 未找到", e);
        }

        log.info("连接池初始化完成，最大连接数: {}", maxPoolSize);
    }

    /**
     * 优先从环境变量获取配置值，如果环境变量不存在则回退到Properties文件
     *
     * <p>这种模式称为"环境变量优先"策略，是12-Factor App推荐的做法：
     * 敏感信息（如密码）通过环境变量注入，避免明文存储在配置文件中。</p>
     *
     * @param envKey   环境变量名
     * @param props    Properties配置对象
     * @param propKey  Properties中的键名
     * @return 配置值
     */
    private static String getEnvOrProp(String envKey, Properties props, String propKey) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            log.debug("从环境变量读取配置: {} = ***", envKey);
            return envValue;
        }
        String propValue = props.getProperty(propKey);
        if (propValue == null || propValue.isEmpty()) {
            throw new RuntimeException("配置项缺失: 环境变量 " + envKey + " 和配置键 " + propKey + " 均未设置");
        }
        log.debug("从配置文件读取配置: {}", propKey);
        return propValue;
    }

    /**
     * 带默认值的配置读取，优先从环境变量获取，回退到Properties文件，最后使用默认值
     *
     * @param envKey       环境变量名
     * @param props        Properties配置对象
     * @param propKey      Properties中的键名
     * @param defaultValue 默认值
     * @return 配置值
     */
    private static String getEnvOrProp(String envKey, Properties props, String propKey, String defaultValue) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            log.debug("从环境变量读取配置: {}", envKey);
            return envValue;
        }
        return props.getProperty(propKey, defaultValue);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return getConnection(username, password);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        try {
            if (!semaphore.tryAcquire(30, TimeUnit.SECONDS)) {
                throw new SQLException("连接池耗尽，等待超时（30秒）");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("等待连接被中断", e);
        }

        try {
            while (true) {
                Connection physical = idleConnections.poll();
                if (physical != null) {
                    if (isValidPhysical(physical)) {
                        log.debug("从空闲队列获取连接成功");
                        return wrapWithProxy(physical);
                    } else {
                        closePhysically(physical);
                        totalConnections.decrementAndGet();
                        log.debug("空闲连接已失效，物理关闭后重试");
                        continue;
                    }
                }

                try {
                    Connection newConn = DriverManager.getConnection(jdbcUrl, username, password);
                    totalConnections.incrementAndGet();
                    log.debug("创建新连接成功，当前总数: {}", totalConnections.get());
                    return wrapWithProxy(newConn);
                } catch (SQLException e) {
                    semaphore.release();
                    throw e;
                }
            }
        } catch (RuntimeException e) {
            semaphore.release();
            throw e;
        }
    }

    void returnConnection(Connection physicalConn) {
        if (physicalConn == null) {
            semaphore.release();
            return;
        }

        try {
            if (!physicalConn.isClosed()) {
                if (!physicalConn.getAutoCommit()) {
                    physicalConn.rollback();
                }
                physicalConn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.warn("重置连接状态失败，物理关闭: {}", e.getMessage());
            closePhysically(physicalConn);
            totalConnections.decrementAndGet();
            semaphore.release();
            return;
        }

        if (isValidPhysical(physicalConn)) {
            boolean offered = idleConnections.offer(physicalConn);
            if (!offered) {
                closePhysically(physicalConn);
                totalConnections.decrementAndGet();
            }
            log.debug("连接已归还到空闲队列");
        } else {
            closePhysically(physicalConn);
            totalConnections.decrementAndGet();
            log.debug("连接已失效，物理关闭");
        }
        semaphore.release();
    }

    private boolean isValidPhysical(Connection conn) {
        try {
            if (conn == null || conn.isClosed()) {
                return false;
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(3);
                stmt.execute(VALIDATION_QUERY);
            }
            return true;
        } catch (SQLException e) {
            log.debug("连接验证失败: {}", e.getMessage());
            return false;
        }
    }

    private void closePhysically(Connection conn) {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            log.warn("关闭物理连接异常: {}", e.getMessage());
        }
    }

    private Connection wrapWithProxy(Connection physical) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                new ConnectionInvocationHandler(physical, this)
        );
    }

    private static class ConnectionInvocationHandler implements InvocationHandler {

        private final Connection physicalConnection;
        private final ConnectionPool pool;
        private boolean closed = false;

        ConnectionInvocationHandler(Connection physical, ConnectionPool pool) {
            this.physicalConnection = physical;
            this.pool = pool;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();

            if ("close".equals(methodName)) {
                if (closed) {
                    return null;
                }
                closed = true;
                pool.returnConnection(physicalConnection);
                return null;
            }

            if ("isClosed".equals(methodName)) {
                return closed || physicalConnection.isClosed();
            }

            if (closed) {
                throw new SQLException("连接已归还到连接池，不能再使用");
            }

            try {
                return method.invoke(physicalConnection, args);
            } catch (Exception e) {
                Throwable cause = e.getCause();
                if (cause instanceof SQLException) {
                    throw cause;
                }
                throw e;
            }
        }
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
    }

    @Override
    public void setLoginTimeout(int seconds) {
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLException("Not a wrapper");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }
}

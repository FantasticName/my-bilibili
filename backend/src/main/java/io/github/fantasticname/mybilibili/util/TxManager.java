package io.github.fantasticname.mybilibili.util;

import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 事务管理器，为同一线程内的一组 DAO 操作提供统一的事务边界
 *
 * <p>事务（Transaction）是数据库操作的基本单元，保证一组操作要么全部成功，要么全部失败。
 * 比如转账操作：A扣钱 + B加钱，必须在一个事务里，不能A扣了钱B没加上。</p>
 *
 * <p>核心原理：</p>
 * <ul>
 *   <li>ThreadLocal&lt;Connection&gt; 绑定当前线程的事务连接</li>
 *   <li>executeInTransaction 方法开启事务，执行业务逻辑，根据异常决定提交或回滚</li>
 *   <li>DAO 层通过 TxManager.currentConnection() 判断当前是否处于事务中，从而复用连接</li>
 * </ul>
 *
 * <p>事务流程：</p>
 * <pre>
 *   TxManager.executeInTransaction(dataSource, () -> {
 *       // 这里面的所有DAO操作都使用同一个Connection
 *       userDao.insert(user);          // 复用事务连接
 *       userProfileDao.insert(profile); // 复用事务连接
 *       return null;
 *   });
 *   // 如果上面两步都成功 → commit
 *   // 如果任何一步抛异常 → rollback
 * </pre>
 *
 * @author FantasticName
 */
public final class TxManager {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(TxManager.class);

    /**
     * ThreadLocal，用于将事务连接绑定到当前线程
     *
     * <p>每个线程都有独立的存储空间，线程A中set的Connection，
     * 只有线程A能get到，线程B拿不到。</p>
     *
     * <p>这样在多线程环境下，每个请求的事务连接互不干扰。</p>
     */
    private static final ThreadLocal<Connection> CURRENT_CONNECTION = new ThreadLocal<>();

    /**
     * 私有构造方法，防止实例化
     */
    private TxManager() {
    }

    /**
     * 获取当前线程绑定的事务连接
     *
     * <p>DAO层通过此方法判断当前是否处于事务中：</p>
     * <ul>
     *   <li>返回非null：处于事务中，DAO应复用此连接</li>
     *   <li>返回null：不在事务中，DAO应从连接池新借一个连接</li>
     * </ul>
     *
     * @return 当前线程的事务连接，若不在事务中则返回null
     */
    public static Connection currentConnection() {
        return CURRENT_CONNECTION.get();
    }

    /**
     * 在数据库事务中执行一段业务逻辑
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>检查是否已存在事务上下文（不支持嵌套事务）</li>
     *   <li>从数据源获取连接，关闭自动提交（开启事务）</li>
     *   <li>将连接绑定到 ThreadLocal</li>
     *   <li>执行业务逻辑（Lambda表达式）</li>
     *   <li>无异常 → 提交事务</li>
     *   <li>有异常 → 回滚事务</li>
     *   <li>finally 中清除 ThreadLocal，恢复自动提交</li>
     * </ol>
     *
     * @param ds    数据源（连接池）
     * @param block 业务逻辑块（Lambda表达式）
     * @param <T>   返回值类型
     * @return 业务逻辑执行结果
     */
    public static <T> T executeInTransaction(DataSource ds, TxSupplier<T> block) {
        // 1. 检查是否已存在事务上下文
        Connection existing = CURRENT_CONNECTION.get();
        if (existing != null) {
            // 1.1 已经存在事务上下文，直接执行（不支持嵌套事务）
            //     嵌套事务很复杂，这里简化处理：外层事务管提交/回滚
            log.debug("已存在事务上下文，直接执行业务逻辑（嵌套事务不支持）");
            try {
                return block.get();
            } catch (Exception e) {
                throw wrapException(e);
            }
        }

        // 2. 开启新事务
        try {
            // 2.1 从数据源获取连接
            Connection conn = ds.getConnection();
            log.debug("获取事务连接成功");

            // 2.2 关闭自动提交，开启手动事务
            //     默认情况下，每条SQL都是自动提交的（autoCommit=true）
            //     关闭后，需要手动调用commit()或rollback()
            conn.setAutoCommit(false);

            // 2.3 将连接绑定到ThreadLocal
            //     后续DAO层通过currentConnection()获取的就是这个连接
            CURRENT_CONNECTION.set(conn);

            try {
                // 3. 执行业务逻辑
                T result = block.get();

                // 4. 业务逻辑执行成功，提交事务
                conn.commit();
                log.debug("事务提交成功");
                return result;
            } catch (BusinessException e) {
                // 5.1 业务异常，回滚事务
                conn.rollback();
                log.warn("业务异常，事务已回滚: code={}, message={}", e.getCode(), e.getMessage());
                throw e;
            } catch (Exception e) {
                // 5.2 其他异常，回滚事务
                conn.rollback();
                log.error("事务执行失败，已回滚: {}", e.getMessage());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "事务执行失败");
            } finally {
                CURRENT_CONNECTION.remove();
                try {
                    conn.close();
                } catch (SQLException e) {
                    log.warn("归还连接失败: {}", e.getMessage());
                }
            }
        } catch (SQLException e) {
            log.error("获取数据库连接失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取数据库连接失败");
        }
    }

    /**
     * 包装异常为RuntimeException
     *
     * <p>如果异常已经是BusinessException，直接返回；
     * 否则包装为BusinessException。</p>
     *
     * @param e 原始异常
     * @return 包装后的运行时异常
     */
    private static RuntimeException wrapException(Exception e) {
        if (e instanceof BusinessException) {
            return (BusinessException) e;
        }
        return new BusinessException(ErrorCode.SYSTEM_ERROR, e.getMessage());
    }
}

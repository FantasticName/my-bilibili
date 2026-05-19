package io.github.fantasticname.mybilibili.dao.base;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 连接持有者，管理数据库连接的生命周期
 *
 * <p>ConnectionHolder 封装了一个数据库连接以及一个关键标志位 borrowed，
 * 用于区分"事务连接"和"自行借用的连接"：</p>
 * <ul>
 *   <li>事务连接（borrowed=false）：由 TxManager 开启事务时获取，
 *       DAO 方法结束后不能关闭，由 TxManager 统一管理提交/回滚/归还</li>
 *   <li>自行借用的连接（borrowed=true）：DAO 方法自行从连接池获取，
 *       使用完毕后必须归还（调用 closeIfBorrowed()）</li>
 * </ul>
 *
 * <p>为什么需要这个类？因为 BaseDao 的 borrow() 方法需要智能判断：
 * 如果当前线程已经在事务中，就复用事务连接（不关闭）；
 * 否则从连接池新借一个（用完归还）。ConnectionHolder 就是这个判断结果的载体。</p>
 *
 * @author FantasticName
 */
public class ConnectionHolder {

    /**
     * 数据库连接
     */
    public final Connection connection;

    /**
     * 是否是自行借用的连接
     *
     * <p>true：从连接池新借的，用完需要归还</p>
     * <p>false：事务连接，不能关闭，由TxManager管理</p>
     */
    public final boolean borrowed;

    /**
     * 构造方法
     *
     * @param connection 数据库连接
     * @param borrowed   是否是自行借用的连接
     */
    public ConnectionHolder(Connection connection, boolean borrowed) {
        this.connection = connection;
        this.borrowed = borrowed;
    }

    /**
     * 如果是自行借用的连接，归还到连接池
     *
     * <p>在 BaseDao 的每个数据库操作方法的 finally 块中调用此方法，
     * 确保自行借用的连接一定会被归还，不会泄漏。</p>
     *
     * <p>如果是事务连接（borrowed=false），此方法不做任何操作，
     * 连接由 TxManager 在事务结束时统一归还。</p>
     */
    public void closeIfBorrowed() {
        if (borrowed) {
            try {
                connection.close();
            } catch (SQLException e) {
                // 归还连接失败，记录警告但不抛出异常
                // 因为这通常是在finally块中调用，不应该影响主流程
                throw new RuntimeException("归还数据库连接失败", e);
            }
        }
    }
}

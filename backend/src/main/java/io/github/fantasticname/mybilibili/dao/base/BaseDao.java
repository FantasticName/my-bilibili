package io.github.fantasticname.mybilibili.dao.base;

import io.github.fantasticname.mybilibili.annotation.Autowired;
import io.github.fantasticname.mybilibili.util.TxManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.ParameterizedType;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 泛型DAO基类，提供通用的CRUD方法，消除子类中的重复JDBC代码
 *
 * <p>DAO（Data Access Object，数据访问对象）是数据库操作的封装层。
 * 每个实体类对应一个DAO，负责该实体的所有数据库操作。</p>
 *
 * <p>BaseDao 通过泛型和反射，将通用的CRUD操作封装在父类中，
 * 子类只需继承并调用父类方法即可完成数据库操作，不需要重复编写JDBC模板代码。</p>
 *
 * <p>核心原理：</p>
 * <ul>
 *   <li>通过 borrow() 方法智能获取连接：若 TxManager.currentConnection() 非空，则复用事务连接；否则从连接池新借一个</li>
 *   <li>ConnectionHolder 负责管理连接的生命周期：事务连接不关闭，自行借用的连接在 finally 中归还</li>
 *   <li>封装 queryOne、queryList、executeUpdate、executeInsert 四个基础方法</li>
 *   <li>通过反射获取子类的泛型参数类型（如 UserDao extends BaseDao&lt;User&gt; 中的 User），
 *       自动创建对应的 ResultSetMapper</li>
 * </ul>
 *
 * <p>子类使用示例：</p>
 * <pre>
 *   public class UserDao extends BaseDao&lt;User&gt; {
 *       public User findById(long id) {
 *           String sql = "SELECT * FROM user WHERE id = ?";
 *           return queryOne(sql, id);
 *       }
 *
 *       public void insert(User user) {
 *           String sql = "INSERT INTO user (phone, password_hash) VALUES (?, ?)";
 *           executeUpdate(sql, user.getPhone(), user.getPasswordHash());
 *       }
 *   }
 * </pre>
 *
 * @param <T> 实体类型
 * @author FantasticName
 */
public abstract class BaseDao<T> {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(BaseDao.class);

    /**
     * 数据源（连接池），通过 @Autowired 由IoC容器注入
     *
     * <p>由于IoC容器使用字段注入，DataSource会在Bean创建后自动注入。
     * DAO方法被调用时，DataSource一定已经注入完毕。</p>
     */
    @Autowired
    protected DataSource dataSource;

    /**
     * 实体类的Class对象，用于创建ResultSetMapper
     */
    private final Class<T> entityClass;

    /**
     * 结果集映射器，将ResultSet行映射为Java对象
     */
    protected final ResultSetMapper<T> mapper;

    /**
     * 无参构造函数，通过反射获取子类的泛型参数类型
     *
     * <p>工作原理：当 UserDao extends BaseDao&lt;User&gt; 时，
     * getGenericSuperclass() 返回 BaseDao&lt;User&gt; 的 Type，
     * 从中可以提取出 User 的 Class 对象。</p>
     *
     * <p>这是泛型反射的经典用法，MyBatis、Spring Data JPA 等框架都用这个技巧。</p>
     */
    @SuppressWarnings("unchecked")
    protected BaseDao() {
        // 1. 获取子类的泛型父类类型
        //    例如 UserDao extends BaseDao<User>，这里得到的就是 BaseDao<User>
        ParameterizedType genericSuperclass = (ParameterizedType) getClass().getGenericSuperclass();

        // 2. 获取泛型参数的实际类型
        //    getActualTypeArguments() 返回 [User]
        this.entityClass = (Class<T>) genericSuperclass.getActualTypeArguments()[0];

        // 3. 创建对应类型的ResultSetMapper
        this.mapper = new ResultSetMapper<>(entityClass);

        log.debug("BaseDao 初始化: entityClass={}", entityClass.getSimpleName());
    }

    /**
     * 智能获取连接：优先复用事务连接，否则新借一个
     *
     * <p>这是BaseDao的核心方法，所有数据库操作都通过它获取连接。</p>
     *
     * <p>判断逻辑：</p>
     * <ol>
     *   <li>检查 TxManager.currentConnection() 是否非空</li>
     *   <li>如果非空，说明当前线程在事务中，复用事务连接（borrowed=false）</li>
     *   <li>如果为空，从连接池新借一个连接（borrowed=true）</li>
     * </ol>
     *
     * @return ConnectionHolder，包含连接和是否需要归还的标志
     */
    protected ConnectionHolder borrow() {
        // 1. 检查当前线程是否在事务中
        Connection txConn = TxManager.currentConnection();
        if (txConn != null) {
            // 1.1 在事务中，复用事务连接，不需要归还
            log.debug("复用事务连接");
            return new ConnectionHolder(txConn, false);
        }

        // 2. 不在事务中，从连接池新借一个
        try {
            Connection conn = dataSource.getConnection();
            log.debug("从连接池获取新连接");
            return new ConnectionHolder(conn, true);
        } catch (SQLException e) {
            log.error("获取数据库连接失败: {}", e.getMessage());
            throw new RuntimeException("获取数据库连接失败", e);
        }
    }

    /**
     * 查询单个对象
     *
     * <p>执行SELECT语句，将结果集的第一行映射为Java对象返回。
     * 如果没有匹配的行，返回null。</p>
     *
     * @param sql    SQL语句，可以使用?占位符
     * @param params 占位符参数
     * @return 映射后的Java对象，没有匹配行则返回null
     */
    protected T queryOne(String sql, Object... params) {
        ConnectionHolder holder = borrow();
        try (PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            // 1. 绑定参数
            setParameters(ps, params);

            // 2. 执行查询
            try (ResultSet rs = ps.executeQuery()) {
                // 3. 如果有结果行，映射为Java对象
                if (rs.next()) {
                    T result = mapper.mapRow(rs);
                    log.debug("查询成功: sql={}, 返回类型={}", sql, entityClass.getSimpleName());
                    return result;
                }
                // 4. 没有结果行，返回null
                log.debug("查询无结果: sql={}", sql);
                return null;
            }
        } catch (Exception e) {
            log.error("查询失败: sql={}, 错误: {}", sql, e.getMessage());
            throw new RuntimeException("查询失败: " + sql, e);
        } finally {
            // 5. 归还连接（如果是自行借用的）
            holder.closeIfBorrowed();
        }
    }

    /**
     * 查询列表
     *
     * <p>执行SELECT语句，将结果集的所有行映射为Java对象列表返回。
     * 如果没有匹配的行，返回空列表。</p>
     *
     * @param sql    SQL语句，可以使用?占位符
     * @param params 占位符参数
     * @return 映射后的Java对象列表
     */
    protected List<T> queryList(String sql, Object... params) {
        ConnectionHolder holder = borrow();
        try (PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            // 1. 绑定参数
            setParameters(ps, params);

            // 2. 执行查询
            try (ResultSet rs = ps.executeQuery()) {
                // 3. 遍历结果集，逐行映射
                List<T> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapper.mapRow(rs));
                }
                log.debug("查询列表成功: sql={}, 返回{}条记录", sql, list.size());
                return list;
            }
        } catch (Exception e) {
            log.error("查询列表失败: sql={}, 错误: {}", sql, e.getMessage());
            throw new RuntimeException("查询列表失败: " + sql, e);
        } finally {
            // 4. 归还连接
            holder.closeIfBorrowed();
        }
    }

    /**
     * 执行更新操作（INSERT、UPDATE、DELETE）
     *
     * <p>执行写操作的SQL语句，返回受影响的行数。</p>
     *
     * @param sql    SQL语句，可以使用?占位符
     * @param params 占位符参数
     * @return 受影响的行数
     */
    protected int executeUpdate(String sql, Object... params) {
        ConnectionHolder holder = borrow();
        try (PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            // 1. 绑定参数
            setParameters(ps, params);

            // 2. 执行更新
            int rows = ps.executeUpdate();
            log.debug("更新成功: sql={}, 影响{}行", sql, rows);
            return rows;
        } catch (Exception e) {
            log.error("更新失败: sql={}, 错误: {}", sql, e.getMessage());
            throw new RuntimeException("更新失败: " + sql, e);
        } finally {
            // 3. 归还连接
            holder.closeIfBorrowed();
        }
    }

    /**
     * 执行插入操作并返回自增主键
     *
     * <p>与 executeUpdate 不同，此方法执行INSERT后会获取数据库生成的自增主键值。
     * 这在插入新记录后需要立即获取ID的场景中非常有用。</p>
     *
     * @param sql    INSERT语句，可以使用?占位符
     * @param params 占位符参数
     * @return 自增主键值，如果获取失败返回-1
     */
    protected long executeInsert(String sql, Object... params) {
        ConnectionHolder holder = borrow();
        try (PreparedStatement ps = holder.connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            // 1. 绑定参数
            setParameters(ps, params);

            // 2. 执行插入
            int rows = ps.executeUpdate();
            log.debug("插入成功: sql={}, 影响{}行", sql, rows);

            // 3. 获取自增主键
            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    long id = generatedKeys.getLong(1);
                    log.debug("获取自增主键: id={}", id);
                    return id;
                }
            }

            // 4. 没有获取到自增主键
            log.warn("未获取到自增主键: sql={}", sql);
            return -1;
        } catch (Exception e) {
            log.error("插入失败: sql={}, 错误: {}", sql, e.getMessage());
            throw new RuntimeException("插入失败: " + sql, e);
        } finally {
            // 5. 归还连接
            holder.closeIfBorrowed();
        }
    }

    /**
     * 绑定PreparedStatement参数
     *
     * <p>将可变参数依次设置到PreparedStatement的占位符位置。
     * JDBC的占位符索引从1开始，不是0。</p>
     *
     * @param ps     PreparedStatement
     * @param params 参数数组
     */
    private void setParameters(PreparedStatement ps, Object... params) throws SQLException {
        if (params == null || params.length == 0) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            // JDBC参数索引从1开始
            ps.setObject(i + 1, params[i]);
        }
    }
}

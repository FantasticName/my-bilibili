package io.github.fantasticname.mybilibili.dao.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 结果集自动映射器，将 JDBC ResultSet 的当前行自动映射为指定的 Java 对象
 *
 * <p>ORM框架的核心功能之一就是"对象关系映射"（Object-Relational Mapping），
 * 即把数据库表的一行记录自动转换成Java对象。ResultSetMapper 就是实现这个功能的。</p>
 *
 * <p>核心原理：</p>
 * <ol>
 *   <li>通过反射解析目标类的所有字段，并建立数据库列名（下划线）到字段名（驼峰）的映射缓存</li>
 *   <li>遍历 ResultSetMetaData 获取所有列，根据列名查找对应字段并赋值</li>
 *   <li>处理特殊类型转换（如 Timestamp → LocalDateTime）</li>
 * </ol>
 *
 * <p>映射规则：数据库列名使用下划线命名（如 password_hash），
 * Java字段名使用驼峰命名（如 passwordHash），
 * 映射器会自动将驼峰转为下划线来匹配数据库列名。</p>
 *
 * <p>为什么用 ConcurrentHashMap 缓存字段映射？因为同一个实体类的映射关系是固定的，
 * 不需要每次查询都重新解析。缓存后只需解析一次，后续直接查缓存即可。</p>
 *
 * @param <T> 目标实体类型
 * @author FantasticName
 */
public class ResultSetMapper<T> {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(ResultSetMapper.class);

    /**
     * 目标实体类的Class对象
     */
    private final Class<T> clazz;

    /**
     * 数据库列名 → Java字段 的映射缓存
     *
     * <p>key：数据库列名（下划线格式，如 password_hash）</p>
     * <p>value：对应的Java字段（如 passwordHash）</p>
     *
     * <p>使用ConcurrentHashMap保证线程安全，
     * 因为多个线程可能同时查询同一个Mapper的映射缓存。</p>
     */
    private final Map<String, Field> columnToField = new ConcurrentHashMap<>();

    /**
     * 构造方法，初始化时解析目标类的字段并建立映射缓存
     *
     * @param clazz 目标实体类的Class对象
     */
    public ResultSetMapper(Class<T> clazz) {
        this.clazz = clazz;

        // 遍历目标类的所有字段（包括private字段）
        for (Field field : clazz.getDeclaredFields()) {
            // 1. 设置可访问，允许修改私有字段
            field.setAccessible(true);

            // 2. 将驼峰字段名转为下划线列名
            //    例如：passwordHash → password_hash
            String columnName = camelToUnderscore(field.getName());

            // 3. 存入映射缓存
            columnToField.put(columnName, field);
            log.debug("映射缓存: {} → {}", columnName, field.getName());
        }
    }

    /**
     * 将 ResultSet 的当前行映射为 Java 对象
     *
     * <p>映射流程：</p>
     * <ol>
     *   <li>通过反射创建目标类的实例</li>
     *   <li>获取 ResultSet 的元数据，遍历所有列</li>
     *   <li>对每一列，根据列名在映射缓存中查找对应的Java字段</li>
     *   <li>如果找到，进行类型转换后赋值</li>
     * </ol>
     *
     * @param rs ResultSet，指针应指向要映射的行
     * @return 映射后的Java对象
     * @throws Exception 映射过程中可能出现的反射或SQL异常
     */
    public T mapRow(ResultSet rs) throws Exception {
        // 1. 通过反射创建目标类的实例
        //    相当于 new T()，但泛型不能直接new，所以用反射
        T instance = clazz.getDeclaredConstructor().newInstance();

        // 2. 获取ResultSet的元数据
        //    元数据包含了列的数量、列名、列类型等信息
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        // 3. 遍历所有列
        for (int i = 1; i <= columnCount; i++) {
            // 3.1 获取列名（优先使用别名，即SQL中AS后面的名称）
            String columnLabel = meta.getColumnLabel(i);

            // 3.2 在映射缓存中查找对应的Java字段
            Field field = columnToField.get(columnLabel);
            if (field == null) {
                // 3.3 没找到对应的字段，跳过此列
                //     比如数据库有10列，但Java对象只有8个字段，那2列就被跳过
                continue;
            }

            // 3.4 从ResultSet获取该列的值
            Object value = rs.getObject(i);
            if (value == null) {
                // 3.5 数据库值为NULL，跳过（Java字段保持默认值）
                continue;
            }

            // 3.6 类型转换处理
            value = convertType(value, field);

            // 3.7 将转换后的值设置到Java对象的对应字段
            field.set(instance, value);
        }

        return instance;
    }

    /**
     * 类型转换，将数据库返回的值转换为Java字段需要的类型
     *
     * <p>数据库驱动返回的类型和Java字段的类型不一定完全匹配，
     * 需要进行转换。比如：</p>
     * <ul>
     *   <li>MySQL的DATETIME类型返回java.sql.Timestamp，但Java实体类用的是java.time.LocalDateTime。</li>
     *   <li>MySQL的BIGINT UNSIGNED返回java.math.BigInteger，但Java实体类用的是java.lang.Long。</li>
     *   <li>MySQL的VARCHAR返回String，但Java实体类用的是枚举类型。</li>
     * </ul>
     *
     * @param value 数据库返回的原始值
     * @param field 目标Java字段
     * @return 转换后的值
     */
    private Object convertType(Object value, Field field) {
        if (value == null) {
            return null;
        }

        Class<?> fieldType = field.getType();

        // 1. Timestamp → LocalDateTime 转换
        //    MySQL的DATETIME类型通过JDBC返回的是java.sql.Timestamp
        //    但我们的实体类使用java.time.LocalDateTime（Java 8+的新时间API）
        if (fieldType == LocalDateTime.class && value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }

        // 2. BigInteger → Long/Integer 转换 (解决 UNSIGNED 整型映射问题)
        //    当 MySQL 字段为 BIGINT UNSIGNED 时，JDBC 驱动会返回 java.math.BigInteger
        //    如果实体类字段是 Long/long 或 Integer/int，需要进行转换，否则反射赋值会报类型不匹配错误
        if (value instanceof java.math.BigInteger) {
            if (fieldType == Long.class || fieldType == long.class) {
                Long longValue = ((java.math.BigInteger) value).longValue();
                log.debug("类型转换: BigInteger -> Long, 字段: {}, 值: {}", field.getName(), longValue);
                return longValue;
            }
            if (fieldType == Integer.class || fieldType == int.class) {
                Integer intValue = ((java.math.BigInteger) value).intValue();
                log.debug("类型转换: BigInteger -> Integer, 字段: {}, 值: {}", field.getName(), intValue);
                return intValue;
            }
        }

        // 3. Long → Integer 转换 (解决 INT UNSIGNED 映射问题)
        //    当 MySQL 字段为 INT UNSIGNED 或 TINYINT UNSIGNED 时，JDBC 驱动可能返回 java.lang.Long
        //    如果实体类字段是 Integer/int，需要进行转换，否则反射赋值会报类型不匹配错误
        if (value instanceof Long && (fieldType == Integer.class || fieldType == int.class)) {
            Integer intValue = ((Long) value).intValue();
            log.debug("类型转换: Long -> Integer, 字段: {}, 值: {}", field.getName(), intValue);
            return intValue;
        }

        // 4. 枚举类型转换
        //    如果Java字段是枚举类型，数据库存储的是枚举名称字符串
        if (fieldType.isEnum() && value instanceof String) {
            @SuppressWarnings("unchecked")
            Class<? extends Enum> enumClass = (Class<? extends Enum>) fieldType;
            try {
                return Enum.valueOf(enumClass, (String) value);
            } catch (IllegalArgumentException e) {
                log.warn("枚举转换失败: 字段={}, 类={}, 原始值={}", field.getName(), enumClass.getSimpleName(), value);
                return null;
            }
        }

        // 5. 其他类型直接返回，由JDBC驱动自动转换
        return value;
    }

    /**
     * 驼峰命名转下划线命名
     *
     * <p>使用正则表达式，在小写字母和大写字母之间插入下划线，然后转小写。</p>
     * <p>例如：</p>
     * <ul>
     *   <li>passwordHash → password_hash</li>
     *   <li>createdAt → created_at</li>
     *   <li>userId → user_id</li>
     *   <li>coverUrl → cover_url</li>
     * </ul>
     *
     * @param camel 驼峰命名字符串
     * @return 下划线命名字符串
     */
    private String camelToUnderscore(String camel) {
        return camel.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}

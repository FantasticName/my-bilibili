package io.github.fantasticname.mybilibili.util;

/**
 * 事务回调函数式接口
 *
 * <p>配合 TxManager.executeInTransaction() 使用，
 * 将需要在事务中执行的业务逻辑以 Lambda 表达式传入。</p>
 *
 * <p>为什么不用 Java 标准的 Supplier？因为 Supplier.get() 不允许抛出受检异常，
 * 而数据库操作（JDBC）会抛出 SQLException。所以我们自定义一个允许抛出异常的接口。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 *   TxManager.executeInTransaction(dataSource, () -> {
 *       userDao.insert(user);
 *       userProfileDao.insert(profile);
 *       return null;
 *   });
 * </pre>
 *
 * @param <T> 回调返回值类型
 * @author FantasticName
 */
@FunctionalInterface
public interface TxSupplier<T> {

    /**
     * 执行业务逻辑并返回结果
     *
     * @return 业务逻辑的返回值
     * @throws Exception 业务逻辑可能抛出的任何异常
     */
    T get() throws Exception;
}

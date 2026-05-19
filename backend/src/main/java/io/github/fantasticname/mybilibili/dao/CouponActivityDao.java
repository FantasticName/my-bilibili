package io.github.fantasticname.mybilibili.dao;

import io.github.fantasticname.mybilibili.annotation.Component;
import io.github.fantasticname.mybilibili.dao.base.BaseDao;
import io.github.fantasticname.mybilibili.dao.base.ConnectionHolder;
import io.github.fantasticname.mybilibili.entity.CouponActivity;

import java.util.List;

/**
 * 优惠券活动DAO，封装优惠券活动表的数据库操作
 *
 * <p>继承 BaseDao&lt;CouponActivity&gt;，获得通用CRUD方法。
 * 秒杀的核心DB操作是"批量减少库存"和"查询活动详情"。</p>
 *
 * @author FantasticName
 */
@Component
public class CouponActivityDao extends BaseDao<CouponActivity> {

    /**
     * 根据ID查询活动
     *
     * @param id 活动ID
     * @return 活动实体，不存在返回null
     */
    public CouponActivity findById(long id) {
        String sql = "SELECT * FROM coupon_activity WHERE id = ?";
        return queryOne(sql, id);
    }

    /**
     * 插入活动（创建新活动）
     *
     * <p>插入时同时设置totalStock和remainStock为相同的值。</p>
     *
     * @param activity 活动实体
     * @return 自增主键ID
     */
    public long insert(CouponActivity activity) {
        String sql = "INSERT INTO coupon_activity (name, description, total_stock, remain_stock, " +
                     "per_user_limit, grab_limit_type, start_time, end_time, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return executeInsert(sql,
                activity.getName(),
                activity.getDescription(),
                activity.getTotalStock(),
                activity.getRemainStock(),
                activity.getPerUserLimit(),
                activity.getGrabLimitType(),
                activity.getStartTime() != null ? activity.getStartTime().toString() : null,
                activity.getEndTime() != null ? activity.getEndTime().toString() : null,
                activity.getStatus()
        );
    }

    /**
     * 更新活动状态
     *
     * @param id     活动ID
     * @param status 状态值（0-未开始，1-进行中，2-已结束）
     */
    public void updateStatus(long id, int status) {
        String sql = "UPDATE coupon_activity SET status = ?, updated_at = NOW() WHERE id = ?";
        executeUpdate(sql, status, id);
    }

    /**
     * 减少活动库存（异步落库时使用）
     *
     * <p>每次异步写入抢购记录时，同步更新DB中的库存。
     * SQL中使用 remain_stock = remain_stock - 1 保证原子减1。
     * 添加 remain_stock > 0 条件防止库存变成负数。</p>
     *
     * @param activityId 活动ID
     * @param count      减少的数量
     * @return 影响的行数（1=成功，0=库存已为0）
     */
    public int decrementStock(long activityId, int count) {
        String sql = "UPDATE coupon_activity SET remain_stock = remain_stock - ? " +
                     "WHERE id = ? AND remain_stock >= ?";
        return executeUpdate(sql, count, activityId, count);
    }

    /**
     * 查询所有进行中的活动
     *
     * @return 活动列表
     */
    public List<CouponActivity> listActive() {
        String sql = "SELECT * FROM coupon_activity WHERE status = 1 ORDER BY start_time DESC";
        return queryList(sql);
    }

    /**
     * 查询所有活动
     *
     * @return 活动列表
     */
    public List<CouponActivity> listAll() {
        String sql = "SELECT * FROM coupon_activity ORDER BY created_at DESC";
        return queryList(sql);
    }
}
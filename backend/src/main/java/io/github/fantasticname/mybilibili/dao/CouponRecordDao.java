package io.github.fantasticname.mybilibili.dao;

import io.github.fantasticname.mybilibili.annotation.Component;
import io.github.fantasticname.mybilibili.dao.base.BaseDao;
import io.github.fantasticname.mybilibili.dao.base.ConnectionHolder;
import io.github.fantasticname.mybilibili.entity.CouponRecord;

import java.util.List;

/**
 * 优惠券抢购记录DAO，封装优惠券抢购记录表的数据库操作
 *
 * <p>继承 BaseDao&lt;CouponRecord&gt;，获得通用CRUD方法。
 * 主要用于查询用户的抢购记录。</p>
 *
 * @author FantasticName
 */
@Component
public class CouponRecordDao extends BaseDao<CouponRecord> {

    /**
     * 插入一条抢购记录
     *
     * <p>抢购记录由CouponGrabConsumer异步写入DB。
     * 注意：grabTime和createdAt设为当前时间。
     * couponCode由Consumer生成（UUID格式16位大写字母+数字）。</p>
     *
     * @param record 抢购记录实体（必须包含couponCode）
     * @return 自增主键ID
     */
    public long insert(CouponRecord record) {
        String sql = "INSERT INTO coupon_record (user_id, activity_id, coupon_code, status, grab_time, created_at) " +
                     "VALUES (?, ?, ?, 0, NOW(), NOW())";
        return executeInsert(sql, record.getUserId(), record.getActivityId(), record.getCouponCode());
    }

    /**
     * 查询用户在指定活动中的抢购记录数
     *
     * <p>用于判断用户是否已达到"每人限抢X个"的上限。
     * 虽然秒杀时Redis也做了限购检查，但DB查询作为最终确认。</p>
     *
     * @param userId     用户ID
     * @param activityId 活动ID
     * @return 抢购记录数
     */
    public int countByUserAndActivity(long userId, long activityId) {
        String sql = "SELECT COUNT(*) AS cnt FROM coupon_record WHERE user_id = ? AND activity_id = ?";
        ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, activityId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("查询抢购记录数失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
        return 0;
    }

    /**
     * 查询用户在指定活动中的所有抢购记录
     *
     * @param userId     用户ID
     * @param activityId 活动ID
     * @return 抢购记录列表
     */
    public List<CouponRecord> listByUserAndActivity(long userId, long activityId) {
        String sql = "SELECT * FROM coupon_record WHERE user_id = ? AND activity_id = ? ORDER BY grab_time DESC";
        return queryList(sql, userId, activityId);
    }

    /**
     * 查询用户的所有抢购记录
     *
     * @param userId 用户ID
     * @return 抢购记录列表
     */
    public List<CouponRecord> listByUser(long userId) {
        String sql = "SELECT * FROM coupon_record WHERE user_id = ? ORDER BY grab_time DESC";
        return queryList(sql, userId);
    }
}
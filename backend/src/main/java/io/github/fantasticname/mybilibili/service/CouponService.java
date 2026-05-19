package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.entity.CouponActivity;

import java.util.List;
import java.util.Map;

/**
 * 优惠券服务接口
 *
 * <p>提供优惠券活动管理和秒杀抢购功能。</p>
 *
 * @author FantasticName
 */
public interface CouponService {

    /**
     * 创建优惠券活动
     *
     * @param activity 活动实体
     * @return 活动ID
     */
    long createActivity(CouponActivity activity);

    /**
     * 抢购优惠券（核心秒杀逻辑）
     *
     * <p>使用Redis Lua脚本保证原子性：检查库存→检查限购→扣库存→加记录。
     * 抢购成功后异步发RocketMQ消息落库。</p>
     *
     * @param userId     用户ID
     * @param activityId 活动ID
     * @param idempotentToken 幂等性Token（从Header获取）
     * @return 抢购结果码（1=成功，-1=活动不存在，-2=库存不足，-3=超出限购，-4=重复抢购）
     */
    int grabCoupon(Long userId, Long activityId, String idempotentToken);

    /**
     * 查询活动详情
     *
     * @param activityId 活动ID
     * @return 活动实体
     */
    CouponActivity getActivityDetail(Long activityId);

    /**
     * 查询用户抢购记录
     *
     * @param userId     用户ID
     * @param activityId 活动ID
     * @return 抢购记录列表（Map包含活动信息+抢购时间+状态）
     */
    List<Map<String, Object>> getUserRecords(Long userId, Long activityId);

    /**
     * 查询所有进行中的活动
     *
     * @return 活动列表
     */
    List<CouponActivity> listActiveActivities();
}
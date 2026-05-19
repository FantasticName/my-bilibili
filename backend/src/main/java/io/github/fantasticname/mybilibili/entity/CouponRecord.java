package io.github.fantasticname.mybilibili.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券抢购记录实体类，对应数据库中的 coupon_record 表
 *
 * <p>每次用户成功抢到优惠券，插入一条记录。
 * 通过 user_id + activity_id 联合查询可以判断用户是否已参与过该活动。</p>
 *
 * <p>记录写入方式：
 * Redis中Lua脚本扣库存成功后 → 发RocketMQ消息 → CouponGrabConsumer异步落库</p>
 *
 * <p>为什么异步落库？</p>
 * <ul>
 *   <li>秒杀QPS极高（如1万QPS），如果每次抢购同步写DB，DB扛不住</li>
 *   <li>Redis已经记录了"已抢购用户集合"，查询不依赖DB</li>
 *   <li>MQ承担削峰填谷——1万QPS变匀速写入，DB压力可控</li>
 * </ul>
 *
 * @author FantasticName
 */
@Data
public class CouponRecord implements Serializable {

    /**
     * 记录ID（主键，自增）
     */
    private Long id;

    /**
     * 用户ID（谁抢到了优惠券）
     */
    private Long userId;

    /**
     * 活动ID（抢的是哪个活动的优惠券）
     */
    private Long activityId;

    /**
     * 优惠券码（UUID格式，16位大写字母+数字，抢购成功时生成）
     */
    private String couponCode;

    /**
     * 优惠券状态（0-有效，1-已使用，2-已过期）
     */
    private Integer status;

    /**
     * 抢购时间
     */
    private LocalDateTime grabTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
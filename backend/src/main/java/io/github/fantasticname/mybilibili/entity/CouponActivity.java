package io.github.fantasticname.mybilibili.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券活动实体类，对应数据库中的 coupon_activity 表
 *
 * <p>优惠券活动是秒杀模块的核心——管理员创建一个活动，
 * 设置总数量、每人限抢数量、开始/结束时间等参数，用户在活动时间内抢购。</p>
 *
 * <p>核心字段说明：</p>
 * <ul>
 *   <li><b>totalStock</b>：总库存（活动创建时确定，不可变）</li>
 *   <li><b>remainStock</b>：剩余库存（每抢一次减1，减到0为止）</li>
 *   <li><b>perUserLimit</b>：每人限抢数量（防黄牛囤货）</li>
 *   <li><b>startTime / endTime</b>：活动时间窗口</li>
 *   <li><b>grabLimitType</b>：库存扣减上限类型（0-不限制，1-限制每人最多抢X个）</li>
 * </ul>
 *
 * <p>秒杀流程中，库存扣减在Redis中通过Lua脚本原子完成，
 * 扣减成功后通过RocketMQ异步写入coupon_record表。</p>
 *
 * @author FantasticName
 */
@Data
public class CouponActivity implements Serializable {

    /**
     * 活动ID（主键，自增）
     */
    private Long id;

    /**
     * 活动名称（如"618满100减20优惠券"）
     */
    private String name;

    /**
     * 活动描述
     */
    private String description;

    /**
     * 总库存（创建后不可变）
     *
     * <p>秒杀的核心数据：库存为0时活动结束。
     * Redis中也有一个库存键（coupon:stock:{id}）用于缓存，初始值=totalStock。</p>
     */
    private Integer totalStock;

    /**
     * 剩余库存（动态减少）
     *
     * <p>每一次成功抢购：Redis中的库存-1，DB中的remainStock也-1（异步）。
     * 当Redis库存为0时，剩余请求直接返回"已抢光"。</p>
     */
    private Integer remainStock;

    /**
     * 每人限抢数量（0表示不限制）
     *
     * <p>防黄牛的关键参数！用户不能无限抢。
     * 在Lua脚本中通过Redis Set检查用户是否已抢够上限。</p>
     */
    private Integer perUserLimit;

    /**
     * 库存扣减上限类型（0-不限制上限，1-限制每人最多perUserLimit个）
     */
    private Integer grabLimitType;

    /**
     * 活动开始时间
     *
     * <p>开始时间前请求直接返回"活动未开始"。
     * JsonFormat指定了日期格式，兼容前端传来的 "yyyy-MM-dd HH:mm:ss" 格式（空格分隔）。</p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /**
     * 活动结束时间
     *
     * <p>结束时间后请求直接返回"活动已结束"。
     * JsonFormat指定了日期格式，兼容前端传来的 "yyyy-MM-dd HH:mm:ss" 格式（空格分隔）。</p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /**
     * 活动状态（0-未开始，1-进行中，2-已结束）
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
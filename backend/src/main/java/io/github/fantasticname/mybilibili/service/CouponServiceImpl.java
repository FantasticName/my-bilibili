package io.github.fantasticname.mybilibili.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.fantasticname.mybilibili.annotation.Autowired;
import io.github.fantasticname.mybilibili.annotation.Service;
import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.github.fantasticname.mybilibili.config.RocketMQConfig;
import io.github.fantasticname.mybilibili.context.TraceIdContext;
import io.github.fantasticname.mybilibili.dao.CouponActivityDao;
import io.github.fantasticname.mybilibili.dao.CouponRecordDao;
import io.github.fantasticname.mybilibili.entity.CouponActivity;
import io.github.fantasticname.mybilibili.entity.CouponRecord;
import io.github.fantasticname.mybilibili.util.IdempotentUtil;
import io.github.fantasticname.mybilibili.util.RedisUtil;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 优惠券服务实现类（秒杀核心逻辑）
 *
 * <p>秒杀系统的核心挑战：</p>
 * <ul>
 *   <li><b>高并发</b>：1万人同时抢100张券，DB扛不住</li>
 *   <li><b>超卖</b>：并发扣库存可能导致库存减到负数</li>
 *   <li><b>黄牛</b>：同一用户抢多张券</li>
 * </ul>
 *
 * <p>解决方案：</p>
 * <ul>
 *   <li><b>Redis缓存库存</b>：活动开始时预热库存到Redis，读写都在Redis完成</li>
 *   <li><b>Lua脚本原子操作</b>：检查库存+检查限购+扣库存+加记录，四步一体</li>
 *   <li><b>RocketMQ异步落库</b>：扣库存成功后发MQ，由消费者慢慢写DB</li>
 *   <li><b>幂等性Token</b>：防止用户重复提交</li>
 *   <li><b>Sentinel限流</b>：QPS超过500时触发限流，保护后端</li>
 * </ul>
 *
 * <p>秒杀流程（一次完整请求）：</p>
 * <ol>
 *   <li>前端调用 /api/idempotent/token 获取Token</li>
 *   <li>用户点击"抢购"，前端带Token调用 /api/coupon/grab</li>
 *   <li>后端校验幂等性Token</li>
 *   <li>后端执行Lua脚本（原子操作）</li>
 *   <li>成功 → 发RocketMQ消息 → 返回"抢购成功"</li>
 *   <li>RocketMQ → CouponGrabConsumer → 写coupon_record → 扣DB库存</li>
 * </ol>
 *
 * @author FantasticName
 */
@Service
public class CouponServiceImpl implements CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponServiceImpl.class);

    /**
     * Redis库存Key前缀
     *
     * <p>格式：coupon:stock:{activityId}
     * 值：整数值（如100），每次抢购减1</p>
     */
    private static final String COUPON_STOCK_KEY_PREFIX = "coupon:stock:";

    /**
     * Redis已抢购用户集合Key前缀
     *
     * <p>格式：coupon:grabbed:{activityId}
     * 值：Set集合，存储已抢到的用户ID</p>
     */
    private static final String COUPON_GRABBED_SET_PREFIX = "coupon:grabbed:";

    /**
     * Redis活动详情缓存Key前缀
     *
     * <p>格式：coupon:activity:{activityId}
     * 值：JSON格式的活动详情</p>
     */
    private static final String COUPON_ACTIVITY_KEY_PREFIX = "coupon:activity:";

    /**
     * 秒杀Lua脚本（原子操作的核心）
     *
     * <p>这个脚本在Redis中原子执行，不会被其他命令打断。
     * 主要逻辑：检查库存→检查限购→扣库存→加用户到已抢集合。</p>
     *
     * <p>返回值说明：
     * 1 = 抢购成功
     * -1 = 活动不存在
     * -2 = 库存不足
     * -3 = 超出每人限购
     * -4 = 已抢过（重复）</p>
     *
     * <p>KEYS[1] = coupon:stock:{activityId}  (库存键)
     * KEYS[2] = coupon:grabbed:{activityId} (已抢购用户集合键)
     * ARGV[1] = userId (当前用户ID)
     * ARGV[2] = perUserLimit (每人限抢数量，0=不限制)</p>
     */
    private static final String SECKILL_LUA_SCRIPT =
            "local stock = redis.call('GET', KEYS[1])\n" +
            "if not stock or tonumber(stock) <= 0 then\n" +
            "    return -2\n" +
            "end\n" +
            "local limit = tonumber(ARGV[2])\n" +
            "if limit > 0 then\n" +
            "    local userCount = redis.call('SCARD', KEYS[2])\n" +
            "    if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then\n" +
            "        return -4\n" +
            "    end\n" +
            "    if userCount >= limit then\n" +
            "        return -3\n" +
            "    end\n" +
            "end\n" +
            "redis.call('DECR', KEYS[1])\n" +
            "redis.call('SADD', KEYS[2], ARGV[1])\n" +
            "return 1";

    @Autowired
    private CouponActivityDao couponActivityDao;

    @Autowired
    private CouponRecordDao couponRecordDao;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public CouponServiceImpl() {
    }

    @Override
    public long createActivity(CouponActivity activity) {
        log.info("创建优惠券活动: name={}, stock={}", activity.getName(), activity.getTotalStock());

        // 初始化剩余库存 = 总库存
        activity.setRemainStock(activity.getTotalStock());
        // 默认状态为未开始
        if (activity.getStatus() == null) {
            activity.setStatus(0);
        }
        // 默认库存扣减类型为限制模式
        if (activity.getGrabLimitType() == null) {
            activity.setGrabLimitType(1);
        }

        long activityId = couponActivityDao.insert(activity);
        log.info("优惠券活动创建成功: activityId={}", activityId);

        // 【秒杀预热】将库存预热到Redis
        if (activity.getStatus() == 1) {
            warmUpActivity(activityId, activity.getTotalStock());
        }

        return activityId;
    }

    @Override
    public int grabCoupon(Long userId, Long activityId, String idempotentToken) {
        log.info("优惠券抢购: userId={}, activityId={}", userId, activityId);

        // ==================== 第0步：幂等性校验 ====================
        // 使用Token机制防止重复提交（用户狂点按钮）
        if (idempotentToken != null && !idempotentToken.isEmpty()) {
            if (!IdempotentUtil.consumeToken(idempotentToken)) {
                log.warn("幂等性校验失败（重复请求）: userId={}, activityId={}", userId, activityId);
                return -4; // 重复抢购
            }
        }

        // ==================== 第1步：查询活动详情 ====================
        CouponActivity activity = getActivityFromCache(activityId);
        if (activity == null) {
            log.warn("活动不存在: activityId={}", activityId);
            return -1;
        }

        // 检查活动时间
        LocalDateTime now = LocalDateTime.now();
        if (activity.getStartTime() != null && now.isBefore(activity.getStartTime())) {
            log.warn("活动未开始: activityId={}", activityId);
            return -1;
        }
        if (activity.getEndTime() != null && now.isAfter(activity.getEndTime())) {
            log.warn("活动已结束: activityId={}", activityId);
            return -1;
        }

        // ==================== 第2步：执行Lua脚本（原子秒杀） ====================
        // 构建Lua脚本的KEY和ARGV参数
        String stockKey = COUPON_STOCK_KEY_PREFIX + activityId;
        String grabbedKey = COUPON_GRABBED_SET_PREFIX + activityId;

        List<String> keys = Arrays.asList(stockKey, grabbedKey);
        List<String> args = Arrays.asList(
                String.valueOf(userId),
                String.valueOf(activity.getPerUserLimit() != null ? activity.getPerUserLimit() : 0)
        );

        // 在Redis中原子执行Lua脚本
        Long result = RedisUtil.evalLong(SECKILL_LUA_SCRIPT, keys, args);
        log.info("Lua脚本执行结果: result={}, userId={}, activityId={}", result, userId, activityId);

        if (result == null) {
            log.error("Lua脚本执行返回null: activityId={}", activityId);
            return -1;
        }

        int resultCode = result.intValue();

        // ==================== 第3步：抢购成功 → 发RocketMQ异步落库 ====================
        if (resultCode == 1) {
            sendGrabMessage(userId, activityId);
            log.info("优惠券抢购成功: userId={}, activityId={}", userId, activityId);
        }

        return resultCode;
    }

    @Override
    public CouponActivity getActivityDetail(Long activityId) {
        return getActivityFromCache(activityId);
    }

    @Override
    public List<Map<String, Object>> getUserRecords(Long userId, Long activityId) {
        List<CouponRecord> records;
        if (activityId != null) {
            records = couponRecordDao.listByUserAndActivity(userId, activityId);
        } else {
            records = couponRecordDao.listByUser(userId);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (CouponRecord r : records) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("userId", r.getUserId());
            map.put("activityId", r.getActivityId());
            map.put("status", r.getStatus());
            map.put("grabTime", r.getGrabTime() != null ? r.getGrabTime().toString() : null);

            // 补充活动信息
            CouponActivity activity = couponActivityDao.findById(r.getActivityId());
            if (activity != null) {
                map.put("activityName", activity.getName());
                map.put("activityDesc", activity.getDescription());
            }
            result.add(map);
        }
        return result;
    }

    @Override
    public List<CouponActivity> listActiveActivities() {
        return couponActivityDao.listActive();
    }

    /**
     * 秒杀预热——活动开始时将库存预热到Redis
     *
     * <p>预热时机：
     * <ul>
     *   <li>管理员将活动状态改为"进行中"时</li>
     *   <li>应用启动时加载已有活动</li>
     * </ul>
     *
     * <p>预热内容：
     * <ul>
     *   <li>coupon:stock:{id} = totalStock</li>
     *   <li>coupon:grabbed:{id} = Set（空集合，随抢购动态增长）</li>
     *   <li>coupon:activity:{id} = 活动详情JSON</li>
     * </ul>
     *
     * @param activityId 活动ID
     * @param totalStock 总库存
     */
    public void warmUpActivity(Long activityId, int totalStock) {
        log.info("秒杀预热: activityId={}, stock={}", activityId, totalStock);
        // 设置库存到Redis
        RedisUtil.set(COUPON_STOCK_KEY_PREFIX + activityId, String.valueOf(totalStock), 0);
        // 清空已抢购用户集合（防止旧数据影响）
        RedisUtil.del(COUPON_GRABBED_SET_PREFIX + activityId);
        // 缓存活动详情（30分钟TTL）
        CouponActivity activity = couponActivityDao.findById(activityId);
        if (activity != null) {
            RedisUtil.setObject(COUPON_ACTIVITY_KEY_PREFIX + activityId, activity, 1800);
        }
        log.info("秒杀预热完成: activityId={}", activityId);
    }

    /**
     * 从缓存获取活动详情（缓存穿透保护）
     *
     * <p>优先从Redis缓存获取，缓存未命中时查DB并写缓存。</p>
     *
     * @param activityId 活动ID
     * @return 活动实体，不存在返回null
     */
    private CouponActivity getActivityFromCache(Long activityId) {
        String cacheKey = COUPON_ACTIVITY_KEY_PREFIX + activityId;

        // 查缓存
        CouponActivity cached = RedisUtil.getObject(cacheKey, CouponActivity.class);
        if (cached != null) {
            return cached;
        }

        // 查DB
        CouponActivity activity = couponActivityDao.findById(activityId);
        if (activity != null) {
            // 写缓存（30分钟TTL）
            RedisUtil.setObject(cacheKey, activity, 1800);
        }
        return activity;
    }

    /**
     * 库存对账方法——将Redis库存与DB库存进行对账
     *
     * <p>用于定时任务或人工触发，校验Redis中的库存与数据库中的库存是否一致，
     * 如果不一致则以数据库为准进行修正。</p>
     *
     * @param activityId 活动ID
     */
    public void reconcileStock(Long activityId) {
        log.info("开始库存对账: activityId={}", activityId);

        // 从数据库获取活动信息
        CouponActivity activity = couponActivityDao.findById(activityId);
        if (activity == null) {
            log.warn("库存对账失败，活动不存在: activityId={}", activityId);
            return;
        }

        // 从Redis获取当前库存
        String stockStr = RedisUtil.get(COUPON_STOCK_KEY_PREFIX + activityId);
        int redisStock = stockStr != null ? Integer.parseInt(stockStr) : -1;

        // 从数据库获取剩余库存
        int dbStock = activity.getRemainStock();

        if (redisStock != dbStock) {
            log.warn("库存不一致! activityId={}, redisStock={}, dbStock={}", activityId, redisStock, dbStock);
            // 以数据库为准，修正Redis库存
            RedisUtil.set(COUPON_STOCK_KEY_PREFIX + activityId, String.valueOf(dbStock), 0);
            log.info("库存已修正: activityId={}, 修正后库存={}", activityId, dbStock);
        } else {
            log.info("库存一致: activityId={}, stock={}", activityId, dbStock);
        }
    }

    /**
     * 发送抢购成功消息到RocketMQ
     *
     * <p>消息内容：{"userId":1001,"activityId":1}
     * Topic：COUPON_GRAB
     * 消费者：CouponGrabConsumer</p>
     *
     * <p>如果NameServer不可用（RocketMQ没启动），抢购仍算成功，
     * 可以改成线程池异步落库的方式兜底。</p>
     *
     * @param userId     用户ID
     * @param activityId 活动ID
     */
    private void sendGrabMessage(Long userId, Long activityId) {
        try {
            // 构建消息体
            Map<String, Object> msgBody = new HashMap<>();
            msgBody.put("userId", userId);
            msgBody.put("activityId", activityId);
            String body = MAPPER.writeValueAsString(msgBody);

            // 创建RocketMQ消息
            Message msg = new Message("COUPON_GRAB", // Topic
                    "grab",                         // Tag
                    body.getBytes(StandardCharsets.UTF_8));

            // 将当前请求的TraceId放入消息属性，以便消费者恢复链路追踪
            String currentTraceId = TraceIdContext.getCurrentTraceId();
            if (currentTraceId != null) {
                msg.putUserProperty("TRACE_ID", currentTraceId);
            }

            // 获取Producer并发送
            DefaultMQProducer producer = RocketMQConfig.getProducer();
            if (producer != null) {
                producer.send(msg);
                log.info("RocketMQ抢购消息发送成功: userId={}, activityId={}, traceId={}",
                        userId, activityId, currentTraceId);
            } else {
                // RocketMQ不可用时，降级为同步落库
                log.warn("RocketMQ不可用，降级为同步落库: userId={}, activityId={}", userId, activityId);
                CouponRecord record = new CouponRecord();
                record.setUserId(userId);
                record.setActivityId(activityId);
                couponRecordDao.insert(record);
                couponActivityDao.decrementStock(activityId, 1);
            }
        } catch (Exception e) {
            log.error("发送抢购消息失败: userId={}, activityId={}, error={}", userId, activityId, e.getMessage());
            // 消息发送失败不抛异常，抢购仍算成功
            // 降级为同步落库
            try {
                CouponRecord record = new CouponRecord();
                record.setUserId(userId);
                record.setActivityId(activityId);
                couponRecordDao.insert(record);
                couponActivityDao.decrementStock(activityId, 1);
            } catch (Exception ex) {
                log.error("降级同步落库也失败: userId={}, activityId={}, error={}", userId, activityId, ex.getMessage());
            }
        }
    }
}
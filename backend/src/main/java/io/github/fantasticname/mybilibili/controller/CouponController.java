package io.github.fantasticname.mybilibili.controller;

import io.github.fantasticname.mybilibili.annotation.*;
import io.github.fantasticname.mybilibili.common.Result;
import io.github.fantasticname.mybilibili.context.UserContext;
import io.github.fantasticname.mybilibili.dao.CouponActivityDao;
import io.github.fantasticname.mybilibili.entity.CouponActivity;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.service.CouponService;
import io.github.fantasticname.mybilibili.util.IdempotentUtil;
import io.github.fantasticname.mybilibili.util.RedisUtil;
import io.github.fantasticname.mybilibili.util.SentinelUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 优惠券控制器，处理优惠券活动管理和秒杀抢购接口
 *
 * <p>接口列表：</p>
 * <ul>
 *   <li>POST /api/coupon/create - 创建优惠券活动（管理员）</li>
 *   <li>POST /api/coupon/activity/{id}/start - 发布/启动活动（管理员）</li>
 *   <li>POST /api/coupon/grab - 抢购优惠券（核心秒杀接口）</li>
 *   <li>GET /api/coupon/activity/{id} - 活动详情</li>
 *   <li>GET /api/coupon/activities - 进行中的活动列表</li>
 *   <li>GET /api/coupon/token - 获取幂等性Token</li>
 *   <li>GET /api/coupon/records - 用户抢购记录</li>
 * </ul>
 *
 * @author FantasticName
 */
@RestController
@io.github.fantasticname.mybilibili.annotation.RequestMapping("/coupon")
public class CouponController {

    private static final Logger log = LoggerFactory.getLogger(CouponController.class);

    @io.github.fantasticname.mybilibili.annotation.Autowired
    private CouponService couponService;

    @io.github.fantasticname.mybilibili.annotation.Autowired
    private CouponActivityDao couponActivityDao;

    public CouponController() {
    }

    /**
     * 创建优惠券活动（管理员接口）
     *
     * <p>只有管理员可以发布优惠券秒杀活动。
     * 前端传入活动名称、描述、库存、限购数量、开始/结束时间等参数。</p>
     *
     * <p>创建后活动状态为"未开始"(0)，需要调用 /start 接口发布。</p>
     *
     * @param activity 活动信息（从JSON请求体解析）
     * @return 创建结果（包含活动ID）
     */
    @PostMapping("/create")
    @RequirePermission("coupon:create")
    public Result<Map<String, Object>> create(@RequestBody CouponActivity activity) {
        User currentUser = UserContext.get();
        log.info("创建优惠券活动: name={}, stock={}, adminId={}", activity.getName(), activity.getTotalStock(), currentUser.getId());

        if (activity.getName() == null || activity.getName().isEmpty()) {
            return Result.error(400, "活动名称不能为空");
        }
        if (activity.getTotalStock() == null || activity.getTotalStock() <= 0) {
            return Result.error(400, "库存必须大于0");
        }
        // 前端不传 grabLimitType 时设默认值为1（限制每人最多perUserLimit个）
        if (activity.getGrabLimitType() == null) {
            activity.setGrabLimitType(1);
        }
        // remainStock 初始等于 totalStock
        activity.setRemainStock(activity.getTotalStock());

        long activityId = couponService.createActivity(activity);

        Map<String, Object> data = new HashMap<>();
        data.put("activityId", activityId);
        data.put("message", "活动创建成功，请点击'发布'按钮启动活动");
        return Result.success(data);
    }

    /**
     * 发布/启动优惠券活动（管理员接口）
     *
     * <p>将活动状态从"未开始"(0)改为"进行中"(1)，
     * 同时将库存预热到Redis（秒杀核心步骤）。</p>
     *
     * <p>预热后的Redis数据结构：
     * coupon:stock:{id} = totalStock（库存数）
     * coupon:grabbed:{id} = Set（已抢购用户集合）</p>
     *
     * @param activityId 活动ID
     * @return 发布结果
     */
    @PostMapping("/activity/{id}/start")
    @RequirePermission("coupon:create")
    public Result<Map<String, Object>> startActivity(@PathVariable("id") Long activityId) {
        User currentUser = UserContext.get();
        log.info("发布优惠券活动: activityId={}, adminId={}", activityId, currentUser.getId());

        // 查询活动是否存在
        CouponActivity activity = couponService.getActivityDetail(activityId);
        if (activity == null) {
            return Result.error(404, "活动不存在");
        }

        // 更新状态为"进行中"
        couponActivityDao.updateStatus(activityId, 1);

        // 秒杀预热——将库存写入Redis
        // warmUpActivity需要注入到Controller或者调用Service公开方法
        // 这里通过Service预热：先手动预热
        try {
            RedisUtil.set("coupon:stock:" + activityId, String.valueOf(activity.getTotalStock()), 0);
            RedisUtil.del("coupon:grabbed:" + activityId);
            log.info("秒杀预热完成: activityId={}, stock={}", activityId, activity.getTotalStock());
        } catch (Exception e) {
            log.error("秒杀预热失败: activityId={}, error={}", activityId, e.getMessage());
            return Result.error(500, "活动发布成功但Redis预热失败，请联系管理员");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("activityId", activityId);
        data.put("message", "活动已发布！库存已预热到Redis，用户现在可以抢购了");
        return Result.success(data);
    }

    /**
     * 抢购优惠券（核心秒杀接口）
     *
     * <p>使用Sentinel做限流保护（资源名：coupon-grab，QPS=500）。
     * 使用幂等性Token防止重复提交。</p>
     *
     * <p>Lua脚本执行结果码：
     * 1=成功，-2=库存不足，-3=超出限购，-4=重复抢购</p>
     *
     * @param activityId 活动ID
     * @param req        请求对象（用于从Header获取幂等性Token）
     * @return 抢购结果
     */
    @PostMapping("/grab")
    @RequirePermission("coupon:grab")
    public Result<Map<String, Object>> grab(
            @RequestParam("activityId") Long activityId,
            javax.servlet.http.HttpServletRequest req) {

        User currentUser = UserContext.get();
        log.info("优惠券抢购请求: userId={}, activityId={}", currentUser.getId(), activityId);

        // 从Header获取幂等性Token
        String idempotentToken = req.getHeader("X-Idempotent-Token");

        // ==================== Sentinel限流保护 ====================
        // 被限流时返回提示信息
        return SentinelUtil.executeWithProtection("coupon-grab", () -> {
            Map<String, Object> data = new HashMap<>();
            int resultCode = couponService.grabCoupon(currentUser.getId(), activityId, idempotentToken);

            data.put("resultCode", resultCode);
            switch (resultCode) {
                case 1:
                    data.put("message", "恭喜！抢购成功！");
                    break;
                case -2:
                    data.put("message", "很遗憾，库存已空");
                    break;
                case -3:
                    data.put("message", "超出限购数量");
                    break;
                case -4:
                    data.put("message", "请勿重复抢购");
                    break;
                default:
                    data.put("message", "抢购失败，请重试");
                    break;
            }
            return data;
        }, () -> {
            // 限流降级：被限流时返回提示
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("resultCode", -999);
            fallback.put("message", "抢购人数过多，请稍后再试");
            return fallback;
        });
    }

    /**
     * 获取幂等性Token（抢购前调用）
     *
     * <p>前端进入抢购页面时调用此接口获取Token，
     * 提交抢购时在Header中带上 X-Idempotent-Token。</p>
     *
     * @return Token字符串
     */
    @GetMapping("/token")
    public Result<Map<String, Object>> getToken() {
        String token = IdempotentUtil.generateToken();
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("ttl", 300); // 5分钟有效期
        log.info("发放幂等性Token: token={}...", token.substring(0, Math.min(8, token.length())));
        return Result.success(data);
    }

    /**
     * 查询活动详情
     *
     * @param activityId 活动ID
     * @return 活动详情
     */
    @GetMapping("/activity/{id}")
    public Result<CouponActivity> getActivityDetail(@PathVariable("id") Long activityId) {
        log.info("查询活动详情: activityId={}", activityId);
        CouponActivity activity = couponService.getActivityDetail(activityId);
        if (activity == null) {
            return Result.error(404, "活动不存在");
        }
        return Result.success(activity);
    }

    /**
     * 查询进行中的活动列表
     *
     * @return 活动列表
     */
    @GetMapping("/activities")
    public Result<List<CouponActivity>> listActive() {
        log.info("查询进行中的活动列表");
        List<CouponActivity> activities = couponService.listActiveActivities();
        return Result.success(activities);
    }

    /**
     * 查询所有活动（管理员接口，包括未发布和已结束的活动）
     *
     * <p>用于管理员管理面板，查看所有活动状态。
     * 需要 coupon:create 权限。</p>
     *
     * @return 所有活动列表
     */
    @GetMapping("/all")
    @RequirePermission("coupon:create")
    public Result<List<CouponActivity>> listAll() {
        log.info("管理员查询所有活动列表");
        List<CouponActivity> activities = couponActivityDao.listAll();
        return Result.success(activities);
    }

    /**
     * 查询用户的抢购记录
     *
     * @param activityId 活动ID（可选，null时查询所有）
     * @return 抢购记录列表
     */
    @GetMapping("/records")
    @RequirePermission("coupon:records")
    public Result<List<Map<String, Object>>> getRecords(
            @RequestParam(value = "activityId", required = false) Long activityId) {
        User currentUser = UserContext.get();
        log.info("查询抢购记录: userId={}, activityId={}", currentUser.getId(), activityId);
        List<Map<String, Object>> records = couponService.getUserRecords(currentUser.getId(), activityId);
        return Result.success(records);
    }
}
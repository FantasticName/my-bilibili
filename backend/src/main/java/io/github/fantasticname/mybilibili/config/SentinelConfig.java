package io.github.fantasticname.mybilibili.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel流量控制与熔断降级配置类
 *
 * <p>Sentinel是阿里巴巴开源的"流量防卫兵"，以流量为切入点，
 * 从流量控制、熔断降级、系统负载保护等多个维度保护服务的稳定性。</p>
 *
 * <p>核心概念：</p>
 * <ul>
 *   <li><b>资源（Resource）</b>：被保护的对象，可以是一个接口、一个方法、一段代码。
 *       通过 SphU.entry("资源名") 包裹需要保护的代码</li>
 *   <li><b>规则（Rule）</b>：对资源的保护策略，分为：
 *     <ul>
 *       <li>FlowRule（流量控制规则）：QPS限流、线程数限流</li>
 *       <li>DegradeRule（熔断降级规则）：慢调用比例、异常比例、异常数熔断</li>
 *     </ul>
 *   </li>
 *   <li><b>Entry</b>：记录了本次请求是否通过、是否需要等待等信息</li>
 *   <li><b>BlockException</b>：当请求被限流/熔断时抛出此异常</li>
 * </ul>
 *
 * <p>本项目没有Spring，使用Sentinel原生API（sentinel-core），
 * 通过SphU.entry() + try-catch BlockException的方式保护接口。</p>
 *
 * <p>限流/熔断后的处理策略：
 * 不返回429错误码，而是返回热榜兜底数据（与Redis故障降级复用同一个hotFallback列表），
 * 这样用户体验更好——至少能看到内容，而不是白屏+错误提示。</p>
 *
 * @author FantasticName
 */
public class SentinelConfig {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(SentinelConfig.class);

    /**
     * 初始化Sentinel规则（在ContainerInitializerListener中调用）
     *
     * <p>Sentinel的规则加载是幂等的——多次调用不会重复加载。
     * 规则加载到内存中，不会持久化到磁盘（项目重启后需重新加载）。</p>
     */
    public static void init() {
        log.info("========================================");
        log.info("开始初始化Sentinel规则...");
        log.info("========================================");

        // 加载流量控制规则
        loadFlowRules();
        // 加载熔断降级规则
        loadDegradeRules();

        log.info("Sentinel规则初始化完成");
        log.info("========================================");
    }

    /**
     * 加载流量控制规则（FlowRule）
     *
     * <p>流量控制（Flow Control）监控应用流量的QPS或并发线程数，
     * 当达到指定阈值时对流量进行控制，避免被瞬时的流量高峰冲垮。</p>
     *
     * <p>规则说明：</p>
     * <ul>
     *   <li>feed-get: QPS=100 — Feed流是核心接口，允许较高QPS</li>
     *   <li>video-detail: QPS=200 — 视频详情页读取量大，QPS较高</li>
     *   <li>comment-create: QPS=20 — 评论写入接口，限制频率防刷</li>
     *   <li>post-create: QPS=10 — 动态发布接口，严格限制</li>
     *   <li>user-register: QPS=5 — 注册接口，严格限制防恶意注册</li>
     *   <li>coupon-grab: QPS=500 — 秒杀接口，高QPS用Lua脚本抗住</li>
     * </ul>
     */
    private static void loadFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        // Feed流接口限流：每秒最多100个请求
        rules.add(createFlowRule("feed-get", 100));
        // 视频详情接口限流：每秒最多200个请求
        rules.add(createFlowRule("video-detail", 200));
        // 评论创建接口限流：每秒最多20个请求
        rules.add(createFlowRule("comment-create", 20));
        // 动态发布接口限流：每秒最多10个请求
        rules.add(createFlowRule("post-create", 10));
        // 用户注册接口限流：每秒最多5个请求
        rules.add(createFlowRule("user-register", 5));
        // 优惠券抢购接口限流：每秒最多500个请求（用Lua脚本抗并发，QPS可以高一些）
        rules.add(createFlowRule("coupon-grab", 500));

        // 加载到Sentinel内存中
        FlowRuleManager.loadRules(rules);
        log.info("流量控制规则已加载: 共{}条", rules.size());
    }

    /**
     * 创建一条流量控制规则
     *
     * @param resource 资源名称
     * @param qps      QPS阈值
     * @return FlowRule对象
     */
    private static FlowRule createFlowRule(String resource, int qps) {
        FlowRule rule = new FlowRule();
        // 设置资源名称（与SphU.entry("资源名")中的名称一致）
        rule.setResource(resource);
        // 限流类型：QPS（每秒查询数）模式
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        // QPS阈值：超过此值则触发限流
        rule.setCount(qps);
        return rule;
    }

    /**
     * 加载熔断降级规则（DegradeRule）
     *
     * <p>熔断降级（Circuit Breaking）监控资源的异常情况，
     * 当异常比例/慢调用比例达到阈值时，自动熔断该资源一段时间。</p>
     *
     * <p>熔断状态机：</p>
     * <ol>
     *   <li>CLOSED（关闭）→ 正常状态，统计请求</li>
     *   <li>OPEN（打开）→ 触发熔断，拒绝所有请求（直接抛BlockException）</li>
     *   <li>HALF-OPEN（半开）→ 熔断时间窗口过后，允许少量请求通过探测</li>
     *   <li>探测成功 → CLOSED（恢复正常）；探测失败 → OPEN（继续熔断）</li>
     * </ol>
     *
     * <p>规则说明：</p>
     * <ul>
     *   <li>feed-get: 异常比例50% → 熔断10秒</li>
     *   <li>coupon-grab: 异常比例50% → 熔断10秒</li>
     * </ul>
     */
    private static void loadDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();

        // Feed流接口熔断规则
        DegradeRule feedRule = new DegradeRule("feed-get");
        // 熔断策略：异常比例（当异常请求占总请求的比例超过阈值时熔断）
        feedRule.setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType());
        // 异常比例阈值：50%（一半请求异常就熔断）
        feedRule.setCount(0.5);
        // 熔断持续时间：10秒
        feedRule.setTimeWindow(10);
        // 最小请求数：至少5个请求才进行统计（避免样本太少误判）
        feedRule.setMinRequestAmount(5);
        // 统计窗口：1秒内的请求
        feedRule.setStatIntervalMs(1000);
        rules.add(feedRule);

        // 优惠券抢购接口熔断规则
        DegradeRule couponRule = new DegradeRule("coupon-grab");
        couponRule.setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType());
        couponRule.setCount(0.5);
        couponRule.setTimeWindow(10);
        couponRule.setMinRequestAmount(5);
        couponRule.setStatIntervalMs(1000);
        rules.add(couponRule);

        DegradeRuleManager.loadRules(rules);
        log.info("熔断降级规则已加载: 共{}条", rules.size());
    }
}
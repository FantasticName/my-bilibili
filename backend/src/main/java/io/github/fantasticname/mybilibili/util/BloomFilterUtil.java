package io.github.fantasticname.mybilibili.util;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 双布隆过滤器工具类（防止缓存穿透）
 *
 * <p>缓存穿透是指：查询一个数据库中不存在的数据，请求绕过缓存直接打到DB。
 * 如果恶意攻击者构造大量不存在的数据ID发起请求，DB会瞬间被打垮。</p>
 *
 * <p>BloomFilter（布隆过滤器）的原理：</p>
 * <ul>
 *   <li>使用一个bit数组 + 多个hash函数</li>
 *   <li>插入时：对元素做K次hash，将对应bit位置1</li>
 *   <li>查询时：对元素做K次hash，检查所有bit位是否都为1</li>
 *   <li>如果所有位都是1 → "可能存在"（允许去查缓存/DB）</li>
 *   <li>如果存在一位是0 → "一定不存在"（直接返回null，不查DB）</li>
 * </ul>
 *
 * <p>BloomFilter的假阳性（False Positive）问题：
 * 即使所有bit位都是1，也不一定真的存过这个数据（可能是hash碰撞），
 * 但至少能过滤掉90%以上的无效请求。</p>
 *
 * <p>双布隆 + 原子切换的设计原理（解决重建时的"数据黑洞"）：</p>
 * <ul>
 *   <li><b>问题</b>：单布隆重建时，先清旧数据才写新数据，重建过程中所有请求都在查空布隆，全穿透到DB</li>
 *   <li><b>方案</b>：维护A/B两个布隆过滤器，用AtomicBoolean标记谁是"主"</li>
 *   <li>重建时：往另一个（从）布隆里写数据，写完后原子切换主从</li>
 *   <li>读请求：始终查主布隆，重建过程不从"主"移到"从"，不影响查询</li>
 *   <li>切换后：新请求查新主布隆，旧主布隆被清空成为下一次重建的"从"</li>
 * </ul>
 *
 * <p>场景举例：</p>
 * <ol>
 *   <li>初始：A=主，B=从（空）</li>
 *   <li>重建开始：往B里写全量ID</li>
 *   <li>重建完成：原子切换 A=从，B=主</li>
 *   <li>下一次重建：往A里写全量ID，完成后切换 A=主，B=从</li>
 * </ol>
 *
 * <p>重建时机：</p>
 * <ul>
 *   <li>定时任务（每30分钟从DB拉全量ID重建一次）</li>
 *   <li>新增数据时调用add方法（往两个布隆都加，保证新数据立即可用）</li>
 * </ul>
 *
 * @author FantasticName
 */
public class BloomFilterUtil {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(BloomFilterUtil.class);

    /**
     * 预期插入元素数量（100万条）
     *
     * <p>这个值决定了布隆过滤器的bit数组大小。
     * 预期值越大，bit数组越大，hash碰撞概率越低（假阳性率越低）。</p>
     */
    private static final int EXPECTED_INSERTIONS = 1_000_000;

    /**
     * 期望的假阳性率（1%）
     *
     * <p>假阳性率越低，需要的hash函数越多（bit数组越大），
     * 1%的假阳性率意味着：100个"不存在"的数据中，只有1个会"可能穿过去"。</p>
     */
    private static final double FPP = 0.01;

    /**
     * 布隆过滤器A（主或从，取决于mainIsA的值）
     *
     * <p>Guava的BloomFilter是内存数据结构，重启后数据丢失，
     * 需要从数据库全量重建。create()方法的三个参数：
     * <ul>
     *   <li>Funnels.integerFunnel()：将Integer转为byte写入bit数组</li>
     *   <li>expectedInsertions：预期元素数，Guava据此计算bit数组大小</li>
     *   <li>fpp：假阳性率，Guava据此计算hash函数数量</li>
     * </ul>
     *
     * <p>注意：这里用的是Integer类型的BloomFilter，实际使用中需要id支持负数吗？
     * 如果id可能超过int范围（如雪花ID），应改为Long类型的BloomFilter。</p>
     */
    private static volatile BloomFilter<Integer> bloomFilterA = BloomFilter.create(
            Funnels.integerFunnel(), EXPECTED_INSERTIONS, FPP
    );

    /**
     * 布隆过滤器B（主或从）
     */
    private static volatile BloomFilter<Integer> bloomFilterB = BloomFilter.create(
            Funnels.integerFunnel(), EXPECTED_INSERTIONS, FPP
    );

    /**
     * 当前主布隆过滤器是A还是B
     *
     * <p>AtomicBoolean保证线程安全：
     * - get()：原子读取当前值
     * - compareAndSet()：原子比较并设置（CAS操作，硬件级别保证）
     * - 当mainIsA=true时，bloomFilterA是主，读请求查A
     * </p>
     */
    private static final AtomicBoolean mainIsA = new AtomicBoolean(true);

    /**
     * 当前正在重建的布隆过滤器（null表示没有重建在进行）
     *
     * <p>用于add操作：新增数据同时加到主和从（如果有正在进行中的重建的话）。
     * volatile保证多线程可见性。</p>
     */
    private static volatile BloomFilter<Integer> rebuildingFilter = null;

    /**
     * 获取主布隆过滤器
     *
     * <p>读请求始终调此方法，从"主"布隆过滤器中查询。
     * 这个方法非常高频（每次查缓存前都调用），所以用局部变量优化性能。</p>
     *
     * @return 当前主布隆过滤器
     */
    public static BloomFilter<Integer> getMain() {
        return mainIsA.get() ? bloomFilterA : bloomFilterB;
    }

    /**
     * 开始重建布隆过滤器
     *
     * <p>此方法由定时任务调用（每30分钟一次），流程：</p>
     * <ol>
     *   <li>确定当前哪个是从布隆（准备往从里写全量ID）</li>
     *   <li>清空从布隆</li>
     *   <li>设置为"正在重建中"（供add方法感知）</li>
     * </ol>
     *
     * @return 从布隆过滤器（让调用方往里add全量ID）
     */
    public static BloomFilter<Integer> beginRebuild() {
        // 确定当前哪个是从布隆
        BloomFilter<Integer> fromFilter = mainIsA.get() ? bloomFilterB : bloomFilterA;
        // 清空从布隆（重新开始）
        fromFilter = BloomFilter.create(Funnels.integerFunnel(), EXPECTED_INSERTIONS, FPP);

        // 更新静态引用
        if (mainIsA.get()) {
            bloomFilterB = fromFilter;
        } else {
            bloomFilterA = fromFilter;
        }

        // 标记"正在重建中"
        rebuildingFilter = fromFilter;
        log.info("布隆过滤器重建开始: 从={}, 旧主大小={}", mainIsA.get() ? "B" : "A", getMain().approximateElementCount());
        return fromFilter;
    }

    /**
     * 结束重建——原子切换主从布隆过滤器
     *
     * <p>此方法在重建完成后调用（所有ID写入完成后）。
     * 使用CAS（Compare-And-Set）原子切换：</p>
     * <ul>
     *   <li>如果当前mainIsA=true，则交换为false → 主变成B</li>
     *   <li>如果当前mainIsA=false，则交换为true → 主变成A</li>
     * </ul>
     */
    public static void endRebuild() {
        boolean oldMain = mainIsA.get();
        // CAS原子交换：预期值(oldMain)相等时才修改
        if (mainIsA.compareAndSet(oldMain, !oldMain)) {
            log.info("布隆过滤器重建完成: 旧主={}, 新主={}, 新主大小={}",
                    oldMain ? "A" : "B",
                    !oldMain ? "A" : "B",
                    getMain().approximateElementCount());
        }
        rebuildingFilter = null;
    }

    /**
     * 新增数据——往两个布隆都加
     *
     * <p>当数据库新增一条记录时调用此方法，保证新数据立即可被布隆识别：
     * 往主布隆和正在重建的从布隆里都加，确保无论何时切换都不会丢失数据。</p>
     *
     * <p>为什么要两个都加？</p>
     * <ul>
     *   <li>场景：主=A，正在重建B</li>
     *   <li>如果只加A：切换后主变成B，新数据不在B中 → 可能穿透过A但查不到（等等，切换后在B里就查不到了）</li>
     *   <li>实际上：加A保证切换前可用，加重建中的B保证切换后可用</li>
     * </ul>
     *
     * @param id 新增记录的ID
     */
    public static void add(Integer id) {
        // 加到主布隆中
        getMain().put(id);

        // 如果有正在进行的重建，也加到从布隆中
        if (rebuildingFilter != null) {
            rebuildingFilter.put(id);
        }
        log.debug("BloomFilter添加ID: id={}", id);
    }

    /**
     * 查询ID是否"可能存在"
     *
     * <p>高频调用方法。只查主布隆，如果返回false（一定不存在），
     * 调用方可以直接跳过查DB，返回null，防止缓存穿透。</p>
     *
     * <p>注意：返回true表示"可能存在"，不是"一定存在"！
     * 可能有约1%的假阳性（hash碰撞导致），此时会多一次多余的DB查和缓存空值。</p>
     *
     * @param id 要查询的ID
     * @return false=一定不存在，true=可能存在
     */
    public static boolean mightContain(Integer id) {
        BloomFilter<Integer> main = getMain();
        boolean result = main.mightContain(id);
        if (!result) {
            log.debug("BloomFilter拦截: id={} 一定不存在，跳过查DB", id);
        }
        return result;
    }

    /**
     * 获取当前主布隆过滤器中的元素数（近似值）
     *
     * <p>布隆过滤器的approximateElementCount()是近似值，不是精确值。
     * 因为布隆过滤器只存bit位，不存原始数据，所以元素数是估算的。</p>
     *
     * @return 近似元素数
     */
    public static long getMainSize() {
        return getMain().approximateElementCount();
    }
}
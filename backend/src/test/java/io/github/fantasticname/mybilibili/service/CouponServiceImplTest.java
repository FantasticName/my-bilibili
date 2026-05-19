package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.dao.CouponActivityDao;
import io.github.fantasticname.mybilibili.dao.CouponRecordDao;
import io.github.fantasticname.mybilibili.entity.CouponActivity;
import io.github.fantasticname.mybilibili.util.RedisUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CouponServiceImpl单元测试
 *
 * <p>覆盖优惠券核心逻辑：库存对账、活动详情查询等。</p>
 *
 * @author FantasticName
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceImplTest {

    @Mock
    private CouponActivityDao couponActivityDao;

    @Mock
    private CouponRecordDao couponRecordDao;

    @InjectMocks
    private CouponServiceImpl couponService;

    private MockedStatic<RedisUtil> redisUtilMock;

    @BeforeEach
    void setUp() {
        redisUtilMock = mockStatic(RedisUtil.class);
    }

    @AfterEach
    void tearDown() {
        redisUtilMock.close();
    }

    /**
     * 创建测试用活动对象
     */
    private CouponActivity createTestActivity(Long id, int totalStock, int remainStock) {
        CouponActivity activity = new CouponActivity();
        activity.setId(id);
        activity.setName("测试活动");
        activity.setTotalStock(totalStock);
        activity.setRemainStock(remainStock);
        activity.setPerUserLimit(1);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        activity.setStatus(1);
        return activity;
    }

    @Nested
    @DisplayName("reconcileStock 库存对账测试")
    class ReconcileStockTests {

        @Test
        @DisplayName("库存一致时不修正")
        void reconcileStock_consistent_noCorrection() {
            CouponActivity activity = createTestActivity(1L, 100, 80);
            when(couponActivityDao.findById(1L)).thenReturn(activity);
            // Redis库存也是80
            redisUtilMock.when(() -> RedisUtil.get("coupon:stock:1")).thenReturn("80");

            couponService.reconcileStock(1L);

            // 库存一致，不应该调用set修正
            redisUtilMock.verify(() -> RedisUtil.set(eq("coupon:stock:1"), anyString(), anyInt()), never());
        }

        @Test
        @DisplayName("库存不一致时以DB为准修正Redis")
        void reconcileStock_inconsistent_correctRedis() {
            CouponActivity activity = createTestActivity(1L, 100, 75);
            when(couponActivityDao.findById(1L)).thenReturn(activity);
            // Redis库存是80，DB库存是75，不一致
            redisUtilMock.when(() -> RedisUtil.get("coupon:stock:1")).thenReturn("80");

            couponService.reconcileStock(1L);

            // 应该以DB为准修正Redis
            redisUtilMock.verify(() -> RedisUtil.set("coupon:stock:1", "75", 0));
        }

        @Test
        @DisplayName("活动不存在时直接返回")
        void reconcileStock_activityNotFound_return() {
            when(couponActivityDao.findById(999L)).thenReturn(null);

            couponService.reconcileStock(999L);

            // 不应该调用Redis
            redisUtilMock.verify(() -> RedisUtil.get(anyString()), never());
        }

        @Test
        @DisplayName("Redis中无库存数据时修正")
        void reconcileStock_redisNoData_correctRedis() {
            CouponActivity activity = createTestActivity(1L, 100, 90);
            when(couponActivityDao.findById(1L)).thenReturn(activity);
            // Redis中无数据
            redisUtilMock.when(() -> RedisUtil.get("coupon:stock:1")).thenReturn(null);

            couponService.reconcileStock(1L);

            // Redis返回null时解析为-1，与DB不一致，应该修正
            redisUtilMock.verify(() -> RedisUtil.set("coupon:stock:1", "90", 0));
        }
    }

    @Nested
    @DisplayName("getActivityDetail 活动详情测试")
    class GetActivityDetailTests {

        @Test
        @DisplayName("从缓存获取活动详情")
        void getActivityDetail_fromCache() {
            CouponActivity cached = createTestActivity(1L, 100, 80);
            redisUtilMock.when(() -> RedisUtil.getObject(eq("coupon:activity:1"), eq(CouponActivity.class)))
                    .thenReturn(cached);

            CouponActivity result = couponService.getActivityDetail(1L);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            // 缓存命中，不应该查DB
            verify(couponActivityDao, never()).findById(anyLong());
        }

        @Test
        @DisplayName("缓存未命中时查DB并写缓存")
        void getActivityDetail_cacheMiss_queryDB() {
            // 缓存未命中
            redisUtilMock.when(() -> RedisUtil.getObject(eq("coupon:activity:1"), eq(CouponActivity.class)))
                    .thenReturn(null);
            CouponActivity dbActivity = createTestActivity(1L, 100, 80);
            when(couponActivityDao.findById(1L)).thenReturn(dbActivity);

            CouponActivity result = couponService.getActivityDetail(1L);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            verify(couponActivityDao).findById(1L);
        }
    }
}

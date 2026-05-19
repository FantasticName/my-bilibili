package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.dao.PostDao;
import io.github.fantasticname.mybilibili.dao.UserBehaviorDao;
import io.github.fantasticname.mybilibili.dao.UserDao;
import io.github.fantasticname.mybilibili.entity.Post;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.entity.UserBehavior;
import io.github.fantasticname.mybilibili.util.RedisUtil;
import io.github.fantasticname.mybilibili.vo.PostVO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RecommendServiceImpl单元测试
 *
 * <p>覆盖推荐系统核心逻辑：Redis推荐池读取、实时计算、离线计算。</p>
 *
 * @author FantasticName
 */
@ExtendWith(MockitoExtension.class)
class RecommendServiceImplTest {

    @Mock
    private UserBehaviorDao userBehaviorDao;

    @Mock
    private PostDao postDao;

    @Mock
    private UserDao userDao;

    @Mock
    private LikeService likeService;

    @InjectMocks
    private RecommendServiceImpl recommendService;

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
     * 创建测试用Post对象
     */
    private Post createTestPost(Long id, Long userId, String content, int likeCount) {
        Post post = new Post();
        post.setId(id);
        post.setUserId(userId);
        post.setContent(content);
        post.setLikeCount(likeCount);
        post.setCommentCount(0);
        post.setStatus(0);
        post.setCreatedAt(LocalDateTime.now());
        return post;
    }

    /**
     * 创建测试用User对象
     */
    private User createTestUser(Long id, String nickname) {
        User user = new User();
        user.setId(id);
        user.setNickname(nickname);
        user.setAvatar("avatar.jpg");
        return user;
    }

    /**
     * 创建测试用UserBehavior对象
     */
    private UserBehavior createTestBehavior(Long userId, String type, Long targetId, int targetType, int weight) {
        UserBehavior behavior = new UserBehavior();
        behavior.setUserId(userId);
        behavior.setBehaviorType(type);
        behavior.setTargetId(targetId);
        behavior.setTargetType(targetType);
        behavior.setWeight(weight);
        behavior.setCreatedAt(LocalDateTime.now());
        return behavior;
    }

    @Nested
    @DisplayName("getRecommendedFeed 推荐Feed测试")
    class GetRecommendedFeedTests {

        @Test
        @DisplayName("Redis推荐池命中时直接返回缓存数据")
        void getRecommendedFeed_redisPoolHit() {
            // Redis推荐池返回缓存的动态ID
            redisUtilMock.when(() -> RedisUtil.lrange("recommend:pool:1", 0, 9))
                    .thenReturn(Arrays.asList("100", "101"));
            // 根据ID查Post
            Post p1 = createTestPost(100L, 5L, "推荐动态1", 10);
            Post p2 = createTestPost(101L, 6L, "推荐动态2", 20);
            when(postDao.findByIds(Arrays.asList(100L, 101L))).thenReturn(Arrays.asList(p1, p2));
            when(userDao.findById(5L)).thenReturn(createTestUser(5L, "user5"));
            when(userDao.findById(6L)).thenReturn(createTestUser(6L, "user6"));
            when(likeService.isLiked(eq(1L), anyInt(), anyLong())).thenReturn(false);

            List<PostVO> result = recommendService.getRecommendedFeed(1L, 10);

            assertNotNull(result);
            assertEquals(2, result.size());
            // 验证没有查行为数据（走了缓存路径）
            verify(userBehaviorDao, never()).listByUser(anyLong(), anyInt());
        }

        @Test
        @DisplayName("Redis推荐池为空时走实时计算")
        void getRecommendedFeed_redisPoolEmpty_fallbackToCompute() {
            // Redis推荐池为空
            redisUtilMock.when(() -> RedisUtil.lrange("recommend:pool:1", 0, 9))
                    .thenReturn(Collections.emptyList());
            // 查行为数据
            when(userBehaviorDao.listByUser(1L, 100)).thenReturn(Collections.emptyList());
            // 查热榜作为候选
            Post hotPost = createTestPost(200L, 7L, "热榜动态", 50);
            when(postDao.listHotPosts(30)).thenReturn(Arrays.asList(hotPost));
            when(userDao.findById(7L)).thenReturn(createTestUser(7L, "user7"));
            when(likeService.isLiked(eq(1L), anyInt(), anyLong())).thenReturn(false);

            List<PostVO> result = recommendService.getRecommendedFeed(1L, 10);

            assertNotNull(result);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Redis异常时降级为实时计算")
        void getRecommendedFeed_redisError_fallbackToCompute() {
            // Redis抛异常
            redisUtilMock.when(() -> RedisUtil.lrange("recommend:pool:1", 0, 9))
                    .thenThrow(new RuntimeException("Redis连接失败"));
            // 查行为数据
            when(userBehaviorDao.listByUser(1L, 100)).thenReturn(Collections.emptyList());
            // 查热榜作为候选
            Post hotPost = createTestPost(200L, 7L, "热榜动态", 50);
            when(postDao.listHotPosts(30)).thenReturn(Arrays.asList(hotPost));
            when(userDao.findById(7L)).thenReturn(createTestUser(7L, "user7"));
            when(likeService.isLiked(eq(1L), anyInt(), anyLong())).thenReturn(false);

            List<PostVO> result = recommendService.getRecommendedFeed(1L, 10);

            assertNotNull(result);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("冷启动用户（无行为数据）返回热榜内容")
        void getRecommendedFeed_coldStart_returnsHotPosts() {
            redisUtilMock.when(() -> RedisUtil.lrange("recommend:pool:1", 0, 9))
                    .thenReturn(Collections.emptyList());
            // 无行为数据
            when(userBehaviorDao.listByUser(1L, 100)).thenReturn(Collections.emptyList());
            Post hotPost = createTestPost(300L, 8L, "热门推荐", 100);
            when(postDao.listHotPosts(30)).thenReturn(Arrays.asList(hotPost));
            when(userDao.findById(8L)).thenReturn(createTestUser(8L, "user8"));
            when(likeService.isLiked(eq(1L), anyInt(), anyLong())).thenReturn(false);

            List<PostVO> result = recommendService.getRecommendedFeed(1L, 10);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(300L, result.get(0).getId());
        }
    }

    @Nested
    @DisplayName("computeAndCacheRecommendPool 离线计算测试")
    class ComputeAndCacheRecommendPoolTests {

        @Test
        @DisplayName("离线计算推荐池并写入Redis")
        void computeAndCacheRecommendPool_success() {
            // 模拟getRecommendedFeed内部逻辑
            redisUtilMock.when(() -> RedisUtil.lrange("recommend:pool:1", 0, 99))
                    .thenReturn(Collections.emptyList());
            when(userBehaviorDao.listByUser(1L, 100)).thenReturn(Collections.emptyList());
            Post hotPost = createTestPost(100L, 5L, "推荐内容", 10);
            when(postDao.listHotPosts(300)).thenReturn(Arrays.asList(hotPost));
            when(userDao.findById(5L)).thenReturn(createTestUser(5L, "user5"));
            when(likeService.isLiked(eq(1L), anyInt(), anyLong())).thenReturn(false);
            // 模拟Redis写入操作
            redisUtilMock.when(() -> RedisUtil.del(anyString())).thenReturn(1L);
            redisUtilMock.when(() -> RedisUtil.lpush(anyString(), any(String[].class))).thenReturn(1L);
            redisUtilMock.when(() -> RedisUtil.expireKey(anyString(), anyLong())).thenReturn(1L);

            recommendService.computeAndCacheRecommendPool(1L);

            // 验证写入了Redis
            redisUtilMock.verify(() -> RedisUtil.del("recommend:pool:1"));
            redisUtilMock.verify(() -> RedisUtil.lpush(eq("recommend:pool:1"), any(String[].class)));
            redisUtilMock.verify(() -> RedisUtil.expireKey("recommend:pool:1", 86400));
        }
    }
}

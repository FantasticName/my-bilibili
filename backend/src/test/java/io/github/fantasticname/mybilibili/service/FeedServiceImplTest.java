package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.dao.PostDao;
import io.github.fantasticname.mybilibili.dao.FollowDao;
import io.github.fantasticname.mybilibili.dao.UserDao;
import io.github.fantasticname.mybilibili.entity.Follow;
import io.github.fantasticname.mybilibili.entity.Post;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.util.RedisUtil;
import io.github.fantasticname.mybilibili.vo.PostVO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FeedServiceImpl单元测试
 *
 * <p>覆盖Feed流核心逻辑：收件箱查询、Pull降级、热榜兜底、两路召回。</p>
 *
 * @author FantasticName
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedServiceImplTest {

    @Mock
    private PostDao postDao;

    @Mock
    private FollowDao followDao;

    @Mock
    private UserDao userDao;

    @Mock
    private LikeService likeService;

    @Mock
    private RecommendServiceImpl recommendService;

    @InjectMocks
    private FeedServiceImpl feedService;

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
    private Post createTestPost(Long id, Long userId, String content) {
        Post post = new Post();
        post.setId(id);
        post.setUserId(userId);
        post.setContent(content);
        post.setLikeCount(0);
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
     * 创建测试用Follow对象
     */
    private Follow createTestFollow(Long followerId, Long followeeId) {
        Follow follow = new Follow();
        follow.setFollowerId(followerId);
        follow.setFolloweeId(followeeId);
        return follow;
    }

    @Nested
    @DisplayName("getFeed 测试")
    class GetFeedTests {

        @Test
        @DisplayName("Redis收件箱有数据时正常返回Feed流")
        void getFeed_inboxHasData_success() {
            // 模拟Redis收件箱返回动态ID
            redisUtilMock.when(() -> RedisUtil.zrevrangeByScore(anyString(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(Arrays.asList("1", "2"));
            // 模拟Post查询
            Post p1 = createTestPost(1L, 2L, "动态1");
            Post p2 = createTestPost(2L, 3L, "动态2");
            when(postDao.findByIds(Arrays.asList(1L, 2L))).thenReturn(Arrays.asList(p1, p2));
            when(userDao.findById(2L)).thenReturn(createTestUser(2L, "user2"));
            when(userDao.findById(3L)).thenReturn(createTestUser(3L, "user3"));
            when(likeService.isLiked(anyLong(), anyInt(), anyLong())).thenReturn(false);
            // 推荐服务返回空列表
            when(recommendService.getRecommendedFeed(anyLong(), anyInt())).thenReturn(Collections.emptyList());

            List<PostVO> result = feedService.getFeed(1L, null, 10);

            assertNotNull(result);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("Redis收件箱为空时走Pull降级")
        void getFeed_inboxEmpty_pullFallback() {
            // 模拟Redis收件箱为空
            redisUtilMock.when(() -> RedisUtil.zrevrangeByScore(anyString(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(Collections.emptyList());
            // 模拟Pull降级
            when(followDao.listFollowing(eq(1L), eq(0), eq(10000)))
                    .thenReturn(Arrays.asList(createTestFollow(1L, 2L)));
            Post p1 = createTestPost(1L, 2L, "动态1");
            when(postDao.listFeedByCursor(anyList(), isNull(), eq(10)))
                    .thenReturn(Arrays.asList(p1));
            when(userDao.findById(2L)).thenReturn(createTestUser(2L, "user2"));
            when(likeService.isLiked(anyLong(), anyInt(), anyLong())).thenReturn(false);

            List<PostVO> result = feedService.getFeed(1L, null, 10);

            assertNotNull(result);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("没有关注任何人且收件箱为空时返回热榜")
        void getFeed_noFollowing_returnsHotPosts() {
            // 模拟Redis收件箱为空
            redisUtilMock.when(() -> RedisUtil.zrevrangeByScore(anyString(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(Collections.emptyList());
            // 没有关注
            when(followDao.listFollowing(eq(1L), eq(0), eq(10000)))
                    .thenReturn(Collections.emptyList());
            // 返回热榜
            Post hotPost = createTestPost(10L, 5L, "热榜动态");
            when(postDao.listHotPosts(10)).thenReturn(Arrays.asList(hotPost));
            when(userDao.findById(5L)).thenReturn(createTestUser(5L, "hotUser"));
            when(likeService.isLiked(anyLong(), anyInt(), anyLong())).thenReturn(false);

            List<PostVO> result = feedService.getFeed(1L, null, 10);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(10L, result.get(0).getId());
        }

        @Test
        @DisplayName("Redis异常时返回hotFallback兜底数据")
        void getFeed_redisError_returnsHotFallback() {
            // 先设置hotFallback数据
            Post hotPost = createTestPost(99L, 5L, "兜底动态");
            when(postDao.listHotPosts(20)).thenReturn(Arrays.asList(hotPost));
            when(userDao.findById(5L)).thenReturn(createTestUser(5L, "hotUser"));
            when(likeService.isLiked(isNull(), anyInt(), anyLong())).thenReturn(false);
            feedService.refreshHotFallback();

            // 模拟Redis异常
            redisUtilMock.when(() -> RedisUtil.zrevrangeByScore(anyString(), anyDouble(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("Redis连接失败"));

            List<PostVO> result = feedService.getFeed(1L, null, 10);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(99L, result.get(0).getId());
        }
    }

    @Nested
    @DisplayName("refreshHotFallback 测试")
    class RefreshHotFallbackTests {

        @Test
        @DisplayName("刷新热榜兜底数据成功")
        void refreshHotFallback_success() {
            Post hotPost = createTestPost(10L, 5L, "热榜动态");
            when(postDao.listHotPosts(20)).thenReturn(Arrays.asList(hotPost));
            when(userDao.findById(5L)).thenReturn(createTestUser(5L, "hotUser"));
            when(likeService.isLiked(isNull(), anyInt(), anyLong())).thenReturn(false);

            feedService.refreshHotFallback();

            List<PostVO> fallback = feedService.getHotFallback();
            assertNotNull(fallback);
            assertEquals(1, fallback.size());
            assertEquals(10L, fallback.get(0).getId());
        }

        @Test
        @DisplayName("刷新热榜兜底数据时DB异常不影响已有数据")
        void refreshHotFallback_dbError_keepsOldData() {
            // 先设置一些兜底数据
            Post hotPost = createTestPost(10L, 5L, "热榜动态");
            when(postDao.listHotPosts(20)).thenReturn(Arrays.asList(hotPost));
            when(userDao.findById(5L)).thenReturn(createTestUser(5L, "hotUser"));
            when(likeService.isLiked(isNull(), anyInt(), anyLong())).thenReturn(false);
            feedService.refreshHotFallback();

            // 模拟DB异常
            when(postDao.listHotPosts(20)).thenThrow(new RuntimeException("DB连接失败"));

            // 刷新失败不应清空已有数据
            feedService.refreshHotFallback();
            List<PostVO> fallback = feedService.getHotFallback();
            assertNotNull(fallback);
            assertEquals(1, fallback.size());
        }
    }

    @Nested
    @DisplayName("getHotFallback 测试")
    class GetHotFallbackTests {

        @Test
        @DisplayName("初始状态返回空列表")
        void getHotFallback_initial_returnsEmpty() {
            List<PostVO> fallback = feedService.getHotFallback();
            assertNotNull(fallback);
            assertTrue(fallback.isEmpty());
        }
    }
}

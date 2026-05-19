package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.github.fantasticname.mybilibili.dao.UserDao;
import io.github.fantasticname.mybilibili.dao.VideoDao;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.entity.Video;
import io.github.fantasticname.mybilibili.util.BloomFilterUtil;
import io.github.fantasticname.mybilibili.util.RedisUtil;
import io.github.fantasticname.mybilibili.vo.VideoVO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoServiceImplTest {

    @Mock
    private VideoDao videoDao;

    @Mock
    private UserDao userDao;

    @Mock
    private LikeService likeService;

    @Mock
    private FavoriteService favoriteService;

    @InjectMocks
    private VideoServiceImpl videoService;

    @BeforeEach
    void setUp() {
        // 往布隆过滤器中添加测试用ID，防止getDetail被拦截
        BloomFilterUtil.add(1);
        BloomFilterUtil.add(999);
        // 清理Redis缓存，防止测试之间缓存状态污染
        try {
            RedisUtil.del("video:detail:1");
            RedisUtil.del("video:detail:999");
        } catch (Exception e) {
            // Redis未连接时忽略
        }
    }

    private Video createTestVideo(Long id, Long userId, String title, String category) {
        Video video = new Video();
        video.setId(id);
        video.setUserId(userId);
        video.setTitle(title);
        video.setCategory(category);
        video.setDescription("测试视频描述");
        video.setVideoUrl("http://example.com/video.mp4");
        video.setCoverUrl("http://example.com/cover.jpg");
        video.setViewCount(100L);
        video.setLikeCount(10L);
        video.setStatus(0);
        video.setCreatedAt(LocalDateTime.now());
        return video;
    }

    private User createTestUser(Long id, String nickname) {
        User user = new User();
        user.setId(id);
        user.setNickname(nickname);
        user.setAvatar("avatar.jpg");
        return user;
    }

    @Nested
    @DisplayName("publish 测试")
    class PublishTests {

        @Test
        @DisplayName("正常发布视频")
        void publish_success() {
            when(videoDao.insert(any(Video.class))).thenReturn(1L);
            Video inserted = createTestVideo(1L, 1L, "测试视频", "搞笑");
            when(videoDao.findById(1L)).thenReturn(inserted);
            when(userDao.findById(1L)).thenReturn(createTestUser(1L, "testUser"));

            VideoVO result = videoService.publish(1L, "测试视频", "测试描述",
                    "http://example.com/cover.jpg", "http://example.com/video.mp4", "搞笑");

            assertNotNull(result);
            assertEquals("测试视频", result.getTitle());
            verify(videoDao).insert(any(Video.class));
        }

        @Test
        @DisplayName("发布失败 - 标题为空")
        void publish_emptyTitle_throwsException() {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> videoService.publish(1L, "", "描述",
                            "http://example.com/cover.jpg", "http://example.com/video.mp4", "搞笑"));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("发布失败 - 视频URL为空")
        void publish_emptyVideoUrl_throwsException() {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> videoService.publish(1L, "测试视频", "描述",
                            "http://example.com/cover.jpg", "", "搞笑"));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        }
    }

    @Nested
    @DisplayName("getDetail 测试")
    class GetDetailTests {

        @Test
        @DisplayName("正常获取视频详情")
        void getDetail_success() {
            Video video = createTestVideo(1L, 1L, "测试视频", "搞笑");
            User user = createTestUser(1L, "testUser");
            when(videoDao.findById(1L)).thenReturn(video);
            when(userDao.findById(1L)).thenReturn(user);
            when(likeService.isLiked(anyLong(), anyInt(), anyLong())).thenReturn(false);
            when(favoriteService.isFavorited(anyLong(), anyInt(), anyLong())).thenReturn(false);

            VideoVO result = videoService.getDetail(1L, 1L);

            assertNotNull(result);
            assertEquals("测试视频", result.getTitle());
            assertEquals("testUser", result.getNickname());
        }

        @Test
        @DisplayName("获取视频详情 - 视频不存在")
        void getDetail_videoNotExists_throwsException() {
            // 999已在setUp中添加到布隆过滤器，BloomFilter会放行
            // DAO返回null，Service应抛出NOT_FOUND_ERROR
            // 使用lenient：因为Redis可能缓存了空值标记直接返回，
            // 此时videoDao.findById不会被调用，严格模式会报UnnecessaryStubbing
            lenient().when(videoDao.findById(999L)).thenReturn(null);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> videoService.getDetail(999L, 1L));
            assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
        }
    }

    @Nested
    @DisplayName("list 测试")
    class ListTests {

        @Test
        @DisplayName("按分区获取视频列表")
        void listByCategory_success() {
            Video v1 = createTestVideo(1L, 1L, "视频1", "搞笑");
            Video v2 = createTestVideo(2L, 2L, "视频2", "搞笑");
            when(videoDao.listByCategory(eq("搞笑"), eq(0), eq(10))).thenReturn(Arrays.asList(v1, v2));
            when(userDao.findById(1L)).thenReturn(createTestUser(1L, "user1"));
            when(userDao.findById(2L)).thenReturn(createTestUser(2L, "user2"));

            List<VideoVO> result = videoService.list("搞笑", 1, 10);

            assertNotNull(result);
            assertEquals(2, result.size());
        }
    }
}

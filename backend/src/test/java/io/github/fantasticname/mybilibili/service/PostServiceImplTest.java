package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.github.fantasticname.mybilibili.dao.PostDao;
import io.github.fantasticname.mybilibili.dao.UserDao;
import io.github.fantasticname.mybilibili.entity.Post;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.util.RedisUtil;
import io.github.fantasticname.mybilibili.vo.PostVO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PostServiceImpl 单元测试
 *
 * <p>测试动态模块的核心功能：发布动态、动态详情、游标分页、删除动态等。</p>
 *
 * @author FantasticName
 */
@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

    @Mock
    private PostDao postDao;

    @Mock
    private UserDao userDao;

    @Mock
    private LikeService likeService;

    @InjectMocks
    private PostServiceImpl postService;

    /**
     * 创建测试用的动态对象
     */
    private Post createTestPost(Long id, Long userId, String content) {
        return createTestPost(id, userId, content, null);
    }

    /**
     * 创建测试用的动态对象（带图片）
     */
    private Post createTestPost(Long id, Long userId, String content, String images) {
        Post post = new Post();
        post.setId(id);
        post.setUserId(userId);
        post.setContent(content);
        post.setImages(images);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setStatus(0);
        post.setCreatedAt(LocalDateTime.now());
        return post;
    }

    /**
     * 创建测试用的用户对象
     */
    private User createTestUser(Long id, String nickname) {
        User user = new User();
        user.setId(id);
        user.setNickname(nickname);
        user.setAvatar("avatar.jpg");
        return user;
    }

    @Nested
    @DisplayName("create 测试")
    class CreateTests {

        @Test
        @DisplayName("正常发布纯文字动态")
        void create_textOnly_success() {
            when(postDao.insert(any(Post.class))).thenReturn(1L);
            Post inserted = createTestPost(1L, 1L, "今天天气真好！");
            when(postDao.findById(1L)).thenReturn(inserted);
            when(userDao.findById(1L)).thenReturn(createTestUser(1L, "testUser"));

            PostVO result = postService.create(1L, "今天天气真好！", null);

            assertNotNull(result);
            assertEquals("今天天气真好！", result.getContent());
            // 纯文字动态，images应为空列表
            assertNotNull(result.getImages());
            assertTrue(result.getImages().isEmpty());
            verify(postDao).insert(any(Post.class));
        }

        @Test
        @DisplayName("发布带图片的动态")
        void create_withImages_success() {
            String images = "img1.jpg,img2.jpg,img3.jpg";
            when(postDao.insert(any(Post.class))).thenReturn(1L);
            Post inserted = createTestPost(1L, 1L, "看图！", images);
            when(postDao.findById(1L)).thenReturn(inserted);
            when(userDao.findById(1L)).thenReturn(createTestUser(1L, "testUser"));

            PostVO result = postService.create(1L, "看图！", images);

            assertNotNull(result);
            assertEquals("看图！", result.getContent());
            // 图片应被解析为列表
            assertNotNull(result.getImages());
            assertEquals(3, result.getImages().size());
        }

        @Test
        @DisplayName("发布纯图片动态（无文字）")
        void create_imagesOnly_success() {
            String images = "photo1.png";
            when(postDao.insert(any(Post.class))).thenReturn(1L);
            Post inserted = createTestPost(1L, 1L, null, images);
            when(postDao.findById(1L)).thenReturn(inserted);
            when(userDao.findById(1L)).thenReturn(createTestUser(1L, "testUser"));

            PostVO result = postService.create(1L, null, images);

            assertNotNull(result);
            assertNull(result.getContent());
            assertEquals(1, result.getImages().size());
        }

        @Test
        @DisplayName("发布动态失败 - 内容和图片都为空")
        void create_emptyContentAndImages_throwsException() {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> postService.create(1L, "", null));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("发布动态失败 - 图片超过9张")
        void create_tooManyImages_throwsException() {
            // 10张图片，逗号分隔
            String images = "1.jpg,2.jpg,3.jpg,4.jpg,5.jpg,6.jpg,7.jpg,8.jpg,9.jpg,10.jpg";

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> postService.create(1L, "test", images));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        }
    }

    @Nested
    @DisplayName("getDetail 测试")
    class GetDetailTests {

        @BeforeEach
        void cleanCache() {
            // 清理Redis缓存，防止测试之间缓存状态污染
            // （如getDetail_deleted测试写入的空值标记影响后续测试）
            try {
                RedisUtil.del("post:1");
                RedisUtil.del("post:999");
            } catch (Exception e) {
                // Redis未连接时忽略
            }
        }

        @Test
        @DisplayName("获取动态详情成功")
        void getDetail_success() {
            Post post = createTestPost(1L, 1L, "详情内容", "a.jpg,b.jpg");
            when(postDao.findById(1L)).thenReturn(post);
            when(userDao.findById(1L)).thenReturn(createTestUser(1L, "testUser"));
            when(likeService.isLiked(1L, 3, 1L)).thenReturn(false);

            PostVO result = postService.getDetail(1L, 1L);

            assertNotNull(result);
            assertEquals("详情内容", result.getContent());
            assertEquals(2, result.getImages().size());
            assertFalse(result.getIsLiked());
        }

        @Test
        @DisplayName("获取动态详情 - 动态不存在")
        void getDetail_notFound_throwsException() {
            // 使用lenient：因为Redis可能缓存了空值标记直接返回，
            // 此时postDao.findById不会被调用，严格模式会报UnnecessaryStubbing
            lenient().when(postDao.findById(999L)).thenReturn(null);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> postService.getDetail(999L, 1L));
            assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("获取动态详情 - 已删除的动态")
        void getDetail_deleted_throwsException() {
            Post post = createTestPost(1L, 1L, "已删除");
            post.setStatus(1);
            when(postDao.findById(1L)).thenReturn(post);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> postService.getDetail(1L, 1L));
            assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("获取动态详情 - 未登录用户不返回点赞状态")
        void getDetail_noLogin_noIsLiked() {
            Post post = createTestPost(1L, 1L, "内容");
            when(postDao.findById(1L)).thenReturn(post);
            when(userDao.findById(1L)).thenReturn(createTestUser(1L, "testUser"));

            PostVO result = postService.getDetail(1L, null);

            assertNotNull(result);
            assertNull(result.getIsLiked());
            // 未登录时不应该调用likeService
            verify(likeService, never()).isLiked(anyLong(), anyInt(), anyLong());
        }
    }

    @Nested
    @DisplayName("listByUserCursor 测试")
    class ListByUserCursorTests {

        @Test
        @DisplayName("首次加载动态列表（无游标）")
        void listByUserCursor_firstPage_success() {
            Post p1 = createTestPost(1L, 1L, "动态1");
            Post p2 = createTestPost(2L, 1L, "动态2");
            when(postDao.listByUserIdCursor(eq(1L), isNull(), eq(10)))
                    .thenReturn(Arrays.asList(p1, p2));
            when(userDao.findById(1L)).thenReturn(createTestUser(1L, "testUser"));

            List<PostVO> result = postService.listByUserCursor(1L, null, 10);

            assertNotNull(result);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("带游标加载动态列表")
        void listByUserCursor_withCursor_success() {
            Post p3 = createTestPost(3L, 1L, "动态3");
            when(postDao.listByUserIdCursor(eq(1L), eq("2025-01-01T00:00:00"), eq(10)))
                    .thenReturn(Collections.singletonList(p3));
            when(userDao.findById(1L)).thenReturn(createTestUser(1L, "testUser"));

            List<PostVO> result = postService.listByUserCursor(1L, "2025-01-01T00:00:00", 10);

            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Nested
    @DisplayName("listByUser 测试")
    class ListByUserTests {

        @Test
        @DisplayName("获取用户动态列表")
        void listByUser_success() {
            Post p1 = createTestPost(1L, 1L, "动态1");
            Post p2 = createTestPost(2L, 1L, "动态2");
            when(postDao.listByUserId(eq(1L), eq(0), eq(10))).thenReturn(Arrays.asList(p1, p2));
            when(userDao.findById(1L)).thenReturn(createTestUser(1L, "testUser"));

            List<PostVO> result = postService.listByUser(1L, 1, 10);

            assertNotNull(result);
            assertEquals(2, result.size());
        }
    }

    @Nested
    @DisplayName("delete 测试")
    class DeleteTests {

        @Test
        @DisplayName("正常删除自己的动态")
        void delete_ownPost_success() {
            Post post = createTestPost(1L, 1L, "我的动态");
            when(postDao.findById(1L)).thenReturn(post);
            doNothing().when(postDao).softDelete(1L);

            postService.delete(1L, 1L);

            verify(postDao).softDelete(1L);
        }

        @Test
        @DisplayName("删除他人动态失败")
        void delete_otherPost_throwsException() {
            Post post = createTestPost(1L, 2L, "别人的动态");
            when(postDao.findById(1L)).thenReturn(post);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> postService.delete(1L, 1L));
            assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("删除不存在的动态失败")
        void delete_notFound_throwsException() {
            when(postDao.findById(999L)).thenReturn(null);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> postService.delete(999L, 1L));
            assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
        }
    }
}

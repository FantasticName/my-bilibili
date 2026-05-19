package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.github.fantasticname.mybilibili.dao.CommentDao;
import io.github.fantasticname.mybilibili.dao.FavoriteFolderDao;
import io.github.fantasticname.mybilibili.dao.FavoriteRecordDao;
import io.github.fantasticname.mybilibili.dao.LikeRecordDao;
import io.github.fantasticname.mybilibili.dao.VideoDao;
import io.github.fantasticname.mybilibili.entity.FavoriteFolder;
import io.github.fantasticname.mybilibili.entity.FavoriteRecord;
import io.github.fantasticname.mybilibili.entity.LikeRecord;
import io.github.fantasticname.mybilibili.entity.Video;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 点赞服务单元测试
 *
 * @author FantasticName
 */
@ExtendWith(MockitoExtension.class)
class LikeServiceImplTest {

    @Mock
    private LikeRecordDao likeRecordDao;

    @Mock
    private VideoDao videoDao;

    @Mock
    private CommentDao commentDao;

    @Mock
    private FavoriteFolderDao favoriteFolderDao;

    @Mock
    private FavoriteRecordDao favoriteRecordDao;

    @Mock
    private FollowService followService;

    @InjectMocks
    private LikeServiceImpl likeService;

    /**
     * 创建测试用视频对象
     *
     * @param id     视频ID
     * @param userId 发布者ID
     * @return 视频对象
     */
    private Video createTestVideo(Long id, Long userId) {
        Video video = new Video();
        video.setId(id);
        video.setUserId(userId);
        video.setTitle("测试视频");
        video.setCategory("搞笑");
        video.setViewCount(100L);
        video.setLikeCount(10L);
        video.setStatus(0);
        video.setCreatedAt(LocalDateTime.now());
        return video;
    }

    /**
     * 创建测试用默认收藏夹对象
     *
     * @param id     收藏夹ID
     * @param userId 用户ID
     * @return 收藏夹对象
     */
    private FavoriteFolder createDefaultFolder(Long id, Long userId) {
        FavoriteFolder folder = new FavoriteFolder();
        folder.setId(id);
        folder.setUserId(userId);
        folder.setName("默认收藏夹");
        folder.setIsDefault(1);
        folder.setCreatedAt(LocalDateTime.now());
        return folder;
    }

    @Nested
    @DisplayName("toggle 测试")
    class ToggleTests {

        @Test
        @DisplayName("点赞")
        void toggle_like_success() {
            when(likeRecordDao.findByUserAndTarget(1L, 1, 1L)).thenReturn(null);
            when(likeRecordDao.insert(any(LikeRecord.class))).thenReturn(1L);
            doNothing().when(videoDao).incrementLikeCount(1L);

            boolean result = likeService.toggle(1L, 1, 1L);

            assertTrue(result);
            verify(likeRecordDao).insert(any(LikeRecord.class));
            verify(videoDao).incrementLikeCount(1L);
        }

        @Test
        @DisplayName("取消点赞")
        void toggle_unlike_success() {
            when(likeRecordDao.findByUserAndTarget(1L, 1, 1L)).thenReturn(new LikeRecord());
            doNothing().when(likeRecordDao).delete(1L, 1, 1L);
            doNothing().when(videoDao).decrementLikeCount(1L);

            boolean result = likeService.toggle(1L, 1, 1L);

            assertFalse(result);
            verify(likeRecordDao).delete(1L, 1, 1L);
            verify(videoDao).decrementLikeCount(1L);
        }
    }

    @Nested
    @DisplayName("isLiked 测试")
    class IsLikedTests {

        @Test
        @DisplayName("检查点赞状态 - 已点赞")
        void isLiked_true() {
            when(likeRecordDao.findByUserAndTarget(1L, 1, 1L)).thenReturn(new LikeRecord());

            boolean result = likeService.isLiked(1L, 1, 1L);

            assertTrue(result);
        }

        @Test
        @DisplayName("检查点赞状态 - 未点赞")
        void isLiked_false() {
            when(likeRecordDao.findByUserAndTarget(1L, 1, 1L)).thenReturn(null);

            boolean result = likeService.isLiked(1L, 1, 1L);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("doubleTap 测试")
    class DoubleTapTests {

        @Test
        @DisplayName("一键二连 - 未点赞未收藏，点赞并收藏进默认收藏夹")
        void doubleTap_notLikedNotFavorited_success() {
            Video video = createTestVideo(1L, 2L);
            FavoriteFolder defaultFolder = createDefaultFolder(10L, 1L);

            when(videoDao.findById(1L)).thenReturn(video);
            // 未点赞
            when(likeRecordDao.findByUserAndTarget(1L, 1, 1L)).thenReturn(null);
            when(likeRecordDao.insert(any(LikeRecord.class))).thenReturn(1L);
            doNothing().when(videoDao).incrementLikeCount(1L);
            // 未收藏
            when(favoriteRecordDao.findByUserAndTarget(1L, 1, 1L)).thenReturn(null);
            // 默认收藏夹存在
            when(favoriteFolderDao.findDefaultByUserId(1L)).thenReturn(defaultFolder);
            when(favoriteRecordDao.insert(any(FavoriteRecord.class))).thenReturn(1L);

            Map<String, Object> result = likeService.doubleTap(1L, 1L);

            // 验证返回结果
            assertNotNull(result);
            assertEquals(true, result.get("liked"));
            assertEquals(true, result.get("favorited"));
            assertTrue(result.get("message").toString().contains("已点赞"));
            assertTrue(result.get("message").toString().contains("已收藏"));
            // 验证不包含"之前已"字样，说明是新操作的
            assertFalse(result.get("message").toString().contains("之前已点赞"));
            assertFalse(result.get("message").toString().contains("之前已收藏"));

            // 验证点赞和收藏的插入操作被调用
            verify(likeRecordDao).insert(any(LikeRecord.class));
            verify(videoDao).incrementLikeCount(1L);
            verify(favoriteRecordDao).insert(any(FavoriteRecord.class));
        }

        @Test
        @DisplayName("一键二连 - 已点赞未收藏，只收藏不重复点赞")
        void doubleTap_alreadyLikedNotFavorited_success() {
            Video video = createTestVideo(1L, 2L);
            FavoriteFolder defaultFolder = createDefaultFolder(10L, 1L);

            when(videoDao.findById(1L)).thenReturn(video);
            // 已点赞
            when(likeRecordDao.findByUserAndTarget(1L, 1, 1L)).thenReturn(new LikeRecord());
            // 未收藏
            when(favoriteRecordDao.findByUserAndTarget(1L, 1, 1L)).thenReturn(null);
            when(favoriteFolderDao.findDefaultByUserId(1L)).thenReturn(defaultFolder);
            when(favoriteRecordDao.insert(any(FavoriteRecord.class))).thenReturn(1L);

            Map<String, Object> result = likeService.doubleTap(1L, 1L);

            // 验证返回结果
            assertNotNull(result);
            assertEquals(true, result.get("liked"));
            assertEquals(true, result.get("favorited"));
            // message应包含"之前已点赞"
            assertTrue(result.get("message").toString().contains("之前已点赞"));
            assertFalse(result.get("message").toString().contains("之前已收藏"));

            // 验证点赞插入操作未被调用（已点赞，不重复操作）
            verify(likeRecordDao, never()).insert(any(LikeRecord.class));
            verify(videoDao, never()).incrementLikeCount(1L);
            // 验证收藏插入操作被调用
            verify(favoriteRecordDao).insert(any(FavoriteRecord.class));
        }

        @Test
        @DisplayName("一键二连 - 未点赞已收藏，只点赞不重复收藏")
        void doubleTap_notLikedAlreadyFavorited_success() {
            Video video = createTestVideo(1L, 2L);

            when(videoDao.findById(1L)).thenReturn(video);
            // 未点赞
            when(likeRecordDao.findByUserAndTarget(1L, 1, 1L)).thenReturn(null);
            when(likeRecordDao.insert(any(LikeRecord.class))).thenReturn(1L);
            doNothing().when(videoDao).incrementLikeCount(1L);
            // 已收藏
            when(favoriteRecordDao.findByUserAndTarget(1L, 1, 1L)).thenReturn(new FavoriteRecord());

            Map<String, Object> result = likeService.doubleTap(1L, 1L);

            // 验证返回结果
            assertNotNull(result);
            assertEquals(true, result.get("liked"));
            assertEquals(true, result.get("favorited"));
            assertFalse(result.get("message").toString().contains("之前已点赞"));
            assertTrue(result.get("message").toString().contains("之前已收藏"));

            // 验证点赞插入操作被调用
            verify(likeRecordDao).insert(any(LikeRecord.class));
            verify(videoDao).incrementLikeCount(1L);
            // 验证收藏插入操作未被调用（已收藏，不重复操作）
            verify(favoriteRecordDao, never()).insert(any(FavoriteRecord.class));
        }

        @Test
        @DisplayName("一键二连 - 已点赞已收藏，两者都不重复操作")
        void doubleTap_alreadyLikedAlreadyFavorited_success() {
            Video video = createTestVideo(1L, 2L);

            when(videoDao.findById(1L)).thenReturn(video);
            // 已点赞
            when(likeRecordDao.findByUserAndTarget(1L, 1, 1L)).thenReturn(new LikeRecord());
            // 已收藏
            when(favoriteRecordDao.findByUserAndTarget(1L, 1, 1L)).thenReturn(new FavoriteRecord());

            Map<String, Object> result = likeService.doubleTap(1L, 1L);

            // 验证返回结果
            assertNotNull(result);
            assertEquals(true, result.get("liked"));
            assertEquals(true, result.get("favorited"));
            assertTrue(result.get("message").toString().contains("之前已点赞"));
            assertTrue(result.get("message").toString().contains("之前已收藏"));

            // 验证点赞和收藏的插入操作都未被调用
            verify(likeRecordDao, never()).insert(any(LikeRecord.class));
            verify(videoDao, never()).incrementLikeCount(1L);
            verify(favoriteRecordDao, never()).insert(any(FavoriteRecord.class));
        }

        @Test
        @DisplayName("一键二连 - 默认收藏夹不存在时自动创建")
        void doubleTap_defaultFolderNotExist_autoCreate() {
            Video video = createTestVideo(1L, 2L);

            when(videoDao.findById(1L)).thenReturn(video);
            // 未点赞
            when(likeRecordDao.findByUserAndTarget(1L, 1, 1L)).thenReturn(null);
            when(likeRecordDao.insert(any(LikeRecord.class))).thenReturn(1L);
            doNothing().when(videoDao).incrementLikeCount(1L);
            // 未收藏
            when(favoriteRecordDao.findByUserAndTarget(1L, 1, 1L)).thenReturn(null);
            // 默认收藏夹不存在
            when(favoriteFolderDao.findDefaultByUserId(1L)).thenReturn(null);
            // 插入收藏夹返回ID
            when(favoriteFolderDao.insert(any(FavoriteFolder.class))).thenReturn(100L);
            when(favoriteRecordDao.insert(any(FavoriteRecord.class))).thenReturn(1L);

            Map<String, Object> result = likeService.doubleTap(1L, 1L);

            // 验证返回结果
            assertNotNull(result);
            assertEquals(true, result.get("liked"));
            assertEquals(true, result.get("favorited"));

            // 验证默认收藏夹被创建
            verify(favoriteFolderDao).insert(any(FavoriteFolder.class));
            // 验证收藏记录被插入
            verify(favoriteRecordDao).insert(any(FavoriteRecord.class));
        }

        @Test
        @DisplayName("一键二连 - 视频不存在")
        void doubleTap_videoNotExists_throwsException() {
            when(videoDao.findById(999L)).thenReturn(null);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> likeService.doubleTap(1L, 999L));
            assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("一键二连 - 视频已下架")
        void doubleTap_videoTakenDown_throwsException() {
            Video video = createTestVideo(1L, 2L);
            video.setStatus(1);
            when(videoDao.findById(1L)).thenReturn(video);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> likeService.doubleTap(1L, 1L));
            assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
        }
    }
}

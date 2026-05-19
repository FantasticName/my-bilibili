package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.github.fantasticname.mybilibili.dao.FavoriteFolderDao;
import io.github.fantasticname.mybilibili.dao.FavoriteRecordDao;
import io.github.fantasticname.mybilibili.dao.UserDao;
import io.github.fantasticname.mybilibili.dao.VideoDao;
import io.github.fantasticname.mybilibili.entity.FavoriteFolder;
import io.github.fantasticname.mybilibili.entity.FavoriteRecord;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.entity.Video;
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
class FavoriteServiceImplTest {

    @Mock
    private FavoriteFolderDao favoriteFolderDao;

    @Mock
    private FavoriteRecordDao favoriteRecordDao;

    @Mock
    private VideoDao videoDao;

    @Mock
    private UserDao userDao;

    @Mock
    private LikeService likeService;

    @InjectMocks
    private FavoriteServiceImpl favoriteService;

    private FavoriteFolder createTestFolder(Long id, Long userId, String name) {
        FavoriteFolder folder = new FavoriteFolder();
        folder.setId(id);
        folder.setUserId(userId);
        folder.setName(name);
        folder.setIsDefault(0);
        folder.setCreatedAt(LocalDateTime.now());
        return folder;
    }

    private User createTestUser(Long id, String nickname) {
        User user = new User();
        user.setId(id);
        user.setNickname(nickname);
        user.setAvatar("avatar.jpg");
        return user;
    }

    private Video createTestVideo(Long id, Long userId, String title) {
        Video video = new Video();
        video.setId(id);
        video.setUserId(userId);
        video.setTitle(title);
        video.setCoverUrl("cover.jpg");
        video.setVideoUrl("video.mp4");
        video.setCategory("搞笑");
        video.setViewCount(0L);
        video.setLikeCount(0L);
        video.setStatus(0);
        video.setCreatedAt(LocalDateTime.now());
        return video;
    }

    @Nested
    @DisplayName("createFolder 测试")
    class CreateFolderTests {

        @Test
        @DisplayName("正常创建收藏夹")
        void createFolder_success() {
            when(favoriteFolderDao.insert(any(FavoriteFolder.class))).thenReturn(1L);
            FavoriteFolder inserted = createTestFolder(1L, 1L, "学习资料");
            when(favoriteFolderDao.findById(1L)).thenReturn(inserted);

            FavoriteFolder result = favoriteService.createFolder(1L, "学习资料");

            assertNotNull(result);
            assertEquals("学习资料", result.getName());
        }

        @Test
        @DisplayName("创建收藏夹失败 - 名称为空")
        void createFolder_emptyName_throwsException() {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> favoriteService.createFolder(1L, ""));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        }
    }

    @Nested
    @DisplayName("listFolders 测试")
    class ListFoldersTests {

        @Test
        @DisplayName("获取收藏夹列表")
        void listFolders_success() {
            FavoriteFolder f1 = createTestFolder(1L, 1L, "默认收藏夹");
            FavoriteFolder f2 = createTestFolder(2L, 1L, "学习资料");
            when(favoriteFolderDao.listByUserId(1L)).thenReturn(Arrays.asList(f1, f2));

            List<FavoriteFolder> result = favoriteService.listFolders(1L);

            assertNotNull(result);
            assertEquals(2, result.size());
        }
    }

    @Nested
    @DisplayName("toggle 测试")
    class ToggleTests {

        @Test
        @DisplayName("收藏视频")
        void toggle_add_success() {
            when(favoriteRecordDao.findByFolderAndTarget(1L, 1, 1L)).thenReturn(null);
            when(favoriteRecordDao.insert(any(FavoriteRecord.class))).thenReturn(1L);

            boolean result = favoriteService.toggle(1L, 1L, 1, 1L);

            assertTrue(result);
            verify(favoriteRecordDao).insert(any(FavoriteRecord.class));
        }

        @Test
        @DisplayName("取消收藏")
        void toggle_remove_success() {
            FavoriteRecord existing = new FavoriteRecord();
            existing.setId(1L);
            when(favoriteRecordDao.findByFolderAndTarget(1L, 1, 1L)).thenReturn(existing);
            doNothing().when(favoriteRecordDao).deleteByFolderAndTarget(1L, 1, 1L);

            boolean result = favoriteService.toggle(1L, 1L, 1, 1L);

            assertFalse(result);
            verify(favoriteRecordDao).deleteByFolderAndTarget(1L, 1, 1L);
        }
    }

    @Nested
    @DisplayName("isFavorited 测试")
    class IsFavoritedTests {

        @Test
        @DisplayName("检查收藏状态 - 已收藏")
        void isFavorited_true() {
            FavoriteRecord record = new FavoriteRecord();
            record.setId(1L);
            when(favoriteRecordDao.findByUserAndTarget(1L, 1, 1L)).thenReturn(record);

            boolean result = favoriteService.isFavorited(1L, 1, 1L);

            assertTrue(result);
        }

        @Test
        @DisplayName("检查收藏状态 - 未收藏")
        void isFavorited_false() {
            when(favoriteRecordDao.findByUserAndTarget(1L, 1, 1L)).thenReturn(null);

            boolean result = favoriteService.isFavorited(1L, 1, 1L);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("listVideos 测试")
    class ListVideosTests {

        @Test
        @DisplayName("获取收藏夹中的视频列表")
        void listVideos_success() {
            FavoriteRecord r1 = new FavoriteRecord();
            r1.setId(1L);
            r1.setFolderId(1L);
            r1.setUserId(1L);
            r1.setTargetType(1);
            r1.setTargetId(1L);
            FavoriteRecord r2 = new FavoriteRecord();
            r2.setId(2L);
            r2.setFolderId(1L);
            r2.setUserId(1L);
            r2.setTargetType(1);
            r2.setTargetId(2L);
            when(favoriteRecordDao.listByFolderId(1L, 0, 10)).thenReturn(Arrays.asList(r1, r2));
            FavoriteFolder folder = new FavoriteFolder();
            folder.setId(1L);
            folder.setUserId(1L);
            folder.setName("测试收藏夹");
            when(favoriteFolderDao.findById(1L)).thenReturn(folder);
            when(videoDao.findById(1L)).thenReturn(createTestVideo(1L, 1L, "视频1"));
            when(videoDao.findById(2L)).thenReturn(createTestVideo(2L, 2L, "视频2"));
            when(userDao.findById(1L)).thenReturn(createTestUser(1L, "user1"));
            when(userDao.findById(2L)).thenReturn(createTestUser(2L, "user2"));

            List<VideoVO> result = favoriteService.listVideos(1L, 1L, 1, 10);

            assertNotNull(result);
            assertEquals(2, result.size());
        }
    }
}

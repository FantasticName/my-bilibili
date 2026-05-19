package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.github.fantasticname.mybilibili.dao.FollowDao;
import io.github.fantasticname.mybilibili.dao.UserDao;
import io.github.fantasticname.mybilibili.entity.Follow;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.vo.PublicUserVO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FollowServiceImplTest {

    @Mock
    private FollowDao followDao;

    @Mock
    private UserDao userDao;

    @InjectMocks
    private FollowServiceImpl followService;

    private User createTestUser(Long id, String nickname) {
        User user = new User();
        user.setId(id);
        user.setNickname(nickname);
        user.setAvatar("avatar.jpg");
        user.setRole(0);
        return user;
    }

    private Follow createTestFollow(Long followerId, Long followeeId) {
        Follow follow = new Follow();
        follow.setFollowerId(followerId);
        follow.setFolloweeId(followeeId);
        return follow;
    }

    @Nested
    @DisplayName("toggle 测试")
    class ToggleTests {

        @Test
        @DisplayName("关注用户")
        void toggle_follow_success() {
            when(userDao.findById(2L)).thenReturn(createTestUser(2L, "user2"));
            when(followDao.findByFollowerAndFollowee(1L, 2L)).thenReturn(null);
            when(followDao.insert(any(Follow.class))).thenReturn(1L);

            boolean result = followService.toggle(1L, 2L);

            assertTrue(result);
            verify(followDao).insert(any(Follow.class));
        }

        @Test
        @DisplayName("取消关注")
        void toggle_unfollow_success() {
            when(userDao.findById(2L)).thenReturn(createTestUser(2L, "user2"));
            when(followDao.findByFollowerAndFollowee(1L, 2L)).thenReturn(createTestFollow(1L, 2L));
            doNothing().when(followDao).delete(1L, 2L);

            boolean result = followService.toggle(1L, 2L);

            assertFalse(result);
            verify(followDao).delete(1L, 2L);
        }

        @Test
        @DisplayName("不能关注自己")
        void toggle_selfFollow_throwsException() {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> followService.toggle(1L, 1L));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        }
    }

    @Nested
    @DisplayName("isFollowing 测试")
    class IsFollowingTests {

        @Test
        @DisplayName("检查关注状态 - 已关注")
        void isFollowing_true() {
            when(followDao.findByFollowerAndFollowee(1L, 2L)).thenReturn(createTestFollow(1L, 2L));

            boolean result = followService.isFollowing(1L, 2L);

            assertTrue(result);
        }

        @Test
        @DisplayName("检查关注状态 - 未关注")
        void isFollowing_false() {
            when(followDao.findByFollowerAndFollowee(1L, 2L)).thenReturn(null);

            boolean result = followService.isFollowing(1L, 2L);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("countFollowing/countFollowers 测试")
    class CountTests {

        @Test
        @DisplayName("统计关注数")
        void countFollowing_success() {
            when(followDao.countFollowing(1L)).thenReturn(5);

            int result = followService.countFollowing(1L);

            assertEquals(5, result);
        }

        @Test
        @DisplayName("统计粉丝数")
        void countFollowers_success() {
            when(followDao.countFollowers(1L)).thenReturn(10);

            int result = followService.countFollowers(1L);

            assertEquals(10, result);
        }
    }

    @Nested
    @DisplayName("listFollowing 测试")
    class ListFollowingTests {

        @Test
        @DisplayName("获取关注列表")
        void listFollowing_success() {
            User u1 = createTestUser(2L, "user2");
            User u2 = createTestUser(3L, "user3");
            when(followDao.listFollowing(eq(1L), eq(0), eq(10)))
                    .thenReturn(Arrays.asList(createTestFollow(1L, 2L), createTestFollow(1L, 3L)));
            when(userDao.findById(2L)).thenReturn(u1);
            when(userDao.findById(3L)).thenReturn(u2);
            when(followDao.countFollowing(2L)).thenReturn(1);
            when(followDao.countFollowers(2L)).thenReturn(0);
            when(followDao.countFollowing(3L)).thenReturn(0);
            when(followDao.countFollowers(3L)).thenReturn(1);
            when(followDao.findByFollowerAndFollowee(1L, 2L)).thenReturn(createTestFollow(1L, 2L));
            when(followDao.findByFollowerAndFollowee(1L, 3L)).thenReturn(null);

            List<PublicUserVO> result = followService.listFollowing(1L, 1, 10);

            assertNotNull(result);
            assertEquals(2, result.size());
        }
    }
}

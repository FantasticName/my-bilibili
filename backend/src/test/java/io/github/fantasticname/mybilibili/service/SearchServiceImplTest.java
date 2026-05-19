package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.dao.PostDao;
import io.github.fantasticname.mybilibili.dao.UserDao;
import io.github.fantasticname.mybilibili.dao.VideoDao;
import io.github.fantasticname.mybilibili.entity.Post;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.entity.Video;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    @Mock
    private VideoDao videoDao;

    @Mock
    private PostDao postDao;

    @Mock
    private UserDao userDao;

    @InjectMocks
    private SearchServiceImpl searchService;

    private Video createTestVideo(Long id, String title, Long userId) {
        Video v = new Video();
        v.setId(id);
        v.setTitle(title);
        v.setUserId(userId);
        v.setCoverUrl("http://test/cover.jpg");
        v.setViewCount(100L);
        v.setLikeCount(10L);
        v.setCategory("搞笑");
        v.setStatus(0);
        v.setCreatedAt(LocalDateTime.now());
        return v;
    }

    private Post createTestPost(Long id, String content, Long userId) {
        Post p = new Post();
        p.setId(id);
        p.setContent(content);
        p.setUserId(userId);
        p.setLikeCount(5);
        p.setCommentCount(2);
        p.setStatus(0);
        p.setCreatedAt(LocalDateTime.now());
        return p;
    }

    private User createTestUser(Long id, String nickname) {
        User u = new User();
        u.setId(id);
        u.setNickname(nickname);
        u.setAvatar("http://test/avatar.jpg");
        u.setCreatedAt(LocalDateTime.now());
        return u;
    }

    @Test
    void testSearchVideos_shouldReturnMatchedVideos() {
        Video v1 = createTestVideo(1L, "搞笑视频集锦", 10L);
        Video v2 = createTestVideo(2L, "搞笑日常", 10L);
        User user = createTestUser(10L, "博主A");

        when(videoDao.countByTitle("搞笑")).thenReturn(2);
        when(videoDao.searchByTitle(eq("搞笑"), eq(0), eq(10))).thenReturn(Arrays.asList(v1, v2));
        when(userDao.findById(10L)).thenReturn(user);

        Map<String, Object> result = searchService.searchVideos("搞笑", 1, 10);

        assertEquals(2, result.get("total"));
        @SuppressWarnings("unchecked")
        List<?> list = (List<?>) result.get("list");
        assertEquals(2, list.size());
    }

    @Test
    void testSearchPosts_shouldReturnMatchedPosts() {
        Post p1 = createTestPost(1L, "今天天气真好", 20L);
        User user = createTestUser(20L, "博主B");

        when(postDao.countByContent("天气")).thenReturn(1);
        when(postDao.searchByContent(eq("天气"), eq(0), eq(10))).thenReturn(Arrays.asList(p1));
        when(userDao.findById(20L)).thenReturn(user);

        Map<String, Object> result = searchService.searchPosts("天气", 1, 10);

        assertEquals(1, result.get("total"));
        @SuppressWarnings("unchecked")
        List<?> list = (List<?>) result.get("list");
        assertEquals(1, list.size());
    }

    @Test
    void testSearchUsers_shouldReturnMatchedUsers() {
        User u1 = createTestUser(1L, "技术达人");
        User u2 = createTestUser(2L, "技术宅");

        when(userDao.countByNickname("技术")).thenReturn(2);
        when(userDao.searchByNickname(eq("技术"), eq(0), eq(10))).thenReturn(Arrays.asList(u1, u2));

        Map<String, Object> result = searchService.searchUsers("技术", 1, 10);

        assertEquals(2, result.get("total"));
        @SuppressWarnings("unchecked")
        List<?> list = (List<?>) result.get("list");
        assertEquals(2, list.size());
    }

    @Test
    void testSearchAll_shouldReturnAllTypes() {
        Video v1 = createTestVideo(1L, "搞笑视频", 10L);
        Post p1 = createTestPost(1L, "搞笑日常", 20L);
        User u1 = createTestUser(1L, "搞笑博主");
        User pubUser = createTestUser(10L, "博主A");
        User postUser = createTestUser(20L, "博主B");

        when(videoDao.countByTitle("搞笑")).thenReturn(1);
        when(videoDao.searchByTitle(eq("搞笑"), eq(0), eq(5))).thenReturn(Arrays.asList(v1));
        when(postDao.countByContent("搞笑")).thenReturn(1);
        when(postDao.searchByContent(eq("搞笑"), eq(0), eq(5))).thenReturn(Arrays.asList(p1));
        when(userDao.countByNickname("搞笑")).thenReturn(1);
        when(userDao.searchByNickname(eq("搞笑"), eq(0), eq(5))).thenReturn(Arrays.asList(u1));
        when(userDao.findById(10L)).thenReturn(pubUser);
        when(userDao.findById(20L)).thenReturn(postUser);

        Map<String, Object> result = searchService.search("搞笑", 1, 5);

        assertEquals(1, result.get("videoTotal"));
        assertEquals(1, result.get("postTotal"));
        assertEquals(1, result.get("userTotal"));

        @SuppressWarnings("unchecked")
        List<?> list = (List<?>) result.get("list");
        assertEquals(3, list.size());
    }
}
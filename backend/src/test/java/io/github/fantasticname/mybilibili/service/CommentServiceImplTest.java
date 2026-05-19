package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.github.fantasticname.mybilibili.dao.CommentDao;
import io.github.fantasticname.mybilibili.dao.PostDao;
import io.github.fantasticname.mybilibili.dao.UserDao;
import io.github.fantasticname.mybilibili.entity.Comment;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.vo.CommentVO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 评论服务单元测试
 *
 * <p>覆盖以下功能：</p>
 * <ul>
 *   <li>创建评论（含递归深度限制）</li>
 *   <li>获取评论列表（含子回复hasMoreReplies标记）</li>
 *   <li>获取子回复（游标分页）</li>
 *   <li>删除评论（权限校验）</li>
 * </ul>
 *
 * @author FantasticName
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock
    private CommentDao commentDao;

    @Mock
    private UserDao userDao;

    @Mock
    private LikeService likeService;

    @Mock
    private PostDao postDao;

    @InjectMocks
    private CommentServiceImpl commentService;

    /**
     * 创建测试用的顶层评论
     */
    private Comment createTestComment(Long id, Long userId, String content, Integer targetType, Long targetId) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setTargetType(targetType);
        comment.setTargetId(targetId);
        comment.setParentId(null);
        comment.setLikeCount(0);
        comment.setStatus(0);
        comment.setCreatedAt(LocalDateTime.now());
        return comment;
    }

    /**
     * 创建测试用的回复评论
     */
    private Comment createTestReply(Long id, Long userId, String content, Integer targetType, Long targetId, Long parentId) {
        Comment comment = createTestComment(id, userId, content, targetType, targetId);
        comment.setParentId(parentId);
        comment.setLikeCount(5);
        return comment;
    }

    /**
     * 创建测试用的用户
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
        @DisplayName("正常创建评论")
        void create_success() {
            when(commentDao.insert(any(Comment.class))).thenReturn(1L);
            Comment inserted = createTestComment(1L, 1L, "好视频！", 1, 1L);
            when(commentDao.findById(1L)).thenReturn(inserted);
            when(userDao.findById(1L)).thenReturn(createTestUser(1L, "testUser"));

            CommentVO result = commentService.create(1L, "好视频！", 1, 1L, null);

            assertNotNull(result);
            assertEquals("好视频！", result.getContent());
            verify(commentDao).insert(any(Comment.class));
        }

        @Test
        @DisplayName("创建评论失败 - 内容为空")
        void create_emptyContent_throwsException() {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> commentService.create(1L, "", 1, 1L, null));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("创建回复 - 父评论不存在")
        void create_parentNotFound_throwsException() {
            when(commentDao.findById(999L)).thenReturn(null);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> commentService.create(1L, "回复内容", 1, 1L, 999L));
            assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("创建回复 - 成功")
        void create_reply_success() {
            Comment parent = createTestComment(10L, 2L, "父评论", 1, 1L);
            when(commentDao.findById(10L)).thenReturn(parent);
            when(commentDao.insert(any(Comment.class))).thenReturn(20L);
            Comment inserted = createTestReply(20L, 1L, "回复内容", 1, 1L, 10L);
            when(commentDao.findById(20L)).thenReturn(inserted);
            when(userDao.findById(1L)).thenReturn(createTestUser(1L, "testUser"));

            CommentVO result = commentService.create(1L, "回复内容", 1, 1L, 10L);

            assertNotNull(result);
            assertEquals(10L, result.getParentId());
        }

        @Test
        @DisplayName("创建回复 - 层级过深（递归深度攻击防护）")
        void create_depthTooDeep_throwsException() {
            // 模拟10层深度的评论链：parentId=100 -> 99 -> 98 -> ... -> 90 -> null
            when(commentDao.findById(100L)).thenAnswer(invocation -> {
                Comment c = new Comment();
                c.setId(100L);
                c.setParentId(99L);
                c.setStatus(0);
                return c;
            });
            // 模拟从99到91的评论链（每层的parentId指向上一个）
            for (long i = 99; i >= 91; i--) {
                final long currentId = i;
                final long parentRefId = i - 1;
                when(commentDao.findById(currentId)).thenAnswer(invocation -> {
                    Comment c = new Comment();
                    c.setId(currentId);
                    c.setParentId(parentRefId >= 90 ? parentRefId : null);
                    c.setStatus(0);
                    return c;
                });
            }
            // id=90是顶层评论（parentId=null）
            Comment topComment = new Comment();
            topComment.setId(90L);
            topComment.setParentId(null);
            topComment.setStatus(0);
            when(commentDao.findById(90L)).thenReturn(topComment);

            // 尝试在10层深度下创建回复，应该抛出异常
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> commentService.create(1L, "过深的回复", 1, 1L, 100L));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
            assertTrue(exception.getMessage().contains("层级过深"));
        }
    }

    @Nested
    @DisplayName("listByTarget 测试")
    class ListByTargetTests {

        @Test
        @DisplayName("获取评论列表 - 空列表")
        void listByTarget_emptyList() {
            when(commentDao.listTopCommentsHot(eq(1), eq(1L), isNull(), isNull(), eq(10)))
                    .thenReturn(new ArrayList<>());

            Map<String, Object> result = commentService.listByTarget(1, 1L, "hot", null, null, 10);

            assertNotNull(result);
            assertTrue(((List<?>) result.get("list")).isEmpty());
            assertNull(result.get("nextCursor"));
        }

        @Test
        @DisplayName("获取评论列表 - 有数据且无子回复")
        void listByTarget_success() {
            Comment c1 = createTestComment(1L, 1L, "评论1", 1, 1L);
            c1.setLikeCount(10);
            Comment c2 = createTestComment(2L, 2L, "评论2", 1, 1L);
            c2.setLikeCount(5);

            when(commentDao.listTopCommentsHot(eq(1), eq(1L), isNull(), isNull(), eq(10)))
                    .thenReturn(Arrays.asList(c1, c2));
            when(commentDao.countRepliesByParentIds(Arrays.asList(1L, 2L)))
                    .thenReturn(new HashMap<>());
            when(commentDao.listRepliesByParentIds(Arrays.asList(1L, 2L)))
                    .thenReturn(new ArrayList<>());
            when(userDao.findById(1L)).thenReturn(createTestUser(1L, "user1"));
            when(userDao.findById(2L)).thenReturn(createTestUser(2L, "user2"));

            Map<String, Object> result = commentService.listByTarget(1, 1L, "hot", null, null, 10);

            assertNotNull(result);
            List<?> list = (List<?>) result.get("list");
            assertEquals(2, list.size());
            assertEquals(5, result.get("nextCursor"));
            assertEquals(2L, result.get("nextCursorId"));
        }

        @Test
        @DisplayName("获取评论列表 - 含子回复且hasMoreReplies标记正确")
        void listByTarget_withReplies() {
            Comment top = createTestComment(1L, 1L, "顶层评论", 1, 1L);
            top.setLikeCount(10);
            Comment reply1 = createTestReply(2L, 2L, "回复1", 1, 1L, 1L);
            Comment reply2 = createTestReply(3L, 3L, "回复2", 1, 1L, 1L);
            Comment reply3 = createTestReply(4L, 4L, "回复3", 1, 1L, 1L);
            Comment reply4 = createTestReply(5L, 5L, "回复4", 1, 1L, 1L);

            when(commentDao.listTopCommentsHot(eq(1), eq(1L), isNull(), isNull(), eq(10)))
                    .thenReturn(Arrays.asList(top));
            // 4条直接子回复
            when(commentDao.listRepliesByParentIds(Arrays.asList(1L)))
                    .thenReturn(Arrays.asList(reply1, reply2, reply3, reply4));
            // 顶层评论有4条直接子回复
            Map<Long, Integer> topCountMap = new HashMap<>();
            topCountMap.put(1L, 4);
            when(commentDao.countRepliesByParentIds(Arrays.asList(1L)))
                    .thenReturn(topCountMap);
            // 子回复没有自己的子回复
            when(commentDao.countRepliesByParentIds(Arrays.asList(2L, 3L, 4L, 5L)))
                    .thenReturn(new HashMap<>());
            when(userDao.findById(anyLong())).thenAnswer(invocation -> {
                Long id = invocation.getArgument(0);
                return createTestUser(id, "user" + id);
            });

            Map<String, Object> result = commentService.listByTarget(1, 1L, "hot", null, null, 10);

            List<CommentVO> list = (List<CommentVO>) result.get("list");
            assertEquals(1, list.size());
            // 只展示3条直接子回复
            assertEquals(3, list.get(0).getReplies().size());
            // 有更多子回复
            assertTrue(list.get(0).getHasMoreReplies());
            // 子回复的replies为空（不递归加载更深层）
            assertEquals(0, list.get(0).getReplies().get(0).getReplies().size());
        }

        @Test
        @DisplayName("获取评论列表 - 子回复有自己的子回复（hasMoreReplies标记）")
        void listByTarget_subRepliesHaveChildren() {
            Comment top = createTestComment(1L, 1L, "顶层评论", 1, 1L);
            top.setLikeCount(10);
            Comment reply1 = createTestReply(2L, 2L, "回复1", 1, 1L, 1L);

            when(commentDao.listTopCommentsHot(eq(1), eq(1L), isNull(), isNull(), eq(10)))
                    .thenReturn(Arrays.asList(top));
            when(commentDao.listRepliesByParentIds(Arrays.asList(1L)))
                    .thenReturn(Arrays.asList(reply1));
            Map<Long, Integer> topCountMap = new HashMap<>();
            topCountMap.put(1L, 1);
            when(commentDao.countRepliesByParentIds(Arrays.asList(1L)))
                    .thenReturn(topCountMap);
            // reply1有2条子回复
            Map<Long, Integer> subCountMap = new HashMap<>();
            subCountMap.put(2L, 2);
            when(commentDao.countRepliesByParentIds(Arrays.asList(2L)))
                    .thenReturn(subCountMap);
            when(userDao.findById(anyLong())).thenAnswer(invocation -> {
                Long id = invocation.getArgument(0);
                return createTestUser(id, "user" + id);
            });

            Map<String, Object> result = commentService.listByTarget(1, 1L, "hot", null, null, 10);

            List<CommentVO> list = (List<CommentVO>) result.get("list");
            CommentVO topVO = list.get(0);
            // reply1有子回复，应该显示"展开更多回复"
            assertTrue(topVO.getReplies().get(0).getHasMoreReplies());
            // replies为空（不递归加载）
            assertEquals(0, topVO.getReplies().get(0).getReplies().size());
        }
    }

    @Nested
    @DisplayName("listReplies 测试（游标分页）")
    class ListRepliesTests {

        @Test
        @DisplayName("获取子回复 - 空列表")
        void listReplies_emptyList() {
            when(commentDao.listRepliesWithCursor(eq(1L), isNull(), isNull(), eq(10)))
                    .thenReturn(new ArrayList<>());

            Map<String, Object> result = commentService.listReplies(1L, null, null, 10);

            assertNotNull(result);
            assertTrue(((List<?>) result.get("list")).isEmpty());
            assertNull(result.get("nextCursor"));
        }

        @Test
        @DisplayName("获取子回复 - 首次加载")
        void listReplies_firstPage() {
            Comment reply1 = createTestReply(2L, 2L, "回复1", 1, 1L, 1L);
            reply1.setLikeCount(10);
            Comment reply2 = createTestReply(3L, 3L, "回复2", 1, 1L, 1L);
            reply2.setLikeCount(5);

            when(commentDao.listRepliesWithCursor(eq(1L), isNull(), isNull(), eq(10)))
                    .thenReturn(Arrays.asList(reply1, reply2));
            // 子回复没有自己的子回复
            when(commentDao.countRepliesByParentIds(Arrays.asList(2L, 3L)))
                    .thenReturn(new HashMap<>());
            when(userDao.findById(anyLong())).thenAnswer(invocation -> {
                Long id = invocation.getArgument(0);
                return createTestUser(id, "user" + id);
            });

            Map<String, Object> result = commentService.listReplies(1L, null, null, 10);

            List<CommentVO> list = (List<CommentVO>) result.get("list");
            assertEquals(2, list.size());
            assertEquals(5, result.get("nextCursor"));
            assertEquals(3L, result.get("nextCursorId"));
            // 子回复的replies为空
            assertEquals(0, list.get(0).getReplies().size());
            assertFalse(list.get(0).getHasMoreReplies());
        }

        @Test
        @DisplayName("获取子回复 - 子回复有自己的子回复")
        void listReplies_subRepliesHaveChildren() {
            Comment reply1 = createTestReply(2L, 2L, "回复1", 1, 1L, 1L);

            when(commentDao.listRepliesWithCursor(eq(1L), isNull(), isNull(), eq(10)))
                    .thenReturn(Arrays.asList(reply1));
            Map<Long, Integer> subCountMap = new HashMap<>();
            subCountMap.put(2L, 3);
            when(commentDao.countRepliesByParentIds(Arrays.asList(2L)))
                    .thenReturn(subCountMap);
            when(userDao.findById(2L)).thenReturn(createTestUser(2L, "user2"));

            Map<String, Object> result = commentService.listReplies(1L, null, null, 10);

            List<CommentVO> list = (List<CommentVO>) result.get("list");
            assertEquals(1, list.size());
            // reply1有子回复，应该显示"展开更多回复"
            assertTrue(list.get(0).getHasMoreReplies());
            // replies为空（不递归加载）
            assertEquals(0, list.get(0).getReplies().size());
        }

        @Test
        @DisplayName("获取子回复 - 游标分页（第二页）")
        void listReplies_secondPage() {
            Comment reply3 = createTestReply(4L, 4L, "回复3", 1, 1L, 1L);
            reply3.setLikeCount(3);

            when(commentDao.listRepliesWithCursor(eq(1L), eq(5), eq(3L), eq(10)))
                    .thenReturn(Arrays.asList(reply3));
            when(commentDao.countRepliesByParentIds(Arrays.asList(4L)))
                    .thenReturn(new HashMap<>());
            when(userDao.findById(4L)).thenReturn(createTestUser(4L, "user4"));

            Map<String, Object> result = commentService.listReplies(1L, 5, 3L, 10);

            List<CommentVO> list = (List<CommentVO>) result.get("list");
            assertEquals(1, list.size());
            assertEquals(3, result.get("nextCursor"));
            assertEquals(4L, result.get("nextCursorId"));
        }

        @Test
        @DisplayName("获取子回复 - size超过50被限制")
        void listReplies_sizeCapped() {
            when(commentDao.listRepliesWithCursor(eq(1L), isNull(), isNull(), eq(50)))
                    .thenReturn(new ArrayList<>());

            commentService.listReplies(1L, null, null, 100);

            // 验证size被限制为50
            verify(commentDao).listRepliesWithCursor(eq(1L), isNull(), isNull(), eq(50));
        }
    }

    @Nested
    @DisplayName("delete 测试")
    class DeleteTests {

        @Test
        @DisplayName("正常删除自己的评论")
        void delete_ownComment_success() {
            Comment comment = createTestComment(1L, 1L, "我的评论", 1, 1L);
            when(commentDao.findById(1L)).thenReturn(comment);
            doNothing().when(commentDao).softDelete(1L);

            commentService.delete(1L, 1L, false);

            verify(commentDao).softDelete(1L);
        }

        @Test
        @DisplayName("删除他人评论失败 - 非管理员")
        void delete_otherComment_throwsException() {
            Comment comment = createTestComment(1L, 2L, "别人的评论", 1, 1L);
            when(commentDao.findById(1L)).thenReturn(comment);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> commentService.delete(1L, 1L, false));
            assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("管理员删除他人评论 - 成功")
        void delete_adminDeleteOther_success() {
            Comment comment = createTestComment(1L, 2L, "别人的评论", 1, 1L);
            when(commentDao.findById(1L)).thenReturn(comment);
            doNothing().when(commentDao).softDelete(1L);

            commentService.delete(1L, 1L, true);

            verify(commentDao).softDelete(1L);
        }

        @Test
        @DisplayName("删除不存在的评论 - 失败")
        void delete_notFound_throwsException() {
            when(commentDao.findById(999L)).thenReturn(null);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> commentService.delete(999L, 1L, false));
            assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
        }
    }
}

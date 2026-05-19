package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.annotation.Autowired;
import io.github.fantasticname.mybilibili.annotation.Service;
import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.github.fantasticname.mybilibili.dao.PostDao;
import io.github.fantasticname.mybilibili.dao.UserDao;
import io.github.fantasticname.mybilibili.entity.Post;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.util.FileUtil;
import io.github.fantasticname.mybilibili.util.RedisUtil;
import io.github.fantasticname.mybilibili.vo.PostVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 动态服务实现类
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>发布动态：支持纯文字或文字+多图，图片通过逗号分隔存储在数据库中</li>
 *   <li>动态列表：支持游标分页，按时间倒序</li>
 *   <li>动态详情：展示内容、图片、发布者信息、点赞数、评论数</li>
 *   <li>删除动态：软删除，仅作者可操作</li>
 * </ul>
 *
 * @author FantasticName
 */
@Service
public class PostServiceImpl implements PostService {

    private static final Logger log = LoggerFactory.getLogger(PostServiceImpl.class);

    /**
     * 动态最多允许上传的图片数量
     */
    private static final int MAX_IMAGE_COUNT = 9;

    @Autowired
    private PostDao postDao;

    @Autowired
    private UserDao userDao;

    @Autowired
    private LikeService likeService;

    public PostServiceImpl() {
    }

    @Override
    public PostVO create(Long userId, String content, String images) {
        log.info("开始发布动态: userId={}", userId);

        // 校验：内容和图片不能同时为空
        boolean hasContent = content != null && !content.trim().isEmpty();
        boolean hasImages = images != null && !images.trim().isEmpty();
        if (!hasContent && !hasImages) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "动态内容和图片不能同时为空");
        }

        // 校验：文字内容长度限制
        if (content != null && content.length() > 5000) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "动态内容过长");
        }

        // 校验：图片数量限制
        if (hasImages) {
            int imageCount = images.split(",").length;
            if (imageCount > MAX_IMAGE_COUNT) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "最多上传" + MAX_IMAGE_COUNT + "张图片");
            }
        }

        // 构建Post实体
        Post post = new Post();
        post.setContent(content != null ? content.trim() : null);
        post.setImages(images != null ? images.trim() : null);
        post.setUserId(userId);

        // 插入数据库
        long postId = postDao.insert(post);
        log.info("动态发布成功: postId={}", postId);

        // 返回VO
        return convertToVO(postDao.findById(postId), userId);
    }

    @Override
    public PostVO getDetail(Long postId, Long currentUserId) {
        log.info("获取动态详情: postId={}", postId);

        // 【二级缓存体系】先查Redis缓存，命中则直接返回
        String cacheKey = "post:" + postId;
        try {
            PostVO cached = RedisUtil.getObject(cacheKey, PostVO.class);
            if (cached != null) {
                // 检查空值标记（id=-1表示"不存在"）
                if (cached.getId() != null && cached.getId() == -1) {
                    throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "动态不存在");
                }
                log.info("动态缓存命中: postId={}", postId);
                return cached;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("动态缓存读取异常，降级查DB: postId={}", postId);
        }

        // 缓存未命中，查DB
        Post post = postDao.findById(postId);
        if (post == null || post.getStatus() != 0) {
            // 缓存空值标记，防止缓存穿透
            try {
                PostVO nullMarker = new PostVO();
                nullMarker.setId(-1L);
                RedisUtil.setObject(cacheKey, nullMarker, 60);
            } catch (Exception e) {
                log.warn("缓存空值标记失败: postId={}", postId);
            }
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "动态不存在");
        }

        PostVO vo = convertToVO(post, currentUserId);

        // 写入缓存，TTL=5分钟+随机60秒，防雪崩
        try {
            int ttl = 300 + new java.util.Random().nextInt(60);
            RedisUtil.setObject(cacheKey, vo, ttl);
            log.info("动态缓存写入: postId={}, TTL={}秒", postId, ttl);
        } catch (Exception e) {
            log.warn("动态缓存写入失败: postId={}", postId);
        }

        return vo;
    }

    @Override
    public List<PostVO> listByUser(Long userId, int page, int size) {
        int offset = (page - 1) * size;
        List<Post> posts = postDao.listByUserId(userId, offset, size);
        List<PostVO> result = new ArrayList<>();
        for (Post p : posts) {
            result.add(convertToVO(p, null));
        }
        return result;
    }

    @Override
    public List<PostVO> listByUserCursor(Long userId, String cursor, int size) {
        // 游标分页查询
        List<Post> posts = postDao.listByUserIdCursor(userId, cursor, size);
        List<PostVO> result = new ArrayList<>();
        for (Post p : posts) {
            result.add(convertToVO(p, null));
        }
        return result;
    }

    @Override
    public int countByUser(Long userId) {
        return postDao.countByUserId(userId);
    }

    @Override
    public void delete(Long postId, Long userId) {
        Post post = postDao.findById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "动态不存在");
        }
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "只能删除自己的动态");
        }
        postDao.softDelete(postId);
        // 【缓存一致性】删除动态后，删除对应的缓存
        deleteCacheWithRetry("post:" + postId);
        log.info("动态删除成功: postId={}", postId);
    }

    @Override
    public PostVO update(Long postId, Long userId, String content, String existingImages, String newImages) {
        log.info("编辑动态: postId={}, userId={}", postId, userId);

        // 查询动态是否存在
        Post post = postDao.findById(postId);
        if (post == null || post.getStatus() != 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "动态不存在");
        }

        // 权限校验：只能编辑自己的动态
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "只能编辑自己的动态");
        }

        // 合并已有图片和新上传图片的文件名
        // existingImages: 前端传来的保留的已有图片文件名（逗号分隔，已经是文件名不含/upload/前缀）
        // newImages: 本次新上传的图片文件名（逗号分隔）
        StringBuilder allImages = new StringBuilder();
        if (existingImages != null && !existingImages.trim().isEmpty()) {
            allImages.append(existingImages.trim());
        }
        if (newImages != null && !newImages.trim().isEmpty()) {
            if (allImages.length() > 0) {
                allImages.append(",");
            }
            allImages.append(newImages.trim());
        }

        String finalImages = allImages.length() > 0 ? allImages.toString() : null;

        // 校验：内容和图片不能同时为空
        boolean hasContent = content != null && !content.trim().isEmpty();
        boolean hasImages = finalImages != null && !finalImages.trim().isEmpty();
        if (!hasContent && !hasImages) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "动态内容和图片不能同时为空");
        }

        // 校验：图片数量限制
        if (hasImages) {
            int imageCount = finalImages.split(",").length;
            if (imageCount > MAX_IMAGE_COUNT) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "最多上传" + MAX_IMAGE_COUNT + "张图片");
            }
        }

        // 更新数据库
        postDao.update(postId, content != null ? content.trim() : null, finalImages);
        // 【缓存一致性】编辑动态后，删除对应的缓存
        deleteCacheWithRetry("post:" + postId);
        log.info("动态编辑成功: postId={}", postId);

        // 返回更新后的VO
        return convertToVO(postDao.findById(postId), userId);
    }

    /**
     * 将Post实体转换为PostVO
     *
     * <p>核心转换逻辑：</p>
     * <ul>
     *   <li>images字段：数据库中存储的是逗号分隔的URL字符串，转换为List&lt;String&gt;</li>
     *   <li>每个图片URL通过FileUtil.toUrl()转换为完整访问路径</li>
     *   <li>用户信息：查询发布者的昵称和头像</li>
     *   <li>点赞状态：如果当前用户已登录，查询是否已点赞</li>
     * </ul>
     *
     * @param post           动态实体
     * @param currentUserId  当前登录用户ID（可为null）
     * @return 动态VO
     */
    private PostVO convertToVO(Post post, Long currentUserId) {
        if (post == null) return null;
        PostVO vo = new PostVO();
        vo.setId(post.getId());
        vo.setContent(post.getContent());

        // 将逗号分隔的图片字符串转换为List<String>
        // 每个图片URL通过FileUtil.toUrl()拼接完整访问路径
        vo.setImages(parseImages(post.getImages()));

        vo.setUserId(post.getUserId());
        vo.setLikeCount(post.getLikeCount());
        vo.setCommentCount(post.getCommentCount());
        vo.setCreatedAt(post.getCreatedAt());

        // 查询发布者信息
        User user = userDao.findById(post.getUserId());
        if (user != null) {
            vo.setNickname(user.getNickname());
            vo.setAvatar(FileUtil.toUrl(user.getAvatar()));
        }

        // 如果当前用户已登录，查询是否已点赞（targetType=3表示动态点赞）
        if (currentUserId != null) {
            vo.setIsLiked(likeService.isLiked(currentUserId, 3, post.getId()));
        }

        return vo;
    }

    /**
     * 将逗号分隔的图片字符串转换为URL列表
     *
     * <p>数据库中images字段存储格式：uuid1.jpg,uuid2.png,uuid3.jpg
     * 转换后：["/upload/uuid1.jpg", "/upload/uuid2.png", "/upload/uuid3.jpg"]</p>
     *
     * @param imagesStr 逗号分隔的图片文件名字符串
     * @return 图片URL列表，如果为空则返回空列表
     */
    private List<String> parseImages(String imagesStr) {
        if (imagesStr == null || imagesStr.trim().isEmpty()) {
            return Collections.emptyList();
        }
        // 按逗号分割，过滤空串，转换为完整URL
        return Arrays.stream(imagesStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(FileUtil::toUrl)
                .collect(Collectors.toList());
    }

    /**
     * 删除缓存并重试（缓存一致性过渡方案）
     *
     * <p>写操作后同步删除Redis缓存，如果删除失败则重试最多3次。
     * 这是RocketMQ事务消息方案引入前的过渡方案。</p>
     *
     * @param cacheKey 缓存Key
     */
    private void deleteCacheWithRetry(String cacheKey) {
        for (int i = 0; i < 3; i++) {
            try {
                if (RedisUtil.del(cacheKey) >= 1) {
                    log.info("缓存删除成功: key={}", cacheKey);
                    return;
                }
                log.warn("缓存删除返回0，重试第{}次: key={}", i + 1, cacheKey);
            } catch (Exception e) {
                log.warn("缓存删除异常，重试第{}次: key={}, error={}", i + 1, cacheKey, e.getMessage());
            }
        }
        log.error("缓存删除失败，3次重试均未成功: key={}", cacheKey);
    }
}

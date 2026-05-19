package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.vo.PostVO;

import java.util.List;

/**
 * 动态服务接口
 *
 * @author FantasticName
 */
public interface PostService {

    /**
     * 发布动态
     *
     * @param userId  发布者ID
     * @param content 动态内容
     * @param images  图片文件名（逗号分隔）
     * @return 动态VO
     */
    PostVO create(Long userId, String content, String images);

    /**
     * 获取动态详情
     *
     * @param postId         动态ID
     * @param currentUserId  当前登录用户ID（可为null，用于判断是否已点赞）
     * @return 动态VO
     */
    PostVO getDetail(Long postId, Long currentUserId);

    /**
     * 获取用户动态列表（普通分页）
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页数量
     * @return 动态VO列表
     */
    List<PostVO> listByUser(Long userId, int page, int size);

    /**
     * 获取用户动态列表（游标分页）
     *
     * <p>游标分页比普通分页更适合瀑布流场景，避免数据重复或遗漏。</p>
     *
     * @param userId 用户ID
     * @param cursor 游标（上一页最后一条的created_at，首次请求传null）
     * @param size   每页数量
     * @return 动态VO列表
     */
    List<PostVO> listByUserCursor(Long userId, String cursor, int size);

    /**
     * 获取用户动态总数
     *
     * @param userId 用户ID
     * @return 总数
     */
    int countByUser(Long userId);

    /**
     * 删除动态
     *
     * @param postId 动态ID
     * @param userId 操作用户ID
     */
    void delete(Long postId, Long userId);

    /**
     * 编辑动态
     *
     * @param postId         动态ID
     * @param userId         操作用户ID
     * @param content        新的文字内容
     * @param existingImages 保留的已有图片文件名（逗号分隔）
     * @param newImages      新上传的图片文件名（逗号分隔）
     * @return 更新后的动态VO
     */
    PostVO update(Long postId, Long userId, String content, String existingImages, String newImages);
}

package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.vo.VideoVO;

import java.util.List;

/**
 * 视频服务接口
 *
 * @author FantasticName
 */
public interface VideoService {

    /**
     * 发布视频
     *
     * @param userId      发布者ID
     * @param title       标题
     * @param description 简介
     * @param coverUrl    封面URL
     * @param videoUrl    视频URL
     * @param category    分区
     * @return 视频VO
     */
    VideoVO publish(Long userId, String title, String description, String coverUrl, String videoUrl, String category);

    /**
     * 获取视频详情
     *
     * @param videoId      视频ID
     * @param currentUserId 当前用户ID（可为null）
     * @return 视频VO
     */
    VideoVO getDetail(Long videoId, Long currentUserId);

    /**
     * 获取视频列表（分页）
     *
     * @param category 分区（null表示全部）
     * @param page     页码
     * @param size     每页数量
     * @return 视频VO列表
     */
    List<VideoVO> list(String category, int page, int size);

    /**
     * 获取用户发布的视频列表
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页数量
     * @return 视频VO列表
     */
    List<VideoVO> listByUser(Long userId, int page, int size);

    /**
     * 获取视频总数
     *
     * @param category 分区（null表示全部）
     * @return 总数
     */
    int count(String category);

    /**
     * 获取用户发布的视频总数
     *
     * @param userId 用户ID
     * @return 总数
     */
    int countByUser(Long userId);

    /**
     * 删除视频（下架）
     *
     * @param videoId 视频ID
     * @param userId  当前用户ID
     */
    void deleteVideo(Long videoId, Long userId);
}

package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.vo.SearchResultVO;

import java.util.List;
import java.util.Map;

/**
 * 搜索服务接口
 *
 * @author FantasticName
 */
public interface SearchService {

    /**
     * 统一搜索：根据关键词搜索视频、动态、用户
     *
     * @param keyword 搜索关键词
     * @param page    页码（从1开始）
     * @param size    每页数量
     * @return Map: total(视频数、动态数、用户数) + list(SearchResultVO列表)
     */
    Map<String, Object> search(String keyword, int page, int size);

    /**
     * 单独搜索视频
     *
     * @param keyword 搜索关键词
     * @param page    页码
     * @param size    每页数量
     * @return Map: total + list
     */
    Map<String, Object> searchVideos(String keyword, int page, int size);

    /**
     * 单独搜索动态
     *
     * @param keyword 搜索关键词
     * @param page    页码
     * @param size    每页数量
     * @return Map: total + list
     */
    Map<String, Object> searchPosts(String keyword, int page, int size);

    /**
     * 单独搜索用户
     *
     * @param keyword 搜索关键词
     * @param page    页码
     * @param size    每页数量
     * @return Map: total + list
     */
    Map<String, Object> searchUsers(String keyword, int page, int size);
}
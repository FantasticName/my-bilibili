package io.github.fantasticname.mybilibili.controller;

import io.github.fantasticname.mybilibili.annotation.Autowired;
import io.github.fantasticname.mybilibili.annotation.GetMapping;
import io.github.fantasticname.mybilibili.annotation.RequestMapping;
import io.github.fantasticname.mybilibili.annotation.RequestParam;
import io.github.fantasticname.mybilibili.annotation.RestController;
import io.github.fantasticname.mybilibili.common.Result;
import io.github.fantasticname.mybilibili.service.SearchService;

import java.util.Map;

/**
 * 搜索控制器
 *
 * @author FantasticName
 */
@RestController
@RequestMapping("/search")
public class SearchController {

    @Autowired
    private SearchService searchService;

    /**
     * 综合搜索：同时搜索视频、动态、用户
     */
    @GetMapping("/all")
    public Result<Map<String, Object>> searchAll(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Map<String, Object> result = searchService.search(keyword.trim(), page, size);
        return Result.success(result);
    }

    /**
     * 只搜索视频
     */
    @GetMapping("/videos")
    public Result<Map<String, Object>> searchVideos(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Map<String, Object> result = searchService.searchVideos(keyword.trim(), page, size);
        return Result.success(result);
    }

    /**
     * 只搜索动态
     */
    @GetMapping("/posts")
    public Result<Map<String, Object>> searchPosts(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Map<String, Object> result = searchService.searchPosts(keyword.trim(), page, size);
        return Result.success(result);
    }

    /**
     * 只搜索用户
     */
    @GetMapping("/users")
    public Result<Map<String, Object>> searchUsers(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Map<String, Object> result = searchService.searchUsers(keyword.trim(), page, size);
        return Result.success(result);
    }
}
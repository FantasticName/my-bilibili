package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.annotation.Autowired;
import io.github.fantasticname.mybilibili.annotation.Service;
import io.github.fantasticname.mybilibili.dao.PostDao;
import io.github.fantasticname.mybilibili.dao.UserDao;
import io.github.fantasticname.mybilibili.dao.VideoDao;
import io.github.fantasticname.mybilibili.entity.Post;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.entity.Video;
import io.github.fantasticname.mybilibili.util.FileUtil;
import io.github.fantasticname.mybilibili.vo.SearchResultVO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 搜索服务实现类
 *
 * @author FantasticName
 */
@Service
public class SearchServiceImpl implements SearchService {

    private static final Logger log = Logger.getLogger(SearchServiceImpl.class.getName());

    @Autowired
    private VideoDao videoDao;

    @Autowired
    private PostDao postDao;

    @Autowired
    private UserDao userDao;

    @Override
    public Map<String, Object> search(String keyword, int page, int size) {
        Map<String, Object> result = new HashMap<>();
        List<SearchResultVO> list = new ArrayList<>();

        int offset = (page - 1) * size;

        Map<String, Object> videoResult = searchVideos(keyword, page, size);
        Map<String, Object> postResult = searchPosts(keyword, page, size);
        Map<String, Object> userResult = searchUsers(keyword, page, size);

        result.put("videoTotal", videoResult.get("total"));
        result.put("postTotal", postResult.get("total"));
        result.put("userTotal", userResult.get("total"));

        @SuppressWarnings("unchecked")
        List<SearchResultVO> videos = (List<SearchResultVO>) videoResult.get("list");
        @SuppressWarnings("unchecked")
        List<SearchResultVO> posts = (List<SearchResultVO>) postResult.get("list");
        @SuppressWarnings("unchecked")
        List<SearchResultVO> users = (List<SearchResultVO>) userResult.get("list");

        if (videos != null) {
            list.addAll(videos);
        }
        if (posts != null) {
            list.addAll(posts);
        }
        if (users != null) {
            list.addAll(users);
        }

        result.put("list", list);
        return result;
    }

    @Override
    public Map<String, Object> searchVideos(String keyword, int page, int size) {
        Map<String, Object> result = new HashMap<>();
        int offset = (page - 1) * size;

        int total = videoDao.countByTitle(keyword);
        List<Video> videos = videoDao.searchByTitle(keyword, offset, size);

        List<SearchResultVO> voList = new ArrayList<>();
        for (Video video : videos) {
            SearchResultVO vo = new SearchResultVO();
            vo.setType("video");
            vo.setId(video.getId());
            vo.setTitle(video.getTitle());
            vo.setCover(FileUtil.toUrl(video.getCoverUrl()));
            vo.setDescription(video.getDescription());
            vo.setCategory(video.getCategory());
            vo.setUserId(video.getUserId());
            vo.setViewCount(video.getViewCount() != null ? video.getViewCount() : 0L);
            vo.setLikeCount(video.getLikeCount() != null ? video.getLikeCount() : 0L);
            vo.setCreatedAt(video.getCreatedAt());

            User user = userDao.findById(video.getUserId());
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setAvatar(FileUtil.toUrl(user.getAvatar()));
            }
            voList.add(vo);
        }

        result.put("total", total);
        result.put("list", voList);
        return result;
    }

    @Override
    public Map<String, Object> searchPosts(String keyword, int page, int size) {
        Map<String, Object> result = new HashMap<>();
        int offset = (page - 1) * size;

        int total = postDao.countByContent(keyword);
        List<Post> posts = postDao.searchByContent(keyword, offset, size);

        List<SearchResultVO> voList = new ArrayList<>();
        for (Post post : posts) {
            SearchResultVO vo = new SearchResultVO();
            vo.setType("post");
            vo.setId(post.getId());
            vo.setTitle(post.getContent());
            vo.setUserId(post.getUserId());
            vo.setLikeCount(post.getLikeCount() != null ? (long) post.getLikeCount() : 0L);
            vo.setCreatedAt(post.getCreatedAt());

            User user = userDao.findById(post.getUserId());
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setAvatar(FileUtil.toUrl(user.getAvatar()));
            }
            voList.add(vo);
        }

        result.put("total", total);
        result.put("list", voList);
        return result;
    }

    @Override
    public Map<String, Object> searchUsers(String keyword, int page, int size) {
        Map<String, Object> result = new HashMap<>();
        int offset = (page - 1) * size;

        int total = userDao.countByNickname(keyword);
        List<User> users = userDao.searchByNickname(keyword, offset, size);

        List<SearchResultVO> voList = new ArrayList<>();
        for (User user : users) {
            SearchResultVO vo = new SearchResultVO();
            vo.setType("user");
            vo.setId(user.getId());
            vo.setNickname(user.getNickname());
            vo.setAvatar(FileUtil.toUrl(user.getAvatar()));
            vo.setCreatedAt(user.getCreatedAt());
            voList.add(vo);
        }

        result.put("total", total);
        result.put("list", voList);
        return result;
    }
}
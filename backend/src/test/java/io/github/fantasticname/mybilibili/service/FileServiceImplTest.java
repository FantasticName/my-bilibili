package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.util.FileUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FileService 单元测试
 *
 * @author FantasticName
 */
@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    @InjectMocks
    private FileServiceImpl fileService;

    @Test
    @DisplayName("上传视频 — 空文件应抛异常")
    void uploadVideoEmptyFile() {
        assertThrows(IllegalArgumentException.class,
                () -> fileService.uploadVideo(new byte[0], "test.mp4", "video/mp4"),
                "视频文件不能为空");
    }

    @Test
    @DisplayName("上传视频 — 超过500MB应抛异常")
    void uploadVideoTooLarge() {
        byte[] largeFile = new byte[500 * 1024 * 1024 + 1];
        assertThrows(IllegalArgumentException.class,
                () -> fileService.uploadVideo(largeFile, "large.mp4", "video/mp4"),
                "视频文件大小不能超过500MB");
    }

    @Test
    @DisplayName("上传视频 — 不支持的后缀应抛异常")
    void uploadVideoInvalidExtension() {
        byte[] data = new byte[1024];
        assertThrows(IllegalArgumentException.class,
                () -> fileService.uploadVideo(data, "test.exe", "application/octet-stream"),
                "不支持的视频格式");
    }

    @Test
    @DisplayName("上传视频 — MP4正常上传")
    void uploadVideoSuccess() {
        try (MockedStatic<FileUtil> mockedFileUtil = mockStatic(FileUtil.class)) {
            mockedFileUtil.when(() -> FileUtil.saveFile(any(byte[].class), eq("mp4")))
                    .thenReturn("abc123.mp4");

            String result = fileService.uploadVideo(new byte[1024], "我的视频.mp4", "video/mp4");
            assertNotNull(result);
            assertEquals("abc123.mp4", result);
        }
    }

    @Test
    @DisplayName("上传视频 — 无文件名但有ContentType")
    void uploadVideoWithoutFilenameButWithContentType() {
        try (MockedStatic<FileUtil> mockedFileUtil = mockStatic(FileUtil.class)) {
            mockedFileUtil.when(() -> FileUtil.saveFile(any(byte[].class), eq("mp4")))
                    .thenReturn("video123.mp4");

            String result = fileService.uploadVideo(new byte[1024], null, "video/mp4");
            assertNotNull(result);
            assertEquals("video123.mp4", result);
        }
    }

    @Test
    @DisplayName("上传封面 — 空文件应抛异常")
    void uploadCoverEmptyFile() {
        assertThrows(IllegalArgumentException.class,
                () -> fileService.uploadCover(new byte[0], "cover.jpg", "image/jpeg"),
                "封面图片不能为空");
    }

    @Test
    @DisplayName("上传封面 — 超过5MB应抛异常")
    void uploadCoverTooLarge() {
        byte[] largeFile = new byte[5 * 1024 * 1024 + 1];
        assertThrows(IllegalArgumentException.class,
                () -> fileService.uploadCover(largeFile, "large.jpg", "image/jpeg"),
                "封面图片大小不能超过5MB");
    }

    @Test
    @DisplayName("上传封面 — 不支持的后缀应抛异常")
    void uploadCoverInvalidExtension() {
        byte[] data = new byte[1024];
        assertThrows(IllegalArgumentException.class,
                () -> fileService.uploadCover(data, "test.bmp", "image/bmp"),
                "不支持的图片格式");
    }

    @Test
    @DisplayName("上传封面 — PNG正常上传")
    void uploadCoverSuccess() {
        try (MockedStatic<FileUtil> mockedFileUtil = mockStatic(FileUtil.class)) {
            mockedFileUtil.when(() -> FileUtil.saveFile(any(byte[].class), eq("png")))
                    .thenReturn("def456.png");

            String result = fileService.uploadCover(new byte[1024], "封面.png", "image/png");
            assertNotNull(result);
            assertEquals("def456.png", result);
        }
    }

    @Test
    @DisplayName("上传封面 — 无文件名但有ContentType")
    void uploadCoverWithoutFilenameButWithContentType() {
        try (MockedStatic<FileUtil> mockedFileUtil = mockStatic(FileUtil.class)) {
            mockedFileUtil.when(() -> FileUtil.saveFile(any(byte[].class), eq("jpg")))
                    .thenReturn("cover789.jpg");

            String result = fileService.uploadCover(new byte[1024], null, "image/jpeg");
            assertNotNull(result);
            assertEquals("cover789.jpg", result);
        }
    }
}
package io.github.fantasticname.mybilibili.controller;

import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.github.fantasticname.mybilibili.common.Result;
import io.github.fantasticname.mybilibili.context.UserContext;
import io.github.fantasticname.mybilibili.dto.LoginDTO;
import io.github.fantasticname.mybilibili.dto.RegisterDTO;
import io.github.fantasticname.mybilibili.dto.UpdateProfileDTO;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.service.UserServiceImpl;
import io.github.fantasticname.mybilibili.util.IdempotentUtil;
import io.github.fantasticname.mybilibili.util.RedisUtil;
import io.github.fantasticname.mybilibili.vo.LoginVO;
import io.github.fantasticname.mybilibili.vo.UserVO;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserServiceImpl userService;

    @InjectMocks
    private UserController userController;

    private MockedStatic<RedisUtil> redisUtilMock;

    private MockedStatic<IdempotentUtil> idempotentUtilMock;

    @BeforeEach
    void setUp() {
        redisUtilMock = mockStatic(RedisUtil.class);
        redisUtilMock.when(() -> RedisUtil.removeToken(anyString())).thenAnswer(inv -> null);
        idempotentUtilMock = mockStatic(IdempotentUtil.class);
        idempotentUtilMock.when(IdempotentUtil::generateToken).thenReturn("test-uuid-token");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        redisUtilMock.close();
        idempotentUtilMock.close();
    }

    private User createTestUser() {
        User user = new User();
        user.setId(1L);
        user.setPhone("13812345678");
        user.setNickname("testUser");
        user.setRole(0);
        user.setStatus(0);
        return user;
    }

    private UserVO createTestUserVO() {
        UserVO vo = new UserVO();
        vo.setId(1L);
        vo.setPhone("13812345678");
        vo.setNickname("testUser");
        vo.setRole(0);
        return vo;
    }

    // ==================== register 测试 ====================

    @Nested
    @DisplayName("register 接口测试")
    class RegisterTests {

        @Test
        @DisplayName("正常注册")
        void register_success() {
            RegisterDTO dto = new RegisterDTO();
            dto.setPhone("13812345678");
            dto.setPassword("123456");
            dto.setConfirmPassword("123456");
            dto.setNickname("testUser");

            UserVO userVO = createTestUserVO();
            when(userService.register(any(RegisterDTO.class))).thenReturn(userVO);

            Result<UserVO> result = userController.register(dto);

            assertNotNull(result);
            assertEquals(0, result.getCode());
            assertNotNull(result.getData());
            assertEquals("13812345678", result.getData().getPhone());
            verify(userService).register(any(RegisterDTO.class));
        }

        @Test
        @DisplayName("注册 - 参数异常传播（被SentinelUtil包装为RuntimeException）")
        void register_businessException_propagates() {
            RegisterDTO dto = new RegisterDTO();
            when(userService.register(any(RegisterDTO.class)))
                    .thenThrow(new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED));

            // register方法被SentinelUtil.executeWithProtection包裹，
            // BusinessException会被包装为RuntimeException
            RuntimeException exception = assertThrows(RuntimeException.class, () -> userController.register(dto));
            assertTrue(exception.getMessage().contains("业务执行异常"));
        }
    }

    // ==================== login 接口测试 ====================

    @Nested
    @DisplayName("login 接口测试")
    class LoginTests {

        @Test
        @DisplayName("正常登录")
        void login_success() {
            LoginDTO dto = new LoginDTO();
            dto.setPhone("13812345678");
            dto.setPassword("123456");

            LoginVO loginVO = new LoginVO();
            loginVO.setToken("test-token");
            loginVO.setExpiresIn(1800);
            loginVO.setUser(createTestUserVO());

            when(userService.login(any(LoginDTO.class))).thenReturn(loginVO);

            Result<LoginVO> result = userController.login(dto);

            assertNotNull(result);
            assertEquals(0, result.getCode());
            assertNotNull(result.getData());
            assertEquals("test-token", result.getData().getToken());
            assertEquals(1800, result.getData().getExpiresIn());
            verify(userService).login(any(LoginDTO.class));
        }

        @Test
        @DisplayName("登录 - 密码错误传播")
        void login_wrongPassword_propagates() {
            LoginDTO dto = new LoginDTO();
            when(userService.login(any(LoginDTO.class)))
                    .thenThrow(new BusinessException(ErrorCode.PHONE_OR_PASSWORD_ERROR));

            BusinessException exception = assertThrows(BusinessException.class, () -> userController.login(dto));
            assertEquals(ErrorCode.PHONE_OR_PASSWORD_ERROR.getCode(), exception.getCode());
        }
    }

    // ==================== getProfile 接口测试 ====================

    @Nested
    @DisplayName("getProfile 接口测试")
    class GetProfileTests {

        @Test
        @DisplayName("正常查看个人信息")
        void getProfile_success() {
            User user = createTestUser();
            UserContext.set(user);

            UserVO userVO = createTestUserVO();
            when(userService.getProfile(1L)).thenReturn(userVO);

            Result<UserVO> result = userController.getProfile();

            assertNotNull(result);
            assertEquals(0, result.getCode());
            assertNotNull(result.getData());
            assertEquals(1L, result.getData().getId());
            verify(userService).getProfile(1L);
        }
    }

    // ==================== updateProfile 接口测试 ====================

    @Nested
    @DisplayName("updateProfile 接口测试")
    class UpdateProfileTests {

        @Test
        @DisplayName("正常修改个人信息")
        void updateProfile_success() {
            User user = createTestUser();
            UserContext.set(user);

            UpdateProfileDTO dto = new UpdateProfileDTO();
            dto.setNickname("newName");

            UserVO updatedVO = createTestUserVO();
            updatedVO.setNickname("newName");
            when(userService.updateProfile(eq(1L), any(UpdateProfileDTO.class))).thenReturn(updatedVO);

            Result<UserVO> result = userController.updateProfile(dto);

            assertNotNull(result);
            assertEquals(0, result.getCode());
            assertEquals("newName", result.getData().getNickname());
            verify(userService).updateProfile(eq(1L), any(UpdateProfileDTO.class));
        }
    }

    // ==================== logout 接口测试 ====================

    @Nested
    @DisplayName("logout 接口测试")
    class LogoutTests {

        @Test
        @DisplayName("正常退出登录")
        void logout_success() {
            User user = createTestUser();
            UserContext.set(user);

            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn("Bearer test-token");

            Result<Void> result = userController.logout(request);

            assertNotNull(result);
            assertEquals(0, result.getCode());
            redisUtilMock.verify(() -> RedisUtil.removeToken("test-token"));
        }

        @Test
        @DisplayName("退出登录 - 无Authorization头")
        void logout_noAuthHeader_stillSuccess() {
            User user = createTestUser();
            UserContext.set(user);

            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn(null);

            Result<Void> result = userController.logout(request);

            assertNotNull(result);
            assertEquals(0, result.getCode());
            redisUtilMock.verify(() -> RedisUtil.removeToken(anyString()), never());
        }

        @Test
        @DisplayName("退出登录 - Token无Bearer前缀")
        void logout_tokenWithoutBearer_stillSuccess() {
            User user = createTestUser();
            UserContext.set(user);

            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn("raw-token");

            Result<Void> result = userController.logout(request);

            assertNotNull(result);
            assertEquals(0, result.getCode());
            redisUtilMock.verify(() -> RedisUtil.removeToken(anyString()), never());
        }
    }

    // ==================== getToken 幂等性Token接口测试 ====================

    @Nested
    @DisplayName("getToken 接口测试")
    class GetTokenTests {

        @Test
        @DisplayName("正常获取幂等性Token")
        void getToken_success() {
            Result<Map<String, Object>> result = userController.getToken();

            assertNotNull(result);
            assertEquals(0, result.getCode());
            assertNotNull(result.getData());
            assertEquals("test-uuid-token", result.getData().get("token"));
            assertEquals(300, result.getData().get("ttl"));
            idempotentUtilMock.verify(IdempotentUtil::generateToken);
        }
    }
}

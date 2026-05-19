package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.github.fantasticname.mybilibili.dao.RoleDao;
import io.github.fantasticname.mybilibili.dao.UserDao;
import io.github.fantasticname.mybilibili.dao.UserRoleDao;
import io.github.fantasticname.mybilibili.dto.LoginDTO;
import io.github.fantasticname.mybilibili.dto.RegisterDTO;
import io.github.fantasticname.mybilibili.dto.UpdateProfileDTO;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.util.JwtUtil;
import io.github.fantasticname.mybilibili.util.RedisUtil;
import io.github.fantasticname.mybilibili.vo.LoginVO;
import io.github.fantasticname.mybilibili.vo.UserVO;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mindrot.jbcrypt.BCrypt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserDao userDao;

    @Mock
    private RoleDao roleDao;

    @Mock
    private UserRoleDao userRoleDao;

    @InjectMocks
    private UserServiceImpl userService;

    private MockedStatic<RedisUtil> redisUtilMock;
    private MockedStatic<JwtUtil> jwtUtilMock;
    private MockedStatic<BCrypt> bcryptMock;

    @BeforeEach
    void setUp() {
        redisUtilMock = mockStatic(RedisUtil.class);
        jwtUtilMock = mockStatic(JwtUtil.class);
        bcryptMock = mockStatic(BCrypt.class);

        redisUtilMock.when(() -> RedisUtil.saveToken(anyString(), anyString())).thenAnswer(inv -> null);
        redisUtilMock.when(() -> RedisUtil.getTokenTtl()).thenReturn(1800);
        redisUtilMock.when(() -> RedisUtil.refreshTokenTtl(anyString())).thenReturn(true);
        redisUtilMock.when(() -> RedisUtil.getUserByToken(anyString())).thenReturn(null);
        redisUtilMock.when(() -> RedisUtil.removeToken(anyString())).thenAnswer(inv -> null);

        jwtUtilMock.when(() -> JwtUtil.generateTokenWithoutExp(anyMap())).thenReturn("mocked-jwt-token");

        bcryptMock.when(() -> BCrypt.checkpw(eq("123456"), anyString())).thenReturn(true);
        bcryptMock.when(() -> BCrypt.checkpw(eq("wrongpassword"), anyString())).thenReturn(false);
        bcryptMock.when(() -> BCrypt.gensalt()).thenReturn("$2a$10$mockedsaltvalue");
        bcryptMock.when(() -> BCrypt.hashpw(anyString(), anyString())).thenReturn("$2a$10$mockedhashvalue");

        try {
            java.lang.reflect.Field roleDaoField = UserServiceImpl.class.getDeclaredField("roleDao");
            roleDaoField.setAccessible(true);
            roleDaoField.set(userService, roleDao);

            java.lang.reflect.Field userRoleDaoField = UserServiceImpl.class.getDeclaredField("userRoleDao");
            userRoleDaoField.setAccessible(true);
            userRoleDaoField.set(userService, userRoleDao);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void tearDown() {
        redisUtilMock.close();
        jwtUtilMock.close();
        bcryptMock.close();
    }

    private User createTestUser(Long id, String phone, String nickname, Integer role, Integer status) {
        User user = new User();
        user.setId(id);
        user.setPhone(phone);
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuvwxABCDEFGHIJ");
        user.setNickname(nickname);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    private RegisterDTO createValidRegisterDTO() {
        RegisterDTO dto = new RegisterDTO();
        dto.setPhone("13812345678");
        dto.setPassword("123456");
        dto.setConfirmPassword("123456");
        dto.setNickname("testUser");
        dto.setRole(0);
        return dto;
    }

    private LoginDTO createValidLoginDTO() {
        LoginDTO dto = new LoginDTO();
        dto.setPhone("13812345678");
        dto.setPassword("123456");
        return dto;
    }

    // ==================== getById 测试 ====================

    @Nested
    @DisplayName("getById 测试")
    class GetByIdTests {

        @Test
        @DisplayName("正常查询 - 用户存在")
        void getById_userExists_returnsUser() {
            User user = createTestUser(1L, "13812345678", "testUser", 0, 0);
            when(userDao.findById(1L)).thenReturn(user);

            User result = userService.getById(1L);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals("13812345678", result.getPhone());
            verify(userDao).findById(1L);
        }

        @Test
        @DisplayName("正常查询 - 用户不存在")
        void getById_userNotExists_returnsNull() {
            when(userDao.findById(999L)).thenReturn(null);

            User result = userService.getById(999L);

            assertNull(result);
            verify(userDao).findById(999L);
        }
    }

    // ==================== register 测试 ====================

    @Nested
    @DisplayName("register 测试")
    class RegisterTests {

        @Test
        @DisplayName("正常注册 - 普通用户")
        void register_normalUser_success() {
            RegisterDTO dto = createValidRegisterDTO();
            when(userDao.findByPhone("13812345678")).thenReturn(null);
            when(userDao.insert(any(User.class))).thenReturn(1L);
            User insertedUser = createTestUser(1L, "13812345678", "testUser", 0, 0);
            when(userDao.findById(1L)).thenReturn(insertedUser);

            UserVO result = userService.register(dto);

            assertNotNull(result);
            assertEquals("13812345678", result.getPhone());
            assertEquals("testUser", result.getNickname());
            verify(userDao).insert(argThat(user ->
                    user.getPhone().equals("13812345678") &&
                            user.getNickname().equals("testUser") &&
                            user.getRole() == 0
            ));
            verify(userRoleDao).insert(any());
        }

        @Test
        @DisplayName("注册失败 - 手机号已注册")
        void register_phoneAlreadyRegistered_throwsException() {
            RegisterDTO dto = createValidRegisterDTO();
            User existingUser = createTestUser(1L, "13812345678", "existingUser", 0, 0);
            when(userDao.findByPhone("13812345678")).thenReturn(existingUser);

            BusinessException exception = assertThrows(BusinessException.class, () -> userService.register(dto));
            assertEquals(ErrorCode.PHONE_ALREADY_REGISTERED.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("注册失败 - 手机号为空")
        void register_emptyPhone_throwsException() {
            RegisterDTO dto = createValidRegisterDTO();
            dto.setPhone(null);

            BusinessException exception = assertThrows(BusinessException.class, () -> userService.register(dto));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("注册失败 - 手机号格式错误")
        void register_invalidPhone_throwsException() {
            RegisterDTO dto = createValidRegisterDTO();
            dto.setPhone("123");

            BusinessException exception = assertThrows(BusinessException.class, () -> userService.register(dto));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("注册失败 - 密码为空")
        void register_emptyPassword_throwsException() {
            RegisterDTO dto = createValidRegisterDTO();
            dto.setPassword(null);

            BusinessException exception = assertThrows(BusinessException.class, () -> userService.register(dto));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("注册失败 - 两次密码不一致")
        void register_passwordNotMatch_throwsException() {
            RegisterDTO dto = createValidRegisterDTO();
            dto.setConfirmPassword("654321");

            BusinessException exception = assertThrows(BusinessException.class, () -> userService.register(dto));
            assertEquals(ErrorCode.PASSWORD_NOT_MATCH.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("注册失败 - 昵称为空")
        void register_emptyNickname_throwsException() {
            RegisterDTO dto = createValidRegisterDTO();
            dto.setNickname(null);

            BusinessException exception = assertThrows(BusinessException.class, () -> userService.register(dto));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("注册失败 - 管理员邀请码错误")
        void register_adminWrongInviteCode_throwsException() {
            RegisterDTO dto = createValidRegisterDTO();
            dto.setRole(2);
            dto.setInviteCode("wrong_code");

            BusinessException exception = assertThrows(BusinessException.class, () -> userService.register(dto));
            assertEquals(ErrorCode.INVITE_CODE_ERROR.getCode(), exception.getCode());
        }
    }

    // ==================== login 测试 ====================

    @Nested
    @DisplayName("login 测试")
    class LoginTests {

        @Test
        @DisplayName("正常登录")
        void login_success() {
            LoginDTO dto = createValidLoginDTO();
            User user = createTestUser(1L, "13812345678", "testUser", 0, 0);
            when(userDao.findByPhone("13812345678")).thenReturn(user);

            LoginVO result = userService.login(dto);

            assertNotNull(result);
            assertEquals("mocked-jwt-token", result.getToken());
            assertEquals(1800, result.getExpiresIn());
            assertNotNull(result.getUser());
            assertEquals("13812345678", result.getUser().getPhone());
            verify(userDao).findByPhone("13812345678");
        }

        @Test
        @DisplayName("登录失败 - 手机号格式错误")
        void login_invalidPhone_throwsException() {
            LoginDTO dto = createValidLoginDTO();
            dto.setPhone("123");

            BusinessException exception = assertThrows(BusinessException.class, () -> userService.login(dto));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("登录失败 - 密码格式错误")
        void login_invalidPassword_throwsException() {
            LoginDTO dto = createValidLoginDTO();
            dto.setPassword("12");

            BusinessException exception = assertThrows(BusinessException.class, () -> userService.login(dto));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("登录失败 - 手机号不存在")
        void login_phoneNotExist_throwsException() {
            LoginDTO dto = createValidLoginDTO();
            when(userDao.findByPhone("13812345678")).thenReturn(null);

            BusinessException exception = assertThrows(BusinessException.class, () -> userService.login(dto));
            assertEquals(ErrorCode.PHONE_OR_PASSWORD_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("登录失败 - 账号被封禁")
        void login_accountBanned_throwsException() {
            LoginDTO dto = createValidLoginDTO();
            User user = createTestUser(1L, "13812345678", "testUser", 0, 1);
            when(userDao.findByPhone("13812345678")).thenReturn(user);

            BusinessException exception = assertThrows(BusinessException.class, () -> userService.login(dto));
            assertEquals(ErrorCode.ACCOUNT_BANNED.getCode(), exception.getCode());
        }
    }

    // ==================== getProfile 测试 ====================

    @Nested
    @DisplayName("getProfile 测试")
    class GetProfileTests {

        @Test
        @DisplayName("正常查询个人信息")
        void getProfile_userExists_returnsUserVO() {
            User user = createTestUser(1L, "13812345678", "testUser", 0, 0);
            when(userDao.findById(1L)).thenReturn(user);

            UserVO result = userService.getProfile(1L);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals("testUser", result.getNickname());
        }

        @Test
        @DisplayName("查询个人信息 - 用户不存在")
        void getProfile_userNotExists_throwsException() {
            when(userDao.findById(999L)).thenReturn(null);

            BusinessException exception = assertThrows(BusinessException.class, () -> userService.getProfile(999L));
            assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
        }
    }

    // ==================== updateProfile 测试 ====================

    @Nested
    @DisplayName("updateProfile 测试")
    class UpdateProfileTests {

        @Test
        @DisplayName("修改昵称")
        void updateProfile_nickname_success() {
            User user = createTestUser(1L, "13812345678", "oldName", 0, 0);
            when(userDao.findById(1L)).thenReturn(user);
            doNothing().when(userDao).update(any(User.class));

            UpdateProfileDTO dto = new UpdateProfileDTO();
            dto.setNickname("newName");

            UserVO result = userService.updateProfile(1L, dto);

            assertNotNull(result);
            verify(userDao).update(argThat(u -> u.getNickname().equals("newName")));
        }

        @Test
        @DisplayName("修改昵称 - 格式错误")
        void updateProfile_invalidNickname_throwsException() {
            User user = createTestUser(1L, "13812345678", "oldName", 0, 0);
            when(userDao.findById(1L)).thenReturn(user);

            UpdateProfileDTO dto = new UpdateProfileDTO();
            dto.setNickname("a".repeat(21));

            BusinessException exception = assertThrows(BusinessException.class, () -> userService.updateProfile(1L, dto));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("修改手机号 - 旧密码错误")
        void updateProfile_wrongOldPassword_throwsException() {
            User user = createTestUser(1L, "13812345678", "testUser", 0, 0);
            when(userDao.findById(1L)).thenReturn(user);

            UpdateProfileDTO dto = new UpdateProfileDTO();
            dto.setNewPhone("13987654321");
            dto.setOldPassword("wrongpassword");

            BusinessException exception = assertThrows(BusinessException.class, () -> userService.updateProfile(1L, dto));
            assertEquals(ErrorCode.OLD_PASSWORD_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("修改密码 - 新密码格式错误")
        void updateProfile_invalidNewPassword_throwsException() {
            User user = createTestUser(1L, "13812345678", "testUser", 0, 0);
            when(userDao.findById(1L)).thenReturn(user);

            UpdateProfileDTO dto = new UpdateProfileDTO();
            dto.setNewPassword("12");
            dto.setConfirmNewPassword("12");
            dto.setOldPassword("123456");

            BusinessException exception = assertThrows(BusinessException.class, () -> userService.updateProfile(1L, dto));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("修改密码 - 两次新密码不一致")
        void updateProfile_newPasswordNotMatch_throwsException() {
            User user = createTestUser(1L, "13812345678", "testUser", 0, 0);
            when(userDao.findById(1L)).thenReturn(user);

            UpdateProfileDTO dto = new UpdateProfileDTO();
            dto.setNewPassword("654321");
            dto.setConfirmNewPassword("111111");
            dto.setOldPassword("123456");

            BusinessException exception = assertThrows(BusinessException.class, () -> userService.updateProfile(1L, dto));
            assertEquals(ErrorCode.PASSWORD_NOT_MATCH.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("用户不存在时修改个人信息")
        void updateProfile_userNotExists_throwsException() {
            when(userDao.findById(999L)).thenReturn(null);

            UpdateProfileDTO dto = new UpdateProfileDTO();
            dto.setNickname("newName");

            BusinessException exception = assertThrows(BusinessException.class, () -> userService.updateProfile(999L, dto));
            assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
        }
    }
}

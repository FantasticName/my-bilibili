package io.github.fantasticname.mybilibili.util;

import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Map;

/**
 * JWT工具类，提供令牌的生成和解析功能
 *
 * <p>JWT（JSON Web Token）是一种开放标准（RFC 7519），用于在各方之间安全地传输信息。
 * 在本项目中，JWT用于用户登录鉴权：</p>
 * <ul>
 *   <li>用户登录成功后，服务端生成JWT令牌返回给前端</li>
 *   <li>前端在后续请求的Authorization请求头中携带JWT令牌</li>
 *   <li>服务端解析JWT令牌，验证用户身份</li>
 * </ul>
 *
 * <p>JWT的结构由三部分组成：Header.Payload.Signature</p>
 * <ul>
 *   <li>Header：声明类型和加密算法</li>
 *   <li>Payload：存放有效信息（如userId）</li>
 *   <li>Signature：签名，用于验证令牌的完整性和真实性</li>
 * </ul>
 *
 * @author FantasticName
 */
public class JwtUtil {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /**
     * JWT签名密钥
     *
     * <p>密钥用于对JWT进行签名和验证。在实际生产环境中，
     * 密钥应该从配置文件中读取，不应该硬编码在代码中。
     * 这里使用一个固定的密钥字符串，仅用于开发环境。</p>
     */
    private static final String SECRET_KEY = "my-bilibili-secret-key-for-jwt-token-signature-2024";

    /**
     * JWT令牌过期时间（毫秒）
     *
     * <p>设置为24小时（24 * 60 * 60 * 1000 = 86400000毫秒）。
     * 超过这个时间后，令牌将失效，用户需要重新登录。</p>
     */
    private static final long EXPIRATION_TIME = 24 * 60 * 60 * 1000L;

    /**
     * 生成JWT令牌
     *
     * <p>将用户信息（如userId）封装到JWT的Payload中，
     * 设置过期时间，并使用密钥进行签名。</p>
     *
     * <p>生成流程：</p>
     * <ol>
     *   <li>设置Payload中的自定义声明（claims），如userId</li>
     *   <li>设置签发时间（issuedAt）</li>
     *   <li>设置过期时间（expiration）</li>
     *   <li>使用密钥和指定算法进行签名</li>
     * </ol>
     *
     * @param claims 自定义声明，如 {"userId": 123}
     * @return 生成的JWT令牌字符串
     */
    public static String generateToken(Map<String, Object> claims) {
        log.debug("开始生成JWT令牌, claims={}", claims);

        // 1. 获取当前时间，作为签发时间
        Date now = new Date();

        // 2. 计算过期时间 = 当前时间 + 过期时长
        Date expiration = new Date(now.getTime() + EXPIRATION_TIME);

        // 3. 构建JWT令牌
        String token = Jwts.builder()
                // 3.1 设置自定义声明（Payload中的数据）
                .setClaims(claims)
                // 3.2 设置签发时间
                .setIssuedAt(now)
                // 3.3 设置过期时间
                .setExpiration(expiration)
                // 3.4 使用密钥和HS256算法进行签名
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY)
                // 3.5 编码为字符串
                .compact();

        log.info("JWT令牌生成成功, 过期时间={}", expiration);
        return token;
    }

    /**
     * 解析JWT令牌，提取其中的声明信息
     *
     * <p>解析流程：</p>
     * <ol>
     *   <li>使用密钥验证签名，确保令牌未被篡改</li>
     *   <li>检查令牌是否过期</li>
     *   <li>提取Payload中的声明信息</li>
     * </ol>
     *
     * <p>如果令牌无效（签名不匹配、已过期等），会抛出异常。</p>
     *
     * @param token JWT令牌字符串
     * @return 令牌中的声明信息（Claims对象，继承自Map）
     * @throws BusinessException 如果令牌无效或已过期
     */
    public static Map<String, Object> parseToken(String token) {
        log.debug("开始解析JWT令牌");

        try {
            // 1. 使用密钥解析JWT令牌
            //    如果签名不匹配，会抛出SignatureException
            //    如果令牌已过期，会抛出ExpiredJwtException
            Claims claims = Jwts.parser()
                    .setSigningKey(SECRET_KEY)
                    .parseClaimsJws(token)
                    .getBody();

            log.debug("JWT令牌解析成功, claims={}", claims);
            return claims;
        } catch (Exception e) {
            // 2. 解析失败，记录错误日志
            log.error("JWT令牌解析失败: {}", e.getMessage());
            // 3. 抛出业务异常，表示未登录
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "无效的登录凭证");
        }
    }

    /**
     * 验证JWT令牌是否有效
     *
     * <p>尝试解析令牌，如果不抛出异常则表示有效。</p>
     *
     * @param token JWT令牌字符串
     * @return true表示有效，false表示无效
     */
    public static boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            log.debug("JWT令牌验证失败: {}", e.getMessage());
            return false;
        }
    }
}

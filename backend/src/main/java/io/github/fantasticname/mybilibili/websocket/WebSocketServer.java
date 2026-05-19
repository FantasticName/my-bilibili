package io.github.fantasticname.mybilibili.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket服务端点（实时推送通知）
 *
 * <p>WebSocket是一种在单个TCP连接上进行全双工通信的协议。
 * 与HTTP的"请求-响应"模式不同，WebSocket建立连接后，
 * 服务端可以主动向客户端推送消息。</p>
 *
 * <p>在本项目中的应用场景：</p>
 * <ul>
 *   <li>用户收到新关注 → 实时推送"XXX关注了你"</li>
 *   <li>用户收到新评论 → 实时推送"XXX评论了你的动态"</li>
 *   <li>用户收到新点赞 → 实时推送"XXX赞了你的视频"</li>
 *   <li>系统通知 → 实时推送"618优惠券活动开始啦"</li>
 * </ul>
 *
 * <p>连接建立流程：</p>
 * <ol>
 *   <li>前端登录后，建立WebSocket连接：ws://localhost:8080/websocket/notification?userId={userId}</li>
 *   <li>服务端onOpen：将 userId → Session 存入ConcurrentHashMap</li>
 *   <li>服务端sendToUser：查找Session，发送JSON消息</li>
 *   <li>客户端onMessage：收到消息后更新UI（消息小红点、弹窗提醒）</li>
 *   <li>连接断开时onClose：从Map中移除</li>
 * </ol>
 *
 * <p>线程安全：使用ConcurrentHashMap存储Session映射，
 * 高并发下安全读写，不需要加额外的synchronized。</p>
 *
 * <p>注意：Tomcat原生支持WebSocket（javax.websocket），
 * 不需要额外引入Netty等框架。</p>
 *
 * @author FantasticName
 */
@ServerEndpoint("/websocket/notification/{userId}")
public class WebSocketServer {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(WebSocketServer.class);

    /**
     * 用户ID → WebSocket Session 的映射表
     *
     * <p>使用ConcurrentHashMap保证线程安全。
     * 一个用户可以有多个设备同时在线（如手机+电脑），
     * 但本简化实现中一个用户只保留最新的Session。</p>
     */
    private static final Map<Long, Session> userSessions = new ConcurrentHashMap<>();

    /**
     * 当客户端建立WebSocket连接时调用
     *
     * <p>Session是WebSocket的连接对象，可以发送消息、获取连接参数等。
     * 这里把它和用户ID关联起来，存到Map中。</p>
     *
     * @param session WebSocket Session
     * @param userId  用户ID（从路径参数获取）
     */
    @OnOpen
    public void onOpen(Session session, @javax.websocket.server.PathParam("userId") Long userId) {
        // 如果之前已有连接（如另一个设备），先关闭旧连接
        Session oldSession = userSessions.get(userId);
        if (oldSession != null && oldSession.isOpen()) {
            try {
                oldSession.close();
            } catch (IOException e) {
                log.warn("关闭旧WebSocket连接失败: userId={}", userId);
            }
        }

        // 存入Map
        userSessions.put(userId, session);
        log.info("WebSocket连接建立: userId={}, sessionId={}, 当前在线: {}人",
                userId, session.getId(), userSessions.size());
    }

    /**
     * 当客户端断开WebSocket连接时调用
     *
     * @param session WebSocket Session
     * @param userId  用户ID
     */
    @OnClose
    public void onClose(Session session, @javax.websocket.server.PathParam("userId") Long userId) {
        userSessions.remove(userId);
        log.info("WebSocket连接断开: userId={}, sessionId={}, 当前在线: {}人",
                userId, session.getId(), userSessions.size());
    }

    /**
     * 收到客户端消息时调用
     *
     * <p>在本项目中，客户端不主动向WebSocket发消息（业务用HTTP），
     * 但保留此方法，可以做心跳检测等扩展。</p>
     *
     * @param message 客户端消息内容
     * @param session WebSocket Session
     * @param userId  用户ID
     */
    @OnMessage
    public void onMessage(String message, Session session, @javax.websocket.server.PathParam("userId") Long userId) {
        log.debug("收到WebSocket消息: userId={}, message={}", userId, message);
        // 目前客户端不发业务消息，预留扩展（如心跳Ping/Pong）
    }

    /**
     * 连接发生错误时调用
     *
     * @param session WebSocket Session
     * @param error   错误对象
     * @param userId  用户ID
     */
    @OnError
    public void onError(Session session, Throwable error,
                        @javax.websocket.server.PathParam("userId") Long userId) {
        log.error("WebSocket连接错误: userId={}, sessionId={}, error={}",
                userId, session.getId(), error.getMessage());
        userSessions.remove(userId);
    }

    /**
     * 向指定用户发送消息（供外部Service调用）
     *
     * <p>这是一个静态方法，方便NotificationServiceImpl直接调用。
     * 查找该用户的WebSocket Session，如果在线则发送JSON消息。</p>
     *
     * <p>如果用户不在线（没有WebSocket连接），不会抛异常，
     * 消息只存储在MySQL中，用户下次打开消息中心可以看到。</p>
     *
     * @param userId  目标用户ID
     * @param message 消息内容（JSON格式字符串）
     */
    public static void sendToUser(Long userId, String message) {
        Session session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                // 同步发送（WebSocket Session的getBasicRemote是线程安全的）
                session.getBasicRemote().sendText(message);
                log.debug("WebSocket消息已发送: userId={}, message={}", userId, message);
            } catch (IOException e) {
                log.error("WebSocket消息发送失败: userId={}, error={}", userId, e.getMessage());
                // 发送失败时移除Session（可能是连接已断开但没触发onClose）
                userSessions.remove(userId);
            }
        } else {
            log.debug("用户不在线，WebSocket消息未发送: userId={}", userId);
        }
    }

    /**
     * 获取当前在线用户数（供监控用）
     *
     * @return 在线用户数
     */
    public static int getOnlineCount() {
        return userSessions.size();
    }
}
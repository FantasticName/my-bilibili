package io.github.fantasticname.mybilibili.listener;

import io.github.fantasticname.mybilibili.ioc.SimpleContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * IoC容器初始化监听器，在Web应用启动时初始化IoC容器
 *
 * <p>ServletContextListener 是Servlet规范提供的监听器接口，
 * 当Web应用启动和关闭时，Tomcat会自动调用对应的方法。</p>
 *
 * <p>本监听器的工作流程：</p>
 * <ol>
 *   <li>Web应用启动时（contextInitialized）：
 *       创建SimpleContainer实例，扫描指定包，完成Bean的创建和依赖注入，
 *       并将容器实例存入ServletContext，供后续的Servlet和Filter使用</li>
 *   <li>Web应用关闭时（contextDestroyed）：
 *       记录关闭日志</li>
 * </ol>
 *
 * <p>为什么用ServletContextListener而不是在Servlet的init方法中初始化？</p>
 * <ul>
 *   <li>ServletContextListener在所有Servlet之前执行，确保容器在Servlet需要时已经准备好</li>
 *   <li>将容器存入ServletContext，所有Servlet都能访问</li>
 *   <li>职责分离：容器初始化和Servlet业务逻辑解耦</li>
 * </ul>
 *
 * @author FantasticName
 */
public class ContainerInitializerListener implements ServletContextListener {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(ContainerInitializerListener.class);

    /**
     * ServletContext中存储IoC容器的属性key
     */
    public static final String CONTAINER_ATTR = "simpleContainer";

    /**
     * 要扫描的基础包名
     *
     * <p>IoC容器会扫描这个包及其子包下所有带有组件注解的类</p>
     */
    private static final String BASE_PACKAGE = "io.github.fantasticname.mybilibili";

    /**
     * Web应用启动时调用，初始化IoC容器
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>创建SimpleContainer实例</li>
     *   <li>调用init方法，扫描包、创建Bean、注入依赖</li>
     *   <li>将容器实例存入ServletContext，供后续使用</li>
     * </ol>
     *
     * @param sce Servlet上下文事件对象
     */
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        log.info("========================================");
        log.info("开始初始化IoC容器...");
        log.info("========================================");

        // 1. 获取ServletContext，它是Web应用的全局上下文
        //    所有Servlet、Filter、Listener共享同一个ServletContext
        ServletContext servletContext = sce.getServletContext();

        // 2. 记录启动信息
        String serverInfo = servletContext.getServerInfo();
        log.info("服务器信息: {}", serverInfo);
        log.info("扫描基础包: {}", BASE_PACKAGE);

        // 3. 创建IoC容器实例
        SimpleContainer container = new SimpleContainer();

        // 4. 初始化容器：扫描包、创建Bean、注入依赖
        //    这是最核心的步骤，整个IoC容器的初始化都在这里完成
        container.init(BASE_PACKAGE);

        // 5. 将容器实例存入ServletContext
        //    后续的Servlet和Filter可以通过ServletContext获取容器
        //    从而从容器中获取需要的Bean
        servletContext.setAttribute(CONTAINER_ATTR, container);

        log.info("========================================");
        log.info("IoC容器初始化完成，已存入ServletContext");
        log.info("========================================");
    }

    /**
     * Web应用关闭时调用
     *
     * @param sce Servlet上下文事件对象
     */
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        log.info("========================================");
        log.info("IoC容器正在关闭...");
        log.info("========================================");

        // 1. 从ServletContext中移除容器引用
        sce.getServletContext().removeAttribute(CONTAINER_ATTR);

        log.info("IoC容器已关闭");
    }
}

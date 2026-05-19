package io.github.fantasticname.mybilibili.ioc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 包扫描器，负责扫描指定包下的所有类
 *
 * <p>核心思路分三步：</p>
 * <ol>
 *   <li>把包名转成文件路径：com.example → com/example</li>
 *   <li>用 ClassLoader 定位到实际的目录或jar包</li>
 *   <li>遍历目录/jar包，找出所有 .class 文件，拼出全限定类名，用 Class.forName() 加载</li>
 * </ol>
 *
 * <p>Java里的 .class 文件可能来自两个地方：</p>
 * <ul>
 *   <li>文件系统：开发时在 target/classes 目录下</li>
 *   <li>jar包：打成jar后，类文件打包在jar里</li>
 * </ul>
 *
 * <p>本扫描器同时支持这两种情况。</p>
 *
 * @author FantasticName
 */
public class PackageScanner {

    /**
     * 日志记录器，用于记录包扫描过程中的详细信息
     */
    private static final Logger log = LoggerFactory.getLogger(PackageScanner.class);

    /**
     * 扫描指定包下所有的类
     *
     * <p>这是包扫描的入口方法。流程如下：</p>
     * <ol>
     *   <li>将包名（如 "com.example"）转换成路径格式（如 "com/example"）</li>
     *   <li>通过当前线程的类加载器获取该路径对应的资源URL</li>
     *   <li>根据URL的协议类型（file 或 jar），分别调用不同的扫描逻辑</li>
     * </ol>
     *
     * @param basePackage 包名，如 "io.github.fantasticname.mybilibili"
     * @return 所有类的 Class 对象列表
     */
    public static List<Class<?>> scan(String basePackage) {
        log.debug("开始扫描包: {}", basePackage);

        // 1. 创建结果列表，用于存放扫描到的所有Class对象
        List<Class<?>> classList = new ArrayList<>();

        // 2. 包名转路径：com.example → com/example
        //    因为ClassLoader的getResource方法使用路径分隔符是'/'而不是'.'
        String path = basePackage.replace('.', '/');
        log.debug("包名转换为路径: {} -> {}", basePackage, path);

        try {
            // 3. 获取当前线程的上下文类加载器
            //    类加载器负责加载类，它知道.class文件在哪里
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            log.debug("获取到类加载器: {}", classLoader.getClass().getName());

            // 4. 通过类加载器找到该路径下的所有资源URL
            //    一个包可能对应多个URL（比如在多个jar包中都存在）
            Enumeration<URL> resources = classLoader.getResources(path);

            // 5. 遍历所有找到的资源URL
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                String protocol = url.getProtocol();
                log.debug("发现资源URL: {}, 协议: {}", url, protocol);

                // 6. 根据协议类型选择不同的扫描策略
                if ("file".equals(protocol)) {
                    // 文件系统协议：开发环境，.class文件在target/classes目录下
                    log.debug("使用文件系统扫描模式");
                    File directory;
                    try {
                        // ✅ 使用 toURI() 自动解码，处理空格和中文路径
                        directory = new File(url.toURI());
                    } catch (java.net.URISyntaxException e) {
                        // 降级处理：手动解码（以防万一）
                        String decodedPath = java.net.URLDecoder.decode(url.getFile(), "UTF-8");
                        directory = new File(decodedPath);
                    }
                    scanDirectory(directory, basePackage, classList);
                } else if ("jar".equals(protocol)) {
                    // jar协议：打包后的环境，.class文件在jar包内
                    log.debug("使用jar包扫描模式");
                    scanJar(url, path, classList);
                } else {
                    // 其他协议暂不支持
                    log.warn("暂不支持该协议的包扫描: {}", protocol);
                }
            }
        } catch (Exception e) {
            // 扫描过程中出现异常，记录错误日志
            log.error("扫描包 {} 时发生异常", basePackage, e);
        }

        log.info("包扫描完成，共扫描到 {} 个类", classList.size());
        return classList;
    }

    /**
     * 递归扫描文件系统目录，把找到的 .class 文件加载成 Class 对象
     *
     * <p>这个方法的工作原理：</p>
     * <ol>
     *   <li>检查目录是否存在、是否真的是目录</li>
     *   <li>遍历目录下的所有文件和子目录</li>
     *   <li>如果是子目录，递归扫描，包名要拼接上子目录名</li>
     *   <li>如果是 .class 文件，提取类名，拼出全限定类名，用 Class.forName() 加载</li>
     * </ol>
     *
     * @param dir         要扫描的目录
     * @param packageName 当前目录对应的包名
     * @param classList   用于存放扫描结果的列表
     */
    private static void scanDirectory(File dir, String packageName, List<Class<?>> classList) {
        // 1. 如果目录不存在或者不是目录，直接返回
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("目录不存在或不是有效目录: {}", dir.getAbsolutePath());
            return;
        }

        log.debug("扫描目录: {}, 对应包名: {}", dir.getAbsolutePath(), packageName);

        // 2. 获取目录下的所有文件和子目录
        File[] files = dir.listFiles();
        if (files == null) {
            log.warn("无法列出目录内容: {}", dir.getAbsolutePath());
            return;
        }

        // 3. 遍历每一个文件/子目录
        for (File file : files) {
            if (file.isDirectory()) {
                // 3.1 如果是子目录，递归扫描
                //     包名要加上子目录名，比如 com.example + ".service" = com.example.service
                String subPackageName = packageName + "." + file.getName();
                log.debug("发现子目录: {}, 递归扫描包: {}", file.getName(), subPackageName);
                scanDirectory(file, subPackageName, classList);
            } else if (file.getName().endsWith(".class")) {
                // 3.2 如果是 .class 文件，加载它
                //     文件名如 "UserService.class"，去掉后缀得到类名 "UserService"
                String className = file.getName().substring(0, file.getName().length() - 6);
                //     拼出全限定类名：包名 + "." + 类名
                String fullClassName = packageName + "." + className;

                try {
                    // 3.3 通过 Class.forName() 加载类
                    //     这个方法会返回对应类的 Class 对象
                    //     Class对象是JVM为每个类创建的"身份证"，存着类的所有信息
                    Class<?> clazz = Class.forName(fullClassName);
                    classList.add(clazz);
                    log.debug("成功加载类: {}", fullClassName);
                } catch (ClassNotFoundException e) {
                    // 类找不到，记录错误但继续扫描其他类
                    log.error("加载类失败: {}", fullClassName, e);
                } catch (NoClassDefFoundError e) {
                    // 类定义找不到（依赖缺失），记录错误但继续扫描
                    log.error("类定义缺失: {}", fullClassName, e);
                }
            }
        }
    }

    /**
     * 扫描jar包中的类
     *
     * <p>当项目打成jar包运行时，.class文件都打包在jar里，不能用File遍历。
     * 需要通过 JarURLConnection 读取jar内的目录条目。</p>
     *
     * <p>工作原理：</p>
     * <ol>
     *   <li>打开jar包的连接</li>
     *   <li>遍历jar包内的所有条目（JarEntry）</li>
     *   <li>找到以目标路径开头且以 .class 结尾的条目</li>
     *   <li>将条目路径转换回全限定类名并加载</li>
     * </ol>
     *
     * @param url       jar包的URL
     * @param path      包路径，如 "com/example"
     * @param classList 用于存放扫描结果的列表
     */
    private static void scanJar(URL url, String path, List<Class<?>> classList) {
        try {
            // 1. 打开jar包连接
            java.net.JarURLConnection jarConnection = (java.net.JarURLConnection) url.openConnection();
            JarFile jarFile = jarConnection.getJarFile();
            log.debug("打开jar包: {}", jarFile.getName());

            // 2. 遍历jar包内的所有条目
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                // 3. 只处理以目标路径开头且以 .class 结尾的条目
                //    比如 "com/example/UserService.class"
                if (name.startsWith(path) && name.endsWith(".class")) {
                    // 4. 将路径格式转换回全限定类名
                    //    "com/example/UserService.class" → "com.example.UserService"
                    String className = name.replace('/', '.').substring(0, name.length() - 6);

                    try {
                        // 5. 加载类
                        Class<?> clazz = Class.forName(className);
                        classList.add(clazz);
                        log.debug("从jar包加载类: {}", className);
                    } catch (ClassNotFoundException e) {
                        log.error("从jar包加载类失败: {}", className, e);
                    } catch (NoClassDefFoundError e) {
                        log.error("jar包中类定义缺失: {}", className, e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("扫描jar包时发生异常: {}", url, e);
        }
    }
}

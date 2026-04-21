package io.github.fantasticname.mybilibili.ioc;

import io.github.fantasticname.mybilibili.annotation.Autowired;
import io.github.fantasticname.mybilibili.annotation.Component;
import io.github.fantasticname.mybilibili.annotation.RestController;
import io.github.fantasticname.mybilibili.annotation.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简易IoC容器，实现控制反转和依赖注入
 *
 * <p>IoC（Inversion of Control，控制反转）的核心思想是：
 * 对象的创建和依赖关系的管理不再由对象自己负责，而是交给容器来统一管理。</p>
 *
 * <p>本容器做三件事：</p>
 * <ol>
 *   <li><b>扫描</b>：通过 PackageScanner 找到所有贴了 @Component / @Service / @RestController 的类</li>
 *   <li><b>创建实例</b>：把每个类通过反射 newInstance() 创建出来，存到一个 Map（beanMap）里</li>
 *   <li><b>注入依赖</b>：检查每个对象里的字段，如果有 @Autowired，就从 Map 里找到对应的对象塞进去</li>
 * </ol>
 *
 * <p>这就是Spring IoC容器的最核心原理。Spring在此基础上还增加了更多功能
 * （如Bean的生命周期管理、AOP、事务等），但底层逻辑和这里是一样的。</p>
 *
 * @author FantasticName
 */
public class SimpleContainer {

    /**
     * 日志记录器，用于记录容器初始化和依赖注入的详细过程
     */
    private static final Logger log = LoggerFactory.getLogger(SimpleContainer.class);

    /**
     * Bean存储池，key是Bean名称，value是Bean实例
     *
     * <p>Bean名称的生成规则：</p>
     * <ul>
     *   <li>如果注解指定了value（如 @Service("myService")），则使用指定的名称</li>
     *   <li>否则使用类名首字母小写（如 UserService → userService）</li>
     * </ul>
     *
     * <p>使用ConcurrentHashMap保证线程安全</p>
     */
    private final Map<String, Object> beanMap = new ConcurrentHashMap<>();

    /**
     * 初始化IoC容器
     *
     * <p>初始化流程：</p>
     * <ol>
     *   <li>扫描指定包下所有类</li>
     *   <li>过滤出带有 @Component / @Service / @RestController 注解的类</li>
     *   <li>通过反射创建这些类的实例，存入beanMap</li>
     *   <li>遍历所有Bean，对带有 @Autowired 注解的字段进行依赖注入</li>
     * </ol>
     *
     * @param basePackage 要扫描的基础包名，如 "io.github.fantasticname.mybilibili"
     */
    public void init(String basePackage) {
        log.info("开始初始化IoC容器，扫描包: {}", basePackage);

        try {
            // ============ 第一步：扫描包下所有类 ============
            // 调用PackageScanner扫描指定包下的所有类，得到Class对象列表
            List<Class<?>> allClasses = PackageScanner.scan(basePackage);
            log.info("包扫描完成，共发现 {} 个类", allClasses.size());

            // ============ 第二步：过滤出带有组件注解的类 ============
            // 只处理贴了 @Component / @Service / @RestController 的类
            // 这三种注解都表示"这个类需要被容器管理"
            List<Class<?>> beanClasses = new ArrayList<>();
            for (Class<?> clazz : allClasses) {
                boolean hasComponentAnno = clazz.isAnnotationPresent(Component.class);
                boolean hasServiceAnno = clazz.isAnnotationPresent(Service.class);
                boolean hasRestControllerAnno = clazz.isAnnotationPresent(RestController.class);

                if (hasComponentAnno || hasServiceAnno || hasRestControllerAnno) {
                    beanClasses.add(clazz);
                    log.debug("发现组件类: {}, 注解: [Component={}, Service={}, RestController={}]",
                            clazz.getName(), hasComponentAnno, hasServiceAnno, hasRestControllerAnno);
                }
            }
            log.info("过滤完成，共发现 {} 个组件类", beanClasses.size());

            // ============ 第三步：创建实例 ============
            // 遍历所有组件类，通过反射创建实例
            for (Class<?> clazz : beanClasses) {
                // 3.1 生成Bean名称
                String beanName = generateBeanName(clazz);
                log.debug("准备创建Bean: {}, 名称: {}", clazz.getSimpleName(), beanName);

                // 3.2 通过反射创建实例
                //     clazz.getDeclaredConstructor().newInstance() 相当于 new 类名()
                //     这是反射创建对象的标准写法
                Object instance = clazz.getDeclaredConstructor().newInstance();

                // 3.3 存入beanMap，key是Bean名称，value是实例
                beanMap.put(beanName, instance);
                log.info("创建Bean成功: {} -> {}", beanName, clazz.getName());
            }

            // ============ 第四步：依赖注入 ============
            // 遍历所有已创建的Bean，检查字段上是否有 @Autowired 注解
            // 如果有，就从容器中找到类型匹配的对象，通过反射注入
            for (Object bean : beanMap.values()) {
                injectDependencies(bean);
            }

            log.info("IoC容器初始化完成，共注册 {} 个Bean", beanMap.size());
        } catch (Exception e) {
            // 容器初始化失败是致命错误，记录错误日志
            log.error("IoC容器初始化失败", e);
            throw new RuntimeException("IoC容器初始化失败", e);
        }
    }

    /**
     * 生成Bean名称
     *
     * <p>名称生成规则：</p>
     * <ol>
     *   <li>如果类上的注解指定了value属性（如 @Service("myService")），则使用指定的名称</li>
     *   <li>否则使用类名首字母小写（如 UserService → userService）</li>
     * </ol>
     *
     * @param clazz 类的Class对象
     * @return Bean名称
     */
    private String generateBeanName(Class<?> clazz) {
        // 1. 依次检查类上的三种注解，看是否指定了value属性
        String value = null;

        // 1.1 检查 @Component 注解的value
        if (clazz.isAnnotationPresent(Component.class)) {
            value = clazz.getAnnotation(Component.class).value();
        }

        // 1.2 检查 @Service 注解的value（优先级更高，因为语义更具体）
        if (clazz.isAnnotationPresent(Service.class)) {
            String serviceValue = clazz.getAnnotation(Service.class).value();
            if (!serviceValue.isEmpty()) {
                value = serviceValue;
            }
        }

        // 1.3 检查 @RestController 注解的value（优先级最高）
        if (clazz.isAnnotationPresent(RestController.class)) {
            String controllerValue = clazz.getAnnotation(RestController.class).value();
            if (!controllerValue.isEmpty()) {
                value = controllerValue;
            }
        }

        // 2. 如果指定了value且不为空，直接使用
        if (value != null && !value.isEmpty()) {
            log.debug("使用注解指定的Bean名称: {}", value);
            return value;
        }

        // 3. 否则使用类名首字母小写作为Bean名称
        //    例如：UserService → userService
        String simpleName = clazz.getSimpleName();
        String defaultName = Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
        log.debug("使用默认Bean名称: {}", defaultName);
        return defaultName;
    }

    /**
     * 对单个Bean进行依赖注入
     *
     * <p>工作原理：</p>
     * <ol>
     *   <li>获取Bean对象的所有字段（包括私有字段）</li>
     *   <li>检查每个字段上是否有 @Autowired 注解</li>
     *   <li>如果有，根据字段类型从容器中找到匹配的Bean</li>
     *   <li>通过反射（field.set()）将找到的Bean注入到字段中</li>
     * </ol>
     *
     * <p>注意：即使字段是 private 的也能注入，因为 field.setAccessible(true) 
     * 告诉JVM"别管访问权限，我就是要操作它"，这就是"暴力反射"，
     * Spring的 @Autowired 也是这么干的。</p>
     *
     * @param bean 需要注入依赖的Bean实例
     */
    private void injectDependencies(Object bean) {
        // 1. 获取Bean对象的所有字段（包括private字段）
        //    getDeclaredFields() 返回该类声明的所有字段，不包括继承的
        Field[] fields = bean.getClass().getDeclaredFields();
        log.debug("检查Bean {} 的依赖，共 {} 个字段", bean.getClass().getSimpleName(), fields.length);

        // 2. 遍历每个字段
        for (Field field : fields) {
            // 2.1 检查字段上是否有 @Autowired 注解
            if (field.isAnnotationPresent(Autowired.class)) {
                // 2.2 获取字段的类型，比如 UserMapper
                Class<?> fieldType = field.getType();
                log.debug("发现 @Autowired 字段: {} -> {}", bean.getClass().getSimpleName(), fieldType.getSimpleName());

                // 2.3 根据类型从容器中查找匹配的Bean
                Object dependency = findBeanByType(fieldType);

                if (dependency != null) {
                    // 2.4 暴力反射，允许修改私有字段
                    //     正常情况下private字段外面访问不到
                    //     setAccessible(true) 告诉JVM："别管它是不是private，我就要操作它！"
                    field.setAccessible(true);

                    try {
                        // 2.5 把找到的依赖对象设置给bean的这个字段
                        //     相当于 bean.field = dependency
                        field.set(bean, dependency);
                        log.info("依赖注入成功: {} -> {}.{}", fieldType.getSimpleName(),
                                bean.getClass().getSimpleName(), field.getName());
                    } catch (IllegalAccessException e) {
                        // 注入失败，记录错误日志
                        log.error("依赖注入失败: {}.{}", bean.getClass().getSimpleName(), field.getName(), e);
                        throw new RuntimeException("依赖注入失败: " + bean.getClass().getSimpleName() + "." + field.getName(), e);
                    }
                } else {
                    // 找不到匹配的Bean，记录警告
                    log.warn("未找到类型为 {} 的Bean，无法注入到 {}.{}",
                            fieldType.getName(), bean.getClass().getSimpleName(), field.getName());
                }
            }
        }
    }

    /**
     * 根据类型从容器中查找Bean
     *
     * <p>遍历beanMap中的所有Bean实例，检查Bean的类型是否是目标类型的子类或实现类。
     * 使用 Class.isAssignableFrom() 方法判断：</p>
     * <ul>
     *   <li>type.isAssignableFrom(bean.getClass()) 返回true，表示bean是type的子类/实现类</li>
     *   <li>这样就能完成"按类型注入"，比如字段类型是接口 UserMapper，
     *       而容器中有实现类 UserMapperImpl 的实例，就能匹配上</li>
     * </ul>
     *
     * @param type 需要查找的字段类型
     * @return 匹配的Bean实例，如果找不到返回null
     */
    private Object findBeanByType(Class<?> type) {
        // 1. 遍历容器中所有的Bean
        for (Object bean : beanMap.values()) {
            // 2. 判断bean是否是type的子类或实现类
            //    type.isAssignableFrom(bean.getClass()) 的含义是：
            //    "type这个类型，能不能接收bean这个对象？"
            //    如果type是接口，bean是实现类，返回true
            //    如果type是父类，bean是子类，返回true
            //    如果type和bean类型完全一样，也返回true
            if (type.isAssignableFrom(bean.getClass())) {
                log.debug("按类型匹配成功: {} -> {}", type.getSimpleName(), bean.getClass().getSimpleName());
                return bean;
            }
        }

        // 3. 遍历完也没找到，返回null
        log.debug("按类型匹配失败，未找到类型: {}", type.getName());
        return null;
    }

    /**
     * 根据名称从容器中获取Bean
     *
     * <p>这是容器对外提供的核心方法之一，让外部代码能从容器中获取需要的对象。
     * 不需要自己 new，直接从容器拿就行——这就是"控制反转"的体现。</p>
     *
     * @param name Bean名称
     * @return Bean实例，如果找不到返回null
     */
    public Object getBean(String name) {
        Object bean = beanMap.get(name);
        if (bean == null) {
            log.warn("未找到名称为 {} 的Bean", name);
        }
        return bean;
    }

    /**
     * 根据类型从容器中获取Bean
     *
     * <p>这是泛型版本的getBean方法，可以直接返回指定类型的对象，无需强制类型转换。</p>
     *
     * @param clazz Bean的类型
     * @param <T>   Bean的泛型类型
     * @return 匹配的Bean实例，如果找不到返回null
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> clazz) {
        // 1. 遍历容器中所有的Bean
        for (Object bean : beanMap.values()) {
            // 2. 如果Bean的类型匹配，返回
            if (clazz.isAssignableFrom(bean.getClass())) {
                log.debug("按类型获取Bean成功: {}", clazz.getSimpleName());
                return (T) bean;
            }
        }

        // 3. 找不到，记录警告并返回null
        log.warn("未找到类型为 {} 的Bean", clazz.getName());
        return null;
    }

    /**
     * 获取容器中所有已注册的Bean
     *
     * @return Bean名称到实例的映射（不可修改的视图）
     */
    public Map<String, Object> getAllBeans() {
        return Map.copyOf(beanMap);
    }
}

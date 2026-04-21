好的，同学别慌！既然你说看不懂，那咱们就从 **零基础** 开始，用大白话一步步讲清楚。我会用你最熟悉的业务场景（比如 `UserService` 调用 `UserMapper` 查询数据库）来举例。

咱们今天只学 **IoC 容器** 这一块，确保你彻底搞懂它是怎么来的。

------

## 第一课：没有 Spring 的时候，我们怎么干活？

假设你要写一个“用户注册”的功能。

**最原始的做法（你现在的代码结构）：**

java

```
// 这是一个接口，定义了查用户的方法
public interface UserMapper {
    User findUser(String username);
}

// 实现类：真正去数据库查数据的（这里假装写个假的）
public class UserMapperImpl implements UserMapper {
    public User findUser(String username) {
        System.out.println("去数据库查用户：" + username);
        return new User(username);
    }
}

// 业务类：负责注册逻辑
public class UserService {
    // 注意：这里写死了 new UserMapperImpl()
    private UserMapper userMapper = new UserMapperImpl();

    public void register(String username) {
        User user = userMapper.findUser(username);
        if (user != null) {
            System.out.println("用户名已存在");
        } else {
            System.out.println("注册成功");
        }
    }
}
```



**问题来了：** 每次要换个数据库实现（比如换成 `UserMapperOracleImpl`），你就得改 `UserService` 的代码，把 `new UserMapperImpl()` 换成 `new UserMapperOracleImpl()`。如果有 100 个 Service 都在 `new` 这个对象，你要改 100 处，这太蠢了。

**你想不想这样：**

- 告诉程序：“嘿，`UserService` 需要一个 `UserMapper` 类型的对象，你帮我准备好。”
- 告诉程序：“`UserMapperImpl` 是一个能用的 `UserMapper` 实现。”
- 然后程序自动帮我们把 `UserMapperImpl` 塞进 `UserService`。

这就是 **IoC（控制反转）** 和 **依赖注入** 的思想。

Spring 就是用 `@Service`、`@Autowired` 这些注解来“告诉程序”这些信息的。现在我们要手写一个简单的版本。

------

## 第二课：你必须懂的三个核心知识点

### 1. Class 对象 —— 类的“身份证”

在 Java 里，每一个类（比如 `UserService.class`）在运行的时候，JVM 都会给它创建一个唯一的 **Class 对象**。这个对象里面存了这个类的**所有信息**：

- 类名叫什么？
- 有哪些属性（字段）？
- 有哪些方法？
- 身上贴了什么注解？

**怎么拿到一个类的 Class 对象？**

java

```
// 方法1：类名.class
Class<UserService> clazz1 = UserService.class;

// 方法2：对象.getClass()
UserService service = new UserService();
Class<?> clazz2 = service.getClass();

// 方法3：根据类的全限定名（字符串）去找
Class<?> clazz3 = Class.forName("com.example.UserService");
```



拿到 `Class` 对象后，你就能知道这个类的所有底细。

### 2. 反射 —— 通过 Class 对象操作类的能力

**反射就是：让你在程序运行的时候，动态地查看类的信息，并且可以动态创建对象、调用方法、修改属性。**

举个例子，假设你已经有了 `UserService` 的 `Class` 对象：

java

```
Class<UserService> clazz = UserService.class;

// 1. 看看类上有没有 @Service 注解？
boolean hasServiceAnno = clazz.isAnnotationPresent(Service.class);

// 2. 创建这个类的一个实例（相当于 new UserService()）
UserService obj = clazz.getDeclaredConstructor().newInstance();

// 3. 获取这个类里面所有的字段（属性）
Field[] fields = clazz.getDeclaredFields();
for (Field field : fields) {
    // 看看这个字段上有没有 @Autowired 注解
    if (field.isAnnotationPresent(Autowired.class)) {
        // 如果有，我们就应该给它注入一个值
    }
}
```



**反射是 Java 的灵魂技能**，IoC 容器全靠它。

### 3. 注解 —— 贴在代码上的“便利贴”

注解本身不干活，它只是一个**标记**。比如你写 `@Service`，程序运行时可以通过反射读到这个标记，然后决定“哦，这个类应该被容器管理”。

自定义一个注解非常简单：

java

```
// 这个注解可以贴在类上（TYPE），运行时还在（RUNTIME）
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Service {
    String value() default "";  // 可以给这个注解一个名字
}
```



------

## 第三课：手写一个极简的 IoC 容器（一步一步来）

### 第一步：定义我们自己的注解

我们不要用 Spring 的，自己造三个。

java

```
// 贴在类上，表示这个类交给容器管
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MyService {
}

// 贴在类上，表示这是个 Controller（其实也是一种特殊的 Service）
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MyRestController {
}

// 贴在字段上，表示容器要帮忙注入一个对象进来
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MyAutowired {
}
```



### 第二步：改造我们的业务类，贴上注解

java

```
@MyService
public class UserMapperImpl implements UserMapper {
    public User findUser(String username) {
        System.out.println("查数据库：" + username);
        return null; // 假设没查到
    }
}

@MyService  // 贴上注解，告诉容器：我这个类需要被管理
public class UserService {

    @MyAutowired  // 贴上注解，告诉容器：我需要一个 UserMapper 的对象
    private UserMapper userMapper;

    public void register(String username) {
        userMapper.findUser(username);  // 不用自己 new 了
        System.out.println("注册逻辑执行了");
    }
}
```



### 第三步：实现容器的核心逻辑

现在我们要写一个 `SimpleContainer` 类，它做三件事：

1. **扫描**：找到所有贴了 `@MyService` / `@MyRestController` 的类。
2. **创建实例**：把每个类 `new` 出来，存到一个 Map 里（比如叫 `beanMap`）。
3. **注入依赖**：检查每个对象里的字段，如果有 `@MyAutowired`，就从 Map 里找到对应的对象，塞进去。

java

```
public class SimpleContainer {
    // 存放所有创建好的对象，key 是类名，value 是实例
    private Map<String, Object> beanMap = new HashMap<>();

    // 入口方法：初始化容器
    public void init() throws Exception {
        // 1. 扫描（为了简单，我直接手动添加要扫描的类，实际 Spring 是扫包的）
        List<Class<?>> classList = new ArrayList<>();
        classList.add(UserService.class);
        classList.add(UserMapperImpl.class);

        // 2. 遍历类列表，创建实例
        for (Class<?> clazz : classList) {
            // 只处理贴了 @MyService 或 @MyRestController 的类
            if (clazz.isAnnotationPresent(MyService.class) || 
                clazz.isAnnotationPresent(MyRestController.class)) {
                
                // 通过反射创建实例（相当于 new 对象）
                Object instance = clazz.getDeclaredConstructor().newInstance();
                
                // 存到 Map 里，key 用类名（简单处理）
                beanMap.put(clazz.getSimpleName(), instance);
            }
        }

        // 3. 依赖注入
        for (Object bean : beanMap.values()) {
            // 获取这个对象的所有字段
            Field[] fields = bean.getClass().getDeclaredFields();
            for (Field field : fields) {
                // 如果字段上有 @MyAutowired 注解
                if (field.isAnnotationPresent(MyAutowired.class)) {
                    // 我们要找一个合适的对象塞给它
                    Class<?> fieldType = field.getType();  // 字段的类型，比如 UserMapper
                    Object dependency = findBeanByType(fieldType);  // 从容器里找一个 UserMapper 类型的对象
                    
                    // 暴力反射，允许修改私有字段
                    field.setAccessible(true);
                    // 把 dependency 设置给 bean 的这个字段
                    field.set(bean, dependency);
                }
            }
        }
    }

    // 根据类型查找 Bean
    private Object findBeanByType(Class<?> type) {
        for (Object bean : beanMap.values()) {
            // 如果 bean 的类型是 type 的子类或实现类
            if (type.isAssignableFrom(bean.getClass())) {
                return bean;
            }
        }
        return null;
    }

    // 提供一个方法，让外面能拿到容器里的对象
    public Object getBean(String name) {
        return beanMap.get(name);
    }
}
```



### 第四步：测试一下！

java

```
public class MainTest {
    public static void main(String[] args) throws Exception {
        // 1. 启动容器
        SimpleContainer container = new SimpleContainer();
        container.init();

        // 2. 从容器中获取 UserService
        UserService userService = (UserService) container.getBean("UserService");

        // 3. 调用方法
        userService.register("zhangsan");
    }
}
```



**输出结果：**

text

```
查数据库：zhangsan
注册逻辑执行了
```



**你看！** `UserService` 里的 `userMapper` 字段不再是 `null`，容器自动帮我们把 `UserMapperImpl` 对象注入进去了。这就是 `@Autowired` 的底层原理！

------

## 第四课：补充讲解你刚才没看懂的关键点

### 1. `Class.forName("包名.类名")` 是干嘛的？

如果我把类名写在配置文件里（比如 `com.example.UserService`），程序启动时读取这个字符串，然后用 `Class.forName("com.example.UserService")` 就能拿到这个类的 `Class` 对象，进而创建实例。这就是 Spring 扫描包的原理：先找到包路径下所有的 `.class` 文件名，拼成全限定名，然后 `Class.forName`。

### 2. 为什么 `field.set(bean, dependency)` 能把值塞进私有字段？

正常情况下，`private` 字段在外面是访问不到的。但是 `field.setAccessible(true)` 这行代码就是告诉 JVM：“别管它是不是 private，我就要操作它！” 这就是**暴力反射**。Spring 也是这么干的。

### 3. 接口怎么注入实现类？

`UserMapper` 是接口，`UserMapperImpl` 是它的实现类。在 `findBeanByType` 方法中，我们用 `type.isAssignableFrom(bean.getClass())` 判断：`bean` 是不是 `type` 的子类/实现类？如果是，就把它返回。这样就完成了**按类型注入**。

### 4. 如果有多个实现类怎么办？

比如 `UserMapper` 有 `UserMapperImpl` 和 `UserMapperOracleImpl` 两个类都贴了 `@MyService`，容器就不知道该注入哪个了。Spring 的做法是：

- 结合 `@Qualifier` 注解指定名字
- 或者按字段名匹配

这属于进阶内容，你先理解单实现的情况就够了。

------

## 第五课：给你一个能直接跑起来的完整代码

我帮你整理一个最简版本，你复制到 IDE 里就能跑，亲自感受一下反射和 IoC 的魅力。

**文件1：MyService.java**

java

```
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MyService {
}
```



**文件2：MyAutowired.java**

java

```
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MyAutowired {
}
```



**文件3：UserMapper.java**

java

```
public interface UserMapper {
    void findUser(String username);
}
```



**文件4：UserMapperImpl.java**

java

```
@MyService
public class UserMapperImpl implements UserMapper {
    @Override
    public void findUser(String username) {
        System.out.println("从数据库查询用户：" + username);
    }
}
```



**文件5：UserService.java**

java

```
@MyService
public class UserService {

    @MyAutowired
    private UserMapper userMapper;

    public void register(String username) {
        System.out.println("开始注册用户：" + username);
        userMapper.findUser(username);
        System.out.println("注册成功！");
    }
}
```



**文件6：SimpleContainer.java**

java

```
import java.lang.reflect.Field;
import java.util.*;

public class SimpleContainer {
    private Map<String, Object> beanMap = new HashMap<>();

    public void init() throws Exception {
        // 1. 手动指定要管理的类（实际项目中可以通过扫描包实现）
        List<Class<?>> classes = Arrays.asList(
            UserService.class,
            UserMapperImpl.class
        );

        // 2. 创建实例
        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(MyService.class)) {
                Object instance = clazz.getDeclaredConstructor().newInstance();
                beanMap.put(clazz.getSimpleName(), instance);
                System.out.println("创建实例：" + clazz.getSimpleName());
            }
        }

        // 3. 依赖注入
        for (Object bean : beanMap.values()) {
            for (Field field : bean.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(MyAutowired.class)) {
                    Object dependency = findBeanByType(field.getType());
                    field.setAccessible(true);
                    field.set(bean, dependency);
                    System.out.println("注入依赖：" + field.getType().getSimpleName() + 
                                       " -> " + bean.getClass().getSimpleName());
                }
            }
        }
    }

    private Object findBeanByType(Class<?> type) {
        for (Object bean : beanMap.values()) {
            if (type.isAssignableFrom(bean.getClass())) {
                return bean;
            }
        }
        throw new RuntimeException("找不到类型为 " + type.getName() + " 的 Bean");
    }

    public Object getBean(String name) {
        return beanMap.get(name);
    }
}
```



**文件7：Main.java**

java

```
public class Main {
    public static void main(String[] args) throws Exception {
        SimpleContainer container = new SimpleContainer();
        container.init();

        UserService userService = (UserService) container.getBean("UserService");
        userService.register("张三");
    }
}
```



运行 `Main`，你会看到：

text

```
创建实例：UserService
创建实例：UserMapperImpl
注入依赖：UserMapper -> UserService
开始注册用户：张三
从数据库查询用户：张三
注册成功！
```



------

## 总结：你现在理解了什么？

1. **Class 对象**：每个类在 JVM 里都有一个唯一的“档案”，存着类的所有信息。
2. **反射**：通过 Class 对象，可以在运行的时候创建对象、获取字段、调用方法，哪怕它们是 private 的。
3. **注解**：就是贴在代码上的标签，本身不做任何事，反射可以读到它并决定怎么处理。
4. **IoC 容器原理**：
   - 扫描类 → 找到有 `@Service` 的 → `newInstance()` 创建对象 → 存到 Map
   - 遍历所有对象 → 找到有 `@Autowired` 的字段 → 从 Map 里找到匹配类型的对象 → `field.set()` 赋值

这就是 Spring 最最核心的基石。后面你再学 `@RestController`、AOP、事务，全都是在这个基础上叠加功能。

下一步，你想让我继续讲 **手写 @RestController 和路由分发** 吗？还是先把这段代码彻底搞懂？随时问我！















很好，你已经抓住了核心痛点！手动添加类确实不现实，咱们现在就来补上**包扫描**这一环。

------

## 一、包扫描的核心思路

假设我们有个包叫 `com.example`，里面有很多类。程序启动时要能**自动找到**这个包下所有贴了 `@MyService` 的类。

思路分三步：

1. **把包名转成文件路径**：`com.example` → `com/example`
2. **定位到实际的目录或 jar 包**：用 `ClassLoader` 找到这个路径对应的 `URL`
3. **遍历目录，找出所有 `.class` 文件**：拼出全限定类名，用 `Class.forName()` 加载，判断是否有指定注解

------

## 二、你首先得懂：ClassLoader 和资源路径

Java 里的 `.class` 文件可能来自两个地方：

- **文件系统**：开发时在 `target/classes` 目录下
- **jar 包**：打成 jar 后，类文件打包在 jar 里

咱们先搞定**文件系统**的情况，这是你开发环境最常见的。

`ClassLoader` 负责加载类，它有一个方法 `getResource(String name)` 可以根据路径找到资源。

java

```
// 把包名换成路径
String path = "com/example";
// 当前线程的类加载器
ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
// 获取这个路径对应的 URL
URL url = classLoader.getResource(path);
System.out.println(url);  // 输出如：file:/D:/myproject/target/classes/com/example
```



拿到 `URL` 后，如果是 `file:` 协议，我们就可以用 `File` 类去遍历目录了。

------

## 三、一步一步写扫描代码

### 第一步：写一个方法，把包名下的所有类找出来

java

```
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class PackageScanner {

    /**
     * 扫描指定包下所有的类
     * @param basePackage 包名，如 "com.example"
     * @return 所有类的 Class 对象列表
     */
    public static List<Class<?>> scan(String basePackage) {
        List<Class<?>> classList = new ArrayList<>();
        
        // 1. 包名转路径
        String path = basePackage.replace('.', '/');
        
        // 2. 获取类加载器，并找到资源 URL
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL url = classLoader.getResource(path);
        
        if (url == null) {
            System.out.println("包不存在：" + basePackage);
            return classList;
        }
        
        // 3. 判断协议，只处理 file 协议（文件系统）
        if (!"file".equals(url.getProtocol())) {
            System.out.println("暂不支持非文件系统的包扫描：" + url.getProtocol());
            return classList;
        }
        
        // 4. 获取目录的 File 对象，开始递归扫描
        File directory = new File(url.getFile());
        scanDirectory(directory, basePackage, classList);
        
        return classList;
    }
    
    /**
     * 递归扫描目录，把找到的 .class 文件加载成 Class 对象
     */
    private static void scanDirectory(File dir, String packageName, List<Class<?>> classList) {
        // 如果目录不存在或者不是目录，直接返回
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        
        // 遍历目录下的所有文件和子目录
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                // 如果是子目录，递归扫描，包名要加上子目录名
                String subPackageName = packageName + "." + file.getName();
                scanDirectory(file, subPackageName, classList);
            } else if (file.getName().endsWith(".class")) {
                // 如果是 .class 文件，提取类名
                String className = file.getName().substring(0, file.getName().length() - 6);
                String fullClassName = packageName + "." + className;
                
                try {
                    // 加载类
                    Class<?> clazz = Class.forName(fullClassName);
                    classList.add(clazz);
                } catch (ClassNotFoundException e) {
                    System.err.println("加载类失败：" + fullClassName);
                }
            }
        }
    }
}
```



### 第二步：在容器初始化时使用扫描结果

修改 `SimpleContainer` 的 `init` 方法，把死列表换成扫描结果，并过滤出有注解的类。

java

```
public class SimpleContainer {
    private Map<String, Object> beanMap = new HashMap<>();

    public void init(String basePackage) throws Exception {
        // 1. 扫描包下所有类
        List<Class<?>> allClasses = PackageScanner.scan(basePackage);
        
        // 2. 只处理带有 @MyService 或 @MyRestController 注解的类
        List<Class<?>> beanClasses = new ArrayList<>();
        for (Class<?> clazz : allClasses) {
            if (clazz.isAnnotationPresent(MyService.class) || 
                clazz.isAnnotationPresent(MyRestController.class)) {
                beanClasses.add(clazz);
            }
        }
        
        // 3. 创建实例
        for (Class<?> clazz : beanClasses) {
            Object instance = clazz.getDeclaredConstructor().newInstance();
            beanMap.put(clazz.getSimpleName(), instance);
            System.out.println("创建实例：" + clazz.getSimpleName());
        }

        // 4. 依赖注入（同之前）
        for (Object bean : beanMap.values()) {
            for (Field field : bean.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(MyAutowired.class)) {
                    Object dependency = findBeanByType(field.getType());
                    field.setAccessible(true);
                    field.set(bean, dependency);
                    System.out.println("注入：" + field.getType().getSimpleName() + 
                                       " -> " + bean.getClass().getSimpleName());
                }
            }
        }
    }
    
    // 其余方法不变...
}
```



### 第三步：测试一下

假设项目结构如下：

text

```
src/main/java/
  └─ com/example/
       ├─ MyService.java
       ├─ MyAutowired.java
       ├─ UserMapper.java
       ├─ UserMapperImpl.java
       ├─ UserService.java
       └─ Main.java
```



修改 `Main` 调用方式：

java

```
public class Main {
    public static void main(String[] args) throws Exception {
        SimpleContainer container = new SimpleContainer();
        container.init("com.example");   // 只需要指定包名
        
        UserService userService = (UserService) container.getBean("UserService");
        userService.register("李四");
    }
}
```



运行结果和之前完全一样，但现在你再添加新类，只要在 `com.example` 包下并且有 `@MyService` 注解，容器就会自动管理它。

------

## 四、包扫描的两个进阶问题

### 1. 如果程序在 jar 包里运行怎么办？

当你把项目打成 jar 包，`url.getProtocol()` 会是 `jar`。这时候不能用 `File` 遍历，需要解析 jar 包内的条目。Spring 的做法是用 `JarURLConnection` 读取 jar 内目录。

简单版实现（可选，如果你暂时不需要打 jar 包运行可以先跳过）：

java

```
if ("jar".equals(url.getProtocol())) {
    // 处理 jar 包内扫描
    JarURLConnection jarConnection = (JarURLConnection) url.openConnection();
    JarFile jarFile = jarConnection.getJarFile();
    Enumeration<JarEntry> entries = jarFile.entries();
    while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();
        if (name.startsWith(path) && name.endsWith(".class")) {
            String className = name.replace('/', '.').substring(0, name.length() - 6);
            classList.add(Class.forName(className));
        }
    }
}
```



### 2. 如何像 Spring 一样支持多个包扫描？

Spring 的 `@ComponentScan` 可以指定多个包。你可以修改 `init` 方法接收一个字符串数组：

java

```
public void init(String... basePackages) throws Exception {
    List<Class<?>> allClasses = new ArrayList<>();
    for (String pkg : basePackages) {
        allClasses.addAll(PackageScanner.scan(pkg));
    }
    // 后续一样...
}
```



------

## 五、总结：你现在掌握的内容

| 步骤                            | 作用                       | 核心技术     |
| :------------------------------ | :------------------------- | :----------- |
| `basePackage.replace('.', '/')` | 包名转路径                 | 字符串操作   |
| `classLoader.getResource(path)` | 找到资源位置               | 类加载机制   |
| `File` 遍历目录                 | 递归找到所有 `.class` 文件 | I/O 操作     |
| `Class.forName(fullClassName)`  | 加载类                     | 反射         |
| `isAnnotationPresent`           | 判断是否有注解             | 反射读取注解 |

**现在你的 IoC 容器已经具备：**

- 自动扫描指定包
- 自动创建 `@MyService` / `@MyRestController` 实例
- 自动注入 `@MyAutowired` 依赖

这已经是 Spring IoC 容器的**最小化可行产品**了。
---
alwaysApply: true
---
# MyBilibili 项目规则

## 规则0：代码规范
- 根据阿里巴巴编码规范进行编码，其中，需要添加javadoc注释，author写FantasticName, 代码本身也需要写上详细注释(包括单行注释), 因为用户是个java小白, 需要详细注释, 才能理解代码。

## 规则1：修改代码后实时更新文档

每次修改完代码，根据实际情况实时更新以下文件：

- `D:\Program Files (x86)\Backend-Study\project-study\MyBilibili\my-bilibili\PROJECT_OVERVIEW.md`
- `D:\Program Files (x86)\Backend-Study\project-study\MyBilibili\my-bilibili\更新日志.md`
- `D:\Program Files (x86)\Backend-Study\project-study\MyBilibili\my-bilibili\backend\api-doc.md`
- `D:\Program Files (x86)\Backend-Study\project-study\MyBilibili\ai\手写框架\手写框架底层架构总结.md`
- `D:\Program Files (x86)\Backend-Study\project-study\MyBilibili\ai\手写框架\手写框架学习.md`
- `D:\Program Files (x86)\Backend-Study\project-study\MyBilibili\ai\手写框架\纯 JDBC 数据库操作规范.md`
- `D:\Program Files (x86)\Backend-Study\project-study\MyBilibili\my-bilibili\.trae\context\project_context.md`

- `D:\Program Files (x86)\Backend-Study\project-study\MyBilibili\my-bilibili\.trae\memory\project_memory.md`
> 最后一个文件是Trae的配置文件之一,是ai助手在对话中要自动更新的项目记忆文件,记录ai助手觉得重要的信息,以实现即使新建会话窗口导致上下文丢失,ai助手也可以读取这个文件以获取记忆信息,包括但不限于重要的项目信息和用户偏好。

## 规则2：数据库结构变更时的处理

1. 每次修改完项目后，如果涉及了数据库结构的更新（比如新增或者修改表或者字段等），需要更新以下文件：
`D:\Program Files (x86)\Backend-Study\project-study\MyBilibili\my-bilibili\backend\init.sql`
> 这个文件是整个项目的数据库所有表的建表语句，需要根据实际情况更新。
2. 每次修改完项目后，如果涉及了数据库结构的更新（比如新增或者修改表或者字段等），需要通过终端执行命令以更新数据库,(我已经安装好了mysql.exe和redis-cli.exe),MySQL数据库和Redis数据库的连接信息,用户密码详见`.trae\memory\project_memory.md`
3. 如果你自己执行命令更新数据库失败, 请你明确告诉我, 并写一个基于已有的数据库修改.sql文件, 这样我可以手动运行这个文件的SQL语句, 就可以和新的init.sql对齐.
`D:\Program Files (x86)\Backend-Study\project-study\MyBilibili\my-bilibili\backend\基于已有的数据库修改.sql`
> 这个文件是基于已有的数据库结构进行修改的SQL语句. 开发者只需要在MySQL命令行中执行这个文件的所有sql语句,就可以和新的init.sql对齐. 这是由于旧的数据库已经存在, 只需要执行部分SQL语句，和整个项目新的数据库表结构保持一致. 需要根据实际情况更新。



## 规则3：新增或修改业务功能时编写教程

如果新增或修改了业务功能，需要在 `/ai/业务功能学习` 目录下新建或修改md文件，为Java小白详细写一份教程，结合源码讲解整个业务内容是如何实现的，帮助Java小白读懂源代码。要讲清楚详细的实现的逻辑，相关的类如何协作，用户在客户端使用某个功能时，底层发生了什么，详细的讲清楚。对于这个业务功能涉及的技术栈和知识点，你要扮演老师的角色，详细给这个java小白讲解，写进教程文档里面。同时,有相关的设计亮点也要讲清楚,以便小白答辩用.

## 规则4：编写单元测试和进行浏览器测试

如果修改了后端代码, 尤其是新增或修改了接口, 就必须编写对应的单元测试. 必须同时通过编译,通过单元测试,通过浏览器测试. 测试通过, 才可以交付,否则就要debug。

## 规则5：和修bug相关的要求

1. 每次提出修bug的要求后, 修完bug改完代码, 必须同时通过编译,通过单元测试,通过浏览器测试. 测试通过, 才可以交付,否则就要debug。
2. 只要我提出修bug的要求,并且在提示词里明确给你贴出了问题的响应体,并且在响应体里包含了TraceId, 则必须查看后端日志, 以确定问题所在.尤其必须: 先打开详细日志文件(文件的绝对路径见下文) , 然后优先根据响应体里的TraceId在日志文件中搜索, 找到对应的日志, 确认问题所在.
后端详细日志地址`D:\Program Files (x86)\Backend-Study\project-study\MyBilibili\my-bilibili\backend\logs\my-bilibili.log`
后端错误日志地址`D:\Program Files (x86)\Backend-Study\project-study\MyBilibili\my-bilibili\backend\logs\my-bilibili-error.log`


## 规则6：修改完源代码后启动或重启服务

- 每次修改完源代码后，如有需要，必须帮用户启动或重启对应的服务（前后端、Nginx），以便用户直接刷新浏览器就可以测试功能。
- 不要再让用户手动重启服务了, 你自己在终端执行重启命令, 服务重启成功后再交付用户测试. 除非你自己启动服务失败了, 才需要指导用户手动重启服务.
- 启动命令详见`.trae/memory/project_memory.md`


## 规则7：开启新任务/新对话, 丢失了上下文, 必须阅读相关文件以熟悉项目记忆
1. 要阅读Trae的配置文件`/my-bilibili/.trae/memory/project_memory.md`,`/my-bilibili/.trae/context/project_context.md` 以熟悉项目记忆.
2. 除此之外, 还要要阅读
`D:\Program Files (x86)\Backend-Study\project-study\MyBilibili\ai\手写框架\纯 JDBC 数据库操作规范.md`
`D:\Program Files (x86)\Backend-Study\project-study\MyBilibili\ai\手写框架\手写框架底层架构总结.md`

 以熟悉项目的底层框架(本项目没有SSM框架, 而是手写框架).
3. 要阅读`/my-bilibili/PROJECT_OVERVIEW.md` 以熟悉项目的整体架构和功能模块.
4. 要阅读`/my-bilibili/backend/api-doc.md` 以熟悉项目的接口文档.
5. 要阅读`/my-bilibili/更新日志.md` 以熟悉项目的历史更新.
6. 要阅读`D:\Program Files (x86)\Backend-Study\project-study\MyBilibili\my-bilibili\backend\init.sql` 以熟悉项目的数据库表结构.

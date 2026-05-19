# MyBilibili 用户模块 API 接口文档

## 基础信息

- **Base URL**: `/api/user`
- **数据格式**: JSON
- **字符编码**: UTF-8
- **认证方式**: JWT Token（通过 `Authorization` 请求头传递，格式：`Bearer {token}`）
- **权限模型**: RBAC0（基于角色的访问控制，用户→角色→权限）

## 统一响应格式

所有接口返回统一的 JSON 格式：

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "traceId": "xxx-xxx-xxx"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 状态码，0表示成功，非0表示错误 |
| message | string | 响应信息 |
| data | object | 业务数据，失败时为null |
| traceId | string | 全链路追踪ID |

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| 0 | 成功 |
| 40000 | 请求参数错误 |
| 40001 | 手机号已注册 |
| 40002 | 两次输入的密码不一致 |
| 40003 | 邀请码错误 |
| 40004 | 旧密码错误 |
| 40100 | 未登录 |
| 40101 | 无权限（RBAC0权限校验不通过） |
| 40102 | 手机号或密码错误 |
| 40301 | 账号已被封禁 |
| 50000 | 系统内部异常 |
| 50002 | 文件上传失败 |

---

## 1. 用户注册

### 请求信息

- **URL**: `POST /api/user/register`
- **Content-Type**: `application/json`
- **是否需要登录**: 否
- **所需权限**: 无（公开接口）

### 请求参数

```json
{
  "phone": "13812345678",
  "password": "123456",
  "confirmPassword": "123456",
  "nickname": "小明",
  "role": 0,
  "inviteCode": "123456"
}
```

| 字段 | 类型 | 必填 | 说明 | 校验规则 |
|------|------|------|------|----------|
| phone | string | 是 | 手机号 | 1开头，11位数字 |
| password | string | 是 | 密码 | 6-12位 |
| confirmPassword | string | 是 | 确认密码 | 必须与password一致 |
| nickname | string | 是 | 昵称 | 1-20位，中英文、数字、下划线 |
| role | int | 否 | 角色 | 0-普通用户（默认），2-管理员 |
| inviteCode | string | 条件必填 | 邀请码 | 管理员注册时必填，6位数字，默认123456 |

### RBAC0 角色自动分配

注册成功后，系统自动根据 `role` 字段在 `user_role` 表中分配 RBAC 角色：
- `role=0`（普通用户）→ 分配 `user` 角色（role_id=1）
- `role=2`（管理员）→ 分配 `admin` 角色（role_id=2）

### 响应示例

**成功响应**:
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1,
    "phone": "13812345678",
    "nickname": "小明",
    "avatar": null,
    "role": 0,
    "createdAt": "2024-01-01T12:00:00"
  },
  "traceId": "xxx-xxx-xxx"
}
```

**失败响应**:
```json
{
  "code": 40001,
  "message": "手机号已注册",
  "data": null,
  "traceId": "xxx-xxx-xxx"
}
```

---

### 1.5 获取幂等性Token

- **URL**: `GET /api/user/token`
- **是否需要登录**: 否（公开接口）
- **所需权限**: 无
- **说明**: 获取幂等性Token，用于防止重复提交。前端在提交表单前先调用此接口获取Token，提交时在Header中带上 `X-Idempotent-Token`。Token有效期5分钟。

**响应数据**:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "token": "550e8400-e29b-41d4-a716-446655440000",
    "ttl": 300
  },
  "traceId": "xxx-xxx-xxx"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| token | string | 幂等性Token（UUID格式） |
| ttl | int | Token有效期（秒），默认300秒（5分钟） |

**使用方式**:
1. 进入表单页时，调用 `GET /api/user/token` 获取Token
2. 提交表单时，在请求Header中添加 `X-Idempotent-Token: {token}`
3. 后端校验Token：首次请求通过，重复请求返回"请勿重复提交"

---

### 7. 编辑动态

- **URL**: `POST /api/post/{postId}/edit`
- **Content-Type**: `multipart/form-data`
- **是否需要登录**: 是
- **所需权限**: `post:update`
- **说明**: 编辑动态内容和图片。existingImages为保留的已有图片文件名（逗号分隔），images为新上传的图片文件。最终图片 = existingImages + 新上传图片。

**请求参数**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | string | 否 | 修改后的文字内容 |
| existingImages | string | 否 | 保留的已有图片URL（逗号分隔） |
| images | file[] | 否 | 新上传的图片文件（最多9张） |

**响应数据**:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1,
    "content": "修改后的内容",
    "images": ["/upload/img1.jpg", "/upload/new_img.jpg"],
    "userId": 1,
    "nickname": "testUser",
    "avatar": "/upload/avatar.jpg",
    "likeCount": 5,
    "commentCount": 3,
    "isLiked": true,
    "createdAt": "2026-05-11T12:00:00"
  }
}
```

---

## 2. 用户登录

### 请求信息

- **URL**: `POST /api/user/login`
- **Content-Type**: `application/json`
- **是否需要登录**: 否
- **所需权限**: 无（公开接口）

### 请求参数

```json
{
  "phone": "13812345678",
  "password": "123456"
}
```

| 字段 | 类型 | 必填 | 说明 | 校验规则 |
|------|------|------|------|----------|
| phone | string | 是 | 手机号 | 1开头，11位数字 |
| password | string | 是 | 密码 | 6-12位 |

### 响应示例

**成功响应**:
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjEsInJvbGUiOjB9.xxx",
    "expiresIn": 1800,
    "user": {
      "id": 1,
      "phone": "13812345678",
      "nickname": "小明",
      "avatar": null,
      "role": 0,
      "createdAt": "2024-01-01T12:00:00"
    }
  },
  "traceId": "xxx-xxx-xxx"
}
```

**失败响应**:
```json
{
  "code": 40102,
  "message": "手机号或密码错误",
  "data": null,
  "traceId": "xxx-xxx-xxx"
}
```

---

## 3. 查看个人信息

### 请求信息

- **URL**: `POST /api/user/profile`
- **Content-Type**: `application/json`
- **是否需要登录**: 是
- **所需权限**: `user:profile:view`

### 请求头

```
Authorization: Bearer {token}
```

### 请求参数

无

### 响应示例

**成功响应**:
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1,
    "phone": "13812345678",
    "nickname": "小明",
    "avatar": "abc123def456.jpg",
    "role": 0,
    "createdAt": "2024-01-01T12:00:00"
  },
  "traceId": "xxx-xxx-xxx"
}
```

**权限不足响应**:
```json
{
  "code": 40101,
  "message": "无权限",
  "data": null,
  "traceId": "xxx-xxx-xxx"
}
```

---

## 4. 修改个人信息

### 请求信息

- **URL**: `PUT /api/user/profile`
- **Content-Type**: `application/json`
- **是否需要登录**: 是
- **所需权限**: `user:profile:update`

### 请求头

```
Authorization: Bearer {token}
```

### 请求参数

所有字段均为可选，只传需要修改的字段：

```json
{
  "nickname": "新昵称",
  "newPhone": "13987654321",
  "oldPassword": "123456",
  "newPassword": "654321",
  "confirmNewPassword": "654321",
  "newAvatar": "newfile123.jpg"
}
```

| 字段 | 类型 | 必填 | 说明 | 校验规则 |
|------|------|------|------|----------|
| nickname | string | 否 | 新昵称 | 1-20位，中英文、数字、下划线 |
| newPhone | string | 否 | 新手机号 | 1开头，11位数字，需验证oldPassword |
| oldPassword | string | 条件必填 | 旧密码 | 修改手机号或密码时必填 |
| newPassword | string | 否 | 新密码 | 6-12位，需验证oldPassword |
| confirmNewPassword | string | 条件必填 | 确认新密码 | 修改密码时必填，必须与newPassword一致 |
| newAvatar | string | 否 | 新头像文件名 | 上传头像接口返回的文件名 |

### 响应示例

**成功响应**:
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1,
    "phone": "13987654321",
    "nickname": "新昵称",
    "avatar": "newfile123.jpg",
    "role": 0,
    "createdAt": "2024-01-01T12:00:00"
  },
  "traceId": "xxx-xxx-xxx"
}
```

---

## 5. 上传头像

### 请求信息

- **URL**: `POST /api/user/avatar`
- **Content-Type**: `multipart/form-data`
- **是否需要登录**: 是
- **所需权限**: `user:avatar:upload`

### 请求头

```
Authorization: Bearer {token}
```

### 请求参数

| 字段 | 类型 | 必填 | 说明 | 校验规则 |
|------|------|------|------|----------|
| file | file | 是 | 头像文件 | 只允许图片格式（jpg/png/gif/webp），最大10MB |

### 响应示例

**成功响应**:
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "avatar": "a1b2c3d4e5f6.jpg"
  },
  "traceId": "xxx-xxx-xxx"
}
```

---

## 6. 退出登录

### 请求信息

- **URL**: `POST /api/user/logout`
- **Content-Type**: `application/json`
- **是否需要登录**: 是
- **所需权限**: `user:logout`

### 请求头

```
Authorization: Bearer {token}
```

### 请求参数

无

### 响应示例

**成功响应**:
```json
{
  "code": 0,
  "message": "ok",
  "data": null,
  "traceId": "xxx-xxx-xxx"
}
```

---

## RBAC0 权限体系说明

本项目采用 RBAC0（Role-Based Access Control）权限模型，通过"用户→角色→权限"三表关联实现细粒度权限控制。

### 数据模型

```
用户(user) ──多对多── 用户角色(user_role) ──多对多── 角色(role)
                                                          │
                                                    角色权限(role_permission)
                                                          │
                                                    权限(permission)
```

### 权限鉴权流程

1. 请求到达 AuthInterceptor，检查方法/类上是否有 `@RequirePermission` 注解
2. 若有，从 Authorization 头提取 Token，解析 JWT，从 Redis 获取用户信息
3. 通过 `PermissionDao.findPermissionCodesByUserId()` 查询用户的所有权限码
4. 判断用户权限列表是否包含 `@RequirePermission` 要求的权限码
5. 不包含则返回 40101（无权限），包含则放行

### 权限码定义

权限码命名规范：`资源:操作`

| 权限码 | 名称 | 分配给 |
|--------|------|--------|
| `user:profile:view` | 查看个人信息 | user、admin |
| `user:profile:update` | 修改个人信息 | user、admin |
| `user:avatar:upload` | 上传头像 | user、admin |
| `user:logout` | 退出登录 | user、admin |
| `user:ban` | 封禁用户 | admin |
| `video:delete` | 删除视频 | admin |
| `comment:delete` | 删除评论 | admin |

### 角色定义

| 角色代码 | 角色名称 | 拥有权限 |
|----------|----------|----------|
| `user` | 普通用户 | user:profile:view、user:profile:update、user:avatar:upload、user:logout |
| `admin` | 管理员 | 全部权限（含 user:ban、video:delete、comment:delete） |

### 接口权限速查表

| 接口 | HTTP方法 | 路径 | 所需权限 |
|------|----------|------|----------|
| 用户注册 | POST | /api/user/register | 无（公开） |
| 用户登录 | POST | /api/user/login | 无（公开） |
| 获取幂等性Token | GET | /api/user/token | 无（公开） |
| 查看个人信息 | POST | /api/user/profile | `user:profile:view` |
| 修改个人信息 | PUT | /api/user/profile | `user:profile:update` |
| 上传头像 | POST | /api/user/avatar | `user:avatar:upload` |
| 退出登录 | POST | /api/user/logout | `user:logout` |

---

## Token 刷新机制说明

- JWT令牌本身不设过期时间（exp字段），主要依靠Redis的TTL控制会话有效期
- Token在Redis中的key格式为 `jwttoken:{token}`，value为用户信息的JSON字符串
- 默认TTL为30分钟（1800秒）
- 每次请求通过鉴权后，自动刷新Redis中Token的TTL（活跃用户自动续期）
- 如果Redis中找不到Token，则认为登录已过期，返回401错误

---

## 视频模块 API

### 基础信息

- **Base URL**: `/api/video`
- **认证方式**: JWT Token（发布和删除需要登录）

---

### 1. 视频列表

- **URL**: `GET /api/video/list`
- **是否需要登录**: 否
- **查询参数**: `?page=1&size=10`

**响应示例**:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "list": [
      {
        "id": 1,
        "title": "视频标题",
        "coverUrl": "cover.jpg",
        "userId": 1,
        "userNickname": "发布者",
        "userAvatar": "avatar.jpg",
        "viewCount": 100,
        "likeCount": 10,
        "createdAt": "2026-05-06T12:00:00"
      }
    ],
    "total": 50,
    "page": 1,
    "size": 10
  },
  "traceId": "xxx"
}
```

---

### 2. 视频详情

- **URL**: `GET /api/video/detail/{id}`
- **是否需要登录**: 否
- **说明**: 每次请求自动递增播放量

**响应示例**:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1,
    "title": "视频标题",
    "description": "视频简介",
    "coverUrl": "cover.jpg",
    "videoUrl": "video.mp4",
    "userId": 1,
    "userNickname": "发布者",
    "userAvatar": "avatar.jpg",
    "viewCount": 101,
    "likeCount": 10,
    "createdAt": "2026-05-06T12:00:00"
  },
  "traceId": "xxx"
}
```

---

### 3. 发布视频

- **URL**: `POST /api/video/publish`
- **Content-Type**: `multipart/form-data`
- **是否需要登录**: 是

**请求参数**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | string | 是 | 视频标题 |
| description | string | 否 | 视频简介 |
| cover | file | 否 | 封面图片 |
| video | file | 是 | 视频文件 |

---

### 4. 删除视频

- **URL**: `DELETE /api/video/{id}`
- **是否需要登录**: 是
- **说明**: 仅作者可删除自己的视频

---

## 评论模块 API

### 基础信息

- **Base URL**: `/api/comment`
- **认证方式**: JWT Token（发表和删除需要登录）

---

### 1. 评论列表（热门排序 + 游标分页 + 树形结构）

- **URL**: `GET /api/comment/list`
- **是否需要登录**: 否（公开接口）

**请求参数**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| targetType | int | 是 | 目标类型：1-视频，2-动态 |
| targetId | long | 是 | 目标ID |
| sort | string | 否 | 排序方式，默认hot（热门） |
| cursor | int | 否 | 游标（上一页最后一条评论的likeCount，首次不传） |
| cursorId | long | 否 | 游标对应的评论ID（首次不传） |
| size | int | 否 | 每页数量，默认10 |

**响应示例**:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "list": [
      {
        "id": 1,
        "content": "评论内容",
        "userId": 1,
        "nickname": "评论者",
        "avatar": "/upload/xxx.jpg",
        "targetType": 1,
        "targetId": 1,
        "parentId": null,
        "likeCount": 5,
        "isLiked": null,
        "hasMoreReplies": true,
        "replies": [
          {
            "id": 2,
            "content": "回复内容",
            "userId": 2,
            "nickname": "回复者",
            "avatar": "/upload/xxx.jpg",
            "parentId": 1,
            "likeCount": 3,
            "replies": []
          }
        ],
        "createdAt": "2026-05-06T12:00:00"
      }
    ],
    "nextCursor": 5,
    "nextCursorId": 1
  },
  "traceId": "xxx"
}
```

**说明**:
- 顶层评论按点赞数降序排列（热门排序）
- 每个顶层评论最多展示3条热门子回复，`hasMoreReplies=true` 表示还有更多
- 游标分页：使用 nextCursor 和 nextCursorId 请求下一页

---

### 2. 发表评论

- **URL**: `POST /api/comment/create`
- **Content-Type**: `application/json`
- **是否需要登录**: 是
- **所需权限**: `comment:create`

**请求参数**:

```json
{
  "targetType": 1,
  "targetId": 1,
  "content": "评论内容",
  "parentId": null
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| targetType | int | 是 | 目标类型：1-视频，2-动态 |
| targetId | long | 是 | 目标ID |
| content | string | 是 | 评论内容 |
| parentId | long | 否 | 父评论ID（回复时使用） |

---

### 3. 获取子回复列表（展开更多回复，支持游标分页）

- **URL**: `GET /api/comment/replies/{parentId}`
- **是否需要登录**: 否

**说明**: 获取某条评论的直接子回复（不递归子孙），支持游标分页。每次加载一页，点击"展开更多回复"继续加载下一页。

**请求参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| parentId | long | 是 | 父评论ID（路径参数） |
| cursor | int | 否 | 游标（上一页最后一条的likeCount，首次请求不传） |
| cursorId | long | 否 | 游标对应的评论ID（首次请求不传） |
| size | int | 否 | 每页数量（默认10，最大50） |

**响应示例**:

```json
{
  "code": 20000,
  "data": {
    "list": [
      {
        "id": 21,
        "content": "回复内容",
        "userId": 2,
        "nickname": "回复者",
        "avatar": "/upload/xxx.jpg",
        "parentId": 3,
        "likeCount": 1,
        "hasMoreReplies": true,
        "replies": [],
        "createdAt": "2026-05-10T22:01:21"
      }
    ],
    "nextCursor": 1,
    "nextCursorId": 21
  },
  "message": "ok"
}
```

**字段说明**:

| 字段 | 说明 |
|------|------|
| list | 直接子回复列表（不递归子孙） |
| list[].hasMoreReplies | 该子回复是否还有自己的子回复（true则显示"展开更多回复"按钮） |
| list[].replies | 始终为空数组（子回复的子回复需要单独请求加载） |
| nextCursor | 下一页游标（null表示没有更多了） |
| nextCursorId | 下一页游标对应的评论ID |

---

### 4. 删除评论

- **URL**: `DELETE /api/comment/{commentId}`
- **是否需要登录**: 是
- **所需权限**: `comment:delete`
- **说明**: 普通用户只能删除自己的评论，管理员可删除任何评论

---

## 关注模块 API

### 基础信息

- **Base URL**: `/api/follow`
- **认证方式**: JWT Token（关注/取关和状态查询需要登录）

---

### 1. 关注/取关

- **URL**: `POST /api/follow/toggle`
- **Content-Type**: `application/json`
- **是否需要登录**: 是

**请求参数**:

```json
{
  "followeeId": 2
}
```

**响应示例**:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "followed": true
  },
  "traceId": "xxx"
}
```

---

### 2. 查询关注状态

- **URL**: `GET /api/follow/status?followeeId=2`
- **是否需要登录**: 是

---

### 3. 关注列表

- **URL**: `GET /api/follow/following/{userId}`
- **是否需要登录**: 否

---

### 4. 粉丝列表

- **URL**: `GET /api/follow/followers/{userId}`
- **是否需要登录**: 否

---

## 点赞模块 API

### 基础信息

- **Base URL**: `/api/like`
- **认证方式**: JWT Token（点赞/取消和状态查询需要登录）

---

### 1. 点赞/取消

- **URL**: `POST /api/like/toggle`
- **Content-Type**: `application/json`
- **是否需要登录**: 是

**请求参数**:

```json
{
  "targetType": 1,
  "targetId": 1
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| targetType | int | 是 | 目标类型：1-视频，2-评论 |
| targetId | long | 是 | 目标ID |

---

### 2. 查询点赞状态

- **URL**: `GET /api/like/status?targetType=1&targetId=1`
- **是否需要登录**: 是

---

### 3. 获取点赞数

- **URL**: `GET /api/like/count?targetType=1&targetId=1`
- **是否需要登录**: 否

---

### 4. 一键二连

- **URL**: `POST /api/like/double-tap/{videoId}`
- **是否需要登录**: 是
- **所需权限**: `like:toggle`
- **说明**: 一键二连是单向操作，只添加不取消。如果未点赞则点赞，如果未收藏进默认收藏夹则收藏。已点赞/已收藏则保持不变。

**路径参数**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| videoId | long | 是 | 视频ID |

**响应示例**:

首次一键二连（未点赞未收藏）:
```json
{
  "code": 0,
  "data": {
    "liked": true,
    "favorited": true,
    "message": "已点赞，已收藏"
  },
  "message": "ok",
  "traceId": "xxx"
}
```

再次一键二连（已点赞已收藏）:
```json
{
  "code": 0,
  "data": {
    "liked": true,
    "favorited": true,
    "message": "已点赞（之前已点赞），已收藏（之前已收藏）"
  },
  "message": "ok",
  "traceId": "xxx"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| liked | boolean | 是否已点赞（操作后状态） |
| favorited | boolean | 是否已收藏（操作后状态） |
| message | string | 操作描述信息 |

---

## 收藏模块 API

### 基础信息

- **Base URL**: `/api/favorite`
- **认证方式**: JWT Token（所有操作需要登录）

---

### 1. 收藏/取消

- **URL**: `POST /api/favorite/toggle`
- **Content-Type**: `application/json`
- **是否需要登录**: 是

**请求参数**:

```json
{
  "videoId": 1,
  "folderId": 1
}
```

---

### 2. 查询收藏状态

- **URL**: `GET /api/favorite/status?videoId=1`
- **是否需要登录**: 是

---

### 3. 我的收藏夹列表

- **URL**: `GET /api/favorite/folders`
- **是否需要登录**: 是

---

### 4. 创建收藏夹

- **URL**: `POST /api/favorite/folder`
- **Content-Type**: `application/json`
- **是否需要登录**: 是

**请求参数**:

```json
{
  "name": "收藏夹名称"
}
```

---

### 5. 删除收藏夹

- **URL**: `DELETE /api/favorite/folder/{id}`
- **是否需要登录**: 是
- **说明**: 仅作者可删除，默认收藏夹不可删除

---

### 6. 收藏夹视频列表

- **URL**: `GET /api/favorite/videos/{folderId}?page=1&size=10`
- **是否需要登录**: 是
- **所需权限**: `favorite:manage`
- **说明**: 获取指定收藏夹中的视频列表（分页），仅可查看自己的收藏夹，越权访问返回40101

---

## 动态模块 API

### 基础信息

- **Base URL**: `/api/post`
- **认证方式**: JWT Token（发布和删除需要登录）

---

### 1. 发布动态

- **URL**: `POST /api/post`
- **Content-Type**: `multipart/form-data`
- **是否需要登录**: 是
- **所需权限**: `post:create`
- **说明**: 支持纯文字、纯图片、或文字+图片。图片通过 `images` 字段上传，最多9张。

**请求参数**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | string | 否 | 动态文字内容（最多5000字） |
| images | file[] | 否 | 图片文件（最多9张，支持jpg/png/gif/webp，单张不超过5MB） |

> content 和 images 至少需要一个不为空。

**响应数据**:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1,
    "content": "今天天气真好！",
    "images": ["/upload/img1.jpg", "/upload/img2.jpg"],
    "userId": 1,
    "nickname": "testUser",
    "avatar": "/upload/avatar.jpg",
    "likeCount": 0,
    "commentCount": 0,
    "isLiked": false,
    "createdAt": "2026-05-11T12:00:00"
  }
}
```

---

### 2. 获取动态详情

- **URL**: `GET /api/post/{postId}`
- **是否需要登录**: 否（登录后返回 isLiked 字段）
- **说明**: 获取动态详情，包括内容、图片、发布者信息、点赞数、评论数

**响应数据**:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1,
    "content": "今天天气真好！",
    "images": ["/upload/img1.jpg"],
    "userId": 1,
    "nickname": "testUser",
    "avatar": "/upload/avatar.jpg",
    "likeCount": 5,
    "commentCount": 3,
    "isLiked": true,
    "createdAt": "2026-05-11T12:00:00"
  }
}
```

---

### 3. 用户动态列表（游标分页）

- **URL**: `GET /api/post/user/{userId}?cursor=xxx&size=10`
- **是否需要登录**: 否
- **说明**: 瀑布流场景使用游标分页，cursor 为上一页最后一条动态的 createdAt，首次请求不传。

**请求参数**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| cursor | string | 否 | 游标（上一页最后一条的createdAt，首次不传） |
| size | int | 否 | 每页数量，默认10 |

**响应数据**:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "list": [
      {
        "id": 1,
        "content": "动态内容",
        "images": ["/upload/img1.jpg"],
        "userId": 1,
        "nickname": "testUser",
        "avatar": "/upload/avatar.jpg",
        "likeCount": 0,
        "commentCount": 0,
        "isLiked": null,
        "createdAt": "2026-05-11T12:00:00"
      }
    ],
    "nextCursor": "2026-05-11T12:00:00"
  }
}
```

---

### 4. 删除动态

- **URL**: `DELETE /api/post/{postId}`
- **是否需要登录**: 是
- **所需权限**: `post:delete`
- **说明**: 仅作者可删除自己的动态（软删除）

---

### 5. 动态点赞

- **URL**: `POST /api/like/toggle`
- **Content-Type**: `application/json`
- **是否需要登录**: 是
- **所需权限**: `like:toggle`
- **说明**: 点赞/取消点赞切换，targetType=3 表示动态点赞

**请求参数**:

```json
{
  "targetType": 3,
  "targetId": 1
}
```

**响应数据**:

```json
{
  "code": 0,
  "message": "ok",
  "data": true
}
```

> data 为 true 表示点赞成功，false 表示取消点赞成功。

---

### 6. 动态评论

- **URL**: `POST /api/comment/create`
- **Content-Type**: `application/json`
- **是否需要登录**: 是
- **所需权限**: `comment:create`
- **说明**: 动态评论复用评论模块，targetType=2 表示动态评论

**请求参数**:

```json
{
  "targetType": 2,
  "targetId": 1,
  "content": "评论内容"
}
```

---

## Feed流 API

### 基础信息

- **Base URL**: `/api/feed`
- **认证方式**: JWT Token（需要登录）

---

### 1. 关注Feed流

- **URL**: `GET /api/feed/following?cursor=2026-05-06T12:00:00&limit=10`
- **是否需要登录**: 是
- **所需权限**: `feed:view`
- **说明**: 游标分页，返回关注用户的动态，按时间倒序。首次请求不传cursor。

---

## 用户扩展 API

### 基础信息

- **Base URL**: `/api/user`

---

### 1. 查看其他用户公开信息

- **URL**: `GET /api/user/public/{id}`
- **是否需要登录**: 否

---

### 2. 查看用户视频列表

- **URL**: `GET /api/user/videos/{id}`
- **是否需要登录**: 否

---

### 3. 查看用户动态列表

- **URL**: `GET /api/user/posts/{id}`
- **是否需要登录**: 否

---

## 文件上传 API

### 基础信息

- **Base URL**: `/api/file`
- **是否需要登录**: 是

---

### 1. 上传视频文件

- **URL**: `POST /api/file/upload/video`
- **Content-Type**: `multipart/form-data`
- **是否需要登录**: 是
- **说明**: 上传视频文件，支持 MP4、AVI、MOV、MKV、FLV、WMV、WebM，大小不超过 500MB

**请求参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| file | File | 视频文件 |

**响应示例**:
```json
{
  "code": 0,
  "data": "abc123def456.mp4",
  "message": "ok"
}
```

---

### 2. 上传封面图片

- **URL**: `POST /api/file/upload/cover`
- **Content-Type**: `multipart/form-data`
- **是否需要登录**: 是
- **说明**: 上传封面图片，支持 JPG、JPEG、PNG、GIF、WebP，大小不超过 5MB

**请求参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| file | File | 图片文件 |

---

## 搜索 API

### 基础信息

- **Base URL**: `/api/search`
- **是否需要登录**: 否

---

### 1. 综合搜索

- **URL**: `GET /api/search/all?keyword=xxx&page=1&size=10`
- **是否需要登录**: 否
- **说明**: 同时搜索视频（按标题）、动态（按内容）、用户（按昵称），返回混合结果列表及各类型总数

**响应示例**:
```json
{
  "code": 0,
  "data": {
    "videoTotal": 5,
    "postTotal": 3,
    "userTotal": 2,
    "list": [
      {
        "type": "video",
        "id": 1,
        "title": "测试视频",
        "cover": "/upload/cover/xxx.jpg",
        "avatar": "/upload/avatar/xxx.jpg",
        "nickname": "张三",
        "userId": 1,
        "description": "视频描述",
        "viewCount": 100,
        "likeCount": 10,
        "category": "学习",
        "createdAt": "2026-05-01T12:00:00"
      }
    ]
  }
}
```

---

### 2. 搜索视频

- **URL**: `GET /api/search/videos?keyword=xxx&page=1&size=10`
- **是否需要登录**: 否
- **说明**: 根据视频标题模糊搜索，支持分页

---

### 3. 搜索动态

- **URL**: `GET /api/search/posts?keyword=xxx&page=1&size=10`
- **是否需要登录**: 否
- **说明**: 根据动态内容模糊搜索，支持分页

---

### 4. 搜索用户

- **URL**: `GET /api/search/users?keyword=xxx&page=1&size=10`
- **是否需要登录**: 否
- **说明**: 根据用户昵称模糊搜索，支持分页

---

## 优惠券模块 API

> **Base URL**: `/api/coupon`

### 1. 获取幂等性Token（抢购前调用）

- **URL**: `GET /api/coupon/token`
- **是否需要登录**: 是
- **所需权限**: `coupon:grab`

**响应示例**:
```json
{
  "code": 0,
  "data": {
    "token": "550e8400-e29b-41d4-a716-446655440000",
    "ttl": 300
  }
}
```

**说明**: 进入抢购页面前调用，获取Token后在抢购请求Header中携带 `X-Idempotent-Token`。Token有效期5分钟。

---

### 2. 抢购优惠券（核心秒杀接口）

- **URL**: `POST /api/coupon/grab`
- **Content-Type**: `application/x-www-form-urlencoded`
- **是否需要登录**: 是
- **所需权限**: `coupon:grab`
- **请求Header**: `X-Idempotent-Token`（必填，由 /api/coupon/token 获取）

**请求参数**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| activityId | Long | 是 | 活动ID |

**响应示例（成功）**:
```json
{
  "code": 0,
  "data": {
    "resultCode": 1,
    "message": "恭喜！抢购成功！"
  }
}
```

**resultCode说明**:

| 值 | 含义 |
|----|------|
| 1 | 抢购成功 |
| -2 | 库存不足 |
| -3 | 超出每人限购数量 |
| -4 | 请勿重复抢购 |
| -999 | 限流保护（Sentinel触发） |

---

### 3. 活动详情

- **URL**: `GET /api/coupon/activity/{id}`
- **是否需要登录**: 否

**响应示例**:
```json
{
  "code": 0,
  "data": {
    "id": 1,
    "name": "618满100减20优惠券",
    "description": "限时优惠，手慢无！",
    "totalStock": 100,
    "remainStock": 87,
    "perUserLimit": 1,
    "startTime": "2026-06-01T00:00:00",
    "endTime": "2026-06-30T23:59:59",
    "status": 1
  }
}
```

---

### 4. 活动列表（进行中）

- **URL**: `GET /api/coupon/activities`
- **是否需要登录**: 否

---

### 5. 用户抢购记录

- **URL**: `GET /api/coupon/records?activityId=1`
- **是否需要登录**: 是
- **所需权限**: `coupon:records`
- **参数**: `activityId`（可选，不传则查询所有活动）

---

### 6. 创建优惠券活动（管理员）

- **URL**: `POST /api/coupon/create`
- **Content-Type**: `application/json`
- **是否需要登录**: 是
- **所需权限**: `coupon:create`（所有登录用户）

**请求体**:
```json
{
  "name": "618年中大促优惠券",
  "description": "限时限量，满100减20！",
  "totalStock": 100,
  "perUserLimit": 1,
  "startTime": "2026-06-01 00:00:00",
  "endTime": "2026-06-30 23:59:59"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 活动名称 |
| description | String | 否 | 活动描述 |
| totalStock | Integer | 是 | 优惠券总库存 |
| perUserLimit | Integer | 否 | 每人限购数量（0=不限） |
| startTime | String | 否 | 活动开始时间 |
| endTime | String | 否 | 活动结束时间 |

> **注意**: 创建后活动状态为"未发布"(0)，需调用 `/start` 接口发布才能让用户看到。

---

### 7. 发布/启动优惠券活动（管理员）

- **URL**: `POST /api/coupon/activity/{id}/start`
- **是否需要登录**: 是
- **所需权限**: `coupon:create`（所有登录用户）

**URL参数**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 活动ID |

**说明**: 将活动状态从"未发布"(0)改为"进行中"(1)，同时将库存预热到Redis（秒杀核心步骤）。

---

### 8. 查询所有活动（管理员）

- **URL**: `GET /api/coupon/all`
- **是否需要登录**: 是
- **所需权限**: `coupon:create`（所有登录用户）

**说明**: 查看所有活动（包括未发布、进行中、已结束），用于管理面板。

---

## 通知模块 API

> **Base URL**: `/api/notification`

### 1. 通知列表

- **URL**: `GET /api/notification/list?page=1&size=20`
- **是否需要登录**: 是
- **所需权限**: `notification:view`

---

### 2. 未读通知数（前端小红点）

- **URL**: `GET /api/notification/unread`
- **是否需要登录**: 是
- **所需权限**: `notification:view`

**响应示例**:
```json
{
  "code": 0,
  "data": {
    "count": 5
  }
}
```

---

### 3. 全部标记为已读

- **URL**: `POST /api/notification/read-all`
- **是否需要登录**: 是
- **所需权限**: `notification:read`

---

### 4. 单条标记为已读

- **URL**: `POST /api/notification/read/{id}`
- **是否需要登录**: 是
- **所需权限**: `notification:read`

---

## WebSocket 实时通知

- **连接地址**: `ws://localhost:8080/websocket/notification/{userId}`
- **说明**: 前端登录后建立WebSocket连接，服务端实时推送通知JSON消息

**推送消息格式**:
```json
{
  "id": 123,
  "type": "LIKE",
  "content": "张三赞了你的动态",
  "fromUserId": 1002,
  "targetId": 456,
  "time": "2026-05-12T10:30:00"
}
```

---

## 安全说明

1. **密码加密**: 使用BCrypt哈希加盐加密，数据库中不存储明文密码
2. **并发保护**: 注册时使用 `synchronized(phone.intern())` 防止并发重复注册
3. **输入校验**: 前后端都做参数校验，不能只依赖数据库约束
4. **唯一性校验**: 手机号等业务层查表做唯一性校验，不依赖数据库唯一键约束
5. **文件白名单**: 只允许上传图片格式文件，防止恶意上传
6. **RBAC0权限控制**: 通过用户→角色→权限三表关联实现细粒度权限鉴权，替代硬编码角色判断

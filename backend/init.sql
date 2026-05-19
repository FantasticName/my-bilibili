-- ============================================================
-- MyBilibili 数据库初始化脚本
-- 数据库: MySQL 8.0+
-- 字符集: utf8mb4
-- ============================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `my_bilibili` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `my_bilibili`;

-- ============================================================
-- 1. 用户表 `user`
-- ============================================================
CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
  `phone` VARCHAR(20) NOT NULL UNIQUE COMMENT '手机号，用于登录',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希（BCrypt加密，内含盐值）',
  `nickname` VARCHAR(50) COMMENT '昵称',
  `avatar` VARCHAR(255) COMMENT '头像URL',
  `role` TINYINT NOT NULL DEFAULT 0 COMMENT '角色：0-普通用户，1-博主，2-管理员（为二轮预留）',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-正常，1-封禁（二轮管理员功能）',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================================
-- 2. 视频表 `video`
-- ============================================================
CREATE TABLE IF NOT EXISTS `video` (
  `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '视频ID',
  `title` VARCHAR(255) NOT NULL COMMENT '视频标题',
  `description` TEXT COMMENT '视频简介',
  `cover_url` VARCHAR(255) COMMENT '封面图URL',
  `video_url` VARCHAR(255) NOT NULL COMMENT '视频文件URL',
  `category` VARCHAR(32) DEFAULT '搞笑' COMMENT '视频分区：美食/探店/科技/搞笑等',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '发布者ID（博主）',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-正常，1-下架（二轮管理员下架）',
  `view_count` BIGINT UNSIGNED DEFAULT 0 COMMENT '播放量',
  `like_count` BIGINT UNSIGNED DEFAULT 0 COMMENT '点赞数',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_category` (`category`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频表';

-- ============================================================
-- 3. 评论表 `comment`
-- ============================================================
CREATE TABLE IF NOT EXISTS `comment` (
  `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '评论ID',
  `content` TEXT NOT NULL COMMENT '评论内容',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '评论用户ID',
  `target_type` TINYINT NOT NULL DEFAULT 1 COMMENT '目标类型：1-视频，2-动态',
  `target_id` BIGINT UNSIGNED NOT NULL COMMENT '目标ID（视频ID或动态ID）',
  `parent_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '父评论ID（支持回复）',
  `like_count` INT UNSIGNED DEFAULT 0 COMMENT '点赞数',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-正常，1-删除（软删除，管理员可删）',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_target` (`target_type`, `target_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

-- ============================================================
-- 4. 关注表 `follow`
-- ============================================================
CREATE TABLE IF NOT EXISTS `follow` (
  `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '关注关系ID',
  `follower_id` BIGINT UNSIGNED NOT NULL COMMENT '关注者用户ID',
  `followee_id` BIGINT UNSIGNED NOT NULL COMMENT '被关注者用户ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  UNIQUE KEY `uk_follower_followee` (`follower_id`, `followee_id`),
  INDEX `idx_followee_id` (`followee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关注表';

-- ============================================================
-- 5. 点赞表（视频点赞 + 评论点赞，使用统一表）
-- ============================================================
CREATE TABLE IF NOT EXISTS `like_record` (
  `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '点赞记录ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '点赞用户ID',
  `target_type` TINYINT NOT NULL COMMENT '目标类型：1-视频，2-评论，3-动态',
  `target_id` BIGINT UNSIGNED NOT NULL COMMENT '目标ID（视频ID、评论ID或动态ID）',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`),
  INDEX `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞记录表';

-- ============================================================
-- 6. 收藏夹表 `favorite_folder`
-- ============================================================
CREATE TABLE IF NOT EXISTS `favorite_folder` (
  `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '收藏夹ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `name` VARCHAR(64) NOT NULL COMMENT '收藏夹名称',
  `is_default` TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认收藏夹：0-否，1-是',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY `uk_user_name` (`user_id`, `name`),
  INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏夹表';

-- ============================================================
-- 7. 收藏记录表 `favorite_record`
-- ============================================================
CREATE TABLE IF NOT EXISTS `favorite_record` (
  `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '收藏记录ID',
  `folder_id` BIGINT UNSIGNED NOT NULL COMMENT '收藏夹ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `target_type` TINYINT NOT NULL COMMENT '目标类型：1-视频',
  `target_id` BIGINT UNSIGNED NOT NULL COMMENT '目标ID（视频ID）',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  UNIQUE KEY `uk_folder_target` (`folder_id`, `target_type`, `target_id`),
  INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏记录表';

-- ============================================================
-- 8. 动态表 `post`
-- ============================================================
CREATE TABLE IF NOT EXISTS `post` (
  `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '动态ID',
  `content` TEXT COMMENT '动态内容',
  `images` VARCHAR(2048) DEFAULT NULL COMMENT '图片URL列表，逗号分隔',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '发布者ID',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-正常，1-删除',
  `like_count` INT UNSIGNED DEFAULT 0 COMMENT '点赞数',
  `comment_count` INT UNSIGNED DEFAULT 0 COMMENT '评论数',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_created_at` (`created_at`),
  FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态表';

-- ============================================================
-- 9. RBAC 相关表（为二轮管理员权限设计）
-- ============================================================

-- 权限表
CREATE TABLE IF NOT EXISTS `permission` (
  `id` INT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '权限ID',
  `code` VARCHAR(64) NOT NULL UNIQUE COMMENT '权限代码（如 video:delete）',
  `name` VARCHAR(64) NOT NULL COMMENT '权限名称',
  `description` VARCHAR(255) COMMENT '描述'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- 角色表
CREATE TABLE IF NOT EXISTS `role` (
  `id` INT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
  `code` VARCHAR(32) NOT NULL UNIQUE COMMENT '角色代码（如 admin）',
  `name` VARCHAR(32) NOT NULL COMMENT '角色名称'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 角色权限关联表
CREATE TABLE IF NOT EXISTS `role_permission` (
  `role_id` INT UNSIGNED NOT NULL,
  `permission_id` INT UNSIGNED NOT NULL,
  PRIMARY KEY (`role_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS `user_role` (
  `user_id` BIGINT UNSIGNED NOT NULL,
  `role_id` INT UNSIGNED NOT NULL,
  PRIMARY KEY (`user_id`, `role_id`),
  FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`role_id`) REFERENCES `role`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- ============================================================
-- 10. RBAC 初始数据
-- ============================================================

-- 角色初始数据
INSERT INTO `role` (`id`, `code`, `name`) VALUES (1, 'user', '普通用户') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `role` (`id`, `code`, `name`) VALUES (2, 'admin', '管理员') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 权限初始数据
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (1, 'user:profile:view', '查看个人信息', '登录用户查看自己的个人信息') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (2, 'user:profile:update', '修改个人信息', '登录用户修改昵称、手机号、密码') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (3, 'user:avatar:upload', '上传头像', '登录用户上传头像') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (4, 'user:logout', '退出登录', '登录用户退出登录') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (5, 'user:ban', '封禁用户', '管理员封禁/解封用户') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (6, 'video:delete', '删除视频', '管理员下架视频') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (7, 'comment:delete', '删除评论', '管理员删除评论') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (8, 'video:publish', '发布视频', '博主发布视频') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (9, 'comment:create', '发表评论', '用户发表评论') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (10, 'follow:manage', '关注管理', '用户关注/取关其他用户') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (11, 'like:toggle', '点赞操作', '用户点赞/取消点赞') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (12, 'favorite:manage', '收藏管理', '用户管理收藏夹和收藏视频') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (13, 'post:create', '发布动态', '用户发布动态') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (14, 'post:delete', '删除动态', '用户删除自己的动态') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (17, 'post:update', '编辑动态', '用户编辑自己的动态') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (15, 'file:upload', '文件上传', '用户上传封面和视频文件') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (16, 'feed:view', '查看Feed流', '登录用户查看关注动态Feed流') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 角色权限关联：普通用户拥有基本权限
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 1) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 2) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 3) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 4) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 8) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 9) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 10) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 11) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 12) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 13) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 14) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 15) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 16) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 17) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

-- 角色权限关联：管理员拥有所有权限
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 1) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 2) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 3) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 4) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 5) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 6) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 7) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 8) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 9) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 10) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 11) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 12) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 13) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 14) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 15) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 16) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 17) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

-- ============================================================
-- 11. 优惠券活动表 `coupon_activity`（秒杀模块）
-- ============================================================
CREATE TABLE IF NOT EXISTS `coupon_activity` (
  `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '活动ID',
  `name` VARCHAR(128) NOT NULL COMMENT '活动名称（如"618满100减20优惠券"）',
  `description` VARCHAR(512) COMMENT '活动描述',
  `total_stock` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '总库存（创建后不可变）',
  `remain_stock` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '剩余库存（动态减少）',
  `per_user_limit` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '每人限抢数量（0=不限制）',
  `grab_limit_type` TINYINT NOT NULL DEFAULT 1 COMMENT '库存扣减上限类型（0=不限制，1=限制每人最多perUserLimit个）',
  `start_time` DATETIME NOT NULL COMMENT '活动开始时间',
  `end_time` DATETIME NOT NULL COMMENT '活动结束时间',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '活动状态（0=未开始，1=进行中，2=已结束）',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券活动表';

-- ============================================================
-- 12. 优惠券抢购记录表 `coupon_record`（秒杀模块）
-- ============================================================
CREATE TABLE IF NOT EXISTS `coupon_record` (
  `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID（谁抢到了优惠券）',
  `activity_id` BIGINT UNSIGNED NOT NULL COMMENT '活动ID（抢的是哪个活动）',
  `coupon_code` VARCHAR(32) DEFAULT NULL COMMENT '优惠券码（UUID格式16位大写字母+数字，抢购成功时生成）',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '优惠券状态（0=有效，1=已使用，2=已过期）',
  `grab_time` DATETIME NOT NULL COMMENT '抢购时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券抢购记录表';

-- ============================================================
-- 13. 消息通知表 `notification`（消息推送模块）
-- ============================================================
CREATE TABLE IF NOT EXISTS `notification` (
  `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '通知ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '接收通知的用户ID',
  `from_user_id` BIGINT UNSIGNED COMMENT '触发通知的用户ID（系统通知时为NULL）',
  `notify_type` VARCHAR(32) NOT NULL COMMENT '通知类型（FOLLOW/COMMENT/LIKE/SYSTEM）',
  `target_id` BIGINT UNSIGNED COMMENT '关联数据ID（如动态ID、视频ID，点击跳转用）',
  `target_type` TINYINT COMMENT '关联数据类型（0=动态，1=视频，2=评论）',
  `content` VARCHAR(255) NOT NULL COMMENT '通知内容（如"XXX关注了你"）',
  `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读（0=未读，1=已读）',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态（0=正常，1=已删除）',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知表';

-- ============================================================
-- 14. 用户行为表 `user_behavior`（推荐系统数据源）
-- ============================================================
CREATE TABLE IF NOT EXISTS `user_behavior` (
  `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `behavior_type` VARCHAR(32) NOT NULL COMMENT '行为类型（VIEW/LIKE/COMMENT/FOLLOW/SHARE）',
  `target_id` BIGINT UNSIGNED NOT NULL COMMENT '目标ID（视频ID/动态ID/用户ID）',
  `target_type` TINYINT NOT NULL COMMENT '目标类型（0=动态，1=视频，2=评论，3=用户）',
  `weight` INT NOT NULL DEFAULT 1 COMMENT '行为权重（VIEW=1, LIKE=3, COMMENT=5, FOLLOW=10）',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '行为时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为表';

-- ============================================================
-- 15. 新增权限数据（优惠券、通知、推荐）
-- ============================================================
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (18, 'coupon:grab', '抢购优惠券', '用户参与优惠券秒杀抢购') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (19, 'coupon:records', '查看抢购记录', '用户查看自己的优惠券抢购记录') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (20, 'notification:view', '查看通知', '用户查看自己的消息通知') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (21, 'notification:read', '标记已读', '用户标记通知为已读') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (22, 'coupon:create', '创建优惠券活动', '用户创建和发布优惠券秒杀活动') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 为普通用户分配新权限
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 18) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 19) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 20) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 21) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 22) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

-- 为管理员分配新权限
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 18) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 19) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 20) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 21) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 22) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

-- ============================================================
-- 基于已有的数据库修改脚本
-- 用途：在已有数据库基础上执行增量修改，对齐新的 init.sql
-- 使用方法：在MySQL命令行中 source 此文件
-- ============================================================

USE `my_bilibili`;

-- ============================================================
-- 1. 新增优惠券活动表
-- ============================================================
CREATE TABLE IF NOT EXISTS `coupon_activity` (
  `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '活动ID',
  `name` VARCHAR(128) NOT NULL COMMENT '活动名称',
  `description` VARCHAR(512) COMMENT '活动描述',
  `total_stock` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '总库存',
  `remain_stock` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '剩余库存',
  `per_user_limit` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '每人限抢数量',
  `grab_limit_type` TINYINT NOT NULL DEFAULT 1 COMMENT '库存扣减上限类型',
  `start_time` DATETIME NOT NULL COMMENT '活动开始时间',
  `end_time` DATETIME NOT NULL COMMENT '活动结束时间',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '活动状态',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券活动表';

-- ============================================================
-- 2. 新增优惠券抢购记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS `coupon_record` (
  `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `activity_id` BIGINT UNSIGNED NOT NULL COMMENT '活动ID',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '优惠券状态',
  `grab_time` DATETIME NOT NULL COMMENT '抢购时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券抢购记录表';

-- ============================================================
-- 3. 新增消息通知表
-- ============================================================
CREATE TABLE IF NOT EXISTS `notification` (
  `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '通知ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '接收通知的用户ID',
  `from_user_id` BIGINT UNSIGNED COMMENT '触发通知的用户ID',
  `notify_type` VARCHAR(32) NOT NULL COMMENT '通知类型',
  `target_id` BIGINT UNSIGNED COMMENT '关联数据ID',
  `target_type` TINYINT COMMENT '关联数据类型',
  `content` VARCHAR(255) NOT NULL COMMENT '通知内容',
  `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知表';

-- ============================================================
-- 4. 新增用户行为表
-- ============================================================
CREATE TABLE IF NOT EXISTS `user_behavior` (
  `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `behavior_type` VARCHAR(32) NOT NULL COMMENT '行为类型',
  `target_id` BIGINT UNSIGNED NOT NULL COMMENT '目标ID',
  `target_type` TINYINT NOT NULL COMMENT '目标类型',
  `weight` INT NOT NULL DEFAULT 1 COMMENT '行为权重',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '行为时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为表';

-- ============================================================
-- 5. 新增权限数据
-- ============================================================
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (18, 'coupon:grab', '抢购优惠券', '用户参与优惠券秒杀抢购') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (19, 'coupon:records', '查看抢购记录', '用户查看自己的优惠券抢购记录') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (20, 'notification:view', '查看通知', '用户查看自己的消息通知') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (21, 'notification:read', '标记已读', '用户标记通知为已读') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- ============================================================
-- 6. 为普通用户分配新权限
-- ============================================================
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 18) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 19) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 20) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 21) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

-- ============================================================
-- 7. 为管理员分配新权限
-- ============================================================
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 18) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 19) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 20) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 21) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

-- ============================================================
-- 8. 新增 coupon:create 权限（管理员创建活动）
-- ============================================================
INSERT INTO `permission` (`id`, `code`, `name`, `description`) VALUES (22, 'coupon:create', '创建优惠券活动', '管理员创建和发布优惠券秒杀活动') ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 22) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

-- ============================================================
-- 9. 将 coupon:create 权限也分配给普通用户
-- ============================================================
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 22) ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

-- ============================================================
-- 10. coupon_record表新增coupon_code列（优惠券码）
-- ============================================================
ALTER TABLE `coupon_record` ADD COLUMN `coupon_code` VARCHAR(32) DEFAULT NULL COMMENT '优惠券码（UUID格式16位大写字母+数字，抢购成功时生成）' AFTER `activity_id`;
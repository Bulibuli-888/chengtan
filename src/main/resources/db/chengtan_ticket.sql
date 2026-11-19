-- ============================================================
-- 城探票务 v2 - 数据库初始化脚本
-- MySQL 8.0+ / utf8mb4
-- 说明：本脚本会重建 chengtan_v2 库中的业务表，请勿在生产库执行
-- ============================================================

CREATE DATABASE IF NOT EXISTS `chengtan_v2`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `chengtan_v2`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `ct_verify_log`;
DROP TABLE IF EXISTS `ct_reschedule`;
DROP TABLE IF EXISTS `ct_order_outbox`;
DROP TABLE IF EXISTS `ct_waitlist`;
DROP TABLE IF EXISTS `ct_ticket`;
DROP TABLE IF EXISTS `ct_ticket_order`;
DROP TABLE IF EXISTS `ct_attendee`;
DROP TABLE IF EXISTS `ct_venue_session`;
DROP TABLE IF EXISTS `ct_venue`;
DROP TABLE IF EXISTS `ct_venue_type`;
DROP TABLE IF EXISTS `ct_user`;

-- ------------------------------------------------------------
-- 用户
-- ------------------------------------------------------------
CREATE TABLE `ct_user` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `phone`       VARCHAR(20)     NOT NULL COMMENT '手机号',
    `nick_name`   VARCHAR(64)     NOT NULL DEFAULT '' COMMENT '昵称',
    `icon`        VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '头像',
    `role`        TINYINT         NOT NULL DEFAULT 0 COMMENT '0普通用户 1场馆运营',
    `status`      TINYINT         NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- ------------------------------------------------------------
-- 场馆分类
-- ------------------------------------------------------------
CREATE TABLE `ct_venue_type` (
    `id`          INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(32)  NOT NULL,
    `icon`        VARCHAR(255) NOT NULL DEFAULT '',
    `sort`        INT          NOT NULL DEFAULT 0,
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '场馆分类';

-- ------------------------------------------------------------
-- 场馆
-- ------------------------------------------------------------
CREATE TABLE `ct_venue` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(128)    NOT NULL,
    `type_id`     INT UNSIGNED    NOT NULL,
    `images`      VARCHAR(1024)   NOT NULL DEFAULT '',
    `address`     VARCHAR(255)    NOT NULL DEFAULT '',
    `x`           DOUBLE          NOT NULL DEFAULT 0 COMMENT '经度',
    `y`           DOUBLE          NOT NULL DEFAULT 0 COMMENT '纬度',
    `rating`      DECIMAL(2, 1)   NOT NULL DEFAULT 5.0,
    `status`      TINYINT         NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
    `description` VARCHAR(1024)   NOT NULL DEFAULT '',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_type_status` (`type_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '场馆';

-- ------------------------------------------------------------
-- 场次（票务的核心：库存按场次隔离）
-- ------------------------------------------------------------
CREATE TABLE `ct_venue_session` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `venue_id`        BIGINT UNSIGNED NOT NULL,
    `session_date`    DATE            NOT NULL COMMENT '演出/入园日期',
    `start_time`      TIME            NOT NULL,
    `end_time`        TIME            NOT NULL,
    `capacity`        INT             NOT NULL COMMENT '总容量',
    `sold`            INT             NOT NULL DEFAULT 0 COMMENT '已售',
    `price`           DECIMAL(10, 2)  NOT NULL,
    `status`          TINYINT         NOT NULL DEFAULT 0 COMMENT '0待开售 1售票中 2售罄 3已结束 4已取消',
    `sale_start_time` DATETIME        NOT NULL,
    `sale_end_time`   DATETIME        NOT NULL,
    `version`         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_venue_date_start` (`venue_id`, `session_date`, `start_time`),
    KEY `idx_status_sale` (`status`, `sale_start_time`),
    KEY `idx_venue_date` (`venue_id`, `session_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '场馆场次';

-- ------------------------------------------------------------
-- 出行人（实名信息只存哈希 + 掩码，不存明文证件号）
-- ------------------------------------------------------------
CREATE TABLE `ct_attendee` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT UNSIGNED NOT NULL,
    `name`          VARCHAR(64)     NOT NULL,
    `id_card_mask`  VARCHAR(32)     NOT NULL DEFAULT '' COMMENT '掩码，如 3301**********1234',
    `id_card_hash`  CHAR(64)        NOT NULL COMMENT 'SHA-256(userId + 证件号 + salt)',
    `phone`         VARCHAR(20)     NOT NULL DEFAULT '',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_idcard` (`user_id`, `id_card_hash`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '出行人';

-- ------------------------------------------------------------
-- 订单
-- ------------------------------------------------------------
CREATE TABLE `ct_ticket_order` (
    `id`            BIGINT UNSIGNED NOT NULL COMMENT '雪花ID',
    `order_no`      VARCHAR(32)     NOT NULL,
    `user_id`       BIGINT UNSIGNED NOT NULL,
    `venue_id`      BIGINT UNSIGNED NOT NULL,
    `session_id`    BIGINT UNSIGNED NOT NULL,
    `quantity`      INT             NOT NULL,
    `unit_price`    DECIMAL(10, 2)  NOT NULL,
    `total_amount`  DECIMAL(10, 2)  NOT NULL,
    `refund_amount` DECIMAL(10, 2)  NOT NULL DEFAULT 0,
    `status`        TINYINT         NOT NULL DEFAULT 10 COMMENT '10待支付 20已支付 30已完成 40已取消 50已退款 60已过期',
    `expire_time`   DATETIME        NOT NULL COMMENT '支付截止时间',
    `pay_time`      DATETIME        NULL,
    `cancel_time`   DATETIME        NULL,
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_create` (`user_id`, `create_time`),
    KEY `idx_session_status` (`session_id`, `status`),
    KEY `idx_status_expire` (`status`, `expire_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '票务订单';

-- ------------------------------------------------------------
-- 票实例（一张票一行：独立核销、独立退款）
-- active_flag 生成列 + 唯一索引 = 同一场次同一证件只能有一张有效票
-- ------------------------------------------------------------
CREATE TABLE `ct_ticket` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `ticket_no`    VARCHAR(32)     NOT NULL,
    `qr_token`     CHAR(32)        NOT NULL COMMENT '一次性核销令牌',
    `order_id`     BIGINT UNSIGNED NOT NULL,
    `order_no`     VARCHAR(32)     NOT NULL,
    `user_id`      BIGINT UNSIGNED NOT NULL,
    `venue_id`     BIGINT UNSIGNED NOT NULL,
    `session_id`   BIGINT UNSIGNED NOT NULL,
    `attendee_id`  BIGINT UNSIGNED NOT NULL,
    `status`       TINYINT         NOT NULL DEFAULT 10 COMMENT '10待使用 20已核销 30已退款 40已作废',
    `verify_time`  DATETIME        NULL,
    `verify_by`    BIGINT UNSIGNED NULL,
    `refund_time`  DATETIME        NULL,
    `active_flag`  TINYINT GENERATED ALWAYS AS (
        CASE WHEN `status` = 10 OR `status` = 20 THEN 1 ELSE NULL END
    ) STORED COMMENT '有效票标记，用于一证一票唯一约束',
    `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ticket_no` (`ticket_no`),
    UNIQUE KEY `uk_qr_token` (`qr_token`),
    UNIQUE KEY `uk_session_attendee_active` (`session_id`, `attendee_id`, `active_flag`),
    KEY `idx_order` (`order_id`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_session_status` (`session_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '票实例';

-- ------------------------------------------------------------
-- 候补队列（Redis ZSet 负责排序，DB 负责持久化）
-- ------------------------------------------------------------
CREATE TABLE `ct_waitlist` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `session_id`       BIGINT UNSIGNED NOT NULL,
    `user_id`          BIGINT UNSIGNED NOT NULL,
    `quantity`         INT             NOT NULL,
    `status`           TINYINT         NOT NULL DEFAULT 0 COMMENT '0排队中 1已通知待支付 2已转订单 3已取消 4已过期',
    `notify_time`      DATETIME        NULL,
    `offer_expire_time` DATETIME       NULL,
    `active_flag`      TINYINT GENERATED ALWAYS AS (
        CASE WHEN `status` = 0 OR `status` = 1 THEN 1 ELSE NULL END
    ) STORED COMMENT '有效候补标记',
    `create_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_user_active` (`session_id`, `user_id`, `active_flag`),
    KEY `idx_session_status_create` (`session_id`, `status`, `create_time`),
    KEY `idx_user_status` (`user_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '候补队列';

-- ------------------------------------------------------------
-- 改签记录
-- ------------------------------------------------------------
CREATE TABLE `ct_reschedule` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_id`         BIGINT UNSIGNED NOT NULL,
    `order_no`         VARCHAR(32)     NOT NULL,
    `from_session_id`  BIGINT UNSIGNED NOT NULL,
    `to_session_id`    BIGINT UNSIGNED NOT NULL,
    `ticket_count`     INT             NOT NULL,
    `price_diff`       DECIMAL(10, 2)  NOT NULL DEFAULT 0,
    `status`           TINYINT         NOT NULL DEFAULT 0 COMMENT '0处理中 1成功 2失败',
    `fail_reason`      VARCHAR(255)    NULL,
    `create_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '改签记录';

-- ------------------------------------------------------------
-- 本地消息表（Outbox）：Redis 扣减与 MQ 投递之间的可靠桥梁
-- ------------------------------------------------------------
CREATE TABLE `ct_order_outbox` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `message_id`      CHAR(32)        NOT NULL,
    `order_no`        VARCHAR(32)     NOT NULL,
    `exchange_name`   VARCHAR(64)     NOT NULL,
    `routing_key`     VARCHAR(64)     NOT NULL,
    `payload`         JSON            NOT NULL,
    `status`          TINYINT         NOT NULL DEFAULT 0 COMMENT '0待发送 1已发送 2失败',
    `retry_count`     INT             NOT NULL DEFAULT 0,
    `next_retry_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_id` (`message_id`),
    KEY `idx_status_next` (`status`, `next_retry_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '本地消息表';

-- ------------------------------------------------------------
-- 核销日志（request_id 唯一 = 核销幂等）
-- ------------------------------------------------------------
CREATE TABLE `ct_verify_log` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `ticket_id`   BIGINT UNSIGNED NOT NULL,
    `ticket_no`   VARCHAR(32)     NOT NULL,
    `operator_id` BIGINT UNSIGNED NOT NULL,
    `request_id`  CHAR(32)        NOT NULL,
    `verify_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_id` (`request_id`),
    UNIQUE KEY `uk_ticket_id` (`ticket_id`),
    KEY `idx_operator_time` (`operator_id`, `verify_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '核销日志';

-- ============================================================
-- 种子数据
-- ============================================================

INSERT INTO `ct_user` (`id`, `phone`, `nick_name`, `icon`, `role`, `status`)
VALUES (1, '13800000000', '城探运营', '', 1, 1),
       (2, '13900000000', '游客小明', '', 0, 1);

INSERT INTO `ct_venue_type` (`id`, `name`, `icon`, `sort`)
VALUES (1, '景区', '', 1),
       (2, '展馆', '', 2),
       (3, '剧场', '', 3),
       (4, '亲子', '', 4),
       (5, '运动', '', 5);

INSERT INTO `ct_venue` (`id`, `name`, `type_id`, `images`, `address`, `x`, `y`, `rating`, `status`, `description`)
VALUES (1, '西湖夜游码头', 1, '/img/venue-1.svg', '杭州市西湖区北山街 1 号', 120.155070, 30.246026, 4.8, 1, '夜游西湖，看三潭印月与城市灯光。'),
       (2, '城市当代美术馆', 2, '/img/venue-2.svg', '杭州市上城区南山路 218 号', 120.160310, 30.250120, 4.7, 1, '常设展与特展轮换，适合半日漫游。'),
       (3, '星光小剧场', 3, '/img/venue-3.svg', '杭州市拱墅区小河路 88 号', 120.148900, 30.261200, 4.6, 1, '沉浸式话剧与民谣现场，座位不超过 200。'),
       (4, '森林亲子乐园', 4, '/img/venue-4.svg', '杭州市西湖区之江路 66 号', 120.132400, 30.217800, 4.5, 1, '户外攀爬、自然课堂与亲子工坊。'),
       (5, '运河体育公园', 5, '/img/venue-5.svg', '杭州市拱墅区运河东路 18 号', 120.141700, 30.272300, 4.4, 1, '跑道、球场与水上运动，按场次预约。'),
       (6, '古镇灯会码头', 1, '/img/venue-6.svg', '杭州市余杭区古镇路 9 号', 120.183200, 30.231500, 4.9, 1, '灯会期间限时开放，分时段入园。');

-- 每个场馆放 3 个场次：明天上午、明天下午、后天晚上
INSERT INTO `ct_venue_session`
(`venue_id`, `session_date`, `start_time`, `end_time`, `capacity`, `sold`, `price`, `status`, `sale_start_time`, `sale_end_time`)
VALUES
(1, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '10:00:00', '12:00:00', 120, 96, 88.00, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '14:00:00', '16:00:00', 120, 60, 88.00, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(1, DATE_ADD(CURDATE(), INTERVAL 2 DAY), '19:00:00', '21:00:00', 100, 20, 128.00, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(2, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '10:00:00', '12:00:00', 80, 30, 60.00, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(2, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '14:00:00', '16:00:00', 80, 72, 60.00, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(2, DATE_ADD(CURDATE(), INTERVAL 2 DAY), '10:00:00', '12:00:00', 80, 0, 60.00, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(3, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '19:00:00', '21:00:00', 160, 150, 180.00, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(3, DATE_ADD(CURDATE(), INTERVAL 2 DAY), '14:00:00', '16:00:00', 160, 40, 180.00, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(3, DATE_ADD(CURDATE(), INTERVAL 2 DAY), '19:00:00', '21:00:00', 160, 0, 220.00, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(4, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '09:00:00', '12:00:00', 60, 58, 45.00, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(4, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '14:00:00', '17:00:00', 60, 22, 45.00, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(4, DATE_ADD(CURDATE(), INTERVAL 2 DAY), '09:00:00', '12:00:00', 60, 0, 55.00, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(5, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '08:00:00', '10:00:00', 200, 80, 30.00, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(5, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '18:00:00', '20:00:00', 200, 190, 30.00, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(5, DATE_ADD(CURDATE(), INTERVAL 2 DAY), '08:00:00', '10:00:00', 200, 0, 40.00, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(6, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '18:00:00', '20:00:00', 300, 280, 99.00, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(6, DATE_ADD(CURDATE(), INTERVAL 2 DAY), '18:00:00', '20:00:00', 300, 100, 99.00, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(6, DATE_ADD(CURDATE(), INTERVAL 3 DAY), '19:00:00', '21:00:00', 300, 0, 129.00, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY));

SET FOREIGN_KEY_CHECKS = 1;

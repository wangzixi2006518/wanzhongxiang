-- Current AI schema, including turn_id required by the message mapper.
SET NAMES utf8mb4;

CREATE TABLE `ai_conversation` (
  `id` varchar(64) NOT NULL,
  `employee_id` bigint NOT NULL,
  `title` varchar(60) NOT NULL,
  `create_time` datetime NOT NULL,
  `update_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_conversation_employee_updated` (`employee_id`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;


CREATE TABLE `ai_message` (
  `id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `conversation_id` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `turn_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `role` varchar(16) COLLATE utf8mb4_bin NOT NULL,
  `content` text COLLATE utf8mb4_bin NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL,
  `create_time` datetime NOT NULL,
  `update_time` datetime NOT NULL,
  `seq` bigint NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_message_seq` (`seq`),
  KEY `idx_ai_message_conversation_created` (`conversation_id`, `create_time`, `id`),
  CONSTRAINT `fk_ai_message_conversation` FOREIGN KEY (`conversation_id`)
    REFERENCES `ai_conversation` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;


CREATE TABLE `ai_rule_document` (
  `document_id` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '文档唯一ID',
  `document_title` varchar(200) COLLATE utf8mb4_bin NOT NULL COMMENT '文档标题',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用：1启用，0禁用',
  `active_version` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '当前生效版本',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='AI规则文档表';


CREATE TABLE `ai_rule_document_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `document_id` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '所属文档ID',
  `version` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '版本号',
  `source_type` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '资料来源类型',
  `source_path` varchar(500) COLLATE utf8mb4_bin NOT NULL COMMENT '资料来源路径',
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING' COMMENT '版本处理状态',
  `chunk_count` int NOT NULL DEFAULT '0' COMMENT '片段数量',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_version` (`document_id`,`version`),
  CONSTRAINT `fk_rule_document_version_document` FOREIGN KEY (`document_id`) REFERENCES `ai_rule_document` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='AI规则文档版本表';

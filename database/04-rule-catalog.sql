-- Fresh database only. Indexes are built later through the authenticated import API.
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO ai_rule_document (document_id,document_title,enabled,active_version,create_time,update_time) VALUES ('wx_order_rules','万众享订单处理规则说明',1,NULL,NOW(),NOW());
INSERT INTO ai_rule_document_version (document_id,version,source_type,source_path,status,chunk_count,create_time,update_time) VALUES ('wx_order_rules','v2-20261009','code-derived','classpath:knowledge/day13/order-processing-rules-v2.txt','PENDING',0,NOW(),NOW());
INSERT INTO ai_rule_document (document_id,document_title,enabled,active_version,create_time,update_time) VALUES ('wx_product_rules','万众享商品与组合商品实现说明',1,NULL,NOW(),NOW());
INSERT INTO ai_rule_document_version (document_id,version,source_type,source_path,status,chunk_count,create_time,update_time) VALUES ('wx_product_rules','v1-20261009','code-derived','classpath:knowledge/day13/product-rules-v1.txt','PENDING',0,NOW(),NOW());
INSERT INTO ai_rule_document (document_id,document_title,enabled,active_version,create_time,update_time) VALUES ('wx_shop_rules','万众享门店营业状态实现说明',1,NULL,NOW(),NOW());
INSERT INTO ai_rule_document_version (document_id,version,source_type,source_path,status,chunk_count,create_time,update_time) VALUES ('wx_shop_rules','v1-20261009','code-derived','classpath:knowledge/day13/shop-status-rules-v1.txt','PENDING',0,NOW(),NOW());
INSERT INTO ai_rule_document (document_id,document_title,enabled,active_version,create_time,update_time) VALUES ('wx_statistics_rules','万众享营业统计口径说明',1,NULL,NOW(),NOW());
INSERT INTO ai_rule_document_version (document_id,version,source_type,source_path,status,chunk_count,create_time,update_time) VALUES ('wx_statistics_rules','v1-20261009','code-derived','classpath:knowledge/day13/business-statistics-rules-v1.txt','PENDING',0,NOW(),NOW());
COMMIT;

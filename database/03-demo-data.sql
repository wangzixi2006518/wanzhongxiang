-- Synthetic development data only. Fresh database only.
-- Demo login: admin / Demo123456! (the existing login service uses MD5).
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO employee
(id,name,username,password,phone,sex,id_number,status,create_time,update_time,create_user,update_user)
VALUES (1,'演示管理员','admin',MD5('Demo123456!'),'00000000000','0','DEMO_ONLY',1,NOW(),NOW(),1,1);

INSERT INTO category
(id,type,name,sort,status,create_time,update_time,create_user,update_user)
VALUES (1,1,'演示商品',1,1,NOW(),NOW(),1,1),
       (2,2,'演示组合商品',2,1,NOW(),NOW(),1,1);

INSERT INTO dish
(id,name,category_id,price,image,description,status,create_time,update_time,create_user,update_user)
VALUES (1,'演示商品A',1,18.00,'','仓库初始化演示数据',1,NOW(),NOW(),1,1),
       (2,'演示商品B',1,12.00,'','仓库初始化演示数据',1,NOW(),NOW(),1,1);

INSERT INTO setmeal
(id,category_id,name,price,status,description,image,create_time,update_time,create_user,update_user)
VALUES (1,2,'演示组合AB',28.00,1,'包含演示商品A和B','',NOW(),NOW(),1,1);

INSERT INTO setmeal_dish (setmeal_id,dish_id,name,price,copies)
VALUES (1,1,'演示商品A',18.00,1),(1,2,'演示商品B',12.00,1);

COMMIT;

-- 创建开发数据库
CREATE DATABASE IF NOT EXISTS picture_management_dev;
CREATE DATABASE IF NOT EXISTS picture_management_test;

-- 创建应用用户并授权
CREATE USER IF NOT EXISTS 'dev_user'@'%' IDENTIFIED BY 'dev_password';
CREATE USER IF NOT EXISTS 'app_user'@'%' IDENTIFIED BY 'app_password';

GRANT ALL PRIVILEGES ON picture_management_dev.* TO 'dev_user'@'%';
GRANT ALL PRIVILEGES ON picture_management_test.* TO 'dev_user'@'%';
GRANT ALL PRIVILEGES ON picture_management.* TO 'app_user'@'%';

FLUSH PRIVILEGES;
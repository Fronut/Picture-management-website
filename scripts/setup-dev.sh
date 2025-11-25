#!/bin/bash

set -e

echo "🚀 设置图片管理系统开发环境..."

# 检查前置条件
echo "📋 检查前置条件..."
command -v docker >/dev/null 2>&1 || { echo "❌ Docker 是必需的但未安装。"; exit 1; }
command -v docker-compose >/dev/null 2>&1 || { echo "❌ Docker Compose 是必需的但未安装。"; exit 1; }
command -v node >/dev/null 2>&1 || { echo "❌ Node.js 是必需的但未安装。"; exit 1; }

# 创建必要的目录
echo "📁 创建必要的目录..."
mkdir -p logs/backend logs/nginx uploads thumbnails ai-service/models

# 复制环境文件
if [ ! -f .env ]; then
    echo "📝 从模板创建 .env 文件..."
    cp .env.example .env
    echo "⚠️  请更新 .env 文件中的配置"
fi

# 启动基础设施服务
echo "🐳 启动基础设施服务 (MySQL, Redis)..."
docker-compose up -d mysql redis

# 等待服务准备就绪
echo "⏳ 等待服务准备就绪..."
sleep 10

# 检查MySQL是否就绪
until docker exec picture-mysql-dev mysqladmin ping -h localhost -u root -proot --silent; do
    echo "等待 MySQL..."
    sleep 2
done

# 设置后端
echo "🔧 设置后端..."
cd backend
if [ ! -f ./mvnw ]; then
    echo "📥 下载 Maven Wrapper..."
    mvn -N io.takari:maven:wrapper
fi
./mvnw clean compile
cd ..

# 设置前端
echo "🎨 设置前端..."
cd frontend
npm install
cd ..

# 设置AI服务依赖
echo "🤖 设置AI服务依赖..."
cd ai-service
if [ ! -f requirements.txt ]; then
    echo "📝 创建AI服务依赖文件..."
    cat > requirements.txt << EOL
fastapi==0.104.1
uvicorn==0.24.0
pydantic==2.5.0
python-multipart==0.0.6
paddlehub
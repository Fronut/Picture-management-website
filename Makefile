.PHONY: help build test clean deploy

help:
	@echo "可用命令:"
	@echo "  make dev        - 启动开发环境"
	@echo "  make build      - 构建所有组件"
	@echo "  make test       - 运行所有测试"
	@echo "  make clean      - 清理构建产物"
	@echo "  make deploy-dev - 部署到开发环境"

dev:
	docker-compose up -d mysql redis
	cd backend && ./mvnw spring-boot:run &
	cd frontend && npm run dev

build:
	cd backend && ./mvnw clean package -DskipTests
	cd frontend && npm run build
	cd ai-service && docker build -t ai-service .

test:
	cd backend && ./mvnw test
	cd frontend && npm run test:unit
	cd ai-service && python -m pytest

clean:
	cd backend && ./mvnw clean
	cd frontend && rm -rf dist node_modules
	cd ai-service && rm -rf __pycache__ *.pyc

deploy-dev:
	docker-compose -f docker/docker-compose.dev.yml up -d

# 数据库相关
db-migrate:
	cd backend && ./mvnw flyway:migrate

db-clean:
	cd backend && ./mvnw flyway:clean

# 代码质量
lint:
	cd backend && ./mvnw spotless:apply
	cd frontend && npm run lint -- --fix

check-style:
	cd backend && ./mvnw checkstyle:check
	cd frontend && npm run lint
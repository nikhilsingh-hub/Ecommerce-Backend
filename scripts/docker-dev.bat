@echo off
REM Development Docker setup script for Windows

setlocal EnableDelayedExpansion

echo 🚀 Starting E-commerce Backend Development Environment

REM Check if Docker is installed
docker --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Docker is not installed. Please install Docker Desktop first.
    pause
    exit /b 1
)

REM Check if Docker Compose is installed
docker-compose --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Docker Compose is not available. Please ensure Docker Desktop is running.
    pause
    exit /b 1
)

REM Create necessary directories
echo 📁 Creating necessary directories...
if not exist logs\mysql mkdir logs\mysql
if not exist logs\app mkdir logs\app
if not exist docker\grafana\provisioning\datasources mkdir docker\grafana\provisioning\datasources
if not exist docker\grafana\provisioning\dashboards mkdir docker\grafana\provisioning\dashboards
if not exist docker\grafana\dashboards mkdir docker\grafana\dashboards

REM Stop any existing containers
echo 🛑 Stopping existing containers...
docker-compose down --remove-orphans

REM Build and start services
echo 🔨 Building and starting services...
docker-compose up --build -d

REM Wait for services to be healthy
echo ⏳ Waiting for services to be ready...

REM Simple wait (Windows doesn't have a great way to check service health easily)
echo ⌛ Waiting 2 minutes for services to start...
timeout /t 120 /nobreak

echo Development environment should be ready!
echo.
echo 📋 Service URLs:
echo    🔗 Application:     http://localhost:8080
echo    📚 API Docs:        http://localhost:8080/swagger-ui.html
echo    🏥 Health Check:    http://localhost:8080/api/v1/health
echo    🔍 Elasticsearch:   http://localhost:9200
echo    📊 Kibana:          http://localhost:5601
echo    🗄️  Database Admin:  http://localhost:8081
echo    📈 Prometheus:      http://localhost:9090
echo    📊 Grafana:         http://localhost:3000 (admin/admin)
echo.
echo 💡 Useful commands:
echo    View logs:       docker-compose logs -f [service_name]
echo    Restart app:     docker-compose restart app
echo    Stop all:        docker-compose down
echo    Clean up:        docker-compose down -v
echo.

pause

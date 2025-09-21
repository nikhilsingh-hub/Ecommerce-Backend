#!/bin/bash
# Development Docker setup script

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}🚀 Starting E-commerce Backend Development Environment${NC}"

# Check if Docker and Docker Compose are installed
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker is not installed. Please install Docker first.${NC}"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}❌ Docker Compose is not installed. Please install Docker Compose first.${NC}"
    exit 1
fi

# Create necessary directories
echo -e "${YELLOW}📁 Creating necessary directories...${NC}"
mkdir -p logs/mysql
mkdir -p logs/app
mkdir -p docker/grafana/provisioning/datasources
mkdir -p docker/grafana/provisioning/dashboards
mkdir -p docker/grafana/dashboards

# Stop any existing containers
echo -e "${YELLOW}🛑 Stopping existing containers...${NC}"
docker-compose down --remove-orphans

# Build and start services
echo -e "${YELLOW}🔨 Building and starting services...${NC}"
docker-compose up --build -d

# Wait for services to be healthy
echo -e "${YELLOW}⏳ Waiting for services to be ready...${NC}"

# Function to wait for service health
wait_for_service() {
    local service_name=$1
    local max_attempts=30
    local attempt=0
    
    echo -e "${YELLOW}⌛ Waiting for $service_name to be healthy...${NC}"
    
    while [ $attempt -lt $max_attempts ]; do
        if docker-compose ps $service_name | grep -q "healthy"; then
            echo -e "${GREEN}✅ $service_name is ready!${NC}"
            return 0
        fi
        
        echo -e "${YELLOW}   Attempt $((attempt + 1))/$max_attempts - $service_name not ready yet...${NC}"
        sleep 10
        attempt=$((attempt + 1))
    done
    
    echo -e "${RED}❌ $service_name failed to become healthy${NC}"
    return 1
}

# Wait for core services
wait_for_service mysql
wait_for_service elasticsearch
wait_for_service app

echo -e "${GREEN}🎉 Development environment is ready!${NC}"
echo ""
echo -e "${GREEN}📋 Service URLs:${NC}"
echo -e "   🔗 Application:     http://localhost:8080"
echo -e "   📚 API Docs:        http://localhost:8080/swagger-ui.html"
echo -e "   🏥 Health Check:    http://localhost:8080/api/v1/health"
echo -e "   🔍 Elasticsearch:   http://localhost:9200"
echo -e "   📊 Kibana:          http://localhost:5601"
echo -e "   🗄️  Database Admin:  http://localhost:8081"
echo -e "   📈 Prometheus:      http://localhost:9090"
echo -e "   📊 Grafana:         http://localhost:3000 (admin/admin)"
echo ""
echo -e "${YELLOW}💡 Useful commands:${NC}"
echo -e "   📜 View logs:       docker-compose logs -f [service_name]"
echo -e "   🔄 Restart app:     docker-compose restart app"
echo -e "   🛑 Stop all:        docker-compose down"
echo -e "   🧹 Clean up:        docker-compose down -v"
echo ""
echo -e "${GREEN}🚀 Happy coding!${NC}"

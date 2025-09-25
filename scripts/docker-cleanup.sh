#!/bin/bash
# Docker cleanup script for E-commerce Backend

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Coloris

echo -e "${YELLOW}🧹 Cleaning up E-commerce Backend Docker Environment${NC}"

# Stop all containers
echo -e "${YELLOW}🛑 Stopping all containers...${NC}"
docker-compose down --remove-orphans

# Remove volumes (optional - will delete all data)
read -p "Do you want to remove all data volumes? This will delete all database data and cannot be undone. (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}🗑️  Removing all volumes...${NC}"
    docker-compose down -v
    
    # Remove any dangling volumes
    docker volume prune -f
    
    echo -e "${RED}⚠️  All data has been deleted!${NC}"
else
    echo -e "${GREEN}✅ Data volumes preserved${NC}"
fi

# Clean up unused images
echo -e "${YELLOW}🧹 Cleaning up unused Docker images...${NC}"
docker image prune -f

# Clean up unused networks
echo -e "${YELLOW}🧹 Cleaning up unused Docker networks...${NC}"
docker network prune -f

# Clean up build cache
echo -e "${YELLOW}🧹 Cleaning up Docker build cache...${NC}"
docker builder prune -f

# Remove application logs
echo -e "${YELLOW}🧹 Cleaning up application logs...${NC}"
rm -rf logs/*

echo -e "${GREEN}✨ Cleanup completed!${NC}"
echo ""
echo -e "${GREEN}💡 To start fresh, run: ./scripts/docker-dev.sh${NC}"

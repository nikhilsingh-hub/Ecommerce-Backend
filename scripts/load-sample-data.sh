#!/bin/bash
# Script to load sample data into the E-commerce application

set -e

# Configuration
API_BASE_URL="http://localhost:8080/api/v1"
CONTENT_TYPE="Content-Type: application/json"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}🔄 Loading sample data into E-commerce Backend${NC}"

# Check if application is running
echo -e "${YELLOW}📡 Checking if application is running...${NC}"
if ! curl -f -s "$API_BASE_URL/health" > /dev/null; then
    echo -e "${RED}❌ Application is not running at $API_BASE_URL${NC}"
    echo -e "${YELLOW}💡 Please start the application first with: ./scripts/docker-dev.sh${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Application is running${NC}"

# Function to create a product
create_product() {
    local name="$1"
    local description="$2"
    local categories="$3"
    local price="$4"
    local sku="$5"
    local attributes="$6"
    local images="$7"
    
    echo -e "${BLUE}📦 Creating product: $name${NC}"
    
    local response=$(curl -s -w "HTTP_STATUS:%{http_code}" \
        -X POST "$API_BASE_URL/products" \
        -H "$CONTENT_TYPE" \
        -d "{
            \"name\": \"$name\",
            \"description\": \"$description\",
            \"categories\": $categories,
            \"price\": $price,
            \"sku\": \"$sku\",
            \"attributes\": $attributes,
            \"images\": $images
        }")
    
    local http_code=$(echo "$response" | grep -o "HTTP_STATUS:[0-9]*" | cut -d: -f2)
    local body=$(echo "$response" | sed 's/HTTP_STATUS:[0-9]*$//')
    
    if [ "$http_code" -eq 201 ]; then
        local product_id=$(echo "$body" | grep -o '"id":[0-9]*' | cut -d: -f2)
        echo -e "${GREEN}✅ Created product: $name (ID: $product_id)${NC}"
        echo "$product_id"
    else
        echo -e "${RED}❌ Failed to create product: $name (HTTP $http_code)${NC}"
        echo "$body"
        return 1
    fi
}

# Electronics Products
echo -e "${YELLOW}📱 Creating Electronics products...${NC}"

create_product \
    "Wireless Bluetooth Headphones" \
    "Premium wireless headphones with active noise cancellation and 30-hour battery life" \
    "[\"Electronics\", \"Audio\", \"Headphones\"]" \
    "199.99" \
    "WBH-001" \
    "{\"color\": \"black\", \"battery_life\": \"30 hours\", \"noise_cancellation\": \"active\", \"wireless\": \"true\"}" \
    "[\"https://images.unsplash.com/photo-1505740420928-5e560c06d30e\", \"https://images.unsplash.com/photo-1484704849700-f032a568e944\"]"

create_product \
    "Smartphone Pro Max 256GB" \
    "Latest flagship smartphone with advanced camera system and fast charging" \
    "[\"Electronics\", \"Mobile\", \"Smartphones\"]" \
    "999.99" \
    "SPM-256" \
    "{\"storage\": \"256GB\", \"color\": \"space_gray\", \"camera\": \"triple_lens\", \"5g\": \"true\"}" \
    "[\"https://images.unsplash.com/photo-1511707171634-5f897ff02aa9\", \"https://images.unsplash.com/photo-1520923642038-b4259acecbd7\"]"

create_product \
    "Gaming Laptop RTX 4070" \
    "High-performance gaming laptop with RTX 4070 graphics and 144Hz display" \
    "[\"Electronics\", \"Computers\", \"Gaming\"]" \
    "1599.99" \
    "GL-RTX4070" \
    "{\"gpu\": \"RTX 4070\", \"ram\": \"16GB\", \"storage\": \"1TB SSD\", \"display\": \"144Hz\"}" \
    "[\"https://images.unsplash.com/photo-1496181133206-80ce9b88a853\", \"https://images.unsplash.com/photo-1525547719571-a2d4ac8945e2\"]"

create_product \
    "4K Smart TV 55 inch" \
    "Ultra HD Smart TV with HDR support and built-in streaming apps" \
    "[\"Electronics\", \"TV\", \"Entertainment\"]" \
    "699.99" \
    "TV-55-4K" \
    "{\"size\": \"55 inch\", \"resolution\": \"4K\", \"hdr\": \"true\", \"smart\": \"true\"}" \
    "[\"https://images.unsplash.com/photo-1461151304267-38535e780c79\", \"https://images.unsplash.com/photo-1593359677879-a4bb92f829d1\"]"

# Home & Garden Products
echo -e "${YELLOW}🏠 Creating Home & Garden products...${NC}"

create_product \
    "Robot Vacuum Cleaner" \
    "Smart robot vacuum with mapping technology and app control" \
    "[\"Home\", \"Appliances\", \"Cleaning\"]" \
    "299.99" \
    "RVC-001" \
    "{\"mapping\": \"true\", \"app_control\": \"true\", \"battery_life\": \"90 minutes\", \"self_charging\": \"true\"}" \
    "[\"https://images.unsplash.com/photo-1558618666-fcd25c85cd64\", \"https://images.unsplash.com/photo-1586880244439-29c04d4a14c1\"]"

create_product \
    "Air Purifier HEPA Filter" \
    "Advanced air purifier with HEPA filter for large rooms up to 500 sq ft" \
    "[\"Home\", \"Appliances\", \"Air Quality\"]" \
    "249.99" \
    "AP-HEPA-500" \
    "{\"filter_type\": \"HEPA\", \"coverage\": \"500 sq ft\", \"noise_level\": \"quiet\", \"smart_controls\": \"true\"}" \
    "[\"https://images.unsplash.com/photo-1584464491033-06628f3a6b7b\", \"https://images.unsplash.com/photo-1627118175935-fd7d4b1a9bf2\"]"

create_product \
    "Garden Tool Set Professional" \
    "Complete 15-piece garden tool set with ergonomic handles and storage bag" \
    "[\"Garden\", \"Tools\", \"Outdoor\"]" \
    "89.99" \
    "GTS-PRO-15" \
    "{\"pieces\": \"15\", \"material\": \"stainless_steel\", \"handles\": \"ergonomic\", \"storage\": \"included\"}" \
    "[\"https://images.unsplash.com/photo-1416879595882-3373a0480b5b\", \"https://images.unsplash.com/photo-1598300042247-d088f8ab3a91\"]"

# Fashion Products
echo -e "${YELLOW}👕 Creating Fashion products...${NC}"

create_product \
    "Premium Cotton T-Shirt" \
    "Soft and comfortable premium cotton t-shirt in multiple colors" \
    "[\"Fashion\", \"Clothing\", \"T-Shirts\"]" \
    "29.99" \
    "PCT-001" \
    "{\"material\": \"100% cotton\", \"fit\": \"regular\", \"colors\": \"multiple\", \"care\": \"machine_wash\"}" \
    "[\"https://images.unsplash.com/photo-1521572163474-6864f9cf17ab\", \"https://images.unsplash.com/photo-1503341504253-dff4815485f1\"]"

create_product \
    "Leather Jacket Classic" \
    "Genuine leather jacket with classic design and premium craftsmanship" \
    "[\"Fashion\", \"Clothing\", \"Jackets\"]" \
    "299.99" \
    "LJ-CLASSIC" \
    "{\"material\": \"genuine_leather\", \"style\": \"classic\", \"lining\": \"quilted\", \"pockets\": \"multiple\"}" \
    "[\"https://images.unsplash.com/photo-1520975661595-6453be3f7070\", \"https://images.unsplash.com/photo-1574455145921-44a8e18d6485\"]"

create_product \
    "Running Shoes Athletic" \
    "Lightweight running shoes with advanced cushioning and breathable design" \
    "[\"Fashion\", \"Footwear\", \"Sports\"]" \
    "129.99" \
    "RS-ATHLETIC" \
    "{\"type\": \"running\", \"cushioning\": \"advanced\", \"weight\": \"lightweight\", \"breathable\": \"true\"}" \
    "[\"https://images.unsplash.com/photo-1542291026-7eec264c27ff\", \"https://images.unsplash.com/photo-1515955656352-a1fa3ffcd111\"]"

# Books & Education
echo -e "${YELLOW}📚 Creating Books & Education products...${NC}"

create_product \
    "Programming Fundamentals Book" \
    "Comprehensive guide to programming fundamentals with practical examples" \
    "[\"Books\", \"Education\", \"Programming\"]" \
    "49.99" \
    "PFB-001" \
    "{\"pages\": \"500\", \"language\": \"english\", \"level\": \"beginner\", \"format\": \"paperback\"}" \
    "[\"https://images.unsplash.com/photo-1532012197267-da84d127e765\", \"https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c\"]"

create_product \
    "Wireless Bluetooth Earbuds" \
    "True wireless earbuds with premium sound quality and long battery life" \
    "[\"Electronics\", \"Audio\", \"Earbuds\"]" \
    "89.99" \
    "WBE-001" \
    "{\"type\": \"true_wireless\", \"battery_life\": \"24 hours\", \"water_resistant\": \"IPX7\", \"charging_case\": \"included\"}" \
    "[\"https://images.unsplash.com/photo-1590658268037-6bf12165a8df\", \"https://images.unsplash.com/photo-1572569511254-d8f925fe2cbb\"]"

# Sports & Fitness
echo -e "${YELLOW}🏋️ Creating Sports & Fitness products...${NC}"

create_product \
    "Yoga Mat Premium Non-Slip" \
    "Extra thick yoga mat with superior grip and eco-friendly materials" \
    "[\"Sports\", \"Fitness\", \"Yoga\"]" \
    "39.99" \
    "YM-PREMIUM" \
    "{\"thickness\": \"6mm\", \"material\": \"eco_friendly\", \"grip\": \"non_slip\", \"size\": \"72x24 inches\"}" \
    "[\"https://images.unsplash.com/photo-1544367567-0f2fcb009e0b\", \"https://images.unsplash.com/photo-1506629905057-f39a6913ded5\"]"

create_product \
    "Adjustable Dumbbells Set" \
    "Space-saving adjustable dumbbells with quick weight change system" \
    "[\"Sports\", \"Fitness\", \"Weights\"]" \
    "199.99" \
    "ADS-001" \
    "{\"weight_range\": \"5-50 lbs\", \"adjustment\": \"quick_change\", \"space_saving\": \"true\", \"material\": \"steel\"}" \
    "[\"https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b\", \"https://images.unsplash.com/photo-1581009146145-b5ef050c2e1e\"]"

echo -e "${GREEN}🎉 Sample data loading completed!${NC}"
echo ""
echo -e "${BLUE}📊 Summary:${NC}"
echo -e "   Created products across multiple categories:"
echo -e "   📱 Electronics (4 products)"
echo -e "   🏠 Home & Garden (3 products)"
echo -e "   👕 Fashion (3 products)"
echo -e "   📚 Books & Education (1 product)"
echo -e "   🏋️ Sports & Fitness (2 products)"
echo ""
echo -e "${YELLOW}💡 Next steps:${NC}"
echo -e "   🔍 Test search: curl '$API_BASE_URL/products/search?q=wireless'"
echo -e "   📊 View products: curl '$API_BASE_URL/products/search'"
echo -e "   🏥 Check health: curl '$API_BASE_URL/health'"
echo -e "   📚 API docs: http://localhost:8080/swagger-ui.html"
echo ""
echo -e "${GREEN}✨ Happy testing!${NC}"

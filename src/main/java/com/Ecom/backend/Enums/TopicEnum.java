package com.Ecom.backend.Enums;

public enum TopicEnum {
    PRODUCT_EVENT("product-events");
    
    private final String value;
    
    TopicEnum(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
}

package com.Ecom.backend.Enums;

public enum ConsumerGroupEnums {
    ELASTICSEARCH_SYNC("elasticsearch-sync");
    
    private final String value;
    
    ConsumerGroupEnums(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
}

package com.resumeai.compass;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class CompassJson {
    private final ObjectMapper mapper;
    public CompassJson(ObjectMapper mapper) { this.mapper = mapper; }
    public String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("无法保存求职数据",e); }
    }
    public <T> T read(String text, Class<T> type) {
        try { return mapper.readValue(text,type); }
        catch (Exception e) { throw new IllegalStateException("无法读取求职数据",e); }
    }
}

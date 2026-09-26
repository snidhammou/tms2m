package com.tms.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tms.server.web.dto.DeviceDtos.InstalledApp;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class JsonSupport {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
    private static final TypeReference<List<InstalledApp>> APPS = new TypeReference<>() { };

    private final ObjectMapper mapper;

    public JsonSupport(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        try {
            return value == null ? null : mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public Map<String, Object> readMap(String json) {
        return read(json, MAP, Map.of());
    }

    public List<InstalledApp> readApps(String json) {
        return read(json, APPS, List.of());
    }

    public List<Long> readLongList(String json) {
        return read(json, new TypeReference<List<Long>>() { }, List.of());
    }

    public List<String> readStringList(String json) {
        return read(json, new TypeReference<List<String>>() { }, List.of());
    }

    public Map<String, String> readStringMap(String json) {
        return read(json, new TypeReference<Map<String, String>>() { }, Map.of());
    }

    private <T> T read(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            return fallback;
        }
    }
}

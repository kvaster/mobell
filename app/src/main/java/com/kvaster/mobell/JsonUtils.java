package com.kvaster.mobell;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class JsonUtils {
    public static class ObjectEntry {
        private final String key;
        private final Object value;

        private ObjectEntry(String key, Object value) {
            this.key = key;
            this.value = value;
        }
    }

    public static ObjectEntry je(String key, Object value) {
        return new ObjectEntry(key, value);
    }

    public static JSONObject jo(ObjectEntry... entries) {
        try {
            JSONObject obj = new JSONObject();
            for (ObjectEntry entry : entries) {
                obj.put(entry.key, JSONObject.wrap(entry.value));
            }
            return obj;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public static JSONArray ja(Object... entries) {
        JSONArray arr = new JSONArray();
        for (Object entry : entries) {
            arr.put(JSONObject.wrap(entry));
        }
        return arr;
    }
}

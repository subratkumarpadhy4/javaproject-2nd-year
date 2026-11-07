package com.verivote.client;

import java.io.InputStream;
import java.util.Properties;

public final class ApplicationConfig {

    private static final Properties props = new Properties();

    static {
        load();
    }

    private ApplicationConfig() {}

    public static void load() {
        try (InputStream in = ApplicationConfig.class.getResourceAsStream("/application-client.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception ignored) {
        }
    }

    public static String get(String key) {
        return get(key, "");
    }

    public static String get(String key, String defaultValue) {
        String val = System.getProperty(key);
        if (val != null && !val.isBlank()) {
            return val;
        }
        return props.getProperty(key, defaultValue);
    }
}

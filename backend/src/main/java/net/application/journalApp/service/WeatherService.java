package net.application.journalApp.service;
import net.application.journalApp.api.*;
import net.application.journalApp.cache.*;
import net.application.journalApp.config.*;
import net.application.journalApp.constants.*;
import net.application.journalApp.dto.*;
import net.application.journalApp.entity.*;
import net.application.journalApp.enums.*;
import net.application.journalApp.filter.*;
import net.application.journalApp.model.*;
import net.application.journalApp.repository.*;
import net.application.journalApp.scheduler.*;
import net.application.journalApp.service.*;
import net.application.journalApp.utils.*;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WeatherService {
    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final long CACHE_TTL_MILLIS = 5 * 60 * 1000L;
    private static final long RATE_LIMIT_BACKOFF_MILLIS = 60 * 1000L;
    private final Map<String, CachedWeather> memoryCache = new ConcurrentHashMap<>();
    private final Map<String, Long> retryAfter = new ConcurrentHashMap<>();
    @Value("${weather.api.key}")
    private String apiKey;
    @Value("${weather.api.url}")
    private String weatherUrl;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private AppCache appCache;

    @Autowired
    private RedisService redisService;

    public synchronized WeatherResponse getWeather(String city) {
        long now = System.currentTimeMillis();
        CachedWeather localCached = memoryCache.get(city);
        if (localCached != null && localCached.expiresAt > now) return localCached.response;
        Long retryAt = retryAfter.get(city);
        if (retryAt != null && retryAt > now) return localCached == null ? null : localCached.response;

        WeatherResponse weatherResponse = redisService.get("weather_of_" + city, WeatherResponse.class);
        if (weatherResponse != null) {
            memoryCache.put(city, new CachedWeather(weatherResponse, now + CACHE_TTL_MILLIS));
            return weatherResponse;
        } else {
            String template = appCache.appCache == null ? null : appCache.appCache.get(AppCache.keys.WEATHER_API.toString());
            if (template == null || template.contains("openweathermap.org")) template = weatherUrl;
            if (template == null || apiKey == null || apiKey.trim().isEmpty()) return getOpenMeteoFallback(city, now);
            String finalAPI;
            if (template.contains(Placeholders.CITY) || template.contains(Placeholders.API_KEY)
                    || template.contains("{city}") || template.contains("{apiKey}")) {
                finalAPI = template
                        .replace(Placeholders.CITY, city)
                        .replace(Placeholders.API_KEY, apiKey)
                        .replace("{city}", city)
                        .replace("{apiKey}", apiKey);
            } else {
                finalAPI = UriComponentsBuilder.fromHttpUrl(template)
                        .queryParam("access_key", apiKey)
                        .queryParam("query", city)
                        .queryParam("units", "m")
                        .build()
                        .encode()
                        .toUriString();
            }
            try {
                ResponseEntity<WeatherResponse> response = restTemplate.exchange(finalAPI, HttpMethod.GET, null, WeatherResponse.class);
                WeatherResponse body = response.getBody();
                if (body != null && body.getError() != null) {
                    log.warn("Weatherstack returned error {}: {}", body.getError().getCode(), body.getError().getInfo());
                    return getOpenMeteoFallback(city, now);
                }
                if (body == null || body.getCurrent() == null) {
                    log.warn("Weatherstack returned no current weather for city {}", city);
                    return getOpenMeteoFallback(city, now);
                }
                body.setProvider("Weatherstack");
                memoryCache.put(city, new CachedWeather(body, now + CACHE_TTL_MILLIS));
                retryAfter.remove(city);
                redisService.set("weather_of_" + city, body, 300L);
                return body;
            } catch (HttpClientErrorException.TooManyRequests e) {
                retryAfter.put(city, System.currentTimeMillis() + RATE_LIMIT_BACKOFF_MILLIS);
                log.warn("Weatherstack rate limit reached for city {}. Cooling down before retrying.", city);
                return localCached != null ? localCached.response : getOpenMeteoFallback(city, now);
            } catch (RestClientException | IllegalArgumentException e) {
                log.warn("Weather lookup failed for city {}", city, e);
                return localCached != null ? localCached.response : getOpenMeteoFallback(city, now);
            }
        }

    }

    @SuppressWarnings("unchecked")
    private WeatherResponse getOpenMeteoFallback(String city, long now) {
        if (!"Mumbai".equalsIgnoreCase(city)) return null;
        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://api.open-meteo.com/v1/forecast")
                    .queryParam("latitude", 19.0760)
                    .queryParam("longitude", 72.8777)
                    .queryParam("current", "temperature_2m,apparent_temperature,weather_code")
                    .queryParam("timezone", "Asia/Kolkata")
                    .build()
                    .encode()
                    .toUriString();
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, null, Map.class);
            Map<String, Object> payload = response.getBody();
            Map<String, Object> current = payload == null ? null : (Map<String, Object>) payload.get("current");
            if (current == null || !(current.get("temperature_2m") instanceof Number)
                    || !(current.get("apparent_temperature") instanceof Number)) return null;

            WeatherResponse fallback = new WeatherResponse();
            WeatherResponse.Current conditions = new WeatherResponse.Current();
            conditions.setTemperature(((Number) current.get("temperature_2m")).doubleValue());
            conditions.setFeelslike(((Number) current.get("apparent_temperature")).doubleValue());
            conditions.setWeatherDescriptions(Collections.singletonList(weatherDescription(current.get("weather_code"))));
            fallback.setCurrent(conditions);
            fallback.setProvider("Open-Meteo");
            memoryCache.put(city, new CachedWeather(fallback, now + CACHE_TTL_MILLIS));
            redisService.set("weather_of_" + city, fallback, 300L);
            return fallback;
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Open-Meteo fallback failed for city {}", city, e);
            return null;
        }
    }

    private String weatherDescription(Object rawCode) {
        if (!(rawCode instanceof Number)) return "Current conditions";
        int code = ((Number) rawCode).intValue();
        if (code == 0) return "Clear sky";
        if (code <= 3) return "Partly cloudy";
        if (code == 45 || code == 48) return "Foggy";
        if (code <= 57) return "Drizzle";
        if (code <= 67 || (code >= 80 && code <= 82)) return "Rainy";
        if (code <= 77 || code == 85 || code == 86) return "Snowy";
        if (code >= 95) return "Thunderstorms";
        return "Cloudy";
    }

    private static class CachedWeather {
        private final WeatherResponse response;
        private final long expiresAt;

        private CachedWeather(WeatherResponse response, long expiresAt) {
            this.response = response;
            this.expiresAt = expiresAt;
        }
    }
}

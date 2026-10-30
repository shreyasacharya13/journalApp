package net.application.journalApp.controller;
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


import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
@Tag(name = "User APIs", description = "Read, Update & Delete User")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WeatherService weatherService;

    @PutMapping
    public ResponseEntity<?> updateUser(@RequestBody User user) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userName = authentication.getName();
        boolean updated = userService.updateAccount(userName, user.getUserName(), user.getPassword());
        return updated ? ResponseEntity.noContent().build() : ResponseEntity.badRequest().build();
    }

    @DeleteMapping
    public ResponseEntity<?> deleteUserById() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = userService.findByUserName(authentication.getName());
        if (user == null) return ResponseEntity.notFound().build();
        userRepository.deleteByUserName(authentication.getName());
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping
    public ResponseEntity<?> greeting() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        WeatherResponse weatherResponse = weatherService.getWeather("Mumbai");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("greeting", "Hi " + authentication.getName());
        if (weatherResponse != null) {
            WeatherResponse.Current current = weatherResponse.getCurrent();
            Map<String, Object> weather = new LinkedHashMap<>();
            weather.put("city", "Mumbai");
            weather.put("temperature", current.getTemperature());
            weather.put("feelsLike", current.getFeelslike());
            weather.put("provider", weatherResponse.getProvider() == null ? "Weatherstack" : weatherResponse.getProvider());
            List<String> descriptions = current.getWeatherDescriptions();
            weather.put("description", descriptions == null || descriptions.isEmpty() ? "Current conditions" : descriptions.get(0));
            result.put("weather", weather);
        }
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

}

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


import lombok.extern.slf4j.Slf4j;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class UserService {
    @Autowired
    private UserRepository userRepository;

    private static final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public boolean saveNewUser(User user) {
        try {
            if (user == null || user.getUserName() == null || user.getUserName().trim().isEmpty() || user.getPassword() == null || user.getPassword().length() < 8 || userRepository.findByUserName(user.getUserName()) != null) return false;
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            user.setRoles(Arrays.asList("USER"));
            userRepository.save(user);
            return true;
        } catch (Exception e) {
            if (e instanceof DuplicateKeyException) return false;
            log.error("Failed to create user", e);
            throw new IllegalStateException("Failed to create user", e);
        }
    }

    public boolean updateAccount(String oldUsername, String newUsername, String rawPassword) {
        User user = userRepository.findByUserName(oldUsername);
        if (user == null) return false;
        if (newUsername != null && !newUsername.trim().isEmpty() && !newUsername.equals(oldUsername)) {
            if (userRepository.findByUserName(newUsername) != null) return false;
            user.setUserName(newUsername.trim());
        }
        if (rawPassword != null && !rawPassword.isEmpty()) {
            if (rawPassword.length() < 8) return false;
            user.setPassword(passwordEncoder.encode(rawPassword));
        }
        userRepository.save(user);
        return true;
    }

    public void saveAdmin(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRoles(Arrays.asList("USER", "ADMIN"));
        userRepository.save(user);
    }

    public void saveUser(User user) {
        userRepository.save(user);
    }

    public List<User> getAll() {
        return userRepository.findAll();
    }

    public Optional<User> findById(ObjectId id) {
        return userRepository.findById(id);
    }

    public void deleteById(ObjectId id) {
        userRepository.deleteById(id);
    }

    public User findByUserName(String userName) {
        return userRepository.findByUserName(userName);
    }
}

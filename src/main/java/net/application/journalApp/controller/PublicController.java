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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public")
@Slf4j
@Tag(name = "Public APIs")
public class PublicController {

    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private UserDetailsServiceImpl userDetailsService;
    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/health-check")
    public String healthCheck() {
        log.info("Health is ok !");
        return "Ok";
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody(required = false) UserDTO user) {
        if (user == null || user.getUserName() == null || user.getUserName().trim().isEmpty()
                || user.getPassword() == null || user.getPassword().length() < 8) {
            return ResponseEntity.badRequest().body("Username and a password of at least 8 characters are required");
        }
        String userName = user.getUserName().trim();
        if (userService.findByUserName(userName) != null) {
            return ResponseEntity.badRequest().body("That username is already taken. Please choose another one.");
        }
        User newUser = new User();
        newUser.setEmail(user.getEmail());
        newUser.setUserName(userName);
        newUser.setPassword(user.getPassword());
        newUser.setSentimentAnalysis(user.isSentimentAnalysis());
        if (userService.saveNewUser(newUser)) return ResponseEntity.status(HttpStatus.CREATED).build();
        return ResponseEntity.badRequest().body("That username is already taken. Please choose another one.");
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody(required = false) UserDTO user) {
        if (user == null || user.getUserName() == null || user.getUserName().trim().isEmpty()
                || user.getPassword() == null || user.getPassword().isEmpty()) {
            return new ResponseEntity<>("Username and password are required", HttpStatus.BAD_REQUEST);
        }
        try{
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUserName(), user.getPassword()));
            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUserName());
            String jwt = jwtUtil.generateToken(userDetails.getUsername());
            return new ResponseEntity<>(jwt, HttpStatus.OK);
        }catch (Exception e){
            log.error("Exception occurred while createAuthenticationToken ", e);
            return new ResponseEntity<>("Incorrect username or password", HttpStatus.BAD_REQUEST);
        }
    }
}

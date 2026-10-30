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



import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RestController
@RequestMapping("/auth/google")
@Slf4j
public class GoogleAuthController {

    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret:}")
    private String clientSecret;
    @Value("${google.redirect-uri}")
    private String redirectUri;
    @Value("${google.frontend-uri:http://localhost:5173}")
    private String frontendUri;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    UserDetailsServiceImpl userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/login")
    public ResponseEntity<?> beginLogin(HttpServletResponse response) {
        if (clientId == null || clientId.trim().isEmpty()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Google OAuth is not configured");
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        byte[] verifierBytes = new byte[32];
        new SecureRandom().nextBytes(verifierBytes);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes);
        String challenge;
        try { challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII))); }
        catch (Exception e) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); }
        Cookie cookie = new Cookie("oauth_state", state);
        cookie.setHttpOnly(true);
        cookie.setSecure(redirectUri.startsWith("https://"));
        cookie.setPath("/auth/google");
        cookie.setMaxAge(300);
        response.addCookie(cookie);
        Cookie verifierCookie = new Cookie("oauth_verifier", verifier);
        verifierCookie.setHttpOnly(true);
        verifierCookie.setSecure(redirectUri.startsWith("https://"));
        verifierCookie.setPath("/auth/google");
        verifierCookie.setMaxAge(300);
        response.addCookie(verifierCookie);
        String url = UriComponentsBuilder.fromHttpUrl("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", clientId).queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code").queryParam("scope", "openid email profile")
                .queryParam("state", state).queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256").build().encode().toUriString();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, url).build();
    }

    @GetMapping("/callback")
    public ResponseEntity<?> handleGoogleCallback(@RequestParam(required = false) String code,
                                                   @RequestParam(required = false) String state,
                                                   @RequestParam(required = false) String error,
                                                   @org.springframework.web.bind.annotation.CookieValue(value = "oauth_state", required = false) String savedState,
                                                   @org.springframework.web.bind.annotation.CookieValue(value = "oauth_verifier", required = false) String verifier,
                                                   HttpServletResponse response) {
        try {
            if (error != null) return redirectToFrontend("google_cancelled");
            if (code == null || state == null || savedState == null || verifier == null
                    || !MessageDigest.isEqual(savedState.getBytes(java.nio.charset.StandardCharsets.UTF_8), state.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                return redirectToFrontend("google_session_expired");
            }
            if (clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty()) {
                return redirectToFrontend("google_not_configured");
            }
            Cookie expiredState = new Cookie("oauth_state", ""); expiredState.setPath("/auth/google"); expiredState.setMaxAge(0); response.addCookie(expiredState);
            Cookie expiredVerifier = new Cookie("oauth_verifier", ""); expiredVerifier.setPath("/auth/google"); expiredVerifier.setMaxAge(0); response.addCookie(expiredVerifier);
            String tokenEndpoint = "https://oauth2.googleapis.com/token";
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("code", code);
            params.add("client_id", clientId);
            params.add("client_secret", clientSecret);
            params.add("redirect_uri", redirectUri);
            params.add("grant_type", "authorization_code");
            params.add("code_verifier", verifier);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(tokenEndpoint, request, Map.class);
            if (tokenResponse.getBody() == null || tokenResponse.getBody().get("id_token") == null) return redirectToFrontend("google_failed");
            String idToken = (String) tokenResponse.getBody().get("id_token");
            String userInfoUrl = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
            ResponseEntity<Map> userInfoResponse = restTemplate.getForEntity(userInfoUrl, Map.class);
            if (userInfoResponse.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> userInfo = userInfoResponse.getBody();
                long expiresAt = Long.parseLong(String.valueOf(userInfo.get("exp")));
                if (!clientId.equals(userInfo.get("aud")) || (!"accounts.google.com".equals(userInfo.get("iss")) && !"https://accounts.google.com".equals(userInfo.get("iss"))) || !"true".equals(String.valueOf(userInfo.get("email_verified"))) || expiresAt <= System.currentTimeMillis() / 1000) return redirectToFrontend("google_failed");
                String email = (String) userInfo.get("email");
                if (email == null || email.trim().isEmpty()) return redirectToFrontend("google_failed");
                User existing = userRepository.findByUserName(email);
                if (existing == null) {
                    User user = new User();
                    user.setEmail(email);
                    user.setUserName(email);
                    user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                    user.setRoles(Arrays.asList("USER"));
                    userRepository.save(user);
                } else if (existing.getEmail() != null && !email.equalsIgnoreCase(existing.getEmail())) {
                    return redirectToFrontend("google_account_conflict");
                }
                String jwtToken = jwtUtil.generateToken(email);
                String destination = UriComponentsBuilder.fromUriString(frontendUri)
                        .fragment("token={token}&username={username}")
                        .buildAndExpand(jwtToken, email).encode().toUriString();
                return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, destination).build();
            }
            return redirectToFrontend("google_failed");
        } catch (Exception e) {
            log.error("Exception occurred while handleGoogleCallback ", e);
            return redirectToFrontend("google_failed");
        }

    }

    private ResponseEntity<?> redirectToFrontend(String error) {
        String destination = UriComponentsBuilder.fromUriString(frontendUri)
                .fragment("authError={error}")
                .buildAndExpand(error)
                .encode()
                .toUriString();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, destination).build();
    }
}

/*

https://accounts.google.com/o/oauth2/auth?
client_id=YOUR_CLIENT_ID
    &redirect_uri=YOUR_REDIRECT_URI
    &response_type=code
    &scope=email profile
    &access_type=offline
    &prompt=consent

*/

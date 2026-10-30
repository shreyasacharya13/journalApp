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


import net.application.journalApp.entity.User;
import net.application.journalApp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;


    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUserName(username);
        if (user != null) {
            return org.springframework.security.core.userdetails.User.builder()
                    .username(user.getUserName())
                    .password(user.getPassword())
                    .roles(user.getRoles() == null ? new String[]{"USER"} : user.getRoles().toArray(new String[0]))
                    .build();
        }
        throw new UsernameNotFoundException("User not found with username: " + username);
    }
}

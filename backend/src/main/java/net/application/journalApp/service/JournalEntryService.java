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
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class JournalEntryService {

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private UserService userService;



    public void saveEntry(JournalEntry journalEntry, String userName) {
        try {
            User user = userService.findByUserName(userName);
            if (user == null) throw new IllegalArgumentException("User does not exist");
            journalEntry.setDate(LocalDateTime.now());
            JournalEntry saved = journalEntryRepository.save(journalEntry);
            if (user.getJournalEntries() == null) user.setJournalEntries(new java.util.ArrayList<>());
            user.getJournalEntries().add(saved);
            userService.saveUser(user);
        } catch (Exception e) {
            throw new RuntimeException("An error occurred while saving the entry.", e);
        }
    }

    public JournalEntry saveEntry(JournalEntry journalEntry) {
        return journalEntryRepository.save(journalEntry);
    }

    public List<JournalEntry> getAll() {
        return journalEntryRepository.findAll();
    }

    public Optional<JournalEntry> findById(ObjectId id) {
        return journalEntryRepository.findById(id);
    }

    public boolean deleteById(ObjectId id, String userName) {
        boolean removed = false;
        try {
            User user = userService.findByUserName(userName);
            if (user == null || user.getJournalEntries() == null) return false;
            removed = user.getJournalEntries().removeIf(x -> x.getId().equals(id));
            if (removed) {
                userService.saveUser(user);
                journalEntryRepository.deleteById(id);
            }
        } catch (Exception e) {
            log.error("Error ",e);
            throw new RuntimeException("An error occurred while deleting the entry.", e);
        }
        return removed;
    }

    public Optional<JournalEntry> updateEntry(ObjectId id, JournalEntry changes, String userName) {
        User user = userService.findByUserName(userName);
        if (user == null || user.getJournalEntries() == null ||
                user.getJournalEntries().stream().noneMatch(entry -> id.equals(entry.getId()))) {
            return Optional.empty();
        }
        return journalEntryRepository.findById(id).map(entry -> {
            if (changes.getTitle() != null && !changes.getTitle().trim().isEmpty()) entry.setTitle(changes.getTitle());
            if (changes.getContent() != null) entry.setContent(changes.getContent());
            return journalEntryRepository.save(entry);
        });
    }

}

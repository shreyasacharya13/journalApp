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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;


@Service
public class KafkaProducerService {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.topic.journal-events}")
    private String journalTopic;

    public void sendJournalEvent(String eventType, String username, String journalId) {
        String message = String.format(
                "{\"event\":\"%s\",\"username\":\"%s\",\"journalId\":\"%s\",\"timestamp\":\"%s\"}",
                eventType, username, journalId, java.time.LocalDateTime.now()
        );
        kafkaTemplate.send(journalTopic, username, message);
    }

    // Fallback — called when Kafka is down
    public void kafkaFallback(String eventType, String username,
                              String journalId, Throwable t) {
        System.out.println(">>> Kafka is down, fallback triggered for event: "
                + eventType + " by user: " + username);
        // Could save to DB, retry queue, or log for later processing
    }
}

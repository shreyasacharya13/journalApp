package net.application.journalApp.cache;
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



import net.application.journalApp.entity.ConfigJournalAppEntity;
import net.application.journalApp.repository.ConfigJournalAppRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AppCache {
    private static final Logger log = LoggerFactory.getLogger(AppCache.class);

    public enum keys{
        WEATHER_API;
    }

    @Autowired
    private ConfigJournalAppRepository configJournalAppRepository;

    public volatile Map<String, String> appCache = new HashMap<>();

    @PostConstruct
    public void init(){
        Map<String, String> refreshed = new HashMap<>();
        try {
            List<ConfigJournalAppEntity> all = configJournalAppRepository.findAll();
            for (ConfigJournalAppEntity configJournalAppEntity : all) {
                refreshed.put(configJournalAppEntity.getKey(), configJournalAppEntity.getValue());
            }
            appCache = refreshed;
        } catch (Exception e) {
            log.warn("Could not refresh application cache; keeping previous values", e);
        }
    }

}

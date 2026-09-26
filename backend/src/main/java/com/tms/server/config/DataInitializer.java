package com.tms.server.config;

import com.tms.server.domain.AdminUser;
import com.tms.server.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Crée le compte administrateur initial au premier démarrage. */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AdminUserRepository users;
    private final PasswordEncoder encoder;
    private final TmsProperties props;

    public DataInitializer(AdminUserRepository users, PasswordEncoder encoder, TmsProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        if (users.count() == 0) {
            AdminUser admin = new AdminUser();
            admin.setUsername(props.admin().username());
            admin.setPasswordHash(encoder.encode(props.admin().password()));
            users.save(admin);
            log.info("Compte administrateur initial créé : {}", admin.getUsername());
        }
        if ("admin123".equals(props.admin().password())) {
            log.warn("Mot de passe admin par défaut utilisé : définissez TMS_ADMIN_PASSWORD en production.");
        }
        if (props.enrollment().key().startsWith("CHANGE-ME")) {
            log.warn("Clé d'enrôlement par défaut utilisée : définissez TMS_ENROLLMENT_KEY en production.");
        }
    }
}

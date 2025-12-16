package com.imagemanagement.bootstrap;

import com.imagemanagement.config.AdminUserProperties;
import com.imagemanagement.entity.User;
import com.imagemanagement.entity.enums.UserRole;
import com.imagemanagement.entity.enums.UserStatus;
import com.imagemanagement.repository.UserRepository;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class AdminUserInitializer implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminUserInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminUserProperties adminProperties;

    public AdminUserInitializer(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AdminUserProperties adminProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminProperties = adminProperties;
    }

    @Override
    public void run(String... args) {
        if (!adminProperties.isEnabled()) {
            LOGGER.debug("Admin user bootstrap disabled via configuration");
            return;
        }

        Optional<User> existing = userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(
                adminProperties.getUsername(),
                adminProperties.getEmail());

        if (existing.isPresent()) {
            promoteExistingAdmin(existing.get());
        } else {
            createDefaultAdmin();
        }
    }

    private void promoteExistingAdmin(User user) {
        boolean updated = false;
        if (user.getRole() != UserRole.ADMIN) {
            user.setRole(UserRole.ADMIN);
            updated = true;
        }
        if (adminProperties.isResetPassword()) {
            user.setPasswordHash(passwordEncoder.encode(adminProperties.getPassword()));
            updated = true;
        }
        if (updated) {
            userRepository.save(user);
            LOGGER.info("Ensured default admin user '{}' has administrator privileges", user.getUsername());
        } else {
            LOGGER.debug("Default admin user '{}' already present", user.getUsername());
        }
    }

    private void createDefaultAdmin() {
        User admin = new User();
        admin.setUsername(adminProperties.getUsername());
        admin.setEmail(adminProperties.getEmail().toLowerCase(Locale.ROOT));
        admin.setPasswordHash(passwordEncoder.encode(adminProperties.getPassword()));
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        userRepository.save(admin);
        LOGGER.info("Created default admin user '{}'", adminProperties.getUsername());
    }
}

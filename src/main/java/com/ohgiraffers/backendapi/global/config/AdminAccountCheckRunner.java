package com.ohgiraffers.backendapi.global.config;

import com.ohgiraffers.backendapi.domain.user.entity.User;
import com.ohgiraffers.backendapi.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountCheckRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("================ ADMIN ACCOUNT CHECK START ================");
        try {
            User admin = userRepository.findByLoginId("admin").orElse(null);
            if (admin == null) {
                log.error("❌ Admin user NOT FOUND in database!");
            } else {
                log.info("✅ Admin user FOUND. ID: {}, Role: {}", admin.getId(), admin.getRole());
                log.info("🔒 Stored Hash: {}", admin.getPassword());

                boolean matches = passwordEncoder.matches("admin1234!", admin.getPassword());
                if (matches) {
                    log.info("✅ Password MATCHES 'admin1234!'");
                } else {
                    log.error("❌ Password DOES NOT MATCH 'admin1234!'");
                    // Generate a valid hash for comparison/debugging
                    String newHash = passwordEncoder.encode("admin1234!");
                    log.info("ℹ️ Expected Hash for 'admin1234!': {}", newHash);
                }
            }
        } catch (Exception e) {
            log.error("Error checking admin account", e);
        }
        log.info("================ ADMIN ACCOUNT CHECK END ================");
    }
}

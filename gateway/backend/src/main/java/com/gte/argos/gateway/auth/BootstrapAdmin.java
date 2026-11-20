package com.gte.argos.gateway.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Optional first administrator for an empty local development database. */
@Component
public class BootstrapAdmin implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final String loginId;
    private final String password;

    public BootstrapAdmin(JdbcTemplate jdbc, PasswordEncoder passwords,
            @Value("${GATEWAY_BOOTSTRAP_ADMIN_LOGIN:}") String loginId,
            @Value("${GATEWAY_BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.loginId = loginId;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (loginId.isBlank() || password.isBlank()) return;
        Long users = jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Long.class);
        if (users != null && users > 0) return;
        jdbc.update("""
                INSERT INTO app_user (role_id, login_id, password_hash, name, pw_reset_required)
                SELECT role_id, ?, ?, '본사 관리자', true FROM role WHERE code = 'HQ_ADMIN'
                """, loginId, passwords.encode(password));
    }
}

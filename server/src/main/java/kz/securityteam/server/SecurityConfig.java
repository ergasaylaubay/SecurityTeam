package kz.securityteam.server;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpMethod;

@Configuration
public class SecurityConfig {
    @Bean @Profile("!test") KeyMaterial keyMaterial(
            @Value("${messenger.keystore}") String path,
            @Value("${messenger.keystore-password}") String password) throws Exception {
        return KeyMaterial.load(path, password);
    }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean UserDetailsService userDetailsService(JdbcTemplate jdbc) {
        return username -> jdbc.query("SELECT username,password_hash,role,status FROM app_user WHERE username=?",
                (rs, row) -> User.withUsername(rs.getString(1)).password(rs.getString(2))
                        .roles(rs.getString(3)).disabled(!"ACTIVE".equals(rs.getString(4))).build(), username)
                .stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/auth/csrf", "/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
                .requestCache(cache -> cache.disable())
                .formLogin(login -> login.loginProcessingUrl("/auth/login")
                        .successHandler((req, res, auth) -> res.setStatus(204))
                        .failureHandler((req, res, error) -> res.sendError(401)))
                .logout(logout -> logout.logoutUrl("/auth/logout")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((req, res, error) -> res.sendError(401)))
                .build();
    }
}

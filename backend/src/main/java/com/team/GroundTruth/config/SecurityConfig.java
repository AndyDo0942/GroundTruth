package com.team.GroundTruth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration that permits all requests.
 */
@Configuration
public class SecurityConfig {

	/**
	 * Configures a permissive security filter chain.
	 *
	 * @param http http security builder
	 * @return security filter chain
	 * @throws Exception on configuration errors
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
				.httpBasic(Customizer.withDefaults())
				.formLogin(Customizer.withDefaults());
		return http.build();
	}
}

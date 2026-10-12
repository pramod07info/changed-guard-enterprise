package com.changeguard.config;

import com.changeguard.service.FeatureGateFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class FeatureGateConfiguration {
  @Bean
  FilterRegistrationBean<FeatureGateFilter> featureGate(
      FeatureFlags flags, JsonMapper json, Environment env) {
    var registration = new FilterRegistrationBean<>(new FeatureGateFilter(flags, json));
    // Authentication/firewall run first. Disabled flags never bypass authentication.
    registration.setOrder(env.getProperty("spring.security.filter.order", Integer.class, -100) + 1);
    return registration;
  }
}

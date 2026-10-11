package com.changeguard.security;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.security.test.context.support.WithSecurityContext;

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = TenantSecurityContextFactory.class)
public @interface WithTenant {
    String value() default "tenant-a";
}

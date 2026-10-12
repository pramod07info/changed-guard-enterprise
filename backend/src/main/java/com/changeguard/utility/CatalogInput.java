package com.changeguard.utility;

import java.net.URI;
import java.util.Set;
import com.changeguard.exception.InvalidContractException;

/** Shared stateless validation used by catalog and baseline services. */
public final class CatalogInput {
    private CatalogInput() {}
    public static String text(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max)
            throw new InvalidContractException(field + " must contain 1.." + max + " characters");
        return value.trim();
    }
    public static String identifier(String value, String field) {
        String result = text(value, field, 100);
        if (!result.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}"))
            throw new InvalidContractException(field + " must use letters, digits, dot, underscore or hyphen and start with a letter or digit");
        return result;
    }
    public static void page(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0 || offset > 100000)
            throw new InvalidContractException("limit must be 1..100 and offset must be 0..100000");
    }
    public static String repositoryUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String result = text(value, "repositoryUrl", 500);
        try {
            var uri = URI.create(result);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null
                    || !Set.of("github.com", "gitlab.com").contains(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getQuery() != null || uri.getFragment() != null
                    || !uri.getPath().matches("/[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)+/?")
                    || uri.getPath().contains("/../") || uri.getPath().endsWith("/..") || uri.getPath().contains("/./"))
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new InvalidContractException("repositoryUrl must be an HTTPS GitHub or GitLab repository URL without credentials, query or fragment");
        }
        return result;
    }
}

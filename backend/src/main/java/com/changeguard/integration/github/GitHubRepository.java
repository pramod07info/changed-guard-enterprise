package com.changeguard.integration.github;

import java.net.URI;
import com.changeguard.exception.InvalidContractException;

public record GitHubRepository(String owner, String name) {
    public GitHubRepository {
        if (owner == null || name == null || !owner.matches("[A-Za-z0-9][A-Za-z0-9-]{0,99}")
                || !name.matches("[A-Za-z0-9_.-]{1,100}") || name.equals(".") || name.equals(".."))
            throw new InvalidContractException("A public GitHub repository must have an owner/repository name");
    }
    public String fullName() { return owner + "/" + name; }
    public static GitHubRepository fromFullName(String value) {
        if (value == null || value.split("/", -1).length != 2)
            throw new InvalidContractException("Invalid GitHub repository name");
        String[] parts = value.split("/", -1);
        return new GitHubRepository(parts[0], parts[1]);
    }
    public static GitHubRepository fromCatalogUrl(String value) {
        try {
            var uri = URI.create(value == null ? "" : value);
            if (!"https".equals(uri.getScheme()) || !"github.com".equals(uri.getHost()) || uri.getUserInfo() != null
                    || uri.getPort() != -1 || uri.getQuery() != null || uri.getFragment() != null)
                throw new IllegalArgumentException();
            String path = uri.getPath().replaceAll("^/|/$", "");
            if (path.endsWith(".git")) path = path.substring(0, path.length() - 4);
            return fromFullName(path);
        } catch (IllegalArgumentException exception) {
            throw new InvalidContractException("Register an HTTPS github.com owner/repository URL before comparing a PR");
        }
    }
}

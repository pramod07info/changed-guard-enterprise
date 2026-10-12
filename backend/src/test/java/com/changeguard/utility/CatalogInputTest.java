package com.changeguard.utility;

import com.changeguard.exception.InvalidContractException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CatalogInputTest {
    @Test void repositoryMetadataAcceptsSafeSupportedHosts() {
        assertEquals("https://github.com/team/repository", CatalogInput.repositoryUrl("https://github.com/team/repository"));
        assertEquals("https://gitlab.com/group/subgroup/repository", CatalogInput.repositoryUrl("https://gitlab.com/group/subgroup/repository"));
        assertNull(CatalogInput.repositoryUrl(" "));
    }
    @Test void repositoryCredentialsAndUnsupportedHostsRejected() {
        for (String url : new String[]{"http://github.com/team/repo", "https://localhost/team/repo",
                "https://user:secret@github.com/team/repo", "https://github.com/team/repo?token=secret",
                "https://github.com/team/repo#fragment", "https://github.com/../repo"})
            assertThrows(InvalidContractException.class, () -> CatalogInput.repositoryUrl(url));
    }
    @Test void identifiersCannotBeBlankOrContainSpaces() {
        assertEquals("payments-api", CatalogInput.identifier("payments-api", "name"));
        assertThrows(InvalidContractException.class, () -> CatalogInput.identifier("payments api", "name"));
        assertThrows(InvalidContractException.class, () -> CatalogInput.identifier(null, "name"));
    }
}

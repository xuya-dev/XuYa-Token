package dev.xuya.token.core;

import dev.xuya.token.core.model.Permission;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionTest {

    @Test
    void parsesResourceAction() {
        Permission p = Permission.of("user:delete");
        assertEquals("user", p.getResource());
        assertEquals("delete", p.getAction());
    }

    @Test
    void singleSegmentMeansWildcardAction() {
        Permission p = Permission.of("user");
        assertEquals(Permission.WILDCARD, p.getAction());
    }

    @Test
    void wildcardImpliesEverything() {
        assertTrue(Permission.of("*:*").implies(Permission.of("user:delete")));
        assertTrue(Permission.of("user:*").implies(Permission.of("user:delete")));
        assertFalse(Permission.of("user:read").implies(Permission.of("user:delete")));
        assertFalse(Permission.of("*:read").implies(Permission.of("user:delete")));
    }
}

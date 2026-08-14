package dev.xuya.token.core;

import dev.xuya.token.core.model.Permission;
import dev.xuya.token.core.model.Role;
import dev.xuya.token.core.spi.CachedPermissionLoader;
import dev.xuya.token.core.spi.PermissionLoader;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachedPermissionLoaderTest {

    /** 计数版数据来源,记录实际回源次数。 */
    static final class CountingLoader implements PermissionLoader {
        final AtomicInteger roleLoads = new AtomicInteger();
        final AtomicInteger permissionLoads = new AtomicInteger();

        @Override
        public Optional<Role> loadRole(String roleCode) {
            roleLoads.incrementAndGet();
            return Optional.of(Role.builder(roleCode).build());
        }

        @Override
        public Set<Permission> loadPermissions(Set<String> roleCodes) {
            permissionLoads.incrementAndGet();
            Set<Permission> result = new HashSet<>();
            roleCodes.forEach(code -> result.add(Permission.of(code + ":read")));
            return result;
        }
    }

    @Test
    void cachesWithinTtl() {
        CountingLoader counting = new CountingLoader();
        CachedPermissionLoader cached = new CachedPermissionLoader(counting, 60_000);

        cached.loadRole("admin");
        cached.loadRole("admin");
        assertEquals(1, counting.roleLoads.get());

        cached.loadPermissions(Set.of("admin"));
        cached.loadPermissions(Set.of("admin"));
        assertEquals(1, counting.permissionLoads.get());
    }

    @Test
    void keyOrderDoesNotMatter() {
        CountingLoader counting = new CountingLoader();
        CachedPermissionLoader cached = new CachedPermissionLoader(counting, 60_000);

        cached.loadPermissions(new HashSet<>(Set.of("a", "b")));
        cached.loadPermissions(new HashSet<>(Set.of("b", "a")));
        assertEquals(1, counting.permissionLoads.get());
    }

    @Test
    void expiresAfterTtl() {
        CountingLoader counting = new CountingLoader();
        CachedPermissionLoader cached = new CachedPermissionLoader(counting, 0);

        cached.loadRole("admin");
        cached.loadRole("admin");
        assertEquals(2, counting.roleLoads.get());
    }

    @Test
    void invalidateAllForcesReload() {
        CountingLoader counting = new CountingLoader();
        CachedPermissionLoader cached = new CachedPermissionLoader(counting, 60_000);

        cached.loadRole("admin");
        cached.loadPermissions(Set.of("admin"));
        cached.invalidateAll();
        cached.loadRole("admin");
        cached.loadPermissions(Set.of("admin"));

        assertEquals(2, counting.roleLoads.get());
        assertEquals(2, counting.permissionLoads.get());
    }

    @Test
    void delegatesResultContent() {
        CachedPermissionLoader cached = new CachedPermissionLoader(new CountingLoader(), 60_000);
        assertTrue(cached.loadPermissions(Set.of("admin")).contains(Permission.of("admin:read")));
        assertTrue(cached.loadRole("admin").isPresent());
    }
}

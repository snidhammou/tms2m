package com.tms.server.repository;

import com.tms.server.domain.Manufacturer;
import com.tms.server.domain.Terminal;
import com.tms.server.domain.TerminalStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public interface TerminalRepository extends JpaRepository<Terminal, Long>, JpaSpecificationExecutor<Terminal> {

    Optional<Terminal> findBySerialNumber(String serialNumber);

    Optional<Terminal> findByDeviceTokenHash(String deviceTokenHash);

    long countByLastSeenAtAfter(Instant threshold);

    boolean existsByMerchantId(Long merchantId);

    boolean existsByGroupId(Long groupId);

    @Query("select t.manufacturer, count(t) from Terminal t group by t.manufacturer")
    List<Object[]> countByManufacturer();

    @Query("select t.status, count(t) from Terminal t group by t.status")
    List<Object[]> countByStatus();

    /** Filtre combinable utilisé par la liste admin et par les déploiements. */
    static Specification<Terminal> filter(Manufacturer manufacturer, TerminalStatus status,
                                          Long groupId, Long merchantId, String query) {
        return filter(manufacturer, status, groupId, merchantId, null, query);
    }

    /**
     * @param organizationIds organisation ciblée et toutes ses descendantes (null = pas de filtre).
     */
    static Specification<Terminal> filter(Manufacturer manufacturer, TerminalStatus status,
                                          Long groupId, Long merchantId,
                                          java.util.Collection<Long> organizationIds, String query) {
        return (root, cq, cb) -> {
            var p = cb.conjunction();
            if (organizationIds != null) {
                p = cb.and(p, root.join("merchant").get("organization").get("id").in(organizationIds));
            }
            if (manufacturer != null) {
                p = cb.and(p, cb.equal(root.get("manufacturer"), manufacturer));
            }
            if (status != null) {
                p = cb.and(p, cb.equal(root.get("status"), status));
            }
            if (groupId != null) {
                p = cb.and(p, cb.equal(root.get("group").get("id"), groupId));
            }
            if (merchantId != null) {
                p = cb.and(p, cb.equal(root.get("merchant").get("id"), merchantId));
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                p = cb.and(p, cb.or(
                        cb.like(cb.lower(root.get("serialNumber")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("tid"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("model"), "")), like)));
            }
            return p;
        };
    }
}

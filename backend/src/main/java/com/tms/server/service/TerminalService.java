package com.tms.server.service;

import com.tms.server.config.TmsProperties;
import com.tms.server.domain.*;
import com.tms.server.repository.*;
import com.tms.server.web.dto.AdminDtos.*;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class TerminalService {

    private static final int LOW_BATTERY_PERCENT = 20;
    private static final double LOW_STORAGE_RATIO = 0.10;

    private final TerminalRepository terminals;
    private final MerchantRepository merchants;
    private final TerminalGroupRepository groups;
    private final OrganizationRepository organizationRepo;
    private final TaskRepository tasks;
    private final AppPackageRepository apps;
    private final ParameterRepository parameters;
    private final TerminalMetricRepository metrics;
    private final OrganizationService organizations;
    private final TemplateService templates;
    private final AuditService audit;
    private final JsonSupport json;
    private final TmsProperties props;
    private final SyncSignalService signals;

    public TerminalService(TerminalRepository terminals, MerchantRepository merchants, TerminalGroupRepository groups,
                           OrganizationRepository organizationRepo, TaskRepository tasks, AppPackageRepository apps,
                           ParameterRepository parameters, TerminalMetricRepository metrics,
                           OrganizationService organizations, TemplateService templates, AuditService audit,
                           JsonSupport json, TmsProperties props, SyncSignalService signals) {
        this.signals = signals;
        this.terminals = terminals;
        this.merchants = merchants;
        this.groups = groups;
        this.organizationRepo = organizationRepo;
        this.tasks = tasks;
        this.apps = apps;
        this.parameters = parameters;
        this.metrics = metrics;
        this.organizations = organizations;
        this.templates = templates;
        this.audit = audit;
        this.json = json;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<TerminalDto> list(Manufacturer manufacturer, TerminalStatus status, Long groupId, Long merchantId,
                                  Long organizationId, String q) {
        Collection<Long> orgIds = organizationId == null ? null : organizations.descendantIds(organizationId);
        return terminals.findAll(TerminalRepository.filter(manufacturer, status, groupId, merchantId, orgIds, q),
                        Sort.by("serialNumber"))
                .stream().map(t -> toDto(t, false)).toList();
    }

    @Transactional(readOnly = true)
    public TerminalDto get(Long id) {
        return toDto(find(id), true);
    }

    @Transactional
    public TerminalDto create(TerminalCreateRequest req) {
        Terminal t = register(req.serialNumber(), req.manufacturer(), req.model(), req.tid(),
                merchant(req.merchantId()), group(req.groupId()));
        return toDto(t, true);
    }

    /** Pré-enregistrement par lots (liaison de terminaux en masse avant livraison). */
    @Transactional
    public BulkRegisterResponse bulkRegister(BulkRegisterRequest req) {
        Merchant m = merchant(req.merchantId());
        TerminalGroup g = group(req.groupId());
        List<String> skipped = new ArrayList<>();
        int created = 0;
        Set<String> seen = new HashSet<>();
        for (String line : req.serialNumbers().split("[\\r\\n,;]+")) {
            String serial = line.trim();
            if (serial.isEmpty() || !seen.add(serial)) {
                continue;
            }
            if (terminals.findBySerialNumber(serial).isPresent()) {
                skipped.add(serial);
                continue;
            }
            register(serial, req.manufacturer(), req.model(), null, m, g);
            created++;
        }
        audit.log("BULK_REGISTER", null, created + " terminal(aux) pré-enregistré(s), " + skipped.size() + " ignoré(s)");
        return new BulkRegisterResponse(created, skipped);
    }

    private Terminal register(String serialNumber, Manufacturer manufacturer, String model, String tid,
                              Merchant merchant, TerminalGroup group) {
        String serial = serialNumber.trim();
        if (terminals.findBySerialNumber(serial).isPresent()) {
            throw ApiException.conflict("Numéro de série déjà enregistré : " + serial);
        }
        Terminal t = new Terminal();
        t.setSerialNumber(serial);
        t.setManufacturer(manufacturer != null ? manufacturer : Manufacturer.OTHER);
        t.setModel(model);
        t.setTid(tid);
        t.setMerchant(merchant);
        t.setGroup(group);
        terminals.save(t);
        audit.log("TERMINAL_REGISTERED", t.getId(), serial);
        // Zéro contact : les tâches du modèle attendent l'enrôlement du terminal
        templates.onTerminalJoinedGroup(t);
        return t;
    }

    @Transactional
    public TerminalDto update(Long id, TerminalUpdateRequest req) {
        Terminal t = find(id);
        Long oldGroup = t.getGroup() != null ? t.getGroup().getId() : null;
        t.setTid(req.tid());
        t.setMerchant(merchant(req.merchantId()));
        t.setGroup(group(req.groupId()));
        if (req.status() != null && req.status() != t.getStatus()) {
            if (req.status() == TerminalStatus.ACTIVE && t.getDeviceTokenHash() == null) {
                throw ApiException.badRequest("Un terminal jamais enrôlé ne peut pas être activé manuellement");
            }
            audit.log("TERMINAL_STATUS", id, t.getStatus() + " → " + req.status());
            t.setStatus(req.status());
        }
        if (!Objects.equals(oldGroup, req.groupId())) {
            audit.log("TERMINAL_GROUP", id, t.getGroup() != null ? t.getGroup().getName() : "aucun groupe");
            templates.onTerminalJoinedGroup(t);
        }
        return toDto(t, true);
    }

    @Transactional
    public void delete(Long id) {
        Terminal t = find(id);
        tasks.deleteByTerminalId(id);
        metrics.deleteByTerminalId(id);
        parameters.deleteByScopeAndScopeRef(ParameterScope.TERMINAL, id);
        terminals.delete(t);
        audit.log("TERMINAL_DELETED", id, t.getSerialNumber());
    }

    @Transactional(readOnly = true)
    public List<MetricPointDto> metrics(Long id, int hours) {
        find(id);
        Instant since = Instant.now().minus(Duration.ofHours(Math.max(1, Math.min(hours, 24 * 30))));
        return metrics.findByTerminalIdAndRecordedAtAfterOrderByRecordedAtAsc(id, since).stream()
                .map(MetricPointDto::of).toList();
    }

    /** Purge quotidienne des points de supervision trop anciens. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeOldMetrics() {
        metrics.deleteOlderThan(Instant.now().minus(Duration.ofDays(props.storage().retentionDaysOrDefault())));
    }

    @Transactional(readOnly = true)
    public DashboardDto dashboard() {
        List<Terminal> all = terminals.findAll();
        Instant threshold = onlineThreshold();
        long online = all.stream().filter(t -> t.getLastSeenAt() != null && t.getLastSeenAt().isAfter(threshold)).count();
        Map<String, Long> byNetwork = new TreeMap<>();
        all.stream().filter(t -> t.getNetworkType() != null)
                .forEach(t -> byNetwork.merge(t.getNetworkType(), 1L, Long::sum));
        long lowBattery = all.stream().filter(t -> t.getBatteryLevel() != null && t.getBatteryLevel() < LOW_BATTERY_PERCENT).count();
        long lowStorage = all.stream().filter(this::isLowStorage).count();
        long owners = all.stream().filter(t -> Boolean.TRUE.equals(t.getDeviceOwner())).count();
        return new DashboardDto(all.size(), online, all.size() - online,
                toMap(terminals.countByManufacturer()), toMap(terminals.countByStatus()), toMap(tasks.countByStatus()),
                byNetwork, lowBattery, lowStorage, owners,
                merchants.count(), groups.count(), apps.count(), organizationRepo.count());
    }

    private boolean isLowStorage(Terminal t) {
        return t.getStorageTotalBytes() != null && t.getStorageFreeBytes() != null && t.getStorageTotalBytes() > 0
                && (double) t.getStorageFreeBytes() / t.getStorageTotalBytes() < LOW_STORAGE_RATIO;
    }

    /** Un terminal est "en ligne" s'il a contacté le serveur depuis moins de 3 intervalles de polling. */
    private Instant onlineThreshold() {
        return Instant.now().minus(Duration.ofSeconds(3L * props.device().pollIntervalSeconds()));
    }

    private TerminalDto toDto(Terminal t, boolean withApps) {
        boolean online = t.getLastSeenAt() != null && t.getLastSeenAt().isAfter(onlineThreshold());
        Merchant m = t.getMerchant();
        Organization o = m != null ? m.getOrganization() : null;
        TerminalGroup g = t.getGroup();
        return new TerminalDto(t.getId(), t.getSerialNumber(), t.getManufacturer(), t.getModel(),
                t.getOsVersion(), t.getFirmwareVersion(), t.getAgentVersion(), t.getTid(), t.getStatus(), online,
                m != null ? m.getId() : null, m != null ? m.getName() : null,
                o != null ? o.getId() : null, o != null ? o.getName() : null,
                g != null ? g.getId() : null, g != null ? g.getName() : null,
                t.getCreatedAt(), t.getEnrolledAt(), t.getLastSeenAt(),
                t.getBatteryLevel(), t.getIpAddress(), t.getLatitude(), t.getLongitude(), t.getLocationAt(),
                t.getStorageTotalBytes(), t.getStorageFreeBytes(), t.getRamTotalBytes(), t.getRamAvailBytes(),
                t.getNetworkType(), t.getUptimeSeconds(), t.getDeviceOwner(),
                t.getAutoRunPackage(), json.readStringList(t.getKioskPackagesJson()),
                withApps ? json.readApps(t.getInstalledAppsJson()) : null,
                signals.isConnected(t.getId()));
    }

    /** Synchronisation forcée depuis la console. */
    @Transactional(readOnly = true)
    public SyncResponse forceSync(Long id) {
        Terminal t = find(id);
        boolean delivered = signals.signal(id);
        audit.log("FORCE_SYNC", id, t.getSerialNumber() + (delivered ? " (instantané)" : " (au prochain contact)"));
        return new SyncResponse(delivered, Instant.now());
    }

    private Terminal find(Long id) {
        return terminals.findById(id).orElseThrow(() -> ApiException.notFound("Terminal", id));
    }

    private Merchant merchant(Long id) {
        return id == null ? null : merchants.findById(id).orElseThrow(() -> ApiException.notFound("Marchand", id));
    }

    private TerminalGroup group(Long id) {
        return id == null ? null : groups.findById(id).orElseThrow(() -> ApiException.notFound("Groupe", id));
    }

    private static Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> map = new TreeMap<>();
        rows.forEach(r -> map.put(String.valueOf(r[0]), (Long) r[1]));
        return map;
    }
}

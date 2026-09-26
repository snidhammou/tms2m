package com.tms.server.service;

import com.tms.server.config.TmsProperties;
import com.tms.server.domain.*;
import com.tms.server.repository.*;
import com.tms.server.web.dto.AdminDtos.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class TerminalService {

    private final TerminalRepository terminals;
    private final MerchantRepository merchants;
    private final TerminalGroupRepository groups;
    private final TaskRepository tasks;
    private final AppPackageRepository apps;
    private final ParameterRepository parameters;
    private final JsonSupport json;
    private final TmsProperties props;

    public TerminalService(TerminalRepository terminals, MerchantRepository merchants, TerminalGroupRepository groups,
                           TaskRepository tasks, AppPackageRepository apps, ParameterRepository parameters,
                           JsonSupport json, TmsProperties props) {
        this.terminals = terminals;
        this.merchants = merchants;
        this.groups = groups;
        this.tasks = tasks;
        this.apps = apps;
        this.parameters = parameters;
        this.json = json;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<TerminalDto> list(Manufacturer manufacturer, TerminalStatus status, Long groupId, Long merchantId,
                                  String q) {
        return terminals.findAll(TerminalRepository.filter(manufacturer, status, groupId, merchantId, q),
                        Sort.by("serialNumber"))
                .stream().map(t -> toDto(t, false)).toList();
    }

    @Transactional(readOnly = true)
    public TerminalDto get(Long id) {
        return toDto(find(id), true);
    }

    @Transactional
    public TerminalDto create(TerminalCreateRequest req) {
        String serial = req.serialNumber().trim();
        if (terminals.findBySerialNumber(serial).isPresent()) {
            throw ApiException.conflict("Numéro de série déjà enregistré : " + serial);
        }
        Terminal t = new Terminal();
        t.setSerialNumber(serial);
        t.setManufacturer(req.manufacturer() != null ? req.manufacturer() : Manufacturer.OTHER);
        t.setModel(req.model());
        t.setTid(req.tid());
        t.setMerchant(merchant(req.merchantId()));
        t.setGroup(group(req.groupId()));
        return toDto(terminals.save(t), true);
    }

    @Transactional
    public TerminalDto update(Long id, TerminalUpdateRequest req) {
        Terminal t = find(id);
        t.setTid(req.tid());
        t.setMerchant(merchant(req.merchantId()));
        t.setGroup(group(req.groupId()));
        if (req.status() != null && req.status() != t.getStatus()) {
            if (req.status() == TerminalStatus.ACTIVE && t.getDeviceTokenHash() == null) {
                throw ApiException.badRequest("Un terminal jamais enrôlé ne peut pas être activé manuellement");
            }
            t.setStatus(req.status());
        }
        return toDto(t, true);
    }

    @Transactional
    public void delete(Long id) {
        Terminal t = find(id);
        tasks.deleteByTerminalId(id);
        parameters.deleteByScopeAndScopeRef(ParameterScope.TERMINAL, id);
        terminals.delete(t);
    }

    @Transactional(readOnly = true)
    public DashboardDto dashboard() {
        long total = terminals.count();
        long online = terminals.countByLastSeenAtAfter(onlineThreshold());
        return new DashboardDto(total, online, total - online,
                toMap(terminals.countByManufacturer()), toMap(terminals.countByStatus()), toMap(tasks.countByStatus()),
                merchants.count(), groups.count(), apps.count());
    }

    /** Un terminal est "en ligne" s'il a contacté le serveur depuis moins de 3 intervalles de polling. */
    private Instant onlineThreshold() {
        return Instant.now().minus(Duration.ofSeconds(3L * props.device().pollIntervalSeconds()));
    }

    private TerminalDto toDto(Terminal t, boolean withApps) {
        boolean online = t.getLastSeenAt() != null && t.getLastSeenAt().isAfter(onlineThreshold());
        Merchant m = t.getMerchant();
        TerminalGroup g = t.getGroup();
        return new TerminalDto(t.getId(), t.getSerialNumber(), t.getManufacturer(), t.getModel(),
                t.getOsVersion(), t.getFirmwareVersion(), t.getAgentVersion(), t.getTid(), t.getStatus(), online,
                m != null ? m.getId() : null, m != null ? m.getName() : null,
                g != null ? g.getId() : null, g != null ? g.getName() : null,
                t.getCreatedAt(), t.getEnrolledAt(), t.getLastSeenAt(),
                t.getBatteryLevel(), t.getIpAddress(), t.getLatitude(), t.getLongitude(),
                withApps ? json.readApps(t.getInstalledAppsJson()) : null);
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

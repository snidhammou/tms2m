package com.tms.server.service;

import com.tms.server.config.TmsProperties;
import com.tms.server.config.Tokens;
import com.tms.server.domain.Manufacturer;
import com.tms.server.domain.Terminal;
import com.tms.server.domain.TerminalStatus;
import com.tms.server.repository.TerminalRepository;
import com.tms.server.web.dto.DeviceDtos.EnrollRequest;
import com.tms.server.web.dto.DeviceDtos.EnrollResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);

    private final TerminalRepository terminals;
    private final TmsProperties props;

    public EnrollmentService(TerminalRepository terminals, TmsProperties props) {
        this.terminals = terminals;
        this.props = props;
    }

    /**
     * Enrôle (ou ré-enrôle) un terminal. Un nouveau jeton est émis à chaque appel,
     * l'ancien devient donc invalide.
     */
    @Transactional
    public EnrollResponse enroll(EnrollRequest req) {
        if (!Tokens.constantTimeEquals(props.enrollment().key(), req.enrollmentKey())) {
            throw ApiException.forbidden("Clé d'enrôlement invalide");
        }
        String serial = req.serialNumber().trim();
        Terminal terminal = terminals.findBySerialNumber(serial).orElse(null);
        if (terminal == null) {
            if (!props.enrollment().autoAccept()) {
                throw ApiException.forbidden("Terminal non pré-enregistré : " + serial);
            }
            terminal = new Terminal();
            terminal.setSerialNumber(serial);
        }
        if (terminal.getStatus() == TerminalStatus.DISABLED) {
            throw ApiException.forbidden("Terminal désactivé : " + serial);
        }

        Manufacturer detected = Manufacturer.detect(req.manufacturer());
        if (detected != Manufacturer.OTHER || terminal.getManufacturer() == null) {
            terminal.setManufacturer(detected);
        }
        terminal.setModel(req.model());
        terminal.setOsVersion(req.osVersion());
        terminal.setFirmwareVersion(req.firmwareVersion());
        terminal.setAgentVersion(req.agentVersion());

        String token = Tokens.newDeviceToken();
        Instant now = Instant.now();
        terminal.setDeviceTokenHash(Tokens.sha256(token));
        terminal.setStatus(TerminalStatus.ACTIVE);
        terminal.setEnrolledAt(now);
        terminal.setLastSeenAt(now);
        terminals.save(terminal);

        log.info("Terminal enrôlé : {} ({} {})", serial, terminal.getManufacturer(), terminal.getModel());
        return new EnrollResponse(terminal.getId(), token, props.device().pollIntervalSeconds());
    }
}

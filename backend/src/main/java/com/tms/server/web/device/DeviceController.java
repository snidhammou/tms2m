package com.tms.server.web.device;

import com.tms.server.domain.AppPackage;
import com.tms.server.service.AppStorageService;
import com.tms.server.service.DeviceService;
import com.tms.server.service.EnrollmentService;
import com.tms.server.service.IconService;
import com.tms.server.service.SyncSignalService;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Map;
import com.tms.server.web.dto.DeviceDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * API consommée par l'agent Android. Toutes les routes sauf /enroll exigent
 * l'en-tête X-Device-Token obtenu lors de l'enrôlement.
 */
@RestController
@RequestMapping("/api/device/v1")
public class DeviceController {

    private final EnrollmentService enrollment;
    private final DeviceService devices;
    private final AppStorageService apps;
    private final SyncSignalService signals;
    private final IconService icons;

    public DeviceController(EnrollmentService enrollment, DeviceService devices, AppStorageService apps,
                            SyncSignalService signals, IconService icons) {
        this.icons = icons;
        this.enrollment = enrollment;
        this.devices = devices;
        this.apps = apps;
        this.signals = signals;
    }

    @PostMapping("/enroll")
    public EnrollResponse enroll(@Valid @RequestBody EnrollRequest req) {
        return enrollment.enroll(req);
    }

    @PostMapping("/heartbeat")
    public HeartbeatResponse heartbeat(Authentication auth, @RequestBody HeartbeatRequest req,
                                       HttpServletRequest http) {
        return devices.heartbeat(terminalId(auth), req, http.getRemoteAddr());
    }

    /**
     * Canal temps réel (long polling) : la réponse arrive dès que le serveur demande une
     * synchronisation, ou au bout de {@code timeout} secondes (10 à 55) sans événement.
     */
    @GetMapping("/wait")
    public DeferredResult<Map<String, Object>> waitForSync(Authentication auth,
                                                            @RequestParam(defaultValue = "45") int timeout) {
        return signals.await(terminalId(auth), Math.max(10, Math.min(timeout, 55)) * 1000L);
    }

    @PostMapping("/tasks/{taskId}/status")
    public ResponseEntity<Void> taskStatus(Authentication auth, @PathVariable Long taskId,
                                           @Valid @RequestBody TaskStatusUpdate update) {
        devices.updateTaskStatus(terminalId(auth), taskId, update);
        return ResponseEntity.noContent().build();
    }

    /** Fichier produit par une tâche (logs, fichier extrait). */
    @PostMapping(value = "/tasks/{taskId}/artifact", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> taskArtifact(Authentication auth, @PathVariable Long taskId,
                                             @RequestParam("file") MultipartFile file) {
        devices.uploadArtifact(terminalId(auth), taskId, file);
        return ResponseEntity.noContent().build();
    }

    /** Icône (PNG) d'une application installée, demandée via {@code iconsWanted} du heartbeat. */
    @PostMapping(value = "/icons/{packageName:.+}", consumes = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<Void> uploadIcon(@PathVariable String packageName, @RequestBody byte[] png) {
        icons.storeFromDevice(packageName, png);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/parameters")
    public ParametersResponse parameters(Authentication auth, @RequestParam String packageName) {
        return devices.parameters(terminalId(auth), packageName);
    }

    @GetMapping("/apps/{appId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long appId) {
        AppPackage app = apps.get(appId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.android.package-archive"))
                .contentLength(app.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + app.getStoredFileName() + "\"")
                .header("X-Checksum-SHA256", app.getSha256())
                .body(apps.file(app));
    }

    private static Long terminalId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }
}

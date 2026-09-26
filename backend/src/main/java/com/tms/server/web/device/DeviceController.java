package com.tms.server.web.device;

import com.tms.server.domain.AppPackage;
import com.tms.server.service.AppStorageService;
import com.tms.server.service.DeviceService;
import com.tms.server.service.EnrollmentService;
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

    public DeviceController(EnrollmentService enrollment, DeviceService devices, AppStorageService apps) {
        this.enrollment = enrollment;
        this.devices = devices;
        this.apps = apps;
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

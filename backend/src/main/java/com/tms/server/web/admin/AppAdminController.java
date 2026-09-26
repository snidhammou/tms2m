package com.tms.server.web.admin;

import com.tms.server.domain.AppPackage;
import com.tms.server.service.AppStorageService;
import com.tms.server.service.IconService;
import com.tms.server.web.dto.AdminDtos.AppPackageDto;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/admin/v1/apps")
public class AppAdminController {

    private final AppStorageService apps;
    private final IconService icons;

    public AppAdminController(AppStorageService apps, IconService icons) {
        this.icons = icons;
        this.apps = apps;
    }

    @GetMapping
    public List<AppPackageDto> list() {
        return apps.list();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AppPackageDto upload(@RequestParam("file") MultipartFile file,
                                @RequestParam(required = false) String packageName,
                                @RequestParam(required = false) String versionName,
                                @RequestParam(required = false) Long versionCode,
                                @RequestParam(required = false) String description) {
        return apps.upload(file, packageName, versionName, versionCode, description);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        AppPackage app = apps.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.android.package-archive"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + app.getStoredFileName() + "\"")
                .body(apps.file(app));
    }

    /** Icône d'une application par package (dépôt ou remontée par un terminal) ; 404 si inconnue. */
    @GetMapping("/icons/{packageName:.+}")
    public ResponseEntity<Resource> icon(@PathVariable String packageName) {
        return icons.find(packageName)
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                        .<Resource>body(new FileSystemResource(p)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        apps.delete(id);
    }
}

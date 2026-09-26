package com.tms.server.service;

import com.tms.server.config.TmsProperties;
import com.tms.server.config.Tokens;
import com.tms.server.domain.AppPackage;
import com.tms.server.repository.AppPackageRepository;
import com.tms.server.web.dto.AdminDtos.AppPackageDto;
import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** Dépôt d'APK : stockage disque + métadonnées en base. */
@Service
public class AppStorageService {

    private static final Logger log = LoggerFactory.getLogger(AppStorageService.class);

    private final AppPackageRepository repo;
    private final Path storageDir;

    public AppStorageService(AppPackageRepository repo, TmsProperties props) {
        this.repo = repo;
        this.storageDir = Path.of(props.storage().apkDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de créer " + storageDir, e);
        }
    }

    @Transactional(readOnly = true)
    public List<AppPackageDto> list() {
        return repo.findAllByOrderByPackageNameAscVersionCodeDesc().stream().map(AppPackageDto::of).toList();
    }

    @Transactional(readOnly = true)
    public AppPackage get(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Application", id));
    }

    /**
     * Stocke un APK. packageName / versionName / versionCode sont lus dans le manifeste ;
     * les valeurs fournies ne sont utilisées que si le manifeste est illisible.
     */
    @Transactional
    public AppPackageDto upload(MultipartFile file, String packageName, String versionName, Long versionCode,
                                String description) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Fichier APK manquant");
        }
        Path tmp = null;
        try {
            tmp = Files.createTempFile(storageDir, "upload-", ".apk");
            MessageDigest md = Tokens.digest();
            try (InputStream in = new DigestInputStream(file.getInputStream(), md)) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            String sha256 = HexFormat.of().formatHex(md.digest());

            String label = null;
            ApkMeta meta = readMeta(tmp);
            if (meta != null) {
                // Le manifeste fait foi : Android refuse d'installer un APK dont le package
                // ne correspond pas à celui annoncé. Les valeurs saisies ne servent qu'en secours.
                label = meta.getLabel();
                packageName = meta.getPackageName();
                if (!isBlank(meta.getVersionName())) versionName = meta.getVersionName();
                versionCode = meta.getVersionCode();
            }
            if (isBlank(packageName) || versionCode == null) {
                throw ApiException.badRequest("APK illisible : renseignez packageName et versionCode");
            }
            if (repo.existsByPackageNameAndVersionCode(packageName, versionCode)) {
                throw ApiException.conflict(packageName + " versionCode " + versionCode + " existe déjà");
            }

            String storedName = packageName + "-" + versionCode + "-" + sha256.substring(0, 8) + ".apk";
            Files.move(tmp, storageDir.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
            tmp = null;

            AppPackage app = new AppPackage();
            app.setPackageName(packageName);
            app.setVersionName(versionName);
            app.setVersionCode(versionCode);
            app.setLabel(label);
            app.setStoredFileName(storedName);
            app.setOriginalFileName(file.getOriginalFilename());
            app.setSizeBytes(file.getSize());
            app.setSha256(sha256);
            app.setDescription(description);
            return AppPackageDto.of(repo.save(app));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // nettoyage best effort
                }
            }
        }
    }

    public Resource file(AppPackage app) {
        Path path = storageDir.resolve(app.getStoredFileName());
        if (!Files.exists(path)) {
            throw ApiException.notFound("Fichier APK", app.getStoredFileName());
        }
        return new FileSystemResource(path);
    }

    @Transactional
    public void delete(Long id) {
        AppPackage app = get(id);
        repo.delete(app);
        try {
            Files.deleteIfExists(storageDir.resolve(app.getStoredFileName()));
        } catch (IOException e) {
            log.warn("Suppression du fichier {} impossible", app.getStoredFileName(), e);
        }
    }

    private static ApkMeta readMeta(Path apk) {
        try (ApkFile apkFile = new ApkFile(apk.toFile())) {
            return apkFile.getApkMeta();
        } catch (Exception e) {
            log.info("Métadonnées APK non lisibles ({}), valeurs manuelles requises", e.getMessage());
            return null;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

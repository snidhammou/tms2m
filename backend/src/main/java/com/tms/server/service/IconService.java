package com.tms.server.service;

import com.tms.server.config.TmsProperties;
import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.IconFace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Icônes d'applications, une par package (PNG). Sources : l'APK téléversé dans le dépôt,
 * ou l'agent qui envoie l'icône des applications installées que le serveur ne connaît pas encore.
 */
@Service
public class IconService {

    private static final Logger log = LoggerFactory.getLogger(IconService.class);
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+");
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};
    public static final int MAX_ICON_BYTES = 256 * 1024;
    /** Nombre maximal d'icônes demandées à un terminal par heartbeat. */
    private static final int MAX_WANTED_PER_HEARTBEAT = 8;

    private final Path iconDir;

    public IconService(TmsProperties props) {
        this.iconDir = Path.of(props.storage().apkDir()).toAbsolutePath().normalize().resolve("icons");
        try {
            Files.createDirectories(iconDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de créer " + iconDir, e);
        }
    }

    public Optional<Path> find(String packageName) {
        if (!isValidPackage(packageName)) {
            return Optional.empty();
        }
        Path p = iconDir.resolve(packageName + ".png");
        return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
    }

    /** Packages installés sans icône côté serveur (demandés à l'agent). */
    public List<String> missing(Collection<String> installedPackages) {
        if (installedPackages == null) {
            return List.of();
        }
        return installedPackages.stream()
                .filter(IconService::isValidPackage)
                .distinct()
                .filter(p -> find(p).isEmpty())
                .limit(MAX_WANTED_PER_HEARTBEAT)
                .toList();
    }

    /** Icône envoyée par un terminal ; n'écrase pas celle extraite d'un APK du dépôt. */
    public void storeFromDevice(String packageName, byte[] png) {
        if (!isValidPackage(packageName)) {
            throw ApiException.badRequest("Nom de package invalide");
        }
        if (png == null || png.length < 8 || png.length > MAX_ICON_BYTES || !isPng(png)) {
            throw ApiException.badRequest("Icône PNG attendue (max " + MAX_ICON_BYTES / 1024 + " Ko)");
        }
        if (find(packageName).isPresent()) {
            return;
        }
        write(packageName, png);
    }

    /** Extrait la plus grande icône PNG d'un APK (les icônes adaptatives XML sont ignorées). */
    public void extractFromApk(String packageName, Path apk) {
        if (!isValidPackage(packageName)) {
            return;
        }
        try (ApkFile apkFile = new ApkFile(apk.toFile())) {
            byte[] best = null;
            for (IconFace icon : apkFile.getAllIcons()) {
                byte[] data = icon.getData();
                if (icon.isFile() && data != null && isPng(data) && (best == null || data.length > best.length)) {
                    best = data;
                }
            }
            if (best != null && best.length <= 4 * MAX_ICON_BYTES) {
                write(packageName, best);
            }
        } catch (Exception e) {
            log.info("Icône non extractible de {} : {}", apk.getFileName(), e.getMessage());
        }
    }

    private void write(String packageName, byte[] png) {
        try {
            Path tmp = Files.createTempFile(iconDir, "icon-", ".tmp");
            Files.write(tmp, png);
            Files.move(tmp, iconDir.resolve(packageName + ".png"), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("Écriture de l'icône {} impossible", packageName, e);
        }
    }

    private static boolean isPng(byte[] data) {
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (data[i] != PNG_MAGIC[i]) return false;
        }
        return true;
    }

    public static boolean isValidPackage(String p) {
        return p != null && p.length() <= 255 && PACKAGE.matcher(p).matches();
    }
}

package kz.vault.securefilevault.controller;

import model.AuditLog;
import model.FileMetadata;
import model.User;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import repository.AuditLogRepository;
import repository.FileMetadataRepository;
import repository.UserRepository;
import service.CryptoService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileMetadataRepository fileRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final CryptoService cryptoService;
    private final Path storageDir = Paths.get("vault-storage");

    public FileController(FileMetadataRepository fileRepository,
                          UserRepository userRepository,
                          AuditLogRepository auditLogRepository,
                          CryptoService cryptoService) throws Exception {
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.cryptoService = cryptoService;
        Files.createDirectories(storageDir);
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadEncryptedFile(@RequestParam("file") MultipartFile file,
                                                 @RequestParam("username") String username,
                                                 @RequestParam("sha256Hash") String clientHash) {
        try {
            byte[] encryptedBytes = file.getBytes();
            String serverHash = cryptoService.calculateSHA256(encryptedBytes);
            if (!serverHash.equalsIgnoreCase(clientHash)) {
                return ResponseEntity.badRequest().body("Тұтастық қатесі: SHA-256 хэші сәйкес келмейді!");
            }

            User user = userRepository.findByUsername(username).orElseGet(() -> {
                User newUser = new User();
                newUser.setUsername(username);
                newUser.setPasswordHash("CLIENT_SIDE_MANAGED");
                return userRepository.save(newUser);
            });

            String storedFileName = UUID.randomUUID() + "_" + file.getOriginalFilename() + ".enc";
            Path targetPath = storageDir.resolve(storedFileName);
            Files.write(targetPath, encryptedBytes);

            FileMetadata metadata = new FileMetadata();
            metadata.setFileName(file.getOriginalFilename());
            metadata.setStoragePath(targetPath.toString());
            metadata.setSha256Hash(serverHash);
            metadata.setOwner(user);
            FileMetadata saved = fileRepository.save(metadata);

            auditLogRepository.save(new AuditLog("UPLOAD_FILE: " + file.getOriginalFilename(), "127.0.0.1", user));
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Жүктеу қатесі: " + e.getMessage());
        }
    }

    @GetMapping("/user/{username}")
    public List<FileMetadata> getUserFiles(@PathVariable String username) {
        return fileRepository.findByOwnerUsername(username);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> downloadEncryptedFile(@PathVariable Long id) {
        try {
            FileMetadata metadata = fileRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Файл табылмады"));

            byte[] encryptedBytes = Files.readAllBytes(Paths.get(metadata.getStoragePath()));
            auditLogRepository.save(new AuditLog("DOWNLOAD_FILE: " + metadata.getFileName(), "127.0.0.1", metadata.getOwner()));

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + metadata.getFileName() + ".enc\"")
                    .header("X-File-SHA256", metadata.getSha256Hash())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(encryptedBytes);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}

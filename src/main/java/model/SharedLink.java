package model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "shared_links")
public class SharedLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String token;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String encryptedAesKeyRSA;

    private LocalDateTime expiresAt;

    @ManyToOne
    @JoinColumn(name = "file_id")
    private FileMetadata file;

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getEncryptedAesKeyRSA() { return encryptedAesKeyRSA; }
    public void setEncryptedAesKeyRSA(String encryptedAesKeyRSA) { this.encryptedAesKeyRSA = encryptedAesKeyRSA; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public FileMetadata getFile() { return file; }
    public void setFile(FileMetadata file) { this.file = file; }
}

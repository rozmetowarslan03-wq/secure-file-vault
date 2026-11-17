package client;

import service.CryptoService;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.Base64;
import java.util.Scanner;

public class VaultClientApp {

    public static void main(String[] args) {
        CryptoService crypto = new CryptoService();
        Scanner scanner = new Scanner(System.in);

        System.out.println("====================================================");
        System.out.println("   SECURE FILE VAULT — КЛИЕНТТІК ҚОСЫМША (DEMO)     ");
        System.out.println("====================================================");

        try {
            // 1. Бастапқы құпия мәтінді енгізу
            System.out.print("\n1. Шифрланатын құпия мәтінді жазыңыз: ");
            String secretText = scanner.nextLine();

            System.out.print("2. Құпиясөз (Master Password) ойлап табыңыз: ");
            String password = scanner.nextLine();

            // 2. PBKDF2 арқылы кілт генерациялау
            byte[] salt = crypto.generateSalt();
            SecretKey aesKey = crypto.deriveKeyPBKDF2(password, salt);
            System.out.println("\n[OK] PBKDF2 кілті сәтті генерацияланды (256-bit AES).");

            // 3. Клиент жағында AES-GCM шифрлау
            byte[] plainBytes = secretText.getBytes(StandardCharsets.UTF_8);
            byte[] encryptedBytes = crypto.encryptAESGCM(plainBytes, aesKey);
            String sha256Hash = crypto.calculateSHA256(encryptedBytes);

            Path encFile = Paths.get("secret_document.enc");
            Files.write(encFile, encryptedBytes);

            System.out.println("[OK] Файл локальді түрде AES-GCM алгоритмімен шифрланды!");
            System.out.println(" -> Шифрланған файл: " + encFile.toAbsolutePath());
            System.out.println(" -> SHA-256 тұтастық хэші: " + sha256Hash);
            System.out.println(" -> Шифрланған мазмұны (Base64): " +
                    Base64.getEncoder().encodeToString(encryptedBytes).substring(0, Math.min(40, encryptedBytes.length)) + "...");

            // 4. RSA арқылы басқа қолданушыға кілтті бөлісу демонстрациясы
            KeyPair recipientRsaKeys = crypto.generateRSAKeyPair();
            String encryptedAesForFriend = crypto.encryptKeyWithRSA(aesKey, recipientRsaKeys.getPublic());
            System.out.println("\n[OK] RSA-2048 арқылы кілт бөлісу (Key Exchange) орындалды:");
            System.out.println(" -> Алушының ашық кілтімен шифрланған AES кілті: " + encryptedAesForFriend.substring(0, 40) + "...");

            // 5. Файлды қайта оқу және дешифрлау
            System.out.println("\n----------------------------------------------------");
            System.out.print("3. Файлды ашу (дешифрлау) үшін құпиясөзді енгізіңіз: ");
            String checkPassword = scanner.nextLine();

            byte[] readEncrypted = Files.readAllBytes(encFile);
            String currentHash = crypto.calculateSHA256(readEncrypted);

            if (!currentHash.equals(sha256Hash)) {
                System.out.println("[ҚАТЕ] Файл жолда өзгертілген! SHA-256 сәйкес келмейді.");
                return;
            }
            System.out.println("[OK] SHA-256 тұтастығы расталды (файл бүлінбеген).");

            SecretKey decryptKey = crypto.deriveKeyPBKDF2(checkPassword, salt);
            byte[] decryptedBytes = crypto.decryptAESGCM(readEncrypted, decryptKey);
            String recoveredText = new String(decryptedBytes, StandardCharsets.UTF_8);

            System.out.println("\n[СӘТТІ ДЕШИФРЛАНДЫ!] Бастапқы мәтін: " + recoveredText);

        } catch (javax.crypto.AEADBadTagException e) {
            System.out.println("\n[ҚАТЕ — ҚАТЫНАС БҰҒАТТАЛДЫ!] Құпиясөз қате немесе файл бұрмаланған!");
        } catch (Exception e) {
            System.out.println("\n[ҚАТЕ]: " + e.getMessage());
        }
    }
}

package dpas.utils.auth;

import dpas.common.domain.Announcement;
import dpas.common.domain.GeneralBoard;
import dpas.grpc.contract.Contract;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import org.apache.commons.lang3.ArrayUtils;

import javax.crypto.Cipher;
import java.io.IOException;
import java.security.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dpas.utils.auth.CipherUtils.keyFromBytes;

public class MacVerifier {

    public static boolean verifyMac(Contract.RegisterRequest request, Contract.MacReply reply, PublicKey serverKey) {
        try {
            byte[] mac = reply.getMac().toByteArray();
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, serverKey);
            byte[] hash = cipher.doFinal(mac);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] content = digest.digest(request.getMac().toByteArray());

            return Arrays.equals(content, hash);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }


    public static boolean verifyMac(PublicKey pubKey, byte[] content, byte[] mac) {
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, pubKey);
            byte[] hash = cipher.doFinal(mac);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Arrays.equals(digest.digest(content), hash);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    public static boolean verifyMac(Contract.ReadRequest request, Contract.ReadReply reply, PublicKey serverKey) {
        try {
            return verifyMac(serverKey, ByteUtils.toByteArray(request, reply.getAnnouncementsCount()), reply.getMac().toByteArray());
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean verifyMac(PublicKey key, StatusRuntimeException e) {
        Metadata data = e.getTrailers();
        byte[] content = ArrayUtils.addAll(data.get(ErrorGenerator.contentKey), e.getMessage().getBytes());
        byte[] mac = data.get(ErrorGenerator.macKey);
        return MacVerifier.verifyMac(key, content, mac);
    }

    public static boolean verifyMac(PublicKey pubKey, Contract.MacReply reply, Contract.Announcement request) {
        byte[] content = request.getSignature().toByteArray();
        byte[] mac = reply.getMac().toByteArray();
        return verifyMac(pubKey, content, mac);
    }

    public static boolean verifySignature(Contract.Announcement announcement, PublicKey authorKey, String boardIdentifier) {
        try {
            var references = Stream.ofNullable(announcement.getReferencesList())
                    .flatMap(List::stream)
                    .collect(Collectors.toSet());
            byte[] messageBytes = Announcement.generateMessageBytes(announcement.getMessage(), references, boardIdentifier, announcement.getSeq());

            Signature sign = Signature.getInstance("SHA256withRSA");
            sign.initVerify(authorKey);
            sign.update(messageBytes);

            if (!sign.verify(announcement.getSignature().toByteArray())) {
                return false;
            }
        } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException e) {
            return false;
        }
        return true;
    }

    public static boolean verifySignature(Contract.Announcement announcement) {
        try {
            PublicKey authorKey = keyFromBytes(announcement.getPublicKey().toByteArray());
            return verifySignature(announcement, authorKey, GeneralBoard.GENERAL_BOARD_IDENTIFIER);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }
}
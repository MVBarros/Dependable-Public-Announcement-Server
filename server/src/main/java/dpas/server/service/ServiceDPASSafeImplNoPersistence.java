package dpas.server.service;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import dpas.common.domain.Announcement;
import dpas.common.domain.AnnouncementBoard;
import dpas.common.domain.User;
import dpas.common.domain.exception.CommonDomainException;
import dpas.common.domain.exception.InvalidUserException;
import dpas.grpc.contract.Contract;
import dpas.server.session.SessionException;
import dpas.server.session.SessionManager;
import dpas.utils.bytes.ContractUtils;
import dpas.utils.bytes.CypherUtils;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

import static io.grpc.Status.*;

//For testing purposes only
public class ServiceDPASSafeImplNoPersistence extends ServiceDPASImpl {
    private PublicKey _publicKey;
    private PrivateKey _privateKey;
    private SessionManager _sessionManager;

    public ServiceDPASSafeImplNoPersistence(PublicKey pubKey, PrivateKey privKey, SessionManager sessionManager) {
        _publicKey = pubKey;
        _privateKey = privKey;
        _sessionManager = sessionManager;
    }

    @Override
    public void register(Contract.RegisterRequest request, StreamObserver<Empty> responseObserver) {
        responseObserver.onError(UNAVAILABLE.withDescription("Endpoint Not Active").asRuntimeException());
    }


    @Override
    public void post(Contract.PostRequest request, StreamObserver<Empty> responseObserver) {
        responseObserver.onError(UNAVAILABLE.withDescription("Endpoint Not Active").asRuntimeException());
    }

    @Override
    public void postGeneral(Contract.PostRequest request, StreamObserver<Empty> responseObserver) {
        responseObserver.onError(UNAVAILABLE.withDescription("Endpoint Not Active").asRuntimeException());
    }

    @Override
    public void newSession(Contract.ClientHello request, StreamObserver<Contract.ServerHello> responseObserver) {
        try {
            String sessionNonce = request.getSessionNonce();
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(request.getPublicKey().toByteArray()));
            String contentClient = sessionNonce + Base64.getEncoder().encodeToString(publicKey.getEncoded());

            byte[] clientMac = ContractUtils.obtainMac(request);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHashClient = digest.digest(contentClient.getBytes());

            if (!Arrays.equals(encodedHashClient, clientMac))
                throw new IllegalArgumentException("Invalid Client Hmac");

            _sessionManager.createSession(publicKey, sessionNonce);

            //Generate server's mac with its private key
            long seqNumber = new SecureRandom().nextLong();
            String replyContent = sessionNonce + seqNumber;
            byte[] serverMac = ContractUtils.generateMac(replyContent, _privateKey);

            responseObserver.onNext(Contract.ServerHello.newBuilder().setSessionNonce(sessionNonce).setSeq(seqNumber).setMac(ByteString.copyFrom(serverMac)).build());
            responseObserver.onCompleted();

        } catch (GeneralSecurityException e) {
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Invalid Key").asRuntimeException());
        } catch (SessionException e) {
            responseObserver.onError(INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (IOException e) {
            responseObserver.onError(ABORTED.withDescription("Error while generating Server Mac").asRuntimeException());
        }
    }


    @Override
    public void safePost(Contract.SafePostRequest request, StreamObserver<Contract.SafePostReply> responseObserver) {
        try {
            long seq = validatePostRequest(request);
            String sessionNonce = request.getSessionNonce();
            var announcement = generateAnnouncement(request);

            var curr = _announcements.putIfAbsent(announcement.getHash(), announcement);
            if (curr != null) {
                //Announcement with that identifier already	 exists
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Post Identifier Already Exists").asRuntimeException());
            } else {
                announcement.getUser().getUserBoard().post(announcement);
                responseObserver.onNext(generatePostReply(sessionNonce, seq));
                responseObserver.onCompleted();
            }
        } catch (GeneralSecurityException e) {
            responseObserver.onError(CANCELLED.withDescription("Invalid values provided, could not decipher").asRuntimeException());
        } catch (IOException e) {
            responseObserver.onError(CANCELLED.withDescription("An Error ocurred in the server").asRuntimeException());
        } catch (CommonDomainException e) {
            responseObserver.onError(INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (SessionException e) {
            responseObserver.onError(UNAUTHENTICATED.withDescription("Could not validate request").asRuntimeException());
        }
    }

    @Override
    public void safePostGeneral(Contract.SafePostRequest request, StreamObserver<Contract.SafePostReply> responseObserver) {
        try {
            long seq = validatePostRequest(request);
            String sessionNonce = request.getSessionNonce();
            Announcement announcement = generateAnnouncement(request, _generalBoard);

            var curr = _announcements.putIfAbsent(announcement.getHash(), announcement);
            if (curr != null) {
                //Announcement with that identifier already exists
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Post Identifier Already Exists").asRuntimeException());
            } else {
                _generalBoard.post(announcement);
                responseObserver.onNext(generatePostReply(sessionNonce, seq));
                responseObserver.onCompleted();
            }
        } catch (GeneralSecurityException e) {
            responseObserver.onError(CANCELLED.withDescription("Invalid values provided, could not decipher").asRuntimeException());
        } catch (IOException e) {
            responseObserver.onError(CANCELLED.withDescription("An Error ocurred in the server").asRuntimeException());
        } catch (CommonDomainException e) {
            responseObserver.onError(INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (SessionException e) {
            responseObserver.onError(UNAUTHENTICATED.withDescription("Could not validate request").asRuntimeException());
        }
    }

    @Override
    public void safeRegister(Contract.SafeRegisterRequest request, StreamObserver<Contract.SafeRegisterReply> responseObserver) {
        try {
            PublicKey pubKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(request.getPublicKey().toByteArray()));
            String nonce = request.getSessionNonce();
            long nextSeq = _sessionManager.validateSessionRequest(
                    nonce,
                    request.getMac().toByteArray(),
                    ContractUtils.toByteArray(request),
                    request.getSeq());

            var user = new User(pubKey);
            var curr = _users.putIfAbsent(user.getPublicKey(), user);
            if (curr != null) {
                //User with public key already exists
                responseObserver.onError(INVALID_ARGUMENT.withDescription("User Already Exists").asRuntimeException());
            } else {
                byte[] replyMac = ContractUtils.generateMac(nonce, nextSeq, _privateKey);

                responseObserver.onNext(Contract.SafeRegisterReply.newBuilder()
                        .setMac(ByteString.copyFrom(replyMac))
                        .setSeq(nextSeq)
                        .setSessionNonce(nonce)
                        .build());
                responseObserver.onCompleted();
            }
        } catch (CommonDomainException e) {
            responseObserver.onError(INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (SessionException e) {
            responseObserver.onError(UNAUTHENTICATED.withDescription("Could not validate request").asRuntimeException());
        } catch (IOException | GeneralSecurityException e) {
            responseObserver.onError(CANCELLED.withDescription("An Error ocurred in the server").asRuntimeException());
        }
    }

    @Override
    public void goodbye(Contract.GoodByeRequest request, StreamObserver<Empty> responseObserver) {
        try {
            String nonce = request.getSessionNonce();
            _sessionManager.validateSessionRequest(
                    nonce,
                    request.getMac().toByteArray(),
                    ContractUtils.toByteArray(request),
                    request.getSeq());
            _sessionManager.removeSession(nonce);
            responseObserver.onNext(Empty.newBuilder().build());
            responseObserver.onCompleted();
        } catch (SessionException e) {
            responseObserver.onError(UNAUTHENTICATED.withDescription("Could not validate request").asRuntimeException());
        } catch (IOException | GeneralSecurityException e) {
            responseObserver.onError(CANCELLED.withDescription("An Error ocurred in the server").asRuntimeException());
        }
    }

    protected Announcement generateAnnouncement(Contract.SafePostRequest request, AnnouncementBoard board) throws GeneralSecurityException, CommonDomainException {
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(request.getPublicKey().toByteArray()));
        byte[] signature = request.getSignature().toByteArray();
        String message = new String(CypherUtils.decipher(request.getMessage().toByteArray(), _privateKey), StandardCharsets.UTF_8);

        return new Announcement(signature, _users.get(key), message, getListOfReferences(request.getReferencesList()), _counter.getAndIncrement(), board);
    }

    protected Announcement generateAnnouncement(Contract.SafePostRequest request) throws GeneralSecurityException, CommonDomainException {
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(request.getPublicKey().toByteArray()));
        byte[] signature = request.getSignature().toByteArray();
        String message = new String(CypherUtils.decipher(request.getMessage().toByteArray(), _privateKey), StandardCharsets.UTF_8);

        User user = _users.get(key);
        if (user == null) {
            throw new InvalidUserException("User does not exist");
        }
        return new Announcement(signature, user, message, getListOfReferences(request.getReferencesList()), _counter.getAndIncrement(), user.getUserBoard());
    }

    private long validatePostRequest(Contract.SafePostRequest request) throws IOException, GeneralSecurityException, SessionException {
        byte[] content = ContractUtils.toByteArray(request);
        byte[] mac = request.getMac().toByteArray();
        String sessionNonce = request.getSessionNonce();
        long seq = request.getSeq();
        return _sessionManager.validateSessionRequest(sessionNonce, mac, content, seq);
    }

    private Contract.SafePostReply generatePostReply(String sessionNonce, long seq) throws GeneralSecurityException, IOException {
        byte[] mac = ContractUtils.generateMac(sessionNonce, seq, _privateKey);
        return Contract.SafePostReply.newBuilder()
                .setSessionNonce(sessionNonce)
                .setSeq(seq)
                .setMac(ByteString.copyFrom(mac))
                .build();
    }

    public SessionManager getSessionManager() {
        return _sessionManager;
    }


}
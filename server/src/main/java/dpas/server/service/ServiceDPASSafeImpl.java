package dpas.server.service;

import com.google.protobuf.Empty;
import dpas.grpc.contract.Contract;
import dpas.server.persistence.PersistenceManager;
import dpas.server.session.SessionManager;
import io.grpc.stub.StreamObserver;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Random;
import java.util.UUID;

import static io.grpc.Status.UNAVAILABLE;

public class ServiceDPASSafeImpl extends ServiceDPASImpl {
    private PublicKey _publicKey;
    private PrivateKey _privateKey;
    private PersistenceManager _persistenceManager;
    private SessionManager _sessionManager;

    public ServiceDPASSafeImpl(PersistenceManager manager, PublicKey pubKey, PrivateKey privKey, SessionManager sessionManager) {
        _persistenceManager = manager;
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
    public void newSession(Contract.ClientHello request, StreamObserver<Contract.ServerHello> responseObserver) throws NoSuchAlgorithmException,
            InvalidKeySpecException {
        /*
        String sessionNonce = request.getSessionNonce();
        PublicKey pubKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(request.getPublicKey().toByteArray()));
        byte[] mac = request.getMac().toByteArray();

        Random rand = new Random();
        int seqNumber = rand.nextInt();

        Contract.ServerHello reply = Contract.ServerHello.newBuilder().setSessionNonce(sessionNonce).setMac().setSeq(seqNumber).build()
         */

    }


    @Override
    public void safePost(Contract.SafePostRequest request, StreamObserver<Contract.SafePostReply> responseObserver) {
        //TODO
    }

    @Override
    public void safePostGeneral(Contract.SafePostRequest request, StreamObserver<Contract.SafePostReply> responseObserver) {
        //TODO
    }

    @Override
    public void safeRegister(Contract.SafeRegisterRequest request, StreamObserver<Contract.SafeRegisterReply> responseObserver) {
        //TODO
    }

}
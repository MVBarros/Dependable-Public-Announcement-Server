package dpas.server.service;

import com.google.protobuf.ByteString;
import dpas.grpc.contract.Contract;
import dpas.grpc.contract.ServiceDPASGrpc;
import dpas.server.session.Session;
import dpas.server.session.SessionManager;
import dpas.utils.ContractGenerator;
import dpas.utils.MacVerifier;
import dpas.utils.MacGenerator;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.*;
import java.time.LocalDateTime;

import static org.junit.Assert.*;

public class SafeServiceRegisterTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private PublicKey _pubKey;
    private PrivateKey _privKey;
    private static final String SESSION_NONCE = "NONCE";

    private static final int port = 9001;
    private static final String host = "localhost";

    private static ServiceDPASSafeImpl _impl;

    private ServiceDPASGrpc.ServiceDPASBlockingStub _stub;
    private Server _server;
    private ManagedChannel _channel;
    private PrivateKey _serverPrivKey;
    private PublicKey _serverPubKey;

    @Before
    public void setup() throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IOException {

        KeyPairGenerator keygen = KeyPairGenerator.getInstance("RSA");
        keygen.initialize(2048);
        KeyPair keyPair = keygen.generateKeyPair();
        KeyPair serverPair = keygen.generateKeyPair();

        PublicKey _serverPKey = serverPair.getPublic();
        _serverPrivKey = serverPair.getPrivate();
        _serverPubKey = serverPair.getPublic();
        SessionManager _sessionManager = new SessionManager(5000);

        _pubKey = keyPair.getPublic();
        _privKey = keyPair.getPrivate();
        _sessionManager.getSessions().put(SESSION_NONCE, new Session(0, _pubKey, SESSION_NONCE, LocalDateTime.now().plusHours(1)));

        Cipher cipherServer = Cipher.getInstance("RSA");
        cipherServer.init(Cipher.ENCRYPT_MODE, _privKey);

        _impl = new ServiceDPASSafeImpl(_serverPKey, _serverPrivKey, _sessionManager);
        _server = NettyServerBuilder.forPort(port).addService(_impl).build();
        _server.start();

        //Connect to Server
        _channel = NettyChannelBuilder.forAddress(host, port).usePlaintext().build();
        _stub = ServiceDPASGrpc.newBlockingStub(_channel);

    }

    @After
    public void tearDown() {
        _server.shutdown();
        _channel.shutdown();
    }

    @Test
    public void validRegister() throws IOException, GeneralSecurityException {
        var reply = _stub.safeRegister(ContractGenerator.generateRegisterRequest(SESSION_NONCE, 1, _pubKey, _privKey));

        assertEquals(_impl.getSessionManager().getSessions().get(SESSION_NONCE).getSessionNonce(), SESSION_NONCE);
        assertEquals(_impl.getSessionManager().getSessions().get(SESSION_NONCE).getSequenceNumber(), 2);
        assertTrue(MacVerifier.verifyMac(_serverPubKey, reply));
    }


    @Test
    public void invalidSessionNonceRegister() throws GeneralSecurityException, IOException {
        exception.expect(StatusRuntimeException.class);
        exception.expectMessage("Invalid Session");

        _stub.safeRegister(ContractGenerator.generateRegisterRequest("invalid", 1, _pubKey, _privKey));
    }

    @Test
    public void invalidSeqRegister() throws GeneralSecurityException, IOException {
        exception.expect(StatusRuntimeException.class);
        exception.expectMessage("Invalid sequence number");

        _stub.safeRegister(ContractGenerator.generateRegisterRequest(SESSION_NONCE, 7, _pubKey, _privKey));
    }

    @Test
    public void invalidMacRegister() throws GeneralSecurityException, IOException {
        exception.expect(StatusRuntimeException.class);
        exception.expectMessage("Invalid mac");

        byte[] requestMAC = MacGenerator.generateMac("ola", 1,_pubKey, _privKey );
        _stub.safeRegister(Contract.SafeRegisterRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(_pubKey.getEncoded()))
                .setSessionNonce(SESSION_NONCE)
                .setSeq(1)
                .setMac(ByteString.copyFrom(requestMAC))
                .build());
    }

    @Test
    public void noMacRegister() {
        exception.expect(StatusRuntimeException.class);
        exception.expectMessage("Invalid security values provided");

        _stub.safeRegister(Contract.SafeRegisterRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(_pubKey.getEncoded()))
                .setSessionNonce(SESSION_NONCE)
                .setSeq(1)
                .build());
    }

}
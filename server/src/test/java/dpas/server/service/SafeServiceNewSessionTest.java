package dpas.server.service;

import com.google.protobuf.ByteString;
import dpas.grpc.contract.Contract;
import dpas.grpc.contract.ServiceDPASGrpc;
import dpas.server.session.SessionManager;
import dpas.utils.ContractGenerator;
import dpas.utils.MacVerifier;
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

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.*;
import java.util.Base64;

import static org.junit.Assert.*;

public class SafeServiceNewSessionTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private PublicKey _pubKey;
    private PrivateKey _privKey;
    private static final String SESSION_NONCE = "NONCE";
    private static final String SESSION_NONCE2 = "NONCE2";
    private static final String SESSION_NONCE3 = "NONCE3";
    private byte[] _clientMac;

    private static final int port = 9001;
    private static final String host = "localhost";

    private static ServiceDPASSafeImpl _impl;

    private ServiceDPASGrpc.ServiceDPASBlockingStub _stub;
    private Server _server;
    private ManagedChannel _channel;
    private PublicKey _serverPKey;
    private PrivateKey _serverPrivKey;
    private SessionManager _sessionManager;

    @Before
    public void setup() throws NoSuchAlgorithmException, NoSuchPaddingException, BadPaddingException,
            IllegalBlockSizeException, InvalidKeyException, IOException {

        KeyPairGenerator keygen = KeyPairGenerator.getInstance("RSA");
        keygen.initialize(2048);
        KeyPair keyPair = keygen.generateKeyPair();
        KeyPair serverPair = keygen.generateKeyPair();

        _serverPKey = serverPair.getPublic();
        _serverPrivKey = serverPair.getPrivate();
        _sessionManager = new SessionManager(5000);

        _pubKey = keyPair.getPublic();
        _privKey = keyPair.getPrivate();

        Cipher cipherServer = Cipher.getInstance("RSA");
        cipherServer.init(Cipher.ENCRYPT_MODE, _privKey);

        String content = SESSION_NONCE2 + Base64.getEncoder().encodeToString(_pubKey.getEncoded());

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encodedHashClient = digest.digest(content.getBytes());
        _clientMac = cipherServer.doFinal(encodedHashClient);

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
    public void validNewSession() throws GeneralSecurityException, IOException {
        var reply = _stub.newSession(ContractGenerator.generateClientHello(_privKey, _pubKey, SESSION_NONCE));
        assertTrue(MacVerifier.verifyMac(_serverPKey, reply));
        assertEquals(_impl.getSessionManager().getSessions().size(), 1);
        assertArrayEquals(_impl.getSessionManager().getSessions().get(SESSION_NONCE).getPublicKey().getEncoded(), _pubKey.getEncoded());
    }

    @Test
    public void newSessionWrongClientMac() {
        exception.expect(StatusRuntimeException.class);
        exception.expectMessage("Invalid security values provided");

        byte[] invalidMac = "ThisIsInvalid".getBytes();
        try {
            _stub.newSession(Contract.ClientHello.newBuilder()
                    .setMac(ByteString.copyFrom(invalidMac))
                    .setPublicKey(ByteString.copyFrom(_pubKey.getEncoded()))
                    .setSessionNonce(SESSION_NONCE3)
                    .build());
        } finally {
            assertEquals(_impl.getSessionManager().getSessions().size(), 0);
        }
    }

    @Test
    public void repeatedSessions() throws IOException, GeneralSecurityException {
        var reply = _stub.newSession(ContractGenerator.generateClientHello(_privKey, _pubKey, SESSION_NONCE));
        assertTrue(MacVerifier.verifyMac(_serverPKey, reply));

        assertEquals(_impl.getSessionManager().getSessions().get(SESSION_NONCE).getSessionNonce(), SESSION_NONCE);
        assertArrayEquals(_impl.getSessionManager().getSessions().get(SESSION_NONCE).getPublicKey().getEncoded(), _pubKey.getEncoded());
        assertEquals(_impl.getSessionManager().getSessions().size(), 1);

        exception.expect(StatusRuntimeException.class);
        exception.expectMessage("Session already exists!");
        try {
            _stub.newSession(ContractGenerator.generateClientHello(_privKey, _pubKey, SESSION_NONCE));
        } finally {
            assertEquals(_impl.getSessionManager().getSessions().size(), 1);
        }
    }


    @Test
    public void twoSessionsSameUser() throws IOException, GeneralSecurityException {
        var reply = _stub.newSession(ContractGenerator.generateClientHello(_privKey, _pubKey, SESSION_NONCE));
        assertTrue(MacVerifier.verifyMac(_serverPKey, reply));

        assertEquals(_impl.getSessionManager().getSessions().get(SESSION_NONCE).getSessionNonce(), SESSION_NONCE);
        assertArrayEquals(_impl.getSessionManager().getSessions().get(SESSION_NONCE).getPublicKey().getEncoded(), _pubKey.getEncoded());
        assertEquals(_impl.getSessionManager().getSessions().size(), 1);


        reply = _stub.newSession(ContractGenerator.generateClientHello(_privKey, _pubKey, SESSION_NONCE2));
        assertTrue(MacVerifier.verifyMac(_serverPKey, reply));

        assertEquals(_impl.getSessionManager().getSessions().get(SESSION_NONCE2).getSessionNonce(), SESSION_NONCE2);
        assertArrayEquals(_impl.getSessionManager().getSessions().get(SESSION_NONCE2).getPublicKey().getEncoded(), _pubKey.getEncoded());
        assertEquals(_impl.getSessionManager().getSessions().size(), 2);
    }
}

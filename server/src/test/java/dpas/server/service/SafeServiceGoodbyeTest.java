package dpas.server.service;

import com.google.protobuf.ByteString;
import dpas.grpc.contract.Contract;
import dpas.grpc.contract.ServiceDPASGrpc;
import dpas.server.session.SessionManager;
import dpas.utils.ContractGenerator;
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

import java.io.IOException;
import java.security.*;

import static org.junit.Assert.*;

public class SafeServiceGoodbyeTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private PublicKey _pubKey;
    private PrivateKey _privKey;
    private static final String SESSION_NONCE = "NONCE";
    private static final String SESSION_NONCE2 = "NONCE2";

    private long _seq;

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
    public void setup() throws GeneralSecurityException,
            IOException {

        KeyPairGenerator keygen = KeyPairGenerator.getInstance("RSA");
        keygen.initialize(2048);
        KeyPair keyPair = keygen.generateKeyPair();
        KeyPair serverPair = keygen.generateKeyPair();

        _serverPKey = serverPair.getPublic();
        _serverPrivKey = serverPair.getPrivate();
        _sessionManager = new SessionManager(15000);

        _pubKey = keyPair.getPublic();
        _privKey = keyPair.getPrivate();

        _impl = new ServiceDPASSafeImpl(_serverPKey, _serverPrivKey, _sessionManager);
        _server = NettyServerBuilder.forPort(port).addService(_impl).build();
        _server.start();

        //Connect to Server
        _channel = NettyChannelBuilder.forAddress(host, port).usePlaintext().build();
        _stub = ServiceDPASGrpc.newBlockingStub(_channel);
        _seq = _stub.newSession(ContractGenerator.generateClientHello(_privKey, _pubKey, SESSION_NONCE)).getSeq();
    }

    @After
    public void tearDown() {
        _server.shutdown();
        _channel.shutdown();
    }

    @Test
    public void validGoodbye() throws GeneralSecurityException, IOException {
        _stub.goodbye(ContractGenerator.generateGoodbyeRequest(_privKey, SESSION_NONCE, _seq + 1));
        assertEquals(_impl.getSessionManager().getSessions().size(), 0);
    }

    @Test
    public void goodbyeInvalidSeq() throws GeneralSecurityException, IOException {
        exception.expect(StatusRuntimeException.class);
        exception.expectMessage("Invalid sequence number");
        _stub.goodbye(ContractGenerator.generateGoodbyeRequest(_privKey, SESSION_NONCE, _seq + 3));
        assertEquals(_impl.getSessionManager().getSessions().size(), 1);
    }

    @Test
    public void goodbyeInvalidNonce() throws GeneralSecurityException, IOException {
        exception.expect(StatusRuntimeException.class);
        exception.expectMessage("Invalid Session");
        _stub.goodbye(ContractGenerator.generateGoodbyeRequest(_privKey, "Invalid", _seq + 1));
        assertEquals(_impl.getSessionManager().getSessions().size(), 1);
    }

    @Test
    public void goodbyeInvalidMacKey() throws GeneralSecurityException, IOException {
        KeyPairGenerator keygen = KeyPairGenerator.getInstance("RSA");
        keygen.initialize(2048);
        KeyPair keyPair = keygen.generateKeyPair();
        exception.expect(StatusRuntimeException.class);
        exception.expectMessage("Invalid security values provided");
        _stub.goodbye(ContractGenerator.generateGoodbyeRequest(keyPair.getPrivate(), SESSION_NONCE, _seq + 1));
        assertEquals(_impl.getSessionManager().getSessions().size(), 1);
    }

    @Test
    public void invalicMac() throws GeneralSecurityException, IOException {
        var req = ContractGenerator.generateGoodbyeRequest(_privKey, SESSION_NONCE, _seq + 1);
        req = Contract.GoodByeRequest.newBuilder(req).setMac(ByteString.copyFrom(new byte[] {23, 21, 23})).build();
        exception.expect(StatusRuntimeException.class);
        exception.expectMessage("Invalid security values provided");
        _stub.goodbye(req);
        assertEquals(_impl.getSessionManager().getSessions().size(), 1);
    }

}
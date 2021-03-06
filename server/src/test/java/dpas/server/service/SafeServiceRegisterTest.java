package dpas.server.service;

import com.google.protobuf.ByteString;
import dpas.grpc.contract.Contract;
import dpas.grpc.contract.ServiceDPASGrpc;
import dpas.utils.ContractGenerator;
import dpas.utils.auth.ErrorGenerator;
import dpas.utils.auth.MacGenerator;
import dpas.utils.auth.MacVerifier;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.junit.*;
import org.junit.rules.ExpectedException;

import javax.crypto.Cipher;
import java.io.IOException;
import java.security.*;

import static org.junit.Assert.*;

public class SafeServiceRegisterTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    private static PublicKey _serverPKey;
    private static PublicKey _pubKey;
    private static PrivateKey _privKey;

    private static final int port = 9001;
    private static final String host = "localhost";

    private static ServiceDPASSafeImpl _impl;

    private ServiceDPASGrpc.ServiceDPASBlockingStub _stub;
    private Server _server;
    private ManagedChannel _channel;
    private static PrivateKey _serverPrivKey;
    private static PublicKey _serverPubKey;

    private static Contract.RegisterRequest _request;

    @BeforeClass
    public static void oneTimeSetup() throws GeneralSecurityException, IOException {
        KeyPairGenerator keygen = KeyPairGenerator.getInstance("RSA");
        keygen.initialize(4096);
        KeyPair keyPair = keygen.generateKeyPair();
        KeyPair serverPair = keygen.generateKeyPair();

        _serverPKey = serverPair.getPublic();
        _serverPrivKey = serverPair.getPrivate();
        _serverPubKey = serverPair.getPublic();

        _pubKey = keyPair.getPublic();
        _privKey = keyPair.getPrivate();

        Cipher cipherServer = Cipher.getInstance("RSA");
        cipherServer.init(Cipher.ENCRYPT_MODE, _privKey);

        _request = ContractGenerator.generateRegisterRequest(_pubKey, _privKey);

    }

    @Before
    public void setup() throws IOException {
        _impl = new ServiceDPASSafeImpl(_serverPrivKey);
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
        var reply = _stub.register(_request);
        assertTrue(MacVerifier.verifyMac(_request, reply, _serverPubKey));
    }

    @Test
    public void duplicatedRegister() throws IOException, GeneralSecurityException {
        var request = ContractGenerator.generateRegisterRequest(_pubKey, _privKey);
        var reply = _stub.register(request);

        assertTrue(MacVerifier.verifyMac(_request, reply, _serverPubKey));

        reply = _stub.register(request);
        assertTrue(MacVerifier.verifyMac(_request, reply, _serverPubKey));
        assertEquals(_impl.getUsers().size(), 1);
    }


    @Test
    public void invalidMacRegister() throws GeneralSecurityException, IOException {
        exception.expect(StatusRuntimeException.class);
        exception.expectMessage("Could not validate request");
        byte[] requestMAC = MacGenerator.generateMac("ola", 1, _pubKey, _privKey);
        var request = Contract.RegisterRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(_pubKey.getEncoded()))
                .setMac(ByteString.copyFrom(requestMAC))
                .build();
        try {
            _stub.register(request);
        } catch (StatusRuntimeException e) {
            Metadata data = e.getTrailers();
            assertArrayEquals(data.get(ErrorGenerator.contentKey), request.getMac().toByteArray());
            assertEquals(e.getStatus().getCode(), Status.INVALID_ARGUMENT.getCode());
            assertTrue(MacVerifier.verifyMac(_serverPKey, e));
            throw e;
        }
    }

    @Test
    public void noMacRegister() throws GeneralSecurityException {
        exception.expect(StatusRuntimeException.class);
        exception.expectMessage("Could not validate request");
        var request = Contract.RegisterRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(_pubKey.getEncoded()))
                .build();
        try {
            _stub.register(request);
        } catch (StatusRuntimeException e) {
            Metadata data = e.getTrailers();
            assertArrayEquals(data.get(ErrorGenerator.contentKey), request.getMac().toByteArray());
            assertEquals(e.getStatus().getCode(), Status.INVALID_ARGUMENT.getCode());
            assertTrue(MacVerifier.verifyMac(_serverPKey, e));
            throw e;
        }
    }

}

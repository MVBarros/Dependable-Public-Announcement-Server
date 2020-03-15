package dpas.server.service;

import com.google.protobuf.ByteString;
import dpas.grpc.contract.Contract;
import dpas.grpc.contract.ServiceDPASGrpc;
import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.*;

import static org.junit.Assert.assertEquals;

public class PostTest {

    private ServiceDPASGrpc.ServiceDPASBlockingStub _stub;

    private Server _server;
    private PublicKey _firstPublicKey;
    private PublicKey _secondPublicKey;
    private byte[] _firstSignature;
    private byte[] _secondSignature;
    private byte[] _bigMessageSignature;

    private Contract.BoardReference _validReference;
    private Contract.BoardReference _invalidReference;
    private Contract.BoardReference _invalidReference2;
    private Contract.BoardReference _invalidReference3;



    private ManagedChannel _channel;

    private final static String FIRST_USER_NAME = "USER";
    private final static String SECOND_USER_NAME = "USER2";

    private static final String MESSAGE = "Message";
    private static final String SECOND_MESSAGE = "Second Message";
    private static final String INVALID_MESSAGE = "ThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalid" +
            "ThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalid";



    @Before
    public void setup() throws IOException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        KeyPairGenerator keygen = KeyPairGenerator.getInstance("RSA");
        keygen.initialize(1024);
        KeyPair keyPair = keygen.generateKeyPair();
        _firstPublicKey = keyPair.getPublic();

        // generate first signature
        Signature sign = Signature.getInstance("SHA256withRSA");
        sign.initSign(keyPair.getPrivate());
        sign.update(MESSAGE.getBytes());
        _firstSignature = sign.sign();

        // second key pair
        keygen = KeyPairGenerator.getInstance("RSA");
        keygen.initialize(1024);
        keyPair = keygen.generateKeyPair();
        _secondPublicKey = keyPair.getPublic();

        // Generate second signature
        sign = Signature.getInstance("SHA256withRSA");
        sign.initSign(keyPair.getPrivate());
        sign.update(SECOND_MESSAGE.getBytes());
        _secondSignature = sign.sign();


        // third key pair
        keygen = KeyPairGenerator.getInstance("RSA");
        keygen.initialize(1024);
        keyPair = keygen.generateKeyPair();
        PublicKey _thirdPublicKey = keyPair.getPublic();

        // Generate signature for too big message
        sign = Signature.getInstance("SHA256withRSA");
        sign.initSign(keyPair.getPrivate());
        sign.update(INVALID_MESSAGE.getBytes());
        _bigMessageSignature = sign.sign();

        //Valid Reference
        _validReference = Contract.BoardReference.newBuilder()
                .setUserBoardReference(Contract.UserBoardReference
                        .newBuilder()
                        .setPublicKey(ByteString.copyFrom(_firstPublicKey.getEncoded()))
                        .setSequenceNumber(0)
                        .build()).build();
        //Invalid Reference sequenceNumber too high
        _invalidReference = Contract.BoardReference.newBuilder()
                .setUserBoardReference(Contract.UserBoardReference
                        .newBuilder()
                        .setPublicKey(ByteString.copyFrom(_firstPublicKey.getEncoded()))
                        .setSequenceNumber(3)
                        .build()).build();

        _invalidReference2 = Contract.BoardReference.newBuilder()
                .setUserBoardReference(Contract.UserBoardReference
                        .newBuilder()
                        .setPublicKey(ByteString.copyFrom(_thirdPublicKey.getEncoded()))
                        .setSequenceNumber(3)
                        .build()).build();


        _invalidReference3 = Contract.BoardReference.newBuilder()
                .setUserBoardReference(Contract.UserBoardReference
                        .newBuilder()
                        .setPublicKey(ByteString.copyFrom(new byte[] {12, 2, 25}))
                        .setSequenceNumber(3)
                        .build()).build();



        final BindableService impl =  new ServiceDPASImpl();

        //Start server
        _server = NettyServerBuilder
                .forPort(8090)
                .addService(impl)
                .build();
        _server.start();

        final String host = "localhost";
        final int port = 8090;
        _channel = NettyChannelBuilder.forAddress(host, port).usePlaintext().build();
        _stub = ServiceDPASGrpc.newBlockingStub(_channel);

        // create first user
        _stub.register(Contract.RegisterRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(_firstPublicKey.getEncoded()))
                .setUsername(FIRST_USER_NAME)
                .build());

        // create second user
        _stub.register(Contract.RegisterRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(_secondPublicKey.getEncoded()))
                .setUsername(SECOND_USER_NAME)
                .build());
    }

    @After
    public void teardown() {
        _server.shutdown();
        _channel.shutdown();
    }

    @Test
    public void postSuccess() {
        Contract.PostReply reply = _stub.post(Contract.PostRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(_firstPublicKey.getEncoded()))
                .setUsername(FIRST_USER_NAME)
                .setMessage(MESSAGE)
                .setSignature(ByteString.copyFrom(_firstSignature))
                .build());
        assertEquals(reply.getStatus(), Contract.PostStatus.POSTSTATUS_OK);
    }

    @Test
    public void twoPostsSuccess() {
        Contract.PostReply reply = _stub.post(Contract.PostRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(_firstPublicKey.getEncoded()))
                .setUsername(FIRST_USER_NAME)
                .setMessage(MESSAGE)
                .setSignature(ByteString.copyFrom(_firstSignature))
                .build());
        assertEquals(reply.getStatus(), Contract.PostStatus.POSTSTATUS_OK);

        reply = _stub.post(Contract.PostRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(_secondPublicKey.getEncoded()))
                .setUsername(SECOND_USER_NAME)
                .setMessage(SECOND_MESSAGE)
                .setSignature(ByteString.copyFrom(_secondSignature))
                .build());
        assertEquals(reply.getStatus(), Contract.PostStatus.POSTSTATUS_OK);
    }

    @Test
    public void twoPostsValidReference() {
        Contract.PostReply reply = _stub.post(Contract.PostRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(_firstPublicKey.getEncoded()))
                .setUsername(FIRST_USER_NAME)
                .setMessage(MESSAGE)
                .setSignature(ByteString.copyFrom(_firstSignature))
                .build());
        assertEquals(reply.getStatus(), Contract.PostStatus.POSTSTATUS_OK);

        reply = _stub.post(Contract.PostRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(_secondPublicKey.getEncoded()))
                .setUsername(SECOND_USER_NAME)
                .setMessage(SECOND_MESSAGE)
                .addReferences( _validReference)
                .setSignature(ByteString.copyFrom(_secondSignature))
                .build());
        assertEquals(reply.getStatus(), Contract.PostStatus.POSTSTATUS_OK);
    }

    @Test
    public void twoPostsInvalidReference() {
        Contract.PostReply reply = _stub.post(Contract.PostRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(_firstPublicKey.getEncoded()))
                .setUsername(FIRST_USER_NAME)
                .setMessage(MESSAGE)
                .setSignature(ByteString.copyFrom(_firstSignature))
                .build());
        assertEquals(reply.getStatus(), Contract.PostStatus.POSTSTATUS_OK);

        reply = _stub.post(Contract.PostRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(_secondPublicKey.getEncoded()))
                .setUsername(SECOND_USER_NAME)
                .setMessage(SECOND_MESSAGE)
                .addReferences( _invalidReference)
                .setSignature(ByteString.copyFrom(_secondSignature))
                .build());
        assertEquals(reply.getStatus(), Contract.PostStatus.POSTSATATUS_INVALID_REFERENCE);
    }

    @Test
    public void twoPostsInvalidUserReference() {
        Contract.PostReply reply = _stub.post(Contract.PostRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(_firstPublicKey.getEncoded()))
                .setUsername(FIRST_USER_NAME)
                .setMessage(MESSAGE)
                .setSignature(ByteString.copyFrom(_firstSignature))
                .build());
        assertEquals(reply.getStatus(), Contract.PostStatus.POSTSTATUS_OK);

        reply = _stub.post(Contract.PostRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(_secondPublicKey.getEncoded()))
                .setUsername(SECOND_USER_NAME)
                .setMessage(SECOND_MESSAGE)
                .addReferences( _invalidReference2)
                .setSignature(ByteString.copyFrom(_secondSignature))
                .build());
        assertEquals(reply.getStatus(), Contract.PostStatus.POSTSATATUS_INVALID_REFERENCE);
    }

    @Test
    public void twoPostsInvalidReferenceKey() {
        Contract.PostReply reply = _stub.post(Contract.PostRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(_firstPublicKey.getEncoded()))
                .setUsername(FIRST_USER_NAME)
                .setMessage(MESSAGE)
                .setSignature(ByteString.copyFrom(_firstSignature))
                .build());
        assertEquals(reply.getStatus(), Contract.PostStatus.POSTSTATUS_OK);

        reply = _stub.post(Contract.PostRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(_secondPublicKey.getEncoded()))
                .setUsername(SECOND_USER_NAME)
                .setMessage(SECOND_MESSAGE)
                .addReferences( _invalidReference3)
                .setSignature(ByteString.copyFrom(_secondSignature))
                .build());
        assertEquals(reply.getStatus(), Contract.PostStatus.POSTSATATUS_INVALID_REFERENCE);
    }

    @Test
    public void postNullPublicKey() {
        Contract.PostReply reply = _stub.post(Contract.PostRequest.newBuilder()
                .setUsername(FIRST_USER_NAME)
                .setMessage(MESSAGE)
                .setSignature(ByteString.copyFrom(_firstSignature))
                .build());
        assertEquals(reply.getStatus(), Contract.PostStatus.POSTSATATUS_NULL_PUBLIC_KEY);
    }

    @Test
    public void postInvalidMessageSize() {
        Contract.PostReply reply = _stub.post(Contract.PostRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(_firstPublicKey.getEncoded()))
                .setUsername(FIRST_USER_NAME)
                .setMessage(INVALID_MESSAGE)
                .setSignature(ByteString.copyFrom(_bigMessageSignature))
                .build());
        assertEquals(reply.getStatus(), Contract.PostStatus.POSTSTATUS_INVALID_MESSAGE_SIZE);
    }


    @Test
    public void postNullSignature() {
        Contract.PostReply reply = _stub.post(Contract.PostRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(_firstPublicKey.getEncoded()))
                .setUsername(FIRST_USER_NAME)
                .setMessage(MESSAGE)
                .build());
        assertEquals(reply.getStatus(), Contract.PostStatus.POSTSTATUS_INVALID_SIGNATURE);
    }
    @Test
    public void postInvalidSignature() {
        Contract.PostReply reply = _stub.post(Contract.PostRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(_firstPublicKey.getEncoded()))
                .setUsername(FIRST_USER_NAME)
                .setMessage(MESSAGE)
                .setSignature(ByteString.copyFrom(_secondSignature))
                .build());
        assertEquals(reply.getStatus(), Contract.PostStatus.POSTSTATUS_INVALID_SIGNATURE);
    }



}
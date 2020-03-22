package dpas.common.domain;

import dpas.common.domain.exception.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.security.*;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AnnouncementTest {

    private static final String MESSAGE = "Hello World";
    private static final String OTHER_MESSAGE = "This is another announcement";
    private static final byte[] MESSAGE_BYTES = MESSAGE.getBytes();
    private static final String INVALID_MESSAGE = "ThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalid" +
            "ThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalidThisMessageIsInvalid";

    private ArrayList<Announcement> _references = new ArrayList<>();
    private byte[] _signature;
    private User _user;

    @Before
    public void setup() throws NoSuchAlgorithmException, InvalidKeyException, SignatureException, NullPublicKeyException, NullUsernameException, NullMessageException,
            NullSignatureException, NullUserException, NullAnnouncementException, InvalidSignatureException, UnsupportedEncodingException, InvalidMessageSizeException {

        //Generate public key
        KeyPairGenerator keygen = KeyPairGenerator.getInstance("RSA");
        keygen.initialize(2048);
        KeyPair keyPair = keygen.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        //Generate valid signature
        Signature sign = Signature.getInstance("SHA256withRSA");
        sign.initSign(privateKey);
        sign.update(MESSAGE_BYTES);
        this._signature = sign.sign();

        //Generate user
        this._user = new User(publicKey);

        //Create another announcement
        KeyPairGenerator otherKeyGen = KeyPairGenerator.getInstance("RSA");
        keygen.initialize(2048);
        KeyPair otherKeyPair = otherKeyGen.generateKeyPair();
        PublicKey otherPublicKey = otherKeyPair.getPublic();
        PrivateKey otherPrivateKey = otherKeyPair.getPrivate();

        sign.initSign(otherPrivateKey);
        sign.update(OTHER_MESSAGE.getBytes());
        byte[] otherSignature = sign.sign();

        User otherUser = new User(otherPublicKey);
        Announcement ref = new Announcement(otherSignature, otherUser, OTHER_MESSAGE, null);

        //Add it to references
        _references.add(ref);
    }

    @After
    public void tearDown() {

    }

    @Test
    public void validAnnouncement() throws InvalidKeyException, NullMessageException, NoSuchAlgorithmException,
            InvalidSignatureException, NullSignatureException, NullUserException, SignatureException, NullAnnouncementException,
            UnsupportedEncodingException, InvalidMessageSizeException {

        Announcement announcement = new Announcement(_signature, _user, MESSAGE, _references);
        assertEquals(announcement.getSignature(), _signature);
        assertEquals(announcement.getUser(), _user);
        assertEquals(announcement.getMessage(), MESSAGE);
        assertEquals(announcement.getReferences(), _references);
    }

    @Test
    public void validAnnouncementNullReference() throws InvalidKeyException, NullMessageException, NoSuchAlgorithmException,
            InvalidSignatureException, NullSignatureException, NullUserException, SignatureException, NullAnnouncementException,
            UnsupportedEncodingException, InvalidMessageSizeException {

        Announcement announcement = new Announcement(_signature, _user, MESSAGE, null);
        assertEquals(announcement.getSignature(), _signature);
        assertEquals(announcement.getUser(), _user);
        assertEquals(announcement.getMessage(), MESSAGE);
        assertNull(announcement.getReferences());
    }

    @Test(expected = NullSignatureException.class)
    public void nullSignature() throws InvalidKeyException, NullMessageException, NoSuchAlgorithmException,
            InvalidSignatureException, NullSignatureException, NullUserException, SignatureException,
            NullAnnouncementException, UnsupportedEncodingException, InvalidMessageSizeException {

        new Announcement(null, _user, MESSAGE, _references);
    }


    @Test(expected = NullUserException.class)
    public void nullUser() throws InvalidKeyException, NullMessageException, NoSuchAlgorithmException,
            InvalidSignatureException, NullSignatureException, NullUserException, SignatureException, NullAnnouncementException,
            UnsupportedEncodingException, InvalidMessageSizeException {

        new Announcement(_signature, null, MESSAGE, _references);
    }

    @Test(expected = NullMessageException.class)
    public void nullMessage() throws InvalidKeyException, NullMessageException, NoSuchAlgorithmException,
            InvalidSignatureException, NullSignatureException, NullUserException, SignatureException, NullAnnouncementException,
            UnsupportedEncodingException, InvalidMessageSizeException {

    	new Announcement(_signature, _user, null, _references);
    }

    @Test(expected = NullAnnouncementException.class)
    public void nullReferences() throws InvalidKeyException, NullMessageException, NoSuchAlgorithmException,
            InvalidSignatureException, NullSignatureException, NullUserException, SignatureException, NullAnnouncementException,
            UnsupportedEncodingException, InvalidMessageSizeException {

        ArrayList<Announcement> refNullElement = new ArrayList<>();
        refNullElement.add(null);

        new Announcement(_signature, _user, MESSAGE, refNullElement);
    }

    @Test(expected = InvalidSignatureException.class)
    public void invalidSignature() throws InvalidKeyException, NullMessageException, NoSuchAlgorithmException,
            InvalidSignatureException, NullSignatureException, NullUserException, SignatureException, NullAnnouncementException,
            UnsupportedEncodingException, InvalidMessageSizeException {

        byte[] invalidSig = "InvalidSignature".getBytes();
        new Announcement(invalidSig, _user, MESSAGE, _references);
    }

    @Test(expected = InvalidMessageSizeException.class)
    public void invalidMessage() throws InvalidKeyException, NoSuchAlgorithmException, UnsupportedEncodingException,
            NullMessageException, SignatureException, InvalidSignatureException, NullSignatureException,
            NullUserException, NullAnnouncementException, InvalidMessageSizeException {

        new Announcement(_signature, _user, INVALID_MESSAGE, _references);
    }


}

package dpas.common.domain;

import dpas.common.domain.exception.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.security.*;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

public class GeneralBoardTest {

    private Announcement _announcement;
    private User _userA;
    private GeneralBoard _generalBoard;

    @Before
    public void setup() throws NullPublicKeyException, NullUsernameException, NoSuchAlgorithmException, InvalidKeyException, SignatureException, NullMessageException, UnsupportedEncodingException, NullSignatureException, NullUserException, InvalidMessageSizeException, NullAnnouncementException, InvalidSignatureException {
        // generate user
        KeyPairGenerator keygen = KeyPairGenerator.getInstance("RSA");
        keygen.initialize(1024);
        KeyPair keyPair = keygen.generateKeyPair();
        PublicKey _publicKey = keyPair.getPublic();
        _userA = new User("FIRST_USER_NAME", _publicKey);

        //Generate valid signature
        Signature sign = Signature.getInstance("SHA256withRSA");
        sign.initSign(keyPair.getPrivate());
        sign.update("MESSAGE".getBytes());
        byte[] signature = sign.sign();

        // Generate Announcement
        _announcement = new Announcement(signature, _userA, "MESSAGE", null);
        // Generate Board
        _generalBoard = new GeneralBoard();
    }

    @After
    public void tearDown() {
    }

    @Test
    public void validPost() throws NullUserException, NullAnnouncementException, InvalidNumberOfPostsException {
        _generalBoard.post(_userA, _announcement);
        assertEquals(_generalBoard.read(1).get(0), _announcement);
    }

    @Test(expected = NullUserException.class)
    public void nullUserPost() throws NullUserException, NullAnnouncementException, InvalidNumberOfPostsException {
        _generalBoard.post(null, _announcement);
    }

    @Test(expected = NullAnnouncementException.class)
    public void nullAnnouncementPost() throws NullUserException, NullAnnouncementException, InvalidNumberOfPostsException {
        _generalBoard.post(_userA, null);
    }

    @Test
    public void validRead() throws NullUserException, NullAnnouncementException, InvalidNumberOfPostsException {
        _generalBoard.post(_userA, _announcement);
        _generalBoard.post(_userA, _announcement);
        ArrayList<Announcement> expectedAnnouncements = new ArrayList<Announcement>();
        expectedAnnouncements.add(_announcement);
        expectedAnnouncements.add(_announcement);
        assertEquals(_generalBoard.read(2), expectedAnnouncements);
    }

    @Test(expected = InvalidNumberOfPostsException.class)
    public void invalidNumberOfPostsRead() throws NullUserException, NullAnnouncementException, InvalidNumberOfPostsException {
        _generalBoard.post(_userA, _announcement);
        _generalBoard.read(-1);
    }

}
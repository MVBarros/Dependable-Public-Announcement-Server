package dpas.common.domain;

import dpas.common.domain.exception.*;
import dpas.grpc.contract.Contract;

import java.io.Serializable;
import java.security.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Announcement implements Serializable {
    private byte[] _signature;
    private User _user;
    private String _message;
    private ArrayList<Announcement> _references; // Can be null

    private String _identifier;

    public Announcement(byte[] signature, User user, String message, ArrayList<Announcement> references) throws NullSignatureException, NullMessageException,
            NullAnnouncementException, InvalidSignatureException, NoSuchAlgorithmException, InvalidKeyException, SignatureException,
            NullUserException, InvalidMessageSizeException {

        checkArguments(signature, user, message, references);
        checkSignature(signature, user, message);
        this._message = message;
        this._signature = signature;
        this._user = user;
        this._references = references;
        this._identifier = UUID.randomUUID().toString();
    }

    public Announcement(byte[] signature, User user, String message, ArrayList<Announcement> references, String identifier) throws NullSignatureException, NullMessageException,
            NullAnnouncementException, InvalidSignatureException, NoSuchAlgorithmException, InvalidKeyException, SignatureException,
            NullUserException, InvalidMessageSizeException {

        checkArguments(signature, user, message, references);
        checkSignature(signature, user, message);
        this._message = message;
        this._signature = signature;
        this._user = user;
        this._references = references;
        this._identifier = identifier;
    }

    public void checkArguments(byte[] signature, User user, String message, ArrayList<Announcement> references) throws NullSignatureException,
            NullMessageException, NullAnnouncementException, NullUserException, InvalidMessageSizeException {

        if (signature == null) {
            throw new NullSignatureException("Invalid Signature provided: null");
        }
        if (user == null) {
            throw new NullUserException("Invalid User provided: null");
        }
        if (message == null) {
            throw new NullMessageException("Invalid Message Provided: null");
        }

        if (message.length() > 255) {
            throw new InvalidMessageSizeException("Invalid Message Length provided: over 255 characters");
        }

        if (references != null) {
            if (references.contains(null)) {
                throw new NullAnnouncementException("Invalid Reference: A reference cannot be null");
            }
        }
    }

    public void checkSignature(byte[] signature, User user, String message) throws InvalidSignatureException, InvalidKeyException, NoSuchAlgorithmException,
            SignatureException {

        byte[] messageBytes = message.getBytes();
        PublicKey publicKey = user.getPublicKey();

        Signature sign = Signature.getInstance("SHA256withRSA"); // Hardcoded for now
        sign.initVerify(publicKey);
        sign.update(messageBytes);

        try {
            if (!sign.verify(signature))
                throw new InvalidSignatureException();
        } catch (SignatureException e) {
            throw new InvalidSignatureException();
        }
    }

    public String getMessage() {
        return this._message;
    }

    public byte[] getSignature() {
        return this._signature;
    }

    public ArrayList<Announcement> getReferences() {
        return this._references;
    }

    public User getUser() {
        return this._user;
    }

    public String getPostUsername() {
        return this._user.getUsername();
    }

    public String getIdentifier() {
        return _identifier;
    }


    public Contract.Announcement announcementToGRPCObject() {

        Stream<Announcement> myStream = _references.stream();
        List<String> announcementToIdentifier = myStream.map(Announcement::getIdentifier).collect(Collectors.toList());

        return Contract.Announcement.newBuilder().setMessage(_message).setUsername(_user.getUsername()).addAllReferences(announcementToIdentifier).setIdentifier(_identifier).build();
    }
}

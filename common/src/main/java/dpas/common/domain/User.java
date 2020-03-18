package dpas.common.domain;

import dpas.common.domain.exception.NullPublicKeyException;
import dpas.common.domain.exception.NullUserException;
import dpas.common.domain.exception.NullUsernameException;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.io.Serializable;
import java.security.PublicKey;
import java.util.Base64;

public class User implements Serializable {

    private String _username;
    private PublicKey _publicKey;
    private UserBoard _userBoard;

    public User(String username, PublicKey publicKey) throws NullPublicKeyException, NullUsernameException, NullUserException {
        checkArguments(username, publicKey);
        this._username = username;
        this._publicKey = publicKey;
        this._userBoard = new UserBoard(this);
    }

    public void checkArguments(String username, PublicKey publicKey) throws NullPublicKeyException, NullUsernameException {
        if (username == null || username.isBlank()) {
            throw new NullUsernameException("Invalid Username: Cannot be null or blank");
        }

        if (publicKey == null) {
            throw new NullPublicKeyException("Invalid Public Key: Cannot be null");
        }
    }

    public String getUsername() {
        return _username;
    }

    public PublicKey getPublicKey() {
        return _publicKey;
    }

    public UserBoard getUserBoard() {
        return _userBoard;
    }

    public JsonObject toJson()  {

        JsonObjectBuilder jsonBuilder = Json.createObjectBuilder();
        String pubKey = Base64.getEncoder().encodeToString(_publicKey.getEncoded());

        jsonBuilder.add("Type", "Register");
        jsonBuilder.add("Public Key", pubKey);
        jsonBuilder.add("User", _username);

        return jsonBuilder.build();
    }
}

package serverjava;

import java.net.Socket;
import java.util.UUID;

public class UsuariToken {

    private String login;
    private String passwordHash;
    private String token;
    
    
    public UsuariToken(String login, String passwordHash, String token) {
        this.login = login;
        this.passwordHash = passwordHash;
        this.token = token;
        
        token = UUID.randomUUID().toString();
    }
}

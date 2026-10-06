package org.example;

// Classe para requisições de Login/Logout
public class Mensagem {
    private String op;
    private String status;
    private String message;
    private String email;
    private String user;
    private String password;
    private String role;
    private String token;
    private String createdAt;

    private Mensagem(){}

    // MENSAGENS CLIENTE -> SERVIDOR

    public static Mensagem paraRegistro(String email, String user, String password) {
        Mensagem m = new Mensagem();
        m.op = "register";
        m.email = email;
        m.user = user;
        m.password = password;
        return m;
    }

    public static Mensagem paraLogin(String email, String password) {
        Mensagem m = new Mensagem();
        m.op = "login";
        m.email = email;
        m.password = password;
        return m;
    }

    public static Mensagem lerUsuario(String token){
        Mensagem m = new Mensagem();
        m.op = "read_user";
        m.token = token;
        return m;
    }

    public static Mensagem fazerLogout(String token){
        Mensagem m = new Mensagem();
        m.op = "logout";
        m.token = token;
        return m;
    }

    // MENSAGENS SERVIDOR -> CLIENTE

    public static Mensagem paraResposta(String op, String status, String message){
        Mensagem m = new Mensagem();
        m.op = op;
        m.status = status;
        m.message = message;
        return m;
    }

    public static Mensagem respostaLogin(String status, String message, String token, String role){
        Mensagem m = new Mensagem();
        m.op = "login_response";
        m.status = status;
        m.message = message;
        m.token = token;
        m.role = role;
        return m;
    }

    public static Mensagem respostaReadUser(String status, String message, String user, String email, String role, String createdAt){
        Mensagem m = new Mensagem();
        m.op = "read_user_response";
        m.status = status;
        m.message = message;
        m.user = user;
        m.email = email;
        m.createdAt = createdAt;
        return m;
    }



    public String getOp() { return op; }
    public String getEmail() { return email; }
    public String getUser() { return user; }
    public String getPassword() { return password; }
    public String getToken() { return token; }
    public String getMessage() { return message; }

    public void setToken(String token) { this.token = token; }

    public String toString(){
        return "\nop: " + getOp() +
                "\nemail: " + getEmail();
    }
}

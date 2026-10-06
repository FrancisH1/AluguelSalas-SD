package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DatabaseManager {
    // O banco será salvo em um arquivo local chamado 'sistema.db'
    private static final String URL = "jdbc:sqlite:sistema.db";

    // Bloco estático para inicializar a tabela de usuários
    static {
        criarTabelas();
    }

    private static Connection conectar() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    private static void criarTabelas() {
        String sqlUsuarios = "CREATE TABLE IF NOT EXISTS usuarios ("
                + " email TEXT PRIMARY KEY,"
                + " user TEXT UNIQUE NOT NULL,"
                + " password TEXT NOT NULL,"
                + " role TEXT NOT NULL"
                + " createdAt DATETIME NOT NULL"
                + " token TEXT"
                + ");";

        try (Connection conn = conectar();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sqlUsuarios);
        } catch (SQLException e) {
            System.err.println("Erro ao inicializar o banco de dados: " + e.getMessage());
        }
    }

    // 1. Cadastrar Usuário (Register)
    public static synchronized boolean cadastrarUsuario(String email, String user, String password) {
        String sql = "INSERT INTO usuarios(email, user, password, role, createdAt) VALUES(?, ?, ?, ?, ?)";

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String dataFormatada = LocalDateTime.now().format(formatter);

        try (Connection conn = conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            pstmt.setString(2, user);
            pstmt.setString(3, password);
            pstmt.setString(4, "user");
            pstmt.setString(5, dataFormatada);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            // Pode falhar caso o email ou user já existam no banco
            System.err.println("Erro ao cadastrar usuário: " + e.getMessage());
            return false;
        }
    }

    // 2. Validar Login e Salvar Token no Banco
    public static synchronized String autenticarEGerarToken(String user, String password) throws Exception {
        String sqlBusca = "SELECT token, last_activity FROM usuarios WHERE user = ? AND password = ?";

        try (Connection conn = conectar();
             PreparedStatement pstmt = conn.prepareStatement(sqlBusca)) {

            pstmt.setString(1, user);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String tokenExistente = rs.getString("token");
                String lastActivityStr = rs.getString("last_activity");

                // Verifica se já existe uma sessão ativa (3.5)
                if (tokenExistente != null && lastActivityStr != null) {
                    LocalDateTime ultimaAtividade = LocalDateTime.parse(lastActivityStr);
                    long minutosInativo = Duration.between(ultimaAtividade, LocalDateTime.now()).toMinutes();

                    // Se faz MENOS de 30 minutos desde a última requisição, o token AINDA É VÁLIDO!
                    if (minutosInativo < 30) {
                        throw new Exception("409"); // Sinaliza Sessão Única Ativa
                    }
                }

                // Se não tem sessão ativa ou o token anterior expirou, gera NOVO TOKEN (3.1)
                String novoToken = java.util.UUID.randomUUID().toString().replace("-", "")
                        + java.util.UUID.randomUUID().toString().replace("-", "");

                String sqlUpdate = "UPDATE usuarios SET token = ?, last_activity = ? WHERE user = ?";
                try (PreparedStatement pstmtUpdate = conn.prepareStatement(sqlUpdate)) {
                    pstmtUpdate.setString(1, novoToken);
                    pstmtUpdate.setString(2, LocalDateTime.now().toString());
                    pstmtUpdate.setString(3, user);
                    pstmtUpdate.executeUpdate();
                }

                return novoToken; // Token gerado com sucesso (200/201)
            }
        }
        return null; // Credenciais incorretas (401)
    }

    // 3. Logout (Invalidar Token no Banco)
    public static synchronized boolean removerToken(String token) {
        String sql = "UPDATE usuarios SET token = NULL, last_activity = NULL WHERE token = ?";
        try (Connection conn = conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, token);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}

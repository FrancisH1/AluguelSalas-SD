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
import java.util.UUID;

public class DatabaseManager {

    private static final String URL = "jdbc:sqlite:sistema.db";

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final long TOKEN_EXPIRATION_MINUTES = 30;

    static {
        criarTabelas();
    }

    private static Connection conectar() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    // =========================================================
    // BANCO
    // =========================================================

    private static void criarTabelas() {

        String sql = """
                CREATE TABLE IF NOT EXISTS usuarios (
                    email TEXT PRIMARY KEY,
                    user TEXT UNIQUE NOT NULL,
                    password TEXT NOT NULL,
                    role TEXT NOT NULL DEFAULT 'user',
                    createdAt TEXT NOT NULL,
                    token TEXT,
                    last_activity TEXT
                )
                """;

        try (Connection conn = conectar();
             Statement stmt = conn.createStatement()) {

            stmt.execute(sql);

            adicionarColunaSeNaoExiste(
                    conn,
                    "last_activity",
                    "TEXT"
            );

        } catch (SQLException e) {
            System.err.println(
                    "Erro ao inicializar banco: " +
                            e.getMessage()
            );
        }
    }

    private static void adicionarColunaSeNaoExiste(
            Connection conn,
            String coluna,
            String tipo
    ) throws SQLException {

        boolean existe = false;

        String sql = "PRAGMA table_info(usuarios)";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {

                String nomeColuna =
                        rs.getString("name");

                if (coluna.equalsIgnoreCase(nomeColuna)) {
                    existe = true;
                    break;
                }
            }
        }

        if (!existe) {

            try (Statement stmt = conn.createStatement()) {

                stmt.executeUpdate(
                        "ALTER TABLE usuarios ADD COLUMN "
                                + coluna + " " + tipo
                );
            }
        }
    }

    // =========================================================
    // REGISTER
    // =========================================================

    public static synchronized boolean cadastrarUsuario(
            String email,
            String user,
            String password
    ) throws SQLException {

        String sql = """
                INSERT INTO usuarios
                (
                    email,
                    user,
                    password,
                    role,
                    createdAt,
                    token,
                    last_activity
                )
                VALUES (?, ?, ?, 'user', ?, NULL, NULL)
                """;

        try (Connection conn = conectar();
             PreparedStatement stmt =
                     conn.prepareStatement(sql)) {

            stmt.setString(1, email.toLowerCase());
            stmt.setString(2, user.toLowerCase());
            stmt.setString(3, password);

            stmt.setString(
                    4,
                    LocalDateTime.now().format(DATE_FORMAT)
            );

            stmt.executeUpdate();

            return true;

        } catch (SQLException e) {

            /*
             * Email ou username já existente.
             */
            if (e.getMessage() != null &&
                    e.getMessage().contains("UNIQUE")) {

                return false;
            }

            throw e;
        }
    }

    // =========================================================
    // LOGIN
    // =========================================================

    public static synchronized String autenticarEGerarToken(
            String email,
            String password
    ) throws Exception {

        String sql = """
                SELECT token, last_activity
                FROM usuarios
                WHERE email = ? AND password = ?
                """;

        try (Connection conn = conectar();
             PreparedStatement stmt =
                     conn.prepareStatement(sql)) {

            stmt.setString(1, email.toLowerCase());
            stmt.setString(2, password);

            try (ResultSet rs = stmt.executeQuery()) {

                /*
                 * Email/senha incorretos.
                 */
                if (!rs.next()) {
                    return null;
                }

                String tokenExistente =
                        rs.getString("token");

                String lastActivity =
                        rs.getString("last_activity");

                /*
                 * Verifica se existe uma sessão ativa.
                 */
                if (tokenExistente != null &&
                        lastActivity != null) {

                    LocalDateTime ultimaAtividade =
                            LocalDateTime.parse(lastActivity);

                    long minutos =
                            Duration.between(
                                    ultimaAtividade,
                                    LocalDateTime.now()
                            ).toMinutes();

                    if (minutos < TOKEN_EXPIRATION_MINUTES) {

                        /*
                         * Sessão ainda ativa.
                         */
                        throw new Exception("409");
                    }
                }

                String novoToken = gerarToken();

                String update = """
                        UPDATE usuarios
                        SET token = ?,
                            last_activity = ?
                        WHERE email = ?
                        """;

                try (PreparedStatement updateStmt =
                             conn.prepareStatement(update)) {

                    updateStmt.setString(1, novoToken);

                    updateStmt.setString(
                            2,
                            LocalDateTime.now().toString()
                    );

                    updateStmt.setString(
                            3,
                            email.toLowerCase()
                    );

                    updateStmt.executeUpdate();
                }

                return novoToken;
            }
        }
    }

    private static String gerarToken() {

        /*
         * Cada UUID sem '-' possui 32 caracteres hex.
         * Dois UUIDs = 64 caracteres.
         */
        return (
                UUID.randomUUID().toString()
                        .replace("-", "")
                        +
                        UUID.randomUUID().toString()
                                .replace("-", "")
        );
    }

    // =========================================================
    // VALIDAR TOKEN
    // =========================================================

    public static synchronized UsuarioSessao validarToken(
            String token
    ) throws SQLException {

        if (!tokenValido(token)) {
            return null;
        }

        String sql = """
                SELECT
                    email,
                    user,
                    role,
                    createdAt,
                    last_activity
                FROM usuarios
                WHERE token = ?
                """;

        try (Connection conn = conectar();
             PreparedStatement stmt =
                     conn.prepareStatement(sql)) {

            stmt.setString(1, token);

            try (ResultSet rs = stmt.executeQuery()) {

                if (!rs.next()) {
                    return null;
                }

                String lastActivity =
                        rs.getString("last_activity");

                if (lastActivity == null) {
                    return null;
                }

                LocalDateTime ultimaAtividade =
                        LocalDateTime.parse(lastActivity);

                long minutos =
                        Duration.between(
                                ultimaAtividade,
                                LocalDateTime.now()
                        ).toMinutes();

                /*
                 * Sessão expirada.
                 */
                if (minutos >= TOKEN_EXPIRATION_MINUTES) {
                    return null;
                }

                /*
                 * Toda requisição autenticada válida
                 * renova a atividade da sessão.
                 */
                atualizarAtividade(conn, token);

                return new UsuarioSessao(
                        rs.getString("email"),
                        rs.getString("user"),
                        rs.getString("role"),
                        rs.getString("createdAt")
                );
            }
        }
    }

    private static boolean tokenValido(String token) {

        return token != null &&
                token.matches("^[a-f0-9]{64}$");
    }

    private static void atualizarAtividade(
            Connection conn,
            String token
    ) throws SQLException {

        String sql = """
                UPDATE usuarios
                SET last_activity = ?
                WHERE token = ?
                """;

        try (PreparedStatement stmt =
                     conn.prepareStatement(sql)) {

            stmt.setString(
                    1,
                    LocalDateTime.now().toString()
            );

            stmt.setString(2, token);

            stmt.executeUpdate();
        }
    }

    // =========================================================
    // READ USER
    // =========================================================

    public static synchronized UsuarioSessao lerUsuario(
            String token
    ) throws SQLException {

        /*
         * validarToken também atualiza last_activity.
         */
        return validarToken(token);
    }

    // =========================================================
    // UPDATE USER
    // =========================================================

    public static synchronized int atualizarUsuario(
            String token,
            String novoUser,
            String novaSenha
    ) throws SQLException {

        UsuarioSessao sessao =
                obterUsuarioSemRenovar(token);

        if (sessao == null) {
            return 401;
        }

        boolean alterarUser =
                novoUser != null &&
                        !novoUser.isEmpty();

        boolean alterarSenha =
                novaSenha != null &&
                        !novaSenha.isEmpty();

        /*
         * Nenhum campo foi alterado.
         */
        if (!alterarUser && !alterarSenha) {

            atualizarAtividadeDoToken(token);

            return 200;
        }

        try (Connection conn = conectar()) {

            conn.setAutoCommit(false);

            try {

                /*
                 * Verifica se o novo username já pertence
                 * a outro usuário.
                 */
                if (alterarUser) {

                    String verifica = """
                            SELECT email
                            FROM usuarios
                            WHERE user = ?
                              AND email <> ?
                            """;

                    try (PreparedStatement stmt =
                                 conn.prepareStatement(verifica)) {

                        stmt.setString(
                                1,
                                novoUser.toLowerCase()
                        );

                        stmt.setString(
                                2,
                                sessao.email()
                        );

                        try (ResultSet rs =
                                     stmt.executeQuery()) {

                            if (rs.next()) {

                                conn.rollback();

                                return 409;
                            }
                        }
                    }
                }

                String sql;

                if (alterarUser && alterarSenha) {

                    sql = """
                            UPDATE usuarios
                            SET user = ?,
                                password = ?,
                                last_activity = ?
                            WHERE email = ?
                            """;

                } else if (alterarUser) {

                    sql = """
                            UPDATE usuarios
                            SET user = ?,
                                last_activity = ?
                            WHERE email = ?
                            """;

                } else {

                    sql = """
                            UPDATE usuarios
                            SET password = ?,
                                last_activity = ?
                            WHERE email = ?
                            """;
                }

                try (PreparedStatement stmt =
                             conn.prepareStatement(sql)) {

                    if (alterarUser && alterarSenha) {

                        stmt.setString(
                                1,
                                novoUser.toLowerCase()
                        );

                        stmt.setString(
                                2,
                                novaSenha
                        );

                        stmt.setString(
                                3,
                                LocalDateTime.now().toString()
                        );

                        stmt.setString(
                                4,
                                sessao.email()
                        );

                    } else if (alterarUser) {

                        stmt.setString(
                                1,
                                novoUser.toLowerCase()
                        );

                        stmt.setString(
                                2,
                                LocalDateTime.now().toString()
                        );

                        stmt.setString(
                                3,
                                sessao.email()
                        );

                    } else {

                        stmt.setString(
                                1,
                                novaSenha
                        );

                        stmt.setString(
                                2,
                                LocalDateTime.now().toString()
                        );

                        stmt.setString(
                                3,
                                sessao.email()
                        );
                    }

                    stmt.executeUpdate();
                }

                conn.commit();

                return 200;

            } catch (SQLException e) {

                conn.rollback();

                throw e;

            } finally {

                conn.setAutoCommit(true);
            }
        }
    }

    /*
     * Usado pelo update para validar a sessão sem renovar
     * antes da operação.
     */
    private static UsuarioSessao obterUsuarioSemRenovar(
            String token
    ) throws SQLException {

        if (!tokenValido(token)) {
            return null;
        }

        String sql = """
                SELECT
                    email,
                    user,
                    role,
                    createdAt,
                    last_activity
                FROM usuarios
                WHERE token = ?
                """;

        try (Connection conn = conectar();
             PreparedStatement stmt =
                     conn.prepareStatement(sql)) {

            stmt.setString(1, token);

            try (ResultSet rs = stmt.executeQuery()) {

                if (!rs.next()) {
                    return null;
                }

                String lastActivity =
                        rs.getString("last_activity");

                if (lastActivity == null) {
                    return null;
                }

                LocalDateTime ultimaAtividade =
                        LocalDateTime.parse(lastActivity);

                long minutos =
                        Duration.between(
                                ultimaAtividade,
                                LocalDateTime.now()
                        ).toMinutes();

                if (minutos >= TOKEN_EXPIRATION_MINUTES) {
                    return null;
                }

                return new UsuarioSessao(
                        rs.getString("email"),
                        rs.getString("user"),
                        rs.getString("role"),
                        rs.getString("createdAt")
                );
            }
        }
    }

    private static void atualizarAtividadeDoToken(
            String token
    ) throws SQLException {

        try (Connection conn = conectar()) {
            atualizarAtividade(conn, token);
        }
    }

    // =========================================================
    // DELETE USER
    // =========================================================

    public static synchronized int deletarUsuario(
            String token,
            String password
    ) throws SQLException {

        UsuarioSessao sessao =
                obterUsuarioSemRenovar(token);

        if (sessao == null) {
            return 401;
        }

        /*
         * A senha precisa ser confirmada antes da exclusão.
         */
        String verificaSenha = """
                SELECT email
                FROM usuarios
                WHERE email = ?
                  AND password = ?
                """;

        try (Connection conn = conectar();
             PreparedStatement stmt =
                     conn.prepareStatement(verificaSenha)) {

            stmt.setString(1, sessao.email());
            stmt.setString(2, password);

            try (ResultSet rs = stmt.executeQuery()) {

                if (!rs.next()) {
                    return 401;
                }
            }
        }

        /*
         * Não permite excluir o último administrador.
         */
        if ("admin".equals(sessao.role()) &&
                quantidadeAdministradores() <= 1) {

            return 403;
        }

        String sql = """
                DELETE FROM usuarios
                WHERE email = ?
                """;

        try (Connection conn = conectar();
             PreparedStatement stmt =
                     conn.prepareStatement(sql)) {

            stmt.setString(1, sessao.email());

            int removidos =
                    stmt.executeUpdate();

            return removidos > 0
                    ? 200
                    : 401;
        }
    }

    // =========================================================
    // LOGOUT
    // =========================================================

    public static synchronized boolean removerToken(
            String token
    ) throws SQLException {

        if (!tokenValido(token)) {
            return false;
        }

        String sql = """
                UPDATE usuarios
                SET token = NULL,
                    last_activity = NULL
                WHERE token = ?
                """;

        try (Connection conn = conectar();
             PreparedStatement stmt =
                     conn.prepareStatement(sql)) {

            stmt.setString(1, token);

            return stmt.executeUpdate() > 0;
        }
    }

    // =========================================================
    // ADMIN
    // =========================================================

    private static int quantidadeAdministradores()
            throws SQLException {

        String sql = """
                SELECT COUNT(*)
                FROM usuarios
                WHERE role = 'admin'
                """;

        try (Connection conn = conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            return rs.next()
                    ? rs.getInt(1)
                    : 0;
        }
    }

    // =========================================================
    // OBJETO DE SESSÃO
    // =========================================================

    public record UsuarioSessao(
            String email,
            String user,
            String role,
            String createdAt
    ) {
    }
}
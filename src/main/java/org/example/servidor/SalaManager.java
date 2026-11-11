package org.example.servidor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Acesso ao banco para salas e para as reservas que se relacionam com elas
 * (agenda, conflito, "reservas futuras").
 *
 * Todas as operacoes usam o MESMO monitor do DatabaseManager
 * (synchronized (DatabaseManager.class)): o SQLite so aceita um escritor
 * por vez, e assim nao ha "database is locked" entre as duas classes.
 */
public final class SalaManager {

    private SalaManager() {
    }

    private static final DateTimeFormatter HORA =
            DateTimeFormatter.ofPattern("HH:mm");

    public record Sala(
            long id,
            String nome,
            int capacidade,
            String local,
            List<String> recursos,
            String status,
            List<String> slots
    ) {
    }

    /** Reserva de um dia, ja com o nome de quem reservou. */
    public record ReservaDoDia(
            long id,
            String user,
            String emailDono,
            String inicio,
            String fim
    ) {
    }

    public enum Resultado {
        OK,
        NAO_ENCONTRADA,
        NOME_EM_USO,
        CAPACIDADE_MENOR_QUE_RESERVAS,
        TEM_RESERVAS_FUTURAS
    }

    // =========================================================
    // TABELAS
    // =========================================================

    static void criarTabelas(Connection conn) throws SQLException {

        try (Statement st = conn.createStatement()) {

            st.execute("""
                    CREATE TABLE IF NOT EXISTS salas (
                        room_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        capacity INTEGER NOT NULL,
                        location TEXT NOT NULL,
                        resources TEXT NOT NULL DEFAULT '[]',
                        room_status TEXT NOT NULL DEFAULT 'active',
                        slots TEXT NOT NULL
                    )
                    """);

            /*
             * As operacoes de reserva ainda nao existem, mas a tabela ja e
             * necessaria: agenda da sala, delete_room (409) e a remocao de
             * conta (regra 3.13) dependem dela.
             * user_email (e nao o nome) para sobreviver a troca de "user".
             */
            st.execute("""
                    CREATE TABLE IF NOT EXISTS reservas (
                        reservation_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        room_id INTEGER NOT NULL,
                        user_email TEXT NOT NULL,
                        date TEXT NOT NULL,
                        start_time TEXT NOT NULL,
                        end_time TEXT NOT NULL,
                        topic TEXT NOT NULL,
                        participants INTEGER NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);

            st.execute("""
                    CREATE INDEX IF NOT EXISTS idx_reservas_sala_data
                    ON reservas (room_id, date)
                    """);
        }
    }

    // =========================================================
    // CREATE
    // =========================================================

    /** Retorna o room_id, ou null se ja existe sala com esse nome (409). */
    public static Long criar(
            String nome,
            int capacidade,
            String local,
            List<String> recursos,
            List<String> slots
    ) throws SQLException {

        synchronized (DatabaseManager.class) {

            try (Connection conn = DatabaseManager.conectar()) {

                if (nomeEmUso(conn, nome, -1)) {
                    return null;
                }

                String sql = """
                        INSERT INTO salas
                            (name, capacity, location, resources,
                             room_status, slots)
                        VALUES (?, ?, ?, ?, 'active', ?)
                        """;

                try (PreparedStatement st = conn.prepareStatement(sql)) {

                    st.setString(1, nome);
                    st.setInt(2, capacidade);
                    st.setString(3, local);
                    st.setString(4, recursosParaJson(recursos));
                    st.setString(5, String.join(",", slots));

                    st.executeUpdate();
                }

                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery(
                             "SELECT last_insert_rowid()")) {

                    return rs.next() ? rs.getLong(1) : null;
                }
            }
        }
    }

    // =========================================================
    // READ
    // =========================================================

    /** capacidadeMinima = 0 lista todas. Ordenadas por room_id. */
    public static List<Sala> listar(int capacidadeMinima)
            throws SQLException {

        synchronized (DatabaseManager.class) {

            String sql = """
                    SELECT room_id, name, capacity, location, resources,
                           room_status, slots
                    FROM salas
                    WHERE capacity >= ?
                    ORDER BY room_id
                    """;

            try (Connection conn = DatabaseManager.conectar();
                 PreparedStatement st = conn.prepareStatement(sql)) {

                st.setInt(1, capacidadeMinima);

                List<Sala> salas = new ArrayList<>();

                try (ResultSet rs = st.executeQuery()) {

                    while (rs.next()) {
                        salas.add(lerSala(rs));
                    }
                }

                return salas;
            }
        }
    }

    public static Sala buscar(long id) throws SQLException {

        synchronized (DatabaseManager.class) {

            try (Connection conn = DatabaseManager.conectar()) {
                return buscar(conn, id);
            }
        }
    }

    // =========================================================
    // UPDATE
    // =========================================================

    /**
     * "" (ou slots null/vazio) = nao alterar. Retorna o motivo da recusa
     * ou OK. A verificacao e a gravacao acontecem no mesmo bloco.
     */
    public static Resultado atualizar(
            long id,
            String nome,
            String capacidade,
            String local,
            List<String> recursos,
            String status,
            List<String> slots
    ) throws SQLException {

        synchronized (DatabaseManager.class) {

            try (Connection conn = DatabaseManager.conectar()) {

                Sala atual = buscar(conn, id);

                if (atual == null) {
                    return Resultado.NAO_ENCONTRADA;
                }

                String novoNome = nome.isEmpty() ? atual.nome() : nome;

                int novaCapacidade = capacidade.isEmpty()
                        ? atual.capacidade()
                        : Integer.parseInt(capacidade);

                String novoLocal = local.isEmpty() ? atual.local() : local;

                String novoStatus = status.isEmpty() ? atual.status() : status;

                List<String> novosSlots =
                        (slots == null || slots.isEmpty())
                                ? atual.slots()
                                : slots;

                List<String> novosRecursos;
                if (recursos == null || recursos.isEmpty()) {
                    novosRecursos = atual.recursos();
                } else if (recursos.size() == 1 && "0".equals(recursos.get(0))) {
                    novosRecursos = List.of();
                } else {
                    novosRecursos = recursos;
                }

                if (!novoNome.equals(atual.nome())
                        && nomeEmUso(conn, novoNome, id)) {
                    return Resultado.NOME_EM_USO;
                }

                /*
                 * Reduzir a capacidade nao pode deixar uma reserva futura
                 * com mais participantes do que cabem (pendencia 3).
                 */
                if (novaCapacidade < atual.capacidade()
                        && existeReservaFuturaMaiorQue(
                        conn, id, novaCapacidade)) {
                    return Resultado.CAPACIDADE_MENOR_QUE_RESERVAS;
                }

                String sql = """
                        UPDATE salas
                        SET name = ?, capacity = ?, location = ?,
                            resources = ?, room_status = ?, slots = ?
                        WHERE room_id = ?
                        """;

                try (PreparedStatement st = conn.prepareStatement(sql)) {

                    st.setString(1, novoNome);
                    st.setInt(2, novaCapacidade);
                    st.setString(3, novoLocal);

                    // [] mantém; ["0"] limpa; lista normal substitui.
                    st.setString(4, recursosParaJson(novosRecursos));

                    st.setString(5, novoStatus);
                    st.setString(6, String.join(",", novosSlots));
                    st.setLong(7, id);

                    st.executeUpdate();
                }

                return Resultado.OK;
            }
        }
    }

    // =========================================================
    // DELETE
    // =========================================================

    public static Resultado remover(long id) throws SQLException {

        synchronized (DatabaseManager.class) {

            try (Connection conn = DatabaseManager.conectar()) {

                if (buscar(conn, id) == null) {
                    return Resultado.NAO_ENCONTRADA;
                }

                if (contarReservasFuturas(conn, id) > 0) {
                    return Resultado.TEM_RESERVAS_FUTURAS;
                }

                conn.setAutoCommit(false);

                try {

                    /*
                     * So sobram reservas ja encerradas: saem junto com a
                     * sala para nao ficarem apontando para um room_id morto.
                     */
                    try (PreparedStatement st = conn.prepareStatement(
                            "DELETE FROM reservas WHERE room_id = ?")) {

                        st.setLong(1, id);
                        st.executeUpdate();
                    }

                    try (PreparedStatement st = conn.prepareStatement(
                            "DELETE FROM salas WHERE room_id = ?")) {

                        st.setLong(1, id);
                        st.executeUpdate();
                    }

                    conn.commit();

                } catch (SQLException e) {

                    conn.rollback();

                    throw e;

                } finally {

                    conn.setAutoCommit(true);
                }

                return Resultado.OK;
            }
        }
    }

    // =========================================================
    // RESERVAS (somente consultas usadas por list_rooms/read_room)
    // =========================================================

    /** Reservas da sala no dia, por horario, com o nome de quem reservou. */
    public static List<ReservaDoDia> reservasDoDia(long roomId, String data)
            throws SQLException {

        synchronized (DatabaseManager.class) {

            String sql = """
                    SELECT r.reservation_id, u.user, r.user_email,
                           r.start_time, r.end_time
                    FROM reservas r
                    JOIN usuarios u ON u.email = r.user_email
                    WHERE r.room_id = ? AND r.date = ?
                    ORDER BY r.start_time
                    """;

            try (Connection conn = DatabaseManager.conectar();
                 PreparedStatement st = conn.prepareStatement(sql)) {

                st.setLong(1, roomId);
                st.setString(2, data);

                List<ReservaDoDia> lista = new ArrayList<>();

                try (ResultSet rs = st.executeQuery()) {

                    while (rs.next()) {

                        lista.add(new ReservaDoDia(
                                rs.getLong(1),
                                rs.getString(2),
                                rs.getString(3),
                                rs.getString(4),
                                rs.getString(5)
                        ));
                    }
                }

                return lista;
            }
        }
    }

    /** Regra 5.4: novo_inicio < fim_existente E novo_fim > inicio_existente. */
    public static boolean temConflito(
            long roomId,
            String data,
            String inicio,
            String fim
    ) throws SQLException {

        synchronized (DatabaseManager.class) {

            String sql = """
                    SELECT COUNT(*)
                    FROM reservas
                    WHERE room_id = ? AND date = ?
                      AND ? < end_time AND ? > start_time
                    """;

            try (Connection conn = DatabaseManager.conectar();
                 PreparedStatement st = conn.prepareStatement(sql)) {

                st.setLong(1, roomId);
                st.setString(2, data);
                st.setString(3, inicio);
                st.setString(4, fim);

                try (ResultSet rs = st.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        }
    }

    // =========================================================
    // INTERNOS (recebem a conexao; sempre dentro do synchronized)
    // =========================================================

    private static Sala buscar(Connection conn, long id)
            throws SQLException {

        String sql = """
                SELECT room_id, name, capacity, location, resources,
                       room_status, slots
                FROM salas
                WHERE room_id = ?
                """;

        try (PreparedStatement st = conn.prepareStatement(sql)) {

            st.setLong(1, id);

            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? lerSala(rs) : null;
            }
        }
    }

    private static boolean nomeEmUso(
            Connection conn,
            String nome,
            long ignorarId
    ) throws SQLException {

        String sql = """
                SELECT 1 FROM salas
                WHERE name = ? AND room_id <> ?
                """;

        try (PreparedStatement st = conn.prepareStatement(sql)) {

            st.setString(1, nome);
            st.setLong(2, ignorarId);

            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Reserva futura = ainda nao terminou (data depois de hoje, ou hoje e fim depois de agora). */
    private static int contarReservasFuturas(Connection conn, long roomId)
            throws SQLException {

        String sql = """
                SELECT COUNT(*) FROM reservas
                WHERE room_id = ?
                  AND (date > ? OR (date = ? AND end_time > ?))
                """;

        try (PreparedStatement st = conn.prepareStatement(sql)) {

            String hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();

            st.setLong(1, roomId);
            st.setString(2, hoje);
            st.setString(3, hoje);
            st.setString(4, LocalTime.now(ZoneId.of("America/Sao_Paulo")).format(HORA));

            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static boolean existeReservaFuturaMaiorQue(
            Connection conn,
            long roomId,
            int capacidade
    ) throws SQLException {

        String sql = """
                SELECT COUNT(*) FROM reservas
                WHERE room_id = ?
                  AND participants > ?
                  AND (date > ? OR (date = ? AND end_time > ?))
                """;

        try (PreparedStatement st = conn.prepareStatement(sql)) {

            String hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();

            st.setLong(1, roomId);
            st.setInt(2, capacidade);
            st.setString(3, hoje);
            st.setString(4, hoje);
            st.setString(5, LocalTime.now(ZoneId.of("America/Sao_Paulo")).format(HORA));

            try (ResultSet rs = st.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static Sala lerSala(ResultSet rs) throws SQLException {

        String slots = rs.getString("slots");

        return new Sala(
                rs.getLong("room_id"),
                rs.getString("name"),
                rs.getInt("capacity"),
                rs.getString("location"),
                recursosDeJson(rs.getString("resources")),
                rs.getString("room_status"),
                slots.isEmpty()
                        ? new ArrayList<>()
                        : new ArrayList<>(List.of(slots.split(",")))
        );
    }

    private static String recursosParaJson(List<String> recursos) {

        JsonArray a = new JsonArray();

        for (String r : recursos) {
            a.add(r);
        }

        return a.toString();
    }

    private static List<String> recursosDeJson(String json) {

        List<String> r = new ArrayList<>();

        for (JsonElement e : JsonParser.parseString(json).getAsJsonArray()) {
            r.add(e.getAsString());
        }

        return r;
    }
}

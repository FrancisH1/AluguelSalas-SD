package org.example.servidor;

import org.example.comum.Horarios;
import org.example.comum.Protocolo;
import org.example.comum.Validador;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.example.comum.Protocolo.MSG_DADOS;
import static org.example.comum.Protocolo.MSG_PERMISSAO;
import static org.example.comum.Protocolo.MSG_TOKEN_INVALIDO;
import static org.example.comum.Protocolo.lista;
import static org.example.comum.Protocolo.resp;
import static org.example.comum.Protocolo.str;

/**
 * CRUD de salas. create/update/delete exigem admin; list/read, qualquer
 * usuario logado. Ordem: 400 -> 401 -> 403 -> 404 -> 409 -> executa.
 */
final class OperacoesSala {

    private OperacoesSala() {
    }

    private static final String MSG_SALA_400 = "Dados da sala em formato invalido";
    private static final String MSG_FILTROS_400 = "Filtros em formato invalido";
    private static final String MSG_SALA_404 = "Sala nao encontrada";
    private static final String MSG_SALA_DUPLICADA = "Sala ja cadastrada";
    private static final String MSG_RESERVAS_ATIVAS = "Sala possui reservas ativas";

    /*
     * Pendencia 3 do protocolo: o 409 de "reduzir capacidade abaixo dos
     * participantes de uma reserva futura" nao tem message definida. Usa a
     * mais proxima do catalogo; troque aqui se o grupo combinar outra.
     */
    private static final String MSG_CAPACIDADE_409 = "Capacidade menor que participantes de reservas ativas";

    // =========================================================
    // create_room (admin)
    // =========================================================

    static JsonObject criar(JsonObject j) throws Exception {

        final String op = "create_room_response";

        String token = str(j, "token");
        String nome = str(j, "name");
        String capacidade = str(j, "capacity");
        String local = str(j, "location");
        List<String> recursos = lista(j, "resources");
        List<String> slots = lista(j, "slots");

        /*
         * Em criacao, "" e invalido (regra 2.11): as regex ja rejeitam.
         */
        if (!Validador.token(token)
                || !Validador.nomeSala(nome)
                || !Validador.capacidade(capacidade)
                || !Validador.local(local)
                || !recursosValidosCriacao(recursos)
                || !slotsValidos(slots)) {

            return resp(op, "400", MSG_SALA_400);
        }

        DatabaseManager.UsuarioSessao autor = DatabaseManager.validarToken(token);

        if (autor == null) {
            return resp(op, "401", MSG_TOKEN_INVALIDO);
        }

        if (!"admin".equals(autor.role())) {
            return resp(op, "403", MSG_PERMISSAO);
        }

        Long id = SalaManager.criar(
                nome, Integer.parseInt(capacidade), local, recursos, slots);

        if (id == null) {
            return resp(op, "409", MSG_SALA_DUPLICADA);
        }

        JsonObject r = resp(op, "201", "Sala cadastrada com sucesso");

        r.addProperty("room_id", String.valueOf(id));

        return r;
    }

    // =========================================================
    // list_rooms (logado)
    // =========================================================

    static JsonObject listar(JsonObject j) throws Exception {

        final String op = "list_rooms_response";

        String token = str(j, "token");

        if (!Validador.token(token)) {
            return resp(op, "400", MSG_FILTROS_400);
        }

        /*
         * Filtro de horario: date, start_time e end_time vem juntos ou
         * nenhum (parte do trio -> 400).
         */
        boolean temData = j.has("date");
        boolean temInicio = j.has("start_time");
        boolean temFim = j.has("end_time");

        String data = null;
        String inicio = null;
        String fim = null;
        List<Horarios.Aula> aulas = null;

        if (temData || temInicio || temFim) {

            data = str(j, "date");
            inicio = str(j, "start_time");
            fim = str(j, "end_time");

            if (!temData || !temInicio || !temFim
                    || !Validador.data(data)) {
                return resp(op, "400", MSG_FILTROS_400);
            }

            aulas = Horarios.aulasEntre(inicio, fim);

            if (aulas == null) {
                return resp(op, "400", MSG_FILTROS_400);
            }
        }

        int capacidadeMinima = 0;

        if (j.has("min_capacity")) {

            String min = str(j, "min_capacity");

            if (!Validador.capacidade(min)) {
                return resp(op, "400", MSG_FILTROS_400);
            }

            capacidadeMinima = Integer.parseInt(min);
        }

        if (DatabaseManager.validarToken(token) == null) {
            return resp(op, "401", MSG_TOKEN_INVALIDO);
        }

        JsonArray salas = new JsonArray();

        for (SalaManager.Sala s : SalaManager.listar(capacidadeMinima)) {

            JsonObject item = itemSala(s);

            if (aulas != null) {
                item.addProperty(
                        "available",
                        String.valueOf(
                                salaLivre(s, data, inicio, fim, aulas))
                );
            }

            salas.add(item);
        }

        JsonObject r = resp(op, "200", "Consulta realizada com sucesso");

        r.addProperty("count", String.valueOf(salas.size()));
        r.add("rooms", salas);

        return r;
    }

    /**
     * Regra 8.4: livre = sala active + todas as aulas ocupadas fazem parte
     * dos slots dela naquele dia da semana + nenhuma reserva sobrepondo.
     * Domingo nunca tem funcionamento.
     */
    private static boolean salaLivre(
            SalaManager.Sala s,
            String data,
            String inicio,
            String fim,
            List<Horarios.Aula> aulas
    ) throws Exception {

        if (!"active".equals(s.status())) {
            return false;
        }

        String dia = Horarios.codigoDoDia(LocalDate.parse(data));

        if (dia == null) {
            return false;
        }

        for (Horarios.Aula a : aulas) {

            if (!s.slots().contains(dia + a.codigo())) {
                return false;
            }
        }

        return !SalaManager.temConflito(s.id(), data, inicio, fim);
    }

    // =========================================================
    // read_room (logado)
    // =========================================================

    static JsonObject ler(JsonObject j) throws Exception {

        final String op = "read_room_response";

        String token = str(j, "token");
        String roomId = str(j, "room_id");

        boolean comData = j.has("date");
        String data = str(j, "date");

        if (!Validador.token(token)
                || !Validador.id(roomId)
                || (comData && !Validador.data(data))) {

            return resp(op, "400", MSG_DADOS);
        }

        DatabaseManager.UsuarioSessao autor = DatabaseManager.validarToken(token);

        if (autor == null) {
            return resp(op, "401", MSG_TOKEN_INVALIDO);
        }

        SalaManager.Sala s = SalaManager.buscar(Long.parseLong(roomId));

        if (s == null) {
            return resp(op, "404", MSG_SALA_404);
        }

        JsonObject r = resp(op, "200", "Consulta realizada com sucesso");

        copiar(itemSala(s), r);

        if (comData) {

            r.addProperty("date", data);
            r.add("agenda", agenda(s, data, autor));
        }

        return r;
    }

    /**
     * Regra 8.5: um item por horario da sala nesse dia da semana, na ordem
     * da tabela. "indisponivel" = reservado ou sala inactive. Reservado
     * traz "user" (quem reservou) para qualquer logado; "reservation_id"
     * so para o admin e para o dono da reserva.
     */
    private static JsonArray agenda(
            SalaManager.Sala s,
            String data,
            DatabaseManager.UsuarioSessao autor
    ) throws Exception {

        JsonArray agenda = new JsonArray();

        String dia = Horarios.codigoDoDia(LocalDate.parse(data));

        if (dia == null) {
            return agenda; // domingo: sala nao funciona
        }

        List<SalaManager.ReservaDoDia> reservas =
                SalaManager.reservasDoDia(s.id(), data);

        boolean admin = "admin".equals(autor.role());

        for (Horarios.Aula aula : Horarios.AULAS) {

            String codigo = dia + aula.codigo();

            if (!s.slots().contains(codigo)) {
                continue;
            }

            JsonObject item = new JsonObject();

            item.addProperty("slot", codigo);
            item.addProperty("start_time", aula.inicio());
            item.addProperty("end_time", aula.fim());

            SalaManager.ReservaDoDia dona = null;

            for (SalaManager.ReservaDoDia rv : reservas) {

                if (Horarios.sobrepoe(
                        aula.inicio(), aula.fim(), rv.inicio(), rv.fim())) {
                    dona = rv;
                    break;
                }
            }

            if (dona != null) {

                item.addProperty("slot_status", "indisponivel");
                item.addProperty("user", dona.user());

                if (admin || dona.emailDono().equals(autor.email())) {
                    item.addProperty(
                            "reservation_id", String.valueOf(dona.id()));
                }

            } else {

                item.addProperty(
                        "slot_status",
                        "active".equals(s.status()) ? "livre" : "indisponivel"
                );
            }

            agenda.add(item);
        }

        return agenda;
    }

    // =========================================================
    // update_room (admin)
    // =========================================================

    static JsonObject atualizar(JsonObject j) throws Exception {

        final String op = "update_room_response";

        String token = str(j, "token");
        String roomId = str(j, "room_id");
        String nome = str(j, "name");
        String capacidade = str(j, "capacity");
        String local = str(j, "location");
        String status = str(j, "room_status");
        List<String> recursos = lista(j, "resources");

        /*
         * slots: ausente ou [] = nao alterar (regra 8.7); se vier, tem
         * de ser array de strings validas.
         */
        boolean comSlots = j.has("slots");
        List<String> slots = comSlots ? lista(j, "slots") : null;

        boolean slotsOk = !comSlots
                || (slots != null
                && (slots.isEmpty() || slotsValidos(slots)));

        boolean recursosOk = !j.has("resources") || recursosValidosUpdate(recursos);

        /*
         * "" = nao alterar (regra 2.11). resources [] tambem significa
         * manter; ["0"] e o comando explicito para limpar todos.
         */
        if (!Validador.token(token)
                || !Validador.id(roomId)
                || nome == null || capacidade == null
                || local == null || status == null
                || !recursosOk
                || !slotsOk
                || (!nome.isEmpty() && !Validador.nomeSala(nome))
                || (!capacidade.isEmpty() && !Validador.capacidade(capacidade))
                || (!local.isEmpty() && !Validador.local(local))
                || (!status.isEmpty()
                && !status.equals("active") && !status.equals("inactive"))) {

            return resp(op, "400", MSG_SALA_400);
        }

        if (nome.isEmpty() && capacidade.isEmpty() && local.isEmpty()
                && status.isEmpty() && (recursos == null || recursos.isEmpty())
                && (slots == null || slots.isEmpty())) {
            return resp(op, "400", "Nenhum dado para atualizar");
        }

        DatabaseManager.UsuarioSessao autor = DatabaseManager.validarToken(token);

        if (autor == null) {
            return resp(op, "401", MSG_TOKEN_INVALIDO);
        }

        if (!"admin".equals(autor.role())) {
            return resp(op, "403", MSG_PERMISSAO);
        }

        SalaManager.Resultado r = SalaManager.atualizar(
                Long.parseLong(roomId),
                nome, capacidade, local, recursos, status, slots);

        return switch (r) {

            case NAO_ENCONTRADA ->
                    resp(op, "404", MSG_SALA_404);

            case NOME_EM_USO ->
                    resp(op, "409", MSG_SALA_DUPLICADA);

            case CAPACIDADE_MENOR_QUE_RESERVAS ->
                    resp(op, "409", MSG_CAPACIDADE_409);

            case OK, TEM_RESERVAS_FUTURAS ->
                    resp(op, "200", "Sala atualizada com sucesso");
        };
    }

    // =========================================================
    // delete_room (admin)
    // =========================================================

    static JsonObject remover(JsonObject j) throws Exception {

        final String op = "delete_room_response";

        String token = str(j, "token");
        String roomId = str(j, "room_id");

        if (!Validador.token(token) || !Validador.id(roomId)) {
            return resp(op, "400", MSG_DADOS);
        }

        DatabaseManager.UsuarioSessao autor = DatabaseManager.validarToken(token);

        if (autor == null) {
            return resp(op, "401", MSG_TOKEN_INVALIDO);
        }

        if (!"admin".equals(autor.role())) {
            return resp(op, "403", MSG_PERMISSAO);
        }

        SalaManager.Resultado r = SalaManager.remover(Long.parseLong(roomId));

        return switch (r) {

            case NAO_ENCONTRADA ->
                    resp(op, "404", MSG_SALA_404);

            case TEM_RESERVAS_FUTURAS ->
                    resp(op, "409", MSG_RESERVAS_ATIVAS);

            case OK, NOME_EM_USO, CAPACIDADE_MENOR_QUE_RESERVAS ->
                    resp(op, "200", "Sala removida com sucesso");
        };
    }

    // =========================================================
    // AUXILIARES
    // =========================================================

    private static boolean recursosValidosCriacao(List<String> recursos) {
        if (recursos == null) return false;
        for (String recurso : recursos) {
            if (recurso == null || recurso.equals("0")
                    || !recurso.matches("^[A-Za-z0-9 -]{1,30}$")) return false;
        }
        return true;
    }

    /** resources em update: ausente/[] mantém; ["0"] limpa; lista normal substitui. */
    private static boolean recursosValidosUpdate(List<String> recursos) {
        if (recursos == null) return false;
        if (recursos.isEmpty()) return true;
        if (recursos.size() == 1 && "0".equals(recursos.get(0))) return true;
        for (String recurso : recursos) {
            if (recurso == null || recurso.equals("0")
                    || !recurso.matches("^[A-Za-z0-9 -]{1,30}$")) return false;
        }
        return true;
    }

    /** Pelo menos um item, todos validos (2M1...), sem repetir (regra 8.2). */
    private static boolean slotsValidos(List<String> slots) {

        if (slots == null || slots.isEmpty()) {
            return false;
        }

        Set<String> vistos = new HashSet<>();

        for (String s : slots) {

            if (!Validador.slot(s) || !vistos.add(s)) {
                return false;
            }
        }

        return true;
    }

    /** Campos da sala, todos como string (regra 2.3). */
    private static JsonObject itemSala(SalaManager.Sala s) {

        JsonObject o = new JsonObject();

        o.addProperty("room_id", String.valueOf(s.id()));
        o.addProperty("name", s.nome());
        o.addProperty("capacity", String.valueOf(s.capacidade()));
        o.addProperty("location", s.local());
        o.add("resources", Protocolo.paraArray(s.recursos()));
        o.addProperty("room_status", s.status());
        o.add("slots", Protocolo.paraArray(s.slots()));

        return o;
    }

    private static void copiar(JsonObject de, JsonObject para) {

        for (String chave : new String[]{
                "room_id", "name", "capacity", "location",
                "resources", "room_status", "slots"}) {

            para.add(chave, de.get(chave));
        }
    }
}

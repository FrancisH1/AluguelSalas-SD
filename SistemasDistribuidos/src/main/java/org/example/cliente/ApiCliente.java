package org.example.cliente;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.example.comum.Protocolo;
import org.example.comum.Validador;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.example.comum.Protocolo.lista;
import static org.example.comum.Protocolo.str;

/**
 * Tudo que o cliente sabe fazer, uma chamada por operacao do protocolo.
 * Nao tem interface: serve a GUI (e a qualquer outra interface).
 *
 * - Guarda o token e o role da sessao, so em memoria (regra 3.2).
 * - Nunca lanca excecao de rede: devolve Resultado com falhaDeRede.
 * - Em 401 numa operacao autenticada, descarta a sessao (regra 3.9) e avisa
 *   por setAoPerderSessao. O callback roda na thread que fez a chamada.
 * - O role serve so para a interface esconder menus (regra 3.14): quem
 *   decide a permissao e sempre o servidor.
 */
public final class ApiCliente {

    private static final int TIMEOUT_LOGOUT_FINAL_MS = 2000;

    private final Conexao conexao = new Conexao();

    private volatile String token = null;
    private volatile String role = "user";
    private volatile Runnable aoPerderSessao = () -> {
    };

    // =========================================================
    // ESTADO
    // =========================================================

    public void configurar(String ip, int porta) {
        conexao.configurar(ip, porta);
    }

    public boolean logado() {
        return token != null;
    }

    public String role() {
        return role;
    }

    public boolean admin() {
        return logado() && "admin".equals(role);
    }

    public void setAoPerderSessao(Runnable acao) {
        this.aoPerderSessao = acao == null ? () -> {
        } : acao;
    }

    private void limparSessao() {
        token = null;
        role = "user";
    }

    // =========================================================
    // SESSAO E PROPRIO CADASTRO
    // =========================================================

    public Resultado<Void> registrar(String email, String user, String senha) {

        JsonObject req = nova("register");

        req.addProperty("email", email);
        req.addProperty("user", user);
        req.addProperty("password", senha);

        return chamar(req, false, null);
    }

    /** Em caso de sucesso, devolve o role e guarda token e role. */
    public Resultado<String> login(String email, String senha) {

        JsonObject req = nova("login");

        req.addProperty("email", email);
        req.addProperty("password", senha);

        Resultado<String> r = chamar(req, false, j -> {

            String t = str(j, "token");

            if (!Validador.token(t)) {
                return null;
            }

            String papel = str(j, "role");

            token = t;
            role = papel == null ? "user" : papel;

            return role;
        });

        if (r.ok() && r.dados() == null) {

            return new Resultado<>(
                    "500", "O servidor enviou um token invalido.",
                    null, false, false
            );
        }

        return r;
    }

    /**
     * Regra 3.10: o token so e descartado depois do logout_response
     * (200, ou 401 se ja nao valia). Sem resposta, a sessao e mantida.
     */
    public Resultado<Void> logout() {

        if (token == null) {
            return new Resultado<>("200", "Sem sessao ativa.", null, false, false);
        }

        // autenticada=false: aqui um 401 nao e "sessao perdida", e o proprio resultado
        Resultado<Void> r = chamar(autenticada("logout"), false, null);

        if (!r.falhaDeRede()
                && ("200".equals(r.status()) || "401".equals(r.status()))) {
            limparSessao();
        }

        return r;
    }

    /**
     * Chamar ao fechar a janela: tenta o logout numa conexao propria e
     * curta (a principal pode ter caido por inatividade) e fecha tudo.
     * Se o processo for morto a forca, quem resolve e o servidor, ao
     * perceber a conexao caida.
     */
    public void encerrar() {

        String t = token;

        if (t != null) {

            JsonObject req = nova("logout");

            req.addProperty("token", t);

            try {
                conexao.enviarEmConexaoPropria(req, TIMEOUT_LOGOUT_FINAL_MS);
            } catch (IOException ignored) {
            }

            limparSessao();
        }

        conexao.fechar();
    }

    /** Atualiza tambem o role guardado. */
    public Resultado<Modelos.Usuario> meuCadastro() {

        return chamar(autenticada("read_user"), true, j -> {

            Modelos.Usuario u = usuario(j);

            if (u.role() != null) {
                role = u.role();
            }

            return u;
        });
    }

    /** "" = nao alterar. */
    public Resultado<Void> atualizarMeuCadastro(String user, String senha) {

        JsonObject req = autenticada("update_user");

        req.addProperty("user", user);
        req.addProperty("password", senha);

        return chamar(req, true, null);
    }

    /** Em caso de sucesso a conta some e a sessao e descartada. */
    public Resultado<Void> deletarMeuCadastro(String senha) {

        JsonObject req = autenticada("delete_user");

        req.addProperty("password", senha);

        Resultado<Void> r = chamar(req, true, null);

        if (r.ok()) {
            limparSessao();
        }

        return r;
    }

    // =========================================================
    // SALAS (qualquer usuario logado)
    // =========================================================

    public Resultado<List<Modelos.Sala>> listarSalas(Modelos.FiltroSalas filtro) {

        JsonObject req = autenticada("list_rooms");

        if (filtro != null) {

            if (filtro.data() != null) {
                req.addProperty("date", filtro.data());
                req.addProperty("start_time", filtro.inicio());
                req.addProperty("end_time", filtro.fim());
            }

            if (filtro.capacidadeMinima() != null) {
                req.addProperty("min_capacity", filtro.capacidadeMinima());
            }
        }

        return chamar(req, true, j -> {

            List<Modelos.Sala> salas = new ArrayList<>();

            for (JsonElement e : j.get("rooms").getAsJsonArray()) {
                salas.add(sala(e.getAsJsonObject()));
            }

            return salas;
        });
    }

    /** data = null: so os dados da sala. Com data: inclui a agenda do dia. */
    public Resultado<Modelos.DetalheSala> lerSala(String id, String data) {

        JsonObject req = autenticada("read_room");

        req.addProperty("room_id", id);

        if (data != null) {
            req.addProperty("date", data);
        }

        return chamar(req, true, j -> {

            List<Modelos.ItemAgenda> agenda = new ArrayList<>();

            if (j.has("agenda")) {

                for (JsonElement e : j.get("agenda").getAsJsonArray()) {
                    agenda.add(itemAgenda(e.getAsJsonObject()));
                }
            }

            return new Modelos.DetalheSala(sala(j), str(j, "date"), agenda);
        });
    }

    // =========================================================
    // ADMIN: SALAS
    // =========================================================

    /** Devolve o room_id da sala criada. */
    public Resultado<String> criarSala(Modelos.NovaSala sala) {

        JsonObject req = autenticada("create_room");

        req.addProperty("name", sala.nome());
        req.addProperty("capacity", sala.capacidade());
        req.addProperty("location", sala.local());
        req.add("resources", Protocolo.paraArray(sala.recursos()));
        req.add("slots", Protocolo.paraArray(sala.slots()));

        return chamar(req, true, j -> str(j, "room_id"));
    }

    /**
     * "" = nao alterar campos escalares. resources []/ausente mantém;
     * ["0"] limpa todos; lista normal substitui. slots null/[] mantém.
     */
    public Resultado<Void> atualizarSala(
            String id,
            String nome,
            String capacidade,
            String local,
            List<String> recursos,
            String estado,
            List<String> slots
    ) {

        JsonObject req = autenticada("update_room");

        req.addProperty("room_id", id);
        req.addProperty("name", nome);
        req.addProperty("capacity", capacidade);
        req.addProperty("location", local);
        req.add("resources", Protocolo.paraArray(recursos));
        req.addProperty("room_status", estado);

        if (slots != null) {
            req.add("slots", Protocolo.paraArray(slots));
        }

        return chamar(req, true, null);
    }

    public Resultado<Void> removerSala(String id) {

        JsonObject req = autenticada("delete_room");

        req.addProperty("room_id", id);

        return chamar(req, true, null);
    }

    // =========================================================
    // ADMIN: USUARIOS
    // =========================================================

    public Resultado<List<Modelos.Usuario>> listarUsuarios() {

        return chamar(autenticada("admin_list_users"), true, j -> {

            List<Modelos.Usuario> lista = new ArrayList<>();

            for (JsonElement e : j.get("users").getAsJsonArray()) {
                lista.add(usuario(e.getAsJsonObject()));
            }

            return lista;
        });
    }

    public Resultado<Modelos.Usuario> lerUsuario(String alvo) {

        JsonObject req = autenticada("admin_read_user");

        req.addProperty("target_user", alvo);

        return chamar(req, true, ApiCliente::usuario);
    }

    /** "" = nao alterar. role: "user", "admin" ou "". */
    public Resultado<Void> atualizarUsuario(
            String alvo,
            String user,
            String senha,
            String papel
    ) {

        JsonObject req = autenticada("admin_update_user");

        req.addProperty("target_user", alvo);
        req.addProperty("user", user);
        req.addProperty("password", senha);
        req.addProperty("role", papel);

        return chamar(req, true, null);
    }

    public Resultado<Void> removerUsuario(String alvo) {

        JsonObject req = autenticada("admin_delete_user");

        req.addProperty("target_user", alvo);

        return chamar(req, true, null);
    }

    // =========================================================
    // INTERNOS
    // =========================================================

    /** "op" sempre primeiro (regra 2.1). */
    private static JsonObject nova(String op) {

        JsonObject o = new JsonObject();

        o.addProperty("op", op);

        return o;
    }

    private JsonObject autenticada(String op) {

        JsonObject o = nova(op);

        o.addProperty("token", token);

        return o;
    }

    /**
     * Envia, interpreta e converte. conversor so roda em 200/201.
     * autenticada = true: um 401 significa "sem sessao" e dispara o aviso.
     */
    private <T> Resultado<T> chamar(
            JsonObject requisicao,
            boolean autenticada,
            Function<JsonObject, T> conversor
    ) {

        JsonObject r;

        try {

            r = conexao.enviar(requisicao);

        } catch (IOException e) {

            return Resultado.redeFalhou(
                    "Sem resposta do servidor: " + e.getMessage()
            );
        }

        if (r == null) {
            return Resultado.redeFalhou("Resposta invalida do servidor.");
        }

        String status = str(r, "status");
        String mensagem = str(r, "message");

        if (status == null) {
            return Resultado.redeFalhou("Resposta invalida do servidor.");
        }

        T dados = null;

        if (conversor != null
                && ("200".equals(status) || "201".equals(status))) {
            dados = conversor.apply(r);
        }

        boolean perdeu = autenticada && "401".equals(status);

        if (perdeu) {

            limparSessao();

            aoPerderSessao.run();
        }

        return new Resultado<>(
                status,
                mensagem == null ? "" : mensagem,
                dados,
                false,
                perdeu
        );
    }

    private static Modelos.Usuario usuario(JsonObject j) {

        return new Modelos.Usuario(
                str(j, "user"),
                str(j, "email"),
                str(j, "role"),
                str(j, "created_at")
        );
    }

    private static Modelos.Sala sala(JsonObject j) {

        int capacidade;

        try {
            capacidade = Integer.parseInt(str(j, "capacity"));
        } catch (NumberFormatException e) {
            capacidade = 0;
        }

        List<String> recursos = lista(j, "resources");
        List<String> slots = lista(j, "slots");

        return new Modelos.Sala(
                str(j, "room_id"),
                str(j, "name"),
                capacidade,
                str(j, "location"),
                recursos == null ? new ArrayList<>() : recursos,
                str(j, "room_status"),
                slots == null ? new ArrayList<>() : slots,
                j.has("available") ? "true".equals(str(j, "available")) : null
        );
    }

    private static Modelos.ItemAgenda itemAgenda(JsonObject j) {

        return new Modelos.ItemAgenda(
                str(j, "slot"),
                str(j, "start_time"),
                str(j, "end_time"),
                "livre".equals(str(j, "slot_status")),
                str(j, "user"),
                str(j, "reservation_id")
        );
    }
}

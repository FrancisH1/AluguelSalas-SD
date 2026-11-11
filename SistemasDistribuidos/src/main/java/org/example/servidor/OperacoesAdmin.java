package org.example.servidor;

import org.example.comum.Protocolo;
import org.example.comum.Validador;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import static org.example.comum.Protocolo.MSG_DADOS;
import static org.example.comum.Protocolo.MSG_PERMISSAO;
import static org.example.comum.Protocolo.MSG_TOKEN_INVALIDO;
import static org.example.comum.Protocolo.resp;
import static org.example.comum.Protocolo.str;

/**
 * Operacoes admin_* (regra 3.7). Ordem de validacao sugerida pelo protocolo:
 * campos e regex (400) -> token (401) -> permissao (403) -> existencia (404)
 * -> conflito (409) -> executa.
 *
 * A permissao vem do role GRAVADO NO BANCO para o dono do token, lido a cada
 * requisicao (regra 3.14). Nunca de um campo "role" enviado pelo cliente.
 */
final class OperacoesAdmin {

    private OperacoesAdmin() {
    }

    // =========================================================
    // admin_list_users
    // =========================================================

    static JsonObject listarUsuarios(JsonObject j) throws Exception {

        final String op = "admin_list_users_response";

        String token = str(j, "token");

        if (!Validador.token(token)) {
            return resp(op, "400", "Token em formato invalido");
        }

        DatabaseManager.UsuarioSessao autor = DatabaseManager.validarToken(token);

        if (autor == null) {
            return resp(op, "401", MSG_TOKEN_INVALIDO);
        }

        if (!"admin".equals(autor.role())) {
            return resp(op, "403", MSG_PERMISSAO);
        }

        JsonArray usuarios = new JsonArray();

        for (DatabaseManager.UsuarioResumo u : DatabaseManager.listarUsuarios()) {

            // nunca devolve senha
            JsonObject item = new JsonObject();

            item.addProperty("user", u.user());
            item.addProperty("email", u.email());
            item.addProperty("role", u.role());

            usuarios.add(item);
        }

        JsonObject r = resp(op, "200", "Consulta realizada com sucesso");

        r.addProperty("count", String.valueOf(usuarios.size()));
        r.add("users", usuarios);

        return r;
    }

    // =========================================================
    // admin_read_user
    // =========================================================

    static JsonObject lerUsuario(JsonObject j) throws Exception {

        final String op = "admin_read_user_response";

        String token = str(j, "token");
        String alvo = str(j, "target_user");

        if (!Validador.token(token) || !Validador.user(alvo)) {
            return resp(op, "400", MSG_DADOS);
        }

        DatabaseManager.UsuarioSessao autor = DatabaseManager.validarToken(token);

        if (autor == null) {
            return resp(op, "401", MSG_TOKEN_INVALIDO);
        }

        if (!"admin".equals(autor.role())) {
            return resp(op, "403", MSG_PERMISSAO);
        }

        DatabaseManager.UsuarioSessao u = DatabaseManager.buscarUsuarioPorNome(alvo);

        if (u == null) {
            return resp(op, "404", "Usuario nao encontrado");
        }

        JsonObject r = resp(op, "200", "Consulta realizada com sucesso");

        r.addProperty("user", u.user());
        r.addProperty("email", u.email());
        r.addProperty("role", u.role());
        r.addProperty("created_at", u.createdAt());

        return r;
    }

    // =========================================================
    // admin_update_user
    // =========================================================

    static JsonObject atualizarUsuario(JsonObject j) throws Exception {

        final String op = "admin_update_user_response";

        String token = str(j, "token");
        String alvo = str(j, "target_user");
        String user = str(j, "user");
        String senha = str(j, "password");
        String role = str(j, "role");

        /*
         * "" = nao alterar (regra 2.11). A chave "email" e proibida
         * (RNF 5.c): o e-mail nao muda depois do cadastro.
         */
        if (!Validador.token(token)
                || !Validador.user(alvo)
                || user == null || senha == null || role == null
                || j.has("email")
                || (!user.isEmpty() && !Validador.user(user))
                || (!senha.isEmpty() && !Validador.password(senha))
                || (!role.isEmpty()
                && !role.equals("user") && !role.equals("admin"))) {

            return resp(op, "400", MSG_DADOS);
        }

        if (user.isEmpty() && senha.isEmpty() && role.isEmpty()) {
            return resp(op, "400", "Nenhum dado para atualizar");
        }

        DatabaseManager.UsuarioSessao autor = DatabaseManager.validarToken(token);

        if (autor == null) {
            return resp(op, "401", MSG_TOKEN_INVALIDO);
        }

        if (!"admin".equals(autor.role())) {
            return resp(op, "403", MSG_PERMISSAO);
        }

        DatabaseManager.ResultadoAdmin r =
                DatabaseManager.atualizarUsuarioAdmin(alvo, user, senha, role);

        return switch (r) {

            case NAO_ENCONTRADO ->
                    resp(op, "404", "Usuario nao encontrado");

            case ULTIMO_ADMIN ->
                    resp(op, "403", Protocolo.MSG_ULTIMO_ADMIN);

            case USUARIO_EM_USO ->
                    resp(op, "409", "Usuario ja esta em uso");

            case OK ->
                    resp(op, "200", "Dados atualizados com sucesso");
        };
    }

    // =========================================================
    // admin_delete_user
    // =========================================================

    static JsonObject removerUsuario(JsonObject j) throws Exception {

        final String op = "admin_delete_user_response";

        String token = str(j, "token");
        String alvo = str(j, "target_user");

        if (!Validador.token(token) || !Validador.user(alvo)) {
            return resp(op, "400", MSG_DADOS);
        }

        DatabaseManager.UsuarioSessao autor = DatabaseManager.validarToken(token);

        if (autor == null) {
            return resp(op, "401", MSG_TOKEN_INVALIDO);
        }

        if (!"admin".equals(autor.role())) {
            return resp(op, "403", MSG_PERMISSAO);
        }

        /*
         * Apaga cadastro, sessao e todas as reservas do alvo (regra 3.13).
         */
        DatabaseManager.ResultadoAdmin r =
                DatabaseManager.removerUsuarioPorNome(alvo);

        return switch (r) {

            case NAO_ENCONTRADO ->
                    resp(op, "404", "Usuario nao encontrado");

            case ULTIMO_ADMIN ->
                    resp(op, "403", Protocolo.MSG_ULTIMO_ADMIN);

            case OK, USUARIO_EM_USO ->
                    resp(op, "200", "Usuario removido com sucesso");
        };
    }
}

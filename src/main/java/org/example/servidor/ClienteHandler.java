package org.example.servidor;

import org.example.comum.Protocolo;
import org.example.comum.Validador;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.example.comum.Protocolo.MSG_DADOS;
import static org.example.comum.Protocolo.MSG_TOKEN_INVALIDO;
import static org.example.comum.Protocolo.resp;
import static org.example.comum.Protocolo.str;

class ClienteHandler implements Runnable {

    /** Regra 1.6: 8192 bytes, ja incluindo o \n. */
    static final int TAMANHO_MAXIMO = 8192;

    /** Regra 1.9: 300 s sem receber nada -> fecha a conexao (sem invalidar o token). */
    private static final int TIMEOUT_INATIVIDADE_MS = 300_000;

    private final Socket socket;

    /**
     * Token que esta conexao TCP "segura" em ConexoesAtivas (null = nenhum).
     * Ao fechar a conexao ele e solto, e a sessao no banco passa a ser
     * considerada orfa (ver ConexoesAtivas).
     */
    private String tokenVinculado = null;

    public ClienteHandler(Socket socket) {
        this.socket = socket;
    }

    // =========================================================
    // LOOP DA CONEXAO
    // =========================================================

    @Override
    public void run() {

        try (
                InputStream in = new BufferedInputStream(socket.getInputStream());
                OutputStream out = socket.getOutputStream()
        ) {

            socket.setSoTimeout(TIMEOUT_INATIVIDADE_MS);
            socket.setKeepAlive(true);

            while (true) {

                JsonObject resposta;

                try {

                    String linha = lerLinha(in);

                    if (linha == null) {
                        System.out.println(
                                "Cliente desconectou: " + socket.getInetAddress()
                        );
                        break;
                    }

                    System.out.println("[Recebido do Cliente " + socket.getInetAddress() + "]: " + linha);
                    resposta = tratar(linha);

                } catch (LinhaGrandeException e) {

                    resposta = resp(
                            "error",
                            "400",
                            "Mensagem excede o tamanho maximo"
                    );
                }

                /*
                 * ÚNICO ponto onde uma resposta é enviada.
                 */
                enviar(out, resposta);

                System.out.println("[Enviado ao Cliente " + socket.getInetAddress() + "]: " + resposta);
            }

        } catch (SocketTimeoutException e) {

            System.out.println(
                    "Conexao fechada por inatividade: " + socket.getInetAddress()
            );

        } catch (IOException e) {

            /*
             * Processo do cliente encerrado sem logout, queda de rede etc.
             */
            System.out.println(
                    "Conexao perdida com " + socket.getInetAddress() +
                            ": " + e.getMessage()
            );

        } catch (RuntimeException e) {

            e.printStackTrace();

        } finally {

            /*
             * Em QUALQUER saida (EOF, timeout, erro, excecao) a conexao
             * deixa de ser dona do token. O token continua no banco, valido
             * para reconexao (regra 1.11), mas um novo login do dono da
             * conta passa a ser aceito em vez de levar 409.
             */
            soltarToken();

            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    // =========================================================
    // FRAMING (regras 1.4 a 1.6)
    // =========================================================

    static class LinhaGrandeException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    /**
     * Le bytes ate o LF. Retorna null no fim da conexao (inclusive se
     * ela cair no meio de uma linha, que nao conta como requisicao).
     * Linhas acima de 8192 bytes (com o \n) sao consumidas ate o LF,
     * sem guardar na memoria, e sinalizadas com LinhaGrandeException.
     */
    static String lerLinha(InputStream in)
            throws IOException, LinhaGrandeException {

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int total = 0;
        int b;

        while ((b = in.read()) != -1) {

            total++;

            if (b == '\n') {

                if (total > TAMANHO_MAXIMO) {
                    throw new LinhaGrandeException();
                }

                return buffer.toString(StandardCharsets.UTF_8);
            }

            if (total <= TAMANHO_MAXIMO) {
                buffer.write(b);
            }
        }

        return null;
    }

    /** Regra 1.5: toda mensagem termina com LF (nunca \r\n). */
    private void enviar(OutputStream out, JsonObject resposta)
            throws IOException {

        /*
         * toString() do JsonObject: linha unica, chaves na ordem em que
         * foram inseridas (op primeiro).
         */
        out.write(
                (resposta.toString() + "\n")
                        .getBytes(StandardCharsets.UTF_8)
        );

        out.flush();
    }

    // =========================================================
    // PARSE + DISPATCH
    // =========================================================

    private JsonObject tratar(String linha) {

        JsonObject json;

        try {

            JsonElement elemento = JsonParser.parseString(linha);

            if (!elemento.isJsonObject()) {
                return resp("error", "400", "Requisicao invalida");
            }

            json = elemento.getAsJsonObject();

        } catch (JsonParseException e) {

            /*
             * JSON malformado -> 400 (e nao 500).
             */
            return resp("error", "400", "Requisicao invalida");
        }

        String op = str(json, "op");

        if (op == null) {
            return resp("error", "400", "Requisicao invalida");
        }

        try {

            JsonObject resposta = processar(op, json);

            atualizarVinculo(op, json, resposta);

            return resposta;

        } catch (Exception e) {

            e.printStackTrace();

            return resp(
                    op + "_response",
                    "500",
                    "Erro interno do servidor"
            );
        }
    }

    private JsonObject processar(String op, JsonObject json)
            throws Exception {

        return switch (op) {

            // sessao e proprio cadastro
            case "register" -> register(json);
            case "login" -> login(json);
            case "logout" -> logout(json);
            case "read_user" -> readUser(json);
            case "update_user" -> updateUser(json);
            case "delete_user" -> deleteUser(json);

            // administrador: usuarios
            case "admin_list_users" -> OperacoesAdmin.listarUsuarios(json);
            case "admin_read_user" -> OperacoesAdmin.lerUsuario(json);
            case "admin_update_user" -> OperacoesAdmin.atualizarUsuario(json);
            case "admin_delete_user" -> OperacoesAdmin.removerUsuario(json);

            // salas
            case "create_room" -> OperacoesSala.criar(json);
            case "list_rooms" -> OperacoesSala.listar(json);
            case "read_room" -> OperacoesSala.ler(json);
            case "update_room" -> OperacoesSala.atualizar(json);
            case "delete_room" -> OperacoesSala.remover(json);

            /*
             * check_availability e as operacoes de reserva fazem parte da
             * proxima entrega: por enquanto caem aqui.
             */
            default -> resp("error", "400", "Operacao desconhecida");
        };
    }

    // =========================================================
    // VINCULO CONEXAO <-> TOKEN
    // =========================================================

    /**
     * Mantem ConexoesAtivas coerente com o que esta conexao ja provou
     * possuir. O login e tratado dentro de login().
     */
    private void atualizarVinculo(
            String op,
            JsonObject json,
            JsonObject resposta
    ) {

        if (!"200".equals(Protocolo.status(resposta))) {
            return;
        }

        switch (op) {

            case "login" -> {
                // ja tratado em login()
            }

            case "logout", "delete_user" -> soltarToken();

            // Qualquer outra operacao autenticada que deu certo: o token
            // e valido nesta conexao. Cobre o cliente que reconectou e
            // reenviou com o mesmo token.
            default -> adotarToken(str(json, "token"));
        }
    }

    private void adotarToken(String token) {

        if (token == null || token.equals(tokenVinculado)) {
            return;
        }

        ConexoesAtivas.desvincular(tokenVinculado);
        ConexoesAtivas.vincular(token);
        tokenVinculado = token;
    }

    /**
     * Para o login: o DatabaseManager ja chamou ConexoesAtivas.vincular()
     * dentro da secao critica, entao aqui so registramos o dono.
     */
    private void registrarTokenDoLogin(String token) {

        ConexoesAtivas.desvincular(tokenVinculado);
        tokenVinculado = token;
    }

    private void soltarToken() {

        ConexoesAtivas.desvincular(tokenVinculado);
        tokenVinculado = null;
    }

    // =========================================================
    // REGISTER
    // =========================================================

    private JsonObject register(JsonObject j) throws Exception {

        final String op = "register_response";

        String email = str(j, "email");
        String user = str(j, "user");
        String senha = str(j, "password");

        if (!Validador.email(email)
                || !Validador.user(user)
                || !Validador.password(senha)) {

            return resp(op, "400", "Dados de cadastro em formato invalido");
        }

        boolean criado = DatabaseManager.cadastrarUsuario(email, user, senha);

        if (!criado) {
            return resp(op, "409", "Usuario ou email ja cadastrado");
        }

        return resp(op, "201", "Usuario cadastrado com sucesso");
    }

    // =========================================================
    // LOGIN  (ordem fixa: 400 -> 401 -> 409)
    // =========================================================

    private JsonObject login(JsonObject j) throws Exception {

        final String op = "login_response";

        String email = str(j, "email");
        String senha = str(j, "password");

        if (!Validador.email(email) || !Validador.password(senha)) {
            return resp(op, "400", "Email ou senha em formato invalido");
        }

        try {

            String token =
                    DatabaseManager.autenticarEGerarToken(email, senha);

            if (token == null) {
                return resp(op, "401", "Email ou senha incorretos");
            }

            /*
             * Antes de qualquer outra coisa que possa falhar: a partir
             * daqui esta conexao e a dona do token.
             */
            registrarTokenDoLogin(token);

            DatabaseManager.UsuarioSessao sessao =
                    DatabaseManager.lerUsuario(token);

            if (sessao == null) {
                return resp(op, "500", "Erro interno do servidor");
            }

            JsonObject r = resp(op, "200", "Login realizado com sucesso");

            r.addProperty("token", token);
            r.addProperty("role", sessao.role());

            return r;

        } catch (Exception e) {

            if ("409".equals(e.getMessage())) {
                return resp(op, "409", "Usuario ja possui sessao ativa");
            }

            throw e;
        }
    }

    // =========================================================
    // LOGOUT
    // =========================================================

    private JsonObject logout(JsonObject j) throws Exception {

        final String op = "logout_response";

        String token = str(j, "token");

        if (!Validador.token(token)) {
            return resp(op, "400", "Token em formato invalido");
        }

        if (DatabaseManager.validarToken(token) == null
                || !DatabaseManager.removerToken(token)) {

            return resp(op, "401", MSG_TOKEN_INVALIDO);
        }

        return resp(op, "200", "Logout realizado com sucesso");
    }

    // =========================================================
    // READ USER
    // =========================================================

    private JsonObject readUser(JsonObject j) throws Exception {

        final String op = "read_user_response";

        String token = str(j, "token");

        if (!Validador.token(token)) {
            return resp(op, "400", "Token em formato invalido");
        }

        DatabaseManager.UsuarioSessao sessao =
                DatabaseManager.lerUsuario(token);

        if (sessao == null) {
            return resp(op, "401", MSG_TOKEN_INVALIDO);
        }

        JsonObject r = resp(op, "200", "Consulta realizada com sucesso");

        r.addProperty("user", sessao.user());
        r.addProperty("email", sessao.email());
        r.addProperty("role", sessao.role());
        r.addProperty("created_at", sessao.createdAt());

        return r;
    }

    // =========================================================
    // UPDATE USER
    // =========================================================

    private JsonObject updateUser(JsonObject j) throws Exception {

        final String op = "update_user_response";

        String token = str(j, "token");
        String user = str(j, "user");
        String senha = str(j, "password");

        /*
         * 400: campos obrigatorios, email proibido (RNF 5.c), regex.
         * "" significa "nao alterar" e e aceito.
         */
        if (!Validador.token(token)
                || user == null || senha == null
                || j.has("email")
                || (!user.isEmpty() && !Validador.user(user))
                || (!senha.isEmpty() && !Validador.password(senha))) {

            return resp(op, "400", MSG_DADOS);
        }

        /*
         * Regra 2.13: nada para alterar -> 400, nao 200.
         */
        if (user.isEmpty() && senha.isEmpty()) {
            return resp(op, "400", "Nenhum dado para atualizar");
        }

        int resultado = DatabaseManager.atualizarUsuario(token, user, senha);

        return switch (resultado) {

            case 200 -> resp(op, "200", "Dados atualizados com sucesso");

            case 401 -> resp(op, "401", MSG_TOKEN_INVALIDO);

            case 409 -> resp(op, "409", "Usuario ja esta em uso");

            default -> resp(op, "500", "Erro interno do servidor");
        };
    }

    // =========================================================
    // DELETE USER
    // =========================================================

    private JsonObject deleteUser(JsonObject j) throws Exception {

        final String op = "delete_user_response";

        String token = str(j, "token");
        String senha = str(j, "password");

        /*
         * No protocolo, o unico 400 de delete_user e
         * "Senha em formato invalido".
         */
        if (!Validador.token(token) || !Validador.password(senha)) {
            return resp(op, "400", "Senha em formato invalido");
        }

        int resultado = DatabaseManager.deletarUsuario(token, senha);

        return switch (resultado) {

            case 200 -> resp(op, "200", "Usuario removido com sucesso");

            case 401 -> resp(op, "401", MSG_TOKEN_INVALIDO);

            // Senha de confirmacao errada com token valido: 403 e a
            // sessao continua (regra 3.9).
            case DatabaseManager.SENHA_INCORRETA ->
                    resp(op, "403", "Senha incorreta");

            case DatabaseManager.ULTIMO_ADMIN ->
                    resp(op, "403", Protocolo.MSG_ULTIMO_ADMIN);

            default -> resp(op, "500", "Erro interno do servidor");
        };
    }
}

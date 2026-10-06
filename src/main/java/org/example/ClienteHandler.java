package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

class ClienteHandler implements Runnable {

    private final Socket socket;
    private final Gson gson;

    public ClienteHandler(Socket socket) {

        this.socket = socket;
        this.gson = new Gson();
    }

    @Override
    public void run() {

        try (
                BufferedReader in =
                        new BufferedReader(
                                new InputStreamReader(
                                        socket.getInputStream()
                                )
                        );

                PrintWriter out =
                        new PrintWriter(
                                socket.getOutputStream(),
                                true
                        )
        ) {

            String jsonRecebido;

            while ((jsonRecebido = in.readLine()) != null) {

                if ("SAIR".equals(jsonRecebido)) {

                    System.out.println(
                            "Conexao encerrada de: " +
                                    socket.getInetAddress()
                    );

                    break;
                }

                Mensagem resposta;

                try {

                    JsonElement elemento =
                            JsonParser.parseString(
                                    jsonRecebido
                            );

                    if (!elemento.isJsonObject()) {

                        resposta = erro(
                                "error",
                                "400",
                                "Requisicao invalida"
                        );

                    } else {

                        JsonObject json =
                                elemento.getAsJsonObject();

                        if (!stringObrigatoria(
                                json,
                                "op"
                        )) {

                            resposta = erro(
                                    "error",
                                    "400",
                                    "Requisicao invalida"
                            );

                        } else {

                            Mensagem req =
                                    gson.fromJson(
                                            json,
                                            Mensagem.class
                                    );

                            resposta =
                                    processar(
                                            req,
                                            json,
                                            req.getOp()
                                    );
                        }
                    }

                } catch (Exception e) {

                    e.printStackTrace();

                    resposta = erro(
                            "error",
                            "500",
                            "Erro interno do servidor"
                    );
                }

                /*
                 * ÚNICO ponto onde uma resposta é enviada.
                 */
                out.println(
                        gson.toJson(resposta)
                );

                System.out.println(
                        "[Enviado ao Cliente]: " +
                                resposta.getOp() +
                                " " +
                                resposta.getStatus()
                );
            }

        } catch (Exception e) {

            System.err.println(
                    "Erro na comunicacao com o cliente: " +
                            e.getMessage()
            );

        } finally {

            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    // =========================================================
    // DISPATCH
    // =========================================================

    private Mensagem processar(
            Mensagem req,
            JsonObject json,
            String op
    ) throws Exception {

        if (op == null) {

            return erro(
                    "error",
                    "400",
                    "Operacao invalida"
            );
        }

        return switch (op) {

            case "register" ->
                    register(req, json);

            case "login" ->
                    login(req, json);

            case "logout" ->
                    logout(req, json);

            case "read_user" ->
                    readUser(req, json);

            case "update_user" ->
                    updateUser(req, json);

            case "delete_user" ->
                    deleteUser(req, json);

            default ->
                    erro(
                            "error",
                            "400",
                            "Operacao nao suportada"
                    );
        };
    }

    // =========================================================
    // REGISTER
    // =========================================================

    private Mensagem register(
            Mensagem req,
            JsonObject json
    ) throws Exception {

        if (!stringObrigatoria(
                json,
                "email"
        ) ||
                !stringObrigatoria(
                        json,
                        "user"
                ) ||
                !stringObrigatoria(
                        json,
                        "password"
                )) {

            return erro(
                    "register_response",
                    "400",
                    "Dados de cadastro em formato invalido"
            );
        }

        if (!Cliente.validarEmail(
                req.getEmail()
        ) ||
                !Cliente.validarUser(
                        req.getUser()
                ) ||
                !Cliente.validarPassword(
                        req.getPassword()
                )) {

            return erro(
                    "register_response",
                    "400",
                    "Dados de cadastro em formato invalido"
            );
        }

        boolean criado =
                DatabaseManager.cadastrarUsuario(
                        req.getEmail(),
                        req.getUser(),
                        req.getPassword()
                );

        if (!criado) {

            return erro(
                    "register_response",
                    "409",
                    "Usuario ou email ja cadastrado"
            );
        }

        return erro(
                "register_response",
                "201",
                "Usuario cadastrado com sucesso"
        );
    }

    // =========================================================
    // LOGIN
    // =========================================================

    private Mensagem login(
            Mensagem req,
            JsonObject json
    ) throws Exception {

        if (!stringObrigatoria(
                json,
                "email"
        ) ||
                !stringObrigatoria(
                        json,
                        "password"
                )) {

            return erro(
                    "login_response",
                    "400",
                    "Email ou senha em formato invalido"
            );
        }

        if (!Cliente.validarEmail(
                req.getEmail()
        ) ||
                !Cliente.validarPassword(
                        req.getPassword()
                )) {

            return erro(
                    "login_response",
                    "400",
                    "Email ou senha em formato invalido"
            );
        }

        try {

            String token =
                    DatabaseManager
                            .autenticarEGerarToken(
                                    req.getEmail(),
                                    req.getPassword()
                            );

            if (token == null) {

                return erro(
                        "login_response",
                        "401",
                        "Email ou senha incorretos"
                );
            }

            DatabaseManager.UsuarioSessao sessao =
                    DatabaseManager.lerUsuario(
                            token
                    );

            if (sessao == null) {

                return erro(
                        "login_response",
                        "500",
                        "Erro interno do servidor"
                );
            }

            return Mensagem.respostaLogin(
                    "200",
                    "Login realizado com sucesso",
                    token,
                    sessao.role()
            );

        } catch (Exception e) {

            if ("409".equals(
                    e.getMessage()
            )) {

                return erro(
                        "login_response",
                        "409",
                        "Usuario ja possui sessao ativa"
                );
            }

            throw e;
        }
    }

    // =========================================================
    // LOGOUT
    // =========================================================

    private Mensagem logout(
            Mensagem req,
            JsonObject json
    ) throws Exception {

        if (!stringObrigatoria(
                json,
                "token"
        )) {

            return erro(
                    "logout_response",
                    "400",
                    "Token em formato invalido"
            );
        }

        if (!Cliente.validarToken(
                req.getToken()
        )) {

            return erro(
                    "logout_response",
                    "400",
                    "Token em formato invalido"
            );
        }

        /*
         * Verifica se a sessão existe e está ativa.
         */
        if (DatabaseManager.validarToken(
                req.getToken()
        ) == null) {

            return erro(
                    "logout_response",
                    "401",
                    "Token invalido ou expirado"
            );
        }

        boolean removido =
                DatabaseManager.removerToken(
                        req.getToken()
                );

        if (!removido) {

            return erro(
                    "logout_response",
                    "401",
                    "Token invalido ou expirado"
            );
        }

        return erro(
                "logout_response",
                "200",
                "Logout realizado com sucesso"
        );
    }

    // =========================================================
    // READ USER
    // =========================================================

    private Mensagem readUser(
            Mensagem req,
            JsonObject json
    ) throws Exception {

        if (!stringObrigatoria(
                json,
                "token"
        )) {

            return erro(
                    "read_user_response",
                    "400",
                    "Token em formato invalido"
            );
        }

        if (!Cliente.validarToken(
                req.getToken()
        )) {

            return erro(
                    "read_user_response",
                    "400",
                    "Token em formato invalido"
            );
        }

        DatabaseManager.UsuarioSessao sessao =
                DatabaseManager.lerUsuario(
                        req.getToken()
                );

        if (sessao == null) {

            return erro(
                    "read_user_response",
                    "401",
                    "Token invalido ou expirado"
            );
        }

        return Mensagem.respostaReadUser(
                "200",
                "Consulta realizada com sucesso",
                sessao.user(),
                sessao.email(),
                sessao.role(),
                sessao.createdAt()
        );
    }

    // =========================================================
    // UPDATE USER
    // =========================================================

    private Mensagem updateUser(
            Mensagem req,
            JsonObject json
    ) throws Exception {

        /*
         * token, user e password são obrigatórios.
         */
        if (!stringObrigatoria(
                json,
                "token"
        ) ||
                !stringObrigatoria(
                        json,
                        "user"
                ) ||
                !stringObrigatoria(
                        json,
                        "password"
                )) {

            return erro(
                    "update_user_response",
                    "400",
                    "Dados em formato invalido"
            );
        }

        /*
         * Email não faz parte da atualização.
         */
        if (json.has("email")) {

            return erro(
                    "update_user_response",
                    "400",
                    "Dados em formato invalido"
            );
        }

        if (!Cliente.validarToken(
                req.getToken()
        )) {

            return erro(
                    "update_user_response",
                    "400",
                    "Dados em formato invalido"
            );
        }

        /*
         * String vazia significa não alterar.
         */
        if (!req.getUser().isEmpty() &&
                !Cliente.validarUser(
                        req.getUser()
                )) {

            return erro(
                    "update_user_response",
                    "400",
                    "Dados em formato invalido"
            );
        }

        if (!req.getPassword().isEmpty() &&
                !Cliente.validarPassword(
                        req.getPassword()
                )) {

            return erro(
                    "update_user_response",
                    "400",
                    "Dados em formato invalido"
            );
        }

        int resultado =
                DatabaseManager.atualizarUsuario(
                        req.getToken(),
                        req.getUser(),
                        req.getPassword()
                );

        return switch (resultado) {

            case 200 ->
                    erro(
                            "update_user_response",
                            "200",
                            "Dados atualizados com sucesso"
                    );

            case 401 ->
                    erro(
                            "update_user_response",
                            "401",
                            "Token invalido ou expirado"
                    );

            case 409 ->
                    erro(
                            "update_user_response",
                            "409",
                            "Usuario ja esta em uso"
                    );

            default ->
                    erro(
                            "update_user_response",
                            "500",
                            "Erro interno do servidor"
                    );
        };
    }

    // =========================================================
    // DELETE USER
    // =========================================================

    private Mensagem deleteUser(
            Mensagem req,
            JsonObject json
    ) throws Exception {

        if (!stringObrigatoria(
                json,
                "token"
        ) ||
                !stringObrigatoria(
                        json,
                        "password"
                )) {

            return erro(
                    "delete_user_response",
                    "400",
                    "Senha em formato invalido"
            );
        }

        if (!Cliente.validarToken(
                req.getToken()
        )) {

            return erro(
                    "delete_user_response",
                    "400",
                    "Token em formato invalido"
            );
        }

        if (!Cliente.validarPassword(
                req.getPassword()
        )) {

            return erro(
                    "delete_user_response",
                    "400",
                    "Senha em formato invalido"
            );
        }

        int resultado =
                DatabaseManager.deletarUsuario(
                        req.getToken(),
                        req.getPassword()
                );

        return switch (resultado) {

            case 200 ->
                    erro(
                            "delete_user_response",
                            "200",
                            "Usuario removido com sucesso"
                    );

            case 401 ->
                    erro(
                            "delete_user_response",
                            "401",
                            "Token invalido ou expirado"
                    );

            case 403 ->
                    erro(
                            "delete_user_response",
                            "403",
                            "Nao e possivel remover o ultimo administrador"
                    );

            default ->
                    erro(
                            "delete_user_response",
                            "500",
                            "Erro interno do servidor"
                    );
        };
    }

    // =========================================================
    // AUXILIARES
    // =========================================================

    private Mensagem erro(
            String op,
            String status,
            String message
    ) {

        return Mensagem.paraResposta(
                op,
                status,
                message
        );
    }

    private boolean stringObrigatoria(
            JsonObject json,
            String campo
    ) {

        return json.has(campo)
                && !json.get(campo).isJsonNull()
                && json.get(campo).isJsonPrimitive()
                && json.get(campo)
                .getAsJsonPrimitive()
                .isString();
    }
}
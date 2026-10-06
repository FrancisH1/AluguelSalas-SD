package org.example;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

// Classe responsável por tratar a comunicação com um cliente específico
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
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String jsonRecebido;
            // Lê as mensagens enviadas pelo cliente linha a linha
            while ((jsonRecebido = in.readLine()) != null) {

                if("SAIR".equals(jsonRecebido)) {
                    System.out.println("Conexão encerrada de: " + socket.getInetAddress());
                    break;
                }

                // 1. Recebe a linha de texto do cliente e converte para o Objeto Mensagem
                Mensagem req = gson.fromJson(jsonRecebido, Mensagem.class);
                String jsonEnvio;

                System.out.println("[Recebido do Cliente]: " + req.toString());

                Mensagem resp = null;

                // Lógica simples baseada na operação
                switch (req.getOp()) {
                    case "register":
                        boolean cadastrou = DatabaseManager.cadastrarUsuario(req.getEmail(), req.getUser(), req.getPassword());
                        if (cadastrou) {
                            resp = Mensagem.paraResposta("register_response", "200", "Usuário cadastrado com sucesso!");
                        } else {
                            resp = Mensagem.paraResposta("resgister_response", "409", "Usuário ou e-mail já cadastrado.");
                        }
                        break;

                    case "login":
                        try {
                            String token = DatabaseManager.autenticarEGerarToken(req.getUser(), req.getPassword());
                            if (token != null) {
                                // Sucesso
                                out.println(gson.toJson(Mensagem.respostaLogin("200", "Login realizado com sucesso", token, "user")));
                            } else {
                                // 401 Credenciais Incorretas
                                out.println(gson.toJson(Mensagem.paraResposta("login_response", "401", "Credenciais incorretas")));
                            }
                        } catch (Exception e) {
                            if ("409".equals(e.getMessage())) {
                                // ITEM 3.5: Sessão ativa existente
                                out.println(gson.toJson(Mensagem.paraResposta("login_response", "409", "Usuário já possui uma sessão ativa")));
                            }
                        }

                    case "logout":
                        boolean deslogou = DatabaseManager.removerToken(req.getToken());
                        if(deslogou){
                            resp = Mensagem.paraResposta("logout_response", "200", "Logout com sucesso!");
                        } else {
                            resp = Mensagem.paraResposta("logout_response", "401", "Token inválido ou expirado!");
                        }
                        break;

                    case "read_user":
                        //resp = Mensagem.respostaReadUser("200", "Consulta realizada com sucesso", );
                        break;

                    default:
                        resp = Mensagem.paraResposta("", "400", "Operação não suportada.");
                        break;
                }

                System.out.println("[Enviado ao Cliente]: " + resp.getMessage());

                // Envia a resposta em formato JSON como uma linha de texto
                jsonEnvio = gson.toJson(resp);
                out.println(jsonEnvio);
            }

        } catch (Exception e) {
            System.err.println("Erro na comunicação com o cliente: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

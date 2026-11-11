package org.example.cliente;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Camada de transporte: um socket TCP, uma requisicao por vez, uma resposta
 * por requisicao (regra 1.7). Sem interface com o usuario.
 *
 * Todos os metodos sao synchronized: mesmo que duas threads chamem ao mesmo
 * tempo, as requisicoes nunca se misturam na conexao.
 */
public final class Conexao {

    /** Regra 1.11: tentativas de reenviar a mesma requisicao numa conexao nova. */
    private static final int TENTATIVAS = 3;

    private static final int TIMEOUT_CONEXAO_MS = 5000;
    private static final int TIMEOUT_LEITURA_MS = 15000;

    private String ip;
    private int porta;

    private Socket socket;
    private OutputStream out;
    private BufferedReader in;

    /** Define o servidor. Fecha a conexao atual, se houver. */
    public synchronized void configurar(String ip, int porta) {

        fechar();

        this.ip = ip;
        this.porta = porta;
    }

    /**
     * Envia uma requisicao e devolve a resposta (null se a linha recebida
     * nao for um objeto JSON).
     *
     * Regra 1.11: se a conexao caiu ou foi fechada por inatividade (o
     * servidor fecha com 300 s), abre outra e reenvia a mesma requisicao,
     * com o mesmo token. Se o servidor ja nao tiver a sessao, responde 401.
     *
     * Excecao: se o servidor NAO respondeu a tempo, a requisicao nao e
     * reenviada (poderia executar duas vezes); a conexao e fechada.
     */
    public synchronized JsonObject enviar(JsonObject requisicao)
            throws IOException {

        if (ip == null) {
            throw new IOException("Servidor nao configurado.");
        }

        byte[] dados =
                (requisicao.toString() + "\n").getBytes(StandardCharsets.UTF_8);

        IOException ultimoErro = null;

        for (int i = 1; i <= TENTATIVAS; i++) {

            if (socket == null) {
                conectar();   // falha aqui nao e repetida: servidor fora do ar
            }

            try {

                out.write(dados);
                out.flush();

                String linha = in.readLine();

                if (linha == null) {
                    throw new IOException("Servidor encerrou a conexao.");
                }

                try {

                    JsonElement e = JsonParser.parseString(linha);

                    return e.isJsonObject() ? e.getAsJsonObject() : null;

                } catch (JsonParseException e) {
                    return null;
                }

            } catch (SocketTimeoutException e) {

                fechar();

                throw new IOException("O servidor nao respondeu a tempo.");

            } catch (IOException e) {

                ultimoErro = e;

                fechar();
            }
        }

        throw ultimoErro;
    }

    /**
     * Envia UMA requisicao numa conexao propria e curta, sem mexer na
     * conexao principal. Usado para o logout ao encerrar o programa: a
     * conexao principal pode ja ter sido fechada por inatividade.
     */
    public void enviarEmConexaoPropria(JsonObject requisicao, int timeoutMs)
            throws IOException {

        String ipLocal;
        int portaLocal;

        synchronized (this) {
            ipLocal = ip;
            portaLocal = porta;
        }

        if (ipLocal == null) {
            return;
        }

        try (Socket s = new Socket()) {

            s.connect(new InetSocketAddress(ipLocal, portaLocal), timeoutMs);
            s.setSoTimeout(timeoutMs);

            OutputStream o = s.getOutputStream();

            o.write(
                    (requisicao.toString() + "\n")
                            .getBytes(StandardCharsets.UTF_8)
            );

            o.flush();

            new BufferedReader(
                    new InputStreamReader(
                            s.getInputStream(),
                            StandardCharsets.UTF_8
                    )
            ).readLine();
        }
    }

    public synchronized void fechar() {

        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }

        socket = null;
        out = null;
        in = null;
    }

    private void conectar() throws IOException {

        Socket s = new Socket();

        try {

            s.connect(new InetSocketAddress(ip, porta), TIMEOUT_CONEXAO_MS);
            s.setSoTimeout(TIMEOUT_LEITURA_MS);

        } catch (IOException e) {

            try {
                s.close();
            } catch (IOException ignored) {
            }

            throw new IOException(
                    "Nao foi possivel conectar a " + ip + ":" + porta +
                            " (" + e.getMessage() + ")"
            );
        }

        socket = s;
        out = s.getOutputStream();

        in = new BufferedReader(
                new InputStreamReader(
                        s.getInputStream(),
                        StandardCharsets.UTF_8
                )
        );
    }
}

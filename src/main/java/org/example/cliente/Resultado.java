package org.example.cliente;

/**
 * Resultado de uma chamada a ApiCliente.
 *
 * @param status        status do protocolo ("200", "403"...); "" se a rede falhou
 * @param mensagem      message do servidor (ou o motivo da falha de rede)
 * @param dados         dados da resposta (so quando o status e de sucesso)
 * @param falhaDeRede   nao houve resposta utilizavel do servidor
 * @param sessaoPerdida o servidor respondeu 401 a uma operacao autenticada:
 *                      a sessao foi descartada e a interface deve voltar ao login
 */
public record Resultado<T>(
        String status,
        String mensagem,
        T dados,
        boolean falhaDeRede,
        boolean sessaoPerdida
) {

    static <T> Resultado<T> redeFalhou(String motivo) {
        return new Resultado<>("", motivo, null, true, false);
    }

    public boolean ok() {
        return !falhaDeRede
                && ("200".equals(status) || "201".equals(status));
    }

    /** Texto para mostrar ao usuario. */
    public String texto() {
        return falhaDeRede ? mensagem : "[" + status + "] " + mensagem;
    }
}

package org.example.servidor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro, somente em memoria, de quais tokens possuem ao menos uma
 * conexao TCP viva neste servidor.
 *
 * O token continua gravado no banco (regra 3.2) e continua valido por
 * 30 min sem uso (regra 3.3), mesmo depois que a conexao cai (regras
 * 1.9 e 1.11: o cliente pode reconectar e reenviar com o mesmo token).
 *
 * O que este registro acrescenta: no login, uma sessao ainda dentro da
 * validade mas SEM conexao viva e considerada orfa (o cliente foi
 * encerrado sem logout) e pode ser substituida, em vez de bloquear o
 * dono da conta com 409 ate o token expirar.
 *
 * Como vive em memoria, apos reiniciar o servidor nenhum token tem
 * conexao viva: todas as sessoes antigas viram orfas, o que e correto.
 */
public final class ConexoesAtivas {

    private static final Map<String, Integer> CONTAGEM =
            new ConcurrentHashMap<>();

    private ConexoesAtivas() {
    }

    public static void vincular(String token) {
        if (token != null) {
            CONTAGEM.merge(token, 1, Integer::sum);
        }
    }

    public static void desvincular(String token) {
        if (token != null) {
            CONTAGEM.computeIfPresent(
                    token,
                    (t, n) -> n <= 1 ? null : n - 1
            );
        }
    }

    public static boolean temConexaoViva(String token) {
        return token != null && CONTAGEM.containsKey(token);
    }
}

package org.example.cliente.gui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Chamadas de rede fora da thread da interface (um readLine() bloqueante
 * congelaria a janela).
 *
 * Uma unica thread: o protocolo e uma requisicao por vez numa conexao, e
 * assim a ordem das chamadas e a ordem das respostas sao a mesma.
 *
 * rodar() deve ser chamado na thread da interface; aoConcluir tambem
 * roda nela.
 */
final class Async {

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "rede");
                t.setDaemon(true);
                return t;
            });

    private static final BooleanProperty OCUPADO =
            new SimpleBooleanProperty(false);

    private static int pendentes = 0;

    private Async() {
    }

    /** true enquanto houver chamada em andamento. */
    static BooleanProperty ocupado() {
        return OCUPADO;
    }

    static <T> void rodar(Callable<T> trabalho, Consumer<T> aoConcluir) {

        mudar(+1);

        Task<T> tarefa = new Task<>() {
            @Override
            protected T call() throws Exception {
                return trabalho.call();
            }
        };

        tarefa.setOnSucceeded(e -> {

            mudar(-1);

            aoConcluir.accept(tarefa.getValue());
        });

        tarefa.setOnFailed(e -> {

            mudar(-1);

            Throwable erro = tarefa.getException();

            erro.printStackTrace();

            Dialogos.erro("Erro inesperado: " + erro);
        });

        EXECUTOR.execute(tarefa);
    }

    private static void mudar(int delta) {

        pendentes += delta;

        OCUPADO.set(pendentes > 0);
    }
}

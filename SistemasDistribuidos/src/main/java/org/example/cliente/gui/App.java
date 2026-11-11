package org.example.cliente.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.cliente.ApiCliente;

/** Janela unica: troca entre a tela de login e a tela principal. */
public class App extends Application {

    private final ApiCliente api = new ApiCliente();

    private Stage stage;

    @Override
    public void start(Stage stage) {

        this.stage = stage;

        stage.setTitle("Reserva de Salas");

        /*
         * Qualquer 401 numa operacao autenticada (sessao expirada, conta
         * removida por um admin...) volta para o login. O callback roda na
         * thread de rede, entao passa para a thread da interface.
         */
        api.setAoPerderSessao(() -> Platform.runLater(() -> {

            Dialogos.aviso(
                    "Sua sessão foi encerrada pelo servidor. " +
                            "Faça login novamente."
            );

            mostrarLogin();
        }));

        /*
         * Rede de seguranca: Ctrl+C no terminal nao passa por stop().
         * encerrar() e idempotente.
         */
        Runtime.getRuntime().addShutdownHook(new Thread(api::encerrar));

        mostrarLogin();

        stage.show();
    }

    void mostrarLogin() {
        trocar(new TelaLogin(this, api).raiz(), 480, 400);
    }

    void mostrarPrincipal() {
        trocar(new TelaPrincipal(this, api).raiz(), 1100, 700);
    }

    private void trocar(Parent raiz, double largura, double altura) {

        stage.setScene(new Scene(raiz, largura, altura));

        stage.centerOnScreen();
    }

    /** Janela fechada: logout em conexao propria, para nao deixar sessao presa. */
    @Override
    public void stop() {
        api.encerrar();
    }
}

package org.example.cliente.gui;

import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import org.example.cliente.Resultado;
import org.example.comum.Validador;

import java.util.Optional;

/** Caixas de dialogo simples. Todas devem ser chamadas na thread da interface. */
final class Dialogos {

    private Dialogos() {
    }

    static void erro(String mensagem) {
        alerta(Alert.AlertType.ERROR, "Erro", mensagem);
    }

    static void aviso(String mensagem) {
        alerta(Alert.AlertType.WARNING, "Atenção", mensagem);
    }

    static void info(String mensagem) {
        alerta(Alert.AlertType.INFORMATION, "Informação", mensagem);
    }

    /**
     * Mostra a mensagem do servidor quando uma operacao falhou. Em sessao
     * perdida o App ja avisa e volta ao login, entao nao duplica.
     */
    static void falha(Resultado<?> r) {

        if (!r.sessaoPerdida()) {
            erro(r.texto());
        }
    }

    static boolean confirmar(String pergunta) {

        Alert a = new Alert(
                Alert.AlertType.CONFIRMATION,
                pergunta,
                ButtonType.YES,
                ButtonType.NO
        );

        a.setTitle("Confirmação");
        a.setHeaderText(null);

        return a.showAndWait().filter(b -> b == ButtonType.YES).isPresent();
    }

    /** Pede uma senha valida; vazio se o usuario cancelar. */
    static Optional<String> pedirSenha(String titulo, String texto) {

        Dialog<String> d = new Dialog<>();

        d.setTitle(titulo);

        PasswordField campo = new PasswordField();
        Label erro = new Label();

        erro.setStyle("-fx-text-fill: #b00020;");

        VBox conteudo = new VBox(8, new Label(texto), campo, erro);

        conteudo.setPadding(new Insets(12));

        d.getDialogPane().setContent(conteudo);
        d.getDialogPane().getButtonTypes().addAll(
                ButtonType.OK, ButtonType.CANCEL
        );

        Button ok = (Button) d.getDialogPane().lookupButton(ButtonType.OK);

        ok.addEventFilter(ActionEvent.ACTION, ev -> {

            if (!Validador.password(campo.getText())) {

                erro.setText("Senha inválida: use só letras e números (até 20).");

                ev.consume();
            }
        });

        d.setResultConverter(b -> b == ButtonType.OK ? campo.getText() : null);

        return d.showAndWait();
    }

    private static void alerta(
            Alert.AlertType tipo,
            String titulo,
            String mensagem
    ) {

        Alert a = new Alert(tipo, mensagem, ButtonType.OK);

        a.setTitle(titulo);
        a.setHeaderText(null);

        a.showAndWait();
    }
}

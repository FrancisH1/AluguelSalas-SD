package org.example.cliente.gui;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.example.comum.Validador;

import java.util.Optional;

/**
 * Edicao de usuario: nome, senha e (so para admin) perfil.
 * Campo vazio = "nao alterar" (regra 2.11).
 */
final class FormularioUsuario {

    /** papel: "", "user" ou "admin". Os tres campos vazios nunca acontecem. */
    record Dados(String user, String senha, String papel) {
    }

    private FormularioUsuario() {
    }

    static Optional<Dados> mostrar(
            String titulo,
            String texto,
            boolean comPerfil
    ) {

        Dialog<Dados> d = new Dialog<>();

        d.setTitle(titulo);

        TextField user = new TextField();
        PasswordField senha = new PasswordField();
        ComboBox<String> papel = new ComboBox<>();
        Label erro = new Label();

        user.setPromptText("(manter)");
        senha.setPromptText("(manter)");

        papel.getItems().addAll("(manter)", "user", "admin");
        papel.getSelectionModel().selectFirst();

        erro.setStyle("-fx-text-fill: #b00020;");
        erro.setWrapText(true);

        GridPane g = new GridPane();

        g.setHgap(10);
        g.setVgap(10);
        g.setPadding(new Insets(14));

        g.add(new Label(texto), 0, 0, 2, 1);
        g.addRow(1, new Label("Novo usuário"), user);
        g.addRow(2, new Label("Nova senha"), senha);

        if (comPerfil) {
            g.addRow(3, new Label("Perfil"), papel);
        }

        g.add(erro, 0, 4, 2, 1);

        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(
                ButtonType.OK, ButtonType.CANCEL
        );

        Button ok = (Button) d.getDialogPane().lookupButton(ButtonType.OK);

        ok.addEventFilter(ActionEvent.ACTION, ev -> {

            String u = user.getText().trim();
            String s = senha.getText();
            String p = papelEscolhido(papel, comPerfil);

            String problema = null;

            if (u.isEmpty() && s.isEmpty() && p.isEmpty()) {
                problema = "Preencha pelo menos um campo.";
            } else if (!u.isEmpty() && !Validador.user(u)) {
                problema = "Usuário inválido: só letras minúsculas (até 30).";
            } else if (!s.isEmpty() && !Validador.password(s)) {
                problema = "Senha inválida: só letras e números (até 20).";
            }

            if (problema != null) {

                erro.setText(problema);

                ev.consume();
            }
        });

        d.setResultConverter(b -> b == ButtonType.OK
                ? new Dados(
                user.getText().trim(),
                senha.getText(),
                papelEscolhido(papel, comPerfil))
                : null);

        return d.showAndWait();
    }

    private static String papelEscolhido(ComboBox<String> papel, boolean comPerfil) {

        String v = papel.getValue();

        return (!comPerfil || v == null || v.startsWith("(")) ? "" : v;
    }
}

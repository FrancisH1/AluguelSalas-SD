package org.example.cliente.gui;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.cliente.ApiCliente;
import org.example.comum.Validador;

import java.util.Optional;

/** Servidor, e-mail e senha; e o cadastro de uma conta nova. */
final class TelaLogin {

    private final App app;
    private final ApiCliente api;

    private final TextField ip = new TextField("127.0.0.1");
    private final TextField porta = new TextField();
    private final TextField email = new TextField();
    private final PasswordField senha = new PasswordField();
    private final Label mensagem = new Label();

    private final VBox raiz = new VBox(14);

    TelaLogin(App app, ApiCliente api) {

        this.app = app;
        this.api = api;

        Label titulo = new Label("Reserva de Salas");

        titulo.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        porta.setPromptText("ex.: 12345");
        email.setPromptText("voce@exemplo.com");

        GridPane form = new GridPane();

        form.setHgap(10);
        form.setVgap(10);

        form.addRow(0, new Label("Servidor (IP)"), ip);
        form.addRow(1, new Label("Porta"), porta);
        form.addRow(2, new Label("E-mail"), email);
        form.addRow(3, new Label("Senha"), senha);

        Button entrar = new Button("Entrar");

        entrar.setDefaultButton(true);
        entrar.setOnAction(e -> acaoEntrar());

        Button criarConta = new Button("Criar conta");

        criarConta.setOnAction(e -> acaoCriarConta());

        mensagem.setWrapText(true);
        mensagem.setStyle("-fx-text-fill: #b00020;");

        raiz.setPadding(new Insets(24));
        raiz.getChildren().addAll(
                titulo,
                new Separator(),
                form,
                new HBox(10, entrar, criarConta),
                mensagem
        );

        // enquanto uma chamada esta em andamento, nada pode ser clicado
        raiz.disableProperty().bind(Async.ocupado());
    }

    Parent raiz() {
        return raiz;
    }

    /** Valida IP e porta e configura a conexao. false se invalidos. */
    private boolean lerServidor() {

        String host = ip.getText().trim();

        if (host.isEmpty()) {

            mensagem.setText("Informe o IP do servidor.");

            return false;
        }

        int p;

        try {
            p = Integer.parseInt(porta.getText().trim());
        } catch (NumberFormatException e) {
            p = -1;
        }

        if (p < 1 || p > 65535) {

            mensagem.setText("Porta inválida (1 a 65535).");

            return false;
        }

        api.configurar(host, p);

        return true;
    }

    private void acaoEntrar() {

        mensagem.setText("");

        if (!lerServidor()) {
            return;
        }

        String e = email.getText().trim();
        String s = senha.getText();

        if (!Validador.email(e)) {

            mensagem.setText("E-mail inválido (letras minúsculas, números, . _ -).");

            return;
        }

        if (!Validador.password(s)) {

            mensagem.setText("Senha inválida: use só letras e números (até 20).");

            return;
        }

        Async.rodar(() -> api.login(e, s), r -> {

            if (r.ok()) {

                app.mostrarPrincipal();

            } else {

                mensagem.setText(r.texto());
            }
        });
    }

    private void acaoCriarConta() {

        mensagem.setText("");

        if (!lerServidor()) {
            return;
        }

        Optional<String[]> dados = dialogoCadastro();

        if (dados.isEmpty()) {
            return;
        }

        String[] d = dados.get();

        Async.rodar(() -> api.registrar(d[0], d[1], d[2]), r -> {

            if (r.ok()) {

                email.setText(d[0]);
                senha.clear();

                Dialogos.info("Conta criada! Agora é só entrar.");

            } else {

                mensagem.setText(r.texto());
            }
        });
    }

    /** E-mail, usuario e senha (duas vezes). Retorna {email, user, senha}. */
    private Optional<String[]> dialogoCadastro() {

        Dialog<String[]> d = new Dialog<>();

        d.setTitle("Criar conta");

        TextField e = new TextField();
        TextField u = new TextField();
        PasswordField s1 = new PasswordField();
        PasswordField s2 = new PasswordField();
        Label erro = new Label();

        e.setPromptText("voce@exemplo.com");
        u.setPromptText("só letras minúsculas");
        erro.setStyle("-fx-text-fill: #b00020;");
        erro.setWrapText(true);

        GridPane g = new GridPane();

        g.setHgap(10);
        g.setVgap(10);
        g.setPadding(new Insets(14));

        g.addRow(0, new Label("E-mail"), e);
        g.addRow(1, new Label("Usuário"), u);
        g.addRow(2, new Label("Senha"), s1);
        g.addRow(3, new Label("Repita a senha"), s2);
        g.add(erro, 0, 4, 2, 1);

        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(
                ButtonType.OK, ButtonType.CANCEL
        );

        Button ok = (Button) d.getDialogPane().lookupButton(ButtonType.OK);

        ok.addEventFilter(ActionEvent.ACTION, ev -> {

            String problema = null;

            if (!Validador.email(e.getText().trim())) {
                problema = "E-mail inválido.";
            } else if (!Validador.user(u.getText().trim())) {
                problema = "Usuário inválido: só letras minúsculas (até 30).";
            } else if (!Validador.password(s1.getText())) {
                problema = "Senha inválida: só letras e números (até 20).";
            } else if (!s1.getText().equals(s2.getText())) {
                problema = "As senhas não conferem.";
            }

            if (problema != null) {

                erro.setText(problema);

                ev.consume();
            }
        });

        d.setResultConverter(b -> b == ButtonType.OK
                ? new String[]{
                e.getText().trim(), u.getText().trim(), s1.getText()}
                : null);

        return d.showAndWait();
    }
}

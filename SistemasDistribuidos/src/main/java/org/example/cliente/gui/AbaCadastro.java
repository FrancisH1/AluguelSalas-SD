package org.example.cliente.gui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.cliente.ApiCliente;
import org.example.cliente.Modelos;

import java.util.Optional;
import java.util.function.Consumer;

/** Ver, editar e excluir o proprio cadastro. */
final class AbaCadastro implements Recarregavel {

    private final App app;
    private final ApiCliente api;
    private final Consumer<String> status;
    private final Runnable aoMudar;

    private final VBox raiz = new VBox(18);

    private final Label user = new Label("-");
    private final Label email = new Label("-");
    private final Label papel = new Label("-");
    private final Label criado = new Label("-");

    AbaCadastro(
            App app,
            ApiCliente api,
            Consumer<String> status,
            Runnable aoMudar
    ) {

        this.app = app;
        this.api = api;
        this.status = status;
        this.aoMudar = aoMudar;

        GridPane dados = new GridPane();

        dados.setHgap(14);
        dados.setVgap(8);

        dados.addRow(0, negrito("Usuário"), user);
        dados.addRow(1, negrito("E-mail"), email);
        dados.addRow(2, negrito("Perfil"), papel);
        dados.addRow(3, negrito("Criado em"), criado);

        Button editar = new Button("Editar cadastro");

        editar.setOnAction(e -> acaoEditar());

        Button excluir = new Button("Excluir minha conta");

        excluir.setOnAction(e -> acaoExcluir());

        Button atualizar = new Button("Atualizar");

        atualizar.setOnAction(e -> recarregar());

        raiz.setPadding(new Insets(20));
        raiz.getChildren().addAll(dados, new HBox(10, editar, excluir, atualizar));
    }

    Node raiz() {
        return raiz;
    }

    @Override
    public void recarregar() {

        Async.rodar(api::meuCadastro, r -> {

            if (!r.ok()) {

                status.accept(r.texto());

                return;
            }

            Modelos.Usuario u = r.dados();

            user.setText(u.user());
            email.setText(u.email());
            papel.setText(u.role());
            criado.setText(u.criadoEm());

            status.accept("Cadastro carregado.");
        });
    }

    private void acaoEditar() {

        Optional<FormularioUsuario.Dados> dados = FormularioUsuario.mostrar(
                "Editar meu cadastro",
                "Deixe em branco o que não quer alterar.",
                false
        );

        if (dados.isEmpty()) {
            return;
        }

        FormularioUsuario.Dados d = dados.get();

        Async.rodar(() -> api.atualizarMeuCadastro(d.user(), d.senha()), r -> {

            status.accept(r.texto());

            if (r.ok()) {

                recarregar();

                aoMudar.run();

            } else {

                Dialogos.falha(r);
            }
        });
    }

    private void acaoExcluir() {

        if (!Dialogos.confirmar(
                "Excluir definitivamente a sua conta e todas as suas reservas?")) {
            return;
        }

        Optional<String> senha = Dialogos.pedirSenha(
                "Confirmar exclusão", "Digite a sua senha para confirmar:");

        if (senha.isEmpty()) {
            return;
        }

        Async.rodar(() -> api.deletarMeuCadastro(senha.get()), r -> {

            if (r.ok()) {

                Dialogos.info("Sua conta foi removida.");

                app.mostrarLogin();

            } else {

                status.accept(r.texto());

                Dialogos.falha(r);   // 403: senha incorreta, a sessão continua
            }
        });
    }

    private static Label negrito(String texto) {

        Label l = new Label(texto);

        l.setStyle("-fx-font-weight: bold;");

        return l;
    }
}

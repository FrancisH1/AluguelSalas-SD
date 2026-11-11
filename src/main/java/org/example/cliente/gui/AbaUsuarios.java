package org.example.cliente.gui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.example.cliente.ApiCliente;
import org.example.cliente.Modelos.Usuario;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/** Administrador: lista, consulta, edita e remove qualquer usuario. */
final class AbaUsuarios implements Recarregavel {

    private final ApiCliente api;
    private final Consumer<String> status;
    private final Runnable aoMudarPerfil;

    private final VBox raiz = new VBox(10);
    private final TableView<Usuario> tabela = new TableView<>();

    AbaUsuarios(ApiCliente api, Consumer<String> status, Runnable aoMudarPerfil) {

        this.api = api;
        this.status = status;
        this.aoMudarPerfil = aoMudarPerfil;

        tabela.getColumns().add(coluna("Usuário", Usuario::user, 200));
        tabela.getColumns().add(coluna("E-mail", Usuario::email, 330));
        tabela.getColumns().add(coluna("Perfil", Usuario::role, 100));
        tabela.setPlaceholder(new Label("Nenhum usuário carregado."));

        VBox.setVgrow(tabela, Priority.ALWAYS);

        Button atualizar = new Button("Atualizar lista");
        Button detalhes = new Button("Ver detalhes");
        Button editar = new Button("Editar");
        Button remover = new Button("Remover");

        atualizar.setOnAction(e -> recarregar());
        detalhes.setOnAction(e -> acaoDetalhes());
        editar.setOnAction(e -> acaoEditar());
        remover.setOnAction(e -> acaoRemover());

        raiz.setPadding(new Insets(14));
        raiz.getChildren().addAll(
                tabela, new HBox(10, atualizar, detalhes, editar, remover));
    }

    Node raiz() {
        return raiz;
    }

    @Override
    public void recarregar() {

        Async.rodar(api::listarUsuarios, r -> {

            if (!r.ok()) {

                status.accept(r.texto());

                Dialogos.falha(r);

                return;
            }

            tabela.setItems(FXCollections.observableArrayList(r.dados()));

            status.accept(r.dados().size() + " usuário(s).");
        });
    }

    private Usuario selecionado() {

        Usuario u = tabela.getSelectionModel().getSelectedItem();

        if (u == null) {
            Dialogos.aviso("Selecione um usuário na lista.");
        }

        return u;
    }

    private void acaoDetalhes() {

        Usuario alvo = selecionado();

        if (alvo == null) {
            return;
        }

        Async.rodar(() -> api.lerUsuario(alvo.user()), r -> {

            if (!r.ok()) {

                status.accept(r.texto());

                Dialogos.falha(r);

                return;
            }

            Usuario u = r.dados();

            Dialogos.info(
                    "Usuário: " + u.user() + "\n" +
                            "E-mail: " + u.email() + "\n" +
                            "Perfil: " + u.role() + "\n" +
                            "Criado em: " + u.criadoEm()
            );
        });
    }

    private void acaoEditar() {

        Usuario alvo = selecionado();

        if (alvo == null) {
            return;
        }

        Optional<FormularioUsuario.Dados> dados = FormularioUsuario.mostrar(
                "Editar " + alvo.user(),
                "Deixe em branco o que não quer alterar.",
                true
        );

        if (dados.isEmpty()) {
            return;
        }

        FormularioUsuario.Dados d = dados.get();

        Async.rodar(
                () -> api.atualizarUsuario(
                        alvo.user(), d.user(), d.senha(), d.papel()),
                r -> {

                    status.accept(r.texto());

                    if (r.ok()) {

                        recarregar();

                        // pode ter sido o proprio admin que mudou de perfil
                        aoMudarPerfil.run();

                    } else {

                        Dialogos.falha(r);
                    }
                }
        );
    }

    private void acaoRemover() {

        Usuario alvo = selecionado();

        if (alvo == null) {
            return;
        }

        if (!Dialogos.confirmar(
                "Remover o usuário '" + alvo.user() +
                        "' e todas as reservas dele?")) {
            return;
        }

        Async.rodar(() -> api.removerUsuario(alvo.user()), r -> {

            status.accept(r.texto());

            if (r.ok()) {
                recarregar();
            } else {
                Dialogos.falha(r);
            }
        });
    }

    private static TableColumn<Usuario, String> coluna(
            String titulo,
            Function<Usuario, String> valor,
            double largura
    ) {

        TableColumn<Usuario, String> c = new TableColumn<>(titulo);

        c.setCellValueFactory(
                cd -> new SimpleStringProperty(valor.apply(cd.getValue())));

        c.setPrefWidth(largura);

        return c;
    }
}

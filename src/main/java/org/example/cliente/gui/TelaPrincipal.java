package org.example.cliente.gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.example.cliente.ApiCliente;

import java.util.HashMap;
import java.util.Map;

/** Cabecalho com o usuario e o botao Sair, abas no centro, status embaixo. */
final class TelaPrincipal {

    private final App app;
    private final ApiCliente api;

    private final BorderPane raiz = new BorderPane();
    private final Label usuario = new Label();
    private final Label status = new Label("Pronto.");

    private final Map<Tab, Recarregavel> recarregaveis = new HashMap<>();

    TelaPrincipal(App app, ApiCliente api) {

        this.app = app;
        this.api = api;

        // ---------- topo ----------
        Label titulo = new Label("Reserva de Salas");

        titulo.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Button sair = new Button("Sair");

        sair.setOnAction(e -> acaoSair());

        Region espacoTopo = new Region();

        HBox.setHgrow(espacoTopo, Priority.ALWAYS);

        HBox topo = new HBox(12, titulo, espacoTopo, usuario, sair);

        topo.setPadding(new Insets(10, 14, 10, 14));
        topo.setAlignment(Pos.CENTER_LEFT);

        // ---------- abas ----------
        TabPane abas = new TabPane();

        abas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        AbaCadastro cadastro =
                new AbaCadastro(app, api, this::mostrarStatus, this::atualizarPerfil);

        adicionar(abas, "Meu cadastro", cadastro.raiz(), cadastro);

        AbaSalas salas = new AbaSalas(api, this::mostrarStatus);

        adicionar(abas, "Salas", salas.raiz(), salas);

        /*
         * Aba de admin so e montada para quem logou como admin. E so
         * conveniencia de interface: o servidor recusa com 403 de qualquer jeito.
         */
        if (api.admin()) {

            AbaUsuarios usuarios =
                    new AbaUsuarios(api, this::mostrarStatus, this::atualizarPerfil);

            adicionar(abas, "Usuários (admin)", usuarios.raiz(), usuarios);
        }

        abas.getSelectionModel().selectedItemProperty().addListener(
                (obs, antiga, nova) -> {

                    Recarregavel r = recarregaveis.get(nova);

                    if (r != null) {
                        r.recarregar();
                    }
                }
        );

        // ---------- rodape ----------
        ProgressIndicator progresso = new ProgressIndicator();

        progresso.setPrefSize(18, 18);
        progresso.visibleProperty().bind(Async.ocupado());

        Region espacoRodape = new Region();

        HBox.setHgrow(espacoRodape, Priority.ALWAYS);

        HBox rodape = new HBox(10, status, espacoRodape, progresso);

        rodape.setPadding(new Insets(6, 14, 6, 14));
        rodape.setAlignment(Pos.CENTER_LEFT);

        raiz.setTop(topo);
        raiz.setCenter(abas);
        raiz.setBottom(rodape);

        cadastro.recarregar();   // a primeira aba ja nasce selecionada
        atualizarPerfil();
    }

    Parent raiz() {
        return raiz;
    }

    private void adicionar(
            TabPane abas,
            String titulo,
            Node conteudo,
            Recarregavel aba
    ) {

        Tab tab = new Tab(titulo, conteudo);

        recarregaveis.put(tab, aba);

        abas.getTabs().add(tab);
    }

    private void mostrarStatus(String texto) {
        status.setText(texto);
    }

    /**
     * Rele o proprio cadastro: atualiza o nome no topo e o role guardado.
     * Se o role mudou (um admin se rebaixou, ou foi promovido), as abas
     * precisam ser refeitas.
     */
    private void atualizarPerfil() {

        String antes = api.role();

        Async.rodar(api::meuCadastro, r -> {

            if (!r.ok()) {
                return;
            }

            if (!antes.equals(api.role())) {

                app.mostrarPrincipal();

                return;
            }

            usuario.setText(r.dados().user() + " (" + api.role() + ")");
        });
    }

    private void acaoSair() {

        Async.rodar(api::logout, r -> {

            if (!api.logado()) {

                app.mostrarLogin();

            } else if (Dialogos.confirmar(
                    r.texto() + "\n\nNão foi possível confirmar o logout " +
                            "no servidor. Sair mesmo assim?")) {

                Async.rodar(() -> {
                    api.encerrar();
                    return null;
                }, x -> app.mostrarLogin());
            }
        });
    }
}

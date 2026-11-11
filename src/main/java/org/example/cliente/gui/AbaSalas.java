package org.example.cliente.gui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.example.cliente.ApiCliente;
import org.example.cliente.Modelos.DetalheSala;
import org.example.cliente.Modelos.FiltroSalas;
import org.example.cliente.Modelos.ItemAgenda;
import org.example.cliente.Modelos.NovaSala;
import org.example.cliente.Modelos.Sala;
import org.example.cliente.Resultado;
import org.example.comum.Horarios;
import org.example.comum.Validador;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Lista de salas (com filtro de capacidade e de disponibilidade num
 * horario), dados da sala selecionada e agenda por data. Administrador
 * ganha os botoes de cadastrar, editar e remover.
 */
final class AbaSalas implements Recarregavel {

    private static final String[] DIAS = {
            "", "", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado"
    };

    private record LinhaAgenda(String data, ItemAgenda item) {
    }

    private record AgendaCarregada(Resultado<?> erro, List<LinhaAgenda> linhas) {
    }

    private final ApiCliente api;
    private final Consumer<String> status;

    private final VBox raiz = new VBox(10);

    // filtros
    private final DatePicker filtroData = new DatePicker();
    private final ComboBox<String> filtroInicio = new ComboBox<>();
    private final ComboBox<String> filtroFim = new ComboBox<>();
    private final TextField filtroCapacidade = new TextField();

    // salas
    private final TableView<Sala> tabela = new TableView<>();
    private final Label detalhes = new Label("Selecione uma sala.");

    // agenda
    private final DatePicker dataAgenda = new DatePicker(LocalDate.now());
    private final TableView<LinhaAgenda> tabelaAgenda = new TableView<>();

    AbaSalas(ApiCliente api, Consumer<String> status) {

        this.api = api;
        this.status = status;

        // ---------- filtros ----------
        for (Horarios.Aula a : Horarios.AULAS) {

            filtroInicio.getItems().add(a.inicio());
            filtroFim.getItems().add(a.fim());
        }

        filtroInicio.setPromptText("início");
        filtroFim.setPromptText("fim");
        filtroData.setPromptText("data");
        filtroCapacidade.setPromptText("capacidade mín.");
        filtroCapacidade.setPrefWidth(120);

        Button buscar = new Button("Buscar");
        Button limpar = new Button("Limpar filtros");

        buscar.setOnAction(e -> recarregar());
        limpar.setOnAction(e -> limparFiltros());

        HBox filtros = new HBox(8,
                new Label("Disponível em:"), filtroData, filtroInicio,
                new Label("até"), filtroFim, filtroCapacidade, buscar, limpar);

        // ---------- tabela de salas ----------
        tabela.getColumns().add(colunaSala("ID", Sala::id, 45));
        tabela.getColumns().add(colunaSala("Nome", Sala::nome, 150));
        tabela.getColumns().add(colunaSala("Cap.",
                s -> String.valueOf(s.capacidade()), 55));
        tabela.getColumns().add(colunaSala("Local", Sala::local, 170));
        tabela.getColumns().add(colunaSala("Estado",
                s -> s.ativa() ? "Ativa" : "Inativa", 70));
        tabela.getColumns().add(colunaSala("Recursos",
                s -> String.join(", ", s.recursos()), 190));
        tabela.getColumns().add(colunaSala("Disponível", s ->
                s.disponivel() == null ? "—"
                        : (s.disponivel() ? "Sim" : "Não"), 80));
        tabela.setPlaceholder(new Label("Nenhuma sala encontrada."));
        tabela.setPrefHeight(230);

        tabela.getSelectionModel().selectedItemProperty().addListener(
                (obs, antiga, nova) -> {

                    mostrarDetalhes(nova);

                    if (nova != null) {
                        carregarAgenda(1);
                    } else {
                        tabelaAgenda.getItems().clear();
                    }
                });

        // ---------- botoes de admin ----------
        HBox botoes = new HBox(8);

        if (api.admin()) {

            Button nova = new Button("Nova sala");
            Button editar = new Button("Editar sala");
            Button remover = new Button("Remover sala");

            nova.setOnAction(e -> acaoNova());
            editar.setOnAction(e -> acaoEditar());
            remover.setOnAction(e -> acaoRemover());

            botoes.getChildren().addAll(nova, editar, remover);
        }

        // ---------- detalhes + agenda ----------
        detalhes.setWrapText(true);
        detalhes.setPrefWidth(300);
        detalhes.setMinWidth(300);
        detalhes.setStyle("-fx-alignment: top-left;");

        tabelaAgenda.getColumns().add(colunaAgenda("Data", l -> l.data(), 90));
        tabelaAgenda.getColumns().add(colunaAgenda("Horário",
                l -> l.item().slot(), 60));
        tabelaAgenda.getColumns().add(colunaAgenda("Início",
                l -> l.item().inicio(), 60));
        tabelaAgenda.getColumns().add(colunaAgenda("Fim",
                l -> l.item().fim(), 60));
        tabelaAgenda.getColumns().add(colunaAgenda("Estado",
                l -> estado(l.item()), 100));
        tabelaAgenda.getColumns().add(colunaAgenda("Reservado por",
                l -> l.item().usuario() == null ? "" : l.item().usuario(), 130));
        tabelaAgenda.getColumns().add(colunaAgenda("Reserva",
                l -> l.item().reservaId() == null ? ""
                        : "#" + l.item().reservaId(), 70));
        tabelaAgenda.setPlaceholder(new Label(
                "Sem horários neste dia (a sala não funciona nele)."));

        Button verAgenda = new Button("Ver agenda do dia");
        Button verSemana = new Button("Próximos 7 dias");

        verAgenda.setOnAction(e -> carregarAgenda(1));
        verSemana.setOnAction(e -> carregarAgenda(7));

        VBox direita = new VBox(8,
                new HBox(8, new Label("Agenda a partir de:"), dataAgenda,
                        verAgenda, verSemana),
                tabelaAgenda);

        VBox.setVgrow(tabelaAgenda, Priority.ALWAYS);
        HBox.setHgrow(direita, Priority.ALWAYS);

        HBox inferior = new HBox(14, detalhes, direita);

        VBox.setVgrow(inferior, Priority.ALWAYS);

        raiz.setPadding(new Insets(14));
        raiz.getChildren().addAll(filtros, tabela, botoes, inferior);
    }

    Node raiz() {
        return raiz;
    }

    // =========================================================
    // LISTA
    // =========================================================

    @Override
    public void recarregar() {

        FiltroSalas filtro = lerFiltro();

        if (filtro == null) {
            return;   // ja mostrou o motivo
        }

        Async.rodar(() -> api.listarSalas(filtro), r -> {

            if (!r.ok()) {

                status.accept(r.texto());

                Dialogos.falha(r);

                return;
            }

            tabela.setItems(FXCollections.observableArrayList(r.dados()));

            status.accept(r.dados().size() + " sala(s).");
        });
    }

    private void limparFiltros() {

        filtroData.setValue(null);
        filtroInicio.setValue(null);
        filtroFim.setValue(null);
        filtroCapacidade.clear();

        recarregar();
    }

    /** null = filtro invalido (a mensagem ja foi mostrada). */
    private FiltroSalas lerFiltro() {

        LocalDate data = filtroData.getValue();
        String inicio = filtroInicio.getValue();
        String fim = filtroFim.getValue();
        String minimo = filtroCapacidade.getText().trim();

        boolean comHorario = data != null || inicio != null || fim != null;

        if (comHorario && (data == null || inicio == null || fim == null)) {

            Dialogos.aviso(
                    "Para filtrar por disponibilidade, informe data, início e fim.");

            return null;
        }

        if (comHorario && Horarios.aulasEntre(inicio, fim) == null) {

            Dialogos.aviso(
                    "Horário inválido: o fim deve ser depois do início.");

            return null;
        }

        if (!minimo.isEmpty() && !Validador.capacidade(minimo)) {

            Dialogos.aviso("Capacidade mínima inválida (1 a 9999).");

            return null;
        }

        return new FiltroSalas(
                comHorario ? data.toString() : null,
                comHorario ? inicio : null,
                comHorario ? fim : null,
                minimo.isEmpty() ? null : minimo
        );
    }

    // =========================================================
    // DETALHES E AGENDA
    // =========================================================

    private void mostrarDetalhes(Sala s) {

        if (s == null) {

            detalhes.setText("Selecione uma sala.");

            return;
        }

        StringBuilder t = new StringBuilder();

        t.append("Sala ").append(s.id()).append(" — ").append(s.nome()).append('\n');
        t.append("Capacidade: ").append(s.capacidade()).append('\n');
        t.append("Local: ").append(s.local()).append('\n');
        t.append("Estado: ").append(s.ativa()
                ? "ativa (livre para reservas)" : "inativa (indisponível)")
                .append('\n');
        t.append("Recursos: ").append(s.recursos().isEmpty()
                ? "(nenhum)" : String.join(", ", s.recursos())).append("\n\n");
        t.append("Funcionamento:\n");

        for (int dia = 2; dia <= 7; dia++) {

            StringBuilder aulas = new StringBuilder();

            for (Horarios.Aula a : Horarios.AULAS) {

                if (s.slots().contains(dia + a.codigo())) {
                    aulas.append(a.codigo()).append(' ');
                }
            }

            if (aulas.length() > 0) {
                t.append("  ").append(DIAS[dia]).append(": ")
                        .append(aulas.toString().trim()).append('\n');
            }
        }

        detalhes.setText(t.toString());
    }

    /** dias = 1: so a data escolhida; 7: ela e os 6 dias seguintes. */
    private void carregarAgenda(int dias) {

        Sala sala = tabela.getSelectionModel().getSelectedItem();

        if (sala == null) {

            Dialogos.aviso("Selecione uma sala na lista.");

            return;
        }

        LocalDate base = dataAgenda.getValue() == null
                ? LocalDate.now() : dataAgenda.getValue();

        Async.rodar(() -> {

            List<LinhaAgenda> linhas = new ArrayList<>();

            for (int i = 0; i < dias; i++) {

                String data = base.plusDays(i).toString();

                Resultado<DetalheSala> r = api.lerSala(sala.id(), data);

                if (!r.ok()) {
                    return new AgendaCarregada(r, linhas);
                }

                for (ItemAgenda item : r.dados().agenda()) {
                    linhas.add(new LinhaAgenda(data, item));
                }
            }

            return new AgendaCarregada(null, linhas);

        }, a -> {

            if (a.erro() != null) {

                status.accept(a.erro().texto());

                Dialogos.falha(a.erro());

                return;
            }

            tabelaAgenda.setItems(FXCollections.observableArrayList(a.linhas()));

            status.accept("Agenda da sala " + sala.nome() + " carregada.");
        });
    }

    private static String estado(ItemAgenda i) {

        if (i.livre()) {
            return "Livre";
        }

        return i.usuario() != null ? "Reservada" : "Indisponível";
    }

    // =========================================================
    // ADMIN
    // =========================================================

    private void acaoNova() {

        Optional<FormularioSala.Dados> dados = FormularioSala.mostrar(null);

        if (dados.isEmpty()) {
            return;
        }

        FormularioSala.Dados d = dados.get();

        NovaSala nova = new NovaSala(
                d.nome(), d.capacidade(), d.local(), d.recursos(), d.slots());

        Async.rodar(() -> api.criarSala(nova), r -> {

            status.accept(r.texto());

            if (r.ok()) {

                Dialogos.info("Sala criada (ID " + r.dados() + ").");

                recarregar();

            } else {

                Dialogos.falha(r);
            }
        });
    }

    private void acaoEditar() {

        Sala sala = tabela.getSelectionModel().getSelectedItem();

        if (sala == null) {

            Dialogos.aviso("Selecione uma sala na lista.");

            return;
        }

        Optional<FormularioSala.Dados> dados = FormularioSala.mostrar(sala);

        if (dados.isEmpty()) {
            return;
        }

        FormularioSala.Dados d = dados.get();

        /*
         * Envia todos os campos (os que nao mudaram vao com o valor atual,
         * o que o servidor aceita). recursos e a lista completa.
         */
        Async.rodar(
                () -> api.atualizarSala(
                        sala.id(), d.nome(), d.capacidade(), d.local(),
                        d.recursos(), d.estado(), d.slots()),
                r -> {

                    status.accept(r.texto());

                    if (r.ok()) {
                        recarregar();
                    } else {
                        Dialogos.falha(r);
                    }
                }
        );
    }

    private void acaoRemover() {

        Sala sala = tabela.getSelectionModel().getSelectedItem();

        if (sala == null) {

            Dialogos.aviso("Selecione uma sala na lista.");

            return;
        }

        if (!Dialogos.confirmar("Remover a sala '" + sala.nome() + "'?")) {
            return;
        }

        Async.rodar(() -> api.removerSala(sala.id()), r -> {

            status.accept(r.texto());

            if (r.ok()) {
                recarregar();
            } else {
                Dialogos.falha(r);   // 409: a sala tem reservas futuras
            }
        });
    }

    // =========================================================
    // COLUNAS
    // =========================================================

    private static TableColumn<Sala, String> colunaSala(
            String titulo,
            Function<Sala, String> valor,
            double largura
    ) {

        TableColumn<Sala, String> c = new TableColumn<>(titulo);

        c.setCellValueFactory(
                cd -> new SimpleStringProperty(valor.apply(cd.getValue())));

        c.setPrefWidth(largura);

        return c;
    }

    private static TableColumn<LinhaAgenda, String> colunaAgenda(
            String titulo,
            Function<LinhaAgenda, String> valor,
            double largura
    ) {

        TableColumn<LinhaAgenda, String> c = new TableColumn<>(titulo);

        c.setCellValueFactory(
                cd -> new SimpleStringProperty(valor.apply(cd.getValue())));

        c.setPrefWidth(largura);

        return c;
    }
}

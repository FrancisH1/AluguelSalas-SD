package org.example.cliente.gui;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.example.cliente.Modelos.Sala;
import org.example.comum.Validador;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Cadastro (sala == null) e edicao de sala. */
final class FormularioSala {

    /** estado: "active" ou "inactive" (so faz sentido na edicao). */
    record Dados(
            String nome,
            String capacidade,
            String local,
            List<String> recursos,
            String estado,
            List<String> slots
    ) {
    }

    private FormularioSala() {
    }

    static Optional<Dados> mostrar(Sala existente) {

        boolean edicao = existente != null;

        Dialog<Dados> d = new Dialog<>();

        d.setTitle(edicao ? "Editar sala " + existente.nome() : "Nova sala");
        d.setResizable(true);

        TextField nome = new TextField();
        TextField capacidade = new TextField();
        TextField local = new TextField();
        TextField recursos = new TextField();
        ComboBox<String> estado = new ComboBox<>();
        GradeHorarios grade = new GradeHorarios();
        Label erro = new Label();

        nome.setPromptText("letras, números, espaço e hífen");
        capacidade.setPromptText("1 a 9999");
        local.setPromptText("ex.: Bloco B, 1º andar");
        recursos.setPromptText("separados por vírgula; digite 0 para remover todos");

        estado.getItems().addAll(
                "Ativa (livre para reservas)", "Inativa (indisponível)");
        estado.getSelectionModel().selectFirst();

        erro.setStyle("-fx-text-fill: #b00020;");
        erro.setWrapText(true);

        if (edicao) {

            nome.setText(existente.nome());
            capacidade.setText(String.valueOf(existente.capacidade()));
            local.setText(existente.local());
            recursos.setText(String.join(", ", existente.recursos()));
            estado.getSelectionModel().select(existente.ativa() ? 0 : 1);
            grade.definir(existente.slots());
        }

        GridPane campos = new GridPane();

        campos.setHgap(10);
        campos.setVgap(10);

        campos.addRow(0, new Label("Nome"), nome);
        campos.addRow(1, new Label("Capacidade"), capacidade);
        campos.addRow(2, new Label("Localização"), local);
        campos.addRow(3, new Label("Recursos"), recursos);

        if (edicao) {
            campos.addRow(4, new Label("Estado"), estado);
        }

        Label dica = new Label(
                "Horários de funcionamento (clique no dia ou na aula " +
                        "para marcar a coluna ou a linha inteira):");

        dica.setWrapText(true);

        VBox conteudo = new VBox(12, campos, dica, grade, erro);

        conteudo.setPadding(new Insets(14));

        ScrollPane rolagem = new ScrollPane(conteudo);

        rolagem.setPrefViewportHeight(560);
        rolagem.setPrefViewportWidth(620);

        d.getDialogPane().setContent(rolagem);
        d.getDialogPane().getButtonTypes().addAll(
                ButtonType.OK, ButtonType.CANCEL);

        Button ok = (Button) d.getDialogPane().lookupButton(ButtonType.OK);

        ok.addEventFilter(ActionEvent.ACTION, ev -> {

            String problema = null;

            if (!Validador.nomeSala(nome.getText().trim())) {
                problema = "Nome inválido: letras, números, espaço e hífen (até 30).";
            } else if (!Validador.capacidade(capacidade.getText().trim())) {
                problema = "Capacidade inválida: número de 1 a 9999.";
            } else if (!Validador.local(local.getText().trim())) {
                problema = "Localização inválida: letras, números, espaço e , . - (até 60).";
            } else if (grade.selecionados().isEmpty()) {
                problema = "Marque pelo menos um horário de funcionamento.";
            }

            if (problema != null) {

                erro.setText(problema);

                ev.consume();
            }
        });

        d.setResultConverter(b -> {

            if (b != ButtonType.OK) {
                return null;
            }

            List<String> lista = new ArrayList<>();

            for (String r : recursos.getText().split(",")) {

                if (!r.trim().isEmpty()) {
                    lista.add(r.trim());
                }
            }

            return new Dados(
                    nome.getText().trim(),
                    capacidade.getText().trim(),
                    local.getText().trim(),
                    lista,
                    estado.getSelectionModel().getSelectedIndex() == 1
                            ? "inactive" : "active",
                    grade.selecionados()
            );
        });

        return d.showAndWait();
    }
}

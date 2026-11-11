package org.example.cliente.gui;

import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.GridPane;
import org.example.comum.Horarios;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Grade de horarios da UTFPR: colunas = dias (segunda a sabado), linhas =
 * aulas (M1...N5). Cada celula e um botao de liga/desliga com o codigo
 * (2M1, 3T2...). Clicar no nome do dia ou da aula alterna a coluna ou a
 * linha inteira.
 */
final class GradeHorarios extends GridPane {

    private static final String[] DIAS = {
            "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"
    };

    private final Map<String, ToggleButton> botoes = new LinkedHashMap<>();

    GradeHorarios() {

        setHgap(4);
        setVgap(4);

        // cabecalho: um botao por dia (dia 2 = segunda ... 7 = sabado)
        for (int c = 0; c < DIAS.length; c++) {

            int dia = c + 2;

            Button cabecalho = new Button(DIAS[c]);

            cabecalho.setMaxWidth(Double.MAX_VALUE);
            cabecalho.setOnAction(e -> alternar(codigosDoDia(dia)));

            add(cabecalho, c + 1, 0);
        }

        for (int r = 0; r < Horarios.AULAS.size(); r++) {

            Horarios.Aula aula = Horarios.AULAS.get(r);

            Button rotulo = new Button(
                    aula.codigo() + "  " + aula.inicio() + "–" + aula.fim());

            rotulo.setMaxWidth(Double.MAX_VALUE);
            rotulo.setOnAction(e -> alternar(codigosDaAula(aula)));

            add(rotulo, 0, r + 1);

            for (int c = 0; c < DIAS.length; c++) {

                String codigo = (c + 2) + aula.codigo();

                ToggleButton b = new ToggleButton(codigo);

                b.setPrefWidth(58);

                botoes.put(codigo, b);

                add(b, c + 1, r + 1);
            }
        }
    }

    /** Selecionados, por dia e depois por aula. */
    List<String> selecionados() {

        List<String> r = new ArrayList<>();

        for (int dia = 2; dia <= 7; dia++) {

            for (String codigo : codigosDoDia(dia)) {

                if (botoes.get(codigo).isSelected()) {
                    r.add(codigo);
                }
            }
        }

        return r;
    }

    void definir(Collection<String> codigos) {

        for (Map.Entry<String, ToggleButton> e : botoes.entrySet()) {
            e.getValue().setSelected(codigos.contains(e.getKey()));
        }
    }

    private List<String> codigosDoDia(int dia) {

        List<String> r = new ArrayList<>();

        for (Horarios.Aula a : Horarios.AULAS) {
            r.add(dia + a.codigo());
        }

        return r;
    }

    private List<String> codigosDaAula(Horarios.Aula aula) {

        List<String> r = new ArrayList<>();

        for (int dia = 2; dia <= 7; dia++) {
            r.add(dia + aula.codigo());
        }

        return r;
    }

    /** Se todos ja estao ligados, desliga; senao liga todos. */
    private void alternar(List<String> codigos) {

        boolean todos = true;

        for (String c : codigos) {
            todos &= botoes.get(c).isSelected();
        }

        for (String c : codigos) {
            botoes.get(c).setSelected(!todos);
        }
    }
}

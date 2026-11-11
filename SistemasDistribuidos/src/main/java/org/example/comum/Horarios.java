package org.example.comum;

import java.time.LocalDate;
import java.util.List;

/**
 * Tabela de aulas da UTFPR (protocolo, secao 8).
 *
 * Codigo de horario = dia + turno + aula. Ex.: 2M1 = segunda, manha,
 * primeira aula (07:30-08:20). Dia: 2 segunda ... 7 sabado.
 */
public final class Horarios {

    private Horarios() {
    }

    public record Aula(String codigo, String inicio, String fim) {
    }

    /** Em ordem cronologica. O indice na lista e a ordem da aula no dia. */
    public static final List<Aula> AULAS = List.of(
            new Aula("M1", "07:30", "08:20"),
            new Aula("M2", "08:20", "09:10"),
            new Aula("M3", "09:10", "10:00"),
            new Aula("M4", "10:20", "11:10"),
            new Aula("M5", "11:10", "12:00"),
            new Aula("M6", "12:00", "12:50"),
            new Aula("T1", "13:00", "13:50"),
            new Aula("T2", "13:50", "14:40"),
            new Aula("T3", "14:40", "15:30"),
            new Aula("T4", "15:50", "16:40"),
            new Aula("T5", "16:40", "17:30"),
            new Aula("T6", "17:30", "18:20"),
            new Aula("N1", "18:40", "19:30"),
            new Aula("N2", "19:30", "20:20"),
            new Aula("N3", "20:20", "21:10"),
            new Aula("N4", "21:20", "22:10"),
            new Aula("N5", "22:10", "23:00")
    );

    /**
     * Digito do dia no codigo: '2' segunda ... '7' sabado.
     * Domingo nunca tem funcionamento (regra 8.4): retorna null.
     */
    public static String codigoDoDia(LocalDate data) {

        int dow = data.getDayOfWeek().getValue(); // 1 = segunda ... 7 = domingo

        return dow == 7 ? null : String.valueOf(dow + 1);
    }

    public static boolean inicioValido(String hora) {

        for (Aula a : AULAS) {
            if (a.inicio().equals(hora)) {
                return true;
            }
        }

        return false;
    }

    public static boolean fimValido(String hora) {

        for (Aula a : AULAS) {
            if (a.fim().equals(hora)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Aulas ocupadas por uma reserva de {inicio} ate {fim}, inclusive as
     * que ficam no meio (09:10-11:10 ocupa M3 e M4).
     *
     * Retorna null se inicio nao e inicio de aula, fim nao e fim de aula,
     * ou o fim nao vem depois do inicio.
     */
    public static List<Aula> aulasEntre(String inicio, String fim) {

        int i = -1;
        int j = -1;

        for (int k = 0; k < AULAS.size(); k++) {

            if (i < 0 && AULAS.get(k).inicio().equals(inicio)) {
                i = k;
            }

            if (AULAS.get(k).fim().equals(fim)) {
                j = k;
            }
        }

        if (i < 0 || j < 0 || j < i) {
            return null;
        }

        return AULAS.subList(i, j + 1);
    }

    /** Sobreposicao: novo_inicio < fim_existente E novo_fim > inicio_existente. */
    public static boolean sobrepoe(
            String inicioA, String fimA,
            String inicioB, String fimB
    ) {
        return inicioA.compareTo(fimB) < 0 && fimA.compareTo(inicioB) > 0;
    }
}

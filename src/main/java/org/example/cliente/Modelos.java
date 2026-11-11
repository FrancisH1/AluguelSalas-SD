package org.example.cliente;

import java.util.List;

/** Dados que a ApiCliente devolve (e recebe), ja convertidos do JSON. */
public final class Modelos {

    private Modelos() {
    }

    public record Usuario(
            String user,
            String email,
            String role,
            String criadoEm      // so vem em read_user / admin_read_user
    ) {
    }

    public record Sala(
            String id,
            String nome,
            int capacidade,
            String local,
            List<String> recursos,
            String estado,       // "active" ou "inactive"
            List<String> slots,  // codigos UTFPR: 2M1, 3T2...
            Boolean disponivel   // null se a consulta nao tinha filtro de horario
    ) {

        public boolean ativa() {
            return "active".equals(estado);
        }
    }

    public record ItemAgenda(
            String slot,
            String inicio,
            String fim,
            boolean livre,
            String usuario,      // quem reservou (null se livre)
            String reservaId     // so para admin e dono da reserva
    ) {
    }

    public record DetalheSala(
            Sala sala,
            String data,                 // null se a consulta nao pediu agenda
            List<ItemAgenda> agenda
    ) {
    }

    /** Todos os campos sao opcionais (null = sem filtro). data/inicio/fim vem juntos. */
    public record FiltroSalas(
            String data,
            String inicio,
            String fim,
            String capacidadeMinima
    ) {
    }

    public record NovaSala(
            String nome,
            String capacidade,
            String local,
            List<String> recursos,
            List<String> slots
    ) {
    }
}

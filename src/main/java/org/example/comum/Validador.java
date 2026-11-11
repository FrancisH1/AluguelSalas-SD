package org.example.comum;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Regex do dicionario do protocolo (v2.8). Usado pelo cliente e pelo
 * servidor, para os dois lados validarem exatamente a mesma coisa.
 */
public final class Validador {

    private Validador() {
    }

    private static final Pattern EMAIL = Pattern.compile(
            "^[a-z0-9._%+-]+@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}$");
    private static final Pattern USER = Pattern.compile("^[a-z]{1,30}$");
    private static final Pattern PASSWORD = Pattern.compile("^[A-Za-z0-9]{1,20}$");
    private static final Pattern TOKEN = Pattern.compile("^[a-f0-9]{64}$");
    private static final Pattern ID = Pattern.compile("^[0-9]{1,10}$");
    private static final Pattern NOME_SALA = Pattern.compile("^[A-Za-z0-9 -]{1,30}$");
    private static final Pattern CAPACIDADE = Pattern.compile("^[1-9][0-9]{0,3}$");
    private static final Pattern LOCAL = Pattern.compile("^[A-Za-z0-9 ,.-]{1,60}$");
    private static final Pattern DATA = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}$");
    private static final Pattern SLOT = Pattern.compile("^[2-7](M[1-6]|T[1-6]|N[1-5])$");

    private static boolean casa(Pattern p, String s) {
        return s != null && p.matcher(s).matches();
    }

    public static boolean email(String s) { return casa(EMAIL, s); }

    public static boolean user(String s) { return casa(USER, s); }

    public static boolean password(String s) { return casa(PASSWORD, s); }

    public static boolean token(String s) { return casa(TOKEN, s); }

    /** room_id e reservation_id. */
    public static boolean id(String s) { return casa(ID, s); }

    public static boolean nomeSala(String s) { return casa(NOME_SALA, s); }

    /** capacity, participants e min_capacity. */
    public static boolean capacidade(String s) { return casa(CAPACIDADE, s); }

    public static boolean local(String s) { return casa(LOCAL, s); }

    /** Formato AAAA-MM-DD E data que existe no calendario (2026-02-30 nao). */
    public static boolean data(String s) {

        if (!casa(DATA, s)) {
            return false;
        }

        try {
            LocalDate.parse(s);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    /** Codigo da UTFPR: dia + turno + aula (2M1, 4T3, 5N5...). */
    public static boolean slot(String s) { return casa(SLOT, s); }
}

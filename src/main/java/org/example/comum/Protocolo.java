package org.example.comum;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Helpers de leitura de requisicoes e montagem de respostas (JSON). */
public final class Protocolo {

    private Protocolo() {
    }

    public static final String MSG_PERMISSAO = "Permissao insuficiente";
    public static final String MSG_TOKEN_INVALIDO = "Token invalido ou expirado";
    public static final String MSG_DADOS = "Dados em formato invalido";
    public static final String MSG_ULTIMO_ADMIN =
            "Nao e possivel remover o ultimo administrador";

    /** Resposta basica. "op" e inserida primeiro (regra 2.1). */
    public static JsonObject resp(String op, String status, String message) {

        JsonObject o = new JsonObject();

        o.addProperty("op", op);
        o.addProperty("status", status);
        o.addProperty("message", message);

        return o;
    }

    /** Status da resposta, como string ("200"). */
    public static String status(JsonObject resposta) {
        return str(resposta, "status");
    }

    /**
     * Valor string da chave, ou null se ausente, null ou de outro tipo
     * (regra 2.10: tudo isso e invalido para campo obrigatorio).
     */
    public static String str(JsonObject j, String chave) {

        JsonElement e = j.get(chave);

        if (e == null
                || e.isJsonNull()
                || !e.isJsonPrimitive()
                || !e.getAsJsonPrimitive().isString()) {
            return null;
        }

        return e.getAsString();
    }

    /**
     * Array de strings, ou null se ausente, nao for array ou tiver
     * algum elemento que nao seja string.
     */
    public static List<String> lista(JsonObject j, String chave) {

        JsonElement e = j.get(chave);

        if (e == null || !e.isJsonArray()) {
            return null;
        }

        List<String> r = new ArrayList<>();

        for (JsonElement item : e.getAsJsonArray()) {

            if (item == null
                    || item.isJsonNull()
                    || !item.isJsonPrimitive()
                    || !item.getAsJsonPrimitive().isString()) {
                return null;
            }

            r.add(item.getAsString());
        }

        return r;
    }

    public static JsonArray paraArray(List<String> itens) {

        JsonArray a = new JsonArray();

        for (String s : itens) {
            a.add(s);
        }

        return a;
    }
}

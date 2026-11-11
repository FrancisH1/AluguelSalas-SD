package org.example.cliente.gui;

import javafx.application.Application;

/**
 * Ponto de entrada. Existe so porque, ao rodar a partir de um JAR no
 * classpath, a classe com o main() NAO pode estender Application (senao
 * o JavaFX reclama de "runtime components are missing").
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}

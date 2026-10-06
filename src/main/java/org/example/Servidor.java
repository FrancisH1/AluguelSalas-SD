package org.example;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class Servidor {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Digite a porta para abrir o servidor: ");
        final int PORTA = scanner.nextInt();
        System.out.println("Iniciando o servidor TCP na porta " + PORTA + "...");

        try (ServerSocket serverSocket = new ServerSocket(PORTA)) {
            while (true) {
                // Aguarda e aceita a conexão de um novo cliente
                Socket socketCliente = serverSocket.accept();
                System.out.println("Novo cliente conectado: " + socketCliente.getInetAddress());

                // Cria uma Thread separada para atender este cliente
                Thread threadCliente = new Thread(new ClienteHandler(socketCliente));
                threadCliente.start();
            }
        } catch (Exception e) {
            System.out.println("Erro no ServerSocket: " + e.getMessage());
        } finally {
            scanner.close();
        }
    }
}

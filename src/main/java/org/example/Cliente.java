package org.example;

import com.google.gson.Gson;

import java.io.*;
import java.lang.classfile.Label;
import java.net.Socket;
import java.util.InputMismatchException;
import java.util.Scanner;



public class Cliente {
    static Gson gson = new Gson();
    public static String tokenSessao = null;

    static void main() throws IOException {
        Scanner scanner = new Scanner(System.in);

        String IP;
        int PORTA;

        while(true){
            System.out.println("IP: ");
            IP = scanner.nextLine();

            try {
                System.out.println("Porta: ");
                PORTA = scanner.nextInt();
                scanner.nextLine();
                break;
            } catch (InputMismatchException e) {
                System.out.println("Dado inválido");
            }
        }

        try (Socket socket = new Socket(IP, PORTA)) {

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            boolean rodando = true;

            while (rodando) {
                if (tokenSessao != null) {
                    while (true) {
                        int opcao = MenuUsuario(scanner);
                        String jsonEnvio;

                        switch (opcao) {
                            case 1:
                                Mensagem readUser = Mensagem.lerUsuario(tokenSessao);
                                jsonEnvio = gson.toJson(readUser);
                                out.println(jsonEnvio);
                                Mensagem resposta = gson.fromJson(in.readLine(), Mensagem.class);

                                System.out.println("Email: " + resposta.getEmail());
                                System.out.println("Usuario: " + resposta.getUser());

                                break;
                            case 0:
                                System.out.println("Deslogando...");
                                Mensagem logout = Mensagem.fazerLogout(tokenSessao);
                                jsonEnvio = gson.toJson(logout);
                                out.println(jsonEnvio);
                                tokenSessao = null;
                                break;
                        }

                        Mensagem resposta = gson.fromJson(in.readLine(), Mensagem.class);
                        if(tokenSessao == null) break;
                    }
                } else {
                    while (true) {
                        int opcao = MenuInicial(scanner);
                        String email;
                        String usuario;
                        String senha;
                        String jsonEnvio;

                        switch (opcao) {
                            case 1:
                                email = lerEmail(scanner);
                                usuario = lerUser(scanner);
                                senha = lerPassword(scanner);
                                Mensagem registro = Mensagem.paraRegistro(email, usuario, senha);
                                jsonEnvio = gson.toJson(registro);
                                out.println(jsonEnvio);
                                break;
                            case 2:
                                email = lerEmail(scanner);
                                senha = lerPassword(scanner);
                                Mensagem login = Mensagem.paraLogin(email, senha);
                                jsonEnvio = gson.toJson(login);
                                out.println(jsonEnvio);
                                break;
                            case 0:
                                System.out.println("Finalizando conexão...");
                                out.println("SAIR");
                                break;
                        }
                        if (opcao == 0) {
                            rodando = false;
                            break;
                        }

                        Mensagem resposta = gson.fromJson(in.readLine(), Mensagem.class);
                        if (resposta.getToken() != null) {
                            tokenSessao = resposta.getToken();
                            break;
                        }
                    }
                }
            }
        } catch (IOException e){
            System.out.println("Erro ao conectar");
        }

        scanner.close();
    }

    public static int MenuUsuario(Scanner scanner){

        System.out.println("\n--- MENU ---");
        System.out.println("1. Visualizar Cadastro");
        System.out.println("2. Editar Cadastro");
        System.out.println("3. Deletar cadastro");
        System.out.println("0. Logout");

        int op = scanner.nextInt();
        scanner.nextLine();

        return op;
    }

    public static int MenuInicial(Scanner scanner){

        System.out.println("\n--- MENU ---");
        System.out.println("1. Registrar");
        System.out.println("2. Login");
        System.out.println("0. Sair");
        System.out.print("Escolha uma opção: ");

        int op = scanner.nextInt();
        scanner.nextLine();

        return op;
    }

    public static String lerEmail(Scanner scanner){

        System.out.print("Digite o email: ");
        String email = scanner.nextLine();

        // Validação usando o Regex do seu projeto
        if (!email.matches("^[a-z0-9.]+@[a-z0-9]+(\\.[a-z]+){1,2}$")) {
            System.out.println("Email inválido!");
            return null;
        }

        return email;
    }

    public static String lerUser(Scanner scanner){

        System.out.print("Digite o usuário: ");
        String usuario = scanner.nextLine();

        if (!usuario.matches("^[a-z]{1,30}$")) {
            System.out.println("Email inválido!");
            return null;
        }

        return usuario;
    }

    public static String lerPassword(Scanner scanner) {
        Console console = System.console();

        String password;
        if (console != null) {
            // Esconde os caracteres digitados no terminal
            char[] passwordChars = console.readPassword("Digite a senha: ");
            password = new String(passwordChars);
        } else {
            // Fallback caso esteja rodando dentro de uma IDE (Eclipse, IntelliJ, NetBeans)
            System.out.print("Digite a senha: ");
            password = scanner.nextLine();
        }

        if (!password.matches("^[A-Za-z0-9]{1,20}$")) {
            System.out.println("Senha inválida! Use apenas letras e números.");
            return null;
        }

        return password;
    }
}

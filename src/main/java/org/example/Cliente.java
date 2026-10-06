package org.example;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.InputMismatchException;
import java.util.Scanner;

public class Cliente {

    static Gson gson = new Gson();

    public static String tokenSessao = null;

    // =========================================================
    // MAIN
    // =========================================================

    public static void main(String[] args)
            throws IOException {

        Scanner scanner =
                new Scanner(System.in);

        String IP;
        int PORTA;

        while (true) {

            System.out.print("IP: ");

            IP = scanner.nextLine();

            try {

                System.out.print("Porta: ");

                PORTA = scanner.nextInt();

                scanner.nextLine();

                break;

            } catch (InputMismatchException e) {

                System.out.println(
                        "Dado invalido"
                );

                scanner.nextLine();
            }
        }

        System.out.println(
                "Conectando ao servidor TCP " +
                        IP + ":" + PORTA + "..."
        );

        try (
                Socket socket =
                        new Socket(IP, PORTA);

                PrintWriter out =
                        new PrintWriter(
                                socket.getOutputStream(),
                                true
                        );

                BufferedReader in =
                        new BufferedReader(
                                new InputStreamReader(
                                        socket.getInputStream()
                                )
                        )
        ) {

            boolean rodando = true;

            while (rodando) {

                if (tokenSessao == null) {

                    rodando =
                            menuInicial(
                                    scanner,
                                    out,
                                    in
                            );

                } else {

                    rodando =
                            menuUsuario(
                                    scanner,
                                    out,
                                    in
                            );
                }
            }

        } catch (IOException e) {

            System.out.println(
                    "Erro ao conectar/comunicar " +
                            "com o servidor: " +
                            e.getMessage()
            );

        } finally {

            scanner.close();
        }
    }

    // =========================================================
    // MENU INICIAL
    // =========================================================

    private static boolean menuInicial(
            Scanner scanner,
            PrintWriter out,
            BufferedReader in
    ) throws IOException {

        int opcao =
                MenuInicial(scanner);

        switch (opcao) {

            case 1 -> {

                String email =
                        lerEmail(scanner);

                if (email == null) {
                    break;
                }

                String usuario =
                        lerUser(scanner);

                if (usuario == null) {
                    break;
                }

                String senha =
                        lerPassword(scanner);

                if (senha == null) {
                    break;
                }

                Mensagem registro =
                        Mensagem.paraRegistro(
                                email,
                                usuario,
                                senha
                        );

                Mensagem resposta =
                        enviar(
                                registro,
                                out,
                                in
                        );

                mostrarResposta(
                        resposta
                );
            }

            case 2 -> {

                String email =
                        lerEmail(scanner);

                if (email == null) {
                    break;
                }

                String senha =
                        lerPassword(scanner);

                if (senha == null) {
                    break;
                }

                Mensagem login =
                        Mensagem.paraLogin(
                                email,
                                senha
                        );

                Mensagem resposta =
                        enviar(
                                login,
                                out,
                                in
                        );

                mostrarResposta(
                        resposta
                );

                if ("login_response".equals(
                        resposta.getOp()
                ) &&
                        "200".equals(
                                resposta.getStatus()
                        )) {

                    tokenSessao =
                            resposta.getToken();

                    System.out.println(
                            "Sessao iniciada."
                    );
                }
            }

            case 0 -> {

                out.println("SAIR");

                return false;
            }

            default -> {

                System.out.println(
                        "Opcao invalida."
                );
            }
        }

        return true;
    }

    // =========================================================
    // MENU DO USUARIO
    // =========================================================

    private static boolean menuUsuario(
            Scanner scanner,
            PrintWriter out,
            BufferedReader in
    ) throws IOException {

        int opcao =
                MenuUsuario(scanner);

        switch (opcao) {

            // -------------------------------------------------
            // READ USER
            // -------------------------------------------------

            case 1 -> {

                Mensagem req =
                        Mensagem.lerUsuario(
                                tokenSessao
                        );

                Mensagem resposta =
                        enviar(
                                req,
                                out,
                                in
                        );

                mostrarResposta(
                        resposta
                );

                if ("read_user_response".equals(
                        resposta.getOp()
                ) &&
                        "200".equals(
                                resposta.getStatus()
                        )) {

                    System.out.println(
                            "Email: " +
                                    resposta.getEmail()
                    );

                    System.out.println(
                            "Usuario: " +
                                    resposta.getUser()
                    );

                    System.out.println(
                            "Role: " +
                                    resposta.getRole()
                    );

                    System.out.println(
                            "Criado em: " +
                                    resposta.getCreatedAt()
                    );
                }
            }

            // -------------------------------------------------
            // UPDATE USER
            // -------------------------------------------------

            case 2 -> {

                System.out.println(
                        "Deixe vazio o campo que " +
                                "nao deseja alterar."
                );

                String usuario =
                        lerUserParaUpdate(
                                scanner
                        );

                String senha =
                        lerPasswordParaUpdate(
                                scanner
                        );

                Mensagem req =
                        Mensagem.atualizarUsuario(
                                tokenSessao,
                                usuario,
                                senha
                        );

                Mensagem resposta =
                        enviar(
                                req,
                                out,
                                in
                        );

                mostrarResposta(
                        resposta
                );
            }

            // -------------------------------------------------
            // DELETE USER
            // -------------------------------------------------

            case 3 -> {

                System.out.println();
                System.out.println(
                        "ATENCAO: esta operacao " +
                                "remove definitivamente o cadastro."
                );

                String senha =
                        lerPassword(scanner);

                if (senha == null) {
                    break;
                }

                Mensagem req =
                        Mensagem.deletarUsuario(
                                tokenSessao,
                                senha
                        );

                Mensagem resposta =
                        enviar(
                                req,
                                out,
                                in
                        );

                mostrarResposta(
                        resposta
                );

                if ("delete_user_response".equals(
                        resposta.getOp()
                ) &&
                        "200".equals(
                                resposta.getStatus()
                        )) {

                    tokenSessao = null;

                    System.out.println(
                            "Cadastro removido com sucesso."
                    );
                }
            }

            // -------------------------------------------------
            // LOGOUT
            // -------------------------------------------------

            case 0 -> {

                Mensagem req =
                        Mensagem.fazerLogout(
                                tokenSessao
                        );

                Mensagem resposta =
                        enviar(
                                req,
                                out,
                                in
                        );

                mostrarResposta(
                        resposta
                );

                tokenSessao = null;
            }

            default -> {

                System.out.println(
                        "Opcao invalida."
                );
            }
        }

        return true;
    }

    // =========================================================
    // ENVIO / RECEBIMENTO
    // =========================================================

    private static Mensagem enviar(
            Mensagem mensagem,
            PrintWriter out,
            BufferedReader in
    ) throws IOException {

        /*
         * Uma requisicao.
         */
        out.println(
                gson.toJson(mensagem)
        );

        /*
         * Exatamente uma resposta.
         */
        String linha =
                in.readLine();

        if (linha == null) {

            throw new IOException(
                    "Servidor encerrou a conexao."
            );
        }

        return gson.fromJson(
                linha,
                Mensagem.class
        );
    }

    private static void mostrarResposta(
            Mensagem resposta) {

        if (resposta == null) {

            System.out.println(
                    "Resposta invalida do servidor."
            );

            return;
        }

        System.out.println(
                "[" +
                        resposta.getStatus() +
                        "] " +
                        resposta.getMessage()
        );
    }

    // =========================================================
    // MENUS
    // =========================================================

    public static int MenuUsuario(
            Scanner scanner) {

        System.out.println();
        System.out.println("--- MENU ---");
        System.out.println(
                "1. Visualizar Cadastro"
        );
        System.out.println(
                "2. Editar Cadastro"
        );
        System.out.println(
                "3. Deletar cadastro"
        );
        System.out.println(
                "0. Logout"
        );

        System.out.print(
                "Escolha uma opcao: "
        );

        try {

            int op =
                    scanner.nextInt();

            scanner.nextLine();

            return op;

        } catch (InputMismatchException e) {

            scanner.nextLine();

            return -1;
        }
    }

    public static int MenuInicial(
            Scanner scanner) {

        System.out.println();
        System.out.println("--- MENU ---");
        System.out.println(
                "1. Registrar"
        );
        System.out.println(
                "2. Login"
        );
        System.out.println(
                "0. Sair"
        );

        System.out.print(
                "Escolha uma opcao: "
        );

        try {

            int op =
                    scanner.nextInt();

            scanner.nextLine();

            return op;

        } catch (InputMismatchException e) {

            scanner.nextLine();

            return -1;
        }
    }

    // =========================================================
    // LEITURA / VALIDAÇÃO
    // =========================================================

    public static String lerEmail(
            Scanner scanner) {

        System.out.print(
                "Digite o email: "
        );

        String email =
                scanner.nextLine().trim();

        if (!validarEmail(email)) {

            System.out.println(
                    "Email invalido!"
            );

            return null;
        }

        return email;
    }

    public static String lerUser(
            Scanner scanner) {

        System.out.print(
                "Digite o usuario: "
        );

        String usuario =
                scanner.nextLine().trim();

        if (!validarUser(usuario)) {

            System.out.println(
                    "Usuario invalido!"
            );

            return null;
        }

        return usuario;
    }

    public static String lerUserParaUpdate(
            Scanner scanner) {

        System.out.print(
                "Novo usuario " +
                        "(Enter para manter): "
        );

        String usuario =
                scanner.nextLine().trim();

        if (usuario.isEmpty()) {

            return "";
        }

        if (!validarUser(usuario)) {

            System.out.println(
                    "Usuario invalido!"
            );

            return "";
        }

        return usuario;
    }

    public static String lerPassword(
            Scanner scanner) {

        Console console =
                System.console();

        String password;

        if (console != null) {

            char[] passwordChars =
                    console.readPassword(
                            "Digite a senha: "
                    );

            password =
                    new String(passwordChars);

        } else {

            System.out.print(
                    "Digite a senha: "
            );

            password =
                    scanner.nextLine();
        }

        if (!validarPassword(
                password
        )) {

            System.out.println(
                    "Senha invalida! " +
                            "Use apenas letras e numeros."
            );

            return null;
        }

        return password;
    }

    public static String lerPasswordParaUpdate(
            Scanner scanner) {

        System.out.print(
                "Nova senha " +
                        "(Enter para manter): "
        );

        String password =
                scanner.nextLine();

        if (password.isEmpty()) {

            return "";
        }

        if (!validarPassword(
                password
        )) {

            System.out.println(
                    "Senha invalida! " +
                            "Use apenas letras e numeros."
            );

            return "";
        }

        return password;
    }

    // =========================================================
    // REGEX
    // =========================================================

    public static boolean validarEmail(
            String email) {

        return email != null &&
                email.matches(
                        "^[a-z0-9.]+@[a-z0-9]+(\\.[a-z]+){1,2}$"
                );
    }

    public static boolean validarUser(
            String user) {

        return user != null &&
                user.matches(
                        "^[a-z]{1,30}$"
                );
    }

    public static boolean validarPassword(
            String password) {

        return password != null &&
                password.matches(
                        "^[A-Za-z0-9]{1,20}$"
                );
    }

    public static boolean validarToken(
            String token) {

        return token != null &&
                token.matches(
                        "^[a-f0-9]{64}$"
                );
    }
}
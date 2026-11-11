# Projeto SD

## Ambiente
- JDK 25 (o `pom.xml` configura source/target 25).
- Apache Maven 3.9+.
- JavaFX é baixado pelo Maven a partir do `pom.xml`.
- IDE recomendada: IntelliJ IDEA.

## Abrir e compilar
1. Extraia o ZIP em uma pasta sem acentos, por exemplo `C:\Projetos\SistemasDistribuidos` ou `~/Projetos/SistemasDistribuidos`.
2. Abra a pasta que contém `pom.xml` no IntelliJ como projeto Maven.
3. Configure o Project SDK como JDK 25.
4. Aguarde o Maven baixar Gson, SQLite JDBC e JavaFX.
5. No terminal, dentro da pasta do `pom.xml`, execute `mvn clean package`.

## Iniciar o servidor
1. Execute `org.example.servidor.Servidor` no IntelliJ, ou use a configuração Maven/Java equivalente.
2. Digite a porta solicitada, por exemplo `5000`.
3. Mantenha o terminal do servidor aberto.
4. Na primeira inicialização com banco sem conflito, é criado `admin@admin.com` com senha `admin`.

## Iniciar o cliente
1. Em outro terminal, execute `org.example.cliente.gui.Launcher`.
2. Na tela de conexão, informe IP do servidor (`127.0.0.1` para a mesma máquina) e a porta informada ao iniciar o servidor.
3. Entre com `admin@admin.com` / `admin` se o banco foi inicializado sem conflito.
4. Cadastre usuários comuns e teste as telas de usuários e salas.

## Testes de demonstração EP-2
- Admin lista todos os usuários.
- Admin atualiza outro usuário.
- Admin exclui usuário comum.
- Admin cadastra sala com nome, capacidade, local, recursos e slots.
- Usuário consulta salas e agenda por data.
- Admin atualiza sala, incluindo capacidade, status e slots.
- Admin remove sala sem reservas futuras.
- Digite IP/porta no cliente e porta no servidor.
- Observe no console do servidor as mensagens JSON recebidas e enviadas.
- Teste duas conexões de clientes simultaneamente.

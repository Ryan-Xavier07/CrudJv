import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;

/**
 * Exemplo de uso. Requer um driver JDBC no classpath, p.ex. H2:
 *   javac *.java
 *   java -cp .:h2.jar Main        (Windows: ".;h2.jar")
 * Para outro banco, troque a URL abaixo.
 */

public class Main {
    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:teste")) {
            GenericDAO<Cliente> dao = new GenericDAO<>(conn, Cliente.class);

            dao.criarTabela(new Cliente());

            dao.inserir(new Cliente("Ana", "ana@email.com", 25, LocalDate.now()));
            dao.inserir(new Cliente("Bruno", "bruno@email.com", 30, LocalDate.now()));

            System.out.println("Todos:");
            dao.listarTodos().forEach(System.out::println);

            System.out.println("Busca id 1: " + dao.buscarPorId(1));

            dao.alterarCampo(1, "idade", 26);
            System.out.println("Após alterar: " + dao.buscarPorId(1));

            dao.remover(2);
            System.out.println("Após remover id 2: " + dao.listarTodos());
        }
    }
}

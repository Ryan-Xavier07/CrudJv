import java.time.LocalDate;

@Table(name = "clientes")
public class Cliente {

    @Id(autoIncrement = true)
    @Column(name = "id_cliente")
    private int id;

    @Column(name = "nome_cliente", length = 100, nullable = false)
    private String nome;

    @Column(name = "email", unique = true)
    private String email;

    private int idade; // sem annotation: coluna "idade"

    @Column(name = "data_cadastro")
    private LocalDate dataCadastro;

    public Cliente() {} // obrigatório para o GenericDAO

    public Cliente(String nome, String email, int idade, LocalDate dataCadastro) {
        this.nome = nome;
        this.email = email;
        this.idade = idade;
        this.dataCadastro = dataCadastro;
    }

    public int getId() { return id; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public int getIdade() { return idade; }
    public LocalDate getDataCadastro() { return dataCadastro; }

    @Override
    public String toString() {
        return "Cliente{id=" + id + ", nome=" + nome + ", email=" + email
                + ", idade=" + idade + ", dataCadastro=" + dataCadastro + "}";
    }
}

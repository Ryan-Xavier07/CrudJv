import java.lang.annotation.*;

/** Define o nome da tabela. Se ausente, usa o nome da classe. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Table {
    String name();
}

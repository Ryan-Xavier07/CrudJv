import java.lang.annotation.*;

/** Configura a coluna correspondente a um atributo. Tudo é opcional. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {
    /** Nome da coluna. Vazio = nome do atributo. */
    String name() default "";
    /** Tamanho máximo (usado em VARCHAR). */
    int length() default 255;
    /** false => NOT NULL. */
    boolean nullable() default true;
    /** true => UNIQUE. */
    boolean unique() default false;
    /** Tipo SQL específico (ex.: "TEXT"). Vazio = mapeamento automático. */
    String sqlType() default "";
}

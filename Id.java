import java.lang.annotation.*;

/** Marca a chave primária. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Id {
    /** true => geração automática (IDENTITY). O campo não entra no INSERT. */
    boolean autoIncrement() default false;
}

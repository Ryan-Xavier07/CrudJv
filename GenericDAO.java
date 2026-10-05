import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO genérico baseado em Generics, Reflection e Annotations.
 * @param <T> classe da entidade
 */
public class GenericDAO<T> {

    private final Connection conn;
    private final Class<T> classe;

    public GenericDAO(Connection conn, Class<T> classe) {
        this.conn = conn;
        this.classe = classe;
    }

    // ------------------------------------------------------------------
    // Metadados (Reflection + Annotations)
    // ------------------------------------------------------------------

    private static String nomeTabela(Class<?> c) {
        Table t = c.getAnnotation(Table.class);
        return (t != null && !t.name().isBlank()) ? t.name() : c.getSimpleName();
    }

    private static String nomeColuna(Field f) {
        Column col = f.getAnnotation(Column.class);
        return (col != null && !col.name().isBlank()) ? col.name() : f.getName();
    }

    /** Atributos persistíveis (ignora static e sintéticos). */
    private static List<Field> campos(Class<?> c) {
        List<Field> lista = new ArrayList<>();
        for (Field f : c.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
            f.setAccessible(true);
            lista.add(f);
        }
        return lista;
    }

    /** Campo @Id; se não houver, tenta o atributo chamado "id". */
    private static Field campoId(Class<?> c) {
        for (Field f : campos(c)) {
            if (f.isAnnotationPresent(Id.class)) return f;
        }
        for (Field f : campos(c)) {
            if (f.getName().equalsIgnoreCase("id")) return f;
        }
        throw new IllegalStateException("Classe " + c.getSimpleName() + " não possui campo de ID.");
    }

    private static boolean autoIncrement(Field f) {
        Id id = f.getAnnotation(Id.class);
        return id != null && id.autoIncrement();
    }

    // ------------------------------------------------------------------
    // Conversão Java <-> JDBC
    // ------------------------------------------------------------------

    private static void setParametro(PreparedStatement ps, int idx, Object valor) throws SQLException {
        if (valor instanceof LocalDate d) ps.setDate(idx, Date.valueOf(d));
        else if (valor instanceof LocalDateTime dt) ps.setTimestamp(idx, Timestamp.valueOf(dt));
        else ps.setObject(idx, valor);
    }

    private static Object lerColuna(ResultSet rs, String coluna, Class<?> tipo) throws SQLException {
        if (tipo == int.class || tipo == Integer.class) { int v = rs.getInt(coluna); return (rs.wasNull() && tipo == Integer.class) ? null : v; }
        if (tipo == long.class || tipo == Long.class) { long v = rs.getLong(coluna); return (rs.wasNull() && tipo == Long.class) ? null : v; }
        if (tipo == double.class || tipo == Double.class) { double v = rs.getDouble(coluna); return (rs.wasNull() && tipo == Double.class) ? null : v; }
        if (tipo == float.class || tipo == Float.class) { float v = rs.getFloat(coluna); return (rs.wasNull() && tipo == Float.class) ? null : v; }
        if (tipo == boolean.class || tipo == Boolean.class) { boolean v = rs.getBoolean(coluna); return (rs.wasNull() && tipo == Boolean.class) ? null : v; }
        if (tipo == String.class) return rs.getString(coluna);
        if (tipo == LocalDate.class) { Date d = rs.getDate(coluna); return d == null ? null : d.toLocalDate(); }
        if (tipo == LocalDateTime.class) { Timestamp t = rs.getTimestamp(coluna); return t == null ? null : t.toLocalDateTime(); }
        return rs.getObject(coluna);
    }

    private T mapear(ResultSet rs) throws Exception {
        Constructor<T> ctor = classe.getDeclaredConstructor();
        ctor.setAccessible(true);
        T obj = ctor.newInstance();
        for (Field f : campos(classe)) {
            f.set(obj, lerColuna(rs, nomeColuna(f), f.getType()));
        }
        return obj;
    }

    // ------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------

    /** INSERT INTO tabela (col1, col2...) VALUES (?, ?...) */
    public void inserir(T obj) {
        List<String> cols = new ArrayList<>();
        List<Object> valores = new ArrayList<>();
        try {
            for (Field f : campos(classe)) {
                if (autoIncrement(f)) continue;
                cols.add(nomeColuna(f));
                valores.add(f.get(obj));
            }
            String sql = "INSERT INTO " + nomeTabela(classe)
                    + " (" + String.join(", ", cols) + ") VALUES ("
                    + String.join(", ", java.util.Collections.nCopies(cols.size(), "?")) + ")";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < valores.size(); i++) setParametro(ps, i + 1, valores.get(i));
                ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao inserir: " + e.getMessage(), e);
        }
    }

    /** DELETE FROM tabela WHERE id = ? */
    public boolean remover(Object id) {
        String sql = "DELETE FROM " + nomeTabela(classe) + " WHERE " + nomeColuna(campoId(classe)) + " = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setParametro(ps, 1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao remover: " + e.getMessage(), e);
        }
    }

    /**
     * UPDATE tabela SET nome_campo = ? WHERE id = ?
     * @param campo nome do atributo Java (ou da coluna)
     */
    public boolean alterarCampo(Object id, String campo, Object novoValor) {
        Field alvo = null;
        for (Field f : campos(classe)) {
            if (f.getName().equals(campo) || nomeColuna(f).equals(campo)) { alvo = f; break; }
        }
        if (alvo == null) throw new IllegalArgumentException("Campo inexistente: " + campo);

        String sql = "UPDATE " + nomeTabela(classe) + " SET " + nomeColuna(alvo)
                + " = ? WHERE " + nomeColuna(campoId(classe)) + " = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setParametro(ps, 1, novoValor);
            setParametro(ps, 2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao alterar campo: " + e.getMessage(), e);
        }
    }

    /** SELECT * FROM tabela */
    public List<T> listarTodos() {
        String sql = "SELECT * FROM " + nomeTabela(classe);
        List<T> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapear(rs));
            return lista;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao listar: " + e.getMessage(), e);
        }
    }

    /** SELECT * FROM tabela WHERE id = ? (retorna null se não existir) */
    public T buscarPorId(Object id) {
        String sql = "SELECT * FROM " + nomeTabela(classe) + " WHERE " + nomeColuna(campoId(classe)) + " = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setParametro(ps, 1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao buscar: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Criação dinâmica de tabela
    // ------------------------------------------------------------------

    private static String tipoSql(Field f) {
        Column col = f.getAnnotation(Column.class);
        if (col != null && !col.sqlType().isBlank()) return col.sqlType();

        Class<?> t = f.getType();
        if (t == int.class || t == Integer.class) return "INTEGER";
        if (t == long.class || t == Long.class) return "BIGINT";
        if (t == double.class || t == Double.class) return "DOUBLE";
        if (t == float.class || t == Float.class) return "FLOAT";
        if (t == boolean.class || t == Boolean.class) return "BOOLEAN";
        if (t == String.class) return "VARCHAR(" + (col != null ? col.length() : 255) + ")";
        if (t == LocalDate.class) return "DATE";
        if (t == LocalDateTime.class) return "TIMESTAMP";
        throw new IllegalArgumentException("Tipo sem mapeamento SQL: " + t.getName()
                + " (use @Column(sqlType = \"...\"))");
    }

    /** Monta o CREATE TABLE a partir da classe (separado para facilitar testes). */
    public static String gerarCreateTable(Class<?> c) {
        Field id = campoId(c);
        List<String> defs = new ArrayList<>();

        for (Field f : campos(c)) {
            Column col = f.getAnnotation(Column.class);
            StringBuilder sb = new StringBuilder(nomeColuna(f)).append(' ').append(tipoSql(f));

            if (f.equals(id)) {
                if (autoIncrement(f)) sb.append(" GENERATED BY DEFAULT AS IDENTITY");
                sb.append(" PRIMARY KEY");
            } else {
                if (col != null && !col.nullable()) sb.append(" NOT NULL");
                if (col != null && col.unique()) sb.append(" UNIQUE");
            }
            defs.add(sb.toString());
        }
        return "CREATE TABLE IF NOT EXISTS " + nomeTabela(c) + " (\n  " + String.join(",\n  ", defs) + "\n)";
    }

    public void criarTabela(Object objeto) {
        String sql = gerarCreateTable(objeto.getClass());
        System.out.println(sql);
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao criar tabela: " + e.getMessage(), e);
        }
    }
}

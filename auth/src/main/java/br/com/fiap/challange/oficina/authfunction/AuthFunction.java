package br.com.fiap.challange.oficina.authfunction;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Recebe um CPF, valida o formato, confirma que existe um cliente ativo com
 * esse documento no banco e devolve um JWT compatível com o esperado pela
 * aplicação principal (mesma chave/algoritmo do JwtService, claim "role" =
 * CLIENTE).
 */
public class AuthFunction implements HttpFunction {

    private static final Gson GSON = new Gson();
    private static volatile HikariDataSource dataSource;

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        response.appendHeader("Content-Type", "application/json");

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            writeError(response, 405, "Método não permitido");
            return;
        }

        String cpf;
        try {
            JsonObject body = GSON.fromJson(request.getReader(), JsonObject.class);
            cpf = body != null && body.has("cpf") ? body.get("cpf").getAsString() : null;
        } catch (Exception e) {
            writeError(response, 400, "Corpo da requisição inválido");
            return;
        }

        if (!CpfValidator.isValido(cpf)) {
            writeError(response, 400, "CPF inválido");
            return;
        }
        String documento = CpfValidator.normalizar(cpf);

        Cliente cliente;
        try {
            cliente = buscarCliente(documento);
        } catch (Exception e) {
            writeError(response, 500, "Falha ao consultar cliente");
            return;
        }

        if (cliente == null) {
            writeError(response, 404, "Cliente não encontrado");
            return;
        }
        if (!cliente.ativo) {
            writeError(response, 403, "Cliente inativo");
            return;
        }

        String token = gerarToken(documento);
        JsonObject out = new JsonObject();
        out.addProperty("token", token);
        response.setStatusCode(200);
        response.getWriter().write(GSON.toJson(out));
    }

    private record Cliente(long id, boolean ativo) {
    }

    private Cliente buscarCliente(String documento) throws Exception {
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, ativo FROM clientes WHERE documento = ?")) {
            stmt.setString(1, documento);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                return new Cliente(rs.getLong("id"), rs.getBoolean("ativo"));
            }
        }
    }

    private String gerarToken(String documento) {
        String secret = env("JWT_SECRET");
        long expirationMs = Long.parseLong(env("JWT_EXPIRATION"));
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "CLIENTE");

        return Jwts.builder()
                .claims(claims)
                .subject(documento)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    private static synchronized HikariDataSource getDataSource() {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:postgresql:///" + env("DB_NAME")
                    + "?cloudSqlInstance=" + env("INSTANCE_CONNECTION_NAME")
                    + "&socketFactory=com.google.cloud.sql.postgres.SocketFactory");
            config.setUsername(env("DB_USER"));
            config.setPassword(env("DB_PASSWORD"));
            config.setMaximumPoolSize(2);
            dataSource = new HikariDataSource(config);
        }
        return dataSource;
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variável de ambiente ausente: " + name);
        }
        return value;
    }

    private void writeError(HttpResponse response, int status, String mensagem) throws Exception {
        response.setStatusCode(status);
        JsonObject out = new JsonObject();
        out.addProperty("mensagem", mensagem);
        out.addProperty("status", status);
        response.getWriter().write(GSON.toJson(out));
    }
}

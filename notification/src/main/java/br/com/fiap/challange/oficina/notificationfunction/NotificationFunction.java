package br.com.fiap.challange.oficina.notificationfunction;

import com.google.cloud.functions.CloudEventsFunction;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.cloudevents.CloudEvent;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Escuta o tópico de notificações (publicado pela aplicação principal via
 * PubSubEmailAdapter) e manda o e-mail de fato — a parte "lenta"/de rede que
 * antes rodava dentro da própria aplicação.
 */
public class NotificationFunction implements CloudEventsFunction {

    private static final Logger LOGGER = Logger.getLogger(NotificationFunction.class.getName());
    private static final Gson GSON = new Gson();

    @Override
    public void accept(CloudEvent event) throws Exception {
        if (event.getData() == null) {
            LOGGER.warning("Evento recebido sem dados, ignorando");
            return;
        }

        String cloudEventPayload = new String(event.getData().toBytes(), StandardCharsets.UTF_8);
        JsonObject envelope = GSON.fromJson(cloudEventPayload, JsonObject.class);
        String encodedData = envelope.getAsJsonObject("message").get("data").getAsString();
        String decoded = new String(Base64.getDecoder().decode(encodedData), StandardCharsets.UTF_8);

        JsonObject notificacao = GSON.fromJson(decoded, JsonObject.class);
        String destinatario = notificacao.get("destinatario").getAsString();
        String nomeCliente = notificacao.get("nomeCliente").getAsString();
        String numeroOS = notificacao.get("numeroOS").getAsString();
        String valorTotal = notificacao.get("valorTotal").getAsString();

        enviarEmail(destinatario, nomeCliente, numeroOS, valorTotal);
    }

    private void enviarEmail(String destinatario, String nomeCliente, String numeroOS, String valorTotal) throws Exception {
        String host = env("MAIL_HOST");
        String port = env("MAIL_PORT");
        String username = System.getenv("MAIL_USERNAME");
        String password = System.getenv("MAIL_PASSWORD");
        boolean auth = Boolean.parseBoolean(System.getenv().getOrDefault("MAIL_AUTH", "false"));
        boolean startTls = Boolean.parseBoolean(System.getenv().getOrDefault("MAIL_STARTTLS", "false"));
        String from = System.getenv().getOrDefault("MAIL_FROM", "oficina@localhost");

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", String.valueOf(auth));
        props.put("mail.smtp.starttls.enable", String.valueOf(startTls));

        Session session = (auth && username != null && !username.isBlank())
                ? Session.getInstance(props, new jakarta.mail.Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                })
                : Session.getInstance(props);

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
        message.setSubject("Orçamento disponível - OS " + numeroOS);
        message.setContent(montarCorpo(nomeCliente, numeroOS, valorTotal), "text/html; charset=utf-8");

        Transport.send(message);
        LOGGER.info("E-mail de orçamento enviado para=" + destinatario + " OS=" + numeroOS);
    }

    private String montarCorpo(String nomeCliente, String numeroOS, String valorTotal) {
        return """
                <html>
                <body style="font-family: Arial, sans-serif; max-width: 600px; margin: auto;">
                  <h2 style="color: #333;">Orçamento disponível para aprovação</h2>
                  <p>Olá, <strong>%s</strong>!</p>
                  <p>O orçamento da sua Ordem de Serviço <strong>%s</strong> foi gerado e está aguardando sua aprovação.</p>
                  <table style="border-collapse: collapse; width: 100%%;">
                    <tr style="background-color: #f2f2f2;">
                      <td style="padding: 8px; border: 1px solid #ddd;"><strong>OS</strong></td>
                      <td style="padding: 8px; border: 1px solid #ddd;">%s</td>
                    </tr>
                    <tr>
                      <td style="padding: 8px; border: 1px solid #ddd;"><strong>Valor Total</strong></td>
                      <td style="padding: 8px; border: 1px solid #ddd;">R$ %s</td>
                    </tr>
                  </table>
                  <p style="margin-top: 20px;">Entre em contato com a oficina para <strong>aprovar ou recusar</strong> o orçamento.</p>
                  <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                  <p style="color: #999; font-size: 12px;">Oficina Mecânica — Sistema de Gestão</p>
                </body>
                </html>
                """.formatted(nomeCliente, numeroOS, numeroOS, valorTotal);
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variável de ambiente ausente: " + name);
        }
        return value;
    }
}

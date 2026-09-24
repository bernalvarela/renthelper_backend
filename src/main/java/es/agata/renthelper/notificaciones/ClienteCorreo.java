package es.agata.renthelper.notificaciones;

import es.agata.renthelper.config.Cifrador;
import es.agata.renthelper.dominio.Ajustes;
import es.agata.renthelper.repositorio.RepositorioAjustes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Properties;

/**
 * Envío de correo al candidato.
 *
 * <p>Texto plano y no HTML a propósito: lo único que lleva es un enlace, y el texto plano no
 * acaba en spam, se ve igual en cualquier cliente y no se rompe en el móvil.
 *
 * <p>El emisor se construye por envío a partir de la configuración guardada, en vez de ser un
 * bean autoconfigurado desde {@code spring.mail.*}. Eso es lo que permite cambiar el servidor
 * desde el panel sin reiniciar; a cambio se paga una conexión nueva por correo, que para el
 * volumen de esto —un enlace por candidatura— no se nota.
 *
 * <p>Sin configurar escribe en el log en vez de enviar, igual que el cliente de Telegram.
 */
@Component
public class ClienteCorreo {

	private static final Logger log = LoggerFactory.getLogger(ClienteCorreo.class);

	private final RepositorioAjustes repoAjustes;
	private final Cifrador cifrador;

	public ClienteCorreo(RepositorioAjustes repoAjustes, Cifrador cifrador) {
		this.repoAjustes = repoAjustes;
		this.cifrador = cifrador;
	}

	@Transactional(readOnly = true)
	public boolean configurado() {
		return repoAjustes.findFirstBy().filter(Ajustes::correoConfigurado).isPresent();
	}

	@Transactional(readOnly = true)
	public void enviar(String destinatario, String asunto, String cuerpo) {
		if (destinatario == null || destinatario.isBlank()) {
			return;
		}
		Optional<Ajustes> ajustes = repoAjustes.findFirstBy().filter(Ajustes::correoConfigurado);
		if (ajustes.isEmpty()) {
			log.info("[correo no configurado] Para: {} · {}\n{}", destinatario, asunto, cuerpo);
			return;
		}

		Ajustes config = ajustes.get();
		SimpleMailMessage mensaje = new SimpleMailMessage();
		mensaje.setFrom(config.getCorreoRemitente());
		mensaje.setTo(destinatario);
		mensaje.setSubject(asunto);
		mensaje.setText(cuerpo);
		emisor(config).send(mensaje);

		log.info("Correo enviado a {}: {}", destinatario, asunto);
	}

	private JavaMailSenderImpl emisor(Ajustes config) {
		JavaMailSenderImpl emisor = new JavaMailSenderImpl();
		emisor.setHost(config.getSmtpHost());
		emisor.setPort(config.getSmtpPuerto());
		emisor.setUsername(config.getSmtpUsuario());
		emisor.setPassword(cifrador.descifrar(config.getSmtpPasswordCifrada()));

		Properties props = emisor.getJavaMailProperties();
		// Autenticación sólo si hay usuario: un relé interno sin credenciales rechaza el AUTH.
		props.put("mail.smtp.auth", String.valueOf(
				config.getSmtpUsuario() != null && !config.getSmtpUsuario().isBlank()));
		props.put("mail.smtp.starttls.enable", String.valueOf(config.isSmtpStarttls()));
		// Sin estos, un SMTP que no responde bloquea el hilo del outbox indefinidamente y con él
		// todas las evaluaciones pendientes.
		props.put("mail.smtp.connectiontimeout", "5000");
		props.put("mail.smtp.timeout", "5000");
		props.put("mail.smtp.writetimeout", "5000");
		return emisor;
	}
}

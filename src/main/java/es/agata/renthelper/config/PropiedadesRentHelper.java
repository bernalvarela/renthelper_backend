package es.agata.renthelper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "renthelper")
public record PropiedadesRentHelper(
		String urlPublica,
		/**
		 * Marca como sintética toda candidatura nueva. Se activa en el perfil dev: en local los
		 * datos te los inventas tú, y así el tier gratuito de Gemini —que no es apto para datos
		 * reales— sí puede evaluarlas sin tocar la salvaguarda.
		 */
		boolean sinteticasPorDefecto,
		Admin admin,
		RateLimit rateLimit,
		Retencion retencion,
		Outbox outbox,
		Llm llm) {

	/**
	 * En producción se configura {@code passwordHash} (BCrypt) para no tener la contraseña en
	 * claro en el {@code .env}. En local eso es una fricción absurda, así que {@code password}
	 * acepta texto plano y {@link BootstrapAdmin} lo cifra al arrancar; si están los dos, gana
	 * el hash.
	 */
	public record Admin(String email, String passwordHash, String password) {

		public boolean tieneHash() {
			return passwordHash != null && !passwordHash.isBlank();
		}

		public boolean tienePlano() {
			return password != null && !password.isBlank();
		}
	}

	/**
	 * Con 150 contactos legítimos en 20 horas no se puede apretar mucho: esto frena bots,
	 * no candidatos. La primera línea es el middleware de Traefik.
	 */
	public record RateLimit(int altasPorIpHora, int peticionesPorIpMinuto) {

		public RateLimit {
			altasPorIpHora = altasPorIpHora <= 0 ? 10 : altasPorIpHora;
			peticionesPorIpMinuto = peticionesPorIpMinuto <= 0 ? 90 : peticionesPorIpMinuto;
		}
	}

	public record Retencion(int meses, String cron) {

		public Retencion {
			meses = meses <= 0 ? 6 : meses;
			cron = cron == null ? "0 30 4 * * *" : cron;
		}
	}

	public record Outbox(long intervaloMs, int lote, int maxIntentos, long backoffBaseSegundos,
	                     /**
	                      * Espera cuando el proveedor no está disponible por cuota o saturación.
	                      * En minutos y no en segundos porque una cuota diaria no se recupera con
	                      * un backoff exponencial de minutos.
	                      */
	                     long aplazamientoMinutos,
	                     int maxAplazamientos) {

		public Outbox {
			intervaloMs = intervaloMs <= 0 ? 15000 : intervaloMs;
			lote = lote <= 0 ? 3 : lote;
			maxIntentos = maxIntentos <= 0 ? 5 : maxIntentos;
			backoffBaseSegundos = backoffBaseSegundos <= 0 ? 60 : backoffBaseSegundos;
			aplazamientoMinutos = aplazamientoMinutos <= 0 ? 60 : aplazamientoMinutos;
			maxAplazamientos = maxAplazamientos <= 0 ? 24 : maxAplazamientos;
		}
	}

	public record Llm(String promptVersion, int ajusteMaximo,
	                  /**
	                   * Pide además el veredicto sobre el nombre en la MISMA llamada. A cambio, el
	                   * nombre sale hacia el proveedor; a false no sale nunca y el resto funciona
	                   * igual, sólo que sin marca de autenticidad.
	                   */
	                  boolean verificarNombre) {

		public Llm {
			promptVersion = promptVersion == null ? "v1" : promptVersion;
			ajusteMaximo = ajusteMaximo <= 0 ? 15 : ajusteMaximo;
		}
	}

}

package es.agata.renthelper.servicio;

import es.agata.renthelper.api.ExcepcionNegocio;
import es.agata.renthelper.api.admin.DtosAdmin;
import es.agata.renthelper.config.Cifrador;
import es.agata.renthelper.dominio.Ajustes;
import es.agata.renthelper.dominio.ProveedorLlm;
import es.agata.renthelper.llm.EvaluadorLlm;
import es.agata.renthelper.llm.FabricaModelos;
import es.agata.renthelper.llm.ResultadoPrueba;
import es.agata.renthelper.notificaciones.ClienteCorreo;
import es.agata.renthelper.notificaciones.ClienteTelegram;
import es.agata.renthelper.repositorio.RepositorioAjustes;
import es.agata.renthelper.repositorio.RepositorioProveedorLlm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Configuración que se edita desde el panel: modelos, correo y Telegram.
 *
 * <p>Todo esto vivía en {@code application.yml}. Se movió a la base de datos porque los cupos
 * gratuitos cambian cada pocas semanas, los proveedores retiran modelos con poco aviso y hace
 * falta reordenar la cadena en caliente; con la configuración en un fichero, cada ajuste era
 * recompilar y desplegar.
 *
 * <p>Regla que gobierna los tres formularios: <b>un secreto vacío significa «no lo toques»</b>,
 * nunca «bórralo». La interfaz no puede mostrar el valor actual —no sale del servidor—, así que
 * si vaciar el campo borrara la clave, cualquiera que guardase un cambio de puerto se quedaría
 * sin correo sin enterarse.
 */
@Service
public class ServicioAjustes {

	private static final Logger log = LoggerFactory.getLogger(ServicioAjustes.class);

	private final RepositorioProveedorLlm repoProveedores;
	private final RepositorioAjustes repoAjustes;
	private final Cifrador cifrador;
	private final FabricaModelos fabrica;
	private final EvaluadorLlm evaluador;
	private final ClienteCorreo correo;
	private final ClienteTelegram telegram;

	public ServicioAjustes(RepositorioProveedorLlm repoProveedores, RepositorioAjustes repoAjustes,
	                       Cifrador cifrador, FabricaModelos fabrica, EvaluadorLlm evaluador,
	                       ClienteCorreo correo, ClienteTelegram telegram) {
		this.repoProveedores = repoProveedores;
		this.repoAjustes = repoAjustes;
		this.cifrador = cifrador;
		this.fabrica = fabrica;
		this.evaluador = evaluador;
		this.correo = correo;
		this.telegram = telegram;
	}

	// --- Proveedores ---------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<DtosAdmin.ProveedorDto> proveedores() {
		return repoProveedores.findAllByOrderByOrdenAscNombreAsc().stream().map(this::aDto).toList();
	}

	@Transactional
	public DtosAdmin.ProveedorDto crearProveedor(DtosAdmin.PeticionProveedor peticion) {
		exigir(peticion.nombre(), "NOMBRE", "El nombre es obligatorio.");
		exigir(peticion.urlBase(), "URL_BASE", "La url base es obligatoria.");
		exigir(peticion.modelo(), "MODELO", "El modelo es obligatorio.");
		if (repoProveedores.existsByNombre(peticion.nombre().trim())) {
			throw ExcepcionNegocio.invalido("NOMBRE", "Ya hay un proveedor con ese nombre.");
		}

		ProveedorLlm proveedor = new ProveedorLlm(peticion.nombre().trim(),
				normalizarUrl(peticion.urlBase()), peticion.modelo().trim());
		// Al final de la cadena: quien lo añade todavía no sabe si funciona, y colarlo delante
		// pondría un proveedor sin probar a decidir sobre candidaturas reales.
		proveedor.setOrden(siguienteOrden());
		aplicar(proveedor, peticion);
		repoProveedores.save(proveedor);

		log.info("Proveedor {} añadido ({} · {})", proveedor.getNombre(), proveedor.getUrlBase(),
				proveedor.getModelo());
		return aDto(proveedor);
	}

	@Transactional
	public DtosAdmin.ProveedorDto actualizarProveedor(UUID id, DtosAdmin.PeticionProveedor peticion) {
		ProveedorLlm proveedor = buscar(id);
		aplicar(proveedor, peticion);
		proveedor.tocar();
		repoProveedores.save(proveedor);
		// La caché guarda un cliente por (id, actualizadoEn), así que `tocar()` ya lo invalida.
		// El clear es para no dejar creciendo indefinidamente los clientes de versiones viejas.
		fabrica.olvidar();

		log.info("Proveedor {} actualizado ({} · {}) · activo: {}", proveedor.getNombre(),
				proveedor.getUrlBase(), proveedor.getModelo(), proveedor.isActivo());
		return aDto(proveedor);
	}

	@Transactional
	public void borrarProveedor(UUID id) {
		ProveedorLlm proveedor = buscar(id);
		repoProveedores.delete(proveedor);
		fabrica.olvidar();
		log.warn("Proveedor {} eliminado de la cadena", proveedor.getNombre());
	}

	/**
	 * Sube o baja un proveedor en la cadena.
	 *
	 * <p>Un puesto arriba o abajo, que con cuatro o cinco modelos es más cómodo que arrastrar y
	 * no necesita librería de terceros.
	 */
	@Transactional
	public List<DtosAdmin.ProveedorDto> mover(UUID id, int delta) {
		List<ProveedorLlm> cadena = repoProveedores.findAllByOrderByOrdenAscNombreAsc();
		int actual = -1;
		for (int i = 0; i < cadena.size(); i++) {
			if (cadena.get(i).getId().equals(id)) {
				actual = i;
				break;
			}
		}
		if (actual < 0) {
			throw ExcepcionNegocio.noEncontrado("Proveedor inexistente");
		}
		int destino = actual + (delta < 0 ? -1 : 1);
		if (destino < 0 || destino >= cadena.size()) {
			return proveedores();
		}

		// Renumerar, intercambiar y volver a numerar. Permutar sólo los dos valores de orden falla
		// en silencio cuando venían empatados: el repositorio desempata por nombre y el clic no
		// movía nada. Esto es correcto siempre, a cambio de reescribir la cadena entera —que son
		// cuatro filas—.
		renumerar(cadena);
		ProveedorLlm uno = cadena.get(actual);
		java.util.Collections.swap(cadena, actual, destino);
		renumerar(cadena);
		repoProveedores.saveAll(cadena);

		log.info("Proveedor {} movido a la posición {}", uno.getNombre(), destino + 1);
		return proveedores();
	}

	// --- Correo y Telegram ---------------------------------------------------------------

	@Transactional(readOnly = true)
	public DtosAdmin.AjustesDto ajustes() {
		Ajustes a = repoAjustes.findFirstBy().orElseGet(Ajustes::new);
		return new DtosAdmin.AjustesDto(
				a.getSmtpHost(), a.getSmtpPuerto(), a.getSmtpUsuario(), a.isSmtpStarttls(),
				a.getCorreoRemitente(), a.getSmtpPasswordCifrada() != null,
				a.getTelegramChatId(), a.getTelegramTokenCifrado() != null,
				cifrador.configurado(),
				a.isBonificacionActiva(), a.getBonificacionEdadMin(), a.getBonificacionEdadMax(),
				a.getBonificacionNota());
	}

	@Transactional
	public DtosAdmin.AjustesDto guardarAjustes(DtosAdmin.PeticionAjustes peticion) {
		Ajustes a = repoAjustes.findFirstBy().orElseGet(Ajustes::new);

		a.setSmtpHost(limpiar(peticion.smtpHost()));
		if (peticion.smtpPuerto() != null) {
			a.setSmtpPuerto(peticion.smtpPuerto());
		}
		a.setSmtpUsuario(limpiar(peticion.smtpUsuario()));
		a.setCorreoRemitente(limpiar(peticion.correoRemitente()));
		if (peticion.smtpStarttls() != null) {
			a.setSmtpStarttls(peticion.smtpStarttls());
		}
		a.setTelegramChatId(limpiar(peticion.telegramChatId()));

		if (peticion.bonificacionActiva() != null) {
			a.setBonificacionActiva(peticion.bonificacionActiva());
		}
		if (peticion.bonificacionEdadMin() != null) {
			a.setBonificacionEdadMin(peticion.bonificacionEdadMin());
		}
		if (peticion.bonificacionEdadMax() != null) {
			a.setBonificacionEdadMax(peticion.bonificacionEdadMax());
		}
		a.setBonificacionNota(limpiar(peticion.bonificacionNota()));

		// Los dos secretos, sólo si vienen con valor.
		if (tieneValor(peticion.smtpPassword())) {
			a.setSmtpPasswordCifrada(cifrador.cifrar(peticion.smtpPassword().trim()));
		}
		if (tieneValor(peticion.telegramToken())) {
			a.setTelegramTokenCifrado(cifrador.cifrar(peticion.telegramToken().trim()));
		}

		a.tocar();
		repoAjustes.save(a);
		log.info("Ajustes guardados · correo: {} · telegram: {}",
				a.correoConfigurado() ? "configurado" : "sin configurar",
				a.telegramConfigurado() ? "configurado" : "sin configurar");
		return ajustes();
	}

	// --- Comprobaciones ------------------------------------------------------------------

	/**
	 * Prueba un proveedor con una llamada mínima.
	 *
	 * <p>El modo de fallo habitual no es un error al guardar sino el silencio: la configuración
	 * parece correcta y la clave equivocada o la url sin {@code /v1} sólo se descubren horas
	 * después, cuando las candidaturas aparecen sin nota.
	 */
	@Transactional(readOnly = true)
	public ResultadoPrueba probarProveedor(UUID id) {
		return evaluador.probar(buscar(id));
	}

	/**
	 * Manda un correo de prueba.
	 *
	 * <p>Al remitente configurado, no a una dirección que escribas: con Gmail y compañía, mandarse
	 * a uno mismo es lo único que comprueba de verdad que el remitente está aceptado. Escribir
	 * otro destino podría funcionar y seguir fallando luego con el real.
	 */
	@Transactional(readOnly = true)
	public ResultadoPrueba probarCorreo() {
		Ajustes a = repoAjustes.findFirstBy().orElseGet(Ajustes::new);
		if (!a.correoConfigurado()) {
			return new ResultadoPrueba(false, "Faltan el servidor SMTP o el remitente", 0);
		}
		long inicio = System.currentTimeMillis();
		try {
			correo.enviar(a.getCorreoRemitente(), "RentHelper: prueba de configuración",
					"Si lees esto, el envío de correo funciona.\n\nNo hace falta responder.");
			return new ResultadoPrueba(true, "Enviado a " + a.getCorreoRemitente(),
					(int) (System.currentTimeMillis() - inicio));
		} catch (RuntimeException e) {
			log.warn("Prueba de correo fallida: {}", e.getMessage());
			return new ResultadoPrueba(false, mensajeDe(e), (int) (System.currentTimeMillis() - inicio));
		}
	}

	@Transactional(readOnly = true)
	public ResultadoPrueba probarTelegram() {
		Ajustes a = repoAjustes.findFirstBy().orElseGet(Ajustes::new);
		if (!a.telegramConfigurado()) {
			return new ResultadoPrueba(false, "Faltan el token del bot o el identificador de chat", 0);
		}
		long inicio = System.currentTimeMillis();
		try {
			telegram.enviar("<b>RentHelper</b>: prueba de configuración. Si lees esto, funciona.");
			return new ResultadoPrueba(true, "Mensaje enviado al chat " + a.getTelegramChatId(),
					(int) (System.currentTimeMillis() - inicio));
		} catch (RuntimeException e) {
			log.warn("Prueba de Telegram fallida: {}", e.getMessage());
			return new ResultadoPrueba(false, mensajeDe(e), (int) (System.currentTimeMillis() - inicio));
		}
	}

	/** Aplana la cadena de causas: el mensaje de arriba suele ser genérico y no dice nada. */
	private static String mensajeDe(Throwable e) {
		StringBuilder sb = new StringBuilder();
		for (Throwable actual = e; actual != null; actual = actual.getCause()) {
			if (actual.getMessage() != null && sb.indexOf(actual.getMessage()) < 0) {
				sb.append(sb.isEmpty() ? "" : " · ").append(actual.getMessage());
			}
		}
		return sb.isEmpty() ? e.getClass().getSimpleName() : sb.toString();
	}

	// --- Andamiaje -----------------------------------------------------------------------

	private void aplicar(ProveedorLlm proveedor, DtosAdmin.PeticionProveedor peticion) {
		if (tieneValor(peticion.nombre())) {
			proveedor.setNombre(peticion.nombre().trim());
		}
		if (tieneValor(peticion.urlBase())) {
			proveedor.setUrlBase(normalizarUrl(peticion.urlBase()));
		}
		if (tieneValor(peticion.modelo())) {
			proveedor.setModelo(peticion.modelo().trim());
		}
		if (tieneValor(peticion.apiKey())) {
			proveedor.setApiKeyCifrada(cifrador.cifrar(peticion.apiKey().trim()));
		}
		if (peticion.activo() != null) {
			proveedor.setActivo(peticion.activo());
		}
		if (peticion.aptoDatosReales() != null) {
			proveedor.setAptoDatosReales(peticion.aptoDatosReales());
		}
		if (peticion.sombra() != null) {
			proveedor.setSombra(peticion.sombra());
		}
		if (peticion.limiteMensualLlamadas() != null) {
			proveedor.setLimiteMensualLlamadas(peticion.limiteMensualLlamadas());
		}
	}

	private DtosAdmin.ProveedorDto aDto(ProveedorLlm p) {
		return new DtosAdmin.ProveedorDto(p.getId(), p.getNombre(), p.getUrlBase(), p.getModelo(),
				p.getApiKeyCifrada() != null, cifrador.pista(p.getApiKeyCifrada()), p.isActivo(),
				p.getOrden(), p.isAptoDatosReales(), p.isSombra(), p.getLimiteCrudo());
	}

	/**
	 * La url tiene que llevar la versión.
	 *
	 * <p>El cliente sólo le añade {@code /chat/completions}. Sin el {@code /v1} el proveedor
	 * responde un 404 que habla de una ruta que tú nunca escribiste, y cuesta un rato entenderlo.
	 * Quitar la barra final evita la otra mitad del problema: la doble barra.
	 */
	private String normalizarUrl(String url) {
		String limpia = url.trim();
		return limpia.endsWith("/") ? limpia.substring(0, limpia.length() - 1) : limpia;
	}

	private int siguienteOrden() {
		return repoProveedores.findAllByOrderByOrdenAscNombreAsc().stream()
				.mapToInt(ProveedorLlm::getOrden).max().orElse(-1) + 1;
	}

	private void renumerar(List<ProveedorLlm> cadena) {
		for (int i = 0; i < cadena.size(); i++) {
			cadena.get(i).setOrden(i);
		}
	}

	private ProveedorLlm buscar(UUID id) {
		return repoProveedores.findById(id)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Proveedor inexistente"));
	}

	private static boolean tieneValor(String texto) {
		return texto != null && !texto.isBlank();
	}

	private static String limpiar(String texto) {
		return texto == null || texto.isBlank() ? null : texto.trim();
	}

	private static void exigir(String valor, String campo, String mensaje) {
		if (!tieneValor(valor)) {
			throw ExcepcionNegocio.invalido(campo, mensaje);
		}
	}
}

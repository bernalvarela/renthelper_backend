package es.agata.renthelper.api.admin;

import es.agata.renthelper.config.UsuarioAutenticado;
import es.agata.renthelper.llm.ServicioInformeComparativo;
import es.agata.renthelper.outbox.ServicioOutbox;
import es.agata.renthelper.servicio.ServicioAdmin;
import es.agata.renthelper.llm.ResultadoPrueba;
import es.agata.renthelper.servicio.ServicioAjustes;
import es.agata.renthelper.servicio.ServicioComparacion;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class ControladorAdmin {

	private final ServicioAdmin servicio;
	private final ServicioComparacion comparacion;
	private final ServicioOutbox outbox;
	private final ServicioAjustes servicioAjustes;
	private final ServicioInformeComparativo informes;

	public ControladorAdmin(ServicioAdmin servicio, ServicioComparacion comparacion,
	                        ServicioOutbox outbox, ServicioAjustes servicioAjustes,
	                        ServicioInformeComparativo informes) {
		this.informes = informes;
		this.servicio = servicio;
		this.comparacion = comparacion;
		this.outbox = outbox;
		this.servicioAjustes = servicioAjustes;
	}

	/** Lo que está haciendo el outbox: cola, reintentos y esperas por cuota. */
	@GetMapping("/actividad")
	public DtosAdmin.Actividad actividad() {
		return outbox.actividad();
	}

	/** Anular una operación en cola que no va a salir, en vez de esperar a que agote sus reintentos. */
	@PostMapping("/actividad/{id}/cancelar")
	public Map<String, Object> cancelarOperacion(@PathVariable UUID id) {
		outbox.cancelar(id);
		return Map.of("cancelada", true);
	}

	@GetMapping("/sesion")
	public DtosAdmin.Sesion sesion(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return new DtosAdmin.Sesion(usuario.email(), usuario.rol());
	}

	// --- Anuncios ----------------------------------------------------------------------

	@GetMapping("/anuncios")
	public List<DtosAdmin.ResumenAnuncio> anuncios() {
		return servicio.listarAnuncios();
	}

	@GetMapping("/anuncios/{id}")
	public DtosAdmin.ResumenAnuncio anuncio(@PathVariable UUID id) {
		return servicio.anuncio(id);
	}

	@PostMapping("/anuncios")
	public DtosAdmin.ResumenAnuncio crear(@RequestBody DtosAdmin.PeticionAnuncio peticion) {
		return servicio.crearAnuncio(peticion);
	}

	@PatchMapping("/anuncios/{id}")
	public DtosAdmin.ResumenAnuncio actualizar(@PathVariable UUID id,
	                                           @RequestBody DtosAdmin.PeticionAnuncio peticion) {
		return servicio.actualizarAnuncio(id, peticion);
	}

	// --- Triaje ------------------------------------------------------------------------

	@GetMapping("/anuncios/{id}/candidaturas")
	public List<DtosAdmin.FilaCandidatura> candidaturas(
			@PathVariable UUID id,
			@RequestParam(defaultValue = "false") boolean incluirBorradores) {
		return servicio.candidaturas(id, incluirBorradores);
	}

	@GetMapping("/candidaturas/{id}")
	public DtosAdmin.FichaCandidatura ficha(@PathVariable UUID id) {
		return servicio.ficha(id);
	}

	@PostMapping("/candidaturas/{id}/triaje")
	public DtosAdmin.FilaCandidatura triar(@PathVariable UUID id,
	                                       @RequestBody DtosAdmin.PeticionTriaje peticion) {
		return servicio.triar(id, peticion);
	}

	/**
	 * Supresión definitiva (RGPD art. 17). Distinta de descartar, que es triaje y conserva el
	 * registro. DELETE y no POST porque es exactamente eso: borrar el recurso.
	 */
	@DeleteMapping("/candidaturas/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void borrarCandidatura(@PathVariable UUID id) {
		servicio.borrarCandidatura(id);
	}

	/** Lanza la evaluación con IA que se había omitido por nombre aparentemente inventado. */
	@PostMapping("/candidaturas/{id}/evaluar-ia")
	public Map<String, Object> evaluarConIa(@PathVariable UUID id) {
		servicio.evaluarConIa(id);
		return Map.of("encolada", true);
	}

	/** Repuntuar una sola. El lote por anuncio no sirve cuando el problema es de una concreta. */
	@PostMapping("/candidaturas/{id}/reevaluar")
	public Map<String, Object> reevaluarCandidatura(@PathVariable UUID id,
	                                               @RequestParam(required = false) UUID proveedorId) {
		servicio.reevaluarCandidatura(id, proveedorId);
		return Map.of("encolada", true);
	}

	/** Anular las evaluaciones en cola de una candidatura, desde su fila. */
	@PostMapping("/candidaturas/{id}/cancelar-evaluacion")
	public Map<String, Object> cancelarEvaluacion(@PathVariable UUID id) {
		return Map.of("canceladas", outbox.cancelarEvaluaciones(id));
	}

	/** Repuntuar en lote tras cambiar la rúbrica. El histórico anterior se conserva. */
	@PostMapping("/anuncios/{id}/reevaluar")
	public Map<String, Object> reevaluar(@PathVariable UUID id) {
		return Map.of("encoladas", servicio.reevaluar(id));
	}

	@GetMapping("/anuncios/{id}/embudo")
	public List<DtosAdmin.PasoEmbudo> embudo(@PathVariable UUID id) {
		return servicio.embudo(id);
	}

	/** Los finalistas que se pueden comparar, el máximo y el último informe comparativo. */
	@GetMapping("/anuncios/{id}/informe-comparativo")
	public DtosAdmin.EstadoComparativa informeComparativo(@PathVariable UUID id) {
		return informes.estado(id);
	}

	/** Una llamada al modelo con esos finalistas. Tarda lo que tarde el modelo (hasta un minuto). */
	@PostMapping("/anuncios/{id}/informe-comparativo")
	public DtosAdmin.InformeComparativoDto generarInformeComparativo(@PathVariable UUID id,
	                                                                @RequestBody DtosAdmin.PeticionComparativa peticion) {
		return informes.generar(id, peticion.candidaturaIds());
	}

	@GetMapping("/anuncios/{id}/comparacion")
	public ServicioComparacion.Comparacion comparacion(@PathVariable UUID id) {
		return comparacion.comparar(id);
	}

	// --- Esquemas y rúbricas -----------------------------------------------------------

	@GetMapping("/esquemas")
	public List<DtosAdmin.ResumenEsquema> esquemas() {
		return servicio.listarEsquemas();
	}

	@PutMapping("/esquemas/{id}")
	public DtosAdmin.ResultadoGuardado guardarEsquema(@PathVariable UUID id,
	                                                  @RequestBody DtosAdmin.PeticionEsquema peticion,
	                                                  @RequestParam(defaultValue = "es") List<String> idiomas) {
		return servicio.guardarEsquema(id, peticion, idiomas);
	}

	@PostMapping("/esquemas")
	public DtosAdmin.ResultadoGuardado crearEsquema(@RequestBody DtosAdmin.PeticionEsquema peticion,
	                                                @RequestParam(defaultValue = "es") List<String> idiomas) {
		return servicio.guardarEsquema(null, peticion, idiomas);
	}

	@PostMapping("/esquemas/{id}/duplicar")
	public DtosAdmin.ResumenEsquema duplicarEsquema(@PathVariable UUID id) {
		return servicio.duplicarEsquema(id);
	}

	/** Esqueleto mínimo que ya pasa la validación, para escribir encima. */
	@PostMapping("/esquemas/vacio")
	public DtosAdmin.ResumenEsquema esquemaVacio(@RequestParam(defaultValue = "es") List<String> idiomas) {
		return servicio.crearEsquemaVacio(idiomas);
	}

	@PostMapping("/rubricas/{id}/duplicar")
	public DtosAdmin.ResumenRubrica duplicarRubrica(@PathVariable UUID id) {
		return servicio.duplicarRubrica(id);
	}

	@GetMapping("/rubricas")
	public List<DtosAdmin.ResumenRubrica> rubricas() {
		return servicio.listarRubricas();
	}

	@PutMapping("/rubricas/{id}")
	public DtosAdmin.ResultadoGuardado guardarRubrica(@PathVariable UUID id,
	                                                  @RequestBody DtosAdmin.PeticionRubrica peticion,
	                                                  @RequestParam UUID formSchemaId) {
		return servicio.guardarRubrica(id, peticion, formSchemaId);
	}

	@PostMapping("/rubricas")
	public DtosAdmin.ResultadoGuardado crearRubrica(@RequestBody DtosAdmin.PeticionRubrica peticion,
	                                                @RequestParam UUID formSchemaId) {
		return servicio.guardarRubrica(null, peticion, formSchemaId);
	}

	// --- Configuración: modelos, correo y Telegram ---------------------------------------

	@GetMapping("/proveedores")
	public List<DtosAdmin.ProveedorDto> proveedores() {
		return servicioAjustes.proveedores();
	}

	@PostMapping("/proveedores")
	public DtosAdmin.ProveedorDto crearProveedor(@RequestBody DtosAdmin.PeticionProveedor peticion) {
		return servicioAjustes.crearProveedor(peticion);
	}

	/** PATCH y no PUT: la clave de API ausente significa «no la toques», no «bórrala». */
	@PatchMapping("/proveedores/{id}")
	public DtosAdmin.ProveedorDto actualizarProveedor(@PathVariable UUID id,
	                                                  @RequestBody DtosAdmin.PeticionProveedor peticion) {
		return servicioAjustes.actualizarProveedor(id, peticion);
	}

	@DeleteMapping("/proveedores/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void borrarProveedor(@PathVariable UUID id) {
		servicioAjustes.borrarProveedor(id);
	}

	/** Devuelve la cadena entera: al mover uno cambian los órdenes de varios. */
	@PostMapping("/proveedores/{id}/mover")
	public List<DtosAdmin.ProveedorDto> moverProveedor(@PathVariable UUID id,
	                                                   @RequestParam int delta) {
		return servicioAjustes.mover(id, delta);
	}

	@GetMapping("/ajustes")
	public DtosAdmin.AjustesDto ajustes() {
		return servicioAjustes.ajustes();
	}

	@PutMapping("/ajustes")
	public DtosAdmin.AjustesDto guardarAjustes(@RequestBody DtosAdmin.PeticionAjustes peticion) {
		return servicioAjustes.guardarAjustes(peticion);
	}

	/** Los tres botones de «comprobar». Devuelven el error tal cual: es lo que se puede arreglar. */
	@PostMapping("/proveedores/{id}/probar")
	public ResultadoPrueba probarProveedor(@PathVariable UUID id) {
		return servicioAjustes.probarProveedor(id);
	}

	@PostMapping("/ajustes/probar-correo")
	public ResultadoPrueba probarCorreo() {
		return servicioAjustes.probarCorreo();
	}

	@PostMapping("/ajustes/probar-telegram")
	public ResultadoPrueba probarTelegram() {
		return servicioAjustes.probarTelegram();
	}

	// --- Tu nota y tus apuntes -----------------------------------------------------------

	/** Separado de /triaje a propósito: puntuar no debería citar a nadie por su cuenta. */
	@PostMapping("/candidaturas/{id}/nota")
	public DtosAdmin.FilaCandidatura anotarNota(@PathVariable UUID id,
	                                            @RequestBody DtosAdmin.PeticionNota peticion) {
		return servicio.anotarNota(id, peticion);
	}

	@PostMapping("/candidaturas/{id}/comentarios")
	public DtosAdmin.ComentarioDto comentar(@PathVariable UUID id,
	                                        @RequestBody DtosAdmin.PeticionComentario peticion) {
		return servicio.comentar(id, peticion);
	}

	@DeleteMapping("/comentarios/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void borrarComentario(@PathVariable UUID id) {
		servicio.borrarComentario(id);
	}
}

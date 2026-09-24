package es.agata.renthelper.api;

import org.springframework.http.HttpStatus;

public class ExcepcionNegocio extends RuntimeException {

	private final HttpStatus estado;
	private final String codigo;

	public ExcepcionNegocio(HttpStatus estado, String codigo, String mensaje) {
		super(mensaje);
		this.estado = estado;
		this.codigo = codigo;
	}

	public static ExcepcionNegocio noEncontrado(String mensaje) {
		return new ExcepcionNegocio(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", mensaje);
	}

	public static ExcepcionNegocio conflicto(String codigo, String mensaje) {
		return new ExcepcionNegocio(HttpStatus.CONFLICT, codigo, mensaje);
	}

	public static ExcepcionNegocio invalido(String codigo, String mensaje) {
		return new ExcepcionNegocio(HttpStatus.BAD_REQUEST, codigo, mensaje);
	}

	public HttpStatus getEstado() {
		return estado;
	}

	public String getCodigo() {
		return codigo;
	}
}

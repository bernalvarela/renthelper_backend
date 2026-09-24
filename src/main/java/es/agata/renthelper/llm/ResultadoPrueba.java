package es.agata.renthelper.llm;

/**
 * Resultado de comprobar que un proveedor responde.
 *
 * <p>{@code detalle} lleva la respuesta del modelo si fue bien, y el error completo si no.
 * Enseñarlo tal cual es lo útil: «401» y «el modelo no existe» se arreglan de formas muy
 * distintas, y un «no funciona» genérico obligaría a ir al log.
 */
public record ResultadoPrueba(boolean ok, String detalle, int latenciaMs) {
}

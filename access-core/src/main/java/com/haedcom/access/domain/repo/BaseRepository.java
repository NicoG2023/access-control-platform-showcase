package com.haedcom.access.domain.repo;

import java.util.List;
import java.util.Optional;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Repositorio base genérico para acceso a datos mediante JPA.
 *
 * <p>
 * Encapsula operaciones CRUD comunes sobre entidades JPA y expone el {@link EntityManager}
 * inyectado para que repositorios concretos puedan construir consultas específicas.
 * </p>
 *
 * <p>
 * Este repositorio:
 * <ul>
 * <li>No contiene lógica de negocio.</li>
 * <li>Opera sobre entidades JPA administradas por el {@link EntityManager}.</li>
 * <li>Asume que el control transaccional se realiza en la capa de servicio.</li>
 * </ul>
 * </p>
 *
 * @param <T> tipo de la entidad
 * @param <ID> tipo del identificador de la entidad
 */
public class BaseRepository<T, ID> {

  /**
   * {@link EntityManager} inyectado por el contenedor.
   *
   * <p>
   * Es protegido para que repositorios concretos puedan usarlo directamente al definir consultas
   * JPQL o Criteria.
   * </p>
   */
  @Inject
  protected EntityManager em;

  /**
   * Clase de la entidad gestionada por este repositorio.
   */
  private final Class<T> entityClass;

  /**
   * Constructor protegido.
   *
   * <p>
   * Normalmente es invocado por repositorios concretos que extienden esta clase.
   * </p>
   *
   * @param entityClass clase de la entidad
   */
  protected BaseRepository(Class<T> entityClass) {
    this.entityClass = entityClass;
  }

  /**
   * Busca una entidad por su identificador.
   *
   * @param id identificador de la entidad
   * @return {@link Optional} con la entidad si existe; vacío en caso contrario
   */
  public Optional<T> findById(ID id) {
    return Optional.ofNullable(em.find(entityClass, id));
  }

  /**
   * Persiste una nueva entidad en el contexto de persistencia.
   *
   * <p>
   * La operación {@code persist} no garantiza que el {@code INSERT} se ejecute inmediatamente en la
   * base de datos; esto suele ocurrir al hacer {@code flush} o al finalizar la transacción.
   * </p>
   *
   * @param entity entidad a persistir
   * @return la misma entidad persistida
   */
  public T persist(T entity) {
    em.persist(entity);
    return entity;
  }

  /**
   * Fusiona el estado de una entidad separada en el contexto de persistencia.
   *
   * <p>
   * Devuelve la instancia administrada (managed) resultante.
   * </p>
   *
   * @param entity entidad separada
   * @return entidad administrada
   */
  public T merge(T entity) {
    return em.merge(entity);
  }

  /**
   * Elimina una entidad administrada del contexto de persistencia.
   *
   * <p>
   * La entidad debe estar administrada; de lo contrario, debe ser previamente obtenida o fusionada.
   * </p>
   *
   * @param entity entidad a eliminar
   */
  public void delete(T entity) {
    em.remove(entity);
  }

  /**
   * Lista todas las entidades de este tipo de forma paginada.
   *
   * <p>
   * Este método es genérico y no aplica filtros; los repositorios concretos deberían preferir
   * métodos específicos que incluyan restricciones de negocio (por ejemplo, por tenant).
   * </p>
   *
   * @param page número de página (base 0)
   * @param size tamaño de página
   * @return lista de entidades
   */
  public List<T> listAll(int page, int size) {
    return em.createQuery("from " + entityClass.getName(), entityClass).setFirstResult(page * size)
        .setMaxResults(size).getResultList();
  }

  /**
   * Fuerza la sincronización del contexto de persistencia con la base de datos.
   *
   * <p>
   * En JPA, operaciones como {@link #persist(Object)}, {@link #merge(Object)} o cambios sobre
   * entidades administradas pueden quedar pendientes hasta el commit de la transacción.
   * </p>
   *
   * <p>
   * Invocar {@code flush()} fuerza la ejecución inmediata de los SQL pendientes, permitiendo:
   * <ul>
   * <li>Detectar tempranamente errores de integridad (p.ej. violaciones de UNIQUE).</li>
   * <li>Traducir dichas violaciones a excepciones de negocio dentro del alcance del servicio.</li>
   * <li>Evitar fallos "tardíos" al finalizar la transacción.</li>
   * </ul>
   * </p>
   *
   * <p>
   * Este método debe usarse de forma explícita y consciente, ya que puede introducir round-trips
   * adicionales a la base de datos.
   * </p>
   */
  public void flush() {
    em.flush();
  }
}

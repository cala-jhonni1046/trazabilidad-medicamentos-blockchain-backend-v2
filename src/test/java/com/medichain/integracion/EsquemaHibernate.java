package com.medichain.integracion;

import jakarta.persistence.Entity;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utilidad de prueba EsquemaHibernate en MediChain.
 * Arma, fuera del contexto de Spring, una SessionFactory de Hibernate con
 * TODAS las entidades (@Entity de com.medichain) contra una base dada, para:
 * <ul>
 *   <li>crear el esquema tal como lo deducen las entidades (hbm2ddl "create"):
 *       es la referencia contra la que se comparan las migraciones;</li>
 *   <li>validarlo (hbm2ddl "validate"), igual que la app al arrancar.</li>
 * </ul>
 * Usa las mismas estrategias de nombres que la app (las lee de la
 * EntityManagerFactory del contexto), así el esquema deducido es el mismo.
 * Hibernate 7 ya no tiene SchemaExport: el esquema se crea o valida al
 * construir la SessionFactory.
 */
public final class EsquemaHibernate {

    /** Ajustes de la app que definen los nombres de tablas y columnas. */
    private static final List<String> AJUSTES_DE_NOMBRES = List.of(AvailableSettings.PHYSICAL_NAMING_STRATEGY,
            AvailableSettings.IMPLICIT_NAMING_STRATEGY);

    private EsquemaHibernate() {
    }

    /** Crea en la base (vacía) el esquema que deducen las entidades. */
    public static void crear(String url, String usuario, String clave, Map<String, Object> ajustesDeLaApp) {
        construir(url, usuario, clave, ajustesDeLaApp, "create");
    }

    /** Valida la base contra las entidades; lanza la excepción de Hibernate si no coinciden. */
    public static void validar(String url, String usuario, String clave, Map<String, Object> ajustesDeLaApp) {
        construir(url, usuario, clave, ajustesDeLaApp, "validate");
    }

    /** Clases @Entity del proyecto. */
    public static List<Class<?>> entidades() {
        ClassPathScanningCandidateComponentProvider escaner = new ClassPathScanningCandidateComponentProvider(false);
        escaner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<Class<?>> entidades = new ArrayList<>();
        for (BeanDefinition definicion : escaner.findCandidateComponents("com.medichain")) {
            try {
                entidades.add(Class.forName(definicion.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("No se pudo cargar la entidad " + definicion.getBeanClassName(), e);
            }
        }
        return entidades;
    }

    /** Construye y cierra una SessionFactory con la acción de esquema dada ("create" o "validate"). */
    private static void construir(String url, String usuario, String clave, Map<String, Object> ajustesDeLaApp,
                                  String accion) {
        StandardServiceRegistryBuilder constructor = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.JAKARTA_JDBC_URL, url)
                .applySetting(AvailableSettings.JAKARTA_JDBC_USER, usuario)
                .applySetting(AvailableSettings.JAKARTA_JDBC_PASSWORD, clave)
                .applySetting(AvailableSettings.HBM2DDL_AUTO, accion);
        for (String ajuste : AJUSTES_DE_NOMBRES) {
            Object valor = ajustesDeLaApp.get(ajuste);
            if (valor == null) {
                throw new IllegalStateException("La app no informa el ajuste " + ajuste);
            }
            constructor.applySetting(ajuste, valor);
        }
        StandardServiceRegistry registro = constructor.build();
        try {
            MetadataSources fuentes = new MetadataSources(registro);
            for (Class<?> entidad : entidades()) {
                fuentes.addAnnotatedClass(entidad);
            }
            // Crear o validar el esquema ocurre al construir la SessionFactory.
            try (SessionFactory fabrica = fuentes.buildMetadata().buildSessionFactory()) {
                if (!fabrica.isOpen()) {
                    throw new IllegalStateException("Hibernate no pudo abrir la SessionFactory de referencia");
                }
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registro);
        }
    }
}

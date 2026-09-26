package com.tms.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Mise à niveau du schéma pour les bases créées par une version antérieure.
 *
 * Hibernate fige la liste des valeurs d'un enum au moment où il crée la table :
 * <ul>
 *   <li>H2 : colonne de type SQL {@code ENUM('A','B',…)} ;</li>
 *   <li>PostgreSQL : colonne VARCHAR + contrainte {@code CHECK (col IN (…))}.</li>
 * </ul>
 * {@code ddl-auto=update} ne les met jamais à jour : un nouveau type de tâche serait refusé.
 * On convertit donc ces colonnes en VARCHAR et on retire les contraintes figées (la validation
 * reste assurée par l'application). À remplacer par des migrations Flyway en production.
 */
@Component
@Order(0)
public class SchemaUpgrade implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaUpgrade.class);

    private final JdbcTemplate jdbc;

    public SchemaUpgrade(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        convertH2EnumColumns();
        dropFrozenEnumChecks();
    }

    /** H2 : colonnes ENUM → VARCHAR(32). */
    private void convertH2EnumColumns() {
        try {
            List<Map<String, Object>> cols = jdbc.queryForList(
                    "SELECT table_name, column_name FROM information_schema.columns "
                            + "WHERE upper(data_type) = 'ENUM' AND lower(table_schema) = 'public'");
            for (Map<String, Object> c : cols) {
                String table = ident(c.get("table_name"));
                String column = ident(c.get("column_name"));
                jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " SET DATA TYPE VARCHAR(32)");
                log.info("Colonne enum convertie en VARCHAR : {}.{}", table, column);
            }
        } catch (Exception e) {
            log.debug("Conversion des colonnes ENUM ignorée : {}", e.getMessage());
        }
    }

    /** PostgreSQL : contraintes CHECK figées sur les valeurs d'enum de tasks.type. */
    private void dropFrozenEnumChecks() {
        try {
            List<String> names = jdbc.queryForList(
                    "SELECT tc.constraint_name FROM information_schema.table_constraints tc "
                            + "JOIN information_schema.check_constraints cc ON tc.constraint_name = cc.constraint_name "
                            + "WHERE lower(tc.table_name) = 'tasks' AND tc.constraint_type = 'CHECK' "
                            + "AND cc.check_clause LIKE '%INSTALL_APP%'", String.class);
            for (String name : names) {
                jdbc.execute("ALTER TABLE tasks DROP CONSTRAINT \"" + name.replace("\"", "") + "\"");
                log.info("Contrainte figée retirée sur tasks.type : {}", name);
            }
        } catch (Exception e) {
            log.debug("Vérification des contraintes ignorée : {}", e.getMessage());
        }
    }

    private static String ident(Object name) {
        return "\"" + String.valueOf(name).replace("\"", "") + "\"";
    }
}

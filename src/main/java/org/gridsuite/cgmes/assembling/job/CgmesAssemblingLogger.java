/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.cgmes.assembling.job;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

import static com.datastax.driver.core.querybuilder.QueryBuilder.*;

/**
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class CgmesAssemblingLogger implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CgmesAssemblingLogger.class);

    private final CassandraConnector connector = new CassandraConnector();

    private static final String KEYSPACE_CGMES_ASSEMBLING = "cgmes_assembling";

    private static final String HANDLED_FILES_TABLE = "handled_files";
    private static final String IMPORTED_FILES_TABLE = "imported_files";
    private static final String FILENAME_BY_UUID_TABLE = "filename_by_uuid";
    private static final String UUID_BY_FILENAME_TABLE = "uuid_by_filename";
    private static final String DEPENDENCIES_TABLE = "dependencies";

    private static final String FILENAME_COLUMN = "filename";
    private static final String ORIGIN_COLUMN = "origin";
    private static final String IMPORT_DATE_COLUMN = "import_date";
    private static final String UUID_COLUMN = "uuid";
    private static final String DEPENDENCIES_COLUMN = "dependencies";

    private PreparedStatement psInsertHandledFile;
    private PreparedStatement psInsertImportedFile;
    private PreparedStatement psInsertFileNameByUUID;
    private PreparedStatement psInsertUuidByFilename;
    private PreparedStatement psInsertDependencies;

    public void connectDb(String hostname, int port) {
        connector.connect(hostname, port);

        psInsertHandledFile = connector.getSession().prepare(insertInto(KEYSPACE_CGMES_ASSEMBLING, HANDLED_FILES_TABLE)
                .value(FILENAME_COLUMN, bindMarker())
                .value(ORIGIN_COLUMN, bindMarker())
                .value(IMPORT_DATE_COLUMN, bindMarker()));

        psInsertFileNameByUUID = connector.getSession().prepare(insertInto(KEYSPACE_CGMES_ASSEMBLING, FILENAME_BY_UUID_TABLE)
                .value(UUID_COLUMN, bindMarker())
                .value(FILENAME_COLUMN, bindMarker())
                .value(ORIGIN_COLUMN, bindMarker()));

        psInsertUuidByFilename = connector.getSession().prepare(insertInto(KEYSPACE_CGMES_ASSEMBLING, UUID_BY_FILENAME_TABLE)
                .value(FILENAME_COLUMN, bindMarker())
                .value(UUID_COLUMN, bindMarker())
                .value(ORIGIN_COLUMN, bindMarker()));

        psInsertImportedFile = connector.getSession().prepare(insertInto(KEYSPACE_CGMES_ASSEMBLING, IMPORTED_FILES_TABLE)
                .value(FILENAME_COLUMN, bindMarker())
                .value(ORIGIN_COLUMN, bindMarker())
                .value(IMPORT_DATE_COLUMN, bindMarker()));

        psInsertDependencies = connector.getSession().prepare(insertInto(KEYSPACE_CGMES_ASSEMBLING, DEPENDENCIES_TABLE)
                .value(UUID_COLUMN, bindMarker())
                .value(DEPENDENCIES_COLUMN, bindMarker()));
    }

    public boolean isHandledFile(String filename, String origin) {
        ResultSet resultSet = connector.getSession().execute(select(FILENAME_COLUMN,
                ORIGIN_COLUMN,
                IMPORT_DATE_COLUMN)
                .from(KEYSPACE_CGMES_ASSEMBLING, HANDLED_FILES_TABLE)
                .where(eq(FILENAME_COLUMN, filename)).and(eq(ORIGIN_COLUMN, origin)));
        Row one = resultSet.one();
        return one != null;
    }

    public boolean isImportedFile(String filename, String origin) {
        ResultSet resultSet = connector.getSession().execute(select(FILENAME_COLUMN,
                ORIGIN_COLUMN,
                IMPORT_DATE_COLUMN)
                .from(KEYSPACE_CGMES_ASSEMBLING, IMPORTED_FILES_TABLE)
                .where(eq(FILENAME_COLUMN, filename)).and(eq(ORIGIN_COLUMN, origin)));
        Row one = resultSet.one();
        return one != null;
    }

    public String getUuidByFileName(String filename, String origin) {
        ResultSet resultSet = connector.getSession().execute(select(UUID_COLUMN, FILENAME_COLUMN,
                ORIGIN_COLUMN)
                .from(KEYSPACE_CGMES_ASSEMBLING, UUID_BY_FILENAME_TABLE)
                .where(eq(FILENAME_COLUMN, filename)).and(eq(ORIGIN_COLUMN, origin)));
        Row one = resultSet.one();
        return one != null ? one.getString(0) : null;
    }

    public String getFileNameByUuid(String uuid, String origin) {
        ResultSet resultSet = connector.getSession().execute(select(FILENAME_COLUMN, UUID_COLUMN,
                ORIGIN_COLUMN)
                .from(KEYSPACE_CGMES_ASSEMBLING, FILENAME_BY_UUID_TABLE)
                .where(eq(UUID_COLUMN, uuid)).and(eq(ORIGIN_COLUMN, origin)));
        Row one = resultSet.one();
        return one != null ? one.getString(0) : null;
    }

    public List<String> getDependencies(String uuid) {
        ResultSet resultSet = connector.getSession().execute(select(UUID_COLUMN, DEPENDENCIES_COLUMN)
                .from(KEYSPACE_CGMES_ASSEMBLING, DEPENDENCIES_TABLE)
                .where(eq(UUID_COLUMN, uuid)));
        Row one = resultSet.one();
        return one != null ? one.getList(1, String.class) : null;
    }

    public void logFileAvailable(String fileName, String uuid, String origin, Date date) {
        connector.getSession().execute(psInsertHandledFile.bind(fileName, origin, date));
        connector.getSession().execute(psInsertFileNameByUUID.bind(uuid, fileName, origin));
        connector.getSession().execute(psInsertUuidByFilename.bind(fileName, uuid, origin));
    }

    public void logFileImported(String fileName, String origin, Date date) {
        connector.getSession().execute(psInsertImportedFile.bind(fileName, origin, date));
    }

    public void logFileDependencies(String uuid, List<String> dependencies) {
        connector.getSession().execute(psInsertDependencies.bind(uuid, dependencies));
        LOGGER.info("Add dependancy between file {} and files {}", uuid, dependencies);
    }

    public void close() {
        connector.close();
    }
}

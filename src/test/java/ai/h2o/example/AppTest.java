/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package ai.h2o.example;

import com.github.sakserv.minicluster.impl.HiveLocalMetaStore;
import com.github.sakserv.minicluster.impl.HiveLocalServer2;
import org.apache.hadoop.hive.conf.HiveConf;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class AppTest {
    private HiveLocalMetaStore hiveLocalMetaStore;
    private HiveLocalServer2 hiveLocalServer2;

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        final Path hiveDir = tmpDir.newFolder("hive-mini-cluster").toPath();
        final String hiveMetastoreDerbyDbDir = hiveDir.resolve("metastore_db").toString();
        final String hiveScratchDir = hiveDir.resolve("hdfs-scratchdir").toString();
        final String hiveWarehouseDir = hiveDir.resolve("warehouse").toString();
        hiveLocalMetaStore = new HiveLocalMetaStore.Builder()
                .setHiveMetastoreHostname("localhost")
                .setHiveMetastorePort(9083)
                .setHiveMetastoreDerbyDbDir(hiveMetastoreDerbyDbDir)
                .setHiveScratchDir(hiveScratchDir)
                .setHiveWarehouseDir(hiveWarehouseDir)
                .setHiveConf(new HiveConf())
                .build();
        hiveLocalMetaStore.start();
        hiveLocalServer2 = new HiveLocalServer2.Builder()
                .setHiveServer2Hostname("localhost")
                .setHiveServer2Port(10000)
                .setHiveMetastoreHostname("localhost")
                .setHiveMetastorePort(9083)
                .setHiveMetastoreDerbyDbDir(hiveMetastoreDerbyDbDir)
                .setHiveScratchDir(hiveScratchDir)
                .setHiveWarehouseDir(hiveWarehouseDir)
                .setZookeeperConnectionString("")
                .setHiveConf(new HiveConf())
                .build();
        hiveLocalServer2.start();
    }

    @After
    public void tearDown() throws Exception {
        hiveLocalServer2.stop();
        hiveLocalMetaStore.stop();
    }

    @Test
    public void testHiveConnector() {
        App app = new App();
        assertEquals("{\"success\":true}", app.runConnector("NOAUTH"));
    }
}

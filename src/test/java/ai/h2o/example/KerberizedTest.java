package ai.h2o.example;

import com.github.sakserv.minicluster.impl.HdfsLocalCluster;
import com.github.sakserv.minicluster.impl.HiveLocalMetaStore;
import com.github.sakserv.minicluster.impl.HiveLocalServer2;
import com.github.sakserv.minicluster.impl.KdcLocalCluster;
import com.github.sakserv.minicluster.impl.MRLocalCluster;
import com.github.sakserv.minicluster.impl.ZookeeperLocalCluster;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.security.UserGroupInformation;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.Assert.assertEquals;

public class KerberizedTest {
    private static final String USER_NAME = "alice";

    private KdcLocalCluster kdcLocalCluster;
    private HdfsLocalCluster hdfsLocalCluster;
    private MRLocalCluster mrLocalCluster;
    private ZookeeperLocalCluster zookeeperLocalCluster;
    private HiveLocalMetaStore hiveLocalMetaStore;
    private HiveLocalServer2 hiveLocalServer2;

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder(Paths.get("", "build").toFile());

    @Before
    public void setUp() throws Exception {
        final String kdcBaseDir = tmpDir.newFolder("kdc-mini-cluster").getAbsolutePath();
        System.setProperty("KDC_MINI_CLUSTER_BASE_DIR", kdcBaseDir);
        kdcLocalCluster = new KdcLocalCluster.Builder()
                .setHost("127.0.0.1")
                .setPort(34340)
                .setBaseDir(kdcBaseDir)
                .setOrgName("KDC")
                .setOrgDomain("LOCAL")
                .setPrincipals(new String[]{USER_NAME})
                .setKrbInstance("localhost")
                .setInstance("DefaultKrbServer")
                .setTransport("TCP")
                .setMaxTicketLifetime(86400000)
                .setMaxRenewableLifetime(604800000)
                .setDebug(true)
                .build();
        kdcLocalCluster.start();
        final String USER_PRINCIPAL = kdcLocalCluster.getKrbPrincipalWithRealm(USER_NAME);
        final String USER_KEYTAB = kdcLocalCluster.getKeytabForPrincipal(USER_NAME);
        System.out.println("############################################################################# KDC Started");

        final Configuration hdfsConfig = new Configuration();
        // HDFS configurations
        hdfsConfig.setInt("dfs.replication", 1);
        hdfsConfig.setInt("dfs.namenode.http-address", 50070);
        // HDFS configurations - Kerberos
        hdfsConfig.set("hadoop.security.authentication", "kerberos");
        hdfsConfig.setBoolean("hadoop.security.authorization", true);
        hdfsConfig.set("dfs.namenode.kerberos.principal", USER_PRINCIPAL);
        hdfsConfig.set("dfs.namenode.keytab.file", USER_KEYTAB);
        hdfsConfig.set("dfs.secondary.namenode.kerberos.principal", USER_PRINCIPAL);
        hdfsConfig.set("dfs.secondary.namenode.keytab.file", USER_KEYTAB);
        hdfsConfig.set("dfs.datanode.kerberos.principal", USER_PRINCIPAL);
        hdfsConfig.set("dfs.datanode.keytab.file", USER_KEYTAB);
        hdfsConfig.set("dfs.web.authentication.kerberos.principal", USER_PRINCIPAL);
        hdfsConfig.set("dfs.web.authentication.kerberos.keytab", USER_KEYTAB);
        hdfsConfig.setBoolean("dfs.block.access.token.enable", true);
        hdfsConfig.set("dfs.data.transfer.protection", "authentication");
        hdfsConfig.set("dfs.http.policy", "HTTPS_ONLY");
        // HDFS configurations - SSL
        hdfsConfig.set("hadoop.ssl.server.conf", "conf/keytab/ssl-server.xml");
        hdfsConfig.set("dfs.https.server.keystore.resource", hdfsConfig.get("hadoop.ssl.server.conf"));
        hdfsLocalCluster = new HdfsLocalCluster.Builder()
                .setHdfsNamenodePort(9000)
                .setHdfsNamenodeHttpPort(hdfsConfig.getInt("dfs.namenode.http-address", -1))
                .setHdfsTempDir(tmpDir.newFolder("hdfs-mini-cluster").getPath())
                .setHdfsNumDatanodes(1)
                .setHdfsEnablePermissions(true)
                .setHdfsFormat(true)
                .setHdfsEnableRunningUserAsProxyUser(false)
                .setHdfsConfig(hdfsConfig)
                .build();
        hdfsLocalCluster.start();
        System.out.println("############################################################################ HDFS Started");
        // Yarn Configurations
        hdfsConfig.set("yarn.resourcemanager.hostname", "127.0.0.1");
        // Yarn Configurations - Kerberos
        hdfsConfig.set("yarn.resourcemanager.principal", USER_PRINCIPAL);
        hdfsConfig.set("yarn.resourcemanager.keytab", USER_KEYTAB);
        hdfsConfig.set("yarn.nodemanager.principal", USER_PRINCIPAL);
        hdfsConfig.set("yarn.nodemanager.keytab", USER_KEYTAB);
        // MapReduce configurations - Kerberos
        hdfsConfig.set("mapreduce.jobhistory.principal", USER_PRINCIPAL);
        hdfsConfig.set("mapreduce.jobhistory.keytab", USER_KEYTAB);
        mrLocalCluster = new MRLocalCluster.Builder()
                .setNumNodeManagers(1)
                .setJobHistoryAddress(hdfsConfig.get("mapreduce.jobhistory.address"))
                .setResourceManagerHostname(hdfsConfig.get("yarn.resourcemanager.hostname"))
                .setResourceManagerAddress(hdfsConfig.get("yarn.resourcemanager.address"))
                .setResourceManagerSchedulerAddress(hdfsConfig.get("yarn.resourcemanager.scheduler.address"))
                .setResourceManagerResourceTrackerAddress(hdfsConfig.get("yarn.resourcemanager.resource-tracker.address"))
                .setResourceManagerWebappAddress(hdfsConfig.get("yarn.resourcemanager.webapp.address"))
                .setHdfsDefaultFs(hdfsConfig.get("fs.defaultFS"))
                .setUseInJvmContainerExecutor(false)
                .setConfig(hdfsConfig)
                .build();
        mrLocalCluster.start();
        System.out.println("################################################################ Hadoop MapReduce Started");

        zookeeperLocalCluster = new ZookeeperLocalCluster.Builder()
                .setPort(22010)
                .setTempDir(tmpDir.newFolder("zk-mini-cluster").toPath().toString())
                .setZookeeperConnectionString("127.0.0.1:22010")
                .build();
        zookeeperLocalCluster.start();
        System.out.println("############################################################################## ZK Started");
        final Path hiveDir = tmpDir.newFolder("hive-mini-cluster").toPath();
        final String hiveMetastoreDerbyDbDir = hiveDir.resolve("metastore_db").toString();
        final String hiveScratchDir = hiveDir.resolve("hdfs-scratchdir").toString();
        final String hiveWarehouseDir = hiveDir.resolve("warehouse").toString();
        final HiveConf hiveConf = new HiveConf();
        hiveConf.setVar(HiveConf.ConfVars.HIVE_SERVER2_THRIFT_BIND_HOST, "localhost");
        hiveConf.setVar(HiveConf.ConfVars.HIVE_SERVER2_AUTHENTICATION, "KERBEROS");
        hiveConf.setBoolVar(HiveConf.ConfVars.METASTORE_USE_THRIFT_SASL, true);
        hiveConf.setVar(HiveConf.ConfVars.METASTORE_KERBEROS_PRINCIPAL, USER_PRINCIPAL);
        hiveConf.setVar(HiveConf.ConfVars.METASTORE_KERBEROS_KEYTAB_FILE, USER_KEYTAB);
        hiveConf.setVar(HiveConf.ConfVars.HIVE_SERVER2_KERBEROS_PRINCIPAL, USER_PRINCIPAL);
        hiveConf.setVar(HiveConf.ConfVars.HIVE_SERVER2_KERBEROS_KEYTAB, USER_KEYTAB);
        hiveLocalMetaStore = new HiveLocalMetaStore.Builder()
                .setHiveMetastoreHostname("localhost")
                .setHiveMetastorePort(hiveConf.getIntVar(HiveConf.ConfVars.METASTORE_SERVER_PORT))
                .setHiveMetastoreDerbyDbDir(hiveMetastoreDerbyDbDir)
                .setHiveScratchDir(hiveScratchDir)
                .setHiveWarehouseDir(hiveWarehouseDir)
                .setHiveConf(hiveConf)
                .build();
        hiveLocalMetaStore.start();
        System.out.println("################################################################## Hive Metastore Started");
        hiveLocalServer2 = new HiveLocalServer2.Builder()
                .setHiveServer2Hostname(hiveConf.getVar(HiveConf.ConfVars.HIVE_SERVER2_THRIFT_BIND_HOST))
                .setHiveServer2Port(hiveConf.getIntVar(HiveConf.ConfVars.HIVE_SERVER2_THRIFT_PORT))
                .setHiveMetastoreHostname(hiveLocalMetaStore.getHiveMetastoreHostname())
                .setHiveMetastorePort(hiveLocalMetaStore.getHiveMetastorePort())
                .setHiveMetastoreDerbyDbDir(hiveMetastoreDerbyDbDir)
                .setHiveScratchDir(hiveScratchDir)
                .setHiveWarehouseDir(hiveWarehouseDir)
                .setZookeeperConnectionString(zookeeperLocalCluster.getZookeeperConnectionString())
                .setHiveConf(hiveConf)
                .build();
        hiveLocalServer2.start();
        System.out.println("#################################################################### Hive Server2 Started");

        UserGroupInformation.loginUserFromKeytab(USER_PRINCIPAL, USER_KEYTAB);
        Class.forName("org.apache.hive.jdbc.HiveDriver");
        final String url = String.format("jdbc:hive2://%s:%d/default;principal=%s",
                hiveLocalServer2.getHiveServer2Hostname(),
                hiveLocalServer2.getHiveServer2Port(),
                USER_PRINCIPAL);
        final Connection con = DriverManager.getConnection(url,
                HiveConf.ConfVars.METASTORE_CONNECTION_USER_NAME.defaultStrVal,
                HiveConf.ConfVars.METASTOREPWD.defaultStrVal);
        con.createStatement().execute("CREATE EXTERNAL TABLE IF NOT EXISTS grades(" +
                "last_name VARCHAR(50), " +
                "first_name VARCHAR(50), " +
                "ssn VARCHAR(11), " +
                "test1 FLOAT, " +
                "test2 FLOAT, " +
                "test3 FLOAT, " +
                "test4 FLOAT, " +
                "final FLOAT, " +
                "grade VARCHAR(2)" +
                ") ROW FORMAT DELIMITED FIELDS TERMINATED BY ',' tblproperties('skip.header.line.count'='1')");
        con.createStatement().execute("LOAD DATA LOCAL INPATH '" +
                Paths.get("src/test/resources/datasets/grades.csv").toAbsolutePath() +
                "' OVERWRITE INTO TABLE grades");
        con.close();
        System.out.println("############################################################################## Setup done");
    }

    @After
    public void tearDown() throws Exception {
        if (hiveLocalServer2 != null) {
            hiveLocalServer2.stop();
        }
        if (hiveLocalMetaStore != null) {
            hiveLocalMetaStore.stop();
        }
        if (zookeeperLocalCluster != null) {
            zookeeperLocalCluster.stop();
        }
        if (mrLocalCluster != null) {
            mrLocalCluster.stop();
        }
        if (hdfsLocalCluster != null) {
            hdfsLocalCluster.stop();
        }
        if (kdcLocalCluster != null) {
            kdcLocalCluster.stop();
            System.setProperty("KDC_MINI_CLUSTER_BASE_DIR", "");
            UserGroupInformation.getLoginUser().logoutUserFromKeytab();
            UserGroupInformation.reset();
        }
    }

    @Test
    public void testHiveConnector() {
        final ConnectorRunner connectorRunner = new ConnectorRunner();
        final Parameters parameters = Parameters.builder()
                .setAuthType("KEYTAB")
                .setUser(USER_NAME)
                .setUserPrincipal(kdcLocalCluster.getKrbPrincipalWithRealm(USER_NAME))
                .setUserKeytab(kdcLocalCluster.getKeytabForPrincipal(USER_NAME))
                .setQuery("SELECT count(*) FROM grades")
                .build();
        assertEquals("{\"success\":true}", connectorRunner.run(parameters));
    }
}

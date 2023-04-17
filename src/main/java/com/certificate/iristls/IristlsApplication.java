package com.certificate.iristls;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.intersystems.jdbc.IRIS;
import com.intersystems.jdbc.IRISConnection;
import com.intersystems.jdbc.IRISDataSource;

@SpringBootApplication
public class IristlsApplication implements CommandLineRunner{

	@Value( "${spring.datasource.username}" )
	private String username;
	@Value( "${spring.datasource.password}" )
	private String password;
	@Value( "${spring.datasource.url}" )
	private String url;
	@Value( "${spring.datasource.sys.url}" )
	private String sysUrl;

	public static void main(String[] args) {
		SpringApplication.run(IristlsApplication.class, args);
	}

	@Override
    public void run(String... args) throws Exception {

		try{
    		String javaHome = System.getenv("JAVA_HOME");
			String opensslHome = System.getenv("OPENSSL_HOME");
			
			// Certificate and private key generation
			Process p = Runtime.getRuntime().exec("\""+opensslHome+"\\openssl\" req -x509 -newkey rsa:2048 -keyout C:\\Certificados\\key.pem -out C:\\Certificados\\cert.pem -sha256 -days 3650 -nodes -subj \"/CN=localhost\"");
			p.waitFor();
			
			// Deletion of old truststore just in case
			File truststore = new File("C:\\Certificados\\truststore.jks");
			
			if (truststore.exists()){
				truststore.delete();
			}		
			// Creation of new truststore with certification fingerprint
			p = Runtime.getRuntime().exec("\""+javaHome+"\\bin\\keytool\" -importcert -file C:\\Certificados\\cert.pem -keystore C:\\Certificados\\truststore.jks -storepass InterSystems -noprompt -trustcacerts");
			p.waitFor();
			
			File folder = new File("src/main/resources");
			String absolutePath = folder.getAbsolutePath();
			
			// IRIS CONNECTION
			IRISConnection irisConnection =  new IRISConnection();
			irisConnection = (IRISConnection) DriverManager.getConnection(sysUrl, username, password);
			IRIS irisInstance = IRIS.createIRIS(irisConnection);

			// Execution de IRIS commands to create %SuperServer TLS/SSL configuration and enable TLS/SSL comunication for SuperServer
			boolean superServerConfigLoaded = irisInstance.classMethodBoolean("%SYSTEM.OBJ", "Load", absolutePath+"\\Configuration.xml","ck","","1");
			boolean sslSuperServerUpdated = irisInstance.classMethodBoolean("User.Configuration", "UpdateSSLSuperServer");
			boolean testTableReady = irisInstance.classMethodBoolean("User.Configuration", "SetupTestTable");
			
			if (superServerConfigLoaded && sslSuperServerUpdated && testTableReady)
			{
				// Example of insert in specific table and namespace, to reproduce the example is required to configure an existing namespace and table
				IRISDataSource ds = new IRISDataSource();
				ds.setConnectionSecurityLevel(10);
				ds.setURL(url);
				ds.setUser(username);
				ds.setPassword(password);
				// ds.setKeyRecoveryPassword(keyPassword);
				Connection conn = ds.getConnection();
				String insert = "INSERT INTO Phonebook.Company VALUES(?,?,?)";
				try {
					PreparedStatement ps = conn.prepareStatement(insert);
					ps.setNull(1, -5    );
					ps.setString(2, "An address" );
					ps.setObject(3, "A company"   );
					ps.executeUpdate();
					ps.close();
				}
				catch (Exception e) {
					System.out.println("\nException in insert\n"+e);
				}
				irisConnection.close();
				conn.close();
			}
		  }
		catch (SQLException e){
		  System.out.println(e.getMessage());
		  e.printStackTrace();
		}
	}
}

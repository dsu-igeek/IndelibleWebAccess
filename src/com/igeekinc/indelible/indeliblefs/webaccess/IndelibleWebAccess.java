/*
 * Copyright 2002-2014 iGeek, Inc.
 * All Rights Reserved
 * @Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.@
 */
 
package com.igeekinc.indelible.indeliblefs.webaccess;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.PropertyConfigurator;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;

import com.igeekinc.indelible.PreferencesManager;
import com.igeekinc.indelible.indeliblefs.IndelibleFSClientPreferences;
import com.igeekinc.indelible.indeliblefs.security.AuthenticationFailureException;
import com.igeekinc.indelible.indeliblefs.utilities.IndelibleFSUtilBase;
import com.igeekinc.indelible.indeliblefs.webaccess.assets.AssetsServlet;
import com.igeekinc.indelible.server.IndelibleServerPreferences;
import com.igeekinc.indelible.server.VendorProperties;
import com.igeekinc.util.MonitoredProperties;
import com.igeekinc.util.SystemInfo;
import com.igeekinc.util.logging.ErrorLogMessage;

public class IndelibleWebAccess extends IndelibleFSUtilBase
{

	private Server server;
	private static IndelibleWebAccess webAccess;
	protected DailyRollingFileAppender rollingLog;
	protected MonitoredProperties preferences;
	
    public static final String kPropertiesFileName = "indelible.webaccess.properties"; //$NON-NLS-1$
    public static final String kVerboseLogFileLevelPropertyName = "com.igeekinc.indelible.indeliblefs.webaccess.verboseLogLevel";
	private ContextHandlerCollection	contexts;
    
	public IndelibleWebAccess() throws IOException, UnrecoverableKeyException,
			InvalidKeyException, KeyStoreException, NoSuchAlgorithmException,
			CertificateException, IllegalStateException,
			NoSuchProviderException, SignatureException,
			AuthenticationFailureException, InterruptedException
	{
	}
	
	@Override
	public MonitoredProperties setupProperties() throws IOException
	{
		IndelibleFSClientPreferences.initPreferences(null);
		IndelibleFSClientPreferences.addPreferencesFile(new File(IndelibleFSClientPreferences.getPreferencesDir(), kPropertiesFileName));
        MonitoredProperties clientProperties = IndelibleFSClientPreferences.getProperties();
        PreferencesManager.setIfNotSet(IndelibleServerPreferences.kLogFileDirectoryPropertyName, 
        		new File(SystemInfo.getSystemInfo().getLogDirectory(), IndelibleServerPreferences.kLogDirName).getAbsolutePath());
        preferences = clientProperties;
		return clientProperties;
	}

	public void runApp() throws IOException
	{
        server = new Server(8091);
        contexts = new ContextHandlerCollection();
        server.setHandler(contexts);
        ServletContextHandler context = new ServletContextHandler(contexts, "/");
        context.addServlet(IndelibleWebAccessServlet.class, "/volumes/*");
        context.addServlet(AssetsServlet.class, "/assets/*");
        context.addServlet(IndeliblePhotoAlbumServlet.class, "/photos/*");
        context.addServlet(IndeliblePhotoAlbumRSSServlet.class, "/rss/*");
        context.addServlet(IndelibleFSUtilsServlet.class, "/ifsutils/*");
        /*
        HashLoginService loginService = new HashLoginService();
        loginService.setName("IndeliblePhoto Realm");
        loginService.putUser("gakudo", new Password("nakane"), new String[]{"user"});
        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(new String[]{"user"});
        constraint.setAuthenticate(true);
        
        ConstraintMapping cm = new ConstraintMapping();
        cm.setConstraint(constraint);
        cm.setPathSpec("/photos/*");
        ConstraintSecurityHandler constraintSecurityHandler = new ConstraintSecurityHandler();
        constraintSecurityHandler.addConstraintMapping(cm);
        constraintSecurityHandler.setLoginService(loginService);
		context.setSecurityHandler(constraintSecurityHandler);
		*/
        
        HashLoginService loginService = new HashLoginService();
        loginService.setName("IndeliblePhoto Realm");
        
        ConstraintSecurityHandler constraintSecurityHandler = new ConstraintSecurityHandler();
        constraintSecurityHandler.setLoginService(loginService);
		context.setSecurityHandler(constraintSecurityHandler);
        try
        {
            server.start();
        } catch (Exception e)
        {
            Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
        }
        
        while(true)
        {
        	try
        	{
        		Thread.sleep(10000000);
        	}
        	catch (InterruptedException e)
        	{
        		
        	}
        }
	}

    public static void main(String[] args) 
    {
        try
        {
        	webAccess = new IndelibleWebAccess();
        	webAccess.run(args);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }
    
    
    @Override
    public void setupLogging(MonitoredProperties serverProperties)
    {
    	Logger.getRootLogger().setLevel(Level.ERROR);
        Properties loggingProperties = new Properties();
        loggingProperties.putAll(serverProperties);
        File additionalLoggingConfigFile = new File(serverProperties.getProperty(IndelibleServerPreferences.kPreferencesDirPropertyName),
        "executorLoggingOptions.properties"); //$NON-NLS-1$
        Exception savedException = null;
        try
        {
            if (additionalLoggingConfigFile.exists())
            {
                Properties additionalLoggingProperties = new Properties();
                FileInputStream additionalLoggingInStream = new FileInputStream(additionalLoggingConfigFile);
                additionalLoggingProperties.load(additionalLoggingInStream);
                loggingProperties.putAll(additionalLoggingProperties);
            }	
        }
        catch (Exception e)
        {
            savedException = e;
        }
        Logger.getRootLogger().removeAllAppenders();	// Clean up anything lying around
        PropertyConfigurator.configure(loggingProperties);
        rollingLog = new DailyRollingFileAppender();
    
        File logDir = new File(getLogFileDir()); //$NON-NLS-1$
        logDir.mkdirs();
        File logFile = new File(logDir, getServerLogFileName()); //$NON-NLS-1$
        System.out.println("Server log file = "+logFile.getAbsolutePath());
        String logFileEncoding = VendorProperties.getLogFileEncoding();
    	if (logFile.exists() && logFileEncoding.toLowerCase().equals("utf-16") && logFile.length() >= 2)
    	{
    		SimpleDateFormat checkFormatter = new SimpleDateFormat("yyyy-MM-dd");
    		if (checkFormatter.format(new Date()).equals(checkFormatter.format(new Date(logFile.lastModified()))))	// We'll be writing to the same file
    		{
    			// Check the BOM
    			try {
    				InputStream checkStream = new FileInputStream(logFile);
    				int bom0 = checkStream.read();
    				int bom1 = checkStream.read();
    				if (bom0 == 0xfe && bom1 == 0xff)
    					logFileEncoding = "utf-16be";
    				else
    					logFileEncoding = "utf-16le";
    			} catch (FileNotFoundException e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    			} catch (IOException e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    			}
    		}
    	}
        rollingLog.setEncoding(logFileEncoding);
        rollingLog.setFile(logFile.getAbsolutePath());
        rollingLog.setDatePattern("'.'yyyy-MM-dd"); //$NON-NLS-1$
        setLogFileLevelFromPrefs();
    
        rollingLog.activateOptions();
        //rollingLog.setLayout(new XMLLayout());
        rollingLog.setLayout(new PatternLayout("%d %-5p [%t]: %m%n")); //$NON-NLS-1$
        Logger.getRootLogger().addAppender(rollingLog);
    }
    
	public void setLogFileLevelFromPrefs()
    {
		Level level = Level.toLevel(preferences.getProperty(kVerboseLogFileLevelPropertyName, "INFO"), Level.INFO);
		rollingLog.setThreshold(level); //$NON-NLS-1$
		Logger.getRootLogger().setLevel(level);
    }
	
    public String getLogFileDir()
    {
        return preferences.getProperty(IndelibleServerPreferences.kLogFileDirectoryPropertyName);
    }
    
    public String getServerLogFileName()
    {
        return "indelibleWebAccess.log";
    }

	public ContextHandlerCollection getContexts()
	{
		return contexts;
	}
}

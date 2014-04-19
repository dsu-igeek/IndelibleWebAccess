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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.rmi.RemoteException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.igeekinc.indelible.indeliblefs.CreateDirectoryInfo;
import com.igeekinc.indelible.indeliblefs.CreateFileInfo;
import com.igeekinc.indelible.indeliblefs.IndelibleDirectoryNodeIF;
import com.igeekinc.indelible.indeliblefs.IndelibleFSClient;
import com.igeekinc.indelible.indeliblefs.IndelibleFSForkIF;
import com.igeekinc.indelible.indeliblefs.IndelibleFSObjectIF;
import com.igeekinc.indelible.indeliblefs.IndelibleFSVolumeIF;
import com.igeekinc.indelible.indeliblefs.IndelibleFileLike;
import com.igeekinc.indelible.indeliblefs.IndelibleFileNodeIF;
import com.igeekinc.indelible.indeliblefs.IndelibleServerConnectionIF;
import com.igeekinc.indelible.indeliblefs.MoveObjectInfo;
import com.igeekinc.indelible.indeliblefs.datamover.DataMoverSession;
import com.igeekinc.indelible.indeliblefs.exceptions.CannotDeleteDirectoryException;
import com.igeekinc.indelible.indeliblefs.exceptions.FileExistsException;
import com.igeekinc.indelible.indeliblefs.exceptions.ForkNotFoundException;
import com.igeekinc.indelible.indeliblefs.exceptions.NotDirectoryException;
import com.igeekinc.indelible.indeliblefs.exceptions.NotFileException;
import com.igeekinc.indelible.indeliblefs.exceptions.ObjectNotFoundException;
import com.igeekinc.indelible.indeliblefs.exceptions.PermissionDeniedException;
import com.igeekinc.indelible.indeliblefs.exceptions.VolumeNotFoundException;
import com.igeekinc.indelible.indeliblefs.proxies.IndelibleFSServerProxy;
import com.igeekinc.indelible.indeliblefs.remote.IndelibleFSForkRemoteInputStream;
import com.igeekinc.indelible.indeliblefs.security.AuthenticationFailureException;
import com.igeekinc.indelible.indeliblefs.security.EntityAuthenticationServer;
import com.igeekinc.indelible.indeliblefs.uniblock.CASIDMemoryDataDescriptor;
import com.igeekinc.indelible.oid.IndelibleFSObjectID;
import com.igeekinc.indelible.oid.ObjectIDFactory;
import com.igeekinc.util.ClientFileMetaData;
import com.igeekinc.util.ClientFileMetaDataProperties;
import com.igeekinc.util.FilePath;
import com.igeekinc.util.logging.DebugLogMessage;
import com.igeekinc.util.logging.ErrorLogMessage;

public class IndelibleFSUtilsServlet extends HttpServlet
{
    private static final String	kLastModifiedTimePropertyName	= "lastModified";

	private static final long serialVersionUID = 2374103832749294703L;

    protected IndelibleFSServerProxy fsServer;
    //protected IndelibleServerConnectionIF connection;
    protected ArrayList<IndelibleServerConnectionIF>connectionPool;
    protected EntityAuthenticationServer securityServer;
    protected Logger logger;
    
    public static final String kBasicMetaDataPropertyName="com.igeekinc.indeliblefs.basicMetaData";
    
    public IndelibleFSUtilsServlet() throws IOException, UnrecoverableKeyException, InvalidKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IllegalStateException, NoSuchProviderException, SignatureException, AuthenticationFailureException, InterruptedException
    {
        logger = Logger.getLogger(getClass());

        IndelibleFSServerProxy[] servers = new IndelibleFSServerProxy[0];
        
        while(servers.length == 0)
        {
            servers = IndelibleFSClient.listServers();
            if (servers.length == 0)
                Thread.sleep(1000);
        }
        fsServer = servers[0];

        connectionPool = new ArrayList<IndelibleServerConnectionIF>();
    }
    
    public static final int kMaxSimultaneousConnections = 10;
    int numConnections = 0;
    protected synchronized IndelibleServerConnectionIF useConnection() throws IOException
    {
    	IndelibleServerConnectionIF returnConnection = null;
    	while (returnConnection == null)
    	{
    		if (connectionPool.size() > 0)
    		{
    			returnConnection = connectionPool.remove(0);
    		}
    		else
    		{
    			if (numConnections < kMaxSimultaneousConnections)
    			{
    				returnConnection = fsServer.open();
    				numConnections ++;
    			}
    			else
    			{
    				try
    				{
    					wait();
    				} catch (InterruptedException e)
    				{

    				}	// Wait for someone else to return a connection to the pool
    			}
    		}
    	}
    	return returnConnection;
    }
    
    protected synchronized void returnConnection(IndelibleServerConnectionIF returnConnection)
    {
    	try
    	{
    		if (returnConnection.inTransaction())
    			returnConnection.rollback();
    		connectionPool.add(returnConnection);
    		notifyAll();
    	}
    	catch (IOException e)
    	{
    		try
			{
    			numConnections --;
				returnConnection.close();
			} catch (IOException e1)
			{
				Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e1);
			}
    	}
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException
    {
        Map<String, String[]>paramMap = req.getParameterMap();
        String [] cmdKeys = paramMap.get("cmd");
        if (cmdKeys == null || cmdKeys.length < 1)
        {
            resp.sendError(200);
        }
        else
        {
            try
            {
                String command = cmdKeys[0];
                JSONObject resultObject = null;
                if (command.equals("duplicate"))
                    resultObject = duplicate(req, resp, paramMap);
                if (command.equals("delete"))
                    resultObject = delete(req, resp);
                if (command.equals("listvolumes"))
                    resultObject = listVolumes(req, resp);
                if (command.equals("list"))
                    resultObject = list(req, resp);
                if (command.equals("createvolume"))
                	resultObject = createVolume(req, resp, paramMap);
                if (command.equals("mkdir"))
                	resultObject = mkdir(req, resp);
                if (command.equals("rmdir"))
                	resultObject = rmdir(req, resp);
                if (command.equals("info"))
                	resultObject = info(req, resp);
                if (command.equals("touch"))
                	resultObject = touch(req, resp, paramMap);
                if (command.equals("move"))
                	resultObject = move(req, resp, paramMap);
                if (command.equals("get"))
                {
                    resultObject=get(req, resp);
                    if (resultObject == null)   // File transferred OK
                        return;
                }
                if (command.equals("allocate"))
                	resultObject = allocate(req, resp, paramMap);
                if (resultObject != null)
                {
                    resp.getWriter().print(resultObject.toString());
                }
                else
                {
                    throw new InternalError("No result object created");
                }
            } catch (IndelibleMgmtException e)
            {
                Logger.getLogger(getClass()).debug(new DebugLogMessage("Caught exception"), e);
                doError(resp, e);
            }
            catch(Throwable t)
            {
                doError(resp, new IndelibleMgmtException(IndelibleMgmtException.kInternalError, t));
            }
        }
    }
    
    
    @Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException 
	{
    	try {
			JSONObject resultObject = createFile(req, resp);
            if (resultObject != null)
            {
                resp.getWriter().print(resultObject.toString());
            }
            else
            {
                throw new InternalError("No result object created");
            }
		}  catch (IndelibleMgmtException e)
        {
            Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
            doError(resp, e);
        }
        catch(Throwable t)
        {
            doError(resp, new IndelibleMgmtException(IndelibleMgmtException.kInternalError, t));
        }
	}
    
	public JSONObject createVolume(HttpServletRequest req, HttpServletResponse resp, Map<String, String[]> paramMap) throws RemoteException, JSONException, IndelibleMgmtException
    {
    	try {
    		String [] volumePropertiesArray = paramMap.get("volumeProperties");
    		Properties volumeProperties = null;
    		if (volumePropertiesArray != null && volumePropertiesArray.length > 0)
    		{
    			String jsonEncodedVolumeProperties = volumePropertiesArray[0];
    			JSONObject jsonVolumeProperties = new JSONObject(jsonEncodedVolumeProperties);
    			Iterator<?> keyIterator = jsonVolumeProperties.keys();
    			volumeProperties = new Properties();
    			while(keyIterator.hasNext())
    			{
    				String curKey = (String) keyIterator.next();
    				Object curValueObj = jsonVolumeProperties.get(curKey);
    				volumeProperties.put(curKey, curValueObj.toString());
    			}
    		}
    		
    		String [] volumeMetaDataArray = paramMap.get("volumeMetaData");
    		HashMap<String, HashMap<String, Object>>volumeMetaData = new HashMap<String, HashMap<String,Object>>();
    		if (volumeMetaDataArray != null && volumeMetaDataArray.length > 0)
    		{
    			String jsonEncodedVolumeMetaData = volumeMetaDataArray[0];
    			JSONObject jsonMetaData = new JSONObject(jsonEncodedVolumeMetaData);
    			Iterator<?> keyIterator = jsonMetaData.keys();
    			while(keyIterator.hasNext())
    			{
    				String curKey = (String) keyIterator.next();
    				Object curValueObj = jsonMetaData.get(curKey);
    				if (curValueObj instanceof JSONObject)
    				{
            			HashMap<String, Object>curMetaData = new HashMap<String, Object>();
    					JSONObject jsonMD = (JSONObject) curValueObj;
						Iterator<?>mdKeyIterator = jsonMD.keys();
    					while(mdKeyIterator.hasNext())
    					{
    						String curMDKey = (String) mdKeyIterator.next();
    						Object curMDValue = jsonMD.get(curMDKey);
    						if (curMDValue != null)
    							curMetaData.put(curMDKey, curMDValue.toString());
    					}
    					volumeMetaData.put(curKey, curMetaData);
    				}
    			}
    		}
    		IndelibleServerConnectionIF connection = useConnection();
    		try
    		{
    			connection.startTransaction();
    			IndelibleFSVolumeIF createVolume = connection.createVolume(volumeProperties);
    			long now = System.currentTimeMillis();
    			setLastModifiedTime(createVolume, now);
    			setLastModifiedTime(createVolume.getRoot(), now);
    			for (String curMDKey:volumeMetaData.keySet())
    			{
    				HashMap<String, Object>curMD = volumeMetaData.get(curMDKey);
    				createVolume.setMetaDataResource(curMDKey, curMD);
    			}
    			connection.commit();
    			JSONObject returnObject = new JSONObject();
    			JSONObject createVolumeObject = new JSONObject();
    			returnObject.put("createvolume", createVolumeObject);
    			createVolumeObject.put("fsid", createVolume.getObjectID().toString());
    			return returnObject;
    		}
    		finally
    		{
    			returnConnection(connection);
    		}
		} catch (PermissionDeniedException e) {
			throw new IndelibleMgmtException(IndelibleMgmtException.kPermissionDenied, e);
		} catch (IOException e) {
			throw new IndelibleMgmtException(IndelibleMgmtException.kInternalError, e);
		}
    }
	
	public JSONObject createFile(HttpServletRequest req, HttpServletResponse resp) throws JSONException, IndelibleMgmtException, IOException
	{
		boolean completedOK = false;
		IndelibleServerConnectionIF connection = useConnection();
		try
		{
			try
			{
				connection.startTransaction();
			} catch (IOException e)
			{
				throw new IndelibleMgmtException(IndelibleMgmtException.kInternalError, e);
			}
			try
			{
				JSONObject returnObject = new JSONObject();
				JSONObject createFileObject = new JSONObject();
				returnObject.put("createfile", createFileObject);
				String path=req.getPathInfo();

				FilePath reqPath = FilePath.getFilePath(path);
				if (reqPath == null || reqPath.getNumComponents() < 2)
					throw new IndelibleMgmtException(IndelibleMgmtException.kInvalidArgument, null);

				// Should be an absolute path
				reqPath = reqPath.removeLeadingComponent();
				String fsIDStr = reqPath.getComponent(0);
				IndelibleFSVolumeIF volume = getVolume(fsIDStr);
				if (volume == null)
					throw new IndelibleMgmtException(IndelibleMgmtException.kVolumeNotFoundError, null);
				FilePath createPath = reqPath.removeLeadingComponent();
				FilePath parentPath = createPath.getParent();
				FilePath childPath = createPath.getPathRelativeTo(parentPath);
				if (childPath.getNumComponents() != 1)
					throw new IndelibleMgmtException(IndelibleMgmtException.kInvalidArgument, null);
				IndelibleFileNodeIF parentNode = volume.getObjectByPath(parentPath);
				if (!parentNode.isDirectory())
					throw new IndelibleMgmtException(IndelibleMgmtException.kNotDirectory, null);
				IndelibleDirectoryNodeIF parentDirectory = (IndelibleDirectoryNodeIF)parentNode;
				IndelibleFileNodeIF childNode = null;
				try
				{
					childNode = parentDirectory.getChildNode(childPath.getName());
				}
				catch (ObjectNotFoundException e)
				{
					// Not a problem
				}
				if (childNode == null)
				{
					try {
						CreateFileInfo childInfo = parentDirectory.createChildFile(childPath.getName(), true);
						childNode = childInfo.getCreatedNode();
						setLastModifiedTime(childInfo.getDirectoryNode(), System.currentTimeMillis());
					} catch (FileExistsException e) {
						// Probably someone else beat us to it...
						childNode = parentDirectory.getChildNode(childPath.getName());
					}
				}
				else
				{

				}
				if (childNode.isDirectory())
					throw new IndelibleMgmtException(IndelibleMgmtException.kNotFile, null);

				IndelibleFSForkIF dataFork = childNode.getFork("data", true);
				dataFork.truncate(0);
				InputStream readStream =req.getInputStream();
				byte [] writeBuffer = new byte[1024*1024];
				int readLength;
				long bytesCopied = 0;
				int bufOffset = 0;
				while ((readLength = readStream.read(writeBuffer, bufOffset, writeBuffer.length - bufOffset)) > 0)
				{
					if (bufOffset + readLength == writeBuffer.length)
					{
						dataFork.appendDataDescriptor(new CASIDMemoryDataDescriptor(writeBuffer, 0, writeBuffer.length));
						bufOffset = 0;
					}
					else
						bufOffset += readLength;
					bytesCopied += readLength;
				}
				if (bufOffset != 0)
				{
					// Flush out the final stuff
					dataFork.appendDataDescriptor(new CASIDMemoryDataDescriptor(writeBuffer, 0, bufOffset));
				}
				setLastModifiedTime(childNode, System.currentTimeMillis());
				connection.commit();
				completedOK = true;
				createFileObject.put("fileid", childNode.getObjectID().toString());
				createFileObject.put("copied", bytesCopied);
				return returnObject;
			} catch (PermissionDeniedException e) {
				throw new IndelibleMgmtException(IndelibleMgmtException.kPermissionDenied, e);
			} catch (IOException e) {
				throw new IndelibleMgmtException(IndelibleMgmtException.kInternalError, e);
			} catch (ObjectNotFoundException e) {
				throw new IndelibleMgmtException(IndelibleMgmtException.kPathNotFound, e);
			} catch (ForkNotFoundException e) {
				throw new IndelibleMgmtException(IndelibleMgmtException.kForkNotFound, e);
			}
			finally
			{
				if (!completedOK)
					try {
						connection.rollback();
					} catch (IOException e) {
						throw new IndelibleMgmtException(IndelibleMgmtException.kInternalError, e);
					}
			}
		}
		finally
		{
			returnConnection(connection);
		}
	}
	
	public JSONObject touch(HttpServletRequest req, HttpServletResponse resp, Map<String, String[]> paramMap) throws JSONException, IndelibleMgmtException, IOException
	{
		boolean completedOK = false;
		IndelibleServerConnectionIF connection = useConnection();
		try
		{
			synchronized(connection)
			{
				connection.startTransaction();
				JSONObject returnObject = new JSONObject();
				JSONObject createFileObject = new JSONObject();
				returnObject.put("touch", createFileObject);
				String path=req.getPathInfo();

				FilePath reqPath = FilePath.getFilePath(path);
				if (reqPath == null || reqPath.getNumComponents() < 2)
					throw new IndelibleMgmtException(IndelibleMgmtException.kInvalidArgument, null);

				// Should be an absolute path
				reqPath = reqPath.removeLeadingComponent();
				String fsIDStr = reqPath.getComponent(0);
				IndelibleFSVolumeIF volume = getVolume(fsIDStr);
				if (volume == null)
					throw new IndelibleMgmtException(IndelibleMgmtException.kVolumeNotFoundError, null);
				FilePath createPath = reqPath.removeLeadingComponent();
				FilePath parentPath = createPath.getParent();
				FilePath childPath = createPath.getPathRelativeTo(parentPath);
				if (childPath.getNumComponents() != 1)
					throw new IndelibleMgmtException(IndelibleMgmtException.kInvalidArgument, null);
				IndelibleFileNodeIF parentNode = volume.getObjectByPath(parentPath);
				if (!parentNode.isDirectory())
					throw new IndelibleMgmtException(IndelibleMgmtException.kNotDirectory, null);
				IndelibleDirectoryNodeIF parentDirectory = (IndelibleDirectoryNodeIF)parentNode;
				IndelibleFileNodeIF childNode = null;
				long touchTime;
				if (paramMap.containsKey("mtime"))
				{
					touchTime = Long.parseLong(paramMap.get("mtime")[0]);
					touchTime = touchTime * 1000;	// MS baby
				}
				else
				{
					touchTime=System.currentTimeMillis();
				}
				try
				{
					childNode = parentDirectory.getChildNode(childPath.getName());
				}
				catch (ObjectNotFoundException e)
				{
					
				}
				if (childNode == null)
				{
					try {
						CreateFileInfo childInfo = parentDirectory.createChildFile(childPath.getName(), true);
						childNode = childInfo.getCreatedNode();
						setLastModifiedTime(childInfo.getDirectoryNode(), touchTime);
					} catch (FileExistsException e) {
						// Probably someone else beat us to it...
						childNode = parentDirectory.getChildNode(childPath.getName());
					}
				}
				else
				{
				}

				setLastModifiedTime(childNode, touchTime);
				connection.commit();

				completedOK = true;
				createFileObject.put("fileid", childNode.getObjectID().toString());
				return returnObject;
			}
		} catch (PermissionDeniedException e) {
			throw new IndelibleMgmtException(IndelibleMgmtException.kPermissionDenied, e);
		} catch (IOException e) {
			throw new IndelibleMgmtException(IndelibleMgmtException.kInternalError, e);
		} catch (ObjectNotFoundException e) {
			throw new IndelibleMgmtException(IndelibleMgmtException.kPathNotFound, e);
		}
		finally
		{
			if (!completedOK)
			{
				try {
					connection.rollback();
				} catch (IOException e) {
					throw new IndelibleMgmtException(IndelibleMgmtException.kInternalError, e);
				}
			}
			returnConnection(connection);
		}
	}
	
	protected void setLastModifiedTime(IndelibleFSObjectIF modifiedObject, long modifiedTime)
			throws RemoteException, PermissionDeniedException, IOException
	{
		HashMap<String, Object>basicMDHashMap = new HashMap<String, Object>();
		basicMDHashMap.put(kLastModifiedTimePropertyName, modifiedTime);
		modifiedObject.setMetaDataResource(kBasicMetaDataPropertyName, basicMDHashMap);
	}
    public JSONObject listVolumes(HttpServletRequest req, HttpServletResponse resp) throws IOException, JSONException, IndelibleMgmtException
    {
    	IndelibleServerConnectionIF connection = useConnection();
    	try
    	{
    		IndelibleFSObjectID [] volumeIDs = connection.listVolumes();
    		JSONObject returnObject = new JSONObject();
    		JSONArray volumeList = new JSONArray();
    		returnObject.put("volumelist", volumeList);
    		for (IndelibleFSObjectID curVolumeID:volumeIDs)
    		{
    			JSONObject volumeObject = new JSONObject();
    			volumeList.put(volumeObject);
    			volumeObject.put("id", curVolumeID.toString());
    			JSONObject metaDataObject = new JSONObject();
    			volumeObject.put("metadata", metaDataObject);
    			try
    			{
    				IndelibleFSVolumeIF volume;
    				synchronized(connection)
    				{
    					try
    					{
    						volume = connection.retrieveVolume(curVolumeID);
    					} catch (VolumeNotFoundException e)
    					{
    						continue;
    					}
    					if (volume != null)
    					{
    						String [] mdNames = volume.listMetaDataResources();
    						for (String curMDName:mdNames)
    						{
    							HashMap<String, Object>curMD = volume.getMetaDataResource(curMDName);
    							JSONObject curMDObject = new JSONObject();
    							for (String curMDKey:curMD.keySet())
    							{
    								Object curMDValue = curMD.get(curMDKey);
    								curMDObject.put(curMDKey, curMDValue.toString());
    							}
    							metaDataObject.put(curMDName, curMDObject);
    						}
    					}
    				}
    			} catch (PermissionDeniedException e)
    			{
    				throw new IndelibleMgmtException(IndelibleMgmtException.kPermissionDenied, e);
    			} catch (IOException e)
    			{
    				throw new IndelibleMgmtException(IndelibleMgmtException.kInternalError, e);
    			}
    		}
    		return returnObject;
    	}
    	finally
    	{
    		returnConnection(connection);
    	}
    }
    
    public JSONObject list(HttpServletRequest req, HttpServletResponse resp) throws JSONException, IndelibleMgmtException, IOException
    {
        JSONObject returnObject = new JSONObject();
        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleMgmtException(IndelibleMgmtException.kInvalidArgument, null);

        // Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSVolumeIF volume = getVolume(fsIDStr);
		if (volume == null)
			throw new IndelibleMgmtException(IndelibleMgmtException.kVolumeNotFoundError, null);
        FilePath listPath = reqPath.removeLeadingComponent();
        IndelibleServerConnectionIF connection = useConnection();
        try
        {
        	synchronized(connection)
        	{
        		IndelibleFileNodeIF listFile = volume.getObjectByPath(listPath);
        		if (listFile.isDirectory())
        		{
        			IndelibleDirectoryNodeIF listDir = (IndelibleDirectoryNodeIF)listFile;
        			String [] children = listDir.list();
        			JSONArray listObject = new JSONArray();
        			returnObject.put("list", listObject);
        			Arrays.sort(children);
        			for (String curChildName:children)
        			{
        				IndelibleFileNodeIF curChild = listDir.getChildNode(curChildName);
        				listObject.put(listIndelibleFile(curChildName, curChild));
        			}
        		}
        		else
        		{
        			returnObject.append("list", listIndelibleFile(listPath.getName(), listFile));
        		}
        	}
            return returnObject;
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleMgmtException(IndelibleMgmtException.kPathNotFound, e);
        } catch (PermissionDeniedException e)
        {
            throw new IndelibleMgmtException(IndelibleMgmtException.kPermissionDenied, e);
        } catch (IOException e)
        {
            throw new IndelibleMgmtException(IndelibleMgmtException.kInternalError, e);
        }
        finally
        {
        	returnConnection(connection);
        }

    }
    
    public JSONObject mkdir(HttpServletRequest req, HttpServletResponse resp) throws JSONException, IndelibleMgmtException, IOException
    {
        JSONObject returnObject = new JSONObject();
        JSONObject mkdirObject = new JSONObject();
        returnObject.put("mkdir", mkdirObject);
        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleMgmtException(IndelibleMgmtException.kInvalidArgument, null);

        // Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSVolumeIF volume = getVolume(fsIDStr);
		if (volume == null)
			throw new IndelibleMgmtException(IndelibleMgmtException.kVolumeNotFoundError, null);
        FilePath mkdirPath = reqPath.removeLeadingComponent();
        try
        {
        	FilePath parentPath = mkdirPath.getParent();
        	FilePath childPath = mkdirPath.getPathRelativeTo(parentPath);
			if (childPath.getNumComponents() != 1)
				throw new IndelibleMgmtException(IndelibleMgmtException.kInvalidArgument, null);
			IndelibleServerConnectionIF connection = useConnection();
			try
			{
				IndelibleFileNodeIF parentNode = volume.getObjectByPath(parentPath);
				if (!parentNode.isDirectory())
					throw new IndelibleMgmtException(IndelibleMgmtException.kNotDirectory, null);
				IndelibleDirectoryNodeIF parentDirectory = (IndelibleDirectoryNodeIF)parentNode;
				connection.startTransaction();
				boolean rollback = true;
				try
				{
					CreateDirectoryInfo createInfo = parentDirectory.createChildDirectory(childPath.getName());
					long now = System.currentTimeMillis();
					setLastModifiedTime(createInfo.getDirectoryNode(), now);
					setLastModifiedTime(createInfo.getCreatedNode(), now);
					connection.commit();
					rollback = false;
					mkdirObject.put("fileid", createInfo.getCreatedNode().getObjectID().toString());
				}
				finally
				{
					if (rollback)
						connection.rollback();
				}
			}
			finally
			{
				returnConnection(connection);
			}
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleMgmtException(IndelibleMgmtException.kPathNotFound, e);
        } catch (PermissionDeniedException e)
        {
            throw new IndelibleMgmtException(IndelibleMgmtException.kPermissionDenied, e);
        } catch (IOException e)
        {
            throw new IndelibleMgmtException(IndelibleMgmtException.kInternalError, e);
        } catch (FileExistsException e) {
			throw new IndelibleMgmtException(IndelibleMgmtException.kDestinationExists, e);
		}
        return returnObject;
    }
    
    public JSONObject listIndelibleFile(String name, IndelibleFileNodeIF file) throws JSONException, IOException
    {
        JSONObject returnObject = new JSONObject();
        returnObject.put("name", name);
        returnObject.put("length", file.totalLength());
        if (file.isDirectory())
            returnObject.put("directory", true);
        else
            returnObject.put("directory", false);
        return returnObject;
    }
    
    public JSONObject duplicate(HttpServletRequest req, HttpServletResponse resp,  Map<String, String[]>paramMap) throws IndelibleMgmtException, PermissionDeniedException, RemoteException, IOException, JSONException
    {
        JSONObject returnObject = new JSONObject();
        JSONObject duplicateObject = new JSONObject();
        returnObject.put("duplicate", duplicateObject);
        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        String [] destPathStrArr = paramMap.get("dest");
        if (reqPath == null || destPathStrArr == null || destPathStrArr.length < 1 || reqPath.getNumComponents() < 3)
            throw new IndelibleMgmtException(IndelibleMgmtException.kInvalidArgument, null);
        String destPathStr = destPathStrArr[0];
        // Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSVolumeIF volume = getVolume(fsIDStr);
        FilePath sourcePath = reqPath.removeLeadingComponent();

        FilePath destPath = FilePath.getFilePath(destPathStr);
        if (destPath == null)
            throw new IndelibleMgmtException(IndelibleMgmtException.kInvalidArgument, null);
        
        IndelibleFileNodeIF sourceFile = null, checkFile = null;
        
        // Our semantics on the name of the created file are the same as Unix cp.  If the create path specifies a directory, the
        // name will be the source file name (unless no source file is specified, in which case it's an error).  If the create path
        // specifies a non-existing file, then the create path name will be used (if it specifies an existing file then we copy over the existing file)
        String createName = null;
        if (sourcePath != null)
        {
            createName = sourcePath.getName();
        }
        IndelibleServerConnectionIF connection = useConnection();
        try
        {
        	connection.startTransaction();
        	boolean rollback = false;
        	try
        	{
        		try
        		{
        			sourceFile = volume.getObjectByPath(sourcePath);
        		} catch (ObjectNotFoundException e1)
        		{
        			throw new IndelibleMgmtException(IndelibleMgmtException.kPathNotFound, e1);
        		}

        		try
        		{
        			checkFile = volume.getObjectByPath(destPath);
        			if (checkFile != null && !checkFile.isDirectory())
        			{
        				throw new IndelibleMgmtException(IndelibleMgmtException.kDestinationExists, null);
        			}
        			createName = sourcePath.getName();
        		}
        		catch (ObjectNotFoundException e)
        		{
        			try
        			{
        				checkFile = volume.getObjectByPath(destPath.getParent());
        				createName = destPath.getName();
        			}
        			catch (ObjectNotFoundException e1)
        			{
        				throw new IndelibleMgmtException(IndelibleMgmtException.kPathNotFound, null);
        			}
        		}
        		if (checkFile.isDirectory())
        		{
        			IndelibleDirectoryNodeIF parentDir = (IndelibleDirectoryNodeIF) checkFile;
        			if (sourceFile != null)
        			{

        				try {
        					CreateFileInfo createFileInfo = parentDir.createChildFile(createName, sourceFile, true);

        					duplicateObject.put("fileid", createFileInfo.getCreatedNode().getObjectID().toString());
        					long now = System.currentTimeMillis();
        					setLastModifiedTime(createFileInfo.getCreatedNode(), now);
        					setLastModifiedTime(createFileInfo.getDirectoryNode(), now);
        				} catch (FileExistsException e) {
        					throw new IndelibleMgmtException(IndelibleMgmtException.kDestinationExists, e);
        				} catch (ObjectNotFoundException e) {
        					throw new IndelibleMgmtException(IndelibleMgmtException.kPathNotFound, e);
        				} catch (NotFileException e) {
        					throw new IndelibleMgmtException(IndelibleMgmtException.kPathNotFound, e);
        				}
        			}
        		}
        		else
        		{
        			throw new IndelibleMgmtException(IndelibleMgmtException.kNotDirectory, null);
        		}

        		connection.commit();
        		rollback = false;
        		return returnObject;
        	}
        	finally
        	{
        		if (rollback)
        			connection.rollback();
        	}
        }
        finally
        {
        	returnConnection(connection);
        }
    }
    
    public JSONObject move(HttpServletRequest req, HttpServletResponse resp,  Map<String, String[]>paramMap) throws IndelibleMgmtException, PermissionDeniedException, RemoteException, IOException, JSONException
    {
        JSONObject returnObject = new JSONObject();
        JSONObject moveObject = new JSONObject();
        returnObject.put("move", moveObject);
        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        String [] destPathStrArr = paramMap.get("dest");
        if (reqPath == null || destPathStrArr == null || destPathStrArr.length < 1 || reqPath.getNumComponents() < 3)
            throw new IndelibleMgmtException(IndelibleMgmtException.kInvalidArgument, null);
        String destPathStr = destPathStrArr[0];
        // Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSVolumeIF volume = getVolume(fsIDStr);
        FilePath sourcePath = reqPath.removeLeadingComponent();
        sourcePath = FilePath.getFilePath("/").getChild(sourcePath);	// Make sourcePath absolute
        FilePath destPath = FilePath.getFilePath(destPathStr);
        if (destPath == null)
            throw new IndelibleMgmtException(IndelibleMgmtException.kInvalidArgument, null);
        IndelibleServerConnectionIF connection = useConnection();
        try
        {
        	try
        	{
        		MoveObjectInfo moveInfo = volume.moveObject(sourcePath, destPath);
        		moveObject.put("fileid", moveInfo.getDestInfo().getCreatedNode().getObjectID().toString());
                return returnObject;
        	} catch (ObjectNotFoundException e)
        	{
        		throw new IndelibleMgmtException(IndelibleMgmtException.kPathNotFound, e);
        	} catch (FileExistsException e)
        	{
        		throw new IndelibleMgmtException(IndelibleMgmtException.kDestinationExists, e);
        	} catch (NotDirectoryException e)
        	{
        		throw new IndelibleMgmtException(IndelibleMgmtException.kNotDirectory, e);
        	}
        }
        finally
        {
        	returnConnection(connection);
        }
    }
    public IndelibleFSVolumeIF getVolume(String fsIDStr) throws IndelibleMgmtException, IOException
    {
        IndelibleFSObjectID retrieveVolumeID = (IndelibleFSObjectID) ObjectIDFactory.reconstituteFromString(fsIDStr);
        if (retrieveVolumeID == null)
        {
            throw new IndelibleMgmtException(IndelibleMgmtException.kInvalidVolumeID, null);
        }
        
        IndelibleFSVolumeIF volume = null;
        IndelibleServerConnectionIF connection = useConnection();
        try
        {
        		volume = connection.retrieveVolume(retrieveVolumeID);
                if (volume == null)
                {
                    throw new IndelibleMgmtException(IndelibleMgmtException.kVolumeNotFoundError, null);
                }
                return volume;
        } catch (VolumeNotFoundException e1)
        {
            throw new IndelibleMgmtException(IndelibleMgmtException.kVolumeNotFoundError, e1);
        } catch (IOException e1)
        {
            throw new IndelibleMgmtException(IndelibleMgmtException.kInternalError, e1);
        }
        finally
        {
        	returnConnection(connection);
        }

    }
    
    public JSONObject get(HttpServletRequest req, HttpServletResponse resp) throws JSONException, IndelibleMgmtException, IOException
    {
        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleMgmtException(IndelibleMgmtException.kInvalidArgument, null);

        // Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSVolumeIF volume = getVolume(fsIDStr);
        FilePath listPath = reqPath.removeLeadingComponent();
        IndelibleServerConnectionIF connection = useConnection();
        try
        {
        	try
        	{
        		IndelibleFileNodeIF getFile = volume.getObjectByPath(listPath);
        		if (getFile.isDirectory())
        			throw new IndelibleMgmtException(IndelibleMgmtException.kNotFile, null);
        		IndelibleFSForkIF dataFork = getFile.getFork("data", false);
        		if (dataFork != null)
        		{
        			IndelibleFSForkRemoteInputStream forkStream = new IndelibleFSForkRemoteInputStream(dataFork);
        			OutputStream outStream = resp.getOutputStream();
        			byte [] outBuffer = new byte[resp.getBufferSize()];
        			int bytesRead;
        			while ((bytesRead = forkStream.read(outBuffer)) > 0)
        				outStream.write(outBuffer, 0, bytesRead);
        		}
                return null;
        	} catch (ObjectNotFoundException e)
        	{
        		throw new IndelibleMgmtException(IndelibleMgmtException.kPathNotFound, e);
        	} catch (PermissionDeniedException e)
        	{
        		throw new IndelibleMgmtException(IndelibleMgmtException.kPermissionDenied, e);
        	} catch (IOException e)
        	{
        		throw new IndelibleMgmtException(IndelibleMgmtException.kInternalError, e);
        	} catch (ForkNotFoundException e)
        	{
        		throw new IndelibleMgmtException(IndelibleMgmtException.kForkNotFound, e);
        	}
        }
        finally
        {
        	returnConnection(connection);
        }
    }

    public JSONObject delete(HttpServletRequest req, HttpServletResponse resp)  throws IndelibleMgmtException, PermissionDeniedException, RemoteException, IOException, JSONException
    {
    	JSONObject returnObject = new JSONObject();
    	JSONObject deleteObject = new JSONObject();
    	returnObject.put("delete", deleteObject);
    	String path=req.getPathInfo();
    	FilePath reqPath = FilePath.getFilePath(path);
    	if (reqPath == null || reqPath.getNumComponents() < 3)
    		throw new IndelibleMgmtException(IndelibleMgmtException.kInvalidArgument, null);

    	// Should be an absolute path
    	reqPath = reqPath.removeLeadingComponent();
    	String fsIDStr = reqPath.getComponent(0);
    	IndelibleFSVolumeIF volume = getVolume(fsIDStr);
    	FilePath deletePath = reqPath.removeLeadingComponent();
    	FilePath parentPath = deletePath.getParent();
    	String deleteName = deletePath.getName();

    	IndelibleDirectoryNodeIF parentNode = null;
        IndelibleServerConnectionIF connection = useConnection();
    	try
    	{
    		connection.startTransaction();
    		boolean rollback = true;
    		try
    		{
    			try
    			{
    				IndelibleFileNodeIF parentObject = volume.getObjectByPath(parentPath);
    				if (!(parentObject instanceof IndelibleDirectoryNodeIF))
    				{
    					throw new IndelibleMgmtException(IndelibleMgmtException.kNotDirectory, null);
    				}
    				parentNode = (IndelibleDirectoryNodeIF)parentObject; 
    			} catch (ObjectNotFoundException e1)
    			{
    				throw new IndelibleMgmtException(IndelibleMgmtException.kPathNotFound, e1);
    			}
    			try {
    				parentNode.deleteChild(deleteName);
    				setLastModifiedTime(parentNode, System.currentTimeMillis());
    				connection.commit();
    				rollback = false;
    				return returnObject;
    			} catch (PermissionDeniedException e) 
    			{
    				throw new IndelibleMgmtException(IndelibleMgmtException.kPermissionDenied, e);
    			} catch (CannotDeleteDirectoryException e) {
    				throw new IndelibleMgmtException(IndelibleMgmtException.kNotFile, e);
    			}
    		}
    		finally
    		{
    			if (rollback)
    				connection.rollback();
    		}
    	}
    	finally
    	{
    		returnConnection(connection);
    	}
    }

    public JSONObject rmdir(HttpServletRequest req, HttpServletResponse resp)  throws IndelibleMgmtException, PermissionDeniedException, RemoteException, IOException, JSONException
    {
    	JSONObject returnObject = new JSONObject();
    	JSONObject deleteObject = new JSONObject();
    	returnObject.put("rmdir", deleteObject);
    	String path=req.getPathInfo();
    	FilePath reqPath = FilePath.getFilePath(path);
    	if (reqPath == null || reqPath.getNumComponents() < 3)
    		throw new IndelibleMgmtException(IndelibleMgmtException.kInvalidArgument, null);

    	// Should be an absolute path
    	reqPath = reqPath.removeLeadingComponent();
    	String fsIDStr = reqPath.getComponent(0);
    	IndelibleFSVolumeIF volume = getVolume(fsIDStr);
    	FilePath deletePath = reqPath.removeLeadingComponent();
    	FilePath parentPath = deletePath.getParent();
    	String deleteName = deletePath.getName();

    	IndelibleDirectoryNodeIF parentNode = null;
        IndelibleServerConnectionIF connection = useConnection();
    	try
    	{
    		connection.startTransaction();
    		boolean rollback = true;
    		try
    		{
    			try
    			{
    				IndelibleFileNodeIF parentObject = volume.getObjectByPath(parentPath);
    				if (!(parentObject instanceof IndelibleDirectoryNodeIF))
    				{
    					throw new IndelibleMgmtException(IndelibleMgmtException.kNotDirectory, null);
    				}
    				parentNode = (IndelibleDirectoryNodeIF)parentObject; 
    			} catch (ObjectNotFoundException e1)
    			{
    				throw new IndelibleMgmtException(IndelibleMgmtException.kPathNotFound, e1);
    			}


    			try {
    				parentNode.deleteChildDirectory(deleteName);
    				setLastModifiedTime(parentNode, System.currentTimeMillis());
    				connection.commit();
    				rollback = false;
    				return returnObject;
    			} catch (PermissionDeniedException e) 
    			{
    				throw new IndelibleMgmtException(IndelibleMgmtException.kPermissionDenied, e);
    			} catch (NotDirectoryException e) {
    				throw new IndelibleMgmtException(IndelibleMgmtException.kNotDirectory, e);
    			}
    		}
    		finally
    		{
    			if (rollback)
    				connection.rollback();
    		}
    	}
    	finally
    	{
    		returnConnection(connection);
    	}
    }
    
    public JSONObject allocate(HttpServletRequest req, HttpServletResponse resp, Map<String, String[]>paramMap) throws JSONException, IndelibleMgmtException, IOException
    {
        JSONObject returnObject = new JSONObject();
        JSONObject allocateObject = new JSONObject();
        returnObject.put("allocate", allocateObject);
        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        String [] sizeStrArr = paramMap.get("size");
        if (reqPath == null || sizeStrArr == null || sizeStrArr.length < 1 || reqPath.getNumComponents() < 2)
            throw new IndelibleMgmtException(IndelibleMgmtException.kInvalidArgument, null);
        String sizeString = sizeStrArr[0];
        long allocateLength;
        try
        {
        	allocateLength = Long.parseLong(sizeString);
        }
        catch (NumberFormatException e)
        {
        	throw new IndelibleMgmtException(IndelibleMgmtException.kInvalidArgument, e);
        }
        // Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSVolumeIF volume = getVolume(fsIDStr);
		if (volume == null)
			throw new IndelibleMgmtException(IndelibleMgmtException.kVolumeNotFoundError, null);
        FilePath allocatePath = reqPath.removeLeadingComponent();
        IndelibleServerConnectionIF connection = useConnection();
        try
        {
        	FilePath parentPath = allocatePath.getParent();
        	FilePath childPath = allocatePath.getPathRelativeTo(parentPath);
			if (childPath.getNumComponents() != 1)
				throw new IndelibleMgmtException(IndelibleMgmtException.kInvalidArgument, null);
			synchronized(connection)
			{
				IndelibleFileNodeIF parentNode = volume.getObjectByPath(parentPath);
				if (!parentNode.isDirectory())
					throw new IndelibleMgmtException(IndelibleMgmtException.kNotDirectory, null);
				IndelibleDirectoryNodeIF parentDirectory = (IndelibleDirectoryNodeIF)parentNode;
				connection.startTransaction();
				boolean rollback = true;
				try
				{
					IndelibleFileNodeIF childNode = null;
					try
					{
						childNode = parentDirectory.getChildNode(childPath.getName());
					}
					catch (ObjectNotFoundException e)
					{
						// Yah, I meant to do that
					}
					if (childNode == null)
					{
						CreateFileInfo childInfo = parentDirectory.createChildFile(childPath.getName(), true);
						childNode = childInfo.getCreatedNode();
						IndelibleFSForkIF dataFork = childNode.getFork("data", true);
						dataFork.extend(allocateLength);
						allocateObject.put("fileid", childInfo.getCreatedNode().getObjectID().toString());
						allocateObject.put("length", allocateLength);
					}
					else
					{
						throw new IndelibleMgmtException(IndelibleMgmtException.kDestinationExists, null);
					}
					if (childNode.isDirectory())
						throw new IndelibleMgmtException(IndelibleMgmtException.kNotFile, null);


					connection.commit();
					rollback = false;
			        return returnObject;
				}
				finally
				{
					if (rollback)
						connection.rollback();
				}
			}
			
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleMgmtException(IndelibleMgmtException.kPathNotFound, e);
        } catch (PermissionDeniedException e)
        {
            throw new IndelibleMgmtException(IndelibleMgmtException.kPermissionDenied, e);
        } catch (IOException e)
        {
            throw new IndelibleMgmtException(IndelibleMgmtException.kInternalError, e);
        } catch (FileExistsException e) {
			throw new IndelibleMgmtException(IndelibleMgmtException.kDestinationExists, e);
		} catch (ForkNotFoundException e)
		{
			throw new IndelibleMgmtException(IndelibleMgmtException.kInternalError, e);
		}
        finally
        {
        	returnConnection(connection);
        }
    }
    
    public JSONObject info(HttpServletRequest req, HttpServletResponse resp) throws JSONException, IndelibleMgmtException, IOException
    {
        JSONObject returnObject = new JSONObject();
        JSONObject infoObject = new JSONObject();
        returnObject.put("info", infoObject);
        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleMgmtException(IndelibleMgmtException.kInvalidArgument, null);
        // Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSVolumeIF volume = getVolume(fsIDStr);
		if (volume == null)
			throw new IndelibleMgmtException(IndelibleMgmtException.kVolumeNotFoundError, null);
        FilePath infoPath = reqPath.removeLeadingComponent();
        IndelibleServerConnectionIF connection = useConnection();
        try
        {
        	IndelibleFileNodeIF infoNode = volume.getObjectByPath(infoPath);
        	infoObject.put("fileid", infoNode.getObjectID().toString());
        	infoObject.put("directory", infoNode.isDirectory());
        	if (infoNode.isDirectory())
        		infoObject.put("directoryStr", "Y");
        	else
        		infoObject.put("directoryStr", "N");
        	if (infoNode.isFile())
        		infoObject.put("length", infoNode.totalLength());
        	else
        		infoObject.put("length", ((IndelibleDirectoryNodeIF)infoNode).getNumChildren());
        	SimpleDateFormat formatter = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
        	formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        	HashMap<String, Object>mdHashMap = infoNode.getMetaDataResource(IndelibleFileLike.kClientFileMetaDataPropertyName);
        	long mtime;
        	if (mdHashMap != null)
        	{
        		ClientFileMetaDataProperties mdProps = ClientFileMetaDataProperties.getPropertiesForMap(mdHashMap);
        		ClientFileMetaData md = mdProps.getMetaData();

        		//infoObject.put("modified", formatter.format(md.getModifyTime()));
        		mtime = md.getModifyTime().getTime();
        	}
        	else
        	{
        		HashMap<String, Object>basicMDHashMap = infoNode.getMetaDataResource(kBasicMetaDataPropertyName);
        		if (basicMDHashMap != null)
        		{
        			mtime = (Long) basicMDHashMap.get(kLastModifiedTimePropertyName);
        			//infoObject.put("modified", formatter.format(new Date(lastModifiedTime)));
        		}
        		else
        		{
        			//infoObject.put("modified", formatter.format(new Date(0)));
        			mtime = 0;
        		}
        	}

        	infoObject.put("modified", formatter.format(new Date(mtime)));
        	long mtimeSecs = mtime/1000;
        	infoObject.put("mtime", mtimeSecs);	// The integer representation
        	return returnObject;
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleMgmtException(IndelibleMgmtException.kPathNotFound, e);
        } catch (PermissionDeniedException e)
        {
            throw new IndelibleMgmtException(IndelibleMgmtException.kPermissionDenied, e);
        } catch (IOException e)
        {
            throw new IndelibleMgmtException(IndelibleMgmtException.kInternalError, e);
        }
        finally
        {
        	returnConnection(connection);
        }
    }
    
    public void doError(HttpServletResponse resp, IndelibleMgmtException exception)
    {
        resp.setStatus(500);
        try
        {
            JSONObject resultObject = new JSONObject();
            JSONObject errorObject = new JSONObject();
            resultObject.put("error", errorObject);
            errorObject.put("code", exception.getErrorCode());
            
            Throwable cause = exception.getCause();
            if (cause != null)
            {
                StringWriter stringWriter = new StringWriter();
                cause.printStackTrace(new PrintWriter(stringWriter));
                errorObject.put("longReason", stringWriter.toString());
            }
            else
            {
                errorObject.put("longReason", "");
            }
            resp.getWriter().print(resultObject.toString());
            
        } catch (JSONException e)
        {
            Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
        } catch (IOException e)
        {
            Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
        }
    }
}

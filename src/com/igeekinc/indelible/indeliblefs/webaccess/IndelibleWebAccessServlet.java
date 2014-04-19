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
import java.rmi.RemoteException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.log4j.Logger;
import org.apache.xerces.dom.DocumentImpl;
import org.apache.xml.serialize.Method;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.igeekinc.indelible.indeliblefs.CreateDirectoryInfo;
import com.igeekinc.indelible.indeliblefs.CreateFileInfo;
import com.igeekinc.indelible.indeliblefs.IndelibleDirectoryNodeIF;
import com.igeekinc.indelible.indeliblefs.IndelibleFSClient;
import com.igeekinc.indelible.indeliblefs.IndelibleFSForkIF;
import com.igeekinc.indelible.indeliblefs.IndelibleFSVolumeIF;
import com.igeekinc.indelible.indeliblefs.IndelibleFileNodeIF;
import com.igeekinc.indelible.indeliblefs.IndelibleServerConnectionIF;
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
import com.igeekinc.util.FilePath;
import com.igeekinc.util.logging.ErrorLogMessage;

public class IndelibleWebAccessServlet extends XMLOutputServlet
{
	private static final long serialVersionUID = 2374103832749294703L;

	protected IndelibleFSServerProxy fsServer;
	protected IndelibleServerConnectionIF connection;
	protected EntityAuthenticationServer securityServer;
	protected Logger logger;



	public IndelibleWebAccessServlet() throws IOException, UnrecoverableKeyException, InvalidKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IllegalStateException, NoSuchProviderException, SignatureException, AuthenticationFailureException, InterruptedException, SAXException
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

		connection = fsServer.open();
		try
		{
			Document stylesheet =  XMLUtils.getDocument(IndelibleWebAccessServlet.class.getResourceAsStream("IndelibleWebAcessStylesheet.xsl"));
			setStylesheet(stylesheet);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}


	@Override
	protected Document doRequest(HttpServletRequest req,
			HttpServletResponse resp) throws ServletException, IOException
	{
		DocumentImpl buildDoc = new DocumentImpl();
		Map<String, String[]>paramMap = req.getParameterMap();
		String [] keys = paramMap.keySet().toArray(new String[0]);
		if (keys.length != 1)
		{
			resp.sendError(200);
		}
		else
		{
			try
			{
				String command = keys[0].toLowerCase().trim();
				if (command.equals("create"))
					createFile(req, resp, buildDoc);
				if (command.equals("duplicate"))
					duplicate(req, resp, paramMap.get(keys[0])[0], buildDoc);
				if (command.equals("delete"))
					delete(req, resp, buildDoc);
				if (command.equals("listvolumes"))
					listVolumes(req, resp, buildDoc);
				if (command.equals("list"))
					list(req, resp, buildDoc);
				if (command.equals("createvolume"))
					createVolume(req, resp, buildDoc);
				if (command.equals("mkdir"))
					mkdir(req, resp, buildDoc);
				if (command.equals("rmdir"))
					rmdir(req, resp, buildDoc);
				if (command.equals("get"))
				{
					get(req, resp);
					return null;
				}
				if (command.equals("allocate"))
					allocate(req, resp, paramMap.get(keys[0])[0], buildDoc);
				if (command.equals("rename"))
					rename(req, resp, paramMap.get(keys[0])[0], buildDoc);
			} catch (IndelibleWebAccessException e)
			{
				Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
				doError(resp, e);
			}
			catch(Throwable t)
			{
				doError(resp, new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, t));
			}
		}
		return buildDoc;
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException 
	{
		try
		{
			Document  servletOutput = new DocumentImpl();

			if (stylesheetFile != null)
			{
				if (stylesheetFile.lastModified() != sfLastModified)
				{
					reloadStylesheet();
				}
			}
			resp.setContentType("text/html; charset="+encoding);
			if (needsAuthentication(req))
			{
				if (!authenticate(req))
				{
					askPassword(resp);
					return;
				}
			}

			try {
				createFilePut(req, resp, servletOutput);
			}  catch (IndelibleWebAccessException e)
			{
				Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
				doError(resp, e);
			}
			catch(Throwable t)
			{
				doError(resp, new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, t));
			}
			if (servletOutput != null)
			{
				doProcess(getStylesheet(), servletOutput, resp.getOutputStream());

				OutputFormat  defOF = new OutputFormat(Method.XML, "UTF-8", false);
				defOF.setOmitXMLDeclaration(true);
				defOF.setOmitDocumentType(true);
				defOF.setIndenting(true);
				XMLSerializer xmlSer = new XMLSerializer(System.err, defOF);

				xmlSer.serialize(servletOutput);
			}
		}

		catch (Exception e)
		{
			logger.warn("Caught exception in doWork", e);
			throw new ServletException(e.getMessage());
		}
	}
    
	public void createVolume(HttpServletRequest req, HttpServletResponse resp, Document buildDoc) throws RemoteException, IndelibleWebAccessException
    {
    	try {
    		connection.startTransaction();
			IndelibleFSVolumeIF createVolume = connection.createVolume(null);
			connection.commit();
	        Element rootElem = buildDoc.createElement("createVolume");
	        buildDoc.appendChild(rootElem);
	        XMLUtils.appendSingleValElement(buildDoc, rootElem, "fsid", createVolume.getObjectID().toString());
		} catch (PermissionDeniedException e) {
			throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
		} catch (IOException e) {
			throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
		}
    }
	
	public void createFile(HttpServletRequest req, HttpServletResponse resp, Document buildDoc) throws IndelibleWebAccessException, IOException, ServletException, FileUploadException
	{
		boolean completedOK = false;

		boolean isMultipart = ServletFileUpload.isMultipartContent(req);
		if (!isMultipart)
			throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);
		ServletFileUpload upload = new ServletFileUpload();
		FileItemIterator iter = upload.getItemIterator(req);
		if (iter.hasNext())
		{
			FileItemStream item = iter.next();
			String fieldName = item.getFieldName();
			FilePath dirPath = null;
			if (fieldName.equals("upfile"))
			{
				try
				{
					connection.startTransaction();
					String path=req.getPathInfo();
					String fileName = item.getName();
					if (fileName.indexOf('/') >= 0)
						throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);
					dirPath = FilePath.getFilePath(path);
					FilePath reqPath = dirPath.getChild(fileName);
					if (reqPath == null || reqPath.getNumComponents() < 2)
						throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);

					// Should be an absolute path
					reqPath = reqPath.removeLeadingComponent();
					String fsIDStr = reqPath.getComponent(0);
					IndelibleFSVolumeIF volume = getVolume(fsIDStr);
					if (volume == null)
						throw new IndelibleWebAccessException(IndelibleWebAccessException.kVolumeNotFoundError, null);
					FilePath createPath = reqPath.removeLeadingComponent();
					FilePath parentPath = createPath.getParent();
					FilePath childPath = createPath.getPathRelativeTo(parentPath);
					if (childPath.getNumComponents() != 1)
						throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);
					IndelibleFileNodeIF parentNode = volume.getObjectByPath(parentPath);
					if (!parentNode.isDirectory())
						throw new IndelibleWebAccessException(IndelibleWebAccessException.kNotDirectory, null);
					IndelibleDirectoryNodeIF parentDirectory = (IndelibleDirectoryNodeIF)parentNode;
					IndelibleFileNodeIF childNode = null;
					childNode = parentDirectory.getChildNode(childPath.getName());
					if (childNode == null)
					{
						try {
							CreateFileInfo childInfo = parentDirectory.createChildFile(childPath.getName(), true);
							childNode = childInfo.getCreatedNode();
						} catch (FileExistsException e) {
							// Probably someone else beat us to it...
							childNode = parentDirectory.getChildNode(childPath.getName());
						}
					}
					else
					{

					}
					if (childNode.isDirectory())
						throw new IndelibleWebAccessException(IndelibleWebAccessException.kNotFile, null);

					IndelibleFSForkIF dataFork = childNode.getFork("data", true);
					dataFork.truncate(0);
					InputStream readStream =item.openStream();
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
					connection.commit();
					completedOK = true;
				} catch (PermissionDeniedException e) {
					throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
				} catch (IOException e) {
					throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
				} catch (ObjectNotFoundException e) {
					throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
				} catch (ForkNotFoundException e) {
					throw new IndelibleWebAccessException(IndelibleWebAccessException.kForkNotFound, e);
				}
				finally
				{
					if (!completedOK)
						try {
							connection.rollback();
						} catch (IOException e) {
							throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
						}
				}
				listPath(dirPath, buildDoc);
				return;
			}
		}
		throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);
	}
	
	public void createFilePut(HttpServletRequest req, HttpServletResponse resp, Document buildDoc) throws RemoteException, IndelibleWebAccessException
	{
		boolean completedOK = false;
		try
		{
			connection.startTransaction();
	        Element rootElem = buildDoc.createElement("createFile");
	        buildDoc.appendChild(rootElem);
			String path=req.getPathInfo();

			FilePath reqPath = FilePath.getFilePath(path);
			if (reqPath == null || reqPath.getNumComponents() < 2)
				throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);

			// Should be an absolute path
			reqPath = reqPath.removeLeadingComponent();
			String fsIDStr = reqPath.getComponent(0);
			IndelibleFSVolumeIF volume = getVolume(fsIDStr);
			if (volume == null)
				throw new IndelibleWebAccessException(IndelibleWebAccessException.kVolumeNotFoundError, null);
			FilePath createPath = reqPath.removeLeadingComponent();
			FilePath parentPath = createPath.getParent();
			FilePath childPath = createPath.getPathRelativeTo(parentPath);
			if (childPath.getNumComponents() != 1)
				throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);
			IndelibleFileNodeIF parentNode = volume.getObjectByPath(parentPath);
			if (!parentNode.isDirectory())
				throw new IndelibleWebAccessException(IndelibleWebAccessException.kNotDirectory, null);
			IndelibleDirectoryNodeIF parentDirectory = (IndelibleDirectoryNodeIF)parentNode;
			IndelibleFileNodeIF childNode = null;
			childNode = parentDirectory.getChildNode(childPath.getName());
			if (childNode == null)
			{
				try {
					CreateFileInfo childInfo = parentDirectory.createChildFile(childPath.getName(), true);
					childNode = childInfo.getCreatedNode();
				} catch (FileExistsException e) {
					// Probably someone else beat us to it...
					childNode = parentDirectory.getChildNode(childPath.getName());
				}
			}
			else
			{
				
			}
			if (childNode.isDirectory())
				throw new IndelibleWebAccessException(IndelibleWebAccessException.kNotFile, null);
			
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
			connection.commit();
			completedOK = true;
			XMLUtils.appendSingleValElement(buildDoc, rootElem, "fileID", childNode.getObjectID().toString());
			XMLUtils.appendSingleValElement(buildDoc, rootElem, "copied", Long.toString(bytesCopied));
		} catch (PermissionDeniedException e) {
			throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
		} catch (IOException e) {
			throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
		} catch (ObjectNotFoundException e) {
			throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
		} catch (ForkNotFoundException e) {
			throw new IndelibleWebAccessException(IndelibleWebAccessException.kForkNotFound, e);
		}
		finally
		{
			if (!completedOK)
				try {
					connection.rollback();
				} catch (IOException e) {
					throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
				}
		}
	}
    public void listVolumes(HttpServletRequest req, HttpServletResponse resp, Document buildDoc) throws IOException
    {
        Element rootElem = buildDoc.createElement("volumesList");
        buildDoc.appendChild(rootElem);
        IndelibleFSObjectID [] volumeIDs = connection.listVolumes();
        for (IndelibleFSObjectID curVolumeID:volumeIDs)
        {
        	try
			{
				IndelibleFSVolumeIF curVolume = connection.retrieveVolume(curVolumeID);
            	Element volumeElement = buildDoc.createElement("volume");
            	XMLUtils.appendSingleValElement(buildDoc, volumeElement, "id", curVolumeID.toString());
	        	HashMap<String, Object>volumeResources = curVolume.getMetaDataResource(IndelibleFSVolumeIF.kVolumeResourcesName);
	        	if (volumeResources != null)
	        	{
	        		String volumeName = (String) volumeResources.get(IndelibleFSVolumeIF.kVolumeNamePropertyName);
	        		if (volumeName != null)
	        		{
	        			XMLUtils.appendSingleValElement(buildDoc, volumeElement, "name", volumeName);
	        		}
	        	}
	        	rootElem.appendChild(volumeElement);
			} catch (VolumeNotFoundException e)
			{
				// TODO Auto-generated catch block
				Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
			} catch (PermissionDeniedException e)
			{
				// TODO Auto-generated catch block
				Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
			} catch (IOException e)
			{
				// TODO Auto-generated catch block
				Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
			}

        }
    }
    
    public void list(HttpServletRequest req, HttpServletResponse resp, Document buildDoc) throws RemoteException, IndelibleWebAccessException
    {

        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);

        listPath(reqPath, buildDoc);
    }


	protected void listPath(FilePath reqPath, Document buildDoc) throws IndelibleWebAccessException
	{
        Element rootElem = buildDoc.createElement("list");
        buildDoc.appendChild(rootElem);
		// Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSVolumeIF volume = getVolume(fsIDStr);
		if (volume == null)
			throw new IndelibleWebAccessException(IndelibleWebAccessException.kVolumeNotFoundError, null);
        FilePath listPath = reqPath.removeLeadingComponent();
        XMLUtils.appendSingleValElement(buildDoc, rootElem, "path", reqPath.toString());
        XMLUtils.appendSingleValElement(buildDoc, rootElem, "parentPath", reqPath.getParent().toString());
        try
        {
            IndelibleFileNodeIF listFile = volume.getObjectByPath(listPath);
            if (listFile.isDirectory())
            {
                IndelibleDirectoryNodeIF listDir = (IndelibleDirectoryNodeIF)listFile;
                String [] children = listDir.list();
                Arrays.sort(children);
                for (String curChildName:children)
                {
                    IndelibleFileNodeIF curChild = listDir.getChildNode(curChildName);
                    Element curFileElement = listIndelibleFile(reqPath.getChild(curChildName).toString(), curChildName, curChild, buildDoc);
                    rootElem.appendChild(curFileElement);
                }
            }
            else
            {
            	Element curFileElement = listIndelibleFile(listPath.toString(), listPath.getName(), listFile, buildDoc);
            	rootElem.appendChild(curFileElement);
            }
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
        } catch (PermissionDeniedException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
        } catch (IOException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
        }
        return;
	}
    
    public void mkdir(HttpServletRequest req, HttpServletResponse resp, Document buildDoc) throws RemoteException, IndelibleWebAccessException
    {
        Element rootElem = buildDoc.createElement("mkdir");
        buildDoc.appendChild(rootElem);
        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);

        // Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSVolumeIF volume = getVolume(fsIDStr);
		if (volume == null)
			throw new IndelibleWebAccessException(IndelibleWebAccessException.kVolumeNotFoundError, null);
        FilePath mkdirPath = reqPath.removeLeadingComponent();
        try
        {
        	FilePath parentPath = mkdirPath.getParent();
        	FilePath childPath = mkdirPath.getPathRelativeTo(parentPath);
			if (childPath.getNumComponents() != 1)
				throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);
			IndelibleFileNodeIF parentNode = (IndelibleFileNodeIF) volume.getObjectByPath(parentPath);
			if (!parentNode.isDirectory())
				throw new IndelibleWebAccessException(IndelibleWebAccessException.kNotDirectory, null);
			IndelibleDirectoryNodeIF parentDirectory = (IndelibleDirectoryNodeIF)parentNode;
			connection.startTransaction();
			CreateDirectoryInfo createInfo = parentDirectory.createChildDirectory(childPath.getName());
			connection.commit();
			XMLUtils.appendSingleValElement(buildDoc, rootElem, "fileID", createInfo.getCreatedNode().getObjectID().toString());
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
        } catch (PermissionDeniedException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
        } catch (IOException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
        } catch (FileExistsException e) {
			throw new IndelibleWebAccessException(IndelibleWebAccessException.kDestinationExists, e);
		}
    }
    
    public Element listIndelibleFile(String path, String name, IndelibleFileNodeIF file, Document buildDoc) throws IOException
    {
        Element returnElement = buildDoc.createElement("file");
        XMLUtils.appendSingleValElement(buildDoc, returnElement, "path", path);
        XMLUtils.appendSingleValElement(buildDoc, returnElement, "name", name);
        XMLUtils.appendSingleValElement(buildDoc, returnElement, "length", Long.toString(file.totalLength()));
        if (file.isDirectory())
        	XMLUtils.appendSingleValElement(buildDoc, returnElement, "directory", "Y");
        else
        	XMLUtils.appendSingleValElement(buildDoc, returnElement, "directory", "N");
        return returnElement;
    }
    
    public void duplicate(HttpServletRequest req, HttpServletResponse resp, String destPathStr, Document buildDoc) throws IndelibleWebAccessException, PermissionDeniedException, RemoteException, IOException
    {
        Element rootElem = buildDoc.createElement("duplicate");
        buildDoc.appendChild(rootElem);
        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || destPathStr == null || reqPath.getNumComponents() < 3)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);

        // Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSVolumeIF volume = getVolume(fsIDStr);
        FilePath sourcePath = reqPath.removeLeadingComponent();

        FilePath destPath = FilePath.getFilePath(destPathStr);
        if (destPath == null)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);
        
        IndelibleFileNodeIF sourceFile = null, checkFile = null;
        
        // Our semantics on the name of the created file are the same as Unix cp.  If the create path specifies a directory, the
        // name will be the source file name (unless no source file is specified, in which case it's an error).  If the create path
        // specifies a non-existing file, then the create path name will be used (if it specifies an existing file then we copy over the existing file)
        String createName = null;
        if (sourcePath != null)
        {
            createName = sourcePath.getName();
        }
        connection.startTransaction();
        try
        {
            sourceFile = volume.getObjectByPath(sourcePath);
        } catch (ObjectNotFoundException e1)
        {
            connection.rollback();
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e1);
        }

        IndelibleFSObjectID createdID;
        
        try
        {
            checkFile = volume.getObjectByPath(destPath);
            if (checkFile != null && !checkFile.isDirectory())
            {
                throw new IndelibleWebAccessException(IndelibleWebAccessException.kDestinationExists, null);
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
                throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, null);
            }
        }
        if (checkFile.isDirectory())
        {
            IndelibleDirectoryNodeIF parentDir = (IndelibleDirectoryNodeIF)checkFile;
            if (sourceFile != null)
            {

                    try {
                        CreateFileInfo createFileInfo = parentDir.createChildFile(createName, sourceFile, true);
                        XMLUtils.appendSingleValElement(buildDoc, rootElem, "fileID", createFileInfo.getCreatedNode().getObjectID().toString());
                    } catch (FileExistsException e) {
                        throw new IndelibleWebAccessException(IndelibleWebAccessException.kDestinationExists, e);
                    } catch (ObjectNotFoundException e) {
                        throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
                    } catch (NotFileException e) {
                        throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
                    }
            }
        }
        else
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kNotDirectory, null);
        }
        connection.commit();
    }
    
    public void rename(HttpServletRequest req, HttpServletResponse resp, String destPathStr, Document buildDoc) throws IndelibleWebAccessException, PermissionDeniedException, RemoteException, IOException
    {
        Element rootElem = buildDoc.createElement("duplicate");
        buildDoc.appendChild(rootElem);
        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || destPathStr == null || reqPath.getNumComponents() < 3)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);

        // Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSVolumeIF volume = getVolume(fsIDStr);
        FilePath sourcePath = reqPath.removeLeadingComponent();
        sourcePath = FilePath.getFilePath("/").getChild(sourcePath);	// Make sourcePath absolute
        FilePath destinationPath = FilePath.getFilePath(destPathStr);
        IndelibleFileNodeIF sourceFile = null;
        FilePath destPath = FilePath.getFilePath(destPathStr);
        if (destPath == null)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);
        String createName = null;
        if (sourcePath != null)
        {
            createName = sourcePath.getName();
        }
        try
        {
        	volume.moveObject(sourcePath, destinationPath);
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
        } catch (FileExistsException e)
		{
        	throw new IndelibleWebAccessException(IndelibleWebAccessException.kDestinationExists, e);
		} catch (NotDirectoryException e)
		{
			throw new IndelibleWebAccessException(IndelibleWebAccessException.kNotDirectory, e);
		}
        
    }
    public IndelibleFSVolumeIF getVolume(String fsIDStr) throws IndelibleWebAccessException
    {
        IndelibleFSObjectID retrieveVolumeID = (IndelibleFSObjectID) ObjectIDFactory.reconstituteFromString(fsIDStr);
        if (retrieveVolumeID == null)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidVolumeID, null);
        }
        
        IndelibleFSVolumeIF volume = null;
        try
        {
            volume = connection.retrieveVolume(retrieveVolumeID);
        } catch (VolumeNotFoundException e1)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kVolumeNotFoundError, e1);
        } catch (IOException e1)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e1);
        }
        if (volume == null)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kVolumeNotFoundError, null);
        }
        return volume;
    }
    
    public void get(HttpServletRequest req, HttpServletResponse resp) throws RemoteException, IndelibleWebAccessException
    {
        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);

        // Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSVolumeIF volume = getVolume(fsIDStr);
        FilePath listPath = reqPath.removeLeadingComponent();

        try
        {
            IndelibleFileNodeIF getFile = volume.getObjectByPath(listPath);
            if (getFile.isDirectory())
                throw new IndelibleWebAccessException(IndelibleWebAccessException.kNotFile, null);
            resp.setContentType("application/x-download");
            resp.setHeader("Content-Disposition", "attachment; filename=\"" +listPath.getName()+"\"");
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
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
        } catch (PermissionDeniedException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
        } catch (IOException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
        } catch (ForkNotFoundException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kForkNotFound, e);
        }
        return;
    }

    public void delete(HttpServletRequest req, HttpServletResponse resp, Document buildDoc)  throws IndelibleWebAccessException, PermissionDeniedException, RemoteException, IOException
    {
    	String path=req.getPathInfo();
    	FilePath reqPath = FilePath.getFilePath(path);
    	if (reqPath == null || reqPath.getNumComponents() < 3)
    		throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);
    	FilePath dirPath = reqPath.removeTrailingComponent();
    	// Should be an absolute path
    	reqPath = reqPath.removeLeadingComponent();
    	String fsIDStr = reqPath.getComponent(0);
    	IndelibleFSVolumeIF volume = getVolume(fsIDStr);
    	FilePath deletePath = reqPath.removeLeadingComponent();
    	FilePath parentPath = deletePath.getParent();
    	String deleteName = deletePath.getName();

    	IndelibleDirectoryNodeIF parentNode = null;

    	connection.startTransaction();
    	try
    	{
    		IndelibleFileNodeIF parentObject = volume.getObjectByPath(parentPath);
    		if (!(parentObject instanceof IndelibleDirectoryNodeIF))
    		{
    			connection.rollback();
    			throw new IndelibleWebAccessException(IndelibleWebAccessException.kNotDirectory, null);
    		}
    		parentNode = (IndelibleDirectoryNodeIF)parentObject; 
    	} catch (ObjectNotFoundException e1)
    	{
    		connection.rollback();
    		throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e1);
    	}


    	try {
    		parentNode.deleteChild(deleteName);
    	} catch (PermissionDeniedException e) 
    	{
    		throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
    	} catch (CannotDeleteDirectoryException e) {
    		throw new IndelibleWebAccessException(IndelibleWebAccessException.kNotFile, e);
    	}
    	
    	connection.commit();
    	listPath(dirPath, buildDoc);
    }

    public void rmdir(HttpServletRequest req, HttpServletResponse resp, Document buildDoc)  throws IndelibleWebAccessException, PermissionDeniedException, RemoteException, IOException
    {
        Element rootElem = buildDoc.createElement("rmdir");
        buildDoc.appendChild(rootElem);
    	String path=req.getPathInfo();
    	FilePath reqPath = FilePath.getFilePath(path);
    	if (reqPath == null || reqPath.getNumComponents() < 3)
    		throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);

    	// Should be an absolute path
    	reqPath = reqPath.removeLeadingComponent();
    	String fsIDStr = reqPath.getComponent(0);
    	IndelibleFSVolumeIF volume = getVolume(fsIDStr);
    	FilePath deletePath = reqPath.removeLeadingComponent();
    	FilePath parentPath = deletePath.getParent();
    	String deleteName = deletePath.getName();

    	IndelibleDirectoryNodeIF parentNode = null;

    	connection.startTransaction();
    	try
    	{
    		IndelibleFileNodeIF parentObject = volume.getObjectByPath(parentPath);
    		if (!(parentObject instanceof IndelibleDirectoryNodeIF))
    		{
    			connection.rollback();
    			throw new IndelibleWebAccessException(IndelibleWebAccessException.kNotDirectory, null);
    		}
    		parentNode = (IndelibleDirectoryNodeIF)parentObject; 
    	} catch (ObjectNotFoundException e1)
    	{
    		connection.rollback();
    		throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e1);
    	}


    	try {
    		parentNode.deleteChildDirectory(deleteName);
    	} catch (PermissionDeniedException e) 
    	{
    		throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
    	} catch (NotDirectoryException e) {
    		throw new IndelibleWebAccessException(IndelibleWebAccessException.kNotDirectory, e);
    	}
    	
    	connection.commit();
    }
    
    public void allocate(HttpServletRequest req, HttpServletResponse resp, String lengthString, Document buildDoc) throws RemoteException, IndelibleWebAccessException
    {
        Element rootElem = buildDoc.createElement("allocate");
        buildDoc.appendChild(rootElem);
        String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);
        long allocateLength;
        try
        {
        	allocateLength = Long.parseLong(lengthString);
        }
        catch (NumberFormatException e)
        {
        	throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, e);
        }
        // Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSVolumeIF volume = getVolume(fsIDStr);
		if (volume == null)
			throw new IndelibleWebAccessException(IndelibleWebAccessException.kVolumeNotFoundError, null);
        FilePath allocatePath = reqPath.removeLeadingComponent();
        try
        {
        	FilePath parentPath = allocatePath.getParent();
        	FilePath childPath = allocatePath.getPathRelativeTo(parentPath);
			if (childPath.getNumComponents() != 1)
				throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);
			IndelibleFileNodeIF parentNode = volume.getObjectByPath(parentPath);
			if (!parentNode.isDirectory())
				throw new IndelibleWebAccessException(IndelibleWebAccessException.kNotDirectory, null);
			IndelibleDirectoryNodeIF parentDirectory = (IndelibleDirectoryNodeIF)parentNode;
			connection.startTransaction();
			IndelibleFileNodeIF childNode = null;
			childNode = parentDirectory.getChildNode(childPath.getName());
			if (childNode == null)
			{
					CreateFileInfo childInfo = parentDirectory.createChildFile(childPath.getName(), true);
					childNode = childInfo.getCreatedNode();
					IndelibleFSForkIF dataFork = childNode.getFork("data", true);
					dataFork.extend(allocateLength);
					XMLUtils.appendSingleValElement(buildDoc, rootElem, "fileID", childInfo.getCreatedNode().getObjectID().toString());
					XMLUtils.appendSingleValElement(buildDoc, rootElem, "length", Long.toString(allocateLength));
			}
			else
			{
				throw new IndelibleWebAccessException(IndelibleWebAccessException.kDestinationExists, null);
			}
			if (childNode.isDirectory())
				throw new IndelibleWebAccessException(IndelibleWebAccessException.kNotFile, null);
			

			connection.commit();
			
        } catch (ObjectNotFoundException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, e);
        } catch (PermissionDeniedException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kPermissionDenied, e);
        } catch (IOException e)
        {
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
        } catch (FileExistsException e) {
			throw new IndelibleWebAccessException(IndelibleWebAccessException.kDestinationExists, e);
		} catch (ForkNotFoundException e)
		{
			throw new IndelibleWebAccessException(IndelibleWebAccessException.kInternalError, e);
		}
    }
    
    public void doError(HttpServletResponse resp, IndelibleWebAccessException exception)
    {
        resp.setStatus(500);
        /*
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
        */
    }
}

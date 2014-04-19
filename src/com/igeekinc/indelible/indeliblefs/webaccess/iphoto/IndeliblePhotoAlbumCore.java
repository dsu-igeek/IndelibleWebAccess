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
 
package com.igeekinc.indelible.indeliblefs.webaccess.iphoto;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.servlet.ServletContext;

import org.apache.log4j.Logger;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;

import com.igeekinc.indelible.indeliblefs.IndelibleDirectoryNodeIF;
import com.igeekinc.indelible.indeliblefs.IndelibleFSClient;
import com.igeekinc.indelible.indeliblefs.IndelibleFSVolumeIF;
import com.igeekinc.indelible.indeliblefs.IndelibleFileNodeIF;
import com.igeekinc.indelible.indeliblefs.IndelibleServerConnectionIF;
import com.igeekinc.indelible.indeliblefs.exceptions.ObjectNotFoundException;
import com.igeekinc.indelible.indeliblefs.exceptions.PermissionDeniedException;
import com.igeekinc.indelible.indeliblefs.exceptions.VolumeNotFoundException;
import com.igeekinc.indelible.indeliblefs.proxies.IndelibleFSServerProxy;
import com.igeekinc.indelible.indeliblefs.security.EntityAuthenticationServer;
import com.igeekinc.indelible.indeliblefs.webaccess.IndelibleWebAccessException;
import com.igeekinc.indelible.oid.IndelibleFSObjectID;
import com.igeekinc.util.FilePath;
import com.igeekinc.util.logging.ErrorLogMessage;

public class IndeliblePhotoAlbumCore
{
	private Logger logger;
	protected IndelibleFSServerProxy fsServer;
	protected IndelibleServerConnectionIF connection;
	protected EntityAuthenticationServer securityServer;
	private HashMap<IndelibleFSObjectID, LibraryPaths>libraryVolumes = new HashMap<IndelibleFSObjectID, LibraryPaths>();
	private HashMap<IndelibleFSObjectID, iPhotoLibrary>libraries = new HashMap<IndelibleFSObjectID, iPhotoLibrary>();
	public static final String kPhotoAlbumMetaDataPropertyName = "com.igeekinc.indelible.photoalbum";
	public static final String kiPhotoBaseDirPropertyName = "com.igeekinc.indelible.photoalbum.iphotobasedir";
	public static final String kiPhotoIndelibleDirPropertyName = "com.igeekinc.indelible.photoalbum.iphotoindelibledir";
	public static final String kPhotoAlbumAccessControlPropertyName = "com.igeekinc.indelible.photoalbum.accesscontrol";
	public IndeliblePhotoAlbumCore(ServletContextHandler context) throws IOException, InterruptedException
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
		IndelibleFSObjectID [] volumeIDs = connection.listVolumes();
		for (IndelibleFSObjectID curObjectID:volumeIDs)
		{
			try
			{
				IndelibleFSVolumeIF curVolume = connection.retrieveVolume(curObjectID);
				HashMap<String, Object>photoAlbumMD = curVolume.getMetaDataResource(kPhotoAlbumMetaDataPropertyName);
				if (photoAlbumMD != null)
				{
					String iPhotoBaseDirStr = (String) photoAlbumMD.get(kiPhotoBaseDirPropertyName);
					FilePath iPhotoBaseDirPath = FilePath.getFilePath(iPhotoBaseDirStr);
					String iPhotoIndelibleDirStr = (String)photoAlbumMD.get(kiPhotoIndelibleDirPropertyName);
					FilePath iPhotoIndelibleDirPath = FilePath.getFilePath(iPhotoIndelibleDirStr);
					IndelibleFileNodeIF iPhotoBaseNode = curVolume.getObjectByPath(iPhotoIndelibleDirPath);
					if (iPhotoBaseNode.isDirectory())
					{
						LibraryPaths libraryPaths = new LibraryPaths(iPhotoBaseDirPath, iPhotoIndelibleDirPath);
						libraryVolumes.put(curObjectID, libraryPaths);
						setupPasswords(context, curObjectID, photoAlbumMD);
					}
				}
			} catch (VolumeNotFoundException e)
			{
				// TODO Auto-generated catch block
				Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
			} catch (PermissionDeniedException e)
			{
				// TODO Auto-generated catch block
				Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
			} catch (ObjectNotFoundException e)
			{
				// TODO Auto-generated catch block
				Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
			}
		}
	}
	
	private void setupPasswords(ServletContextHandler context, IndelibleFSObjectID volumeID, HashMap<String, Object> photoAlbumMD)
	{

		Object accessControlObject = photoAlbumMD.get(kPhotoAlbumAccessControlPropertyName);
		if (accessControlObject != null && accessControlObject instanceof String)
		{
			String accessControl = (String)accessControlObject;
			if (accessControl.indexOf('=') > 0)
			{
				String user = accessControl.substring(0, accessControl.indexOf('='));
				String passwordString = accessControl.substring(accessControl.indexOf('=') + 1);
				ConstraintSecurityHandler constraintSecurityHandler = (ConstraintSecurityHandler) context.getSecurityHandler();
				HashLoginService loginService = (HashLoginService) constraintSecurityHandler.getLoginService();
				Credential password = Credential.getCredential(passwordString);

				loginService.putUser(user, password, new String[]{"user"});
		        Constraint constraint = new Constraint();
		        constraint.setName(Constraint.__BASIC_AUTH);
		        constraint.setRoles(new String[]{"user"});
		        constraint.setAuthenticate(true);
		        
		        ConstraintMapping cm = new ConstraintMapping();
		        cm.setConstraint(constraint);
		        cm.setPathSpec("/photos/"+volumeID.toString()+"/*");
		        constraintSecurityHandler.addConstraintMapping(cm);
				/*
		        HashLoginService loginService = new HashLoginService();
		        loginService.setName("IndeliblePhoto Realm");
		        
		        Credential password = Credential.getCredential(passwordString);

				loginService.putUser(user, password, new String[]{"user"});
		        Constraint constraint = new Constraint();
		        constraint.setName(Constraint.__BASIC_AUTH);
		        constraint.setRoles(new String[]{"user"});
		        constraint.setAuthenticate(true);
		        
		        ConstraintMapping cm = new ConstraintMapping();
		        cm.setConstraint(constraint);
		        cm.setPathSpec("/photos/"+volumeID.toString()+"/*");
		        ConstraintSecurityHandler constraintSecurityHandler = new ConstraintSecurityHandler();
		        constraintSecurityHandler.addConstraintMapping(cm);
		        constraintSecurityHandler.setLoginService(loginService);
				context.setSecurityHandler(constraintSecurityHandler);
				*/
			}
		}
	}

	public LibraryVolumeInfo [] getLibraryVolumeInfo()
	{
		
		IndelibleFSObjectID [] volumeIDs = new IndelibleFSObjectID[libraryVolumes.keySet().size()];
		volumeIDs = libraryVolumes.keySet().toArray(volumeIDs);
		ArrayList<LibraryVolumeInfo> returnVolumeInfoList = new ArrayList<LibraryVolumeInfo>();
		for (IndelibleFSObjectID curVolumeID:volumeIDs)
        {
        	try
			{
				IndelibleFSVolumeIF curVolume = connection.retrieveVolume(curVolumeID);
	        	HashMap<String, Object>volumeResources = curVolume.getMetaDataResource(IndelibleFSVolumeIF.kVolumeResourcesName);
	        	String volumeName = null;
	        	if (volumeResources != null)
	        	{
	        		volumeName = (String) volumeResources.get(IndelibleFSVolumeIF.kVolumeNamePropertyName);
	        	}
	        	returnVolumeInfoList.add(new LibraryVolumeInfo(curVolumeID, volumeName));
			} catch (VolumeNotFoundException e)
			{
				// TODO Auto-generated catch block
				Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
			} catch (RemoteException e)
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
			} finally {}
        }
		LibraryVolumeInfo [] returnVolumeInfo = new LibraryVolumeInfo[returnVolumeInfoList.size()];
		returnVolumeInfo = returnVolumeInfoList.toArray(returnVolumeInfo);
		return returnVolumeInfo;
	}
	
	public boolean isVolumeALibrary(IndelibleFSObjectID checkVolumeID)
	{
        return (libraryVolumes.containsKey(checkVolumeID));
	}
	
	public iPhotoLibrary getLibraryForVolume(IndelibleFSObjectID volumeID) throws IndelibleWebAccessException, IOException, ObjectNotFoundException, PermissionDeniedException
	{
		 if (!isVolumeALibrary(volumeID))
	        	throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, null);
		 
		IndelibleFSVolumeIF volume = getVolume(volumeID);
		if (volume == null)
			throw new IndelibleWebAccessException(IndelibleWebAccessException.kVolumeNotFoundError, null);
		LibraryPaths libraryPaths = libraryVolumes.get(volumeID);
		FilePath libraryFilePath = libraryPaths.getiPhotoIndelibleDirPath();
		iPhotoLibrary library = libraries.get(volumeID);
		
		if (library != null)
		{
			long timeSinceLastCheck = System.currentTimeMillis() - library.getLastCheckTime();
			if (timeSinceLastCheck > 10000)
			{
				// OK, let's check to see if the library file has been updated
				if (library.needsReload())
				{
					library = null;
				}
			}
		}
		if (library == null)
		{
			library = new iPhotoLibrary(volume, libraryFilePath, libraryPaths.getiPhotoBaseDirPath());
			libraries.put(volumeID, library);
		}
		return library;
	}
	
    public IndelibleFSVolumeIF getVolume(IndelibleFSObjectID retrieveVolumeID) throws IndelibleWebAccessException
    {
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
}

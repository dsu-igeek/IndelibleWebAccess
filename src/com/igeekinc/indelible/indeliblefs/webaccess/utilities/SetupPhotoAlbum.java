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
 
package com.igeekinc.indelible.indeliblefs.webaccess.utilities;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import java.io.IOException;
import java.rmi.RemoteException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.eclipse.jetty.util.security.Credential;

import com.igeekinc.indelible.indeliblefs.IndelibleFSVolumeIF;
import com.igeekinc.indelible.indeliblefs.exceptions.PermissionDeniedException;
import com.igeekinc.indelible.indeliblefs.exceptions.VolumeNotFoundException;
import com.igeekinc.indelible.indeliblefs.remote.IndelibleFileNodeRemote;
import com.igeekinc.indelible.indeliblefs.security.AuthenticationFailureException;
import com.igeekinc.indelible.indeliblefs.utilities.IndelibleFSUtilBase;
import com.igeekinc.indelible.indeliblefs.webaccess.iphoto.IndeliblePhotoAlbumCore;
import com.igeekinc.indelible.oid.IndelibleFSObjectID;
import com.igeekinc.indelible.oid.ObjectIDFactory;
import com.igeekinc.util.logging.ErrorLogMessage;

public class SetupPhotoAlbum extends IndelibleFSUtilBase
{
    private IndelibleFSObjectID	retrieveVolumeID;
	private HashMap<String, Object>	properties;
	public SetupPhotoAlbum() throws IOException,
            UnrecoverableKeyException, InvalidKeyException, KeyStoreException,
            NoSuchAlgorithmException, CertificateException,
            IllegalStateException, NoSuchProviderException, SignatureException,
            AuthenticationFailureException, InterruptedException
    {
        // TODO Auto-generated constructor stub
    }
    
    @Override
	public void processArgs(String[] args) throws Exception
	{
    	LongOpt [] longOptions = {
                new LongOpt("fsid", LongOpt.REQUIRED_ARGUMENT, null, 'f'),
                new LongOpt("albumPath", LongOpt.REQUIRED_ARGUMENT, null, 'p'),
                new LongOpt("basePath", LongOpt.REQUIRED_ARGUMENT, null, 'b'),
                new LongOpt("user", LongOpt.REQUIRED_ARGUMENT, null, 'u'),
                new LongOpt("password", LongOpt.REQUIRED_ARGUMENT, null, 'w'),
                new LongOpt("verbose", LongOpt.NO_ARGUMENT, null, 'v')
        };
       // Getopt getOpt = new Getopt("MultiFSTestRunner", args, "p:ns:", longOptions);
        Getopt getOpt = new Getopt("IndelibleList", args, "f:p:u:w", longOptions);
        
        int opt;
        String fsIDStr = null, albumPath = null, basePath = null, albumUser = null, albumPassword = null;
        properties = new HashMap<String, Object>();
        while ((opt = getOpt.getopt()) != -1)
        {
            switch(opt)
            {
            case 'f':
                fsIDStr = getOpt.getOptarg();
                break;
            case 'p':
            	albumPath = getOpt.getOptarg();
            	break;
            case 'b':
            	basePath = getOpt.getOptarg();
            	break;
            case 'u':
            	albumUser = getOpt.getOptarg();
            	break;
            case 'w':
            	albumPassword = getOpt.getOptarg();
            	break;
            case 'v':
             	increaseVerbosity();
             	break;
            }
        }
        
        if (fsIDStr == null || albumPath == null || basePath == null || (albumUser != null && albumPassword == null))
        {
            showUsage();
            System.exit(0);
        }
        properties.put(IndeliblePhotoAlbumCore.kiPhotoIndelibleDirPropertyName, albumPath);
        properties.put(IndeliblePhotoAlbumCore.kiPhotoBaseDirPropertyName, basePath);
        if (albumUser != null)
        {
        	String md5Password = Credential.MD5.digest(albumPassword);
        	String accessControl = albumUser+"="+md5Password;
        	properties.put(IndeliblePhotoAlbumCore.kPhotoAlbumAccessControlPropertyName, accessControl);
        }
                
        retrieveVolumeID = (IndelibleFSObjectID) ObjectIDFactory.reconstituteFromString(fsIDStr);
        if (retrieveVolumeID == null)
        {
            System.err.println("Invalid volume ID "+retrieveVolumeID);
            System.exit(1);
        }
        

	}

    @Override
	public void runApp()
    {   
        IndelibleFSVolumeIF volume = null;
        try
        {
            volume = connection.retrieveVolume(retrieveVolumeID);
        } catch (VolumeNotFoundException e1)
        {
            System.err.println("Could not find volume ID "+retrieveVolumeID);
            System.exit(1);
        } catch (IOException e1)
        {
            Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e1);
            System.exit(1);
        }
        if (volume == null)
        {
            System.err.println("Could not find volume "+retrieveVolumeID);
            System.exit(1);
        }
        try
        {
            Map<String, Object>curMDProperties = volume.getMetaDataResource(IndeliblePhotoAlbumCore.kPhotoAlbumMetaDataPropertyName);
            if (curMDProperties == null)
            	curMDProperties = new HashMap<String, Object>();
            for (String curKey:properties.keySet())
            {
            	if (curMDProperties.containsKey(curKey))
            		System.out.println("Replacing "+curKey+"="+properties.get(curKey));
            	else
            		System.out.println("Adding "+curKey+"="+properties.get(curKey));
            	curMDProperties.put(curKey,  properties.get(curKey));
            }
            connection.startTransaction();
            volume.setMetaDataResource(IndeliblePhotoAlbumCore.kPhotoAlbumMetaDataPropertyName, curMDProperties);
            connection.commit();
        } catch (PermissionDeniedException e)
        {
            System.err.println("Permission denied");
            System.exit(1);
        } catch (RemoteException e)
        {
            Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
            System.exit(1);
        } catch (IOException e)
        {
            Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
            System.exit(1);
        }

        System.exit(0);
    }
    
    void listIndelibleFile(String listFileName, IndelibleFileNodeRemote listFile) throws RemoteException
    {
        System.out.println(listFile.totalLength()+" "+listFileName);
    }
    
    private void showUsage()
    {
        System.err.println("Usage: IndelibleListVolumeProperties --fsid <File System ID>");
    }
    public static void main(String [] args)
    {
        int retCode = 1;
        try
        {
            SetupPhotoAlbum icfs = new SetupPhotoAlbum();
            icfs.run(args);
            retCode = 0;
        } catch (Throwable t)
        {
        	t.printStackTrace();
        }
        finally
        {
            System.exit(retCode);
        }
    }
}

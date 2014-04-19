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
import java.rmi.RemoteException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.xerces.dom.DocumentImpl;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletContextHandler.Context;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.igeekinc.indelible.indeliblefs.exceptions.ObjectNotFoundException;
import com.igeekinc.indelible.indeliblefs.exceptions.PermissionDeniedException;
import com.igeekinc.indelible.indeliblefs.security.AuthenticationFailureException;
import com.igeekinc.indelible.indeliblefs.webaccess.iphoto.Album;
import com.igeekinc.indelible.indeliblefs.webaccess.iphoto.Image;
import com.igeekinc.indelible.indeliblefs.webaccess.iphoto.IndeliblePhotoAlbumCore;
import com.igeekinc.indelible.indeliblefs.webaccess.iphoto.iPhotoLibrary;
import com.igeekinc.indelible.oid.IndelibleFSObjectID;
import com.igeekinc.indelible.oid.ObjectIDFactory;
import com.igeekinc.util.FilePath;
import com.igeekinc.util.logging.ErrorLogMessage;

class ImageInfo
{
	private Album album;
	private Image image;
	public ImageInfo(Album album, Image image)
	{
		this.album = album;
		this.image = image;
	}
	public Album getAlbum()
	{
		return album;
	}
	public Image getImage()
	{
		return image;
	}
}
class RandomInfo
{
	public static final long kOneDayMS	= 24L * 3600L * 1000L;
	public static final long kThirtyDaysMS = 30L * kOneDayMS;
	public static final long kOneYearMS = 365L * kOneDayMS;
	
	private Random random = new Random();
	private Album lastAlbum;
	private int lastRetrieved;
	@SuppressWarnings("unchecked")
	private ArrayList<Album> albumSplits [] = new ArrayList[3];
	
	
	public RandomInfo(iPhotoLibrary library)
	{
		Album [] albums = library.getAlbums();
		for (int curAlbumNum = 0; curAlbumNum < albumSplits.length; curAlbumNum++)
			albumSplits[curAlbumNum] = new ArrayList<Album>();
		long now = System.currentTimeMillis();
		if (albums.length == 0)
			throw new InternalError("Must have at least one album in the library");
		for (Album curAlbum:albums)
		{
			if (curAlbum.getNumPhotos() > 0)
			{
				Image firstImage = curAlbum.getImageAtIndex(0);
				if (firstImage != null)
				{
					Date firstImageTime = firstImage.getDate();
					long delta = now - firstImageTime.getTime();
					if (delta < kThirtyDaysMS)
					{
						albumSplits[0].add(curAlbum);
					}
					else
					{
						if (delta < kOneYearMS)
						{
							albumSplits[1].add(curAlbum);
						}
						else
						{
							albumSplits[2].add(curAlbum);
						}
					}
				}
			}
		}
	}
	
	Album getRandomAlbum()
	{
		Album returnAlbum = null;
		while(returnAlbum == null)
		{
			int albumType = random.nextInt(3);
			if (albumSplits[albumType].size() > 0)
			{
				int albumNum = random.nextInt(albumSplits[albumType].size());
				returnAlbum =  albumSplits[albumType].get(albumNum);
			}
		}
		return returnAlbum;
	}
	
	public ImageInfo[] getNextImages(int numToReturn)
	{
		ImageInfo [] returnImages = new ImageInfo[numToReturn];
		for (int curImageNum = 0; curImageNum < numToReturn; curImageNum++)
		{
			lastRetrieved ++;
			while (lastAlbum == null || lastAlbum.getNumPhotos() <= lastRetrieved)
			{
				lastAlbum = getRandomAlbum();
				lastRetrieved = 0;
			}
			returnImages[curImageNum] = new ImageInfo(lastAlbum, lastAlbum.getImageAtIndex(lastRetrieved));
		}
		return returnImages;
	}
}

public class IndeliblePhotoAlbumRSSServlet extends XMLOutputServlet
{
	private static final long	serialVersionUID	= -5259200617035876170L;
	IndeliblePhotoAlbumCore core;
	private HashMap<String, Integer> lastRetrieved = new HashMap<String, Integer>();
	private HashMap<IndelibleFSObjectID, RandomInfo> randomInfo = new HashMap<IndelibleFSObjectID, RandomInfo>();
	public IndeliblePhotoAlbumRSSServlet() throws IOException, UnrecoverableKeyException, InvalidKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IllegalStateException, NoSuchProviderException, SignatureException, AuthenticationFailureException, InterruptedException, SAXException
	{
	}
	@Override
	public void init(ServletConfig config)
	throws ServletException
	{
		super.init(config);
		try
		{
			ServletContext context = config.getServletContext();
			if (context instanceof Context)
			{
				ServletContextHandler contextHandler = (ServletContextHandler)((Context)context).getContextHandler();
				core = new IndeliblePhotoAlbumCore(contextHandler);
				Document stylesheet =  XMLUtils.getDocument(IndelibleWebAccessServlet.class.getResourceAsStream("IndeliblePhotoAlbumRSSStylesheet.xsl"));
				setStylesheet(stylesheet);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
    protected String getContentType()
    {
    	return "text/xml";
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
				if (command.equals("rss"))
				{
					doRSS(req, resp, buildDoc);
				}
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
    public void doRSS(HttpServletRequest req, HttpServletResponse resp, DocumentImpl buildDoc) throws RemoteException, IndelibleWebAccessException
    {
    	String path=req.getPathInfo();
        FilePath reqPath = FilePath.getFilePath(path);
        if (reqPath == null || reqPath.getNumComponents() < 2)
            throw new IndelibleWebAccessException(IndelibleWebAccessException.kInvalidArgument, null);
		Element rootElem = buildDoc.createElement("albumlist");
        buildDoc.appendChild(rootElem);
		// Should be an absolute path
        reqPath = reqPath.removeLeadingComponent();
        String fsIDStr = reqPath.getComponent(0);
        IndelibleFSObjectID retrieveVolumeID = (IndelibleFSObjectID) ObjectIDFactory.reconstituteFromString(fsIDStr);
		String requestEncoded = reqPath.getComponent(1);
		
		String urlPrefix = req.getScheme()
			      + "://"
			      + req.getServerName()
			      + ":"
			      + req.getServerPort();
        try
        {
        	iPhotoLibrary library = core.getLibraryForVolume(retrieveVolumeID);
        	if (requestEncoded.equals("random"))
        	{
        		doRandom(retrieveVolumeID, library, buildDoc, rootElem, urlPrefix);
        	}
        	else
        	{
        		String albumToListGUID =  Base64.decode(requestEncoded);
        		listAlbum(retrieveVolumeID, library, albumToListGUID, buildDoc,
        				rootElem, urlPrefix);
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
	private void listAlbum(IndelibleFSObjectID retrieveVolumeID,
			iPhotoLibrary library, String albumToListGUID,
			DocumentImpl buildDoc, Element rootElem, String urlPrefix)
			throws IndelibleWebAccessException
	{
		Album albumToList = library.getAlbum(albumToListGUID);
		if (albumToList == null)
			throw new IndelibleWebAccessException(IndelibleWebAccessException.kPathNotFound, null);
		XMLUtils.appendSingleValElement(buildDoc, rootElem, "link", urlPrefix+"/photos/"+retrieveVolumeID.toString()+"/"+Base64.encode(albumToListGUID));
		String [] keys = albumToList.getKeys();
		String albumIDString = retrieveVolumeID.toString() + albumToListGUID;
		int startNum;
		if (lastRetrieved.containsKey(albumIDString))
		{
			startNum = lastRetrieved.get(albumIDString) + 10;
		}
		else
		{
			startNum = 0;
		}
		for (int curKeyNum = startNum ; curKeyNum < startNum + 10; curKeyNum++)
		{
			String curKey = keys[curKeyNum % keys.length];
			Element photoElement = buildDoc.createElement("photo");
			XMLUtils.appendSingleValElement(buildDoc, photoElement, "imagekey", curKey);
			Image retrieveImage = albumToList.getImageForKey(curKey);
			String fileType = retrieveImage.getImageMIMEType();
			XMLUtils.appendSingleValElement(buildDoc, photoElement, "imageType", fileType);
			rootElem.appendChild(photoElement);
			lastRetrieved.put(albumIDString, startNum);
		}
	}
    
	private void doRandom(IndelibleFSObjectID retrieveVolumeID, iPhotoLibrary library,
			DocumentImpl buildDoc, Element rootElem, String urlPrefix)
			throws IndelibleWebAccessException
	{
		RandomInfo ourRandomInfo = randomInfo.get(retrieveVolumeID);
		if (ourRandomInfo == null)
		{
			ourRandomInfo = new RandomInfo(library);
			randomInfo.put(retrieveVolumeID, ourRandomInfo);
		}
		
		ImageInfo [] images = ourRandomInfo.getNextImages(10);
		
		XMLUtils.appendSingleValElement(buildDoc, rootElem, "link", urlPrefix+"/photos/"+retrieveVolumeID.toString()+"/"+Base64.encode(images[0].getAlbum().getGUID()));
		
		for (ImageInfo curImage:images)
		{
			String curKey = curImage.getImage().getKey();
			Element photoElement = buildDoc.createElement("photo");
			XMLUtils.appendSingleValElement(buildDoc, photoElement, "imagekey", curKey);
			Image retrieveImage = curImage.getImage();
			String fileType = retrieveImage.getImageMIMEType();
			XMLUtils.appendSingleValElement(buildDoc, photoElement, "imageType", fileType);
			rootElem.appendChild(photoElement);
		}
	}
	public void doError(HttpServletResponse resp, IndelibleWebAccessException exception)
    {
        resp.setStatus(500);
    }
}

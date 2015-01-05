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

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.apache.log4j.Logger;
import org.imgscalr.Scalr;
import org.imgscalr.Scalr.Method;
import org.perf4j.log4j.Log4JStopWatch;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.XMLPropertyListParser;
import com.igeekinc.indelible.indeliblefs.IndelibleDirectoryNodeIF;
import com.igeekinc.indelible.indeliblefs.IndelibleFSForkIF;
import com.igeekinc.indelible.indeliblefs.IndelibleFSVolumeIF;
import com.igeekinc.indelible.indeliblefs.IndelibleFileNodeIF;
import com.igeekinc.indelible.indeliblefs.exceptions.ForkNotFoundException;
import com.igeekinc.indelible.indeliblefs.exceptions.ObjectNotFoundException;
import com.igeekinc.indelible.indeliblefs.exceptions.PermissionDeniedException;
import com.igeekinc.indelible.indeliblefs.remote.IndelibleFSForkRemoteInputStream;
import com.igeekinc.util.FilePath;
import com.igeekinc.util.datadescriptor.DataDescriptor;
import com.igeekinc.util.logging.ErrorLogMessage;
import com.igeekinc.util.perf.MBPerSecondLog4jStopWatch;

class CachedImage
{
	int width, height;
	byte [] bytes;
}
public class iPhotoLibrary
{
	private static final String	kPhotoLibraryExtension	= ".photolibrary";
	public static final String	kAlbumDataFileName	= "AlbumData.xml";
	public static final String	kMasterImageListKey	= "Master Image List";
	public static final String kListOfAlbumsKey = "List of Albums";
	public static final String kListOfFacesKey = "List of Faces";
	
	private HashMap<String, Image>images = new HashMap<String, Image>();
	private HashMap<String, Image>imagesByGUID = new HashMap<String, Image>();
	private IndelibleFSVolumeIF volume;
	private FilePath indeliblePath;
	private FilePath pathToRoot;	// Path to remove from image paths
	private IndelibleDirectoryNodeIF indelibleAlbumRoot;
	private File albumRoot;
	private HashMap<String, Album>albums = new HashMap<String, Album>();
	private HashMap<String, Face>facesByName = new HashMap<String, Face>();
	private HashMap<String, Face>facesByKey = new HashMap<String, Face>();
	private HashMap<String, byte []>faceCache = new HashMap<String, byte[]>();
	private HashMap<String, ArrayList<Image>> imagesByFace = new HashMap<String, ArrayList<Image>>();
	private ArrayList<Face>facesByOrder = new ArrayList<Face>();
	private HashMap<String, ArrayList<SoftReference<CachedImage>>>imageCache = new HashMap<String, ArrayList<SoftReference<CachedImage>>>();
	private long lastCheckTime, lastModifyTime;
	
	public iPhotoLibrary(File albumRoot) throws IOException
	{
		this(albumRoot, FilePath.getFilePath(albumRoot));
	}
	
	public iPhotoLibrary(File albumRoot, FilePath pathToRoot) throws IOException
	{
		this.albumRoot = albumRoot;
		this.pathToRoot = pathToRoot;
		if (!albumRoot.exists())
			throw new IllegalArgumentException(albumRoot.getAbsolutePath()+" does not exist");
		if (!albumRoot.isDirectory())
			throw new IllegalArgumentException(albumRoot.getAbsolutePath()+" is not a directory");
		File albumData = new File(albumRoot, kAlbumDataFileName);
		if (!albumData.exists())
			throw new IllegalArgumentException(albumData.getAbsolutePath()+" does not exist");
		if (!albumData.isFile())
			throw new IllegalArgumentException(albumData.getAbsolutePath()+" is not a regular file");
		FileInputStream fis = new FileInputStream(albumData);
		
		init(fis);
	}
	
	public iPhotoLibrary(IndelibleFSVolumeIF volume, FilePath indeliblePath, FilePath pathToRoot) throws IOException, ObjectNotFoundException, PermissionDeniedException
	{
		this.volume = volume;
		this.indeliblePath = indeliblePath;
		this.pathToRoot = pathToRoot;
		this.indelibleAlbumRoot = (IndelibleDirectoryNodeIF) volume.getObjectByPath(indeliblePath);
		IndelibleFileNodeIF albumData;
		try
		{
			albumData = indelibleAlbumRoot.getChildNode(kAlbumDataFileName);
		} catch (PermissionDeniedException e1)
		{
			throw new IOException("Permission denied");
		} catch (ObjectNotFoundException e)
		{
			throw new IOException(kAlbumDataFileName+" not found");
		}
		InputStream fis;
		try
		{
			fis = new BufferedInputStream(new IndelibleFSForkRemoteInputStream(albumData.getFork("data", false)), 1024*1024);
		} catch (ForkNotFoundException e)
		{
			Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
			throw new IllegalArgumentException("No data fork");
		} catch (PermissionDeniedException e)
		{
			throw new IOException("Permission denied");
		}
		try
		{
			init(fis);
			lastModifyTime = albumData.lastModified();
		}
		finally
		{
			fis.close();
		}
	}
	
	public boolean needsReload()
	{
		lastCheckTime = System.currentTimeMillis();
		if (indelibleAlbumRoot != null)
		{
			// Get the latest version of the directory.  This should really be fixed in the Indelible API
			IndelibleFileNodeIF albumData;
			try
			{
				indelibleAlbumRoot = (IndelibleDirectoryNodeIF) volume.getObjectByPath(indeliblePath);
				albumData = indelibleAlbumRoot.getChildNode(kAlbumDataFileName);
				long albumDataLastModified = albumData.lastModified();
				if (albumDataLastModified <= lastModifyTime)
					return false;
			} catch (PermissionDeniedException e)
			{
				// TODO Auto-generated catch block
				Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
			} catch (ObjectNotFoundException e)
			{
				// TODO Auto-generated catch block
				Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
			} catch (IOException e)
			{
				// TODO Auto-generated catch block
				Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
			}
		}
		return true;
	}
	private void init(InputStream inputStream) throws IOException
	{
		try
		{
			NSDictionary iPhotoAlbumDict = (NSDictionary)XMLPropertyListParser.parse(inputStream);
			NSDictionary facesDict = (NSDictionary)iPhotoAlbumDict.objectForKey(kListOfFacesKey);
			facesByName = new HashMap<String, Face>();
			facesByOrder = new ArrayList<Face>(facesDict.count());
			String [] allKeys = facesDict.allKeys();
			for (String curKey:allKeys)
			{
				NSObject curFaceObject = facesDict.objectForKey(curKey);
				if (curFaceObject instanceof NSDictionary)
				{
					NSDictionary curFaceDict = (NSDictionary)curFaceObject;
					Face curFace = new Face(this, curFaceDict);
					facesByName.put(curFace.getName(), curFace);
					facesByOrder.add(curFace.getOrder(), curFace);
					facesByKey.put(curFace.getKey(), curFace);
				}
				else
				{
					Logger.getLogger(getClass()).error("Face "+curKey+" is not an NSDictionary, skipping");
				}
			}
			NSDictionary masterImageListDict = (NSDictionary)iPhotoAlbumDict.objectForKey(kMasterImageListKey);
			for (String curKey:masterImageListDict.allKeys())
			{
				NSDictionary curImageDict = (NSDictionary)masterImageListDict.objectForKey(curKey);
				Image curImage = new Image(curKey, this, curImageDict);
				images.put(curKey, curImage);
				imagesByGUID.put(curImage.getGUID(), curImage);
				ImageFace [] facesInImage = curImage.getFaces();
				for (ImageFace curImageFace:facesInImage)
				{
					ArrayList<Image>imageList = imagesByFace.get(curImageFace.getFaceKey());
					if (imageList == null)
					{
						imageList = new ArrayList<Image>();
						imagesByFace.put(curImageFace.getFaceKey(), imageList);
					}
					imageList.add(curImage);
				}
			}

			NSArray albumsArray = (NSArray)iPhotoAlbumDict.objectForKey(kListOfAlbumsKey);
			for (int curAlbumNum = 0 ;curAlbumNum < albumsArray.count(); curAlbumNum++)
			{
				NSObject curAlbumObject = albumsArray.objectAtIndex(curAlbumNum);
				if (curAlbumObject instanceof NSDictionary)
				{
					NSDictionary curAlbumDict = (NSDictionary)curAlbumObject;
					Album curAlbum = new Album(this, curAlbumDict);
					String GUID = curAlbum.getGUID();
					albums.put(GUID, curAlbum);
				}
				else
				{
					Logger.getLogger(getClass()).error("Album "+curAlbumNum+" is not an NSDictionary, skipping");
				}
			}

		} catch (Exception e)
		{
			Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
			throw new IOException("Caught exception "+e.getMessage());
		}
	}
	
	public String getLibraryName()
	{
		String libraryName = pathToRoot.getName();
		if (libraryName.endsWith(kPhotoLibraryExtension))
			libraryName = libraryName.substring(0,  libraryName.length() - kPhotoLibraryExtension.length());
		return libraryName;
	}
	
	public Image getImageForKey(String key)
	{
		return images.get(key);
	}
	
	public Image getImageForGUID(String guid)
	{
		return imagesByGUID.get(guid);
	}
	
	public InputStream getStreamForImage(Image image) throws IOException
	{
		Log4JStopWatch getStreamWatch = new Log4JStopWatch("getStreamForImage");
		try
		{
			InputStream returnStream;
			byte [] cachedBytes = getCachedImage(image.getKey(), -1, -1);
			if (cachedBytes != null)
			{
				returnStream = new ByteArrayInputStream(cachedBytes);
			}
			else
			{
				FilePath origPath = image.getImagePath();
				FilePath imagePath = origPath.getPathRelativeTo(pathToRoot);
				imagePath = indeliblePath.getChild(imagePath);
				if (indelibleAlbumRoot != null)
				{
					IndelibleFileNodeIF imageNode = indelibleAlbumRoot;

					Log4JStopWatch resolvePathWatch = new Log4JStopWatch("getStreamForImage:resolvePath");
					
					try
					{
						imageNode = indelibleAlbumRoot.getVolume().getObjectByPath(imagePath);
					} catch (PermissionDeniedException e)
					{
						throw new IOException("Permission denied");
					} catch (ObjectNotFoundException e)
					{
						throw new IOException("File not found");
					}
					resolvePathWatch.stop();
					Log4JStopWatch getIndelibleInputStream = new Log4JStopWatch("getStreamForImage:getIndelibleStream");
					try
					{
						IndelibleFSForkIF dataFork = imageNode.getFork("data", false);
						long length = dataFork.length();
						if (length < 8 * 1024 * 1024)
						{
							DataDescriptor dataDescriptor = dataFork.getDataDescriptor(0, length);
							byte [] data = dataDescriptor.getData();
							returnStream = new ByteArrayInputStream(data);
						}
						else
						{
							returnStream = new BufferedInputStream(new IndelibleFSForkRemoteInputStream(dataFork), 1024*1024);
						}
					} catch (ForkNotFoundException e)
					{
						Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
						throw new IOException("No data fork for "+origPath.toString());
					} catch (PermissionDeniedException e)
					{
						throw new IOException("Permission denied");
					}
					getIndelibleInputStream.stop();
				}
				else
				{
					File imageFile = new File(albumRoot, imagePath.toString());
					returnStream = new FileInputStream(imageFile);
				}
			}
			return returnStream;
		}
		finally
		{
			getStreamWatch.stop();
		}
	}
	
	public long getByteLengthForImage(Image image) throws IOException
	{
		byte [] cachedBytes = getCachedImage(image.getKey(), -1, -1);
		if (cachedBytes != null)
		{
			return cachedBytes.length;
		}
		else
		{
			FilePath origPath = image.getImagePath();
			FilePath imagePath = origPath.getPathRelativeTo(pathToRoot);
			imagePath = indeliblePath.getChild(imagePath);
			if (indelibleAlbumRoot != null)
			{
				IndelibleFileNodeIF imageNode;
				try
				{
					imageNode = indelibleAlbumRoot.getVolume().getObjectByPath(imagePath);
				} catch (PermissionDeniedException e)
				{
					throw new IOException("Permission denied");
				} catch (ObjectNotFoundException e)
				{
					throw new IOException("File not found");
				}
				return imageNode.length();
			}
			return 0;
		}
	}
	/**
	 * Return an InputStream to a suitably resized image
	 * @param image - iPhotoLibrary image to get stream for
	 * @param height - desired height (<0 = sourceHeight)
	 * @param width - desired width (<0 = sourceWidth)
	 * @return
	 * @throws IOException
	 */
	public InputStream getStreamForResizedImage(Image image, int height, int width) throws IOException
	{
		Log4JStopWatch getStreamForResizedImageWatch = new Log4JStopWatch("getStreamForResizedImage");
		if (height < 0 && width < 0)
		{
			return getStreamForImage(image);	// No resizing
		}
		byte [] cachedBytes = getCachedImage(image.getKey(), width, height);
		if (cachedBytes == null)
		{
			long byteLengthForImage = getByteLengthForImage(image);
			MBPerSecondLog4jStopWatch readImageWatch = new MBPerSecondLog4jStopWatch("getStreamForResizedImage:readImage");
			InputStream streamForImage = getStreamForImage(image);
			readImageWatch.bytesProcessed(byteLengthForImage);
			readImageWatch.stop();
			MBPerSecondLog4jStopWatch decodeImageWatch = new MBPerSecondLog4jStopWatch("getStreamForResizedImage:decodeImage");
			BufferedImage sourceImage = ImageIO.read(streamForImage);
			decodeImageWatch.bytesProcessed(byteLengthForImage);
			decodeImageWatch.stop();
			
			Log4JStopWatch resizeImageWatch = new Log4JStopWatch("getStreamForResizedImage:resizeImage");
			BufferedImage returnImage;
			int sourceHeight = sourceImage.getHeight();
			int sourceWidth = sourceImage.getWidth();
			if (height < 0)
			{
				if (width < 0)
				{
					height = sourceHeight;
				}
				else
				{
					double widthRatio = (double)width/(double)sourceWidth;
					height = (int)(sourceHeight * widthRatio);
				}
			}
			if (width < 0)
			{
				if (height < 0)
				{
					width = sourceWidth;
				}
				else
				{
					double heightRatio = (double)height/(double)sourceHeight;
					width = (int)(sourceWidth * heightRatio);
				}
			}
			if (sourceHeight == height && sourceWidth == width)
			{
				returnImage = sourceImage;
			}
			else
			{
				double heightRatio = (double)height/(double)sourceHeight;
				double widthRatio = (double)width/(double)sourceWidth;
				int cropHeight = sourceHeight, cropWidth = sourceWidth;
				/*
				 * First, we will crop the source image so that the aspect ratio is the same
				 * We do this by picking the large ratio of the height/sourceHeight and width/sourceWidth
				 * and then use that to scale the other dimension
				 */
				int cropStartX = 0, cropStartY = 0;
				if (heightRatio >= widthRatio)
				{
					cropWidth = (int)((sourceWidth * widthRatio)/ heightRatio);
					cropStartX = (sourceWidth - cropWidth)/2;
				}
				else
				{
					cropHeight = (int)((sourceHeight * heightRatio)/widthRatio);
					cropStartY = (sourceHeight - cropHeight)/2;
				}
				BufferedImage croppedImage;
				if (cropWidth != sourceWidth || cropHeight != sourceHeight)
				{
					croppedImage = sourceImage.getSubimage(cropStartX, cropStartY, cropWidth, cropHeight);
				}
				else
				{
					croppedImage = sourceImage;
				}

				returnImage = Scalr.resize(croppedImage, Method.QUALITY, width, height, Scalr.OP_ANTIALIAS);
			}
			resizeImageWatch.stop();
			Log4JStopWatch saveImageWatch = new Log4JStopWatch("getStreamForResizedImage:saveResizedImage");
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(.80f);
			writer.setOutput(new MemoryCacheImageOutputStream(bos));
			IIOImage iioImage = new IIOImage(returnImage, null, null);
			writer.write(null, iioImage, param);
			bos.close();
			writer.dispose();
			saveImageWatch.stop();
			cachedBytes = bos.toByteArray();
			addToCache(image.getKey(), width, height, cachedBytes);
		}
		InputStream returnStream = new ByteArrayInputStream(cachedBytes);
		getStreamForResizedImageWatch.stop();
		return returnStream;
	}
	
	public BufferedImage getScaledInstance(BufferedImage img,
            int targetWidth,
            int targetHeight,
            Object hint,
            boolean higherQuality)
	{
		int type = (img.getTransparency() == Transparency.OPAQUE) ?
				BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
		BufferedImage ret = (BufferedImage)img;
		int w, h;
		if (higherQuality) {
			// Use multi-step technique: start with original size, then
			// scale down in multiple passes with drawImage()
			// until the target size is reached
			w = img.getWidth();
			h = img.getHeight();
		} else {
			// Use one-step technique: scale directly from original
			// size to target size with a single drawImage() call
			w = targetWidth;
			h = targetHeight;
		}

		do {
			if (higherQuality && w > targetWidth) {
				w /= 2;
				if (w < targetWidth) {
					w = targetWidth;
				}
			}

			if (higherQuality&& h > targetHeight) {
				h /= 2;
				if (h < targetHeight) {
					h = targetHeight;
				}
			}

			BufferedImage tmp = new BufferedImage(w, h, type);
			Graphics2D g2 = tmp.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
			g2.drawImage(ret, 0, 0, w, h, null);
			g2.dispose();

			ret = tmp;
		} while (w != targetWidth || h != targetHeight);

		return ret;
	}
	
	public InputStream getStreamForImageThumb(Image image) throws IOException
	{
		FilePath origPath = image.getThumbPath();
		FilePath thumbPath = origPath.getPathRelativeTo(pathToRoot);
		InputStream returnStream;
		if (indelibleAlbumRoot != null)
		{
			IndelibleFileNodeIF imageNode = indelibleAlbumRoot;
			for (int curComponentNum = 0; curComponentNum < thumbPath.getNumComponents(); curComponentNum++)
			{
				try
				{
					if (imageNode.isDirectory())
						imageNode = ((IndelibleDirectoryNodeIF)imageNode).getChildNode(thumbPath.getComponent(curComponentNum));
					else
						throw new FileNotFoundException(origPath.toString());
				} catch (PermissionDeniedException e)
				{
					throw new IOException("Permission denied");
				} catch (ObjectNotFoundException e)
				{
					throw new FileNotFoundException(origPath.toString());
				}
			}
			try
			{
				IndelibleFSForkIF dataFork = imageNode.getFork("data", false);
				returnStream = new BufferedInputStream(new IndelibleFSForkRemoteInputStream(dataFork), 1024*1024);
			} catch (ForkNotFoundException e)
			{
				Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
				throw new IOException("No data fork for "+origPath.toString());
			} catch (PermissionDeniedException e)
			{
				throw new IOException("Permission denied");
			}
		}
		else
		{
			File imageFile = new File(albumRoot, thumbPath.toString());
			returnStream = new FileInputStream(imageFile);
		}
		return returnStream;
	}
	
	public InputStream getStreamForFaceImage(Face face) throws IOException
	{
		byte[] faceBytes = faceCache.get(face.getKey());
		if (faceBytes == null)
		{
			Image fullImage = getImageForGUID(face.getKeyImageGUID());
			BufferedImage sourceImage = ImageIO.read(getStreamForImage(fullImage));
			ImageFace [] imageFaces = fullImage.getFaces();
			ImageRectangle faceImageRectangle = null;
			for (ImageFace checkFace:imageFaces)
			{
				if (checkFace.getFaceKey().equals(face.getKey()))
				{
					faceImageRectangle = checkFace.getImageRectangle();
					break;
				}
			}
			if (faceImageRectangle == null)
				return null;
			int sourceHeight = sourceImage.getHeight();
			int sourceWidth = sourceImage.getWidth();
			int startX = (int) (sourceWidth * faceImageRectangle.getX1());
			int width = (int) (sourceWidth * faceImageRectangle.getX2());
			startX -= (int)(width * .25);
			if (startX < 0)
				startX = 0;

			width += (int) (width * .5);
			if (startX + width > sourceWidth)
				width = sourceWidth - startX;
			int endY = sourceHeight - (int) (sourceHeight * faceImageRectangle.getY1());	// iPhoto flipped the axes, thank you
			int height = (int) (sourceHeight * faceImageRectangle.getY2());

			int startY = endY - height;

			startY -= (int)(height * .25);
			height += (int)(height * .5);
			BufferedImage croppedImage;

			croppedImage = sourceImage.getSubimage(startX, startY, width, height);

			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ImageIO.write(croppedImage, fullImage.getImageMIMEType(), bos);
			bos.close();
			faceBytes = bos.toByteArray();
			faceCache.put(face.getKey(), faceBytes);
		}
		InputStream returnStream = new ByteArrayInputStream(faceBytes);
		return returnStream;
	}
	
	public String [] getAlbumGUIDs()
	{
		String [] returnGUIDs = new String[albums.keySet().size()];
		returnGUIDs = albums.keySet().toArray(returnGUIDs);
		return returnGUIDs;
	}
	
	public Album [] getAlbums()
	{
		return albums.values().toArray(new Album[albums.size()]);
	}
	
	public Album getAlbum(String GUID)
	{
		return albums.get(GUID);
	}
	
	public Face[] getFaces()
	{
		Face [] returnFaces = new Face[facesByOrder.size()];
		returnFaces = facesByOrder.toArray(returnFaces);
		return returnFaces;
	}

	public Face getFaceByKey(String retrieveKey)
	{
		return facesByKey.get(retrieveKey);
	}
	
	public Image [] getImagesForFace(Face face)
	{
		ArrayList<Image>imageList = imagesByFace.get(face.getKey());
		Image [] returnImages;
		if (imageList != null)
		{
			returnImages = new Image[imageList.size()];
			returnImages = imageList.toArray(returnImages);
		}
		else
		{
			returnImages = new Image[0];
		}
		return returnImages;
	}
	
	public byte [] getCachedImage(String imageKey, int width, int height)
	{
		synchronized(imageCache)
		{
			byte [] returnBytes = null;
			ArrayList<SoftReference<CachedImage>>cachedImagesForKey = imageCache.get(imageKey);
			if (cachedImagesForKey != null)
			{
				Iterator<SoftReference<CachedImage>>referenceIterator = cachedImagesForKey.iterator();
				while(referenceIterator.hasNext())
				{
					SoftReference<CachedImage>curReference = referenceIterator.next();
					CachedImage checkCachedImage = curReference.get();
					if (checkCachedImage != null)
					{
						if (checkCachedImage.width == width && checkCachedImage.height == height)
						{
							returnBytes = checkCachedImage.bytes;
							break;
						}
					}
					else
					{
						// Got whacked
						referenceIterator.remove();
					}
				}
			}
			if (returnBytes == null)
			{
				// Check the file cache
				File cacheFile = null;
				try
				{
					cacheFile = getCacheFile(imageKey, width, height);
				} catch (IOException e1)
				{
					// TODO Auto-generated catch block
					Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e1);
				}
				if (cacheFile != null && cacheFile.exists())
				{
					int bytesRead = 0;
					
					try
					{
						FileInputStream readStream = new FileInputStream(cacheFile);
						returnBytes = new byte[(int)cacheFile.length()];
						bytesRead = readStream.read(returnBytes);
						readStream.close();
						addToInMemoryCache(imageKey, width, height, returnBytes);
					} catch (FileNotFoundException e)
					{
						// TODO Auto-generated catch block
						Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
					} catch (IOException e)
					{
						// TODO Auto-generated catch block
						Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
					}
					finally
					{
						if (bytesRead != cacheFile.length())
						{
							cacheFile.delete();	// Something's wonky, just whack it
							returnBytes = null;
						}
					}
				}
			}
			return returnBytes;
		}
	}
	
	public void addToCache(String imageKey, int width, int height, byte [] bytes)
	{
		synchronized(imageCache)
		{
			addToInMemoryCache(imageKey, width, height, bytes);
			try
			{
				File writeFile = getCacheFile(imageKey, width, height);
				FileOutputStream cacheWriteStream = new FileOutputStream(writeFile);
				cacheWriteStream.write(bytes);
				cacheWriteStream.close();
			} catch (FileNotFoundException e)
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

	private void addToInMemoryCache(String imageKey, int width, int height,
			byte[] bytes)
	{
		ArrayList<SoftReference<CachedImage>>cachedImagesForKey = imageCache.get(imageKey);
		if (cachedImagesForKey == null)
		{
			cachedImagesForKey = new ArrayList<SoftReference<CachedImage>>();
			imageCache.put(imageKey, cachedImagesForKey);
		}
		Iterator<SoftReference<CachedImage>>referenceIterator = cachedImagesForKey.iterator();
		while(referenceIterator.hasNext())
		{
			SoftReference<CachedImage>curReference = referenceIterator.next();
			CachedImage checkCachedImage = curReference.get();
			if (checkCachedImage == null || (checkCachedImage.width == width && checkCachedImage.height == height))
				referenceIterator.remove();
		}
		CachedImage newCachedImage = new CachedImage();
		newCachedImage.width = width;
		newCachedImage.height = height;
		newCachedImage.bytes = bytes;
		SoftReference<CachedImage>newReference = new SoftReference<CachedImage>(newCachedImage);
		cachedImagesForKey.add(newReference);
	}

	protected File getCacheFile(String imageKey, int width, int height) throws IOException
	{
		File cacheDir = new File("/tmp/iPhotoLibrary-Cache/" + indelibleAlbumRoot.getVolume().getObjectID());
		if (!cacheDir.exists())
		{
			cacheDir.mkdirs();
		}
		File cacheFile = new File(cacheDir, imageKey+"w"+width+"h"+height+".cache");
		return cacheFile;
	}

	/**
	 * Returns the last time checkReload() was called
	 * @return
	 */
	public long getLastCheckTime()
	{
		return lastCheckTime;
	}

	/**
	 * Returns the last time when the album index file was updated
	 * @return
	 */
	public long getLastModifyTime()
	{
		return lastModifyTime;
	}
}

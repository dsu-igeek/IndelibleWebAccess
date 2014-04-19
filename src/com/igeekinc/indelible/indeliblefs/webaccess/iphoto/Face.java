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

import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSString;

public class Face
{
	private iPhotoLibrary parent;
	private String key;
	private String name;
	private String keyImageGUID;
	private int keyImageIndex;
	private int photoCount;
	private int order;
	
	/*
	 * <key>key</key>
     * <string>1442</string>
     * <key>name</key>
     * <string>Edo Papa</string>
     * <key>key image</key>
     * <string>Pybi5Fm2Tw+uPHKt3x5BDw</string>
     * <key>key image face index</key>
     * <integer>2</integer>
     * <key>PhotoCount</key>
     * <string>32</string>
     * <key>Order</key>
     * <integer>0</integer>
	 */
	
	public final String kKeyPropertyName = "key";
	public final String kNamePropertyName = "name";
	public final String kKeyImageGUIDPropertyName = "key image";
	public final String kKeyImageIndexNamePropertyName = "key image face index";
	public final String kPhotoCountPropertyName = "PhotoCount";
	public final String kOrderPropertyName = "Order";
	
	public Face(iPhotoLibrary parent, NSDictionary faceDictionary)
	{
		this.parent = parent;
		key = ((NSString)faceDictionary.objectForKey(kKeyPropertyName)).toString();
		name = ((NSString)faceDictionary.objectForKey(kNamePropertyName)).toString();
		keyImageGUID = ((NSString)faceDictionary.objectForKey(kKeyImageGUIDPropertyName)).toString();
		NSNumber keyImageIndexNumber = (NSNumber)faceDictionary.objectForKey(kKeyImageIndexNamePropertyName);
		if (keyImageIndexNumber != null)
			keyImageIndex = keyImageIndexNumber.intValue();
		else
			keyImageIndex = -1;
		photoCount = Integer.parseInt(((NSString)faceDictionary.objectForKey(kPhotoCountPropertyName)).toString());
		order = ((NSNumber)faceDictionary.objectForKey(kOrderPropertyName)).intValue();
	}

	public iPhotoLibrary getParent()
	{
		return parent;
	}

	public String getKey()
	{
		return key;
	}

	public String getName()
	{
		return name;
	}

	public String getKeyImageGUID()
	{
		return keyImageGUID;
	}

	public int getKeyImageIndex()
	{
		return keyImageIndex;
	}

	public int getPhotoCount()
	{
		return photoCount;
	}

	public int getOrder()
	{
		return order;
	}
}

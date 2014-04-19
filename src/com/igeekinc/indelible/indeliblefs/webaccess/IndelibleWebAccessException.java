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

public class IndelibleWebAccessException extends Exception
{
    private static final long serialVersionUID = 2845384216374787147L;
    private int errorCode;
    private Throwable cause;
    
    public static final int kVolumeNotFoundError=1;
    public static final int kInvalidVolumeID = 2;
    public static final int kInternalError = 3;
    public static final int kInvalidArgument = 4;
    public static final int kPathNotFound = 5;
    public static final int kDestinationExists = 6;
    public static final int kNotDirectory = 7;
    public static final int kPermissionDenied = 8;
    public static final int kNotFile = 9;
    public static final int kForkNotFound = 10;
    
    public IndelibleWebAccessException(int errorCode, Throwable cause)
    {
        this.errorCode = errorCode;
        this.cause = cause;
    }

    public int getErrorCode()
    {
        return errorCode;
    }

    public Throwable getCause()
    {
        return cause;
    }
}

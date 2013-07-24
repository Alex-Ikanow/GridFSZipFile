/* net.sf.jazzlib.ZipFile MODIFIED TO GridFSZipFile
   Copyright (C) 2001, 2002, 2003 Free Software Foundation, Inc.

This file is part of GNU Classpath.

GNU Classpath is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2, or (at your option)
any later version.

GNU Classpath is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with GNU Classpath; see the file COPYING.  If not, write to the
Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
02111-1307 USA.

Linking this library statically or dynamically with other modules is
making a combined work based on this library.  Thus, the terms and
conditions of the GNU General Public License cover the whole
combination.

As a special exception, the copyright holders of this library give you
permission to link this library with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on this library.  If you modify this library, you may extend
this exception to your version of the library, but you are not
obligated to do so.  If you do not wish to do so, delete this
exception statement from your version. */

package net.sf.jazzlib;

import java.io.BufferedInputStream;
import java.io.DataInput;
import java.io.InputStream;
import java.io.IOException;
import java.io.EOFException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.ikanow.utility.GridFSRandomAccessFile;

/**
 * This class represents a Zip archive.  You can ask for the contained
 * entries, or get an input stream for a file entry.  The entry is
 * automatically decompressed.
 *
 * This class is thread safe:  You can open input streams for arbitrary
 * entries in different threads.
 *
 * @author Jochen Hoenicke
 * @author Artur Biesiadowski
 */
@SuppressWarnings({ "rawtypes", "deprecation", "unchecked" })
public class GridFSZipFile implements ZipConstants
{

  /**
   * Mode flag to open a zip file for reading.
   */
  public static final int OPEN_READ = 0x1;

  /**
   * Mode flag to delete a zip file after reading.
   */
  public static final int OPEN_DELETE = 0x4;

  // Name of this zip file.
  private final String name;

  // File from which zip entries are read.
  private final GridFSRandomAccessFile raf;

  // The entries of this zip file when initialized and not yet closed.
  private HashMap entries;

  private boolean closed = false;

	public GridFSZipFile(String fileName, GridFSRandomAccessFile randomAccessFile) throws ZipException {
		this.raf = randomAccessFile;
		this.name = fileName;
	}
  /**
   * Read an unsigned short in little endian byte order from the given
   * DataInput stream using the given byte buffer.
   *
   * @param di DataInput stream to read from.
   * @param b the byte buffer to read in (must be at least 2 bytes long).
   * @return The value read.
   *
   * @exception IOException if a i/o error occured.
   * @exception EOFException if the file ends prematurely
   */
  private final int readLeShort(DataInput di, byte[] b) throws IOException
  {
    di.readFully(b, 0, 2);
    return (b[0] & 0xff) | (b[1] & 0xff) << 8;
  }

  /**
   * Read an int in little endian byte order from the given
   * DataInput stream using the given byte buffer.
   *
   * @param di DataInput stream to read from.
   * @param b the byte buffer to read in (must be at least 4 bytes long).
   * @return The value read.
   *
   * @exception IOException if a i/o error occured.
   * @exception EOFException if the file ends prematurely
   */
  private final int readLeInt(DataInput di, byte[] b) throws IOException
  {
    di.readFully(b, 0, 4);
    return ((b[0] & 0xff) | (b[1] & 0xff) << 8)
	    | ((b[2] & 0xff) | (b[3] & 0xff) << 8) << 16;
  }

  
  /**
   * Read an unsigned short in little endian byte order from the given
   * byte buffer at the given offset.
   *
   * @param b the byte array to read from.
   * @param off the offset to read from.
   * @return The value read.
   */
  private final int readLeShort(byte[] b, int off)
  {
    return (b[off] & 0xff) | (b[off+1] & 0xff) << 8;
  }

  /**
   * Read an int in little endian byte order from the given
   * byte buffer at the given offset.
   *
   * @param b the byte array to read from.
   * @param off the offset to read from.
   * @return The value read.
   */
  private final int readLeInt(byte[] b, int off)
  {
    return ((b[off] & 0xff) | (b[off+1] & 0xff) << 8)
	    | ((b[off+2] & 0xff) | (b[off+3] & 0xff) << 8) << 16;
  }
  

  /**
   * Read the central directory of a zip file and fill the entries
   * array.  This is called exactly once when first needed. It is called
   * while holding the lock on <code>raf</code>.
   *
   * @exception IOException if a i/o error occured.
   * @exception ZipException if the central directory is malformed 
   */
private void readEntries() throws ZipException, IOException
  {
    /* Search for the End Of Central Directory.  When a zip comment is 
     * present the directory may start earlier.
     * FIXME: This searches the whole file in a very slow manner if the
     * file isn't a zip file.
     */
    long pos = raf.length() - ENDHDR;
    byte[] ebs  = new byte[CENHDR];
    
    do
      {
	if (pos < 0)
	  throw new ZipException
	    ("central directory not found, probably not a zip file: " + name);
	raf.seek(pos--);
      }
    while (readLeInt(raf, ebs) != ENDSIG);
    
    if (raf.skipBytes(ENDTOT - ENDNRD) != ENDTOT - ENDNRD)
      throw new EOFException(name);
    int count = readLeShort(raf, ebs);
    if (raf.skipBytes(ENDOFF - ENDSIZ) != ENDOFF - ENDSIZ)
      throw new EOFException(name);
    int centralOffset = readLeInt(raf, ebs);

    entries = new HashMap(count+count/2);
    raf.seek(centralOffset);
    
    byte[] buffer = new byte[16];
    for (int i = 0; i < count; i++)
      {
      	raf.readFully(ebs);
	if (readLeInt(ebs, 0) != CENSIG)
	  throw new ZipException("Wrong Central Directory signature: " + name);

	int method = readLeShort(ebs, CENHOW);
	int dostime = readLeInt(ebs, CENTIM);
	int crc = readLeInt(ebs, CENCRC);
	int csize = readLeInt(ebs, CENSIZ);
	int size = readLeInt(ebs, CENLEN);
	int nameLen = readLeShort(ebs, CENNAM);
	int extraLen = readLeShort(ebs, CENEXT);
	int commentLen = readLeShort(ebs, CENCOM);
	
	int offset = readLeInt(ebs, CENOFF);

	int needBuffer = Math.max(nameLen, commentLen);
	if (buffer.length < needBuffer)
	  buffer = new byte[needBuffer];

	raf.readFully(buffer, 0, nameLen);
	String name = new String(buffer, 0, 0, nameLen);

	ZipEntry entry = new ZipEntry(name);
	entry.setMethod(method);
	entry.setCrc(crc & 0xffffffffL);
	entry.setSize(size & 0xffffffffL);
	entry.setCompressedSize(csize & 0xffffffffL);
	entry.setDOSTime(dostime);
	if (extraLen > 0)
	  {
	    byte[] extra = new byte[extraLen];
	    raf.readFully(extra);
	    entry.setExtra(extra);
	  }
	if (commentLen > 0)
	  {
	    raf.readFully(buffer, 0, commentLen);
	    entry.setComment(new String(buffer, 0, commentLen));
	  }
	entry.offset = offset;
	entries.put(name, entry);
      }
  }

  /**
   * Closes the ZipFile.  This also closes all input streams given by
   * this class.  After this is called, no further method should be
   * called.
   * 
   * @exception IOException if a i/o error occured.
   */
  public void close() throws IOException
  {
    synchronized (raf)
      {
	closed = true;
	entries = null;
	raf.close();
      }
  }

  /**
   * Calls the <code>close()</code> method when this ZipFile has not yet
   * been explicitly closed.
   */
  protected void finalize() throws IOException
  {
    if (!closed && raf != null) close();
  }

  /**
   * Returns an enumeration of all Zip entries in this Zip file.
   */
  public Enumeration entries()
  {
    try
      {
	return new ZipEntryEnumeration(getEntries().values().iterator());
      }
    catch (IOException ioe)
      {
	return null;
      }
  }

  /**
   * Checks that the ZipFile is still open and reads entries when necessary.
   *
   * @exception IllegalStateException when the ZipFile has already been closed.
   * @exception IOEexception when the entries could not be read.
   */
  private HashMap getEntries() throws IOException
  {
    synchronized(raf)
      {
	if (closed)
	  throw new IllegalStateException("ZipFile has closed: " + name);

	if (entries == null)
	  readEntries();

	return entries;
      }
  }

  /**
   * Searches for a zip entry in this archive with the given name.
   *
   * @param the name. May contain directory components separated by
   * slashes ('/').
   * @return the zip entry, or null if no entry with that name exists.
   */
  public ZipEntry getEntry(String name)
  {
    try
      {
	HashMap entries = getEntries();
	ZipEntry entry = (ZipEntry) entries.get(name);
	return entry != null ? (ZipEntry) entry.clone() : null;
      }
    catch (IOException ioe)
      {
	return null;
      }
  }


  //access should be protected by synchronized(raf)
  private byte[] locBuf = new byte[LOCHDR];

  /**
   * Checks, if the local header of the entry at index i matches the
   * central directory, and returns the offset to the data.
   * 
   * @param entry to check.
   * @return the start offset of the (compressed) data.
   *
   * @exception IOException if a i/o error occured.
   * @exception ZipException if the local header doesn't match the 
   * central directory header
   */
  private long checkLocalHeader(ZipEntry entry) throws IOException
  {
    synchronized (raf)
      {
	raf.seek(entry.offset);
	raf.readFully(locBuf);
	
	if (readLeInt(locBuf, 0) != LOCSIG)
	  throw new ZipException("Wrong Local header signature: " + name);

	if (entry.getMethod() != readLeShort(locBuf, LOCHOW))
	  throw new ZipException("Compression method mismatch: " + name);

	if (entry.getName().length() != readLeShort(locBuf, LOCNAM))
	  throw new ZipException("file name length mismatch: " + name);

	int extraLen = entry.getName().length() + readLeShort(locBuf, LOCEXT);
	return entry.offset + LOCHDR + extraLen;
      }
  }

  /**
   * Creates an input stream reading the given zip entry as
   * uncompressed data.  Normally zip entry should be an entry
   * returned by getEntry() or entries().
   *
   * @param entry the entry to create an InputStream for.
   * @return the input stream.
   *
   * @exception IOException if a i/o error occured.
   * @exception ZipException if the Zip archive is malformed.  
   */
  public InputStream getInputStream(ZipEntry entry) throws IOException
  {
    HashMap entries = getEntries();
    String name = entry.getName();
    ZipEntry zipEntry = (ZipEntry) entries.get(name);
    if (zipEntry == null)
      throw new NoSuchElementException(name);

    long start = checkLocalHeader(zipEntry);
    int method = zipEntry.getMethod();
    InputStream is = new BufferedInputStream(new PartialInputStream
      (raf, start, zipEntry.getCompressedSize()));
    switch (method)
      {
      case ZipOutputStream.STORED:
	return is;
      case ZipOutputStream.DEFLATED:
	return new InflaterInputStream(is, new Inflater(true));
      default:
	throw new ZipException("Unknown compression method " + method);
      }
  }
  
  /**
   * Returns the (path) name of this zip file.
   */
  public String getName()
  {
    return name;
  }

  /**
   * Returns the number of entries in this zip file.
   */
  public int size()
  {
    try
      {
	return getEntries().size();
      }
    catch (IOException ioe)
      {
	return 0;
      }
  }
  
  private static class ZipEntryEnumeration implements Enumeration
  {
    private final Iterator elements;

    public ZipEntryEnumeration(Iterator elements)
    {
      this.elements = elements;
    }

    public boolean hasMoreElements()
    {
      return elements.hasNext();
    }

    public Object nextElement()
    {
      /* We return a clone, just to be safe that the user doesn't
       * change the entry.  
       */
      return ((ZipEntry)elements.next()).clone();
    }
  }

  private static class PartialInputStream extends InputStream
  {
    private final GridFSRandomAccessFile raf;
    long filepos, end;

    public PartialInputStream(GridFSRandomAccessFile raf, long start, long len)
    {
      this.raf = raf;
      filepos = start;
      end = start + len;
    }
    
    public int available()
    {
      long amount = end - filepos;
      if (amount > Integer.MAX_VALUE)
	return Integer.MAX_VALUE;
      return (int) amount;
    }
    
    public int read() throws IOException
    {
      if (filepos == end)
	return -1;
      synchronized (raf)
	{
	  raf.seek(filepos++);
	  return raf.read();
	}
    }

    public int read(byte[] b, int off, int len) throws IOException
    {
      if (len > end - filepos)
	{
	  len = (int) (end - filepos);
	  if (len == 0)
	    return -1;
	}
      synchronized (raf)
	{
	  raf.seek(filepos);
	  int count = raf.read(b, off, len);
	  if (count > 0)
	    filepos += len;
	  return count;
	}
    }

    public long skip(long amount)
    {
      if (amount < 0)
	throw new IllegalArgumentException();
      if (amount > end - filepos)
	amount = end - filepos;
      filepos += amount;
      return amount;
    }
  }
}

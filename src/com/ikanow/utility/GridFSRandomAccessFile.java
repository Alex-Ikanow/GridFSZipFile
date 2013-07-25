/* com.ikanow.utility.GridFSZipRandomAccessFile
   Copyright (C) 2013 Ikanow

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

package com.ikanow.utility;

import java.io.DataInput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;

import org.bson.types.ObjectId;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;

import net.sf.jazzlib.ZipException;

/** NOT THREAD SAFE
 *  Don't consider any function tested unless it is closed with TESTED
 * @author apiggott@ikanow.com
 *
 */
public class GridFSRandomAccessFile implements DataInput {

	////////////////////////////////////////////////
	////////////////////////////////////////////////
	
	// CONSTRUCTOR
	
	/** Returns a random access file accessor from a database and FS name
	 * @param db - com.mongodb.DB, the database name 
	 * @param fsName - string, the "collection" name
	 * @param fileId - org.bson.ObjectId, the _id of the file 
	 * @throws IOException
	 */
	public GridFSRandomAccessFile(DB db, String fsName, ObjectId fileId) throws IOException {
		DBCollection fileColl = db.getCollection(new StringBuffer(fsName).append(".files").toString());
		_chunkCollection = db.getCollection(new StringBuffer(fsName).append(".chunks").toString());
		
		_chunkQuery = new BasicDBObject(_CHUNK_files_id_, fileId);
		_chunkQuery.put(_CHUNK_n_, 0);
		_fileObj = (BasicDBObject) fileColl.findOne(new BasicDBObject(_FILE_id_, fileId));
		if (null == _fileObj) {
			throw new IOException("File Not Found");
		}		
		_fileId = fileId;
		_chunkSize = _fileObj.getInt(_FILE_chunkSize_);
		_fileSize = _fileObj.getLong(_FILE_length_);
		_lastChunkNum = (int) (_fileSize/_chunkSize);
		_finalChunkSize = (int) (_fileSize % _chunkSize);
		_currChunkSize = (_lastChunkNum == 0) ? _finalChunkSize : _chunkSize;
	}//TOTEST

	////////////////////////////////////////////////
	////////////////////////////////////////////////
	
	// "FUNCTIONAL OVERRIDES" FROM RANDOMACCESSILE (ONLY FUNCTIONS CALLED FROM ZIPFILE)
	
	/** Moves the file position to a new absolute location
	 * @param pos - long, the new absolute position of the file
	 * @throws IOException
	 */
	public void seek(long pos) throws IOException {
		int skip = (int)(pos - _currPosInFile);
		this.skipBytes(skip);
	}//(tested skipBytes)

	/** Move around in the file via relative offset
	 * @see java.io.DataInput#skipBytes(int)
	 */
	@Override
	public int skipBytes(int n) throws IOException {
		long oldPosInFile = _currPosInFile;
		_currPosInFile = _currPosInFile + n;
		if (_currPosInFile < 0) {
			_currPosInFile = 0;
		}
		else if (_currPosInFile > _fileSize) {
			_currPosInFile = _fileSize;
		}
		int newChunkNum = (int) (_currPosInFile/_chunkSize);
		
		if ((newChunkNum != _currChunkNum) || (null == _currChunkObj)) {
			_chunkQuery.put(_CHUNK_n_, newChunkNum);
			BasicDBObject newChunk = (BasicDBObject) _chunkCollection.findOne(_chunkQuery);
			if (null == newChunk) {
				throw new IOException("Unknown I/O exception");
			}
			_currChunkObj = newChunk;
			_currChunkNum = newChunkNum;
			_currData = (byte[]) newChunk.get(_CHUNK_data_); 					
			_currChunkSize = (_lastChunkNum == 0) ? _finalChunkSize : _chunkSize;
		}
		_currPosInChunk = (int) (_currPosInFile % _chunkSize);
		return (int) (_currPosInFile - oldPosInFile);		

	}//TOTEST

	/** returns the total length of the file
	 * @return long, the total length of the file
	 */
	public long length() {
		return _fileSize;
	}//TOTEST
	
	/** Reads from the file and updates its file position
	 * @param b - byte[], the data block in which to write
	 * @see java.io.DataInput#readFully(byte[])
	 */
	public void readFully(byte[] b) throws IOException {
		read(b, 0, b.length); 		
	}//(tested read(...))
	
	/** Reads from the file and updates its file position
	 * @param b - byte[], the data block in which to write
	 * @param off - int, where to start writing into b 
	 * @param len - int, the max number of bytes to read
	 * @see java.io.DataInput#readFully(byte[], int, int)
	 */
	@Override
	public void readFully(byte[] b, int off, int len) throws IOException {
		read(b, off, len); 		
	}//(tested read(...))

	/** Reads one bytes from the file and updates its file position
	 * @return the value of the byte
	 * @throws IOException 
	 */
	public synchronized int read() throws IOException { // (reads 1B)
		read(this._saved8Bytes, 0, 1);
		return (int) _saved8Bytes[0];
	}//TOTEST
	
	/** Reads from the file and updates its file position
	 * @param b - byte[], the data block in which to write
	 * @return int, the number of bytes read
	 * @throws IOException 
	 */
	public int read(byte[] b) throws IOException {
		read(b, 0, b.length); 		
		return 0;
	}//(tested read(...))
	
	/** Reads from the file and updates its file position
	 * @param b - byte[], the data block in which to write
	 * @param off - int, where to start writing into b 
	 * @param len - int, the max number of bytes to read
	 * @return int, the number of bytes read
	 * @throws IOException 
	 */
	public int read(byte[] b, int off, int len) throws IOException {
		if ((_currPosInFile + len) > _fileSize) { // adjust len to fit in the file
			len = (int) (_fileSize - _currPosInFile);
		}
		if (null == _currChunkObj) { // get data if none currently exists
			skipBytes(0);
		}
		while (len > 0) {
			int toRead = len;
			if (toRead > (_currChunkSize - _currPosInChunk)) {
				toRead = _currChunkSize - _currPosInChunk;
			}
			for (int i = 0; i < toRead; ++i) { // read from one chunk
				b[off + i] = _currData[_currPosInChunk + i];
			}
			len -= toRead;
			if (len > 0) { // get the next chunk if needed
				skipBytes(toRead);
			}
		}
		return len;
	}//TOTEST
	
	/**
	 * Does nothing, up to the caller to close any MongoDB connections
	 */
	public void close() {
		//No need to do anything, up to calling code to call DBCollection, which has different persistence
	}//(no test)
	
	////////////////////////////////////////////////
	////////////////////////////////////////////////
	
	// ACTUAL OVERRIDES FROM DATAINPUT
	
	@Override
	public boolean readBoolean() throws IOException {
		return (0 != read());
	}

	@Override
	public byte readByte() throws IOException {
		return (byte)read();
	}

	@Override
	public char readChar() throws IOException {
		return (char)read();
	}

	@Override
	public double readDouble() throws IOException {
		read(this._saved8Bytes, 0, 8);
		return ByteBuffer.wrap(_saved8Bytes).getDouble();
	}

	@Override
	public float readFloat() throws IOException {
		read(this._saved8Bytes, 0, 4);
		return ByteBuffer.wrap(_saved8Bytes).getFloat();
	}

	@Override
	public int readInt() throws IOException {
		read(this._saved8Bytes, 0, 4);
		return ByteBuffer.wrap(_saved8Bytes).getInt();
	}

	/** NOT SUPPORTED
	 * @see java.io.DataInput#readLine()
	 */
	@Override
	public String readLine() throws IOException {
		throw new IOException("NOT SUPPORTED");		
	}

	@Override
	public long readLong() throws IOException {
		read(this._saved8Bytes, 0, 8);
		return ByteBuffer.wrap(_saved8Bytes).getLong();
	}

	@Override
	public short readShort() throws IOException {
		read(this._saved8Bytes, 0, 2);
		return ByteBuffer.wrap(_saved8Bytes).getShort();
	}

	/** NOT SUPPORTED
	 * @see java.io.DataInput#readUTF()
	 */
	@Override
	public String readUTF() throws IOException {
		throw new IOException("NOT SUPPORTED");		
	}

	@Override
	public int readUnsignedByte() throws IOException {
		return (int)((byte)read() & 0xFF);
	}

	@Override
	public int readUnsignedShort() throws IOException {
		return (int)((short)read() & 0xFFff);
	}	
	
	////////////////////////////////////////////////
	////////////////////////////////////////////////
	
	// INTERNAL STATE
	
	protected DBCollection _chunkCollection = null;
	
	// Cached attributes from file:
	protected BasicDBObject _fileObj = null;
	protected int _chunkSize;
	protected int _lastChunkNum;
	protected long _fileSize;
	protected ObjectId _fileId;
	protected int _finalChunkSize;
	protected Date _modified; // (only filled in if needed)
	
	// Cached attributes from chunk:
	protected BasicDBObject _currChunkObj = null;
	protected byte[] _currData; // (only filled in if needed)
	
	// The current location in the file/chunk
	protected long _currPosInFile = 0;
	protected int _currPosInChunk = 0;
	protected int _currChunkNum = 0;
	protected int _currChunkSize = 0;
	
	// Safety for DB object access:
	public static final String _FILE_id_ = "_id";
	public static final String _FILE_chunkSize_ = "chunkSize";
	public static final String _FILE_length_ = "length";
	public static final String _FILE_uploadDate_ = "uploadDate";
	public static final String _CHUNK_files_id_ = "files_id";
	public static final String _CHUNK_n_ = "n";
	public static final String _CHUNK_data_ = "data";
	
	// For performance
	protected BasicDBObject _chunkQuery = null;
	protected byte[] _saved8Bytes = new byte[8];
	
	////////////////////////////////////////////////
	////////////////////////////////////////////////
	
	//TEST CODE
	
	public static void main(String[] args) throws ZipException {
	}
	
}

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
import java.util.Date;

import org.bson.types.ObjectId;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;

import net.sf.jazzlib.ZipException;

public class GridFSRandomAccessFile implements DataInput {

	////////////////////////////////////////////////
	////////////////////////////////////////////////
	
	// CONSTRUCTOR
	
	public GridFSRandomAccessFile(DBCollection coll, ObjectId fileId) {
		_chunkQuery = new BasicDBObject(_CHUNK_files_id_, fileId);
		_chunkQuery.put(_CHUNK_n_, 0);
		//TODO get file, grab stuff
	}

	////////////////////////////////////////////////
	////////////////////////////////////////////////
	
	// "FUNCTIONAL OVERRIDES" FROM RANDOMACCESSILE (ONLY FUNCTIONS CALLED FROM ZIPFILE)
	
	public int skipBytes(int n) throws IOException {
		int bytesSkipped = n;
		long newPosInFile = _currPosInFile + n;
		int newPosInChunk = _currPosInChunk + n; 
		int newChunkPos = (int) (newPosInFile/_chunkSize);
		if ((newChunkPos != _currChunkNum) || (null == _currChunk)) {
			if (newChunkPos < 0) {
				n += newChunkPos*_chunkSize;
				newChunkPos = 0;
				newPosInChunk = 0;
			}//TOTEST
			else if (newChunkPos >= _numChunks) {
				n += (_numChunks - newChunkPos - 1)*_chunkSize;
				newChunkPos = _numChunks - 1;
				newPosInChunk = _chunkSize - 1;
			}//TOTEST
			else {
				newPosInChunk = (int) (newPosInFile % _chunkSize);
			}//TOTEST
			_chunkQuery.put(_CHUNK_n_, newChunkPos);
			BasicDBObject newChunk = (BasicDBObject) _collection.findOne(_chunkQuery);
			if (null == newChunk) {
				throw new IOException("Unknown I/O exception");
			}
			_currChunk = newChunk;
			_data = (byte[]) newChunk.get(_CHUNK_data_); //TODO: need to check this is right
		}
		_currPosInFile += n;
		_currPosInChunk = newPosInChunk;
		return bytesSkipped;
	}//TOTEST

	public long length() {
		return _fileSize;
	}//TOTEST
	
	public void seek(long pos) throws IOException {
		int skip = (int)(pos - _currPosInFile);
		this.skipBytes(skip);
	}//(tested skipBytes)

	public void readFully(byte[] b) {
		//TODO
	}//(tested read(...))
	
	@Override
	public void readFully(byte[] b, int off, int len) throws IOException {
		// TODO 		
	}//(tested read(...))

	public int read() { // (reads 1B)
		//TODO
		return 0;
	}//TOTEST
	
	public int read(byte[] b) {
		//TODO
		return 0;
	}//(tested read(...))
	
	public int read(byte[] b, int off, int len) {
		//TODO
		return 0;
	}//TOTEST
	
	public void close() {
		//No need to do anything, up to calling code to call DBCollection, which has different persistence
	}//(no test)
	
	////////////////////////////////////////////////
	////////////////////////////////////////////////
	
	// ACTUAL OVERRIDES FROM DATAINPUT
	
	@Override
	public boolean readBoolean() throws IOException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public byte readByte() throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public char readChar() throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double readDouble() throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float readFloat() throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int readInt() throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String readLine() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long readLong() throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public short readShort() throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String readUTF() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int readUnsignedByte() throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int readUnsignedShort() throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}	
	
	////////////////////////////////////////////////
	////////////////////////////////////////////////
	
	// INTERNAL STATE
	
	protected DBCollection _collection = null;
	
	// The current location in the file/chunk
	protected long _currPosInFile = 0;
	protected int _currPosInChunk = 0;
	protected int _currChunkNum = 0;
	
	// The objects representing what and where we are:
	protected BasicDBObject _currFile = null;
	protected BasicDBObject _currChunk = null;
	
	// Cached attributes from file:
	protected int _chunkSize;
	protected int _numChunks;
	protected long _fileSize;
	protected ObjectId _fileId;
	protected Date _modified; // (only filled in if needed)
	
	// Cached attributes from chunk:
	protected byte[] _data; // (only filled in if needed)
	
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
	
	////////////////////////////////////////////////
	////////////////////////////////////////////////
	
	//TEST CODE
	
	public static void main(String[] args) throws ZipException {
	}
	
}

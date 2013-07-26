Modification of net.sf.jazzlib.ZipFile supporting MongoDB-GridFS-based ZIP files

GPL2 with classpath exception (due to use of net.sf.jazzlib.ZipFile)

When used in conjunction with the MongoDB java driver, provides a java.util.zip.ZipFile "interface" that backs directly onto a GridFS-uploaded ZIP file.

The GridFSRandomAccessFile can be also be used standalone, though it will not be that efficient in general cases because it doesn't cache chunks.

USAGE:

		import com.ikanow.utility.GridFSRandomAccessFile;
		import net.sf.jazzlib.GridFSZipFile;
		//MongoDB imports

		String dbName = "test";
		DBCollection db = new MongoClient().getDB(dbName);
		String fsName = "fs"; // (the default)
		ObjectId fileId = new ObjectId("FILEID");
		GridFSRandomAccessFile shareAsFile = new GridFSRandomAccessFile(db, fsName, fileId);
		//or
		//GridFSRandomAccessFile shareAsFile = new GridFSRandomAccessFile(new GridFS(db, fsName), fileId);
		String dummyFilename = "CanBeAnythingYouWant";
		net.sf.jazzlib.GridFSZipFile zipFile = new net.sf.jazzlib.GridFSZipFile(dummyFilename, shareAsFile);
		
		//Then use the zipFile object however you would use a java.util.zip.ZipFile...
 


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.ScheduledThreadPoolExecutor;
/*
 * Koushik Dasika
 * Chris Kim
 * Sreenidhi Kasireddy
 *
 */

public class TorrentManager {
	
	private TrackerThread trackThread;
	private byte[] id;
	int terminate = 0;
	

	public TorrentManager() {

	}
	//Takes in the Torrent file and converts it to a byte array
	public static byte[] readTorrent(File torrent) throws FileNotFoundException, IOException{
		FileInputStream fis = new FileInputStream(torrent);
		byte[] torrentinfo = new byte[(int) torrent.length()];
		try{
			fis.read(torrentinfo);
		}catch (IOException e) {
			try {
				fis.close();
			} catch (Exception ex) {
			} 
			throw e;
	}
		return torrentinfo;
	}
	
	//Takes in the torrent file, converts it into a byte array, and then 
	//decodes it using TorrentInfo.java
	public void loadTorrent(File torrentfile, File destination) throws IOException, BencodingException, InterruptedException{
		byte[] torrentdata = readTorrent(torrentfile);
		final TorrentInfo holder = new TorrentInfo(torrentdata);
		
		trackThread = new TrackerThread(holder, destination, this);
		new Thread(trackThread). start( );
				
		
	}
	
	//Generates a 20 byte Peer ID
	public final byte[] getPeerId() {

		if (id == null) {
			id = new byte[20];
			id[0] = (byte)'C';
			id[1] = (byte)'H';
			id[2] = (byte)'R';
			id[3] = (byte)'I';
			id[4] = (byte)'S';
			Random r = new Random();
			for(int i = 5; i < 20;i++) {				
				id[i] = (byte)(Byte.MIN_VALUE + r.nextInt(Byte.MAX_VALUE-Byte.MIN_VALUE));
			}
		}
		return id;
	}
}
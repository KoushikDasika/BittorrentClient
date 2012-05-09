import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
/*
 * Koushik Dasika
 * Chris Kim
 * Sreenidhi Kasireddy
 *
 */

public class DownloadManager {
	private TorrentInfo torrentinfo;
	private File destination;
	private TrackerThread trackerthread;
	private RandomAccessFile rAccess;
	
	private BitSet[] allPieces;
	private int[] availPieces; // Keeps track of pieces. 0 for available, 1 for completed, and 2 for in progress.
	
	private int maxPieceLength = 16384;
	private int totalNumPieces;
	
	private int lastIndex;
	private int lastPartLength;
	
	private boolean lock = false;
	

	
	public DownloadManager(TorrentInfo ti, File dest, TrackerThread tt) throws FileNotFoundException, IOException{
		torrentinfo = ti;
		destination = dest;
		trackerthread = tt;
		
		totalNumPieces = (int) Math.ceil((torrentinfo.file_length)/(1.0 * torrentinfo.piece_length));
		lastIndex = totalNumPieces-1;
		System.out.println("There are this many pieces: " + totalNumPieces);
		
		lastPartLength = (int) Math.ceil((torrentinfo.file_length) % torrentinfo.piece_length);
		maxPieceLength = torrentinfo.piece_length;
		availPieces = new int[totalNumPieces];

		
		//creates a RandomAccessFile which will be used to hold pieces of the torrent in memory
		rAccess = new RandomAccessFile(destination, "rwd");
		if (!destination.exists()) {
			rAccess.setLength(torrentinfo.file_length);
		} else {
			if (destination.length() != torrentinfo.file_length) {
				rAccess.setLength(torrentinfo.file_length);
			}
		}
	}
	
	
	//Checks if all pieces are downloaded
	public synchronized boolean isComplete() {
		for (int i = 0; i < totalNumPieces; i++) {
			if (availPieces[i] == 1) {
				continue;
			} else {
				return false;
			}
		}
		return true;
	}
		
	//Use rarest-first to determine next piece to download
	public synchronized int getNextPieceToDownload(Peer peer, int id) {
		while (lock == true) {
			try {
				wait();
	        } catch (InterruptedException e) { }
		}
		
		lock = true;
		notifyAll();

		BitSet peerpieces = peer.getAvailablePieces();
			
		if (peerpieces == null) {
			return -1;
		}
		
		if (isComplete()) {
			return -1;
		}
		
		//Initialize if needed
		if (allPieces == null) {
			allPieces = new BitSet[trackerthread.getNumberOfEligiblePeers()];
		}
			
		allPieces[id] = peerpieces;
		int[] pieceAvailability = new int[totalNumPieces];
		
		
		//Loop through bitsets and count the availability of each piece. Store the count in pieceAvailability[]
		for (int i = 0; i < trackerthread.getNumberOfEligiblePeers(); i++)
		{
			for (int j = 0; j < totalNumPieces; j++)
			{
				if (allPieces[i] != null)
				if (allPieces[i].get(j))
					pieceAvailability[j] = pieceAvailability[j] + 1;
			}
		}
		
		int rarestIndex = 0;
		//setting count to an arbitrarily high number.
		int count = 999999;
		
		for (int i = 0; i<totalNumPieces; i++)
		{
			if (pieceAvailability[i] < count && pieceAvailability[i] > 0 && availPieces[i] == 0 && peerpieces.get(i)) {
				rarestIndex = i;
				count = pieceAvailability[i];
			}
		}
		
		
		//If no suitable pieces can be found, then try an "in progress" piece
		if (count == 999999)
			for (int i = 0; i<totalNumPieces; i++)
			{
				if (pieceAvailability[i] < count && pieceAvailability[i] > 0 && availPieces[i] == 2 && peerpieces.get(i)) {
					rarestIndex = i;
					count = pieceAvailability[i];
				}
			}
		
		//If no suitable pieces can be found still, then give up
		if (count == 999999)
			return -1;
		
		
		//Set status of piece to "in progress"
		availPieces[rarestIndex] = 2;
		
		lock = false;
		notifyAll();
		return rarestIndex;

		
		/*
			Vector<Integer> v = new Vector(0);
			
			for (int i = 0; i < totalNumPieces; i++) {
				if (peerpieces.get(i) == true && availPieces[i] == 0 ) {
					v.add(i);
				}
			}
			if (v.isEmpty())
				return -1;
	
			Collections.shuffle(v);
		
		availPieces[v.get(0)] = 2;
		
		lock = false;
		notifyAll();
		return v.get(0);
		*/
		
	}

	
	public int getPieceLength(int index) {
		if (index == lastIndex) {
			return lastPartLength;
		}
		return maxPieceLength;
	}
		
	public int getTotalNumPieces() {
		return totalNumPieces;
	}
	
	public void setPieceCompleted(int piece) {
		availPieces[piece] = 1;
	}
	
	public void setPieceFailed(int piece) {
		availPieces[piece] = 0;
	}
	
	public void writeToDisk(int index, int begin, byte[] block,
			int offset, int length) throws IOException {

		long seek = index * torrentinfo.piece_length + maxPieceLength * begin;

		if (rAccess != null) {
			rAccess.seek(seek);
			rAccess.write(block, offset, length);
		//	System.out.println("Writing piece " + index);
		}
	}
	
	public void readFromDisk(int index, int begin, byte[] block, int length) throws IOException {

		long seek = index * torrentinfo.piece_length + maxPieceLength * begin;

		if (rAccess != null) {
			rAccess.seek(seek);
		}
		rAccess.readFully(block, 0, length);
	}
	
	public static byte[] hash(byte[] hashThis) {

		byte[] hash = new byte[20];
		MessageDigest md = null;
		try {
				md = MessageDigest.getInstance("SHA-1");
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		hash = md.digest(hashThis);
		return hash;
	}
}

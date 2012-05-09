import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.BitSet;

/*
 * Koushik Dasika
 * Chris Kim
 * Sreenidhi Kasireddy
 *
 */

public class PeerThread implements Runnable{
	private final TorrentInfo torrentinfo;
	private final TorrentManager tmanager;
	private final Peer peer;
	private DownloadManager dlmanager;
	private Socket psocket = null;
	private Boolean interestingPeer;
	
	private boolean choked = true;
	
	private static final String PROTOCOLSTR = "BitTorrent protocol";
	private static final int RESERVEDBYTES = 8;
	private static final int INFOHASHLENGTH = 20;
	private static final int PEERIDLENGTH = 20;
	
	private static final byte INTERESTED = 2;
	private static final byte UNINTERESTED = 3;
	private static final byte HAVE = 4;
	private static final byte REQUEST = 6;
	private static final byte PIECE = 7;
	
	public static final byte BITFIELD = 5;
	public static final byte KEEP_ALIVE = -1;
	public static final byte CHOKE = 0;
	public static final byte UNCHOKE = 1;
	
	
	//Constructs a Peer Thread given certain parameters
	public PeerThread(Peer p, DownloadManager dlman, TorrentManager tman, TorrentInfo tinfo){
		super();
		peer = p;
		dlmanager = dlman;
		tmanager = tman;
		torrentinfo = tinfo;
		interestingPeer = true;
	}
	

	public void run() {
		try {
			//Opens a socket
			psocket = new Socket();
			InetSocketAddress peerAddress = new InetSocketAddress(peer.getAddress(), peer.getPort());
			psocket.setKeepAlive(true);			
			psocket.setSoTimeout(600000);
			psocket.connect(peerAddress, 47474 + (int)Thread.currentThread().getId());
			
			DataOutputStream outputStream = new DataOutputStream(psocket.getOutputStream());
			writeHandshake(outputStream);
			

			boolean canRead = true;
			DataInputStream inputStream = new DataInputStream(psocket.getInputStream());
			int len = inputStream.readByte();
			byte[] proto = new byte[len];
			inputStream.read(proto);
			byte[] reserved = new byte[RESERVEDBYTES];
			inputStream.read(reserved);
			byte[] infoHash = new byte[INFOHASHLENGTH];
			inputStream.read(infoHash);
			byte[] peerId = new byte[PEERIDLENGTH];
			inputStream.read(peerId);
			

			while (canRead && !dlmanager.isComplete()) {
				if (inputStream.available() == 0) {
					
					Thread.yield();
					try {
						Thread.sleep(10);
					}catch (InterruptedException ex) {
						return;
					}
					continue;
				}
				
				int lenPrefix = inputStream.readInt();
				// keepalive
				if (lenPrefix == 0) {
					continue;
				}
				
				byte message = inputStream.readByte();
				
				if (message == PIECE) {
					int index = inputStream.readInt();
					int begin = inputStream.readInt();
					int blength = lenPrefix - 9;
					
					byte[] block = new byte[blength];
					int numBytesRead;
					int togo = blength;
					while (togo > 0) {
						numBytesRead = inputStream.read(block, 0 + blength - togo, togo);
						if (numBytesRead == -1) {
							canRead = false;
							continue;
						}
						togo -= numBytesRead;
					}

					dlmanager.writeToDisk(index, begin, block, 0, blength);
					int pieceLength = dlmanager.getPieceLength(index);
					if (blength == pieceLength) {
						//Check hashes
						byte[] newhash = dlmanager.hash(block);
						byte[] hash = torrentinfo.piece_hashes[index].array();
						if (Arrays.equals(hash, newhash)) {
							System.out.println("Hash matches, setting piece "+index+" completed.");

							dlmanager.setPieceCompleted(index);
							if (dlmanager.isComplete()) {
								System.out.println("The file is complete.");
								// terminate
								//canRead = false;
								continue;
							}
						}
						else {
							System.out.println("Hash failed!");	
							dlmanager.setPieceFailed(index);
						}
					}
				}
				
				//Decode bitfield
				if (message == BITFIELD) {
					System.out.println("P" + Thread.currentThread().getName() + ": Received BITFIELD from "+peer.getId());
					byte[] body = new byte[lenPrefix - 1];
					
					inputStream.read(body);

					for (int i = 0; i < body.length; i++) {
						byte bite = body[i];
						for (int j = 0; j < 8; j++) {
							int bitfieldposition = i * 8 + j;
							boolean isSet = isBitSet(bite, 7 - j);
							peer.availablePieces.set(bitfieldposition, isSet);
						}
					}
					System.out.println("P" + Thread.currentThread().getName() + ": Bitfield: " + peer.availablePieces);
					if (dlmanager.getNextPieceToDownload(peer, Integer.parseInt(Thread.currentThread().getName())) != -1)
					{
						System.out.println(String.format("P" + Thread.currentThread().getName() + ": Sending INTERESTED message to "+ peer.getId()));
						outputStream.writeInt(1);
						outputStream.write(INTERESTED);
					}
					else 
					{
						System.out.println("No pieces to download from " + peer.getId());
						interestingPeer = false;
						outputStream.writeInt(1);
						outputStream.write(UNINTERESTED);
					}				
				}
				// unchoke
				if (message == UNCHOKE) {
					choked = false;
				}
				
				if (message==CHOKE) {
					choked = true;
				}
				
				if (message == INTERESTED) {
					//Send UNCHOKE message to peer
					outputStream.writeInt(1);
					outputStream.write(UNCHOKE);
					continue;
				}

				if (!choked && interestingPeer) {
					requestPiece(outputStream);
				}
			}
			
			//Tell peers we have the pieces
			if (dlmanager.isComplete()) {
				for (int i=0; i<dlmanager.getTotalNumPieces(); i++)
					havePiece(outputStream, i);
			}
			
			
			//Logic for accepting requests and uploading pieces (seeding stage)
			while (tmanager.terminate !=1) {
				
					if (inputStream.available() != 0) {
					
					Thread.yield();
					try {
						Thread.sleep(10);
					}catch (InterruptedException ex) {
						return;
					}
				
				
				byte message = 0;
				try {
					int lenPrefix = inputStream.readInt();				
					message = inputStream.readByte();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					//e.printStackTrace();
				}
				
				// unchoke
				if (message == UNCHOKE) {
					choked = false;
				}
				
				if (message==CHOKE) {
					choked = true;
				}
				
				if (message == INTERESTED)
				{
					outputStream.writeInt(1);
					outputStream.write(UNCHOKE);
				}
				
				if (message == UNINTERESTED)
				{
					System.out.println(":(");
				}
				
				if (message == REQUEST && !choked)
				{
					int index = inputStream.readInt();
					if (index < 0 || index > 99999)
						continue;
					int begin = inputStream.readInt();
					System.out.println("begin " + begin);
					int blength = dlmanager.getPieceLength(index);
					System.out.println("blength " + blength);
					
					System.out.println("Piece "+index+" requested by "+peer.getId());
										
					outputStream.writeInt(blength + 9);
					outputStream.write(PIECE);
					outputStream.writeInt(index);
					outputStream.writeInt(begin);
					
					byte[] block = new byte[blength];
					
					dlmanager.readFromDisk(index, begin, block, blength);
					System.out.println("Sending piece " + index + " to "+ peer.getId());
					outputStream.write(block);
					continue;
				}
				
				}
				
			}
			
			System.out.println("Closing peer socket");
			cleanup(outputStream,inputStream,psocket);

		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}
	
	private void cleanup(OutputStream outputStream, InputStream inputStream, Socket socket) {
		try {
			outputStream.close();
		} catch (Exception e) {

		}
		try {
			inputStream.close();
		} catch (Exception e) {

		}
		try {
			if (!socket.isClosed()) {
				socket.close();
			}
		} catch (Exception e) {

		}
	}
	private void writeHandshake(DataOutputStream outputStream) throws IOException {
		outputStream.writeByte(19);
		outputStream.write(PROTOCOLSTR.getBytes("ASCII"));
		outputStream.write(new byte[RESERVEDBYTES]);
		outputStream.write(torrentinfo.info_hash.array());
		outputStream.write(tmanager.getPeerId());
	}
	
	private void requestPiece(DataOutputStream outputStream) throws IOException {
		int index = dlmanager.getNextPieceToDownload(peer,Integer.parseInt(Thread.currentThread().getName()));
		if (index ==-1)
		{
			System.out.println("No pieces to download from " + peer.getId());
			interestingPeer = false;
		}
		else
		{
			int subPiece = 0;
			outputStream.writeInt(13);
			outputStream.write(REQUEST);
			outputStream.writeInt(index);
			System.out.println("Requesting piece #"+index + " from " + peer.getId());
			outputStream.writeInt(subPiece);
			int length = dlmanager.getPieceLength(index);
			outputStream.writeInt(length);
		}
	}
	
	private void havePiece(DataOutputStream outputStream, int index) throws IOException {
			outputStream.writeInt(5);
			outputStream.write(HAVE);
			outputStream.writeInt(index);
	}
	
	private static Boolean isBitSet(byte b, int bit) {
		return (b & (1 << bit)) != 0;
	}
	
}


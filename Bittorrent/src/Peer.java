import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;
/*
 * Koushik Dasika
 * Chris Kim
 * Sreenidhi Kasireddy
 *
 */


public class Peer {

	private String ipadd;
	private byte[] peerId;
	private String peerIdString;
	private int portnumber;
	
	public BitSet availablePieces = new BitSet();
	
	public final static ByteBuffer PeerKey = ByteBuffer.wrap(new byte[] { 'p', 'e', 'e', 'r', ' ', 'i', 'd' });
	public final static ByteBuffer IPKey = ByteBuffer.wrap(new byte[] { 'i', 'p' });
	public final static ByteBuffer KEY_PORT = ByteBuffer.wrap(new byte[] { 'p', 'o', 'r', 't' });

	//Constructs Peer object
	public Peer(Map<ByteBuffer, Object> peer) {
		if (peer.containsKey(PeerKey)) {
			ByteBuffer tempId = (ByteBuffer)peer.get(PeerKey);
			CharBuffer result = Charset.forName("ISO-8859-1").decode(tempId);
			peerIdString = result.toString();
			peerId = peerIdString.getBytes();
		}
		if (peer.containsKey(IPKey)) {
			ByteBuffer tempAddress = (ByteBuffer)peer.get(IPKey);
			CharBuffer result = Charset.forName("ISO-8859-1").decode(tempAddress);
			ipadd = result.toString();
			
		}
		if (peer.containsKey(KEY_PORT)) {
			portnumber = ((Number) peer.get(KEY_PORT)).intValue();
		}
	}

	public String getAddress() {
		return ipadd;
	}

	public byte[] getPeerId() {
		return peerId;
	}

	public int getPort() {
		return portnumber;
	}

	public String getId() {
		return peerIdString;
	}
	
	public BitSet getAvailablePieces() {
		return availablePieces;
	}

}

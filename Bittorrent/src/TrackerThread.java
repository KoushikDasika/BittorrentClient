/*
 * Koushik Dasika
 * Chris Kim
 * Sreenidhi Kasireddy
 *
 */


import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;



public class TrackerThread implements Runnable{
	private File destination;
	private TorrentInfo torrentinfo;
	private TorrentManager tmanager;
	private DownloadManager dlmanager;
	private Socket tsocket = null;
	private OutputStream out;
	private InputStream in;
	private PrintWriter outw;
	private StringBuilder sb;
	private Map<ByteBuffer, Object> announce;

	
	private int interval=180;
	private List<Peer> peerList;
	private ArrayList<Peer> eligiblePeers;
	
	public final static ByteBuffer KEY_INTERVAL = ByteBuffer.wrap(new byte[] { 'i', 'n', 't', 'e', 'r', 'v', 'a', 'l' });
	public final static ByteBuffer PeersKey = ByteBuffer.wrap(new byte[] { 'p', 'e', 'e', 'r', 's' });

	public TrackerThread(TorrentInfo holder, File destination, TorrentManager torrentManager) throws FileNotFoundException, IOException {
		// TODO Auto-generated constructor stub
		this.torrentinfo = holder;
		this.tmanager = torrentManager;
		this.destination = destination;
		dlmanager = new DownloadManager(torrentinfo,destination, this);
	}

	public TorrentInfo getTorrentinfo() {
		return torrentinfo;
	}


	public TorrentManager getTmanager() {
		return tmanager;
	}
	
	
	@Override
	public void run(){
	
		
		try {
			trackerRequest();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		
		//Wait a couple seconds
		try {
			Thread.sleep(1500);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		//Start a peerThread for each eligible peer		
		PeerThread[] peerThread = new PeerThread[eligiblePeers.size()];
		
		for (int i=0; i < eligiblePeers.size(); i++)
		{
			peerThread[i] = new PeerThread( eligiblePeers.get(i), dlmanager, tmanager, torrentinfo);
			new Thread(peerThread[i], Integer.toString(i)). start( );		
		}
				
		System.out.println("interval is "+interval);
		
		while (tmanager.terminate !=1)
		{
			try {
				Thread.sleep(interval*1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			trackerScrape();
		}
		
	}
	
	
	// Open a tcp socket and send HTTP GET request to the tracker
	@SuppressWarnings("unchecked")
	private void trackerRequest() throws UnknownHostException, IOException {
				
		tsocket = new Socket(torrentinfo.announce_url.getHost(), torrentinfo.announce_url.getPort());
		out = tsocket.getOutputStream();
		outw = new PrintWriter(out,false);
		outw.print("GET " + "/announce?");
		outw.print("info_hash=");
		

		// Convert info_hash to an escaped hex string
		byte[] bytes = torrentinfo.info_hash.array();
		sb = new StringBuilder(bytes.length*2);
	    for(byte b: bytes)
	    	sb.append("%" + Integer.toHexString(b+0x800).substring(1));
	    
	    // Build HTTP GET request
	    outw.print(sb.toString());		
	    String myPeerId = new String(tmanager.getPeerId());
		outw.print("&peer_id=" + myPeerId);
		outw.print("&port=" + "6881");
		outw.print("&uploaded=" + "0");
		outw.print("&downloaded=" + "0");
		outw.print("&left=" + Integer.toString(torrentinfo.file_length));
		outw.print("&event=" + "started");
        outw.print(" HTTP/\1.1\r\n");
        outw.print("Accept: text/plain, text/html, text/*\r\n");
        outw.print("\r\n");
        outw.flush();
        
        in = tsocket.getInputStream();
               

        //  read http response
        /*
        InputStreamReader inr = new InputStreamReader(in);
        BufferedReader br = new BufferedReader(inr);
        String line;
        while ((line = br.readLine()) != null)
        { 
        	System.out.println(line);
        }
        */
        
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[16384];

        while ((nRead = in.read(data, 0, data.length)) != -1) {
          buffer.write(data, 0, nRead);
        }

        buffer.flush();


        byte[] response = buffer.toByteArray();
        byte[] important;

        //Get correct range of header to pass to Bencoder
        if (response[109] == 100)
        	important = Arrays.copyOfRange(response, 109, response.length);
        else
        	important = Arrays.copyOfRange(response, 110, response.length);

        try {
			announce = (Map<ByteBuffer, Object>) Bencoder2.decode(important);

		//	ToolKit.printMap(announce, 0);
			AnnounceResults(announce);
		} catch (BencodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	//Process tracker response, go through peerlist and choose peers
	public void AnnounceResults(Map<ByteBuffer, Object> a) {
		if(a.containsKey(KEY_INTERVAL)) {
			this.interval = ((Number)a.get(KEY_INTERVAL)).intValue();
			if (this.interval > 180)
				this.interval = 180;
		}
		if(a.containsKey(PeersKey)) {
			List<Map<ByteBuffer,Object>> peerList = (List<Map<ByteBuffer,Object>>) announce.get(PeersKey);
			this.peerList = new ArrayList<Peer>(peerList.size());
			
			int count = 0;
			eligiblePeers = new ArrayList<Peer>();
			
			for(Map<ByteBuffer,Object> peer : peerList) {
				Peer p = new Peer(peer);
				//Only connect to peers at 128.6.5.130 and 128.6.5.131 with peer_id prefix of RUBT01
				if( (p.getAddress().equals("128.6.5.130") || p.getAddress().equals("128.6.5.131") )&& p.getId().indexOf("RUBT01") == 0)
				{
					eligiblePeers.add(p);
					System.out.println("peer " + count + ": "+ p.getAddress() + ":" + p.getPort() + " " + p.getId());
					count++;
				}
			}
			System.out.println("Number of eligible peers: "+eligiblePeers.size());
		}
		
		
	}

	
	private void trackerScrape() {
		System.out.println("Scraping on an interval of " + interval + " seconds.");
		outw.print(sb.toString());		
	    String myPeerId = new String(tmanager.getPeerId());
		outw.print("&peer_id=" + myPeerId);
		outw.print("&port=" + "6881");
		outw.print("&uploaded=" + "0");
		outw.print("&downloaded=" + "0");
		outw.print("&left=" + Integer.toString(torrentinfo.file_length));
		if (dlmanager.isComplete())
			outw.print("&event=" + "completed");
		else if (tmanager.terminate == 1)
			outw.print("&event=" + "stopped");
        outw.print(" HTTP/\1.1\r\n");
        outw.print("Accept: text/plain, text/html, text/*\r\n");
        outw.print("\r\n");
        outw.flush();
	}
	
	public void closeSockets(){
		trackerScrape();
		System.out.println("Closing tracker socket");
		try {
			out.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			in.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			tsocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public int getNumberOfEligiblePeers() {
		return eligiblePeers.size();
	}
}

import java.io.Console;
import java.io.File;
import java.util.Scanner;


/*
 * Koushik Dasika
 * Chris Kim
 * Sreenidhi Kasireddy
 *
 */
public class RUBTClient {
	private TorrentManager torrentMan;
	
	
	//Starts the RUBTClient 
	public static void main(String[] args) throws InterruptedException{
		if(args.length != 2){
			System.err.println("Enter name of torrent file and torrent destination file");
		}
		
		String torrent = args[0];
		String destinationfile = args[1];
		
		RUBTClient client = new RUBTClient();
		client.start(new File(torrent), new File(destinationfile));
		
		
		
	}
	
	//Sets the listening port for this computer
	public RUBTClient(){
		
	}
	//Starts the torrent manager 
	public void start(File torrent, File destination) throws InterruptedException{
		torrentMan = new TorrentManager();
		try{
			torrentMan.loadTorrent(torrent, destination);
		}catch(Exception e){
			e.printStackTrace();
		}
		
	}
	
	
	

}

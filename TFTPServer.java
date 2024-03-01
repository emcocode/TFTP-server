import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class TFTPServer 
{
	public static int TFTPPORT;
	public static final int BUFSIZE = 516;
	public static String READDIR;
	public static String WRITEDIR;
	// OP codes
	public static final int OP_RRQ = 1;
	public static final int OP_WRQ = 2;
	public static final int OP_DAT = 3;
	public static final int OP_ACK = 4;
	public static final int OP_ERR = 5;

	public static void main(String[] args) throws IOException {
		// Starting the server
		// Here we also allow other folders to be the public folders, as it was requested.
		try 
		{
			if (args.length == 2) { // If we have proper arguments, we use them. Otherwise we set port 69 and folder to rw/.
				try {
					TFTPPORT = Integer.parseInt(args[0]);
					if (args[1].startsWith("/")) {
						args[1] = args[1].substring(1);
					}
					if (!args[1].endsWith("/")) {
						args[1] += "/";
					}
					File folder = new File(args[1]);
					if (folder.exists() && folder.isDirectory()) {
						READDIR = args[1];
						WRITEDIR = args[1];
					} else {
						new File(args[1]).mkdirs();
						READDIR = args[1];
						WRITEDIR = args[1];
					}
					System.out.println("Setting " + args[1] + " as folder.");
				} catch (Exception e) {
					System.out.println("Invalid input!");
					System.exit(1);
				}
				TFTPServer server = new TFTPServer();
				server.start();
			} else {
				TFTPPORT = 69;
				READDIR = "rw/";
				WRITEDIR = "rw/";
				TFTPServer server = new TFTPServer();
				server.start();
			}
			
		}
		catch (SocketException e) {
			e.printStackTrace();
		}
	}

	
	private void start() throws IOException {
		byte[] buf= new byte[BUFSIZE];
		
		// Create socket
		DatagramSocket socket = new DatagramSocket(null);
		
		// Create local bind point 
		SocketAddress localBindPoint = new InetSocketAddress(TFTPPORT);
		socket.bind(localBindPoint);

		System.out.printf("Listening at port %d for new requests\n", TFTPPORT);

		// Loop to handle client requests 
		while (true) 
		{        
			
			final InetSocketAddress clientAddress = receiveFrom(socket, buf);
			
			// If clientAddress is null, an error occurred in receiveFrom()
			if (clientAddress == null) 
				continue;

			final StringBuffer requestedFile= new StringBuffer();
			final int reqtype = ParseRQ(buf, requestedFile);

			new Thread() 
			{
				public void run() 
				{
					try 
					{
						DatagramSocket sendSocket= new DatagramSocket(0);

						// Connect to client
						sendSocket.connect(clientAddress);						
						
						// Two different types of request information, I´m going with second one, but it is easily changed.
						// System.out.printf("%s request for %s from %s using port %d\n",
						// 		(reqtype == OP_RRQ) ? "Read" : "Write",
						// 		requestedFile.toString(), clientAddress.getHostName(), clientAddress.getPort());  
								
						System.out.printf("%s request for %s from %s using port %d\n",
								(reqtype == OP_RRQ) ? "Read" : "Write",
								clientAddress.getHostName(), clientAddress.getAddress(), clientAddress.getPort()); 

						// Read request
						if (reqtype == OP_RRQ) 
						{      
							requestedFile.insert(0, READDIR);
							HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
						}
						// Write request
						else 
						{                       
							requestedFile.insert(0, WRITEDIR);
							HandleRQ(sendSocket,requestedFile.toString(),OP_WRQ);  
						}
						sendSocket.close();
					} 
					catch (SocketException e) {
						e.printStackTrace();
					}
				}
			}.start();
		}
	}
	
	/**
	 * Reads the first block of data, i.e., the request for an action (read or write).
	 * @param socket (socket to read from)
	 * @param buf (where to store the read data)
	 * @return socketAddress (the socket address of the client)
	 */
	private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) throws IOException
	{
		// Create datagram packet
		DatagramPacket dgp = new DatagramPacket(buf, buf.length);
		
		// Receive packet
		socket.receive(dgp);
		
		// Get client address and port from the packet
		InetSocketAddress socketAddress = new InetSocketAddress(dgp.getAddress(), dgp.getPort());

		return socketAddress;
	}

	/**
	 * Parses the request in buf to retrieve the type of request and requestedFile
	 * 
	 * @param buf (received request)
	 * @param requestedFile (name of file to read/write)
	 * @return opcode (request type: RRQ or WRQ)
	 */
	private int ParseRQ(byte[] buf, StringBuffer requestedFile) 
	{
		// See "TFTP Formats" in TFTP specification for the RRQ/WRQ request contents
		ByteBuffer wrap = ByteBuffer.wrap(buf);
		short opcode = wrap.getShort();
		
		requestedFile.append(new String(buf, 2, buf.length - 2));
		
		return opcode;
	}

	/**
	 * Handles RRQ and WRQ requests 
	 * 
	 * @param sendSocket (socket used to send/receive packets)
	 * @param requestedFile (name of file to read/write)
	 * @param opcode (RRQ or WRQ)
	 */
	private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode) {
		if(opcode == OP_RRQ)
		{
			// See "TFTP Formats" in TFTP specification for the DATA and ACK packet contents
			boolean result = send_DATA_receive_ACK(sendSocket, requestedFile, 1);
		}
		else if (opcode == OP_WRQ) 
		{
			boolean result = receive_DATA_send_ACK(sendSocket, requestedFile, 0);
		}
		else 
		{
			System.err.println("Invalid request. Sending an error packet.");
			send_ERR(sendSocket, 4, "Illegal TFTP operation!");
			return;
		}		
	}
	
	private boolean send_DATA_receive_ACK(DatagramSocket sendSocket, String requestedFile, int block) {
		try {
			String inputFileName = requestedFile.toString().split("octet")[0];
			String fileName = requestedFile.toString().split("octet")[0].substring(0, inputFileName.length() - 1);
			System.out.println("Sending " + fileName);
			File file = new File(fileName);
			
			if (file.exists()) {
				FileInputStream fis = new FileInputStream(file);

				byte[] buffer = new byte[512];
				int readBuffer = fis.read(buffer);
	
				if (readBuffer < 512) {
					ByteBuffer bb = ByteBuffer.allocate(BUFSIZE);
					bb.putShort((short) 3);
					bb.putShort((short) block);
					bb.put(buffer);
					
					short opcode = 1;
					while (opcode != 4) {
						DatagramPacket packetWithFile = new DatagramPacket(bb.array(), readBuffer + 4);
						sendSocket.send(packetWithFile);
	
						ByteBuffer ack = ByteBuffer.allocate(4);
						DatagramPacket ackPacket = new DatagramPacket(ack.array(), ack.array().length);
						sendSocket.receive(ackPacket);
						opcode = ack.getShort();
						opcode = 4;
					}
					return true;
				} else {
					System.out.println("File too big!");
					send_ERR(sendSocket, 0, "File too big.");
					return false;
				}
			} else {
				System.out.println("File not found!");
				send_ERR(sendSocket, 1, "File not found.");
				return false;
			}
		} catch (Exception e) {
			System.out.println("Not sent - access violation!");
			send_ERR(sendSocket, 2, "Access violation.");
			return false;
		}
	}
	
	private boolean receive_DATA_send_ACK(DatagramSocket sendSocket, String requestedFile, int block) {		
		try {
			String inputFileName = requestedFile.toString().split("octet")[0];
			String fileName = requestedFile.toString().split("octet")[0].substring(0, inputFileName.length() - 1);
			System.out.println("Receiving " + fileName);
			File file = new File(fileName);

			if (!file.exists()) {
				FileOutputStream fos = new FileOutputStream(file);

				ByteBuffer bb = ByteBuffer.allocate(4);
				bb.putShort((short) 4);
				bb.putShort((short) block);

				DatagramPacket acknowledgement = new DatagramPacket(bb.array(), bb.array().length);
				sendSocket.send(acknowledgement);

				try {
					byte[] buffer = new byte[BUFSIZE];
					DatagramPacket packetWithFile = new DatagramPacket(buffer, buffer.length);
					sendSocket.receive(packetWithFile);
				
					ByteBuffer receivedData = ByteBuffer.wrap(packetWithFile.getData());
					short opcode = receivedData.getShort();

					byte[] packetData = Arrays.copyOfRange(packetWithFile.getData(), 4, packetWithFile.getLength());
					if (packetData.length < 512) {
						fos.write(packetData);
						fos.flush();

						ByteBuffer packetAck = ByteBuffer.allocate(4);
						packetAck.putShort((short) 4);
						packetAck.putShort(receivedData.getShort());
						DatagramPacket dataAck = new DatagramPacket(packetAck.array(), packetAck.array().length);
						sendSocket.send(dataAck);
					} else {
						System.out.println("Too big file.");
						send_ERR(sendSocket, 0, "Too big file.");
						return false;
					}
				} catch (Exception e) {
					System.out.println("Something went wrong!");
					send_ERR(sendSocket, 0, "Something went wrong, could not receive file.");
					return false;
				}
				return true;
				} else {
					System.out.println("File already exists!");
					send_ERR(sendSocket, 6, "File already exists.");
					return false;
				}
			} catch (Exception e) {
				System.out.println("Something went wrong - access violation!");
				send_ERR(sendSocket, 2, "Access violation.");
				return false;
			} 
		}
	
	private void send_ERR(DatagramSocket sendSocket, int errorCode, String errorMsg) {
		ByteBuffer errorMessage = ByteBuffer.allocate(errorMsg.length() + 5);
		errorMessage.putShort((short) 5);
		errorMessage.putShort((short) errorCode);
		errorMessage.put(errorMsg.getBytes());

		DatagramPacket errPacket = new DatagramPacket(errorMessage.array(), errorMessage.array().length);
		try {
			sendSocket.send(errPacket);
		} catch (IOException e) {
			System.out.println("Something went wrong with error messaging.");
			e.printStackTrace();
		}
	}
	
}




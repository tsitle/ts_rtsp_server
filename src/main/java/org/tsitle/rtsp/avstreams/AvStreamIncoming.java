package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.AvCannotOpenInputException;
import org.tsitle.rtsp.exceptions.InputStreamEosException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;
import org.tsitle.rtsp.exceptions.UnixSocketException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;

import java.io.*;
import java.net.StandardProtocolFamily;
import java.net.URI;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AvStreamIncoming {

	private enum InputType {
		FILE, SOCKET
	}

	private final @Nullable LogMsgInterface logMsgInterface;
	private final InputType inputType;
	private final String inputUriStr;

	private InputStream gis;
	private BufferedInputStream bis;

	private boolean haveEos = false;

	/**
	 * Constructor.
	 * @param inputUri Input URI
	 * @throws AvCannotOpenInputException If the input stream cannot be opened
	 */
	public AvStreamIncoming(@NonNull URI inputUri) throws AvCannotOpenInputException {
		this(null, inputUri);
	}

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param inputUri Input URI
	 * @throws AvCannotOpenInputException If the input stream cannot be opened
	 */
	public AvStreamIncoming(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull URI inputUri
			) throws AvCannotOpenInputException {
		this.logMsgInterface = logMsgInterface;
		if (inputUri.getScheme() == null) {
			throw new IllegalArgumentException("Input URI scheme cannot be null (inputUri='" + inputUri + "')");
		}
		switch (inputUri.getScheme()) {
			case "file" -> this.inputType = InputType.FILE;
			case "socket" -> this.inputType = InputType.SOCKET;
			default -> throw new AvCannotOpenInputException("Unsupported URI scheme: " + inputUri.getScheme());
		}
		this.inputUriStr = inputUri.getPath();

		openInput();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void close() {
		final String FNC_NAME = getClass().getSimpleName() + ".close()";

		try {
			bis.close();
			gis.close();
			/*if (! isFile) {  // @TODO
				if (clientChannel != null) {
					clientChannel.close();
				}
				if (serverChannel != null) {
					serverChannel.close();
				}
				deleteSocketFile();
			}*/
			//
			haveEos = true;
		} catch (IOException ex) {
			logError(FNC_NAME, "IOException caught: " + ex.getMessage());
		}
	}

	/**
	 * Rewinds the stream to the beginning
	 * @throws AvCannotOpenInputException If the input stream cannot be reopened
	 */
	public void rewind() throws AvCannotOpenInputException {
		close();
		//
		openInput();
		//
		haveEos = false;
	}

	/**
	 * Checks if we can still read data from the stream.
	 * @return True if the end of the stream has been reached, false otherwise
	 */
	public boolean haveEos() {
		return haveEos;
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Reads some bytes from the stream.
	 * @param buf Output buffer to store the data in
	 * @param length Number of bytes to read
	 * @return Number of bytes read
	 */
	public int readBytes(byte[] buf, int length) throws InputStreamIoException, InputStreamEosException {
		return readBytes(buf, 0, length);
	}

	/**
	 * Reads some bytes from the stream.
	 * @param buf Output buffer to store the data in
	 * @param destOffset Offset in the output buffer to start writing at
	 * @param length Number of bytes to read
	 * @return Number of bytes read
	 */
	public int readBytes(byte[] buf, int destOffset, int length) throws InputStreamIoException, InputStreamEosException {
		final String FNC_NAME = getClass().getSimpleName() + ".readBytes()";

		try {
			int totalDidRead = 0;
			int stillToRead = length;
			while (stillToRead > 0) {
				int tmpToRead = Math.min(stillToRead, 32 * 1024);
				int tmpDidRead = bis.read(buf, destOffset, tmpToRead);
				if (tmpDidRead == -1) {
					haveEos = true;
					if (totalDidRead == 0) {
						throw new InputStreamEosException();
					}
				}
				stillToRead -= tmpDidRead;
				destOffset += tmpDidRead;
				totalDidRead += tmpDidRead;
			}
			return totalDidRead;
		} catch (IOException ex) {
			logError(FNC_NAME, "IOException caught: " + ex.getMessage());
			throw new InputStreamIoException(ex.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void openInput() throws AvCannotOpenInputException {
		try {
			this.gis = switch (inputType) {
					case FILE -> openFile();
					case SOCKET -> openSocket();
				};
		} catch (FileNotFoundException ex) {
			throw new AvCannotOpenInputException("FileNotFoundException caught: " + ex.getMessage());
		} catch (UnixSocketException ex) {
			throw new AvCannotOpenInputException("UnixSocketException caught: " + ex.getMessage());
		}
		this.bis = new BufferedInputStream(this.gis);
	}

	private @NonNull InputStream openFile() throws FileNotFoundException {
		return new FileInputStream(inputUriStr);
	}

	private void createServerSocket() throws IOException {
		final String FNC_NAME = getClass().getSimpleName() + ".readFromSocket()";

		Path socketPath = Path.of(inputUriStr);
		Files.deleteIfExists(socketPath);

		logDebug(FNC_NAME, "Accessing: " + socketPath);
		UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);

		try (ServerSocketChannel serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
			serverChannel.bind(address);
			logDebug(FNC_NAME, "Listening for binary data on: " + socketPath);

			while (true) {
				try (SocketChannel clientChannel = serverChannel.accept();
					InputStream is = Channels.newInputStream(clientChannel);
					FileOutputStream fos = new FileOutputStream("received_output.bin")) {

					logDebug(FNC_NAME, "Receiving binary stream...");

					byte[] buffer = new byte[8192]; // 8KB buffer
					int bytesRead;
					long totalBytes = 0;

					while ((bytesRead = is.read(buffer)) != -1) {
						fos.write(buffer, 0, bytesRead);
						totalBytes += bytesRead;
					}

					logDebug(FNC_NAME, "Transfer complete. Total bytes: " + totalBytes);
				} catch (IOException ex) {
					logError(FNC_NAME, "Connection error: " + ex.getMessage());
				}
			}
		} catch (IOException ex) {
			logError(FNC_NAME, "Connection error: " + ex.getMessage());
		} finally {
			Files.deleteIfExists(socketPath);
		}
	}

	/*private @NonNull InputStream openSocket() throws UnixSocketException {
		final String FNC_NAME = getClass().getSimpleName() + ".readFromSocket()";

		deleteSocketFile();

		Path socketPath = Path.of(socketUri);
		logDebug(FNC_NAME, "Accessing: " + socketPath);
		UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);

		try {
			serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
			serverChannel.configureBlocking(false);
			serverChannel.bind(address);
			logDebug(FNC_NAME, "Listening for binary data on: " + socketPath);
			clientChannel = null;
			int tmpTries = 0;
			while (clientChannel == null && ++tmpTries <= 10 * 15) {
				clientChannel = serverChannel.accept();
				try {
					Thread.sleep(100);
				} catch (InterruptedException ex) {
					throw new RuntimeException(e);
				}
			}
			if (clientChannel == null) {
				throw new IOException("Did not receive anything on socket");
			}
			logDebug(FNC_NAME, "Client connected on: " + socketPath);
			return Channels.newInputStream(clientChannel);
		} catch (IOException ex) {
			deleteSocketFile();
			throw new UnixSocketException(e.getMessage());
		}
	}*/

	private @NonNull InputStream openSocket() throws UnixSocketException {
		try {
			// @TODO
			return new FileInputStream("asd");
		} catch (FileNotFoundException ex) {
			throw new UnixSocketException(ex.getMessage());
		}
	}

	private void deleteSocketFile() {
		try {
			Files.deleteIfExists(Path.of(inputUriStr));
		} catch (IOException ignore) {
			// ignore
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		System.out.println(fncName + ": " + msg);  // @TODO
		if (logMsgInterface == null) {
			return;
		}
		logMsgInterface.addMsgForLogThread(RtxpLogLevel.DEBUG, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

	private void logError(@NonNull String fncName, @NonNull String msg) {
		System.err.println(fncName + ": " + msg);  // @TODO
		if (logMsgInterface == null) {
			return;
		}
		logMsgInterface.addMsgForLogThread(RtxpLogLevel.ERROR, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}

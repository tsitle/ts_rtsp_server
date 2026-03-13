package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.AvCannotOpenInputException;
import org.tsitle.rtsp.exceptions.InputStreamEosException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;

import java.io.*;
import java.net.URI;

public final class AvStreamIncoming implements AutoCloseable {

	private enum InputType {
		FILE, MQ
	}

	private final @Nullable LogMsgInterface logMsgInterface;
	private final InputType inputType;
	private final String inputUriHost;
	private final String inputUriPath;
	private final String inputUriAuth;

	private InputStream gis;

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
			case "tcp" -> this.inputType = InputType.MQ;
			default -> throw new AvCannotOpenInputException("Unsupported URI scheme: " + inputUri.getScheme());
		}
		this.inputUriHost = (this.inputType == InputType.MQ ? inputUri.getHost() : "");
		this.inputUriPath = inputUri.getPath();
		this.inputUriAuth = (this.inputType == InputType.MQ ? inputUri.getUserInfo() : "");

		//
		openInput();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void close() {
		final String FNC_NAME = getClass().getSimpleName() + ".close()";

		try {
			if (gis != null) {
				gis.close();
			}
			//
			haveEos = true;
		} catch (IOException e) {
			logError(FNC_NAME, "IOException caught: " + e.getMessage());
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
				int tmpDidRead = gis.read(buf, destOffset, tmpToRead);
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
		} catch (IOException e) {
			logError(FNC_NAME, "IOException caught: " + e.getMessage());
			throw new InputStreamIoException(e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void openInput() throws AvCannotOpenInputException {
		try {
			this.gis = switch (inputType) {
					case FILE -> openFile();
					case MQ -> openMq();
				};
		} catch (FileNotFoundException e) {
			throw new AvCannotOpenInputException("FileNotFoundException caught: " + e.getMessage());
		} catch (MqException e) {
			throw new AvCannotOpenInputException("MqException caught: " + e.getMessage());
		}
	}

	private @NonNull InputStream openFile() throws FileNotFoundException {
		return new FileInputStream(inputUriPath);
	}

	private @NonNull InputStream openMq() throws MqException {
		MqInputStream resIs = new MqInputStream(logMsgInterface, inputUriHost, inputUriPath, inputUriAuth);
		resIs.connectToMq();
		return resIs;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logError(@NonNull String fncName, @NonNull String msg) {
		if (logMsgInterface == null) {
			return;
		}
		logMsgInterface.addMsgForLogThread(RtxpLogLevel.ERROR, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}

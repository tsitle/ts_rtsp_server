package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.AvCannotOpenInputException;
import org.tsitle.rtsp.exceptions.InputStreamEosException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;
import org.tsitle.rtsp.threads.LogMsgInterface;

import java.io.*;
import java.net.URI;

public final class AvStreamIncomingFromFile extends AvStreamIncomingBase {

	private final String inputUriPath;

	private InputStream gis;

	/**
	 * Constructor.
	 * @param streamSourceId Stream source identifier
	 * @param inputUri Input URI
	 * @throws AvCannotOpenInputException If the input stream cannot be opened
	 */
	public AvStreamIncomingFromFile(
				int streamSourceId,
				@NonNull URI inputUri
			) throws AvCannotOpenInputException {
		this(null, streamSourceId, inputUri);
	}

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param streamSourceId Stream source identifier
	 * @param inputUri Input URI
	 * @throws AvCannotOpenInputException If the input stream cannot be opened
	 */
	public AvStreamIncomingFromFile(
				@Nullable LogMsgInterface logMsgInterface,
				int streamSourceId,
				@NonNull URI inputUri
			) throws AvCannotOpenInputException {
		super(logMsgInterface, streamSourceId);

		if (inputUri.getScheme() == null) {
			throw new IllegalArgumentException("Input URI scheme cannot be null (inputUri='" + inputUri + "')");
		}
		if (! inputUri.getScheme().equals("file")) {
			throw new IllegalArgumentException("Input URI scheme must be 'file'");
		}
		this.inputUriPath = inputUri.getPath();

		//
		openInput();
	}

	// -----------------------------------------------------------------------------------------------------------------
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
				int tmpToRead = Math.min(stillToRead, 4 * 1024);
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

	/**
	 * Rewinds the stream to the beginning
	 * @throws AvCannotOpenInputException If the input stream cannot be reopened
	 */
	@Override
	public void rewind() throws AvCannotOpenInputException {
		close();
		//
		openInput();
		//
		haveEos = false;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void close() {
		final String FNC_NAME = getClass().getSimpleName() + ".close()";

		try {
			if (gis != null) {
				gis.close();
			}
		} catch (IOException e) {
			logError(FNC_NAME, "IOException caught: " + e.getMessage());
		}
		//
		haveEos = true;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void openInput() throws AvCannotOpenInputException {
		try {
			this.gis = new FileInputStream(inputUriPath);
		} catch (FileNotFoundException e) {
			throw new AvCannotOpenInputException("FileNotFoundException caught: " + e.getMessage());
		}
	}

}

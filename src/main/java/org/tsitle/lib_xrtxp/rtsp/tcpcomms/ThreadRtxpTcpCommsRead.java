package org.tsitle.lib_xrtxp.rtsp.tcpcomms;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketActivityTimeoutException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketClosedException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoTcpChannelNr;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Optional;

final class ThreadRtxpTcpCommsRead extends ThreadRtxpTcpCommsBase {

	private enum ParseState {
		PS_NONE,
		PS_RTSP_HD,
		PS_RTSP_BODY,
		PS_RTP_RTCP
	}

	private static final int SOCKET_READ_FRAG_SIZE = 2048;
	private static final int RTSP_INPUT_LINE_MAX_LENGTH = 1024 * 4;

	private final @NonNull InputStream socketIs;
	private final BufferExt cacheSockBuf = new BufferExt();
	private final BufferExt cacheTempBuf = new BufferExt();
	private @NonNull ParseState parseState = ParseState.PS_NONE;
	private int parseBinaryPayloadLen = -1;
	private final RtspProtoTcpChannelNr parseBinaryChanNr = RtspProtoTcpChannelNr.ofEmpty();
	private boolean parseTextHaveEmptyLine = false;
	private int parseTextContentLenExp = -1;
	private int parseTextContentLenAct = 0;
	private boolean parseTextClearValue = false;

	private long discardedBinaryPktCount = 0L;
	private long discardedTextLinesCount = 0L;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param tcpActivityInterface TCP activity checker interface
	 * @param clientConnectionNr Client connection number
	 * @param rtspSocketTcp RTSP TCP socket for client communication
	 * @param timeoutSocketReadMs Timeout for TCP socket read operations in milliseconds
	 */
	ThreadRtxpTcpCommsRead(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull TcpActivityInterface tcpActivityInterface,
				int clientConnectionNr,
				@NonNull Socket rtspSocketTcp,
				int timeoutSocketReadMs
			) {
		super(
				logMsgInterface,
				tcpActivityInterface,
				clientConnectionNr,
				rtspSocketTcp,
				timeoutSocketReadMs,
				true
			);
		//
		try {
			this.socketIs = rtspSocketTcp.getInputStream();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		//
		this.cacheSockBuf.increaseSize(SOCKET_READ_FRAG_SIZE);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	void closeIoStream() {
		closeIoStream(BlockedState.Flag.SOCKET_READ, socketIs, null);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	long getDiscardedTextLinesCount() {
		try {
			blockedState.waitForUnblockedAndThenBlock(BlockedState.Flag.BUFFER_READ);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
			return 0L;
		}
		try {
			return discardedTextLinesCount;
		} finally {
			blockedState.unblock(BlockedState.Flag.BUFFER_READ);
		}
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	boolean canReadRtsp()
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException {
		raiseForErrorState();
		try {
			blockedState.waitForUnblockedAndThenBlock(BlockedState.Flag.BUFFER_READ);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
			return false;
		}
		try {
			return (! textDataRingBuf.isEmpty());
		} finally {
			blockedState.unblock(BlockedState.Flag.BUFFER_READ);
		}
	}

	Optional<String> readRtspLine()
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException {
		raiseForErrorState();
		try {
			blockedState.waitForUnblockedAndThenBlock(BlockedState.Flag.BUFFER_READ);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
			return Optional.empty();
		}
		try {
			if (textDataRingBuf.isEmpty()) {
				return Optional.empty();
			}
			TextRbEntry textEntryPtr = textDataRingBuf.poll().orElseThrow();
			return Optional.of(textEntryPtr.text);
		} finally {
			blockedState.unblock(BlockedState.Flag.BUFFER_READ);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	long getDiscardedBinaryPktCount() {
		try {
			blockedState.waitForUnblockedAndThenBlock(BlockedState.Flag.BUFFER_READ);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
			return 0L;
		}
		try {
			return discardedBinaryPktCount;
		} finally {
			blockedState.unblock(BlockedState.Flag.BUFFER_READ);
		}
	}

	boolean canReadRtpOrRtcp(@NonNull RtspProtoTcpChannelNr channNr)
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException {
		raiseForErrorState();
		if (! isRtpRtcpAllowed.get()) {
			return false;
		}

		try {
			blockedState.waitForUnblockedAndThenBlock(BlockedState.Flag.BUFFER_READ);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
			return false;
		}
		try {
			if (channNr.isEmpty()) {
				return false;
			}
			return (mapBinaryDataRbs.containsKey(channNr) && ! mapBinaryDataRbs.get(channNr).isEmpty());
		} finally {
			blockedState.unblock(BlockedState.Flag.BUFFER_READ);
		}
	}

	boolean readRtpOrRtcpBinary(@NonNull BufferExt buf, @NonNull RtspProtoTcpChannelNr channNr)
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException {
		buf.clear();
		raiseForErrorState();
		if (! isRtpRtcpAllowed.get()) {
			return false;
		}

		try {
			blockedState.waitForUnblockedAndThenBlock(BlockedState.Flag.BUFFER_READ);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
			return false;
		}
		try {
			if (channNr.isEmpty() || ! mapBinaryDataRbs.containsKey(channNr)) {
				return false;
			}
			BinaryPktRingBuf rbPtr = mapBinaryDataRbs.get(channNr);
			if (rbPtr.isEmpty()) {
				return false;
			}
			buf.copyOf(rbPtr.poll().orElseThrow());
			return true;
		} finally {
			blockedState.unblock(BlockedState.Flag.BUFFER_READ);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected boolean mainLoop() throws TcpSocketClosedException, TcpSocketIoException, TcpSocketActivityTimeoutException {
		if (rtspSocketTcp.isClosed()) {
			if (! (hasBeenRequestedToStop() || hasSocketBeenImplicitlyClosed.get())) {
				throw new TcpSocketClosedException();
			}
			boolean haveText = canReadRtsp();
			boolean haveBin = isRtpRtcpAllowed.get();
			if (haveBin) {
				haveBin = false;
				for (RtspProtoTcpChannelNr channNr : mapBinaryDataRbs.keySet()) {
					BinaryPktRingBuf rbPtr = mapBinaryDataRbs.get(channNr);
					if (! rbPtr.isEmpty()) {
						haveBin = true;
						break;
					}
				}
			}
			return (haveText || haveBin);  // continue or stop the thread
		}

		try {
			blockedState.waitForUnblockedAndThenBlock(BlockedState.Flag.SOCKET_READ);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
			return false;  // stop the thread
		}
		try {
			tcpActivityInterface.checkTcpActivityTimeout();

			try {
				blockedState.waitForUnblockedAndThenBlock(BlockedState.Flag.BUFFER_READ);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();  // restore flag
				return false;  // stop the thread
			}
			try {
				if (! readFromSocket()) {
					return true;  // nothing received - continue the thread
				}
				parseCachedData();
			} finally {
				blockedState.unblock(BlockedState.Flag.BUFFER_READ);
			}

			tcpActivityInterface.resetTcpActivityTimeoutTimer();
		} finally {
			blockedState.unblock(BlockedState.Flag.SOCKET_READ);
		}

		return true;  // all good - continue the thread
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private boolean readFromSocket() throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".readFromSocket()";

		try {
			if (rtspSocketTcp.isClosed()) {
				return false;
			}
			final int curOffs = cacheSockBuf.getUsed();
			cacheSockBuf.increaseSize(curOffs + SOCKET_READ_FRAG_SIZE);
			int tmpRd = socketIs.read(cacheSockBuf.getBaPtr(), curOffs, SOCKET_READ_FRAG_SIZE);
			if (tmpRd == -1) {
				if (hasBeenRequestedToStop()) {
					return false;
				}
				throw new TcpSocketClosedException();
			}
			if (tmpRd == 0) {
				return false;
			}

			cacheSockBuf.setUsed(curOffs + tmpRd);
		} catch (SocketTimeoutException e) {
			return false;
		} catch (IOException e) {
			if (hasBeenRequestedToStop()) {
				return false;
			}
			throw new TcpSocketIoException(FNC_NAME + ": IOException caught: " + e.getMessage());
		}
		return true;
	}

	private void parseCachedData() {
		final boolean localIsRtpRtcpAllowed = isRtpRtcpAllowed.get();
		final int bufSz = cacheSockBuf.getUsed();
		int bufRem = bufSz;
		int consumed = 0;
		for (int ix = 0; ix < bufSz; ) {
			byte b = cacheSockBuf.get(ix++);
			--bufRem;
			++consumed;
			int tmpUsed = 0;
			if (localIsRtpRtcpAllowed &&
					(parseState == ParseState.PS_RTP_RTCP || (parseState == ParseState.PS_NONE && b == '$'))) {
				// unread the first byte
				if (parseState == ParseState.PS_RTP_RTCP) {
					--ix;
					++bufRem;
					--consumed;
				}
				//
				parseState = ParseState.PS_RTP_RTCP;
				if (parseBinaryPayloadLen < 0 && bufRem < 3) {
					break;
				}
				if (parseBinaryPayloadLen < 0) {
					b = cacheSockBuf.get(ix++);  // channel
					--bufRem;
					++consumed;
					try {
						parseBinaryChanNr.setChannel8bit(Byte.toUnsignedInt(b));
					} catch (RtspProtoNumberRangeException e) {
						// this will never happen
					}
					b = cacheSockBuf.get(ix++);  // payload len upper byte
					--bufRem;
					++consumed;
					parseBinaryPayloadLen = (((int)b << 8) & 0xFF00);
					b = cacheSockBuf.get(ix++);  // payload len lower byte
					--bufRem;
					++consumed;
					parseBinaryPayloadLen |= ((int)b & 0x00FF);
					//
					clearBinaryDataRingBufEntry();
				}
				if (parseBinaryPayloadLen >= 0) {
					tmpUsed = copyBinaryDataFromBuffer(ix, bufRem);
				}
			} else if (parseState == ParseState.PS_NONE ||
					parseState == ParseState.PS_RTSP_HD || parseState == ParseState.PS_RTSP_BODY) {
				// unread the first char
				--ix;
				++bufRem;
				--consumed;
				//
				if (parseState == ParseState.PS_NONE) {
					if (textDataRingBuf.isFull()) {
						// discard oldest entry
						textDataRingBuf.poll();
						++discardedTextLinesCount;
					}
					//
					parseState = ParseState.PS_RTSP_HD;
				}
				if (parseTextClearValue) {
					parseTextClearValue = false;
					textDataRingBuf.getWritePointer().ifPresent(x -> x.text = "");
				}
				tmpUsed = copyTextDataFromBuffer(ix, bufRem);
			}
			ix += tmpUsed;
			bufRem -= tmpUsed;
			consumed += tmpUsed;
		}

		//
		if (consumed < bufSz) {
			// this shouldn't happen all that often
			cacheTempBuf.copyOf(cacheSockBuf, consumed, bufSz - consumed);
			cacheSockBuf.copyOf(cacheTempBuf);
		} else {
			cacheSockBuf.clear();
		}
	}

	private void clearBinaryDataRingBufEntry() {
		if (! mapBinaryDataRbs.containsKey(parseBinaryChanNr)) {
			mapBinaryDataRbs.put(
					parseBinaryChanNr.clone(),
					new BinaryPktRingBuf(BINARY_PKT_RB_SIZE_INIT, BINARY_PKT_RB_SIZE_MAX)
			);
		}
		BinaryPktRingBuf rbPtr = mapBinaryDataRbs.get(parseBinaryChanNr);
		if (rbPtr.isFull()) {
			// discard oldest entry
			rbPtr.poll();
			++discardedBinaryPktCount;
		}
		rbPtr.getWritePointer().ifPresent(BufferExt::clear);
	}

	private int copyBinaryDataFromBuffer(int srcOffs, int bufRem) {
		if (! mapBinaryDataRbs.containsKey(parseBinaryChanNr)) {
			return 1;  // this should never happen
		}
		BinaryPktRingBuf rbPtr = mapBinaryDataRbs.get(parseBinaryChanNr);
		Optional<BufferExt> lastEntryPtr = rbPtr.getWritePointer();
		if (lastEntryPtr.isEmpty()) {
			return 1;  // this should never happen
		}

		BufferExt bePtr = lastEntryPtr.get();
		final int alreadyCopied = bePtr.getUsed();
		final int stillToCopy = parseBinaryPayloadLen - alreadyCopied;
		int resI = 0;

		if (stillToCopy > 0) {
			resI = Math.min(stillToCopy, bufRem);
			bePtr.copyFrom(cacheSockBuf, srcOffs, bePtr.getUsed(), resI);
		}
		if (stillToCopy == resI) {
			rbPtr.advanceWritePointer();
			//
			parseState = ParseState.PS_NONE;
			parseBinaryPayloadLen = -1;
		}

		return resI;
	}

	private int copyTextDataFromBuffer(int srcOffs, int bufRem) {
		final String FNC_NAME = getClass().getSimpleName() + ".copyTextDataFromBuffer()";

		Optional<TextRbEntry> lastEntryPtr = textDataRingBuf.getWritePointer();
		if (lastEntryPtr.isEmpty()) {
			return 1;  // this should never happen
		}
		TextRbEntry textPtr = lastEntryPtr.get();

		int resI = 0;

		ArrayList<Character> tmpList = new ArrayList<>();

		boolean haveLf = false;
		while (bufRem > 0) {
			boolean discard = false;
			while (bufRem > 0) {
				char c = (char)cacheSockBuf.get(srcOffs++);
				--bufRem;
				++resI;
				if (discard) {
					if (parseState == ParseState.PS_RTSP_BODY &&
							parseTextContentLenExp >= 0 && ++parseTextContentLenAct >= parseTextContentLenExp) {
						break;
					}
					if (c == CRLF.charAt(1)) {
						break;
					}
					continue;
				}
				tmpList.add(c);
				if (parseState == ParseState.PS_RTSP_BODY &&
						parseTextContentLenExp >= 0 && ++parseTextContentLenAct >= parseTextContentLenExp) {
					break;
				}
				if (tmpList.size() >= RTSP_INPUT_LINE_MAX_LENGTH) {
					tmpList.add(CRLF.charAt(0));
					tmpList.add(CRLF.charAt(1));
					haveLf = true;
					discard = true;
					continue;
				}
				if (c == CRLF.charAt(1)) {
					haveLf = true;
					break;
				}
			}

			//
			if (parseState == ParseState.PS_RTSP_BODY &&
					parseTextContentLenExp >= 0 && parseTextContentLenAct >= parseTextContentLenExp) {
				break;
			}
			//
			String tmpLine = textPtr.text + listToString(tmpList);
			if (tmpLine.endsWith(CRLF)) {
				tmpLine = tmpLine.substring(0, tmpLine.length() - CRLF.length());  // don't use strip() here
			}
			if (haveLf) {
				if (parseState == ParseState.PS_RTSP_HD && tmpLine.toLowerCase().startsWith("content-length:")) {
					tmpLine = tmpLine.substring("content-length:".length()).strip();
					try {
						parseTextContentLenExp = Integer.parseInt(tmpLine);
					} catch (NumberFormatException e) {
						logError(FNC_NAME, "invalid content length value in RTSP header");
						parseTextContentLenExp = 0;
					}
				} else if (parseState == ParseState.PS_RTSP_HD && tmpLine.isEmpty()) {
					parseTextHaveEmptyLine = true;
				}
				break;
			}
		}

		textPtr.text += listToString(tmpList);
		if ((parseState == ParseState.PS_RTSP_HD && haveLf) ||
				(parseState == ParseState.PS_RTSP_BODY &&
						(parseTextContentLenExp < 0 || parseTextContentLenAct >= parseTextContentLenExp ||
								haveLf))) {
			//logDebug(FNC_NAME, "full line: " + textPtr.text.strip());
			textDataRingBuf.advanceWritePointer();
			//
			if ((parseState == ParseState.PS_RTSP_HD && parseTextHaveEmptyLine) ||
					(parseState == ParseState.PS_RTSP_BODY &&
							(parseTextContentLenExp < 1 || parseTextContentLenAct >= parseTextContentLenExp))) {
				if (parseState == ParseState.PS_RTSP_HD && parseTextContentLenExp > 0) {
					parseState = ParseState.PS_RTSP_BODY;
					parseTextClearValue = true;
				} else {
					parseState = ParseState.PS_NONE;
					parseTextContentLenExp = -1;
					parseTextContentLenAct = 0;
					parseTextClearValue = true;
				}
				parseTextHaveEmptyLine = false;
			} else if (parseState == ParseState.PS_RTSP_HD) {
				parseState = ParseState.PS_NONE;
				parseTextClearValue = true;
			} else if (parseState == ParseState.PS_RTSP_BODY && haveLf) {
				parseTextClearValue = true;
			}
		}

		return resI;
	}

	private static @NonNull String listToString(@NonNull ArrayList<@NonNull Character> list) {
		StringBuilder builder = new StringBuilder(list.size());
		for (Character c : list) {
			builder.append(c);
		}
		return builder.toString();
	}

}

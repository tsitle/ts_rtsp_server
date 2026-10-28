package org.tsitle.lib_xrtxp.rtsp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketActivityTimeoutException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketClosedException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketIoException;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSocketPortNr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoTcpChannelNr;

import java.util.*;

/**
 * Interface for a TCP Socket wrapper that reads/writes RTxP messages over a single TCP socket.
 */
public interface RtxpTcpCommsInterface {

	boolean isSocketClosed();

	void closeSocket();

	@SuppressWarnings("unused")
	@NonNull RtspProtoSocketPortNr getSocketLocalPort();

	@NonNull RtspProtoSocketPortNr getSocketRemotePort();

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	boolean getIsRtpRtcpAllowed();

	void setIsRtpRtcpAllowed(boolean value);

	// -----------------------------------------------------------------------------------------------------------------

	void setTcpActivityTimeoutForRtspOnly();

	void setTcpActivityTimeoutForRtxp();

	void resetTcpActivityTimeoutTimer();

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Wait until an RTSP line is available for reading or the timeout has elapsed.<br />
	 * Returns early if the connection gets closed.
	 * @param timeoutMs Max. time to wait in milliseconds
	 * @return true if an RTSP line is available
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	boolean waitUntilCanReadRtsp(long timeoutMs)
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException;

	Optional<String> readRtspLine()
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException;

	void writeRtspLines(@NonNull List<@NonNull String> lines)
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException;

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Wait until an RTP/RTCP packet is available for reading on the given channel or the timeout has elapsed.<br />
	 * Returns early if the connection gets closed.
	 * @param channNr TCP channel number
	 * @param timeoutMs Max. time to wait in milliseconds
	 * @return true if an RTP/RTCP packet is available
	 */
	boolean waitUntilCanReadRtpOrRtcp(@NonNull RtspProtoTcpChannelNr channNr, long timeoutMs)
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException;

	boolean readRtpOrRtcpBinary(@NonNull BufferExt buf, @NonNull RtspProtoTcpChannelNr channNr)
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException;

	void writeRtpOrRtcpBinary(@NonNull BufferView bufView, @NonNull RtspProtoTcpChannelNr channNr)
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException;

}

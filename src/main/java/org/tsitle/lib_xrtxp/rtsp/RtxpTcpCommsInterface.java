package org.tsitle.lib_xrtxp.rtsp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketActivityTimeoutException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketClosedException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketIoException;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoTcpChannelNr;

import java.util.*;

/**
 * Interface for a TCP Socket wrapper that reads/writes RTxP messages over a single TCP socket.
 */
public interface RtxpTcpCommsInterface {

	boolean isSocketClosed();

	void closeSocket();

	@SuppressWarnings("unused")
	int getSocketLocalPort();

	int getSocketRemotePort();

	// -----------------------------------------------------------------------------------------------------------------

	boolean getIsRtpRtcpAllowed();

	void setIsRtpRtcpAllowed(boolean value);

	void setTcpActivityTimeoutForRtspOnly();

	void setTcpActivityTimeoutForRtxp();

	void resetTcpActivityTimeoutTimer();

	// -----------------------------------------------------------------------------------------------------------------

	boolean canReadRtsp() throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException;

	Optional<String> readRtspLine();

	void writeRtspLines(@NonNull List<@NonNull String> lines) throws TcpSocketIoException;

	// -----------------------------------------------------------------------------------------------------------------

	boolean canReadRtpOrRtcp(@NonNull RtspProtoTcpChannelNr channNr, boolean ignoreEmpty)
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException;

	boolean readRtpOrRtcpBinary(@NonNull BufferExt buf, @NonNull RtspProtoTcpChannelNr channNr)
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException;

	void writeRtpOrRtcpBinary(@NonNull BufferView bufView, @NonNull RtspProtoTcpChannelNr channNr) throws TcpSocketIoException;

}
